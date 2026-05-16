# Printer Thumbnail Compatibility

MobileSlicer must not treat every Orca thumbnail format as equally proven.
The source can parse and route several formats, but printer compatibility only
counts when a fixture or live host proves the exported file is accepted by the
consumer.

The canonical matrix is:

- `regression-fixtures/printer-thumbnail-compatibility/matrix.json`

The gate is:

```bash
python3 scripts/printer_thumbnail_compatibility_audit.py --pretty
```

## Current Status

| Target | Status | Evidence |
| --- | --- | --- |
| Qidi Q2 Fluidd/Moonraker PNG thumbnails | proven live | `fluidd-thumbnail-metadata` uploaded to the Qidi Q2 Moonraker host on 2026-05-16 and Moonraker reported `32x32`, `48x48`, and `300x300` thumbnails. |
| Generic Fluidd/Moonraker PNG thumbnails | gated | Local/device gate validates `48x48/PNG` and `300x300/PNG` G-code blocks and visual metrics. |
| Mainsail/Moonraker PNG thumbnails | pending live validation | Expected through Moonraker metadata, but not claimed until a Mainsail host is tested. |
| OctoPrint PNG thumbnails | pending live validation | Upload exists, but thumbnail display is plugin-dependent. |
| Qidi ColPic `gimage`/`simage` | gated | Android device fixture capture and the metadata benchmark require both `gimage` and `simage` blocks. |
| BTT TFT thumbnails | gated | Android device fixture capture and the metadata benchmark require a `thumbnail_BTT` block with the requested dimensions. |
| QOI G-code thumbnails | gated | Fixture/gate covers `thumbnail_QOI` and requires a real QOI payload signature. |
| JPG G-code thumbnails | gated | Android real libslic3r links libjpeg-turbo, routes JPG/JPEG requests through Orca validation, and fixture/benchmark gates require a real JPEG payload in `thumbnail_JPG`. |
| Bambu/Orca sliced 3MF package thumbnails | gated | `sliced-3mf-metadata` and desktop-Orca thumbnail reference matrix cover plate roles, including real Creality K1 and Prusa MK4 profile cases. |
| Multi-plate sliced 3MF package thumbnails | gated | `multi-plate-sliced-3mf-metadata` verifies `plate_1` and `plate_2` G-code, JSON, and role thumbnail package entries through Orca's generic sliced 3MF writer. |

## Rules

- `proven_live` requires a live host or UI consumer and an automated gate.
- `gated` requires a reproducible local/device gate.
- `source_supported` is not product support. It means the code path exists but
  still needs a generated-output fixture.
- `pending_live_validation` must stay visible until a real host is tested.
- `not_supported` must stay explicit so release notes and UI behavior do not
  imply parity that does not exist.

## Next Compatibility Work

The current Qidi Q2 live gates upload, poll metadata, and delete generated test
files. They must not start prints.

1. Test a real Mainsail host and move it to `proven_live` only after Moonraker
   metadata reports expected thumbnails.
2. Test OctoPrint/plugin thumbnail display against a live or containerized
   host before moving it out of `pending_live_validation`.
3. Keep adding real desktop Orca profile cases to
   `regression-fixtures/orca-thumbnail-references/matrix.json` when a printer
   family has materially different bed dimensions, thumbnail defaults, or host
   expectations.
