# Fiero Launcher — keypad home screen for the TCL Flip 2

Minimal launcher to replace Launcher4 (`com.tim.flip2.launcher4`) on the TCL Flip 2 (4058G, Android 11 / SDK 30,
240×320 at 160 dpi, armeabi-v7a, 1 GB RAM, no touch, no GMS). This is FlipSpot's milestone M5
(`C:\dev\flipspot\PLAN.md`); keys, constraints and build setup come from there.

## Hard constraints

- No touch: everything works with D-pad, OK, Back, soft keys, Call, 0–9/*/#.
- Kotlin, plain Android Views, no Compose, no DI, **no dependencies**. minSdk/targetSdk 30, compileSdk 37.
- 16–18 sp text, ~5 list rows per screen.
- Launcher4 stays installed as the fallback. Never uninstall or disable it.
- Default home changes only with an explicit OK. Revert:
  `adb shell cmd package set-home-activity com.tim.flip2.launcher4/com.android.launcher3.Launcher`

## Screens and keys

**Home**: glance row (signal bars, carrier, ringer mode if not Normal, battery), large clock, date, Now Playing
line (▶/❚❚ title · artist, hidden when no session), D-pad hints, soft-key labels.

| Key | Action |
|---|---|
| D-pad ▲▼◀▶ | Open the app on that key (Fiero's own prefs; defaults Spotify / Maps / F-Droid / FlipSpot) |
| OK | All apps |
| Left soft | Notification shade (`StatusBarManager.expandNotificationsPanel`, EXPAND_STATUS_BAR) |
| Right soft | FlipSpot |
| Call | Dialer |
| 0–9 | Dialer pre-filled with that digit |
| \* / # short | Dialer pre-filled with \* / # (fires on key-up) |
| \* long | Play/pause the Now Playing session |
| # long | Ringer: Normal ↔ Silent (Vibrate if Silent is refused) |
| Back | Nothing (Home is the bottom of the stack) |

**All apps**: alphabetical, virtualised `ListScreen` from FlipSpot. Digits filter T9-style against the start of
any word ("6277" → Maps…), `0` = word break; Back deletes a digit, then leaves; right soft clears the filter.
Left soft = Options: put the app on a D-pad key, or App info.

## Architecture

```
FieroApp          – package-change receiver invalidates the app-list cache; one io thread
Apps              – launchable-app query (cached), launch with fallback, T9 matching
NowPlaying        – follows active MediaSessions (playing first, Spotify preferred); MediaListener is the
                    empty NotificationListenerService that grants access to them
Shortcuts         – D-pad shortcut store (SharedPreferences "shortcuts", "label|pkg/cls")
HomeActivity      – home screen; also the HOME entry via the disabled alias .HomeAlias
AllAppsActivity   – app list, T9 filter, shortcut assignment
ui/KeyRouter      – copied from FlipSpot (+ CALL, '#' renamed POUND), log tag "Fiero"
ui/ListScreen     – copied from FlipSpot; rows stretch to fill the box (36 dp minimum)
```

The `HOME` intent filter sits on `.HomeAlias`, which ships **disabled**, so installing or updating Fiero can
never make it a home candidate by itself. `scripts\deploy.ps1 -SetHome` starts Fiero with
`--ez home_alias true` (Fiero enables its own alias) and calls `set-home-activity`; `-RevertHome` puts Launcher4
back and has Fiero disable the alias again.

## Milestones

**M0 — scaffold, home, shortcuts, all apps (done 2026-11-05)**. Launched with `am start`; not the default home.

**M1 — Now Playing, ringer, play/pause (done 2026-11-05)**. Still launched with `am start`.

**M2 — default home (done 2026-11-05, with the user's OK)**. `deploy.ps1 -SetHome`; Home and End land on Fiero,
Right opens FlipSpot, Back from FlipSpot returns to Fiero. Revert: `deploy.ps1 -RevertHome`.

### Now Playing source: decision

Chosen (M1): **MediaSession via a NotificationListenerService in Fiero**, not a FlipSpot provider/broadcast.
- Spotify publishes an active MediaSession whenever it plays (`dumpsys media_session`: `com.spotify.music`
  active=true), whoever started the playback.
- FlipSpot has no service; its App Remote state exists only while its process is alive and connected, which on
  1 GB RAM is often not the case. A FlipSpot provider would need FlipSpot running, a signature permission,
  change notifications, and a commit in both repos.
- The listener is one small class plus a one-time grant over adb
  (`adb shell cmd notification allow_listener dev.mike.fierolauncher/dev.mike.fierolauncher.MediaListener`),
  and also gives play/pause/skip through `MediaController.transportControls`. Launcher4 does the same thing.
- Cost: audiobook author may be missing from the session metadata (FlipSpot found App Remote has no artist for
  chapters). Acceptable for a one-line strip.

## Dev loop

```
.\scripts\deploy.ps1            # debug build, install, am start, logcat (tag Fiero)
.\scripts\deploy.ps1 -Release   # R8 build, 59 KB, signed with the debug key
.\scripts\deploy.ps1 -HomeInfo  # which app is the default home
```

JDK: `C:\Program Files\Android\Android Studio\jbr` (PATH java is Java 8). Gradle 9.8.2 wrapper (checksum-pinned),
AGP 9.5.0 with built-in Kotlin.

## Phone findings

### M0 (2026-11-05)

- Screen is 240×320 physical, density 160 (no forced size at the moment). The system status bar takes the top
  ~24 px and a black system strip ~30 px sits under the app window, so an app gets about 240×266 dp:
  5 list rows of ~39 dp with a title bar, footer and soft-key bar.
- Launcher4 is the default home via the role (`cmd package resolve-activity … HOME` → Launcher4, isDefault).
  Its `keyshortcut_*key` globals use full class names (`com.spotify.music.MainActivity`, etc.); Fiero's defaults
  copy them.
- 25 launchable apps, including Launcher4's own entries ("12Key-Keyboard", "Call history", …).
- `StatusBarManager.expandNotificationsPanel` (hidden API, called reflectively) opens the shade from a
  targetSdk-30 app with only EXPAND_STATUS_BAR.
- The stock dialer (`com.android.dialer/.app.DialpadActivity`) appends `ACTION_DIAL` numbers to whatever is
  still typed in it ("5" → "55"); starting it with NEW_TASK|CLEAR_TASK gives a fresh number.
- The key's ACTION_UP does not leak into the app that a key-down launched.
- No SIM in the phone at the moment: carrier is empty, so the glance row shows "No service".

### M1 (2026-11-05)

- `cmd notification allow_listener dev.mike.fierolauncher/dev.mike.fierolauncher.MediaListener` grants access
  without touch; `deploy.ps1` repeats it after every install. Launcher4's own listener was not enabled.
- Spotify's session reports title/artist (e.g. "Solar Cruise · Oscillian") and play state; `transportControls`
  play/pause works. The session disappears some time after playback stops (none listed before the test), so
  the line hides then.
- Notification-listener access also gives Do Not Disturb policy access: `RINGER_MODE_SILENT` works without a
  separate `allow_dnd` grant, and sets `zen_mode`=1 (Silent icon in the status bar). Back to Normal resets
  `zen_mode`=0.
- `KeyEvent.isLongPress` is set on the first auto-repeat (also on `input keyevent --longpress`), so * / # act
  on key-up when short and on the long-press repeat otherwise.

### M2 (2026-11-05)

- `adb shell pm enable <component>` fails for a non-debuggable (release) app: "Shell cannot change component
  state". The app itself may toggle its own components, so Fiero does it on an intent extra.
- `cmd package set-home-activity dev.mike.fierolauncher/.HomeAlias` works once the alias is enabled; the
  default-home check then reports `dev.mike.fierolauncher/.HomeAlias`, isDefault=true.
- Home key → Fiero (clears All apps above it). End, tested as an injected KEYCODE_POWER short press, goes Home
  and leaves the screen on (`end_button_behavior`=1). A physical End press from inside an app also lands on Fiero
  (confirmed by hand; Telecom logs `endCallInternal` first, then goes Home when there is no call).
- Right → FlipSpot, Back from FlipSpot → Fiero. Reinstalling Fiero keeps it the default home (the alias's
  enabled state persists across `install -r`).
- After `adb reboot` (boot_completed in ~20 s): Fiero is still the default home and comes up first with no lock
  screen; notification-listener access survives (`enabled_notification_listeners`), and the Now Playing line
  showed Spotify's restored (paused) session.
