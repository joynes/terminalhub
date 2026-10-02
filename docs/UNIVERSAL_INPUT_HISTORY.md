# Universal input history and pinned actions

Press **★** in the keybar to open Pinned actions. It is present in the fresh-install
layout (replacing `2`). Existing custom layouts are preserved; add **PINNED ACTIONS**
in Settings → keybar if needed.

The **Recent** tab and Text Input's **HISTORY** button show this project's recent
input. Tap an entry to edit it. Hold it to Pin, Copy, Edit or Delete. Initially 20
entries are shown; Show more reveals older entries, up to 100 per project.

When pinning, enter a name and choose Project or Global. With **Send immediately
with Enter** enabled, tapping the action pastes its content and sends Enter
separately after the existing write synchronization and delay. With it disabled,
the action opens an editable Text Input draft. Hold an action to edit its name,
content, scope or behavior, prepare it, or delete it. Nothing is tool-specific.

## What is recorded

Only user input is observed, before transport. Direct keyboard/keybar characters
build a project draft; Enter records it, backspace removes the last Unicode code
point, and Ctrl+C discards it. Semantic paste (including direct dictation) keeps
embedded newlines until Enter. Text Input and executed pins save one complete
submission; their delayed Enter is not recorded a second time. Exact adjacent
duplicates are collapsed. Terminal output and protocol replies are never recorded.

This is approximate history, not a reconstruction of a shell or editor's internal
buffer: cursor movements, readline recall, completion and vi/TUI modes are not
emulated. Review recalled input before executing it. History can include passwords
or other sensitive text typed into a terminal; remove sensitive entries in Recent.

## Persistence and privacy

History and pins are local Room data. Database migration 9 → 10 adds the pins table
without deleting existing servers, projects or history. Updating the app needs no
preliminary export.

Explicit configuration export includes global pins and pins belonging to exported
projects. Import assigns their project references to the newly imported projects;
it replaces existing pins along with the rest of the imported configuration.
Old backups without pins restore none. Input history is never included in exports.
Protect exported files: pinned commands may contain secrets.

The database is excluded from Android automatic cloud backup and device transfer
because it contains raw input history. Use manual configuration export/import to
move server/project configuration and pins to another device. This does not affect
normal app updates or persistence after process death.

Direct pinned-action buttons in the keybar are deferred; this version provides the
default ★ launcher plus the configurable launcher position.

## Verification

Unit tests cover recorder Enter/cancel/backspace, Unicode, multiline paste, project
isolation, explicit submission ownership and default/custom keybar layouts.
Android tests cover actual IME/hardware ingress, bracketed paste, output exclusion,
retention/deduplication, scope/edit/delete, migration and persistence, backup data
remapping, plus the ★ → Recent → Pin → named action flow and prepare mode.
