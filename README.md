# B+ Media Player 1.9.1

Original Windows x64 media player with Arabic and English UI, built for Basem Helmy.
No advertising, activation, updater or remote playback/conversion service.
Media stays on the computer. Original files are never edited.

## Install and open files

1. Close every running B+ window before installing an update. The installer
   checks running B+.exe processes, including portable copies, and whether the
   installed executable can be replaced. It asks you to close B+; it does not
   kill it. A silent install aborts safely if B+ is running.
2. Run Bplus-1.9.1-Setup.exe. It installs for the current user, without admin.
   v1.2-v1.6 share the same install folder and uninstall key. v1.6 renames the
   shortcuts to "B+ Media Player" and removes the old "B+" shortcuts on upgrade.
   New versions overwrite that install, keep one uninstall entry and replace
   the same shortcuts. v1.5/v1.6 record and reuse the install path; for older
   installs it can recover the folder from the known B+ uninstaller path.
   App identity remains com.bplus.player. Settings and resume data are kept.
   v1.0/v1.1 portable executables are standalone files, not installed versions;
   they are not automatically deleted from Downloads or other user folders.
   Unknown builds installed under another identity are not silently removed.
3. Right-click a supported media file, choose Open with, then B+.
   To use Explorer double-click, select B+ as the Windows default once.
   Windows protects this choice; the installer does not force a new default.
4. Settings > Files opens Windows default-app settings. It also saves an optional
   local launch report when requested. The report contains local file paths and
   is never uploaded automatically.

Split delivery: keep all numbered .7z volumes together and extract .001 with
7-Zip. Do not rename individual volumes. The extracted file is one installer.
This development build is unsigned. Check the supplied SHA-256 before running.

## What's new

- Startup media delivery uses a renderer pull and acknowledgment instead of
  relying on one startup event. A second process forwards its file to the
  existing window. Out-of-order startup replies are ignored.
- One shared extension list drives argument parsing, file dialogs, folder
  scanning and generated installer registrations.
- Windows OpenWith ProgIds, application capabilities and quoted open commands
  are registered for the supported types, without changing protected UserChoice.
- Bundled offline FFmpeg prepares formats that the browser engine cannot play.
- Night, day and follow-Windows themes; 65-100% whole-window opacity.
- Fourteen accent colors and a custom picker, applied live and saved locally.
- Fourteen configurable actions. Old custom keys are preserved when possible;
  duplicate keys are prevented. Ctrl+O, Ctrl+L, Ctrl+P and F11 remain fixed.
- Empty-stage double-click opens the file dialog. With media loaded, it toggles
  fullscreen. The sun popup, settings gear and speed control remain separate.
- Local per-file playback position resume, subtitles and image/audio controls.

## Formats and limits

Recognized video containers include MP4, M4V, MOV, WebM, MKV, AVI, WMV, FLV,
MPEG/MPG/M2V, MTS/M2TS/TS, VOB, 3GP/3G2, ASF, DIVX, F4V and OGV.
Recognized audio types include MP3, FLAC, WAV, AAC, OGG, M4A, WMA, OPUS,
AIFF/AIF, APE, AC3, DTS, AMR, ALAC, MKA, MP2, OGA and WEBA.
An extension is not a guarantee for every codec combination.

Files with native supported codecs use direct playback. Others are remuxed or
converted locally to temporary MP4 before playback. H.264 and AAC/MP3 streams
may be copied without re-encoding. Other video is converted to H.264 and other
audio to AAC. This can take time and extra disk space, especially for long or
high-resolution files. Cancel stops preparation; Play retries it. Settings >
Files can clear temporary conversions, stopping playback first. Conversions are
session-only and are removed on exit or next startup, where possible.

Only the first video stream and first audio stream are used. Embedded subtitle
and audio-track switching, DVDs/menus, DRM, HDR tone mapping, network streams
and every unusual codec are not supported. Use external SRT/VTT subtitles.
Protected, corrupt and unsupported files produce an error rather than a
promise of universal playback. Whole-window opacity affects the video too.

## Validation status

Fresh packaged-process launches and a separate-process file handoff were tested
under Linux, including Arabic/spaces/# paths and MKV conversion. Actual decoded
playback fixtures cover H.264 MP4/MOV/MKV, MPEG-4 AVI, WMV2/WMA, FLV, MPEG-2,
HEVC MP4, MP3, FLAC, WAV, AAC, OGG, OPUS and M4A. Playback resume, audio signal,
subtitles, settings, themes, palettes and shortcuts were also checked.

The shipped Windows application and Windows FFmpeg executable have not been
run on native Windows here. Explorer associations, hardware-dependent behavior
and Windows opacity still need native Windows acceptance testing. The helper's
archive hash was checked against its publisher's release digest. Additional
compatibility tests ran the actual Windows FFmpeg binary and converted WMV
to H.264/AAC. The actual Windows installer was tested with a v1.4 upgrade,
recovered moved install path and repeat install under Wine 11.22. It kept one
uninstall entry and exactly the two expected shortcuts. A running portable
B+.exe blocked the installer; closing it allowed installation. Uninstall
cleanup and preservation of user-data sentinels also passed. This is not a native
Windows acceptance test; the full Windows app was not validated under Wine.

## Source and rebuilding

This source backup excludes node_modules, generated installers, media fixtures
and the compiled third-party helper. It includes all B+ app code, original
icons, fonts, licenses, NSIS script and portable launch-parser tests.

For Windows development, copy resources/ffmpeg/ffmpeg.exe from the installed
B+ 1.5 package to vendor/ffmpeg/ffmpeg.exe. Check its binary SHA-256 against
vendor/ffmpeg/PROVENANCE.json. That file also records the original archive,
FFmpeg source revision and build-recipe revision. The publisher's latest
archive URL can change; do not silently substitute an unverified download.

Then, with Node.js and NSIS available:

    npm ci
    npm test
    npm start
    npx electron-builder --win --dir
    makensis build/installer.nsi

The .npmrc selects the castLabs Electron distribution used in this build.
On Linux development only, install a local FFmpeg or set BPLUS_FFMPEG to its
executable path. The Windows release includes its helper and needs no download.

The B+ source and original icons are MIT licensed. Third-party software keeps
its own licenses. The standalone FFmpeg helper is GPL-3.0-or-later. License and
source/build provenance are included. Review complete source-distribution and
codec-patent requirements before public or commercial redistribution. This
private development package does not claim rights in third-party codecs or
GOM Player, and it is not a legal or codec-patent warranty.
