# Mobile Slicer Verification

Use `scripts/verify_android.sh` for repeatable local, APK, device, and Benchy checks.

## Quick Commands

```bash
scripts/verify_android.sh unit
scripts/verify_android.sh lint
scripts/verify_android.sh stubs
scripts/verify_android.sh script-tests
scripts/verify_android.sh apk
scripts/verify_android.sh local
```

After large Kotlin file-boundary refactors, run at least:

```bash
scripts/verify_android.sh unit
scripts/verify_android.sh apk
```

## Device Smoke

The script defaults to `$ANDROID_SERIAL`, then `RFCYA01ANVE`.

```bash
scripts/verify_android.sh device
scripts/verify_android.sh device RFCYA01ANVE
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh device-automation RFCYA01ANVE
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh slice-lifecycle RFCYA01ANVE
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh slice-regression RFCYA01ANVE
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh perf RFCYA01ANVE
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh perf-heavy RFCYA01ANVE
```

`device` builds and installs the debug APK only. `device-automation` builds, installs,
cold-launches the app, verifies a clean crash buffer, slices `mobileslicer_test_cube.stl`
through `com.mobileslicer.action.AUTOMATE_SLICE`, and fails if the status is not
`success:` or the emitted G-code is unexpectedly small. `slice-regression` runs a
physical-device matrix for brim, wall count, infill, support, temperature, and speed
changes, pulls emitted G-code, and checks that the expected G-code geometry or command
signals changed.
`slice-lifecycle` runs valid, rejected, and valid automation loads and asserts the
rejected load does not leave stale G-code output behind before a replacement model
slices successfully.

If device automation fails after a slice starts, the script captures context,
logcat, crash logcat, status text, and G-code head/tail snippets under
`artifacts/device-automation/`.

## Performance Gate

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh perf RFCYA01ANVE
```

`perf` is developer/release tooling only; it does not add or expose benchmark UI
inside the app. It builds and installs the `perfDebug` APK, measures cold startup,
then runs app-private automation slices for the small cube, bridge/support, and
small-perimeter-array fixtures plus medium and complex calibration STLs. Reports are written under
`artifacts/performance/<timestamp>/` with `report.json`, `report.md`, and a
`latest` symlink. Raw peak/final `dumpsys meminfo` snapshots are saved under the
run's `meminfo/` directory. Device condition snapshots are saved under
`device-state/`, including battery, thermal service, thermal zones, CPU
frequency, CPU load, top processes, and `getprop` output before and after the
run.

Default hard budgets are intentionally broad enough for normal phone variance:

- cold startup: `MOBILE_SLICER_PERF_MAX_STARTUP_MS`, default `4000`
- peak process PSS: `MOBILE_SLICER_PERF_MAX_PEAK_PSS_KB`, default `900000`
- Java heap: `MOBILE_SLICER_PERF_MAX_JAVA_HEAP_KB`, default `350000`
- native heap: `MOBILE_SLICER_PERF_MAX_NATIVE_HEAP_KB`, default `700000`
- graphics memory: `MOBILE_SLICER_PERF_MAX_GRAPHICS_KB`, default `350000`
- private-other memory: `MOBILE_SLICER_PERF_MAX_PRIVATE_OTHER_KB`, default `550000`
- small cube slice: `MOBILE_SLICER_PERF_MAX_SMALL_CUBE_SLICE_MS`, default `120000`
- bridge/support slice: `MOBILE_SLICER_PERF_MAX_BRIDGE_SUPPORT_SLICE_MS`, default `180000`
- perimeter-array slice: `MOBILE_SLICER_PERF_MAX_PERIMETER_ARRAY_SLICE_MS`, default `180000`
- medium speed-structure slice: `MOBILE_SLICER_PERF_MAX_MEDIUM_SPEED_STRUCTURE_SLICE_MS`, default `240000`
- complex VFA slice: `MOBILE_SLICER_PERF_MAX_COMPLEX_VFA_SLICE_MS`, default `300000`
- stress temperature-tower slice: `MOBILE_SLICER_PERF_MAX_STRESS_TEMPERATURE_TOWER_SLICE_MS`, default `600000`

The default `perf` run skips the largest 10MB temperature-tower stress fixture to
keep routine checks bounded. Include it explicitly before release candidates or
when chasing large-model performance:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
MOBILE_SLICER_PERF_INCLUDE_STRESS=1 \
scripts/verify_android.sh perf RFCYA01ANVE
```

Slice records include phase timings from the automation path:
`stagingMs`, `nativeLoadMs`, `placementMs`, `configMs`, `nativeSliceMs`,
`writeGcodeMs`, and `elapsedMs`. Memory attribution includes peak total PSS,
Java heap, native heap, graphics, private-other, and system memory. Use
`perf-heavy` while optimizing memory pressure; it runs only the medium, complex,
and stress fixtures.

`perf-heavy` automatically compares against the checked-in heavy device baseline
at `performance-baselines/perf-heavy-device-baseline.json` when that file is
present. Set `MOBILE_SLICER_PERF_BASELINE=none` to run without a baseline, or
set `MOBILE_SLICER_PERF_BASELINE=/path/to/report.json` to compare against a
specific run. The report includes a compact baseline comparison for elapsed
time, PSS, native heap, Java heap, output bytes, and preview metrics.

For optimization work that might leak or retain native/Java memory across
slices, repeat the heavy gate:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
MOBILE_SLICER_PERF_REPEAT_COUNT=2 \
scripts/verify_android.sh perf-heavy RFCYA01ANVE
```

Repeated records are named with `-r1`, `-r2`, and so on. The analyzer applies
the normal slice budgets to the base fixture name and also fails if peak memory
grows too much between the first and last repeat. Override the default 20%
growth allowance with `MOBILE_SLICER_PERF_REPEAT_MEMORY_GROWTH_PERCENT`.
The growth gate also requires more than `98304KB` of absolute growth by default
so small allocator or graphics warmup deltas do not fail optimization runs; tune
that floor with `MOBILE_SLICER_PERF_REPEAT_MEMORY_GROWTH_MIN_KB`.

To compare the full `perf` matrix against a previous run, pass the prior
`report.json`:

```bash
MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
MOBILE_SLICER_PERF_BASELINE=artifacts/performance/latest/report.json \
scripts/verify_android.sh perf RFCYA01ANVE
```

Baseline comparison defaults allow 25% startup, slice-time, and memory regression,
and 35% emitted-output-size regression. Override with
`MOBILE_SLICER_PERF_STARTUP_REGRESSION_PERCENT`,
`MOBILE_SLICER_PERF_SLICE_REGRESSION_PERCENT`,
`MOBILE_SLICER_PERF_MEMORY_REGRESSION_PERCENT`, and
`MOBILE_SLICER_PERF_OUTPUT_REGRESSION_PERCENT`. Percentage failures also require
an absolute increase, which keeps small measurement noise from blocking a run:
`MOBILE_SLICER_PERF_STARTUP_REGRESSION_MIN_MS` defaults to `250`,
`MOBILE_SLICER_PERF_SLICE_REGRESSION_MIN_MS` defaults to `750`,
`MOBILE_SLICER_PERF_MEMORY_REGRESSION_MIN_KB` defaults to `98304`, and
`MOBILE_SLICER_PERF_OUTPUT_REGRESSION_MIN_UNITS` defaults to `1024`.
Individual metrics can override that floor with
`MOBILE_SLICER_PERF_<METRIC>_REGRESSION_MIN`; `peak_pss_kb` defaults to a
higher `131072KB` floor because total PSS includes allocator and process memory
sampling noise beyond the native heap attribution.

Refresh `performance-baselines/perf-heavy-device-baseline.json` only after a
clean `local`, `slice-regression`, and two-pass `perf-heavy` run. The baseline
should be updated in the same change as the intentional performance improvement
or accepted device variance.

## Benchy Automation

```bash
scripts/verify_android.sh benchy /home/peanut/Documents/3DBenchy.stl
scripts/verify_android.sh benchy /home/peanut/Documents/3DBenchy.stl RFCYA01ANVE
```

This builds and installs the debug APK, stages the STL into app-private storage with `run-as`, starts the existing `com.mobileslicer.action.AUTOMATE_SLICE` path, prints the status file, and lists the app-private G-code output.

Expected successful status shape:

```text
success: model=... staged=... output=... bytes=... placementMs=... elapsedMs=... config=...
```

## Full Local Plus Device Smoke

```bash
scripts/verify_android.sh all
```

This runs JVM tests, builds the APK, installs it on the device, and launches the app.

## Notes

- `unit` maps to `cd android-app && ./gradlew testDebugUnitTest`.
- `lint` maps to `cd android-app && ./gradlew lintDebug`.
- `stubs` validates `engine-wrapper/orca-android-libslic3r/stub_inventory.json`
  against the active Android Orca subset stub files and the Android Orca CMake
  target references.
- `script-tests` runs shell syntax checks for verification/build scripts.
- `perf` runs non-UI physical-device startup/slicing performance checks and writes
  ignored reports under `artifacts/performance/`.
- `apk` maps to `cd android-app && ./gradlew assembleDebug`.
- `local` runs `script-tests`, `stubs`, `asset-tests`, `lint`, `unit`, and `apk`
  in that order.
- `release` runs release Kotlin compilation, `lintVitalRelease`, and R8 when
  signing is not configured; with release signing configured it builds the signed
  release APK and reports the artifact path. Set `MOBILE_SLICER_VERSION_NAME` and
  `MOBILE_SLICER_VERSION_CODE` for release-candidate builds.
- Device modes use `tools/adb` when present, falling back to `adb` on `PATH`.
- Benchy mode avoids shared-storage raw reads by copying the STL into the app-private `files/automation` directory before starting automation.
- Use the Samsung device for final runtime truth when Waydroid and physical-device results differ.
