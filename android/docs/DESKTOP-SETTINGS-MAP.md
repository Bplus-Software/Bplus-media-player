# B+ desktop to Android settings inventory

24 November 2026. Android 1.0.3/code 4, approved for preview release after off-device review. Physical-phone validation is still pending.

Desktop source: repository snapshot 698ddf0d3c7802948b30da6fe06613c784cec3e0, app/renderer/settings.js, renderer.js and icons.js. Downloaded from https://api.github.com/repos/basem-sadoun/Bplus-media-player/zipball.

"Implemented" means present in source and locally compiled. It does not mean physical-phone validation. The review images are actual Android views rendered off-device with sample media and update data.

## Category order

The Android screen now shows the desktop categories in the same order, right to left in Arabic: الصورة، الصوت، التشغيل، الترجمة، المظهر، اللغة، الاختصارات، الملفات، التحديثات، حول البرنامج. Ten icon tabs in two rows replace the Android chevron category list.

## Picture / الصورة

- الإضاءة، التباين، التشبع، درجة اللون: implemented sliders, persisted and applied through Media3 video effects. Effects are display-only.
- طريقة ملء الصورة: implemented Fit / Zoom / Stretch segments, persisted.
- Device codec and graphics behavior needs testing. The Android effect algorithms are not identical to desktop CSS filters.

## Audio / الصوت

- تضخيم الصوت: implemented 0–200 control using player volume below 100% and Android LoudnessEnhancer above 100%. Device support varies; distortion warning shown.
- الجهير، الترددات المتوسطة، الترددات العالية: implemented persisted -12 to +12 controls mapped across Android's available five-band equalizer. This is not the desktop's exact three-filter DSP implementation. Hardware effect availability varies.
- No phone audio-output validation yet.

## Playback / التشغيل

- التشغيل التلقائي عند فتح ملف: implemented.
- تشغيل الملف التالي تلقائيًا: implemented.
- إضافة ملفات المجلد بنفس الصيغة عند فتح ملف: existing Android folder-based implementation retained; Android folder access grant is required.
- تكرار الملف: implemented persisted switch.
- خطوة التقديم والتأخير (ثانية): implemented for keyboard left/right seeks. Dedicated on-screen 10-second buttons remain explicitly 10 seconds.
- سرعة التشغيل الافتراضية: implemented six speeds, 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2.
- صورة مصغرة عند الوقوف على شريط التقدم: Android adaptation is a thumbnail while dragging, not mouse hover.
- Android-specific group retains remember position, background playback and PiP.

## Subtitles / الترجمة

- تحميل الترجمة المجاورة تلقائيًا: not ported yet. Technically possible for a granted folder, not generally for an isolated content URI. Screen says it is not enabled.
- خط الترجمة: partial. Bundled Cairo plus Android sans / serif / monospace. Desktop also lists Segoe UI, Tahoma, Arial, Verdana, Times New Roman and Courier New; these font files are not bundled/licensed here. Do not claim exact font parity.
- حجم خط الترجمة: implemented 14–54 control.
- لون الترجمة: partial, white/yellow/green picker, not desktop arbitrary color yet.
- تأخير الترجمة: existing external-file timing picker retained. Embedded-track timing not supported. Full desktop +/- minute and +/-10-second preset row not yet ported.
- External subtitle opening retained.

## Appearance / المظهر

Grouping matches desktop: الواجهة / اللون / عرض الصوتيات.

### Interface / الواجهة

- نمط الأيقونات: all five desktop families are bundled from desktop glyph assets, with preview and selection: Phosphor, Phosphor Fill, Phosphor Duotone, Lucide, Material Rounded. Original license texts included. Glyphs without a desktop counterpart use Android line-icon fallback. Centered transport play/pause deliberately retain their corrected Android drawing.
- إظهار قائمة التشغيل عند البدء: implemented.
- السمة: Night / Day / Follow system implemented, Android system replaces Windows. Theme resets custom shade.
- درجة السمة: implemented, desktop direction, 0 light to 100 dark. Applies when the Apply shade action is tapped, unlike desktop immediate redraw.
- عتامة النافذة: not applicable as a desktop window effect. Android player remains an opaque activity; screen explains this. No overlay permission requested.

### Color / اللون

- All sixteen desktop presets: Blue, Orange, Green, Purple, Red, Pink, Teal, Amber, Cyan, Indigo, Lime, Rose, Silver, Copper, White, Light grey.
- Custom #RRGGBB: implemented text-entry picker, not desktop native graphical color chooser.
- Very light custom accents still need contrast testing on Day theme. Do not call the full light-theme audit complete.

### Visualizer / عرض الصوتيات

All seven desktop names are implemented, individually selectable, with visual previews:

1. الغلاف مع أعمدة / Cover + bars
2. أعمدة / Bars
3. موجة / Wave
4. خط / Line
5. نقاط / Dots
6. دوائر / Circles
7. شعار B+ / B+ Logo

- Sizes: صغير / وسط / كبير, implemented.
- Android canvas rendering follows the desktop style identities, not pixel-identical desktop animation.
- A Media3 decoded-PCM tap supplies waveform, RMS and spectrum data without microphone permission. No synthetic time pulse in normal playback. Picker previews use explicit sample shapes.
- Spectrum analyzer, performance, headphone/Bluetooth routing and real musical response need phone tests. This is not validated by static renders.

## Language / اللغة

Arabic and English selection implemented. Settings/menu strings follow the choice. Broader English UI layout needs a visual pass.

## Shortcuts / الاختصارات

- Desktop has 16 configurable bindings: play/pause, fullscreen, mute, next, previous, stop, subtitles toggle, earlier/later subtitles, playlist, settings, snapshot, forward/back seek, volume up/down.
- Android currently has fixed external-keyboard Space, F and left/right seek. Full configurable 16-action binding is not ported, not an Android impossibility. Screen states the limitation.

## Files / الملفات

- Android file picker, folder access and missing-file cleanup implemented.
- Windows default-app registration adapts to Android Open with and user folder grants.
- Desktop bundled FFmpeg transcoding, conversion-cache cleanup and Windows launch-report controls are not present. Android uses stock Media3 and device codecs. Do not copy desktop's extension list as a codec guarantee.
- File-opening diagnostic export is not ported, technically possible.
- Remove from library does not delete the media file.

## Updates / التحديثات

- Automatic check/download, prerelease option and manual update implemented.
- New version card, notes, byte/percentage progress and checking/downloading/verifying/permission/ready/installing/confirmation/error states.
- One manual Update action downloads, verifies and opens Android's installer. Unknown-app permission return retries installation. Final OS confirmation remains mandatory when Android requires it.
- Exact Android asset name, newer package version code, HTTPS host restrictions, SHA when supplied, stored local hash and signing certificate checks retained.
- Final Android confirmation opens directly when foreground; notification fallback otherwise.
- Real signed upgrade, permission-return and install behavior must be tested on phone.

## About / حول البرنامج

Version and licenses implemented. Extended developer/about presentation remains simpler than desktop.

## Cross-cutting gaps

- Per-section reset is not ported.
- No claim of complete desktop feature parity.
- 46 local tests pass at this checkpoint, including 39 UI/render/interaction tests and 7 updater selector tests. Debug compile and Android lint pass after fixing the API31 icon-asset loading path.
- Tests include deleted-file cleanup, explicit-removal reference cleanup, installer confirmation routing, failure/retry and staged-file cleanup. They do not prove actual phone playback or installation.

## Launcher icon

The desktop build/icon.png mark is used for legacy and adaptive Android color assets. Separate foreground and white background layers and a monochrome B+ cutout are included. API35 tests verify adaptive foreground/background/monochrome resources; off-device Android drawable renders were inspected. The home-screen illustration is not a real launcher screenshot.
