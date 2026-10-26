# Terminals inside projects

Existing project tabs and project tmux sessions stay in place. SSH projects with
tmux enabled now show a second **TERMINALS** row beneath the project tabs.

- Tap **+** in that row to open another terminal in the project's folder. A blank
  name gets an available name such as `Terminal 2`.
- Tap a terminal to switch to it. Long press to rename or close it.
- With one terminal the row is hidden. Hold the project tab and choose **New
  terminal**, or use **New terminal** in the terminal screen's menu. Creating a
  second terminal reveals the row again. Progress and errors remain visible.
- Closing a terminal stops its programs and panes after confirmation. The last
  terminal cannot be closed here. **Restart session** in the project menu stops
  all terminals in that project.
- Windows and their processes live on the server and are rediscovered after
  reconnecting or restarting the app. No export or project migration is needed.
- History, notes and pinned-action scope remain project based. Terminal switching
  is paused while a macro or delayed Text Input Enter is being sent.

## Tab appearance

Settings → Terminal & input → Project & terminal tabs provides separate width,
minimum height and label-size controls for project tabs and tmux terminal tabs.
Each group has its own reset button. The compact terminal-row default is 28 dp.
Large labels or Android font scaling can increase the actual height to keep text
readable. Widths stay fixed and long names use an ellipsis.

All six preferences persist across restarts and are included in settings-only
YAML and full backups. YAML keys: `projectTabWidth` (48–200 dp),
`projectTabHeight` (24–64 dp), `projectTabTextSize` (8–24 sp),
`terminalTabWidth` (48–240 dp), `terminalTabHeight` (24–64 dp) and
`terminalTabTextSize` (8–24 sp). Missing keys preserve current values; invalid
values reject the import. Defaults are 70/28/9 and 100/28/12 respectively.

## Implementation

Each project retains its own tmux session. Its terminals are tmux windows, each
with an independent shell and working directory. Opening a window does not open
another persistent SSH terminal connection. Short auxiliary commands use the
existing SSH transfer pool; file transfers retain the same shared infrastructure.

Commands address stable session/window IDs, check project membership before
mutation and shell-quote arguments. Names also escape tmux format expansion.
Lists use NUL framing and reject incomplete responses. Commands have bounded
time/output and are never automatically replayed after an ambiguous failure.

The foreground screen refreshes the active project's list every three seconds;
refreshing does not select a window. Leaving the screen cancels polling. Other
tmux clients attached to the same session see its shared active window, which is
normal tmux behavior. Existing multi-pane windows can be selected and closed as
a unit; pane creation is outside this feature.

## Verification

- JVM tests cover list parsing, state transitions, duplicate taps, failures,
  cancellation and project changes.
- Integration tests run real tmux in a private temporary socket directory. They
  verify create/select/rename/close, process and project isolation, stale lists,
  literal format strings, names/paths with special characters, last-window
  protection and reconnect with a dead pane beside a healthy window.
- Compose tests cover selecting/creating, progress, input guards, confirmation
  and last-window protection.
- Manual diagnostic-app verification uses an isolated OpenSSH server: create,
  switch, rename, write from both windows, force-stop/relaunch, rediscover the
  same process IDs, and close one window while preserving the other process.

Real-tmux JVM tests require tmux installed locally; they skip where unavailable.
