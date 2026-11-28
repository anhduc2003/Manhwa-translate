#!/usr/bin/env python3
"""Exports the background-fill model to ONNX for the phone.

    python export_onnx.py <data-dir>

Writes `fillnet.onnx`: input `patch` (batch, 7, H, W) — RGB with the hole zeroed, the mask, and the
paper colour on three constant planes — and output `fill` (batch, 3, H, W) in 0..1, which already
includes the paper colour the graph adds back. The graph is fully convolutional, so a phone can hand it
a whole region crop instead of the 128x128 patch it was trained on.
"""
import os
import sys

import numpy as np
import onnx
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic

from train_inpaint import FillNet


def main():
    data_dir = sys.argv[1]
    model = FillNet()
    model.load_state_dict(torch.load(os.path.join(data_dir, "fillnet.pt"), map_location="cpu"))
    model.eval()
    fp32 = os.path.join(data_dir, "fillnet.onnx")
    torch.onnx.export(
        model,
        torch.zeros(1, 7, 128, 128),
        fp32,
        input_names=["patch"],
        output_names=["fill"],
        opset_version=17,
        dynamic_axes={"patch": {0: "batch", 2: "height", 3: "width"}, "fill": {0: "batch", 2: "height", 3: "width"}},
    )
    graph = onnx.load(fp32)
    onnx.external_data_helper.convert_model_from_external_data(graph)
    onnx.save_model(graph, fp32)
    external = fp32 + ".data"
    if os.path.exists(external):
        os.remove(external)

    int8 = os.path.join(data_dir, "fillnet_int8.onnx")
    quantize_dynamic(fp32, int8, weight_type=QuantType.QInt8)
    for path in (fp32, int8):
        print(f"{os.path.basename(path)}: {os.path.getsize(path) / 1024:.0f} KB", flush=True)

    import onnxruntime as ort

    session = ort.InferenceSession(fp32, providers=["CPUExecutionProvider"])
    sample = np.random.rand(1, 4, 128, 128).astype(np.float32)
    with torch.no_grad():
        expected = model(torch.from_numpy(sample)).numpy()
    got = session.run(None, {"patch": sample})[0]
    print(f"max difference against PyTorch: {float(np.abs(got - expected).max()):.2e}")


if __name__ == "__main__":
    main()
