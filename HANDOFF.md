# Rich TV Remote 2.2 handoff

Updated 2026-10-26 (Asia/Ho_Chi_Minh).

## Current release

The user explicitly requested version **2.1**, commit and push to the configured repository.
Source, tests and docs form the release commit; inspect `git log -1` and `git status --short`
for the exact current revision. Branch: `main`; origin: `tecix/phone-tv-remote`.

- Display name: **Rich TV Remote**. Source namespace: `io.github.tecix.richtvremote`.
  Gradle project: `rich-tv-remote`. Version name **2.1**, code **3**.
- Installed application ID remains `io.github.tecix.textv` to preserve settings, URI grants,
  and paired identity. The old MainActivity component is retained as an alias.
- Signed release installed with `adb install -r`. Do not uninstall, clear data, regenerate
  client identity or substitute a debug APK to work around signature differences.

## Behavior

- Main and panel microphones immediately send recognized text as SmartTube Search,
  including spoken play/open prefixes. Manual Search/Play keep their separate actions.
  Disconnected/rejected speech retains the query. Reconnect alone never sends it.
  The Direct connection stays open during the external recognition activity.
- Arrow-shaped **File** button opens OpenDocument beside the SmartTube search bar.
  SEND/SEND_MULTIPLE and content VIEW/Open with support generic/vendor media MIME types,
  provider/filename resolution, audio/video containers and photos. Multi-file shares let
  the user choose one file. Known documents/archives are rejected. Photos use DLNA.
- First shares retain destination/Play confirmation. A new valid pick/share while one is
  active or preparing automatically replaces it. Old requests are cancelled/joined before
  starting the new file; superseded errors/busy states cannot overwrite the latest request.
- DLNA selection initially starts AirReceiver via the selected Direct/HA route, then scans.
  Existing paired TinyTV AppLink launching is reused; no new companion install is needed.
  AirReceiver's saved DLNA switch must already be on. No private preferences are rewritten.
- DLNA selection is retained for the app session, independently of the Activity and playback.
  The top-bar receiver chip opens **Disconnect DLNA**. Normal shares reuse the receiver
  without scanning/relaunching. Change receiver forces a refresh; unreachable/changed
  endpoints get one rediscovery/retry. Delayed scans cannot undo Disconnect.
- **Stop TV streaming** and notification **Stop streaming** share an app-scoped controller.
  Both cancel preparation/discovery/autoplay, close the phone server/cache/locks, join any
  in-flight command, then stop playback on the original destination. DLNA uses AVTransport
  Stop. VLC uses its saved Direct/HA settings; a temporary paired Direct connection flushes
  MEDIA_STOP before closing, so background notification Stop does not depend on the UI socket.
  Failures keep the destination available for retry. Stop keeps the selected DLNA receiver;
  Disconnect clears it and stops active DLNA streaming.
- The old Assistant key is labeled **Browser** with a globe, preserving its ASSIST key event.
- Playback timeline/seek uses actual app metadata/capabilities. Optional TinyTV media access
  is separate from DLNA. Unknown/live/nonseekable content is not fabricated.

## README gallery and release

Release page: https://github.com/tecix/phone-tv-remote/releases/tag/v2.2
The signed phone APK is `rich-tv-remote-v2.2-arm64-v8a.apk`; SHA256SUMS accompanies it.
README links 21 optimized WebP previews covering primary screens, panels, setup dialogs and
light/dark/compact layouts. They use sample data, not private device contents. Regenerate with
`./gradlew testDebugUnitTest --tests 'io.github.tecix.richtvremote.*ScreenshotTest'` then
`python3 scripts/update-screenshots.py` (requires cwebp). Pairing uses a separate finite native
looper render to avoid Espresso waiting indefinitely on its focused cursor; app behavior is unchanged.

## Validation and remaining device checks

- Phone: **89 tests**, zero failures/errors. Signed release/R8 and release lint passed
  (zero errors, 12 existing warnings). `git diff --check` passed.
- Coverage includes parsing/routing, protocol, SOAP/HTTP ranges, real notification action
  dispatch/removal, pending command cancellation, rapid replacement ordering, retry after
  Stop failure, receiver reuse/disconnect/stale scans, UI controls and 21 screenshot renders.
- Main-screen DLNA session layout reviewed in `app/build/outputs/roborazzi/remote_dlna_session.png`.
- The live TV DLNA endpoint at `192.168.2.192:3500` currently refuses connections. No Stop
  reached the TV during the latest probe; actual TV Stop/background playback remains unverified.
- TV notification-listener access for **TinyTV media controls** was previously NOT enabled.
  Do not silently grant it. The phone's Enable playback access on TV opens the settings for
  the user. This is unrelated to DLNA and the phone notification permission.
- Real timeline/seek across SmartTube/VLC/Web Video Caster, locked-phone transfer and Drive
  end-to-end flows still require device verification. Google Drive must share the actual
  file with Send a copy/Open with, not an authenticated private Drive webpage.

## Development and artifacts

Workspace: `/home/tecix/Projects/tv-hack/phone-remote`.
Kotlin source/tests: `app/src/{main,test}/java/io/github/tecix/richtvremote`.
Important streaming files: `RemoteViewModel.kt`, `media/StreamingController.kt`,
`MediaStreaming.kt`, `DlnaReceiverSession.kt`, `MediaShareService.kt`, `DlnaController.kt`,
`MediaTransferReplacement.kt`, `SingleMediaServer.kt` and `ui/screens/SharedMediaSheet.kt`.

```sh
JAVA_HOME=/home/tecix/Android/jdk17 ./gradlew testDebugUnitTest assembleRelease lintRelease --offline
```

Gradle cache and Git metadata writes require sandbox escalation in this environment.

- APK: `app/build/outputs/apk/release/app-release.apk`.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/lint-results-release.html`.
- Local release snapshots: `.local-backups/`; never commit APKs, keystores or credentials.
  Receiver APK snapshots can contain embedded receiver identities.
- Phone: Xiaomi Mi 10, last authorized ADB `192.168.2.3:39669` (ports are ephemeral).
- TV: Philips TV638_DVB, Android 5.1.1/API 22, IP `192.168.2.192`.
  Avoid occupying HA's historical single-client ADB connection; never borrow its private key.

## Companion receiver

Companion checkout: `../tv-mods/tinytv-receiver`, previously installed **1.13 / code 14**.
`receiver-media.patch` records already-applied optional media changes. Do not blindly reapply.
The authenticated TLS extension uses fields 9000/9001; HA uses a DUMP-protected broadcast.
Standard pairing, AppLink fields, TLS identity and ordinary packets are unchanged.
Its prior build passed and JVM regression suite passed 162 assertions, but Android MediaSession
runtime behavior is isolated by a test double and still needs hardware verification.
