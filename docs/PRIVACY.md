# Privacy and permissions

Editing, playback, projects, waveform analysis, GIF decoding, effects, recording and export run locally. The app has no account flow, advertising SDK, analytics integration, AI model or cloud processing service. Project files and selected media are not uploaded by these workflows.

The manifest requests INTERNET and RECORD_AUDIO. Android's document picker provides access to user-selected files; no broad storage permission is requested. Microphone recording is explicit and stops when the app leaves the foreground.

Optional HTTPS link imports require the Internet access switch. The native importer enforces a gate, public-address DNS/URL checks, bounded redirects/body sizes, cancellation and connection cleanup. The gate turns off and the session closes before downloaded media are decoded. Importing contacts the chosen source host, which receives the request/IP and URL parameters. Projects retain local media rather than live download links.

The switch is an application control, not an OS firewall and not revocation of Android's normal INTERNET permission. Sources requiring authentication, DRM, streaming manifests or unsupported media indexing may be unavailable. Keep Android and WebView updated; bounded parsing is not a guarantee against all malformed media or platform decoder bugs.
