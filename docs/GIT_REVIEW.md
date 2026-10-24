# Git review

Open **Git changes** from the session menu. To use a keybar button, open
Settings → Key bar, add or replace a button, and choose **GIT DIFF**. Custom
keybar layouts and highlights include this action in their existing exports.

The viewer starts fullscreen. WINDOW / FULL SCREEN changes its size; A− / A+
changes code size. Code wraps to the screen width, supports text selection,
and scrolls vertically. Added and removed lines have both background colors
and explicit + / − markers; old and new line numbers appear beside the code.
Syntax colors cover common code and configuration files, including multiline
comments and strings. They are a lightweight lexer, not a full language parser;
syntax context before a diff hunk is not available.

## Comparisons

- All changes: tracked working files versus HEAD, plus untracked files.
- Unstaged: tracked working files versus the index, plus untracked files.
- Staged: the index versus HEAD (`git diff --cached`).
- Latest commit: HEAD versus its first parent, or the empty tree for a root
  commit. The commit subject, author and date are shown.

Repositories without a first commit are supported. Renames retain both paths;
binary changes are reported by Git without attempting to render binary data.
Refresh reloads the current comparison. Changes made on the server while the
viewer is open appear after refresh; there is no background polling.

## Architecture and bounds

This is a read-only SSH-project feature. It uses the existing transport pool
and a short-lived auxiliary exec channel, never the terminal PTY. Closing the
viewer cancels its work and closes only that channel. There is no listener,
tunnel, server helper, database migration, additional permission or dependency.

Git must be installed on the server. Discovery includes the containing repo
and nested `.git` directories/files (including worktrees). It skips dependency
and build folders and stops descending beyond five directory levels. Discovery
rejects more than 30 candidates. Each command times out after 20 seconds and
combined stdout/stderr is bounded to 1 MiB. Truncated file lists are errors;
truncated patches are explicitly labeled. Display is bounded to 6,000 lines
and 16,000 characters per line, with visible limit messages.

Commands disable pagers, external diff and textconv programs, and optional
index writes. Paths are shell-quoted and passed as literal Git pathspecs;
NUL-separated file lists preserve whitespace and unusual filenames.

## Validation

Real Git repository tests cover all comparison modes, unborn branches, root
commits, renames, deletions, detached HEAD, binary files, worktrees, discovery,
and unusual filenames. Executor tests verify transport reuse, bounded output,
cancellation during stalled exec, and missing exit status. UI tests cover
fullscreen/windowed display, vertical scrolling, wrapped code, font controls,
retry/loading feedback, and keybar configuration/activation. Palette tests
require at least 4.5:1 text contrast on all code/diff backgrounds.
