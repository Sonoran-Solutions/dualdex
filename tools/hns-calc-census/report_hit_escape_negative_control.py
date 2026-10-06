#!/usr/bin/env python3
"""Record a passing admission-only check against the unchanged starting API."""
import argparse,hashlib,json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
START='27205d4a1f84521c55e038dbe3ca0db8d0657ea6'
TEST=ROOT/'tools/hns-calc-census/starting-head/HnsHitEscapeStartingHeadTest.kt'
TARGET=Path(__file__).with_name('hit-escape-negative-control.json')
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--checkout',type=Path)
p.add_argument('--canonical-log',type=Path)
p.add_argument('--check',action='store_true')
a=p.parse_args()
if a.check:
    run=json.loads(TARGET.read_text())
else:
    assert a.checkout and a.canonical_log
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=a.checkout,text=True).strip()==START
    assert subprocess.check_output(['git','diff','--exit-code'],cwd=a.checkout)==b''
    assert 'BUILD SUCCESSFUL' in a.canonical_log.read_text()
    xml=ET.parse(a.checkout/'app/build/test-results/testDebugUnitTest/TEST-com.dualdex.calculator.HnsHitEscapeStartingHeadTest.xml').getroot()
    run=dict(command='./ci.sh test',exitStatus=0,tests=int(xml.attrib['tests']),failures=int(xml.attrib['failures']),errors=int(xml.attrib['errors']),skipped=int(xml.attrib['skipped']))
run.update(startingSha=START,moves=[369,521,740],refusal='HNS_MOVE_MECHANICS_NOT_MODELLED',testSha256=hashlib.sha256(TEST.read_bytes()).hexdigest())
assert run['exitStatus']==0 and run['tests']==1 and run['errors']==run['failures']==run['skipped']==0
text=json.dumps(run,indent=2,sort_keys=True)+'\n'
if a.check:assert TARGET.read_text()==text,'Starting-head evidence stale'
else:TARGET.write_text(text)
print('Starting-head hit-escape admission: three move-mechanics refusals verified')
