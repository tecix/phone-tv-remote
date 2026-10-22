# TV Remote: Google, HA — Status

_Last updated: 2026-10-22_

## Done

- [x] Project setup: Compose + Material 3 Expressive (material3 1.5.0-beta01), AGP 9.4.1, SDK 37
- [x] Direct: certificate, pairing, remote control, reconnect with backoff
- [x] Home Assistant: keys, text, long press, app launch, connection test in Settings
- [x] mDNS discovery, saved TVs (select / rename / re-pair / remove), add by IP
- [x] Remote screen, keyboard sheet, settings screen, pairing dialog
- [x] Phone volume keys → TV, haptics, dynamic color, dark mode
- [x] SmartTube row: Captions (key macro) and 1.5× speed (`S` toggle)
- [x] Quick launch: YouTube, Web Video Caster, AirReceiver, VLC
- [x] HA wide tile: TV screenshot background, current app, uptime, refresh
- [x] Tests passing: protocol (certificate, protobuf, framing, pairing digest matches the Python
      app), macro parser, uptime format, 6 screenshot renders

## Verified on real hardware

| Check | Result |
|---|---|
| App runs on Xiaomi Mi 10 | ✅ remote screen with dynamic color |
| mDNS finds the TV ("TV638") | ✅ |
| TLS connect + "needs pairing" detection + pairing start (TV shows a code) | ✅ |
| HA: media_player attributes, screenshot (234 KB PNG), `cat /proc/uptime` via `adb_command` | ✅ (curl from the PC) |
| TV package names for quick launch | ✅ read from the TV via HA |

## Not yet verified

- [ ] Finishing pairing with the TV's code, then key presses / volume feedback over Direct
- [ ] The new build on the phone (installed, but the phone was locked)
- [ ] HA mode end-to-end on the phone (HA URL/token must be entered in the phone app's Settings)
- [ ] **Captions macro**: the default `DOWN RIGHT*2 OK BACK` is a guess; it needs calibrating
      with a SmartTube video playing on the TV
- [ ] 1.5× speed: needs SmartTube › Settings › General › "Remap S to speed toggle" on and 1.5×
      picked once
- [ ] Quick launch of Web Video Caster / AirReceiver / VLC from both modes

## Known issues / limitations

- Pairing dialog: the keyboard covers the Pair button (✓ on the keyboard submits).
- The HA tile needs screencap on in the Android Debug Bridge integration's options; otherwise the
  tile shows a TV icon instead of a screenshot.
- `adb_response` is shared with any other `androidtv.adb_command` caller; the uptime parser only
  accepts output shaped like `/proc/uptime`.
- The pairing-dialog screenshot test was dropped (dialog windows never go idle under Robolectric).
- The release APK is unsigned; no signing config yet.
- Low disk space on the dev machine (~1.4 GB free) ruled out an emulator.

## Next steps

1. Pair with the TV and test Direct keys and volume feedback.
2. Enter HA settings on the phone; check the tile (screenshot, app name, uptime).
3. Play a SmartTube video and calibrate the captions macro.
4. Make the pairing dialog move above the keyboard.
5. Optional: release signing config, app icon polish, first git commit (repo initialised, nothing committed).
