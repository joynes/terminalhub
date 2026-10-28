# Keyboard shortcuts and quick find

Open Settings → Terminal & input → Keyboard shortcuts. Tap an action and press a
combination on a USB or Bluetooth keyboard, then Save. Clear removes that binding.
Conflicts are shown before saving. Shortcuts start unassigned so an update does not
take over existing terminal commands. “Use suggested shortcuts” assigns all actions:

| Combination | Action |
| --- | --- |
| Ctrl+Alt+Right / Left | Next / previous open project |
| Ctrl+Alt+Down / Up | Next / previous terminal window |
| Ctrl+Alt+P | Find projects and terminal windows together |
| Ctrl+Alt+N / Q | Create / close project |
| Ctrl+Alt+T / W | Create / close terminal window |
| Ctrl+Alt+R | Rename the active terminal window |

One Find action is also available from the terminal menu.
Search accepts nonconsecutive letters, like `trm` for TerminalHub, and multiple
words. Project search includes server names. Closed projects are excluded by default; the Include closed projects toggle adds them and their windows. Use arrows and
Enter, or tap a result. Escape cancels. Window search lists all open projects'
tmux windows in the same result list, using their stable IDs even when names are identical.
Results identify projects and windows separately. Existing project-find bindings are
preserved; a former window-find binding becomes Find if no project-find binding existed.

Shortcuts work on the terminal screen, including when text input has focus.
Configured combinations take priority over terminal input. Native dialogs keep
their own keyboard handling. Closing a tmux window requires confirmation; the last
window cannot be closed. Disconnected or busy windows show a message instead of
sending a command to the wrong terminal.

Use Ctrl, Alt or Meta with a key, or F1–F12. Android may intercept system shortcuts
before the app receives them. Alt+Tab and Ctrl/Alt+Space are rejected explicitly.
Avoid combinations used by AltGr on your keyboard layout. This configures app
actions, not the Android keyboard language or general key remapping.

Bindings persist and are included in settings YAML and full configuration backups.
Example under `settings:`:

```yaml
keyboardShortcuts:
  - FIND_PROJECT=CTRL+ALT+P
```

An empty list disables all bindings. Omitted settings preserve existing bindings
on partial import. Invalid actions, keys and duplicate combinations reject import.

Find discovers windows concurrently (up to four projects at a time), without selecting them. Loading and partial failures are shown. Results include project and server names. Selecting a window opens its project and selects the logical window key after the live window list is ready; unavailable windows produce a message.
