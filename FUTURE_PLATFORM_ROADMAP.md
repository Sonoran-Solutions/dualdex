# DualDex Future Platform Roadmap

> **Long-term roadmap only. This document is explicitly not part of the AYN Thor `0.9.0-beta.1` release scope.**
>
> The current GBA beta should be stabilized, released, and validated with real users before work begins here. Nothing in this document should delay the initial beta.

## Vision

DualDex should eventually become more than a GBA emulator with a Pokémon companion. The long-term goal is a **multi-system Pokémon emulation companion platform** that can support the main-series games and ROM hacks/mods from Generation 1 through Generation 9 while preserving the thing that makes DualDex different:

> **the game stays fully playable, while DualDex adds trustworthy live party/battle data, version-aware damage calculations, trainer/encounter information, and searchable documentation around it.**

The important architectural lesson is that “support every generation” should **not** mean forcing every console into the same emulator integration model.

A scalable design should support several emulator/memory backends behind one companion API:

```text
                 DualDex Companion UI
                         │
                  GameIntegration
                         │
              Normalized Pokémon Snapshots
                         │
                    MemoryTransport
          ┌──────────────┼──────────────┐
          │              │              │
   Libretro RAM      Emulator RPC    GDB / Debugger
  GB/GBC/GBA/NDS        3DS           Switch
```

Nintendo DS / Platinum Kaizo remains the recommended first expansion because it exercises multi-screen rendering, touch input, a new Pokémon data format, and a new emulator core without jumping immediately to the much larger 3DS/Switch integration problem.

---

# Support Model: Do Not Promise “Every Hack Just Works”

The end goal can be broad compatibility without pretending arbitrary modified games are safe to parse automatically.

DualDex should expose explicit integration levels:

| Level | Meaning | Features |
|---|---|---|
| **0 — Emulator Only** | System/core can run the game, but DualDex does not understand it. | Emulation, saves, states, controls. |
| **1 — Static Companion** | DualDex can identify/extract game data but does not trust live memory. | Dex, moves, trainers, encounters, searchable docs, manual calculator. |
| **2 — Live Player State** | Exact game/version layout verified. | Party, levels, moves, HP/status, location where available. |
| **3 — Live Battle Integration** | Battle structures/lifecycle verified. | Active battlers, opponent, field state, auto-filled calculator. |
| **4 — Enhanced Controls** | Exact UI state/input mappings verified. | Optional context-aware battle controls/macros using normal emulator input. |

A new hack should be allowed to fall back safely from Level 3 to Level 1 rather than displaying plausible-but-wrong live information.

---

# Generation / Platform Feasibility Matrix

| Era | Main platforms | Example games | Emulator strategy | Live companion feasibility | Overall |
|---|---|---|---|---|---|
| **Gen 1** | GB | Red / Blue / Yellow | Existing mGBA core or GB-specific libretro core | Straightforward RAM model; excellent reverse-engineering resources | **Very High** |
| **Gen 2** | GB / GBC | Gold / Silver / Crystal | Existing mGBA core or GB/GBC core | Straightforward RAM banking; excellent disassemblies | **Very High** |
| **Gen 3** | GBA | RSE / FRLG + hacks | Existing mGBA integration | Already DualDex’s current platform | **Current** |
| **Gen 4** | NDS | DPPt / HGSS + hacks | DeSmuME or melonDS DS libretro | Well researched; Platinum decomp + existing live sync tooling | **High** |
| **Gen 5** | NDS | BW / BW2 + hacks | Reuse NDS platform layer | Same console foundation; separate data/runtime parser | **High** |
| **Gen 6** | 3DS | XY / ORAS + hacks/randomizers | Azahar integration or bridge | Guest-memory RPC exists; static data tooling is mature | **Medium-High** |
| **Gen 7** | 3DS | SM / USUM | Reuse 3DS platform layer | Same emulator family, new game layouts/mechanics | **Medium-High** |
| **Gen 7-era** | Switch | Let’s Go Pikachu/Eevee | Switch emulator integration | Static extraction is mature; runtime integration is harder | **Medium** |
| **Gen 8** | Switch | Sword/Shield, BDSP, Legends: Arceus | Switch emulator integration | Multiple distinct engines; debugger access is plausible | **Medium** |
| **Gen 9** | Switch | Scarlet/Violet | Switch emulator integration | Static data extraction exists; runtime/version complexity is high | **Medium** |

The hardest part of later generations is **not the calculator itself**. The official Smogon damage-calculator library already supports all generations and exposes an adaptable generation/data interface. The hard parts are emulator integration, exact build identity, live-memory mapping, and hack/mod-specific mechanics.

---

# Platform Research Findings

## 1. Game Boy / Game Boy Color — Generations 1 and 2

This is probably the cheapest future expansion after the GBA beta.

The current mGBA libretro core already supports `.gb`, `.gbc`, and `.gba`, along with saves, states and memory monitoring. That means DualDex may not need another emulator core at all for Gen 1/2.

Alternative GB/GBC cores such as Gambatte, SameBoy and Gearboy exist, but using mGBA first would minimize core-management and licensing complexity because DualDex already ships it.

### Why Gen 1/2 are attractive

- Small/simple memory maps compared with later systems.
- Mature Pokémon disassemblies from `pret`.
- Huge ROM-hack ecosystems.
- Existing DualDex single-screen + companion layout already fits perfectly.
- The same `GameDataPack`, `PlayerSnapshot`, and calculator abstractions can be validated on a much simpler system before tackling 3DS/Switch.

### Recommended approach

- [ ] Add `GB` and `GBC` system descriptors to the generalized mGBA session.
- [ ] Implement Gen 1 Pokémon/player/battle parser against verified vanilla Red/Blue/Yellow builds.
- [ ] Implement Gen 2 parser against verified Crystal first, then Gold/Silver.
- [ ] Generate static data from ROM/disassembly-compatible formats where practical.
- [ ] Add exact-version profiles for popular hacks rather than assuming all hacks share vanilla layouts.

Potential reverse-engineering references include `pret/pokered` and `pret/pokecrystal`.

**Recommendation:** Gen 1/2 should become the first “new generations” after the generic platform work, even if DS remains the first *new console core*.

---

## 2. Game Boy Advance — Generation 3

This remains the current production foundation.

The GBA beta work should intentionally produce reusable pieces for every later platform:

- stable content identity,
- safe save storage,
- emulator-thread ownership,
- versioned engine/layout profiles,
- compatibility states,
- generic snapshots,
- `GameDataPack`,
- calculator-rule abstraction.

Do not throw away the GBA architecture when multi-system work starts. Refactor it until GBA becomes the first implementation of the generic interfaces.

---

## 3. Nintendo DS — Generations 4 and 5

Nintendo DS is the most logical first new console platform.

### Core options

#### melonDS DS

The maintained `melonDS DS` libretro core is technically attractive because it supports Android arm64, multiple screen layouts, touch/virtual cursor input, hardware rendering, saves/states, memory monitoring and modern libretro interfaces.

However, Platinum Kaizo’s current documentation recommends DeSmuME and warns about save behavior with melonDS-core emulators. That should be treated as a compatibility gate for that exact hack until tested on the current core/version.

#### DeSmuME

DeSmuME is the compatibility-first Platinum Kaizo candidate because the hack’s existing live-sync tooling targets it and the project currently recommends it.

The downside is that Android performance/JIT behavior may be less attractive than modern melonDS builds.

### Gen 4

Gen 4 is especially promising because:

- `pret/pokeplatinum` provides a readable Platinum decompilation,
- Gen 4 Pokémon structures are well documented,
- Platinum Kaizo already has live DeSmuME-to-calculator synchronization,
- tools such as DSPRE and `hzla/ddex` demonstrate static extraction/editing of Gen 4 data.

### Gen 5

Once the DS platform layer is stable, Black/White and Black 2/White 2 should be a **new game integration**, not a new emulator project.

Useful research/tooling already exists:

- Pokémon Black decompilation work,
- Gen 5 ROM editors that understand trainers, encounters, moves, species data and scripts,
- mature save/Pokémon structure research in the wider PKHeX ecosystem.

Gen 5 will still need its own runtime parser and battle-state mapping, but it should reuse:

- DS rendering,
- touch/controller handling,
- DS memory transport,
- save/state infrastructure,
- ROM identity,
- static NDS archive readers.

**Recommendation:** after Platinum Kaizo, Gen 5 is the highest-value next platform target because the expensive DS work is already paid for.

---

## 4. Nintendo 3DS — Generations 6 and 7

3DS support requires a different emulator-integration strategy.

### Emulator direction: Azahar

Azahar is the active open-source 3DS emulator that emerged from the post-Citra ecosystem and currently provides Android builds. Its published Android requirements are comfortably below modern Thor-class hardware on paper, although real Pokémon performance still needs device testing.

The particularly important finding for DualDex is that Azahar includes a scripting/RPC server whose configuration explicitly says it allows **remote guest-memory access**. Azahar also retains debugger/GDB infrastructure.

That makes a companion bridge plausible without requiring a libretro memory API.

### Why not simply use the legacy Citra libretro core?

A Citra libretro core exists, but current Libretro documentation lists memory monitoring as unsupported. That makes it much less attractive for DualDex’s defining live-companion feature.

A future prototype should compare:

1. **Azahar RPC bridge** — DualDex reads verified guest-memory ranges through a localhost/debug transport.
2. **Azahar source integration** — deeper integration/fork if dual-display rendering and lifecycle require it.
3. **Legacy Citra libretro** — emulator-only fallback/prototype, not the preferred live-data architecture unless memory exposure is added.

### Static game data

`pk3DS` supports the 3DS Pokémon titles and already understands data such as:

- trainers,
- wild encounters,
- personal/base stats,
- moves,
- learnsets,
- evolutions,
- marts and related tables.

That is strong evidence that a 3DS `GameDataPackExtractor` is practical even before live memory works.

### 3DS UX on Thor

A 3DS integration can reuse the DS multi-screen renderer concept:

- Thor top display: 3DS top screen, optionally with a companion sidecar.
- Thor bottom display: 3DS touch screen by default.
- Controller shortcut: instantly toggle the physical bottom display between the real 3DS bottom screen and DualDex companion tabs.
- Initially render one stereoscopic eye only; stereoscopic 3D is not a DualDex requirement.

### Recommended 3DS order

- [ ] Vanilla Pokémon X/Y emulator prototype.
- [ ] Reliable save/update handling.
- [ ] RPC/debug memory transport prototype.
- [ ] Gen 6 static data extraction.
- [ ] Gen 6 party snapshot.
- [ ] Gen 6 battle snapshot/calculator.
- [ ] ORAS profile.
- [ ] Gen 7 integration for Sun/Moon, then Ultra Sun/Ultra Moon.
- [ ] Only then begin validating ROM hacks/randomizers/modded builds.

---

## 5. Nintendo Switch — Generations 8 and 9

Switch is the largest technical jump and should be considered a separate long-term program rather than “Phase 2 of DS support.”

### Emulator landscape

There is no mature Switch libretro path comparable to mGBA/DS.

Active open-source Switch emulator projects such as Eden provide Android/ARM64 builds. Eden’s current debugging documentation exposes a guest GDB stub and memory mappings, which creates a plausible live-memory transport for DualDex.

That suggests the first Switch experiment should be a **debugger/RPC companion bridge**, not immediately embedding an entire Switch emulator into DualDex.

Possible long-term models:

```text
A. ExternalEmulatorBridge
DualDex Companion <-> localhost GDB/RPC <-> Switch emulator

B. IntegratedNativeEmulator
DualDex owns/forks emulator frontend and calls emulator core directly

C. Companion-only mode
User runs supported emulator; DualDex provides static docs/calc if live bridge is unavailable
```

Model A is the lowest-risk research path. Model B provides the best eventual Thor UX but carries the most engineering and licensing burden.

### Static data is much more feasible than full live integration

`pkNX` already demonstrates structured extraction/editing for Switch Pokémon titles including:

- Let’s Go Pikachu/Eevee,
- Sword/Shield,
- Legends: Arceus,
- Scarlet/Violet data dumping.

This strongly supports a Switch `GameDataPack` pipeline for species, moves, encounters, trainers and other game data.

### Switch should not be treated as one Pokémon engine

At minimum, expect separate integrations for:

```text
LetsGoIntegration
SwordShieldIntegration
BDSPIntegration
LegendsArceusIntegration
ScarletVioletIntegration
```

These titles differ far more internally than FireRed-based ROM hacks do.

BDSP in particular should be treated as its own engine family rather than “Sword/Shield with different data.” Legends: Arceus also needs game-specific battle/calculator concepts rather than assuming standard main-series battle flow.

### Switch identity must include updates and mods

A single cartridge/dump hash is no longer enough.

Recommended identity metadata:

```text
ModernGameIdentity
- systemId
- titleId / programId
- base-content identity
- executable/build identity
- installed update version
- relevant DLC set
- mod-pack fingerprint
- integration profile version
```

A Scarlet/Violet mod running against one update must never silently inherit memory offsets from another update.

### Thor UX advantage

Ironically, Switch may eventually produce the cleanest DualDex layout:

- **Top physical display:** full 16:9 Switch game.
- **Bottom physical display:** full-time DualDex companion.

Unlike DS/3DS, there is no original second game screen competing with the companion for space.

Potential bottom-screen tabs:

- Battle / Calc
- Party
- Opponent
- Team / Trainer lookup
- Encounters
- Docs
- Raid information where supported

This is an excellent end-state UX even though the emulator integration is substantially harder.

---

# Cross-Generation Calculator Strategy

DualDex should stop thinking of the calculator as “the Gen 3 calculator.”

The upstream `@smogon/calc` project is designed for all generations and supports an adaptable data layer. Long term, DualDex should expose:

```text
CalculatorEngine
  + GenerationRules
  + GameDataPack
  + GameMechanicsOverrides
  + BattleSnapshot
```

Examples of profile-specific rules include:

- Gen 1 special/stat behavior,
- Gen 2 mechanics,
- pre/post physical-special split,
- Fairy type availability,
- Mega Evolution,
- Z-Moves,
- Dynamax/Gigantamax,
- Terastallization,
- hack-specific type charts,
- hack-specific moves/abilities/items,
- Legends-specific battle rules.

The calculator should be generation-aware by default and hack-aware through explicit overrides/data rather than edits to one global bundle.

---

# Proposed Multi-System Architecture

## 1. Emulator session abstraction

The current mGBA host should become one implementation of a wider session API.

```text
EmulatorSession
- sessionId
- systemId
- emulatorId
- loadGame()
- unloadGame()
- run/pause/reset()
- saveState()/loadState()
- saveData()
- videoSurfaces
- audioInfo
- inputCapabilities
- memoryTransport
```

Possible implementations:

```text
LibretroEmulatorSession   // mGBA, DeSmuME, melonDS DS
RpcEmulatorSession        // Azahar bridge
GdbEmulatorSession        // Switch emulator bridge
NativeEmulatorSession     // future deeper 3DS/Switch integration
```

The companion layer should not care which one is active.

---

## 2. Memory transport abstraction

This is the key addition needed for Gen 1–9 scalability.

```text
MemoryTransport
- listRegions()
- read(region/address, length)
- readScatter(requests)
- resolveModule(name/id)      // optional
- getBuildIdentity()
- capabilities
```

Implementations:

```text
LibretroMemoryTransport
- retro_get_memory_data()
- RETRO_ENVIRONMENT_SET_MEMORY_MAPS

RpcMemoryTransport
- local emulator scripting/RPC service

GdbRemoteMemoryTransport
- GDB remote protocol guest-memory reads

NativeMemoryTransport
- direct emulator-core API when integrated in-process
```

Parsers should never know whether bytes came from mGBA, Azahar or Eden.

### Security rule

RPC/GDB bridges should bind to localhost only by default and must not expose write access to the network. Companion functionality should be read-only unless a narrowly defined feature explicitly requires normal emulator input.

---

## 3. Console adapter

```text
ConsoleAdapter
- systemId
- supportedEmulators
- video-layout capabilities
- input mapping
- touch capabilities
- save conventions
- normalized memory regions
```

Examples:

```text
GameBoyAdapter
GameBoyAdvanceAdapter
NintendoDSAdapter
Nintendo3DSAdapter
NintendoSwitchAdapter
```

---

## 4. Game integration adapter

```text
GameIntegration
- compatibility detector
- runtime parser
- PlayerSnapshot provider
- BattleSnapshot provider
- GameDataPack provider
- CalculatorRules
- documentation provider
- optional verified UI actions
```

Examples:

```text
Gen1PokemonIntegration
Gen2PokemonIntegration
Gen3PokemonIntegration
PlatinumGen4Integration
Gen5PokemonIntegration
Gen6PokemonIntegration
Gen7PokemonIntegration
SwordShieldIntegration
ScarletVioletIntegration
```

ROM hacks/mods extend a known engine/version profile where possible instead of becoming separate emulator code paths.

---

## 5. Core-independent snapshots

```text
PlayerSnapshot
- party
- location
- inventory summary (optional)

BattleSnapshot
- isInBattle
- battleFormat
- playerBattlers
- enemyBattlers
- active party indexes
- HP/status/stat stages
- weather/terrain/field state
- trainer identity if known
- special mechanic state (Mega/Z/Dynamax/Tera/etc.)

PokemonSnapshot
- species/form
- level
- current/max HP
- stats
- IVs/EVs/DVs where applicable
- moves/PP
- item
- ability
- status
```

The Party and Calculator screens should consume these objects without knowing the console generation.

---

## 6. `GameDataPack`

Static game knowledge belongs in a normalized data layer:

```text
GameDataPack
- exact game/version identity
- generation
- species/forms
- moves
- abilities
- items
- type chart
- evolutions
- learnsets
- trainer sets
- encounters
- shops/items/TMs where available
- level caps / progression metadata
- mechanics overrides
- documentation index
```

Sources, in preferred order:

1. **Local extraction from the user’s game dump/mod installation.**
2. **Declarative profile maintained by DualDex.**
3. **Explicitly licensed community data pack.**

Local extraction is ideal because it can follow the exact modified build and reduces stale spreadsheet problems.

---

## 7. Game identity model

For cartridge-era systems:

```text
GameIdentity
- systemId
- contentSha256
- gameProfileId
- versionProfileId
```

For install/update-based platforms:

```text
ModernGameIdentity
- systemId
- title/program identity
- executable/build identity
- update version
- DLC/mod fingerprint
- gameProfileId
- versionProfileId
```

Storage should remain content-aware while save states remain emulator/core-specific:

```text
saves/<system>/<game-id>/...
states/<system>/<game-id>/<emulator-id>/...
data/<system>/<game-id>/game-data-pack.*
profiles/<system>/<game-id>/...
```

---

## 8. Generic display-surface model

Do not special-case `topScreen` and `bottomScreen` forever.

```text
GameVideoSurface
- surfaceId
- nativeWidth/nativeHeight
- touchCapable
- preferredPhysicalDisplay
- crop/layout metadata
```

This supports:

- GB/GBC/GBA: one game surface + companion surface,
- DS: two game surfaces competing with companion space,
- 3DS: two differently sized game surfaces,
- Switch: one 16:9 game surface + a dedicated companion display.

---

## 9. Community integration manifests

Long-term hack/mod support should be contributable without requiring people to rewrite DualDex internals.

A safe declarative profile format could contain:

```text
IntegrationManifest
- schemaVersion
- systemId
- baseGame
- exact supported versions/hashes/build IDs
- engine/layout profile
- memory symbols/offsets
- mechanics flags
- data-extractor selection
- docs links
- feature support levels
```

Do not load arbitrary executable code from community profiles. Keep downloadable/community integrations declarative unless they are reviewed and shipped as part of DualDex itself.

Hack authors could optionally publish a DualDex manifest alongside a release, which would be much more sustainable than reverse engineering every update after the fact.

---

# Platform UX Concepts

## GB / GBC / GBA

Keep the current model:

- top display = game,
- bottom display = full companion.

This remains the cleanest DualDex experience.

## Nintendo DS

### Full DS mode

- Top physical display = DS top screen.
- Bottom physical display = DS touch screen.
- Companion hidden.

### Sidecar mode

A 4:3 DS top screen rendered full-height on the Thor top display leaves horizontal pillarbox space. Use that space for a narrow opponent/damage sidebar without covering the game.

### Companion mode

- Top display keeps DS top screen + optional sidecar.
- Bottom display toggles between the real DS touchscreen and DualDex tabs.
- Provide an instant controller shortcut to restore the DS screen.

### Advanced battle-control mode

Only after exact UI state is verified, DualDex can offer enhanced move buttons showing damage ranges and then send normal stylus/button input to the game. Never modify game memory for basic control automation.

## Nintendo 3DS

Use the same philosophy as DS:

- top display = 3DS top surface + optional sidecar,
- bottom display = real 3DS touchscreen by default,
- instant toggle to companion,
- one-eye rendering initially.

## Nintendo Switch

This is the ideal eventual hardware layout:

- top display = full game,
- bottom display = persistent DualDex companion.

No toggle is required unless the user explicitly wants a different layout.

---

# Long-Term Implementation Sequence

## Phase 0 — Ship and stabilize GBA

Do not begin this roadmap until:

- the GBA public beta is in users’ hands,
- save identity/storage migrations are stable,
- emulator-core ownership/threading is stable,
- engine/layout profiles are authoritative,
- unsupported ROMs fail safely,
- the calculator is profile/version aware.

---

## Phase 1 — Multi-system foundation

Goal: make the existing GBA build run through generic interfaces with no user-visible regression.

- [ ] `EmulatorSession`
- [ ] `MemoryTransport`
- [ ] `ConsoleAdapter`
- [ ] generic video surfaces
- [ ] generic input capabilities
- [ ] generic system/save/state directories
- [ ] core-independent snapshots
- [ ] versioned `GameDataPack`
- [ ] expanded game identity model

Keep mGBA as the regression test.

---

## Phase 2 — Generation 1 / 2 support through mGBA

Goal: prove the abstractions on a second Pokémon data format without another emulator core.

- [ ] Red/Blue/Yellow verified profiles.
- [ ] Crystal verified profile.
- [ ] Gold/Silver profiles.
- [ ] Gen 1/2 battle/calculator rules.
- [ ] Initial popular-hack profiles only after vanilla layouts are solid.

---

## Phase 3 — Nintendo DS emulator prototype

Goal:

> **Vanilla Pokémon Platinum boots reliably, uses both Thor displays, accepts touch/controller input, and safely saves/restores.**

Prototype DeSmuME and melonDS DS.

Test:

- [ ] boot/reboot,
- [ ] battery save + restore,
- [ ] save states,
- [ ] audio/frame pacing,
- [ ] suspend/resume,
- [ ] touch/controller input,
- [ ] both DS screens,
- [ ] memory access,
- [ ] 60+ minute soak sessions.

Do not choose the production DS core until Platinum Kaizo save behavior has been validated.

---

## Phase 4 — Vanilla Platinum Gen 4 integration

- [ ] Gen 4 Pokémon decrypt/unshuffle/checksum parser.
- [ ] Player party snapshot.
- [ ] Battle lifecycle.
- [ ] Active player/enemy battlers.
- [ ] Singles first; doubles afterward.
- [ ] Gen 4 calculator rules.
- [ ] Static Platinum `GameDataPack` extraction.
- [ ] Golden fixtures/tests.

Use documented structures and `pret/pokeplatinum` rather than guessing offsets.

---

## Phase 5 — Platinum Kaizo

Treat every supported release as an exact version profile.

- [ ] Record exact supported version/hash.
- [ ] Validate emulator save compatibility.
- [ ] Validate live memory layout.
- [ ] Generate/verify hack-specific `GameDataPack`.
- [ ] Validate trainer sets, encounters, moves, abilities and mechanics.
- [ ] Auto-import player/opponent into calculator.
- [ ] Trainer/encounter/docs lookup.
- [ ] Add optional Thor sidecar/companion battle UX only after parsing is stable.

Never silently treat a future Kaizo update as the same build.

---

## Phase 6 — Generation 5

Reuse the entire DS platform layer.

Recommended order:

1. Vanilla Black/White exact revision.
2. Vanilla Black 2/White 2.
3. Static Gen 5 data extraction.
4. Live player/battle parser.
5. Gen 5 calculator integration.
6. Popular hacks after exact-version validation.

This should be substantially cheaper than the initial DS work.

---

## Phase 7 — Nintendo 3DS platform spike

Goal:

> **Run a vanilla Gen 6 title on Thor and prove reliable read-only guest-memory access through Azahar or another maintained 3DS emulator integration.**

- [ ] Benchmark Azahar on relevant Thor models.
- [ ] Validate top/bottom rendering strategy.
- [ ] Validate saves/updates.
- [ ] Prototype localhost RPC guest-memory reads.
- [ ] Build `RpcMemoryTransport`.
- [ ] Ensure bridge is localhost/read-only by default.
- [ ] Decide external bridge vs deeper source integration.

---

## Phase 8 — Generations 6 and 7

- [ ] XY static data extraction + `GameDataPack`.
- [ ] XY live player/battle snapshots.
- [ ] ORAS profile.
- [ ] Gen 6 calculator mechanics.
- [ ] Sun/Moon profile.
- [ ] Ultra Sun/Ultra Moon profile.
- [ ] Gen 7 calculator mechanics.
- [ ] Exact-version hack/randomizer profiles.

---

## Phase 9 — Nintendo Switch bridge prototype

Do not start by embedding a Switch emulator.

Goal:

> **Run one supported Switch Pokémon title in a maintained Android emulator and prove that DualDex can safely identify the exact game build and read verified guest-memory state through a debugger bridge.**

- [ ] Benchmark maintained emulator(s) on Thor hardware.
- [ ] Prototype GDB guest-memory reads.
- [ ] Build `GdbRemoteMemoryTransport`.
- [ ] Resolve guest module/build identity reliably.
- [ ] Measure polling overhead.
- [ ] Verify suspend/resume/reconnect behavior.
- [ ] Decide whether an external bridge can provide acceptable dual-display UX.
- [ ] Only consider source/native integration after the bridge proves the concept.

---

## Phase 10 — Generations 8 and 9

Treat each Switch engine as its own integration.

Possible order:

1. Sword/Shield — conventional battle flow and strong tooling support.
2. Let’s Go — separate mechanics/integration.
3. BDSP — separate engine family.
4. Legends: Arceus — custom battle semantics.
5. Scarlet/Violet — latest/highest-complexity Gen 9 target.

For each title:

- [ ] exact base/update identity,
- [ ] static `GameDataPack`,
- [ ] party snapshot,
- [ ] battle snapshot,
- [ ] calculator mechanics,
- [ ] DLC/update validation,
- [ ] mod fingerprinting,
- [ ] safe fallback when an unknown update/mod is detected.

---

## Phase 11 — Community compatibility ecosystem

Only after the profile format is mature:

- [ ] document `IntegrationManifest` schema,
- [ ] validator CLI/tool,
- [ ] diagnostics output for memory-layout authors,
- [ ] community profile repository,
- [ ] signed/versioned downloadable data profiles,
- [ ] compatibility matrix generated from manifests/tests,
- [ ] contribution docs aimed at ROM-hack authors.

The ideal long-term outcome is that a hack author can ship a small DualDex compatibility manifest/data pack rather than waiting for DualDex itself to reverse engineer every release.

---

# Licensing / Distribution Strategy

Licensing becomes increasingly important as more emulator projects are involved.

Currently relevant examples include:

- libretro API: MIT
- mGBA: MPL-2.0
- DeSmuME libretro: GPLv2
- melonDS DS libretro: GPLv3+
- Azahar: GPLv2-or-later
- Eden: GPLv3-or-later

DualDex is currently MIT licensed.

Do **not** assume dynamic loading, IPC, a plugin boundary, or separate APK automatically answers copyleft/distribution questions.

Before shipping any new emulator component:

- review its exact license and dependencies,
- document source/version/build provenance,
- determine whether the emulator is bundled, separately installed, or communicated with through a bridge,
- preserve required license notices/source obligations,
- review consequences for any future Play Store/commercial distribution.

This roadmap is technical planning, not legal advice.

DualDex must never distribute commercial ROMs, proprietary BIOS/firmware/NAND data, encryption keys, title keys, or copyrighted game assets. Users supply their own legally obtained game/system data where required.

---

# Major Risks

## 1. Scope explosion

The architectural end state may support Gen 1–9, but implementation should happen one verified title at a time.

Do not build “Switch support” while DS is still unstable.

## 2. Emulator project churn

3DS/Switch emulator projects can change maintainers, APIs, licenses and Android support. Keep `EmulatorSession` and `MemoryTransport` boundaries strong so an emulator can be replaced without rewriting Pokémon integration code.

## 3. Version/update drift

The farther forward the generation, the less useful a single ROM hash becomes. Exact executable/update/mod identity is mandatory.

## 4. False confidence on hacks

A recognizable game title does not mean its live memory layout is safe. Compatibility levels must remain explicit.

## 5. Performance

GB/GBC/GBA/DS polling is cheap. 3DS/Switch debugger transports may not be. Snapshot reads should be batched and rate-limited rather than issuing hundreds of tiny remote reads every frame.

## 6. Licensing

The technically best emulator may not be the simplest emulator to distribute with an MIT application. Resolve shipping architecture before committing to deep integration.

## 7. UI complexity

DS/3DS require preserving an actual touchscreen while offering companion UI. The user must always have an immediate, predictable way back to the original game screen.

---

# Recommended Order of Expansion

Once the current GBA beta is genuinely stable:

1. **Generalize GBA into the multi-system architecture.**
2. **Gen 1/2 through the existing mGBA core.**
3. **Nintendo DS platform + vanilla Platinum.**
4. **Platinum Kaizo.**
5. **Gen 5 on the existing DS platform.**
6. **3DS platform spike with Azahar RPC/debug transport.**
7. **Gen 6, then Gen 7.**
8. **Switch debugger/bridge research.**
9. **Gen 8 integrations.**
10. **Gen 9 / Scarlet-Violet integration.**
11. **Only then formalize a community manifest/profile ecosystem for broad hack/mod coverage.**

This ordering maximizes reuse at each step rather than jumping directly from GBA to the hardest platform.

---

# Reference / Research Links

These should be rechecked when implementation starts because emulator projects and tooling change over time.

### Libretro / classic systems

- mGBA libretro documentation: https://docs.libretro.com/library/mgba/
- Libretro memory-monitoring reference: https://docs.libretro.com/guides/memorymonitoring/
- Gambatte documentation: https://docs.libretro.com/library/gambatte/
- SameBoy documentation: https://docs.libretro.com/library/sameboy/
- melonDS DS documentation: https://docs.libretro.com/library/melonds_ds/
- DeSmuME documentation: https://docs.libretro.com/library/desmume/
- Citra libretro documentation: https://docs.libretro.com/library/citra/

### Reverse engineering / ROM data

- pret organization / reverse-engineering projects: https://github.com/pret
- Pokémon Red/Blue disassembly: https://github.com/pret/pokered
- Pokémon Crystal disassembly: https://github.com/pret/pokecrystal
- Pokémon Platinum decompilation: https://github.com/pret/pokeplatinum
- Pokémon Black decompilation project: https://github.com/pokemodding/pokeblack
- DSPRE Gen 4 ROM editor: https://github.com/DS-Pokemon-Rom-Editor/DSPRE
- Frost’s Gen 5 Editor: https://github.com/FrostFalcon/FrostsGen5Editor
- Gen 4 ROM-data/calc tooling (`ddex`): https://github.com/hzla/ddex
- pk3DS 3DS Pokémon ROM tooling: https://github.com/kwsch/pk3DS
- pkNX Switch Pokémon ROM tooling: https://github.com/kwsch/pkNX
- PKHeX save/Pokémon-format research reference: https://github.com/kwsch/PKHeX

### Calculator

- Smogon damage calculator / `@smogon/calc`: https://github.com/smogon/damage-calc

### 3DS emulator

- Azahar: https://github.com/azahar-emu/azahar
- Azahar RPC/debug configuration source: https://github.com/azahar-emu/azahar/blob/master/src/android/app/src/main/jni/default_ini.h

### Switch emulator research

- Eden project mirror: https://github.com/eden-emulator/mirror
- Eden guest-debugging documentation: https://github.com/eden-emulator/mirror/blob/master/docs/Debug.md

### Platinum Kaizo

- Platinum Kaizo documentation/resources: https://platinumkaizo.com/documentation-resources/
- Platinum Kaizo FAQ / emulator notes: https://platinumkaizo.com/frequently-asked-questions/
- Platinum Kaizo live-sync Lua tool: https://github.com/anastarawneh/PKLuaScript

### AYN Thor

- AYN Thor product page/specifications: https://www.ayntec.com/products/ayn-thor

---

# Relationship to the Current Beta

This roadmap stays **outside the current release checklist’s beta exit criteria**.

The current priority remains:

1. protect GBA saves,
2. stabilize the current mGBA lifecycle,
3. make ROM/version detection trustworthy,
4. make GBA memory profiles authoritative,
5. make the existing companion/calculator accurate,
6. release and learn from real AYN Thor users.

That work is not separate from the Gen 1–9 vision. It is the foundation for it.

If DualDex gets ROM identity, memory transport, versioned profiles, core ownership, generic snapshots and calculator rules right on GBA, the project can grow generation-by-generation without becoming a collection of unrelated emulator hacks.

---

# Parallel Future Track — Pokémon Champions External Companion Mode

Pokémon Champions creates a fundamentally different opportunity from the emulator integrations above: DualDex would **not own or emulate the game process at all**. The official Android client can remain the authoritative game on one physical display while DualDex runs as a separate companion application on the other.

This should be treated as a parallel post-beta research track rather than part of the Generation 8/9 Switch-emulation sequence.

## Core UX

On dual-display Android hardware such as the AYN Thor:

```text
TOP DISPLAY
┌──────────────────────────────────────────┐
│                                          │
│       Official Pokémon Champions         │
│                                          │
└──────────────────────────────────────────┘

BOTTOM DISPLAY
┌──────────────────────────────────────────┐
│               DualDex                    │
│                                          │
│ Battle / Calc / Scout / Team / Speed     │
│ Coverage / Pokédex / Opponent            │
└──────────────────────────────────────────┘
```

The first Android-specific requirement is to verify that DualDex can keep a real companion `Activity` alive on the secondary display while Pokémon Champions owns the primary display. The existing `Presentation` approach may not be the right lifecycle primitive for this mode because the main foreground activity belongs to another application.

## Reuse Champions Helper as the data pipeline

The existing `Sonoran-Solutions/Champions-Helper` project should remain a separate research/data-generation project rather than being embedded wholesale into the Android application.

Its Showdown-based ingestion pipeline can generate versioned offline data packs for DualDex containing:

- legal species and forms,
- moves,
- abilities,
- items,
- type data,
- regulation legality,
- Mega information,
- speed-tier inputs,
- calculator metadata.

Conceptually:

```text
Champions-Helper ingestion
          │
          ▼
ChampionsDataPack
- regulation id
- species/forms
- moves
- abilities
- items
- mechanics metadata
          │
          ▼
DualDex Android assets / downloadable data
```

Do **not** make the Android app depend on the Helper's PostgreSQL + Express runtime. Generate compact versioned assets instead.

Regulations must be treated as first-class versions. A change in the active Champions regulation should select a different data pack rather than require application code changes.

## Live state: visual observation, not cross-app RAM access

The normal GBA path works because DualDex owns mGBA and can read the emulator's memory directly. Pokémon Champions is a separate Android application and normal Android sandboxing prevents DualDex from treating its process memory like an emulator core.

The preferred live-state experiment is therefore:

```text
Pokémon Champions display
          │
          ▼
Android MediaProjection capture
          │
          ▼
ChampionsVisualStateProvider
- known UI-region crops
- OCR where appropriate
- sprite/icon/template recognition
- HP-bar estimation
- confidence scoring
          │
          ▼
Normalized BattleSnapshot
          │
          ▼
DualDex battle / calculator UI
```

This should remain a **read-only visual companion**. Do not make direct process-memory reading, network interception, APK modification, hidden-information extraction, or gameplay automation part of the design target.

Only information visible or reliably inferable from the player's own game screen should enter the live snapshot. Unrevealed opponent moves, items, EVs, or other hidden state must remain unknown rather than guessed and presented as fact.

## Required architecture refactor

The current companion path should eventually stop depending directly on `LibretroHost` calls inside `CompanionViewModel`.

Introduce a generic source abstraction such as:

```text
GameStateProvider
- playerState
- opponentState
- battleState
- confidence / validity metadata

MgbaStateProvider
- reads emulator memory

ChampionsVisualStateProvider
- reads captured visual state

FutureRpcStateProvider
- reads emulator RPC/debug state
```

The companion UI should consume normalized `PlayerSnapshot`, `PokemonSnapshot`, and `BattleSnapshot` objects regardless of whether the underlying source is emulator RAM, RPC, debugger memory, or computer vision.

This refactor is valuable even if Champions integration ultimately fails because it removes the current assumption that all live data must originate from an in-process libretro core.

## Go / no-go proof of concept

Do **not** port the full Champions Helper UI or begin OCR work first.

The first prototype should do only this:

1. [ ] Detect/install-check the official Pokémon Champions Android package without bundling it.
2. [ ] Launch or place Pokémon Champions on the Thor primary display.
3. [ ] Keep a minimal DualDex companion `Activity` active on the secondary display.
4. [ ] Request normal Android `MediaProjection` screen-capture permission.
5. [ ] Capture Pokémon Champions frames and display a live thumbnail/debug view in DualDex.
6. [ ] Confirm the game does not return black/secure frames during team preview and battle.
7. [ ] Measure CPU/GPU/battery impact at approximately 2–5 analyzed frames per second.
8. [ ] Verify suspend/resume, focus changes, and dual-display lifecycle behavior.

**Go condition:** DualDex can reliably capture useful Champions frames while both applications remain playable and responsive.

**No-go condition:** Champions consistently blocks capture, the Thor cannot keep both activities usable, or the capture cost materially harms gameplay. If this gate fails, stop rather than escalating into invasive process/network reverse engineering.

## Suggested capability sequence after a successful capture POC

### Champions C0 — Static companion

- [ ] Import generated `ChampionsDataPack` assets.
- [ ] Champions Pokédex/scout view.
- [ ] Team builder.
- [ ] Defensive synergy and offensive coverage analysis.
- [ ] Speed tiers.
- [ ] Regulation-aware damage calculator.
- [ ] Item/ability reference.

This is essentially the useful parts of Champions Helper adapted to DualDex's native companion UI.

### Champions C1 — Team-preview recognition

Team preview is the best first computer-vision target because the layout is relatively stable and the information changes infrequently.

- [ ] Detect the player's six Pokémon.
- [ ] Detect the opponent's six Pokémon.
- [ ] Match visible species/forms against `ChampionsDataPack`.
- [ ] Auto-populate Scout / matchup / coverage views.
- [ ] Attach confidence to every recognition result.
- [ ] Allow immediate manual correction when recognition is uncertain.

### Champions C2 — Read-only live battle observation

Add only state that can be derived reliably from visible UI/events:

- [ ] active player Pokémon,
- [ ] active opponent Pokémon,
- [ ] approximate/current HP where visually reliable,
- [ ] visible status conditions,
- [ ] visible weather/terrain/field indicators,
- [ ] revealed item/ability events,
- [ ] substitutions/faints,
- [ ] battle lifecycle and turn transitions where confidence is high.

A 2–5 Hz analysis loop is likely sufficient; Pokémon battle state does not require per-frame computer vision.

### Champions C3 — Auto-filled battle calculator

Feed verified visual state into the same calculator/battle UI used by other DualDex integrations.

- [ ] Auto-fill attacker/defender species/forms.
- [ ] Apply visible field conditions.
- [ ] Show speed comparisons where known.
- [ ] Show damage ranges based on known data.
- [ ] Clearly distinguish exact/observed values from assumptions or user-entered values.
- [ ] Never present guessed hidden opponent information as verified.

## Initial feasibility estimate

| Capability | Estimate |
|---|---|
| Champions static Pokédex/team/calculator features | **Very High** |
| Champions on top + DualDex on bottom | **High** |
| Team-preview species recognition | **High if capture works** |
| Active Pokémon / visible battle-state recognition | **Medium-High** |
| HP/status/field tracking | **Medium-High, UI dependent** |
| Useful near-real-time `BattleSnapshot` | **Medium-High after incremental validation** |
| Direct Champions process-memory integration | **Not a normal Android design target** |
| Hidden-information extraction / automated play | **Out of scope** |

## Product value

If successful, this would broaden DualDex from an emulator-specific companion into a more general **dual-screen Pokémon companion platform**:

```text
GBA / classic games
DualDex owns emulation
        ↓
Memory-backed GameStateProvider

Pokémon Champions
Official app owns gameplay
        ↓
Visual GameStateProvider

Both
        ↓
Shared DualDex companion UI + calculator + data packs
```

That architectural model could eventually support other external games or emulators where DualDex does not own the game process, provided the integration remains reliable, read-only, and transparent about confidence.

### Research references

- Champions Helper: https://github.com/Sonoran-Solutions/Champions-Helper
- Android MediaProjection API: https://developer.android.com/media/grow/media-projection
- Android multi-display/activity launch APIs: https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)
