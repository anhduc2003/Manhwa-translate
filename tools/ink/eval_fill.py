#!/usr/bin/env python3
"""Compares the trained background fill with the app's own eraser, region by region.

    python eval_fill.py <pages-dir> <boxes-json-dir> <model-dir> [padding]

For every OCR box the page is filled twice: once by the app (through the demo harness, which writes
`<page>-erased.png`) and once by the model, which covers the box and rebuilds it. Both fills are then
scored the same way — how far the filled area's median colour sits from the paper ring around the
region, and how flat the fill is — so the numbers are comparable.
"""
import json
import os
import subprocess
import sys

import numpy as np
import torch
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from train_inpaint import FillNet  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def app_erase(page_path, boxes_path, out_dir):
    env = os.environ.copy()
    env["JAVA_TOOL_OPTIONS"] = "-Dregiondebug=1"
    subprocess.run(
        ["./gradlew", "-q", ":demo:realPage", f"-Pargs={page_path} {boxes_path} {out_dir}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env=env,
        check=False,
    )
    prefix = os.path.basename(page_path).rsplit(".", 1)[0]
    erased = os.path.join(out_dir, f"{prefix}-erased.png")
    source = os.path.join(out_dir, f"{prefix}-source.png")
    if not os.path.exists(erased):
        return None, None
    return Image.open(source).convert("RGB"), Image.open(erased).convert("RGB")


def model_erase(page, boxes, model, patch=128, padding=16):
    """Covers each box and lets the model rebuild it, one patch of context around the box."""
    filled = page.copy()
    for box in boxes:
        x0, y0, x1, y1 = (int(round(box[key])) for key in ("x0", "y0", "x1", "y1"))
        left = max(0, x0 - padding)
        top = max(0, y0 - padding)
        right = min(page.width, x1 + padding)
        bottom = min(page.height, y1 + padding)
        crop = np.asarray(page.crop((left, top, right, bottom)), dtype=np.float32) / 255.0
        mask = np.zeros(crop.shape[:2], dtype=np.float32)
        mask[y0 - top : y1 - top, x0 - left : x1 - left] = 1.0
        # The model pools twice, so the canvas has to divide by four; pad and cut the answer back out.
        size = max(patch, crop.shape[0], crop.shape[1])
        size += (-size) % 4
        canvas = np.zeros((size, size, 3), dtype=np.float32)
        canvas_mask = np.zeros((size, size), dtype=np.float32)
        canvas[: crop.shape[0], : crop.shape[1]] = crop
        canvas_mask[: mask.shape[0], : mask.shape[1]] = mask
        stacked = np.concatenate([canvas * (1 - canvas_mask)[:, :, None], canvas_mask[:, :, None]], axis=2)
        tensor = torch.from_numpy(np.ascontiguousarray(stacked.transpose(2, 0, 1))).unsqueeze(0)
        with torch.no_grad():
            out = model(tensor)[0].numpy().transpose(1, 2, 0)
        rebuilt = out[: crop.shape[0], : crop.shape[1]]
        patch_image = Image.fromarray((np.clip(rebuilt, 0, 1) * 255).astype(np.uint8))
        filled.paste(patch_image, (left, top))
    return filled


def ring_colour(image, box, pad=14):
    x0, y0, x1, y1 = box
    outer = np.asarray(
        image.crop((max(0, x0 - pad), max(0, y0 - pad), min(image.width, x1 + pad), min(image.height, y1 + pad))),
        dtype=float,
    )
    height, width = outer.shape[:2]
    mask = np.ones((height, width), dtype=bool)
    mask[pad : height - pad, pad : width - pad] = False
    return outer[mask].reshape(-1, 3)


def score(source, filled, box):
    x0, y0, x1, y1 = (int(v) for v in box)
    ring = ring_colour(source, (x0, y0, x1, y1))
    if ring.size == 0:
        return None
    paper = np.median(ring.reshape(-1, 3), axis=0)
    area = np.asarray(filled.crop((x0, y0, x1, y1)), dtype=float).reshape(-1, 3)
    stain = float(np.abs(np.median(area, axis=0) - paper).max())
    spread = float(area.std(axis=0).mean())
    return stain, spread


def main():
    pages_dir, boxes_dir, model_dir = sys.argv[1:4]
    padding = int(sys.argv[4]) if len(sys.argv) > 4 else 16
    model = FillNet()
    model.load_state_dict(torch.load(os.path.join(model_dir, "fillnet.pt"), map_location="cpu"))
    model.eval()

    out_dir = os.path.join(model_dir, "demo")
    os.makedirs(out_dir, exist_ok=True)
    app_scores, model_scores = [], []
    for name in sorted(os.listdir(pages_dir)):
        if not name.endswith(".jpg"):
            continue
        page_path = os.path.join(pages_dir, name)
        boxes_path = os.path.join(boxes_dir, name.rsplit(".", 1)[0] + ".json")
        if not os.path.exists(boxes_path):
            continue
        boxes = json.load(open(boxes_path))["boxes"]
        source, erased = app_erase(page_path, boxes_path, out_dir)
        if source is None:
            print(f"{name}: the demo harness produced nothing", flush=True)
            continue
        rebuilt = model_erase(source, boxes, model, padding=padding)
        rows = []
        for box in boxes:
            app = score(source, erased, (box["x0"], box["y0"], box["x1"], box["y1"]))
            mine = score(source, rebuilt, (box["x0"], box["y0"], box["x1"], box["y1"]))
            if app is None or mine is None:
                continue
            app_scores.append(app[0])
            model_scores.append(mine[0])
            rows.append((app[0], mine[0], box["text"][:26]))
        rows.sort(key=lambda row: -row[0])
        print(f"{name}: {len(rows)} regions", flush=True)
        for app, mine, text in rows[:6]:
            print(f"  app {app:6.1f}  model {mine:6.1f}  {text!r}", flush=True)

    app_all = np.array(app_scores)
    model_all = np.array(model_scores)
    print(
        f"\nstain median  app {np.median(app_all):.1f}  model {np.median(model_all):.1f}\n"
        f"stain over 25 app {int((app_all > 25).sum())}/{len(app_all)}  "
        f"model {int((model_all > 25).sum())}/{len(model_all)}"
    )


if __name__ == "__main__":
    main()
