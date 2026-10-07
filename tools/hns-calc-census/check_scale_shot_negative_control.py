#!/usr/bin/env python3
"""Verify the recorded Scale Shot admission-only control ran on Slice 13's exact head."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "tools/hns-calc-census/starting-head/scale-shot-negative-control.json"
TEST = ROOT / "tools/hns-calc-census/starting-head/HnsScaleShotStartingHeadTest.kt"
doc = json.loads(EVIDENCE.read_text())
assert doc["startingHead"] == "c91f024ad50f428fdd99b537e2caa6dab3abd431"
assert doc["testClass"] == "com.dualdex.calculator.HnsScaleShotStartingHeadTest"
assert doc["result"] == "PASS" and doc["tests"] == 1 and doc["failures"] == doc["errors"] == 0
assert doc["assertion"] == "Scale Shot refuses with exactly HNS_MOVE_MECHANICS_NOT_MODELLED through the existing move mechanics gate"
assert doc["testSourceSha256"] == hashlib.sha256(TEST.read_bytes()).hexdigest()
assert len(doc["junitXmlSha256"]) == 64 and all(c in "0123456789abcdef" for c in doc["junitXmlSha256"])
print("Scale Shot starting-head negative control: PASS at c91f024; existing move-mechanics refusal")
