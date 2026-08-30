# TerminalHub implementation handoff for AI agents

Status: ready for implementation

Last audited: 2026-08-27

Public repository: https://github.com/joynes/terminalhub

Local clone used for this handoff:
`/Users/joka/terminalhub/real-term/terminalhub`

## Mission

Prepare TerminalHub for advanced SSH/tmux users by completing two product work
streams in this order:

1. add explicit SSH host-key trust and changed-key rejection;
2. restore optional background SSH continuity with a user-started,
   policy-compliant Android foreground service.

Do not combine both work streams into one large change. Each must have focused
tests, documentation, a commit, and its own release evidence. Host-key trust is
the first priority because the current behavior is a security and credibility
blocker. Background SSH is second because it introduces Google Play policy risk.

## Mandatory instructions and boundaries

Before changing anything:

1. Read `/Users/joka/terminalhub/real-term/terminalhub/AGENTS.md` completely.
2. Fetch `origin/main` and inspect divergence. Other agents may push while work
   is in progress; preserve and rebase onto their work instead of overwriting it.
3. Inspect current source. This handoff describes the audited baseline, not a
   substitute for checking the latest code.
4. Never commit credentials, SSH keys, private endpoints, signing files, Play
   credentials, tester identities, or raw logs.
5. Keep AI as an optional use case. The product is an Android workspace for
   persistent project tabs on user-controlled SSH servers.
6. Preserve tmux as the durable remote-state mechanism. A live Android SSH socket
   is useful but must never be described as guaranteed persistence.
7. After code changes, run proportional tests, build the signed production
   release, upload the release APK to `gdrive:apks/`, and commit the completed
   change as required by `AGENTS.md`.

The private operational tracker is in the adjacent private repository:
`/Users/joka/terminalhub/real-term/terminalhub-marketing/LAUNCH_PROGRESS.md`.
Update it only with non-sensitive aggregate status and commit that repository
separately.

## Current audited state

- Android package: `se.joynes.terminalhub`.
- Minimum API: 24.
- Target SDK: 36.
- Latest verified Google Play release in the tracker: `251 (1.251)`.
- Open tabs, tab order, active tab, and project profiles persist on Android.
- tmux keeps remote processes alive; the app reconnects and reattaches.
- The current production architecture has no Android foreground service.
- SSH keepalive is opportunistic while Android keeps the process alive.
- SSH, SCP upload, SCP download, and public-key installation currently accept
  whatever server host key is presented.
- Google Play rejected the previous automatic `dataSync` foreground-service
  implementation. It was removed in commit `fd014d6`.

Do not change documentation claims such as `host_key_pinning: false` or
`background_ssh_guaranteed: false` until the corresponding behavior is actually
implemented, tested, and released.

---

## Work stream A — explicit SSH host-key trust

### Outcome

On first contact with a host and port, TerminalHub shows the server host-key
algorithm and SHA-256 fingerprint and requires an explicit user decision. After
trust, every SSH/SCP path accepts only the stored key. A changed key is rejected
and never silently replaces the trusted key. The user can deliberately forget a
trusted key from the server editor and then verify the replacement.

### Current insecure entry points

At the audited baseline, permissive `ExtendedServerHostKeyVerifier` instances
exist in:

- `app/src/main/java/se/joynes/terminalhub/data/ssh/SshConnection.kt`
- `app/src/main/java/se/joynes/terminalhub/data/ssh/ScpUploader.kt`
- `app/src/main/java/se/joynes/terminalhub/data/ssh/ScpDownloader.kt`
- `app/src/main/java/se/joynes/terminalhub/data/ssh/SshPublicKeyInstaller.kt`

Search the latest tree for every `Connection.connect`,
`ExtendedServerHostKeyVerifier`, and verifier that returns `true`. There must be
no unreviewed permissive connection path after implementation.

Relevant UI and dependency paths include:

- `app/src/main/java/se/joynes/terminalhub/ui/screen/servers/AddEditServerViewModel.kt`
- `app/src/main/java/se/joynes/terminalhub/ui/screen/servers/AddEditServerScreen.kt`
- `app/src/main/java/se/joynes/terminalhub/data/security/`
- `app/src/main/java/se/joynes/terminalhub/di/SshModule.kt`
- `app/src/main/java/se/joynes/terminalhub/data/repository/ServerRepository.kt`

### Required trust model

Use endpoint identity, not server-profile identity:

- normalized hostname or IP address;
- SSH port;
- host-key algorithm;
- exact host-key bytes;
- SHA-256 fingerprint for display;
- first-trusted timestamp and optional last-seen timestamp.

Do not include username in the trust key. Several profiles may legitimately use
the same host and port. Normalize hostname casing, but do not perform speculative
DNS canonicalization that merges distinct configured endpoints.

The OpenSSH-style displayed fingerprint should be computed from the raw host-key
bytes using SHA-256 and standard Base64 without trailing padding:

```text
SHA256:<base64-without-padding>
```

Host keys are public identity material, not authentication secrets, but the
store must still be app-private and excluded from logs and exports unless an
export format is deliberately designed later.

### Suggested architecture

Introduce one application-scoped source of truth, for example:

- `KnownHostRepository` — app-private persistence and endpoint lookup;
- `KnownHost` — trusted endpoint, algorithm, key bytes, fingerprint, timestamps;
- `HostKeyChallenge` — unknown or changed candidate plus old/new fingerprints;
- `TerminalHubHostKeyVerifier` — the only Trilead verifier used by all raw SSH
  and SCP connections.

The exact class names may change, but do not duplicate verifier logic across the
four clients.

The Trilead verifier callback is synchronous. It must not display Compose UI or
block waiting for UI input. For an unknown key:

1. capture a sanitized `HostKeyChallenge`;
2. reject that connection attempt;
3. surface a typed result to the ViewModel/UI;
4. show the algorithm and fingerprint;
5. let the user trust or cancel;
6. persist only after explicit trust; and
7. retry the original action explicitly.

For a changed key, show both the trusted and presented fingerprints, use strong
warning language, and provide no one-tap automatic overwrite. Require the user
to choose **Forget trusted key** in server settings and then perform a fresh
first-connection verification.

### Required UI behavior

- A new server can be saved before trust, but Test SSH or first connection must
  trigger the fingerprint decision.
- Server editing shows whether the configured host/port has a trusted key and
  displays its algorithm and fingerprint.
- **Forget trusted key** requires confirmation and affects the host/port endpoint,
  potentially shared by several server profiles.
- Public-key installation must not bypass trust. If it is the first network
  action, it must use the same challenge flow before sending the password.
- Upload/download must refuse an unknown or changed host key. Do not fall back to
  permissive verification because a terminal session was previously active.
- Errors should explain the safe next action without exposing host-key bytes or
  private server data in logs.

### Host-key acceptance rules

| State | Required behavior |
| --- | --- |
| No trusted key | Reject attempt, show algorithm/fingerprint, require explicit trust, retry |
| Exact algorithm/key match | Connect normally |
| Same endpoint, different key | Reject, show changed-key warning, never overwrite automatically |
| Same host, different port | Treat as a separate endpoint |
| Same endpoint, different username | Reuse endpoint trust |
| User forgets trusted key | Remove only after confirmation; next attempt becomes first trust |
| Corrupt stored record | Fail closed, report a recoverable local trust-store error |

### Required tests

Unit-test at minimum:

- fingerprint formatting with known bytes;
- hostname normalization;
- first contact produces a challenge and rejects the attempt;
- explicit trust stores the exact algorithm and bytes;
- matching reconnect succeeds;
- changed algorithm or bytes is rejected;
- changed key does not mutate the trusted record;
- host/port isolation;
- username independence;
- forget/reset behavior;
- corrupt-record failure behavior;
- all SSH/SCP clients receive the shared verifier dependency.

Add UI/ViewModel tests for first trust, cancel, changed-key warning, forget
confirmation, and retry. Test terminal connect, connection test, upload,
download, and public-key installation on an emulator. Test at least one real SSH
server where the host key can be deliberately replaced in a controlled test
environment.

### Documentation changes after completion

Only after passing tests:

- update `SECURITY.md` and remove the current permissive-host-key warning;
- update `docs/index.html`, `docs/llms.txt`, and `docs/llms-full.txt`;
- change `docs/docs-index.json` field `host_key_pinning` to `true`;
- document first trust and changed-key recovery for users;
- add release notes and update the private launch tracker.

### Definition of done

- No production `ExtendedServerHostKeyVerifier` unconditionally returns `true`.
- Every SSH and SCP connection path uses one shared trust policy.
- Unknown and changed keys fail closed.
- First trust is explicit and fingerprint-visible.
- Changed keys cannot be accepted accidentally.
- Automated tests and controlled end-to-end tests pass.
- Signed release APK/AAB is produced according to repository instructions.
- Documentation matches the verified release.

---

## Work stream B — policy-compliant background SSH

### Prerequisite

Complete work stream A first. Before writing code, read the full plan:

`docs/POLICY_COMPLIANT_BACKGROUND_SSH_PLAN.md`

Recheck current official Android and Google Play foreground-service policy on
the implementation date. `specialUse` is a proposed type subject to Play review,
not a guarantee of acceptance.

### Outcome

Users can explicitly enable **Keep SSH active in background** for current SSH
project tabs. Android immediately shows an accurate ongoing notification. The
user can stop the mode from the app or notification. When the mode is off or the
system kills it, saved tabs and tmux reconnection continue to work exactly as
they do today.

### Likely source areas

- new `app/src/main/java/se/joynes/terminalhub/service/BackgroundSshService.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/se/joynes/terminalhub/MainActivity.kt`
- `app/src/main/java/se/joynes/terminalhub/domain/TerminalSessionManager.kt`
- `app/src/main/java/se/joynes/terminalhub/data/ssh/SshManager.kt`
- `app/src/main/java/se/joynes/terminalhub/data/runtime/AppRuntimeRepository.kt`
- `app/src/main/java/se/joynes/terminalhub/data/settings/AppSettingsRepository.kt`
- `app/src/main/java/se/joynes/terminalhub/ui/screen/settings/SettingsScreen.kt`
- `app/src/main/java/se/joynes/terminalhub/ui/screen/settings/SettingsViewModel.kt`

Inspect commit `fd014d6` for the removed implementation, but do not restore it
unchanged. Its automatic startup and `dataSync` declaration are specifically
what this design must avoid.

### Non-negotiable behavior

- The mode defaults to off.
- Start only after an obvious user action while the app is visible.
- Never start merely because a tab connects, restores, or reconnects.
- Never start from boot, a background receiver, or silent process recovery.
- Show an immediate ongoing notification with session count, open-app action,
  and prominent Stop action.
- Stopping closes live transports but does not kill tmux unless the user performs
  a separate tmux-kill action.
- Prefer `START_NOT_STICKY` for the first policy submission.
- Do not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the first version.
- An optional general battery-settings link may open Android settings after an
  explicit tap without requesting direct exemption.
- Notification text must not contain terminal contents, hostnames, usernames,
  private project names, or credentials.
- The service observes existing session managers; it does not create a second
  session registry.

### Play-specific implementation boundary

Do not call persistent interactive SSH `dataSync`. The proposed manifest type is
`specialUse` with a precise service-level subtype explaining user-started,
interactive SSH continuity. Confirm this against current policy before coding.

The production submission needs:

- exact manifest permissions and service type;
- completed Foreground Service declaration;
- explanation of why interruption drops the live transport but not tmux work;
- an unlisted video showing enable, notification, background use, return, and
  Stop;
- Internal testing before production;
- staged production rollout after approval; and
- no unrelated policy-sensitive changes in the same review submission.

### Required tests and done criteria

Use the full matrix in `POLICY_COMPLIANT_BACKGROUND_SSH_PLAN.md`. At minimum:

- connection alone does not start the service;
- explicit enable does;
- notification appears immediately and reports a sanitized session count;
- Stop works from notification and app;
- final SSH tab leaves the explicitly enabled service visible in its zero-session idle state;
- denial of notification permission leaves the feature off with a clear message;
- process recovery does not silently restart it;
- tmux fallback reconnects without duplicate startup commands;
- Android 13–16 permission and service-start behavior is tested;
- screen-off, Doze, Wi-Fi/mobile transition, task swipe, and process death are
  tested on emulator and physical phone;
- Play approves the exact declared behavior.

Do not update public copy to promise background continuity before Play approval
and production verification.

---

## Work stream C — distribution and communication artifacts

Complete after the security work and before broad promotion:

1. Create a signed GitHub Release with tag, accurate notes, release APK, and
   SHA-256 checksum tied to the exact source commit.
2. Audit F-Droid eligibility, dependencies, reproducibility, and metadata; submit
   or record exact blockers.
3. Record a 30–45 second real emulator/device workflow video showing:
   server/project creation, several server tabs, app return, tmux resume, mobile
   link opening, and file transfer. If background SSH is approved, show its
   explicit enable/notification/stop flow in the policy video separately.
4. Publish two technical guides:
   - managing multiple tmux projects from Android;
   - starting and restoring remote Git projects from a phone.
5. Recruit 10–20 SSH/tmux users and record only privacy-safe aggregate results.
6. Fix observed onboarding blockers before Hacker News or broad community posts.

Do not add an analytics SDK solely for launch measurement. Begin with aggregate
Google Play acquisition data, GitHub traffic/releases, UTM-tagged links, and
direct tester feedback.

## Build and verification commands

Use the installed Android Studio JBR and SDK when needed:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/joka/Library/Android/sdk"
```

Run unit tests across the app and terminal modules:

```sh
./gradlew \
  :app:testProductionDebugUnitTest \
  :terminal-emulator:testDebugUnitTest \
  :terminal-view:testDebugUnitTest
```

Build production artifacts using the repository's configured signing process:

```sh
./gradlew assembleProductionRelease bundleProductionRelease
```

Never add signing credentials to Git. Follow `AGENTS.md` and the private release
workflow in the adjacent marketing repository. Upload the release APK only to
`gdrive:apks/` after a code change has been completed and verified.

Also run:

- `git diff --check`;
- relevant Android Lint tasks;
- manifest inspection for final permissions/service types;
- emulator cold-start and primary workflow smoke tests;
- physical-phone tests where specified; and
- screenshot/video inspection for private information.

## Commit and release discipline

- Preserve unrelated user or agent changes.
- Use focused commits: host-key trust, background SSH, documentation/release
  updates should remain separately reviewable.
- Do not mark tracker items complete based only on compilation.
- Record exact test commands, artifact hashes, commit IDs, Play state, and known
  limitations.
- If a policy-sensitive approach is rejected, record the reviewer's exact reason
  and revert to the last approved behavior rather than disguising the same work
  under an inaccurate permission or service type.

## Final completion checklist

- [ ] Host-key trust implementation passes all required tests.
- [ ] Host-key trust is verified on emulator and controlled physical/server setup.
- [ ] Security documentation and machine-readable boundary are updated.
- [ ] A security release is built, archived, committed, and published as intended.
- [ ] Background SSH implementation starts only from explicit user action.
- [ ] Notification, Stop, tmux fallback, and lifecycle tests pass.
- [ ] Foreground-service Play declaration and demonstration video are complete.
- [ ] Internal test succeeds and Play approves the declared behavior.
- [ ] Staged production rollout is verified before broad promotion.
- [ ] GitHub Release, F-Droid audit, workflow video, and technical guides exist.
- [ ] Private `LAUNCH_PROGRESS.md` contains evidence and remaining work.
