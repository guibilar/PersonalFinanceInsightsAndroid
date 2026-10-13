#!/usr/bin/env python3
"""Probe only (T12): turns the build log, the test results and the lint reports into annotations,
because the CI logs and artifacts can't be read from the task session."""
import glob, hashlib, os, re, sys
import xml.etree.ElementTree as ET

def esc(text):
    return text.replace('%', '%25').replace('\r', '').replace('\n', '%0A')

def emit(kind, title, lines, limit=60):
    if not lines:
        return
    body = '\n'.join(lines[:limit])
    if len(lines) > limit:
        body += f'\n... and {len(lines) - limit} more'
    print(f'::{kind} title={title}::{esc(body)}')

log = open('build.log', encoding='utf-8', errors='replace').read().splitlines() if os.path.exists('build.log') else []
root = os.getcwd()

compile_errors = []
for line in log:
    if line.startswith('e: '):
        compile_errors.append(line[3:].replace('file://' + root + '/', ''))
emit('error', 'Kotlin errors', compile_errors)

wrong = []
for i, line in enumerate(log):
    if line.startswith('* What went wrong:'):
        wrong.extend(l for l in log[i + 1:i + 8] if l.strip() and not l.startswith('* Try'))
emit('error', 'What went wrong', wrong, 40)

failures = []
for path in glob.glob('**/build/test-results/**/*.xml', recursive=True):
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    for case in tree.iter('testcase'):
        for failure in list(case.iter('failure')) + list(case.iter('error')):
            message = (failure.get('message') or failure.text or '').strip().splitlines()
            detail = ' | '.join(message[:3])[:400]
            failures.append(f"{case.get('classname', '').split('.')[-1]}.{case.get('name')}: {detail}")
emit('error', 'Failed tests', failures)

lint = []
for path in glob.glob('**/build/reports/lint-results-*.xml', recursive=True):
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    for issue in tree.iter('issue'):
        location = issue.find('location')
        where = ''
        if location is not None:
            where = f"{location.get('file', '').replace(root + '/', '')}:{location.get('line', '')}"
        lint.append(f"[{issue.get('severity')}] {issue.get('id')}: {issue.get('message')} ({where})")
emit('error', 'Lint issues', lint)

if '--hashes' in sys.argv:
    for module in sorted(set(p.split('/build/shots/')[0] for p in glob.glob('**/build/shots/*.png', recursive=True))):
        lines = []
        for path in sorted(glob.glob(f'{module}/build/shots/*.png')):
            digest = hashlib.sha256(open(path, 'rb').read()).hexdigest()[:16]
            lines.append(f'{os.path.basename(path)} {digest}')
        emit('notice', f'Shots {module}', lines, 200)
