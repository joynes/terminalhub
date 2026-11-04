# Automated tests and coverage

The target is **95% line coverage across the authored Android app code**. Branch
coverage is reported separately. Compose screens, ViewModels, data classes,
coroutine bodies and the app's SSH patches remain in the denominator. Generated
Android resources/BuildConfig, Hilt factories/injectors, Room implementations and
the debug-only Hilt test activity are excluded. The vendored Termux modules have
their own JVM suites and are run alongside the app; they are not counted as app
coverage.

## Run locally

Use JDK 21 and the Android SDK/NDK versions declared in the project. On this Mac:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:testDiagnosticDebugUnitTest :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest
python3 -m unittest discover -s scripts -p 'test_*.py'
```

With one test emulator running, generate the combined JVM + Android report and
enforce the target:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:appCoverageVerification --max-workers=2
python3 scripts/coverage_summary.py app/build/reports/jacoco/appCoverageReport/report.xml
```

The report task is `:app:appCoverageReport`; it measures without enforcing the
threshold. The verification task fails below 95%. Both JVM `.exec` and emulator
`.ec` data must be present; missing data cannot silently pass. HTML and XML
reports are under `app/build/reports/jacoco/appCoverageReport/`.

The instrumentation suite uses `se.joynes.terminalhub.diag`, separate from the
production app. HiltTestActivity is in `src/debug` so it runs in the target app
process and is absent from release builds. Tests requiring a live SSH account
are explicitly skipped when their credentials are absent. Local tmux and OpenSSH
SFTP process integrations run when those tools are installed; CI installs both.
Do not pass personal server credentials to routine CI.

Remote host workflow tests simulate the SSH channel boundary only. They exercise
the real ViewModels, Room database, terminal emulator, connection lifecycle, tmux
window protocol handling, macros and multi-project routing. Separate native tmux
and SFTP integration tests exercise real local processes. Navigation and document
picker tests use isolated diagnostic data and inspect the files actually written.
The session-log export tests cover both a chosen document and a failed destination
followed by a successful retry.

JaCoCo 0.8.14 is pinned for compatibility with the project's large Compose
functions. The terminal screen's outer layout is a separate composition to keep
its bytecode within instrumentation limits.

## GitHub Actions

`.github/workflows/tests.yml` runs on pushes, pull requests and manual dispatch.
GitHub receives commits when they are **pushed**; local commits alone do not
trigger a GitHub run. A JVM job runs the app and Termux suites. A second job runs
an API 35 emulator, merges JVM/Android coverage and enforces the 95% line target.
Reports are uploaded as artifacts even on test/coverage failure, with a measured
summary in the workflow run. The workflow requires no signing key or application
credentials. Actions are pinned to commit hashes and receive read-only repository
permissions.

The Git diff UI regression group runs first for faster feedback on emulator
scrolling failures. A failure records viewport/semantic bounds and a screenshot
when capture is available. The subsequent complete suite has no class filter;
its coverage must still reach 95% before the job passes.

The presence of a 95% gate is a requirement, not evidence that 95% has already
been reached. Use the report and workflow result to see the measured value.
