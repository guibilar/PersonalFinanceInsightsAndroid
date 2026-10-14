#!/usr/bin/env python3
"""Writes the JUnit XML results of all Gradle modules as GitHub annotations.

One notice per module with its totals, and one error per failed test with the first lines of its message.
GitHub shows at most 10 notices and 10 errors per step, so the script writes at most MAX_ERRORS test errors,
and then one error with the number of the other failures.

Usage: python3 scripts/test-annotations.py [root]   (root defaults to the current folder)
Exit code: always 0. The Gradle step fails the job, not this script.
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET

MAX_ERRORS = 9
MESSAGE_LINES = 6
MESSAGE_CHARS = 900


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def module_of(path, root):
    """app/build/test-results/testDebugUnitTest/TEST-x.xml -> :app; core/today/... -> :core:today"""
    parts = os.path.relpath(path, root).split(os.sep)
    return ":" + ":".join(parts[: parts.index("build")])


def first_lines(text):
    lines = [line.rstrip() for line in (text or "").strip().splitlines() if line.strip()]
    short = "\n".join(lines[:MESSAGE_LINES])
    return short[:MESSAGE_CHARS]


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    files = sorted(glob.glob(os.path.join(root, "**", "build", "test-results", "*", "TEST-*.xml"), recursive=True))
    if not files:
        print("::warning title=Test results::No JUnit XML results found. The tests did not run.")
        return 0

    totals = {}
    failures = []
    for path in files:
        module = module_of(path, root)
        counts = totals.setdefault(module, {"tests": 0, "failed": 0, "skipped": 0, "time": 0.0})
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as error:
            failures.append((module, os.path.basename(path), f"The result file cannot be read: {error}"))
            counts["failed"] += 1
            continue
        for case in suite.iter("testcase"):
            counts["tests"] += 1
            counts["time"] += float(case.get("time") or 0)
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                counts["failed"] += 1
                name = f"{case.get('classname', '')}.{case.get('name', '')}"
                text = problem.get("message") or problem.text or problem.get("type") or "(no message)"
                failures.append((module, name, first_lines(text)))
            elif case.find("skipped") is not None:
                counts["skipped"] += 1

    lines = []
    for module, counts in sorted(totals.items()):
        passed = counts["tests"] - counts["failed"] - counts["skipped"]
        lines.append(
            (
                module,
                f"{counts['tests']} tests: {passed} passed, {counts['failed']} failed, "
                f"{counts['skipped']} skipped ({counts['time']:.0f} s)",
            )
        )
    if len(lines) <= 10:
        for module, text in lines:
            print(f"::notice title={escape_property('Tests ' + module)}::{text}")
    else:
        # More modules than GitHub shows notices: one notice with a line per module.
        print(f"::notice title=Tests::{escape_data(chr(10).join(f'{m}: {t}' for m, t in lines))}")

    for module, name, text in failures[:MAX_ERRORS]:
        title = escape_property(f"Failed test {module}")
        print(f"::error title={title}::{escape_data(name + chr(10) + text)}")
    if len(failures) > MAX_ERRORS:
        print(f"::error title=More failed tests::{len(failures) - MAX_ERRORS} more failed tests are not shown.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
