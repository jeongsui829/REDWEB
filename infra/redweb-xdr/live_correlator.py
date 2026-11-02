#!/usr/bin/env python3
import argparse
import gzip
import ipaddress
import json
import os
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

SOURCE = Path("/var/ossec/logs/alerts/alerts.json")
STATE = Path("/var/lib/redweb-xdr/preview-state.json")

KST = timezone(timedelta(hours=9))
WINDOW = timedelta(minutes=10)
COOLDOWN = timedelta(minutes=10)
MAX_DELAY = timedelta(minutes=2)

RULE_TYPES = {
    "100110": "SQLI",
    "100111": "XSS",
    "100120": "AUTH",
    "100121": "AUTH",
    "100122": "AUTH",
}
ORDER = ("AUTH", "SQLI", "XSS")


def now_kst():
    return datetime.now(KST)


def empty_state():
    return {
        "cursor": None,
        "latest": {},
        "emitted": {},
    }


def save_state(state):
    tmp = STATE.with_suffix(".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(state, f, separators=(",", ":"))
        f.flush()
        os.fsync(f.fileno())
    os.chmod(tmp, 0o600)
    os.replace(tmp, STATE)


def load_state():
    if not STATE.exists():
        return empty_state()
    with STATE.open(encoding="utf-8") as f:
        state = json.load(f)
    if not all(k in state for k in ("cursor", "latest", "emitted")):
        raise RuntimeError("Invalid preview state")
    return state


def parse_event(raw):
    try:
        e = json.loads(raw)
        rid = str(e.get("rule", {}).get("id", ""))
        kind = RULE_TYPES.get(rid)
        if not kind:
            return None

        ts = datetime.fromisoformat(e["timestamp"])
        addr = ipaddress.ip_address(e["data"]["srcip"])

        if addr.version != 4:
            return None

        return ts, str(addr), kind, addr.is_global
    except (ValueError, KeyError, TypeError):
        return None


def correlate(state, item, allow_test=False, bootstrap=False):
    ts, ip, kind, is_public = item

    if not is_public and not allow_test:
        return None

    now = now_kst()
    if ts > now + timedelta(minutes=1):
        return None
    if ts < now - timedelta(minutes=20):
        return None

    latest = state["latest"].setdefault(ip, {})
    old = latest.get(kind)

    if old is None or ts > datetime.fromisoformat(old):
        latest[kind] = ts.isoformat()

    active = [
        k for k in ORDER
        if k in latest
        and timedelta(0) <= ts - datetime.fromisoformat(latest[k])
        <= WINDOW
    ]

    if len(active) < 2 or bootstrap:
        return None

    tier = "THREE" if len(active) == 3 else "TWO"

    emitted = state["emitted"].setdefault(ip, {})
    previous = emitted.get(tier)

    if previous:
        if ts - datetime.fromisoformat(previous) < COOLDOWN:
            return None

    # 지나치게 오래된 경보는 실시간 경보 후보로 전송하지 않음
    if now - ts > MAX_DELAY:
        return None

    emitted[tier] = ts.isoformat()
    types = "+".join(active)

    return f"REDWEB_CORR tier={tier} srcip={ip} types={types}"


def prune(state):
    cutoff = now_kst() - timedelta(minutes=20)

    for ip in list(state["latest"]):
        entries = state["latest"][ip]
        for kind in list(entries):
            if datetime.fromisoformat(entries[kind]) < cutoff:
                del entries[kind]
        if not entries:
            del state["latest"][ip]

    for ip in list(state["emitted"]):
        entries = state["emitted"][ip]
        for tier in list(entries):
            if datetime.fromisoformat(entries[tier]) < cutoff:
                del entries[tier]
        if not entries:
            del state["emitted"][ip]


def process_stream(f, state, bootstrap=False, limit=None):
    processed = 0
    candidates = []

    while True:
        position = f.tell()
        if limit is not None and position >= limit:
            break

        raw = f.readline()
        if not raw:
            break

        if not raw.endswith(b"\n"):
            f.seek(position)
            break

        if limit is not None and f.tell() > limit:
            f.seek(position)
            break

        item = parse_event(raw)
        if not item:
            continue

        processed += 1
        result = correlate(state, item, bootstrap=bootstrap)
        if result:
            candidates.append(result)

    return processed, candidates


def archive_path(day):
    d = datetime.fromisoformat(day).date()
    folder = SOURCE.parent / str(d.year) / d.strftime("%b")
    base = folder / f"ossec-alerts-{d.day}.json"

    if base.exists():
        return base
    gz = Path(str(base) + ".gz")
    return gz if gz.exists() else None


def initialize(state):
    with SOURCE.open("rb") as f:
        stat = os.fstat(f.fileno())
        end = f.seek(0, os.SEEK_END)

        # 시작 전 최근 10분 이벤트를 상관분석 상태에만 반영
        f.seek(0)
        process_stream(f, state, bootstrap=True, limit=end)

        # 자정 직후에는 전날 보관 로그도 상태 복원에 사용
        yesterday = (now_kst() - timedelta(days=1)).date()
        if now_kst().hour == 0:
            old = archive_path(yesterday.isoformat())
            if old:
                opener = gzip.open if old.suffix == ".gz" else open
                with opener(old, "rb") as prev:
                    process_stream(prev, state, bootstrap=True)

        state["cursor"] = {
            "dev": stat.st_dev,
            "ino": stat.st_ino,
            "offset": end,
            "day": now_kst().date().isoformat(),
        }

    prune(state)
    save_state(state)
    print("INITIALIZED: 기존 이벤트 재발송 없이 현재 EOF에서 시작", flush=True)


def poll(state):
    if not SOURCE.exists():
        print("WARNING: alerts.json 없음", flush=True)
        return

    if state["cursor"] is None:
        initialize(state)
        return

    stat = SOURCE.stat()
    cursor = state["cursor"]
    rotated = (
        stat.st_dev != cursor["dev"]
        or stat.st_ino != cursor["ino"]
        or stat.st_size < cursor["offset"]
    )

    if rotated:
        old = archive_path(cursor["day"])

        if old:
            opener = gzip.open if old.suffix == ".gz" else open
            with opener(old, "rb") as f:
                f.seek(cursor["offset"])
                _, candidates = process_stream(f, state)
                for msg in candidates:
                    print("DRY_RUN_WOULD_EMIT:", msg, flush=True)
        else:
            print(
                "WARNING: 보관 로그 미도착 — 기존 체크포인트 유지 후 재시도",
                flush=True,
            )
            return

        print("INFO: 로그 로테이션 감지", flush=True)
        cursor["offset"] = 0
        cursor["dev"] = stat.st_dev
        cursor["ino"] = stat.st_ino
        cursor["day"] = now_kst().date().isoformat()

    with SOURCE.open("rb") as f:
        f.seek(cursor["offset"])
        processed, candidates = process_stream(f, state)
        cursor["offset"] = f.tell()

    for msg in candidates:
        print("DRY_RUN_WOULD_EMIT:", msg, flush=True)

    prune(state)
    save_state(state)

    if processed:
        print(
            f"INFO: 입력={processed}, 후보={len(candidates)}",
            flush=True
        )


def self_test():
    state = empty_state()
    now = now_kst()
    ip = "192.0.2.10"

    tests = [
        (now, ip, "AUTH", False),
        (now + timedelta(seconds=1), ip, "XSS", False),
        (now + timedelta(seconds=2), ip, "SQLI", False),
    ]

    outputs = [
        correlate(state, e, allow_test=True)
        for e in tests
    ]
    outputs = [x for x in outputs if x]

    expected = [
        f"REDWEB_CORR tier=TWO srcip={ip} types=AUTH+XSS",
        f"REDWEB_CORR tier=THREE srcip={ip} types=AUTH+SQLI+XSS",
    ]

    for line in outputs:
        print("SELF_TEST:", line)

    if outputs != expected:
        raise SystemExit("SELF_TEST FAILED")

    print("SELF_TEST PASS: 2종 및 3종 상관분석")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--watch", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return

    if not args.once and not args.watch:
        parser.error("--once 또는 --watch를 지정하세요")

    state = load_state()
    print("MODE: DRY_RUN / NO JOURNAL / NO WAZUH ALERT", flush=True)

    while True:
        poll(state)
        if args.once:
            break
        time.sleep(2)


if __name__ == "__main__":
    main()
