# OrcaSlicer Patch Workflow

Treat `vendor/orcaslicer/` as upstream-owned code.

Local changes should be small, intentional, and represented here as patch files when they are promoted beyond an experiment.

Recommended workflow:

```bash
git diff --output=patches/orcaslicer/YYYYMMDD-short-name.patch -- vendor/orcaslicer
```

Patch files should include:

- why the Android app needs the change
- whether the patch is temporary or intended to remain
- what upstream commit or release it was based on
- the test or fixture used to validate it

Do not edit vendored source casually. Prefer wrapper/shim code under `engine-wrapper/` when that can solve the problem.

## Current Patches

- `20260426-current-mobile-preview-and-perimeter.patch`
  - captures the current Android-local Orca delta in `PerimeterGenerator.cpp`
    and `libvgcode` viewer sources
  - covers the mobile preview range/width-scale support and the current
    perimeter-generation adjustment
  - should be reviewed and refreshed against the next upstream Orca sync before
    being reapplied
- `20260507-android-release-vendor-delta.patch`
  - captures the current release-candidate Android vendor delta in
    `TriangleSelector`, `Print`, `bbs_3mf`, and `libnest2d`
  - documented in `VENDOR_PATCHES.md`
  - must be refreshed or removed before the next upstream Orca sync
