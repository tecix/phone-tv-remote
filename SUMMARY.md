# TV Remote: Google, HA — Summary

Android phone remote for Google TV / Android TV, built with Kotlin, Jetpack Compose and
Material 3 Expressive. It drives the TV two ways, switchable at the top of the remote:

- **Direct** — Android TV Remote protocol v2 (the Google TV app's protocol), ported from the
  desktop app's `remote_v2.py` / `keystore.py` in [gnome-tv-remote](https://github.com/tecix/gnome-tv-remote).
- **Home Assistant** — `remote.send_command` with `input keyevent …` through HA's Android Debug
  Bridge integration, ported from `home_assistant.py`.

## Features

| Area | What it does |
|---|---|
| Discovery | mDNS (`_androidtvremote2._tcp`) lists TVs on the Wi-Fi; manual add by IP |
| Pairing | TV shows a 6-char hex code; checksum verified on the phone before sending |
| Remote | D-pad (hold to repeat), OK/Back/Home (hold for long press), power, input, assistant, menu, captions, TV settings, volume and channel rockers, media keys |
| Keyboard | Bottom sheet; typed/deleted characters are sent as they change |
| Live status (Direct) | Volume level (wavy bar), mute, current app, standby |
| HA tile | Wide 16:9 card: TV screenshot as background, current app name, TV uptime, refresh button |
| Macro actions | Any number of named key sequences, editable on the remote or in Settings; defaults: **Captions** and **1.5× speed** |
| Quick launch | YouTube, Web Video Caster, AirReceiver, VLC |
| Phone integration | Volume keys control the TV, haptics, dynamic (wallpaper) color, light/dark |

## How it works

### Direct (ports 6466 / 6467)
- A self-signed RSA-2048 client certificate is created on first run (hand-built DER, no
  BouncyCastle) and stored in app-private storage (`files/remote/`).
- A custom `X509KeyManager` always presents that certificate; the TV's self-signed one is trusted.
- Messages are varint length-prefixed protobuf, encoded by hand (`direct/Proto.kt`).
- Sends go out in order on a writer thread; a reader thread answers pings and reports volume,
  power and current app. Disconnects trigger reconnects with backoff while the app is open.

### Home Assistant
- Keys: `remote.send_command` on the configured `remote.*` entity, one worker thread; held keys
  are dropped when HA lags (max 4 pending).
- Macros run as one shell command: `input keyevent A; sleep 0.3; input keyevent B …`.
- App launch: `monkey` with the leanback launcher category, falling back to the normal launcher.
- Tile (polled every 5 s while HA mode is visible, on a separate client so keys never wait):
  - app name: `app_name` of the media_player (default: `media_player.<remote entity name>`)
  - screenshot: the media_player's `entity_picture` (needs screencap on in the integration)
  - uptime: `androidtv.adb_command` → `cat /proc/uptime`, read back from `adb_response`
    (every 10 min; the boot time is kept so the display ticks locally)

### Macro actions
- Add, rename, edit or delete any number of named key sequences from the remote or Settings.
- Sequences work in both Direct and Home Assistant modes, using key names and `*n` repeats.
- **Captions** defaults to `DOWN RIGHT*4 OK BACK`. Existing custom captions sequences migrate;
  the previous default updates to the new sequence. Deleting all actions leaves an empty list.
- **1.5× speed** defaults to `S`; with SmartTube's "Remap S to speed toggle" on, it toggles
  between 1× and the last speed picked (set 1.5× once).

## Project layout

```
app/src/main/java/io/github/tecix/textv/
  MainActivity.kt          screens, lifecycle, phone volume keys
  RemoteViewModel.kt       connection state, pairing, routing, HA tile polling
  TvKey.kt                 keycodes, macro parser, RemoteBackend interface
  direct/                  Proto, ClientIdentity (certificate), RemoteV2 (pairing + remote)
  ha/HomeAssistantRemote.kt  HaHttp, HomeAssistantRemote (keys), HomeAssistantStatus (tile)
  discovery/TvDiscovery.kt mDNS discovery
  data/SettingsRepository.kt DataStore settings (TVs, route, HA config, macros, toggles)
  data/MacroAction.kt       named key sequences, defaults, legacy migration, JSON storage
  ui/theme/Theme.kt        MaterialExpressiveTheme, expressive motion, fallback violet scheme
  ui/components/           RemoteButton (press/repeat/long-press, haptics)
  ui/screens/              Remote, HaTileCard, Devices, Pairing, Keyboard, Settings
app/src/test/              ProtocolTest, MacroTest, ScreenshotTest (Robolectric + Roborazzi)
```

## Build

| Item | Version |
|---|---|
| AGP / Gradle | 9.4.1 / 9.8.0 (built-in Kotlin) |
| Kotlin (Compose compiler plugin) | 2.2.21 |
| Compose BOM / material3 | 2026.09.00 / 1.5.0-beta01 (Expressive components) |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |

```sh
export JAVA_HOME=~/Android/jdk17      # the system JDK 21 has no compiler
./gradlew installDebug                # build + install on the connected phone
./gradlew testDebugUnitTest           # unit tests + screenshots in app/build/outputs/roborazzi/
./gradlew assembleRelease             # R8-minified, unsigned (~1.7 MB)
```
