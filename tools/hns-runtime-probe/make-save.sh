#!/usr/bin/env bash
# make-save.sh — DualDex H&S 2.0.5 save-progression driver
#
# Drives the official 2.0.5 ROM from a cold boot using the save_generator binary,
# producing a legal battery-backed save file with:
#   - player party count >= 2
#   - trainer with >= 2 Pokémon reachable from the save point
#
# Prerequisites (must be available on PATH or via DUALDEX_* environment variables):
#   - mGBA libretro shared library (.so), version ≥ 0.10.0
#   - Official H&S 2.0.5 ROM (legally obtained)
#   - save_generator binary (built with ./build.sh from this directory)
#
# Usage:
#   ./make-save.sh <mgba_libretro.so> <hns_2.0.5.gba> <output.sav> [max_frames]
#
# Environment overrides:
#   DUALDEX_MGBA_CORE   path to mgba_libretro.so (overrides positional arg 1)
#   DUALDEX_HNS_ROM     path to hns_2.0.5.gba    (overrides positional arg 2)
#
# Exit codes:
#   0   save file created and validated
#   1   failure (missing prerequisite, checkpoint not reached, or SRAM flush failed)
#   2   usage error

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAVE_GEN="${SCRIPT_DIR}/save_generator"

# --- argument parsing ---
MGBA_CORE="${DUALDEX_MGBA_CORE:-${1:-}}"
HNS_ROM="${DUALDEX_HNS_ROM:-${2:-}}"
OUTPUT_SAV="${3:-}"
MAX_FRAMES="${4:-108000}"

if [[ -z "${MGBA_CORE}" || -z "${HNS_ROM}" || -z "${OUTPUT_SAV}" ]]; then
    echo "usage: $0 <mgba_libretro.so> <hns_2.0.5.gba> <output.sav> [max_frames]" >&2
    echo "  or set DUALDEX_MGBA_CORE and DUALDEX_HNS_ROM environment variables." >&2
    exit 2
fi

# --- prerequisite checks ---
if [[ ! -f "${SAVE_GEN}" ]]; then
    echo "ERROR: save_generator binary not found at ${SAVE_GEN}" >&2
    echo "       Run ./build.sh from the hns-runtime-probe directory first." >&2
    exit 1
fi

if [[ ! -f "${MGBA_CORE}" ]]; then
    echo "ERROR: mGBA libretro core not found: ${MGBA_CORE}" >&2
    exit 1
fi

if [[ ! -f "${HNS_ROM}" ]]; then
    echo "ERROR: H&S 2.0.5 ROM not found: ${HNS_ROM}" >&2
    exit 1
fi

# --- banner ---
echo "== make-save.sh =="
echo "core       : ${MGBA_CORE}"
echo "rom        : ${HNS_ROM}"
echo "output     : ${OUTPUT_SAV}"
echo "max_frames : ${MAX_FRAMES}"
echo ""

# --- run save_generator ---
# save_generator exits non-zero on any missed checkpoint.
"${SAVE_GEN}" "${MGBA_CORE}" "${HNS_ROM}" "${OUTPUT_SAV}" "${MAX_FRAMES}"
GEN_EXIT=$?

if [[ ${GEN_EXIT} -ne 0 ]]; then
    echo "" >&2
    echo "ERROR: save_generator exited with code ${GEN_EXIT}." >&2
    echo "       The deterministic input sequence did not reach the required game state." >&2
    echo "       This is the correct fail-closed behavior; no save was generated." >&2
    exit 1
fi

# --- validate output ---
if [[ ! -f "${OUTPUT_SAV}" ]]; then
    echo "ERROR: save_generator reported success but ${OUTPUT_SAV} does not exist." >&2
    exit 1
fi

SAV_SIZE=$(stat -c%s "${OUTPUT_SAV}" 2>/dev/null || stat -f%z "${OUTPUT_SAV}")
echo ""
echo "output_file : ${OUTPUT_SAV}"
echo "output_size : ${SAV_SIZE} bytes"

# GBA battery saves are exactly 128 KiB (131072 bytes) for standard SRAM.
# Flash saves are 64 KiB or 128 KiB. Accept 65536-131072 bytes.
if [[ ${SAV_SIZE} -lt 65536 ]]; then
    echo "ERROR: save file is smaller than 64 KiB (${SAV_SIZE} bytes); likely corrupt." >&2
    exit 1
fi

echo ""
echo "make-save.sh : OK — ${OUTPUT_SAV} is ready for scenario_runner."
