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
