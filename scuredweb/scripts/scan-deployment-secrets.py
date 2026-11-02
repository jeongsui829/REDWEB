"""Report only location/type, never matched values. Also inspect ZIP/WAR text entries."""
import json
import re
import sys
import zipfile
from pathlib import Path

RULES = {
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "cloud-access-key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "token-literal": re.compile(r"\b(?:ghp_[A-Za-z0-9]{30,}|sk-proj-[A-Za-z0-9_-]{30,})\b"),
    "credential-assignment-review": re.compile(r'''(?i)(?:db\.password|password|api[_-]?key|secret)\s*[=:]\s*["']([^"'\r\n]{4,})["']'''),
    "seed-password-review": re.compile(r"(?i)INSERT\s+INTO\s+RW_USER"),
    "sql-password-seed-review": re.compile(r"(?i)(?:MERGE\s+INTO\s+RW_USER|PASSWORD_HASH\s*=\s*')"),
}
TEXT = {".java", ".xml", ".properties", ".sql", ".ps1", ".sh", ".env", ".example", ".conf", ".json", ".md", ".txt", ".yml", ".yaml", ".js", ".jsp"}

def inspect(location, data, findings):
    text = data.decode("utf-8", errors="replace")
    for kind, pattern in RULES.items():
        for match in pattern.finditer(text):
            if kind == "credential-assignment-review" and any(x in match[1] for x in ("${", "REPLACE", "CHANGE", "request.", "System.", "<")):
                continue
            findings.append({"location": location, "line": text.count("\n", 0, match.start()) + 1, "type": kind})

def scan(root):
    findings=[]
    files=[root] if root.is_file() else root.rglob("*")
    for path in files:
        if not path.is_file() or any(p in {".git", ".work-predeploy"} for p in path.parts): continue
        if path.suffix.lower() in {".zip", ".war"}:
            try:
                with zipfile.ZipFile(path) as archive:
                    for entry in archive.infolist():
                        if Path(entry.filename).suffix.lower() in TEXT and entry.file_size < 20_000_000:
                            inspect(str(path)+"!"+entry.filename,archive.read(entry),findings)
            except zipfile.BadZipFile:
                findings.append({"location":str(path),"type":"unreadable-archive"})
        elif path.suffix.lower() in TEXT and path.stat().st_size < 20_000_000:
            inspect(str(path),path.read_bytes(),findings)
    return findings

if __name__ == "__main__":
    findings=scan(Path(sys.argv[1] if len(sys.argv)>1 else "."))
    print(json.dumps(findings,ensure_ascii=False,indent=2))
    sys.exit(1 if findings else 0)
