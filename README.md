# AITerminal

AITerminal is an Android SSH terminal app for managing remote sessions and project workflows from a phone or tablet. It uses Jetpack Compose for the UI and Termux terminal components for terminal rendering/input behavior.

## Features

- SSH session management with saved servers and projects.
- Terminal tabs with tmux-oriented session support.
- Remote file upload and download over SSH/SCP/SFTP.
- Import/export for app data.
- AI tool launch helpers for Claude, Gemini, Codex, and Openclaw.
- Local diagnostic build flavor for app testing.

## Build

Requirements:

- Android Studio or Android SDK with JDK 17+
- Android SDK platform 36

Build the production release APK:

```sh
./gradlew assembleProductionRelease
```

The APK is written to:

```text
app/build/outputs/apk/production/release/app-production-release.apk
```

## License

AITerminal is released under the GNU General Public License v3.0 only. See [LICENSE](LICENSE).

The app includes or adapts terminal components from the Termux project. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and license notes.
