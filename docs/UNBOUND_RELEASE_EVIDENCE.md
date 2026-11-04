# Unbound exact-candidate release evidence

Task `unbound-static`; issues [#60](https://github.com/Sonoran-Solutions/dualdex/issues/60)
and [#26](https://github.com/Sonoran-Solutions/dualdex/issues/26). Measured 2026-11-04 UTC.
Base revision `d592f559e0eb97159513551fc05ea6a663532a9a`; this work continues
checkpoint `982224b`. Attempt 2 replaces the earlier unmeasured/blocked identity manifest.

**VERIFIED:** the identities below were measured from both owner-supplied files,
not copied from the task assertions. **NOT_VERIFIED:** official patch provenance,
numeric release label, runtime layout, and production support. The SHA-256 guard
is diagnostic only; no production profile or trusted-hash registration changed.

## Exact bytes

The sanitized [identity JSON](../tools/rom-evidence/evidence/unbound-identity.json)
is output from [unbound_identity.py](../tools/rom-evidence/unbound_identity.py).

| Property | Supplied FireRed base | Candidate |
|---|---|---|
| Status | VERIFIED measured bytes | VERIFIED measured bytes |
| Length | 16,777,216 | 33,554,432 |
| CRC32 | `dd88761c` | `4b3d4957` |
| SHA-1 | `41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc` | `b4776b82a4c7915d0fadeaa27e013523f99dfd94` |
| SHA-256 | `3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac` | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` |

**VERIFIED:** both GBA headers contain title `POKEMON FIRE`, game code `BPRE`,
maker `01`, fixed byte `0x96`, unit/device bytes zero, revision zero, and zero
reserved bytes. Stored and calculated complement checksums both equal `0x68`.
The JSON includes all these fields. Retaining this header does not identify a hack.

**OBSERVED:** 7,062,654 of the first 16,777,216 bytes agree with the supplied base;
924 nonuniform aligned 4 KiB blocks are unchanged. Six selected block offsets and
SHA-256 values are in the JSON. Uniform padding is excluded from the block count.
This is consistent with derivation from this revision-zero FireRed base, including
substantial retained code/data. It does **not** establish which patch was applied,
whether extra modifications were applied, or publisher authenticity.

## Release label and remaining patch prerequisite

**VERIFIED (static text only):** the bounded Gen III string at file offset
`0x01EEFE18` decodes to `Pokémon Unbound Version`. It is credits text; its use by a
running credits screen was not observed. The original BPRE header and this text
establish neither the numeric version nor a particular official patch.

**NOT_VERIFIED:** the owner's 2026-11-04 claim is **Unbound v2.1.1.1**. The bounded
plain Gen III numeric-version scan returned no candidates. Additional private
searches for the exact encoded `2.1.1.1` and ASCII numeric versions found no credible
numeric label. Therefore agreement **or disagreement** with the claim is unknown;
absence of plain text does not contradict it. Compressed graphics, dynamically
constructed text, title-screen rendering, and an exact source build remain unexamined.
This task does not invent a version label from the filename or source constants.

**VERIFIED (publisher web metadata only):** Skeli's
[original release thread](https://www.pokecommunity.com/threads/pok%C3%A9mon-unbound-completed.382178/)
labels its main-story download **Unbound 2.1.1.1**, updated November 8, 2022, with a
rival-battle crash hotfix. That link resolves to the landing page for
[Pokemon Unbound Official Patch 2.1.1+.zip](https://www.mediafire.com/file/1utikbeymc5b2by/Pokemon_Unbound_Official_Patch_2.1.1%252B.zip/file).
Only HTML metadata was read; no archive, patch or ROM was downloaded.

**BLOCKED — official reproduction:** the owner must supply the original patch
inside that exact publisher-linked **2.1.1+** archive (the release-thread label is
**2.1.1.1**), retaining the original inner filename, archive and patch SHA-256,
and the above source URL. The inner patch filename/hash cannot be verified without
the archive; it is not assumed to be `2.1.1.1.ups`. The Battle Frontier demo, a
translation, an audio/performance modification, or an unofficial mirror is not
this prerequisite. Validate the patch format and input checksums, apply in memory
to the measured base, and compare every candidate byte. Until then,
`patch_to_rom_identity` remains **NOT_VERIFIED**. A publisher label alone cannot
bind this candidate to that release.

## Reproduction and validation

All commands ran from this worktree with `TMPDIR` unchanged:
`/home/dq/dualdex-private/unbound/static-tmp`. Private output stays in
`$TMPDIR/private/`, outside the actual repository. Each ROM invocation used a
600-second wall-clock deadline, five-second kill grace, and `ulimit -f 2048`
(2 MiB per file in Bash); displays were bounded by `head -c 1048576`.
No emulator, core, save or state was loaded. No background job remains.

```bash
(ulimit -f 2048; timeout --kill-after=5 600 \
  python3 tools/rom-evidence/unbound_identity.py \
  --rom /home/dq/dualdex-private/unbound/unbound-candidate.gba \
  --base '/home/dq/Downloads/1636 - Pokemon Fire Red (U)(Squirrels).gba' \
  > "$TMPDIR/private/identity-final.json" 2> "$TMPDIR/private/identity-final.err")
```

**VERIFIED:** identity inspection exited 0 (under two seconds). The report binds
its inspector SHA-256. The data report and validation details are in
[UNBOUND_DATA_TEXT_EVIDENCE.md](UNBOUND_DATA_TEXT_EVIDENCE.md).

**BLOCKED:** full ROM-free discovery ran 80 tests in 0.208 seconds: 79 passed,
one unchanged private-directory-guard test errored. All 15 Unbound tests passed.
The sandbox also exposes a read-only synthetic `.git` directory at `$TMPDIR/.git`,
despite the corrected directory setting. Python confirms it uses this exact TMPDIR;
`git -C "$TMPDIR" rev-parse --show-toplevel` reports no actual Git repository.
The guard intentionally treats the marker conservatively. No test was weakened,
no marker was removed, and no unchanged failed suite was blindly retried.
Coordinator prerequisite: rerun the same discovery in an environment without this
synthetic ancestor marker. This is a failed required gate, not a successful suite.

No `ci.sh test/build/all`, commit, push or PR was attempted in attempt 2; the
coordinator owns combined validation and commits. Production files and the shared
ROM-evidence README are unchanged.
