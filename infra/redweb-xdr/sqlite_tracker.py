#!/usr/bin/env python3
"""REDWEB XDR SQLite tracker - DRY RUN only."""

import argparse
import copy
import gzip
import hashlib
import os
import time
from datetime import date
from pathlib import Path

import live_correlator as core
import outbox

SOURCE = Path("/var/ossec/logs/alerts/alerts.json")
DATABASE = Path("/var/lib/redweb-xdr/preview-outbox.sqlite3")


def archive_path(day_string):
    day = date.fromisoformat(day_string)
    directory = (
        SOURCE.parent
        / str(day.year)
        / day.strftime("%b")
    )
    name = f"ossec-alerts-{day.day}.json"

    plain = directory / name
    compressed = directory / (name + ".gz")

    if plain.exists():
        return plain
    if compressed.exists():
        return compressed

    return None


def scan(stream, state, generated, bootstrap=False):
    processed = 0

    while True:
        position = stream.tell()
        raw = stream.readline()

        if not raw:
            return processed, False

        if not raw.endswith(b"\n"):
            stream.seek(position)
            return processed, True

        item = core.parse_event(raw)

        if item is None:
            continue

        processed += 1

        message = core.correlate(
            state,
            item,
            bootstrap=bootstrap,
        )

        if message is None:
            continue

        event_id = hashlib.sha256(
            raw + b"\x00" + message.encode("utf-8")
        ).hexdigest()

        generated.append({
            "id": event_id,
            "message": message,
        })


def initialize(db):
    state = core.empty_state()
    now = core.now_kst()

    # 자정 직후 시작하는 경우 전날 이벤트를
    # 상관분석 상태 복원에만 사용합니다.
    if now.hour == 0:
        yesterday = now.date().fromordinal(
            now.date().toordinal() - 1
        )
        archive = archive_path(yesterday.isoformat())

        if archive is not None:
            opener = (
                gzip.open
                if archive.suffix == ".gz"
                else open
            )
            with opener(archive, "rb") as stream:
                scan(stream, state, [], bootstrap=True)

    with SOURCE.open("rb") as stream:
        stat = os.fstat(stream.fileno())

        # 기존 경보 재발송 없이
        # 최근 공격 유형 상태만 복원합니다.
        scan(stream, state, [], bootstrap=True)

        offset = stream.tell()

    state["cursor"] = {
        "dev": stat.st_dev,
        "ino": stat.st_ino,
        "offset": offset,
        "day": now.date().isoformat(),
    }

    core.prune(state)

    # 초기 체크포인트도 SQLite 트랜잭션으로 저장
    outbox.store_batch(db, state, [])

    print(
        "INITIALIZED: SQLite 체크포인트 생성 / "
        "기존 경보 재발송 없음",
        flush=True,
    )


def poll(db):
    if not SOURCE.is_file():
        print(
            "WARNING: Wazuh alerts.json 없음",
            flush=True,
        )
        return

    saved = outbox.get_cursor(db)

    if saved is None:
        initialize(db)
        return

    state = copy.deepcopy(saved)

    if not all(
        key in state
        for key in ("cursor", "latest", "emitted")
    ):
        raise RuntimeError("Invalid SQLite tracker state")

    cursor = state["cursor"]

    if cursor is None:
        raise RuntimeError("Missing SQLite cursor")

    generated = []
    recovered = 0
    rotated = False

    with SOURCE.open("rb") as current:
        stat = os.fstat(current.fileno())

        rotated = (
            stat.st_dev != cursor["dev"]
            or stat.st_ino != cursor["ino"]
            or stat.st_size < cursor["offset"]
        )

        if rotated:
            archive = archive_path(cursor["day"])

            if archive is None:
                print(
                    "WARNING: 보관 파일 미도착 — "
                    "SQLite 체크포인트 유지",
                    flush=True,
                )
                return

            opener = (
                gzip.open
                if archive.suffix == ".gz"
                else open
            )

            with opener(archive, "rb") as previous:
                previous.seek(cursor["offset"])

                recovered, partial = scan(
                    previous,
                    state,
                    generated,
                )

                if partial:
                    print(
                        "WARNING: 보관 파일의 미완성 행 — "
                        "체크포인트 유지",
                        flush=True,
                    )
                    return

            cursor = {
                "dev": stat.st_dev,
                "ino": stat.st_ino,
                "offset": 0,
                "day": core.now_kst().date().isoformat(),
            }
            state["cursor"] = cursor

        current.seek(cursor["offset"])

        processed, partial = scan(
            current,
            state,
            generated,
        )

        cursor["offset"] = current.tell()

    core.prune(state)

    # 상태, 읽기 위치, 생성한 경보를
    # 하나의 SQLite 트랜잭션으로 확정합니다.
    outbox.store_batch(db, state, generated)

    if rotated:
        print(
            "INFO: 로테이션 복구 및 SQLite 저장 완료",
            flush=True,
        )

    for event in generated:
        print(
            "SQLITE_QUEUED:",
            event["message"],
            flush=True,
        )

    if recovered or processed:
        print(
            f"INFO: 복구={recovered}, "
            f"신규={processed}, "
            f"대기열추가={len(generated)}",
            flush=True,
        )

    if partial:
        print(
            "INFO: 마지막 미완성 행은 다음 조회에서 처리",
            flush=True,
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--watch", action="store_true")
    args = parser.parse_args()

    if args.once == args.watch:
        parser.error(
            "--once 또는 --watch 중 하나를 지정하세요"
        )

    os.umask(0o077)

    print(
        "MODE: SQLITE DRY-RUN / "
        "NO JOURNAL EMIT / NO DISCORD",
        flush=True,
    )

    db = outbox.open_db(DATABASE)

    try:
        while True:
            poll(db)

            if args.once:
                break

            time.sleep(2)

    finally:
        db.close()


if __name__ == "__main__":
    main()
