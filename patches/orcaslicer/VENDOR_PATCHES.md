# Mobile Slicer Orca Vendor Patches

`vendor/orcaslicer/` is upstream-owned. Any local changes must be deliberate, documented, and easy to rebase.

## Active Patch Set

### `20260507-android-release-vendor-delta.patch`

- Captures the current release-candidate Android vendor delta.
- Files touched:
  - `vendor/orcaslicer/deps_src/libnest2d/include/libnest2d/placers/nfpplacer.hpp`
  - `vendor/orcaslicer/deps_src/libnest2d/include/libnest2d/selections/selection_boilerplate.hpp`
  - `vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp`
  - `vendor/orcaslicer/src/libslic3r/Print.cpp`
  - `vendor/orcaslicer/src/libslic3r/TriangleSelector.cpp`
  - `vendor/orcaslicer/src/libslic3r/TriangleSelector.hpp`
- Reason:
  - Android paint/cut/planning support and skirt parity need these native behaviors while keeping Android app code behind the wrapper boundary.
- Upstream status:
  - Local Mobile Slicer delta. Rebase manually during the next Orca sync.
- Rebase risk:
  - Medium/high. `TriangleSelector` and nesting/planning code are active Orca internals.
- Validation:
  - Full Android release gate passed at `artifacts/release-gates/20260507-074713/summary.md`.

## Policy

- Prefer wrapper or shim fixes over vendor edits.
- Keep patches small and named by date plus purpose.
- Refresh patch files whenever vendor source diffs change.
- Do not ship a release with undocumented vendor diffs.
