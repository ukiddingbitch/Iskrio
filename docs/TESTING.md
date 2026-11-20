# Testing

## Self-contained checks

```sh
bash ./gradlew assembleDebug testDebugUnitTest lintDebug
node tools/test-js.mjs
```

Use gradlew.bat on Windows. Java tests use small synthetic metadata fixtures in app/src/test/resources/. The JavaScript suites use Node's standard library only. The 2.7.0 baseline passed 62 JVM tests and seven JavaScript suites; Android lint reported zero errors and 13 warnings. Portrait-orientation/JavaScript warnings include intentional app behavior.

## Recorded runtime verification

The owned x86_64 Android API 37 emulator was used for subtitle save/library loading, native Freeze with following audio and Undo/Redo, ramp/reverse/constant audio detachment, a four-project/65-media backup restoration, and rotated preview/export comparison. The independent decoder accepted the generated AAC assets. Browser UI checks covered images, personal templates, GIFs, editing, exports and editable backups.

The earlier 2.6 audit also exercised a 45-case native export matrix and fully decoded 52 generated media files; 2.7 used focused regressions rather than repeating that entire matrix. Raw historical debug dumps, personal paths and downloaded source media are not committed in this public copy.

## Optional native audio integration runner

`app/src/androidTest/.../OfflineMediaTest.java` is an existing fixture-driven instrumentation runner, not a zero-setup general UI suite. Build its APK with `assembleDebugAndroidTest` and install the debug app and test APK on a disposable emulator.

Its offline mode expects an indexed MP4 with AAC audio at app-private `cache/Iskrio25-range.mp4` (the historical fixture name). The video payload should be substantially larger than the audio because the runner asserts that selective audio reads use less than half of the source bytes. Generate your own redistributable fixture with FFmpeg, push it to `/data/local/tmp/`, then copy it into the debug app's cache with `adb shell run-as sk.iskrio.studio cp ...`.

Run:

```sh
adb shell am instrument -w sk.iskrio.studio.test/sk.iskrio.studio.OfflineMediaTest
```

The optional `-e audioUrl HTTPS_URL` argument exercises a public HTTPS indexed audio source instead; external services and range support can change. It deliberately contacts that host. Never use private/signed URLs in public logs.

## Remaining scope

Physical ARM hardware, API 26 runtime behavior, long 4K/60 exports, storage exhaustion, all codec variants, all external hosts and every edit combination are not exhaustively verified. A native decode success does not establish visual parity. Validate the changed interaction and export content as well as stream integrity.
