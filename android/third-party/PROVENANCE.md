# Dependencies in this candidate

AndroidX Media3 1.11.1: media3-exoplayer, media3-ui, media3-session, media3-effect and their transitive AndroidX/Guava/annotation/Kotlin Java dependencies, resolved from Google Maven/Maven Central. Apache2.0 project, keep license notices. No native LibVLC, FFmpeg or third-party decoder binaries remain in this candidate.

Official source https://github.com/androidx/media
Official license https://github.com/androidx/media/blob/release/LICENSE
Format limits https://developer.android.com/media/media3/exoplayer/supported-formats

Android's platform decoders determine supported sample formats. Containers include MP4/M4A/Matroska/WebM/MP3/Ogg/WAV/FLAC/AAC/TS/PS/FLV, not a guarantee for every codec combination. No promise of AVI/WMV or DTS/TrueHD/AC3 on every phone. Unsupported tracks must fail clearly. No FFmpeg extension included.

Cairo fonts: OFL license included, copied from existing B+ desktop source.
Application source license has not been selected by its owner. Do not declare the B+ application itself GPL or Apache by association with a dependency.

Earlier WIP used LibVLC3.7.8. It was removed before distribution because binary source/license provenance did not match an LGPL-only claim. Do not copy old WIP dependency notices into this release.
