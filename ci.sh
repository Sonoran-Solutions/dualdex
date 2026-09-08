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
    native/tests/test_pokemon_reader.c \
    -o native/build/test_runner
  ./native/build/test_runner
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

case "${1:-all}" in
  test)  native_test; gradle_test ;;
  build) gradle_build ;;
  all)   native_test; gradle_test; gradle_build ;;
  *)     echo "usage: $0 [test|build|all]" >&2; exit 2 ;;
esac
