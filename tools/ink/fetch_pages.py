#!/usr/bin/env python3
"""Downloads chapter pages for the text-detector dataset.

    python fetch_pages.py <out-dir> <chapter-page.html> [more.html ...]

Each chapter page is a rendered Next.js page; the chapter's strips are the jpg URLs under
`uploads/series/`. Pages are named `<chapter>-<index>.jpg` so a whole chapter can be held out.
"""
import os
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

AGENT = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
)
PATTERN = re.compile(r"https://media\.omegascans\.org/file/[^\"']*uploads/series/[^\"']*\.jpg")


def chapter_name(html_path):
    match = re.search(r"chapter-(\d+)", html_path)
    return f"ch{match.group(1)}" if match else os.path.basename(html_path)


def image_urls(html_path):
    with open(html_path, encoding="utf-8", errors="ignore") as handle:
        found = PATTERN.findall(handle.read())
    seen, ordered = set(), []
    for url in found:
        if url in seen:
            continue
        seen.add(url)
        ordered.append(url)
    return ordered


def fetch(job):
    url, target = job
    if os.path.exists(target) and os.path.getsize(target) > 1024:
        return target
    request = urllib.request.Request(url, headers={"User-Agent": AGENT, "Referer": "https://omegascans.org/"})
    with urllib.request.urlopen(request, timeout=60) as response, open(target, "wb") as handle:
        handle.write(response.read())
    return target


def main():
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    jobs = []
    for html in sys.argv[2:]:
        name = chapter_name(html)
        for index, url in enumerate(image_urls(html), start=1):
            jobs.append((url, os.path.join(out_dir, f"{name}-{index:02d}.jpg")))
    with ThreadPoolExecutor(max_workers=4) as pool:
        for done, path in enumerate(pool.map(fetch, jobs), start=1):
            if done % 10 == 0 or done == len(jobs):
                print(f"{done}/{len(jobs)} {os.path.basename(path)}", flush=True)


if __name__ == "__main__":
    main()
