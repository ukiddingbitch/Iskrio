# FFmpeg engine source access

The application pins `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.9` from Maven Central. This GPL-enabled engine requires the combined distributed application to comply with GPL version 3; Iskrio-authored files retain their original MIT grant. Preserve all component notices.

## Pinned upstream source

- [Upstream Android release](https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v8.1.9-lts-android)
- [Wrapper/JNI/build-source archive at the exact tag](https://github.com/ffmpegkit-maintained/ffmpeg/archive/refs/tags/v8.1.9-lts-android.zip)
- Tag: `v8.1.9-lts-android`; native build root: `android-8.1-lts/`.
- Source locations, version pins and build patches are in `android-8.1-lts/scripts/source.sh` and `scripts/android/*.sh` in that archive. These scripts identify/download the FFmpeg and enabled library sources required for a native rebuild.
- [Pinned FFmpeg upstream revision](https://github.com/FFmpeg/FFmpeg/tree/n8.1.3)
- [x264 upstream source](https://code.videolan.org/videolan/x264)
- [x265 upstream source](https://bitbucket.org/multicoreware/x265_git/)

The separately prepared release attachment `Iskrio-FFmpeg-Build-Sources.zip` is the matching upstream wrapper/JNI/build-source archive. SHA-256:

```text
097ab125ed973d80a88e3b6ddc81d25e2baddb8db4a8cf6ba91c861273aad31c
```

The source archive is not committed as a large vendored Git blob. Its build scripts require the referenced upstream codec/library sources and build environment; it is not a claim that this Android project alone rebuilds every native library or that the native binary rebuild is byte-reproducible.

Ordinary Android Studio builds resolve the pinned Maven engine. Keep matching application/native source access, build information and license notices with binary redistribution. Bundled native notices are in `app/src/main/assets/licenses/ffmpeg/`; general third-party notices are in `app/src/main/assets/licenses/THIRD-PARTY-NOTICES.txt`.
