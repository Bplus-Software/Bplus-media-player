# Android video-player baseline review

Reviewed 24 November 2026. This is a controls and settings baseline, not a claim that B+ matches every codec, renderer, feature or device supported by these projects. Product documentation and source README files were inspected; competitor apps were not installed and tested on a phone.

## Recommendation

Use familiar direct controls and left-brightness/right-volume gestures, with a visible fallback and feedback. Keep frequent video changes inside the player. Follow Basem's specific tap-volume/long-press-tracks and compact horizontal-slider instruction instead of copying another player's exact layout.

## Findings and implementation check

| Area | Source baseline | B+ current local implementation | Remaining evidence or gap |
|---|---|---|---|
| Volume and brightness | VLC and Just Player document left brightness/right volume swipes | Container-owned dispatch; visible volume and sun buttons; horizontal inline sliders; percent feedback | Physical-phone response unverified; OS audio limits can apply |
| Basic playback | VLC documents seek and playback controls; Just Player documents speed/resize | Play/pause, previous/next, 10-second buttons, timeline, speed, fit/zoom/stretch, rotation | Real decoding and rotation need handset test |
| Touch shortcuts | Just Player documents double-tap seek and pinch zoom | Double-tap outer thirds seeks 10 seconds; middle toggles play; pinch scales 1x-3x with feedback | Dispatch tested with consuming child; decoded surface zoom not phone-verified |
| Lock | VLC and Just Player document touch lock | Long press unlocks; controls and gestures suppressed while locked | Real pocket/multi-touch behavior unverified |
| Audio/subtitles | Just Player documents track selection, subtitle styles and external subtitles | Tap volume controls loudness; long-press selects tracks; external subtitle picker; font/size/color/delay settings | Auto matching/search remains absent; delay only external subtitles |
| Settings context | VLC separates quick video tools from persistent options | Audio/video/subtitle appearance open over the current player; existing desktop category organization retained globally | Full desktop shortcut customization and per-section reset not ported |
| Resume and queue | VLC documents resume policies and speed retention | Remember position, queue, next, repeat/shuffle, stored default speed | Ask-before-resume option absent |
| Background and notification | Just Player documents media session/audio focus/headset pause; Android recommends a playback service and media session | Foreground playback service/session, audio focus, headset-unplug pause, notification actions, zero-speed paused/buffering state, timestamped progress | OEM notification animation and background lifecycle remain device-untested; not migrated to MediaSessionService |
| PiP | All three open-source projects document PiP/background support | Direct PiP button, leave-to-PiP preference, actions | Real-device PiP not verified |
| Advanced decoding/rendering | mpv lists libass, dual subtitles and advanced rendering; Just Player bundles FFmpeg audio decoding | Stock Media3 device decoding, basic filters and subtitle styles | No claim of mpv/libass, dual subtitles, FFmpeg audio parity or all-format support |

## Risks found in this pass

The earlier off-device gesture tests did not enforce the real Android permission/dispatch paths. Added MODIFY_AUDIO_SETTINGS to the manifest, but that omission alone is not a proven cause of Basem's failure. Replaced child-dependent handling with dispatch at the player container, including tests where the video child consumes all events. The horizontal timeline and inline level slider are excluded from video scrubbing gestures. Direction locks once a swipe starts. System brightness is read when no window override exists instead of assuming 50%.

The first landscape preview had center transport overlapping the slider region. The transport now hides while the inline slider is open in short landscape, then returns when closed. No new installer was built during render review.

## Source ledger

- VLC official video-player documentation: https://docs.videolan.me/vlc-android-user/video/video_player.html . Gestures, lock, quick controls. Primary documentation, no page date visible.
- VLC official video settings: https://docs.videolan.me/vlc-android-user/more/settings/extra_video.html . Resume, saved brightness/speed, seek controls. Primary documentation; UI details may lag releases.
- Just Player README: https://github.com/moneytoo/Player . Gestures, tracks, speed, resize, PiP, media session, headset pause, subtitle/system-style and advanced settings. Current repository documentation.
- mpv-android README: https://github.com/mpv-android/mpv-android/ . Gestures, background, PiP, rendering and subtitle capabilities. Current repository documentation.
- MX Player FAQ: https://mxplayer.net/faq.html . Device-dependent volume/brightness restrictions and decoder limits. Official but contains old Android references, so not used as proof of current exact UI.
- Independent practical MX review: https://www.makeuseof.com/i-replaced-androids-default-video-player/ . Published 19 September 2026; describes left brightness/right volume and horizontal seeking. Its exaggerated volume-boost quantity was not relied on.
- Community mpv issue: https://github.com/mpv-android/mpv-android/issues/1143 . Open report updated 15 January 2026, illustrates a real gesture/volume failure and developer discussion of fixed-volume/no-audio gating. Not evidence that B+ has that same cause.
- Android playback architecture: https://developer.android.com/media/media3/session/background-playback . Primary platform guidance for media-session foreground service.
- Android audio API: https://developer.android.com/reference/android/media/AudioManager . Primary platform API reference.
- Permission reference: https://developer.android.com/reference/android/Manifest.permission . MODIFY_AUDIO_SETTINGS concerns global audio settings. Does not prove the exact handset failure cause.

## What this does not establish

No popularity ranking or superiority claim is made. There is no physical handset, decoded sample playback, Bluetooth route, OEM notification animation or installer test in these renders. Basem's handset acceptance remains the closing check, not a substitute for the regressions above.
