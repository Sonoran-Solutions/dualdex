# DualDex UI Design Audit & Redesign Roadmap

This document reviews the current companion UI from the implementation in `app/src/main/java/com/dualdex/companion/ui` and `assistant/AssistantScreenView.kt`.

The goal is not to redesign DualDex into a generic Material app. The goal is to make it feel intentionally designed for a gaming handheld: compact, fast, readable, consistent, and clearly its own product.

> Note: this is a code-level design audit rather than a screenshot-by-screenshot visual review. A final visual polish pass should still be done on the actual AYN Thor hardware once these foundations are in place.

---

## Executive Summary

The current UI is functional and information-rich, but it often feels like each screen was designed independently. That is probably the main source of the current "AI vibe-coded" feeling.

The issue is not that the interface is too simple. It is that there are too many visual decisions happening at once:

- Emoji used as the primary icon system.
- Nearly every feature is placed inside a rounded card.
- Many cards have their own accent color.
- Blue, green, yellow, red, purple, cyan, and slate are often used decoratively instead of semantically.
- Corner radii vary widely between screens.
- Some screens use raw pixels while others correctly convert spacing to dp.
- Titles and helper copy are often much longer than necessary.
- Status pills such as `Ready`, `LIVE SYNC`, `LIVE BATTLE`, environment badges, and other labels compete for attention.
- Navigation exposes ten equal-weight tabs at once.
- Individual screens use slightly different color systems and interaction patterns.

The strongest improvement would come from **removing visual decisions**, not adding more decoration.

DualDex should feel more like a purpose-built handheld companion tool and less like a collection of feature-demo cards.

---

# Recommended Design Direction

## "Quiet Handheld Companion"

A good design target for DualDex would be:

- Dark and OLED-friendly.
- Dense enough to be useful on a handheld.
- One strong brand accent.
- Minimal decorative color.
- Fast to scan during gameplay.
- Built around live game state rather than app chrome.
- Slightly technical, but not debug-tool-looking.
- Clearly distinct from official Pokémon UI rather than imitating it.

The second screen should feel like an instrument panel for the game running above it.

The information should be visually more important than the containers around it.

## Implementation Status

The shared View-based foundation and global navigation shell are now in place. `DualDexTheme` and `DualDexComponents` establish semantic color, spacing, radius, typography, surface, button, and interaction-state tokens for new companion chrome. The shell now exposes Library, Party, Battle, Map, and More through vector-backed navigation; legacy Calculator and Type Matchups remain available from More as secondary Battle tools.

The existing `BattleConsoleScreenView` is now the primary Battle destination. Its internal visual treatment remains intentionally out of scope for this foundation pass and should receive its own focused redesign later.

---

# What Is Already Working

A redesign should preserve the following strengths:

- The dark theme is appropriate for the device and use case.
- The feature organization itself is understandable.
- The Party screen has genuinely useful live information.
- The calculator is unusually well integrated with live game state.
- The full-screen Map approach is much stronger than putting a map inside another card.
- Save management is exposed instead of hidden.
- The Assistant already follows a familiar chat structure.
- Type colors are useful when they actually represent Pokémon types.
- The UI is built from straightforward Android Views and can be incrementally refactored without a risky framework rewrite.

Do not throw those strengths away just to make the app look fashionable.

---

# Highest-Priority Design Problems

## 1. There Is No Shared Design System

Colors, spacing, radii, buttons, cards, titles, chips, and states are recreated directly inside each screen.

Examples include different background colors such as:

- `#101014`
- `#121216`
- `#0F172A`

And multiple slightly different surface colors such as:

- `#181822`
- `#1E1E26`
- `#16161E`
- `#1E293B`

This makes screens feel like separate prototypes instead of one product.

### Fix

Create a tiny shared design layer before redesigning individual screens.

Suggested files:

- `ui/DualDexTheme.kt`
- `ui/DualDexComponents.kt`

Or use Android color/style resources if preferred.

At minimum centralize:

- Colors
- Spacing
- Corner radii
- Typography sizes
- Button styles
- Input styles
- Card/surface styles
- Selected/focused/pressed states
- Status colors

A full Compose rewrite is **not** recommended before beta. The current View architecture is fine if the styling is centralized.

---

## 2. Raw Pixels and dp Are Mixed

Some screens correctly use a `dp()` helper while others pass literal values such as:

```kotlin
setPadding(20, 20, 20, 20)
cornerRadius = 14f
```

Those are raw pixels, not density-independent pixels.

Party, Cheats, and Map convert values with screen density, while Home, Calculator, Settings, Saves, Type Chart, and Assistant contain many raw pixel values.

This can make spacing and corner radii look different across devices and is especially important for the longer-term single-screen Android goal.

### Fix

- [ ] Convert all layout spacing and radii to dp.
- [ ] Use a shared `dp()` utility or resource dimensions.
- [ ] Define spacing tokens rather than arbitrary values.

Recommended spacing scale:

- `4dp` — tight internal spacing
- `8dp` — related controls
- `12dp` — standard compact gap
- `16dp` — normal page/section spacing
- `24dp` — major separation

Avoid one-off values unless the layout genuinely requires them.

---

## 3. Emoji Are Doing Too Much UI Work

Examples include:

- `🎮 DualDex Game Library`
- `📁 Open ROM`
- `⚡ Continue Last Game`
- `👥 Active Party`
- `⚔️ Damage Calculator`
- `💾 Save & Migration Manager`
- `🤖 DualDex ROM Hack Assistant`
- `⚙️ DualDex Settings & Configuration`

This is one of the strongest "AI-generated dashboard" signals.

Emoji also render differently across Android versions and devices, making them poor core UI icons.

### Fix

Replace navigation and action emoji with a small consistent vector icon set.

Good options:

- Material Symbols / Material Icons
- Custom simple monoline vectors
- A small DualDex-specific icon set later

Use emoji only when they are actual content, not interface chrome.

Type badges can remain colorful because their color is meaningful game data.

---

## 4. Too Much Decorative Color

The current screens often assign a different accent to each section:

- Blue headings
- Green headings
- Gold headings
- Red defender headings
- Purple map states
- Cyan map states

This creates a "rainbow dashboard" effect.

### Fix

Use color by **role**, not by section.

Recommended roles:

- **Accent** — selected state, primary actions, links
- **Success** — successful state, healthy HP, connected/available state
- **Warning** — medium HP or actual warning
- **Danger** — destructive actions, low HP, critical failure
- **Muted** — secondary information
- **Type colors** — Pokémon type badges only

Do not make a Settings section yellow simply because another section is blue.

### Example Palette Direction

These values are only a starting point, not mandatory final colors:

- Background: `#101116`
- Surface: `#181A20`
- Elevated Surface: `#1F222A`
- Border: `#2A2E38`
- Primary Text: `#F4F5F7`
- Secondary Text: `#9AA0AA`
- Accent: `#5B9CFF`
- Success: `#55C878`
- Warning: `#E5B84B`
- Danger: `#EF6262`

The minimal blue used in the DD branding is a good candidate for the main accent.

---

## 5. The Interface Is Over-Carded

Cards currently surround:

- Resume controls
- Folder controls
- Every ROM
- Party sections
- Calculator sections
- Settings groups
- Save groups
- Each cheat
- Type selectors
- Results
- Assistant responses

Rounded cards are useful, but when everything is a card nothing feels important.

### Fix

Use cards only for actual objects or high-value focal content.

Good uses:

- Continue/Resume game
- Selected Pokémon summary
- Current damage result
- Assistant messages
- A map floating panel

Better as plain sections or list rows:

- Settings
- ROM library items
- Save slots
- Cheat rows
- Controller mapping
- Folder configuration

Use dividers and spacing to group ordinary settings instead of wrapping everything in a rounded rectangle.

---

## 6. Ten Bottom Navigation Tabs Are Too Many

The current nav exposes:

- Home
- Party
- Map
- Calc
- Types
- Docs
- Cheats
- Saves
- Assistant
- Settings

They are squeezed into a horizontally scrolling bottom navigation bar with very small labels.

This treats every feature as equally important and makes the product feel like a feature collection instead of an opinionated tool.

### Recommended Navigation

Use five primary destinations:

1. **Library**
2. **Party**
3. **Battle**
4. **Map**
5. **More**

### Battle

Combine related live battle tools:

- Damage Calculator
- Type Matchups

These can be internal tabs/segments inside Battle.

### More

Place lower-frequency utilities here:

- Saves
- Docs
- Assistant
- Cheats
- Settings

This keeps those features available without permanently consuming navigation space.

It will also translate far better to normal phones later.

---

## 7. Too Many Status Pills and "Live" Labels

Current examples include:

- `Ready`
- `● LIVE SYNC`
- `⚔️ LIVE BATTLE`
- Battery pills
- Environment pills
- Preset pills
- Region buttons

These elements individually make sense, but together they create dashboard noise.

### Fix

Status should usually disappear when everything is normal.

Examples:

- Do not show `Ready` all the time.
- Replace `LIVE SYNC` with a small green dot if sync state matters.
- Show a battle indicator only while actually in battle.
- Keep battery information only if Android system UI is hidden and the information is useful.

The best normal state often has **no status badge at all**.

---

## 8. Copy Is Often Too Long and Too Technical

Several labels read like feature descriptions rather than finished product copy.

Examples:

| Current | Suggested |
| --- | --- |
| `🎮 DualDex Game Library` | `Library` |
| `Select a game to start playing on the top screen with companion stats below` | Remove, or `Choose a game to start.` |
| `⚡ Continue Last Game` | `Continue` |
| `▶️ Resume Playthrough` | `Resume` |
| `⚔️ Damage Calculator` | `Battle` or `Damage` |
| `🛡️ Type Matchup & Weakness Calculator` | `Type Matchups` |
| `💾 Save & Migration Manager` | `Saves` |
| `⚙️ DualDex Settings & Configuration` | `Settings` |
| `🤖 DualDex ROM Hack Assistant` | `Assistant` |
| `Searching game documentation and web grounding...` | `Searching…` |
| `No party data (Waiting for ROM)` | `No game loaded` |

Engineering details such as:

`59.7 FPS target | EWRAM Poller: 10Hz | Audio: 32768Hz`

should live in **Diagnostics / Advanced**, not normal Settings.

---

# Proposed Visual Foundation

## Typography

Keep typography simple.

Suggested hierarchy:

- Screen title: `22sp`, semibold/bold
- Section title: `16sp`, medium/semibold
- Primary body: `14sp`, regular
- Secondary/meta: `12sp`, regular
- Compact labels: `11–12sp`, medium

Avoid making nearly every label bold.

Bold text should communicate hierarchy, not simply make the UI feel "important."

---

## Corner Radius

Current screens use many values from roughly 10 to 20.

Recommended:

- Small controls: `8dp`
- Standard surfaces/cards: `10–12dp`
- Pills: fully rounded only for true badges/tags

Avoid 18–20dp rounded cards everywhere. Smaller radii make the app feel more like a utility and less like a generated SaaS dashboard.

---

## Buttons

Define four consistent button styles.

### Primary

For the most important action on the screen.

Examples:

- Resume
- Save
- Ask

Filled accent color.

### Secondary

For normal actions.

Examples:

- Import Save
- Export Save
- Change Folder

Neutral surface with border.

### Ghost / Icon

For lightweight toolbar actions.

Examples:

- Refresh
- Center Map
- Reset Zoom

### Destructive

For actual destructive actions only.

Examples:

- Delete Cheat
- Clear API Key

Use danger color sparingly.

There should normally be only one visually dominant primary action in a given section.

---

## Interaction States

Custom `GradientDrawable` backgrounds currently replace much of Android's standard button feedback.

Some screens have pressed states while many do not.

This matters especially on a gaming handheld.

Every interactive component should have consistent:

- Default
- Pressed
- Focused
- Selected
- Disabled

### Controller Focus

Add a clearly visible focus ring for physical controller navigation.

A focus state should be more obvious than a subtle color shift because users may interact with the bottom screen without touching it.

- [ ] Minimum `48dp` touch/focus targets for primary controls.
- [ ] Consistent focus ring.
- [ ] Consistent pressed feedback.
- [ ] Disabled controls visibly distinguishable from normal controls.

---

# Screen-by-Screen Recommendations

## Library / Home

### Current Problems

- Product name is repeated in the screen title.
- Resume, folder setup, search, and every ROM use separate card treatments.
- Play buttons repeat on every row.
- Folder setup feels as visually important as game selection.
- Emoji appear throughout.

### Recommended Layout

**Top**

`Library`

Search field and small folder/settings action.

**Continue section**

One prominent resume surface:

- Game name
- Compatibility state
- Resume button

**Games section**

Use flat list rows rather than separate cards.

Each row can show:

- Game/hack title
- File/version metadata
- Compatibility badge
- Optional last-played information
- Chevron or small Play icon

Make the entire row clickable rather than requiring a large Play button on every game.

### Compatibility Badges

This is an ideal place to expose the compatibility work from the release checklist:

- Verified
- Unverified
- Emulator Only

These statuses are more useful than decorative card colors.

---

## Party

This screen should probably be the visual centerpiece of DualDex.

### Current Problems

- `Active Party` + `LIVE SYNC` consumes valuable vertical space.
- Party selector chips are large and visually heavy.
- Pokémon details rely on large blocks of text separated with pipes.
- HP is text rather than a strong visual indicator.
- Empty state contains debug/tool-style wording such as `=== DualDex Party Monitor ===`.

### Recommended Layout

**Party strip**

A compact six-slot horizontal strip or 2×3 layout.

Each member should show:

- Name
- Level
- Small HP bar
- Selected state

Do not require every slot to look like a full card.

**Selected Pokémon header**

- Name
- Level
- Type badges
- HP bar
- Nature
- Held item

**Stats**

Use a six-column stat grid instead of pipe-separated paragraphs.

Example:

| HP | Atk | Def | SpA | SpD | Spe |
| --- | --- | --- | --- | --- | --- |
| 146 | 135 | 92 | 88 | 96 | 121 |

IV/EV information can be shown as secondary values beneath each stat.

**Moves**

Use compact rows with:

- Move name
- Type
- Power
- Accuracy

The currently selected/most relevant move can receive accent emphasis.

### Empty State

Replace the current monitor-style message with:

`No game loaded`

`Party data will appear when a supported game is running.`

---

## Battle / Damage Calculator

The calculator is a major differentiator and should feel faster than a conventional form.

### Current Problems

- It is visually structured like a settings form.
- Attacker, defender, field conditions, moves, and results all receive similar card weight.
- The result is visually separated from the information the player is actually trying to compare.
- Long opponent stat paragraphs are difficult to scan during gameplay.

### Recommended Layout

**Top Matchup Row**

`Attacker  →  Defender`

Show names, levels, HP, and type badges.

When live opponent data is available, make that clear with a small indicator rather than a large `LIVE BATTLE` pill.

**Moves as the primary result**

Each move row should immediately show:

- Move name/type
- Damage range
- Percentage range
- KO likelihood if available

Example concept:

`Thunderbolt   84–99 HP   62–73%`

This lets the player compare all four moves at a glance.

The best/currently selected option can receive subtle accent emphasis.

**Field Modifiers**

Weather, screens, critical hit, etc. should be a compact secondary control row or collapsible `Field` section.

Do not let modifiers dominate the screen unless the player is actively editing them.

---

## Type Matchups

### Current Problems

- Three large cards are used for a simple two-input tool.
- Eighteen brightly colored type buttons create an extremely busy screen.
- Decorative green/yellow section headings add more color on top of already semantic type colors.

### Recommended Direction

Fold Type Matchups into the **Battle** destination as a secondary mode.

Use:

- `Type 1` selector
- `Type 2` selector
- Immediate weaknesses/resistances results

Type colors themselves are semantically useful and should remain.

Everything surrounding them should be neutral.

Results should prioritize multipliers:

- `4×`
- `2×`
- `½×`
- `¼×`
- `Immune`

Avoid additional warning/sparkle/shield emoji.

---

## Map

The Map screen is currently one of the stronger design concepts because it allows the map to be the main content rather than placing it inside a card.

### Keep

- Full-bleed map.
- Floating top controls.
- Collapsible bottom details.
- Center-on-player action.
- Region switching.

### Improve

The Map currently uses a noticeably different slate/Tailwind-style palette from the rest of the app (`#0F172A`, `#1E293B`, cyan, purple, etc.).

Restyle the map controls using the same global DualDex surfaces and accent color as every other screen.

### Move Debug Information

The default subtitle currently exposes data such as:

- Map tile coordinates
- Save block group
- Map number

This is useful diagnostic information, but it is not normal player-facing information.

Default view should show something like:

`Johto • Route 32`

Detailed coordinates can move under an expandable `Technical details` or Diagnostics section.

---

## Saves

### Current Problems

- Title sounds like a system utility: `Save & Migration Manager`.
- Battery save, quick saves, and slots are all visually equal cards.
- Import/export actions compete with the more common save/load actions.
- Every slot contains multiple buttons.

### Recommended Layout

Title: `Saves`

**Quick Save**

One compact high-priority section.

- Quick Save
- Quick Load
- Last saved timestamp

**Save Slots**

Flat rows:

`Slot 1      Sep 4, 9:42 PM       Load`

Selecting a row can expose overwrite/save actions if needed.

**Battery Save**

Lower-frequency utility section:

- Import `.sav`
- Export `.sav`
- Backup status

Use emulator-neutral wording as described in `RELEASE_CHECKLIST.md`.

When the auto-resume/manual-quicksave separation is implemented, surface those concepts clearly here.

---

## Settings

This screen currently has one of the strongest "generated settings dashboard" vibes.

### Current Problems

- Every category is a rounded card.
- Every category has its own heading color.
- Controls, explanatory copy, technical diagnostics, controller documentation, API configuration, and Cheats navigation all coexist at the same level.
- Several settings are implemented as custom colored buttons when standard switches/segmented controls would communicate state better.

### Recommended Structure

Use a standard grouped settings list.

### Display

- Shader
- Aspect ratio / scaling

### Emulation

- Fast-forward speed
- Audio options later if needed

### Controls

- Controller shortcuts
- Remapping later

### Assistant

- Gemini API key
- Model
- Privacy/help link

### Advanced

- Diagnostics
- Debug information

### About

- Version
- GitHub
- License
- Support

Remove the `Open Cheats Manager` card from Settings. Cheats already has its own destination.

Move detailed controller mapping into a Help/Controls screen or expandable information row.

Move performance metrics into Diagnostics.

---

## Assistant

### Current Problems

- The provider/model branding receives too much visual emphasis.
- Quick prompts are permanently hard-coded around Ghost Grey-specific questions.
- Search execution details are displayed prominently inside answers.
- The title repeats the product name and includes an emoji.

### Recommended Layout

Title: `Assistant`

Subtle optional subtitle:

`Answers use game documentation and web sources when available.`

Keep the familiar chat layout.

Suggestion chips are useful, but generate them from the active profile instead of hard-coding Ghost Grey examples.

Examples:

- `Evolution changes`
- `Where is Fly?`
- `Next gym`
- `Type changes`

Search queries should not appear as a large banner in ordinary answers.

Citations can appear as compact source links beneath the response.

Provider/model information belongs in Settings/About unless needed for an error state.

---

## Cheats

### Current Problems

- Header itself is placed inside a card.
- Three large equal-weight action buttons appear immediately.
- Cheat code text is permanently expanded.
- `ACTIVE` / `OFF` uses a custom button instead of a familiar switch.
- Delete action is always exposed.

### Recommended Layout

Use a normal screen title and compact toolbar actions:

- Add
- Presets
- More

Each cheat becomes a list row:

- Cheat name
- `Preset` badge if applicable
- On/off switch

Tap the row to reveal/edit the code.

Delete should live inside an overflow/details action rather than permanently taking visual space.

Code formatting can remain monospaced in the expanded details view.

---

## Docs

Keep documentation focused on the active game.

Recommended chrome:

- Simple `Docs` title
- Current game/profile below it
- Back/forward/open-external actions only when necessary

The WebView itself should receive as much space as possible.

Avoid surrounding the webpage with another heavy card layer.

---

# Top App Header

The current global header includes:

- Time
- Profile name
- Open ROM button
- Battle status
- Battery

This is a lot of chrome before the actual companion content begins.

### Recommended Header

If Android system bars are hidden and time/battery are genuinely necessary, keep them very quiet.

Suggested structure:

**Left:** active ROM / profile

**Right:** battle indicator when relevant + battery/time in muted text

Move `Open ROM` into Library rather than showing it on every screen.

Remove the permanent `Ready` state.

The header should mostly disappear into the interface instead of looking like another dashboard card.

---

# Information Hierarchy Rules

Before adding any visual element, classify it as one of these:

1. **Primary live information** — what the player needs right now.
2. **Interactive control** — something the player may change.
3. **Secondary metadata** — useful but not urgent.
4. **Diagnostic information** — useful for debugging/support.

Current UI often gives all four categories similar visual weight.

A redesign should heavily emphasize category 1, clearly expose category 2, mute category 3, and hide category 4 unless requested.

---

# Shared Components to Build

Before rewriting individual screens, create reusable components for:

- [ ] `screenTitle()`
- [ ] `sectionTitle()`
- [ ] `primaryButton()`
- [ ] `secondaryButton()`
- [ ] `iconButton()`
- [ ] `destructiveButton()`
- [ ] `surface()` / `card()`
- [ ] `listRow()`
- [ ] `statusIndicator()`
- [ ] `typeBadge()`
- [ ] `segmentedControl()`
- [ ] `styledInput()`
- [ ] `emptyState()`
- [ ] `divider()`
- [ ] controller focus/pressed drawable

Do not allow every screen to invent its own versions of these again.

---

# Responsive Design for Future Single-Screen Support

The UI cleanup should prepare for standard Android devices without blocking the Thor beta.

Avoid designing around one fixed panel size.

Create basic layout classes such as:

- **Compact** — phones / small handhelds
- **Medium** — landscape phones / handhelds
- **Expanded** — tablets / Thor companion panel where appropriate

The same components should rearrange rather than being entirely different screens.

Examples:

### Party

- Compact: vertical details with horizontally scrollable party strip.
- Expanded: party strip + two-column detail area.

### Battle

- Compact: matchup above moves.
- Expanded: matchup/controls beside damage results.

### Library

- Compact: one-column list.
- Expanded: wider list with metadata columns.

### Map

- Keep full-screen map on every size; change floating control dimensions only.

This will make the eventual Play Store/general-Android version much easier to build.

---

# Accessibility / Handheld UX

- [ ] Minimum 48dp primary interactive targets.
- [ ] Do not depend on color alone to communicate state.
- [ ] Check contrast for muted text and disabled states.
- [ ] Add content descriptions to icon-only controls.
- [ ] Ensure large Android font settings do not destroy layouts.
- [ ] Add clear controller focus indication.
- [ ] Define predictable focus order for physical controls.
- [ ] Avoid horizontally scrolling navigation where possible.
- [ ] Test every redesigned screen without touching the display.

Controller UX should be considered part of the design system, not an afterthought.

---

# Suggested Implementation Order

## Phase 1 — Foundation

- [x] Create shared color tokens.
- [x] Create spacing/radius tokens.
- [ ] Convert raw pixels to dp.
- [x] Create shared typography styles.
- [x] Create standard button/input/surface components.
- [x] Add pressed/focused/disabled states.
- [x] Replace emoji navigation icons with vectors.

## Phase 2 — Navigation and Global Chrome

- [x] Reduce navigation from ten destinations to five primary destinations.
- [x] Create `Library / Party / Battle / Map / More` structure.
- [ ] Merge Type Matchups into Battle.
- [x] Move Saves/Docs/Assistant/Cheats/Settings into More.
- [x] Simplify global header.
- [x] Move Open ROM action into Library.
- [x] Remove permanent Ready/LIVE badges where unnecessary.

## Phase 3 — Highest-Value Screens

Redesign these first because users will see them constantly:

1. [ ] Library
2. [ ] Party
3. [ ] Battle / Calculator
4. [ ] Saves
5. [ ] Settings

## Phase 4 — Secondary Screens

- [ ] Map palette/chrome cleanup.
- [ ] Assistant cleanup.
- [ ] Cheats list redesign.
- [ ] Docs chrome cleanup.

## Phase 5 — Responsive Foundation

- [ ] Add compact/medium/expanded layout behavior.
- [ ] Test on normal Android phone emulator/device.
- [ ] Test landscape handheld dimensions.
- [ ] Test controller-only navigation.

---

# Fastest "Remove the AI Vibe" Pass

If a full redesign is not practical before the first beta, these changes alone would make a noticeable difference:

1. [ ] Remove emoji from screen titles, navigation, and most buttons.
2. [ ] Use one accent color throughout the app.
3. [ ] Stop assigning different decorative colors to every section heading.
4. [ ] Reduce card corner radius and remove unnecessary cards.
5. [ ] Shorten screen titles and helper copy.
6. [ ] Replace ten-tab bottom navigation with five primary destinations.
7. [ ] Remove permanent `Ready`, `LIVE SYNC`, and similar status pills.
8. [ ] Move technical/debug information into Diagnostics.
9. [ ] Standardize spacing and convert everything to dp.
10. [ ] Give all buttons and controller-focusable elements the same interaction states.

Those ten changes would likely provide the largest visual improvement for the least code churn.

---

# Suggested UI Personality

DualDex should not look like:

- A generic AI dashboard.
- A SaaS admin panel.
- An official Pokémon clone.
- A mobile website made of cards.

It should look like:

- A dedicated gaming handheld utility.
- A live companion instrument panel.
- Something built around speed and information density.
- A product with a single visual author.

The minimal DD logo direction already fits this better than a heavily themed Pokémon-style interface. The app UI should follow the same idea: recognizable through restraint rather than decoration.

---

# UI Refresh Definition of Done

The UI redesign can be considered successful when:

- Every screen clearly looks like part of the same product.
- Users can identify the main action within a second or two.
- Emoji are no longer the core icon system.
- Colors have consistent semantic meaning.
- Cards are used selectively rather than universally.
- Navigation exposes only the most important destinations.
- Live battle/party information visually outranks app chrome.
- Settings look like settings rather than a feature showcase.
- Debug information is hidden from normal users.
- Physical controller focus is obvious everywhere.
- Layout spacing behaves consistently across screen densities.
- The visual system can expand to normal Android phones without being rewritten from scratch.

The redesign does not need to be flashy. A quieter, more consistent interface will make DualDex feel substantially more finished.
