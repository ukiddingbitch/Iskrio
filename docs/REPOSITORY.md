# GitHub repository settings

- Name: `iskrio`
- Visibility: **Public**
- Description: An offline-first Android studio for memes, GIFs, video and audio editing. Multitrack timelines, local exports, no ads, no accounts and no AI.
- Homepage: leave blank until a real project website exists.

## Topics

GitHub supports at most 20 topics. These 20 use lowercase letters and hyphens:

```text
android, android-app, offline-first, video-editor, video-editing, meme-generator, gif-editor, gif-maker, image-editor, audio-editor, timeline-editor, ffmpeg, ffmpegkit, java, javascript, android-webview, privacy, open-source, multimedia, creative-tools
```

[GitHub topic rules](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics)

## Initial upload

Create an empty public repository named `iskrio`. Do not generate another README or license when creating it: those files already exist here. Upload the contents of this source folder at the repository root, or initialize Git locally and push it. If using Git on Windows, preserve executable permission for the wrapper with `git update-index --chmod=+x gradlew` after staging it.

The ZIP is a transport copy: extract it and commit the source files, rather than committing the ZIP itself. Build outputs, SDK paths, IDE caches and signing keys are intentionally omitted. The sibling release-assets folder is for release attachments, not normal Git commits.

For a binary release, build/review with the current source and your signing key, attach the APK and matching source access/native archive, and preserve notices. The provided development APK is debug-signed; do not present it as a production-signed store release. No repository, release or external upload has been created automatically.
