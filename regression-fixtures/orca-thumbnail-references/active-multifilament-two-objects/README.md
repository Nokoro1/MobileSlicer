# Orca Thumbnail Visual Reference

- Orca build: `OrcaSlicer-2.4.0-dev:`
- Source package: `h2d-direct-two-filament-objects.gcode.3mf`
- Model: `two generated probe cubes`
- Printer profile: `Bambu Lab H2D 0.4 nozzle`
- Filament profile: `red PLA, blue PLA`
- Process profile: `0.20mm Standard`

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
