# Release Performance Hardening

This is the checklist for optimization and release-hardening work. Use it when a
change can affect slice speed, preview responsiveness, memory pressure, emitted
G-code, native wrapper behavior, or app stability.

## Goal

Mobile Slicer should stay fast and stable without degrading output quality or
preview fidelity.

Accepted performance work must preserve these contracts:

- emitted G-code must not change unless the change is intentional and explained
- Prepare STL rendering must stay full fidelity
- Preview must render exact selected native `libvgcode` ranges, not silent
  substitutes or lossy geometry
- optimization telemetry must stay out of normal UI
- native memory improvements must be covered by repeatable gates, not one-off
  manual observations

## Required Reading

Before changing performance-sensitive code, read:

- `README/CORE_GOAL.md`
- `README/ARCHITECTURE.md`
- `README/VERIFICATION.md`
- `README/OPTIMIZATION_NOTES.md`
- `README/GCODE_PREVIEW.md`
- `README/NATIVE_SLICE_PERF_NOTES.md`

Use those docs as the source of truth for current boundaries. If a change
updates a boundary, update the relevant doc in the same commit.

## Normal Verification Stack

Run the smallest useful set while iterating, then finish with the larger gates.

```bash
scripts/verify_android.sh script-tests
cd android-app && ./gradlew testDebugUnitTest
cd android-app && ./gradlew assembleDebug
```

For native, automation, or perf-gate changes, run the focused test first:

```bash
python3 -m unittest scripts/test_analyze_mobile_performance.py
scripts/verify_android.sh script-tests
```

For real device performance validation:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh perf-heavy <serial>
```

For preview lifecycle and cleanup validation:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
scripts/verify_android.sh preview-lifecycle <serial>
```

For release confidence:

```bash
scripts/release_gate_android.sh <serial>
```

## Perf-Heavy Meaning

`perf-heavy` is the primary optimization gate for large-model slice pressure. It
runs medium, complex, and stress fixtures on a physical device and writes
artifacts under `artifacts/performance/<timestamp>/`.

Use `report.json` for exact values and `report.md` for quick review. A passing
run should have:

- `failures: []`
- unchanged output bytes unless the change intentionally affects G-code
- reasonable slice timing against the checked-in baseline
- no crash-buffer entries
- large-preview processor storage released during export
- retained processor move and line-end buffers at `0`

## Native RSS Checkpoints

Large-preview records include native RSS checkpoints:

- `native_after_finalize_rss_kb`
- `native_after_release_rss_kb`
- `native_after_stats_rss_kb`
- `native_before_return_rss_kb`

The analyzer enforces these invariants for large preview cases:

- processor preview storage must be released during export
- processor move and line-end buffers must not remain retained
- RSS must drop meaningfully after preview-storage release
- post-release checkpoints must be present
- RSS must not grow more than `8 MB` after stats update
- RSS must not grow more than `8 MB` before slice return

The `8 MB` allowances are configurable:

```bash
MOBILE_SLICER_PERF_MAX_NATIVE_AFTER_STATS_GROWTH_KB=8192
MOBILE_SLICER_PERF_MAX_NATIVE_BEFORE_RETURN_GROWTH_KB=8192
```

Do not loosen these values to hide a regression. Only tune them when repeated
physical-device runs prove normal variance is larger than the current allowance.

## Preview Lifecycle Meaning

`preview-lifecycle` exercises preview range loading, layer churn, repeated
preview open/close behavior, and memory cleanup. A passing run should have:

- `lifecycle_ready` equal to requested lifecycle cycles
- `slow_frames: 0`
- `rendered_frames` greater than `0`
- final native heap under budget
- final graphics memory near `0`

Use this gate after changes to:

- native preview loading
- `libvgcode` viewer ownership
- render-thread cleanup
- preview range planning
- cached G-code preview state
- workspace or activity lifecycle cleanup

## Baseline Policy

Do not refresh `performance-baselines/perf-heavy-device-baseline.json` just
because a run is noisy. Refresh only after:

1. the change is intentional and understood
2. `scripts/verify_android.sh local` passes
3. `slice-regression` passes on device
4. two-pass `perf-heavy` passes on device
5. emitted G-code bytes are reviewed
6. the commit explains why the baseline changed

When comparing repeated runs:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
MOBILE_SLICER_PERF_REPEAT_COUNT=2 \
scripts/verify_android.sh perf-heavy <serial>
```

## Rejected Optimization Paths

Do not reintroduce these as normal user-facing behavior:

- sampled or decimated Prepare STL geometry
- shell/proxy replacement for large G-code preview
- automatic preview layer-window substitution after a selected range fails
- extrusion width/height scaling to make large previews look cheaper
- Android-side G-code preview geometry generation as the default path
- forced native allocator trimming without measured benefit

Rejected paths can be revisited only behind an explicit debug or experiment
path, with device screenshots/perf evidence and no silent quality degradation.

## Commit Expectations

Performance commits should include:

- the implementation
- focused unit/script tests when logic changes
- device artifact paths for real-device validation
- a short explanation of output-quality impact
- doc updates when workflow or boundaries change

Keep unrelated dirty work out of the commit. If `engine-wrapper/orca_wrapper.cpp`
or other shared files already contain unrelated changes, stage only the intended
hunks.

