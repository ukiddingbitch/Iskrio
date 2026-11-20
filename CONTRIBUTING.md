# Contributing to Iskrio

Keep pull requests small and explain the user-visible problem, resulting behavior and verification. Preserve local editing/export, explicit network gating, user-owned templates and the portrait workflow. AI/cloud processing is outside the current project scope.

## Development checks

Use the toolchain in the README, then run:

```sh
bash ./gradlew assembleDebug testDebugUnitTest lintDebug
node tools/test-js.mjs
```

Use `gradlew.bat` on Windows. Test the affected user flow on a device/emulator when it involves media decoding, the Android bridge, storage or native FFmpeg behavior. Add regression coverage for a concrete failure rather than tests that merely repeat implementation details.

For media issues, report Android version, device ABI, WebView version, dimensions, codec, duration and the exact editing/export steps. Attach a small synthetic or redistributable sample when possible. Do not publish private media, credentials or signed/private download links.

Do not commit generated builds, local SDK paths, IDE caches, keystores, downloaded social-media examples or secret configuration. Preserve license notices and document new dependencies. Contributions to Iskrio-authored code use the existing MIT grant; combined binary distributions must also satisfy the GPL-enabled engine's requirements described in LICENSING.md.
