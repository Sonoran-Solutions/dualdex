# DualDex Navigator visual system

Issues #133 (Navigator shell) and #151 (party in the game's own idiom). This is the
spike: the shell, Map, and Party. Other screens pick up the palette and panels through
the semantic tokens but have not been redesigned. Gate status: **pending physical-device
review** (see the end of this file).

## Style contract

- `CompanionVisualStyle { NAVIGATOR, QUIET_HANDHELD }` in `DualDexTheme.kt`. Navigator is
  the default. Classic (Quiet Handheld) can be picked under **Settings → Display →
  Companion Style**, and picking it rebuilds the companion right away without touching the
  emulator. The setting is stored as `key_companion_visual_style`.
- Classic is a true old/new comparison for the gate: it restores the original palette,
  shell, and the original Party layout (`ClassicPartyScreenView`, unchanged from before this
  spike). The pixel Party below is Navigator-only.
- Screens ask for semantic roles (`DualDexTheme.Color.surface`, `.accent`, `.focusRing`,
  ...). They never ask which style is active. The few Navigator-only touches (shell,
  micro-labels, map console strips) check `DualDexTheme.isNavigator` in one place each.
- Tokens are read when a view is built, so a style change rebuilds the shell
  (`CompanionPresentation.rebuildContent()` / `MainActivity.rebuildCompanionForStyleChange()`).

## Navigator vocabulary

| Role | Value | Notes |
| --- | --- | --- |
| Shell rim | `#C9D2DD` | 6dp, with a bevel highlight; the only light surface |
| Bezel | `#172436` | status strip and soft keys sit on it |
| LCD background | `#0A2E3A` | viewport and screen backgrounds |
| Raised panel | `#103F4B` | `surface(elevated = true)` |
| Text | `#EAF9F5` / `#AFC3C9` | primary / secondary |
| Accent (selected) | `#6BE4E8` | selected nav plate, primary buttons, header strips |
| Focus cursor | `#FFE178` | controller focus only, so it never looks like "selected" |
| Success / danger | `#72D69C` / `#FF7B7B` | always next to text |

- Panels are `NavigatorPanelDrawable`: a thin outline with clipped corners and an optional
  header strip set into the border. The viewport frame is drawn as a foreground, so it also
  clips the corners of whatever screen is inside it.
- Type: system sans for body text. Monospace bold (`DualDexTheme.Type.device`) for short
  device labels, values, the clock, and micro-labels.
- Micro-labels (`CART TEAM BATTLE NAV SYS`, `NAVIGATOR · LOCATION`, ...) are only
  decoration. TalkBack never hears them; the visible labels and content descriptions keep
  the full names.
- `● LIVE` appears only for a verified ROM. It is text plus colour, never colour alone.
- Motion: the selected party slot's Poké Ball bobs (300 ms) and the existing map locator
  pulse runs. The bob checks Android's animator setting on every tick, so turning
  animations off stops it at rest even mid-bob. There is no flicker, no
  scanlines, and nothing animates on a live update.

## Party slots (#151)

- Six slots are drawn in code on the GBA pixel grid. Each slot renders at one GBA pixel per
  pixel into a small bitmap, which is then blitted at an integer scale with filtering off.
  `PartySlotModel.chooseGrid` picks the largest scale that fits 3×2, 2×3, or 1×6. On the
  Thor's 1240×1080 lower screen that is 3×2 at 4×. Extra width makes the window wider, so
  the HP bar grows instead of leaving empty bars at the sides.
- Slots never drop below the 48dp touch target, and a rendered slot never exceeds its cell.
  When six slots at that floor do not fit the height budget (55% of the screen), the grid
  caps itself at the budget and scrolls instead of clipping. The scale only drops below the
  touch floor when even one column cannot hold it, with a hard floor of an 84 px cell.
- Frame states: normal, selected, fainted, selected+fainted, no-HP (eggs), and no-HP
  selected. Missing party members draw as empty frames that can't be focused.
- The HP bar colour and minimum width follow Gen 3 `GetHPBarLevel` / `GetScaledHPFraction`
  (green above 24/48, yellow above 9/48, at least 1 px while alive). Status badges (PSN TOX
  PAR SLP FRZ BRN FNT, plus `???` for unknown bits) reuse the calculator's live status bit
  layout. Gender comes from the pinned H&S gender-ratio byte and is hidden when unknown.
- `PartySlotModel.hpColor` is the single HP-band authority on this screen: the slot bar and
  the detail meter both use it, so 51–52% reads yellow in both places. Other screens keep
  their existing raw-ratio colours.
- Controller focus draws yellow corner brackets around the slot. Selection uses a white
  frame and an opened ball. Pressed adds a light overlay. Each slot's content description
  reads the name, level, HP, and status.
- The detail panel keeps every field it showed before (name, species, level, types, HP,
  nature, held item, stats with IV/EV, EV total, moves, type defenses). It now splits them
  into Status / Moves / Defense pages, and the chosen page survives the 10 Hz poll and
  switching members.

## Non-copying rules

- No official logos, fonts, sprites, icon sheets, sounds, or extracted UI graphics. The
  Poké Ball sprite, slot frames, bitmap font (`PixelFont.kt`), and palettes are original
  work drawn in code for this project.
- Layout rules and thresholds may follow the public pret decompilations, as facts that are
  re-implemented. Their assets may not be copied.
- PokeDaisey (GPLv3) was used only as a description of the approach. None of its code or
  PNGs are used.
- Bundled H&S art and ROM-extracted mini icons (#151 phase 2) are **not** in this change.
  The H&S authors have not confirmed their licence, and icons must come from the player's
  own ROM at runtime. The procedural slot stays as the fallback renderer when that lands.

## Gate (#133 phase 4)

| Check | Status |
| --- | --- |
| Shell, Map, Party on Thor hardware (touch) | Done: `docs/navigator/` screenshots |
| Classic ⇄ Navigator live switch with a game running | Done on Thor |
| Party HP/state readable at a glance; selection and detail page persist across live polls | Done on Thor |
| Classic shows the original Party layout (true A/B) | Done on Thor: `classic-party.png` |
| Smaller viewport: ~300 px companion in phone split-screen, slots at the 48dp floor, grid scrolls instead of clipping | Done on a Galaxy Z Fold cover screen: `small-viewport-party.png` (no ROM loaded, so empty frames) |
| Default style before the ruling | Owner decision: Navigator stays the default; Classic is one tap away |
| Controller-focus walk and enlarged font scale | **Human reviewer** |
| A / B / C recommendation | **Human reviewer**, recorded on #133 |
