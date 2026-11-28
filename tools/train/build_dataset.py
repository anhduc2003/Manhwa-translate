#!/usr/bin/env python3
"""Labels every page with PP-OCR boxes and cuts them into training tiles.

    python build_dataset.py <pages-dir> <out-dir>

Writes `<out-dir>/labels/<page>.json` (text boxes) and `<out-dir>/<split>.npz` with 384x384 tiles
and their text masks. Chapter 15 and the last page of every chapter are held out for evaluation,
so the metrics are measured on artwork the model never saw.
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

TILE = 384
STRIDE = 192
TEXT_DILATE = 3

ENGINE = RapidOCR()


def ocr_boxes(page, top):
    result, _ = ENGINE(np.array(page))
    boxes = []
    for entry in result or []:
        polygon, text, score = entry[0], entry[1], entry[2]
        if not text.strip():
            continue
        xs = [point[0] for point in polygon]
        ys = [point[1] for point in polygon]
        boxes.append(
            {
                "x0": float(min(xs)),
                "y0": float(min(ys)) + top,
                "x1": float(max(xs)),
                "y1": float(max(ys)) + top,
                "text": text.strip(),
                "score": float(score),
            }
        )
    return boxes


def label_page(path):
    page = Image.open(path).convert("RGB")
    width, height = page.size
    boxes, seen = [], []
    step = 1400 - 200
    for top in range(0, height, step):
        bottom = min(top + 1400, height)
        for box in ocr_boxes(page.crop((0, top, width, bottom)), top):
            center = ((box["x0"] + box["x1"]) / 2, (box["y0"] + box["y1"]) / 2)
            if any(abs(center[0] - sx) < 6 and abs(center[1] - sy) < 6 for sx, sy in seen):
                continue
            seen.append(center)
            boxes.append(box)
        if bottom >= height:
            break
    return {"width": width, "height": height, "boxes": boxes}


def tile_page(page, boxes, seed):
    """Cuts one page into tiles plus a mask, keeping text tiles and a share of empty ones."""
    width, height = page.size
    pixels = np.asarray(page, dtype=np.uint8)
    mask = np.zeros((height, width), dtype=np.uint8)
    for box in boxes:
        x0, y0, x1, y1 = (int(round(box[k])) for k in ("x0", "y0", "x1", "y1"))
        x0, y0 = max(0, x0 - TEXT_DILATE), max(0, y0 - TEXT_DILATE)
        x1, y1 = min(width, x1 + TEXT_DILATE), min(height, y1 + TEXT_DILATE)
        mask[y0:y1, x0:x1] = 255

    images, masks = [], []
    rng = np.random.default_rng(seed)
    for top in range(0, max(1, height - TILE + 1), STRIDE):
        for left in range(0, max(1, width - TILE + 1), STRIDE):
            patch_mask = mask[top : top + TILE, left : left + TILE]
            if patch_mask.shape != (TILE, TILE):
                continue
            has_text = patch_mask.any()
            if not has_text and rng.random() > 0.35:
                continue
            images.append(pixels[top : top + TILE, left : left + TILE])
            masks.append(patch_mask)
    return images, masks


def main():
    pages_dir, out_dir = sys.argv[1], sys.argv[2]
    labels_dir = os.path.join(out_dir, "labels")
    os.makedirs(labels_dir, exist_ok=True)
    names = sorted(name for name in os.listdir(pages_dir) if name.endswith(".jpg"))
    splits = {"train": ([], []), "val": ([], [])}
    for index, name in enumerate(names, start=1):
        page_path = os.path.join(pages_dir, name)
        label_path = os.path.join(labels_dir, name.replace(".jpg", ".json"))
        if os.path.exists(label_path):
            document = json.load(open(label_path))
        else:
            document = label_page(page_path)
            json.dump(document, open(label_path, "w"))
        page = Image.open(page_path).convert("RGB")
        images, masks = tile_page(page, document["boxes"], index)
        # Held out: a whole chapter plus the last page of every chapter.
        held_out = name.startswith("ch15-") or name.endswith("-99.jpg") or name[-6:-4] == "10"
        bucket = "val" if held_out else "train"
        splits[bucket][0].extend(images)
        splits[bucket][1].extend(masks)
        print(
            f"{index}/{len(names)} {name}: {len(document['boxes'])} boxes, {len(images)} tiles "
            f"({bucket})",
            flush=True,
        )

    for split, (images, masks) in splits.items():
        if not images:
            continue
        np.savez_compressed(
            os.path.join(out_dir, f"{split}.npz"),
            images=np.stack(images),
            masks=np.stack(masks).astype(np.uint8),
        )
        print(f"{split}: {len(images)} tiles -> {out_dir}/{split}.npz", flush=True)


if __name__ == "__main__":
    main()
