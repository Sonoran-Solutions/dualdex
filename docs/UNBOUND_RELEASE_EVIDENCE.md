# Unbound release identity — incomplete evidence checkpoint

Task: `unbound-static`, issues [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60)
and [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26).
Starting revision: `d592f559e0eb97159513551fc05ea6a663532a9a`.

**BLOCKED:** this checkpoint does not establish the candidate's release identity.
The owner supplied a 33,554,432-byte candidate with SHA-256
`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`,
header `POKEMON FIRE` / `BPRE`. Those are owner-supplied assertions, not independently
measured results in this checkpoint. Do not register this diagnostic pin as trusted.

## Prepared inspector

[`unbound_identity.py`](../tools/rom-evidence/unbound_identity.py) reuses the existing
bounded local-file reader, digest helper and strict Gen III text decoder. It rejects
any candidate with a different length or SHA-256, reads the base header, computes
length/CRC32/SHA-1/SHA-256 and the GBA header checksum, looks for bounded Gen III
version candidates, and reports unchanged nonuniform 4 KiB base regions by digest.
It writes no ROM, copied table, save or dump. A numeric version candidate is only
**OBSERVED**, not a verified release label or evidence that the title screen uses it.

**NOT_VERIFIED:** candidate/base independent identities, exact version label,
unchanged base regions, and consistency with applying the official patch to FireRed
USA revision zero. The report at
[`unbound-identity.json`](../tools/rom-evidence/evidence/unbound-identity.json)
records these omissions explicitly; it is a blocked-run manifest, not tool output.

**NOT_VERIFIED:** publisher patch-to-ROM identity. The exact owner prerequisite is
the official English main-story Unbound patch for the version established from this
candidate (version currently unknown), obtained through Skeli's original publisher
release post, with its original filename, release/source URL and SHA-256. A similarly
named ROM, an unofficial modification, a translated patch or the Battle Frontier
demo does not close this gate. After establishing the version, record the precise
patch filename and source before reproducing it against the independently hashed
FireRed USA rev0 base. It would be dishonest to prescribe a particular versioned
patch file before the in-ROM label has been measured. No patch was downloaded.

## Execution blockers and handoff

1. **BLOCKED — private logging:** session writes are limited to this worktree and
   `/tmp`; the required `/home/dq/dualdex-private/unbound/unbound-static/` is outside
   those roots. No ROM/core inspection was launched and no private input was copied.
   A question requesting permission to keep only sanitized bounded logs in `/tmp`
   was submitted, but no approval had arrived at this checkpoint. Alternatively,
   provide a session writable private directory. Do not interpret silence as approval.
2. **BLOCKED — canonical ROM-free test gate:** the unchanged existing test
   `test_private_capture_guard_checkout_worktree_and_symlinks` rejects its temporary
   directory because the sandbox exposes a read-only `/tmp/.git` marker. Both
   permitted write roots therefore lie under Git markers. A writable temporary
   directory outside any Git checkout is needed; do not weaken the existing guard
   or test and do not remove an unowned marker.
3. **BLOCKED — checkpoint commit:** `git add` failed with read-only-filesystem error
   creating `/home/dq/Projects/dualdex/.git/worktrees/unbound-static/index.lock`.
   The session must permit ordinary writes to this worktree's Git metadata before
   the requested checkpoint commit can be made. No push or PR was attempted.

Once logging is authorized, the intended command is:

```bash
timeout --kill-after=5 600 python3 tools/rom-evidence/unbound_identity.py \
  --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba \
  --base '/home/dq/Downloads/1636 - Pokemon Fire Red (U)(Squirrels).gba' \
  > /home/dq/dualdex-private/unbound/unbound-static/identity.log 2>&1
```

Run with a 1 MiB output-file limit, inspect at most `head -c 1M`, preserve command
status, and only replace the blocked manifest with successful sanitized JSON.
This command has **NOT** run. The existing 60-minute validation budget remains;
no expensive suite, emulator, save or state was used.

## Validation actually performed

**OBSERVED:** Python 3 ROM-free command
`python3 -m unittest discover -s tools/rom-evidence -v`, under a 600-second timeout,
five-second kill grace and 1 MiB file-size limit, ran **70 tests in 0.408 seconds**:
69 passed, one existing environment-dependent test errored as described above.
All **five new `UnboundIdentityTest` tests passed**. The exit status was **1**, not
a successful suite result. Bounded log: `/tmp/unbound-static-tests-part1.log`.
There is no current-candidate `ci.sh` evidence; combined validation belongs to the
coordinator and was explicitly excluded from this task.

Production profiles, trusted hashes, native/Kotlin code, `ci.sh`, and the shared
ROM-evidence README are unchanged. Part 2 source leads and remaining work are in
[`UNBOUND_DATA_TEXT_EVIDENCE.md`](UNBOUND_DATA_TEXT_EVIDENCE.md).
