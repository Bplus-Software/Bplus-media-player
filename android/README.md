# B+ Media Player for Android, 1.0.1 first preview

Original Android Java UI using stock AndroidX Media3 1.11.1. Android12+ (API31). No ads, activation or media uploads. GitHub is contacted for update checks/downloads. Only files/folders selected by the user are read. App source license has not been selected by its owner; dependency licenses do not assign a license to the original app.

This first preview is NOT complete desktop parity and has not been run on an Android phone. Read docs/VALIDATION.md for actual checks, known gaps and the device test checklist.

## Build
JDK17, Android SDK36/build-tools36, Gradle8.13, Android Gradle Plugin8.10.1. Set ANDROID_HOME or create untracked local.properties with sdk.dir. Run gradle :app:assembleRelease :app:lintDebug :app:testDebugUnitTest. The release output is unsigned until signed with the retained player-specific key. Do not commit key/password files. Package ID com.bplus.player, versionName1.0.1, versionCode2. Future APKs must retain the key and increase versionCode.

## Shared releases
Repository Bplus-Software/Bplus-media-player. Exact Android asset naming: Bplus-Media-Player-1.0.1.apk. Coordinated Windows asset: Bplus-1.0.1-Setup.exe. Same tag/version; no Android-only tags while older desktop fallback depends on an exe. This version is a prerelease and stable v1.0.0 stays unchanged.

## Updater
Exact Android APK selector, newer semantic version, explicit prerelease opt-in, resumable HTTPS download, GitHub SHA256 digest validation when supplied, package ID/newer versionCode and signing certificate verification. Automatic check/download toggle, Wi-Fi-only setting, manual check. Conditional no-user-action self-update where Android permits, confirmation notification otherwise. Install waits while media is playing. Initial unknown-source trust is user-controlled. Periodic checks are Android-scheduled opportunities, not guaranteed exact times. No newer release exists yet for a real upgrade test.

## Playback
Targets common phone media such as MP4/M4A/MP3/MKV/WebM/Ogg/FLAC/WAV, subject to the phone's decoder support, not extension alone. No bundled FFmpeg/LibVLC decoder, universal codec promise, DRM playback or cloud conversion.

## Dependencies
See third-party/PROVENANCE.md and licenses. Media3/AndroidX Apache2.0, Cairo OFL. No GPL engine binary in this candidate.

## Android 1.0.3 UI revision

The current working source includes the desktop-matched settings revision, seven PCM-driven visualizer choices, five icon families, adaptive launcher mark, shared dark sheets, visible video tools, removed-file cleanup and direct update flow. The owner approved the off-device UI and launcher review on 24 November 2026. This source is version 1.0.3/code 4. See docs/DESKTOP-SETTINGS-MAP.md for exact implemented/partial/missing status. It is not complete desktop parity and has not been phone-tested.
