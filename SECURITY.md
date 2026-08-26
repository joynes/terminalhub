# Security policy

TerminalHub is an Android SSH client that connects directly to servers selected
and controlled by the user. It does not provide a relay, hosted shell, server,
VPN, or AI service.

## Supported versions

Security fixes are applied to the latest production release and the current
`main` branch. Older releases are not maintained separately.

| Version | Supported |
| --- | --- |
| Latest Google Play production release | Yes |
| Current `main` branch | Yes |
| Older releases | No |

## Report a vulnerability

Use GitHub's private vulnerability reporting for this repository. Do not open a
public issue when a report contains an exploit, credentials, private server
details, or other information that would put users at risk.

Include the affected version, Android version, reproduction steps, expected and
observed behavior, and the smallest sanitized log excerpt needed to understand
the problem. Never include passwords, private keys, passphrases, tokens, or
unredacted server data.

## Current SSH trust limitation

Production release 1.251 and the current source at the time this policy was
written accept the host key presented by an SSH or SCP server without comparing
it with a previously trusted fingerprint. That means the app does not currently
detect a server-key change and must not be treated as providing OpenSSH-style
known-host protection.

Until host-key pinning is shipped and verified:

- prefer a trusted private network or a private VPN;
- verify that the hostname or IP address reaches the intended machine through
  an independent control;
- avoid using TerminalHub for high-sensitivity infrastructure over an untrusted
  network; and
- keep the app, Android device, remote SSH server, and authentication keys up to
  date.

This limitation is tracked as a release-readiness blocker. Documentation and
marketing must not claim pinned host identity until the implementation and its
key-change recovery flow have been tested.

## Local data boundary

Server profiles, credentials, private keys, terminal history, logs, and project
workspace state can be stored on the Android device. Project repositories and
remote processes remain on the user's server. Configuration exports exclude
passwords and private keys but can contain hostnames, usernames, paths, scripts,
and repository URLs, so exports should still be treated as sensitive.

See the [privacy policy](https://joynes.github.io/terminalhub/privacy-policy.html)
and [complete documentation](https://joynes.github.io/terminalhub/) for the
current product model and limitations.
