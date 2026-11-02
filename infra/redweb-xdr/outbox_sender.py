#!/usr/bin/env python3
"""REDWEB XDR outbox sender - no live transport enabled."""

import argparse
import sqlite3
import tempfile
from pathlib import Path

import outbox


DATABASE = Path(
    "/var/lib/redweb-xdr/preview-outbox.sqlite3"
)


def deliver_pending(db, transport):
    sent = 0
    failed = 0

    for event_id, message in outbox.pending(db):
        try:
            transport(event_id, message)
        except Exception as exc:
            outbox.record_failure(db, event_id, exc)
            failed += 1
            continue

        # 이 작업이 실패하면 이벤트는 대기열에 남음.
        # 이미 전송됐을 수 있으므로 중복 전송 가능성이 존재.
        outbox.mark_delivered(db, event_id)
        sent += 1

    return sent, failed


def status():
    if not DATABASE.is_file():
        raise SystemExit("STOP: SQLite DB가 없습니다.")

    db = sqlite3.connect(
        f"file:{DATABASE}?mode=ro",
        uri=True,
    )

    try:
        rows = db.execute("""
            SELECT
                COUNT(*),
                SUM(CASE WHEN delivered_at IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN delivered_at IS NOT NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN attempts > 0 THEN 1 ELSE 0 END)
            FROM outbox
        """).fetchone()

        print("=== REDWEB XDR OUTBOX STATUS ===")
        print("전체:", rows[0] or 0)
        print("미전송:", rows[1] or 0)
        print("전송 완료:", rows[2] or 0)
        print("실패 기록 존재:", rows[3] or 0)
        print("MODE: READ ONLY")
    finally:
        db.close()


def self_test():
    with tempfile.TemporaryDirectory(
        prefix="redweb-sender-test-"
    ) as tmp:
        database = Path(tmp) / "sender.sqlite3"
        db = outbox.open_db(database)

        events = [
            {
                "id": "test-two",
                "message": (
                    "REDWEB_CORR tier=TWO "
                    "srcip=192.0.2.10 types=AUTH+XSS"
                ),
            },
            {
                "id": "test-three",
                "message": (
                    "REDWEB_CORR tier=THREE "
                    "srcip=192.0.2.10 types=AUTH+SQLI+XSS"
                ),
            },
        ]

        outbox.store_batch(db, {"offset": 100}, events)

        received = []

        def failing_transport(event_id, message):
            if event_id == "test-two":
                raise OSError("simulated transport failure")
            received.append(event_id)

        sent, failed = deliver_pending(
            db, failing_transport
        )

        assert (sent, failed) == (1, 1)
        assert len(outbox.pending(db)) == 1

        row = db.execute(
            "SELECT attempts FROM outbox WHERE event_id = ?",
            ("test-two",),
        ).fetchone()

        assert row[0] == 1
        print("PASS: 전송 실패 시 대기열 유지 및 실패 횟수 기록")

        db.close()
        db = outbox.open_db(database)

        sent, failed = deliver_pending(
            db,
            lambda event_id, message: received.append(event_id),
        )

        assert (sent, failed) == (1, 0)
        assert len(outbox.pending(db)) == 0

        print("PASS: 재시작 후 미전송 이벤트 재처리")
        print("PASS: 전송 완료 이벤트 재전송 방지")

        # 새로운 이벤트를 이용해 ACK 저장 실패 상황 모의
        outbox.store_batch(
            db,
            {"offset": 200},
            [{
                "id": "test-ack-failure",
                "message": (
                    "REDWEB_CORR tier=TWO "
                    "srcip=192.0.2.11 types=SQLI+XSS"
                ),
            }],
        )

        db.execute("""
            CREATE TRIGGER fail_delivery_ack
            BEFORE UPDATE OF delivered_at ON outbox
            WHEN NEW.event_id = 'test-ack-failure'
            BEGIN
                SELECT RAISE(
                    ABORT,
                    'simulated delivery ACK failure'
                );
            END
        """)

        try:
            deliver_pending(
                db,
                lambda event_id, message: received.append(event_id),
            )
            raise AssertionError("ACK 실패가 발생하지 않음")
        except sqlite3.DatabaseError:
            pass

        assert len(outbox.pending(db)) == 1
        assert received.count("test-ack-failure") == 1

        print("PASS: ACK 저장 실패 시 미전송 상태 유지")

        db.execute("DROP TRIGGER fail_delivery_ack")
        db.close()
        db = outbox.open_db(database)

        deliver_pending(
            db,
            lambda event_id, message: received.append(event_id),
        )

        assert len(outbox.pending(db)) == 0
        assert received.count("test-ack-failure") == 2

        print("PASS: 장애 복구 후 재시도 가능")
        print("CONFIRMED: ACK 실패 시 중복 전송 가능")
        print("RESULT: OUTBOX DELIVERY RECOVERY TEST PASS")
        print("MODE: MOCK TRANSPORT / NO JOURNAL / NO DISCORD")

        db.close()


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--self-test", action="store_true")
    group.add_argument("--status", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
    else:
        status()


if __name__ == "__main__":
    main()
