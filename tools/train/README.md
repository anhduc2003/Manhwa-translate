# Text detector training

A small U-Net (`TextNet`, 117k parameters) that marks lettering on a manhwa page. It runs at
192×192 on tiles of 384×384, so the same graph can run on a phone through ONNX Runtime.

Everything here is offline tooling: the app does not use the model yet.

## Pipeline

```sh
python fetch_pages.py <chapter-url> <pages-dir>      # real pages from the reader
python build_dataset.py <pages-dir> <dataset-dir>    # PP-OCR labels + 384x384 tiles
python train_unet.py <dataset-dir> 10                # CPU, ~10 min/epoch on 4 cores
python eval_detector.py <dataset-dir> <pages-dir> 0.75
python check_extras.py <dataset-dir> <pages-dir> 0.75
python export_onnx.py <dataset-dir>                  # textnet.onnx + textnet_int8.onnx
```

`build_dataset.py` labels each page with PP-OCR and holds out chapter 15 plus the last page of every
chapter, so every number below is measured on artwork the model never saw.

## Measured on 105 pages (10 held out)

| threshold | recall | precision | F1 | pixel IoU |
|-----------|--------|-----------|----|-----------|
| 0.50 | 0.873 | 0.421 | 0.568 | 0.403 |
| 0.60 | 0.850 | 0.442 | 0.582 | 0.422 |
| 0.75 | 0.824 | 0.559 | 0.666 | 0.441 |

Recall counts PP-OCR lines a proposal covers by at least half; precision counts proposals that are
at least a third inside a PP-OCR line. Both are strict: the model merges several lines into one
bubble, which counts against precision even when the merged region is correct.

`check_extras.py` asks the more interesting question — what does the model see that the page-level
OCR pass missed? On the held-out pages at 0.75 it proposes 108 regions with no OCR box; cropping
each and running PP-OCR on the crop returns lettering in 22 of them (20%), including small credits
(`ARTIST: PYEONGHYUNG`, `OR JOIN OUR DISCORD SERVER`) and speech the page pass dropped. The other 86
are artwork, which is why the model is not a drop-in replacement for OCR on its own.

`export_onnx.py` reports 9 ms per tile and 0.34 s for a 720×15000 page on a 4-core cloud CPU
(float32, single-threaded ONNX Runtime); the int8 build is smaller but slower on this machine.
