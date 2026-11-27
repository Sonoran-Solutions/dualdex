# RetroAchievements

Open **More → Achievements** on the companion screen to sign in and manage Hardcore,
unlock banners, and unlock sounds. The screen groups achievements by unlock state
and shows measured progress, missable achievements, and active leaderboard attempts.
Rich presence and unlock banners appear in the companion status area.

## Client and GBA address mapping

DualDex vendors the MIT-licensed rcheevos client at tag `v12.5.0`, commit
`1433173220a7eaede6a9ed7a18e94117be1821e0`. See the [vendor provenance](../native/rcheevos/PROVENANCE.md)
and [license](../native/rcheevos/LICENSE).

DualDex hashes the exact cached game bytes with SHA-256 and MD5 in one streaming
pass. RetroAchievements looks up the GBA game by MD5. DualDex still uses SHA-256
for saves and profile trust.

The bridge uses the pinned rcheevos `RC_CONSOLE_GAMEBOY_ADVANCE` region table
directly. Every read goes through DualDex's bounds-checked libretro memory map.

| Flat RetroAchievements range | GBA bus range | Memory |
| --- | --- | --- |
| `0x000000–0x007FFF` | `0x03000000–0x03007FFF` | IWRAM |
| `0x008000–0x047FFF` | `0x02000000–0x0203FFFF` | EWRAM |
| `0x048000–0x057FFF` | `0x0E000000–0x0E00FFFF` | Cartridge save RAM |

A request outside those ranges, across a region boundary, or into an unmapped
core region returns zero bytes. The client evaluates an active set after each core
frame. When emulation pauses, the service keeps the client's timer queue running.
A signed-out account or a ROM without a set has no per-frame achievement evaluation.

## Hardcore

Hardcore blocks save-state loads, including auto-resume, cheats, and companion
battle inputs. Battery saves, normal controller input, and fast-forward stay
available. A saved sign-in with Hardcore enabled guards a new ROM launch while
the set lookup runs. A ROM without a set releases the guard. Enabling Hardcore
during a supported game resets the emulated game and clears active cheats.
Turning Hardcore off switches the current run to Softcore. The companion also
shows a message if RetroAchievements forces a drop to Softcore.

## Account data and offline behavior

The password is sent for that sign-in attempt over HTTPS to RetroAchievements.
DualDex stores only the account username and the returned session token in
app-private preferences. The app's backup and device-transfer allowlists exclude
these preferences. Signing out removes both values and cancels pending requests
for the signed-out session. App diagnostics omit credentials and request bodies.

RetroAchievements receives the game MD5 and, during a supported session, unlocks,
leaderboard submissions, and rich presence. HTTP runs on a background IO
dispatcher with redirects disabled. rcheevos retries pending unlocks and scores
in memory after temporary failures, including after leaving the game. Signing
out or closing the app process clears this queue.

## ROM hack availability

The public game listings showed no published base achievements for Heart & Soul
or R.O.W.E. when checked. Hash recognition and a published set are separate.
[Heart & Soul listing](https://retroachievements.org/game/35631)
[R.O.W.E. listing](https://retroachievements.org/game/21881)

The Unbound base set lists supported v2.1.0 and v2.1.1 MD5s. DualDex's profile pins
a SHA-256, so matching that exact build to an RA set requires the user-supplied
ROM's MD5. DualDex checks it at launch.
[Unbound supported game hashes](https://retroachievements.org/game/17530/hashes)

## ROM-free bridge check

```sh
mkdir -p native/build
gcc -O2 -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -DRC_CLIENT_SUPPORTS_HASH \
  -I native/include -I native/rcheevos/include \
  native/src/gba_memory_map.c native/src/ra_bridge.c native/tests/test_ra_bridge.c \
  native/rcheevos/src/*.c native/rcheevos/src/rcheevos/*.c \
  native/rcheevos/src/rapi/*.c native/rcheevos/src/rhash/*.c \
  -lm -lpthread -o native/build/test_ra_bridge
./native/build/test_ra_bridge
```
