#!/usr/bin/env bash
#
# Build the DualDex H&S 2.0.5 runtime probe tools.
#
# Developer tools only: NOT built by ci.sh and NOT shipped in the APK. All three tools
# link the *production* native reader sources so the runtime evidence they produce is
# evidence about the reader that ships, not about a reimplementation.
#
#   ./build.sh
#     builds:
#       ./runtime_battle_probe    — overworld/boot baseline probe (from PR #45)
#       ./save_generator          — drives ROM to produce a legal .sav file
#       ./scenario_runner         — loads .sav and asserts scenario invariants
#
set -euo pipefail
cd "$(dirname "$0")"

REPO_ROOT="$(cd ../.. && pwd)"
CC="${CC:-$(command -v gcc || command -v clang || true)}"
if [ -z "$CC" ]; then
  echo "error: no host C compiler (gcc or clang) found" >&2
  exit 1
fi

COMMON_CFLAGS="-O2 -Wall -Wextra -I ${REPO_ROOT}/native/include"
COMMON_SRCS="${REPO_ROOT}/native/src/pokemon_reader.c \
  ${REPO_ROOT}/native/src/pokemon_text.c \
  ${REPO_ROOT}/native/src/gba_memory_map.c \
  ${REPO_ROOT}/native/src/libretro_host.c"
COMMON_LDFLAGS="-ldl -lpthread"

echo "building: runtime_battle_probe"
$CC $COMMON_CFLAGS \
  runtime_battle_probe.c \
  $COMMON_SRCS \
  $COMMON_LDFLAGS \
  -o runtime_battle_probe
echo "built: $(pwd)/runtime_battle_probe"

echo "building: save_generator"
$CC $COMMON_CFLAGS \
  save_generator.c \
  $COMMON_SRCS \
  $COMMON_LDFLAGS \
  -o save_generator
echo "built: $(pwd)/save_generator"

echo "building: scenario_runner"
$CC $COMMON_CFLAGS \
  scenario_runner.c \
  $COMMON_SRCS \
  $COMMON_LDFLAGS \
  -o scenario_runner
echo "built: $(pwd)/scenario_runner"
