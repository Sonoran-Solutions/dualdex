# Launching DualDex from a frontend

DualDex exposes `com.dualdex/.FrontendLaunchActivity` for ES-DE, Cocoon, iiSU, Daijisho and
similar frontends. It is exported but has **no intent filter**, so it never appears as a second
"Open with" entry; frontends start it by component name.

- ROM source: `intent.data`, else the first non-blank extra of `rom`, `ROM`, `path`, `file`, `uri`.
- A readable filesystem path (`/storage/...` or `file://...`) is played in place.
- Anything else (`content://`, or a path DualDex cannot read) is copied once to app storage
  (`files/frontend_roms/<name>`); later launches skip the copy while the bytes match.
- Back exits with `finishAndRemoveTask()`, returning to the frontend.
- A second launch while running (`onNewIntent`) switches ROM in the same session.

Battery saves and states are keyed by the ROM's SHA-256, so they are shared with library launches.

## ES-DE

`custom_systems/es_systems.xml` (add the command to the `gba` system, or copy the system entry):

```xml
<system>
    <name>gba</name>
    <fullname>Nintendo Game Boy Advance</fullname>
    <path>%ROMPATH%/gba</path>
    <extension>.gba .GBA .bin .BIN</extension>
    <command label="DualDex">%EMULATOR_DUALDEX% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%</command>
    <platform>gba</platform>
    <theme>gba</theme>
</system>
```

`custom_systems/es_find_rules.xml`:

```xml
<ruleList>
    <emulator name="DUALDEX">
        <rule type="androidpackage">
            <entry>com.dualdex/.FrontendLaunchActivity</entry>
        </rule>
    </emulator>
</ruleList>
```

`%ROMSAF%` passes a `content://` URI (copied once). Use `%EXTRA_rom%=%ROM%` instead to pass a
plain path, which is played in place when DualDex can read that folder.

## Daijisho / Cocoon / iiSU

Create a custom player with package `com.dualdex`, activity `com.dualdex.FrontendLaunchActivity`,
action `android.intent.action.VIEW`, and the ROM passed as data (`{file.uri}` / `{file.path}`)
or as an extra named `rom`.

## adb test commands

```bash
# Path in data (played in place when readable)
adb shell am start -n com.dualdex/.FrontendLaunchActivity -d "file:///sdcard/ROMs/gba/game.gba"

# Path as an extra
adb shell am start -n com.dualdex/.FrontendLaunchActivity --es rom /sdcard/ROMs/gba/game.gba

# Switch ROM while running (delivered via onNewIntent)
adb shell am start -n com.dualdex/.FrontendLaunchActivity --es path /sdcard/ROMs/gba/other.gba
```

Without "All files access", Android 11+ blocks direct reads of shared storage paths; DualDex then
tries the path through the content resolver and shows an error if that also fails. Pass a
`content://` URI (ES-DE `%ROMSAF%`) in that case.
