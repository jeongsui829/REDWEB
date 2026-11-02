#!/usr/bin/env python3
"""REDWEB XDR safe outbox sender - test-only CLI."""

import argparse
import hashlib
import sqlite3
import tempfile
from pathlib import Path

import journal_transport
import outbox

DATABASE = Path(
    "/var/lib/redweb-xdr/preview-outbox.sqlite3"
)


def prepare(db):
    db.execute("""
        CREATE TABLE IF NOT EXISTS outbox_quarantine (
            event_id TEXT PRIMARY KEY,
            reason TEXT NOT NULL,
            quarantined_at TEXT NOT NULL
                DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
        )
    """)


def eligible(db):
    return db.execute("""
        SELECT o.event_id, o.message
        FROM outbox AS o
        LEFT JOIN outbox_quarantine AS q
          ON q.event_id = o.event_id
        WHERE o.delivered_at IS NULL
          AND q.event_id IS NULL
        ORDER BY o.created_at, o.rowid
    """).fetchall()


def quarantine(db, event_id, reason):
    db.execute("BEGIN IMMEDIATE")
    try:
        db.execute("""
            INSERT OR IGNORE INTO outbox_quarantine
                (event_id, reason)
            VALUES (?, ?)
        """, (event_id, str(reason)[:500]))
        db.execute("COMMIT")
    except Exception:
        db.execute("ROLLBACK")
        raise


def deliver(db, transport):
    result = {
        "sent": 0,
        "failed": 0,
        "quarantined": 0,
    }

    for event_id, message in eligible(db):
        # 전송 전에 형식을 검증하여 영구 오류 분리
        try:
            journal_transport.validate(
                event_id, message
            )
        except ValueError as exc:
            quarantine(db, event_id, exc)
            result["quarantined"] += 1
            continue

        # 일시적인 전송 장애는 재시도 대상으로 유지
        try:
            transport(event_id, message)
        except Exception as exc:
            outbox.record_failure(db, event_id, exc)
            result["failed"] += 1
            continue

        # ACK DB 저장 실패는 상위로 전파
        # 이 경우 재시도 시 중복 전송될 수 있음
        outbox.mark_delivered(db, event_id)
        result["sent"] += 1

    return result


def self_test():
    with tempfile.TemporaryDirectory(
        prefix="redweb-quarantine-test-"
    ) as tmp:
        path = Path(tmp) / "test.sqlite3"
        db = outbox.open_db(path)
        prepare(db)

        two = (
            "REDWEB_CORR tier=TWO "
            "srcip=8.8.8.8 types=AUTH+XSS"
        )
        three = (
            "REDWEB_CORR tier=THREE "
            "srcip=8.8.8.8 types=AUTH+SQLI+XSS"
        )
        invalid = (
            "REDWEB_CORR tier=TWO "
            "srcip=127.0.0.1 types=AUTH+XSS"
        )

        def event(name, message):
            return {
                "id": hashlib.sha256(
                    name.encode()
                ).hexdigest(),
                "message": message,
            }

        events = [
            event("valid-two", two),
            event("invalid-local", invalid),
            event("valid-three", three),
        ]

        outbox.store_batch(
            db, {"offset": 100}, events
        )

        attempted = []

        def first_transport(event_id, message):
            attempted.append(message)

            if message == two:
                raise OSError(
                    "simulated temporary journal failure"
                )

        result = deliver(db, first_transport)

        assert result == {
            "sent": 1,
            "failed": 1,
            "quarantined": 1,
        }, result

        assert invalid not in attempted
        assert len(eligible(db)) == 1

        q = db.execute("""
            SELECT COUNT(*)
            FROM outbox_quarantine
        """).fetchone()[0]

        assert q == 1

        print("PASS: 영구 오류 이벤트 격리")
        print("PASS: 로컬 IP 이벤트 전송 차단")
        print("PASS: 일시적 장애 이벤트 대기열 유지")
        print("PASS: 정상 THREE 전송 완료")

        db.close()
        db = outbox.open_db(path)
        prepare(db)

        recovered = []

        result = deliver(
            db,
            lambda event_id, message:
                recovered.append(message),
        )

        assert result == {
            "sent": 1,
            "failed": 0,
            "quarantined": 0,
        }, result

        assert recovered == [two]
        assert len(eligible(db)) == 0

        print("PASS: 재시작 후 일시적 장애 복구")
        print("PASS: 격리된 이벤트 자동 재시도 제외")

        # 이미 격리된 이벤트는 유지,
        # 정상 이벤트는 모두 전송 완료된 상태
        counts = db.execute("""
            SELECT
                (SELECT COUNT(*) FROM outbox),
                (SELECT COUNT(*)
                 FROM outbox
                 WHERE delivered_at IS NOT NULL),
                (SELECT COUNT(*)
                 FROM outbox_quarantine)
        """).fetchone()

        assert counts == (3, 2, 1), counts
        assert db.execute(
            "PRAGMA integrity_check"
        ).fetchone()[0] == "ok"

        print("PASS: 정상 2건 / 격리 1건")
        print("PASS: SQLite 무결성 검사")

        db.close()

        print(
            "RESULT: QUARANTINE RECOVERY TEST PASS"
        )
        print(
            "MODE: MOCK / NO LOGGER / NO DISCORD"
        )


def status():
    if not DATABASE.exists():
        raise SystemExit("STOP: 운영 SQLite DB 없음")

    db = sqlite3.connect(
        f"file:{DATABASE}?mode=ro",
        uri=True,
    )

    try:
        exists = db.execute("""
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE type='table'
              AND name='outbox_quarantine'
        """).fetchone()[0]

        total = db.execute(
            "SELECT COUNT(*) FROM outbox"
        ).fetchone()[0]

        completed = db.execute("""
            SELECT COUNT(*) FROM outbox
            WHERE delivered_at IS NOT NULL
        """).fetchone()[0]

        quarantined = 0

        if exists:
            quarantined = db.execute("""
                SELECT COUNT(*)
                FROM outbox_quarantine
            """).fetchone()[0]

        print("=== REDWEB XDR OUTBOX ===")
        print("전체:", total)
        print("전송 완료:", completed)
        print("격리:", quarantined)
        print(
            "재시도 대상:",
            total - completed - quarantined
        )
        print("MODE: READ ONLY")

    finally:
        db.close()


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(
        required=True
    )
    group.add_argument(
        "--self-test", action="store_true"
    )
    group.add_argument(
        "--status", action="store_true"
    )

    args = parser.parse_args()

    if args.self_test:
        self_test()
    else:
        status()


if __name__ == "__main__":
    main()
