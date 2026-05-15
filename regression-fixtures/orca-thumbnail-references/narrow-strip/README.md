# Orca Thumbnail Visual Reference

- Orca build: `OrcaSlicer-2.4.0-dev:
OrcaSlicer unknown`
- Source package: `narrow-strip.gcode.3mf`
- Model: `/home/peanut/Development/MobileSlicer/regression-fixtures/slicing/stage2_gap_infill_narrow_strip_fixture.stl`
- Printer profile: `Qidi Q2 0.4 nozzle`
- Filament profile: `Generic PLA @Qidi Q2 0.4 nozzle`
- Process profile: `0.20mm Standard @Qidi Q2`

Required role PNGs:

- `plate.png` from `Metadata/plate_1.png`
- `no_light.png` from `Metadata/plate_no_light_1.png`
- `top.png` from `Metadata/top_1.png`
- `pick.png` from `Metadata/pick_1.png`

Use this directory with:

```sh
MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR=this/directory \
MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 \
scripts/verify_android.sh egl-thumbnail-compare <serial>
```
