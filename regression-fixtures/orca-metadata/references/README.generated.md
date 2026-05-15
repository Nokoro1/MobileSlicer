# Generated Orca CLI References

These files were produced by OrcaSlicer CLI slicing with generated Qidi Q2
profiles under `regression-fixtures/orca-metadata/reference-inputs/`. Those
profiles are derived from the same MobileSlicer baseline automation config used
by `scripts/verify_android.sh orca-fixture-capture-mobile`, so visible metadata
drift is not hidden by accidentally comparing unrelated settings.

Orca's CLI path in `vendor/orcaslicer/src/OrcaSlicer.cpp` calls
`export_gcode(..., nullptr)`, so CLI-generated G-code does not contain thumbnail
blocks even when `thumbnails` is configured. Use these files for print-time,
filament, and non-thumbnail metadata comparisons only.

Thumbnail block parity is validated against MobileSlicer fixture outputs and the
Orca source contract in `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.hpp`
and `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.cpp`.
