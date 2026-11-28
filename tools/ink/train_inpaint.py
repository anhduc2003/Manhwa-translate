#!/usr/bin/env python3
"""Trains the model that rebuilds the background behind erased lettering.

    python train_inpaint.py <data-dir> [epochs]

The pairs come from build_patches.py: a patch of real artwork and a mask that hides part of it. The
network sees the patch with the hidden part zeroed plus the mask, and is asked for the whole patch
back, scored mostly on what it had to invent. Small enough (about 250k weights) to run on a phone.
"""
import os
import sys
import time

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset

PATCH = 128


class Pairs(Dataset):
    def __init__(self, path, flip=True):
        data = np.load(path)
        self.images = data["images"]
        self.masks = data["masks"]
        self.papers = data["papers"]
        self.flip = flip

    def __len__(self):
        return len(self.images)

    def __getitem__(self, index):
        image = self.images[index].astype(np.float32) / 255.0
        mask = self.masks[index].astype(np.float32)
        paper = self.papers[index].astype(np.float32) / 255.0
        if self.flip:
            if np.random.random() < 0.5:
                image, mask = image[:, ::-1], mask[:, ::-1]
            if np.random.random() < 0.5:
                image, mask = image[::-1], mask[::-1]
            if np.random.random() < 0.3:
                image = np.clip(image * np.random.uniform(0.9, 1.1), 0, 1)
        hidden = image * (1.0 - mask)[:, :, None]
        # The paper colour rides along as three constant planes: the network then never has to guess
        # which colour the hole should be, only how the background varies across it.
        planes = np.broadcast_to(paper.reshape(1, 1, 3), image.shape)
        stacked = np.concatenate([hidden, mask[:, :, None], planes], axis=2)
        return (
            torch.from_numpy(np.ascontiguousarray(stacked.transpose(2, 0, 1))),
            torch.from_numpy(np.ascontiguousarray(image.transpose(2, 0, 1))),
            torch.from_numpy(np.ascontiguousarray(mask)),
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


class FillNet(nn.Module):
    """Three-level U-Net: sees the patch, the hole, and enough context to continue a gradient."""

    def __init__(self, base=32):
        super().__init__()
        self.down1 = block(7, base)
        self.down2 = block(base, base * 2)
        self.down3 = block(base * 2, base * 4)
        self.pool = nn.MaxPool2d(2)
        self.up2 = nn.ConvTranspose2d(base * 4, base * 2, 2, stride=2)
        self.mid2 = block(base * 4, base * 2)
        self.up1 = nn.ConvTranspose2d(base * 2, base, 2, stride=2)
        self.mid1 = block(base * 2, base)
        self.head = nn.Conv2d(base, 3, 1)

    def forward(self, x):
        a = self.down1(x)
        b = self.down2(self.pool(a))
        c = self.down3(self.pool(b))
        y = self.mid2(torch.cat([self.up2(c), b], dim=1))
        y = self.mid1(torch.cat([self.up1(y), a], dim=1))
        # The answer is the paper colour plus whatever the network adds to it, so even an untrained
        # network paints the right colour and training only sharpens it. The last layer starts at
        # zero, which makes the starting point exactly the flat fill it has to beat.
        return torch.clamp(x[:, 4:7] + self.head(y), 0.0, 1.0)


def gradient(image):
    """Differences along each axis: keeps edges from turning into mush."""
    return (image[:, :, 1:, :] - image[:, :, :-1, :]).abs().mean() + (
        image[:, :, :, 1:] - image[:, :, :, :-1]
    ).abs().mean()


def main():
    data_dir = sys.argv[1]
    epochs = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    torch.manual_seed(0)
    torch.set_num_threads(os.cpu_count() or 4)

    train = DataLoader(Pairs(os.path.join(data_dir, "train.npz")), batch_size=8, shuffle=True)
    val = DataLoader(Pairs(os.path.join(data_dir, "val.npz"), flip=False), batch_size=8)
    print(f"train batches {len(train)}, val batches {len(val)}", flush=True)

    model = FillNet()
    nn.init.zeros_(model.head.weight)
    nn.init.zeros_(model.head.bias)
    print(f"parameters: {sum(p.numel() for p in model.parameters())}", flush=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    def loss_fn(pred, target, mask):
        hole = (pred - target).abs().mean(dim=1)
        inside = (hole * mask).sum() / (mask.sum() + 1e-6)
        overall = hole.mean()
        # A hole that comes back the wrong colour is the failure people actually see, so the mean
        # colour of the filled area is scored too, not just its pixels.
        weight = mask.unsqueeze(1)
        count = weight.sum(dim=(2, 3)).clamp(min=1e-6)
        pred_mean = (pred * weight).sum(dim=(2, 3)) / count
        target_mean = (target * weight).sum(dim=(2, 3)) / count
        colour = (pred_mean - target_mean).abs().mean()
        return inside + 0.3 * overall + 0.2 * gradient(pred) + 0.5 * colour, inside, overall

    best = float("inf")
    for epoch in range(1, epochs + 1):
        model.train()
        started, total = time.time(), 0.0
        for step, (inputs, targets, masks) in enumerate(train, start=1):
            optimizer.zero_grad()
            loss, inside, overall = loss_fn(model(inputs), targets, masks)
            loss.backward()
            optimizer.step()
            total += float(inside)
            if step % 40 == 0:
                print(
                    f"  epoch {epoch} step {step}/{len(train)} hole L1 {total / step:.4f} "
                    f"all {float(overall):.4f}",
                    flush=True,
                )
        scheduler.step()

        model.eval()
        scores = []
        with torch.no_grad():
            for inputs, targets, masks in val:
                _, inside, _ = loss_fn(model(inputs), targets, masks)
                scores.append(float(inside))
        score = float(np.mean(scores)) if scores else 0.0
        print(
            f"epoch {epoch}: train hole L1 {total / max(1, len(train)):.4f}, "
            f"val hole L1 {score:.4f}, {time.time() - started:.0f}s",
            flush=True,
        )
        if score <= best:
            best = score
            torch.save(model.state_dict(), os.path.join(data_dir, "fillnet.pt"))
    print(f"best val hole L1 {best:.4f} -> {data_dir}/fillnet.pt")


if __name__ == "__main__":
    main()
