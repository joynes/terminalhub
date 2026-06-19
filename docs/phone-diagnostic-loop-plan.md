# Phone Diagnostic Loop Plan

## Goal

Reproduce and isolate the grey terminal rendering bug on the physical phone without overwriting the currently installed release app.

## Constraints

- Keep the installed release app intact.
- Use a separate package name for all diagnostic builds.
- Prefer short iterative loops over broad changes.
- Capture enough evidence per iteration to decide the next step without guessing.
- Use `Local device` as the default test target unless a specific SSH comparison is needed.

## Main Strategy

Create a dedicated diagnostic install path with its own application ID, then run a tight test loop on the phone:

1. Build and install a side-by-side diagnostic app.
2. Run the terminal in `Local device` mode by default.
3. Run one focused rendering hypothesis per build.
4. Collect the same evidence every time.
5. Compare against the previous run.
6. Continue until the transform source is isolated.

Reason:

- The grey rendering issue reproduces in local mode as well, so SSH is no longer required for the primary reproduction loop.
- Using `Local device` removes SSH transport, auth, remote shell startup, and server configuration as confounding variables.

## Packaging Strategy

Use a dedicated diagnostic variant so the release app remains untouched.

Recommended setup:

- Add a `diagnostic` build type or product flavor.
- Use:
  - `applicationIdSuffix = ".diag"`
  - `versionNameSuffix = "-diag"`
  - different app label, for example `AI Terminal Hub DIAG`
  - different launcher icon tint or badge if convenient

Target outcome:

- Release app stays installed as `se.joynes.aiterminal`
- Diagnostic app installs separately as `se.joynes.aiterminal.diag`

## What The Diagnostic App Should Contain

Each diagnostic build should include the minimum tooling needed to inspect the issue on-device:

- In-app render diagnostics already added to `LogView`
- A visible black control swatch
- Optional visible color test strip:
  - black
  - white
  - red
  - green
  - blue
- A simple diagnostics menu or hidden toggle screen with:
  - terminal view on/off
  - plain Android `View` on/off
  - plain Compose `Box` on/off
  - software layer on/off
  - hardware/default layer on/off
  - Force Dark flags reported
  - current window and view alpha/layer state

## Test Loop

For every iteration, run the same loop:

1. Install the diagnostic APK only.
2. Open the diagnostic app on the phone.
3. Create or open a project configured with `Local device`.
4. Open the terminal test screen for that local project.
5. Record:
   - screenshot
   - one `TERMDIAG` log line
   - confirmation that the project target is `Local device`
   - whether the control swatch is black or grey
   - whether reverse video changes only terminal colors or the whole surface impression
6. Change exactly one hypothesis in code.
7. Rebuild the diagnostic APK.
8. Reinstall the diagnostic app.
9. Repeat.

Rule:

- Never mix multiple unrelated rendering changes in one iteration.
- Keep the target mode fixed to `Local device` across iterations unless the current step is explicitly an SSH-vs-local comparison.

## Evidence To Capture Every Time

Minimum evidence bundle per run:

- app version / git commit
- package name
- target mode (`Local device` by default)
- screenshot of terminal screen
- screenshot with developer option overlays if used
- one copied `TERMDIAG` line
- one note with:
  - terminal background appears `black`, `grey`, or `white`
  - control swatch appears `black` or `grey`
  - reverse-video behavior

## Isolation Matrix

Use these comparisons to narrow the cause:

### Case A

- Terminal is grey
- Black Android control swatch is black

Interpretation:

- Problem is specific to terminal rendering path or terminal view internals.

### Case B

- Terminal is grey
- Black Android control swatch is also grey

Interpretation:

- Problem affects embedded Android views in this screen, not just terminal rendering.

### Case C

- Android control swatch is black
- Compose black box is grey

Interpretation:

- Problem is likely in Compose or window composition path.

### Case D

- Everything black becomes grey uniformly

Interpretation:

- Suspect global color transform, display enhancement, accessibility transform, or OEM processing.

## Phone-Side Checks

Use these phone checks in a controlled way, one at a time:

- Developer options:
  - Show layout bounds
  - Debug GPU overdraw
  - Disable HW overlays
  - Profile HWUI rendering
- Display settings:
  - extra dim
  - reading mode
  - eye comfort / night mode
  - adaptive color / vivid / enhanced contrast
  - grayscale / color correction / accessibility display adjustments

Important:

- Only enable one system-level toggle at a time and record the outcome.

## Suggested Iteration Order

1. Ship side-by-side diagnostic package.
2. Reproduce the bug in `Local device` mode and keep that as the baseline.
3. Compare terminal vs black Android swatch.
4. Add Compose black swatch and compare.
5. Add a dedicated diagnostic activity with no app chrome, no tabs, no overlays.
6. Compare edge-to-edge enabled vs disabled.
7. Compare same content in:
   - `TerminalView`
   - plain `View`
   - Compose-only screen
8. Only after the local baseline is understood, optionally compare the same build in SSH mode to confirm whether the issue is transport-independent.
9. If all app-controlled paths still show grey, investigate phone display processing outside the app.

## Implementation Tasks

1. Add diagnostic build type or flavor with `applicationIdSuffix = ".diag"`.
2. Add app label suffix and optional diagnostic icon badge.
3. Add a dedicated diagnostic activity or screen with:
   - terminal view
   - Android black swatch
   - Compose black swatch
   - optional RGB strip
   - explicit label showing current target mode: `Local device` or `SSH`
4. Add a compact diagnostics panel that prints:
   - package name
   - build type
   - git commit if available
   - layer type
   - alpha
   - Force Dark state
5. Add a repeatable “capture this run” checklist to the screen or log output.

## Stop Condition

Stop the loop once one of these is true:

- the exact layer producing the grey transform is identified
- a single minimal code change removes the grey on phone
- the issue is proven to come from phone-level display processing outside the app

## Practical Next Step

Implement the first safe milestone:

- create the side-by-side diagnostic package `se.joynes.aiterminal.diag`
- keep the current release package untouched
- move the current terminal diagnostics into that diagnostic package
- use that package for all further phone loops
- make `Local device` the default project target for all diagnostic iterations
