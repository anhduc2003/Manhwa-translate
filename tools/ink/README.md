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
`build_pairs.py` makes the pairs the other way round: it finds the real lettering with PP-OCR, masks
it exactly the way the eraser does, and cuts windows out of those regions. The network learns to
rebuild what the mask hides, and at run time the mask is the lettering itself.

`build_patches.py` is the first attempt, kept for the record: it covered random artwork with blobs,
which is not the hole the eraser hands the model. Trained that way the model learned the wrong shape
and painted a caption box brown, so the pipeline moved to `build_pairs.py`.

`FillNet` is a three-level U-Net, ~470k weights, 7 input channels (RGB with the hole zeroed, the mask,
and the paper colour on three constant planes) and 3 output channels. Its answer is **the paper colour
plus what the network adds**, and the last layer starts at zero, so an untrained network already
paints the right colour and training only sharpens it. Trained on 80 real pages (6 718 windows, a
seventh of the pages held out) for 14 epochs, the best epoch reaches a hole L1 of **0.130**.

The eraser then pins the fill's colour to the background around the hole, which removes the small
brightness bias a model trained on thousands of regions always picks up.

## Measured against the app's eraser

`eval_fill.py` fills the same 157 OCR regions on four real pages twice — once through the demo
harness, which runs the app's own `TextEraser`, and once through the model — then scores both the same
way: how far the filled area's median colour sits from the paper ring around the region.

| hole the model was given | median stain | regions over 25/255 |
|---|---|---|
| the whole box | 2.0 | 35 / 157 |
| only the lettering pixels | **1.0** | **5 / 157** |
| app's eraser, same regions | 5.0 | 19 / 157 |

The first row is the unfair test: painting a whole box throws away artwork the model never had to
touch. Limited to the lettering — which is how it runs — and with the colour pinned to the background,
it wins on both counts. The gains are largest exactly where the surface fit fails: the `OMEGASCANS.ORG`
watermark 107 → 16, `HuMP` 53 → 1, `品` 43 → 8, and small text over dark artwork on the credits page
69 → 29.

The case that started this: four caption boxes on a dark teal background where the blob-trained model
painted brown. The current model, colour pinned, comes back at 0–2/255 from the background on all
four.

`export_onnx.py` writes `models/fillnet.onnx` (1 035 KB, checked against PyTorch to 1.6e-06) and a
quantised `fillnet_int8.onnx` (343 KB).
