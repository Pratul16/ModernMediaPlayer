# Modern Media Player

A local video and music player for Android (Kotlin, Jetpack Compose, Media3, libVLC).

- Plays almost any format: ExoPlayer (hardware) → FFmpeg (software) → VLC fallback
- Gestures, background audio, picture-in-picture, subtitles, playlists, smart resume and series
- Built-in file actions, multi-select, hidden folders, optional app lock
- Private: no ads, no tracking; media and history never leave the device

## Build

Requires Android Studio (JDK 17+).

```bash
./gradlew.bat :app:assembleDebug      # debug APKs
./gradlew.bat :app:bundleRelease      # Play Store bundle
```

Release signing and store steps: see [PLAY_STORE.md](PLAY_STORE.md). Privacy policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## License

Copyright © 2026 Pratul.

Modern Media Player is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see
[LICENSE](LICENSE) for details.

Bundled components: AndroidX, Jetpack Compose, Media3, Room, Coil (Apache 2.0); libVLC (LGPL 2.1+);
FFmpeg decoder for Media3, Jellyfin build (GPL 3.0).
