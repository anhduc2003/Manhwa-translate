#!/usr/bin/env python3
"""Scores the trained detector against PP-OCR boxes on pages it never saw.

    python eval_detector.py <dataset-dir> <pages-dir> [threshold]

Runs the model over a held-out page tile by tile, joins the tiles into one heat map, cuts it into
proposals and compares those with the PP-OCR boxes: how many real text lines the model found
(recall), how many of its proposals landed on text (precision), and pixel IoU against the boxes.
"""
import json
import os
import sys

import numpy as np
import torch
from PIL import Image
from scipy import ndimage

from train_unet import SCALE, TILE, TextNet, downscale

STRIDE = 192


def predict_page(model, page, threshold):
    width, height = page.size
    pixels = np.asarray(page, dtype=np.float32) / 255.0
    heat = np.zeros((height, width), dtype=np.float32)
    for top in range(0, max(1, height - TILE + 1), STRIDE):
        for left in range(0, max(1, width - TILE + 1), STRIDE):
            patch = pixels[top : top + TILE, left : left + TILE]
            if patch.shape[:2] != (TILE, TILE):
                continue
            small = downscale(patch)
            tensor = torch.from_numpy(small.transpose(2, 0, 1)).unsqueeze(0)
            with torch.no_grad():
                prob = torch.sigmoid(model(tensor))[0, 0].numpy()
            prob = np.repeat(np.repeat(prob, SCALE, axis=0), SCALE, axis=1)
            heat[top : top + TILE, left : left + TILE] = np.maximum(
                heat[top : top + TILE, left : left + TILE], prob
            )
    return heat > threshold


def proposals(mask, min_area=1200):
    labels, count = ndimage.label(mask)
    boxes = []
    for index in range(1, count + 1):
        ys, xs = np.where(labels == index)
        if len(xs) < min_area:
            continue
        boxes.append((xs.min(), ys.min(), xs.max(), ys.max()))
    return boxes


def overlap(a, b):
    x0, y0 = max(a[0], b[0]), max(a[1], b[1])
    x1, y1 = min(a[2], b[2]), min(a[3], b[3])
    if x1 <= x0 or y1 <= y0:
        return 0.0
    return (x1 - x0) * (y1 - y0)


def main():
    dataset_dir, pages_dir = sys.argv[1], sys.argv[2]
    threshold = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
    model = TextNet()
    model.load_state_dict(torch.load(os.path.join(dataset_dir, "textnet.pt"), map_location="cpu"))
    model.eval()
    names = sorted(
        name for name in os.listdir(pages_dir) if name.startswith("ch15-") and name.endswith(".jpg")
    )
    total_hit = total_ocr = total_prop = total_good = 0
    ious = []
    for name in names:
        page = Image.open(os.path.join(pages_dir, name)).convert("RGB")
        labels = json.load(open(os.path.join(dataset_dir, "labels", name.replace(".jpg", ".json"))))
        predicted = predict_page(model, page, threshold)
        boxes = proposals(predicted)
        hits = 0
        for box in labels["boxes"]:
            rect = (box["x0"], box["y0"], box["x1"], box["y1"])
            area = max(1.0, (rect[2] - rect[0]) * (rect[3] - rect[1]))
            covered = max((overlap(rect, proposal) for proposal in boxes), default=0.0)
            if covered / area >= 0.5:
                hits += 1
        good = 0
        for proposal in boxes:
            area = max(1.0, (proposal[2] - proposal[0]) * (proposal[3] - proposal[1]))
            covered = max(
                (overlap(proposal, (b["x0"], b["y0"], b["x1"], b["y1"])) for b in labels["boxes"]),
                default=0.0,
            )
            if covered / area >= 0.3:
                good += 1
        truth = np.zeros(predicted.shape, dtype=bool)
        for box in labels["boxes"]:
            truth[int(box["y0"]) : int(box["y1"]), int(box["x0"]) : int(box["x1"])] = True
        union = (predicted | truth).sum()
        ious.append(float((predicted & truth).sum()) / union if union else 1.0)
        total_hit += hits
        total_ocr += len(labels["boxes"])
        total_prop += len(boxes)
        total_good += good
        print(
            f"{name}: {hits}/{len(labels['boxes'])} boxes found, {good}/{len(boxes)} proposals real, "
            f"IoU {ious[-1]:.3f}",
            flush=True,
        )
    recall = total_hit / max(1, total_ocr)
    precision = total_good / max(1, total_prop)
    f1 = 2 * recall * precision / max(1e-6, recall + precision)
    print(
        f"\nrecall {recall:.3f} ({total_hit}/{total_ocr})  precision {precision:.3f} "
        f"({total_good}/{total_prop})  F1 {f1:.3f}  pixel IoU {np.mean(ious):.3f}"
    )


if __name__ == "__main__":
    main()
