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
# Gradle is the authoritative pass/fail for the app; the native runner is a
# host-toolchain check and is best-effort if a C compiler is unavailable.

set -euo pipefail
cd "$(dirname "$0")"

native_test() {
  echo "== native C test runner =="
  if ! command -v gcc >/dev/null 2>&1 && ! command -v clang >/dev/null 2>&1; then
    echo "  (no host C compiler found; skipping native tests)"
    return 0
  fi
  gcc -O2 -I native/include native/src/pokemon_reader.c native/src/pokemon_text.c native/tests/test_pokemon_reader.c -o native/test_runner
  ./native/test_runner
}

gradle_test() {
  echo "== gradle unit tests =="
  ./gradlew testDebugUnitTest
}

gradle_build() {
  echo "== gradle assembleDebug =="
  ./gradlew assembleDebug
}

case "${1:-all}" in
  test)  native_test; gradle_test ;;
  build) gradle_build ;;
  all)   native_test; gradle_test; gradle_build ;;
  *)     echo "usage: $0 [test|build|all]" >&2; exit 2 ;;
esac
