# Releasing Modern Media Player

## 1. Create your upload key (once, keep it forever)

Play Store builds must be signed with your own key. Run this in the project folder. It asks for a
password: choose a strong one and store it in a password manager.

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v -keystore upload-key.jks -keyalg RSA -keysize 4096 -validity 10000 -alias upload
```

Then create `keystore.properties` in the project folder (same level as `settings.gradle.kts`):

```properties
storeFile=upload-key.jks
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```

Both files are in `.gitignore` and must **never** go to GitHub. Back up `upload-key.jks` somewhere
safe (USB drive / cloud vault). With Play App Signing a lost upload key can be reset, but it takes days.

## 2. Build

```bash
./gradlew.bat :app:bundleRelease      # Play Store: app/build/outputs/bundle/release/app-release.aab
./gradlew.bat :app:assembleRelease    # Sharing:    app/build/outputs/apk/release/app-universal-release.apk
```

Before every new upload, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## 3. Play Console setup

1. Create a developer account at https://play.google.com/console (one-time US$25 fee, identity check).
   New personal accounts must run a **closed test with at least 12 testers for 14 days** before
   production access is granted.
2. Create app → name "Modern Media Player", default language, App, Free.
3. **App content** section:
   - Privacy policy URL: publish `PRIVACY_POLICY.md` somewhere public (see below).
   - Ads: **No ads**.
   - App access: **All functionality available without special access**.
   - Content rating questionnaire: category *Utility/Productivity*; answer No to violence, etc.
     The app plays the user's own files and has no user-generated content sharing.
   - Target audience: 18+ (or 13+); not designed for children.
   - Data safety: **No data collected, no data shared**. The app has no internet permission.
     Biometrics are processed by Android only.
   - Foreground service declaration (media playback): "Continues audio/video playback when
     the user leaves the player; shows media controls in the notification." Attach a short
     screen recording of background audio playing with the notification.
   - Photo/video permissions declaration: the app is a media player and needs broad access to
     the user's videos and audio as its core function.
4. **Store listing**: short + full description (below), app icon 512×512 PNG, feature graphic
   1024×500, at least 2 phone screenshots.
5. Upload `app-release.aab` to the **Closed testing** track, add testers, then promote to production.

### Privacy policy hosting

Play needs a public URL. Options: a public GitHub Gist, Google Sites, or GitHub Pages from a
separate *public* repo containing only `PRIVACY_POLICY.md` (the code repo can stay private).

## 4. Store listing text (draft)

**Short description (≤80 chars)**

> Plays almost any video & music. Gestures, hidden folders, app lock. No ads.

**Full description**

> Modern Media Player plays the videos and music on your phone — beautifully, and almost any format:
> MP4, MKV, AVI, WMV, FLV, MOV, WebM, MP3, FLAC and many more, with hardware and software decoding.
>
> • Smart gestures: swipe to seek, slide for volume and brightness, double-tap to skip (+10s, +20s, +30s), hold for 2× and slide to pick any speed up to 3×
> • Remembers everything: resume where you left off, next episode automatically, subtitles found for you
> • Background audio and picture-in-picture with ⟲10 / 10⟳ controls
> • Subtitles: SRT, ASS, VTT with sync delay, sizes and colours
> • File manager built in: rename, copy, cut, move, delete, share, multi-select, create folders
> • Playlists and favorites
> • Hidden folders: hidden videos disappear from the gallery and every other app
> • Optional app lock with fingerprint, face or an app PIN
> • Five colour themes including Glass, light and dark modes
> • Private: no ads, no accounts, no internet permission — nothing leaves your phone

## 5. Open-source licences

The app bundles libVLC (LGPL-2.1+) and FFmpeg decoders (LGPL). LGPL allows use in closed apps as
long as the libraries are dynamically linked (they are) and their licences are credited in the app
(Settings → About lists them).
