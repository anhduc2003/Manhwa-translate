#!/usr/bin/env python3
"""Trains a small U-Net that marks lettering on a manhwa tile.

    python train_unet.py <dataset-dir> [epochs]

The dataset comes from build_dataset.py: 384x384 RGB tiles and the text mask PP-OCR would draw on
them. The network sees the tile at half resolution (192x192) so a CPU can train it and a phone can
run it inside the reader's existing tile loop; the mask it predicts is upsampled back to the tile.
"""
import os
import sys
import time

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset

TILE = 384
SCALE = 2


def downscale(image):
    """Averages SCALExSCALE blocks: (h, w, c) float32 -> (h/SCALE, w/SCALE, c)."""
    height, width, channels = image.shape
    return image.reshape(height // SCALE, SCALE, width // SCALE, SCALE, channels).mean(axis=(1, 3))


def downscale_mask(mask):
    height, width = mask.shape
    blocks = mask.reshape(height // SCALE, SCALE, width // SCALE, SCALE)
    return blocks.max(axis=(1, 3))


class Tiles(Dataset):
    def __init__(self, path, flip=True):
        data = np.load(path)
        self.images = data["images"]
        self.masks = data["masks"]
        self.flip = flip

    def __len__(self):
        return len(self.images)

    def __getitem__(self, index):
        image = downscale(self.images[index].astype(np.float32) / 255.0)
        mask = downscale_mask((self.masks[index] > 127).astype(np.float32))
        if self.flip:
            if np.random.random() < 0.5:
                image, mask = image[:, ::-1], mask[:, ::-1]
            if np.random.random() < 0.3:
                image = np.clip(image * np.random.uniform(0.85, 1.15), 0, 1)
        return (
            torch.from_numpy(np.ascontiguousarray(image.transpose(2, 0, 1))),
            torch.from_numpy(np.ascontiguousarray(mask)).unsqueeze(0),
        )


def block(cin, cout):
    return nn.Sequential(
        nn.Conv2d(cin, cout, 3, padding=1, bias=False),
        nn.BatchNorm2d(cout),
        nn.ReLU(inplace=True),
        nn.Conv2d(cout, cout, 3, padding=1, bias=False),
        nn.BatchNorm2d(cout),
        nn.ReLU(inplace=True),
    )


class TextNet(nn.Module):
    """Three-level U-Net: enough to see a whole bubble, small enough for a phone."""

    def __init__(self):
        super().__init__()
        self.down1 = block(3, 16)
        self.down2 = block(16, 32)
        self.down3 = block(32, 64)
        self.pool = nn.MaxPool2d(2)
        self.up2 = nn.ConvTranspose2d(64, 32, 2, stride=2)
        self.mid2 = block(64, 32)
        self.up1 = nn.ConvTranspose2d(32, 16, 2, stride=2)
        self.mid1 = block(32, 16)
        self.head = nn.Conv2d(16, 1, 1)

    def forward(self, x):
        a = self.down1(x)
        b = self.down2(self.pool(a))
        c = self.down3(self.pool(b))
        y = self.mid2(torch.cat([self.up2(c), b], dim=1))
        y = self.mid1(torch.cat([self.up1(y), a], dim=1))
        return self.head(y)


def iou(pred, target, threshold=0.5):
    hit = ((pred > threshold) & (target > 0.5)).sum().item()
    union = ((pred > threshold) | (target > 0.5)).sum().item()
    return hit / union if union else 1.0


def main():
    data_dir = sys.argv[1]
    epochs = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    torch.manual_seed(0)
    torch.set_num_threads(os.cpu_count() or 4)

    train = DataLoader(Tiles(os.path.join(data_dir, "train.npz")), batch_size=32, shuffle=True)
    val = DataLoader(Tiles(os.path.join(data_dir, "val.npz"), flip=False), batch_size=32)
    print(f"train batches {len(train)}, val batches {len(val)}", flush=True)

    model = TextNet()
    print(f"parameters: {sum(p.numel() for p in model.parameters())}", flush=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-3)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    def loss_fn(logits, target):
        # Dice keeps the mask tight on a page that is mostly background; BCE stabilises the start.
        bce = nn.functional.binary_cross_entropy_with_logits(logits, target)
        prob = torch.sigmoid(logits)
        intersection = (prob * target).sum(dim=(1, 2, 3))
        dice = 1 - (2 * intersection + 1) / (prob.sum(dim=(1, 2, 3)) + target.sum(dim=(1, 2, 3)) + 1)
        return bce + dice.mean()

    best = 0.0
    for epoch in range(1, epochs + 1):
        model.train()
        started, total = time.time(), 0.0
        for step, (images, masks) in enumerate(train, start=1):
            optimizer.zero_grad()
            loss = loss_fn(model(images), masks)
            loss.backward()
            optimizer.step()
            total += loss.item()
            if step % 20 == 0:
                print(f"  epoch {epoch} step {step}/{len(train)} loss {total / step:.4f}", flush=True)
        scheduler.step()

        model.eval()
        scores = []
        with torch.no_grad():
            for images, masks in val:
                scores.append(iou(torch.sigmoid(model(images)), masks))
        score = float(np.mean(scores)) if scores else 0.0
        print(
            f"epoch {epoch}: train loss {total / max(1, len(train)):.4f}, "
            f"val IoU {score:.3f}, {time.time() - started:.0f}s",
            flush=True,
        )
        if score >= best:
            best = score
            torch.save(model.state_dict(), os.path.join(data_dir, "textnet.pt"))
    print(f"best val IoU {best:.3f} -> {data_dir}/textnet.pt")


if __name__ == "__main__":
    main()
