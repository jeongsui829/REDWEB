#!/usr/bin/env python3
"""Collect a sanitized REVIEW COPY of REDWEB infrastructure; stdlib only.

Does not restart services, change the firewall, run Docker, or upload anything.
Run on the Ubuntu VPS: sudo python3 export_redweb_infra.py
"""
from __future__ import annotations

import argparse
import ast
from datetime import datetime, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import stat
from zipfile import ZIP_DEFLATED, ZipFile

MASK = 'REPLACE_LOCALLY'
MAX_BYTES = 2 * 1024 * 1024
SENSITIVE = re.compile(
    r'(?i)(?:password|passwd|(?:^|[_\-.])pass(?:$|[_\-.])|pwd|secret|'
    r'token|api[_-]?key|webhook|authorization|credential|data_source_name|dsn)'
)
REFERENCE = re.compile(r'(?i)(?:_file|_path|_env|_name|_keys|_pattern|_regex)$')
PATTERNS = (
    (re.compile(r'-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----.*?-----END (?:[A-Z ]+)?PRIVATE KEY-----', re.S),
     'PRIVATE_KEY_REMOVED'),
    (re.compile(r'https?://(?:discord(?:app)?\.com)/api/webhooks/[^\s"\'<>`]+', re.I),
     'https://example.invalid/REPLACE_WITH_DISCORD_WEBHOOK'),
    (re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|'
                r'sk-proj-[A-Za-z0-9_-]{20,}|(?:AKIA|ASIA)[A-Z0-9]{16})\b'), MASK),
    (re.compile(r'(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{12,}'), 'Bearer REPLACE_LOCALLY'),
    (re.compile(r'(?i)\bJSESSIONID=[A-Za-z0-9._-]{12,}'), 'JSESSIONID=REPLACE_LOCALLY'),
    (re.compile(r'(?i)([?&](?:access_token|token|api[_-]?key|password|secret|key)=)[^&#\s"\'<>]+'),
     r'\1REPLACE_LOCALLY'),
    (re.compile(r'(?i)([a-z][a-z0-9+.-]*://)[^/\s"\'<>:@]+:[^@\s"\'<>]+@'),
     r'\1REPLACE_USER:REPLACE_PASSWORD@'),
    (re.compile(r'(?i)(\bIDENTIFIED\s+BY\s+)(?:"[^"]*"|\S+)'), r'\1"REPLACE_LOCALLY"'),
)
SKIP_DIRS = {
    '.git', 'data', 'storage', 'wal', 'chunks', 'cache', 'backups', 'backup',
    'grafana-data', 'loki-data', 'prometheus-data', 'oracle-data', 'node_modules',
    '__pycache__', 'exports', 'lost+found',
}
CONFIG_EXT = {'.yml', '.yaml', '.alloy', '.conf', '.json', '.xml', '.rules', '.properties'}


def sensitive(name):
    if name.lower() in {'data_source_name', 'dsn'}:
        return True
    return bool(SENSITIVE.search(name)) and not REFERENCE.search(name)


def python_literals(text):
    """Replace only literal credentials; preserve program structure and names."""
    tree = ast.parse(text)
    lines = text.splitlines(keepends=True)
    offsets, total = [], 0
    for line in lines:
        offsets.append(total)
        total += len(line)

    def position(line_no, byte_column):
        # AST column offsets are UTF-8 byte offsets, including Korean text.
        line = lines[line_no - 1]
        return offsets[line_no - 1] + len(line.encode('utf-8')[:byte_column].decode('utf-8'))

    edits = {}

    def mark(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, str) and node.value:
            start = position(node.lineno, node.col_offset)
            end = position(node.end_lineno, node.end_col_offset)
            edits[(start, end)] = repr(MASK)

    for node in ast.walk(tree):
        if isinstance(node, (ast.Assign, ast.AnnAssign)):
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            for target in targets:
                name = target.id if isinstance(target, ast.Name) else (
                    target.attr if isinstance(target, ast.Attribute) else '')
                if sensitive(name):
                    mark(node.value)
        elif isinstance(node, ast.Dict):
            for key, value in zip(node.keys, node.values):
                if isinstance(key, ast.Constant) and isinstance(key.value, str) and sensitive(key.value):
                    mark(value)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            positional = node.args.posonlyargs + node.args.args
            defaults = [None] * (len(positional) - len(node.args.defaults)) + node.args.defaults
            for arg, value in zip(positional + node.args.kwonlyargs, defaults + node.args.kw_defaults):
                if sensitive(arg.arg):
                    mark(value)
    for (start, end), value in sorted(edits.items(), reverse=True):
        text = text[:start] + value + text[end:]
    ast.parse(text)
    return text


def redact(text, suffix, protected=()):
    original = text
    for pattern, replacement in PATTERNS:
        text = pattern.sub(replacement, text)
    # Real management IPs are not needed in a published template.
    for value in sorted(set(protected), key=len, reverse=True):
        text = re.sub(r'(?<![0-9A-Fa-f:.])' + re.escape(value) + r'(?![0-9A-Fa-f:.])',
                      '192.0.2.10', text)
    if suffix == '.py':
        text = python_literals(text)
    else:
        # XML secret elements (including multiline values).
        def xml_value(match):
            name, attrs, value = match.group(1, 2, 3)
            return '<' + name + attrs + '>' + (MASK if sensitive(name) else value) + '</' + name + '>'
        text = re.sub(r'<([\w.-]+)(\s[^>]*|)>([^<]*)</\1>', xml_value, text, flags=re.S)
        # Quoted attributes / inline YAML / Alloy / systemd Environment entries.
        def inline_value(match):
            return match[1] + match[2] + MASK + match[2]
        text = re.sub(
            r'''(?i)(\b[\w.-]*(?:password|passwd|pwd|secret|token|api[_-]?key|webhook|authorization|credential|dsn|data_source_name)[\w.-]*\s*[:=]\s*)(["'])(.*?)(?:\2)''',
            lambda m: inline_value(m) if sensitive(re.split(r'\s*[:=]', m[1])[0]) else m[0], text)
        # Key/value files and YAML environment lists, including unquoted secrets.
        output, block_indent = [], None
        for line in text.splitlines(keepends=True):
            indent = len(line) - len(line.lstrip(' '))
            if block_indent is not None:
                if not line.strip() or indent > block_indent:
                    continue
                block_indent = None
            quoted = re.match(r'''^(\s*-\s*)(["'])([\w.-]+)=(.*)\2[ \t]*(\r?\n)?$''', line)
            if quoted and sensitive(quoted[3]):
                output.append(quoted[1] + quoted[2] + quoted[3] + '=' + MASK + quoted[2] + (quoted[5] or ''))
                continue
            if re.match(r'^\s*Environment\s*=', line):
                line = re.sub(r'([\w.-]+)=([^\s"\']+)',
                              lambda m: m[1] + '=' + MASK if sensitive(m[1]) else m[0], line)
            match = re.match(r'^(\s*(?:-\s*)?)([\w.-]+)(\s*[:=]\s*)([^\r\n]*)(\r?\n)?$', line)
            if match and sensitive(match[2]) and match[4].strip():
                if re.fullmatch(r'[|>][+-]?[1-9]?\s*(?:#.*)?', match[4].strip()):
                    block_indent = indent
                replacement = MASK if match[3].strip() == '=' else '"' + MASK + '"'
                line = match[1] + match[2] + match[3] + replacement + (match[5] or '')
            output.append(line)
        text = ''.join(output)
        if suffix == '.json':
            def scrub(value):
                if isinstance(value, dict):
                    return {k: MASK if sensitive(k) and v is not None else scrub(v) for k, v in value.items()}
                if isinstance(value, list):
                    return [scrub(v) for v in value]
                return value
            try:
                text = json.dumps(scrub(json.loads(text)), ensure_ascii=False, indent=2) + '\n'
            except ValueError:
                raise ValueError('JSON sanitization failed') from None
    return text, text != original


def discover(monitor=Path('/opt/monitoring')):
    items = []
    for directory, pattern, destination in (
        ('/opt/redweb-xdr', '*.py', 'redweb-xdr'),
        ('/usr/local/sbin', 'redweb-wazuh-*.py', 'helpers'),
        ('/etc/systemd/system', 'redweb-xdr*', 'systemd'),
        ('/etc/systemd/system/wazuh-manager.service.d', '*.conf', 'systemd/wazuh-manager.service.d'),
        ('/var/ossec/etc/rules', '*redweb*.xml', 'wazuh/rules'),
        ('/var/ossec/etc/decoders', '*redweb*.xml', 'wazuh/decoders'),
    ):
        for path in sorted(Path(directory).glob(pattern)):
            if path.is_file():
                items.append((path, destination + '/' + path.name))
    for filename in ('ossec.conf', 'local_internal_options.conf'):
        path = Path('/var/ossec/etc') / filename
        if path.is_file():
            items.append((path, 'wazuh/' + filename))
    # Referenced by the firewall base unit and Wazuh's custom response command.
    # They are outside /opt/redweb-xdr and were missing from the first export.
    for source, destination in (
        ('/usr/local/sbin/redweb-xdr-firewall-base', 'helpers/redweb-xdr-firewall-base'),
        ('/var/ossec/active-response/bin/redweb-web-guard.py',
         'wazuh/active-response/redweb-web-guard.py'),
    ):
        path = Path(source)
        if path.is_file():
            items.append((path, destination))
    if monitor.is_dir() and not monitor.is_symlink():
        for current, dirs, names in os.walk(monitor, followlinks=False):
            parent = Path(current)
            dirs[:] = sorted(d for d in dirs if d.lower() not in SKIP_DIRS
                             and not d.startswith('.') and not (parent / d).is_symlink())
            if len(parent.relative_to(monitor).parts) >= 6:
                dirs[:] = []
            for name in sorted(names):
                path = parent / name
                if (path.suffix.lower() in CONFIG_EXT or name.endswith('.env.example')) and not re.search(
                    r'(?i)(\.bak|\.backup|backup-|\.old|\.tmp)', name
                ):
                    items.append((path, 'monitoring/' + path.relative_to(monitor).as_posix()))
    return items


def protected_addresses(path=Path('/etc/redweb-xdr/guard_allowlist.txt')):
    values = []
    if path.is_file() and not path.is_symlink():
        for line in path.read_text(encoding='utf-8').splitlines():
            value = line.split('#', 1)[0].strip()
            try:
                network = ipaddress.ip_network(value, strict=False)
            except ValueError:
                continue
            if not network.is_loopback:
                values.extend((value, str(network.network_address)))
    return values


def collect(destination):
    destination = Path(destination).expanduser()
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    # Create exclusively and restrict permissions before writing any content.
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    included, skipped = [], []
    protected = protected_addresses()
    try:
        with os.fdopen(fd, 'wb') as raw, ZipFile(raw, 'w', ZIP_DEFLATED) as archive:
            for path, relative in discover():
                reason = None
                if path.is_symlink() or any(p.is_symlink() for p in path.parents):
                    reason = 'symlink_not_followed'
                elif not stat.S_ISREG(path.stat().st_mode):
                    reason = 'not_regular_file'
                elif path.stat().st_size == 0:
                    reason = 'empty_file'
                elif path.stat().st_size > MAX_BYTES:
                    reason = 'file_size_limit'
                if reason:
                    skipped.append({'source': str(path), 'reason': reason})
                    continue
                try:
                    text = path.read_text(encoding='utf-8-sig')
                    public, changed = redact(text, path.suffix.lower(), protected)
                except (OSError, UnicodeError, ValueError, SyntaxError):
                    skipped.append({'source': str(path), 'reason': 'read_or_sanitization_failed'})
                    continue
                # Configurations with replacements are explicitly templates.
                if changed and path.suffix.lower() != '.py':
                    relative += '.example'
                target = 'infra/' + relative
                data = public.encode('utf-8')
                archive.writestr(target, data)
                included.append({'source': str(path), 'path': target, 'redacted': changed,
                                 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
            archive.writestr('infra/redweb-xdr/guard_allowlist.txt.example',
                             '# Replace locally with your management IP; no real addresses exported.\n'
                             '127.0.0.1\n::1\n# 192.0.2.10/32\n')
            collected_sources = {i['source'] for i in included}
            missing = [p for p in (
                '/usr/local/sbin/redweb-xdr-firewall-base',
                '/var/ossec/active-response/bin/redweb-web-guard.py',
            ) if p not in collected_sources]
            manifest = {'format_version': 1, 'created_at_utc': datetime.now(timezone.utc).isoformat(),
                        'status': 'REVIEW_REQUIRED_NOT_PUBLICLY_UPLOADED',
                        'included': included, 'skipped': skipped, 'required_sources_missing': missing,
                        'not_collected': ['.env values', 'SQLite databases and state', 'logs and alert history',
                                          'Docker data volumes', 'real guard allowlist', 'keys/certificates',
                                          'live Grafana API exports not present in provisioning files']}
            archive.writestr('infra/MANIFEST.json', json.dumps(manifest, ensure_ascii=False, indent=2))
            archive.writestr('infra/README.md',
                '# REDWEB infrastructure review export\n\n'
                'This is a sanitized review copy, not a complete disaster-recovery backup.\n'
                'No service restart, firewall change, Docker operation, or upload was performed.\n'
                'Known credential fields, webhook URLs, tokens and management IPs were masked.\n'
                'Automated masking is best effort. Review every file before publishing.\n'
                'Files ending in .example require local configuration; do not install blindly.\n'
                'Review Python source changes listed in MANIFEST.json before deployment.\n'
                'Real .env files, application state, logs, keys and data volumes were excluded.\n'
                'Grafana settings stored only in its database require a separate export.\n')
    except BaseException:
        # Preserve a partial file for diagnosis; it stays 0600 and is not a valid export.
        raise
    with ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('ZIP integrity check failed')
    return {'zip': str(destination.resolve()), 'collected_files': len(included),
            'redacted_files': sum(i['redacted'] for i in included), 'skipped_files': len(skipped),
            'required_sources_missing': missing,
            'zip_integrity': 'PASS', 'public_upload': 'NOT_PERFORMED'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='New ZIP path; never overwrites an existing file')
    args = parser.parse_args()
    if os.name != 'posix':
        parser.error('Run this collector on the Ubuntu VPS, not on Windows.')
    if os.geteuid() != 0:
        parser.error('Run with sudo to read the specified Wazuh and service files.')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f')
    target = args.output or Path('/var/lib/redweb-xdr/exports') / ('infra-review-' + stamp + '.zip')
    print(json.dumps(collect(target), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
