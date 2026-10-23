# Rich TV Remote 2.0 status

Last updated: 2026-10-24. **Read [HANDOFF.md](HANDOFF.md) to resume.**

## Implemented and installed

- Version **2.0**, Android version code **2**, installed with settings/pairing preserved.
- Main-screen **Stop TV streaming** and notification **Stop streaming** now cancel pending jobs and send a real stop to the original DLNA/VLC destination, independently of Activity/service lifetime. TV failures remain retryable.
- The selected DLNA receiver is saved for the app session and shown beneath the top bar with **Disconnect DLNA**. Normal file shares reuse it; explicit refresh or a failed endpoint triggers discovery. Stop keeps the receiver, Disconnect clears it.

- Picking/sharing a new file during an active/preparing DLNA/VLC share automatically cancels and stops the old transfer, awaits its cleanup, and plays the new one on the selected destination. Rapid replacements suppress stale commands/errors; the first share retains its destination panel. Signed update installed on the phone with data/pairing preserved.

- Renamed to **Rich TV Remote**, source namespace `io.github.tecix.richtvremote`, Gradle project `rich-tv-remote`. Installed application ID retained for pairing/settings, old activity preserved as an alias.
- Arrow-shaped **File** button opens the system file picker beside the resized SmartTube search bar. Expanded SEND/SEND_MULTIPLE and content VIEW filters; provider/filename MIME resolution supports audio/video containers and photos, with a choice for multi-file shares.
- DLNA selection starts AirReceiver via the selected Direct/HA route before discovery, with startup retry. Existing TinyTV AppLink helper is reused; no companion update is needed. AirReceiver's saved DLNA switch must be on. Actual TV startup verification remains pending because TV ADB is offline.

- Existing Direct/HA remote, pairing, keyboard, macros, quick launch and HA tile retained.
- Phone Vietnamese/English recognition via Android Activity Results; deterministic parsing and safely
  encoded SmartTube search/play links; editable/manual fallback. No paid service, LLM or TV AI runtime.
- Material 3 redesign: labeled current-mode top-bar control, merged media card, search bar and improved sheet.
- Main and panel microphone recognition immediately sends SmartTube Search and closes on acceptance; rejection retains the query. Manual Search/Play keep their actions. Direct stays connected during recognition.
- The old Assistant key now uses a Browser globe icon and label.
- Language flags, selected checkmark/fill and persisted language.
- Real media-session timeline/seek support with volume shown separately. Requires TV media access and
  app-provided metadata/seek capability; unknown/live/unsupported media is not fabricated.
- Share actual audio/video files or HTTP(S) media links to AirReceiver/DLNA or VLC on TV. SSDP receiver discovery and standard UPnP playback added; DLNA works independently of Direct/HA. Files are cached on the phone and served over local Wi-Fi with byte ranges, DLNA headers, foreground notification/Stop, CPU/Wi-Fi locks and four-hour expiry.
- TinyTV Receiver upgraded to **1.13 (code 14)** in the companion checkout and on the TV. Standard
  pairing/TLS/AppLinks remain unchanged; optional media extension and protected HA ADB broadcast added.
- Signed phone release installed on the authorized phone, preserving data and pairing.

## Remaining setup / verification

- **TV notification-listener access is NOT enabled.** User consent/setup remains pending. Use the phone's
  **Enable playback access on TV**, then enable **TinyTV media controls** on the TV. Do not grant silently.
- Actual timeline and seeking across SmartTube/Web Video Caster/VLC and Direct/HA need device verification.
- End-to-end Drive sharing, locked-phone transfer and real TV Stop still need device verification. Notification Stop dispatch/cancellation and UI controls are covered by regression tests; the live DLNA endpoint currently refuses connections.
- The user reported the earlier voice-search functionality worked. Do not confuse that report with
  validation of these latest playback/file-sharing flows.
- Older pairing-dialog/keyboard, captions macro calibration and quick-launch checks in project history
  were not comprehensively retested on hardware in this session.

## Validation

- Phone: **71 tests passed**, zero failures/errors; signed release build, release lint (zero errors, 11 warnings) and R8 passed. Rich TV Remote installed successfully with `adb install -r`. Actual phone package queries verified generic/vendor sharing, multi-file sharing and Open with targets.
- Receiver: full Android build passed; existing focused regression suite passed **162 assertions**.
  Its Android MediaSession implementation is compiled, but isolated by a test double in the JVM suite.
- Live TV: correct model, VLC installed, receiver/accessibility services running, media endpoint returns
  the expected access-required response. No actual seek/playback success is claimed.
- Light/dark/compact screenshot previews generated; `git diff --check` passed.
- LAN discovery found **TV638_DVB-192[DLNA]** at `192.168.2.192:3500`; DLNA description advertises AVTransport:1. Actual video playback/smoothness is still unverified. Phone was locked during deployment; no unlock or notification-listener grant was attempted.

## Continuation notes

- Version 2.0 source, tests and documentation form the requested release commit. Check `git log -1` and `git status --short` for current revision/state; companion source remains in its own checkout.
- `receiver-media.patch` records changes already applied to `../tv-mods/tinytv-receiver`; do not blindly reapply.
- Phone last ADB: `192.168.2.3:39669` (ephemeral). TV ADB: `192.168.2.192:5556`, disconnected after checks.
- Release signing is configured; debug signature differs from the installed release. Do not uninstall or clear data.
- APK snapshots/receiver rollback are saved in ignored `.local-backups/`; details and commands are in HANDOFF.md.
