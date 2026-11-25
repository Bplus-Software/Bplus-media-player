# Android 1.0.3 preview validation

24 November 2026. Basem approved the UI and launcher renders before this build. Those are off-device renders of actual Android views, with fixture media/update data, not physical-phone screenshots. Basic playback feedback exists for earlier 1.0.1/1.0.2 builds, not this 1.0.3 revision.

## Checked locally
- 46 tests, 39 native-view/render/interaction and 7 update-selector tests.
- Android lint and release compilation.
- Removed/deleted-file cleanup, existing-file preservation, installer-confirmation routing, retry state, staged-file cleanup, PCM read without consuming input, all seven picker styles, adaptive foreground/background/monochrome resource.
- Actual pixel inspection of portrait/landscape video, menu and subtitle sheets, brightness/queue sheets, settings groups, visualizer picker, update card, centered play/pause, and adaptive/themed icon.
- com.bplus.player, 1.0.3/code 4, minSdk 31, targetSdk 35.
- Same player-specific signing identity as earlier builds. Final APK signature and alignment are verified at handoff.

## Not phone-tested
Revised insets and font/display scale, real decoding, audio focus/calls/headsets/Bluetooth, notifications, screen-off playback, actual OS PiP, SAF grants and remote providers, subtitles/timing, snapshot and video effects, PCM visualizer performance, boost/EQ hardware support, real signed upgrade and unknown-app permission/installer flow.

## Feature mapping and known gaps
See DESKTOP-SETTINGS-MAP.md and RELEASE-1.0.3-ar.md. This is not complete desktop parity.

## Phone checklist
1. Upgrade over 1.0.2 and check library/settings/resume survive.
2. Open local MP4 and audio from Files/Share; pause, seek, switch tracks and reopen.
3. Portrait/landscape, long filenames, small display and large fonts, control hide/lock/unlock.
4. Actual PiP and notification controls, background/screen-off audio, headset/call interruptions.
5. Folder grants, favorites/recent, explicit removal and external deletion, temporarily unavailable providers.
6. All seven visualizers with actual music, sizes, icon families, dark/light/theme changes, effects, subtitles and snapshot.
7. Update download/progress/verification, first permission return, final OS install confirmation, cancellation and retry.

## 1.0.4 navigation and session patch
- Updates retains all settings categories; switching and reopening tested.
- Audio sheet retains the player root and track through dismissal.
- Video container intercepts vertical gestures over controls but excludes the timeline; taps remain un-intercepted. Touch dispatch and control actions tested off-device.
- Notification state uses zero speed while paused/buffering, timestamped playback anchors and cached metadata. Seek-to-zero forces an immediate zero-position anchor.
- Actual phone decoding, OEM notification animation and real-device gesture ergonomics remain untested.

## 1.0.5 direct video controls and baseline pass
- 62 tests: 55 UI/interaction + 7 updater selector, no failures/skips.
- Volume normal tap opens inline horizontal slider; long press selects tracks. Brightness has a dedicated button.
- Player-container dispatch tested even with a video child consuming every event; portrait/landscape vertical gestures tested. Timeline and inline sliders excluded.
- Double tap outer thirds and synthetic two-pointer pinch tested; feedback rendered. Decoded video surface behavior remains phone-untested.
- Audio/picture/subtitle appearance sheets preserve player root. Back first closes inline level slider.
- Competitor/control baseline: ANDROID-PLAYER-BASELINE.md. This is a sourced checklist, not full feature or decoding parity.

## 1.0.6 priority layout and level-panel dismissal
- 64 tests: 57 UI/interaction + 7 updater selector.
- Important controls form one bottom row; aspect/snapshot/PiP/queue/tools are on top.
- Level panel is above timeline with spacing, no X. Same control toggles, other control switches, outside touches dismiss; inside slider remains excluded from video gestures.
- Portrait and landscape actual-view renders inspected; no phone verification.

## 1.0.7 audio button, header and speed slider
- Basem approved the reviewed batch on 24 November 2026 at 16:10 +03.
- 67 tests pass: 60 UI/interaction and 7 updater selector; no failures or skips.
- Release lint and compilation pass. Application version 1.0.7/code 8.
- Audio uses the outlined three-wave speaker icon instead of brightness; tap opens audio settings. Existing settings gear remains.
- Shared B+ header stays on the physical left for Arabic and English. Android language choices remain Arabic/English only.
- Playback speed has a horizontal slider from 0.25x to 3.00x in 0.05x steps and 0.5x/1x/1.5x/2x shortcuts. Video uses the raised inline panel; audio uses the styled bottom sheet.
- Actual-view Arabic/English audio and audio/video speed renders inspected for clipping and overlap. They are off-device renders, not phone screenshots.
- Real-device playback, speed behavior, decoder limits and OEM UI still need phone testing. Other phone caveats above remain.

## 1.0.8 displayed-list playback and library return
- Approved by Basem on 24 November 2026 at 16:34 +03 after the behavior description and off-device fixture render.
- Version 1.0.8/code 9. 73 tests: 66 UI/interaction and 7 updater selector, no failures/skips.
- Library row and menu Play use the same filtered/ordered list as the screen; no mixed audio/video queue. Recent ordering, Favorites, folder/search and selected index tested.
- Audio middle-item selection, Next/Previous and Back to Audio tested. Library tab/filter/search/folder are persisted and restored by a new activity.
- Explicit queue playback retains that queue. Manual Next/Previous navigate even with repeat-current enabled, while automatic end repeats the current file.
- Inspected actual-view return-to-Audio fixture render. No user files were touched. Real media decoding/end events and exact phone lifecycle sequence remain untested; source-level gaps are confirmed, not a claim that every phone path was reproduced.

## 1.0.9 matched transport icons and audio volume-only control
- Approved by Basem on 24 November 2026 at 16:42 +03, all updates approved for publication after his verbal correction.
- Version 1.0.9/code 10. 74 tests: 67 UI/interaction + 7 updater selector, no failures/skips.
- Previous now maps to the existing desktop prev glyph, matching Next in each of five icon families instead of falling back to a different outline.
- Audio speaker button opens a single media-stream volume slider, not the audio-effects settings. Gear keeps full settings. Arabic/English root and sheet actual-view renders inspected.
- Test checks actual button click, one SeekBar only, no bass control, player preservation and previous/next glyph availability in all five families.
- Phone audio output, volume policies, headset/Bluetooth and OEM behavior remain untested.
