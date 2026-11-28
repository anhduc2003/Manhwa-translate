#!/usr/bin/env python3
"""Asks whether the detector finds lettering the page-level OCR pass did not report.

    python check_extras.py <dataset-dir> <pages-dir> [threshold]

For every proposal on a held-out page that no PP-OCR box covers, the crop is fed back to PP-OCR on
its own. A crop that comes back with text is lettering the page pass dropped - small print, stylised
SFX, watermarks - which is exactly what the app currently leaves unerased. Crops that come back
empty are places the model would erase artwork for nothing.
"""
import json
import os
import sys

import torch
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

from eval_detector import overlap, predict_page, proposals
from train_unet import TextNet

ENGINE = RapidOCR()


def crop_text(page, box):
    x0, y0, x1, y1 = box
    pad = 6
    crop = page.crop(
        (max(0, x0 - pad), max(0, y0 - pad), min(page.width, x1 + pad), min(page.height, y1 + pad))
    )
    if crop.width < 8 or crop.height < 8:
        return "", 0.0
    if crop.width < 64:
        crop = crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS)
    result, _ = ENGINE(np.array(crop))
    best = ("", 0.0)
    for entry in result or []:
        text, score = entry[1].strip(), float(entry[2])
        if text and score > best[1]:
            best = (text, score)
    return best


def main():
    dataset_dir, pages_dir = sys.argv[1], sys.argv[2]
    threshold = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
    model = TextNet()
    model.load_state_dict(torch.load(os.path.join(dataset_dir, "textnet.pt"), map_location="cpu"))
    model.eval()
    names = sorted(
        name for name in os.listdir(pages_dir) if name.startswith("ch15-") and name.endswith(".jpg")
    )
    with_text = without = 0
    samples = []
    for name in names:
        page = Image.open(os.path.join(pages_dir, name)).convert("RGB")
        labels = json.load(open(os.path.join(dataset_dir, "labels", name.replace(".jpg", ".json"))))
        boxes = [(b["x0"], b["y0"], b["x1"], b["y1"]) for b in labels["boxes"]]
        extras = []
        for proposal in proposals(predict_page(model, page, threshold)):
            area = max(1.0, (proposal[2] - proposal[0]) * (proposal[3] - proposal[1]))
            covered = max((overlap(proposal, box) for box in boxes), default=0.0)
            if covered / area < 0.3:
                extras.append(proposal)
        found = []
        for proposal in extras:
            text, score = crop_text(page, proposal)
            if score >= 0.6 and len(text) >= 2:
                with_text += 1
                found.append(f"{text!r} ({score:.2f})")
            else:
                without += 1
        print(f"{name}: {len(extras)} unmatched proposals, {len(found)} hold text {found[:6]}", flush=True)
        samples.extend(found)
    total = with_text + without
    print(
        f"\n{with_text}/{total} unmatched proposals contain lettering the page pass missed "
        f"({with_text / max(1, total):.0%}); {without} are artwork only"
    )


if __name__ == "__main__":
    main()
