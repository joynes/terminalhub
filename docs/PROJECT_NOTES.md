# Project notes

NOTE in the keybar toggles one multiline scratchpad for the current project. It stays
above the keybar and changes projects with the active tab. Notes never execute in the
terminal and are independent of input-history consent. Existing custom keybars are
preserved; add PROJECT NOTES in Settings if needed. The previous factory layout is
upgraded automatically.

## SSH projects

Notes are plain UTF-8 Markdown on the server at:

`~/.terminalhub/project-notes/<sha256-of-canonical-project-path>/note.md`

The key does not contain a phone identifier or local database ID. Restore the same
server/user and project path after reinstalling to recover the note. Moving or
renaming the project directory changes its key; copy the old note deliberately if
you want to move it. Different SSH users have separate home-directory stores.

Room is an app-private working copy for offline edits. Local edits are saved
immediately; remote writes are coalesced after 1.5 seconds. Opening a project/note,
reconnecting, or RETRY SYNC attempts reconciliation. No periodic retry loop is used.
The existing shared SSH transport pool supplies a short-lived SFTP channel; there
is no shell interpolation, PTY command, or new credential store.

Remote changes are compared by SHA-256 content revision (mtime is also retained),
not device clocks. A dirty working copy conflicting with the server is retained
until KEEP THIS DEVICE or USE SERVER VERSION is chosen. VIEW BOTH and COPY BOTH
are non-destructive. A second change on the server before resolving is checked
again. SFTP has no general compare-and-swap primitive: simultaneous writes in the
tiny interval between the final revision check and rename are not a distributed
transaction. Avoid editing the same note simultaneously on multiple devices.

Uploads use a private temporary file and atomic OpenSSH
`posix-rename@openssh.com` for replacement. Servers without that extension can read
or create a note, but replacement fails visibly and retains both copies instead of
deleting the previous file. New directories use 0700, files 0600. Interrupted uploads
leave the old note intact and attempt to remove temporary files; a killed process
can leave an unused `.part` file. A 30-second watchdog closes only a stalled auxiliary
SFTP channel. Notes are bounded to 256 KB of UTF-8, never silently truncated.

CLEAR requires confirmation. Offline removal is explicitly marked pending and
retried; it does not pretend the server copy is already gone. Deleting a project
configuration does not delete its remote note. Notes are not encrypted on the server;
protect your SSH account, server disks and backups. No raw note contents are logged.

## Local Android projects and backup

App-private local notes do **not** survive uninstall or clearing application data.
Export Config offers **Include local project notes**, off by default. Enable it and
keep the resulting backup safely before uninstalling. It includes readable private
text, not encryption. Import restores local projects and remaps their notes/pins to
the new project IDs. The current export workflow exports the open project tabs.

SSH note text is never included in configuration exports. Export/import restores
server/project configuration; restore credentials separately, then connect to the
same remote project directory to recover notes. Android automatic backup excludes
the database, so it is not a substitute for explicit local-note export.

## Verification

Unit tests cover reconciliation, offline persistence/recreation, conflict choices,
pending clear, edits during a download, canonical keys, UTF-8, bounds, interrupted
publication, and SFTP resource release. A local OpenSSH SFTP integration test covers
real protocol creation, atomic replacement, permissions and recovery using new IDs.
The SSH authentication/pooling layer has its existing independent tests.

Android tests cover Room 10→11 migration, retention of projects/pins, note reopen,
explicit local-note backup with new IDs, excluding remote notes, NOTE toggle,
active-project content switching, clear confirmation and conflict viewing/choices.
