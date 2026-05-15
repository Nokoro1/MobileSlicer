# STEP Sliced Source Metadata Fixture

Captured from MobileSlicer device automation with:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh step-sliced-3mf-source-metadata
```

- Source model: `regression-fixtures/import/occt_screw.step`
- Captured package: `step-sliced-source-metadata.gcode.3mf`
- Audit output: `audit.json`

This fixture exists because the vendored desktop Orca CLI rejects STEP/STP
inputs in both direct input and assemble-list project generation paths. It is
therefore not a desktop-Orca reference fixture. It is a MobileSlicer-produced
package fixture proving the Android STEP path preserves Orca-style
`source_file` metadata after tessellating the STEP file to STL for slicing.

The required evidence is:

- `Metadata/model_settings.config` contains part-level `source_file` metadata
  ending in `.step`.
- The package includes sliced G-code, plate JSON, package thumbnails, model
  settings, project settings, and slice info.
- `scripts/orca_3mf_project_preservation_audit.py --require-step-source`
  passes against the package.

Do not replace this fixture with a pre-converted STL package. That would prove
mesh slicing only, not STEP source metadata preservation.
