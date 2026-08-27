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

## SSH host-key trust

Current TerminalHub builds use explicit, OpenSSH-style host identity trust for
terminal SSH, connection tests, SCP upload/download, and public-key installation.
On first contact with a host and port, the connection is rejected until the user
compares and explicitly trusts the displayed algorithm and `SHA256:` fingerprint.

The trusted key is app-private and shared by profiles using the same normalized
host and port; usernames do not create separate trust. A changed algorithm or
key is blocked and never overwrites the trusted record. Verify a legitimate
server replacement outside TerminalHub, then use **Forget trusted key** in the
server editor and perform a fresh Test SSH verification. Treat an unexpected
change as a possible interception attempt.

Older production builds which predate the host-key-trust release remain
permissive. Check the installed release notes before relying on this protection.

## Local data boundary

Server profiles, credentials, private keys, terminal history, logs, and project
workspace state can be stored on the Android device. Project repositories and
remote processes remain on the user's server. Configuration exports exclude
passwords, private keys, and trusted host-key records, but can contain hostnames,
usernames, paths, scripts, and repository URLs, so exports should still be
treated as sensitive.

See the [privacy policy](https://joynes.github.io/terminalhub/privacy-policy.html)
and [complete documentation](https://joynes.github.io/terminalhub/) for the
current product model and limitations.
