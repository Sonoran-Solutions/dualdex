#!/usr/bin/env bash
#
# Sonoran canonical DualDex CI contract (ORCH-090).
#
# One command vocabulary shared by humans, Antigravity, agents, and GitHub
# Actions. Run from the repo root:
#
#   ./ci.sh test     # native C runner + Kotlin unit tests
#   ./ci.sh build    # assemble the debug APK
#   ./ci.sh all      # test then build (default)
#   ./ci.sh release  # assemble the production-signed release APK (requires
#                    # external signing credentials; fails closed if missing)
#
# The contract is deterministic, non-interactive, and fail-closed: every
# authoritative check is required. A missing host C compiler, a missing or
# stale QuickJS submodule, a failing native test, a failing Gradle test, or a
# failing build all fail the command with a non-zero exit code.

set -euo pipefail
cd "$(dirname "$0")"

# Resolve a host C compiler: prefer gcc, fall back to clang.
find_cc() {
  if command -v gcc >/dev/null 2>&1; then
    printf 'gcc\n'
  elif command -v clang >/dev/null 2>&1; then
    printf 'clang\n'
  else
    printf '\n'
  fi
}

# QuickJS (native/quickjs) is required by the native build (assembleDebug).
# Check it out at the exact commit recorded by this superproject and fail
# closed, so a stale or wrong submodule checkout cannot corrupt the build.
init_submodules() {
  echo "== verifying native/quickjs submodule =="
  if ! git submodule update --init --recursive; then
    echo "error: failed to initialize/update the native/quickjs submodule (required to build)" >&2
    return 1
  fi

  local expected actual
  expected="$(git rev-parse "HEAD:native/quickjs" 2>/dev/null || true)"
  actual="$(git -C native/quickjs rev-parse HEAD 2>/dev/null || true)"
  if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
    echo "error: native/quickjs is not at the superproject-recorded commit (expected ${expected:-unset}, got ${actual:-unset})" >&2
    return 1
  fi
  echo "  native/quickjs @ ${actual}"
}

native_test() {
  echo "== native C test runner =="
  local cc
  cc="$(find_cc)"
  if [ -z "$cc" ]; then
    echo "error: no host C compiler (gcc or clang) found; the native test suite is required" >&2
    return 1
  fi

  mkdir -p native/build
  "$cc" -O2 \
    -I native/include \
    native/src/pokemon_reader.c \
    native/src/pokemon_text.c \
    native/src/gba_memory_map.c \
    native/tests/test_pokemon_reader.c \
    -o native/build/test_runner
  ./native/build/test_runner
}

# The H&S runtime probe is a developer tool that `ci.sh` deliberately does not ship, but its
# `--selftest` mode is PURE: no ROM, no emulator core, no environment. It exercises the fail-closed
# state machines that the opponent voluntary-switch and player forced-replacement evidence depends
# on, so it belongs in the canonical gate rather than only in the probe's own build script. It is a
# separate suite from `native/build/test_runner`: that one covers the production reader, this one
# covers the evidence harness. Both are required, and the counts are reported separately so neither
# can be mistaken for the other.
tracker_selftest() {
  echo "== H&S runtime probe pure tracker selftests =="
  local cc
  cc="$(find_cc)"
  if [ -z "$cc" ]; then
    echo "error: no host C compiler found; the tracker selftest suite is required" >&2
    return 1
  fi

  mkdir -p native/build
  "$cc" -O2 \
    -I native/include \
    tools/hns-runtime-probe/runtime_battle_probe.c \
    native/src/pokemon_reader.c \
    native/src/pokemon_text.c \
    native/src/gba_memory_map.c \
    native/src/libretro_host.c \
    -ldl -lpthread \
    -o native/build/runtime_battle_probe_selftest
  ./native/build/runtime_battle_probe_selftest --selftest
}

gradle_test() {
  echo "== gradle unit tests =="
  ./gradlew testDebugUnitTest
}

gradle_build() {
  echo "== gradle assembleDebug =="
  init_submodules
  ./gradlew assembleDebug
}

gradle_release() {
  echo "== gradle assembleRelease (production signing required) =="
  init_submodules
  ./gradlew assembleRelease
}

case "${1:-all}" in
  test)    native_test; tracker_selftest; gradle_test ;;
  build)   gradle_build ;;
  all)     native_test; tracker_selftest; gradle_test; gradle_build ;;
  release) gradle_release ;;
  *)       echo "usage: $0 [test|build|all|release]" >&2; exit 2 ;;
esac
