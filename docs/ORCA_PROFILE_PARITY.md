# Orca Profile Parity

MobileSlicer separates reusable presets from workspace working settings.

Export metadata and thumbnail parity is tracked separately in
`docs/ORCA_METADATA_THUMBNAIL_PARITY_PLAN.md`. That plan covers G-code
thumbnails, print statistics, sliced 3MF thumbnail assets, and the verification
gates required before claiming Orca output parity.

## Preset library

`ProfileStore` is the reusable library for printer, filament, and process presets. The Profiles screen manages this library: importing Orca presets, duplicating presets, editing reusable custom presets, and exporting profiles.

Library changes are intentionally global. They are not the normal path for making one-off adjustments to a single plate.

## Workspace process state

The workspace keeps one active `PlateProfileState` and mirrors it onto every `WorkspacePlate` for project persistence:

- `selectedProcessId` identifies the process preset used as the workspace baseline.
- `editedProcessProfile` stores workspace process edits when the workspace has diverged from the preset.

When `editedProcessProfile` is present, the workspace shows the process as modified. Slicing uses the edited process for all plates in the workspace, while the preset in `ProfileStore` remains unchanged.

## Workspace flow

The plate sheet is only for plate and object management. Process controls do not live there.

When Profiles is opened from the workspace, the Process tab becomes workspace-aware:

- Applying a process preset changes the workspace process for all plates.
- Editing a process opens the same process editor used by the preset library.
- `Apply to plate` stores the edited process on the workspace plates without writing to the preset library.
- `Save preset` writes the edited settings back to the reusable process preset and clears the workspace override.
- If the workspace has process overrides and the user switches process presets, the UI offers the Orca-style transfer/discard choice.

New plates inherit the current workspace process state. Saved projects persist `PlateProfileState` on each plate, and project open synchronizes the restored workspace process state back across every plate.

## Modified preset state

The workspace process editor uses the same settings surface as reusable process
presets. In workspace mode the primary action is `Apply to plate`; this updates
the workspace process override used by every plate in the current workspace.
The secondary action is `Save preset`; this writes the edited settings back to
the reusable process preset and clears the workspace override. Changed fields
are marked inline by the editor dirty state so the user can tell which setting
diverged from the preset baseline.

## Native config

The native slice path receives an `ActiveSlicerConfiguration` whose process is already resolved from the workspace process state. Existing native config assembly, Orca resolved JSON merging, explicit override layers, cache signatures, and plate planning signatures therefore include workspace process edits through the effective process profile.

Object-scoped process state uses a separate native config payload,
`mobile_slicer_object_process_overrides`. `PlateObjectProcessOverride` stores
two separate concepts:

- `selectedProcessId`: the saved process preset assigned to that object.
- `editedProcessProfile`: unsaved object-only edits made from the unified process editor.

This mirrors Orca's object process flow. `Use as object base` assigns a saved
preset to only that object. `Apply to object` stores edited settings on only
that object and marks the object editor with `*`. `Save preset` writes the
edited process back to the reusable preset library, keeps the object assigned to
that saved preset, and clears the object dirty state. `Reset object` removes the
object process state so the object returns to the workspace process.

Each native payload entry carries the mobile object id, the current plate object
index, the selected process id, and the Orca process override JSON to apply to
that model object. The native wrapper applies the explicit override JSON to
`Slic3r::ModelObject::config` after the global process config so an object
override wins only for its target object. A saved object base preset is emitted
when it differs from the workspace process; unsaved object edits are emitted
directly from `editedProcessProfile`. Profile metadata keys such as preset ids,
compatibility lists, and inherited preset names are filtered before object
application. Other unsupported non-object keys are ignored at native object
scope, matching Orca's separation between process, object, region, printer, and
filament config owners.
During slicing, the native bridge writes a `MobileSlicerNative:
object_process_override` logcat line with the target object index plus accepted
and ignored key counts. That log is the first check when a per-object edit saves
correctly but appears not to affect the slice.

`scripts/verify_android.sh object-process <device>` is the release gate for
this path. It first slices the cube fixture and asserts the native wrapper
accepts object-scoped slicer keys while ignoring metadata probes. It then
generates a two-object 3MF, preserves the 3MF object boundaries through the
automation loader, slices a baseline and an object override variant, pulls both
G-code files, and verifies that only one separated object region changes. This
keeps Orca-style object overrides tied to the target object instead of silently
becoming workspace-wide process edits.

## Object labels and exclude-object markers

Orca treats `gcode_label_objects` and `exclude_object` as separate settings.
`gcode_label_objects` controls the readable `; printing object ...` and
`; stop printing object ...` comments. `exclude_object` is preserved as its own
process setting, but Orca only emits firmware exclude-object commands when the
active printer/flavor path enables them. In the current vendored Orca
non-Bambu/Qidi-compatible path, `exclude_object=true` with `gcode_flavor=klipper`
does not emit `EXCLUDE_OBJECT` or `M486` commands unless Orca's printer-context
gate enables exclude-object output.

This distinction matters for printer metadata indexing. A profile with
`gcode_label_objects=false` must not produce object label comments as a side
effect of enabling thumbnails, Fluidd metadata, object overrides, or copied
objects. Hidden object comments can make printer-side metadata parsing much
slower on some Moonraker/Fluidd setups.

`scripts/verify_android.sh orca-object-label-parity <device>` is the release
gate for this contract. It slices all four combinations of
`gcode_label_objects` and `exclude_object`, verifies label comments and exclude
commands independently, and also slices a printable non-cube fixture with labels
disabled. When `MOBILE_SLICER_MOONRAKER_URL` is set, the same gate uploads the
label-off printable output to Moonraker and requires metadata keys such as print
time, filament length, and slicer to appear within
`MOBILE_SLICER_MOONRAKER_LABEL_OFF_METADATA_MAX_MS`.

## Process persistence

Every process field emitted by `ProcessProfile.toJson()` must be restored by `JSONObject.toProcessProfile()`. This includes scalar settings, enum-backed Orca options, and grouped detail models such as quality surface, infill detail, prime tower, special mode, and G-code output settings.

The regression test `processProfileJsonRoundTripPreservesSavedProcessSettings` compares every saved process JSON key after a save/load cycle so editor-visible settings like `onlyOneWallFirstLayer` cannot silently reset when a profile is reopened.

`scripts/audit_process_profile_coverage.py` is the complete source-level
process coverage gate. It audits every `ProcessProfile` setting property
against defaults, JSON save/load, emitted Orca native config keys, generated
Orca metadata or documented aliases, and object-scope eligibility. The checked
in report is `docs/ORCA_PROCESS_SETTING_COVERAGE.md`; `scripts/test_process_profile_coverage.py`
fails if that report is stale or any field loses coverage.

The current audit covers 341 process setting properties. The buckets are:

- metadata: preset identity, compatibility, and preserved Orca source chain
- filament/tool-scoped: extruder, support filament, sparse infill filament, and
  multi-material temperature/plate controls
- process/support/multimaterial/output: active workspace and plate process
  settings
- object-candidate: native process keys that may be attempted at object scope
  through `Slic3r::ModelObject::config`
- workspace/plate-only, stored-only, and not-object: keys intentionally not
  treated as object edits

## Process Editor Audit

The process editor has explicit tests for each handoff:

- Rendered process tab callbacks must be wired by `ProcessProfileEditorSelectedTabContent`.
- Initial-backed draft fields must either save through `toProcessProfile()` or be documented as hidden/stored metadata.
- Process tab parameters must be rendered or intentionally stored-only.
- Process profile properties must either be emitted to native config or documented as stored-only/profile metadata.
- Process profile coverage must stay current in `docs/ORCA_PROCESS_SETTING_COVERAGE.md`.

The currently hidden stored-only process fields are preset identity/metadata, import compatibility controls, and `adaptiveLayerHeight`. Orca's vendored `adaptive_layer_height` PrintConfig option is commented out, so MobileSlicer preserves the field but does not expose it as a live slicer control.

## Object And Modifier Scope

MobileSlicer currently supports workspace-wide process overrides and
per-object filament assignment, paint payloads, transforms, cuts, split
results, per-object process overrides, and modifier-mesh process overrides.
Per-object process override UI is intentionally separate from the plate sheet;
it opens the unified process editor with an object scope and writes
`PlateObjectProcessOverride` instead of changing the workspace process.

Modifier meshes are represented as `PlateObjectModifierMesh` entries owned by
their parent `PlateObject`. A modifier stores its own source STL path, transform
metadata, enabled state, and `PlateObjectProcessOverride`. During native config
assembly MobileSlicer emits `mobile_slicer_modifier_process_overrides`; the
native wrapper loads the modifier STL as an Orca `PARAMETER_MODIFIER` volume and
applies the stored placement transform before applying accepted settings to
`ModelVolume::config`. This keeps modifier process settings separate from object
process settings and from the reusable process profile library.

Modifier UI is intentionally in the object sheet, not on the main workspace
action row and not in the plate-management sheet. From an object row, the user
can import a modifier mesh, enable or disable it, remove it, recenter or rotate
it, drag it in the 3D view when movement is unlocked, edit numeric X/Y/Z
offset, rotation, and scale values, and open the unified process editor in
modifier scope. In that editor,
`Apply to modifier` stores the edited process only on the modifier volume while
`Save preset` writes the edits to the reusable preset library and leaves the
modifier assigned to that saved preset. Enabled modifier meshes are rendered in
the workspace viewer with a distinct overlay color so the user can see the
region that will receive the modifier process; modifiers owned by the selected
object are highlighted as the active modifier regions.

The implemented modifier contracts are:

- storage round-trip in saved projects
- native config/application per affected model object or modifier volume
- object-sheet creation/import, enable/disable, delete, center, rotate, and
- numeric transform editing, and scoped process editing
- release tests proving the object/modifier path changes emitted toolpath

Modifier transform editing, process scope, native application, and saved-project
persistence are covered. Remaining work should focus on broader project-level
3MF fixture coverage rather than adding a second modifier-editing surface.

## 3MF Project Scope

Current Android 3MF support extracts printable mesh geometry for workspace
preview and slicing. Full Orca/Bambu project parity is broader: plates,
objects, transforms, filament mapping, painted regions, modifiers, thumbnails,
and project metadata need preservation. Saved project objects can now carry
`ThreeMfProjectMetadata` so imports can record preserved and unsupported
project features instead of flattening that context into an anonymous STL.

The 3MF project inspector reads the imported package as ZIP/XML and records
plate names, object names, object-to-filament assignments, filament count,
project thumbnail entries, and Orca/Bambu config entries. This metadata is
saved with the project and covered by `orca-3mf-project-preservation`, which
audits a real Orca package fixture for the same evidence.

The round-trip comparison contract lives in
`orca_3mf_project_preservation_audit.py` and is exposed through
`scripts/verify_android.sh orca-3mf-roundtrip-contract`. It compares a source
3MF package with a round-trip export and fails if required plate names, object
names, object-to-filament assignments, project thumbnails, or config entries
are lost.

The device-backed proof is `scripts/verify_android.sh
orca-3mf-roundtrip-device <serial>`. It imports a real checked-in Orca 3MF
package on Android, exports a MobileSlicer project 3MF without slicing, pulls
that output, and runs the same source-vs-round-trip audit. The native project
writer preserves imported Orca plate/config data when present; it only creates
a synthetic single-plate package for non-project workspaces.
