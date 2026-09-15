#!/usr/bin/env bash
#
# Build the DualDex H&S 2.0.5 runtime battle-state probe.
#
# Developer tool only: it is NOT built by ci.sh and NOT shipped in the APK. It links the
# *production* native reader sources so the runtime evidence it produces is evidence about the
# reader that ships, not about a reimplementation.
#
#   ./build.sh            # builds ./runtime_battle_probe next to this script
#
set -euo pipefail
cd "$(dirname "$0")"

REPO_ROOT="$(cd ../.. && pwd)"
CC="${CC:-$(command -v gcc || command -v clang || true)}"
if [ -z "$CC" ]; then
  echo "error: no host C compiler (gcc or clang) found" >&2
  exit 1
fi

"$CC" -O2 -Wall -Wextra \
  -I "$REPO_ROOT/native/include" \
  runtime_battle_probe.c \
  "$REPO_ROOT/native/src/pokemon_reader.c" \
  "$REPO_ROOT/native/src/pokemon_text.c" \
  "$REPO_ROOT/native/src/gba_memory_map.c" \
  "$REPO_ROOT/native/src/libretro_host.c" \
  -ldl -lpthread \
  -o runtime_battle_probe

echo "built: $(pwd)/runtime_battle_probe"
