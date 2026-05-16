# STEP Multi-Plate Sliced Source Metadata Fixture

Captured from MobileSlicer device automation with:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh step-multi-plate-sliced-3mf-source-metadata 100.123.18.83:42917
```

- Source model: `regression-fixtures/import/occt_screw.step`
- Captured package: `step-multi-plate-sliced-source-metadata.gcode.3mf`
- Audit output: `audit.json`

This fixture extends the single-plate STEP package fixture by proving the same
Orca-style STEP source metadata survives a sliced multi-plate package export.
The automation loads the STEP-derived mesh as two workspace objects, writes
separate plate G-code for plate 1 and plate 2, then exports one sliced
`.gcode.3mf` package.

The required evidence is:

- `Metadata/model_settings.config` contains STEP/STP `source_file` evidence for
  at least two objects.
- The package contains `Metadata/plate_1.gcode` and `Metadata/plate_2.gcode`.
- The package contains `Metadata/plate_1.json` and `Metadata/plate_2.json`.
- Package thumbnails cover both plate indices.
- Object names, object-to-filament assignments, project settings, model
  settings, and slice info remain present.

This is app-produced because the vendored desktop Orca CLI currently rejects
STEP/STP input before it can create a true STEP-derived project reference. Do
not replace this fixture with a pre-converted STL package; that would erase the
source metadata this gate is designed to protect.
