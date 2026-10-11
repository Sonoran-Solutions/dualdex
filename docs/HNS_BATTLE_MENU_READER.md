# H&S 2.0.5 battle-menu reader (#174)

Implementation evidence only. Profile interaction flags remain **false**. AYN Thor
acceptance and flag activation belong to #175; no device input, saves, or states were used.

Target: official ROM SHA-256
`edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b`;
upstream `Release-v2.0.5`, commit `1f42b74dff0e9fe942419845d040663dd829a973`.

## Release bindings

`tools/hns-layout/generate_hns_release_phase_evidence.py` emits addresses, instruction
signature hashes, input hashes, and match counts in `release_phase_evidence.json`.
No cartridge bytes are emitted. Source inputs are the existing non-LTO source build
whose ROM and symbol hashes are retained in that artifact. The source map resolves
static `HandleInputChooseAction` specifically inside `battle_controller_player.o`;
the Oak and Safari functions share its name and must never win a dictionary lookup.

| Symbol | Source build | Official release, Thumb bit clear |
| --- | --- | --- |
| HandleInputChooseAction (player) | `0x0805D648` | `0x0805D1D0` |
| HandleInputChooseMove | `0x0805C5EC` | `0x0805C09C` |
| HandleInputChooseTarget | `0x0805C0D8` | `0x0805BB84` |
| gBattlerControllerFuncs (4 function pointers) | `0x03002F3C` | `0x03002F54` |
| gActionSelectionCursor (4 bytes) | `0x020003A4` | `0x020003A4` |
| gMoveSelectionCursor (4 bytes) | `0x020003A8` | `0x020003A8` |

The action handler uses a unique 128-byte instruction prefix. Move and target
handlers use unique 16- and 30-byte prefixes, respectively, ending before compiler
instruction scheduling/stack-size differences. Each has a separate unique 64-byte
interior match. A matched target-handler literal load independently references the
move entry. Cursor arrays are resolved from matched literal-load instruction
sequences and agree with the source symbols. Two independent instruction matches
(move and target handlers) resolve the controller array to `0x03002F54`.
**Its source IWRAM address is not the release address.** No fixed relocation delta
is used to derive any runtime address.

Regenerate the phase evidence using the command documented in
`tools/hns-runtime-probe/README.md` (including `--source-map`), then regenerate
`native/src/hns_live_battle_layout_gen.h` with the live-layout generator. Both
commands support `--verify`. Ordinary CI remains ROM-free: it checks generated
menu definitions, release identity, binding corroboration, and synthetic
instruction/literal/static-symbol cases.

## Runtime contract

The new native reader requires the exact compiled H&S configuration, ACTIVE
lifecycle, readable IWRAM, and the action-selection battle-main callback. Player
positions identify the battler; `gActiveBattler` is never read. More than one player
flank yields unknown menu state and no selected battler, including a doubles battle
with an absent partner. Link/partner and inconsistent format flags also fail closed.
Unrecognized player controller functions yield animation/text.
Cursor values outside 0..3 remain unavailable. Other layouts retain legacy behavior.

JNI packs state in bits 0..3, battler+1 in 4..6, action cursor+1 in 8..10, move
cursor+1 in 12..14, and player count in 16..18. Zero is unavailable for the latter
fields. Native code 6 is target selection. The ViewModel decodes this same tuple
for polling and direct macro reads, passing both cursors and topology to the policy.

Move macros reject overlapping taps, unknown topology, pause, or unavailable core
readers. Each button spans at least one completed frame down and one up; edges start
between core frames, with 20 additional settle frames. Navigation always starts LEFT,
UP, then RIGHT/DOWN as needed. Every directional result is checked against a direct
core read, including command normalization before opening FIGHT. The final move
cursor is read again before A. Input authority/battler changes, missing cursors,
dropped required directions, pause, dispatch failure, and watchdog expiry abort.
A target-selection state after A is reported with no additional input. Physical
inputs remain merged; a physically held copy of the automated button is refused
because it cannot generate a fresh edge. Cancellation releases automated input.

Bounds: singles move selection only; 1200 completed-frame / 20-second macro watchdog,
2-second stalled-frame wait, and 120 frames to observe the command-to-move transition.
Party switching remains outside this issue. Existing tile source-slot order and
zero-PP disabling are unchanged. Device timing, restrictions such as Encore/Taunt,
and physical input during a macro still require #175's acceptance matrix.
