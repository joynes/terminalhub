# Start commands per terminal window

Long-press a terminal tab and choose **Edit start command**. For a project with one
terminal and a hidden terminal bar, use **Settings → Terminal information**.
Alternatively, long-press the project tab and choose **Start command** when the
project has exactly one known terminal. This edits that default terminal directly.
Save a single-line command, including any arguments. Edit or **REMOVE** it from
the same dialog.

Select the terminal, then open its information and choose **Prepare start command**.
This opens the app's text input with the saved command. Review it and send it when
ready. Preparing replaces the current text input draft. Saving, reconnecting and
restoring windows never execute the command automatically. Switch to an inactive
terminal before preparing its command. The command runs in the terminal's current
shell context; include `cd /path && ...` if a specific directory is required.

Commands are stored on this device in the existing project/server-scoped window
layout preferences, indexed by stable window keys. Renaming/reordering windows,
app restarts and app-driven tmux window restoration retain them. Closing a window
removes its command on the next saved layout; an unrelated window reusing its tmux
ID does not inherit it. Commands are not uploaded to the server or shared between
phones. Full settings import clears the local window layouts as before.

Layout format 3 reads versions 1 and 2 without losing names or program hints.
Polling updates program observations but preserves explicitly saved commands.
Program observations never populate launch commands automatically.

Verification covers stable identity through rename/reorder/server restoration,
legacy decoding, no automatic execution, local persistence/removal during polling,
editing and preparing from the hidden single-window menu, and inactive-window guards.
