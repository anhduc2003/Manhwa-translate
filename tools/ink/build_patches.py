#!/usr/bin/env python3
"""Cuts training pairs for the background-fill model out of real pages.

    python build_patches.py <pages-dir> <out-dir> [patch] [per-page]

A page never shows what sat behind its lettering, so the pairs are made the other way round: take a
patch of real artwork and cover part of it with strokes and blobs that look like lettering. The
network learns to rebuild what the cover hides, and at run time the cover is the lettering itself.
Patches are split by page, so the validation pages were never seen.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

PATCH = 128
PER_PAGE = 60


def mask_for(size, rng):
    """Strokes and blobs roughly the shape of lettering: thick, clustered, never the whole patch."""
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    clusters = rng.integers(1, 5)
    for _ in range(int(clusters)):
        centre_x = rng.integers(size // 6, size - size // 6)
        centre_y = rng.integers(size // 6, size - size // 6)
        width = rng.integers(24, size // 2)
        height = rng.integers(12, size // 3)
        left = int(np.clip(centre_x - width // 2, 0, size - 2))
        top = int(np.clip(centre_y - height // 2, 0, size - 2))
        right = int(np.clip(left + width, left + 2, size))
        bottom = int(np.clip(top + height, top + 2, size))
        draw.rectangle([left, top, right, bottom], fill=255)
        strokes = rng.integers(2, 7)
        for _ in range(int(strokes)):
            stroke_width = int(rng.integers(3, 9))
            x0 = int(rng.integers(left, right))
            y0 = int(rng.integers(top, bottom))
            x1 = int(np.clip(x0 + rng.integers(-width, width), 0, size))
            y1 = int(np.clip(y0 + rng.integers(-height, height), 0, size))
            draw.line([x0, y0, x1, y1], fill=255, width=stroke_width)
    values = np.asarray(mask, dtype=np.uint8)
    return (values > 127).astype(np.uint8)


def main():
    pages_dir, out_dir = sys.argv[1], sys.argv[2]
    patch = int(sys.argv[3]) if len(sys.argv) > 3 else PATCH
    per_page = int(sys.argv[4]) if len(sys.argv) > 4 else PER_PAGE
    os.makedirs(out_dir, exist_ok=True)
    names = sorted(name for name in os.listdir(pages_dir) if name.endswith(".jpg"))
    rng = np.random.default_rng(7)
    splits = {"train": ([], []), "val": ([], [])}
    for index, name in enumerate(names):
        page = Image.open(os.path.join(pages_dir, name)).convert("RGB")
        width, height = page.size
        if width < patch or height < patch:
            continue
        pixels = np.asarray(page)
        bucket = "val" if index % 7 == 0 else "train"
        for _ in range(per_page):
            left = int(rng.integers(0, width - patch))
            top = int(rng.integers(0, height - patch))
            splits[bucket][0].append(pixels[top : top + patch, left : left + patch])
            splits[bucket][1].append(mask_for(patch, rng))
        print(f"{index + 1}/{len(names)} {name} ({bucket})", flush=True)

    for split, (images, masks) in splits.items():
        if not images:
            continue
        np.savez_compressed(
            os.path.join(out_dir, f"{split}.npz"),
            images=np.stack(images).astype(np.uint8),
            masks=np.stack(masks).astype(np.uint8),
        )
        print(f"{split}: {len(images)} patches", flush=True)


if __name__ == "__main__":
    main()
