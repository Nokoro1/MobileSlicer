# Orca Golden Fixture Gate

The Orca metadata fixture gate is the release-track proof that MobileSlicer is
not silently losing export metadata. It is deliberately metadata-first: it
checks thumbnail contracts, print-time lines, filament lines, sliced 3MF package
entries, thumbnail relationships, `Metadata/plate_1.json`, and baseline PNG
visual metrics before any desktop-Orca pixel-diff work.

## Files

- `regression-fixtures/orca-metadata/manifest.json`
  - Declares every parity case, the expected MobileSlicer output path, the
    expected desktop Orca reference path, and the metadata assertions.
- `regression-fixtures/orca-metadata/mobile/`
  - MobileSlicer outputs for fixture validation.
- `regression-fixtures/orca-metadata/references/`
  - Desktop OrcaSlicer CLI metadata outputs generated from a recorded Orca
    build. Official CLI slicing calls `export_gcode(..., nullptr)`, so these
    files validate print-time, filament, and sliced 3MF package metadata but
    not G-code thumbnail blocks.
- `scripts/orca_metadata_fixture_gate.py`
  - Reads the manifest and validates any present outputs.
- `scripts/orca_metadata_audit.py`
  - Low-level inspector used by the fixture gate.

## Commands

Non-strict mode validates the manifest and any files that exist:

```sh
python3 scripts/orca_metadata_fixture_gate.py --pretty
scripts/verify_android.sh orca-fixture-gate
```

Capture MobileSlicer fixture outputs from a connected Android device:

```sh
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
  scripts/verify_android.sh orca-fixture-capture-mobile <serial>
```

This writes the supported MobileSlicer outputs under
`regression-fixtures/orca-metadata/mobile/` and audits each file immediately.
The current capture covers PNG G-code, Qidi-normalized PNG G-code, QOI G-code,
sliced `.gcode.3mf` metadata, and a deterministic two-object/two-filament
toolchange export.

Strict mode is the release/local parity mode now that desktop Orca references
and MobileSlicer fixture outputs are committed and no manifest cases remain
pending:

```sh
MOBILE_SLICER_STRICT_ORCA_FIXTURES=1 scripts/verify_android.sh orca-fixture-gate
```

Strict mode fails any case that declares a missing Orca reference path, a
missing MobileSlicer fixture output, or is still marked `pending`. Non-strict
mode remains available for fixture development, but the normal `local` release
path uses strict mode.

Generate the current desktop Orca references with:

```sh
scripts/generate_orca_reference_fixtures.sh
```

Thumbnail block parity is validated against MobileSlicer outputs and the Orca
source contract in `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.hpp` and
`vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.cpp`.

Run the device timing and metadata benchmark with:

```sh
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
  scripts/verify_android.sh orca-metadata-benchmark <serial>
```

This writes a timestamped report under
`artifacts/orca-metadata-benchmark/` with `nativeSliceMs`, `thumbnailMs`,
`writeGcodeMs`, total elapsed time, output byte counts, audit JSON, and logcat
for the no-thumbnail, PNG, QOI, and sliced 3MF metadata cases. Use this report
when judging whether metadata work changed slicer time. `nativeSliceMs` is the
toolpath phase; `thumbnailMs` and `writeGcodeMs` account for export metadata
overhead.

The benchmark also asserts the thumbnail scope that caused the last regression:
plain `.gcode` PNG/QOI cases must log `packageThumbnails=false` and render only
the requested G-code thumbnail, while sliced `.gcode.3mf` must log
`packageThumbnails=true` and render the package thumbnail set. The Android
release gate runs both `sliced-3mf-metadata` and `orca-metadata-benchmark`
before the heavy performance gate.

Desktop-Orca-identical pixels are not claimed by this gate. The current proof
is metadata/package parity plus compatible nonblank role thumbnails. The
renderer feasibility analysis is in
`docs/ORCA_THUMBNAIL_RENDERER_FEASIBILITY.md`.

## Required Fixture Cases

The manifest currently tracks these required cases:

- `simple-cube-png-gcode`: ordinary PNG thumbnail, print time, and filament
  usage.
- `qidi-q2-legacy-png-gcode`: Qidi legacy `thumbnail_size` normalization to
  Orca-style `thumbnail:150x150`, with `gimage` and `simage` forbidden.
- `non-qidi-qoi-gcode`: non-Qidi thumbnail format preservation through
  `thumbnail_QOI`.
- `sliced-3mf-metadata`: Bambu/Orca sliced 3MF assets:
  `plate_1.png`, `plate_1_small.png`, `plate_no_light_1.png`, `top_1.png`,
  `pick_1.png`, thumbnail relationships, `Metadata/plate_1.json`, and
  PNG-level visual checks for dimensions, visible pixels, transparent
  background, no-light brightness, and pick/top distinction.
- `multi-plate-sliced-3mf-metadata`: Bambu/Orca sliced 3MF package structure
  with `plate_1` and `plate_2` G-code entries, bbox JSON entries, normal/small
  plate thumbnails, no-light thumbnails, top thumbnails, and pick thumbnails.
  The fixture is intentionally printer-agnostic: it validates Orca package
  structure rather than a Qidi-specific host behavior.
- `multi-filament-gcode`: deterministic two-object/two-filament MobileSlicer
  automation export requiring a thumbnail, print time, filament usage, and
  toolchange metadata. Desktop Orca CLI cannot assign per-object extruders for
  this case without a project fixture, so the manifest validates the
  MobileSlicer export contract directly.

## Rules For Updating References

- Generate desktop Orca outputs from the same input model/profile case declared
  in the manifest.
- Record the exact Orca build or commit in `references/orca-version.txt` and
  `manifest.json` when changing the reference build.
- Do not hand-edit exported `.gcode` or `.gcode.3mf` files.
- Keep thumbnail payload checks separate from stripped G-code body checks. The
  current desktop references use official Qidi profiles while MobileSlicer
  fixture capture uses automation-safe Android profiles, so strict body-hash
  comparison is intentionally disabled until a same-profile desktop/mobile pair
  is committed.
- If a fixture capture exposes a metadata mismatch, fix the exporter path before
  updating fixture expectations. The QOI fixture is intentionally present to
  catch silent fallback to PNG.
