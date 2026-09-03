# Policy-compliant background SSH plan

Status: implemented in code; Google Play declaration and Internal-track validation pending

Last reviewed: 2026-08-27

Implementation notes (2026-08-27):

- Added an off-by-default, user-started setting with an explanatory confirmation.
- Added a `specialUse` `START_NOT_STICKY` foreground service and ongoing Stop notification.
- Added notification permission handling and live SSH tab counts. Once explicitly enabled, the mode remains active with a zero-session notification until the user stops it or Android ends the service.
- Stop closes SSH transports without issuing `tmux kill-session`.
- Process startup ends the running mode but preserves the user's preference. It
  cannot silently restart; the user must tap Start in the visible app to resume it.
- Added reducer and notification-count tests for the required state transitions.

## Objective

Restore the option to keep active SSH connections alive while TerminalHub is in
the background, without returning to the foreground-service design rejected by
Google Play in August 2026.

The feature must be explicitly started by the user, continuously visible while
running, easy to stop, accurately declared, and tested against current Android
and Google Play requirements. tmux remains the durable fallback when Android or
the network terminates a connection.

## Why the previous implementation was rejected

The removed `SshSessionService` started automatically when an SSH session was
registered and declared itself as a `dataSync` foreground service. Its real job
was to keep interactive SSH sessions alive indefinitely, not to complete a
finite upload, download, backup, import, or export operation.

Google Play reported that the foreground-service permission was missing or
incorrectly declared and that the functionality was not sufficiently initiated
by or perceptible to the user. Release 1.251 therefore removed the service,
notification permission, direct battery-exemption request, and related settings
before the app was approved.

## Required product behavior

### User-controlled mode

Add an opt-in control named **Keep SSH active in background**. It must default to
off and must not start merely because a project tab connects.

The first start should explain:

- Android will show an ongoing TerminalHub notification;
- active SSH connections can consume battery and mobile data;
- the user can stop the mode from the notification or the app;
- Android or the network can still terminate a connection; and
- tmux keeps remote work alive so TerminalHub can reconnect safely.

Only start the foreground service immediately after a visible user action while
the app is in the foreground. Do not start it from app launch, tab restoration,
background receivers, boot completion, or automatic reconnect code.

### Persistent notification

While the mode is active, show a non-dismissible foreground-service notification
with:

- title such as `TerminalHub is keeping SSH active`;
- the number of active SSH project sessions;
- an action that opens TerminalHub;
- a prominent **Stop** action; and
- wording that does not claim that Android can guarantee continuity.

Stopping from either surface must stop keepalives, close current SSH transports,
remove the notification, and leave server-side tmux sessions running unless the
user separately chooses to kill them.

### Session behavior

- When the mode is off, retain the current lifecycle: Android may stop the app;
  saved tabs reconnect and reattach to tmux when TerminalHub returns.
- When the mode is on, the service keeps the app process at foreground-service
  priority and owns the lifetime signal for existing SSH transports.
- New tabs opened while the service is running join the visible session count.
- Closing the final SSH tab leaves the explicitly enabled service in a visible
  idle state showing zero active connections. This avoids silently changing the
  user's setting; the notification and Settings screen continue to provide Stop actions.
- If the service or process is killed, do not silently restart it from the
  background. Keep the saved preference and offer an explicit visible Start
  action when the user returns; recover remote work through normal tmux reconnection.
- Never create a second source of SSH-session truth. The service should observe
  the existing `SshManager` and `TerminalSessionManager` state.

## Android implementation

### Foreground-service type

Do not reuse `dataSync` for persistent interactive SSH. The likely candidate is
Android's `specialUse` foreground-service type because the work is long-lived,
user-visible, and does not accurately fit a finite transfer category.

Proposed manifest shape for Android 14 and later:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".service.BackgroundSshService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-started persistent interactive SSH connections for active terminal project tabs" />
</service>
```

This is a proposal, not proof of Play approval. Recheck the official foreground-
service types and Play declaration form immediately before implementation and
submission.

### Service contract

Use explicit actions instead of implicit startup:

- `ACTION_START_BACKGROUND_SSH` — accepted only after the in-app user action;
- `ACTION_STOP_BACKGROUND_SSH` — notification and in-app stop path;
- `ACTION_REFRESH_NOTIFICATION` — updates count and text without restarting;
- `ACTION_OPEN_APP` — notification content intent.

Recommended lifecycle rules:

- call `startForeground()` immediately in `onCreate()` or the start handler;
- use one stable notification channel dedicated to active SSH sessions;
- use `START_NOT_STICKY` for the initial policy-safe version;
- record whether the stop was user initiated, idle, system driven, or caused by
  an error;
- cancel service jobs and unregister observers in `onDestroy()`; and
- never log hostnames, credentials, terminal contents, or notification contents
  containing private project data.

The notification permission is separate from permission to start a foreground
service. Request it contextually when the user enables the feature. If the user
denies notifications, do not pretend background continuity is enabled; explain
the requirement and keep the mode off.

### Battery settings

Do not restore `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the first version.
Google Play restricts direct exemption requests to narrow acceptable use cases.

TerminalHub provides an explicit **Battery optimization** row in Connection &
Background using the general battery-optimization settings intent. It reports
the Android Doze-exemption status after the user returns. This should:

- avoid declaring the direct exemption permission;
- avoid launching settings automatically;
- recommend the change for users who choose persistent background SSH while
  making clear that it remains a user-controlled system setting;
- avoid claiming that an exemption guarantees a live connection; and
- remain separate from the primary foreground-service opt-in.

Only consider a direct exemption request after documenting why foreground-
service priority is insufficient, confirming that the use case is acceptable,
and completing any required Play declaration or review.

## Google Play submission plan

Before uploading a build containing the service:

1. Confirm the exact service type and permissions in the production manifest.
2. Complete the Foreground Service declaration in Play Console.
3. Describe the feature as user-started interactive SSH continuity, not sync.
4. Explain the impact of deferral or interruption: the live transport drops, but
   server-side tmux work continues and can be reattached.
5. Provide an unlisted demonstration video showing the complete user flow:
   - open TerminalHub;
   - enable **Keep SSH active in background**;
   - observe the immediate notification;
   - send the app to the background;
   - return to the same live terminal;
   - stop the mode from the notification; and
   - verify that the notification and service stop.
6. Ensure store text and screenshots describe the feature as optional and do not
   promise uninterrupted background operation.
7. Reconcile permissions with Data Safety and the privacy policy.
8. Upload to Internal testing first and inspect every Play warning.
9. Use a staged production rollout after approval rather than immediately moving
   all users to the new architecture.

Do not combine the first foreground-service review with unrelated permission or
policy-sensitive changes. A narrow submission is easier to explain, test, and
roll back.

## Verification matrix

### Unit tests

- explicit start action enables service state;
- tab connection alone never starts the service;
- stop action closes transports but does not kill tmux;
- final remote tab leaves the explicitly enabled service visible in its zero-session idle state;
- notification session count follows connected SSH sessions;
- denied notification permission leaves the mode off;
- process recovery does not start the service without a new user action.

### Emulator and device tests

- Android 7/API 24 minimum-version behavior;
- Android 13 notification permission denied and granted;
- Android 14, 15, and 16 foreground-service startup rules;
- screen off, app backgrounded, Wi-Fi/mobile transition, and Doze;
- several project tabs with active-tab and all-session keepalive scopes;
- stop from notification, stop from settings, swipe task away, force stop, and
  low-memory process death;
- OEM battery restrictions on at least one non-Pixel physical device;
- notification content contains no private terminal text or credentials.

### Acceptance criteria

- The service starts only from an obvious user action.
- A correct ongoing notification appears immediately and remains visible.
- The user can stop the feature from both app and notification.
- Existing SSH sessions remain interactive through a normal background interval
  materially more reliably than release 1.251.
- System termination falls back to saved-tab plus tmux reconnection without data
  loss or duplicate remote commands.
- Production manifest and Play declaration exactly match runtime behavior.
- Google Play approves the foreground-service declaration and release.

## Rollout and rollback

Keep the feature behind a local setting that defaults to off. During internal
testing, collect only sanitized failure information and avoid adding a tracking
SDK solely for this feature.

If Play rejects `specialUse`, do not relabel the same behavior as `dataSync` or
another inaccurate type. Keep release 1.251's tmux-based lifecycle, record the
reviewer's exact wording, and reassess either a narrower foreground task or
distribution outside Google Play.

If the feature causes crashes, notification loops, excessive battery use, or
session duplication, disable it in the next release while preserving normal
tab restoration and tmux reconnection.

## Implementation order

1. Add service-state tests and explicit start/stop state machine.
2. Implement `BackgroundSshService` and notification actions.
3. Add the opt-in UI and contextual notification-permission flow.
4. Add the optional general battery-settings link without exemption permission.
5. Run the complete automated and device verification matrix.
6. Update documentation and Play declarations from the verified build.
7. Release through Internal testing, Play review, and staged production rollout.

## Official references

- Android foreground services overview:
  https://developer.android.com/develop/background-work/services/fgs
- Android foreground-service types, including `specialUse`:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- Android background-start restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Google Play foreground-service declaration requirements:
  https://support.google.com/googleplay/android-developer/answer/13392821
- Android Doze and battery-exemption guidance:
  https://developer.android.com/training/monitoring-device-state/doze-standby
