#!/usr/bin/env bash
#
# Regression: the source-check bootstrap in ci.sh must fail closed when any
# required regeneration command fails, even when the pinned upstream checkout
# already has valid (gitignored) generated headers that would let later
# verification stages succeed.
#
# Background: the bootstrap runs in contexts where bash suppresses errexit
# (under `if !` / `||`); an inner `set -e` does not re-enable it there, so
# relying on `set -e` alone silently ignores an early failure whenever a later
# command succeeds. Every required operation must propagate failure
# explicitly. This test drives the PRODUCTION path -- it sources ci.sh and
# runs the real source_check / regen_upstream_headers against the real pinned
# checkout -- rather than a test-local copy of the orchestration. ci.sh's
# source_check invokes it with the real host compiler and pinned checkout.
#
# Controls:
#   1. Negative (production path): source_check with a host compiler stub that
#      fails on first use must return non-zero, invoke the compiler exactly
#      once (proving no later generation command ran), and must NOT reach the
#      data-pack verification or Gradle stages.
#   2. Positive (bootstrap block, same stub mechanism): with a pass-through
#      compiler the bootstrap must regenerate every provisioning artifact and
#      succeed, and must leave the pinned checkout's tracked files untouched.
set -euo pipefail
cd "$(dirname "$0")/../.."

source ./ci.sh
# Prevent any nested source_check (this test drives one) from recursing into
# this regression again.
export DUALDEX_BOOTSTRAP_REGRESSION_ACTIVE=1

die() { printf 'test_bootstrap_fail_closed: ERROR: %s\n' "$*" >&2; exit 1; }
note() { printf 'test_bootstrap_fail_closed: %s\n' "$*"; }

real_cc="$(realpath "${1:?usage: test_bootstrap_fail_closed.sh <real-host-c++> <pinned-upstream-dir>}")"
upstream="$(realpath "${2:?usage: test_bootstrap_fail_closed.sh <real-host-c++> <pinned-upstream-dir>}")"
export HNS_UPSTREAM_DIR="$upstream"

# A preprocessor stand-in satisfies source_check's fail-closed preprocessor
# gate ahead of the bootstrap; the negative control must stop at the bootstrap
# and never preprocess real upstream sources with it.
export ARM_CPP=/usr/bin/cpp
[ -x "$ARM_CPP" ] || die "$ARM_CPP not found; a host cpp is required for the preprocessor gate stand-in"

generated_headers=(
  include/constants/map_groups.h
  include/constants/layouts.h
  include/constants/map_event_ids.h
  include/constants/region_map_sections.h
  src/data/map_group_count.h
  src/data/tutor_moves.h
  src/data/pokemon/teachable_learnsets.h
)
for header in "${generated_headers[@]}"; do
  [ -f "$upstream/$header" ] || \
    die "pre-existing generated header missing: $upstream/$header (this regression requires an already-built pinned checkout)"
done

# Host compiler stub that records every invocation and optionally fails.
stub_dir="$(mktemp -d)"
invocations="$(mktemp)"
neg_log="$(mktemp)"
pos_log="$(mktemp)"
cleanup() { rm -rf "$stub_dir"; rm -f "$invocations" "$neg_log" "$pos_log"; }
trap cleanup EXIT

write_stub() {
  cat > "$stub_dir/g++" <<STUB
#!/usr/bin/env bash
printf '%s\n' "\$*" >> '$invocations'
$1
STUB
  chmod +x "$stub_dir/g++"
}

# --- Control 1: negative, production path -------------------------------------
write_stub 'exit 1'
if PATH="$stub_dir:$PATH" source_check >"$neg_log" 2>&1; then
  sed -n '1,120p' "$neg_log" >&2
  die "source_check succeeded despite an injected early bootstrap failure"
fi
inv_count="$(grep -c . "$invocations" || true)"
[ "$inv_count" -eq 1 ] || \
  die "bootstrap continued past the first failure (compiler invoked ${inv_count}x, expected 1)"
if grep -q "Verification PASSED" "$neg_log"; then
  die "data-pack verification ran despite the injected bootstrap failure"
fi
if grep -Eq '^BUILD (SUCCESSFUL|FAILED)' "$neg_log"; then
  die "Gradle ran despite the injected bootstrap failure"
fi
for header in "${generated_headers[@]}"; do
  [ -f "$upstream/$header" ] || \
    die "pre-existing generated header vanished during the negative control: $header"
done
note "negative control OK: source_check failed closed at the bootstrap (compiler invoked once; verification and Gradle unreached)"

# --- Control 2: positive, bootstrap block with a working compiler -------------
trap - RETURN   # source_check may leave a RETURN trap behind; do not inherit it
: > "$invocations"
write_stub "exec '$real_cc' \"\$@\""
pos_prov="$(mktemp -d)"
if ! PATH="$stub_dir:$PATH" regen_upstream_headers "$stub_dir/g++" "$pos_prov" >"$pos_log" 2>&1; then
  sed -n '1,120p' "$pos_log" >&2
  rm -rf "$pos_prov"
  die "bootstrap block failed with a working compiler (false failure)"
fi
for artifact in jsonproc mapjson all_tutors.json all_teaching_types.json; do
  [ -e "$pos_prov/$artifact" ] || \
    die "expected provisioning artifact missing after successful bootstrap: $artifact"
done
[ -z "$(git -C "$upstream" status --porcelain --untracked-files=no)" ] || \
  die "successful bootstrap modified tracked files in $upstream"
rm -rf "$pos_prov"
note "positive control OK: bootstrap regenerated all provisioning artifacts and succeeded"
