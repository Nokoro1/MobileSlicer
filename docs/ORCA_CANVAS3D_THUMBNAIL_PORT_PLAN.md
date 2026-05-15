# Orca Canvas3D Thumbnail Port Plan

This document records the concrete plan for investigating and, if feasible,
porting OrcaSlicer's own thumbnail rendering stack to MobileSlicer on Android.
It is intentionally explicit about dependencies, risks, validation, and stop
conditions so the work does not turn into an open-ended rewrite.

## Current Decision

Do not replace the current MobileSlicer thumbnail path with a full Orca
`GLCanvas3D` port immediately. Import more of Orca only behind a proven,
measured extraction boundary.

The current path is correct for export metadata:

- Android asks Orca native code for parsed thumbnail requests.
- Android renders RGBA thumbnail buffers.
- Native Orca receives those buffers through the thumbnail callback.
- Orca writes G-code thumbnail blocks itself.
- For sliced `.gcode.3mf`, MobileSlicer fills Orca `StoreParams` thumbnail
  arrays before calling Orca's 3MF writer.

The current practical path is the Android EGL renderer plus strict desktop Orca
reference fixtures. The checked-in desktop Orca package-thumbnail matrix now
passes against MobileSlicer EGL output on Android hardware. That is enough to
keep improving the minimal renderer and fixture matrix before attempting a full
`GLCanvas3D` port.

Porting Orca's renderer remains the route toward implementation-identical
pixels, but it should stay behind evidence: if complex fixtures cannot be made
to pass with the minimal renderer, then extract or port more Orca render code.

The current checked decision is:

- Keep production slicing on MobileSlicer's Android EGL renderer.
- Keep Orca native code responsible for thumbnail request parsing, G-code
  thumbnail block writing, and sliced-3MF package writing.
- Import Orca renderer code only if the first extraction spike proves it can be
  compiled without dragging in wxWidgets, desktop `wxGLCanvas`, desktop app
  events, or broad `libslic3r_gui` initialization.
- Treat any import that requires the full `GLCanvas3D` object, wx event system,
  `GUI_App`, `MainFrame`, or `Plater` as "too bad" for the shipping app until a
  separate research branch proves size, runtime, and maintenance cost.
- Normal slice time cannot regress materially. New renderer work must stay
  behind the existing thumbnail callback and package export split, then pass the
  metadata benchmark before it ships.

The source boundary is now guarded by:

```sh
python3 scripts/orca_thumbnail_port_audit.py --pretty
```

That audit checks the exact Orca desktop thumbnail entry points, the Android
EGL/native build boundary, and the production/automation renderer wiring. It is
also part of `scripts/verify_android.sh script-tests`.

Current implementation checkpoint:

- `android-app/app/src/main/java/com/mobileslicer/workspace/OrcaThumbnailRenderPolicy.kt`
  now holds the extracted Orca thumbnail role/camera contract.
- `OffscreenEglSliceThumbnailRenderer` consumes that policy instead of hiding
  Orca-derived camera constants inside the renderer implementation.
- `OrcaThumbnailRenderPolicyTest` verifies:
  - `plate` and `gcode` use angled `Iso` behavior,
  - `no_light` uses the same angled mode with `ban_light`,
  - `top` uses `Top_Plate`,
  - `pick` uses `Top_Plate` plus picking/no-light semantics,
  - wide/narrow footprint tuning stays stable.
- Small thumbnail quality is now policy-owned:
  - G-code thumbnails up to 300px render at 2x and downsample to the requested
    size, covering both Fluidd's `48x48/PNG,300x300/PNG` pair and Qidi Q2's
    legacy `150x150/PNG` path.
  - Package role thumbnails up to 128px render at 2x and downsample, so
    sliced-3MF package thumbnails are held to the same decoded-detail quality
    gate as normal G-code thumbnails.
- `orca-metadata-benchmark` now includes both:
  - `metadata-png-48` for small Fluidd-style preview coverage.
  - `metadata-png-128` for the larger G-code thumbnail path.
  - `metadata-fluidd-png` for the Fluidd/Moonraker contract of
    `48x48/PNG,300x300/PNG` in one plain G-code export.
  Both require decoded PNG visuals and anti-alias/detail variation. The
  committed metadata fixture gate now enforces the same visual/anti-alias
  checks for PNG G-code fixture outputs.
- The audit checks that the policy points back to
  `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp`.
- Device thumbnail comparison can now exercise a two-source, two-color layout
  through `MOBILE_SLICER_EGL_COMPARE_SOURCE_LAYOUT=two_filament_objects` and
  `MOBILE_SLICER_EGL_COMPARE_SOURCE_COLORS`. This is a renderer regression
  hook, not a desktop parity claim by itself.

Current multi-filament renderer status:

- Desktop Orca CLI probes can create a package with two objects and extruder
  assignments in `Metadata/model_settings.config`.
- The same probes still collapse the simple cube slice metadata to one active
  filament in `Metadata/plate_1.json` and `slice_info.config`.
- Therefore the next port/parity step is not more Android tuning; it is a
  proven desktop Orca multi-filament package fixture with two active filaments
  and valid role PNGs. Only then should the matrix grow a
  `two_filament_objects` Orca visual-reference case.
- The proof is now executable, not just policy text:
  `scripts/extract_orca_thumbnail_references.py --require-active-filaments 2`
  and `scripts/orca_thumbnail_reference_fixture_audit.py` require at least two
  active filaments in `Metadata/plate_1.json`, `Metadata/slice_info.config`,
  and object extruder metadata before a `two_filament_objects` reference can
  pass.
- `scripts/probe_orca_active_multifilament_reference.sh` now automates the
  desktop-reference attempt against Bambu H2D and generic MyToolChanger
  multi-nozzle profiles. The current passing local run
  `artifacts/orca-thumbnail-reference-capture/active-multifilament-reference-probe-20260515-072144`
  uses the vendored package binary to produce a Bambu H2D direct multi-input
  `.gcode.3mf` package that proves two active filaments in `plate_1.json`,
  `slice_info.config`, and `model_settings.config`.
- The extracted fixture is checked in under
  `regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects`
  and included in `matrix.json` with `source_layout=two_filament_objects`.
  The Android comparison runner preserves empty matrix fields while parsing and
  mirrors the desktop Orca object placement for this layout, so the active
  multi-filament device gate now tests two colored objects on the H2D bed
  instead of falling back to a single 270x270 source.
  The AppImage still crashes for this generated package in the local Linux
  environment, so the probe prefers `vendor/orcaslicer/build/package/bin/orca-slicer`
  unless `ORCA_SLICER_BIN` is explicitly set.

This is the first extraction boundary. The next native probe should target this
policy instead of re-reading arbitrary renderer constants.

## Why This Is Not A Small Patch

Orca's thumbnail renderer is not part of the simple headless slicer API.

The actual desktop path is:

1. `Plater` or CLI export decides thumbnails are needed.
2. Orca has a GUI/render scene with:
   - `PartPlateList`
   - `ModelObjectPtrs`
   - `GLVolumeCollection`
   - extruder colors
   - a `GLShaderProgram`
   - `Camera` state
   - `OpenGLManager` state
3. `GLCanvas3D::render_thumbnail_framebuffer(...)` or
   `GLCanvas3D::render_thumbnail_framebuffer_ext(...)` creates an offscreen GL
   framebuffer.
4. `GLCanvas3D::render_thumbnail_internal(...)` renders the Orca GUI volumes.
5. `glReadPixels` copies RGBA pixels into `ThumbnailData`.
6. Orca's G-code or 3MF writer compresses and stores those pixels.

MobileSlicer currently has steps 1, 5, and 6 shaped correctly. It does not have
Orca's desktop GUI render scene running on Android.

Directly adding `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp` to Android is
not a contained change. The file includes or depends on:

- wxWidgets headers and types: `wx/glcanvas.h`, `wx/bitmap.h`, `wx/image.h`,
  `wx/timer.h`, `wxEvent`, `wxGLCanvas`, `wxGLContext`.
- Desktop Orca GUI state: `GUI_App`, `MainFrame`, `Plater`,
  `GUI_ObjectList`, `NotificationManager`, `Tab`, and GUI events.
- Desktop render scene types: `GLVolumeCollection`, `PartPlateList`,
  `GLShaderProgram`, `OpenGLManager`, Orca `Camera`, and GUI-owned colors.
- Desktop overlay/tooling dependencies: imgui, imguizmo, gizmo managers,
  toolbars, labels, and layer editing support.

That does not mean Orca renderer code is impossible to reuse. It means the
right import boundary is not `GLCanvas3D.cpp` as-is. The first practical import
target is the pure thumbnail subset: camera role math, visible-volume filtering,
thumbnail role color behavior, and framebuffer/readback behavior adapted to the
existing Android EGL context.

## Source Map

Primary Orca source files:

- `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.hpp`
  - `render_thumbnail(...)`
  - `render_thumbnail_framebuffer(...)`
  - `render_thumbnail_framebuffer_ext(...)`
  - `render_thumbnail_internal(...)`
- `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp`
  - framebuffer allocation
  - render invocation
  - `glReadPixels`
  - camera/scene/volume rendering
- `vendor/orcaslicer/src/slic3r/GUI/Plater.cpp`
  - desktop package role generation:
    - normal plate
    - no-light plate
    - top
    - pick
  - `StoreParams` thumbnail array population
- `vendor/orcaslicer/src/OrcaSlicer.cpp`
  - CLI/export thumbnail regeneration path using `GLCanvas3D`
- `vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp`
  - consumes `StoreParams.thumbnail_data`
  - consumes `StoreParams.no_light_thumbnail_data`
  - consumes `StoreParams.top_thumbnail_data`
  - consumes `StoreParams.pick_thumbnail_data`
  - writes `Metadata/plate_N.png`, `plate_N_small.png`,
    `plate_no_light_N.png`, `top_N.png`, and `pick_N.png`

Current MobileSlicer integration points:

- `android-app/app/src/main/java/com/mobileslicer/workspace/SliceThumbnailRenderer.kt`
  - current Android thumbnail renderer
  - separates G-code thumbnails from sliced-3MF package thumbnails
- `android-app/app/src/main/java/com/mobileslicer/MainActivitySlicing.kt`
  - gets Orca thumbnail requests
  - renders/uploads thumbnails
  - logs `packageThumbnails`
- `android-app/app/src/main/java/com/mobileslicer/automation/AutomationSliceRunner.kt`
  - automation equivalent used by release gates
- `engine-wrapper/orca_wrapper_preview_api.cpp`
  - accepts uploaded RGBA buffers
  - fills sliced-3MF `StoreParams` thumbnail arrays
- `engine-wrapper/orca_wrapper_slice_export_api.cpp`
  - passes uploaded G-code thumbnails into Orca's native thumbnail callback
- `scripts/verify_android.sh`
  - release/device gates for thumbnail scope, timing, and package metadata
- `scripts/orca_metadata_audit.py`
  - metadata and visual metric audit

## Feasibility Verdict

Porting is feasible only if we can isolate the renderer from the desktop app.
There are three realistic approaches.

### Approach 1: Android Offscreen Renderer That Matches Orca

This does not port `GLCanvas3D` directly. It builds a purpose-specific
Android/EGL renderer that matches Orca's thumbnail camera, framing, colors, and
role behavior.

Pros:

- Lowest risk.
- Does not drag in wxWidgets or the full desktop GUI stack.
- Fits the current upload/export contract.
- Easier to test on Android.

Cons:

- Not implementation-identical to Orca.
- Requires careful visual tuning.
- Pixel parity may still differ by shader/antialiasing/device GPU.

This is the best practical path if we want better visual parity without
destabilizing slicing/export.

### Approach 2: Extract A Minimal Orca Thumbnail Render Core

This tries to reuse Orca's render math and GL volume construction, but creates
a smaller Android-facing entry point instead of embedding the full desktop
`GLCanvas3D`.

Pros:

- More faithful than a clean-room Android renderer.
- Still avoids a full desktop UI port if extraction succeeds.
- Can reuse the current `ThumbnailData` handoff.

Cons:

- Requires untangling GUI dependencies from `GLCanvas3D`.
- May require local patches to Orca GUI source.
- Upstream merges could become harder.

This is the best route if Approach 1 cannot get close enough visually.

First extraction spike:

1. Use `OrcaThumbnailRenderPolicy` as the source-backed contract for the probe.
2. Create a native probe target, not a shipping path, that includes only the
   smallest possible Orca thumbnail subset.
3. Start with camera/framing and role semantics:
   - `Camera::ViewAngleType::Iso`
   - `Camera::ViewAngleType::Top_Plate`
   - object-box padding from `render_thumbnail_internal`
   - `for_picking`
   - `ban_light`
4. Do not include wxWidgets or `GLCanvas3D` as a class.
5. Feed the probe with the same mesh/color data used by the current Android EGL
   renderer.
6. Emit the same RGBA buffers used by the existing thumbnail callback.
7. Compare against the desktop Orca reference matrix.
8. Measure before enabling it for production:
   - plain G-code one-thumbnail overhead,
   - sliced-3MF five-thumbnail overhead,
   - APK/native library size delta,
   - device memory during copy/import/slice.

Acceptance for importing this subset:

- It compiles for `arm64-v8a` without wxWidgets.
- It does not require `GUI_App`, `MainFrame`, or `Plater`.
- It keeps `orca-metadata-benchmark` inside the existing thresholds.
- It improves at least one real desktop Orca reference metric without
  regressing the current matrix.
- It stays switchable through the existing renderer boundary until the device
  gates prove it.
- It does not remove the bounded supersampling path for small Fluidd-style
  thumbnails unless the replacement renderer proves equal or better visual
  metrics at the same requested output size.

### Approach 3: Full `GLCanvas3D`/GUI Render Stack Port

This attempts to build Orca's desktop thumbnail renderer on Android.

Pros:

- Closest to true implementation-level parity.
- Reuses Orca's real thumbnail code path.

Cons:

- Highest engineering cost.
- High risk of pulling in wxWidgets/GUI assumptions.
- Requires Android-compatible replacements for desktop GL context handling.
- Requires constructing `PartPlateList`, `GLVolumeCollection`,
  `GLShaderProgram`, `OpenGLManager`, camera, colors, and render volume state.
- More likely to break during Orca upstream updates.

This should be a research branch, not the default path.

## Recommended Plan

The plan only makes sense if we gate each phase. If a phase fails, we stop,
document the reason, and keep the current renderer plus stricter visual tests.

### Phase 0: Preserve Current Contract

Goal: keep current metadata behavior stable while renderer research happens.

Requirements:

- Plain `.gcode` renders only requested G-code thumbnails.
- Sliced `.gcode.3mf` renders package thumbnails only when exporting package
  metadata.
- Orca native code remains responsible for writing thumbnail blocks and 3MF
  package entries.
- Existing gates stay required:
  - `scripts/verify_android.sh local`
  - `scripts/verify_android.sh sliced-3mf-metadata <serial>`
  - `scripts/verify_android.sh orca-metadata-benchmark <serial>`
  - `scripts/release_gate_android.sh <serial>`

Acceptance:

- No regression in `packageThumbnails=false` for plain G-code.
- No regression in `packageThumbnails=true` for sliced 3MF.
- No slice-time regression from renderer research unless the new renderer is
  explicitly enabled.

### Phase 1: Build Desktop Orca Visual References

Goal: get real visual targets before rewriting renderer code.

Status: active and passing for the current checked-in matrix.

Completed:

- Built the vendored Orca source with Podman.
- Patched the local source build so the CLI thumbnail path requests OpenGL 3.3
  instead of accidentally requesting OpenGL 3.4 from the GLFW library version.
- Generated the first desktop Orca `.gcode.3mf` package with valid package
  role thumbnails.
- Extracted `plate.png`, `no_light.png`, `top.png`, and `pick.png` into
  `regression-fixtures/orca-thumbnail-references/simple-cube`.
- Ran strict Android comparison successfully for the current matrix:
  - `simple-cube`: `artifacts/egl-thumbnail-compare/20260514-195451`
  - `tall-box`: `artifacts/egl-thumbnail-compare/20260514-195506`
  - `small-perimeter`: `artifacts/egl-thumbnail-compare/20260514-195521`
  - `perimeter-array`: `artifacts/egl-thumbnail-compare/20260514-195537`
  - `narrow-strip`: `artifacts/egl-thumbnail-compare/20260514-195553`
  - `bridge-speed`: `artifacts/egl-thumbnail-compare/20260514-195608`
  - `complex-retraction-tower`: `artifacts/egl-thumbnail-compare/20260514-195624`
  - `arranged-objects`: `artifacts/egl-thumbnail-compare/20260514-195640`
  - `creality-k1-simple-cube`: `artifacts/egl-thumbnail-compare/20260514-195656`
- Re-ran the full strict Android matrix successfully on 2026-05-15 after adding
  the active multi-filament case and renderer/harness fixes:
  - `simple-cube`: `artifacts/egl-thumbnail-compare/20260515-071752`
  - `tall-box`: `artifacts/egl-thumbnail-compare/20260515-071807`
  - `small-perimeter`: `artifacts/egl-thumbnail-compare/20260515-071821`
  - `perimeter-array`: `artifacts/egl-thumbnail-compare/20260515-071836`
  - `narrow-strip`: `artifacts/egl-thumbnail-compare/20260515-071850`
  - `bridge-speed`: `artifacts/egl-thumbnail-compare/20260515-071904`
  - `complex-retraction-tower`: `artifacts/egl-thumbnail-compare/20260515-071919`
  - `arranged-objects`: `artifacts/egl-thumbnail-compare/20260515-071933`
  - `active-multifilament-two-objects`: `artifacts/egl-thumbnail-compare/20260515-071948`
  - `creality-k1-simple-cube`: `artifacts/egl-thumbnail-compare/20260515-072002`
- Added additional desktop Orca reference cases:
  - `tall-box`
  - `small-perimeter`
  - `perimeter-array`
  - `narrow-strip`
  - `bridge-speed`
  - `complex-retraction-tower`
  - `arranged-objects`
  - `creality-k1-simple-cube`
- Added `scripts/run_orca_thumbnail_reference_matrix.sh` to run every checked-in
  reference case against the matching STL on device at 512x512 reference
  resolution.
- Added `regression-fixtures/orca-thumbnail-references/matrix.json` as the
  single source of truth for the checked-in desktop Orca thumbnail matrix.
  Regeneration, local fixture auditing, and device comparison now read from that
  file instead of maintaining duplicate hard-coded case lists.
- Added `scripts/orca_thumbnail_reference_fixture_audit.py` and the
  `orca-thumbnail-reference-fixtures` verify mode so release gates fail locally
  if a matrix case is missing its model, manifest, source `.gcode.3mf`, package
  thumbnail entries, or nonblank 512x512 role PNGs.
- Updated the Android EGL renderer so:
  - `plate` and `no_light` use a yawed Orca-style package camera.
  - broad fixtures use wider horizontal fitting without forcing the very-flat
    crop on moderately broad bridge geometry.
  - `no_light` uses an explicit full-bright shader mode, keeping it distinct
    from the lit `plate` role.

Tasks:

- Generate desktop Orca sliced-3MF outputs with thumbnails enabled through a
  GUI-capable or patched reference path.
- Store references under `regression-fixtures/orca-metadata/references/`.
- Record:
  - Orca commit/build
  - input model
  - printer profile
  - filament profile
  - process profile
  - export command/manual steps
  - output package
  - audit JSON
- Include fixtures for:
  - simple cube
  - Benchy or complex organic STL
  - multi-filament
  - multi-object
  - multi-plate when MobileSlicer supports it

Acceptance:

- Reference packages contain actual `Metadata/plate_1.png`,
  `plate_no_light_1.png`, `top_1.png`, and `pick_1.png`.
- `scripts/orca_metadata_audit.py` reports comparable visual metric entries
  between desktop and MobileSlicer packages.
- `scripts/orca_thumbnail_visual_diff.py` can compare MobileSlicer role PNGs
  against the desktop package or an extracted reference directory.
- `scripts/extract_orca_thumbnail_references.py` extracts the four desktop Orca
  package role PNGs from a GUI-capable or patched desktop `.gcode.3mf` package
  into `regression-fixtures/orca-thumbnail-references/<case>/`.
- Desktop references are reproducible enough to use as regression fixtures.

Remaining:

- Run the full matrix on device whenever ADB is connected.
- Add complex/organic STL fixture.
- Add true multi-filament fixture.
- Add multi-plate fixture after MobileSlicer has multi-plate package export.

Blocker:

- Current desktop CLI references do not include G-code thumbnail blocks because
  Orca CLI uses a null thumbnail callback for those exports. That limitation
  must be handled by a GUI-driven export, a patched reference build, or a
  dedicated desktop fixture generator.
- `regression-fixtures/orca-thumbnail-references/simple-cube` is the expected
  first strict visual reference directory. It should contain `plate.png`,
  `no_light.png`, `top.png`, `pick.png`, `manifest.json`, and the source
  desktop Orca `.gcode.3mf` package.

### Phase 2: Android EGL Smoke Prototype

Goal: prove Android can create an offscreen GL render target and read pixels
reliably in the app/native environment.

Status: complete for the current device gate.

Current implementation:

- `OffscreenEglSmokeRunner` provides an automation-only Android pbuffer EGL
  smoke path.
- `scripts/verify_android.sh egl-thumbnail-smoke <serial>` builds/installs the
  debug app, starts `com.mobileslicer.action.EGL_THUMBNAIL_SMOKE`, and asserts:
  - nonblank pixels,
  - `glError=0`,
  - total/render/readback timing under thresholds.
- `OffscreenEglSliceThumbnailRenderer` is now a production-shaped renderer
  backend behind the same thumbnail renderer contract as the current software
  renderer. It creates an offscreen pbuffer, uploads real mesh triangles, draws
  the selected thumbnail role, reads RGBA pixels back, and records split timing:
  `eglCreateMs`, `uploadMs`, `drawMs`, `readPixelsMs`, `cleanupMs`, and
  `totalMs`.
- `scripts/verify_android.sh egl-slice-thumbnail-smoke <serial>` starts
  `com.mobileslicer.action.EGL_SLICE_THUMBNAIL_SMOKE` and asserts:
  - nonblank pixels,
  - minimum visible-pixel and bbox coverage,
  - `glError=0`,
  - upload/draw/readback/total timing under thresholds.
- `scripts/verify_android.sh egl-thumbnail-compare <serial>` stages the cube
  STL fixture into app-private storage, renders `plate`, `no_light`, `top`, and
  `pick` through both the current software renderer and the guarded EGL
  renderer, pulls PNG artifacts plus `metrics.json`, and fails on:
  - blank EGL output,
  - tiny bbox,
  - GL errors,
  - excessive total/draw/readback timing,
  - collapsed role outputs.
- The same gate now writes:
  - `software-vs-egl-visual-diff.json`
  - `software-vs-egl-visual-diff.md`
  - `orca-reference-visual-diff.json`
  - `orca-reference-visual-diff.md`
  under `artifacts/egl-thumbnail-compare/<timestamp>/`.
- `artifacts/egl-thumbnail-compare/latest` points to the latest comparison
  artifact directory.
- If `MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR` points to a directory or
  `.gcode.3mf` containing desktop Orca `plate`, `plate_no_light`, `top`, and
  `pick` PNGs, the gate compares MobileSlicer EGL output against those
  references. Missing desktop references are reported as `reference_missing`
  by default and become fatal when
  `MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1`.
- `scripts/release_gate_android.sh` runs the full checked-in desktop Orca
  thumbnail reference matrix before metadata benchmark work. The single
  `egl-thumbnail-compare` command remains the per-fixture debug entry point.
- `scripts/verify_android.sh orca-thumbnail-reference-fixtures` runs the local
  desktop-reference integrity gate before ADB/device comparison work.
- This path is intentionally disconnected from normal Slice and from current
  thumbnail export behavior unless package thumbnails are explicitly requested.
  Normal plain G-code Slice must continue to render only Orca-requested G-code
  thumbnails.

Tasks:

- Add an isolated debug/prototype renderer entry point.
- Create an EGL context without touching the main workspace viewer.
- Render a known triangle/cube into an RGBA buffer.
- Read pixels back.
- Save or return metrics for:
  - dimensions
  - nontransparent pixel ratio
  - bbox
  - average luma
  - elapsed render time

Acceptance:

- Works on the real device used for release testing through
  `egl-thumbnail-smoke`.
- Real mesh upload/draw/readback works on the same device through
  `egl-slice-thumbnail-smoke`.
- Fixture-backed role comparison works on the same device through
  `egl-thumbnail-compare`, and produces artifacts under
  `artifacts/egl-thumbnail-compare/<timestamp>/`.
- Does not require launching a visible GL surface.
- Does not corrupt the workspace viewer GL state.
- Render plus readback time is acceptable for thumbnail sizes.

Stop condition:

- If offscreen EGL cannot be made reliable on target devices, do not port
  `Canvas3D`; continue improving the current renderer and visual metrics.

### Phase 3: Match Orca Thumbnail Roles With A Minimal Renderer

Goal: replace the current software thumbnail pixels with a renderer that better
matches Orca's role behavior.

Status: started.

Completed for the current seven-case fixture matrix:

- `top` and `pick` use whole-bed top projection, matching Orca's `Top_Plate`
  framing behavior.
- `plate` and `no_light` use Orca's `Iso` package-thumbnail camera direction,
  expanded transformed volume box, and `zoom_to_box` style framing.
- `no_light` uses a full-bright shader mode so it stays visually distinct from
  the lit `plate` role.
- Tall/narrow models are covered by the checked-in `complex-retraction-tower`
  desktop Orca fixture so the angled crop cannot regress to the old over-zoomed
  framing.
- `gcode` stays on the previous low-cost thumbnail camera path.
- The strict desktop Orca visual gate passes for `plate`, `no_light`, `top`,
  and `pick` in all checked-in cases.

Tasks:

- Expand fixture-driven coverage for `OffscreenEglSliceThumbnailRenderer` beyond
  the cube STL into:
  - multi-object STL (covered by `arranged-objects`),
  - multi-filament colors,
  - true rotated object,
  - tall object (covered by `complex-retraction-tower`),
  - sliced `.gcode.3mf` package thumbnail roles.
- Implement role modes:
  - `gcode`
  - `plate`
  - `no_light`
  - `top`
  - `pick`
- Match Orca concepts:
  - orthographic camera for normal plate thumbnails
  - top camera for top/pick thumbnails
  - bed framing and object bbox margins
  - transparent background
  - filament colors
  - no-light darkening
  - pick render distinction
- Keep the existing upload API:
  - `orca_clear_slice_thumbnails`
  - `orca_add_slice_thumbnail_rgba`

Acceptance:

- Current metadata fixture gate still passes.
- Device benchmark still passes timing thresholds.
- Visual metrics are closer to desktop Orca references than the current
  renderer.
- No behavior change to Orca's native export writers.

### Phase 4: Investigate Orca GL Volume Reuse

Goal: decide whether Approach 2 is viable.

Tasks:

- Identify the smallest source slice needed to construct `GLVolumeCollection`
  from loaded `Model` data.
- Identify dependencies on:
  - wx app state
  - ImGui
  - GUI selection/gizmos
  - desktop OpenGL manager
  - shaders/resources
  - bed rendering
- Prototype a native function that builds enough Orca render volume state for
  one simple model without a desktop `Plater`.

Acceptance:

- A minimal Android-native test can build renderable Orca volumes.
- It does not require initializing the full desktop app singleton.
- It can render one reference model into `ThumbnailData`.

Stop condition:

- If this requires broad wx/GUI initialization, do not proceed to a full port
  unless we explicitly accept a large research branch.

### Phase 5: Full `Canvas3D` Port Research Branch

Goal: only attempt this if Phase 4 proves the dependency surface is manageable.

Tasks:

- Add GUI render sources to Android CMake behind a feature flag.
- Provide Android replacements/shims for desktop-only assumptions.
- Create an offscreen context compatible with Orca's framebuffer code.
- Load shaders/resources expected by `GLShaderProgram`.
- Construct or adapt:
  - `PartPlateList`
  - `GLVolumeCollection`
  - extruder colors
  - camera
  - bed/plate bounds
  - framebuffer type handling
- Implement a native API:
  - input: engine/model/role/width/height
  - output: RGBA pixels and timing/error metadata
- Route the existing Android thumbnail renderer interface to this native API
  when enabled.

Acceptance:

- Pixel or near-pixel parity against desktop Orca references for simple cases.
- Same role images as desktop Orca:
  - plate
  - no-light
  - top
  - pick
- No crashes or GL context leaks after repeated slices.
- No regression in normal workspace rendering.
- Release gates pass with the feature enabled.

Stop condition:

- If the port requires carrying a large private fork of Orca GUI code or
  destabilizes native slicing/export, keep it behind an experimental flag and
  ship the minimal renderer path instead.

## Validation Matrix

Every renderer candidate must pass these checks.

### Functional

- G-code thumbnail blocks still use Orca's native writer.
- QOI, PNG, JPG, BTT, and vendor-specific thumbnail tags are not silently
  converted to the wrong format.
- Sliced 3MF still contains required ZIP entries and relationships.
- `Metadata/plate_1.json` remains present and valid.

### Scope

- Plain `.gcode`:
  - `packageThumbnails=false`
  - rendered count equals requested G-code thumbnails
- Sliced `.gcode.3mf`:
  - `packageThumbnails=true`
  - package thumbnail roles are present

### Visual

- Nonblank visible pixels.
- Transparent background where expected.
- Bbox within expected frame.
- No-light is darker than plate.
- Pick is visually distinct from top.
- Desktop/mobile metric deltas trend down after renderer changes.
- The four package thumbnail roles are compared with
  `scripts/orca_thumbnail_visual_diff.py`, using alpha coverage, bbox, luma,
  and coverage-ratio thresholds instead of subjective screenshots.

### Performance

- Simple PNG thumbnail stays below the benchmark threshold.
- QOI thumbnail stays below the benchmark threshold.
- Sliced 3MF package thumbnail render stays below the benchmark threshold.
- 3MF write time stays below the benchmark threshold.
- No five-thumbnail work during normal plain G-code slices.

### Stability

- Repeated automation slices do not crash.
- GL context creation/destruction does not leak resources.
- Workspace viewer does not lose or corrupt its GL state after slicing.
- App can background/foreground after renderer use.

## Risk Review

### The Plan Makes Sense If

- We keep export wiring unchanged.
- We prove visual references first.
- We prove Android offscreen rendering before importing Orca GUI code.
- We compare measured visual output, not subjective screenshots.
- We keep every step behind gates and feature flags.

### The Plan Does Not Make Sense If

- We try to drop `GLCanvas3D.cpp` into Android CMake without isolating
  dependencies.
- We port wx/desktop GUI infrastructure just for thumbnails before proving
  visual benefit.
- We replace the current renderer before matching the current release gates.
- We treat desktop CLI references as pixel references when they do not contain
  desktop thumbnail images.
- We let thumbnail work change toolpaths, profile application, or package
  structure.

## Recommended Next Step

Continue Phase 1 and Phase 3 together:

- Run strict Android visual comparisons for the checked-in matrix.
- Add desktop Orca reference fixtures for multi-filament and arranged-object
- Add desktop Orca visual fixtures for multi-filament and true rotated-object
  cases. Arranged-object and non-Qidi Creality K1 coverage are already in the
  checked-in matrix.
- Tune the minimal Android EGL renderer only where measured fixture deltas show
  a real gap.

Do not start a full `Canvas3D` port yet. The current renderer passes the first
real desktop fixture, and a full port would add substantial dependency and
maintenance risk before the fixture matrix proves it is necessary.
