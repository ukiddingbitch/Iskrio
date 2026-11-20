# Building signed releases

Version 2.7.1 uses versionCode 20. The release build explicitly disables Java/Kotlin and JNI debugging. MainActivity already enables WebView debugging only for a debuggable application, so WebView remote debugging is disabled in the release APK too. Debug builds remain available for development. No media/editor feature, dependency or Android permission changes in this release.

## Local Windows signing setup

The owner's signing material is outside the public project at `%USERPROFILE%\.android\iskrio-release\`. It contains the private PKCS12 keystore, a DPAPI-protected local credentials file and a private recovery file. Never upload that directory or its contents. Keep a separate secure backup of the keystore and recovery password; DPAPI credentials alone are tied to the Windows user profile.

With JDK 25 and the Android SDK configured, run:

```powershell
.\tools\build-release.ps1
```

The helper reads the local protected credentials, passes them to Gradle through process-only environment variables and restores the previous environment afterwards. It does not print the password. The signed APK is generated at `app/build/outputs/apk/release/app-release.apk`.

## Other contributors / machines

Use your own private signing key and set all four environment variables before `assembleRelease`:

- `ISKRIO_KEYSTORE_FILE`: private keystore path.
- `ISKRIO_STORE_PASSWORD`: keystore password.
- `ISKRIO_KEY_ALIAS`: alias inside the keystore.
- `ISKRIO_KEY_PASSWORD`: key password.

Android Studio's **Generate Signed Bundle / APK** flow is also supported through its injected signing properties. Release packaging fails clearly when no signing configuration is available. Debug builds require no release credentials. Never put passwords in public Gradle files or commit a keystore.

## Verification and upgrades

Check the APK signature, release certificate and `debuggable` flag before publishing. A debug APK and a release APK signed with different keys cannot update each other normally. Export projects/library first, uninstall the old debug app, install the release APK and restore the backup. Future release updates must use this same private release key and a higher versionCode.

The full application remains GPL version 3 when distributed with the GPL-enabled native engine; retain matching source access and notices. This packaging change does not establish compatibility with every physical device or media file.
