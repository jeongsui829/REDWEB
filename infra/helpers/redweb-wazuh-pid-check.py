#!/usr/bin/python3
from pathlib import Path
import errno
import os
import re
import sys
import time

apply = sys.argv[1:] == ["--apply"]
if sys.argv[1:] not in (["--apply"], ["--dry-run"]):
    raise SystemExit("Use --dry-run or --apply")

boot = next(
    int(line.split()[1])
    for line in Path("/proc/stat").read_text().splitlines()
    if line.startswith("btime ")
)
run = Path("/var/ossec/var/run")
archive = Path("/var/backups/redweb-xdr/stale-pids") / (
    time.strftime("%Y%m%d-%H%M%S") + f"-{os.getpid()}"
)

for name in ("wazuh-analysisd", "wazuh-syscheckd", "wazuh-modulesd"):
    for path in sorted(run.glob(name + "-*.pid")):
        if path.is_symlink() or not path.is_file():
            raise SystemExit(f"STOP: unexpected file type: {path.name}")
        before = path.stat()
        if before.st_mtime >= boot:
            print("KEEP current-boot:", path.name)
            continue
        match = re.fullmatch(re.escape(name) + r"-(\d+)\.pid", path.name)
        value = path.read_text().strip()
        if not match or value != match.group(1):
            raise SystemExit(f"STOP: unexpected PID format: {path.name}")
        try:
            exe = os.readlink(f"/proc/{value}/exe")
        except OSError as exc:
            if exc.errno not in (errno.ENOENT, errno.ESRCH):
                raise
            exe = None
        if exe and exe.removesuffix(" (deleted)") == f"/var/ossec/bin/{name}":
            print("KEEP matching-process:", path.name)
            continue
        after = path.stat()
        if (before.st_ino, before.st_mtime_ns, before.st_size) != (
            after.st_ino, after.st_mtime_ns, after.st_size
        ):
            raise SystemExit("STOP: PID file changed during inspection")
        print("ARCHIVE" if apply else "WOULD_ARCHIVE", path.name)
        if apply:
            archive.mkdir(parents=True, mode=0o700, exist_ok=True)
            path.rename(archive / path.name)

print("PID_CHECK_OK", "apply" if apply else "dry-run")
