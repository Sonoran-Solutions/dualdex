# Unbound 2.1.1.1 calculator capability inventory — issue #60 §F

## 1. Provenance and present capability

**Every CFRU/DPE-derived claim below is `SOURCE_APPROXIMATE` for Unbound 2.1.1.1.**
That release was built from Skeli's private tree. The public commits are useful
mechanics leads, not its build source, build configuration, ABI, or an independent
damage oracle for the release. A public default, an `#ifdef UNBOUND` branch, or a
matching Pokémon name does not establish what the release executes. Exact-ROM or
runtime confirmation is required before promoting any such claim.

| Input | Identity / evidence available in this task |
|---|---|
| Target release | Pokémon Unbound **v2.1.1.1**, as stated by the owner |
| Target ROM SHA-256 | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` — owner-supplied identity, not independently hashed or runtime-verified here |
| Public CFRU | `Skeli789/Complete-Fire-Red-Upgrade` @ `b637a27898b14e25dd24d0f69a3e302f0069deb8`; local checkout `/home/dq/dualdex-private/unbound/src/cfru` |
| Public DPE | `Skeli789/Dynamic-Pokemon-Expansion` @ `cdfc053a56326a13dc5311b24488445e17536b7e`; local checkout `/home/dq/dualdex-private/unbound/src/dpe` |
| DualDex inspected revision | `d592f559e0eb97159513551fc05ea6a663532a9a` (`d592f55`, task base); no implementation changes in this task |
| Public checkout integrity | Both HEADs matched the supplied commits; neither had tracked modifications when inspected |
| Exact-ROM damage, settings, or layout evidence collected here | **NOT_RUN / NOT_FOUND**; no ROM, save, runtime probe, build, or test execution |

Source locations in the inventory are relative to the pinned CFRU checkout unless
prefixed `DPE:` or `DualDex:`. Public references can be resolved in the
[pinned CFRU tree](https://github.com/Skeli789/Complete-Fire-Red-Upgrade/tree/b637a27898b14e25dd24d0f69a3e302f0069deb8)
and [pinned DPE tree](https://github.com/Skeli789/Dynamic-Pokemon-Expansion/tree/cdfc053a56326a13dc5311b24488445e17536b7e).
These links identify the locally read sources; no network lookup was used.

What was read:

- CFRU `src/damage_calc.c`, the relevant branches of `src/ability_battle_effects.c`,
  `src/battle_util.c`, `src/general_bs_commands.c`, `src/dynamax.c`, `src/mega.c`,
  `src/frontier.c`, `src/util.c`, `src/exp.c`, and `src/move_menu.c`; supporting
  constants, type/move tables and battle-script macros/scripts.
- **Configuration correction:** `include/config.h` is a generic platform header.
  The battle feature defaults inspected here live in **`src/config.h`**. Its
  commented-out definitions mean “off in this public configuration,” not “off in
  Unbound.” No release build manifest establishes the private `UNBOUND` defines.
- DPE `README.md`, `include/base_stats.h`, and selected `src/Base_Stats.c` records,
  including Hoopa-Unbound and Mega forms. This is a data-format/content comparison,
  not an exhaustive release species-data extraction.
- DualDex [H&S capability evidence](HNS_2_0_5_CALCULATOR_CAPABILITY.md),
  [vanilla evidence](VANILLA_CALCULATOR_EVIDENCE.md), and
  [QuickJS golden provenance contract](QUICKJS_CALCULATOR_TESTS.md);
  `CalcCapabilityPolicy.kt`, `CalcModels.kt`, `GameDataPack.kt`,
  `app/src/main/assets/profiles/unbound.json`, and
  `tools/calc-bundler/entry.js` / its dependency lockfile.

**Current product verdict: `CalcSupport.UNSUPPORTED`; no Unbound damage number.**
`CalcRuleset` has only `VANILLA_GEN3` and `HNS_2_0_5`
(`CalcCapabilityPolicy.kt:746`). `capabilityFor()` explicitly excludes CFRU from
vanilla fallback (`:1260–1276`) and returns no Unbound row. The profile has
`engine: "CFRU"`, `gameDataPackId: "modern"`, and an empty `sha256Hashes` list.
Its `isVerified` / memory-layout flags are not calculator mechanics authority.
The owner's hash in this document does not populate or authorize that profile.

The bundle pins `@smogon/calc` **0.11.0** (`package-lock.json:432`), selects
`Generations.get(input.gen || 3)` (`entry.js:198`), and calls generic `calculate`
unless `typeSystem` is exactly `hns_2_0_5` (`:1841–1847`). H&S has a dedicated
source-specific arithmetic path; it is not a CFRU engine. The generic `modern`
data pack uses generation 8, but **content generation and mechanics generation
are separate decisions**. Neither the generic pack nor the Gen 8 smoke fixture
proves Unbound compatibility. `ModernDataPack` explicitly returns `false` for
species and move authoritativeness (`GameDataPack.kt:176–180`).

## 2. Mechanics inventory

Statuses describe the **expressive capability of the existing bridge / closest
engine**, not production admission or exact-release verification:

- `REPRESENTABLE`: an existing input/engine can express the named rule or constant;
  order, data and release equivalence still require fixtures.
- `DIFFERS`: the inspected CFRU behavior differs from an existing relevant path,
  or a matching nominal multiplier conceals a different stage/order.
- `UNSUPPORTED`: a necessary operand, mechanic or output contract is absent from
  the current Unbound path; naming a move or substituting power is insufficient.
- `UNKNOWN`: release configuration, identity or semantics cannot be determined
  from these sources. Refuse when that unknown is relevant.

**All statuses remain `SOURCE_APPROXIMATE` with respect to the target ROM.** In the
configuration column, “public” means this checkout; **the exact Unbound build
value is unknown unless independently established later**. No row below lifts the
current blanket `UNSUPPORTED` product verdict.

| Mechanic | CFRU source file:line and observed behavior | Unbound config value if determinable | Closest DualDex calculator generation / behavior | Status |
|---|---|---|---|---|
| Base damage and operation order | `src/damage_calc.c:2623–2635,2981–3010`; integer `floor(floor((floor(2L/5)+2) × BP × A / D)/50)+2`, followed by sequential modifiers. `battle_script_macros.s:54–58` orders crit selection → damage calculation → STAB/type → random adjustment; `damage_calc.c:290` applies crit after `CalculateBaseDamage` returns | No single generation flag establishes this whole pipeline; private differences unknown | Gen 8 is a comparison baseline for modern constants; ADV and H&S are different pipelines. H&S rounds in its own UQ4.12/roll-first order. No CFRU parity established | **DIFFERS** |
| Critical multiplier, Sniper, stat stages | `src/damage_calc.c:44–49,290,2942–2978,3112–3114`: public ×1.5 crit, extra ×1.5 Sniper; ignore negative attacking / positive defending stages on crit | Public `OLD_CRIT_DAMAGE` off (`src/config.h:284`); on gives ×2 crit | Gen 6+ ×1.5 is the closest constant; vanilla/H&S ×2 is wrong for this public default. Stage and Sniper rounding need independent vectors | **REPRESENTABLE** (selected crit constant only) |
| Critical rate / forced crit / prevention | `src/damage_calc.c:35–42,81–151`: default denominators `{24,8,2,1,1}`; ability/item/focus/move predicates and forced crit branches | Public `CRIT_CHANCE_GEN_6` and `CRIT_CHANCE_GEN_2_TO_5` off (`src/config.h:285–287`); private odds unknown | `move.isCrit` selects a conditional damage line (`entry.js:265`); it does not model crit probability, force/prevention authority, or predict RNG | **UNSUPPORTED** (odds / automatic crit resolution) |
| STAB and Adaptability | `src/damage_calc.c:799–829`: any of three live attacker types can match; ordinary ×1.5, Adaptability ×2; Struggle excluded | No dedicated STAB toggle located | Generic modern engines express ordinary two-type STAB / Adaptability. Bridge type override permits only 1–2 types (`entry.js:90` area); a third live type cannot be passed as an ordinary override | **REPRESENTABLE** (1–2 types); **UNSUPPORTED** (third type) |
| Fairy and Gen 6+ Steel chart | `src/Tables/type_tables.h:147–186`; Ghost/Dark have no Steel resistance entry, Dragon→Fairy immune; `src/damage_calc.c:1453–1455` reads the chart | Public modern chart; no Fairy-on/off runtime option established for release | Generic Gen 6+ chart is the closest match; generic Gen 3 is not. H&S custom chart is tied to H&S semantics, not proof for CFRU | **REPRESENTABLE** (ordinary chart) |
| Physical / special split | `src/battle_util.c:1588–1611`: per-move split plus dynamic category moves; optional type-based fallback | Public `OLD_MOVE_SPLIT` off (`src/config.h:274`) | Gen 4+ per-move categories / explicit category override; must bind release metadata and final category, not infer from the FireRed base | **REPRESENTABLE** (ordinary split); **UNKNOWN** (private overrides) |
| Ordinary weather and suppression | `src/damage_calc.c:3066–3090`: Rain/Sun ×1.5/×0.5 after +2; weather predicate and defender Utility Umbrella matter | Public `INFINITE_WEATHER` off (`src/config.h:271`); duration is distinct from current weather | Generic `field.weather` exists (`entry.js:283`). Exact spelling, suppression, per-holder applicability and modifier order require proof | **REPRESENTABLE** (ordinary conditions); **UNKNOWN** (full parity) |
| Sand, primal/custom weather | `src/damage_calc.c:1403–1405,1539–1540,2924–2931`: primal move failures, Delta Stream matchup change, Rock SpD ×1.5; “Vicious Sandstorm” also boosts Ground SpD | Release availability / weather word unknown | Modern ordinary Sand/Harsh Sun/Heavy Rain/Strong Winds are comparison candidates, not custom-weather parity. No Unbound weather decoder | **UNKNOWN** / **UNSUPPORTED** for unmodelled weather |
| Electric / Grassy / Psychic / Misty Terrain | `src/damage_calc.c:4156–4192`: public ×1.3 offensive terrain at BP stage, groundedness predicates; Misty halves Dragon, Grassy halves selected Ground moves, with semi-invulnerability exception; Electric includes special magnetism predicate | Public `OLD_TERRAIN_BOOST` off; `VAR_TERRAIN = 0x5000`, `INFINITE_TERRAIN` off (`src/config.h:17,272,291`) | Gen 8 has closest ×1.3 constants; generic terrain text is forwarded (`entry.js:284–286`). H&S terrain predicates differ; no exact Unbound groundedness/state authority | **DIFFERS** (predicates/order); known neutral terrain can be a future subset |
| Stat-modifying offensive abilities | `src/damage_calc.c:2643–2756`: Huge/Pure Power, Hustle, Guts, Solar Power, Slow Start, Defeatist, pinch abilities, Flash Fire, Gorilla Tactics, Plus/Minus and Flower Gift | Private identity/activation unknown; Huge Power explicitly depends on Scalemons eligibility (`:2650`) | Generic Gen 8 recognizes many names; ordinary identity alone cannot convey activation timers, ally state or source stage. Flash Fire here boosts SpA (`:2704–2708`), requiring a physical Fire control | **DIFFERS** / **UNKNOWN** by ability |
| Base-power offensive abilities | `src/damage_calc.c:3861–4042`: Technician, Rivalry, Reckless, Iron Fist, Toxic/Flare Boost, Sand Force, Sheer Force, -ate/Normalize, Mega Launcher, Strong Jaw, Tough Claws, Neuroforce, Stakeout, Steelworker/Steely Spirit, Water Bubble, Analytic, Punk Rock, Transistor, Dragon's Maw; ally Battery/Power Spot | Public `OLD_ATE_BOOST` off gives ×1.2; Transistor branch ×1.5, not later Gen 9 ×1.3 (`src/config.h:289`; `damage_calc.c:4008–4013`) | Gen 8 is a nearer baseline than Gen 9. Sequential BP flooring, original/dynamic power, contact/sound/punch flags, move order and allies need classification per numeric ID | **DIFFERS** until per-family proof |
| Defensive abilities / immunity / suppression | `src/damage_calc.c:2760–2797,3127–3184`; Thick Fat halves attacking stats, Fur Coat doubles Defense, Filter/Solid Rock/Prism Armor, Multiscale/Shadow Shield, Fluffy, Punk Rock, Ice Scales, Friend Guard. `src/ability_battle_effects.c:1277–1287,1714–1731` mutates abilities under Neutralizing Gas and handles absorbers | No release-effective ability table or suppression layout established | Generic Gen 8 has many analogues; H&S admission lists and suppression rules cannot be reused. Unknown immunity/suppression must never become neutral damage | **UNKNOWN**; individual known modifiers may later be classified |
| Plates, type boosters, Muscle Band, Wise Glasses, signature orbs | `src/damage_calc.c:4045–4099`: ×1.2 type boost at BP stage; Muscle Band / Wise Glasses ×1.1; species/type-restricted orbs and modern Soul Dew | Public `OLD_SOUL_DEW_EFFECT` off (`src/config.h:295`); private item table unknown | Gen 4+ type-boost percentage is closer than ADV ×1.1. Names plus percentages do not establish ordered integer results | **DIFFERS** (stage/rounding) |
| Gems and consumption | `src/damage_calc.c:4102–4112,4141–4149`: ×1.3 BP by held matching Gem or `consumedGem`; Symbiosis note prevents duplicate boost | Public `OLD_GEM_BOOST` off (`src/config.h:290`); ×1.5 if enabled | Gen 6+ nominal Gem constant; current bridge has no Unbound consumed-Gem state. Acrobatics checks consumption/held item separately (`:3300–3307`) | **UNSUPPORTED** (consumption-dependent hit) |
| Life Orb | `src/damage_calc.c:4135–4137`: ×1.3 **base power**, before terrain; secondary/recoil interactions need separate audit | Unconditional public item branch; private effect mapping unknown | Modern generic Life Orb is not evidence for this placement. H&S's final-modifier implementation is not interchangeable with CFRU BP truncation | **DIFFERS** |
| Expert Belt / Metronome | `src/damage_calc.c:3120–3123,4115–4131`: Belt ×1.2 on super-effective damage; Metronome BP ×1.2…×2 from live history counter | Release item IDs / counter layout unknown | Generic modern item analogues exist; entry bridge does not forward a generic Metronome history operand | **REPRESENTABLE** (Belt predicate/constant); **UNSUPPORTED** (history-dependent Metronome) |
| Choice, species-stat items, Eviolite, Assault Vest, resist berries | `src/damage_calc.c:2800–2899,3186–3199`: stat boosts; Choice disabled during Dynamax; berries depend on Unnerve, type and Ripen (public Ripen branch divides by **3**) | Private held-effect/quality records unknown | Generic modern analogues; source-specific Ripen result and integer placement require dedicated fixtures. Do not assume stock item IDs/effects | **DIFFERS** / **UNKNOWN** |
| Mega Evolution / Primal / Ultra Burst | `src/mega.c:48–98,143–175,191–214`: item/form eligibility and form ability; form data in DPE | Public `MEGA_EVOLUTION_FEATURE` on (`src/config.h:217`); private forms and restrictions unknown | A resolved post-transformation species/type/base-stat/ability snapshot is expressible; current bridge has no generic Mega transition selector or Unbound form authority. Holding a stone alone does not prove transformed stats | **REPRESENTABLE** (known final form); **UNSUPPORTED** (transition) |
| Z-moves | `src/damage_calc.c:3278–3283,4211–4242`: selected base move / `z_move_power` or signature move power; `:2058–2061` applies 25% through relevant protection | Public Z implementation exists; exact release eligibility, records and flags unknown | Generic Gen 7/8 library facilities are not exposed as a bridge `isZ` operand (`entry.js:264–278` forwards only crit and overrides). A BP override cannot establish protection/type/ability execution | **UNSUPPORTED** |
| Dynamax / Max / G-Max | `src/damage_calc.c:3285–3288,4245–4263,3046–3050`; `src/dynamax.c:753–783,881–916`: conversion power, anti-Dynamax doubling, HP scaling and exception for Shedinja | Public `DYNAMAX_FEATURE` on; runtime flag `FLAG_DYNAMAX_BATTLE = 0x918` (`src/config.h:61,218`) | Generic Gen 8 facilities require missing generic Dynamax/Max operands and authoritative HP/form state. Current generic bridge does not forward `isDynamax` / Max selection | **UNSUPPORTED** |
| Max raids / boss shields | `src/damage_calc.c:2066–2084`: shield-dependent HP divisor, no KO while shielded, damage capped at next shield threshold; `src/dynamax.c:771–783,1633–1650`: star HP scale and difficulty-dependent starting shields | Public `FLAG_RAID_BATTLE = 0x919`, no-force-end `0x91A` (`src/config.h:62–63`); private boss state unknown | No stock generation encodes this CFRU boss lifecycle in the bridge; simply multiplying HP is insufficient. Shields may also exist outside raid battles (`damage_calc.c:2077`) | **UNSUPPORTED** |
| Doubles spread reduction | `src/damage_calc.c:3239–3247`: ×0.75 only when target mask / living-battler counts qualify, including allies for all-target moves | No old-spread toggle located; runtime topology required | Gen 4+ has closest constant; ADV/H&S ×0.5 is unsuitable. Generic `gameType` cannot convey the CFRU live count; H&S's separate count contract is not an Unbound reader | **UNSUPPORTED** (live conditional reduction) |
| Burn and frostbite | `src/damage_calc.c:3217–3227`: halve physical damage except Guts / Facade; optional frostbite halves special | Public `OLD_BURN_DAMAGE` off concerns **residual** 1/16 vs 1/8, not this physical cut; no active `FROSTBITE` definition established | Modern selected-hit burn semantics are closest; ADV Facade and operation placement differ. No Unbound frostbite decoding; don't reinterpret freeze speculatively | **REPRESENTABLE** (known ordinary burn rule); **UNKNOWN** (frostbite) |
| Reflect / Light Screen / Aurora Veil | `src/damage_calc.c:3204–3215,4406–4417`: screens/Veil ×1/2, or floor(2×damage/3) with two living defenders; bypassed by crit / screen-bypassing ability; Brick Break exception | Runtime `defSideStatus`, `gNewBS->AuroraVeilTimers`, format and living count; exact layout unknown | Generic side inputs exist, but no Unbound live-count authority. H&S fixed-point 0.667 post-roll path does not establish this pre-roll integer branch | **REPRESENTABLE** (Singles operands); **UNSUPPORTED** (unknown topology); ordering unproven |
| Random damage roll | `src/damage_calc.c:2011–2025,4420–4424`: `100 - (Random() & 15)` → integers 85…100, floor and minimum 1 for nonzero ordinary damage; set-damage path skips it | No range toggle located | Existing ordinary 16-roll output is a suitable shape. Matching endpoints alone is insufficient; compare complete vectors. AI estimates at 93%/96% (`:536,645`) are **not** the battle roll rule | **REPRESENTABLE** (ordinary roll range) |
| Multi-hit / Parental Bond | `src/general_bs_commands.c:2866–2890`: hit count, Skill Link and Battle Bond; `src/damage_calc.c:3229–3236,3827–3839`: second Parental Bond strike / escalating Triple Kick or Axel | Public `OLD_PARENTAL_BOND_DAMAGE` off → second strike ÷4, not ÷2 (`src/config.h:296`) | Gen 7/8 nominal second-hit constant; generic bridge forwards no chosen hit count / strike index. H&S repeated-strike authority is build-specific. Whole-move totals need per-strike HP/item/ability transitions | **UNSUPPORTED** |
| Fixed / HP-based / counter damage | `assembly/battle_scripts/general_attack_battle_scripts.s:1743–1794,3507–3514`; `src/general_bs_commands.c:3574–3582,5118–5125`; `src/damage_calc.c:2023–2025`: Seismic Toss/Night Shade, Psywave, Counter, Metal Burst, Final Gambit, Endeavor use other paths | Exact move identities / immunities / base-vs-Dynamax HP semantics unknown | Generic modern fixed-damage handling is only a candidate for isolated known moves. No generic counter-history authority; Psywave has 101 RNG inputs (50–150% level), not the ordinary 16 rolls | **UNSUPPORTED** as a family; per-move proof required |
| Dynamic power/type/category and volatile execution | `src/damage_calc.c:1699–1969,3299–3840`: -ate, Weather Ball, Judgment, Multi-Attack, Terrain Pulse, HP/speed/weight/history power; `src/battle_util.c:1588–1601` dynamic split | Private metadata and live state unknown | Type/BP/category overrides exist, but not a proof of effect flags, grounding, move order, charge, semi-invulnerability, substitute, protection or between-hit state | **UNKNOWN** / **UNSUPPORTED** until effect-family authority |
| Badge boosts / Explosion / Hidden Power legacy options | `src/config.h:288,292–293`; `src/damage_calc.c:2902–2922,2934–2940,3737` | Public `BADGE_BOOSTS`, `OLD_EXPLOSION_BOOST`, `OLD_HIDDEN_POWER_BP` off; release unknown | Gen 5+ is nearer for no Explosion defense halving; modern Hidden Power BP60 candidate. H&S badge authority is not applicable | **UNKNOWN** (release flags) |
| Unbound-specific Portal Power | `src/config.h:294`; `include/constants/abilities.h:213`; `src/damage_calc.c:2787–2797`: optional Hoopa-Unbound ability reduces attacking stats ×0.75 for non-contact moves | Public `PORTAL_POWER` **off**; release activation and assignment unknown. DPE `src/Base_Stats.c:26480–26509` instead assigns Hoopa-Unbound **Magician** | No stock calculator Portal Power behavior. Do not conflate with Shadow Shield or apply a final ×0.75 substitute; no exact ability table | **UNSUPPORTED** if active; **UNKNOWN** if present |
| Unbound-specific moves, species buffs, other custom abilities | Public move/species tables describe public content only; DPE `include/base_stats.h:3–35`, `src/Base_Stats.c:10` and CFRU `src/Tables/battle_moves.c` are comparison inputs, not a private-release census | **Not determinable**; no exact-release custom-move/ability delta list available here | Existing overrides cover six base stats, 1–2 types, BP/type/category/ateBoost only (`entry.js:65–161`). They cannot manufacture arbitrary effect scripts or prove name-to-ID equality | **UNKNOWN**; no claim that “none exist” |
| Special battle rules / stat and type remapping | `src/damage_calc.c:3250–3255` Ghost full-HP Shadow Shield rule; `src/battle_util.c:365–394,2475–2526`; `src/frontier.c:685–739` Scalemons / Camomons / other facility rules | Flag-, facility-tier- and Circus-dependent; private release availability/storage unknown | A generation number does not encode these rules. Even Huge Power changes under Scalemons. All relevant unknown/active unmodelled modes refuse | **UNSUPPORTED** / **UNKNOWN** |

### Why a generation switch is insufficient

The ordinary public path modifies BP, modifies stats, computes the integer base
formula, then applies weather / ability / item / screens / burn / spread effects
inside `CalculateBaseDamage`. Crit multiplication follows its return; STAB/type
and finally the random roll follow in the battle script. Matching ×1.5 crit,
×0.75 spread and ×1.3 terrain constants does **not** prove equivalence to a stock
Gen 8 order or to H&S's custom implementation. Life Orb at BP, sequential floors,
conditional live battler counts and the optional Portal Power branch are concrete
reasons to require a mechanic-by-mechanic differential matrix. No unexplained
disagreement may be hidden by changing a move's BP until the endpoints match.

## 3. Runtime settings and options that alter rules or operands

Public flag/var numbers below are **logical IDs, not RAM offsets**. Release IDs,
SaveBlock locations, struct layouts, defaults and transitions remain unknown.
No public layout is permission to read an identically named release variable.
Unknown is not equivalent to false, clear weather, no item, or a neutral ability.

| Setting / context | Public storage / code lead (`SOURCE_APPROXIMATE`) | Calculator effect and honest policy |
|---|---|---|
| Difficulty: release Vanilla / Difficult / Expert / Insane labels to verify | `VarGet(VAR_GAME_DIFFICULTY)` in `src/move_menu.c:2166–2198`, `src/dynamax.c:1638–1640`; `include/global.h:167–173` instead enumerates NORMAL/EASY/HARD/EXPERT (0…3). No `VAR_GAME_DIFFICULTY` definition or release UI-to-enum mapping established | **Do not equate the public enum names with the release UI labels.** Public code proves bag restrictions and starting-shield differences, not a universal difficulty damage multiplier. Difficulty can also select different teams/stats/context. Refuse unread difficulty/rule context until its relevance is classified; only later may authoritative downstream stats suffice for a proven value-only difference |
| Level caps and scaling | `FLAG_HARD_LEVEL_CAP` / `GetCurrentLevelCap()` hook in `src/exp.c:572–583` (implementation supplied by the integrator); `src/config.h:46–47` public wild/trainer scaling flags `0x90D` / `0x90E` | Usually changes level/stats, not the selected-hit formula; exact release cap/scaling rules unknown. Never infer a level from progress/cap when the battle level is unread. A future snapshot with authoritative level/raw stats may absorb proven operand-only effects |
| Species / learnset / ability randomizers | Public `src/config.h:195–198` has commented flags `0x940` / `0x941` / `0x942`; `src/util.c:275–296` ability randomization depends on trainer ID/species and facility context | Prove exact effective species, moves, types, stats and ability; static slot/default ability is insufficient. Refuse unknown mode/state. A proven encounter-only randomizer need not itself block a future fully authoritative hit; active unmodelled remapping does |
| Random types, type effectiveness, moves, stat scaling / sandbox-style modes | No exact-release option schema established. Public Scalemons/Camomons predicates are in `src/frontier.c:685–717`; public Scalemons changes ability behavior at `src/damage_calc.c:2650` | These are evidence questions, not asserted release menu options. Refuse relevant unread/active unsupported remapping. Do not reuse H&S `challengeSettings` fields, masks, or offsets |
| Inverse battles | Public `FLAG_INVERSE = 0x900` (`src/config.h:33`), or `gBattleCircusFlags & BATTLE_CIRCUS_INVERSE` (`src/battle_util.c:2520–2526`); `src/damage_calc.c:1500–1534` flips immunity/resistance to ×2 and ×2 to ×0.5; Freeze-Dry exception follows inversion | No inverse selector is forwarded by the generic bridge. Refuse active inverse; unread inverse/Circus state also refuses unless exact evidence proves it irrelevant. Do not emulate by swapping attacker/defender types |
| “Gen 8 mechanics” toggle | **No player-facing toggle or release storage established.** Public `OLD_*` defines select crit, Gem, terrain, -ate, Parental Bond, split, burn residual, etc. at compile time (`src/config.h:267–299`) | Treat each constant as a separate release confirmation target. Presence of Gen 8 species or `FLAG_GEN_8_PLACED_IN_GAME` references is not evidence of a damage-rules switch. Refuse unknown active rule; don't invent a menu control or assume `gen: 8` proves it |
| Weather / terrain and temporary field state | Public start terrain `VAR_TERRAIN = 0x5000`; live `gTerrainType`, `gBattleWeather`, per-battler grounding, `gNewBS` timers / suppression; `src/damage_calc.c:3066,4152–4192` | Capture current effective field and applicability, not merely its starting setting. Unknown field word/bit, suppression, timer-dependent effect or grounding refuses |
| Singles / Doubles / partner / format and facility tier | Public flags `FLAG_DOUBLE_BATTLE = 0x907`, tag/two-opponent flags `0x908/0x909` (`src/config.h:40–42`); facility flag `0x930`, tier var `0x5018` (`:83–87`); live `gBattleTypeFlags`, living battlers | Need selected target, live count and partner abilities/items; static format is insufficient for spread/screens. Initial proposed admission should be Singles only. Facility tier can alter stats/types or ban Mega/Z; unread context refuses |
| Circus / boss / gym-specific rules | `gBattleCircusFlags`; `IsDeltaStreamBattle`, `IsMagnetRiseBattle`, `IsBadThoughtsBattle`, `IsPixieBattle`, `IsShadowShieldBattle` (`src/battle_util.c:2475–2526`); Ghost damage halving `src/damage_calc.c:3250–3255` | Public hooks establish possible exceptional rules, not which release bosses use them. Identify exact battle rules independently of difficulty. Refuse unread/custom rule; ordinary weather/ability names cannot stand in for it |
| Mega / Z / Dynamax / raid and shields | Public feature defines plus flags `0x918–0x91A`, `gNewBS->zMoveData`, `gNewBS->dynamaxData`, star count and shield counters | “Feature compiled” differs from “active for this hit.” Refuse active or unread gimmicks/shields until a dedicated, release-bound implementation exists; observing no ordinary Dynamax flag does not prove absence of non-raid shields |
| Item restrictions / no-bag / consumable retention | `src/move_menu.c:2164–2202`, optional `VAR_ITEM_RESTRICTIONS`; public `FLAG_DISABLE_BAG = 0x915`, `FLAG_KEEP_CONSUMABLE_ITEMS = 0x927` (`src/config.h:58,66`) | Bag rules are not held-item suppression rules. Current effective held effect, consumed Gem/berry state, Klutz/Embargo/Magic Room and prior changes still need authority. Postbattle restoration does not undo in-hit consumption |

## 4. Proposed Unbound capability row and admission matrix

This is a **proposal**, not a new enum, profile, data pack, fixture, or authorization.
`CalcLimitationDisposition` vocabulary is preserved: `NON_BLOCKING` lowers trust;
`SOFT_IGNORED_MECHANIC` requires an identified relevant effect deliberately removed
from the authorized request and disclosed; `HARD_REFUSAL` produces no number.
Any independent hard refusal wins. Unknown identity/state can never be softened.

| `CalcCapability` field | Proposed exact-release row |
|---|---|
| `ruleset` | New, explicitly scoped `UNBOUND_2_1_1_1` (proposed identifier; absent today) |
| Exact identity | Owner-specified SHA above, independently checked against running ROM before live authority; do not admit by header/title or generic `engine = CFRU` |
| `mechanicsGeneration` | **Unassigned pending evidence.** `8` is a comparison candidate for public constants, not an admission choice. If stock arithmetic disagrees, use a dedicated CFRU path and document its baseline rather than changing generations until one fixture matches |
| `contentSource` | Proposed pinned `unbound_2_1_1_1` release data authority; not existing generic `modern`, DPE HEAD, or the H&S pack. Bind numeric IDs, form variants and move effect/flag data as well as names |
| `ceiling` | **Today: `UNSUPPORTED`. Future admitted subset: at most `ESTIMATED`**, after the base arithmetic/data/runtime prerequisites below. No `VERIFIED` claim from public-source parity |
| `alwaysLimitations` | Today the missing capability row itself refuses. A future row needs explicit Unbound provenance / rule / data blockers until discharged; do not reuse H&S-scoped enums as if they describe Unbound |
| `label` | `Pokemon Unbound 2.1.1.1 — source-approximate mechanics; partial calculator support` (future subset only) |

`CalcCapability.mechanicsGeneration` is a non-null integer today; “unassigned” is
intentional documentation of missing evidence, **not** an implementable Kotlin row.
The honest immediate action remains leaving `capabilityFor(unbound)` absent.
Adding this inventory alone does not satisfy the admission prerequisites.

| Request / evidence condition | Proposed support and disposition | Limitation vocabulary / implementation constraint |
|---|---|---|
| Current Unbound request | `UNSUPPORTED` | No capability row; no silent `VANILLA_GEN3`, H&S or generic-modern fallback |
| Future exact-release, ordinary **Singles selected hit**, pinned species/move identity and effect flags, proven integer pipeline, known neutral special rules/gimmicks, authoritative required live operands, all relevant effects modelled | `ESTIMATED`; provenance ceiling `NON_BLOCKING` only **after** the hard prerequisites are met | New Unbound-specific provenance limitation; existing `ROM_NOT_EXACT_VERIFIED` describes runtime mismatch, not private-source uncertainty |
| Exact SHA supplied or verified, but only public source / generic names / modern pack available | `UNSUPPORTED`, `HARD_REFUSAL` | Exact identity is necessary for live evidence but does not discharge missing release data/mechanics authority |
| Non-exact / unknown ROM or release, unknown difficulty, inverse/facility/boss rules, ruleset constant, randomizer or effective type/stat remapping | `UNSUPPORTED`, `HARD_REFUSAL` | Proposed Unbound equivalents of rule-state / identity blockers. Existing H&S `CHALLENGE_SETTINGS_UNREADABLE` and toggle limitations are **analogues**, not valid Unbound field mappings |
| Unknown species/move/data membership, invalid level/stat/stage, unknown live participant/slot | `UNSUPPORTED`, `HARD_REFUSAL` | Existing generic `SPECIES_NOT_IN_PINNED_DATA`, `MOVE_NOT_IN_PINNED_DATA`, `LEVEL_OUT_OF_RANGE`, `STAT_VALUES_OUT_OF_RANGE`, `BOOSTS_OUT_OF_RANGE`, `LIVE_PARTICIPANT_STATE_UNKNOWN`, `LIVE_INPUTS_NOT_VERIFIED` are relevant concepts |
| Unknown effective ability/item identity, immunity, suppression, activation or relevance | `UNSUPPORTED`, `HARD_REFUSAL` | Need Unbound identity/context classifiers. Generic `ABILITY_NOT_MODELLED` / `ITEM_NOT_MODELLED` are currently `NON_BLOCKING`; they are **insufficient** for this unknown-state gate |
| Known, relevant, unmodelled ability/item/field **multiplier**, with otherwise trustworthy base calculation and no unresolved execution, immunity, type/category rewrite or required-state dependency | Potential future `ESTIMATED`, `SOFT_IGNORED_MECHANIC`, only after a reviewed per-ID/per-context classification | Follow H&S's named-caveat pattern: remove exactly the effect, retain known identity in diagnostics, never silently pass an ignored name. A user-visible acknowledgement alone cannot supply missing authority. No Unbound soft-ignore whitelist is established here |
| Known effect proven irrelevant to this selected hit | Future `ESTIMATED` without an ignored-effect caveat, subject to all other gates | Neutrality must be proved from authoritative context; blank / default / unread does not mean irrelevant |
| Gen 8 request with a known arithmetic difference, custom type rule, Portal Power, unmodelled dynamic move, inverse, active/unread Mega transition/Z/Max/raid/shields | `UNSUPPORTED`, `HARD_REFUSAL` | Need Unbound-specific mechanics reasons; no “approximate” escape for an untrustworthy base hit |
| Doubles spread/screens/partner-dependent effect without complete topology and arithmetic authority; unsupported multi-hit/fixed/counter history | `UNSUPPORTED`, `HARD_REFUSAL` | H&S/vanilla limitation names illustrate the pattern; Unbound requires its own count / partner / strike / history contracts |
| Critical odds, accuracy, full-turn/whole-battle prediction, unproved KO probability | Outside the initial selected-hit output contract; refuse unsupported outputs | `isCrit` is a hypothetical selected-hit operand. Expose no probability from generic library output as an Unbound claim |

### Golden fixtures required before admission or mechanic claims

**No Unbound goldens were created or executed here.** The list below is the
required evidence backlog, not a statement that these checks pass. Keep the
[QuickJS provenance gate](QUICKJS_CALCULATOR_TESTS.md): do not generate expected
damage with the calculator under test and copy it back as the oracle. A public
CFRU harness would establish only `SOURCE_APPROXIMATE` host parity. Bind claimed
release behavior to the exact ROM/runtime (or authenticated exact private build
source plus independent release confirmation), retaining inputs and evidence.

For every ordinary hit fixture retain **all 16 ordered rolls**, not just min/max;
explicitly record release SHA, settings, species/form and numeric IDs, move
effect/flags/BP/type/category, level, raw/base stats and derivation, IV/EV/nature,
stages, HP/status, effective abilities/items and activation, field/side/volatile
state, crit selection, topology and rule context. Distinguish computed damage
from HP loss capped by fainting, substitutes, survival effects or raid shields.
If a runtime observation provides only one roll, label it as one observation;
it does not establish the remaining 15 rolls. Fixed/random-set damage and
multi-hit families require their actual outcome domains instead.

| Fixture group (proposed) | Required controls / boundary cases | What it unlocks |
|---|---|---|
| U01 identity and data authority | Exact ROM hash and version; wrong/unknown hash refuses; species/form/ability/item/move IDs cross-checked to release data; generic-modern same-name negative control | Correct release/data selection; never alone damage admission |
| U02 ordinary core arithmetic | Neutral physical and special hits, low/high level, small BP/stat values near truncation boundaries, minimum 1, ±stages, no hidden field/ability/item; full roll vectors | Candidate pipeline choice and narrow base-hit subset |
| U03 crit and stages | Crit off/on at odd damage; negative attack / positive defense ignored, opposite stages retained; Sniper; Battle/Shell Armor and forced-crit effects; odds stages separately observed | Conditional crit damage; odds remain separately gated |
| U04 STAB / type chart / split | No STAB vs ×1.5 vs Adaptability ×2; Fairy→Dragon, Dragon→Fairy, Ghost/Dark→Steel, dual-type rounding, immunity; physical Dark and special Ghost control; third-type refusal | Ordinary chart/category and STAB |
| U05 weather / terrain | Clear/Rain/Sun; suppression and Umbrella per holder; Sand Rock/Ground controls; primal/Delta/custom weather refuse unless proved. Four terrains with grounded/airborne/magnetism and semi-state controls; Grassy Ground moves | Individual field branches and rules constants |
| U06 offensive abilities | Each ability admitted by ID and role; active/inactive thresholds, status, contact/sound/punch flags, order, ally conditions; Transistor ×1.5 vs ×1.3 discriminator; physical/special Flash Fire; Scalemons Huge Power control | Per-ability admission or a named known-effect caveat |
| U07 defense / immunity / suppression | Thick Fat placement, Fur Coat, Filter/Prism Armor, full-HP Multiscale/Shadow Shield, Fluffy contact×Fire, Friend Guard; absorbers, Levitate, Mold Breaker/Teravolt/Turboblaze, Gastro Acid/Neutralizing Gas | Proves a damaging hit is executable and defensive predicates are correct |
| U08 held items and combinations | Plates/type boosters, Muscle Band/Wise Glasses, species orbs/Soul Dew, Choice/Eviolite/Vest; Life Orb **BP vs final damage discriminator**; Expert Belt; Gems before/after consumption and Acrobatics/Symbiosis; Metronome counter; resist berries/Unnerve/Ripen | Per-item stages, identities and consumption authority |
| U09 modifier order / integer boundaries | Combined ability + item + terrain + weather + burn/screen + crit + STAB/type; choose inputs that distinguish sequential floors from fixed-point accumulation and different roll placement | Establishes source pipeline beyond matching isolated multipliers |
| U10 burn / screens | Physical burn, Guts, Facade, special control; residual burn separately; Singles Reflect/Light Screen/Veil, crit, Brick Break and Infiltrator; frostbite refuses until proved | Selected-hit status/screen subset |
| U11 topology and partner integration | Singles, Doubles single target, spread with two vs one surviving eligible targets, all-target ally presence, ally faint transitions; screen two vs one defender; Battery/Power Spot/Steely Spirit/Flower Gift/Friend Guard | Any later Doubles admission; static format alone fails |
| U12 dynamic/fixed/multi-hit families | HP/speed/weight/history power and type/category changes per effect ID; Psywave outcome domain; level damage and immunity; counters with missing-history refusal; per-strike crit/rolls, Skill Link/Battle Bond, Parental Bond ×0.25 vs ×0.5, escalating strikes and item/HP transitions | Separate later move-family admissions, not a blanket move allow-list |
| U13 transformations / Z / Max / raids | Post-Mega stats/type/ability and transition timing; Z base conversion/signature/protection; Max power and HP / Choice suppression / anti-Max moves; raid stars, shield HP divisor, threshold caps, non-raid shields | Dedicated gimmick implementation; initial admission can instead prove absent and refuse active cases |
| U14 settings / custom mechanics | Each release difficulty label and raw value; level scaling/caps; each available randomizer; inverse + Freeze-Dry/grounding/immunity exceptions; facility/Circus/boss rules; Portal Power contact/non-contact and assignment; release custom-data delta census | Bounded rule domains; no inference that private differences are absent |
| U15 production boundary and refusal | Missing/stale/mismatched slot snapshot, unknown flags/IDs, unread settings/field/HP, invalid operands, wrong format/gen/data pack; relevant unsupported mechanic; named soft-ignore sanitation and independent hard-refusal precedence | Proves the shipped request, not just a hand-built JS input, respects the matrix |

U01–U04, U09 and U15 plus proof that all other relevant effects/rules are neutral
or explicitly supported are prerequisites for even the narrow initial ordinary
Singles subset. Later feature groups are required **before admitting those
features**; leaving them refused is acceptable. Reuse existing vanilla/H&S
fixtures only as engine regression controls, never as Unbound goldens.

## 5. Open questions requiring exact-ROM or runtime evidence

1. Independently establish the owner's version/hash association and the running
   ROM trust path. What private commit, build flags, patches and data revisions
   produced this release? Can an authenticated build manifest be obtained?
2. Which public arithmetic branches are actually present: crit rate/multiplier,
   terrain/Gem/-ate boosts, Parental Bond, badges, Explosion, Hidden Power,
   burn/frostbite and all weather rules? Does exact integer order match the
   public path, including Life Orb, Flash Fire and Ripen?
3. Where are release difficulty, cap, randomizer, facility/Circus and boss/gym
   rules stored, what are their raw domains/defaults, and when can they change?
   What is the exact Vanilla/Difficult/Expert/Insane UI mapping? Is there any
   actual player-facing “Gen 8 mechanics” option, rather than compile-time flags
   or Gen 8 encounter unlocks?
4. What are the release's species/form/base-stat/type, move/effect/flag,
   ability and held-effect/quality tables? Which differ from public CFRU/DPE or
   generic Gen 8? Is Portal Power enabled/assigned, and are there other custom
   abilities or move scripts? Public DPE's Magician record cannot answer this.
5. Which exact live layouts and lifecycle windows supply raw stats/types
   (including a third type), effective/suppressed abilities/items, consumption,
   stages, HP/status, grounding, terrain/weather, screens, volatiles, target and
   partner state? How are stale or unsettled snapshots detected and refused?
6. How are transformations, Z/Max conversions, raid stars/shields and special
   non-raid boss shields detected? Which moves use base versus scaled HP? Can
   they be proven inactive for the initial subset without guessing from mode?
7. Which independent exact-release hits establish each admission and which
   full-vector source/oracle fixtures support them? Record evidence tier per
   mechanic; host/public-source agreement alone does not close release parity.

This document supplies an inventory and an admission proposal only. Policy,
profiles, runtime readers, data packs, bundle and fixtures remain unchanged.
Documentation validation is diff review plus `git diff --check`; no gameplay,
Gradle, build, or calculator test evidence is claimed by this task.
