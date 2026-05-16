# Orca Parity Resume Handoff

Last updated: 2026-05-16.

This is the short resume point for the current Orca parity track. Use the
deeper audit docs for rationale, but start here when picking the work back up.

## Current Git State

- Branch: `orca-parity-local-checkpoint`
- Latest validation commit before this handoff:
  `f431d882 Record live Fluidd validation rerun`
- Previous relevant commits:
  - `b4756288 Add Prusa thumbnail reference coverage`
  - `8654d00a Gate combined Orca project preservation fixture`
  - `1bd9441a Gate multi-plate STEP sliced source metadata`
  - `cd638966 Gate STEP sliced source metadata fixture`
  - `061158b1 Preserve STEP source metadata in sliced packages`
- One unrelated local change was present after the latest commit:
  - `docs/scanner/README.md`
  - It is scanner resume documentation and was intentionally not committed with
    the Orca parity validation work.

## Live Device And Printer

- Android device used: `100.123.18.83:36021`
- Device model reported by ADB: `SM_S938U1`
- Live Qidi Q2 Moonraker/Fluidd URL used for gates:
  - `http://100.112.193.10:10088`
- LAN mirror observed:
  - `http://192.168.1.162`
- Printer state at validation time: Klippy ready.
- The live gates uploaded generated G-code files, waited for Moonraker metadata,
  and deleted the uploaded files. They did not start prints.

## Last Verified Gates

Run on 2026-05-16:

```bash
MOBILE_SLICER_MOONRAKER_URL=http://100.112.193.10:10088 \
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh fluidd-thumbnail-metadata 100.123.18.83:36021
```

Result:

- Passed.
- Artifact directory:
  `artifacts/fluidd-thumbnail-metadata/20260516-082858`
- Moonraker remote file:
  `MobileSlicer_fluidd_thumbnail_20260516-082904.gcode`
- Moonraker metadata reported thumbnails:
  - `32x32`
  - `48x48`
  - `300x300`
- `thumbnailMs: 81`
- `elapsedMs: 274`

```bash
MOBILE_SLICER_MOONRAKER_URL=http://100.112.193.10:10088 \
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh orca-object-label-parity 100.123.18.83:36021
```

Result:

- Passed.
- Artifact directory:
  `artifacts/orca-object-label-parity/20260516-082944`
- Moonraker remote file:
  `MobileSlicer_label_off_20260516-083005.gcode`
- Moonraker label-off metadata keys:
  - `filament_total`
  - `layer_height`
  - `object_height`
  - `slicer`
- This confirms the label-off path stays object-label-clean in Moonraker.

```bash
scripts/verify_android.sh printer-thumbnail-compatibility
git diff --check
```

Result:

- Both passed.

## Current Proven Coverage

The current checked-in Orca parity work proves:

- Plain `.gcode` thumbnail blocks are generated through Orca's native thumbnail
  callback path.
- Fluidd/Moonraker sees MobileSlicer PNG thumbnails on a real Qidi Q2 host.
- The Qidi Q2 live gate reports the small Fluidd/Moonraker-compatible thumbnail
  plus the requested `48x48` and `300x300` thumbnails.
- Object label output follows Orca's `gcode_label_objects` and `exclude_object`
  behavior instead of forcing object labels globally.
- Sliced `.gcode.3mf` package thumbnails and metadata are gated locally and on
  Android.
- Multi-plate sliced `.gcode.3mf` packages are gated for package entries,
  plate JSON, and role thumbnails.
- STEP source metadata is preserved in sliced packages for single-plate and
  multi-plate fixtures.
- 3MF project import/export preservation has device-backed coverage for the
  combined fixture: multi-plate objects, filament assignments, object settings,
  modifier settings, height ranges, project settings, source evidence, and
  project thumbnails.
- Desktop Orca thumbnail reference coverage includes Qidi, Creality K1, and
  Prusa MK4 profile cases.

## Known Non-Claims

Do not claim these as complete yet:

- Mainsail live thumbnail display. It is expected through Moonraker metadata,
  but no real Mainsail UI or host fixture has proven it.
- OctoPrint thumbnail display. Upload support exists, but display is
  plugin-dependent and still needs a fixture or live/container host.
- Pixel-identical desktop Orca thumbnails. Current thumbnails are
  Orca-compatible and desktop-reference-gated, not implementation-identical to
  Orca's desktop `GLCanvas3D` renderer.
- Full Orca desktop renderer import. The source audit shows a direct import
  pulls in wxWidgets, desktop GUI state, desktop plater infrastructure, and
  OpenGL GUI objects. Keep this as a measured extraction spike, not the default
  shipping path.

## Main Docs To Read Next

- `docs/ORCA_PARITY_AUDIT_NEXT_STEPS.md`
- `docs/PRINTER_THUMBNAIL_COMPATIBILITY.md`
- `docs/ORCA_THUMBNAIL_NEXT_STEPS_RESEARCH.md`
- `docs/ORCA_CANVAS3D_THUMBNAIL_PORT_PLAN.md`
- `docs/ORCA_METADATA_THUMBNAIL_PARITY_PLAN.md`
- `docs/ORCA_GOLDEN_FIXTURES.md`

## Recommended Next Step

Move from Qidi/Fluidd proof to host diversity:

1. Add a real Mainsail/Moonraker live validation path or container-backed host
   fixture.
2. Upload a generated thumbnail-bearing G-code file.
3. Confirm Mainsail/Moonraker exposes the expected thumbnail metadata and UI
   display.
4. Delete the uploaded file and document that the gate does not start prints.
5. Only then move Mainsail from `pending_live_validation` to `proven_live` in
   `regression-fixtures/printer-thumbnail-compatibility/matrix.json`.

If Mainsail is not available, the next best compatibility target is an
OctoPrint/plugin thumbnail fixture because that is the other remaining host
path that cannot be honestly claimed from local G-code inspection alone.

## Commands For A Clean Resume

Before new Orca parity work:

```bash
git status --short --branch
adb devices -l
curl -fsS --max-time 3 http://100.112.193.10:10088/server/info
```

For a non-printing live Qidi Q2 sanity check:

```bash
MOBILE_SLICER_MOONRAKER_URL=http://100.112.193.10:10088 \
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh fluidd-thumbnail-metadata 100.123.18.83:36021
```

For local compatibility and metadata sanity:

```bash
scripts/verify_android.sh printer-thumbnail-compatibility
scripts/verify_android.sh orca-thumbnail-reference-fixtures
git diff --check
```

For broader Android/device parity after code changes:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/run_orca_thumbnail_reference_matrix.sh 100.123.18.83:36021

scripts/verify_android.sh orca-project-parity-device-matrix 100.123.18.83:36021
```
