# Fiero Launcher

A minimal home screen for **keypad Android flip phones**, built on and for the TCL Flip 2. Everything works
from the keypad: there's no touch screen to rely on, so nothing needs one.

<p>
  <img src="docs/screenshots/home.png" width="240" alt="Home screen: clock, date, now playing, D-pad shortcuts">
  <img src="docs/screenshots/all-apps.png" width="240" alt="All apps list with a highlight bar">
  <img src="docs/screenshots/t9-filter.png" width="240" alt="Typing 3437 filters the list to Fiero Nav">
  <img src="docs/screenshots/assign-key.png" width="240" alt="Options: put the app on a D-pad key">
</p>

## What it does

- **Home screen:** big clock and date, signal / carrier / battery, ringer mode, and a **Now Playing** line
  for whatever music app is playing (Spotify, podcasts…).
- **D-pad shortcuts:** Up, Down, Left, Right and the right soft key each open an app you choose.
- **All apps:** an alphabetical list. Type on the number keys to filter it T9-style: `6277` finds "Maps",
  `3437` finds "Fiero Nav". Matching works on the start of any word in the name.
- **Phone-style keys:** Call opens the dialer; digits, `*` and `#` open the dialer with that key typed.
- **Long presses:** `*` play/pause, `#` toggles Silent.
- **Tiny:** ~70 KB, no libraries, no network access, no ads or tracking.

## Keys on the home screen

| Key | Action |
|---|---|
| D-pad ▲ ▼ ◀ ▶ | Open the app on that key |
| OK | All apps |
| Left soft key | Notification shade |
| Right soft key | Open the app on that key (shown on the label) |
| Call | Dialer |
| 0–9, `*`, `#` (short) | Dialer with that key typed |
| `*` (hold) | Play / pause what's playing |
| `#` (hold) | Ringer: Normal ↔ Silent |

**All apps:** ▲▼ move, ◀▶ page, OK opens, digits filter, Back deletes a digit (then leaves),
right soft key clears the filter, **left soft key = Options**: put the app on a key, or App info.

To change a shortcut: OK → find the app → left soft key → pick the key.

## Install

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools) and USB debugging
turned on in the phone's Developer options.

1. Download `fiero-launcher-*.apk` from [Releases](../../releases) (or [build it](#build)).
2. Install it:
   ```bash
   adb install -r fiero-launcher-0.3.0.apk
   ```
3. Let it read what's playing (optional, for the Now Playing line and `*` play/pause):
   ```bash
   adb shell cmd notification allow_listener dev.mike.fierolauncher/dev.mike.fierolauncher.MediaListener
   ```
4. Try it without changing anything: `adb shell am start -n dev.mike.fierolauncher/.HomeActivity`
5. **Make it the home screen** when you're happy:
   ```bash
   adb shell am start -n dev.mike.fierolauncher/.HomeActivity --ez home_alias true
   adb shell cmd package set-home-activity dev.mike.fierolauncher/.HomeAlias
   ```

**To undo**, point the phone back at its original launcher. On the TCL Flip 2 that's Launcher4:

```bash
adb shell cmd package set-home-activity com.tim.flip2.launcher4/com.android.launcher3.Launcher
```

(For another phone, find the original with
`adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`.)

Fiero never offers itself as a home app until step 5: its home-screen entry ships switched off. Keep your
original launcher installed as a fallback.

## Compatibility

- Android 11 (API 30) or newer. Built and tested on the **TCL Flip 2** (4058G, 240×320, no touch screen).
- Other keypad Android phones should work if their keys send standard Android keycodes. The table in
  [`ui/KeyRouter.kt`](app/src/main/java/dev/mike/fierolauncher/ui/KeyRouter.kt) lists what the Flip 2 sends;
  `adb logcat -s Fiero` shows each key press on a debug build.
- On first start the shortcuts are the author's (Spotify, Maps Go, F-Droid, FlipSpot). Any whose app isn't
  installed shows as empty until you assign it.

## Build

Requirements: JDK 17+ and the Android SDK (Android Studio provides both).

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk (R8-shrunk)
./gradlew testDebugUnitTest   # T9 matching tests
```

Put `sdk.dir=…` in `local.properties` if Gradle can't find the SDK. The release build is signed with your
**debug key** for convenience; use your own keystore if you distribute it.

On Windows, `scripts\deploy.ps1` does build + install + launch + logcat in one go (`-Release`, `-SetHome`,
`-RevertHome`, `-HomeInfo`; see the top of the script).

## How it works

Plain Kotlin and Android Views, no dependencies. The Now Playing line reads active media sessions through
a notification listener, which is also what allows Silent mode. The home-screen entry lives on a disabled
activity-alias so installing Fiero can't hijack the Home key.
[`PLAN.md`](PLAN.md) has the design notes and everything learned about the phone along the way.

## Related

- [Fiero Nav](https://github.com/SuperFieroStatus/fiero-nav): keypad-first turn-by-turn driving navigation.
- [FlipSpot](https://github.com/SuperFieroStatus/flipspot): a keypad Spotify front end.

## License

[MIT](LICENSE)
