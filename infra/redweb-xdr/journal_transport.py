#!/usr/bin/env python3
"""REDWEB XDR validated journald transport."""

import argparse
import ipaddress
import re
import subprocess


EVENT_RE = re.compile(
    r"REDWEB_CORR tier=(TWO|THREE) "
    r"srcip=([0-9.]+) "
    r"types=(AUTH\+SQLI\+XSS|AUTH\+SQLI|AUTH\+XSS|SQLI\+XSS)"
)


def validate(event_id, message):
    if not re.fullmatch(r"[0-9a-f]{64}", event_id):
        raise ValueError("Invalid outbox event ID")

    if not isinstance(message, str):
        raise ValueError("Invalid message type")

    match = EVENT_RE.fullmatch(message)
    if match is None:
        raise ValueError("Invalid REDWEB_CORR message")

    tier, raw_ip, types = match.groups()

    address = ipaddress.ip_address(raw_ip)

    if address.version != 4 or not address.is_global:
        raise ValueError("Local or non-public IP rejected")

    if str(address) != raw_ip:
        raise ValueError("Non-canonical IP address")

    number_of_types = len(types.split("+"))

    expected = 2 if tier == "TWO" else 3

    if number_of_types != expected:
        raise ValueError("Tier and attack types mismatch")

    return True


def send(event_id, message, runner=None):
    validate(event_id, message)

    if runner is None:
        runner = subprocess.run

    command = [
        "/usr/bin/logger",
        "-p", "user.notice",
        "-t", "redweb-corr",
        "--",
        f"{message} event_id={event_id}",
    ]

    runner(
        command,
        check=True,
        timeout=5,
        capture_output=True,
        text=True,
    )


def self_test():
    event_id = "a" * 64
    calls = []

    def mock_runner(command, **kwargs):
        calls.append((command, kwargs))

    two = (
        "REDWEB_CORR tier=TWO "
        "srcip=8.8.8.8 types=AUTH+XSS"
    )

    three = (
        "REDWEB_CORR tier=THREE "
        "srcip=8.8.8.8 types=AUTH+SQLI+XSS"
    )

    send(event_id, two, runner=mock_runner)
    send(event_id, three, runner=mock_runner)

    assert len(calls) == 2

    for command, kwargs in calls:
        assert command[:6] == [
            "/usr/bin/logger",
            "-p", "user.notice",
            "-t", "redweb-corr",
            "--",
        ]
        assert kwargs["check"] is True
        assert kwargs["timeout"] == 5

    print("PASS: TWO 및 THREE 전송 명령 검증")
    print("PASS: SYSLOG_IDENTIFIER redweb-corr")

    invalid_messages = [
        "REDWEB_CORR tier=THREE srcip=8.8.8.8 types=AUTH+XSS",
        "REDWEB_CORR tier=TWO srcip=127.0.0.1 types=AUTH+XSS",
        "REDWEB_CORR tier=TWO srcip=192.0.2.10 types=AUTH+XSS",
        "REDWEB_CORR tier=TWO srcip=8.8.8.8 types=SQLI",
        two + " unexpected=data",
    ]

    for message in invalid_messages:
        try:
            send(event_id, message, runner=mock_runner)
        except ValueError:
            pass
        else:
            raise AssertionError(
                "Invalid message was accepted: " + message
            )

    assert len(calls) == 2

    print("PASS: 비정상 이벤트 및 테스트 IP 차단")

    try:
        send("invalid-id", two, runner=mock_runner)
    except ValueError:
        pass
    else:
        raise AssertionError("Invalid event ID accepted")

    print("PASS: 이벤트 ID 형식 검사")

    def failing_runner(command, **kwargs):
        raise subprocess.CalledProcessError(
            returncode=1,
            cmd=command,
        )

    try:
        send(event_id, two, runner=failing_runner)
    except subprocess.CalledProcessError:
        print("PASS: logger 전송 실패 감지")
    else:
        raise AssertionError("Transport failure ignored")

    print("RESULT: JOURNAL TRANSPORT SELF-TEST PASS")
    print("MODE: MOCK LOGGER / NO JOURNAL / NO DISCORD")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--self-test",
        action="store_true",
        required=True,
    )
    parser.parse_args()

    self_test()


if __name__ == "__main__":
    main()
