#!/usr/bin/env python3
"""Probe only (T12): smaller copies of the new and changed screenshots, in probe-shots/, to look at them."""
import glob, os
from PIL import Image

os.makedirs('probe-shots', exist_ok=True)
names = [p for p in glob.glob('feature/documents/build/shots/*.png')]
for name in ('capture', 'modules', 'tabs-modules', 'today', 'today-modules', 'tablet-today'):
    names += glob.glob(f'app/build/shots/{name}.png')
for path in names:
    image = Image.open(path).convert('RGB')
    width = 540
    image = image.resize((width, int(image.height * width / image.width)))
    image.save(os.path.join('probe-shots', os.path.basename(path).replace('.png', '.jpg')), 'JPEG', quality=70)
