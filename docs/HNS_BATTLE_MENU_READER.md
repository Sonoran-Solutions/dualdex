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

The native reader requires the exact compiled H&S configuration, ACTIVE lifecycle,
readable IWRAM, and a verified singles topology. Player positions identify the
battler; gActiveBattler is never read. More than one player flank yields unknown
menu state and no selected battler, including a doubles battle with an absent
partner. Link/partner and inconsistent format flags also fail closed.
The action-selection battle-main callback authorizes the menu handlers;
unrecognized player controller functions yield animation/text. An ACTIVE singles
battle whose main callback leaves action selection yields TURN_RUNNING, with
player identity but no menu cursors. Cursor values outside 0..3 remain unavailable.
Other layouts retain legacy behavior.

JNI packs state in bits 0..3, battler+1 in 4..6, action cursor+1 in 8..10, move
cursor+1 in 12..14, and player count in 16..18. Zero is unavailable for the latter
fields. Native code 6 is target selection; 7 is TURN_RUNNING. The ViewModel decodes
this tuple for polling and direct macro reads, passing both cursors and topology to
the policy. A failed core read is null, distinct from native state zero; polling
retains the previous snapshot and macros retry up to three times, one frame apart.

Move macros run on Dispatchers.Default and reject overlapping taps, unknown
topology, pause, or unavailable core readers. Each button spans at least one
completed frame down and one up; edges start between core frames. Only directional
presses add 20 settle frames. Navigation changes the observed column, then row,
only when needed. Each directional result is checked against a direct core read.
The last read authorizes A without a duplicate cursor read. Input authority/battler
changes, missing cursors, dropped required directions, pause, dispatch failure,
and watchdog expiry abort.

After the move A, text/animation can precede a result. TURN_RUNNING proves success,
target selection reports without further input, and returning to a move/command
menu for the same player after a non-menu state reports MoveRefused. If the menu
never leaves within six completed frames, ConfirmNotRegistered asks the user to
tap again; automation never retries A. Otherwise observation times out.
Frame waits suspend on the coordinator's completed-frame StateFlow.

An exclusive session spans navigation through post-A observation. A physical
button held at session start refuses the tap with Cancelled("Physical button
held"). During the session, physical changes are recorded but never dispatched.
Session cleanup restores physical routing and latches held buttons until released;
only release/repress can produce another physical edge. Cancellation, exceptions,
and watchdogs release the session. Core writes happen outside the router state lock.
Only the coordinator's distinct LockTimeoutException retries an edge write: three
attempts with 2 ms lock waits bound total acquisition waits to 6 ms per edge (about
three emulated frames at 8x). Other exceptions fail immediately. Exhausted release
writes clear input with an unlocked zero as the last resort, then fail. The down
frame starts counting only after a successful press write.

Bounds: singles move selection only; 1200 completed-frame / 20-second macro watchdog,
2-second stalled-frame wait, and 120 frame-by-frame observations for transitions
and move acceptance (each read may also consume up to two retry frames).
Party switching remains outside this issue. Existing tile source-slot order and
zero-PP disabling are unchanged. Device timing, actual move restrictions such as
Encore/Taunt, and the hardware input matrix still require #175's acceptance.
