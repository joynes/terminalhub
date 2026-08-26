# Contributing to TerminalHub

Bug reports, documentation improvements, tests, and focused code changes are
welcome. TerminalHub is GPL-3.0-only and uses adapted Termux terminal components.

## Before opening an issue

1. Read the [documentation](https://joynes.github.io/terminalhub/) and search
   existing issues and discussions.
2. Confirm the behavior on the latest production release or current `main`.
3. Remove credentials, private server details, tokens, and sensitive logs.
4. Use private vulnerability reporting for security issues rather than a public
   issue.

Use GitHub Discussions for setup questions, workflow ideas, and early feature
proposals. Use Issues for reproducible defects and accepted implementation work.

## Build and test

The project requires JDK 17 or newer and Android SDK Platform 36. Android
Studio's bundled JDK is supported.

```sh
./gradlew assembleProductionDebug
./gradlew app:testProductionDebugUnitTest \
  terminal-emulator:testDebugUnitTest \
  terminal-view:testDebugUnitTest
```

The `diagnostic` flavor can be installed beside production and includes
deterministic preview states used by the screenshot workflow:

```sh
./gradlew assembleDiagnosticDebug
```

## Pull requests

- Keep each change focused and explain the user-visible result.
- Add or update tests for behavior changes.
- Update README and `docs/` when user-visible behavior changes.
- Preserve the distinction between Android workspace state, SSH transport state,
  and server-side tmux persistence.
- Verify marketing-sensitive claims such as reconnect behavior, file transfer,
  and terminal compatibility against the production flavor.
- Do not commit signing files, credentials, private endpoints, personal data, or
  generated local configuration.

For terminal rendering or input changes, identify whether the affected code is
TerminalHub-specific or adapted from Termux and preserve applicable attribution.
