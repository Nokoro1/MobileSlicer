# Orca Thumbnail Visual Reference

- Orca build: `OrcaSlicer-2.4.0-dev:
OrcaSlicer unknown`
- Source package: `creality-k1-simple-cube.gcode.3mf`
- Model: `/home/peanut/Development/MobileSlicer/regression-fixtures/slicing/mobileslicer_test_cube.stl`
- Printer profile: `Creality K1 (0.4 nozzle)`
- Filament profile: `Creality Generic PLA @K1-all`
- Process profile: `0.20mm Standard @Creality K1 (0.4 nozzle)`

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
