# TerminalHub

**Start projects on your servers from Android. Keep each one in a persistent tab.**

TerminalHub is an open-source Android workspace for people who work in several
terminal projects at once. Add one or more SSH servers, create or clone projects
from the phone, and open each project in its own tab. TerminalHub remembers the
tabs and their order, while tmux keeps remote work available after disconnects,
app restarts, and network changes.

[Get TerminalHub on Google Play](https://play.google.com/store/apps/details?id=se.joynes.terminalhub)
· [View the open-source code](https://github.com/joynes/terminalhub)

The phone is where you configure, start, and switch projects. The shell,
repository, and long-running processes live on a Linux or macOS workstation,
home server, or VPS that you control. The main workflow therefore requires at
least one machine that the phone can reach over SSH.

## The Core Workflow

1. Add an SSH server with a hostname, port, username, and password or private key.
2. Add a project and choose which server should run it.
3. Optionally provide a Git URL, setup script, and command to start with the project.
4. Open the project. TerminalHub creates its remote folder or clones the repository,
   starts or attaches to its tmux session, and adds it to the tab bar.
5. Add more projects on the same server or other servers and switch between them
   without leaving the terminal workspace.
6. Reopen the app later. Saved tabs, tab order, project profiles, and tmux-backed
   sessions are restored.

![TerminalHub demo](docs/assets/terminalhub-demo.gif)

## Why TerminalHub

- **Projects are first-class.** A project combines a server, remote directory,
  optional Git repository, tmux session, startup script, and terminal tab.
- **Several servers, one tab bar.** Keep an API on a VPS, a mobile build on a
  workstation, and homelab operations open side by side.
- **Tabs persist.** TerminalHub stores which project tabs are open and their
  order. Closing and reopening the app does not mean rebuilding the workspace.
- **tmux keeps work alive.** Reattach to the remote session after Android process
  restarts, SSH interruptions, or a change between Wi-Fi and mobile data.
- **Start new work from the phone.** Create a remote project folder or clone a Git
  repository without first opening a laptop.
- **Upload files in context.** Send Android files directly into the active remote
  project.
- **A terminal designed for touch.** Use a configurable keybar for Ctrl, Alt,
  Esc, arrows, and common actions, plus a larger multiline input with per-project
  history.
- **Bring any terminal workflow.** Run development servers, builds, logs, editors,
  administration tools, or custom startup commands.
- **AI is one useful workflow, not the product boundary.** Codex, Claude Code,
  Gemini, and other terminal agents can each run in separate project tabs, making
  it easier to follow several AI sessions from one phone.

Other useful parts include terminal search, app and session logs, SSH
diagnostics, password and private-key authentication, export/import of workspace
configuration, and an optional biometric gate on startup.

## Relationship To Termux

TerminalHub uses adapted terminal rendering and input components from the
open-source Termux project. That gives its embedded terminal a proven Android
foundation, while TerminalHub adds its own SSH server, project, persistent-tab,
tmux recovery, and file-transfer workflows.

TerminalHub is an independent project and is not affiliated with or endorsed by
Termux. It is not a full Termux environment: the primary TerminalHub workflow
runs projects on machines reached over SSH. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and licenses.

## Requirements

For normal remote-project use:

- An Android phone or tablet.
- At least one Linux or macOS workstation, home server, VPS, or lab machine.
- An SSH server and a user account you can log into.
- `tmux` on the remote machine for persistent sessions.
- Network reachability from Android to the server. A private VPN such as
  Tailscale is recommended instead of exposing SSH directly to the internet.
- Optional: Git and any project-specific tools you want to run remotely.

AI terminal clients such as `codex`, `claude`, or `gemini` are entirely optional
and, when used, are installed on your server rather than supplied by TerminalHub.

For the Android build machine:

- macOS, Linux, or Windows with a shell.
- Git.
- Android Studio, or the Android SDK command-line tools.
- JDK 17 or newer. Android Studio's bundled JDK works.
- Android SDK Platform 36.
- Android SDK Build Tools installed by Android Studio/SDK Manager.

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

There is also a diagnostic flavor for repeatable local testing without
overwriting the installed production app:

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

Install the release APK on a connected emulator or device:

```sh
adb devices
adb install -r app/build/outputs/apk/production/release/app-production-release.apk
```

If `adb` is not on your `PATH`, use the SDK path directly, for example:

```sh
$HOME/Library/Android/sdk/platform-tools/adb devices
```

## First Run

1. Prepare a reachable SSH server and install `tmux` on it.
2. Add the server in TerminalHub.
3. Add a project, choose the server, and optionally enter a Git URL.
4. Open the project to create or clone it and place it in the tab bar.
5. Add projects on any other configured servers.
6. Switch tabs to work across projects, and use file upload or multiline input
   when needed.
7. Restart the app or reconnect later to verify that tabs and tmux sessions return.

## License

TerminalHub is released under the GNU General Public License v3.0 only. See
[LICENSE](LICENSE).

Contributions, bug reports, and independently built variants are welcome. The
complete Android source is public so users can inspect how SSH sessions,
credentials, terminal data, and persisted project state are handled.

TerminalHub includes or adapts terminal components from the Termux project. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and license
notes.
