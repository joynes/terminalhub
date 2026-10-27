# Remote file browser

Status (2026-10-27): describes uncommitted development work in the current
working tree, outside source baseline 4018674. Release availability is not
verified. Test coverage described below is not a record of tests run during
this documentation update.

Open the file browser from the download keybar action. It fills the screen above
the terminal and keybar, with Android's system bars kept clear.

- Tap a folder to enter it. The path at the top is a horizontally scrollable set
  of clickable ancestors. Home returns to the project folder; Up moves one level.
  Back also moves up, and closes the browser at the project root. X closes it
  directly. Browsing remains scoped to the project's remote directory.
- Folder icons and folders-first sorting distinguish directories. Search filters
  names in the current directory. The sort menu supports name, date, type and size;
  selecting the same property reverses its direction. Sort preferences persist.
- Tap a supported file to preview it. Use its checkbox to select it for download.
  Multiple selections are supported; Select all applies to the currently visible
  filtered files. Download writes to the remembered approved Android folder.
  Change folder chooses a different destination.
- Returning from preview preserves the directory, list position and selections.
  Directory and viewport persistence still uses the existing per-project store.
  A temporary search does not overwrite the original list's saved scroll position.
- Download queues keep running if the browser is closed. Preview loading can be
  cancelled with Back; preview errors offer Back to files without reloading.

## Previews

Text, source/config files, CSV and JSON are shown as selectable text. Markdown
uses the existing formatted renderer. HTML is shown as source, not executed.
Text previews are limited to 512 KiB and reject binary NUL content.

PNG, JPEG, WebP, GIF and BMP use the native image decoder, with pinch zoom and pan.
GIF displays a still frame. Image transfers are limited to 8 MiB and decoded
dimensions are bounded. SVG is not rendered.

PDF uses Android's native PdfRenderer, with vertical page scrolling and zoom
controls. Zoomed pages can be panned horizontally. Transfers are limited to 16 MiB;
documents above 2,000 pages are rejected. Only nearby pages are rendered, with
bounded bitmap dimensions. Encrypted, damaged or unsupported PDFs display an error.

Preview reads bytes from the server over the existing transfer infrastructure;
it does not ask for a download destination or save to the user's Downloads folder.
Text/images are held in memory. PDF needs a private temporary cache file for
random access; it is removed when the viewer closes or opening fails.

## Verification

JVM tests cover preview byte limits, binary preservation, double taps,
cancellation, error recovery and exact ancestor paths. Emulator tests cover
portrait navigation, filtering, sorting, independent download selection, returning
from preview at the same scroll position, image display and native PDF page
rendering, zoom and temporary-file cleanup. Existing folder-permission, download,
location and viewport tests remain applicable.
