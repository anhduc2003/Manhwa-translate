#!/usr/bin/env python3
"""Distils open-source ad/tracker filter lists into the compact assets the app ships.

The output keeps gzip bytes under a .bin name: aapt decompresses anything ending in .gz at
build time, which would put the full text into the APK instead of the compressed form.

Inputs (download once, not committed): easylist.txt, easyprivacy.txt, peterlowe hosts.
Output: app/src/main/assets/filters/<source>.domains.gz + <source>.selectors.gz
"""
import gzip
import re
import sys
from pathlib import Path

OUT = Path("app/src/main/assets/filters")
SAFE_OPTIONS = {
    "third-party", "~third-party", "script", "image", "xmlhttprequest",
    "subdocument", "document", "popup", "other", "stylesheet", "object",
    "font", "media", "websocket", "ping", "beacon",
}
DOMAIN_RULE = re.compile(r"^\|\|([a-z0-9.\-_*]+)\^")

def read(path):
    p = Path(path)
    if not p.exists():
        print(f"missing {path}", file=sys.stderr)
        return []
    return p.read_text(errors="ignore").splitlines()

def parse_abp(lines):
    blocked, allowed, selectors, scoped = set(), set(), set(), set()
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("!") or line.startswith("[") or line.startswith("#"):
            continue
        if "#@#" in line:
            continue
        if "##" in line or "#?#" in line:
            left, _, selector = line.partition("##" if "##" in line else "#?#")
            selector = selector.strip()
            if not selector or selector.startswith("@@"):
                continue
            if left.strip():
                for host in left.split(","):
                    host = host.strip().lstrip("~")
                    if host:
                        scoped.add(f"{host}|{selector}")
            else:
                selectors.add(selector)
            continue
        if line.startswith("@@"):
            match = DOMAIN_RULE.match(line[2:])
            if match:
                allowed.add(match.group(1))
            continue
        if "$" in line:
            pattern, _, options = line.partition("$")
            if not set(options.split(",")).issubset(SAFE_OPTIONS):
                continue
        else:
            pattern = line
        match = DOMAIN_RULE.match(pattern)
        if match:
            blocked.add(match.group(1))
    return blocked, allowed, selectors, scoped

def parse_hosts(lines):
    blocked = set()
    for raw in lines:
        line = raw.split("#")[0].strip()
        if not line or line.startswith("!"):
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[0] in {"0.0.0.0", "127.0.0.1"}:
            blocked.add(parts[1].strip("."))
        elif len(parts) == 1 and "." in parts[0]:
            blocked.add(parts[0].strip("."))
    return blocked - {"localhost", "localhost.localdomain", "broadcasthost", "local"}

def write(name, domains, selectors, scoped):
    OUT.mkdir(parents=True, exist_ok=True)
    allowed = sorted(d for d in domains if d)
    if allowed:
        with gzip.open(OUT / f"{name}.domains.bin", "wt", encoding="utf-8") as handle:
            handle.write("\n".join(allowed))
    merged = sorted(selectors) + sorted(scoped)
    if merged:
        with gzip.open(OUT / f"{name}.selectors.bin", "wt", encoding="utf-8") as handle:
            handle.write("\n".join(merged))
    print(f"{name}: {len(allowed)} domains, {len(merged)} selectors")

def main():
    if len(sys.argv) < 4:
        print("usage: build_filters.py easylist.txt easyprivacy.txt peterlowe.txt [easylist_allow.txt]", file=sys.stderr)
        return
    easy_domains, easy_allowed, easy_selectors, easy_scoped = parse_abp(read(sys.argv[1]))
    priv_domains, priv_allowed, priv_selectors, priv_scoped = parse_abp(read(sys.argv[2]))
    lowe = parse_hosts(read(sys.argv[3]))
    allow_lines = read(sys.argv[4]) if len(sys.argv) > 4 else []
    extra_allowed = set()
    for line in allow_lines:
        match = DOMAIN_RULE.match(line.strip())
        if match:
            extra_allowed.add(match.group(1))
    allowed = easy_allowed | priv_allowed | extra_allowed
    write("easylist", easy_domains - allowed, easy_selectors, easy_scoped)
    write("easyprivacy", priv_domains - allowed - easy_domains, priv_selectors, priv_scoped)
    write("peterlowe", lowe - allowed - easy_domains - priv_domains, set(), set())

if __name__ == "__main__":
    main()
