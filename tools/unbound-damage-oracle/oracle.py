#!/usr/bin/env python3
"""ROM-free integrity check, or bounded compilation of an external CFRU oracle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import resource
import selectors
import shutil
import signal
import subprocess
import tempfile
import time

from matrix import (FIELDS, TYPES, ATTACKER_ABILITIES, DEFENDER_ABILITIES,
                    ATTACKER_ITEMS, DEFENDER_ITEMS, cases)

ROOT = Path(__file__).resolve().parent
COMMIT = "b637a27898b14e25dd24d0f69a3e302f0069deb8"
HARNESS_FILES = ("host.c", "unsupported.h", "matrix.py", "oracle.py")
FLAGS = ["-std=gnu11", "-O1", "-ffunction-sections", "-fdata-sections",
         "-Wno-attributes", "-Wno-attribute-alias", "-Wno-pointer-to-int-cast",
         "-Wno-int-to-pointer-cast", "-Wl,--gc-sections"]
CONSTANTS = {"critical_multiplier": 15, "fairy_type": 23, "burn_status": 16,
             "rain_weather": 1, "sun_weather": 32, "reflect": 1, "light_screen": 2}
OUTPUT_LIMIT = 1024 * 1024


def encoded(value):
    if isinstance(value, dict) and "cases" in value:
        # One numeric scenario per line keeps generated diffs reviewable.
        metadata = json.dumps({k: v for k, v in value.items() if k != "cases"}, sort_keys=True, indent=2)
        entries = ",\n".join("    " + json.dumps(case, sort_keys=True, separators=(",", ":")) for case in value["cases"])
        return (metadata[:-2] + ",\n  \"cases\": [\n" + entries + "\n  ]\n}\n").encode()
    return (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def harness_hash():
    # Include filenames and separators, so concatenation cannot hide a file boundary.
    return sha(b"".join(name.encode() + b"\0" + (ROOT / name).read_bytes() + b"\0"
                        for name in HARNESS_FILES))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_input(operands):
    require(isinstance(operands, dict) and set(operands) == set(FIELDS), "input fields")
    require(all(type(v) is int for v in operands.values()), "inputs must be integers")
    ranges = {"level": (1, 100), "power": (1, 150), "category": (0, 1),
              "screens": (0, 3), "critical": (0, 1)}
    ranges.update({key: (1, 999) for key in FIELDS[4:8]})
    ranges.update({key: (0, 12) for key in FIELDS[12:16]})
    for key, (low, high) in ranges.items():
        require(low <= operands[key] <= high, f"out-of-scope {key}")
    domains = {"move_type": TYPES, **{key: TYPES for key in FIELDS[8:12]},
               "attacker_status": (0, 16), "weather": (0, 1, 32),
               "attacker_ability": ATTACKER_ABILITIES, "defender_ability": DEFENDER_ABILITIES,
               "attacker_hold_effect": ATTACKER_ITEMS, "defender_hold_effect": DEFENDER_ITEMS}
    for key, domain in domains.items():
        require(operands[key] in domain, f"out-of-scope {key}")


def check(document):
    require(set(document) == {"schema_version", "provenance", "cases"}, "corpus fields")
    require(type(document["schema_version"]) is int and document["schema_version"] == 1, "schema version")
    provenance = document["provenance"]
    require(set(provenance) == {"authority", "cfru_commit", "cfru_tree", "damage_source_sha256",
                               "harness_sha256", "compiler", "host", "flags", "constants"}, "provenance fields")
    require(provenance["authority"] == "host-compiled-source-only", "authority")
    require(provenance["cfru_commit"] == COMMIT, "CFRU commit")
    require(provenance["harness_sha256"] == harness_hash(), "stale harness hash: regenerate")
    require(provenance["flags"] == FLAGS and provenance["constants"] == CONSTANTS, "build configuration")
    for key, length in (("cfru_tree", 40), ("damage_source_sha256", 64), ("harness_sha256", 64)):
        value = provenance[key]
        require(isinstance(value, str) and len(value) == length and
                all(char in "0123456789abcdef" for char in value), f"invalid {key}")
    require(all(isinstance(provenance[key], str) and 0 < len(provenance[key]) <= 256
                for key in ("compiler", "host")), "compiler/host identity")
    entries = document["cases"]
    require(isinstance(entries, list) and len(entries) >= 40, "at least 40 cases required")
    expected = cases()
    require(len(entries) == len(expected), "matrix case count")
    for entry, selected in zip(entries, expected):
        require(set(entry) == {"id", "input", "rolls"}, "case fields")
        require(type(entry["id"]) is int and entry["id"] == selected["id"], "case IDs/order")
        validate_input(entry["input"])
        require(entry["input"] == selected["input"], f"case {entry['id']} differs from matrix")
        rolls = entry["rolls"]
        require(isinstance(rolls, list) and len(rolls) == 16, "exactly 16 rolls required")
        require(all(type(v) is int and 0 <= v <= 0x7fffffff for v in rolls), "damage integer domain")
        require(rolls == sorted(rolls), "rolls must be monotone")
        require(all(v == 0 for v in rolls) or all(v >= 1 for v in rolls), "mixed immunity/damage")
        random_rolls = [max(1, rolls[-1] * r // 100) for r in range(85, 101)] if rolls[-1] else [0] * 16
        require(rolls == random_rolls, "rolls disagree with final random multiplier")
    return len(entries)


class Runner:
    """One process group per command; finite output, disk, time and task budgets."""
    def __init__(self, work):
        self.work = work
        self.started = time.monotonic()
        self.jobs = []

    def run(self, command, *, data=None, seconds=15):
        require(time.monotonic() - self.started < 300, "five-minute total job budget exhausted")
        seconds = min(seconds, max(1, 300 - (time.monotonic() - self.started)))
        job = {"command": list(map(str, command)), "cwd": str(ROOT), "owner": "W4",
               "run_directory": str(self.work), "deadline_seconds": seconds,
               "output_budget_bytes": OUTPUT_LIMIT, "file_budget_bytes": 16 * 1024 * 1024,
               "expected": "exit 0; complete stdout parsed by caller"}
        self.jobs.append(job)
        manifest = self.work / "jobs.json"
        manifest.write_bytes(encoded(self.jobs))

        def limits():
            resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
            resource.setrlimit(resource.RLIMIT_FSIZE, (16 * 1024 * 1024, 16 * 1024 * 1024))

        with tempfile.TemporaryFile(dir=self.work) as input_file:
            if data is not None:
                input_file.write(data)
                input_file.seek(0)
            process = subprocess.Popen(command, cwd=ROOT, stdin=input_file, stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE, start_new_session=True, preexec_fn=limits)
            selector = selectors.DefaultSelector()
            selector.register(process.stdout, selectors.EVENT_READ, 0)
            selector.register(process.stderr, selectors.EVENT_READ, 1)
            output = [bytearray(), bytearray()]
            deadline = time.monotonic() + seconds
            failure = None
            try:
                while selector.get_map() or process.poll() is None:
                    if time.monotonic() > deadline:
                        raise RuntimeError("command deadline exceeded")
                    if shutil.disk_usage(self.work).free < 10 * 1024**3:
                        raise RuntimeError("disk reserve below 10 GiB")
                    for key, _ in selector.select(timeout=0.1):
                        chunk = os.read(key.fileobj.fileno(), 65536)
                        if not chunk:
                            selector.unregister(key.fileobj)
                        else:
                            if sum(map(len, output)) + len(chunk) > OUTPUT_LIMIT:
                                raise RuntimeError("command output exceeded 1 MiB")
                            output[key.data].extend(chunk)
                require(process.wait() == 0, f"command exited {process.returncode}")
            except BaseException as error:
                failure = error
            finally:
                # Clean the entire owned group, including descendants after leader exit.
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                    until = time.monotonic() + 5
                    while time.monotonic() < until:
                        process.poll()
                        try:
                            os.killpg(process.pid, 0)
                        except ProcessLookupError:
                            break
                        time.sleep(0.05)
                    else:
                        os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait()
                selector.close()
                process.stdout.close()
                process.stderr.close()
                job["exit_status"] = process.returncode
                job["output_bytes"] = sum(map(len, output))
                job["result"] = "failed" if failure else "passed"
                manifest.write_bytes(encoded(self.jobs))
            if failure:
                (self.work / "failure-stdout.txt").write_bytes(output[0])
                (self.work / "failure-stderr.txt").write_bytes(output[1])
                raise RuntimeError(f"{failure}; bounded diagnostics: {self.work}") from failure
            return bytes(output[0]).decode().strip()


def source_identity(source, runner):
    require((source / "src/damage_calc.c").is_file(), "private path exists but damage source is missing")
    head = runner.run(["git", "-C", str(source), "rev-parse", "HEAD"])
    require(head == COMMIT, f"wrong CFRU commit: {head}")
    status = runner.run(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=all"])
    require(not status, "private source checkout must be clean, including untracked files")
    tree = runner.run(["git", "-C", str(source), "rev-parse", "HEAD^{tree}"])
    return {"cfru_commit": head, "cfru_tree": tree,
            "damage_source_sha256": sha((source / "src/damage_calc.c").read_bytes())}


def observe(source, work, cc):
    runner = Runner(work)
    identity = source_identity(source, runner)
    compiler = runner.run([cc, "--version"]).splitlines()[0]
    binary = work / "oracle"
    runner.run([cc, *FLAGS, "-I", str(source), str(ROOT / "host.c"), "-o", str(binary)], seconds=120)
    constants = json.loads(runner.run([str(binary), "--describe"]))
    require(constants == CONSTANTS, "source constants/configuration disagree with numeric schema")
    selected = cases()
    for case in selected:
        validate_input(case["input"])
    data = "".join(" ".join(str(case["input"][key]) for key in FIELDS) + "\n" for case in selected).encode()
    lines = runner.run([str(binary)], data=data).splitlines()
    require(len(lines) == len(selected), "missing or extra engine results")
    for case, line in zip(selected, lines):
        case["rolls"] = list(map(int, line.split()))
    # Same binary, reverse order: expose state leaking between scenarios.
    reverse_data = b"\n".join(reversed(data.splitlines())) + b"\n"
    reverse_lines = runner.run([str(binary)], data=reverse_data).splitlines()
    require(reverse_lines == list(reversed(lines)), "engine results depend on case ordering")
    require(source_identity(source, runner) == identity, "private source changed during build")
    document = {"schema_version": 1, "provenance": {
        "authority": "host-compiled-source-only", **identity,
        "harness_sha256": harness_hash(), "compiler": compiler,
        "host": f"{platform.system()}-{platform.machine()}", "flags": FLAGS, "constants": constants,
    }, "cases": selected}
    check(document)
    return document


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--verify", action="store_true", help="compile private source and compare every case")
    mode.add_argument("--regenerate", action="store_true", help="replace corpus from private source")
    parser.add_argument("--source", type=Path, default=os.environ.get("CFRU_SOURCE_DIR"))
    parser.add_argument("--cc", default=os.environ.get("CC", "cc"))
    args = parser.parse_args()
    corpus = ROOT / "corpus.json"
    if not args.regenerate:
        raw = corpus.read_bytes()
        document = json.loads(raw)
        count = check(document)
        require(raw == encoded(document), "corpus serialization is not canonical")
        print(f"PASS: {count} cases, {count * 16} numeric rolls; schema, matrix, provenance, monotonicity")
    if not (args.verify or args.regenerate):
        return 0
    if args.source is None or not args.source.exists():
        if args.verify:
            print("SKIP: private CFRU source absent; pass --source PATH or CFRU_SOURCE_DIR (no engine verification performed)")
            return 0
        raise ValueError("regeneration requires --source PATH or CFRU_SOURCE_DIR")
    require(shutil.disk_usage(ROOT).free >= 20 * 1024**3, "need 20 GiB disk reserve")
    work = Path(tempfile.mkdtemp(prefix="unbound-oracle-"))
    try:
        generated = observe(args.source.resolve(), work, args.cc)
        if args.regenerate:
            temporary = ROOT / "corpus.json.tmp"
            temporary.write_bytes(encoded(generated))
            temporary.replace(corpus)
            print(f"PASS: regenerated {len(generated['cases'])} source-backed cases")
        else:
            # Compiler version may differ; source, harness and all numeric outputs must match.
            for key in ("cfru_commit", "cfru_tree", "damage_source_sha256", "harness_sha256", "flags", "constants"):
                require(generated["provenance"][key] == document["provenance"][key], f"provenance mismatch: {key}")
            require(generated["cases"] == document["cases"], "private engine outputs differ from committed corpus")
            print(f"PASS: private engine reproduced all {len(generated['cases'])} cases / {len(generated['cases']) * 16} rolls, forward and reversed")
        shutil.rmtree(work)
    except BaseException:
        print(f"FAILED: retained bounded build diagnostics in {work}")
        raise
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError) as error:
        raise SystemExit(f"FAIL: {error}")
