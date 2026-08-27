# TerminalHub roadmap

This roadmap communicates direction rather than release dates. Priorities can
change after user feedback, security review, or Android platform changes.

## Now — trust and launch readiness

- Add explicit SSH host-key trust and changed-key rejection across terminal,
  upload, download, connection test, and public-key installation flows.
- Design and validate the optional user-started background SSH mode described in
  [docs/POLICY_COMPLIANT_BACKGROUND_SSH_PLAN.md](docs/POLICY_COMPLIANT_BACKGROUND_SSH_PLAN.md).
- Provide a clear recovery flow when a server is intentionally rebuilt or its
  SSH host key changes.
- Improve first-server and first-project guidance using feedback from real SSH
  and tmux users.
- Publish auditable GitHub Releases with notes and checksums.
- Keep documentation, Play copy, screenshots, and implemented behavior aligned.

## Next — daily remote-project workflow

- Reduce friction when reopening several disconnected projects.
- Improve remote file workflows while preserving clear file-transfer scope.
- Expand diagnostics for SSH, tmux, terminal rendering, and Android input issues.
- Add regression coverage for real-world shell, server, and device variations.

## Later — ecosystem and community

- Evaluate F-Droid inclusion and reproducible-build requirements.
- Add community-requested workflows that strengthen the multi-server project-tab
  model.
- Improve localization and accessibility.
- Document optional AI-terminal-agent workflows without making AI a product
  dependency.

## Product boundaries

TerminalHub will remain centered on user-controlled servers, persistent project
tabs, and mobile terminal interaction. It does not supply compute, host projects,
bundle an AI model, or claim affiliation with Termux.

Discuss ideas in [GitHub Discussions](https://github.com/joynes/terminalhub/discussions)
or use the structured feature-request form after checking existing proposals.
