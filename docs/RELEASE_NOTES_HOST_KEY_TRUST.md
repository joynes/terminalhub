# SSH host-key trust release notes

TerminalHub now verifies server identity consistently across terminal SSH,
Test SSH, SCP upload, SCP download, and public-key installation.

- A first connection is blocked until the user verifies and explicitly trusts
  the displayed host-key algorithm and OpenSSH-style SHA-256 fingerprint.
- Trust belongs to the normalized host and port, so profiles with different
  usernames share the same endpoint identity.
- A changed key or algorithm is blocked with both trusted and presented
  fingerprints visible. It is never accepted or stored automatically.
- A deliberate **Forget trusted key** action requires confirmation. The next
  connection becomes a new first-contact verification.
- Corrupt local trust records fail closed and can be reset from the server
  editor.
- Trusted host keys remain app-private and are not included in configuration
  exports or logs.

For a legitimate server-key replacement, verify the new fingerprint directly on
the server first. Then edit the server in TerminalHub, choose **Forget trusted
key**, confirm the warning, run **Test SSH**, compare the new fingerprint, and
choose **Trust and retry**.
