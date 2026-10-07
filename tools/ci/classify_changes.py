#!/usr/bin/env python3
"""Conservative PR work selector; unrecognized changes always need full CI."""
import os
import re
import subprocess
from pathlib import Path

# Planning/CI prose only. README, contribution/build contracts, all native/H&S
# authority, provenance, evidence and generated content deliberately stay full.
LIGHTWEIGHT_DOCS = frozenset({
    "FUTURE_PLATFORM_ROADMAP.md",
    "POST_BETA_ENHANCED_BATTLE_CONSOLE.md",
    "UI_DESIGN_AUDIT.md",
    "docs/CI.md",
})


def docs_only(raw_diff):
    """Accept only ordinary edits to existing, explicitly listed prose files.

    --raw -z --no-renames keeps both sides of renames and unusual names visible.
    Additions, deletions, mode/type changes and malformed/empty input stay full.
    """
    fields = raw_diff.split(b"\0")
    if not fields or fields.pop() != b"" or not fields or len(fields) % 2:
        return False
    for header, path in zip(fields[::2], fields[1::2]):
        parts = header.split()
        if len(parts) != 5 or parts[0] != b":100644" or parts[1] != b"100644" or parts[4] != b"M":
            return False
        if path.decode("utf-8", errors="replace") not in LIGHTWEIGHT_DOCS:
            return False
    return True


def classify(event, base, head):
    if event != "pull_request" or not all(re.fullmatch(r"[0-9a-f]{40}", ref or "") for ref in (base, head)):
        return True
    # Compare immutable event endpoints, including both sides of any rename.
    # Changes on the base since divergence may cause extra full CI, never a skip.
    raw = subprocess.check_output(["git", "diff", "--raw", "-z", "--no-renames", base, head, "--"])
    return not docs_only(raw)


def main():
    full = classify(os.getenv("CI_EVENT"), os.getenv("CI_BASE"), os.getenv("CI_HEAD"))
    print("Full validation required" if full else "Allowlisted prose: lightweight validation")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.write(f"full={'true' if full else 'false'}\n")


if __name__ == "__main__":
    main()
