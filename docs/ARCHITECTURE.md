# Architecture

The Android activity hosts a local WebView editor. `app/src/main/assets/` supplies the offline UI, drawing, GIF encoding, project state and timeline controls. `StudioCore` and `StudioEdit` contain shared timing/edit operations; the UI, Track, crop, subtitle and workspace modules implement interactions and live preview.

The JavaScript bridge in MainActivity handles document selection, app-private media storage, explicit HTTPS downloads, microphone recording and native work. StudioEngine builds local FFmpeg filter graphs for composition and export. Native inputs resolve to private local files; media pipelines do not receive arbitrary remote protocols. Ordinary preview can compose directly; advanced effects use locally rendered previews.

FrameStore retains decoded GIF frames and original bytes for unchanged GIF export. VideoStore/StudioMedia retain video, photo and audio assets. VideoArchive streams portable project/library ZIPs with bounded metadata, entry counts, paths and media sizes. LinkImporter, SafeUrlPolicy and PageMediaResolver implement optional public HTTPS import and metadata resolution; indexed audio-only transport avoids a silent full-video fallback.

Java/JVM tests and dependency-free Node suites are committed. Android native integration needs a disposable device/emulator and fixture setup as described in TESTING.md. Runtime permissions remain in AndroidManifest.xml. See LICENSING.md for component ownership and distribution terms.
