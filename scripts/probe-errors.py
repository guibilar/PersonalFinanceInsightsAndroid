"""Temporary (T88 probe): writes the Kotlin compile errors and the lint issues as annotations."""
import glob
import os
import re
import xml.etree.ElementTree as ET

lines = []
if os.path.exists("gradle.log"):
    with open("gradle.log", encoding="utf-8", errors="replace") as f:
        log = f.read().splitlines()
    for i, line in enumerate(log):
        if line.startswith("e: ") or line.startswith("w: ") and "deprecated" not in line and False:
            lines.append(line[:400])
    wrong = [i for i, l in enumerate(log) if "What went wrong" in l]
    for i in wrong[:6]:
        lines.append("WRONG: " + " | ".join(log[i + 1:i + 5])[:500])
for path in glob.glob("**/build/reports/lint-results-debug.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception as error:
        lines.append(f"lint {path}: {error}")
        continue
    for issue in root.iter("issue"):
        if issue.get("severity") in ("Error", "Fatal", "Warning"):
            loc = issue.find("location")
            where = f"{loc.get('file')}:{loc.get('line')}" if loc is not None else ""
            lines.append(f"LINT {issue.get('id')} {where.replace(os.getcwd(), '')}: {issue.get('message')}"[:400])
chunk = ""
count = 0
for line in lines:
    line = line.replace("%", "%25").replace("\r", "")
    if len(chunk) + len(line) > 3800:
        print(f"::error title=Probe {count}::{chunk}")
        count += 1
        chunk = ""
    chunk += line + "%0A"
if chunk:
    print(f"::error title=Probe {count}::{chunk}")
print(f"::notice title=Probe::{len(lines)} lines")

# The failed tests, with the lines of the app's code in their stack (T88 probe).
for path in glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is None:
            continue
        text = (problem.text or problem.get("message") or "")
        keep = [l.strip() for l in text.splitlines() if "financeinsights" in l or "Caused" in l or "busy" in l.lower() or "Idle" in l][:25]
        body = (case.get("name", "") + "\n" + "\n".join(keep))[:3500]
        print("::error title=Probe stack::" + body.replace("%", "%25").replace("\r", "").replace("\n", "%0A"))
