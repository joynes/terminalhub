# Agent Instructions

## Workflow

- Always attempt tasks autonomously before asking the user to do anything manually.
- After code fixes or feature changes, build the app and upload the latest release APK to `gdrive:apks/` unless the user says not to.
- After each completed code change set, create a git commit unless the user says not to.
- Include a timestamp in each user-facing answer when practical.

## Project-Specific

- Upload the release version only.
