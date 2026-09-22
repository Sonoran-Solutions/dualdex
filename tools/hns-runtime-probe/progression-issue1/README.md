# Issue #1: ordinary progression to Amy & May

These developer-only checkpoints extend the existing Chikorita/Sentret save chain to
Azalea Gym's explicit `trainerbattle_double TRAINER_AMY_AND_MAY_HNS` encounter.
They use the existing probe, controller input, in-game saves and Pokémon Center healing.
They neither edit saves nor write emulated RAM. No ROM, save, state, executable or screenshot
belongs in this directory.

Start with `make-save.sh`, then existing scenarios 30, 31, 32, 33, 34, 45 and 46.
Scenario 46 produces `/tmp/hns_baseline/stage46_tower3f.sav`. Old temporary saves had expired;
the chain was regenerated from a fresh ordinary starter save for this capture. Scenario 32
caught Sentret in this run. Other caught species or moves are not interchangeable fixtures.

From this directory, build with `../build.sh`, then run:

```bash
./run.sh /absolute/path/mgba_libretro.so /absolute/path/hns-2.0.5.gba
# Resume at a checkpoint whose documented input exists, e.g.:
./run.sh /absolute/path/mgba_libretro.so /absolute/path/hns-2.0.5.gba 20
```

The runner accepts an output only when the entire scenario exits zero, preserving any previous
accepted output on failure. Each file pins the ROM hash and asserts its final inactive map and
position before saving. `battle-win` and authorized navigation require victory, not merely battle
exit. Losses, blocked paths, exhausted moves, menu failures, and missing battle shapes fail nonzero.
Logs remain in `/tmp/hns_baseline/issue1-*.log`.

The route waypoints and inputs are explicit; battle RNG, RTC and roaming NPCs can still change a
replay. Falkner and Proton required retries from their unchanged legal input saves in this capture;
the successful scripts retain their ordinary pre-battle waits (600 and 960 frames). These are
**observed successful checkpoints, not a claim that every full-chain replay always wins**. Stop on
a failed checkpoint and inspect it. Do not accept its output or weaken assertions to continue.
The final Twins encounter from stage 66 is short and reproducible independently of those battles.

`spamb N` abbreviates exactly N repetitions of B held for 8 frames, then released for 80 frames.
This dismisses completed story text without repeatedly talking to an NPC. `walk-to X Y battle`
uses the existing coordinate driver with explicit authorization to finish an intercepted trainer
battle. No app UI/control capability is promoted by these developer controls.

## Observed checkpoint chain

Every row below had zero invariant violations and zero script errors. Frame counts identify the
recorded local runs used to assemble the chain; additional zero-frame assertions and the `spamb`
spelling preserve the controller sequence. The opening and final maps are asserted. Early routing
used the driver revisions described in the closure audit; this table does not claim a new full-chain
replay at the final PR commit.

| Script | Input `.sav` stem | Accepted output stem | Observed frames |
|---|---|---|---:|
| [01-silver.txt](01-silver.txt) | `stage46_tower3f` | `stage47_silver` | 13661 |
| [02-return-nurse2.txt](02-return-nurse2.txt) | `stage47_silver` | `stage48_healed` | 12083 |
| [03-rod-heal.txt](03-rod-heal.txt) | `stage48_healed` | `stage49_rod_healed` | 19217 |
| [04-abe.txt](04-abe.txt) | `stage49_rod_healed` | `stage50_falkner_ready` | 10194 |
| [05-falkner-save600.txt](05-falkner-save600.txt) | `stage50_falkner_ready` | `stage51_falkner_won` | 18795 |
| [06-egg.txt](06-egg.txt) | `stage51_falkner_won` | `stage52_pre_egg` | 15136 |
| [07-check-egg.txt](07-check-egg.txt) | `stage52_pre_egg` | `stage52_egg_healed` | 11654 |
| [08-route32.txt](08-route32.txt) | `stage52_egg_healed` | `stage53_route32` | 6142 |
| [09-route32-bypass.txt](09-route32-bypass.txt) | `stage53_route32` | `stage55_route32_center` | 15558 |
| [10-union-entry.txt](10-union-entry.txt) | `stage55_route32_center` | `stage56_union_entry` | 8383 |
| [11-union-cross5.txt](11-union-cross5.txt) | `stage56_union_entry` | `stage57_route33` | 25023 |
| [12-azalea.txt](12-azalea.txt) | `stage57_route33` | `stage58_azalea` | 13604 |
| [13-kurt.txt](13-kurt.txt) | `stage58_azalea` | `stage59_kurt` | 14596 |
| [14-well-entry2.txt](14-well-entry2.txt) | `stage59_kurt` | `stage60_well_entry` | 3996 |
| [15-well-route.txt](15-well-route.txt) | `stage60_well_entry` | `stage61_proton_ready` | 32738 |
| [16-well-return.txt](16-well-return.txt) | `stage61_proton_ready` | `stage62_well_return` | 6145 |
| [17-azalea-heal.txt](17-azalea-heal.txt) | `stage62_well_return` | `stage63_azalea_healed` | 8258 |
| [18-proton-healed.txt](18-proton-healed.txt) | `stage63_azalea_healed` | `stage64_proton_healed` | 5780 |
| [19-proton-delay960.txt](19-proton-delay960.txt) | `stage64_proton_healed` | `stage65_proton_won` | 21308 |
| [20-azalea-gym2.txt](20-azalea-gym2.txt) | `stage65_proton_won` | `stage66_azalea_gym` | 10435 |

Stage 06 is only the return/heal checkpoint; stage 07 actually receives the Togepi egg and asserts
party count 3. Stage 11 stops on Union Cave's southern arrow warp (24/8, 32,45); stage 12 steps
through it onto Route 33. Stages 16–18 heal after the three Well trainers before Proton. These
names preserve the local capture chronology; none of those intermediate checkpoints is Doubles
proof. The final input is `stage66_azalea_gym.sav`, inside Azalea Gym at (11,44), map 4/4.

## Actual Doubles proof

```bash
../runtime_battle_probe /absolute/path/mgba_libretro.so /absolute/path/hns-2.0.5.gba \
  --sav /tmp/hns_baseline/stage66_azalea_gym.sav \
  --script ../scenarios/70-amy-may-doubles.txt --quiet
```

Scenario 70 takes the right spider ride (`AzaleaTown_Gym_EventScript_Trigger_3`), lands beside
May at (6,27), and asserts the production four-battler contract twice, 180 frames apart.
The scenario cannot pass merely by reaching the Gym or any Singles battle. The actual matrix and
criterion disposition are in [the closure audit](../../../docs/HNS_ISSUE_1_CLOSURE_AUDIT.md).

Stage 20 was replayed from the packaged scripts after routing around the returned Slowpoke's
wandering area; Scenario 70 then passed again. This also exercises the `spamb` abbreviation.
