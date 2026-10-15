#!/usr/bin/env python3
"""The screenshot images and their recorded copies (T28).

The screenshot tests write PNG files to <module>/build/shots. The recorded images (the reference) are in git, in
<module>/screenshots. The images are the same on each run, byte for byte, so a compare of the bytes is enough.

  check   Compares each image with its recorded copy. Writes one ::error:: for each changed, new, or removed image
          (module and image name), and one notice with the totals. Exit code 1 when an image differs.
  record  Copies the images to <module>/screenshots, and removes recorded images that no test writes any more.
          Run it after the screenshot tests, when a change of the screens is intended, and commit the result.

Usage: python3 scripts/screenshots.py check|record
"""

import filecmp
import glob
import os
import shutil
import sys

# GitHub shows at most 10 errors per step. The notice lists all.
MAX_ERRORS = 10


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def modules():
    """The modules with screenshot tests: those with images now, or with recorded images."""
    found = {os.path.dirname(os.path.dirname(p)) for p in glob.glob(os.path.join("**", "build", "shots"), recursive=True)}
    found |= {os.path.dirname(p) for p in glob.glob(os.path.join("**", "screenshots"), recursive=True) if "build" not in p.split(os.sep)}
    return sorted(m for m in found if m and not m.startswith("."))


def images(folder):
    return {os.path.relpath(p, folder) for p in glob.glob(os.path.join(folder, "**", "*.png"), recursive=True)}


def check():
    changed, added, removed, same = [], [], [], 0
    for module in modules():
        shots, recorded = os.path.join(module, "build", "shots"), os.path.join(module, "screenshots")
        now, before = images(shots), images(recorded)
        for name in sorted(now & before):
            if filecmp.cmp(os.path.join(shots, name), os.path.join(recorded, name), shallow=False):
                same += 1
            else:
                changed.append(f"{module}:{name}")
        added += [f"{module}:{name}" for name in sorted(now - before)]
        removed += [f"{module}:{name}" for name in sorted(before - now)]

    if same + len(changed) + len(added) == 0:
        print("::error title=Screenshots::No screenshot images. The screenshot tests did not run.")
        return 1

    problems = [("Changed screenshot", n, "differs from the recorded image") for n in changed]
    problems += [("New screenshot", n, "has no recorded image") for n in added]
    problems += [("Removed screenshot", n, "is recorded, but no test writes it") for n in removed]
    for title, name, text in problems[:MAX_ERRORS]:
        print(f"::error title={title}::{name} {text}. If the change is intended, record the images (README, Screenshots).")

    lines = [f"{same + len(changed) + len(added)} images: {same} same, {len(changed)} changed, {len(added)} new, {len(removed)} removed."]
    for label, items in (("Changed", changed), ("New", added), ("Removed", removed)):
        if items:
            lines.append(f"{label}:")
            lines.extend(f"  {item}" for item in items)
    print(f"::notice title=Screenshots::{escape_data(chr(10).join(lines))}")
    print("\n".join(lines))
    return 1 if problems else 0


def record():
    total = 0
    for module in modules():
        shots, recorded = os.path.join(module, "build", "shots"), os.path.join(module, "screenshots")
        now, before = images(shots), images(recorded)
        if not now:
            print(f"{module}: no images in {shots}; nothing changed (run the tests first)")
            continue
        for name in sorted(now):
            target = os.path.join(recorded, name)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(os.path.join(shots, name), target)
        for name in sorted(before - now):
            os.remove(os.path.join(recorded, name))
            print(f"{module}: removed {name}")
        total += len(now)
    print(f"{total} images recorded. Look at the changes (git diff --stat), then commit them.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("check", "record"):
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(check() if sys.argv[1] == "check" else record())
