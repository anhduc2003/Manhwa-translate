#!/usr/bin/env python3
"""Cuts training pairs the way the eraser will actually use them.

    python build_pairs.py <pages-dir> <out-dir> [patch] [windows-per-box]

The first attempt at this covered random artwork with blobs, which is not the hole the eraser hands
the model: in the app the hole is lettering — thin strokes inside a caption box or a bubble. This
script finds the real lettering with PP-OCR, masks it exactly the way the eraser does, and cuts
windows out of those regions, so training sees the same holes inference will produce.
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from rapidocr_onnxruntime import RapidOCR
from scipy import ndimage

PATCH = 128
WINDOWS = 3
TOLERANCE = 40
CONTEXT = 24

ENGINE = RapidOCR()


def boxes_of(page):
    """PP-OCR boxes over the page, in overlapping slices so a tall strip stays readable."""
    width, height = page.size
    tile, overlap = 1400, 200
    found, seen = [], []
    step = tile - overlap
    for top in range(0, height, step):
        bottom = min(top + tile, height)
        result, _ = ENGINE(np.array(page.crop((0, top, width, bottom))))
        for entry in result or []:
            polygon, text, score = entry[0], entry[1], entry[2]
            if not text.strip():
                continue
            xs = [point[0] for point in polygon]
            ys = [point[1] for point in polygon]
            box = {
                "x0": float(min(xs)),
                "y0": float(min(ys)) + top,
                "x1": float(max(xs)),
                "y1": float(max(ys)) + top,
                "text": text.strip(),
            }
            centre = ((box["x0"] + box["x1"]) / 2, (box["y0"] + box["y1"]) / 2)
            if any(abs(centre[0] - sx) < 6 and abs(centre[1] - sy) < 6 for sx, sy in seen):
                continue
            seen.append(centre)
            found.append(box)
        if bottom >= height:
            break
    return found


def paper_of(pixels, box, pad=14):
    """Median colour of the ring around the box: what the eraser calls the paper."""
    height, width = pixels.shape[:2]
    left = max(0, int(box[0]) - pad)
    top = max(0, int(box[1]) - pad)
    right = min(width, int(box[2]) + pad)
    bottom = min(height, int(box[3]) + pad)
    outer = pixels[top:bottom, left:right].astype(float)
    mask = np.ones(outer.shape[:2], dtype=bool)
    inner_top = int(box[1]) - top
    inner_left = int(box[0]) - left
    mask[inner_top : inner_top + int(box[3] - box[1]), inner_left : inner_left + int(box[2] - box[0])] = False
    if not mask.any():
        return None
    return np.median(outer[mask].reshape(-1, 3), axis=0)


def ink_mask(pixels, box, paper, tolerance=TOLERANCE):
    """The letters themselves: pixels far from the paper colour, grown a little like the eraser does."""
    distance = np.abs(pixels.astype(float) - paper).max(axis=2)
    mask = np.zeros(distance.shape, dtype=bool)
    x0, y0, x1, y1 = (int(v) for v in box)
    mask[y0:y1, x0:x1] = distance[y0:y1, x0:x1] > tolerance
    return ndimage.binary_dilation(mask, iterations=2)


def pick(rng, low, high, limit):
    """A window start between [low] and [high], kept inside a page of [limit] slack."""
    low = int(np.clip(low, 0, limit))
    high = int(np.clip(high, 0, limit))
    if high < low:
        low, high = high, low
    return int(rng.integers(low, high + 1))


def main():
    pages_dir, out_dir = sys.argv[1], sys.argv[2]
    patch = int(sys.argv[3]) if len(sys.argv) > 3 else PATCH
    windows = int(sys.argv[4]) if len(sys.argv) > 4 else WINDOWS
    os.makedirs(out_dir, exist_ok=True)
    names = sorted(name for name in os.listdir(pages_dir) if name.endswith(".jpg"))
    rng = np.random.default_rng(11)
    splits = {"train": ([], [], []), "val": ([], [], [])}
    for index, name in enumerate(names):
        page = Image.open(os.path.join(pages_dir, name)).convert("RGB")
        pixels = np.asarray(page)
        height, width = pixels.shape[:2]
        bucket = "val" if index % 7 == 0 else "train"
        kept = 0
        for box in boxes_of(page):
            paper = paper_of(pixels, (box["x0"], box["y0"], box["x1"], box["y1"]))
            if paper is None:
                continue
            mask = ink_mask(pixels, (box["x0"], box["y0"], box["x1"], box["y1"]), paper)
            left = int(box["x0"]) - CONTEXT
            top = int(box["y0"]) - CONTEXT
            right = int(box["x1"]) + CONTEXT
            bottom = int(box["y1"]) + CONTEXT
            for _ in range(windows):
                x = pick(rng, left, right - patch, width - patch)
                y = pick(rng, top, bottom - patch, height - patch)
                window_mask = mask[y : y + patch, x : x + patch]
                if window_mask.mean() < 0.01:
                    continue
                splits[bucket][0].append(pixels[y : y + patch, x : x + patch])
                splits[bucket][1].append(window_mask.astype(np.uint8))
                splits[bucket][2].append(paper.astype(np.uint8))
                kept += 1
        print(f"{index + 1}/{len(names)} {name} ({bucket}) {kept} windows", flush=True)

    for split, (images, masks, papers) in splits.items():
        if not images:
            continue
        np.savez_compressed(
            os.path.join(out_dir, f"{split}.npz"),
            images=np.stack(images).astype(np.uint8),
            masks=np.stack(masks).astype(np.uint8),
            papers=np.stack(papers).astype(np.uint8),
        )
        print(f"{split}: {len(images)} windows", flush=True)


if __name__ == "__main__":
    main()
