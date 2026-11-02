#!/usr/bin/env python3
"""REDWEB XDR explicit single-event outbox dispatch."""

import argparse
import fcntl
import hashlib
import os
import re
import tempfile
from pathlib import Path

import journal_transport
import outbox
import safe_sender

DATABASE = Path("/var/lib/redweb-xdr/preview-outbox.sqlite3")
LOCK = Path("/var/lib/redweb-xdr/outbox-dispatch.lock")


def send_selected(db, event_id, transport):
    if not re.fullmatch(r"[0-9a-f]{64}", event_id):
        raise ValueError("Invalid event ID")

    safe_sender.prepare(db)

    row = db.execute("""
        SELECT message, delivered_at
        FROM outbox
        WHERE event_id = ?
    """, (event_id,)).fetchone()

    if row is None:
        return "NOT_FOUND"

    message, delivered_at = row

    if delivered_at is not None:
        return "ALREADY_DELIVERED"

    quarantined = db.execute("""
        SELECT 1 FROM outbox_quarantine
        WHERE event_id = ?
    """, (event_id,)).fetchone()

    if quarantined:
        return "QUARANTINED"

    try:
        journal_transport.validate(event_id, message)
    except ValueError as exc:
        safe_sender.quarantine(db, event_id, exc)
        return "QUARANTINED"

    try:
        transport(event_id, message)
    except Exception as exc:
        outbox.record_failure(db, event_id, exc)
        return "FAILED_RETRYABLE"

    # logger 성공 후 ACK 저장 실패 시 중복 가능
    # ACK 실패는 성공으로 표시하지 않고 예외 전파
    outbox.mark_delivered(db, event_id)

    return "SENT"


def self_test():
    with tempfile.TemporaryDirectory(
        prefix="redweb-single-dispatch-"
    ) as tmp:
        dbpath = Path(tmp) / "test.sqlite3"
        db = outbox.open_db(dbpath)

        valid = (
            "REDWEB_CORR tier=TWO "
            "srcip=8.8.8.8 types=AUTH+XSS"
        )
        invalid = (
            "REDWEB_CORR tier=TWO "
            "srcip=127.0.0.1 types=AUTH+XSS"
        )
        second = (
            "REDWEB_CORR tier=THREE "
            "srcip=8.8.8.8 types=AUTH+SQLI+XSS"
        )

        events = [
            {
                "id": hashlib.sha256(
                    f"single-{i}".encode()
                ).hexdigest(),
                "message": message,
            }
            for i, message in enumerate(
                [valid, invalid, second]
            )
        ]

        outbox.store_batch(
            db, {"offset": 100}, events
        )

        emitted = []

        def mock_runner(command, **kwargs):
            assert command[:6] == [
                "/usr/bin/logger",
                "-p", "user.notice",
                "-t", "redweb-corr",
                "--",
            ]
            emitted.append(command[-1])

        def mock_transport(event_id, message):
            journal_transport.send(
                event_id,
                message,
                runner=mock_runner,
            )

        first_id = events[0]["id"]
        invalid_id = events[1]["id"]
        second_id = events[2]["id"]

        assert send_selected(
            db, first_id, mock_transport
        ) == "SENT"

        assert emitted == [
            valid + " event_id=" + first_id
        ]

        print("PASS: 지정한 이벤트 1건만 전송")
        print("PASS: Event ID 정확히 전달")

        assert send_selected(
            db, first_id, mock_transport
        ) == "ALREADY_DELIVERED"

        assert len(emitted) == 1

        print("PASS: 동일 ID 재전송 방지")

        assert send_selected(
            db, invalid_id, mock_transport
        ) == "QUARANTINED"

        assert len(emitted) == 1

        print("PASS: 로컬 IP 영구 격리")

        def failing_transport(event_id, message):
            raise OSError("simulated logger failure")

        assert send_selected(
            db, second_id, failing_transport
        ) == "FAILED_RETRYABLE"

        assert len(outbox.pending(db)) == 2

        print("PASS: 전송 실패한 이벤트 보존")

        db.close()
        db = outbox.open_db(dbpath)

        assert send_selected(
            db, second_id, mock_transport
        ) == "SENT"

        assert len(emitted) == 2

        assert emitted[-1] == (
            second + " event_id=" + second_id
        )

        print("PASS: 재연결 후 실패 이벤트 재시도")

        assert send_selected(
            db, second_id, mock_transport
        ) == "ALREADY_DELIVERED"

        assert len(emitted) == 2

        print("PASS: 완료 이벤트 중복 전송 없음")

        integrity = db.execute(
            "PRAGMA integrity_check"
        ).fetchone()[0]

        assert integrity == "ok"

        print("PASS: SQLite 무결성 검사")

        db.close()

    print("RESULT: SINGLE DISPATCH SELF-TEST PASS")
    print("MODE: MOCK LOGGER / NO JOURNAL / NO DISCORD")


def send_live(event_id):
    if os.environ.get("REDWEB_XDR_LIVE_SEND") != "YES":
        raise SystemExit("STOP: 실제 전송 비활성화")

    if os.environ.get(
        "REDWEB_XDR_APPROVED_EVENT_ID"
    ) != event_id:
        raise SystemExit("STOP: 승인 Event ID 불일치")

    if not DATABASE.is_file():
        raise SystemExit("STOP: 운영 SQLite DB 없음")

    with LOCK.open("a") as lock:
        os.fchmod(lock.fileno(), 0o600)

        try:
            fcntl.flock(
                lock,
                fcntl.LOCK_EX | fcntl.LOCK_NB
            )
        except BlockingIOError:
            raise SystemExit("STOP: 다른 전송기 실행 중")

        db = outbox.open_db(DATABASE)

        try:
            result = send_selected(
                db,
                event_id,
                journal_transport.send,
            )

            print("EVENT ID:", event_id)
            print("DISPATCH RESULT:", result)

        finally:
            db.close()


def main():
    parser = argparse.ArgumentParser()

    group = parser.add_mutually_exclusive_group(
        required=True
    )

    group.add_argument(
        "--self-test",
        action="store_true",
    )
    group.add_argument(
        "--send-id",
        metavar="EVENT_ID",
    )

    args = parser.parse_args()

    if args.self_test:
        self_test()
    else:
        send_live(args.send_id)


if __name__ == "__main__":
    main()
