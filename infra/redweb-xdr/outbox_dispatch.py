#!/usr/bin/env python3
"""REDWEB XDR Outbox dispatch. Live send requires explicit opt-in."""

import argparse
import fcntl
import hashlib
import os
import tempfile
from pathlib import Path

import journal_transport
import outbox
import safe_sender

DATABASE = Path("/var/lib/redweb-xdr/preview-outbox.sqlite3")
LOCK = Path("/var/lib/redweb-xdr/outbox-dispatch.lock")


def self_test():
    with tempfile.TemporaryDirectory(prefix="redweb-dispatch-") as tmp:
        db = outbox.open_db(Path(tmp) / "test.sqlite3")
        safe_sender.prepare(db)

        messages = [
            "REDWEB_CORR tier=TWO srcip=8.8.8.8 types=AUTH+XSS",
            "REDWEB_CORR tier=THREE srcip=8.8.8.8 types=AUTH+SQLI+XSS",
            "REDWEB_CORR tier=TWO srcip=127.0.0.1 types=AUTH+XSS",
        ]

        events = [
            {
                "id": hashlib.sha256(
                    f"dispatch-{i}".encode()
                ).hexdigest(),
                "message": message,
            }
            for i, message in enumerate(messages)
        ]

        outbox.store_batch(db, {"offset": 100}, events)

        emitted = []

        def mock_runner(command, **kwargs):
            assert kwargs["check"] is True
            assert command[0] == "/usr/bin/logger"
            assert command[4] == "redweb-corr"
            emitted.append(command[-1])

        def mock_transport(event_id, message):
            journal_transport.send(
                event_id,
                message,
                runner=mock_runner,
            )

        result = safe_sender.deliver(db, mock_transport)

        assert result == {
            "sent": 2,
            "failed": 0,
            "quarantined": 1,
        }, result

        for index in (0, 1):
            assert (
                messages[index]
                + " event_id="
                + events[index]["id"]
            ) in emitted

        print("PASS: TWO 및 THREE 전송 형식")
        print("PASS: Outbox Event ID 전달")
        print("PASS: 로컬 IP 이벤트 격리")

        db.close()
        db = outbox.open_db(Path(tmp) / "test.sqlite3")
        safe_sender.prepare(db)

        repeated = safe_sender.deliver(db, mock_transport)

        assert repeated == {
            "sent": 0,
            "failed": 0,
            "quarantined": 0,
        }, repeated
        assert len(emitted) == 2

        print("PASS: 재시작 후 중복 전송 없음")
        print("RESULT: DISPATCH WORKER SELF-TEST PASS")
        print("MODE: MOCK / NO REAL LOGGER / NO DISCORD")
        db.close()


def send_once():
    if os.environ.get("REDWEB_XDR_LIVE_SEND") != "YES":
        raise SystemExit(
            "STOP: 실제 전송 비활성화. 명시적 승인 필요."
        )

    with LOCK.open("a") as lock:
        os.chmod(LOCK, 0o600)

        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit("STOP: 다른 전송 프로세스가 실행 중")

        db = outbox.open_db(DATABASE)
        try:
            safe_sender.prepare(db)
            result = safe_sender.deliver(
                db, journal_transport.send
            )
            print("DISPATCH RESULT:", result)
        finally:
            db.close()


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--self-test", action="store_true")
    group.add_argument("--status", action="store_true")
    group.add_argument("--send-once", action="store_true")

    args = parser.parse_args()

    if args.self_test:
        self_test()
    elif args.status:
        safe_sender.status()
    else:
        send_once()


if __name__ == "__main__":
    main()
