# TerminalHub documentation site

The files in this directory are published at
<https://joynes.github.io/terminalhub/> through GitHub Pages.

## Audience and structure

- `index.html` is the canonical human documentation. Stable section IDs are part
  of its public interface and should not be changed without redirects.
- `llms.txt` is the short AI-agent routing file.
- `llms-full.txt` is consolidated plain-text product and support context.
- `docs-index.json` is the machine-readable topic and source-code map.
- `AI_IMPLEMENTATION_HANDOFF.md` is the ordered, testable launch-readiness handoff
  for another AI agent or maintainer.
- `POLICY_COMPLIANT_BACKGROUND_SSH_PLAN.md` contains the Android and Google Play
  design for restoring optional background SSH continuity.
- `RELEASE_NOTES_HOST_KEY_TRUST.md` documents first-contact fingerprint trust
  and changed-key recovery.
- `privacy-policy.html` is the web version of `privacy-policy.md`; keep their
  claims synchronized.
- `guides/` contains task-focused, search-friendly workflows with HowTo metadata
  and stable routes for people and AI agents.
- `assets/screenshots/` contains real Android emulator captures used in Google
  Play. Do not replace them with mockups without labeling them.

## Update contract

Whenever product behavior changes:

1. Update the relevant section in `index.html`.
2. Update any affected canonical fact in `llms.txt`.
3. Update `llms-full.txt` when the behavior affects product, support, security,
   build, or architecture context.
4. Update `docs-index.json` if a route, keyword, boundary, or source entry point
   changes.
5. Update the documentation snapshot date.
6. Validate HTML, internal links, JSON, responsive layout, and the published URL.

AI-facing text must distinguish verified behavior, limitations, and non-goals.
Do not infer a supported feature solely because dormant or experimental code
exists in the repository.

## Local preview

Run a static HTTP server from this directory:

```sh
cd docs
python3 -m http.server 8080
```

Then open <http://127.0.0.1:8080/>. The site has no external JavaScript or CSS
dependencies and should remain useful with JavaScript disabled.
