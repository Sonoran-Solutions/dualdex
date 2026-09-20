#!/usr/bin/env bash
#
# Sonoran canonical DualDex CI contract (ORCH-090).
#
# One command vocabulary shared by humans, Antigravity, agents, and GitHub
# Actions. Run from the repo root:
#
#   ./ci.sh test     # native reader suite + H&S tracker selftests +
#                    # QuickJS calculator suite + data-pack generator tests
#                    # + Kotlin unit tests
#   ./ci.sh build    # assemble the debug APK
#   ./ci.sh all      # test then build (default)
#   ./ci.sh release  # assemble the production-signed release APK (requires
#                    # external signing credentials; fails closed if missing)
#
# The contract is deterministic, non-interactive, and fail-closed: every
# authoritative check is required. A missing host C compiler, a missing or
# stale QuickJS submodule, a failing native test, a failing calculator test, a
# failing Gradle test, or a failing build all fail the command with a non-zero
# exit code.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

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

# Generated map data integrity. This is mandatory and self-contained: it reads
# only the committed generated file, so it needs no ROM, no network, and no
# upstream checkout. It proves the pinned H&S 2.0.5 mapping has not been
# hand-edited or left stale, because `--verify-digests` re-derives the mapping
# digest from the file and compares it with the digest the file records.
#
# Byte-for-byte regeneration against the pinned upstream checkout is a separate,
# explicit developer command (`--check`) and deliberately not required here.
hns_map_data_check() {
  echo "== H&S 2.0.5 generated map data integrity =="
  python3 tools/hns-map-data/generate_hns_map_data.py --verify-digests
}

# H&S data-pack generator tests. Mandatory and self-contained: they drive the
# real extraction code (extract_abilities incl. run_cpp and the table
# cross-checks) against tiny synthetic fixtures through a stub preprocessor, so
# they need no upstream checkout, no ARM toolchain, no ROM, and no network. They
# pin the fail-closed enum-parser contract: an assignment the parser cannot
# resolve (unresolved alias, parenthesized initializer, arithmetic) must raise,
# never invent a sequential ID, and the ABILITIES_COUNT_GEN* anchor pattern of
# the pinned header must resolve explicitly instead of by counter coincidence.
hns_generator_test() {
  echo "== H&S data-pack generator tests =="
  (cd tools/hns-data-pack && python3 -m unittest test_generate_hns_data_pack -v)
  echo "== H&S type-system generator tests =="
  (cd tools/hns-type-system && python3 -m unittest test_generate_hns_type_system -v)
  echo "== H&S item-catalogue generator tests =="
  (cd tools/hns-items && python3 -m unittest test_generate_hns_items -v)
}

# Locate the ARM preprocessor the data-pack generator drives. Fail-closed: the
# source validation job refuses to run its data-pack verification without one
# instead of silently skipping it.
find_hns_cpp() {
  if [ -n "${ARM_CPP:-}" ] && [ -x "${ARM_CPP}" ]; then
    printf '%s\n' "$ARM_CPP"
    return 0
  fi
  if command -v arm-none-eabi-cpp >/dev/null 2>&1; then
    command -v arm-none-eabi-cpp
    return 0
  fi
  local candidate
  for candidate in \
    /opt/devkitpro/devkitARM/bin/arm-none-eabi-cpp \
    /usr/bin/arm-none-eabi-cpp \
    "$HOME"/opt/*/bin/arm-none-eabi-cpp \
    "$HOME"/devkit*/devkitARM/bin/arm-none-eabi-cpp \
    /opt/*/bin/arm-none-eabi-cpp; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

# The QuickJS damage calculator is production code: the same js_calc_engine.c
# and the same committed bundle (app/src/main/assets/calc_bundle.js) the APK
# ships. Compiling it for the host against the pinned QuickJS sources lets the
# canonical gate EXECUTE damage calculations instead of only compiling them for
# Android. This is a third, separate suite (the reader runner is
# native/build/test_runner, the evidence harness is
# native/build/runtime_battle_probe_selftest): its fixture expectations are the
# independently derived Gen III goldens documented in
# native/tests/test_js_calc.c and docs/QUICKJS_CALCULATOR_TESTS.md.
#
# Sources, defines, and link libraries mirror app/src/main/cpp/CMakeLists.txt so
# host and Android compile the same engine from the same pinned dependency.
calc_test() {
  echo "== QuickJS damage calculator suite (host) =="
  local cc
  cc="$(find_cc)"
  if [ -z "$cc" ]; then
    echo "error: no host C compiler (gcc or clang) found; the calculator suite is required" >&2
    return 1
  fi

  # The calculator links QuickJS, so the pinned submodule must be initialized
  # and verified at the recorded commit BEFORE compiling. A fresh checkout can
  # therefore run `./ci.sh test` without `./ci.sh build` as a setup step.
  init_submodules

  mkdir -p native/build
  "$cc" -O2 -std=c11 \
    -D_GNU_SOURCE \
    -DCONFIG_VERSION='"2024-01-13"' \
    -DCONFIG_BIGNUM \
    -fno-strict-aliasing \
    -I native/include \
    -I native/quickjs \
    native/quickjs/quickjs.c \
    native/quickjs/libregexp.c \
    native/quickjs/libunicode.c \
    native/quickjs/dtoa.c \
    native/src/js_calc_engine.c \
    native/tests/test_js_calc.c \
    -lm -ldl -lpthread \
    -o native/build/test_js_calc
  ./native/build/test_js_calc
}

gradle_test() {
  echo "== gradle unit tests (self-contained: no ROM, no network) =="
  ./gradlew testDebugUnitTest
}

# Explicit source validation against the pinned Heart & Soul 2.0.5 checkout.
#
# Deliberately separate from `test`: the canonical gate must stay self-contained, so
# the upstream cross-check lives here and is run by its own CI job. This command
# FAILS when the pinned checkout is missing, unreachable or at the wrong revision --
# it never degrades to a silent skip. It needs no ROM and no emulator.
# Regenerate the pinned upstream's gitignored build-time artifacts
# (include/constants/{map_groups,layouts,map_event_ids,region_map_sections}.h,
# src/data/{map_group_count,tutor_moves}.h and
# src/data/pokemon/teachable_learnsets.h) in place within $HNS_UPSTREAM_DIR,
# using upstream's own committed tools from upstream's own committed JSON
# sources. $1 = host C++ compiler binary, $2 = provisioning output directory.
#
# Every required operation propagates failure explicitly. Callers invoke this
# in contexts where bash suppresses errexit for the whole command list (`if !`,
# `||` chains); an inner `set -e` does NOT re-enable errexit there, so a plain
# `set -e` block would silently ignore an early failure whenever a later
# command succeeded (e.g. a dev checkout with pre-existing generated headers).
# Do not replace the explicit `|| exit 1` propagation with set -e.
regen_upstream_headers() {
  local cc_bin="$1" prov_build="$2"
  local upstream="${HNS_UPSTREAM_DIR:?HNS_UPSTREAM_DIR must be set}"
  (
    cd "$upstream" || exit 1
    for required in \
      tools/mapjson/mapjson.cpp tools/mapjson/json11.cpp \
      tools/jsonproc/jsonproc.cpp tools/jsonproc/inja.hpp \
      tools/learnset_helpers/make_tutors.py \
      tools/learnset_helpers/make_teaching_types.py \
      tools/learnset_helpers/make_teachables.py \
      src/data/pokemon/all_learnables.json \
      src/data/pokemon/special_movesets.json \
      data/maps/map_groups.json data/layouts/layouts.json \
      src/data/region_map/region_map_sections.json \
      src/data/region_map/region_map_sections.constants.json.txt; do
      if [ ! -e "$required" ]; then
        echo "error: $required missing from pinned upstream checkout; it is required" >&2
        echo "       to generate the build-time headers the source validation preprocesses" >&2
        exit 1
      fi
    done
    "$cc_bin" -O2 -std=c++17 -w -I tools/jsonproc tools/jsonproc/jsonproc.cpp \
      -o "$prov_build/jsonproc" || exit 1
    "$cc_bin" -O2 -std=c++17 -w tools/mapjson/json11.cpp tools/mapjson/mapjson.cpp \
      -o "$prov_build/mapjson" || exit 1
    "$prov_build/mapjson" groups hns data/maps/map_groups.json data/maps/*/*.json \
      data/maps include/constants || exit 1
    "$prov_build/mapjson" layouts hns data/layouts/layouts.json data/layouts include/constants || exit 1
    "$prov_build/mapjson" event_constants emerald data/maps/*/*.json \
      include/constants/map_event_ids.h || exit 1
    "$prov_build/jsonproc" src/data/region_map/region_map_sections.json \
      src/data/region_map/region_map_sections.constants.json.txt \
      include/constants/region_map_sections.h || exit 1
    python3 tools/learnset_helpers/make_tutors.py "$prov_build/all_tutors.json" || exit 1
    python3 tools/learnset_helpers/make_teaching_types.py \
      "$prov_build/all_teaching_types.json" || exit 1
    python3 tools/learnset_helpers/make_teachables.py --build POKEMON_HNS \
      "$prov_build" || exit 1
  )
}

source_check() {
  echo "== H&S 2.0.5 source validation against the pinned upstream checkout =="
  local upstream="${HNS_UPSTREAM_DIR:-}"
  if [ -z "$upstream" ]; then
    for candidate in "upstream-hns/pokehns-expansion" "../upstream-hns/pokehns-expansion"; do
      if [ -f "$candidate/data/maps/map_groups.json" ]; then
        upstream="$candidate"
        break
      fi
    done
  fi
  if [ -z "$upstream" ]; then
    echo "error: pinned Heart & Soul upstream checkout not found; set HNS_UPSTREAM_DIR" >&2
    echo "       (a checkout of 1f42b74dff0e9fe942419845d040663dd829a973 / Release-v2.0.5 is required)" >&2
    return 1
  fi
  export HNS_UPSTREAM_DIR="$upstream"

  # 1. The generated table must regenerate byte-for-byte from the pinned source.
  python3 tools/hns-map-data/generate_hns_map_data.py \
    --upstream-dir "$upstream" --check

  # 2. The committed Kotlin data pack (species, moves, AND the ability catalogue
  #    with its per-species slot declarations) must regenerate byte-for-byte from
  #    the pinned source. This is the check that actually compares the committed
  #    ability catalogue against upstream; it needs the ARM preprocessor the
  #    generator drives, so locate it fail-closed rather than skipping silently.
  local cpp_bin
  if ! cpp_bin="$(find_hns_cpp)"; then
    echo "error: arm-none-eabi-cpp not found; the data-pack verification is required." >&2
    echo "       Install gcc-arm-none-eabi (or devkitARM) or set ARM_CPP." >&2
    return 1
  fi
  # Materialize the pinned upstream's generated headers. The gitignored build
  # artifacts included by the preprocessed sources (include/constants/{
  # map_groups,layouts,map_event_ids,region_map_sections}.h and src/data/{
  # map_group_count,tutor_moves}.h plus src/data/pokemon/teachable_learnsets.h)
  # are produced by the upstream build's own committed tools from committed JSON
  # sources within the checkout: tools/mapjson, tools/jsonproc and the
  # tools/learnset_helpers scripts. A dev checkout that has been built already
  # has them; a sparse CI checkout does not, so build the tools with the host
  # C++ compiler and regenerate exactly those files. Fail closed: refuse if the
  # sources for the tools or their JSON inputs are missing, and refuse to run if
  # generation leaves the checkout's tracked files modified. This is not faking
  # headers: it is upstream's own toolchain generating upstream's own headers
  # from upstream's own data.
  echo "== regenerating pinned upstream build-time headers (mapjson/jsonproc) =="
  local cc_bin
  cc_bin="$(command -v g++ || command -v clang++)" || {
    echo "error: no host C++ compiler (g++ or clang++) found; building mapjson/jsonproc" >&2
    echo "       to generate the pinned upstream's include/constants headers is required" >&2
    return 1
  }
  local prov_build preflight
  prov_build="$(mktemp -d)"
  preflight="$(mktemp)"
  trap 'rm -rf "$prov_build"; rm -f "$preflight"' RETURN
  if ! regen_upstream_headers "$cc_bin" "$prov_build"; then
    echo "error: regenerating the pinned upstream's build-time headers failed;" >&2
    echo "       refusing to validate against a possibly stale provisioning" >&2
    return 1
  fi
  if [ -n "$(git -C "$upstream" status --porcelain --untracked-files=no)" ]; then
    echo "error: regenerating upstream build-time headers modified tracked files in" >&2
    echo "       $upstream; refusing to validate from a dirty checkout" >&2
    return 1
  fi

  # 1b. The challenge-settings layout table must regenerate byte-for-byte from the pinned
  # source, compiled with the pinned ARM toolchain (same toolchain/flags as §10 of the
  # compatibility evidence). This is the source-check for the runtime challenge-settings
  # reader: sizeof/offsetof/bit positions for SaveBlock3.challengeSettings. It compiles the
  # pinned global.h, so it must run AFTER the build-time headers above are materialized.
  echo "== challenge-settings layout verification (pinned upstream) =="
  python3 tools/hns-layout/generate_hns_challenge_layout.py \
    --upstream-dir "$upstream" --verify

  # 1c. The live BattlePokemon layout table (ability offset/width, types
  #     offset/count/width, enum domains) must regenerate byte-for-byte from the
  #     pinned source, compiled with the same pinned ARM toolchain. This is the
  #     source-check for the runtime live-battler reader; it compiles the pinned
  #     global.h, so it must run AFTER the build-time headers above are
  #     materialized.
  echo "== battle-pokemon layout verification (pinned upstream) =="
  python3 tools/hns-layout/generate_hns_battle_pokemon_layout.py \
    --upstream-dir "$upstream" --verify

  # 1d. The H&S type-system matrix and Fairy mappings must regenerate byte-for-byte from the pinned source.
  echo "== type-system matrix and fairy mappings verification (pinned upstream) =="
  python3 tools/hns-type-system/generate_hns_type_system.py \
    --upstream-dir "$upstream" --verify

  # 1e. The exact held-item identity catalogue (enum Item + gItemsInfo) must
  #     regenerate byte-for-byte from the pinned source. This is the source-check
  #     for the H&S item domain and per-item hold effects (Gap C3); it needs the
  #     ARM preprocessor because the item table's initializers depend on the
  #     build's own config macros.
  echo "== held-item catalogue verification (pinned upstream) =="
  python3 tools/hns-items/generate_hns_items.py \
    --upstream-dir "$upstream" --cpp-bin "$cpp_bin" --verify

  # Regression for the bootstrap's error propagation itself (this is the
  # scenario the regeneration block must guard against: a dev checkout with
  # pre-existing generated headers lets later commands succeed, so an early
  # failure is only caught if every required operation propagates explicitly).
  # Exercises the production path against the real pinned checkout: an
  # injected early compiler failure must fail the check before the data-pack
  # verification or Gradle stages, and a working compiler must regenerate all
  # provisioning artifacts. See tools/ci/test_bootstrap_fail_closed.sh.
  # Re-entry guard: the regression test itself drives source_check; the guard
  # suppresses ONLY this recursive invocation of the regression suite. It must
  # never skip the mandatory stages below (target-header preflight, data-pack
  # --verify, Kotlin upstream validation): a test-recursion flag may prevent
  # another test invocation, it must not prevent the verification this command
  # promises to perform.
  if [ -z "${DUALDEX_BOOTSTRAP_REGRESSION_ACTIVE:-}" ]; then
    echo "== source-check bootstrap fail-closed regression =="
    DUALDEX_BOOTSTRAP_REGRESSION_ACTIVE=1 \
      tools/ci/test_bootstrap_fail_closed.sh "$cc_bin" "$upstream" || return 1
  fi

  echo "  data-pack preprocessor: $cpp_bin"

  # Preflight: the ARM preprocessor must resolve the real target C-library
  # headers (<string.h> via include/global.h). A bare gcc-arm-none-eabi install
  # without libnewlib-arm-none-eabi (or devkitARM without newlib) fails exactly
  # here, so fail closed with the remedy instead of a fatal include error in the
  # middle of the generator run.
  printf '#include <string.h>\n' > "$preflight"
  if ! "$cpp_bin" -E "$preflight" >/dev/null 2>&1; then
    echo "error: $cpp_bin cannot preprocess <string.h>; install the target C-library" >&2
    echo "       headers (e.g. libnewlib-arm-none-eabi on Ubuntu, or devkitARM's newlib)" >&2
    echo "       or point ARM_CPP at a toolchain that resolves them." >&2
    return 1
  fi
  rm -f "$preflight"

  python3 tools/hns-data-pack/generate_hns_data_pack.py \
    --upstream-dir "$upstream" --cpp-bin "$cpp_bin" --verify

  # 3. The Kotlin tests must re-derive the mapping from that source independently,
  #    and must fail (not skip) if it is missing or wrong.
  ./gradlew testDebugUnitTest -Pdualdex.hns.upstreamCheck=true
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

# Dispatch only when ci.sh is executed. When it is sourced as a library (e.g.
# by tools/ci/test_bootstrap_fail_closed.sh, which drives source_check and
# regen_upstream_headers directly), the command vocabulary must not run.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
case "${1:-all}" in
  test)         native_test; tracker_selftest; calc_test; hns_map_data_check; hns_generator_test; gradle_test ;;
  source-check) source_check ;;
  build)        gradle_build ;;
  all)          native_test; tracker_selftest; calc_test; hns_map_data_check; hns_generator_test; gradle_test; gradle_build ;;
  release)      gradle_release ;;
  *)            echo "usage: $0 [test|source-check|build|all|release]" >&2; exit 2 ;;
esac
fi
