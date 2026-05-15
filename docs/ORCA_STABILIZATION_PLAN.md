# Orca Stabilization Plan

This branch is carrying several Orca parity tracks at once. Treat the items
below as release gates before claiming broad Orca support.

## Current Contracts

- STEP import uses OCCT and must pass `orca-import-smoke` with STL, 3MF, and STEP fixtures.
- Workspace process edits apply to every plate in the current workspace without rewriting the reusable process preset.
- `Save preset` is the only action that intentionally writes workspace process edits back to `ProfileStore`.
- Object process state is represented by `PlateObjectProcessOverride`, persisted in saved projects, emitted as `mobile_slicer_object_process_overrides` during slicing, and applied in native code through `Slic3r::ModelObject::config` so Orca object-scope settings affect only the target object.
- `PlateObjectProcessOverride.selectedProcessId` is the saved object base preset. `editedProcessProfile` is the unsaved object-only edit. Saving a preset must update `ProfileStore`, keep the object assigned to the saved preset, and clear only the object dirty state.
- Object process override slices should emit `MobileSlicerNative: object_process_override` with accepted and ignored key counts. If that line is missing, the override did not reach the native object scope.
- Object label comments are controlled only by `gcode_label_objects`. `exclude_object` remains a separate Orca process option and must not force `; printing object`, `; stop printing object`, `EXCLUDE_OBJECT`, or `M486` output on printer paths where Orca would not emit them.
- Modifier process state is represented by `PlateObjectModifierMesh`, persisted in saved projects, emitted as `mobile_slicer_modifier_process_overrides`, loaded as an Orca `PARAMETER_MODIFIER` volume with its stored transform, and applied through `ModelVolume::config`.
- Modifier mesh UI lives in the object sheet. It supports import, enable/disable, delete, center, rotate, direct 3D-view XY drag while movement is unlocked, numeric X/Y/Z offset/rotation/scale editing, and scoped process editing through the same process editor. Main workspace actions should not grow a separate "edit modifier process" shortcut.
- Process setting coverage is enforced by `scripts/audit_process_profile_coverage.py`, `scripts/test_process_profile_coverage.py`, and the checked-in `docs/ORCA_PROCESS_SETTING_COVERAGE.md` report. A process setting is not considered reviewed unless it is classified for save/load, native Orca keys, workspace/plate behavior, and object-scope eligibility.
- Imported 3MF project context is represented by `ThreeMfProjectMetadata` when the importer knows what was preserved and what was flattened. The import inspector records plate names, object names, object-to-filament assignments, filament count, thumbnail entries, config entries, and preserved feature evidence from the 3MF ZIP/XML package. The `orca-3mf-roundtrip-contract` gate defines the source-vs-export comparison that MobileSlicer round-trip artifacts must pass, and `orca-3mf-roundtrip-device <serial>` runs that comparison against an Android-generated project export.
- Workspace object copies reuse the existing `StlMesh` upload when the copied object shares mesh identity with an existing object. The renderer must not clear and reupload every object after copy, duplicate, selection, transform, or arrange changes.

## Workspace Copy Performance

- `WorkspaceObjectUploadManager` keeps GL uploads by object id and by `StlMesh` identity. A duplicate with a new object id should reuse the previous upload for the same mesh.
- A batch with multiple objects sharing the same mesh should create at most one upload for that mesh and reuse it for the rest of the batch.
- A transform-only update should stay on the transform path and avoid mesh upload work entirely.
- ADB verification should include:
  `adb logcat -d -v time | rg 'MobileSlicerPerf.*workspace_object_upload|workspace_triangle_upload'`
- Expected duplicate behavior after the first import: `workspace_object_upload` should show reused uploads and should not show a full delete/recreate cycle for the original mesh.

## Next Release Gates

1. Keep the Orca project parity matrix green: `scripts/verify_android.sh orca-project-parity-matrix` and, with a connected device, `scripts/verify_android.sh orca-project-parity-device-matrix <serial>`. The matrix covers the base Orca package, rich multi-plate object settings, modifier volumes/settings, height ranges/settings, per-plate thumbnail indices, plate JSON metadata for sliced package fixtures, object names, filament assignments, and project settings.
2. Keep the object-process G-code isolation gate green; it now slices a generated two-object 3MF and verifies the object override changes only one object region.
3. Keep the Orca object label parity gate green; it verifies all `gcode_label_objects`/`exclude_object` combinations plus a printable label-off Moonraker metadata path when `MOBILE_SLICER_MOONRAKER_URL` is available.
4. Keep workspace copy performance tests green, especially `WorkspaceObjectUploadManagerTest`, before changing object duplication, arrangement, or viewer upload code.
5. Keep `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `git diff --check`, `scripts/verify_android.sh object-process <device>`, `scripts/verify_android.sh orca-object-label-parity <device>`, `scripts/verify_android.sh orca-import-smoke <device>`, and `scripts/verify_android.sh orca-project-parity-device-matrix <device>` green before pushing to a device.
