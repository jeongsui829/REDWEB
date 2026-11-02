#!/usr/bin/env python3
import gzip
import ipaddress
import json
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

BASE = Path("/var/ossec/logs/alerts")
KST = timezone(timedelta(hours=9))
WINDOW = timedelta(minutes=10)
COOLDOWN = timedelta(minutes=10)

RULE_TYPES = {
    "100110": "SQLI",
    "100111": "XSS",
    "100120": "AUTH",
    "100121": "AUTH",
    "100122": "AUTH",
}

TYPE_ORDER = ("AUTH", "SQLI", "XSS")


def source_files(start, end):
    day = start.date()

    while day <= end.date():
        if day == end.date():
            path = BASE / "alerts.json"
        else:
            folder = BASE / str(day.year) / day.strftime("%b")
            name = f"ossec-alerts-{day.day}"

            path = folder / f"{name}.json"
            if not path.exists():
                path = folder / f"{name}.json.gz"

        if path.exists():
            yield path

        day += timedelta(days=1)


def read_events(start, end):
    events = []
    seen = set()

    for path in source_files(start, end):
        opener = gzip.open if path.suffix == ".gz" else open

        with opener(path, "rt", encoding="utf-8") as f:
            for line in f:
                try:
                    event = json.loads(line)

                    rule_id = str(
                        event.get("rule", {}).get("id", "")
                    )
                    kind = RULE_TYPES.get(rule_id)

                    if kind is None:
                        continue

                    ts = datetime.fromisoformat(
                        event["timestamp"]
                    )

                    if not start <= ts <= end:
                        continue

                    raw_ip = event.get(
                        "data", {}
                    ).get("srcip", "")

                    address = ipaddress.ip_address(raw_ip)

                    # 현재 Wazuh 디코더가 IPv4만 지원합니다.
                    if address.version != 4:
                        continue

                    ip = str(address)

                    event_id = event.get("id")

                    if event_id is not None:
                        event_id = str(event_id)

                        if event_id in seen:
                            continue

                        seen.add(event_id)

                    events.append((ts, ip, kind))

                except (
                    ValueError,
                    TypeError,
                    KeyError,
                    json.JSONDecodeError,
                ):
                    continue

    return sorted(events, key=lambda e: e[0])


class Correlator:
    def __init__(self):
        self.latest = defaultdict(dict)
        self.last_emitted = defaultdict(dict)

    def process(self, ts, ip, kind):
        latest = self.latest[ip]
        latest[kind] = ts

        active = {
            name
            for name, last_ts in latest.items()
            if ts - last_ts <= WINDOW
        }

        if len(active) == 2:
            tier = "TWO"
        elif len(active) == 3:
            tier = "THREE"
        else:
            return None

        previous = self.last_emitted[ip].get(tier)

        if previous is not None and ts - previous < COOLDOWN:
            return None

        self.last_emitted[ip][tier] = ts

        types = "+".join(
            kind for kind in TYPE_ORDER
            if kind in active
        )

        return (
            f"REDWEB_CORR tier={tier} "
            f"srcip={ip} types={types}"
        )


def main():
    end = datetime.now(KST)
    start = end - timedelta(hours=48)

    events = read_events(start, end)
    correlator = Correlator()
    results = []

    print("=== REDWEB XDR OFFLINE REPLAY ===")
    print("기간:", start.isoformat(), "~", end.isoformat())
    print("입력 이벤트:", len(events))
    print("모드: READ-ONLY / NO JOURNAL / NO ALERT")

    for ts, ip, kind in events:
        result = correlator.process(ts, ip, kind)

        if result is None:
            continue

        address = ipaddress.ip_address(ip)

        if address.is_global:
            scope = "PUBLIC_UNVERIFIED"
        else:
            scope = "LOCAL_TEST"

        results.append((ts, scope, result))

    print("\n=== 상관분석 결과 ===")

    for ts, scope, result in results:
        print(ts.isoformat())
        print("  scope:", scope)
        print(" ", result)

    print("\n생성 후보:", len(results), "건")
    print("RESULT: OFFLINE REPLAY COMPLETE")


if __name__ == "__main__":
    main()
