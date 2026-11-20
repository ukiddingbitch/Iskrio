# Licensing

## Iskrio-authored code

The original MIT grant and copyright notice are preserved in LICENSE and app/src/main/assets/licenses/APP-LICENSE.txt. This repository preparation does not revoke that grant or change upstream ownership.

## Combined Android application

The project links `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.9`. The GPL-enabled engine means the combined distributed application is distributed under GPL version 3. The GPL text is in COPYING-GPL-3.0.txt; component-specific notices remain in app/src/main/assets/licenses/ffmpeg/ and the upstream dependency. An APK built from this repository is not covered solely by the repository's MIT notice.

Native source locations, pinned versions, wrapper/build source and the supplied native archive are described in docs/FFMPEG-SOURCES.md. Keep that source access and the notices with binary redistribution. The native archive is separate from the Git tree to avoid vendoring a large upstream ZIP.

## Other components

- OkHttp, Okio, Kotlin standard library and AndroidX Media3: their bundled Apache-2.0 notices apply.
- Android GIF Drawable and Jsoup: their bundled MIT notices apply.
- GIFLIB: its bundled license applies.
- Core-library desugaring: the bundled GPLv2 + Classpath Exception notices apply.
- Manrope: SIL Open Font License; see app/src/main/assets/fonts/OFL-Manrope.txt.

Full notices are retained under app/src/main/assets/licenses/. The UI screenshots use original procedural demo artwork and are covered by the Iskrio-authored MIT grant. They do not include downloaded social-media content or another editor's proprietary assets.
