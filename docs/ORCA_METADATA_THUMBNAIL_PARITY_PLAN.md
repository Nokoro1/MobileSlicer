# Orca Metadata and Thumbnail Parity Plan

This plan defines how MobileSlicer should reach OrcaSlicer parity for exported
file metadata, G-code thumbnails, print time, filament/cost summaries, and
Bambu/Orca 3MF thumbnail assets without slowing slicing or mutating toolpath
G-code outside Orca's native export path.

## Goals

- Preserve Orca output behavior for print time, filament usage, layer count,
  toolchanges, cost, first-layer geometry metadata, and filename/template
  placeholders.
- Generate and embed G-code thumbnails in the same exporter-owned path Orca
  uses.
- Support printer-requested thumbnail dimensions and formats instead of forcing
  everything through MobileSlicer-specific PNG behavior.
- Preserve each printer profile's thumbnail contract. Vendor-specific
  compatibility modes must be opt-in or proven for a specific printer profile;
  the default export path must not replace Orca-requested PNG/JPG/QOI/BTT_TFT
  thumbnails with another vendor format.
- Preserve Bambu/Orca 3MF thumbnail assets and package relationships when
  MobileSlicer exports sliced 3MF output.
- Keep slice timing honest: thumbnail capture and metadata packaging must be
  measured separately from native slicing.
- Prove parity with desktop Orca through golden-output tests and diff tooling.

## Non-goals

- Do not post-process finished G-code in Kotlin to inject or rewrite thumbnail
  blocks.
- Do not recalculate print time or filament usage in Kotlin when Orca already
  produced the statistics.
- Do not change generated toolpaths to add metadata.
- Do not use one printer vendor's thumbnail workaround as a global export
  policy.
- Do not treat project-card thumbnails as equivalent to slicer/export
  thumbnails. Saved-project thumbnails may be useful UI assets, but they are
  not automatically valid G-code or 3MF export metadata.

## Orca References

The reference implementation is the vendored OrcaSlicer source in
`vendor/orcaslicer`, cross-checked against upstream OrcaSlicer source.

- `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.cpp`
  - Parses the `thumbnails` config string.
  - Validates `WxH/FORMAT` entries.
  - Supports multiple thumbnail formats through `GCodeThumbnailsFormat`.
  - Compresses thumbnails into printer-specific encodings.
- `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.hpp`
  - Defines `export_thumbnails_to_file`.
  - Calls the thumbnail callback with `ThumbnailsParams`.
  - Writes Orca G-code thumbnail block structure and tags.
- `vendor/orcaslicer/src/libslic3r/GCode.cpp`
  - Calls `GCodeThumbnails::make_and_check_thumbnail_list`.
  - Calls `GCodeThumbnails::export_thumbnails_to_file` only when a thumbnail
    callback is present.
- `vendor/orcaslicer/src/libslic3r/Print.cpp`
  - Exposes `Print::export_gcode(..., ThumbnailsGeneratorCallback)`.
- `vendor/orcaslicer/src/slic3r/GUI/Plater.cpp`
  - Desktop Orca renders thumbnail image pixels through
    `Canvas3D::render_thumbnail`.
  - Desktop Orca produces plate, no-light, top, and pick thumbnails during
    project/3MF export.
- `vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp`
  - Writes `Metadata/plate_N.png`, `Metadata/plate_no_light_N.png`,
    `Metadata/top_N.png`, `Metadata/pick_N.png`, and
    `Metadata/plate_N_small.png`.
  - Writes package relationships pointing to those thumbnail assets.
- Orca wiki placeholder documentation:
  - `print_time`, `normal_print_time`, `silent_print_time`,
    `total_layer_count`, `used_filament`, `extruded_weight_total`,
    `total_cost`, and related runtime fields are Orca print statistics.
  - Source: https://www.orcaslicer.com/wiki/developer_reference/built_in_placeholders_variables
- Upstream Orca source links used for research:
  - https://github.com/OrcaSlicer/OrcaSlicer
  - https://raw.githubusercontent.com/OrcaSlicer/OrcaSlicer/main/src/libslic3r/GCode/Thumbnails.cpp
  - https://github.com/OrcaSlicer/OrcaSlicer/blob/main/src/libslic3r/GCode/Thumbnails.hpp
  - https://github.com/OrcaSlicer/OrcaSlicer/blob/main/src/slic3r/GUI/Plater.cpp
  - https://github.com/OrcaSlicer/OrcaSlicer/blob/main/src/libslic3r/Format/bbs_3mf.cpp

## Current MobileSlicer State

MobileSlicer already preserves a meaningful part of the metadata surface, but
thumbnail generation is incomplete.

### Already present

- Printer import preserves thumbnail settings:
  - `android-app/app/src/main/java/com/mobileslicer/profiles/OrcaPrinterImportMapping.kt`
  - Imports `thumbnails`, `thumbnails_internal`, and
    `thumbnails_internal_switch`.
- Native config emission includes thumbnail keys:
  - `android-app/app/src/main/java/com/mobileslicer/profiles/NativeSlicePrinterConfiguration.kt`
  - Emits `thumbnails`, `thumbnails_format`, `thumbnails_internal`, and
    `thumbnails_internal_switch`.
- Native config override handling preserves thumbnail keys:
  - `engine-wrapper/orca_wrapper_config_json_overrides.h`
  - Applies `thumbnails` and `thumbnails_format` without blanking vendor
    G-code or thumbnail settings.
- Native applied-key tracking includes thumbnail keys:
  - `engine-wrapper/orca_wrapper_applied_key_manifest.h`
  - `android-app/app/src/main/java/com/mobileslicer/profiles/NativeSliceOrcaParityKeys.kt`
- Print time is taken from Orca's native `GCodeProcessorResult` where possible:
  - `engine-wrapper/orca_wrapper_slice_export_api.cpp`
  - Uses `gcode_result.print_statistics`.
- Android already models timing with a separate `thumbnailMs` field:
  - `android-app/app/src/main/java/com/mobileslicer/workspace/WorkspaceModels.kt`
- Plain `.gcode` slicing renders only Orca-requested G-code thumbnail buffers.
  Role-specific package thumbnails (`plate`, `no_light`, `top`, `pick`) are
  generated only for sliced `.gcode.3mf` export paths so normal slicing does not
  pay the extra package-metadata render cost.
- The strict desktop Orca package-thumbnail visual reference matrix is present
  under `regression-fixtures/orca-thumbnail-references/`.
  `scripts/run_orca_thumbnail_reference_matrix.sh <serial>` runs the release
  visual gate against all checked-in reference cases with
  `MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1`.
- The matrix is data-driven from
  `regression-fixtures/orca-thumbnail-references/matrix.json`, and
  `scripts/verify_android.sh orca-thumbnail-reference-fixtures` validates the
  checked-in desktop reference package, manifest, model, and role PNGs before
  any ADB run.
- The Android EGL thumbnail renderer now uses role-specific Orca-style
  framing:
  - plain G-code thumbnails use the same angled object-box framing as Orca's
    package `plate` preview, and this is the production renderer used during
    normal Slice,
  - `top` and `pick` use whole-bed top projection,
  - `plate` and `no_light` use object-box package framing,
  - normal G-code exports still render only the Orca-requested G-code thumbnail
    sizes, not package-only roles.
- Production G-code thumbnail rendering does not use a software fallback. The
  Android release gates assert `renderers=offscreen_egl` for plain `.gcode`,
  QOI `.gcode`, and sliced `.gcode.3mf` automation runs.
- Android already lazily enriches heavy preview summary data only when needed:
  - `android-app/app/src/main/java/com/mobileslicer/workspace/WorkspacePreviewEffects.kt`
  - This pattern should be reused for non-critical metadata inspection.
- Native thumbnail request parsing is exposed for implementation and audit work:
  - `orca_get_thumbnail_requests_json`
  - `NativeEngineBridge.nativeGetThumbnailRequestsJson`
  - `NativeEngineCalls.getThumbnailRequests`
  - This API reports Orca-parsed thumbnail width, height, format, original
    config values, and validation errors. It does not render or embed image
    data yet.
- Metadata audit scaffolding exists:
  - `scripts/orca_metadata_audit.py`
  - This script strips thumbnail payloads, hashes the remaining G-code body,
    reports thumbnail blocks, decodes embedded PNG G-code thumbnail metrics,
    and inspects sliced 3MF thumbnail, relationship, ZIP entry, bbox JSON
    metadata, and PNG-level visual metrics.
- Golden fixture gate exists and is active:
  - `regression-fixtures/orca-metadata/manifest.json`
  - `scripts/orca_metadata_fixture_gate.py`
  - Non-strict mode validates the manifest and any present outputs.
  - Strict mode fails missing desktop Orca references, missing MobileSlicer
    fixture outputs, and pending cases. The normal local/release gate now runs
    this strict mode.
  - `scripts/verify_android.sh orca-fixture-capture-mobile <serial>` captures
    the supported MobileSlicer outputs on device and audits them immediately.
- Visible G-code metadata parity audit exists:
  - `scripts/orca_gcode_metadata_parity_audit.py`
  - `scripts/verify_android.sh orca-gcode-metadata-parity`
  - This audit extracts print-time seconds, filament usage/cost numbers, layer
    count, generator markers, toolchange commands, thumbnail signatures, and
    stripped body hashes from committed G-code fixtures.
  - When a case has both desktop Orca and MobileSlicer outputs, the audit
    reports value drift separately from hard failures. `--fail-on-drift` is
    intentionally opt-in until the desktop Orca and MobileSlicer fixtures are
    generated from exactly matched printer, filament, and process profiles.
- Android release gating now includes the metadata-specific device checks:
  - `scripts/verify_android.sh printer-thumbnail-compatibility` audits
    `regression-fixtures/printer-thumbnail-compatibility/matrix.json` so
    printer support claims remain separated into `proven_live`, `gated`,
    `source_supported`, `pending_live_validation`, and `not_supported`. This
    prevents source-level support for BTT/JPG/ColPic from being presented as
    full printer compatibility before generated-output or live-host evidence
    exists.
  - `scripts/verify_android.sh sliced-3mf-metadata <serial>` verifies package
    thumbnail entries, package relationships, `Metadata/plate_1.json`, and
    PNG visual sanity.
  - `scripts/verify_android.sh fluidd-thumbnail-metadata <serial>` verifies one
    release-stable plain G-code export with the Fluidd/Moonraker
    `48x48/PNG,300x300/PNG` pair, decoded visual/detail metrics, and thumbnail
    timing under `MOBILE_SLICER_METADATA_FLUIDD_THUMBNAIL_MAX_MS`. This gate
    also requires the G-code generator line to contain an OrcaSlicer-compatible
    MobileSlicer marker, because QIDI and other vendor Moonraker forks commonly
    select their thumbnail parser from known slicer aliases before indexing
    Fluidd thumbnails. When `MOBILE_SLICER_MOONRAKER_URL` is set, the same gate
    uploads the exact generated G-code to that Moonraker host, polls
    `/server/files/metadata`, and fails unless Moonraker reports both Fluidd
    thumbnails. This is the release proof that Fluidd can see the images after
    host-side indexing, not just that the local file contains valid blocks.
  - `scripts/verify_android.sh orca-metadata-benchmark <serial>` verifies that
    plain G-code exports log `packageThumbnails=false`, sliced 3MF exports log
    `packageThumbnails=true`, Fluidd/Moonraker G-code exports contain both
    `48x48/PNG` and `300x300/PNG` thumbnail blocks, and thumbnail/write timing
    stays under the configured thresholds. This benchmark is broader than the
    dedicated Fluidd gate and is useful for audits, but the release gate uses
    the single-case Fluidd check to avoid multi-case device package-disabler
    flakiness on Samsung devices.
- Android export now preserves QOI thumbnail requests in the native path:
  - direct JSON requests such as `thumbnails=96x96` plus
    `thumbnails_format=QOI` are normalized to `96x96/QOI`,
  - the Android libslic3r thumbnail stub emits `thumbnail_QOI` blocks using a
    native QOI encoder instead of falling back to PNG,
  - the fixture and benchmark gates now require the embedded payload to start
    with the QOI file signature, so a mislabeled PNG/JPG/text payload cannot
    pass as QOI parity.
- Android export now has native support and device-capture gates for JPG,
  BTT_TFT, and Qidi ColPic thumbnail requests:
  - JPG requests are encoded with libjpeg-turbo and emitted as Orca-style
    `thumbnail_JPG` blocks,
  - JPG fixture and benchmark gates require a real JPEG SOI/EOI payload inside
    the `thumbnail_JPG` block, not just the correct tag name,
  - BTT_TFT requests emit the BTT header/end structure with the requested
    dimensions,
  - ColPic requests emit the Qidi `gimage`/`simage` pair.
  - `scripts/verify_android.sh orca-fixture-capture-mobile <serial>` captures
    generated fixtures for all three formats, and
    `scripts/verify_android.sh orca-metadata-benchmark <serial>` verifies their
    thumbnail scope and timing budgets.
  - The Android-generated JPG, BTT_TFT, and ColPic fixtures are now gated by
    the strict metadata fixture gate and the printer compatibility matrix.
- Device audit on the Qidi Q2 slice showed the Android output contained
  `380x380/COLPIC`, `210x210/COLPIC`, and `110x110/PNG`, but no
  `150x150/PNG` block. Upstream Orca's Qidi Q2 profile stores
  `thumbnail_size = 150x150` plus `thumbnails_format = PNG`, and
  `PrintConfigDef::handle_legacy` maps `thumbnail_size` to `thumbnails`.
  Android must perform the same normalization before requesting/rendering
  thumbnails.
- The same Qidi Q2 audit showed `printer_settings_id = Qidi` in the emitted
  config block. Orca profile identity normalization must end with the concrete
  machine name, such as `Qidi Q2 0.4 nozzle`, so later restore passes do not
  reintroduce the generic vendor id.
- Regression coverage now enforces printer-specific thumbnail behavior:
  - Qidi Q2 legacy `thumbnail_size` restores to Orca's requested `150x150/PNG`
    default.
  - Non-Qidi legacy `thumbnail_size` restores to the requested format.
  - Non-Qidi explicit `thumbnails` entries preserve their Orca profile formats
    and do not receive vendor-specific overrides.

### Known gaps

- Bambu/Orca 3MF plate thumbnail generation, package relationship parity, and
  first-layer bbox JSON are wired through Orca's native `store_bbs_3mf` path.
  The checked-in package-thumbnail visual matrix now passes against desktop
  Orca references. The remaining visual gap is fixture breadth:
  multi-filament, true rotated-object angled views, and desktop-Orca
  multi-plate package thumbnails.
- The current fixture gate checks baseline PNG visual correctness: valid
  dimensions, nonblank visible pixels, transparent background, darker no-light
  render, and a pick render that is visually distinct from the top render.
  This catches empty or role-collapsed thumbnails, but it is not a desktop
  Orca pixel-diff.
- G-code thumbnail callbacks are wired through Orca's native exporter and the
  fixture gate proves PNG and QOI metadata presence. The remaining thumbnail
  work is visual fidelity and broader format/profile coverage.
- Desktop Orca's own thumbnail renderer is not currently embedded on Android.
  The feasibility finding is documented in
  `docs/ORCA_THUMBNAIL_RENDERER_FEASIBILITY.md`: the renderer lives in Orca's
  GUI/OpenGL `Canvas3D` stack, while MobileSlicer currently builds the headless
  slice/export core plus a lightweight Android thumbnail renderer.
  The step-by-step renderer port plan is documented in
  `docs/ORCA_CANVAS3D_THUMBNAIL_PORT_PLAN.md`.
- Differentiated multi-plate sliced 3MF package structure is implemented and
  release-gated through `multi-plate-sliced-3mf-metadata`. That gate proves
  distinct `Metadata/plate_1.gcode`, `Metadata/plate_2.gcode`,
  `Metadata/plate_1.json`, and `Metadata/plate_2.json`. The remaining
  multi-plate gap is a desktop-Orca visual reference fixture for per-plate
  package thumbnails.

## Metadata Surfaces

Treat these surfaces separately. They have different timing, correctness, and
parity requirements.

### 1. G-code comments and runtime statistics

Examples:

- Estimated print time.
- Silent/normal print time.
- Filament length, volume, weight, and cost.
- Layer count and toolchange count.
- First-layer geometry placeholders.
- Printer, filament, and process preset identifiers.
- Filename-template placeholders.

Required approach:

- Prefer native Orca data from `GCodeProcessorResult` and Orca placeholder
  expansion.
- Kotlin may parse and display native summaries, but it must not invent
  replacement statistics when native values exist.
- If fallback parsing remains necessary, it must be treated as display recovery,
  not as canonical export metadata.

### 2. G-code thumbnails

Examples:

- `; thumbnail begin ...`
- `; thumbnail_QOI begin ...`
- `; thumbnail_JPG begin ...`
- `;gimage:` / `;simage:` ColPic/QIDI payloads.
- BTT TFT thumbnail blocks.

Required approach:

- Use Orca's native thumbnail request parsing.
- Render pixels on Android or in native code.
- Pass raw RGBA thumbnail data back to Orca as `ThumbnailData`.
- Let Orca's `export_thumbnails_to_file` compress and write the thumbnail
  blocks.

### 3. Bambu/Orca sliced 3MF metadata

Examples:

- `Metadata/plate_1.png`
- `Metadata/plate_1_small.png`
- `Metadata/plate_no_light_1.png`
- `Metadata/top_1.png`
- `Metadata/pick_1.png`
- First-layer bounding-box JSON.
- `_rels/.rels` thumbnail relationships.

Required approach:

- Treat this as a separate exporter integration from plain G-code thumbnails.
- Match Orca's `StoreParams` thumbnail arrays and package relationship behavior.
- Do not assume a G-code thumbnail is sufficient for a sliced 3MF package.

### 4. MobileSlicer UI/project thumbnails

Examples:

- Saved-project card thumbnails.
- Home screen recent-project thumbnails.
- Import/search result thumbnails.

Required approach:

- Keep these separate from export metadata.
- They can share renderer utilities only when the camera, dimensions,
  background, colors, and model state are explicitly compatible.

## Architecture Plan

### Phase 0: Parity audit and fixture setup

Deliverables:

- Maintain a metadata parity fixture manifest with at least:
  - one simple STL,
  - one large STL,
  - one printer requesting PNG thumbnails,
  - one printer requesting non-PNG thumbnail format,
  - one Bambu/Orca sliced 3MF case,
  - one multi-filament or toolchange case.
- Add scripts or tests that validate:
  - desktop Orca reference output when present,
  - MobileSlicer native output when present,
  - stripped G-code body hashes with thumbnail blocks removed,
  - metadata diff reports,
  - missing desktop Orca references in strict mode.
- Record the exact Orca commit/build used for each reference output.

Current status:

- `scripts/orca_metadata_fixture_gate.py` and
  `regression-fixtures/orca-metadata/manifest.json` are present.
- `regression-fixtures/orca-metadata/mobile/` contains captured MobileSlicer
  outputs for PNG, Qidi-normalized PNG, QOI, and sliced 3MF metadata cases when
  `orca-fixture-capture-mobile` has been run.
- `scripts/verify_android.sh local` runs the non-strict fixture gate.
- `MOBILE_SLICER_STRICT_ORCA_FIXTURES=1 scripts/verify_android.sh
  orca-fixture-gate` is the switch for enforcing committed desktop Orca
  references after they are populated.

Current status:

- `scripts/orca_metadata_audit.py` can inspect MobileSlicer and Orca G-code or
  sliced 3MF files and produce JSON diff data.
- The committed fixture matrix still needs to be added before Phase 0 is
  complete.

Acceptance gates:

- We can prove which metadata fields are already matching.
- We can prove which thumbnail blocks/assets are missing.
- We can prove whether toolpath G-code changes when thumbnail support is
  enabled.

### Phase 1: Native thumbnail request API

Deliverables:

- Add a native API that exposes Orca-parsed thumbnail requests from the active
  config.
- The API must return:
  - requested width,
  - requested height,
  - requested `GCodeThumbnailsFormat`,
  - Orca validation errors,
  - the original config string for diagnostics.
- Kotlin must not implement its own independent parser for `thumbnails`.

Current status:

- `orca_get_thumbnail_requests_json` exposes Orca-parsed thumbnail requests from
  the current native config JSON.
- Android parses that JSON into `NativeThumbnailRequestSummary`.
- This phase still needs a native/device smoke test against a real engine handle
  after the Android native build is verified.

Acceptance gates:

- Unit tests cover empty thumbnails, valid PNG, valid non-PNG, malformed input,
  and out-of-range dimensions.
- The returned requests match Orca's `make_and_check_thumbnail_list` behavior.
- Invalid thumbnail config fails the same way Orca fails, or is explicitly
  documented as a compatibility exception.

### Phase 2: Android thumbnail rendering source

Deliverables:

- Add a renderer path that can produce RGBA pixel buffers for requested
  thumbnail sizes.
- Start with plain G-code export thumbnails.
- Match Orca's render intent as closely as practical:
  - orthographic camera,
  - plate-framed model,
  - printable-only/parts-only behavior from `ThumbnailsParams`,
  - transparent background where requested,
  - bed visibility where requested,
  - current object transforms and colors.
- Track render timing separately as `thumbnailMs`.

Current status:

- `SliceThumbnailRenderer` provides an initial Android-side RGBA thumbnail
  source for Orca-requested thumbnail sizes.
- The slice pipeline now:
  - reads `NativeEngineCalls.getThumbnailRequests` after native config is set,
  - skips rendering when Orca reports no thumbnail requests,
  - skips rendering when Orca reports thumbnail validation errors,
  - renders RGBA buffers only after native slicing succeeds,
  - reports render time as `thumbnailMs`,
  - logs request/render counts and rendered byte totals.
- This phase is not export-complete yet. The generated RGBA buffers are not
  passed to Orca's G-code thumbnail callback until Phase 3.
- The current renderer is a bounded software top-down orthographic source. It
  is suitable for proving request handling, timing, and buffer ownership before
  callback integration. Pixel-level Orca visual parity still requires either a
  deeper Canvas3D-compatible camera/light implementation or a deterministic
  offscreen GL renderer.

Implementation options:

- Reuse the existing workspace OpenGL renderer with an offscreen framebuffer.
- Use a dedicated native/offscreen renderer if matching Orca camera and lighting
  becomes easier there.
- Avoid using captured UI screenshots as the canonical path unless the capture
  is deterministic and matches requested size/camera/background.

Acceptance gates:

- Rendering does not block or inflate `nativeSliceMs`.
- Rendering is skipped when no thumbnail requests exist.
- Rendering works for large exact-preview meshes without reintroducing
  decimation-only behavior.
- Thumbnails are deterministic enough for regression testing within a defined
  tolerance.

### Phase 3: G-code thumbnail callback integration

Deliverables:

- Store rendered thumbnail buffers on the native engine handle before export.
- Replace the current `nullptr` thumbnail callback with a callback that returns
  `ThumbnailsList` for Orca's requested `ThumbnailsParams`.
- Ensure Orca's native exporter still owns:
  - validation,
  - compression,
  - block tags,
  - base64 line wrapping,
  - cancellation checks,
  - file writing.

Current status:

- Android renders requested thumbnail RGBA buffers before native slicing starts.
- Android normalizes resolved Orca `thumbnail_size` entries into
  printer-visible `thumbnails` entries with `thumbnails_format`, matching
  Orca's legacy config conversion path.
- Android reapplies Orca profile identity defaults after final resolved-value
  restore so generic Qidi `printer_settings_id` values do not overwrite the
  concrete machine name.
- `NativeEngineCalls.clearSliceThumbnails` clears stale native thumbnail buffers
  before each slice request.
- `NativeEngineCalls.addSliceThumbnailRgba` uploads rendered buffers to the
  native engine handle.
- Native stores uploaded buffers on `OrcaEngineImpl::slice_thumbnails`.
- `Print::export_gcode` now receives a real `ThumbnailsGeneratorCallback` when
  buffers are available instead of always receiving `nullptr`.
- The callback converts matching uploaded RGBA buffers into Orca
  `ThumbnailData` for the requested thumbnail size. Orca still performs the
  compression and G-code block writing through `GCodeThumbnails`.
- `clear_generated_gcode` intentionally does not clear uploaded thumbnail
  buffers, because native slicing clears generated G-code at the start of
  `orca_slice`. Android is responsible for replacing/clearing thumbnail buffers
  before each slice request.
- Runtime diagnostics now log parsed thumbnail requests, validation errors,
  upload counts, package-thumbnail scope, render timing, and output marker
  counts. Automation status includes a compact `thumbnailAudit` token so
  CI/device smoke runs can verify the emitted file without manually opening it.
- `scripts/orca_metadata_audit.py` can now enforce expected thumbnail
  signatures, forbidden thumbnail tags, print-time metadata, and filament usage
  metadata. This converts Qidi Q2's Orca contract into an executable check:
  `thumbnail:150x150` is required and `gimage`/`simage` are forbidden.
- PNG G-code fixture outputs can require decoded visual metrics and anti-alias
  variation. The fixture manifest now applies that to the 128px PNG and Qidi Q2
  `150x150/PNG` cases, while desktop Orca CLI references remain metadata-only
  because Orca CLI has no thumbnail callback.

Remaining Phase 3 verification:

- Add committed G-code fixture pairs so `scripts/orca_metadata_audit.py` can
  run without an attached device.
- Run `scripts/orca_metadata_audit.py` on before/after output and confirm that
  the stripped non-thumbnail G-code body hash is unchanged for the fixture
  matrix.
- Add a committed golden fixture once the device output is captured.

Acceptance gates:

- PNG G-code thumbnails appear in output using Orca's native block format.
- Stripping thumbnail blocks produces the same G-code body hash as the
  no-thumbnail export.
- Print time, filament usage, and layer count remain unchanged.
- `writeGcodeMs`, `thumbnailMs`, and total timing are logged separately.

### Phase 4: Full thumbnail format parity

Current status:

- Orca's native compression/writer path handles PNG/JPG/QOI/BTT_TFT/COLPIC
  once Android supplies RGBA pixels for the requested size.
- Android currently renders a deterministic software thumbnail. It is useful
  for metadata and printer preview presence, but it is not yet visually
  equivalent to Orca desktop's `Canvas3D::render_thumbnail`.
- The native callback currently matches by requested dimensions and supplies
  the uploaded RGBA buffer. Full `ThumbnailsParams` semantics such as
  `printable_only`, `parts_only`, `show_bed`, `transparent_background`,
  `plate_id`, and `use_plate_box` remain open.

Deliverables:

- Remove or replace Android thumbnail stubs that normalize unsupported formats
  to PNG.
- Support Orca thumbnail formats used by upstream:
  - PNG,
  - JPG,
  - QOI,
  - BTT TFT,
  - ColPic/QIDI.
- Use Orca's compression/writer code wherever possible.
- Replace or upgrade Android thumbnail rendering so camera, bed visibility,
  transparency, plate selection, object filtering, and colors follow Orca's
  `ThumbnailsParams`.

Acceptance gates:

- Each supported format has a fixture and output validation.
- Unsupported formats are not silently rewritten as PNG.
- Printer-requested tags match Orca output.
- QIDI/ColPic and BTT TFT outputs are validated against known-good Orca output
  or printer-compatible fixtures.
- Visual thumbnail parity is either proven against desktop Orca snapshots or
  explicitly documented as approximate for the release being tested.

### Phase 5: Bambu/Orca sliced 3MF parity

Deliverables:

- Add a native path for plate thumbnail arrays equivalent to Orca's
  `StoreParams` fields:
  - `thumbnail_data`,
  - `no_light_thumbnail_data`,
  - `top_thumbnail_data`,
  - `pick_thumbnail_data`,
  - `calibration_thumbnail_data` if needed,
- first-layer bounding boxes.
- Generate or supply:
  - main plate thumbnails,
  - no-light thumbnails,
  - top thumbnails,
  - pick thumbnails,
  - small thumbnails.
- Preserve relationship entries that point package thumbnail metadata to the
  correct plate assets.

Current status:

- Native 3MF export now populates Orca's `StoreParams` thumbnail arrays when
  Android has uploaded rendered slice thumbnails:
  - `thumbnail_data` produces `Metadata/plate_1.png` and Orca's generated
    `Metadata/plate_1_small.png`.
  - `no_light_thumbnail_data` produces `Metadata/plate_no_light_1.png`.
  - `top_thumbnail_data` produces `Metadata/top_1.png`.
  - `pick_thumbnail_data` produces `Metadata/pick_1.png`.
- The implementation deliberately routes these assets through Orca's
  `store_bbs_3mf` writer instead of adding files to the ZIP from Kotlin. Orca
  remains responsible for PNG encoding, small-thumbnail generation, package
  naming, and `_rels/.rels` targets.
- Android now uploads role-specific thumbnail buffers: `gcode`, `plate`,
  `no_light`, `top`, and `pick`. Native G-code export consumes only the `gcode`
  role, while sliced 3MF export selects the package roles for the matching
  `StoreParams` arrays.
- The wrapper upscales package thumbnails only when needed to satisfy Orca's
  128x128 small thumbnail generator. This prevents Orca's integer downsample
  path from seeing a zero scale factor on profiles that request thumbnails
  smaller than 128 px.
- `scripts/orca_metadata_audit.py` can now fail sliced 3MF outputs that omit
  required Orca package assets or relationship targets. The expected sliced
  package contract for one plate is:
  - `Metadata/plate_1.png`
  - `Metadata/plate_1_small.png`
  - `Metadata/plate_no_light_1.png`
  - `Metadata/top_1.png`
  - `Metadata/pick_1.png`
  - relationship targets `/Metadata/plate_1.png` and
    `/Metadata/plate_1_small.png`
- Native slicing now builds first-layer `PlateBBoxData` from the processed
  Orca `Print` and `GCodeProcessorResult`, then sliced 3MF export attaches it to
  `StoreParams::id_bboxes`. Orca's writer emits `Metadata/plate_1.json`.
- The audit script can now enforce `Metadata/plate_1.json` and validate the
  required Orca fields: `bbox_all`, `bbox_objects`, `filament_ids`,
  `filament_colors`, `is_seq_print`, `first_extruder`, `nozzle_diameter`,
  `bed_type`, `first_layer_time`, and `version`.
- `scripts/verify_android.sh sliced-3mf-metadata` now proves this on a physical
  device by exporting a sliced `.gcode.3mf`, pulling it, and running the audit
  against the package. The audit also requires the checked package thumbnails
  not to be all byte-identical, which catches regressions to one-buffer 3MF
  thumbnail export.
- Native multi-plate sliced 3MF export now accepts a per-plate manifest with
  source G-code, Orca bbox JSON, and MobileSlicer object ids. The writer maps
  those object ids back onto Orca model objects before calling Orca's sliced
  3MF writer, so each package plate can own the correct object membership
  instead of duplicating the whole workspace.
- `scripts/verify_android.sh multi-plate-sliced-3mf-metadata` now proves a
  differentiated two-plate package on device. It slices plate 1 and plate 2
  separately, shifts the second object, changes the second layer height, writes
  one `.gcode.3mf`, and audits that:
  - `Metadata/plate_1.gcode` and `Metadata/plate_2.gcode` both exist and are
    byte-distinct,
  - `Metadata/plate_1.json` and `Metadata/plate_2.json` both exist and are
    byte-distinct,
  - each plate JSON contains the required Orca bbox fields,
  - per-plate role thumbnail entries exist.

Remaining Phase 5 gaps:

- The package thumbnails are role-specific, but they are still generated by
  MobileSlicer's bounded software renderer. Pixel-level visual parity with
  desktop Orca's separate `Canvas3D::render_thumbnail` modes is not complete.
- A committed sliced 3MF golden fixture is still required so this check can run
  without relying on an attached Android device.
- The single-plate and multi-plate device smokes exist, but they still need to
  be promoted into the default release gate once runtime cost is acceptable for
  routine runs.

Acceptance gates:

- Sliced 3MF ZIP contents match Orca's expected thumbnail file names.
- Relationship XML points to the same categories of thumbnail assets as Orca.
- `Metadata/plate_1.json` exists and contains a non-empty `bbox_objects` array
  plus all required Orca bbox fields.
- Multi-plate package exports contain byte-distinct per-plate G-code and bbox
  JSON when the workspace plates differ.
- Package thumbnail entries are not all byte-identical.
- Missing thumbnail data fails visibly in tests rather than producing a
  silently incomplete package.

### Phase 6: Display and diagnostics

Deliverables:

- Expand Android summary display only after native canonical data exists.
- Keep heavy preview/detail enrichment lazy, following the existing
  `WorkspacePreviewEffects` pattern.
- Add diagnostics for:
  - thumbnail requests,
  - thumbnail render duration,
  - thumbnail encode/write duration,
  - metadata source for print time,
  - whether output includes thumbnail blocks/assets.

Acceptance gates:

- Users see metadata without waiting for unnecessary background analysis.
- Logs make it clear whether missing thumbnails are due to config, rendering,
  native callback, or output packaging.

## Verification Strategy

### Golden parity tests

For each fixture:

- Slice with desktop Orca reference build.
- Slice with MobileSlicer native wrapper.
- Compare:
  - full G-code metadata sections,
  - thumbnail block count,
  - thumbnail dimensions,
  - thumbnail tags,
  - print time fields,
  - filament fields,
  - layer count,
  - toolchange count,
  - config block preservation,
  - Bambu 3MF ZIP entries and relationships.

Current visible-metadata audit:

```sh
python3 scripts/orca_gcode_metadata_parity_audit.py --pretty
```

Strict drift mode:

```sh
python3 scripts/orca_gcode_metadata_parity_audit.py --pretty --fail-on-drift
```

Desktop Orca references and MobileSlicer fixture outputs now share the baseline
fixture config in:

```text
regression-fixtures/orca-metadata/mobile-baseline-config.json
```

`scripts/generate_orca_reference_fixtures.sh` translates that config into Orca
machine/process/filament profile JSON under
`regression-fixtures/orca-metadata/reference-inputs/`, including source and
generated SHA-256 hashes. That makes print-time and filament comparisons
meaningful. Orca CLI still cannot be the G-code thumbnail pixel reference
because its CLI export path does not pass a thumbnail callback; the audit marks
that as `reference_unavailable`, while MobileSlicer thumbnail blocks remain
validated against the Orca source contract and device fixture outputs.

### Toolpath safety tests

For each G-code fixture:

- Strip thumbnail blocks from both outputs.
- Normalize only known volatile fields:
  - timestamps,
  - absolute temp/cache paths,
  - generated file names if Orca differs by environment.
- Hash the remaining G-code body.
- Fail if enabling thumbnails changes non-thumbnail G-code.

### Performance tests

Record:

- `modelReloadMs`
- `configMs`
- `nativeSliceMs`
- `thumbnailMs`
- `writeGcodeMs`
- `summaryMs`
- total pipeline time
- output byte count

Fail or warn when:

- thumbnail rendering runs with no thumbnail requests,
- thumbnail rendering is counted as native slicing,
- thumbnail generation scales unexpectedly with G-code line count instead of
  requested image size/model render cost.

### Device tests

Run on at least:

- current wireless ADB test device,
- one lower-memory Android target,
- one large STL case,
- one printer profile requesting multiple thumbnails.

Check:

- no crash,
- no GL context leaks,
- no blank thumbnails,
- no corrupted G-code,
- no unreasonable memory spike.

## Risks and Mitigations

### Risk: Android renderer does not visually match desktop Orca

Mitigation:

- Define parity target precisely:
  - format parity and metadata correctness are required,
  - pixel-perfect desktop thumbnail parity may require porting Orca camera and
    lighting behavior more deeply.
- Use Orca's `ThumbnailsParams` as the contract for camera/background/model
  inclusion.
- Add screenshot/image-diff tolerances instead of pretending raw PNG bytes will
  always match across GL implementations.

### Risk: G-code post-processing corrupts output

Mitigation:

- Do not use Kotlin string insertion for canonical thumbnail support.
- Feed image data into Orca's native thumbnail callback.
- Verify stripped G-code body hashes.

### Risk: PNG-only implementation is mistaken for parity

Mitigation:

- Keep PNG-only as an explicit intermediate milestone.
- Track non-PNG formats as blocking work for "100% Orca parity".
- Remove silent format normalization before declaring parity complete.

### Risk: thumbnails slow slicing

Mitigation:

- Generate only when the printer profile requests thumbnails.
- Time thumbnail rendering separately.
- Keep native slicing and G-code generation metrics distinct.
- Cache only when the cache key includes model geometry, transforms, colors,
  plate, camera, requested size, requested format, and relevant printer/bed
  context.

### Risk: 3MF metadata is conflated with G-code metadata

Mitigation:

- Track G-code thumbnail parity and Bambu/Orca 3MF parity as separate phases.
- Verify ZIP entries and relationships for 3MF.

## Implementation Order

1. Add parity audit fixtures and diff tooling.
2. Add native thumbnail request API using Orca parser.
3. Add Android/offscreen RGBA thumbnail rendering for PNG requests.
4. Wire native thumbnail callback into `Print::export_gcode`.
5. Prove PNG thumbnail G-code parity and toolpath safety.
6. Add non-PNG thumbnail format support.
7. Add Bambu/Orca sliced 3MF thumbnail package support.
8. Expand Android metadata display and diagnostics.
9. Update release gates so metadata parity cannot regress.

## Definition of Done

This work is done only when:

- MobileSlicer passes golden metadata parity tests against desktop Orca for the
  agreed fixture matrix.
- G-code thumbnail blocks are generated through Orca's native writer path.
- Non-thumbnail G-code body hashes remain unchanged when thumbnails are
  enabled.
- Printer-requested thumbnail formats are honored or explicitly rejected with a
  documented incompatibility.
- Bambu/Orca sliced 3MF thumbnail files and relationships match Orca behavior.
- Print time and filament display come from native Orca statistics or a clearly
  documented fallback.
- Timing logs separately identify slicing, thumbnail rendering, writing, and
  summary parsing.
- The release audit includes metadata/thumbnail parity checks.

## Immediate Next Step

Add a proven desktop-Orca multi-filament visual fixture and gate it against
MobileSlicer.

Required proof:

- The source `.gcode.3mf` must contain the four desktop Orca package roles:
  `Metadata/plate_1.png`, `Metadata/plate_no_light_1.png`,
  `Metadata/top_1.png`, and `Metadata/pick_1.png`.
- The package must prove at least two active filaments in all three canonical
  metadata surfaces:
  - `Metadata/plate_1.json`: at least two `filament_ids` and
    `filament_colors`.
  - `Metadata/slice_info.config`: at least two `<filament ...>` entries with
    ids and colors.
  - `Metadata/model_settings.config`: at least two object `extruder` metadata
    values.
- `scripts/extract_orca_thumbnail_references.py --require-active-filaments 2`
  must accept the package before it is checked in.
- `scripts/probe_orca_active_multifilament_reference.sh` is the executable
  desktop-Orca probe for this step. It generates two small STL objects, tries
  both direct multi-input loading and Orca's `--load-assemble-list` path
  against known multi-nozzle desktop profiles, and only writes
  `regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects`
  after extraction proves the active-filament contract.
- `scripts/orca_thumbnail_reference_fixture_audit.py` must pass the matrix
  case with `source_layout=two_filament_objects`.
- The Android matrix must pass with
  `MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/run_orca_thumbnail_reference_matrix.sh <serial>`.

This keeps multi-filament parity honest: a desktop package that merely records
two object assignments but collapses `plate_1.json` or `slice_info.config` to
one active filament is rejected instead of becoming a false reference.

Latest local probe:

- Command:
  `scripts/probe_orca_active_multifilament_reference.sh`
- Artifact:
  `artifacts/orca-thumbnail-reference-capture/active-multifilament-reference-probe-20260515-072144`
- Result:
  the Bambu Lab H2D direct multi-input path produced a valid desktop Orca
  `.gcode.3mf` package through `vendor/orcaslicer/build/package/bin/orca-slicer`.
  The extracted reference is checked in at
  `regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects`.
- Interpretation:
  MobileSlicer now has a real desktop Orca active multi-filament visual
  reference instead of a guessed two-color Android-only target. The fixture is
  part of `regression-fixtures/orca-thumbnail-references/matrix.json` with
  `source_layout=two_filament_objects`, `source_colors=#F2754E,#4EA3F2`, and
  `requirements.active_filaments=2`.
- Device validation:
  the strict Android matrix passed on 2026-05-15 with
  `MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/run_orca_thumbnail_reference_matrix.sh 100.123.18.83:42917`.
  The active multi-filament case produced
  `artifacts/egl-thumbnail-compare/20260515-071948`, and the non-Qidi Creality
  K1 case produced `artifacts/egl-thumbnail-compare/20260515-072002`.
- Harness note:
  the matrix runner must preserve empty optional profile fields before parsing
  bed/source-layout columns. The active multi-filament device fixture also
  mirrors the desktop Orca package's object placement: two colored objects
  centered on X and offset on Y.
- AppImage note:
  the Orca 2.3.2 AppImage still exits with status 139 for this generated
  package in the local Linux environment. The probe therefore prefers the
  vendored package binary unless `ORCA_SLICER_BIN` is explicitly set.
