#!/usr/bin/env python3
"""Writes the Kotlin compile errors and Gradle's "What went wrong" blocks of a Gradle log as GitHub annotations (T28).

A module that does not compile has no test results, so test-annotations.py has nothing to say about it.
- Each "e: file:///<path>:<line>:<column> <message>" line becomes "::error file=<path>,line=<line>,col=<column>::".
- Each "* What went wrong:" block (up to the next "* " line) becomes one error.
At most 10 of each (GitHub shows at most 10 errors per step). Exit code: always 0.

Usage: python3 scripts/gradle-annotations.py <gradle-output-file>
"""

import os
import re
import sys

MAX_ERRORS = 10
COMPILE_ERROR = re.compile(r"^e: file://(?P<path>/[^:]+):(?P<line>\d+):(?P<col>\d+) (?P<message>.*)$")


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def main():
    if len(sys.argv) != 2 or not os.path.exists(sys.argv[1]):
        return 0
    with open(sys.argv[1], encoding="utf-8", errors="replace") as log:
        lines = log.read().splitlines()
    root = os.environ.get("GITHUB_WORKSPACE", os.getcwd()).rstrip("/") + "/"

    errors, seen = [], set()
    for line in lines:
        match = COMPILE_ERROR.match(line)
        if match and line not in seen:
            seen.add(line)
            errors.append(match)
    for match in errors[:MAX_ERRORS]:
        path = match["path"][len(root):] if match["path"].startswith(root) else match["path"]
        print(
            f"::error file={escape_property(path)},line={match['line']},col={match['col']},"
            f"title=Compile error::{escape_data(match['message'])}"
        )
    if len(errors) > MAX_ERRORS:
        print(f"::error title=Compile errors::{len(errors) - MAX_ERRORS} more compile errors. The log has the full list.")

    blocks, index = [], 0
    while index < len(lines):
        if lines[index].strip() == "* What went wrong:":
            body = []
            index += 1
            while index < len(lines) and not lines[index].startswith("* "):
                if lines[index].strip():
                    body.append(lines[index].rstrip())
                index += 1
            blocks.append("\n".join(body[:20]))
        else:
            index += 1
    for body in blocks[:MAX_ERRORS]:
        print(f"::error title=Gradle: what went wrong::{escape_data(body)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
