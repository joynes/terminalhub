# Agent Instructions

## Workflow

- Always attempt tasks autonomously before asking the user to do anything manually.
- After code fixes or feature changes, build the signed production release APK and AAB. Upload the APK to `gdrive:apks/` and publish the same version on Google Play in both Internal testing and Production, unless the user says otherwise. This is standing authorization from Johannes (2026-11-01); do not request publication permission again for routine releases.
- Verify each Google Play track and the Publishing overview after submission. Distinguish drafts, changes awaiting submission, review, and releases actually available to users. Report any Google review or policy blocker accurately.
- After each completed code change set, create a git commit unless the user says not to.
- Include a timestamp in each user-facing answer when practical.

## Project-Specific

- Upload the release version only.
