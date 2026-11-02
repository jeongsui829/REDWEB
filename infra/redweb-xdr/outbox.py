#!/usr/bin/env python3
"""REDWEB XDR SQLite outbox - isolated storage component."""

import json
import sqlite3
import sys
import tempfile
from pathlib import Path


def open_db(path):
    conn = sqlite3.connect(
        str(path),
        timeout=10,
        isolation_level=None,
    )

    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=FULL")
    conn.execute("PRAGMA busy_timeout=10000")

    conn.execute("""
        CREATE TABLE IF NOT EXISTS checkpoints (
            name TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """)

    conn.execute("""
        CREATE TABLE IF NOT EXISTS outbox (
            event_id TEXT PRIMARY KEY,
            message TEXT NOT NULL,
            created_at TEXT NOT NULL
                DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
            delivered_at TEXT,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT
        )
    """)

    return conn


def store_batch(conn, cursor, events):
    """Atomically save checkpoint and generated events."""
    conn.execute("BEGIN IMMEDIATE")

    try:
        for event in events:
            event_id = event["id"]
            message = event["message"]

            previous = conn.execute(
                "SELECT message FROM outbox WHERE event_id = ?",
                (event_id,),
            ).fetchone()

            if previous is not None:
                if previous[0] != message:
                    raise ValueError(
                        "Conflicting event_id: " + event_id
                    )
                continue

            conn.execute(
                """
                INSERT INTO outbox (event_id, message)
                VALUES (?, ?)
                """,
                (event_id, message),
            )

        conn.execute(
            """
            INSERT INTO checkpoints (name, value)
            VALUES ('wazuh', ?)
            ON CONFLICT(name) DO UPDATE
            SET value = excluded.value
            """,
            (json.dumps(cursor, sort_keys=True),),
        )

        conn.execute("COMMIT")

    except Exception:
        conn.execute("ROLLBACK")
        raise


def get_cursor(conn):
    row = conn.execute(
        "SELECT value FROM checkpoints WHERE name = 'wazuh'"
    ).fetchone()

    return json.loads(row[0]) if row else None


def pending(conn):
    return conn.execute(
        """
        SELECT event_id, message
        FROM outbox
        WHERE delivered_at IS NULL
        ORDER BY created_at, rowid
        """
    ).fetchall()


def mark_delivered(conn, event_id):
    conn.execute(
        """
        UPDATE outbox
        SET delivered_at =
            strftime('%Y-%m-%dT%H:%M:%fZ','now'),
            last_error = NULL
        WHERE event_id = ?
          AND delivered_at IS NULL
        """,
        (event_id,),
    )


def record_failure(conn, event_id, error):
    conn.execute(
        """
        UPDATE outbox
        SET attempts = attempts + 1,
            last_error = ?
        WHERE event_id = ?
          AND delivered_at IS NULL
        """,
        (str(error)[:500], event_id),
    )


def self_test():
    with tempfile.TemporaryDirectory(
        prefix="redweb-outbox-test-"
    ) as temp:
        path = Path(temp) / "test.sqlite3"

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
                    "srcip=192.0.2.10 "
                    "types=AUTH+SQLI+XSS"
                ),
            },
        ]

        db = open_db(path)
        store_batch(db, {"offset": 100}, events)
        db.close()

        db = open_db(path)

        assert get_cursor(db) == {"offset": 100}
        assert len(pending(db)) == 2
        print("PASS: 재시작 후 미전송 이벤트 2건 복구")

        store_batch(db, {"offset": 200}, events)

        assert len(pending(db)) == 2
        assert get_cursor(db) == {"offset": 200}
        print("PASS: 동일 이벤트 ID 중복 저장 방지")

        try:
            store_batch(
                db,
                {"offset": 300},
                [{
                    "id": "test-two",
                    "message": "DIFFERENT MESSAGE",
                }],
            )
            raise AssertionError(
                "충돌한 이벤트가 저장됐습니다."
            )
        except ValueError:
            pass

        assert get_cursor(db) == {"offset": 200}
        print("PASS: 오류 시 체크포인트 변경 롤백")

        record_failure(db, "test-two", "simulated failure")
        assert len(pending(db)) == 2
        print("PASS: 전송 실패 이벤트 재시도 대기 유지")

        mark_delivered(db, "test-two")
        db.close()

        db = open_db(path)

        remaining = pending(db)

        assert len(remaining) == 1
        assert remaining[0][0] == "test-three"
        assert get_cursor(db) == {"offset": 200}

        print("PASS: 전송 완료 이벤트와 미전송 이벤트 구분")
        print("PASS: 재시작 후 체크포인트 영구 보존")
        print("RESULT: SQLITE OUTBOX SELF-TEST PASS")

        db.close()


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        raise SystemExit(
            "이 모듈은 현재 --self-test만 지원합니다."
        )
    self_test()
