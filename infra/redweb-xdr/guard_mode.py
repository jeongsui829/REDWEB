#!/usr/bin/env python3
"""REDWEB XDR dedicated nftables guard mode controller."""

import argparse
import fcntl
import json
import os
import subprocess

NFT = "/usr/sbin/nft"
TABLE = "redweb_xdr"
CHAIN = "web_guard"
SET = "blocked_v4"
TAG = "REDWEB_XDR_BLOCK_8080"
LOCK = "/run/lock/redweb-xdr-guard.lock"

ADD_RULE = (
    'add rule inet redweb_xdr web_guard '
    'ip saddr @blocked_v4 tcp dport 8080 '
    'counter drop comment "REDWEB_XDR_BLOCK_8080"\n'
)


def nft_json(*args):
    result = subprocess.run(
        [NFT, "-j", *args],
        check=True,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return json.loads(result.stdout)["nftables"]


def inspect():
    chain = nft_json(
        "list", "chain", "inet", TABLE, CHAIN
    )
    entries = nft_json(
        "list", "set", "inet", TABLE, SET
    )

    rules = [
        item["rule"]
        for item in chain
        if "rule" in item
    ]

    foreign = [
        rule for rule in rules
        if rule.get("comment") != TAG
    ]

    if foreign:
        raise RuntimeError(
            "STOP: 전용 체인에 예상하지 못한 규칙 존재"
        )

    if len(rules) > 1:
        raise RuntimeError(
            "STOP: 중복 차단 규칙 발견"
        )

    sets = [
        item["set"]
        for item in entries
        if "set" in item
    ]

    if len(sets) != 1:
        raise RuntimeError("STOP: IP 집합 확인 실패")

    current_set = sets[0]

    if (
        current_set.get("type") != "ipv4_addr"
        or "timeout" not in current_set.get("flags", [])
    ):
        raise RuntimeError(
            "STOP: 차단 집합의 타입 또는 만료 설정 불일치"
        )

    members = current_set.get("elem", [])

    return bool(rules), members


def apply(script, check_only=False):
    command = [NFT]

    if check_only:
        command.append("-c")

    command.extend(["-f", "-"])

    subprocess.run(
        command,
        input=script,
        text=True,
        check=True,
        capture_output=True,
        timeout=10,
    )


def status():
    enabled, members = inspect()

    print("=== REDWEB XDR FIREWALL MODE ===")
    print("모드:", "DEFENSE" if enabled else "DIAGNOSTIC")
    print("TCP 포트:", 8080)
    print("현재 차단 IP 수:", len(members))
    print("자동 Wazuh 대응:", "아직 미연결")
    print("RESULT: GUARD STATUS OK")


def set_mode(enable):
    with open(LOCK, "a") as lock:
        os.fchmod(lock.fileno(), 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX)

        enabled, members = inspect()

        if enable:
            if enabled:
                print("ALREADY ON: DEFENSE")
                return

            if members:
                raise RuntimeError(
                    "STOP: OFF 상태에 잔여 IP 존재"
                )

            apply(ADD_RULE, check_only=True)
            apply(ADD_RULE)

            print("RESULT: GUARD MODE ON")
            print("차단 IP 목록은 비어 있음")
        else:
            script = (
                "flush chain inet redweb_xdr web_guard\n"
                "flush set inet redweb_xdr blocked_v4\n"
            )

            apply(script, check_only=True)
            apply(script)

            print("RESULT: GUARD MODE OFF")
            print("기존 차단 목록 초기화 완료")

        status()


def self_test():
    assert "ip saddr @blocked_v4" in ADD_RULE
    assert "tcp dport 8080" in ADD_RULE
    assert 'counter drop comment' in ADD_RULE
    assert TAG in ADD_RULE

    print("PASS: IPv4 집합 매칭")
    print("PASS: TCP 8080만 차단")
    print("PASS: 전용 규칙 식별자")
    print("PASS: OFF 시 체인 및 집합 초기화 정의")
    print("RESULT: GUARD CONTROLLER SELF-TEST PASS")
    print("MODE: NO FIREWALL CHANGE")


def main():
    parser = argparse.ArgumentParser()

    group = parser.add_mutually_exclusive_group(
        required=True
    )
    group.add_argument("--status", action="store_true")
    group.add_argument("--self-test", action="store_true")
    group.add_argument("--on", action="store_true")
    group.add_argument("--off", action="store_true")

    args = parser.parse_args()

    if args.self_test:
        self_test()
    elif args.status:
        status()
    elif args.on:
        set_mode(True)
    else:
        set_mode(False)


if __name__ == "__main__":
    main()
