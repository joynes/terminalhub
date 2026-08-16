# Reproducible marketing screenshots

TerminalHub includes a diagnostic-only preview activity for capturing accurate,
repeatable screenshots without connecting to a real SSH server or exposing
private hosts, users, projects or terminal history.

The preview activity is present only in the `diagnostic` flavor. It cannot be
packaged in a production build.

## Capture every scene

The default AVD is `Medium_Phone_API_36.1`. Override `AVD_NAME`, `ADB`, or
`EMULATOR` when needed.

```sh
./scripts/capture-play-screenshots.sh /absolute/output/directory
```

The script starts the emulator if necessary, builds and installs the diagnostic
APK, selects a deterministic 1080 × 1920 viewport and captures the scenes in
their intended order.

## Preview one scene

After installing `diagnosticDebug`:

```sh
adb shell am start -W \
  -n se.joynes.terminalhub.diag/se.joynes.terminalhub.marketing.MarketingPreviewActivity \
  --es scene sessions
```

Available scenes:

- `sessions`
- `resume`
- `prompt`
- `files`
- `opensource`
- `servers` (supplemental)

## Safety and accuracy

- Use only the deterministic example data already defined in the preview.
- Review every image before publishing it.
- Do not substitute concept art for an in-app screenshot.
- Re-run the capture after material UI changes.
- Keep final store assets and unpublished campaign material outside this public repository.
