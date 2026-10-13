#!/usr/bin/env python3
"""Probe only (T12): small JPEG copies of some screenshots, as base64 notices, to look at them from the task session."""
import base64, glob, io, sys
from PIL import Image

for name in sys.argv[1:]:
    paths = glob.glob(f'**/build/shots/{name}.png', recursive=True)
    if not paths:
        print(f'::notice title=Image {name}::missing')
        continue
    image = Image.open(paths[0]).convert('RGB')
    width = 360
    image = image.resize((width, int(image.height * width / image.width)))
    for quality in (60, 45, 30, 20):
        out = io.BytesIO()
        image.save(out, 'JPEG', quality=quality)
        data = base64.b64encode(out.getvalue()).decode()
        if len(data) < 60000:
            break
    print(f'::notice title=Image {name}::{data}')
