# Terminal Color Fix

This documents the terminal background rendering fix that shipped after the Samsung phone investigation.

## What Was Wrong

On the physical Samsung phone, the terminal background could appear gray even when:
- the terminal palette background was black
- sampled terminal cells resolved to black
- reverse-video state was correct

Part of the investigation was noisy because some captures accidentally measured `com.termux/.app.TermuxActivity` instead of this app. Once the foreground app was verified on every run, the real fix path became clear.

## What Actually Fixed It

The fix was not one single line. It was a combination of host-view and renderer changes:

1. Samsung-specific software rendering for `TerminalView`
   - In `SessionHostScreen.kt` and `TerminalScreen.kt`, `TerminalView` is forced to `LAYER_TYPE_SOFTWARE` on Samsung devices.
   - Other devices stay on `LAYER_TYPE_NONE`.

2. Disable default focus highlight on the terminal view
   - `defaultFocusHighlightEnabled = false` is applied to `TerminalView`.
   - This avoids OEM focus/highlight behavior affecting the terminal surface.

3. Stop auto-showing the IME on terminal attach
   - The terminal still requests focus, but the keyboard is no longer automatically forced visible on attach.
   - Keyboard show/hide is now user-driven from taps and the key bar.

4. Make `TerminalView` own a stable real view background
   - `TerminalView.java` now tracks `mViewBackgroundColor`.
   - On draw, the view background is updated from the terminal’s effective background color.
   - In normal mode that is the palette background.
   - In reverse-video mode that is the palette foreground.

5. Explicitly paint background rectangles for all text runs
   - `TerminalRenderer.java` now draws the run background rectangle for every run, including the default background.
   - It no longer relies on a full-canvas clear path for the default background.

## Files Involved

- `app/src/main/java/se/joynes/terminalhub/ui/screen/sessions/SessionHostScreen.kt`
- `app/src/main/java/se/joynes/terminalhub/ui/screen/terminal/TerminalScreen.kt`
- `terminal-view/src/main/java/com/termux/view/TerminalView.java`
- `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java`

## Why This Worked

The final working behavior came from making the terminal surface more explicit and less dependent on OEM-specific hardware/compositor behavior:
- software layer on Samsung
- no focus highlight tinting
- no forced IME interaction on attach
- explicit background painting in the terminal renderer
- stable background color on the Android view itself

## Verification

The final verification loop was:
- confirm this app was the actual `topResumedActivity`
- capture screenshot from the phone
- sample screenshot pixels
- compare with `TERMDIAG` log output
- confirm visually in the app

The final user-visible confirmation was:
- normal mode background black
- reverse video white
- switching back returns to black
