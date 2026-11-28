#!/usr/bin/env python3
"""Exports the trained detector to ONNX, quantises it, and times it.

    python export_onnx.py <dataset-dir> [tiles-dir]

The phone runs the ONNX graph through ONNX Runtime; this script writes `textnet.onnx` (float32) and
`textnet_int8.onnx` (dynamic-quantised, roughly a quarter of the size) and prints how long each takes
per 192x192 tile on this machine, which is a rough stand-in for a phone CPU.
"""
import os
import sys
import time

import numpy as np
import onnx
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic

from train_unet import TextNet


def bench(path, runs=20):
    import onnxruntime as ort

    session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    sample = np.random.rand(1, 3, 192, 192).astype(np.float32)
    name = session.get_inputs()[0].name
    for _ in range(3):
        session.run(None, {name: sample})
    started = time.time()
    for _ in range(runs):
        session.run(None, {name: sample})
    return (time.time() - started) / runs * 1000


def main():
    data_dir = sys.argv[1]
    model = TextNet()
    model.load_state_dict(torch.load(os.path.join(data_dir, "textnet.pt"), map_location="cpu"))
    model.eval()
    fp32 = os.path.join(data_dir, "textnet.onnx")
    torch.onnx.export(
        model,
        torch.zeros(1, 3, 192, 192),
        fp32,
        input_names=["tile"],
        output_names=["logits"],
        opset_version=17,
        dynamic_axes={"tile": {0: "batch"}, "logits": {0: "batch"}},
    )
    # The exporter writes the weights beside the graph; keep one self-contained file for the phone.
    graph = onnx.load(fp32)
    onnx.external_data_helper.convert_model_from_external_data(graph)
    onnx.save_model(graph, fp32)
    external = fp32 + ".data"
    if os.path.exists(external):
        os.remove(external)

    int8 = os.path.join(data_dir, "textnet_int8.onnx")
    quantize_dynamic(fp32, int8, weight_type=QuantType.QInt8)

    for path in (fp32, int8):
        size = os.path.getsize(path) / 1024
        print(f"{os.path.basename(path)}: {size:.0f} KB, {bench(path):.1f} ms/tile", flush=True)

    # A 720x15000 strip is ~38 tiles at stride 192; print what one page costs.
    print("one 720x15000 page = 38 tiles")
    for path in (fp32, int8):
        print(f"  {os.path.basename(path)}: {bench(path) * 38 / 1000:.2f} s/page")


if __name__ == "__main__":
    main()
