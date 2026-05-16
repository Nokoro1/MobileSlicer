# Orca Parity Deep Audit and Next Steps

Start a resumed Orca parity session from
`docs/ORCA_PARITY_RESUME_HANDOFF.md`, then use this file for the deeper audit
context.

This audit is based on the vendored OrcaSlicer source in `vendor/orcaslicer`,
the current MobileSlicer wrapper/app implementation, and the device gates run
against `100.123.18.83:42917` on 2026-05-15. The goal is to keep the next work
ordered by Orca's real behavior, not by whichever UI symptom was most recent.

## Bottom Line

MobileSlicer is no longer blocked on basic thumbnail or sliced-3MF metadata
wiring. The current local/device gates prove:

- G-code thumbnail callbacks are wired through Orca's native export callback.
- Plain `.gcode` export renders only requested G-code thumbnail blocks.
- Sliced `.gcode.3mf` export renders Orca package thumbnail roles.
- Single-plate and two-plate sliced packages include Orca-shaped
  `Metadata/plate_N.json` files with bbox, filament, nozzle, bed, and
  first-layer timing fields.
- Desktop-Orca thumbnail reference comparison exists and passes for the current
  matrix, including an active two-filament project fixture and a non-Qidi
  Creality fixture.
- 3MF project preservation now has both local and Android-device proof for the
  combined interaction fixture: multi-plate objects, filament assignments,
  object settings, modifier settings, height ranges, project settings, and
  project thumbnails survive one MobileSlicer import/export round trip.

This is solid parity coverage for the current fixture matrix. It is not
complete Orca parity. The next best work is broadening printer and host
compatibility evidence without weakening the existing project/workspace gates.

## Verified On 2026-05-15

The following follow-up gate pass was completed on local branch
`orca-parity-local-checkpoint` after local checkpoint commit `01c9c0bd`
(`Checkpoint Orca parity work`). Nothing was pushed to a remote.

- `scripts/verify_android.sh local`
- `scripts/verify_android.sh orca-project-parity-device-matrix 100.123.18.83:42917`
- `scripts/verify_android.sh sliced-3mf-metadata 100.123.18.83:42917`
- `scripts/verify_android.sh multi-plate-sliced-3mf-metadata 100.123.18.83:42917`
- `scripts/verify_android.sh fluidd-thumbnail-metadata 100.123.18.83:42917`
- `scripts/verify_android.sh orca-metadata-benchmark 100.123.18.83:42917`
- `MOBILE_SLICER_MOONRAKER_URL=http://100.112.193.10:10088 scripts/verify_android.sh fluidd-thumbnail-metadata 100.123.18.83:42917`

The live Moonraker/Fluidd check reached `/server/info`, confirmed Klippy was
ready, uploaded the generated G-code without starting a print, waited for
Moonraker metadata, verified reported thumbnail dimensions including `48x48`
and `300x300`, and deleted the uploaded test file. This specifically covers
the Qidi Q2 Fluidd-visible thumbnail path that is not proven by local ZIP/G-code
audits alone.

The Qidi Q2 live host checks were rerun on 2026-05-16 against
`100.123.18.83:36021` with
`MOBILE_SLICER_MOONRAKER_URL=http://100.112.193.10:10088`:

- `scripts/verify_android.sh fluidd-thumbnail-metadata 100.123.18.83:36021`
- `scripts/verify_android.sh orca-object-label-parity 100.123.18.83:36021`

The thumbnail gate produced `thumbnailMs: 81`, uploaded
`MobileSlicer_fluidd_thumbnail_20260516-082904.gcode`, verified Moonraker
reported `32x32`, `48x48`, and `300x300` thumbnails, and deleted the uploaded
file. The object-label gate uploaded a printable label-off file, verified
Moonraker metadata stayed label/object-clean for the label-off case, and
deleted the uploaded file. Neither live gate starts a print.

The following gates were rerun during this audit:

- `scripts/verify_android.sh local`
- `scripts/verify_android.sh object-process 100.123.18.83:42917`
- `scripts/verify_android.sh orca-object-label-parity 100.123.18.83:42917`
- `scripts/verify_android.sh orca-import-smoke 100.123.18.83:42917`
- `scripts/verify_android.sh sliced-3mf-metadata 100.123.18.83:42917`
- `scripts/verify_android.sh multi-plate-sliced-3mf-metadata 100.123.18.83:42917`
- `python3 -m unittest scripts/test_orca_metadata_audit.py scripts/test_orca_metadata_fixture_gate.py`
- `git diff --check`

The combined project preservation pass was completed later on the same branch
after commit `8654d00a` (`Gate combined Orca project preservation fixture`):

- `scripts/verify_android.sh orca-combined-project-roundtrip-device 100.123.18.83:36021`
- `scripts/verify_android.sh orca-project-parity-device-matrix 100.123.18.83:36021`

That device matrix passed the base active-multifilament project, rich object
settings project, modifier project, height-range project, and combined
interaction project round trips. The combined output kept two plates, object
names, object-to-filament assignments, object settings, modifier settings,
height-range settings, project config entries, source-file evidence, and
project thumbnails. This closes the previous Android-device proof gap for the
combined fixture.

During the audit, `local` initially failed lint on suspicious indentation in
`AutomationSliceRunner.kt`. That was fixed and `local` passed afterward.

The multi-plate sliced-3MF audit also exposed a real metadata bug:
`PlateBBoxData::from_json()` did not read `first_layer_time`, so multi-plate
package export could write an uninitialized float into `Metadata/plate_N.json`.
That is now fixed in the vendored Orca header and the Python audit now fails
invalid or non-positive `first_layer_time` values.

## Current Contracts

### G-code Metadata

Print time, filament usage, cost, layer/object labels, and toolchange metadata
come from native Orca/libSlic3r. Kotlin may parse these values for UI display,
but exported metadata must remain native-sourced.

The fixture gate currently compares print time and filament metadata against
desktop Orca references where desktop references are available. Desktop Orca CLI
does not emit G-code thumbnails because its CLI export uses a null thumbnail
callback, so G-code thumbnail blocks are validated against MobileSlicer output
and Orca's thumbnail writer contract instead of desktop CLI pixels.
Desktop CLI sliced-3MF references may also carry `first_layer_time: 0.0`; the
positive first-layer-time requirement is enforced on MobileSlicer package
outputs, where the Android wrapper has access to the generated
`GCodeProcessorResult`.

### G-code Thumbnails

MobileSlicer asks Orca for thumbnail requests, renders the requested sizes on
Android, uploads RGBA buffers, and returns them through Orca's
`ThumbnailsGeneratorCallback`. Orca still writes/compresses the thumbnail
blocks, which keeps tag and format behavior aligned with the native stack.

Current coverage includes PNG, QOI, JPG, BTT TFT, and Qidi ColPic
`gimage`/`simage` contracts.

### Sliced 3MF Package Thumbnails

Orca's sliced package writer expects separate arrays for normal plate,
no-light plate, top, pick, and calibration thumbnails. MobileSlicer now carries
role-specific uploaded buffers into the native package writer. The checked-in
desktop-Orca reference matrix validates that Android outputs are visually
compatible with current tolerances for:

- simple cube,
- tall and narrow geometry,
- perimeter/bridge/retraction fixtures,
- arranged objects,
- active two-filament/two-object project,
- a Creality K1 profile case,
- a Prusa MK4 profile case.

This is a practical visual gate, not a claim that every pixel is identical to
desktop Orca. If pixel-identical thumbnails become a requirement, the remaining
route is still an Orca Canvas3D render-port spike, documented separately in
`docs/ORCA_CANVAS3D_THUMBNAIL_PORT_PLAN.md`.

The Canvas3D track now has a stronger source-level gate. `python3
scripts/orca_thumbnail_port_audit.py --pretty` reports both the required Orca
thumbnail entry points and the direct `GLCanvas3D` import blockers. The current
verdict is that a direct import is not feasible as a shipping path until the
wxWidgets, desktop GUI singleton, desktop plater, desktop OpenGLManager,
`GLVolumeCollection`, and `GLShaderProgram` dependencies are extracted or
replaced. That keeps the project honest: current thumbnails are Orca-compatible
and desktop-reference-gated, while implementation-identical Orca renderer
parity remains a separate extraction task.

The first native extraction probe now exists:
`engine-wrapper/orca_thumbnail_render_contract_probe.cpp`. It is intentionally
non-shipping and standalone. `scripts/orca_thumbnail_extraction_probe_gate.py`
compiles it locally, runs it, verifies the emitted role/camera/supersampling
contract against `OrcaThumbnailRenderPolicy.kt`, and checks the vendored Orca
source still contains the role markers the contract depends on. This gives the
renderer-port work a real buildable artifact without pretending full
`GLCanvas3D` can be imported directly.

The probe has also moved past constants-only coverage. It emits deterministic
framing metrics for cube, tall, narrow, broad-mid-height, and H2D-style
two-object layouts. The gate asserts expanded bounds, broad-footprint zoom
selection, camera distance, top-plate extent, and relative projected-size
invariants. This still is not pixel rendering, but it is the correct next
layer of Orca renderer parity proof before attempting shader or framebuffer
extraction.

### Sliced 3MF Bbox JSON

MobileSlicer now generates `PlateBBoxData` after native slicing and attaches it
to Orca's 3MF store path. Required JSON fields are:

- `bbox_all`
- `bbox_objects`
- `filament_ids`
- `filament_colors`
- `is_seq_print`
- `first_extruder`
- `nozzle_diameter`
- `bed_type`
- `first_layer_time`
- `version`

The audit now verifies that `first_layer_time` is finite and positive, not just
present.

### Object, Modifier, And Label Scope

The device gates prove:

- object process overrides reach Orca object config and are isolated to the
  targeted object,
- modifier process overrides reach an Orca `PARAMETER_MODIFIER` volume,
- object label output follows Orca's `gcode_label_objects`/`exclude_object`
  behavior instead of forcing labels globally.

The remaining gap is not native scope. It is UI completeness for modifier
transform editing.

## Remaining Gaps

### 1. 3MF Project Import/Export Preservation

This is the highest-value next step. Current import smoke proves STL, 3MF, and
STEP can slice. The first preservation pass now records structured
`ThreeMfProjectMetadata` from the imported 3MF package instead of only keeping a
coarse preserved/unsupported feature list. The metadata now includes:

- plate names and plate count,
- object names and object count,
- object-to-filament assignments,
- filament count,
- project thumbnail entries,
- Orca/Bambu config entries,
- preserved feature evidence.

The new `orca-3mf-project-preservation` gate audits a real Orca package fixture
as ZIP/XML and fails if object names, object-filament assignments, project
thumbnails, project settings, or minimum plate/object counts disappear. The
new `orca-3mf-roundtrip-contract` gate exercises the source-vs-round-trip
comparison logic so future MobileSlicer export artifacts can be compared
against the original Orca project package with the same contract.

`scripts/verify_android.sh orca-3mf-roundtrip-device <serial>` is the first
device-backed round-trip proof. It imports the checked-in Orca 3MF package,
exports a MobileSlicer project 3MF without running a slice, pulls the output,
and compares source vs output with the preservation audit. This keeps the gate
focused on project structure and avoids adding thumbnail or slicing time to the
normal slice hot path.

We still need fixture-backed project round trips for:

- per-plate metadata,
- object names,
- object-to-filament assignments,
- per-object and modifier settings,
- project thumbnails,
- multi-plate project structure,
- source mesh/object identity where Orca would preserve it.

Acceptance should require a desktop Orca project fixture plus MobileSlicer
import/export output, not only a successful slice.

The preservation audit now has explicit switches for the richer contract:

- `--require-object-settings`
- `--require-modifier-volumes`
- `--require-modifier-settings`
- `--require-layer-ranges`
- `--require-layer-range-settings`

Those checks inspect `Metadata/model_settings.config` object/part metadata
instead of treating every object transform or file name as a setting. The
current committed active-multifilament fixture intentionally does not enable
those switches because it has object names, filament assignments, thumbnails,
and project settings, but no per-object process metadata or modifier volumes.

The richer desktop-Orca fixture is generated by
`scripts/generate_orca_rich_project_fixture.sh` and checked by
`scripts/verify_android.sh orca-rich-project-fixture` plus
`scripts/verify_android.sh orca-rich-project-roundtrip-contract`. It proves a
real Orca sliced `.gcode.3mf` package with two plates, named objects, filament
assignments, project/package thumbnails, and object-scoped process metadata in
`Metadata/model_settings.config`. Device export can be checked with
`scripts/verify_android.sh orca-rich-project-roundtrip-device <serial>`.

The modifier desktop-Orca fixture is generated by
`scripts/generate_orca_modifier_project_fixture.sh` and checked by
`scripts/verify_android.sh orca-modifier-project-fixture` plus
`scripts/verify_android.sh orca-modifier-project-roundtrip-contract`. It starts
from the richer desktop-Orca fixture, adds a parameter modifier using Orca's own
`Metadata/model_settings.config` schema, then only commits the package after
desktop Orca imports, slices, re-exports, and preserves the modifier. Device
export can be checked with
`scripts/verify_android.sh orca-modifier-project-roundtrip-device <serial>`.

That fixture now proves modifier-volume and modifier-setting preservation.

The height-range desktop-Orca fixture is generated by
`scripts/generate_orca_height_range_project_fixture.sh` and checked by
`scripts/verify_android.sh orca-height-range-project-fixture` plus
`scripts/verify_android.sh orca-height-range-project-roundtrip-contract`. It
uses Orca's assemble-list `height_ranges` path and verifies
`Metadata/layer_config_ranges.xml` exactly the way Orca writes it: object ids in
that file are the 1-based model-object order used by Orca's 3MF importer, not
the mesh resource ids in `3D/3dmodel.model`. Device export can be checked with
`scripts/verify_android.sh orca-height-range-project-roundtrip-device <serial>`.

This proves project-package height/layer-range preservation. It still does not
claim sliced height-range package parity. The local desktop Orca CLI segfaults
before writing diagnostics when assemble-list `height_ranges` are combined with
`--slice` and `--export-3mf`; project-only export accepts that input and writes
`Metadata/layer_config_ranges.xml`, so the current fixture is scoped to the
stable Orca source path.

The combined desktop-Orca fixture is generated by
`scripts/generate_orca_combined_project_fixture.sh` and checked by
`scripts/verify_android.sh orca-combined-project-fixture` plus
`scripts/verify_android.sh orca-combined-project-roundtrip-contract`. It starts
from the modifier seed, adds Orca-style `Metadata/layer_config_ranges.xml`, then
commits only the package after desktop Orca imports and re-exports it. Device
export can be checked with
`scripts/verify_android.sh orca-combined-project-roundtrip-device <serial>`.
This fixture proves the interaction case: multi-plate objects, filament
assignments, object-scoped process settings, modifier volume/settings, and
height-range settings all survive together in one project package.

The installed Flatpak OrcaSlicer 2.3.2-rc on this machine rejects or crashes on
the newer modifier seed path, even though the vendored desktop Orca build used
by MobileSlicer imports and canonicalizes it successfully. Treat that as an
installed-Orca-version compatibility note, not as proof that MobileSlicer can
ignore modifier projects.

STEP-derived project fixture coverage has a different boundary. The vendored
desktop Orca CLI rejects STEP/STP as a positional input and also rejects STEP/STP
objects in `--load-assemble-list`, so a desktop-Orca STEP project reference
cannot currently be generated without first converting the model to STL. That
conversion would erase the source evidence the fixture is supposed to prove, so
it is not an acceptable parity shortcut. The limitation is gated by
`scripts/verify_android.sh orca-step-project-reference-probe`, and the project
preservation audit exposes `--require-step-source` for MobileSlicer-produced
STEP project packages. The committed app-produced fixture is
`regression-fixtures/orca-project-references/step-sliced-source-metadata/step-sliced-source-metadata.gcode.3mf`,
and it is gated by `scripts/verify_android.sh orca-step-sliced-source-fixture`.
The multi-object, multi-plate app-produced fixture is
`regression-fixtures/orca-project-references/step-multi-plate-sliced-source-metadata/step-multi-plate-sliced-source-metadata.gcode.3mf`,
and it is gated by
`scripts/verify_android.sh orca-step-multi-plate-sliced-source-fixture`. That
gate requires at least two sliced plates, two objects with STEP/STP source
metadata, per-plate JSON, per-plate G-code, project thumbnails covering both
plate indices, object names, and filament assignments.

### 2. Modifier Transform Editing UX

Modifier import, enable/disable, delete, center, rotate, direct 3D-view XY drag
when movement is unlocked, numeric transform editing, selected-region
highlighting, and scoped process editing exist.

Native modifier process application, transform persistence, native transform
emission, and the editing surface are gated. The remaining parity work has moved
back to richer source-project fixture coverage.

### 3. Live Host/Printer Compatibility

Qidi Q2 Fluidd/Moonraker PNG thumbnails are proven live. Other targets are
gated or source-supported, but not all are proven on real hosts:

- Mainsail/Moonraker still needs live validation.
- OctoPrint thumbnail display still needs live or containerized validation,
  because display behavior is plugin-dependent.
- More non-Qidi printers should be added to the desktop thumbnail reference
  matrix as real profiles, not Qidi-special cases.

### 4. Fixture Breadth

The current thumbnail matrix is much better than before, but it is still a
matrix. It now includes Qidi-shaped generic cases plus real Creality K1 and
Prusa MK4 profile cases. It should continue to grow with real-world stress
cases:

- larger meshes,
- multiple active filaments with different object sizes,
- painted/color-assigned models,
- modifier meshes,
- additional STEP-derived app packages beyond the committed single-object and
  multi-plate source-metadata fixtures, especially mixed STEP/STP source files
  and CAD assemblies,
- multi-plate projects imported from desktop Orca.

Do this incrementally and keep each new case tied to a real Orca profile or
real host behavior. The current project preservation gates are strong enough
that new thumbnail/profile fixtures no longer need to wait for more local 3MF
project work.

### 5. Release Hygiene

Before claiming broad Orca support, keep these as mandatory gates:

- `scripts/verify_android.sh local`
- `scripts/release_gate_android.sh <serial>` when device time allows
- `scripts/verify_android.sh object-process <serial>`
- `scripts/verify_android.sh orca-object-label-parity <serial>`
- `scripts/verify_android.sh orca-import-smoke <serial>`
- `scripts/verify_android.sh sliced-3mf-metadata <serial>`
- `scripts/verify_android.sh multi-plate-sliced-3mf-metadata <serial>`
- `scripts/run_orca_thumbnail_reference_matrix.sh <serial>`
- `scripts/verify_android.sh orca-3mf-project-preservation`
- `scripts/verify_android.sh orca-rich-project-fixture`
- `scripts/verify_android.sh orca-3mf-roundtrip-contract`
- `scripts/verify_android.sh orca-rich-project-roundtrip-contract`
- `scripts/verify_android.sh orca-modifier-project-fixture`
- `scripts/verify_android.sh orca-modifier-project-roundtrip-contract`
- `scripts/verify_android.sh orca-height-range-project-fixture`
- `scripts/verify_android.sh orca-height-range-project-roundtrip-contract`
- `scripts/verify_android.sh orca-combined-project-fixture`
- `scripts/verify_android.sh orca-combined-project-roundtrip-contract`
- `scripts/verify_android.sh orca-step-project-reference-probe`
- `scripts/verify_android.sh orca-step-sliced-source-fixture`
- `scripts/verify_android.sh orca-step-multi-plate-sliced-source-fixture`
- `scripts/verify_android.sh orca-project-parity-matrix`
- `scripts/verify_android.sh orca-3mf-roundtrip-device <serial>`
- `scripts/verify_android.sh orca-rich-project-roundtrip-device <serial>`
- `scripts/verify_android.sh orca-modifier-project-roundtrip-device <serial>`
- `scripts/verify_android.sh orca-height-range-project-roundtrip-device <serial>`
- `scripts/verify_android.sh orca-combined-project-roundtrip-device <serial>`
- `scripts/verify_android.sh orca-project-parity-device-matrix <serial>`
- `git diff --check`

The Android release gate now runs both `orca-project-parity-matrix` and the
full `orca-project-parity-device-matrix <serial>`. The device matrix includes
the base `orca-3mf-roundtrip-device` case plus rich object settings, modifier
volumes/settings, height ranges, and the combined interaction fixture, so
release validation does not drop the simple active-multifilament package while
expanding richer project coverage.

## Recommended Next Step

Move the next parity work from local project structure to compatibility
breadth. Keep `scripts/verify_android.sh orca-project-parity-matrix` and
`scripts/verify_android.sh orca-project-parity-device-matrix <serial>` green
whenever the 3MF import/export path changes, but the highest-value uncovered
work is now printer/host diversity:

- keep adding real non-Qidi desktop Orca profile cases to the thumbnail
  reference matrix,
- run `scripts/run_orca_thumbnail_reference_matrix.sh <serial>` after each
  matrix expansion,
- validate Mainsail/Moonraker and OctoPrint thumbnail display against real or
  containerized hosts before claiming those as proven live.
