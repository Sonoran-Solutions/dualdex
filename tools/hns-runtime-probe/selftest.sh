#!/usr/bin/env bash
# selftest.sh — DualDex H&S 2.0.5 runtime probe self-test
#
# Runs the scenario_runner in --selftest mode, which exercises the invariant checker
# without a ROM or save file. Validates that the harness infrastructure compiles and
# that the checker logic operates correctly on the trivially-inactive (overworld) case.
#
# Prerequisites:
#   - scenario_runner binary (built with ./build.sh from this directory)
#
# Usage:
#   ./selftest.sh
#
# Exit codes:
#   0   selftest passed
#   1   selftest failed or scenario_runner binary missing

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${SCRIPT_DIR}/scenario_runner"

echo "== selftest.sh =="

if [[ ! -f "${RUNNER}" ]]; then
    echo "ERROR: scenario_runner binary not found at ${RUNNER}" >&2
    echo "       Run ./build.sh from the hns-runtime-probe directory first." >&2
    exit 1
fi

echo "running: ${RUNNER} --selftest"
echo ""

"${RUNNER}" --selftest
RESULT=$?

echo ""
if [[ ${RESULT} -eq 0 ]]; then
    echo "selftest.sh : PASS"
else
    echo "selftest.sh : FAIL (exit ${RESULT})" >&2
fi

exit ${RESULT}
