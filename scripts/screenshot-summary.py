#!/usr/bin/env python3
"""Writes one GitHub notice about the screenshot images, compared with the images of the base branch.

The screenshot tests write PNG files to <module>/build/shots. This script:
1. hashes each image (SHA-256, first 10 characters) and writes the list to OUTPUT (one "<path> <hash>" per line;
   the first line is "# commit <sha>");
2. reads the list of the base branch from BASE (CI restores it from the Actions cache), if it exists;
3. writes one notice with the number of images, and the short hash of each new, changed, or removed image.

Usage: python3 scripts/screenshot-summary.py OUTPUT [BASE]
The commit comes from GITHUB_SHA. Exit code: always 0.
"""

import glob
import hashlib
import os
import sys

MAX_LISTED = 40


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def short_hash(path):
    with open(path, "rb") as image:
        return hashlib.sha256(image.read()).hexdigest()[:10]


def read_list(path):
    commit, images = None, {}
    with open(path, encoding="utf-8") as lines:
        for line in lines:
            line = line.strip()
            if line.startswith("# commit "):
                commit = line[len("# commit "):]
            elif line and not line.startswith("#"):
                name, digest = line.rsplit(" ", 1)
                images[name] = digest
    return commit, images


def main():
    if len(sys.argv) < 2:
        print("Usage: screenshot-summary.py OUTPUT [BASE]", file=sys.stderr)
        return 0
    output = sys.argv[1]
    base = sys.argv[2] if len(sys.argv) > 2 else None

    paths = sorted(glob.glob(os.path.join("**", "build", "shots", "**", "*.png"), recursive=True))
    images = {path.replace(os.sep, "/").replace("/build/shots/", ":"): short_hash(path) for path in paths}

    with open(output, "w", encoding="utf-8") as listing:
        listing.write(f"# commit {os.environ.get('GITHUB_SHA', 'local')}\n")
        for name, digest in sorted(images.items()):
            listing.write(f"{name} {digest}\n")

    if not images:
        print("::notice title=Screenshots::No screenshot images. The screenshot tests did not run.")
        return 0

    if not base or not os.path.exists(base):
        print(
            f"::notice title=Screenshots::{len(images)} images. "
            "No list from the base branch to compare with (it comes from the next run on main)."
        )
        return 0

    base_commit, base_images = read_list(base)
    changed = [f"{n} {base_images[n]}->{d}" for n, d in sorted(images.items()) if n in base_images and base_images[n] != d]
    added = [f"{n} {d}" for n, d in sorted(images.items()) if n not in base_images]
    removed = [f"{n} (was {d})" for n, d in sorted(base_images.items()) if n not in images]

    head = (
        f"{len(images)} images, compared with {len(base_images)} on main {(base_commit or 'unknown')[:7]}: "
        f"{len(changed)} changed, {len(added)} new, {len(removed)} removed, "
        f"{len(images) - len(changed) - len(added)} same."
    )
    lines = [head]
    for label, items in (("Changed", changed), ("New", added), ("Removed", removed)):
        if items:
            lines.append(f"{label}:")
            lines.extend(f"  {item}" for item in items[:MAX_LISTED])
            if len(items) > MAX_LISTED:
                lines.append(f"  … and {len(items) - MAX_LISTED} more")
    print(f"::notice title=Screenshots::{escape_data(chr(10).join(lines))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
