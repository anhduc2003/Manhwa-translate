#!/usr/bin/env python3
"""Runs PP-OCR over a tall manhwa strip and writes the box JSON the demo harness reads.

The strip is cut into overlapping tiles first: a 720x15000 page downsampled to the detector's
input size loses the lettering, which is the same reason the app tiles the page before OCR.
"""
import json
import sys

import numpy as np
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

TILE = 1400
OVERLAP = 200
ENGINE = RapidOCR()


def boxes_for_tile(img, top):
    result, _ = ENGINE(np.array(img))
    found = []
    for entry in result or []:
        polygon, text, score = entry[0], entry[1], entry[2]
        xs = [p[0] for p in polygon]
        ys = [p[1] for p in polygon]
        found.append(
            {
                "x0": int(min(xs)),
                "y0": int(min(ys)) + top,
                "x1": int(max(xs)),
                "y1": int(max(ys)) + top,
                "text": text.strip(),
                "score": float(score),
            }
        )
    return found


def main():
    image_path, out_path = sys.argv[1], sys.argv[2]
    page = Image.open(image_path).convert("RGB")
    width, height = page.size
    boxes = []
    seen = []
    step = TILE - OVERLAP
    for top in range(0, height, step):
        bottom = min(top + TILE, height)
        tile = page.crop((0, top, width, bottom))
        for box in boxes_for_tile(tile, top):
            if not box["text"]:
                continue
            # A box seen in two overlapping tiles would be erased twice with a shifted mask.
            center = ((box["x0"] + box["x1"]) / 2, (box["y0"] + box["y1"]) / 2)
            if any(abs(center[0] - sx) < 6 and abs(center[1] - sy) < 6 for sx, sy in seen):
                continue
            seen.append(center)
            boxes.append(box)
        if bottom >= height:
            break
    boxes.sort(key=lambda b: (b["y0"], b["x0"]))
    with open(out_path, "w") as handle:
        json.dump({"width": width, "height": height, "boxes": boxes}, handle, ensure_ascii=False)
    print(f"{image_path}: {len(boxes)} boxes -> {out_path}")


if __name__ == "__main__":
    main()
