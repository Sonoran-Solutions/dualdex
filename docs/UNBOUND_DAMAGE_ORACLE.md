# Unbound damage oracle: source-backed golden provenance

**VERIFIED:** the committed [corpus](../tools/unbound-damage-oracle/corpus.json)
contains **579 cases × 16 rolls = 9,264 numeric damage values** produced by compiling
private CFRU `src/damage_calc.c` directly with the host C compiler. The checked
source commit is `b637a27898b14e25dd24d0f69a3e302f0069deb8`, tree
`aef5036b507a6649feb6c80cc496fb4ee314575b`; the damage file SHA-256 is
`c24aae2e8cb02adf1c91f9ad27ffcd0ff24e643977eb209124f34e1d8543768f`.
The corpus records its own harness SHA-256, compiler, host, flags and observed
configuration constants. No expected values come from the bundled calculator,
Smogon, Kotlin, a ROM, or a transcription of the damage formula.

**UNVERIFIED:** the candidate claimed to be Unbound v2.1.1.1 was built from this
commit, with this configuration, tables, or private hooks. The candidate SHA-256
`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` is measured,
but [release evidence](UNBOUND_RELEASE_EVIDENCE.md) leaves official patch
reproduction and the numeric version label unverified. The retained FireRed header
and observed credits text identify neither this source commit nor its build flags.
The [calculator plan](UNBOUND_CALCULATOR_PLAN.md), sections 4 and 5, requires further
binary/configuration correspondence and exact-ROM execution before shipping support.
This corpus is **host-compiled-source-only / SOURCE_APPROXIMATE for that ROM**.
It does not satisfy the plan's primary exact-ROM Thumb oracle or runtime cross-reference.

## Executed engine boundary

The [adapter](../tools/unbound-damage-oracle/host.c) includes the private file and
headers in place. It calls the original `atk05_damagecalc`, `atk06_typecalc`, and
`ApplyRandomDmgMultiplier`; this executes `CalculateBaseDamage`, `GetBasePower`,
`AdjustBasePower`, the source STAB/type pipeline and private `gTypeEffectiveness`.
The private implementation, headers and type chart are never copied into this repo.

The harness forces the requested critical outcome through `gNewBS->criticalMultiplier`;
it tests critical damage/stage/screen behavior, not critical odds or prevention.
It supplies every RNG low nibble exactly once, storing results in ascending multiplier
order **85..100**. An engine no-effect flag is normalized to zero HP damage for
immunities; other hits retain the engine's minimum-one rule. Results are uncapped
selected-hit damage, not HP loss clamped to the target's remaining HP. It does not
run move accuracy, battle scripts beyond the damage/type commands, `AdjustDamage`
(sash/sturdy/protect/raid/endurance), or post-hit effects.

The original default configuration is used with **no added `-D` mechanic flags**.
The pinned configuration leaves `OLD_CRIT_DAMAGE`, `OLD_MOVE_SPLIT`, `BADGE_BOOSTS`,
`OLD_ATE_BOOST`, `OLD_GEM_BOOST`, `OLD_TERRAIN_BOOST`, `PORTAL_POWER`, and `FROSTBITE`
undefined. `UNBOUND` is not injected. The source-observed critical multiplier is
15/10, Fairy is 23, burn is 16, ordinary rain/sun are 1/32, and screens are 1/2.
These constants are checked against the schema by the compiled executable. None
is claimed to be verified against the candidate ROM.

## Inputs and coverage

All scenario IDs, inputs and damage vectors are numeric. Field names describe
source-domain operands. No species/move/item names, ROM text, copyrighted engine
source, ROMs, saves, or states are distributed. A synthetic record occupies source
move slot 1 with ordinary-hit effect, selected-target class, explicit power/type/category
and zero special-move flags. **Slot 1 does not claim the shipping move's actual power,
type, category, or identity.** This deliberately isolates ordinary fixed-power damage
from unverified move tables. Held-item inputs are **active hold-effect IDs**, not item
IDs; synthetic item slots 1/2 route to those effects with quality zero. Species 1 is
a placeholder; none of the admitted effects depends on species. The future adapter
must separately prove shipping table identities and effective item/ability state.

| Operand | Domain / coverage |
|---|---|
| Topology | Singles, attacker bank 0 and defender bank 1; other battlers absent |
| Level / power / raw stats | Schema 1..100 / 1..150 / 1..999; rounding cases include levels 1, 5, 37, 50, 100 and powers 1, 15, 59, 60, 61, 79, 80, 81, 150 |
| Categories | Physical 0 and Special 1; raw Attack/SpA and Defense/SpD differ to expose routing errors |
| Stages | Source-domain 0..12 (neutral 6), every stage for each of the four relevant stats, crit off/on and mixed-stage compositions |
| Types | All 18×18 mono matchups, including Fairy 23 and modern Steel interactions; dual-type ¼×/4×/immune and ordering controls; STAB absent, either slot, duplicated slot |
| Weather | 0, ordinary rain 1, ordinary sun 32; Fire/Water/neutral controls in both categories |
| Screens / status | Reflect 1, Light Screen 2, both 3; relevant/irrelevant category controls; burn 16 off/on, crit off/on and compositions |
| Attacker abilities | None 0, Huge Power 37, Hustle 55, Guts 62, Technician 82 (59/60/61 threshold), Sniper 85, Adaptability 88, Tinted Lens 99 |
| Defender abilities | None 0, Thick Fat 47, Heatproof 97, Fur Coat 148, Ice Scales 248, including inactive category/type controls |
| Active hold effects | Attacker: none 0, Life Orb 74 (BP pass), Expert Belt 92, Muscle Band 112, Wise Glasses 119; defender: none 0, Assault Vest 70 |

The generated matrix deduplicates exact operand sets deterministically. No terrain,
third type, suppression, contact-dependent ability, dynamic move, spread move,
stateful item, aura holder, custom ruleset, gimmick or raid behavior is admitted.
Schema bounds keep all arithmetic in the tested non-overflow range. Adding one of
these mechanics requires extending both the boundary and the matrix deliberately.

## Stubbed globals and helper assumptions

Every scenario resets the live battlers, new battle state, battle struct, protect,
disable, status and side arrays. Only the listed inputs vary; no stale state is used.
The verifier also runs the complete matrix in reverse order and requires identical
per-case outputs.

| Host storage / stub | Value and reason |
|---|---|
| `gBattleMons[4]` | First two contain supplied raw stats/types/stages/status/ability; `type3=20` blank, HP=maxHP=300, speed=100; others zero. No species-derived stat construction is involved. |
| `gBankAttacker`, `gBankTarget`, `gBattlersCount`, `gBattleTypeFlags`, `gCurrentMove` | 0, 1, 2, 0 and synthetic slot 1; selects the ordinary Singles live-bank path. |
| `gBattleMoves`, `gSpecialMoveFlags` | Host move record plus zero special flags, as described above. Engine arithmetic and chart stay private and unchanged. |
| `gNewBS`, `gBattleStruct` | Allocated host structures, cleared each case; only requested crit multiplier is set. Volatiles, terrain timers, consumed gems, parental bond, Z/Max state, aura/special-rule flags and counters are absent. The engine writes its normal damage/result/dynamic-type fields. |
| `gBattleWeather`, `gSideStatuses` | Supplied ordinary weather and defender screen flags; attacker side clear. |
| `gTerrainType`, `gBitTable` | Replace absolute GBA addresses with host terrain zero and bit masks 1/2/4/8. Reading GBA addresses on a host is invalid. |
| `gStatStageRatios` | Authored standard 13-stage ratios, `(2,8)` through `(2,2)` through `(8,2)`; neutral at index 6. This substitutes the external vanilla ratio table; candidate-ROM table parity remains unverified. |
| `gProtectStructs`, `gDisableStructs`, `gStatuses3`, `gSideTimers`, `gBattleScripting` | Zeroed/no volatile modifiers, helping hand, delayed state, hazards or stored damage. |
| `gDynamicBasePower`, `gMultiHitCounter`, `gMoveResultFlags` | Zero before each case; no override/repeated hit. |
| `gBattlescriptCurrInstr` | Points to a small host buffer so the two engine command increments are valid; no script bytes are executed. |
| `gBattleMoveDamage`, `gBattleMovePower` | Host storage written by the engine; each random trial restores the engine's pre-random damage. |
| `gBattleResources`, `gMain`, `gPlayerParty`, `gEnemyParty`, `gChosenMovesByBanks`, `gLastHitByType`, `gLastLandedMoves`, `gLastPrintedMoves`, `gLastUsedAbility`, `gAbsentBattlerFlags`, `gBattleCommunication` | Zero-initialized link storage; party/AI/resource-dependent paths are outside the admitted boundary. |
| `gNaturalGiftTable`, `gFlingTable`, `gDynamaxMovePowers`, `gAlternateSpeciesSizeTable` | Link-only zero/sentinel records for excluded variable-power/size/gimmick paths, never selected by admitted inputs. |

Small helper adapters provide unsigned division/min/max, fixed RNG operands,
bank-to-side identity, move category/target lookup, raw speed, type membership,
Flying grounding and synthetic hold-effect lookup. Weather is effective because
suppression is excluded. Screen bypass, ability ignoring/Mold Breaker, aura queries,
inverse/Scalemons/shadow-shield special battles, Dynamax, Z/Max moves, Wonder Room,
Ion Deluge, magnetism and Mud/Water Sport return their explicitly neutral values.
Grounding models only Flying typing; Levitate, grounding items and volatiles are not
accepted. Seventy-two other linked helpers alias a terminating diagnostic in
[unsupported.h](../tools/unbound-damage-oracle/unsupported.h), so accidentally reaching
an unimplemented path fails instead of silently inventing an answer.

## Reproduction and future calculator consumption

Commands and execution budgets are in the [tool README](../tools/unbound-damage-oracle/README.md).
From the repository root, with isolated `TMPDIR` set:

```bash
python3 tools/unbound-damage-oracle/oracle.py
python3 -m unittest discover -s tools/unbound-damage-oracle -p 'test_*.py' -v
python3 tools/unbound-damage-oracle/oracle.py --verify --source /private/path/cfru
```

The ROM-free check validates canonical schema, exact matrix identity, source commit,
harness hash, numeric domains, 16 monotone rolls and the final 85..100 random mapping.
It cannot prove engine correctness without source. `--verify` recompiles a clean
checkout at the pinned commit and compares **every case and every roll**, forward
and reversed; missing private source explicitly skips, while invalid sources,
build failures or mismatches fail. `--regenerate` writes outputs atomically from that
same execution path and never skips. Source identity/cleanliness is checked again
after execution. Compiler identity records the original environment, and a verifier
using another compiler still has to reproduce every numeric result.

Validation on base worktree revision `1c16ced` plus these new files: the ROM-free
checker and six unit tests pass; private regeneration and verification reproduce
all 579 cases / 9,264 rolls in both orders. The unit tests include 17 corrupt-corpus
rejections, full type/stage coverage, Fairy immunity, minimum damage, either-slot
STAB and a separately hand-derived ordinary-hit canary. Generating environment:
Linux x86-64, GCC 13.3.0. Each private build/verify completes in approximately one
second. Successful runs remove their scratch binaries and job manifests; no
background process remains. CI/app/calculator files are unchanged; the integration
coordinator owns the combined `./ci.sh all` gate, per the task's common rules.

A later calculator slice should map each numeric input into its CFRU-specific
request contract and compare all sixteen outputs to `rolls`, preserving the source
and harness provenance. It must not reinterpret hold-effect IDs as item IDs, take
slot 1 as a shipping move record, regenerate expectations from the calculator, or
promote these cases to exact-ROM proof. Add real table/runtime/binary correspondence
as a separate authority and record any disagreements; investigate rather than
rebaseline the source-backed vectors to match the implementation under test.

## JS parity

**VERIFIED:** `node tools/unbound-damage-oracle/test_js_parity.js` compares the isolated,
dependency-free `calculateUnboundDamage` implementation in
`tools/calc-bundler/unbound-cfru.js` with all **579 cases / 9,264 rolls** in the committed
host-compiled-source corpus. Exact integer equality proves parity on that corpus
domain only. Negative checks require unsupported operands and missing/extra fields
to throw. The CI `test` and `all` targets run corpus integrity, Python tool tests,
and JS parity in that order.

**UNVERIFIED:** exact-ROM behavior, configuration and numeric type-chart correspondence.
This remains **SOURCE_APPROXIMATE**, including the source-derived, ROM-unverified
numeric chart. The module stays dark and is not wired into production; parity does
not enable calculator support or establish shipping move/item table identities.
