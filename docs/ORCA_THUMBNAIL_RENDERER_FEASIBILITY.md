# Orca Thumbnail Renderer Feasibility

This note answers whether MobileSlicer can use OrcaSlicer's own desktop
thumbnail renderer on Android for pixel-level thumbnail parity.

## Verdict

Directly embedding Orca's current `Canvas3D` thumbnail renderer into the Android
native wrapper is not a short-term-safe change. The renderer is part of Orca's
desktop GUI/OpenGL stack, not the headless `libslic3r` slice/export core that
MobileSlicer already builds for Android.

The answer to "import the stack if it is not too bad" is: import the thumbnail
contract and reusable render subset, not the whole desktop stack. The whole
stack is currently too broad for the shipping Android app because it crosses
from `libslic3r` into `libslic3r_gui`, wxWidgets, desktop `wxGLCanvas`, GUI
events, `GUI_App`, `MainFrame`, `Plater`, imgui/gizmo plumbing, desktop
`OpenGLManager`, and Orca's GUI-owned `GLVolumeCollection`.

MobileSlicer should keep the current exporter contract now:

- plain `.gcode` slices render only requested G-code thumbnail buffers;
- sliced `.gcode.3mf` exports render the extra package thumbnails;
- Orca's native exporter still writes and compresses the metadata;
- device gates prove package scope, timing, and ZIP/JSON/relationship shape.

Pixel-level Orca visual parity requires a separate renderer project. The best
next implementation path is an Android/offscreen renderer that intentionally
matches Orca camera, bed framing, material colors, lighting, no-light, top, and
pick modes. Porting `Canvas3D` wholesale should be treated as a high-risk
research branch.

The detailed port plan, step ordering, acceptance criteria, and stop conditions
are in `docs/ORCA_CANVAS3D_THUMBNAIL_PORT_PLAN.md`.

The source boundary is now executable:

```sh
python3 scripts/orca_thumbnail_port_audit.py --pretty
```

That gate fails if the checked-in Orca thumbnail entry points, package role
writers, Android EGL boundary, production renderer, automation renderer, or
release scope assertions disappear.

The Android side now has a named extraction contract:

- `OrcaThumbnailRenderPolicy.kt` records the Orca role/camera behavior currently
  mirrored by MobileSlicer.
- `OffscreenEglSliceThumbnailRenderer` consumes that policy.
- `OrcaThumbnailRenderPolicyTest` keeps the policy stable before any native
  probe is attempted.
- Small Fluidd-style thumbnail quality is improved through bounded 2x
  supersampling and downsampling for G-code thumbnails up to 150px, including
  the Qidi Q2 `150x150/PNG` legacy thumbnail contract. Package-role
  thumbnails up to 128px also supersample, which keeps sliced `.gcode.3mf`
  thumbnail assets under the same decoded-detail gate while staying inside the
  benchmark budget.
- The benchmark explicitly includes `48x48` and `128x128` PNG G-code thumbnail
  cases and requires anti-alias/detail variation in decoded PNG metrics.

This is not a full Orca renderer import yet. It is the required first step so a
future native import has a precise target and cannot silently mutate thumbnail
role behavior.

## Source Evidence

The relevant Orca thumbnail entry points are in the vendored source:

- `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.hpp`
  - Declares `render_thumbnail(...)`.
  - Declares static framebuffer renderers:
    `render_thumbnail_framebuffer(...)` and
    `render_thumbnail_framebuffer_ext(...)`.
- `vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp`
  - `render_thumbnail_framebuffer(...)` creates OpenGL framebuffers,
    renderbuffers/textures, calls `render_thumbnail_internal(...)`, and reads
    RGBA pixels with `glReadPixels`.
  - `render_thumbnail_internal(...)` depends on GUI rendering state:
    `PartPlateList`, `GLVolumeCollection`, `GLShaderProgram`, Orca camera
    modes, OpenGL manager capabilities, and desktop render volumes.
- `vendor/orcaslicer/src/slic3r/GUI/Plater.cpp`
  - Desktop export generates package roles by calling
    `get_view3D_canvas3D()->render_thumbnail(...)`.
  - It stores `thumbnail_data`, `no_light_thumbnail_data`,
    `top_thumbnail_data`, and `pick_thumbnail_data` into `StoreParams`.
- `vendor/orcaslicer/src/OrcaSlicer.cpp`
  - The CLI/desktop path can also regenerate package thumbnails through
    `Slic3r::GUI::GLCanvas3D::render_thumbnail_framebuffer(...)` after the
    GUI GL stack, part plates, GL volumes, colors, shader, and framebuffer type
    exist.

This is why the current Android native wrapper can use Orca's metadata writers
but cannot simply call the desktop thumbnail path without also porting a large
part of the GUI render stack.

## Practical Options

### Option A: keep improving MobileSlicer's renderer

This is the current shipping path.

- Match Orca's role intents:
  - normal plate thumbnail,
  - no-light plate thumbnail,
  - top thumbnail,
  - pick thumbnail.
- Keep visual gates:
  - nontransparent pixel ratio,
  - bounding box,
  - average luminance,
  - distinct role hashes,
  - optional desktop/mobile visual metric comparison when both packages carry
    PNG entries.
- Keep strict device gates:
  - plain G-code must log `packageThumbnails=false`;
  - sliced 3MF must log `packageThumbnails=true`;
  - benchmark timing thresholds must fail regressions.

This gives compatible Orca metadata with bounded performance and regression
coverage, but not guaranteed pixel identity with desktop Orca.

### Option B: write an Android offscreen GL renderer

This is the best route toward high visual parity without importing the entire
desktop GUI.

- Use the same model placement, bed dimensions, object colors, and camera role
  rules as Orca.
- Render through Android EGL/OpenGL ES or a small native offscreen renderer.
- Feed RGBA buffers into the existing Orca thumbnail callback/package fields.
- Validate against desktop fixtures with `scripts/orca_metadata_audit.py`
  visual metric comparisons first, then tighten to image-diff thresholds when
  stable.

This is feasible but should be a dedicated milestone because it touches render
infrastructure, not just metadata export.

### Option C: port Orca `Canvas3D`

This is the only path to true implementation-level parity, but it is the most
expensive and fragile.

Required work includes:

- building GUI-only Orca sources in the Android native project;
- replacing or adapting wx/OpenGL desktop assumptions;
- creating an Android-compatible offscreen GL context;
- constructing `PartPlateList`, `GLVolumeCollection`, shader programs, colors,
  camera state, and framebuffer support exactly as the desktop GUI expects;
- keeping that port aligned with upstream Orca GUI changes.

This should not block metadata parity. It is a research track for pixel-perfect
thumbnail parity only.

Current recommendation: do not make Option C the next shipping task. First
attempt a minimal extraction of Orca's thumbnail role math and framebuffer
behavior into the existing Android EGL renderer boundary. If that extraction
cannot improve visual parity without increasing slice time, then a separate
research branch can try the full GUI stack with explicit size/runtime budgets.

## Current Release Contract

The release gate now treats Orca metadata/thumbnail support as a first-class
contract:

- `scripts/verify_android.sh local` runs strict fixture validation.
- `scripts/release_gate_android.sh` runs:
  - `egl-thumbnail-smoke`,
  - `egl-slice-thumbnail-smoke`,
  - the full `run_orca_thumbnail_reference_matrix.sh` desktop-Orca comparison,
  - `sliced-3mf-metadata`,
  - `orca-metadata-benchmark`.
- `egl-slice-thumbnail-smoke` is the first real renderer gate: it exercises the
  offscreen EGL thumbnail backend with actual mesh upload, draw, RGBA readback,
  visual coverage checks, GL error checks, and split timing. It does not replace
  the default software thumbnail renderer or run during normal Slice.
- `egl-thumbnail-compare` adds artifact-backed role coverage. It renders the
  same STL fixture through both thumbnail backends, writes PNGs and JSON metrics
  under `artifacts/egl-thumbnail-compare/<timestamp>/`, and fails if the EGL
  renderer produces blank/tiny images, repeated role outputs, GL errors, or slow
  draw/readback timing.
- The release gate uses the seven-case matrix wrapper instead of only the
  default simple cube so tall, tiny, wide, narrow, bridge-like, and
  higher-triangle tall/narrow fixture framing all stay covered against desktop
  Orca references.
- The benchmark asserts:
  - PNG/QOI plain G-code renders one requested thumbnail and no package roles;
  - sliced 3MF renders the five package thumbnail roles;
  - thumbnail and package write timing stay under configurable thresholds.

Known non-parity remains explicit: thumbnail pixels are fixture-compared against
desktop Orca and gated, but this is not an implementation-identical desktop GUI
renderer port until Option B or C lands.

## Plan Review

The port plan is reasonable only as a staged renderer project. It does not make
sense as a direct replacement patch because the existing MobileSlicer export
contract is already correct and covered by release gates. The uncertain part is
not Orca metadata writing; it is whether Android can host enough of Orca's
desktop GUI/OpenGL render scene to produce matching pixels.

The safe order is:

1. keep current metadata/export gates green;
2. create desktop Orca visual references that actually contain thumbnail PNGs;
3. prove Android offscreen EGL/readback in isolation;
4. improve or replace the current renderer behind the existing thumbnail upload
   API;
5. only then attempt extracting or porting `GLCanvas3D` if the smaller renderer
   cannot reach acceptable visual parity.

The plan should be rejected or paused if it requires broad wx/desktop GUI
initialization before we have proven visual references and Android offscreen
rendering.

## Desktop Reference Capture Contract

The CLI-generated Orca references under `regression-fixtures/orca-metadata` are
metadata references only. For visual thumbnail parity, use a desktop Orca export
that actually contains package PNGs, then extract the four role images:

```sh
scripts/generate_orca_thumbnail_reference_fixture.sh
```

That command runs the local Orca binary/AppImage, writes diagnostic artifacts to
`artifacts/orca-thumbnail-reference-capture/<timestamp>/`, fails if Orca cannot
initialize OpenGL or omits package thumbnails, and extracts the reference
directory when the package is valid.

Captured status on 2026-05-13: the local OrcaSlicer 2.3.2 AppImage can slice
and write a `.gcode.3mf`, but it cannot currently produce desktop thumbnail
PNGs in this shell session. The CLI logs show:

```text
thumbnails stage: plate 1's thumbnail file also not there, need to regenerate
thumbnails stage: plate 1's no_light_thumbnail_file also not there, need to regenerate
thumbnails stage: plate 1's top_file  also not there, need to regenerate
opengl version 3.3.7
glfwInit Success.
Unable to init glew library, Error: Unknown error
init opengl failed! skip thumbnail generating
```

The resulting packages contain `Metadata/plate_1.gcode`,
`Metadata/plate_1.gcode.md5`, and `Metadata/plate_1.json`, but not
`Metadata/plate_1.png`, `Metadata/plate_no_light_1.png`, `Metadata/top_1.png`,
or `Metadata/pick_1.png`. These are metadata-only desktop outputs and are not
valid visual references.

The capture script now forces an X11-oriented environment and avoids forcing
software GL unless requested. If this workstation needs headless capture, the
next prerequisite is `xvfb-run` plus software GL:

```sh
MOBILE_SLICER_USE_XVFB=1 \
LIBGL_ALWAYS_SOFTWARE=1 \
scripts/generate_orca_thumbnail_reference_fixture.sh
```

If Xvfb is unavailable, generate the package through a GUI-capable Orca desktop
session and extract it with the validator below. The gate must keep failing on
missing PNG entries; accepting a metadata-only package would fake visual parity.

Manual extraction from a known-good desktop package uses the same validator:

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

Once that directory exists, run the EGL role comparison as a strict desktop-Orca
visual gate:

```sh
MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR=regression-fixtures/orca-thumbnail-references/simple-cube \
MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 \
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh egl-thumbnail-compare <serial>
```

Until that reference directory exists, `egl-thumbnail-compare` should keep
reporting `reference_missing` rather than claiming visual parity.
