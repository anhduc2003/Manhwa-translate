#!/usr/bin/env python3
"""Per-region report for one page: how flat the fill is and how far its colour sits from the paper."""
import os
import re
import subprocess
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def regions_of(image, ocr, out_dir):
    env = os.environ.copy()
    env["JAVA_TOOL_OPTIONS"] = "-Dregiondebug=1"
    cmd = ["./gradlew", "-q", ":demo:realPage", f"-Pargs={image} {ocr} {out_dir}"]
    raw = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, env=env).stdout
    regions = []
    inside = False
    for line in raw.splitlines():
        if line.startswith("Tất cả vùng:"):
            inside = True
            continue
        if inside:
            match = re.match(r"\s+\[(-?\d+),(-?\d+),(-?\d+),(-?\d+)] lines=(\d+) \"(.*)\"", line)
            if match:
                x0, y0, x1, y1 = (int(match.group(i)) for i in range(1, 5))
                regions.append({"box": (x0, y0, x1, y1), "text": match.group(6)})
            elif line.strip() and not line.startswith(" "):
                inside = False
    return regions, raw


def ring_color(image, box, pad):
    x0, y0, x1, y1 = box
    w, h = image.size
    outer = np.array(
        image.crop((max(0, x0 - pad), max(0, y0 - pad), min(w, x1 + pad), min(h, y1 + pad))),
        dtype=float,
    )
    inner_w = min(w, x1 + pad) - max(0, x0 - pad)
    inner_h = min(h, y1 + pad) - max(0, y0 - pad)
    mask = np.ones((inner_h, inner_w), dtype=bool)
    mask[pad : inner_h - pad, pad : inner_w - pad] = False
    return outer[mask].reshape(-1, 3)


def main():
    image_path, ocr_path, out_dir = sys.argv[1:4]
    regions, raw = regions_of(image_path, ocr_path, out_dir)
    prefix = image_path.rsplit("/", 1)[-1].rsplit(".", 1)[0]
    source = Image.open(f"{out_dir}/{prefix}-source.png").convert("RGB")
    erased = Image.open(f"{out_dir}/{prefix}-erased.png").convert("RGB")
    rows = []
    for region in regions:
        x0, y0, x1, y1 = region["box"]
        before = np.array(source.crop((x0, y0, x1, y1)), dtype=float).reshape(-1, 3)
        after = np.array(erased.crop((x0, y0, x1, y1)), dtype=float).reshape(-1, 3)
        ring = ring_color(source, region["box"], 14)
        if ring.size == 0:
            continue
        paper = np.median(ring.reshape(-1, 3), axis=0)
        fill = np.median(after, axis=0)
        stain = float(np.abs(fill - paper).max())
        rows.append(
            {
                "box": region["box"],
                "text": region["text"][:34],
                "std_before": round(float(before.std(axis=0).mean()), 1),
                "std_after": round(float(after.std(axis=0).mean()), 1),
                "stain": round(stain, 1),
                "paper": [int(v) for v in paper],
                "fill": [int(v) for v in fill],
            }
        )
    rows.sort(key=lambda r: -r["stain"])
    print(f"{prefix}: {len(rows)} regions, worst stains")
    for row in rows[:12]:
        print(
            f"  stain {row['stain']:5.1f} std {row['std_before']:5.1f}->{row['std_after']:5.1f} "
            f"paper {row['paper']} fill {row['fill']} {row['box']} {row['text']!r}"
        )
    stains = [r["stain"] for r in rows]
    if stains:
        print(f"  median stain {np.median(stains):.1f}, over 25: {sum(1 for s in stains if s > 25)}/{len(stains)}")


if __name__ == "__main__":
    main()
