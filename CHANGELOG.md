# Changelog

## 2.7.1

- Add a dedicated private-key release signing configuration without committing credentials.
- Explicitly disable application and JNI debugging in release builds; existing conditional WebView debugging is also off.
- Keep debug builds available for development and retain the current optimizer/media/editor behavior.
- Increment versionCode to 20 and versionName to 2.7.1.
- Add a local DPAPI-aware Windows release build helper and public signing/upgrade instructions.

## 2.7.0

Subtitle/autosave validation, shared backup capacity, rotated preview fitting, timing-preserving audio detachment and mixed-media Freeze fixes. See TESTING.md in docs for existing verification scope.
