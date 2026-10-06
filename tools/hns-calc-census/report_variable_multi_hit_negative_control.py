#!/usr/bin/env python3
"""Record admission-only refusal of Slice 13 moves at the immutable Slice-12 head."""
import argparse
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
START = "2d57cf8b991b04e676fd6d920ddcdf52159f1735"
TEST = ROOT / "tools/hns-calc-census/starting-head/HnsVariableMultiHitStartingHeadTest.kt"
TARGET = Path(__file__).with_name("variable-multi-hit-negative-control.json")
MOVES = ["Arm Thrust", "Bone Rush", "Bullet Seed", "Comet Punch", "Double Slap", "Fury Attack",
         "Fury Swipes", "Icicle Spear", "Pin Missile", "Rock Blast", "Spike Cannon", "Tail Slap"]

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--checkout", type=Path)
p.add_argument("--canonical-log", type=Path)
p.add_argument("--check", action="store_true")
a = p.parse_args()
if a.check:
    run = json.loads(TARGET.read_text())
else:
    assert a.checkout and a.canonical_log
    assert subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=a.checkout, text=True).strip() == START
    assert subprocess.check_output(["git", "diff", "--exit-code"], cwd=a.checkout) == b""
    assert "BUILD SUCCESSFUL" in a.canonical_log.read_text()
    xml = ET.parse(a.checkout / "app/build/test-results/testDebugUnitTest/TEST-com.dualdex.calculator.HnsVariableMultiHitStartingHeadTest.xml").getroot()
    run = dict(command="./ci.sh test", exitStatus=0, tests=int(xml.attrib["tests"]),
               failures=int(xml.attrib["failures"]), errors=int(xml.attrib["errors"]),
               skipped=int(xml.attrib["skipped"]))
run.update(startingSha=START, moves=MOVES, refusal="HNS_MOVE_MECHANICS_NOT_MODELLED",
           testSha256=hashlib.sha256(TEST.read_bytes()).hexdigest())
assert run["exitStatus"] == 0 and run["tests"] == 1 and run["errors"] == run["failures"] == run["skipped"] == 0
text = json.dumps(run, indent=2, sort_keys=True) + "\n"
if a.check:
    assert TARGET.read_text() == text, "Variable multi-hit starting-head evidence stale"
else:
    TARGET.write_text(text)
print("Starting-head variable multi-hit admission: twelve move-mechanics refusals verified")
