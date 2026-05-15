# Orca Thumbnail Parity Research And Next Steps

Date: 2026-05-13

This note records the evidence from the desktop Orca thumbnail investigation and
the next steps that avoid faking parity or increasing normal slice time.

## Current Finding

MobileSlicer's metadata/package flow is structurally correct, and the checked-in
desktop Orca thumbnail reference matrix now passes on Android hardware.

The desktop Orca fixtures under
`regression-fixtures/orca-thumbnail-references/` were generated from the
vendored Orca source build, not from MobileSlicer renders. The strict Android
comparison passes for the four package roles in every checked-in case:

```text
command: MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/run_orca_thumbnail_reference_matrix.sh 100.123.18.83:42917
status:  pass

simple-cube:              artifacts/egl-thumbnail-compare/20260514-195451
tall-box:                 artifacts/egl-thumbnail-compare/20260514-195506
small-perimeter:          artifacts/egl-thumbnail-compare/20260514-195521
perimeter-array:          artifacts/egl-thumbnail-compare/20260514-195537
narrow-strip:             artifacts/egl-thumbnail-compare/20260514-195553
bridge-speed:             artifacts/egl-thumbnail-compare/20260514-195608
complex-retraction-tower: artifacts/egl-thumbnail-compare/20260514-195624
arranged-objects:         artifacts/egl-thumbnail-compare/20260514-195640
creality-k1-simple-cube:  artifacts/egl-thumbnail-compare/20260514-195656

latest device recheck:
simple-cube:                         artifacts/egl-thumbnail-compare/20260515-071752
tall-box:                            artifacts/egl-thumbnail-compare/20260515-071807
small-perimeter:                     artifacts/egl-thumbnail-compare/20260515-071821
perimeter-array:                     artifacts/egl-thumbnail-compare/20260515-071836
narrow-strip:                        artifacts/egl-thumbnail-compare/20260515-071850
bridge-speed:                        artifacts/egl-thumbnail-compare/20260515-071904
complex-retraction-tower:            artifacts/egl-thumbnail-compare/20260515-071919
arranged-objects:                    artifacts/egl-thumbnail-compare/20260515-071933
active-multifilament-two-objects:    artifacts/egl-thumbnail-compare/20260515-071948
creality-k1-simple-cube:             artifacts/egl-thumbnail-compare/20260515-072002
```

This proves the current Android EGL renderer is compatible with Orca's package
thumbnail roles for the current fixture matrix. It does not prove
implementation-identical pixels for every model class; the gate is intentionally
fixture based and should keep expanding as new model classes are supported.

The Android comparison runner now also supports source-layout metadata for
future fixture expansion:

- `single`: one matrix STL rendered as one thumbnail source.
- `two_filament_objects`: the same matrix STL duplicated as two independently
  colored thumbnail sources. The current fixture mirrors the desktop Orca 3MF
  object placement: both objects share the bed center X and are offset along Y.

The second mode is a device-side guard for renderer source ordering and
multi-filament color handling. It is not a substitute for a desktop Orca
multi-filament visual reference.

The matrix runner preserves empty optional profile fields before parsing each
case row. This matters for active multi-filament fixtures because otherwise the
bed dimensions, source layout, and source colors shift into the wrong columns
and the device can accidentally test a single-object 270x270 fallback.

Normal slice time is protected: plain `.gcode` still renders only Orca-requested
G-code thumbnails. Those thumbnails now use the same Android EGL renderer and
Orca-style angled framing as the package `plate` role. The `plate`,
`no_light`, `top`, and `pick` package roles are generated only for sliced
`.gcode.3mf` package export and automation gates.

## Completed Source Build And Reference Capture

The vendored Orca source was built locally with Podman because Docker socket
access was not available:

```sh
cd vendor/orcaslicer
ORCA_CONTAINER_CLI=podman ./build_linux.sh -g -dsr
ORCA_CONTAINER_CLI=podman ./build_linux.sh -g -j 2 -sr
```

The dependency build completed through OCCT, including the STEP/IGES libraries
needed for future STEP parity work:

- `libTKSTEP*.a`
- `libTKIGES.a`
- related OCCT static libraries under `vendor/orcaslicer/deps/build/destdir`

Two source-build fixes were required:

- `vendor/orcaslicer/src/slic3r/GUI/Plater.hpp`
  - Forward declaration changed from `BuildVolume_Type : char` to
    `BuildVolume_Type : signed char` to match `BuildVolume.hpp`.
- `vendor/orcaslicer/src/OrcaSlicer.cpp`
  - The CLI thumbnail path was requesting OpenGL `3.4` because it copied the
    GLFW library version returned by `glfwGetVersion`.
  - The local patch now logs the GLFW version but requests OpenGL `3.3`, which
    matches Orca's thumbnail renderer requirement and works with the host
    NVIDIA OpenGL 4.6 driver.

The desktop reference fixture was generated with:

```sh
ORCA_SLICER_BIN=/home/peanut/Development/MobileSlicer/vendor/orcaslicer/build/package/bin/orca-slicer \
scripts/generate_orca_thumbnail_reference_fixture.sh
```

Generated reference files:

- `regression-fixtures/orca-thumbnail-references/simple-cube/plate.png`
- `regression-fixtures/orca-thumbnail-references/simple-cube/no_light.png`
- `regression-fixtures/orca-thumbnail-references/simple-cube/top.png`
- `regression-fixtures/orca-thumbnail-references/simple-cube/pick.png`
- `regression-fixtures/orca-thumbnail-references/simple-cube/simple-cube.gcode.3mf`
- `regression-fixtures/orca-thumbnail-references/simple-cube/manifest.json`

Additional desktop Orca references now exist:

| case | source model | purpose |
| --- | --- | --- |
| `tall-box` | `regression-fixtures/slicing/stage2_inner_wall_acceleration_tall_box_fixture.stl` | tall-object angled thumbnail framing |
| `small-perimeter` | `regression-fixtures/slicing/stage2_small_perimeter_fixture.stl` | tiny full-bed top/pick thumbnail aliasing |
| `perimeter-array` | `regression-fixtures/slicing/stage2_small_perimeter_array_fixture.stl` | wider multi-island footprint |
| `narrow-strip` | `regression-fixtures/slicing/stage2_gap_infill_narrow_strip_fixture.stl` | long thin footprint and top/pick line visibility |
| `bridge-speed` | `regression-fixtures/slicing/stage2_bridge_speed_fixture.stl` | bridge/support-like footprint |
| `complex-retraction-tower` | `android-app/app/src/main/assets/calib_stl/retraction/retraction_tower.stl` | higher-triangle calibration tower with a tall, narrow visual profile |
| `arranged-objects` | `regression-fixtures/slicing/stage3_arranged_objects_fixture.stl` | broad arranged multi-island package thumbnail framing |
| `creality-k1-simple-cube` | `regression-fixtures/slicing/mobileslicer_test_cube.stl` | non-Qidi Creality K1 profile and bed-dimension coverage |

## Multi-Filament Desktop Probe Result

The next attempted expansion was a desktop Orca multi-filament thumbnail
fixture. Three CLI paths were tested:

- Multiple STL inputs with `--load-filaments` and `--load-filament-ids`.
- Bambu P1P profiles with two loaded filament profiles.
- `--load-assemble-list` with two objects, explicit `filaments`, and manual
  `filament_map`.

The best probe output was:

```text
artifacts/orca-thumbnail-reference-capture/bbl-assemble-multifilament-probe-20260514-201125
```

That package contains two objects and records per-object extruder assignments in
`Metadata/model_settings.config`:

```text
left_cube_1  extruder=1
right_cube_1 extruder=2
filament_map_mode=Manual
filament_maps=1 2
```

However, Orca still wrote one active filament into the sliced package metadata:

```text
Metadata/plate_1.json: filament_colors=["#F2754E"], filament_ids=[0]
Metadata/slice_info.config: <filament id="1" .../>
```

The extracted role PNG metrics also showed only one object color in the normal
roles for this simple fixture. Because the source package does not prove two
active desktop Orca filaments, it must not be checked in as a claimed desktop
multi-filament visual parity case.

What did land from this probe:

- `OffscreenEglSmokeRunner` can now render comparison thumbnails from
  `two_filament_objects`.
- `scripts/verify_android.sh egl-thumbnail-compare` accepts
  `MOBILE_SLICER_EGL_COMPARE_SOURCE_LAYOUT` and
  `MOBILE_SLICER_EGL_COMPARE_SOURCE_COLORS`.
- The matrix list-shell format carries source layout/color fields for a future
  proven desktop fixture.

Next required proof before claiming multi-filament thumbnail parity:

1. Find or create an Orca desktop source package that writes two active
   filaments in `plate_1.json`, `slice_info.config`, and object `extruder`
   metadata in `model_settings.config`.
2. Confirm the four desktop role PNGs visually preserve both material colors or
   the expected Orca role coloring.
3. Extract it with:

   ```sh
   python3 scripts/extract_orca_thumbnail_references.py \
     --source-3mf /path/to/desktop-orca-multifilament.gcode.3mf \
     --output-dir regression-fixtures/orca-thumbnail-references/multi-filament-active \
     --orca-build "OrcaSlicer <commit-or-version>" \
     --model /path/to/source-project-or-model \
     --printer-profile "<desktop Orca printer profile>" \
     --filament-profile "<two active filament profiles>" \
     --process-profile "<desktop Orca process profile>" \
     --source-layout two_filament_objects \
     --source-colors "#F2754E,#4EA3F2" \
     --require-active-filaments 2
   ```

4. Add the case to `matrix.json` with `source_layout=two_filament_objects`,
   `source_colors`, and `requirements.active_filaments=2`.
5. Run the full Android matrix and keep normal slice timing within the current
   benchmark thresholds.

The extractor and fixture audit now reject packages that only assign two
objects to two extruders but still collapse `Metadata/plate_1.json` or
`Metadata/slice_info.config` to one active filament. That prevents the failed
2026-05-14 probe shape from being checked in as a false parity fixture.

The active-reference probe is now automated:

```sh
scripts/probe_orca_active_multifilament_reference.sh
```

It generates two small STL objects and tests:

- Bambu Lab H2D 0.4 nozzle with direct multi-input loading.
- Bambu Lab H2D 0.4 nozzle with `--load-assemble-list`.
- Generic MyToolChanger 0.4 nozzle with direct multi-input loading.
- Generic MyToolChanger 0.4 nozzle with `--load-assemble-list`.

The script only succeeds after
`scripts/extract_orca_thumbnail_references.py --require-active-filaments 2`
accepts the desktop Orca package. A failing run leaves a JSON-lines report and
all generated inputs under
`artifacts/orca-thumbnail-reference-capture/active-multifilament-reference-probe-*`.

Current local result:

```text
artifacts/orca-thumbnail-reference-capture/active-multifilament-reference-probe-20260515-072144/probe-report.jsonl
```

The Bambu Lab H2D direct multi-input path now produces a valid desktop Orca
`.gcode.3mf` package through the vendored package binary. The extracted
fixture lives at
`regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects`
and is included in `matrix.json` with `source_layout=two_filament_objects`,
the red/blue source colors, and `requirements.active_filaments=2`.

The AppImage still exits with status 139 in this environment for the same
generated profile package, so the probe intentionally prefers
`vendor/orcaslicer/build/package/bin/orca-slicer` unless `ORCA_SLICER_BIN`
explicitly overrides it. That keeps the release gate tied to a desktop Orca
binary that can produce the required active-filament package instead of
accepting a guessed Android-only reference.

Each case includes `plate.png`, `no_light.png`, `top.png`, `pick.png`, a copied
desktop Orca `.gcode.3mf`, `manifest.json`, and a case README. The fixture test
`scripts/test_regression_fixtures.py` now enforces that these role PNGs exist,
are 512x512 desktop-Orca images, and are nonblank.

## Android Renderer Update

The Android offscreen EGL renderer now separates package thumbnail roles:

- `top` and `pick`
  - Use whole-bed top projection, matching Orca's `Top_Plate` behavior.
  - The simple cube appears small and centered on the full plate instead of
    filling the thumbnail.
- `plate` and `no_light`
  - Use a yawed Orca-style object-box fit instead of the generic workspace
    orbit camera.
  - The fit targets the desktop Orca package-thumbnail framing while keeping
    the implementation local to package thumbnail rendering.
  - `plate` uses the normal lit shader path.
  - `no_light` uses the same camera/framing but a full-bright shader mode so it
    remains a distinct package role instead of collapsing to the lit image.
- `gcode`
  - Uses the Orca-style angled object-box fit instead of the old Android
    software thumbnail path.
  - Production slicing uses the EGL renderer directly. There is no software
    fallback in the slice/export path; renderer failures should fail device
    gates instead of silently producing lower-fidelity thumbnails.

The relevant implementation is:

- `android-app/app/src/main/java/com/mobileslicer/workspace/OffscreenEglSliceThumbnailRenderer.kt`
- `android-app/app/src/main/java/com/mobileslicer/workspace/SliceThumbnailRenderer.kt`
- `android-app/app/src/main/java/com/mobileslicer/MainActivitySlicing.kt`
- `android-app/app/src/main/java/com/mobileslicer/automation/AutomationSliceRunner.kt`
- `scripts/orca_metadata_audit.py`
- `scripts/orca_thumbnail_visual_diff.py`
- `scripts/test_orca_thumbnail_visual_diff.py`

Final validation for this pass:

```sh
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/run_orca_thumbnail_reference_matrix.sh 100.123.18.83:42917
scripts/verify_android.sh script-tests
python3 -m unittest scripts/test_regression_fixtures.py scripts/test_orca_thumbnail_visual_diff.py scripts/test_extract_orca_thumbnail_references.py
cd android-app && ./gradlew :app:testDebugUnitTest --tests com.mobileslicer.workspace.SliceThumbnailRendererTest
```

The matrix and script gates passed again on 2026-05-15 after adding the active
multi-filament fixture, preserving empty matrix fields during shell parsing,
matching the desktop Orca two-object placement, and rendering the top package
role at full color intensity.

The strict matrix renders at 512x512 by default. That matches the desktop Orca
reference PNG size and avoids false failures from 128px subpixel aliasing on
tiny full-bed roles such as `small-perimeter` and `narrow-strip`. The normal
plain `.gcode` slice path is still limited to requested G-code thumbnail sizes
and does not render package roles.

The visual diff script was also corrected to normalize MobileSlicer and desktop
Orca bbox comparisons across different thumbnail resolutions. Desktop package
PNGs are currently 512x512, while the Android automation comparison renders
128x128 role PNGs.

## Previous AppImage Finding

The local Orca AppImage can slice and export a `.gcode.3mf`, but its thumbnail
generation path fails before it can render the package PNGs:

```text
thumbnails stage: plate 1's thumbnail file also not there, need to regenerate
thumbnails stage: plate 1's no_light_thumbnail_file  also not there, need to regenerate
thumbnails stage: plate 1's top_file  also not there, need to regenerate
opengl version 3.3.7
glfwInit Success.
Unable to init glew library, Error: Unknown error
init opengl failed! skip thumbnail generating
```

The package then contains the sliced G-code and metadata:

```text
Metadata/plate_1.gcode
Metadata/plate_1.gcode.md5
Metadata/plate_1.json
Metadata/project_settings.config
Metadata/model_settings.config
Metadata/slice_info.config
```

but it does not contain the required visual reference entries:

```text
Metadata/plate_1.png
Metadata/plate_no_light_1.png
Metadata/top_1.png
Metadata/pick_1.png
```

Those packages must stay rejected by the visual reference gate.

## Source Evidence

Orca's official import/export documentation describes sliced `.gcode.3mf` as a
package containing the G-code plus printer/material/process information.
It also recommends inspecting 3MF files as ZIP archives, which is exactly how
the current audit validates package entries.

The relevant local Orca source path is:

- `vendor/orcaslicer/src/OrcaSlicer.cpp`
  - CLI sliced-3MF export checks whether plate thumbnails are missing.
  - It creates a hidden GLFW window.
  - It initializes the OpenGL manager.
  - It calls `GLCanvas3D::render_thumbnail_framebuffer(...)` or
    `GLCanvas3D::render_thumbnail_framebuffer_ext(...)`.
  - It fills normal plate, no-light, top, and pick thumbnail data.
- `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp`
  - The real desktop framebuffer renderer and `glReadPixels` path.
- `vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp`
  - Writes the thumbnail PNG entries into the sliced package when thumbnail
    buffers are valid.

There is also a version-path mismatch:

- The extracted Orca AppImage binary contains `glewInit`,
  `Unable to init glew library`, and `glewInit Success.` strings.
- The current vendored Orca source at commit `83fa885d` uses
  `gladLoaderLoadGL()` in `OpenGLManager::init_gl`.

So continued AppImage environment tweaks are lower value than trying the
current vendored desktop binary.

## Current Gate Command

Run the strict visual gate with:

```sh
MOBILE_SLICER_EGL_COMPARE_MODEL=regression-fixtures/slicing/mobileslicer_test_cube.stl \
MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR=regression-fixtures/orca-thumbnail-references/simple-cube \
MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 \
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh egl-thumbnail-compare <serial>
```

Run the full checked-in reference matrix with:

```sh
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/run_orca_thumbnail_reference_matrix.sh <serial>
```

Run the local reference integrity gate first:

```sh
scripts/verify_android.sh orca-thumbnail-reference-fixtures
```

The matrix runner pairs each model with its matching desktop Orca reference
directory from `regression-fixtures/orca-thumbnail-references/matrix.json`.
This avoids the false-positive failure mode where a MobileSlicer render from
one model is compared to a desktop Orca render from another model.
The Android release gate now runs this full matrix instead of only the default
simple-cube comparison. That is intentional: a single cube can prove the wiring,
but it cannot prove Orca-style framing across tall, tiny, wide, narrow, and
bridge-like shapes.

The matrix also pins stricter desktop-Orca visual thresholds than the generic
debug command:

```text
max luma delta:           70.0
max alpha coverage delta: 0.08
max bbox delta:           64 px after resolution normalization
coverage ratio:           0.90..1.15
```

Those values are based on the current checked-in desktop Orca references and
the clean 2026-05-14 device rerun. They leave room for driver-level luma and
antialiasing differences. The bbox tolerance is wider because broad
multi-island angled views differ slightly between desktop Orca and the Android
EGL fit, while alpha coverage and coverage ratio still fail obvious crop,
scale, blank, single-color, or wrong-model regressions.

## Fallback If Local Source Build Stops Working

Use a GUI desktop Orca export as the reference source.

Requirements:

- Same model: `regression-fixtures/slicing/mobileslicer_test_cube.stl`
- Same printer: Qidi Q2 0.4 nozzle
- Same process: 0.20mm Standard
- Same filament: Generic PLA or the matching fixture filament
- Export as sliced `.gcode.3mf`
- Verify package contains:
  - `Metadata/plate_1.png`
  - `Metadata/plate_no_light_1.png`
  - `Metadata/top_1.png`
  - `Metadata/pick_1.png`

Then extract with:

```sh
python3 scripts/extract_orca_thumbnail_references.py \
  --source-3mf /path/to/desktop-orca-simple-cube.gcode.3mf \
  --output-dir regression-fixtures/orca-thumbnail-references/simple-cube \
  --orca-build "OrcaSlicer <commit-or-version>" \
  --model regression-fixtures/slicing/mobileslicer_test_cube.stl \
  --printer-profile "Qidi Q2 0.4 nozzle" \
  --filament-profile "Generic PLA @Qidi Q2 0.4 nozzle" \
  --process-profile "0.20mm Standard @Qidi Q2" \
  --allow-overwrite
```

## What Not To Do

Do not mark visual parity as done while the reference directory is missing.

Do not compare MobileSlicer thumbnails against MobileSlicer thumbnails and call
that Orca parity. The software-vs-EGL comparison is useful for MobileSlicer
renderer health, but it is not desktop Orca parity.

Do not put extra thumbnail work into normal Slice. Desktop-Orca visual reference
capture and strict visual diff are release/testing gates only. Normal slicing
must keep the current split:

- Plain `.gcode`: only requested G-code thumbnails.
- Sliced `.gcode.3mf`: package thumbnails only for package export.

## Next Decision

The strict reference matrix now includes a higher-triangle retraction tower and
passes on device for the current checked-in cases. The next useful work is
expanding beyond single-material single-plate STL geometry:

1. Add a multi-filament desktop Orca visual fixture.
2. Add a true rotated-object angled thumbnail fixture after the current
   arranged-object gate; the broad arranged fixture passes, but a baked-rotation
   synthetic fixture exposed an angled bbox mismatch that should be handled by a
   more exact Orca camera/projection port before it becomes a release gate.
3. Add a desktop-Orca multi-plate visual fixture now that MobileSlicer has a
   differentiated multi-plate `.gcode.3mf` device gate.
4. Keep `GLCanvas3D` porting as a research branch only if Android EGL tuning
   cannot satisfy those fixtures.

That order protects slice time and avoids a full desktop GUI port until the
smaller renderer has proven insufficient.

## Stack Import Decision

The project should not import Orca's full desktop renderer into the shipping
Android build right now. The source audit shows that direct `GLCanvas3D` import
is not a small renderer library; it crosses into wxWidgets, desktop
`wxGLCanvas`, `GUI_App`, `MainFrame`, `Plater`, GUI events, imgui/gizmos,
`OpenGLManager`, `PartPlateList`, and GUI-owned `GLVolumeCollection`.

The next non-corner-cutting step is a minimal Orca thumbnail extraction spike:

1. Keep the current Android EGL renderer as the production path.
2. Treat `OrcaThumbnailRenderPolicy.kt` as the testable extraction contract for
   Orca thumbnail role behavior.
3. Add a native probe target that imports only Orca thumbnail role behavior:
   `Iso`, `Top_Plate`, object-box padding, `for_picking`, `ban_light`, and
   framebuffer/readback semantics.
4. Feed that probe from MobileSlicer's existing mesh/color upload data instead
   of constructing the desktop Orca GUI scene.
5. Compare probe output against the existing desktop Orca reference matrix.
6. Promote it only if it improves reference metrics and keeps benchmark timing
   under the current gates.

The first extraction checkpoint is now implemented:

- `OrcaThumbnailRenderPolicy.kt` names the role/camera contract and stores the
  source-backed constants the Android renderer uses.
- `OffscreenEglSliceThumbnailRenderer` uses that policy.
- `OrcaThumbnailRenderPolicyTest` covers role routing and fit decisions.
- `orca_thumbnail_port_audit.py` verifies the policy stays present and points
  back to Orca `GLCanvas3D.cpp`.
- Small output quality now uses bounded supersampling:
  - G-code thumbnails up to 300px are rendered at 2x then downsampled.
  - Package thumbnails up to 128px are rendered at 2x then downsampled.
  - The Qidi Q2 legacy `150x150/PNG` path and the sliced-3MF package assets
    now have decoded-detail gates instead of only signature/entry checks.
- The device metadata benchmark now checks `48x48`, `128x128`, and the
  Fluidd/Moonraker `48x48/PNG,300x300/PNG` pair for plain G-code exports with
  `--require-gcode-thumbnail-antialias`, so this quality path is a
  release-visible contract rather than a manual inspection note.

The source contract is now guarded by:

```sh
python3 scripts/orca_thumbnail_port_audit.py --pretty
scripts/verify_android.sh script-tests
```

This prevents the plan from becoming vague: if we import more Orca renderer
code, it has to preserve the real desktop entry points, the Android EGL
boundary, and the release timing/scope gates.
