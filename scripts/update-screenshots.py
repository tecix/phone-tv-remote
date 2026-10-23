#!/usr/bin/env python3
"""Export the Compose screenshot gallery to the WebP files linked by README.md (requires cwebp)."""

from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/build/outputs/roborazzi"
DESTINATION = ROOT / "docs/screenshots"
SCREENS = {
    "remote_direct": "remote",
    "remote_ha": "remote-ha",
    "remote_media_shortcuts": "media-and-shortcuts",
    "remote_dlna_session": "dlna-session",
    "dlna_connection_menu": "dlna-menu",
    "devices": "tv-picker",
    "add_tv": "add-tv",
    "pairing": "pairing",
    "keyboard": "keyboard",
    "voice_search": "voice-search",
    "macro_editor": "macro-editor",
    "share_dlna": "share-dlna",
    "share_vlc": "share-vlc",
    "settings": "settings",
    "settings_phone": "settings-controls",
    "settings_dark": "settings-dark",
    "remote_direct_dark": "remote-dark",
    "voice_search_dark": "voice-search-dark",
    "share_dlna_dark": "share-dlna-dark",
    "remote_needs_pairing": "remote-pairing-needed",
    "voice_search_compact": "voice-search-compact",
}


def main() -> None:
    encoder = shutil.which("cwebp")
    if not encoder:
        raise SystemExit("Install cwebp (WebP tools) before exporting screenshots.")
    missing = [name for name in SCREENS if not (SOURCE / f"{name}.png").is_file()]
    if missing:
        raise SystemExit("Run the *ScreenshotTest renders first. Missing captures: " + ", ".join(missing))
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for source, destination in SCREENS.items():
        subprocess.run([
            encoder, "-quiet", "-q", "82", "-m", "6", "-resize", "720", "0",
            str(SOURCE / f"{source}.png"), "-o", str(DESTINATION / f"{destination}.webp"),
        ], check=True)
    print(f"Updated {len(SCREENS)} screenshots in docs/screenshots.")


if __name__ == "__main__":
    main()
