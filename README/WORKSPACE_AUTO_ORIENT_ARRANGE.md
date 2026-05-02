# Workspace Auto Orient And Auto Arrange Plan

This document is the implementation plan for replacing the current lightweight
`Prepare` auto-orient and auto-arrange behavior with Orca-parity behavior while
keeping the existing mobile UI buttons and protecting Prepare, Preview, and
slicing performance.

## Implementation Status

The production mobile buttons currently use the restored local implementation
from `/home/peanut/Games/MobileSlicer`, not the native Orca planner path.

This is intentional. The native `orca_plan_plate_arrangement(...)` path was
able to run, but repeatedly returned unusable zero-origin plans on device. That
made the visible action worse than the older mobile behavior, so the button path
was switched back to the known-working Android implementation while the native
planner remains diagnostic/reference work.

Current production behavior:

* The existing `WorkspaceTransformControls.kt` UI is preserved. The small icon
  buttons only select the tab. The large action row triggers the selected tool.
* `AutoOrient` evaluates right-angle X/Y/Z orientation candidates from the
  object's available mesh bounds, prefers orientations that fit the active bed,
  then chooses the lowest printable height with stable footprint tie-breakers.
  If no bounds are available, it falls back to snapping the current X/Y/Z
  rotations to the nearest 90-degree angles. If no object is selected, all
  plate objects are evaluated.
* `AutoArrange` computes each object's transformed footprint from STL bounds,
  current scale, and full X/Y/Z rotation.
* `AutoArrange` may choose a 90-degree Z rotation for an object when that
  produces a better packable footprint. It preserves X/Y rotations, scale,
  material slot, and model identity.
* `AutoArrange` now uses a centered cluster pass first: larger objects are
  placed from the bed center outward on concentric candidate rings, producing a
  more natural centered layout than lower-left shelf packing.
* The centered cluster pass is optimized for mobile interaction:
  * transformed footprints are computed once per object per arrange request
  * single-object plates take a direct center-placement fast path
  * candidate points are generated in ring order without distance sorting
  * candidate generation is capped by object count to avoid runaway scans
  * occupied footprints are checked through a small spatial hash instead of a
    full placed-object scan for every candidate
* If the centered cluster cannot fit, `AutoArrange` falls back to centered-grid
  placement and then the restored shelf pass from the `/Games` state.
* `AutoArrange` preserves each object's scale, material slot, and model
  identity. It updates X/Y centers and may update Z rotation by a right-angle
  increment when that helps the plate fit.
* Z placement remains owned by the existing viewer/native placement contract:
  `ViewerModelTransform` stores X/Y center, rotations, and scale only, and both
  rendering and slicing compute `z = -rotatedMinZ`, so the transformed object
  bottom is placed on the build plate after arrange/orient.
* Selected-object footprint, picking bounds, and arrange footprints use the
  actual transformed bounding-box center. They must not use the requested
  transform center as a proxy, because asymmetric tilted models can have a
  rotated footprint center that differs from the original model center.
* Multi-filament single-nozzle plates reserve a conservative estimated prime
  tower keepout so arranged objects do not occupy the likely tower area.
* Successful arrange/orient clears generated preview state and increments the
  transform invalidation key so the viewer and slicing path see the new object
  positions.

The native Orca planner code remains useful as future work, but it is not in the
production button path until it can return valid non-zero transforms on Android.

## Goal

MobileSlicer should use the same geometry decisions as OrcaSlicer for:

* auto-orienting plate STL objects into print-friendly orientations
* arranging plate objects on the active printer bed
* preserving prime/wipe tower keepouts and other non-object bed exclusions
* reporting no-fit cases before slicing

The visible UI entry points should remain the existing transform-popover buttons:

* `AutoOrient` in `WorkspaceTransformControls.kt`
* `AutoArrange` in `WorkspaceTransformControls.kt`

The implementation should change what those buttons do, not add a separate
desktop-style workflow.

## Previous State

`Prepare` is Android-owned by design:

* Android owns the STL viewer, bed shell, touch controls, and plate object state.
* `PlateObject.transform` is the source of truth for object placement.
* Native slicing receives explicit per-object transforms through
  `nativeLoadPlateModels(...)`.
* Android must not directly depend on Orca internals.

Before the native planner work, auto-orient was not real auto-orient. In
`WorkspaceTransformControls.kt`, pressing the auto-orient button snaps X/Y/Z
rotation to the nearest 90 degrees.

Before the native planner work, auto-arrange was not Orca arrangement. In `ModelLoaderScreen.kt`,
`arrangePlateObjects()` computes object footprints from transformed bounds and
places objects with a centered cluster pass, centered-grid fallback, and final
shelf fallback. It preserves each object's current rotation and scale, only
changes X/Y centers, and reserves a conservative estimated prime-tower keepout.
The local arranger deliberately does not store a separate Z offset; object
bottom-on-bed placement is derived from transformed bounds during render and
native slice load.

That behavior is acceptable as scaffolding, but it is not feature parity.

## Orca Reference Points

The relevant Orca code is already vendored:

* `vendor/orcaslicer/src/libslic3r/Orient.hpp`
* `vendor/orcaslicer/src/libslic3r/Orient.cpp`
* `vendor/orcaslicer/src/libslic3r/Arrange.hpp`
* `vendor/orcaslicer/src/libslic3r/Arrange.cpp`
* `vendor/orcaslicer/src/libslic3r/ModelArrange.cpp`
* `vendor/orcaslicer/src/libslic3r/Model.cpp`
* `vendor/orcaslicer/src/slic3r/GUI/Jobs/OrientJob.cpp`
* `vendor/orcaslicer/src/slic3r/GUI/Jobs/ArrangeJob.cpp`

Important Orca behaviors:

* `orientation::orient(...)` scores orientations using mesh normals, overhang,
  first-layer/bottom area, convex hull bottom area, low-angle faces, projected
  geometry, and support threshold angle.
* `ArrangePolygon` stores a 2D convex-hull silhouette, translation, rotation,
  inflation, material metadata, height, and bed index.
* `arrangement::arrange(...)` uses libnest2d to pack inflated polygons into the
  active bed shape and supports exclusions/fixed items.
* `ModelInstance::get_arrange_polygon(...)` generates the same style of
  arrange polygon Orca uses for model instances.
* Orca can allow or disallow arrange rotations through `ArrangeParams`.
* Orca arrangement handles non-rectangular bed polygons through `printable_area`.

The desktop GUI job classes are not the Android target. The reusable target is
the `libslic3r` logic behind those jobs.

## Architecture

Add native wrapper planning APIs. The Android app calls those APIs, receives a
small transform plan, and applies it to existing `PlateObject.transform` state.

Do not move Android UI state into native. Do not make Android import Orca
headers. Do not mutate the slicer model as a side effect of pressing the buttons.
Planning should be pure from Android's perspective:

1. Android sends current paths, transforms, material slot ids, printer/process
   config, and requested operation.
2. Native builds temporary Orca model/planning data.
3. Native returns planned transforms or a structured failure.
4. Android applies the transforms to the existing plate state.
5. Slice still uses the normal `nativeLoadPlateModels(...)` path.

This keeps `Prepare` responsive and makes the behavior testable without
requiring a full slice.

Planner logs are part of the contract while this area is being verified on
devices. For a working run, logcat should show:

* `autoArrange input ... native(...) viewer(...)` or
  `autoOrient input ... native(...) viewer(...)`
* native wrapper `orca_plan_plate_arrangement` / `orca_plan_auto_orientation`
  lines
* matching `autoArrange output ...` / `autoOrient output ...` lines
* `autoArrange native applied ... changed=N` or
  `autoOrient native applied ... changed=N`

If the UI dispatches but the native input/output logs do not appear, the issue
is Android-side dispatch. If native output logs appear but changed remains zero,
the issue is the native Orca planner returning unchanged transforms for the
current geometry/config.

## Native API Shape

Use two explicit APIs rather than overloading slice/load behavior.

### Auto Arrange

Proposed C API:

```cpp
int orca_plan_plate_arrangement(
    OrcaEngine* engine,
    const char* const* paths,
    const double* transforms,
    const int* extruder_ids,
    int count,
    const char* config_json,
    int allow_rotation,
    double* out_transforms);
```

Input `transforms` and output `out_transforms` should use the existing
seven-value native transform layout:

1. X offset in mm
2. Y offset in mm
3. Z offset in mm
4. X rotation in radians
5. Y rotation in radians
6. Z rotation in radians
7. uniform scale

Return behavior:

* `ORCA_SUCCESS`: all objects fit; `out_transforms` is filled.
* `ORCA_ERROR_INVALID_ARGUMENT`: malformed inputs.
* `ORCA_ERROR_ARRANGE_NO_FIT`: at least one object cannot fit on the active bed.
* `ORCA_ERROR_LOAD_MODEL`: a model could not be loaded for planning.

If an error occurs, `orca_get_last_error(...)` contains the stable summary used
by the existing native bridge.

### Auto Orient

Implemented batch C API:

```cpp
int orca_plan_auto_orientation(
    OrcaEngine* engine,
    const char* const* paths,
    const double* transforms,
    const int* extruder_ids,
    int count,
    const char* config_json,
    double* out_transforms);
```

Return behavior:

* `ORCA_SUCCESS`: the requested plate STL objects have new grounded transforms.
* `ORCA_ERROR_INVALID_ARGUMENT`: malformed inputs.
* `ORCA_ERROR_LOAD_MODEL`: an STL could not be loaded.
* `ORCA_ERROR_ORIENT_FAILED`: orientation planner failed or produced invalid
  transform data.

The existing mobile button currently submits the plate's local STL objects as a
batch and applies the returned transform for each object. A later selected-only
mode can reuse the same native API with a one-object request.

## Arrange Implementation

Native planning should follow Orca's model as closely as possible.

Implementation sequence:

1. Parse `config_json` into the same `DynamicPrintConfig` baseline used by
   native slicing.
2. Load each STL through the same caching pattern as `orca_load_plate_models`.
3. Build a temporary `Slic3r::Model`.
4. Apply each incoming transform to its temporary `ModelInstance`.
5. For each instance, call `ModelInstance::get_arrange_polygon(...)`.
6. Fill `ArrangePolygon` metadata:
   * object height
   * extruder ids / material slot ids
   * object name
   * brim width when available
   * current Z rotation
   * current scale reflected in geometry before polygon generation
7. Build the bed from `printable_area`, not just rectangular width/depth.
8. Build `ArrangeParams` from Orca config and MobileSlicer policy:
   * `min_obj_distance`
   * skirt/brim distance
   * `allow_rotations`
   * `allow_multi_materials_on_same_plate`
   * sequential-print clearance later, after base parity is proven
   * printer-structure Y-axis alignment if already present in the resolved
     printer config
9. Build fixed/excluded regions:
   * prime/wipe tower keepout for enabled single-nozzle multi-material plates
   * future locked objects
   * future calibration/exclusion regions
10. Run `arrangement::arrange(...)`.
11. Reject any object whose `bed_idx` is not the current physical bed.
12. Convert each result back into the existing seven-value transform layout.
13. Return the full plan without changing `engine->impl.model`.

The native Orca planner path is not used by the production buttons until it can
return valid non-zero transforms on Android. The production Android arranger is
kept deterministic and explicit: centered cluster first, centered grid second,
shelf fallback last, with no hidden native fallback.

## Prime/Wipe Tower Keepout

Do not regress the current multi-material safety behavior.

The current wrapper already computes and relocates prime/wipe tower placement
before slicing. Arrangement planning must use the same tower footprint model so
Prepare and slicing agree.

Required behavior:

* If prime tower is disabled, no tower exclusion is added.
* If a single-nozzle multi-material plate needs a tower, arrangement receives a
  fixed/excluded `ArrangePolygon` representing the tower footprint.
* The footprint must include:
  * `wipe_tower_x`
  * `wipe_tower_y`
  * `prime_tower_width`
  * `prime_tower_brim_width`
  * estimated tower depth from active material/tool count
  * the same margin used by native tower overlap checks
* If the tower is relocated by native slice preflight, the same relocation
  policy should be available to arrangement planning.
* No-fit must fail before slicing with an explicit message.

The plan should not create a layout that only works because the slicer later
moves the tower into object space.

## Auto Orient Implementation

Native planning should call Orca's `orientation::orient(...)`, not approximate
orientation in Kotlin.

Implementation sequence:

1. Load each requested STL into `Slic3r::TriangleMesh`.
2. Build `orientation::OrientMesh`.
3. Set `OrientMesh.overhang_angle` from active process config:
   * prefer per-object `support_threshold_angle` when object-level config
     exists in the future
   * otherwise use global process `support_threshold_angle`
4. Choose Orca mode:
   * default: min-volume/support-volume behavior, matching Orca's normal auto
     orient path
   * optional future mode: min-area, matching Orca's alternate orient setting
5. Run `orientation::orient(...)`.
6. Validate the returned rotation matrix.
7. Convert the rotation into Android's current transform format.
8. Recompute grounded Z and center-preserving X/Y placement.
9. Return the transform plan.

Important transform note:

Orca naturally returns a rotation matrix. Android currently stores Euler angles
in `ViewerModelTransform`. For the first pass, native can return extracted Euler
angles if the extraction is stable for the tested cases. Full parity eventually
requires storing a matrix or quaternion in plate state, with Euler sliders as an
editing view of that transform.

Do not hide this limitation. If Euler extraction causes drift or gimbal issues
in proof models, add matrix/quaternion storage before declaring feature parity.

## Android Integration

Reuse existing controls.

`WorkspaceTransformControls.kt` should keep the same tabs/buttons:

* Move
* Rotate
* Scale
* AutoOrient
* AutoArrange

Behavior changes:

* `AutoOrient` calls a native planning path for the plate STL objects.
* `AutoArrange` calls a native planning path for all plate objects.
* Button press should set a short operation state so repeated taps do not queue
  competing planners.
* Planning must run off the UI thread.
* On success, update `plateObjects` transforms and clear generated Preview state.
* On failure, preserve existing transforms and set `workspaceStatus` to the
  structured native message.

Do not add new visible explanatory text inside the Prepare UI. Existing compact
status messages are enough.

## Performance Contract

The feature has to feel quick and must not slow unrelated paths.

Rules:

* No auto-orient or auto-arrange work during normal viewer rendering.
* No automatic planning on every drag, slider movement, import frame, Preview
  layer change, or slice config preparation.
* Planning runs only when the existing AutoOrient or AutoArrange button is
  pressed, except for explicit future import-time behavior.
* Native planning must not invalidate the warm slice model cache unless the
  returned transforms are applied by Android.
* Do not parse full viewer meshes on the UI thread for planning.
* Reuse the native STL/model load cache pattern already used by
  `orca_load_plate_models`.
* Return only compact numeric plans to Kotlin.

Target budgets on the current phone path:

* Arrange common small plates: under 250 ms.
* Arrange crowded plates: under 1000 ms, with cancellable/background execution
  if it runs longer.
* Auto-orient common STL: under 1000 ms after load.
* No measurable regression to:
  * Prepare first frame
  * Preview layer scrubbing
  * repeated no-change slice config prep
  * G-code export path

If a model is huge, it is acceptable for auto-orient to take longer after an
explicit button press. It is not acceptable for that cost to leak into unrelated
workspace actions.

## Feature Parity Definition

Do not mark this complete when it merely improves layout.

Feature parity means:

* arrangement uses Orca bed shape, not only rectangular dimensions
* arrangement uses Orca object silhouettes, not only axis-aligned bounds
* arrangement supports object inflation/spacing from the active config
* arrangement can rotate objects when Orca would rotate them
* arrangement respects prime/wipe tower keepout
* arrangement reports no-fit instead of silently overlapping or clipping
* auto-orient uses Orca's mesh scoring path
* support threshold angle affects orientation scoring
* transformed Prepare placement matches transformed native slice placement
* saved projects preserve planned transforms
* repeated clones and shared STL sources still use stable native source identity
* behavior is verified against Orca desktop for representative fixtures

## Verification Plan

Add repeatable tests before declaring the feature finished.

Kotlin unit tests:

* transform-plan application updates only intended `PlateObject.transform`
  values
* failure leaves existing transforms unchanged
* generated preview state is cleared after successful plan application
* saved project round trip preserves planned transforms

Native or wrapper tests:

* simple two-object arrange fits without overlap
* crowded no-fit returns `ORCA_ERROR_ARRANGE_NO_FIT`
* non-rectangular bed shape is honored
* prime tower exclusion prevents object/tower overlap
* allow-rotation changes the result where Orca would rotate an object
* auto-orient returns finite grounded transforms
* auto-orient uses `support_threshold_angle`

Parity fixtures:

* Benchy plus one rectangular object on a common rectangular bed
* three or more mixed-size STLs
* one object that only fits if rotation is allowed
* one object that cannot fit
* one single-nozzle multi-material plate requiring prime tower keepout
* one non-rectangular or clipped Orca bed profile

Device proof:

* record arrange/orient elapsed time in logcat
* visually confirm Prepare placement
* slice after planning and confirm no printable-volume regression
* compare generated G-code extents against the planned transforms
* compare at least one reference arrangement/orientation against Orca desktop

## Implementation Order

1. Add native result structs/error codes and JNI/Kotlin bridge declarations.
2. Implement native arrange planner behind the wrapper.
3. Wire existing `AutoArrange` button to native planner.
4. Remove the shelf/grid fallback from production behavior.
5. Implement native auto-orient planner for STL plate objects.
6. Wire existing `AutoOrient` button to native planner.
7. Add arrange/orientation tests, reference fixtures, and device timing logs.
8. Revisit transform storage. If Euler conversion is not lossless enough, add
   matrix/quaternion-backed plate transforms before calling auto-orient complete.

## Open Decisions

These are not blockers for starting, but they must be resolved before marking
the feature complete:

* Whether Android plate state should move from Euler rotation to matrix or
  quaternion storage for exact auto-orient parity.
* Whether AutoOrient should automatically call AutoArrange afterward when the
  new footprint no longer fits in the previous center.
* Whether import-time behavior should apply printer `preferred_orientation` or
  stay explicit-button-only.
* Whether batch auto-orient should be exposed later through the same button when
  multiple objects are selected.

## Non-Goals

* Do not add a new desktop-like arrange dialog.
* Do not add new visible Prepare instructions.
* Do not hand-roll a new Kotlin nesting solver.
* Do not mutate vendored Orca unless a narrow Android build fix is unavoidable.
* Do not run auto-orient/auto-arrange implicitly in performance-sensitive paths.
* Do not claim parity while keeping the current nearest-90-degree orient snap or
  shelf/grid arranger as the production behavior.
