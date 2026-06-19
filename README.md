# TerminalHub

TerminalHub is an Android SSH terminal for people who run real work somewhere
else and use the phone as the slightly-too-small cockpit. It is built around
SSH, tmux, project tabs, file transfer, and AI terminal tools such as Claude
Code, Codex, Gemini, and Openclaw.

It uses Jetpack Compose for the Android UI and Termux terminal components for
terminal rendering and input behavior.

## Why This Exists

- **Tabs are the main event.** Tap the top bar and jump between live sessions.
  Let one AI agent compile excuses while another pretends to refactor.
- **Several sessions can work at once.** Each tab keeps its own project,
  terminal, tmux target, and prompt history.
- **Upload files straight into the current remote project.** Send one file or
  several from Android into the active directory when an AI tool needs logs,
  docs, screenshots, or any other context that was inconveniently born on the
  phone.
- **Write prompts like a human, not like a trapped shell builtin.** A larger
  text input helps with natural-language AI prompts, and input history is saved
  per tab if sending fails or the app gets closed.
- **tmux is the seatbelt.** Sessions are created/attached through tmux by
  default, so work can survive disconnects, app restarts, closed tabs, and other
  traditional ceremonies of mobile networking.
- **Workspace state is portable.** Active tabs, servers, and projects can be
  restored after app updates, and exported/imported when moving to another
  device.
- **The keybar has the keys phones forgot.** Common terminal/modifier keys and
  upload/download actions are reachable without doing finger origami.
- **AI tools get shortcuts.** Launch helpers exist for Claude, Codex, Gemini,
  and Openclaw, because typing the same command forever is not a personality.

Other useful parts:

- Saved SSH servers and project profiles.
- Password and private-key SSH authentication.
- Remote file download from the SSH login directory.
- Terminal search, app logs, session logs, and SSH status/error diagnostics.
- Optional biometric gate on startup.
- Diagnostic build flavor for local testing.

## Requirements

For the Android build machine:

- macOS, Linux, or Windows with a shell.
- Git.
- Android Studio, or the Android SDK command-line tools.
- JDK 17 or newer. Android Studio's bundled JDK works.
- Android SDK Platform 36.
- Android SDK Build Tools installed by Android Studio/SDK Manager.

For the remote machine you connect to:

- SSH server.
- A user account you can log into.
- `tmux` installed if you want session restore, which you probably do.
- Optional: AI terminal tools such as `claude`, `codex`, `gemini`, or
  `openclaw` installed on that remote machine.

## Fresh Setup

Clone the repository:

```sh
git clone https://github.com/joynes/terminalhub.git
cd terminalhub
```

Check that the Gradle wrapper is present:

```sh
ls gradlew gradle/wrapper
```

If you use Android Studio:

1. Open the repository folder.
2. Let Gradle sync finish.
3. Install Android SDK Platform 36 if Android Studio asks for it.
4. Select the `app` run configuration.
5. Run on an emulator or a connected Android device.

If you use the terminal:

```sh
./gradlew tasks
```

On macOS, if you want to use Android Studio's bundled JDK explicitly:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Build

Build a debug APK:

```sh
./gradlew assembleProductionDebug
```

Build the release APK:

```sh
./gradlew assembleProductionRelease
```

The release APK is written to:

```text
app/build/outputs/apk/production/release/app-production-release.apk
```

There is also a diagnostic flavor, useful when you want a separate install for
testing without trampling the normal app:

```sh
./gradlew assembleDiagnosticDebug
```

## Test

Run unit tests:

```sh
./gradlew app:testProductionDebugUnitTest
```

Run a clean build plus unit tests plus release packaging:

```sh
./gradlew clean app:testProductionDebugUnitTest assembleProductionRelease
```

Install the release APK on a connected emulator/device:

```sh
adb devices
adb install -r app/build/outputs/apk/production/release/app-production-release.apk
```

If `adb` is not on your `PATH`, use the SDK path directly, for example:

```sh
$HOME/Library/Android/sdk/platform-tools/adb devices
```

## First Run

1. Add an SSH server with host, port, username, and password or private key.
2. Add a project pointing at that server and the remote project directory.
3. Make sure `tmux` is installed on the remote machine.
4. Open the project. A tab/session should appear.
5. Use the keybar for common terminal keys, upload, and download.
6. Use the larger text input when talking to AI tools like a person instead of
   feeding the shell one nervous line at a time.

## License

TerminalHub is released under the GNU General Public License v3.0 only. See
[LICENSE](LICENSE).

The app includes or adapts terminal components from the Termux project. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and license
notes.
