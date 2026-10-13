# Touch scrolling

Settings → Terminal & Input → Touch scrolling is a collapsible subgroup and applies live to the current view and
every newly opened tab. All nine controls persist through app/process restart and
configuration export/import. Older backups without scroll settings keep the current
values. No export is needed before a normal app update.

For AI-assisted tuning, use Settings → Backup & AI Settings → Export Settings.
The YAML file has separate named scroll fields and detailed comments/ranges, not an
opaque comma-separated string. Import Settings validates and previews changed values
without replacing servers or projects. Export Backup/Restore Backup in the same
category are separately labeled full configuration operations for phone migration.

| Control | Range | Default |
| --- | --- | --- |
| Ordinary terminal drag speed | 0.25–5× | 1.5× |
| tmux/full-screen drag speed | 0.25–5× | 1× |
| Continue after a flick | On/off | On |
| Flick velocity multiplier | 0.25–4× | 1× |
| Braking/friction multiplier | 0.25–4× | 1× |
| Minimum flick speed | 50–2000 pixels/sec | 150 |
| Remote events per flick | 10–1000 | 120 |
| Remote events per animation frame | 1–20 | 6 |
| Reverse finger direction | On/off | Off |

Gentle, Default and Fast presets reset all controls as a group. Individual sliders
preserve the other controls. The minimum flick speed is an extra threshold on top of
Android's gesture recognizer; it cannot override a device's own minimum threshold.

Fling physics operate in pixels, convert to whole terminal rows, and run on Android
animation frames. Text is still rendered by rows, not continuously at sub-row pixel
offsets. tmux/TUIs own their own scrollback: wheel events may represent several lines,
and server configuration or SSH latency can affect the feel. Without mouse tracking,
full-screen apps receive arrow keys, matching the existing drag behavior. The remote
limits control fling traffic, not physical mouse-wheel events or direct key presses.

The old touch-specific flag incorrectly made every tmux touch fling look like a
mouse-mode change and cancelled it immediately. The new runner checks actual mouse
tracking/alternate-buffer modes and session identity. It stops on a new finger down,
tab/session change, mode change, settings change, selection/search scroll, explicit
scroll-to-bottom, or view detachment. A generation guard prevents old queued frames
from touching a new tab or resuming after Stop. Remote traffic is capped per frame and
per flick, including any queued tail after the physical animation finishes.

Tests cover the tmux mode regression, speed/reversal/burst bounds, settings round-trip
and invalid values, persistence, actual config export/import, preset/slider UI changes,
and real Android touch events continuing after release and stopping on the next touch.
