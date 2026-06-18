# Termux Terminal Integration In AITerminal

Timestamp: 2026-03-23 19:32:19 CET

AITerminal integrates the Termux terminal library as an embedded terminal widget layer, not as the full Termux app/runtime.

## Dependency Layer

The app pulls in the two published Termux modules from JitPack in `gradle/libs.versions.toml` and wires them into the app in `app/build.gradle.kts`:

- `terminal-emulator`
- `terminal-view`

That means AITerminal reuses:

- `com.termux.terminal.TerminalSession`
- `com.termux.view.TerminalView`
- `com.termux.view.TerminalViewClient`
- `com.termux.terminal.TerminalSessionClient`

It does not include Termux's `TermuxActivity`, `TermuxService`, `termux-shared`, bootstrap/runtime, package manager, or command API.

## Session Model

The core integration lives in `app/src/main/java/se/joynes/aiterminalhub/domain/TerminalSessionManager.kt`.

What it does:

- Creates a `TerminalSession` per SSH session using a dummy local subprocess, `/system/bin/cat`.
- Uses that `TerminalSession` only as the emulator state machine and screen buffer.
- Waits for `TerminalView` to initialize the emulator, then appends remote SSH bytes directly into `terminalSession.emulator`.
- Sends user keystrokes back out through the SSH connection, not to the dummy subprocess.

This is the key design choice: Termux's terminal engine is being used as a renderer/emulator on top of the app's SSH transport.

## UI Embedding

The actual widget embedding is in:

- `app/src/main/java/se/joynes/aiterminalhub/ui/screen/terminal/TerminalScreen.kt`
- `app/src/main/java/se/joynes/aiterminalhub/ui/screen/sessions/SessionHostScreen.kt`

The flow is:

- Compose uses `AndroidView` to host `TerminalView`.
- A `TerminalViewClientImpl` is attached with `setTerminalViewClient(...)`.
- The active `TerminalSession` is attached with `attachSession(sess)`.
- The view is focused and the soft keyboard is shown manually after attachment.

So the Termux widget is embedded as a normal Android view inside Compose, with Compose managing focus, tabs, overlays, and keyboard toggling around it.

## Input Handling

The custom input bridge is in `app/src/main/java/se/joynes/aiterminalhub/ui/screen/terminal/TerminalViewClientImpl.kt`.

What it does:

- Implements `TerminalViewClient`.
- Reads CTRL/ALT/SHIFT state from the Compose-side modifier state.
- Intercepts typed characters in `onCodePoint(...)` and sends bytes to SSH.
- Intercepts special keys in `onKeyDown(...)` and translates them to ANSI escape sequences.
- Uses tap events only to trigger keyboard showing.

The extra keys UI is not Termux's stock extra-keys view. AITerminal has its own Compose bar in `app/src/main/java/se/joynes/aiterminalhub/ui/screen/terminal/SpecialKeyBar.kt`, which emits escape sequences and modifier state into that same bridge.

## Session Client Hooks

`TerminalSessionClientImpl` is a minimal adapter in `app/src/main/java/se/joynes/aiterminalhub/data/ssh/TerminalSessionClientImpl.kt`.

It mostly no-ops the Termux callbacks, except:

- copy-to-clipboard support

So AITerminal uses only the callback surface it needs.

## Compared With Real Termux

Compared to upstream Termux, this integration is much thinner.

Same idea:

- Use `TerminalView` + `TerminalSession`
- Provide a `TerminalViewClient`
- Provide a `TerminalSessionClient`

Different from Termux:

- No `TermuxActivity` / `TermuxService`
- No local shell/bootstrap environment
- No Termux package/runtime management
- No upstream extra-keys system
- No Termux settings/property system
- No upstream URL handling, copy-mode behavior, pinch-to-font-size logic, or full keyboard shortcut layer

## Summary

AITerminal integrated Termux correctly as a terminal widget/emulator library, then wrapped it with its own SSH transport, Compose UI, tabs, and special-key UX. It is not a fork of the Termux app; it is a custom SSH app using Termux's terminal engine as the rendering/input core.
