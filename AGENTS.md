# Agent Instructions

## Workflow

- Always attempt tasks autonomously before asking the user to do anything manually.
- After code fixes or feature changes, build the signed production release APK and AAB. Upload the APK to `gdrive:apks/` and publish the same version on Google Play in both Internal testing and Production, unless the user says otherwise. This is standing authorization from Johannes (2026-11-01); do not request publication permission again for routine releases.
- Verify each Google Play track and the Publishing overview after submission. Distinguish drafts, changes awaiting submission, review, and releases actually available to users. Report any Google review or policy blocker accurately.
- Prefer the Google Play Developer API using the existing Application Default Credentials before depending on a desktop browser. The ADC at `/Users/joka/.config/gcloud/application_default_credentials.json` already includes the androidpublisher scope; obtain the token via `gcloud auth application-default print-access-token` in process memory and supply `x-goog-user-project` from its configured quota project. Never print tokens or credential contents.
- The local API release helper is `/Users/joka/terminalhub/terminal-hub/publish-terminalhub-play.py` (`inspect`, `upload`, `publish`, `verify`, `status`, with `--version <code>`). Confirm the artifact version and server bundle checksum before publishing. An absent macOS desktop session alone is not a Play publishing blocker.
- Verify actual availability through `applications.tracks.releases.list` and its `releaseLifecycleState`. An edit track's `completed` status describes rollout configuration and must not by itself be reported as published.
- After each completed code change set, create a git commit unless the user says not to.
- Include a timestamp in each user-facing answer when practical.

## Project-Specific

- Upload the release version only.
