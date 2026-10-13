# Temporary (T06): writes Gradle errors and failed tests as annotations. Removed before review.
import glob, re, sys, xml.etree.ElementTree as ET
def esc(t): return t.replace('%','%25').replace('\r','').replace('\n','%0A')
log = open(sys.argv[1], errors='replace').read().splitlines() if len(sys.argv) > 1 else []
errs = [l for l in log if l.startswith('e: ') or 'What went wrong' in l or l.startswith('> ') or 'Error:' in l or 'error:' in l.lower() and 'lint' in l.lower()]
for l in errs[:6]: print(f"::error title=build::{esc(l[:900])}")
n = 0
for f in glob.glob('**/build/test-results/**/*.xml', recursive=True):
    try: root = ET.parse(f).getroot()
    except Exception: continue
    for tc in root.iter('testcase'):
        fail = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
        if fail is None: continue
        n += 1
        if n <= 9:
            msg = (fail.get('message') or fail.text or '')[:700]
            print(f"::error title={esc(tc.get('classname','').split('.')[-1])} {esc(tc.get('name',''))}::{esc(msg)}")
print(f"::notice title=failed tests::{n}")
for f in glob.glob('**/build/reports/lint-results-*.xml', recursive=True):
    root = ET.parse(f).getroot()
    for i, issue in enumerate(root.iter('issue')):
        if i >= 5: break
        loc = issue.find('location')
        print(f"::warning title=lint {issue.get('id')}::{esc((loc.get('file') if loc is not None else '') + ':' + (loc.get('line','') if loc is not None else '') + ' ' + issue.get('message',''))}")
