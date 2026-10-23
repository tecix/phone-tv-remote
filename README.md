# Rich TV Remote 2.0 (Android)

A phone remote for Google TV / Android TV, built with Kotlin, Jetpack Compose and
**Material 3 Expressive**. One app, two ways to reach the TV, switchable at the top of the screen:

- **Direct**: the Android TV Remote protocol v2 (the protocol of Google's own TV app). Pair once with the code on the TV.
- **Home Assistant**: `remote.send_command` through HA's Android Debug Bridge integration, for screens that only real key events can drive.

<p align="center">
  <img src="docs/screenshots/remote.webp" width="260" alt="Remote screen with the Home Assistant tile">
  <img src="docs/screenshots/media-and-shortcuts.webp" width="260" alt="Volume, channel, media keys, macros and quick launch">
  <img src="docs/screenshots/tv-picker.webp" width="260" alt="Saved TVs and mDNS discovery">
</p>

## Features

| | |
|---|---|
| Discovery | mDNS (`_androidtvremote2._tcp`) lists TVs on the Wi-Fi, or add one by IP |
| Remote | D-pad with hold-to-repeat, OK/Back/Home with long press, power, input, menu, captions, settings, volume and channel rockers, media keys |
| Keyboard | Bottom sheet; text is sent as you type |
| Live status | Volume, mute, current app and standby (Direct); a 16:9 tile with a TV screenshot, app name and uptime (Home Assistant) |
| Macro actions | Add, rename, edit and delete any number of key sequences; defaults: **Captions** (`DOWN RIGHT*4 OK BACK`) and **1.5× speed** (`S`) |
| Quick launch | YouTube, Web Video Caster, AirReceiver, VLC |
| File streaming | Arrow-shaped File button opens the file picker; share/Open with accepts media containers, audio and photos, including generic file-manager MIME types and multiple files with a file choice |
| IR Power (Home Assistant) | Runs your IR power script or button; set its entity in Settings → Home Assistant |
| Phone | Volume keys control the TV, haptics, dynamic colour, light/dark |

## Install

Download the APK from the [latest release](https://github.com/tecix/phone-tv-remote/releases/latest) and open it on the phone
(allow installing from your browser or file manager when asked). Needs Android 8.0+ on a 64-bit ARM phone (arm64-v8a).

## Build

```sh
export JAVA_HOME=~/Android/jdk17
./gradlew installDebug          # build and install on the connected phone
./gradlew testDebugUnitTest     # protocol, macro and screenshot tests
./gradlew assembleRelease       # arm64-v8a, signed if keystore.properties exists
```

minSdk 26 · targetSdk 36 · compileSdk 37. Details in [SUMMARY.md](SUMMARY.md), progress in [STATUS.md](STATUS.md).

## Related

- [gnome-tv-remote](https://github.com/tecix/gnome-tv-remote): the GNOME desktop version; the Direct protocol code here is ported from it.
- [tv-hack](https://github.com/tecix/tv-hack): the TV-side work (launcher, TinyTV Receiver) this remote was built for.

## License

GPL-3.0-or-later, the same as gnome-tv-remote, which the protocol code is ported from.


### Voice search, playback timeline and sharing

- The main microphone and the search panel's microphone send recognized speech directly as a SmartTube **Search**, including phrases starting with “play/open”. Manual **Search** and **Play** keep their respective behavior. Rejected/disconnected requests retain the query for retry; reconnecting alone never sends it. The old Assistant key uses a browser globe icon and label.
- The labeled top-bar icon shows the selected Direct/HA route. The playback timeline is separate from the volume number.
- Real playback position and seeking use the optional **TinyTV Receiver 1.13** media extension. Enable **TinyTV media controls** in the TV's notification-access settings using the phone's **Enable playback access on TV** action. Only metadata/seek actions exposed by SmartTube, VLC, or Web Video Caster are available; live or non-seekable streams stay disabled. Stock Remote v2 has no timeline API.
- The TinyTV extension uses fields 9000/9001 on the existing paired TLS connection, without changing pairing or standard packets. Home Assistant uses its authenticated ADB route and a receiver broadcast protected by Android's `DUMP` permission. Native HA media entities with duration/position/seek support also work.
- Use **File** beside the SmartTube search bar to pick a file, or share/Open with from another app. Audio, video containers and photos are supported; when sharing multiple files, choose which one to play in the panel. Generic and vendor MIME types are resolved using the provider and filename. Documents/archives with known non-media types are rejected; the receiver still determines which media codecs it can play.
- The selected DLNA receiver is kept for the app session and shown beneath the top bar. Tap its name for **Disconnect DLNA**. Sharing another file reuses it without discovery or relaunching AirReceiver; **Change receiver** refreshes explicitly. An unreachable endpoint triggers one rediscovery/retry.
- **Stop TV streaming** on the main screen and **Stop streaming** in the notification use the same operation: cancel pending work, stop TV playback, close the phone server, remove the temporary file and release locks. Stop keeps the DLNA receiver selected; Disconnect clears the selection and stops active DLNA streaming. Stop errors remain visible for retry.
- Choose **AirReceiver / DLNA**, select the TV's discovered renderer, then **Play on [receiver]**. The selected Direct/HA route requests AirReceiver startup before discovery and retries while it starts. Direct uses the existing paired TinyTV AppLink helper; no new TV helper install is required. Enable DLNA in AirReceiver once: launching the app uses its saved settings. If no remote route is connected, open AirReceiver manually; actual DLNA streaming/discovery works independently. **VLC** remains available through the remote route for audio/video. For Google Drive, share the **actual file** with **Send a copy/Open with**; private Drive webpage links cannot be opened as authenticated media URLs on the TV.
- While a DLNA/VLC share is active, picking or sharing a new file automatically stops the old transfer, closes its server and removes its cache, then plays the new file on the selected destination. Rapid replacements cancel superseded preparation/commands. The first share still uses the destination panel; if a receiver is unavailable, the panel keeps the new file for retry.
- File shares are cached on the phone before serving a single secret-path URL over local Wi-Fi. VLC/DLNA can issue HEAD and byte-range requests for seeking. A connected-device foreground service and CPU/Wi-Fi locks keep the transfer active while the phone is locked. Stop from the app or notification; transfers expire after four hours and temporary files are deleted. Phone/TV must be mutually reachable on the LAN, and sufficient phone cache space is required. Nothing is uploaded to a cloud service. AirReceiver plays the original file without transcoding; codec support and available Wi-Fi bandwidth still determine smoothness.
- Receiver source changes are also captured in `receiver-media.patch`. The TV media API is compiled in the receiver build; its existing JVM regression suite isolates Android MediaSession behavior, which needs device verification.

The source namespace is `io.github.tecix.richtvremote`, and the Gradle project is `rich-tv-remote`. The installed Android application ID remains `io.github.tecix.textv` so signed updates preserve settings, URI grants and paired identity. A compatibility alias preserves the previous activity component.
