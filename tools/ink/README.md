# Background fill training

A small U-Net (`FillNet`, ~250k parameters) that rebuilds what sat behind erased lettering. It is the
counterpart of `tools/train`: that one finds the lettering, this one puts the background back.

Everything here is offline tooling: the app still fills with its own surface fit.

## Pipeline

```sh
python fetch_pages.py pages ch17.html ch18.html      # or any chapter page saved as html
python build_patches.py pages data 128 60            # 128x128 pairs, 60 per page
python train_inpaint.py data 20                      # CPU, ~2.5 min/epoch on 4 cores
python eval_fill.py test boxes data 16 ink           # vs the app's own eraser
python export_onnx.py data                           # fillnet.onnx
```

## Why the pairs are synthetic

A real page never shows what sat behind its lettering, so there is nothing to supervise a fill with.
`build_patches.py` makes the pairs the other way round: it takes a patch of real artwork and covers
part of it with strokes and blobs roughly the shape of lettering. The network learns to rebuild what
the cover hides, and at run time the cover is the lettering. Training on 80 pages (4 080 patches) and
validating on a held-out seventh of them, the best epoch reaches a hole L1 of **0.052** (~13/255).

## Measured against the app's eraser

`eval_fill.py` fills the same 157 OCR regions on four real pages twice — once through the demo
harness, which runs the app's own `TextEraser`, and once through the model — then scores both the same
way: how far the filled area's median colour sits from the paper ring around the region.

| hole the model was given | median stain | regions over 25/255 |
|---|---|---|
| the whole box | 2.0 | 35 / 157 |
| only the lettering pixels | **1.0** | **13 / 157** |
| app's eraser, same regions | 5.0 | 19 / 157 |

The first row is the unfair test: painting a whole box throws away artwork the model never had to
touch. Limited to the lettering — which is how it would run — it wins on both counts. The gains are
largest exactly where the surface fit fails: the `OMEGASCANS.ORG` watermark goes from 107 to 16 on one
page and from 19 to 83 on another, `HuMP` from 53 to 1, `品` from 43 to 8, and small text over dark
artwork on the credits page from 69 to 29.

Two regions still get worse (`哥` 20 → 50, one watermark 19 → 83), and the worst region on the hardest
page stays bad for both (169 → 128), so this is an improvement, not a solution.

`export_onnx.py` writes `models/fillnet.onnx` (1 035 KB, checked against PyTorch to 1.6e-06) and a
quantised `fillnet_int8.onnx` (343 KB).
