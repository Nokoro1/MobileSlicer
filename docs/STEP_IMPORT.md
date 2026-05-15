# STEP Import

MobileSlicer treats STEP/STP as CAD source geometry and converts it to a
triangulated mesh before the normal workspace path sees it. The original STEP
file is preserved as source metadata, while the generated binary STL is used by
the existing viewer, arrange, auto-orient, paint, slice, and saved-project
paths.

This matches Orca's model: STEP is not sliced as live B-Rep geometry. It is
tessellated with linear and angular deflection controls, then handled as mesh
geometry.

## Android Native Boundary

The Android wrapper exposes:

```text
orca_convert_step_to_stl(inputPath, outputStlPath, linearDeflection, angleDeflection)
```

The function requires the OCCT-backed STEP importer. The Android dependency
builder now stages Orca's OCCT 7.6.0 dependency for the active ABI:

```bash
ANDROID_ABI=arm64-v8a engine-wrapper/orca-android-libslic3r/build-android-deps.sh
```

By default that installs OCCT at `/tmp/orca-deps-install/arm64-occt` or
`/tmp/orca-deps-install/x86_64-occt`, and the app CMake build enables STEP
automatically when that package exists. Custom locations can still be supplied
with `ORCA_ANDROID_OCCT_PREFIX` or the Gradle property
`orcaAndroid.occtPrefix`.

Without OCCT the native call fails explicitly instead of silently pretending
STEP support exists.

Default tessellation values used by the app importer:

```text
linearDeflection = 0.003
angleDeflection = 0.5
```

## Persistence Contract

Saved projects store `PlateObjectGeometrySource.StepMeshConvert` with:

```text
originalPath
convertedStlPath
linearDeflection
angleDeflection
```

The plate object remains `ImportedModelFormat.Stl` because the active workspace
artifact is the generated mesh. The geometry source is what preserves the STEP
origin and conversion settings.

## Implementation Notes

STEP/STP files enter through the same Android storage, share-sheet, external
intent, and Thingiverse import gates as STL/3MF. The app stages the original
file, converts it into cache as binary STL, parses bounds from that mesh, then
loads the mesh through the existing native model path.

Failure must leave the workspace unchanged and report that STEP tessellation
failed. Common causes are a build made before OCCT was staged, invalid STEP
data, or CAD geometry that produces no mesh faces.

The native converter does not trust Orca's `store_stl()` return value because
the vendored writer currently reports success even when a write fails. After
writing, MobileSlicer validates the binary STL header, triangle count, and byte
size before reporting STEP conversion success. This specifically guards against
the 84-byte empty-STL failure mode where tessellation produced geometry but no
loadable workspace mesh was written.

## Release Smoke

`regression-fixtures/import/occt_screw.step` is the STEP smoke fixture used by
the Android Orca import gate. Run it on a connected device with:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh orca-import-smoke <serial>
```

That gate slices three import surfaces through the app automation path:

- direct STL load
- Orca 3MF mesh extraction
- OCCT-backed STEP tessellation

The STEP case must produce a non-empty converted STL, load it through the
native plate-model path, slice it, and write non-trivial G-code.

## Orca Project Fixture Boundary

The current desktop-Orca CLI cannot be used to generate a true STEP-derived
project reference fixture. On the vendored Orca build, direct STEP input is
rejected as an unknown file format, and `--load-assemble-list` rejects STEP/STP
objects as unsupported. This is tracked by:

```bash
scripts/verify_android.sh orca-step-project-reference-probe
```

That probe is intentionally a limitation gate. It prevents us from checking in
a fake "STEP-derived" fixture that was really converted to STL before Orca saw
it. Real STEP project round-trip proof must therefore come from MobileSlicer's
Android import/export path, where the original STEP file and
`StepMeshConvert` geometry source are preserved while the converted STL is used
for mesh operations.

The project preservation audit already has a `--require-step-source` switch for
future app-produced STEP project packages. A valid STEP project package must
preserve STEP/STP source-file evidence instead of only preserving the converted
STL mesh.

## Source Metadata In Sliced Packages

STEP/STP slicing still uses the Orca-compatible tessellation model: the CAD file
is converted to STL before native slicing. The native load path must therefore
receive two paths for every object:

- the mesh path used for slicing and arrangement
- the original source path used for Orca `source_file` metadata

MobileSlicer now sends both through the `nativeLoadPlateModelsV2WithSourcePaths`
path. The native wrapper loads geometry from the mesh path, then restores the
original source path on the `ModelObject` and model-part volume source metadata
before exporting `.3mf`/`.gcode.3mf`. This keeps normal slice performance the
same as the existing tessellated mesh path while preserving STEP/STP evidence in
`Metadata/model_settings.config`.

The device gate for this behavior is:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh step-sliced-3mf-source-metadata
```

The committed regression fixture is:

```bash
scripts/verify_android.sh orca-step-sliced-source-fixture
```

It audits
`regression-fixtures/orca-project-references/step-sliced-source-metadata/step-sliced-source-metadata.gcode.3mf`
with `--require-step-source`, package thumbnails, plate JSON, sliced G-code,
project settings, and model settings requirements.

Do not replace this with a pre-converted STL-only path. That would slice, but it
would lose the source evidence needed for Orca-style project/package parity.
