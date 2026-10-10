#!/usr/bin/env python3
"""Verify the frozen calculator-beta evidence inventory (calculator_beta_freeze.json).

Every recorded SHA-256 must match the committed file, and the admitted-move snapshot must be present.
Any drift, missing file, or empty snapshot is a failure. Pass --root to check a copied tree (used by
test_calculator_beta_freeze_check.py for negative mutation tests).
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

DEFAULT_ROOT = Path(__file__).resolve().parents[2]
SNAPSHOT = "tools/hns-move-mechanics/calculator_beta_freeze.json"


def verify(root):
    """Return a list of problems; an empty list means the frozen inventory is intact."""
    root = Path(root)
    problems = []
    doc = json.loads((root / SNAPSHOT).read_text(encoding="utf-8"))
    if not doc.get("admittedMoves"):
        problems.append("admittedMoves is empty")
    inventory = doc.get("evidenceInventory") or []
    if not inventory:
        problems.append("evidenceInventory is empty")
    for entry in inventory:
        path = root / entry["path"]
        if not path.is_file():
            problems.append(f"missing: {entry['path']}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != entry["sha256"]:
            problems.append(f"sha256 mismatch: {entry['path']}")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args()
    problems = verify(args.root)
    if problems:
        for problem in problems:
            print("FREEZE CHECK FAILED:", problem, file=sys.stderr)
        return 1
    count = len(json.loads((args.root / SNAPSHOT).read_text(encoding="utf-8"))["evidenceInventory"])
    print(f"calculator beta freeze: {count} frozen evidence hashes verified; admitted-move snapshot present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
