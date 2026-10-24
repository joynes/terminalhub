# Keyboard macros in pinned actions

Open ★ Pinned → New (or hold an existing action → Edit action) and select
**Keyboard macro**. Add Key, Text and Pause steps. Move steps with ↑/↓,
duplicate them, or delete them. Global is the default scope for new actions;
Project limits the macro to the current project. A blank name gets a name
derived from the sequence.

The template **Ctrl+C → text → Enter** creates:

1. C with Ctrl enabled (the terminal interrupt byte, not printable text).
2. A 300 ms pause, editable to match the remote program.
3. The text to paste.
4. A separate Enter key.

Keys support Ctrl, Alt and Shift, one character or SPACE, ENTER, TAB, ESC,
BACKSPACE, DELETE, INSERT, arrows, HOME, END, PAGE_UP/DOWN and F1–F12. A key
step can repeat 1–100 times. Text uses the terminal's current bracketed-paste
mode. Named keys use the terminal's current application cursor/keypad modes.

Tapping a macro runs its steps directly. The existing Text Input execute-on-send
setting does not add another Enter. Execution is bound to the original terminal,
including when switching tabs. Replacing or disconnecting that terminal stops
the run. There is one active macro at a time and no automatic retry or resume.
The progress banner shows the target project and current step; STOP cancels
remaining steps. It cannot retract input already sent. Completion means the
input sequence was sent, not that the remote command finished.

Keys are separated by 80 ms. After a paste is flushed, the next key waits
300 ms plus 5 ms per text character, capped at 5 seconds, to avoid interactive
programs treating Enter as part of the paste. Explicit Pause steps count toward
this wait. These are timing-based sequences; they do not detect shell prompts
or screen contents. Increase the pause if a program needs more time to react.

## Persistence and compatibility

Database migration 11 → 12 adds the optional `macroJson` field to pinned actions
without changing existing plain-text actions. Upgrading requires no export.
The payload is versioned JSON (`version: 1`, ordered `steps` with type `key`,
`text` or `pause`). Invalid or unknown versions/steps are rejected before a full
configuration import replaces existing data.

Macros are included in the existing **full app backup**, with global/project
scopes and project-id remapping. The settings-only export does not include
pinned actions. Older plain-text backups remain supported. Old app versions
cannot execute macros; exported macros have `sendEnter: false`, so an older
importer can only prepare their descriptive text rather than executing it.

Limits: 100 steps, 1,000 total key presses, 65,536 characters per text step,
262,144 total text characters, pauses of 0–60,000 ms and at most five minutes
of explicit pauses. Writes have a 15-second timeout; a whole run is bounded
to six minutes. No credentials, terminal text or macro contents are logged.

## Tests

Unit tests exercise terminal byte encoding, Ctrl+C, Ctrl+/, Alt/Ctrl combinations,
function/application cursor keys, write barriers, paste/Enter separation,
repetition, pause timing, disconnects, cancellation, timeout and overlapping runs.
Emulator UI tests exercise creation, editing, ordering, duplication, deletion,
validation, progress and stop controls. Real Android PTY tests interrupt a running
shell command, switch tabs during the macro, and close/recreate the target.
Storage tests cover migration, reopening the database, old backups and complete
global/project macro backup round trips.
