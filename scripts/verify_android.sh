#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android-app"
APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
PERF_APK_PATH="$ANDROID_DIR/app/build/outputs/apk/perfDebug/app-perfDebug.apk"
PACKAGE_NAME="com.mobileslicer"
MAIN_ACTIVITY="com.mobileslicer/.MainActivity"
AUTOMATION_ACTION="com.mobileslicer.action.AUTOMATE_SLICE"
PREVIEW_INTERACTION_ACTION="com.mobileslicer.action.PROFILE_PREVIEW_INTERACTION"
EGL_THUMBNAIL_SMOKE_ACTION="com.mobileslicer.action.EGL_THUMBNAIL_SMOKE"
EGL_SLICE_THUMBNAIL_SMOKE_ACTION="com.mobileslicer.action.EGL_SLICE_THUMBNAIL_SMOKE"
EGL_THUMBNAIL_COMPARE_ACTION="com.mobileslicer.action.EGL_THUMBNAIL_COMPARE"
DEFAULT_SLICE_SMOKE_STL="$ROOT_DIR/regression-fixtures/slicing/mobileslicer_test_cube.stl"
STEP_IMPORT_SMOKE_MODEL="$ROOT_DIR/regression-fixtures/import/occt_screw.step"
THREE_MF_IMPORT_SMOKE_MODEL="$ROOT_DIR/vendor/orcaslicer/resources/handy_models/OrcaCube_v2.3mf"
SUPPORT_SLICE_SMOKE_STL="$ROOT_DIR/regression-fixtures/slicing/stage2_bridge_speed_fixture.stl"
PERIMETER_ARRAY_SLICE_SMOKE_STL="$ROOT_DIR/regression-fixtures/slicing/stage2_small_perimeter_array_fixture.stl"
MEDIUM_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/volumetric_speed/SpeedTestStructure.stl"
COMPLEX_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/vfa/vfa.stl"
STRESS_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/temperature_tower/temperature_tower.stl"
BENCHY_AUTOMATION_CONFIG_PATH="$ROOT_DIR/regression-fixtures/orca-metadata/mobile-baseline-config.json"
[[ -f "$BENCHY_AUTOMATION_CONFIG_PATH" ]] || {
  printf '[verify_android] ERROR: missing automation baseline config: %s\n' "$BENCHY_AUTOMATION_CONFIG_PATH" >&2
  exit 1
}
BENCHY_AUTOMATION_CONFIG="$(python3 - "$BENCHY_AUTOMATION_CONFIG_PATH" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    print(json.dumps(json.load(fh), separators=(",", ":")))
PY
)"
CURRENT_AUTOMATION_SERIAL=""
CURRENT_AUTOMATION_LABEL=""
CURRENT_AUTOMATION_OUTPUT_PATH=""
CURRENT_AUTOMATION_STATUS_PATH=""
AUTOMATION_LAST_OUTPUT_PATH=""
AUTOMATION_LAST_STATUS=""
AUTOMATION_LAST_BYTES=""
AUTOMATION_LAST_STAGING_MS=""
AUTOMATION_LAST_NATIVE_LOAD_MS=""
AUTOMATION_LAST_ELAPSED_MS=""
AUTOMATION_LAST_PLACEMENT_MS=""
AUTOMATION_LAST_CONFIG_MS=""
AUTOMATION_LAST_NATIVE_SLICE_MS=""
AUTOMATION_LAST_THUMBNAIL_MS=""
AUTOMATION_LAST_WRITE_GCODE_MS=""
AUTOMATION_LAST_PREVIEW_MOVES=""
AUTOMATION_LAST_PREVIEW_CACHE_BUILT=""
AUTOMATION_LAST_PREVIEW_CACHE_COMPLETE=""
AUTOMATION_LAST_PREVIEW_CACHED_VERTICES=""
AUTOMATION_LAST_PREVIEW_CACHE_BUILD_MS=""
AUTOMATION_LAST_PREVIEW_INFO_RICH=""
AUTOMATION_LAST_PREVIEW_INFO_ENRICHED_RICH=""
AUTOMATION_LAST_PREVIEW_INFO_LINE_TYPES=""
AUTOMATION_LAST_PREVIEW_INFO_FILAMENTS=""
AUTOMATION_LAST_PREVIEW_INFO_LAYERS=""
AUTOMATION_LAST_PREVIEW_PLAN_MS=""
AUTOMATION_LAST_PREVIEW_LOAD_MS=""
AUTOMATION_LAST_PREVIEW_RANGES=""
AUTOMATION_LAST_PREVIEW_LOADED_START=""
AUTOMATION_LAST_PREVIEW_LOADED_END=""
AUTOMATION_LAST_PREVIEW_LOADED_LAYERS=""
AUTOMATION_LAST_PREVIEW_LOAD_SUCCESS=""
AUTOMATION_LAST_PREVIEW_LOAD_GL_UNAVAILABLE=""
AUTOMATION_LAST_NATIVE_GCODE_BYTES=""
AUTOMATION_LAST_PROCESSOR_MOVE_BYTES=""
AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES=""
AUTOMATION_LAST_PROCESSOR_MOVE_BYTES_RETAINED=""
AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES_RETAINED=""
AUTOMATION_LAST_PROCESSOR_RELEASE_MS=""
AUTOMATION_LAST_PROCESSOR_MOVES_RELEASED_DURING_EXPORT=""
AUTOMATION_LAST_NATIVE_EXPORT_START_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_SETUP_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_LAYERS_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_FOOTER_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_GENERATION_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_FINALIZE_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_RELEASE_RSS_KB=""
AUTOMATION_LAST_NATIVE_AFTER_STATS_RSS_KB=""
AUTOMATION_LAST_NATIVE_BEFORE_RETURN_RSS_KB=""
AUTOMATION_LAST_PREVIEW_LAYER_COUNT_BYTES=""
AUTOMATION_LAST_EXACT_PREVIEW_CACHE_ELIGIBLE=""
AUTOMATION_LAST_PEAK_PSS_KB=""
AUTOMATION_LAST_PEAK_JAVA_HEAP_KB=""
AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB=""
AUTOMATION_LAST_PEAK_GRAPHICS_KB=""
AUTOMATION_LAST_PEAK_PRIVATE_OTHER_KB=""
AUTOMATION_LAST_PEAK_SYSTEM_KB=""
AUTOMATION_LAST_CACHE_TOTAL_KB=""
AUTOMATION_LAST_CACHE_ORCA_TEMP_KB=""
AUTOMATION_LAST_CACHE_GENERATED_GCODE_KB=""
AUTOMATION_LAST_CACHE_STAGED_MODEL_KB=""
PERF_LAST_PEAK_PSS_KB=0
PERF_LAST_PEAK_JAVA_HEAP_KB=0
PERF_LAST_PEAK_NATIVE_HEAP_KB=0
PERF_LAST_PEAK_GRAPHICS_KB=0
PERF_LAST_PEAK_PRIVATE_OTHER_KB=0
PERF_LAST_PEAK_SYSTEM_KB=0
PERF_CURRENT_MEMINFO_DIR=""
PERF_CURRENT_CASE=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/verify_android.sh unit
  scripts/verify_android.sh lint
  scripts/verify_android.sh stubs
  scripts/verify_android.sh script-tests
  scripts/verify_android.sh orca-fixture-gate
  scripts/verify_android.sh orca-gcode-metadata-parity
  scripts/verify_android.sh orca-thumbnail-reference-fixtures
  scripts/verify_android.sh orca-3mf-roundtrip-device [serial]
  scripts/verify_android.sh orca-rich-project-fixture
  scripts/verify_android.sh orca-rich-project-roundtrip-contract
  scripts/verify_android.sh orca-rich-project-roundtrip-device [serial]
  scripts/verify_android.sh orca-modifier-project-fixture
  scripts/verify_android.sh orca-modifier-project-roundtrip-contract
  scripts/verify_android.sh orca-modifier-project-roundtrip-device [serial]
  scripts/verify_android.sh orca-height-range-project-fixture
  scripts/verify_android.sh orca-height-range-project-roundtrip-contract
  scripts/verify_android.sh orca-height-range-project-roundtrip-device [serial]
  scripts/verify_android.sh orca-step-sliced-source-fixture
  scripts/verify_android.sh orca-step-multi-plate-sliced-source-fixture
  scripts/verify_android.sh orca-project-parity-matrix
  scripts/verify_android.sh orca-project-parity-device-matrix [serial]
  scripts/verify_android.sh orca-active-multifilament-reference-probe
  scripts/verify_android.sh orca-step-project-reference-probe
  scripts/verify_android.sh orca-fixture-capture-mobile [serial]
  scripts/verify_android.sh printer-thumbnail-compatibility
  scripts/verify_android.sh fluidd-thumbnail-metadata [serial]
  scripts/verify_android.sh orca-metadata-benchmark [serial]
  scripts/verify_android.sh egl-thumbnail-smoke [serial]
  scripts/verify_android.sh egl-slice-thumbnail-smoke [serial]
  scripts/verify_android.sh egl-thumbnail-compare [serial]
  scripts/verify_android.sh asset-tests
  scripts/verify_android.sh apk
  scripts/verify_android.sh release
  scripts/verify_android.sh local
  scripts/verify_android.sh install [serial]
  scripts/verify_android.sh device [serial]
  scripts/verify_android.sh device-automation [serial]
  scripts/verify_android.sh slice-lifecycle [serial]
  scripts/verify_android.sh slice-regression [serial]
  scripts/verify_android.sh object-process [serial]
  scripts/verify_android.sh orca-object-label-parity [serial]
  scripts/verify_android.sh orca-import-smoke [serial]
  scripts/verify_android.sh sliced-3mf-metadata [serial]
  scripts/verify_android.sh step-sliced-3mf-source-metadata [serial]
  scripts/verify_android.sh step-multi-plate-sliced-3mf-source-metadata [serial]
  scripts/verify_android.sh multi-plate-sliced-3mf-metadata [serial]
  scripts/verify_android.sh skirt-parity [serial]
  scripts/verify_android.sh profile-ui [serial]
  scripts/verify_android.sh responsiveness [serial]
  scripts/verify_android.sh responsiveness-slice [serial]
  scripts/verify_android.sh responsiveness-heavy [serial]
  scripts/verify_android.sh preview-interaction [serial]
  scripts/verify_android.sh preview-churn [serial]
  scripts/verify_android.sh preview-lifecycle [serial]
  scripts/verify_android.sh perf [serial]
  scripts/verify_android.sh perf-heavy [serial]
  scripts/verify_android.sh benchy <local-stl-path> [serial]
  scripts/verify_android.sh all [serial]

Modes:
  unit    Run JVM debug unit tests.
  lint    Run Android lint for the debug variant.
  stubs   Validate the Android Orca native stub inventory.
  script-tests
          Run shell syntax checks for verification scripts.
  orca-fixture-gate
          Validate the Orca metadata fixture manifest and any committed outputs.
          Set MOBILE_SLICER_STRICT_ORCA_FIXTURES=1 to fail when declared
          desktop Orca references, MobileSlicer outputs, or pending cases are
          missing. The local release path enables strict mode automatically.
  orca-gcode-metadata-parity
          Audit committed G-code fixtures for visible metadata parity: print
          time, filament usage, cost, layer count, generator markers,
          toolchanges, thumbnail signatures, and desktop-vs-MobileSlicer drift.
          Set MOBILE_SLICER_FAIL_ORCA_METADATA_DRIFT=1 only after the desktop
          Orca and MobileSlicer fixtures are generated from matched profiles.
  orca-thumbnail-reference-fixtures
          Validate the checked-in desktop Orca thumbnail reference matrix,
          manifests, source sliced 3MF packages, role PNGs, and source models.
  orca-3mf-roundtrip-device
          Build, install, import a real Orca 3MF package on device, export a
          MobileSlicer project 3MF without slicing, pull it, and compare the
          source package against the MobileSlicer round-trip output. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  orca-active-multifilament-reference-probe
          Try to generate a desktop Orca active multi-filament thumbnail
          reference. This is intentionally strict: it fails if Orca collapses
          the package to one active filament or omits package thumbnails.
  orca-fixture-capture-mobile
          Build, install, run the supported Orca metadata fixture cases on a
          device, pull MobileSlicer outputs into regression-fixtures/orca-metadata/mobile,
          and audit each captured output. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  printer-thumbnail-compatibility
          Audit the printer thumbnail compatibility matrix so live/gated/source
          support claims stay honest across Fluidd, Mainsail, OctoPrint, Qidi,
          BTT, QOI/JPG, and sliced 3MF package consumers.
  fluidd-thumbnail-metadata
          Build, install, run one plain G-code slice with Fluidd/Moonraker
          thumbnails enabled, pull the output, require both 48x48/PNG and
          300x300/PNG thumbnail blocks, require decoded visual/detail metrics,
          and assert thumbnail timing stays under
          MOBILE_SLICER_METADATA_FLUIDD_THUMBNAIL_MAX_MS. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1. When
          MOBILE_SLICER_MOONRAKER_URL is set, this also uploads the generated
          G-code to Moonraker, polls /server/files/metadata, and requires
          Moonraker to report both Fluidd thumbnails.
  orca-metadata-benchmark
          Build, install, run no-thumbnail, PNG, QOI, and sliced 3MF metadata
          fixture slices on a device, audit the outputs, and write a timing
          report under artifacts/orca-metadata-benchmark. Also asserts plain
          G-code slices do not render package thumbnails, sliced 3MF does, and
          thumbnail/write timings stay under configurable thresholds. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  egl-thumbnail-smoke
          Build, install, run the isolated offscreen EGL thumbnail prototype,
          assert it renders nonblank pixels, and fail if render/readback timing
          exceeds thresholds. This does not slice and does not touch production
          thumbnail export behavior. Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  egl-slice-thumbnail-smoke
          Build, install, run the offscreen EGL slice thumbnail renderer against
          a real mesh upload/draw/readback path, and fail if visual metrics are
          blank, GL reports an error, or timing exceeds thresholds. This does
          not replace the production software thumbnail renderer. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  egl-thumbnail-compare
          Build, install, render all package thumbnail roles for a real STL
          fixture through both the current software renderer and the guarded EGL
          backend, pull PNG/JSON artifacts, and fail on blank, tiny, collapsed,
          or slow EGL role outputs. Also writes software-vs-EGL visual diff
          reports and, when MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR points
          at desktop Orca PNGs or a .gcode.3mf package, desktop-Orca visual diff
          reports. Set MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 to make
          missing desktop references fatal. Set MOBILE_SLICER_EGL_COMPARE_MODEL
          to compare a non-default fixture. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  asset-tests
          Run script tests for generated Orca Android profile assets.
  apk     Build the debug APK.
  release Run release compile/lint/R8 checks. Builds the signed release APK
          when release signing credentials are configured.
  local   Run stub inventory validation, lint, unit tests, and build the debug APK.
  install Build and install the debug APK on a connected device.
  device  Alias for install; no UI automation, log pulls, or runtime probing.
  device-automation
          Build, install, cold-launch, and assert the process stays alive with
          an empty crash buffer, then slice the cube fixture.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  slice-lifecycle
          Build, install, run valid/invalid/valid automation slices, and assert
          rejected loads do not emit stale G-code.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  slice-regression
          Build, install, run a physical-device slicing parameter matrix, pull
          emitted G-code, and assert expected geometry/G-code changes.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  object-process
          Build, install, run object-scoped process override slices, assert
          native Orca accepted object override keys while ignoring metadata-only
          probes, and compare emitted G-code to prove the override changes only
          one object in a two-object project. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  orca-import-smoke
          Build, install, run automation slicing through the supported Orca
          import surface: STL direct load, Orca 3MF mesh extraction, and STEP
          tessellation. Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  sliced-3mf-metadata
          Build, install, export a sliced .gcode.3mf on device, pull it, and
          audit Orca thumbnail entries, relationships, and Metadata/plate_1.json.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  multi-plate-sliced-3mf-metadata
          Build, install, export a differentiated two-plate sliced .gcode.3mf
          on device, pull it, and audit Orca package entries, per-plate
          relationships, byte-distinct plate G-code, byte-distinct plate bbox
          JSON, and required role thumbnails. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  skirt-parity
          Build, install, run baseline/combined/per-object/brim skirt slices on
          device, pull emitted G-code, and assert Orca skirt features survive
          Android export with finite in-bed first-layer geometry. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  profile-ui
          Build, install, cold-launch, and assert the process stays alive with
          an empty crash buffer. Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  responsiveness
          Build, install, cold-launch, time representative UI navigation steps,
          and capture MobileSlicerPerf logcat events under artifacts/responsiveness.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  responsiveness-slice
          Build, install, run the cube automation slice, and capture model-load,
          slice, preview planning, memory, and MobileSlicerPerf timing artifacts
          under artifacts/responsiveness-slice. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  responsiveness-heavy
          Build, install, run medium, complex, and stress fixture automation
          slices, and capture per-case responsiveness artifacts under
          artifacts/responsiveness-heavy. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  preview-interaction
          Build, install, slice the medium fixture, open the real G-code preview
          renderer, scrub inside a chunk, switch display modes, switch chunks
          when available, and capture runtime preview frame metrics under
          artifacts/preview-interaction. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  preview-churn
          Build, install, slice the medium fixture, open the real G-code preview
          renderer, then rapidly queue alternating preview chunk/display requests
          before waiting for the newest request to render. Captures artifacts
          under artifacts/preview-churn. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  preview-lifecycle
          Build, install, slice the medium fixture, open the real G-code preview
          renderer, then cycle viewer pause/resume teardown while sampling
          device memory. Captures artifacts under artifacts/preview-lifecycle.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  perf    Build/install perfDebug, run non-UI startup and slicing benchmarks,
          write reports under artifacts/performance, and fail on hard budgets
          or optional baseline regressions. Requires
          MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  perf-heavy
          Run the same measurement stack on only the medium, complex, and stress
          fixtures. Intended for memory-pressure optimization checks. Set
          MOBILE_SLICER_PERF_REPEAT_COUNT to repeat each slice and check
          cross-run memory growth. Uses performance-baselines/perf-heavy-device-baseline.json
          by default when present; set MOBILE_SLICER_PERF_BASELINE=none to disable.
  benchy  Build, install, stage an STL app-private, and run automation slicing.
          Requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1.
  all     Run local checks and install the debug APK.

Device serial defaults to $ANDROID_SERIAL. Pass a serial explicitly when more than one device is connected.
USAGE
}

log() {
  printf '[verify_android] %s\n' "$*"
}

fail() {
  printf '[verify_android] ERROR: %s\n' "$*" >&2
  exit 1
}

env_int_or_default() {
  local name="$1"
  local default_value="$2"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    printf '%s\n' "$default_value"
    return 0
  fi
  [[ "$value" =~ ^[0-9]+$ ]] || fail "$name must be an integer, got: $value"
  printf '%s\n' "$value"
}

add_memory_budget_failure() {
  local -n failures_ref="$1"
  local label="$2"
  local value="$3"
  local budget="$4"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    failures_ref+=("$label missing")
  elif [[ "$value" -gt "$budget" ]]; then
    failures_ref+=("$label ${value}KB exceeds budget ${budget}KB")
  fi
}

capture_device_artifacts() {
  local exit_code="$1"
  if [[ -z "$CURRENT_AUTOMATION_SERIAL" ]]; then
    return 0
  fi
  local artifact_root="$ROOT_DIR/artifacts/device-automation"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local safe_label="${CURRENT_AUTOMATION_LABEL:-unknown}"
  safe_label="${safe_label//[^A-Za-z0-9_.-]/_}"
  local artifact_dir="$artifact_root/$stamp-$safe_label"
  mkdir -p "$artifact_dir"
  {
    printf 'exit_code=%s\n' "$exit_code"
    printf 'serial=%s\n' "$CURRENT_AUTOMATION_SERIAL"
    printf 'label=%s\n' "$CURRENT_AUTOMATION_LABEL"
    printf 'output_path=%s\n' "$CURRENT_AUTOMATION_OUTPUT_PATH"
    printf 'status_path=%s\n' "$CURRENT_AUTOMATION_STATUS_PATH"
  } > "$artifact_dir/context.txt"
  adb_device "$CURRENT_AUTOMATION_SERIAL" logcat -d -v time > "$artifact_dir/logcat.txt" 2>&1 || true
  adb_device "$CURRENT_AUTOMATION_SERIAL" logcat -b crash -d -v time > "$artifact_dir/crash-logcat.txt" 2>&1 || true
  if [[ -n "$CURRENT_AUTOMATION_STATUS_PATH" ]] &&
      adb_device "$CURRENT_AUTOMATION_SERIAL" shell run-as "$PACKAGE_NAME" test -f "$CURRENT_AUTOMATION_STATUS_PATH"; then
    adb_device "$CURRENT_AUTOMATION_SERIAL" exec-out run-as "$PACKAGE_NAME" cat "$CURRENT_AUTOMATION_STATUS_PATH" > "$artifact_dir/status.txt" 2>&1 || true
  fi
  if [[ -n "$CURRENT_AUTOMATION_OUTPUT_PATH" ]] &&
      adb_device "$CURRENT_AUTOMATION_SERIAL" shell run-as "$PACKAGE_NAME" test -f "$CURRENT_AUTOMATION_OUTPUT_PATH"; then
    adb_device "$CURRENT_AUTOMATION_SERIAL" exec-out run-as "$PACKAGE_NAME" sh -c "head -n 200 '$CURRENT_AUTOMATION_OUTPUT_PATH'" > "$artifact_dir/gcode-head.txt" 2>&1 || true
    adb_device "$CURRENT_AUTOMATION_SERIAL" exec-out run-as "$PACKAGE_NAME" sh -c "tail -n 200 '$CURRENT_AUTOMATION_OUTPUT_PATH'" > "$artifact_dir/gcode-tail.txt" 2>&1 || true
  fi
  log "Captured device automation artifacts: $artifact_dir"
}

on_error() {
  local exit_code="$?"
  trap - ERR
  capture_device_artifacts "$exit_code" || true
  exit "$exit_code"
}

trap on_error ERR

adb_bin() {
  if [[ -x "$ROOT_DIR/tools/adb" ]]; then
    printf '%s\n' "$ROOT_DIR/tools/adb"
  elif command -v adb >/dev/null 2>&1; then
    command -v adb
  else
    fail "adb not found. Expected $ROOT_DIR/tools/adb or adb on PATH."
  fi
}

device_serial() {
  local requested="${1:-}"
  if [[ -n "$requested" ]]; then
    printf '%s\n' "$requested"
  elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
    printf '%s\n' "$ANDROID_SERIAL"
  else
    fail "Device serial not provided. Pass a serial argument or set ANDROID_SERIAL."
  fi
}

gradle() {
  (cd "$ANDROID_DIR" && ./gradlew "$@")
}

run_unit() {
  log "Running JVM debug unit tests"
  gradle testDebugUnitTest
}

run_lint() {
  log "Running Android debug lint"
  gradle lintDebug
}

run_stub_inventory() {
  log "Validating Android Orca stub inventory"
  "$ROOT_DIR/scripts/verify_stub_inventory.py"
}

run_script_tests() {
  log "Running verification script syntax checks"
  bash -n "$ROOT_DIR/scripts/verify_android.sh"
  bash -n "$ROOT_DIR/scripts/release_gate_android.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_reference_fixtures.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_thumbnail_reference_fixture.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_thumbnail_reference_matrix.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_rich_project_fixture.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_modifier_project_fixture.sh"
  bash -n "$ROOT_DIR/scripts/generate_orca_height_range_project_fixture.sh"
  bash -n "$ROOT_DIR/scripts/probe_orca_active_multifilament_reference.sh"
  bash -n "$ROOT_DIR/scripts/probe_orca_step_project_reference.sh"
  bash -n "$ROOT_DIR/scripts/run_orca_thumbnail_reference_matrix.sh"
  python3 -m py_compile "$ROOT_DIR"/scripts/*.py
  (cd "$ROOT_DIR" && python3 scripts/test_orca_metadata_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_metadata_fixture_gate.py)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_gcode_metadata_parity_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_thumbnail_port_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/orca_thumbnail_extraction_probe_gate.py --pretty >/dev/null)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_thumbnail_visual_diff.py)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_thumbnail_reference_fixture_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_orca_3mf_project_preservation_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_printer_thumbnail_compatibility_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_extract_orca_thumbnail_references.py)
  (cd "$ROOT_DIR" && python3 scripts/test_analyze_mobile_performance.py)
  (cd "$ROOT_DIR" && python3 scripts/test_analyze_preview_responsiveness.py)
  (cd "$ROOT_DIR" && python3 scripts/test_gcode_preview_layer_counter.py)
  (cd "$ROOT_DIR" && python3 scripts/test_regression_fixtures.py)
  (cd "$ROOT_DIR" && python3 scripts/test_release_worktree_audit.py)
  (cd "$ROOT_DIR" && python3 scripts/test_process_profile_coverage.py)
  (cd "$ROOT_DIR" && python3 scripts/test_validate_orca_profile_bundle.py)
  (cd "$ROOT_DIR" && python3 scripts/test_validate_orca_export_with_cli.py)
  (cd "$ROOT_DIR" && python3 scripts/orca_thumbnail_reference_fixture_audit.py --pretty >/dev/null)
}

run_orca_fixture_gate() {
  log "Running Orca metadata fixture gate"
  local strict_args=()
  if [[ "${MOBILE_SLICER_STRICT_ORCA_FIXTURES:-0}" == "1" ]]; then
    strict_args+=(--strict-references)
  fi
  (cd "$ROOT_DIR" && python3 scripts/orca_metadata_fixture_gate.py --pretty "${strict_args[@]}")
}

run_orca_3mf_project_preservation_gate() {
  log "Running Orca 3MF project preservation gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects/h2d-direct-two-filament-objects.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing Orca 3MF project preservation fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 1 \
    --min-object-count 2 \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-project-settings \
    --pretty)
}

run_orca_rich_project_fixture_gate() {
  log "Running rich Orca 3MF project fixture gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/rich-object-settings/rich-object-settings.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing rich Orca project fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --pretty)
}

run_orca_3mf_roundtrip_contract_gate() {
  log "Running Orca 3MF round-trip preservation contract gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects/h2d-direct-two-filament-objects.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing Orca 3MF round-trip fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --source-3mf "$fixture" \
    --roundtrip-3mf "$fixture" \
    --min-plate-count 1 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-project-settings \
    --pretty)
}

run_orca_rich_project_roundtrip_contract_gate() {
  log "Running rich Orca 3MF round-trip preservation contract gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/rich-object-settings/rich-object-settings.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing rich Orca 3MF round-trip fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --source-3mf "$fixture" \
    --roundtrip-3mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --pretty)
}

run_orca_modifier_project_fixture_gate() {
  log "Running modifier Orca 3MF project fixture gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/modifier-object-settings/modifier-object-settings.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing modifier Orca project fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-modifier-volumes \
    --require-modifier-settings \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --pretty)
}

run_orca_modifier_project_roundtrip_contract_gate() {
  log "Running modifier Orca 3MF round-trip preservation contract gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/modifier-object-settings/modifier-object-settings.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing modifier Orca 3MF round-trip fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --source-3mf "$fixture" \
    --roundtrip-3mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-modifier-volumes \
    --require-modifier-settings \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --pretty)
}

run_orca_project_parity_matrix_gate() {
  log "Running Orca project parity matrix"
  run_orca_3mf_project_preservation_gate
  run_orca_rich_project_fixture_gate
  run_orca_rich_project_roundtrip_contract_gate
  run_orca_modifier_project_fixture_gate
  run_orca_modifier_project_roundtrip_contract_gate
  run_orca_height_range_project_fixture_gate
  run_orca_height_range_project_roundtrip_contract_gate
  run_orca_step_sliced_source_fixture_gate
  run_orca_step_multi_plate_sliced_source_fixture_gate
}

run_orca_project_parity_device_matrix_gate() {
  local serial="$1"
  log "Running Orca project parity device matrix on $serial"
  run_orca_3mf_roundtrip_device_gate "$serial"
  run_orca_rich_project_roundtrip_device_gate "$serial"
  run_orca_modifier_project_roundtrip_device_gate "$serial"
  run_orca_height_range_project_roundtrip_device_gate "$serial"
}

run_orca_height_range_project_fixture_gate() {
  log "Running height-range Orca 3MF project fixture gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/height-range-project/height-range-project.3mf"
  [[ -f "$fixture" ]] || fail "Missing height-range Orca project fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-layer-ranges \
    --require-layer-range-settings \
    --require-project-thumbnails \
    --require-project-settings \
    --pretty)
}

run_orca_height_range_project_roundtrip_contract_gate() {
  log "Running height-range Orca 3MF round-trip preservation contract gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/height-range-project/height-range-project.3mf"
  [[ -f "$fixture" ]] || fail "Missing height-range Orca 3MF round-trip fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --source-3mf "$fixture" \
    --roundtrip-3mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-object-settings \
    --require-layer-ranges \
    --require-layer-range-settings \
    --require-project-thumbnails \
    --require-project-settings \
    --pretty)
}

run_orca_step_sliced_source_fixture_gate() {
  log "Running STEP sliced source metadata fixture gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/step-sliced-source-metadata/step-sliced-source-metadata.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing STEP sliced source metadata fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 1 \
    --min-object-count 1 \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --require-step-source \
    --pretty)
}

run_orca_step_multi_plate_sliced_source_fixture_gate() {
  log "Running STEP multi-plate sliced source metadata fixture gate"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/step-multi-plate-sliced-source-metadata/step-multi-plate-sliced-source-metadata.gcode.3mf"
  [[ -f "$fixture" ]] || fail "Missing STEP multi-plate sliced source metadata fixture: $fixture"
  (cd "$ROOT_DIR" && python3 scripts/orca_3mf_project_preservation_audit.py \
    --three-mf "$fixture" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --require-step-source \
    --min-step-source-object-count 2 \
    --pretty)
}

run_orca_3mf_roundtrip_device_gate_for_fixture() {
  local serial="$1"
  local fixture="$2"
  local fixture_label="$3"
  local output_slug="$4"
  local min_plate_count="$5"
  local min_object_count="$6"
  local require_object_settings="$7"
  local require_modifier_settings="${8:-0}"
  local require_layer_range_settings="${9:-0}"
  require_device_automation
  require_automation_fixture "$fixture" "$fixture_label"
  install_apk "$serial"

  local app_model_path
  app_model_path="$(stage_app_private_file "$serial" "$fixture" | tail -n 1)"
  local stamp output_path status_path
  stamp="$(date +%Y%m%d-%H%M%S)"
  output_path="/data/data/$PACKAGE_NAME/files/automation/$output_slug-$stamp.3mf"
  status_path="$output_path.status.txt"
  set_current_automation_context "$serial" "$output_slug" "$output_path" "$status_path"

  log "Running $fixture_label round-trip export on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$output_path" "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local start_output
  start_output="$(adb_device "$serial" shell "am start -W \
    -a '$AUTOMATION_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_model_path '$app_model_path' \
    --es automation_output_path '$output_path' \
    --es automation_status_path '$status_path' \
    --ez automation_export_project_3mf true")"
  printf '%s\n' "$start_output"
  if printf '%s\n' "$start_output" | grep -Eq '^(Error|Exception):'; then
    fail "Automation activity did not start."
  fi

  log "Project round-trip automation status"
  local status
  status="$(wait_for_status "$serial" "$status_path")"
  printf '%s\n' "$status"
  [[ "$status" == success:* ]] || fail "Project 3MF round-trip automation did not report success."
  [[ "$(status_metric "$status" "projectRoundTrip")" == "1" ]] ||
    fail "Project 3MF round-trip status did not include projectRoundTrip=1."

  local tmp_dir roundtrip_path
  tmp_dir="$(mktemp -d)"
  roundtrip_path="$tmp_dir/mobileslicer-roundtrip.3mf"
  pull_app_private_file "$serial" "$output_path" "$roundtrip_path"

  local audit_args=(
    "$ROOT_DIR/scripts/orca_3mf_project_preservation_audit.py"
    --source-3mf "$fixture" \
    --roundtrip-3mf "$roundtrip_path" \
    --min-plate-count "$min_plate_count" \
    --min-object-count "$min_object_count" \
    --require-plate-names \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-project-settings \
    --pretty
  )
  if [[ "$require_object_settings" == "1" ]]; then
    audit_args+=(--require-object-settings)
  fi
  if [[ "$require_modifier_settings" == "1" ]]; then
    audit_args+=(--require-modifier-volumes --require-modifier-settings)
  fi
  if [[ "$require_layer_range_settings" == "1" ]]; then
    audit_args+=(--require-layer-ranges --require-layer-range-settings)
  fi
  log "Auditing MobileSlicer 3MF round-trip output"
  python3 "${audit_args[@]}"
  rm -rf "$tmp_dir"
  clear_current_automation_context
  assert_no_crash_after_launch "$serial"
}

run_orca_3mf_roundtrip_device_gate() {
  local serial="$1"
  local fixture="$ROOT_DIR/regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects/h2d-direct-two-filament-objects.gcode.3mf"
  run_orca_3mf_roundtrip_device_gate_for_fixture \
    "$serial" \
    "$fixture" \
    "Orca 3MF round-trip source package" \
    "orca-3mf-roundtrip-device" \
    1 \
    2 \
    0 \
    0 \
    0
}

run_orca_rich_project_roundtrip_device_gate() {
  local serial="$1"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/rich-object-settings/rich-object-settings.gcode.3mf"
  run_orca_3mf_roundtrip_device_gate_for_fixture \
    "$serial" \
    "$fixture" \
    "rich Orca 3MF round-trip source package" \
    "orca-rich-project-roundtrip-device" \
    2 \
    2 \
    1 \
    0 \
    0
}

run_orca_modifier_project_roundtrip_device_gate() {
  local serial="$1"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/modifier-object-settings/modifier-object-settings.gcode.3mf"
  run_orca_3mf_roundtrip_device_gate_for_fixture \
    "$serial" \
    "$fixture" \
    "modifier Orca 3MF round-trip source package" \
    "orca-modifier-project-roundtrip-device" \
    2 \
    2 \
    1 \
    1 \
    0
}

run_orca_height_range_project_roundtrip_device_gate() {
  local serial="$1"
  local fixture="$ROOT_DIR/regression-fixtures/orca-project-references/height-range-project/height-range-project.3mf"
  run_orca_3mf_roundtrip_device_gate_for_fixture \
    "$serial" \
    "$fixture" \
    "height-range Orca 3MF round-trip source package" \
    "orca-height-range-project-roundtrip-device" \
    2 \
    2 \
    1 \
    0 \
    1
}

run_orca_gcode_metadata_parity() {
  log "Running Orca G-code metadata parity audit"
  local drift_args=()
  if [[ "${MOBILE_SLICER_FAIL_ORCA_METADATA_DRIFT:-0}" == "1" ]]; then
    drift_args+=(--fail-on-drift)
  fi
  (cd "$ROOT_DIR" && python3 scripts/orca_gcode_metadata_parity_audit.py --pretty "${drift_args[@]}")
}

run_orca_fixture_gate_strict() {
  MOBILE_SLICER_STRICT_ORCA_FIXTURES=1 run_orca_fixture_gate
}

run_orca_gcode_metadata_parity_strict_contract() {
  MOBILE_SLICER_FAIL_ORCA_METADATA_DRIFT=0 run_orca_gcode_metadata_parity
}

run_orca_thumbnail_reference_fixture_gate() {
  log "Auditing desktop Orca thumbnail reference fixtures"
  (cd "$ROOT_DIR" && python3 scripts/orca_thumbnail_reference_fixture_audit.py --pretty)
}

run_orca_active_multifilament_reference_probe() {
  log "Probing desktop Orca active multi-filament thumbnail reference generation"
  (cd "$ROOT_DIR" && scripts/probe_orca_active_multifilament_reference.sh)
}

run_orca_step_project_reference_probe() {
  log "Probing desktop Orca STEP project reference availability"
  (cd "$ROOT_DIR" && scripts/probe_orca_step_project_reference.sh)
}

run_printer_thumbnail_compatibility_gate() {
  log "Running printer thumbnail compatibility audit"
  (cd "$ROOT_DIR" && python3 scripts/printer_thumbnail_compatibility_audit.py --pretty)
}

run_asset_generator_tests() {
  log "Running Orca asset generator script tests"
  (cd "$ROOT_DIR" && python3 scripts/test_generate_orca_printer_assets.py)
}

build_apk() {
  log "Building debug APK"
  gradle assembleDebug
  [[ -f "$APK_PATH" ]] || fail "APK missing after build: $APK_PATH"
  log "APK ready: $APK_PATH"
}

build_perf_apk() {
  log "Building perf debug APK"
  gradle assemblePerfDebug
  [[ -f "$PERF_APK_PATH" ]] || fail "Perf debug APK missing after build: $PERF_APK_PATH"
  log "Perf debug APK ready: $PERF_APK_PATH"
}

build_release_apk() {
  local signing_configured=0
  local version_name="${MOBILE_SLICER_VERSION_NAME:-0.1.0}"
  local version_code="${MOBILE_SLICER_VERSION_CODE:-1}"
  log "Release version inputs: versionName=$version_name versionCode=$version_code"
  if [[ -n "${MOBILE_SLICER_RELEASE_STORE_FILE:-}" &&
        -n "${MOBILE_SLICER_RELEASE_STORE_PASSWORD:-}" &&
        -n "${MOBILE_SLICER_RELEASE_KEY_ALIAS:-}" &&
        -n "${MOBILE_SLICER_RELEASE_KEY_PASSWORD:-}" ]]; then
    signing_configured=1
  elif [[ -f "$ANDROID_DIR/release-signing.properties" ]]; then
    signing_configured=1
  fi

  if [[ "$signing_configured" == "1" ]]; then
    log "Building signed release APK"
    gradle :app:assembleRelease
    local release_apk="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
    [[ -f "$release_apk" ]] || fail "Release APK missing after build: $release_apk"
    log "Release APK ready: $release_apk"
  else
    log "Release signing not configured; running release compile, lint, and R8 checks without packaging."
    gradle :app:compileReleaseKotlin :app:lintVitalRelease :app:minifyReleaseWithR8
    log "Release compile, lintVital, and R8 checks passed"
  fi
}

adb_device() {
  local serial="$1"
  shift
  local adb
  adb="$(adb_bin)"
  "$adb" -s "$serial" "$@"
}

ensure_package_enabled() {
  local serial="$1"
  adb_device "$serial" shell pm enable "$PACKAGE_NAME" >/dev/null 2>&1 || true
}

require_device_automation() {
  case "${MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION:-}" in
    1|true|TRUE|yes|YES)
      ;;
    *)
      fail "Device automation requires MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1. Use 'install'/'device' for delivery-only device verification."
      ;;
  esac
}

require_device() {
  local serial="$1"
  log "Checking device $serial"
  adb_device "$serial" get-state >/dev/null
}

require_automation_fixture() {
  local path="$1"
  local description="$2"
  [[ -f "$path" ]] || fail "$description fixture is missing: $path"
}

install_apk() {
  local serial="$1"
  build_apk
  require_device "$serial"
  log "Installing debug APK on $serial"
  adb_device "$serial" install -r "$APK_PATH"
  ensure_package_enabled "$serial"
}

install_perf_apk() {
  local serial="$1"
  build_perf_apk
  require_device "$serial"
  log "Installing perf debug APK on $serial"
  adb_device "$serial" install -r "$PERF_APK_PATH"
  ensure_package_enabled "$serial"
}

launch_app() {
  local serial="$1"
  log "Cold-launching $PACKAGE_NAME on $serial"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  adb_device "$serial" shell am start -W -n "$MAIN_ACTIVITY"
  log "Recent app logs"
  adb_device "$serial" logcat -d -v brief | grep -E 'MobileSlicer|AndroidRuntime|FATAL EXCEPTION' || true
}

launch_app_for_perf() {
  local serial="$1"
  log "Cold-launching $PACKAGE_NAME on $serial for performance measurement"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local output
  output="$(adb_device "$serial" shell am start -W -n "$MAIN_ACTIVITY")"
  printf '%s\n' "$output"
  local startup_ms
  startup_ms="$(printf '%s\n' "$output" | awk -F: '/TotalTime/ {gsub(/ /, "", $2); print $2; exit}')"
  [[ "$startup_ms" =~ ^[0-9]+$ ]] || fail "Unable to parse startup TotalTime from am start output."
  printf '%s\n' "$startup_ms"
}

assert_no_crash_after_launch() {
  local serial="$1"
  sleep 4
  local crash_log
  crash_log="$(adb_device "$serial" logcat -b crash -d -t 200)"
  if [[ -n "$crash_log" ]]; then
    printf '%s\n' "$crash_log" >&2
    fail "Crash log buffer is not empty after launch."
  fi
  local pid
  pid="$(adb_device "$serial" shell pidof "$PACKAGE_NAME" || true)"
  [[ -n "$pid" ]] || fail "$PACKAGE_NAME is not running after launch."
  log "$PACKAGE_NAME running with pid $pid and clean crash buffer"
}

assert_clean_crash_buffer() {
  local serial="$1"
  local crash_log
  crash_log="$(adb_device "$serial" logcat -b crash -d -t 200)"
  if [[ -n "$crash_log" ]]; then
    printf '%s\n' "$crash_log" >&2
    fail "Crash log buffer is not empty."
  fi
  log "Crash buffer clean"
}

dump_ui_xml() {
  local serial="$1"
  adb_device "$serial" shell uiautomator dump /sdcard/mobileslicer-window.xml >/dev/null
  adb_device "$serial" shell cat /sdcard/mobileslicer-window.xml
}

ui_text_bounds() {
  local serial="$1"
  local text="$2"
  local xml
  xml="$(dump_ui_xml "$serial")"
  XML_PAYLOAD="$xml" python3 - "$text" <<'PY'
import os, re, sys, xml.etree.ElementTree as ET
target = sys.argv[1]
root = ET.fromstring(os.environ["XML_PAYLOAD"])
for node in root.iter("node"):
    if node.attrib.get("text") == target or node.attrib.get("content-desc") == target:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local serial="$1"
  local text="$2"
  local coords
  coords="$(ui_text_bounds "$serial" "$text")" || fail "Unable to find UI text: $text"
  log "Tapping '$text'"
  adb_device "$serial" shell input tap $coords
  sleep 1
}

tap_text_after_horizontal_swipe() {
  local serial="$1"
  local text="$2"
  local coords
  if ! coords="$(ui_text_bounds "$serial" "$text")"; then
    adb_device "$serial" shell input swipe 1150 720 250 720 350
    sleep 1
    coords="$(ui_text_bounds "$serial" "$text")" || fail "Unable to find UI text after horizontal swipe: $text"
  fi
  log "Tapping '$text'"
  adb_device "$serial" shell input tap $coords
  sleep 1
}

assert_text_visible() {
  local serial="$1"
  local text="$2"
  ui_text_bounds "$serial" "$text" >/dev/null || fail "Expected UI text is not visible: $text"
  log "Verified UI text: $text"
}

assert_device_unlocked_for_ui() {
  local serial="$1"
  local xml
  xml="$(dump_ui_xml "$serial")"
  if [[ "$xml" == *"keyguard"* || "$xml" == *"Enter PIN"* ]]; then
    fail "Device is locked. Unlock $serial before running profile-ui automation."
  fi
}

ensure_home_visible() {
  local serial="$1"
  for _ in 1 2 3 4; do
    if ui_text_bounds "$serial" "Mobile Slicer" >/dev/null && ui_text_bounds "$serial" "Open Profiles" >/dev/null; then
      return 0
    fi
    adb_device "$serial" shell input keyevent KEYCODE_BACK
    sleep 1
  done
  ui_text_bounds "$serial" "Mobile Slicer" >/dev/null && ui_text_bounds "$serial" "Open Profiles" >/dev/null || fail "Unable to return to app home with Open Profiles visible."
}

run_install_only() {
  local serial="$1"
  install_apk "$serial"
}

run_device_automation_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "default slice smoke STL"
  install_apk "$serial"
  launch_app "$serial"
  assert_no_crash_after_launch "$serial"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "device-smoke" "0"
  assert_no_crash_after_launch "$serial"
}

run_orca_import_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "STL import smoke model"
  require_automation_fixture "$THREE_MF_IMPORT_SMOKE_MODEL" "Orca 3MF import smoke model"
  require_automation_fixture "$STEP_IMPORT_SMOKE_MODEL" "STEP import smoke model"
  install_apk "$serial"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "orca-import-stl" "0"
  run_automation_slice "$THREE_MF_IMPORT_SMOKE_MODEL" "$serial" "orca-import-3mf" "0"
  run_automation_slice "$STEP_IMPORT_SMOKE_MODEL" "$serial" "orca-import-step" "0"
  assert_no_crash_after_launch "$serial"
}

object_process_override_config() {
  python3 - "$BENCHY_AUTOMATION_CONFIG" <<'PY'
import json
import sys

config = json.loads(sys.argv[1])
config["mobile_slicer_object_process_overrides"] = [
    {
        "mobileObjectId": 1,
        "plateObjectIndex": 0,
        "selectedProcessId": "verify_android_object_override",
        "config": {
            "wall_loops": 5,
            "only_one_wall_first_layer": True,
            "printer_settings_id": "ignored_metadata_probe",
            "mobile_slicer_ignored_probe": True,
        },
    }
]
print(json.dumps(config, separators=(",", ":")))
PY
}

object_process_isolation_config() {
  local mode="$1"
  python3 - "$BENCHY_AUTOMATION_CONFIG" "$mode" <<'PY'
import json
import sys

config = json.loads(sys.argv[1])
config.update({
    "brim_width": 0,
    "skirts": 0,
    "sparse_infill_density": 0,
    "top_shell_layers": 2,
    "bottom_shell_layers": 2,
    "wall_loops": 2,
    "gcode_label_objects": True,
})
if sys.argv[2] == "override":
    config["mobile_slicer_object_process_overrides"] = [
        {
            "mobileObjectId": 1,
            "plateObjectIndex": 0,
            "selectedProcessId": "verify_android_object_isolation",
            "config": {
                "wall_loops": 5,
            },
        }
    ]
print(json.dumps(config, separators=(",", ":")))
PY
}

modifier_process_override_config() {
  local modifier_path="$1"
  python3 - "$BENCHY_AUTOMATION_CONFIG" "$modifier_path" <<'PY'
import json
import sys

config = json.loads(sys.argv[1])
config.update({
    "brim_width": 0,
    "skirts": 0,
    "sparse_infill_density": 0,
    "top_shell_layers": 2,
    "bottom_shell_layers": 2,
    "wall_loops": 2,
    "gcode_label_objects": True,
})
config["mobile_slicer_modifier_process_overrides"] = [
    {
        "mobileObjectId": 1,
        "modifierId": 9001,
        "plateObjectIndex": 0,
        "label": "Whole object modifier",
        "path": sys.argv[2],
        "selectedProcessId": "verify_android_modifier_override",
        "config": {
            "wall_loops": 5,
        },
    }
]
print(json.dumps(config, separators=(",", ":")))
PY
}

assert_modifier_process_override_log() {
  local serial="$1"
  local logcat
  logcat="$(adb_device "$serial" logcat -d -v time | grep 'modifier_process_override' || true)"
  printf '%s\n' "$logcat"
  [[ "$logcat" == *"acceptedKeys="*"wall_loops"* ]] ||
    fail "Modifier process override did not accept wall_loops."
}

generate_object_process_isolation_3mf() {
  local output_path="$1"
  python3 - "$output_path" <<'PY'
import pathlib
import sys
import zipfile

output = pathlib.Path(sys.argv[1])
output.parent.mkdir(parents=True, exist_ok=True)

vertices = [
    (0, 0, 0), (22, 0, 0), (22, 22, 0), (0, 22, 0),
    (0, 0, 8), (22, 0, 8), (22, 22, 8), (0, 22, 8),
]
triangles = [
    (0, 2, 1), (0, 3, 2),
    (4, 5, 6), (4, 6, 7),
    (0, 1, 5), (0, 5, 4),
    (1, 2, 6), (1, 6, 5),
    (2, 3, 7), (2, 7, 6),
    (3, 0, 4), (3, 4, 7),
]

def mesh_object(object_id):
    verts = "\n".join(
        f'          <vertex x="{x}" y="{y}" z="{z}"/>'
        for x, y, z in vertices
    )
    tris = "\n".join(
        f'          <triangle v1="{a}" v2="{b}" v3="{c}"/>'
        for a, b, c in triangles
    )
    return f'''    <object id="{object_id}" type="model">
      <mesh>
        <vertices>
{verts}
        </vertices>
        <triangles>
{tris}
        </triangles>
      </mesh>
    </object>'''

model = f'''<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
  <resources>
{mesh_object(1)}
{mesh_object(2)}
  </resources>
  <build>
    <item objectid="1" transform="1 0 0 0 1 0 0 0 1 70 120 0"/>
    <item objectid="2" transform="1 0 0 0 1 0 0 0 1 176 120 0"/>
  </build>
</model>
'''

with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as package:
    package.writestr("[Content_Types].xml", '''<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>
''')
    package.writestr("_rels/.rels", '''<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>
''')
    package.writestr("3D/3dmodel.model", model)
print(output)
PY
}

analyze_object_process_isolation_gcode() {
  local baseline_gcode="$1"
  local override_gcode="$2"
  python3 - "$baseline_gcode" "$override_gcode" <<'PY'
import math
import pathlib
import re
import sys

word_re = re.compile(r"([A-Z])\s*([-+]?(?:\d+(?:\.\d*)?|\.\d+))", re.I)
object_start_re = re.compile(r"; printing object (.+?) id:")

def extrusion_lengths(path):
    x = y = z = e = 0.0
    absolute_xyz = True
    absolute_e = True
    moves = []
    object_lengths = {}
    current_object = None
    for raw in pathlib.Path(path).read_text(errors="replace").splitlines():
        object_match = object_start_re.match(raw)
        if object_match:
            current_object = object_match.group(1)
            object_lengths.setdefault(current_object, 0.0)
            continue
        if raw.startswith("; stop printing object "):
            current_object = None
            continue
        command = raw.split(";", 1)[0].strip()
        if not command:
            continue
        upper = command.upper()
        if upper == "G90":
            absolute_xyz = True
            continue
        if upper == "G91":
            absolute_xyz = False
            continue
        if upper == "M82":
            absolute_e = True
            continue
        if upper == "M83":
            absolute_e = False
            continue
        words = {key.upper(): float(value) for key, value in word_re.findall(command)}
        if not upper.startswith(("G0", "G1")):
            continue
        px, py, pe = x, y, e
        if "X" in words:
            x = words["X"] if absolute_xyz else x + words["X"]
        if "Y" in words:
            y = words["Y"] if absolute_xyz else y + words["Y"]
        if "Z" in words:
            z = words["Z"] if absolute_xyz else z + words["Z"]
        if "E" in words:
            e = words["E"] if absolute_e else e + words["E"]
        delta_e = e - pe
        if delta_e <= 0:
            continue
        distance = math.hypot(x - px, y - py)
        if distance <= 0:
            continue
        midpoint_x = (x + px) / 2.0
        moves.append((midpoint_x, distance))
        if current_object is not None:
            object_lengths[current_object] = object_lengths.get(current_object, 0.0) + distance
    if len(moves) < 20:
        raise SystemExit(f"not enough extrusion moves in {path}: {len(moves)}")
    return moves, object_lengths

baseline_moves, baseline_objects = extrusion_lengths(sys.argv[1])
override_moves, override_objects = extrusion_lengths(sys.argv[2])
common_objects = sorted(set(baseline_objects).intersection(override_objects))
if len(common_objects) >= 2:
    changed = []
    stable_failures = []
    details = []
    for name in common_objects:
        base = baseline_objects[name]
        override = override_objects[name]
        delta = override - base
        details.append(f"{name}:baseline={base:.3f},override={override:.3f},delta={delta:.3f}")
        if abs(delta) >= max(80.0, base * 0.12):
            changed.append(name)
        elif abs(delta) > max(25.0, base * 0.04):
            stable_failures.append(name)
    if len(changed) == 1 and not stable_failures:
        print("object-process-isolation targetObject=" + changed[0] + " " + " ".join(details))
        raise SystemExit(0)
    raise SystemExit(
        "expected exactly one labeled object to change after object process override; "
        f"changed={changed} stableFailures={stable_failures} details={' | '.join(details)}"
    )

xs = sorted(x for x, _ in baseline_moves)
largest_gap = max(range(len(xs) - 1), key=lambda index: xs[index + 1] - xs[index])
split = (xs[largest_gap] + xs[largest_gap + 1]) / 2.0
if xs[largest_gap + 1] - xs[largest_gap] < 20:
    raise SystemExit(f"could not find two separated object regions; largest X gap={xs[largest_gap + 1] - xs[largest_gap]:.3f}")

def bucket_lengths(moves):
    left = sum(distance for x, distance in moves if x < split)
    right = sum(distance for x, distance in moves if x >= split)
    return left, right

base_left, base_right = bucket_lengths(baseline_moves)
override_left, override_right = bucket_lengths(override_moves)
if min(base_left, base_right, override_left, override_right) <= 1.0:
    raise SystemExit(
        "object regions did not both receive extrusion: "
        f"base=({base_left:.3f},{base_right:.3f}) override=({override_left:.3f},{override_right:.3f}) split={split:.3f}"
    )

left_delta = override_left - base_left
right_delta = override_right - base_right
left_changed = abs(left_delta) >= max(80.0, base_left * 0.12)
right_changed = abs(right_delta) >= max(80.0, base_right * 0.12)
if left_changed == right_changed:
    raise SystemExit(
        "expected exactly one object region to change after object process override; "
        f"base=({base_left:.3f},{base_right:.3f}) override=({override_left:.3f},{override_right:.3f}) "
        f"delta=({left_delta:.3f},{right_delta:.3f}) split={split:.3f}"
    )
stable_delta = right_delta if left_changed else left_delta
stable_base = base_right if left_changed else base_left
if abs(stable_delta) > max(25.0, stable_base * 0.04):
    raise SystemExit(
        "non-target object region changed too much; "
        f"base=({base_left:.3f},{base_right:.3f}) override=({override_left:.3f},{override_right:.3f}) "
        f"delta=({left_delta:.3f},{right_delta:.3f}) split={split:.3f}"
    )
target = "left" if left_changed else "right"
print(
    "object-process-isolation "
    f"target={target} splitX={split:.3f} "
    f"baselineLeft={base_left:.3f} overrideLeft={override_left:.3f} "
    f"baselineRight={base_right:.3f} overrideRight={override_right:.3f}"
)
PY
}

run_object_process_gcode_isolation_regression() {
  local serial="$1"
  local artifact_dir="$ROOT_DIR/artifacts/object-process-isolation"
  mkdir -p "$artifact_dir"
  local fixture="$artifact_dir/two-object-isolation.3mf"
  generate_object_process_isolation_3mf "$fixture" >/dev/null

  local baseline_config override_config
  baseline_config="$(object_process_isolation_config baseline)"
  override_config="$(object_process_isolation_config override)"
  run_automation_slice "$fixture" "$serial" "object-process-isolation-baseline" "0" "$baseline_config" "0" ".gcode" "1"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$artifact_dir/baseline.gcode"
  run_automation_slice "$fixture" "$serial" "object-process-isolation-override" "0" "$override_config" "0" ".gcode" "1"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$artifact_dir/override.gcode"
  analyze_object_process_isolation_gcode "$artifact_dir/baseline.gcode" "$artifact_dir/override.gcode" | tee "$artifact_dir/report.txt"
  log "Object process isolation artifacts: $artifact_dir"
}

run_object_process_override_regression() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "object process override smoke STL"
  install_apk "$serial"
  local config
  config="$(object_process_override_config)"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "object-process-override" "0" "$config"
  local logcat
  logcat="$(adb_device "$serial" logcat -d -v time | grep 'object_process_override' || true)"
  printf '%s\n' "$logcat"
  [[ "$logcat" == *"acceptedKeys="*"wall_loops"* ]] ||
    fail "Object process override did not accept wall_loops."
  [[ "$logcat" == *"acceptedKeys="*"only_one_wall_first_layer"* ]] ||
    fail "Object process override did not accept only_one_wall_first_layer."
  [[ "$logcat" == *"ignoredKeys="*"printer_settings_id"* ]] ||
    fail "Object process override did not ignore printer_settings_id metadata."
  [[ "$logcat" == *"ignoredKeys="*"mobile_slicer_ignored_probe"* ]] ||
    fail "Object process override did not ignore mobile_slicer metadata."
  run_object_process_gcode_isolation_regression "$serial"
  local modifier_path modifier_config
  modifier_path="$(stage_app_private_file "$serial" "$DEFAULT_SLICE_SMOKE_STL" | tail -n 1)"
  modifier_config="$(modifier_process_override_config "$modifier_path")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "modifier-process-override" "0" "$modifier_config"
  assert_modifier_process_override_log "$serial"
  assert_no_crash_after_launch "$serial"
}

gcode_marker_count() {
  local gcode_path="$1"
  local pattern="$2"
  grep -E -c "$pattern" "$gcode_path" || true
}

assert_gcode_marker_count_equals() {
  local gcode_path="$1"
  local pattern="$2"
  local expected="$3"
  local description="$4"
  local actual
  actual="$(gcode_marker_count "$gcode_path" "$pattern")"
  [[ "$actual" == "$expected" ]] ||
    fail "$description expected $expected marker(s), found $actual in $gcode_path"
}

assert_gcode_marker_count_positive() {
  local gcode_path="$1"
  local pattern="$2"
  local description="$3"
  local actual
  actual="$(gcode_marker_count "$gcode_path" "$pattern")"
  [[ "$actual" =~ ^[0-9]+$ && "$actual" -gt 0 ]] ||
    fail "$description expected at least one marker, found $actual in $gcode_path"
}

append_orca_object_label_report_case() {
  local report_path="$1"
  local label="$2"
  local gcode_path="$3"
  local printing_count
  local stop_count
  local exclude_count
  local m486_count
  printing_count="$(gcode_marker_count "$gcode_path" '^; printing object ')"
  stop_count="$(gcode_marker_count "$gcode_path" '^; stop printing object ')"
  exclude_count="$(gcode_marker_count "$gcode_path" '^EXCLUDE_OBJECT')"
  m486_count="$(gcode_marker_count "$gcode_path" '^M486')"
  printf '| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
    "$label" \
    "$printing_count" \
    "$stop_count" \
    "$exclude_count" \
    "$m486_count" \
    "${AUTOMATION_LAST_NATIVE_SLICE_MS:-}" \
    "${AUTOMATION_LAST_ELAPSED_MS:-}" \
    "${AUTOMATION_LAST_BYTES:-}" >> "$report_path"
}

run_orca_object_label_case() {
  local serial="$1"
  local artifact_dir="$2"
  local report_path="$3"
  local label="$4"
  local fixture="$5"
  local expect_labels="$6"
  local expect_exclude="$7"
  shift 7

  local config_json output_path
  config_json="$(automation_config_with_overrides "$@")"
  run_automation_slice "$fixture" "$serial" "$label" "0" "$config_json" "0" ".gcode"
  output_path="$artifact_dir/$label.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"

  if [[ "$expect_labels" == "present" ]]; then
    assert_gcode_marker_count_positive "$output_path" '^; printing object ' "$label printing object comments"
    assert_gcode_marker_count_positive "$output_path" '^; stop printing object ' "$label stop printing object comments"
  else
    assert_gcode_marker_count_equals "$output_path" '^; printing object ' "0" "$label printing object comments"
    assert_gcode_marker_count_equals "$output_path" '^; stop printing object ' "0" "$label stop printing object comments"
  fi

  if [[ "$expect_exclude" == "present" ]]; then
    local exclude_count m486_count
    exclude_count="$(gcode_marker_count "$output_path" '^EXCLUDE_OBJECT')"
    m486_count="$(gcode_marker_count "$output_path" '^M486')"
    [[ "$exclude_count" -gt 0 || "$m486_count" -gt 0 ]] ||
      fail "$label expected Orca exclude-object commands, found EXCLUDE_OBJECT=$exclude_count M486=$m486_count"
  else
    assert_gcode_marker_count_equals "$output_path" '^EXCLUDE_OBJECT' "0" "$label EXCLUDE_OBJECT commands"
    assert_gcode_marker_count_equals "$output_path" '^M486' "0" "$label M486 commands"
  fi

  append_orca_object_label_report_case "$report_path" "$label" "$output_path"
  ORCA_OBJECT_LABEL_LAST_OUTPUT="$output_path"
}

verify_moonraker_label_off_metadata_round_trip() {
  local output_path="$1"
  local artifact_dir="$2"
  local report_path="$3"
  local base_url="${MOBILE_SLICER_MOONRAKER_URL:-}"
  if [[ -z "$base_url" ]]; then
    printf -- '- moonrakerLabelOffRoundTrip: skipped (set MOBILE_SLICER_MOONRAKER_URL to verify label-off metadata indexing on a live host)\n' >> "$report_path"
    return 0
  fi

  command -v curl >/dev/null 2>&1 || fail "curl is required for MOBILE_SLICER_MOONRAKER_URL round-trip verification"
  grep -Eq '^; model printing time: .*total estimated time:' "$output_path" ||
    fail "Label-off Moonraker verification expected Orca-compatible print-time metadata in $output_path"

  base_url="${base_url%/}"
  local stamp remote_name encoded_name upload_json metadata_json delete_json metadata_url started_ms elapsed_ms
  stamp="$(date +%Y%m%d-%H%M%S)"
  remote_name="${MOBILE_SLICER_MOONRAKER_UPLOAD_NAME:-MobileSlicer_label_off_${stamp}.gcode}"
  encoded_name="$(urlencode "$remote_name")"
  upload_json="$artifact_dir/moonraker-label-off-upload.json"
  metadata_json="$artifact_dir/moonraker-label-off-metadata.json"
  delete_json="$artifact_dir/moonraker-label-off-delete.json"
  metadata_url="$base_url/server/files/metadata?filename=$encoded_name"

  local curl_headers=()
  if [[ -n "${MOBILE_SLICER_MOONRAKER_API_KEY:-}" ]]; then
    curl_headers=(-H "X-Api-Key: ${MOBILE_SLICER_MOONRAKER_API_KEY}")
  fi

  curl -fsS "${curl_headers[@]}" \
    -F "root=gcodes" \
    -F "print=false" \
    -F "file=@${output_path};filename=${remote_name}" \
    "$base_url/server/files/upload" > "$upload_json"

  started_ms="$(date +%s%3N)"
  local attempt
  for attempt in $(seq 1 20); do
    if curl -fsS "${curl_headers[@]}" "$metadata_url" > "$metadata_json"; then
      elapsed_ms=$(( $(date +%s%3N) - started_ms ))
      python3 - "$metadata_json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
result = payload.get("result", payload)
if not isinstance(result, dict):
    print("Moonraker metadata response did not contain an object result", file=sys.stderr)
    sys.exit(1)
required = ("filament_total", "layer_height", "object_height", "slicer")
missing = [key for key in required if key not in result]
if missing:
    print(f"Moonraker metadata missing required keys: {missing}; keys={sorted(result)}", file=sys.stderr)
    sys.exit(1)
print("Moonraker label-off metadata keys:", ", ".join(required))
PY
      local max_ms="${MOBILE_SLICER_MOONRAKER_LABEL_OFF_METADATA_MAX_MS:-5000}"
      [[ "$elapsed_ms" -le "$max_ms" ]] ||
        fail "Moonraker label-off metadata indexing took ${elapsed_ms}ms, above ${max_ms}ms"
      {
        printf -- '- moonrakerLabelOffRoundTrip: passed\n'
        printf -- '- moonrakerLabelOffMetadataMs: `%s`\n' "$elapsed_ms"
        printf -- '- moonrakerLabelOffMaxMs: `%s`\n' "$max_ms"
        printf -- '- moonrakerLabelOffGcodePrintTime: present\n'
        printf -- '- moonrakerUrl: `%s`\n' "$base_url"
        printf -- '- moonrakerRemoteFile: `%s`\n' "$remote_name"
        printf -- '- moonrakerUpload: `%s`\n' "$upload_json"
        printf -- '- moonrakerMetadata: `%s`\n' "$metadata_json"
      } >> "$report_path"
      if [[ "${MOBILE_SLICER_MOONRAKER_DELETE_AFTER:-1}" != "0" ]]; then
        curl -fsS -X DELETE "${curl_headers[@]}" "$base_url/server/files/gcodes/$encoded_name" > "$delete_json" || true
        printf -- '- moonrakerDelete: `%s`\n' "$delete_json" >> "$report_path"
      fi
      return 0
    fi
    sleep 1
  done

  fail "Moonraker did not return label-off metadata for $remote_name within 20 seconds"
}

run_orca_object_label_parity_matrix() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "object label parity cube STL"
  require_automation_fixture "$SUPPORT_SLICE_SMOKE_STL" "object label parity printable STL"
  install_apk "$serial"

  local stamp artifact_dir report_path label_off_printable_output
  stamp="$(date +%Y%m%d-%H%M%S)"
  artifact_dir="$ROOT_DIR/artifacts/orca-object-label-parity/$stamp"
  mkdir -p "$artifact_dir"
  report_path="$artifact_dir/report.md"

  {
    printf '# Orca Object Label Parity Gate\n\n'
    printf -- '- captured_at: %s\n' "$stamp"
    printf -- '- serial: `%s`\n' "$serial"
    printf -- '- cube_fixture: `%s`\n' "$DEFAULT_SLICE_SMOKE_STL"
    printf -- '- printable_fixture: `%s`\n\n' "$SUPPORT_SLICE_SMOKE_STL"
    printf 'This gate follows Orca behavior: `gcode_label_objects` controls object label comments, while `exclude_object` is preserved as a separate option and only emits exclude commands when Orca enables them for the active printer/flavor path.\n\n'
    printf '| case | printing comments | stop comments | EXCLUDE_OBJECT | M486 | nativeSliceMs | elapsedMs | bytes |\n'
    printf '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n'
  } > "$report_path"

  run_orca_object_label_case "$serial" "$artifact_dir" "$report_path" \
    "orca-label-off-exclude-off" \
    "$DEFAULT_SLICE_SMOKE_STL" \
    "absent" \
    "absent" \
    "gcode_label_objects=false" \
    "exclude_object=false" \
    "thumbnails=\"\"" \
    "thumbnails_format=\"PNG\""

  run_orca_object_label_case "$serial" "$artifact_dir" "$report_path" \
    "orca-label-on-exclude-off" \
    "$DEFAULT_SLICE_SMOKE_STL" \
    "present" \
    "absent" \
    "gcode_label_objects=true" \
    "exclude_object=false" \
    "thumbnails=\"\"" \
    "thumbnails_format=\"PNG\""

  run_orca_object_label_case "$serial" "$artifact_dir" "$report_path" \
    "orca-label-off-exclude-on-klipper" \
    "$DEFAULT_SLICE_SMOKE_STL" \
    "absent" \
    "absent" \
    "gcode_label_objects=false" \
    "exclude_object=true" \
    "gcode_flavor=\"klipper\"" \
    "thumbnails=\"\"" \
    "thumbnails_format=\"PNG\""

  run_orca_object_label_case "$serial" "$artifact_dir" "$report_path" \
    "orca-label-on-exclude-on-klipper" \
    "$DEFAULT_SLICE_SMOKE_STL" \
    "present" \
    "absent" \
    "gcode_label_objects=true" \
    "exclude_object=true" \
    "gcode_flavor=\"klipper\"" \
    "thumbnails=\"\"" \
    "thumbnails_format=\"PNG\""

  run_orca_object_label_case "$serial" "$artifact_dir" "$report_path" \
    "orca-label-printable-label-off" \
    "$SUPPORT_SLICE_SMOKE_STL" \
    "absent" \
    "absent" \
    "gcode_label_objects=false" \
    "exclude_object=false" \
    "thumbnails=\"\"" \
    "thumbnails_format=\"PNG\"" \
    "brim_width=0" \
    "wall_loops=2" \
    "sparse_infill_density=15" \
    "enable_support=false"
  label_off_printable_output="$ORCA_OBJECT_LABEL_LAST_OUTPUT"

  printf '\n' >> "$report_path"
  verify_moonraker_label_off_metadata_round_trip "$label_off_printable_output" "$artifact_dir" "$report_path"

  rm -rf "$ROOT_DIR/artifacts/orca-object-label-parity/latest"
  ln -sfn "$artifact_dir" "$ROOT_DIR/artifacts/orca-object-label-parity/latest"
  log "Orca object label parity artifacts: $artifact_dir"
  assert_clean_crash_buffer "$serial"
}

automation_config_with_overrides() {
  python3 - "$BENCHY_AUTOMATION_CONFIG" "$@" <<'PY'
import json
import sys

config = json.loads(sys.argv[1])
for override in sys.argv[2:]:
    key, value = override.split("=", 1)
    try:
        config[key] = json.loads(value)
    except json.JSONDecodeError:
        config[key] = value
print(json.dumps(config, separators=(",", ":")))
PY
}

run_profile_ui_smoke() {
  local serial="$1"
  require_device_automation
  install_apk "$serial"
  launch_app "$serial"
  assert_device_unlocked_for_ui "$serial"
  ensure_home_visible "$serial"
  tap_text "$serial" "Open Profiles"
  assert_text_visible "$serial" "Profiles"
  tap_text "$serial" "Process"
  assert_text_visible "$serial" "Process Profiles"
  tap_text "$serial" "Edit / Rename"
  assert_text_visible "$serial" "Quality"
  assert_text_visible "$serial" "Layer height"
  tap_text_after_horizontal_swipe "$serial" "Strength"
  assert_text_visible "$serial" "Top/bottom shells"
  adb_device "$serial" shell input keyevent KEYCODE_BACK
  sleep 1
  assert_no_crash_after_launch "$serial"
}

measure_responsiveness_step() {
  local timings_path="$1"
  local step_name="$2"
  shift 2
  local started_ms
  local ended_ms
  local elapsed_ms
  started_ms="$(date +%s%3N)"
  "$@"
  ended_ms="$(date +%s%3N)"
  elapsed_ms=$((ended_ms - started_ms))
  printf '{"name":"%s","elapsed_ms":%s}\n' "$step_name" "$elapsed_ms" >> "$timings_path"
  log "Responsiveness step $step_name: ${elapsed_ms}ms"
}

write_preview_runtime_summary() {
  local log_path="$1"
  if [[ ! -s "$log_path" ]]; then
    printf 'No preview runtime events were captured.\n'
    return
  fi
  python3 - "$log_path" <<'PY'
import re
import sys
from pathlib import Path

events = []
for line in Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace").splitlines():
    if "workspace_preview_runtime" not in line:
        continue
    fields = {
        key: int(value)
        for key, value in re.findall(r"([A-Za-z]+)=([0-9]+)", line)
    }
    if fields:
        events.append(fields)

if not events:
    print("No preview runtime events were captured.")
    raise SystemExit

first_frames = [event["firstFrameMs"] for event in events if "firstFrameMs" in event]
native_loads = [event["nativeLoadMs"] for event in events if "nativeLoadMs" in event]
last_frames = [event["lastFrameMs"] for event in events if "lastFrameMs" in event]
slow_frames = max((event.get("slowFrames", 0) for event in events), default=0)
rendered_frames = max((event.get("frames", 0) for event in events), default=0)

def stat_line(name, values):
    if values:
        print(f"- {name}: min={min(values)} ms max={max(values)} ms last={values[-1]} ms")

print(f"- events: {len(events)}")
stat_line("native_load", native_loads)
stat_line("first_frame", first_frames)
stat_line("frame_time", last_frames)
print(f"- slow_frames: {slow_frames}")
print(f"- rendered_frames: {rendered_frames}")
PY
}

run_responsiveness_profile() {
  local serial="$1"
  require_device_automation
  local artifact_root="$ROOT_DIR/artifacts/responsiveness"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$artifact_root/$stamp"
  local timings_path="$artifact_dir/ui-timings.jsonl"
  mkdir -p "$artifact_dir"

  install_apk "$serial"
  measure_responsiveness_step "$timings_path" "cold_launch" launch_app "$serial"
  assert_device_unlocked_for_ui "$serial"
  measure_responsiveness_step "$timings_path" "home_visible" ensure_home_visible "$serial"
  measure_responsiveness_step "$timings_path" "tap_open_profiles" tap_text "$serial" "Open Profiles"
  measure_responsiveness_step "$timings_path" "profiles_visible" assert_text_visible "$serial" "Profiles"
  measure_responsiveness_step "$timings_path" "tap_process_tab" tap_text "$serial" "Process"
  measure_responsiveness_step "$timings_path" "process_profiles_visible" assert_text_visible "$serial" "Process Profiles"
  measure_responsiveness_step "$timings_path" "tap_edit_process_profile" tap_text "$serial" "Edit / Rename"
  measure_responsiveness_step "$timings_path" "process_quality_visible" assert_text_visible "$serial" "Quality"
  measure_responsiveness_step "$timings_path" "layer_height_visible" assert_text_visible "$serial" "Layer height"
  measure_responsiveness_step "$timings_path" "tap_strength_tab" tap_text_after_horizontal_swipe "$serial" "Strength"
  measure_responsiveness_step "$timings_path" "strength_visible" assert_text_visible "$serial" "Top/bottom shells"

  adb_device "$serial" logcat -d -v time > "$artifact_dir/logcat.txt" 2>&1 || true
  grep -E 'MobileSlicerPerf|workspace_responsiveness' "$artifact_dir/logcat.txt" > "$artifact_dir/responsiveness-logcat.txt" || true
  adb_device "$serial" logcat -b crash -d -v time > "$artifact_dir/crash-logcat.txt" 2>&1 || true
  if [[ -s "$artifact_dir/crash-logcat.txt" ]]; then
    cat "$artifact_dir/crash-logcat.txt" >&2
    fail "Crash log buffer is not empty after responsiveness profile."
  fi
  {
    printf '# MobileSlicer Responsiveness Profile\n\n'
    printf -- '- serial: %s\n' "$serial"
    printf -- '- captured_at: %s\n\n' "$stamp"
    printf '## UI Steps\n\n'
    python3 - "$timings_path" <<'PY'
import json
import sys
from pathlib import Path

for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    record = json.loads(line)
    print(f"- {record['name']}: {record['elapsed_ms']} ms")
PY
    printf '\n## App Timing Events\n\n'
    if [[ -s "$artifact_dir/responsiveness-logcat.txt" ]]; then
      sed 's/^/- /' "$artifact_dir/responsiveness-logcat.txt"
    else
      printf 'No MobileSlicerPerf events were captured during this UI-only run.\n'
    fi
    printf '\n## Preview Runtime Summary\n\n'
    write_preview_runtime_summary "$artifact_dir/responsiveness-logcat.txt"
  } > "$artifact_dir/report.md"
  log "Responsiveness profile artifacts: $artifact_dir"
}

write_responsiveness_slice_report() {
  local serial="$1"
  local stamp="$2"
  local fixture="$3"
  local artifact_dir="$4"
  adb_device "$serial" logcat -d -v time > "$artifact_dir/logcat.txt" 2>&1 || true
  grep -E 'MobileSlicerPerf|workspace_responsiveness|automation:' "$artifact_dir/logcat.txt" > "$artifact_dir/timing-logcat.txt" || true
  adb_device "$serial" logcat -b crash -d -v time > "$artifact_dir/crash-logcat.txt" 2>&1 || true
  if [[ -s "$artifact_dir/crash-logcat.txt" ]]; then
    cat "$artifact_dir/crash-logcat.txt" >&2
    fail "Crash log buffer is not empty after responsiveness slice profile."
  fi
  if [[ -n "$AUTOMATION_LAST_STATUS" ]]; then
    printf '%s\n' "$AUTOMATION_LAST_STATUS" > "$artifact_dir/status.txt"
  fi
  if [[ -n "$AUTOMATION_LAST_OUTPUT_PATH" ]]; then
    adb_device "$serial" exec-out run-as "$PACKAGE_NAME" sh -c "head -n 200 '$AUTOMATION_LAST_OUTPUT_PATH'" > "$artifact_dir/gcode-head.txt" 2>&1 || true
  fi
  {
    printf '# MobileSlicer Responsiveness Slice Profile\n\n'
    printf -- '- serial: %s\n' "$serial"
    printf -- '- captured_at: %s\n' "$stamp"
    printf -- '- fixture: %s\n' "$fixture"
    printf -- '- gcode_bytes: %s\n\n' "${AUTOMATION_LAST_BYTES:-unknown}"
    printf '## Phase Timings\n\n'
    printf -- '- staging: %s ms\n' "${AUTOMATION_LAST_STAGING_MS:-unknown}"
    printf -- '- native_load: %s ms\n' "${AUTOMATION_LAST_NATIVE_LOAD_MS:-unknown}"
    printf -- '- placement: %s ms\n' "${AUTOMATION_LAST_PLACEMENT_MS:-unknown}"
    printf -- '- config: %s ms\n' "${AUTOMATION_LAST_CONFIG_MS:-unknown}"
    printf -- '- native_slice: %s ms\n' "${AUTOMATION_LAST_NATIVE_SLICE_MS:-unknown}"
    printf -- '- write_gcode: %s ms\n' "${AUTOMATION_LAST_WRITE_GCODE_MS:-unknown}"
    printf -- '- total: %s ms\n\n' "${AUTOMATION_LAST_ELAPSED_MS:-unknown}"
    printf '## Preview Readiness\n\n'
    printf -- '- preview_plan: %s ms\n' "${AUTOMATION_LAST_PREVIEW_PLAN_MS:-unknown}"
    printf -- '- preview_load: %s ms\n' "${AUTOMATION_LAST_PREVIEW_LOAD_MS:-unknown}"
    printf -- '- preview_ranges: %s\n' "${AUTOMATION_LAST_PREVIEW_RANGES:-unknown}"
    printf -- '- preview_requested_layers: %s\n' "${AUTOMATION_LAST_PREVIEW_REQUESTED_LAYERS:-unknown}"
    printf -- '- preview_loaded_layers: %s\n' "${AUTOMATION_LAST_PREVIEW_LOADED_LAYERS:-unknown}"
    printf -- '- preview_planned_covered_layers: %s\n' "${AUTOMATION_LAST_PREVIEW_PLANNED_COVERED_LAYERS:-unknown}"
    printf -- '- preview_vertex_budget: %s\n' "${AUTOMATION_LAST_PREVIEW_VERTEX_BUDGET:-unknown}"
    printf -- '- preview_load_success: %s\n\n' "${AUTOMATION_LAST_PREVIEW_LOAD_SUCCESS:-unknown}"
    printf '## Native Retained Data\n\n'
    printf -- '- native_gcode_bytes: %s\n' "${AUTOMATION_LAST_NATIVE_GCODE_BYTES:-unknown}"
    printf -- '- processor_move_bytes: %s\n' "${AUTOMATION_LAST_PROCESSOR_MOVE_BYTES:-unknown}"
    printf -- '- processor_line_end_bytes: %s\n' "${AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES:-unknown}"
    printf -- '- processor_move_bytes_retained: %s\n' "${AUTOMATION_LAST_PROCESSOR_MOVE_BYTES_RETAINED:-unknown}"
    printf -- '- processor_line_end_bytes_retained: %s\n' "${AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES_RETAINED:-unknown}"
    printf -- '- processor_release: %s ms\n' "${AUTOMATION_LAST_PROCESSOR_RELEASE_MS:-unknown}"
    printf -- '- processor_moves_released_during_export: %s\n' "${AUTOMATION_LAST_PROCESSOR_MOVES_RELEASED_DURING_EXPORT:-unknown}"
    printf -- '- preview_layer_count_bytes: %s\n' "${AUTOMATION_LAST_PREVIEW_LAYER_COUNT_BYTES:-unknown}"
    printf -- '- exact_preview_cache_eligible: %s\n\n' "${AUTOMATION_LAST_EXACT_PREVIEW_CACHE_ELIGIBLE:-unknown}"
    printf '## Native Export RSS\n\n'
    printf -- '- export_start: %s KB\n' "${AUTOMATION_LAST_NATIVE_EXPORT_START_RSS_KB:-unknown}"
    printf -- '- after_setup: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_SETUP_RSS_KB:-unknown}"
    printf -- '- after_layers: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_LAYERS_RSS_KB:-unknown}"
    printf -- '- after_footer: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_FOOTER_RSS_KB:-unknown}"
    printf -- '- after_generation: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_GENERATION_RSS_KB:-unknown}"
    printf -- '- after_finalize: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_FINALIZE_RSS_KB:-unknown}"
    printf -- '- after_release: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_RELEASE_RSS_KB:-unknown}"
    printf -- '- after_stats: %s KB\n' "${AUTOMATION_LAST_NATIVE_AFTER_STATS_RSS_KB:-unknown}"
    printf -- '- before_return: %s KB\n\n' "${AUTOMATION_LAST_NATIVE_BEFORE_RETURN_RSS_KB:-unknown}"
    printf '## Peak Memory\n\n'
    printf -- '- pss_kb: %s\n' "${AUTOMATION_LAST_PEAK_PSS_KB:-unknown}"
    printf -- '- java_heap_kb: %s\n' "${AUTOMATION_LAST_PEAK_JAVA_HEAP_KB:-unknown}"
    printf -- '- native_heap_kb: %s\n' "${AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB:-unknown}"
    printf -- '- graphics_kb: %s\n\n' "${AUTOMATION_LAST_PEAK_GRAPHICS_KB:-unknown}"
    printf '## App Timing Events\n\n'
    if [[ -s "$artifact_dir/timing-logcat.txt" ]]; then
      sed 's/^/- /' "$artifact_dir/timing-logcat.txt"
    else
      printf 'No timing events were captured.\n'
    fi
    printf '\n## Preview Runtime Summary\n\n'
    write_preview_runtime_summary "$artifact_dir/timing-logcat.txt"
  } > "$artifact_dir/report.md"
}

run_responsiveness_slice_case() {
  local serial="$1"
  local fixture="$2"
  local label="$3"
  local should_install="$4"
  local artifact_dir="$5"
  local stamp="$6"
  mkdir -p "$artifact_dir"

  run_automation_slice "$fixture" "$serial" "$label" "$should_install" "$BENCHY_AUTOMATION_CONFIG" "1"
  write_responsiveness_slice_report "$serial" "$stamp" "$fixture" "$artifact_dir"
  log "Responsiveness slice artifacts: $artifact_dir"
}

run_responsiveness_slice_profile() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "default slice smoke STL"
  local artifact_root="$ROOT_DIR/artifacts/responsiveness-slice"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$artifact_root/$stamp"

  run_responsiveness_slice_case "$serial" "$DEFAULT_SLICE_SMOKE_STL" "responsiveness-slice" "1" "$artifact_dir" "$stamp"
}

run_responsiveness_heavy_profile() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$MEDIUM_SLICE_PERF_STL" "medium performance STL"
  require_automation_fixture "$COMPLEX_SLICE_PERF_STL" "complex performance STL"
  require_automation_fixture "$STRESS_SLICE_PERF_STL" "stress performance STL"
  local artifact_root="$ROOT_DIR/artifacts/responsiveness-heavy"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$artifact_root/$stamp"
  mkdir -p "$artifact_dir"

  install_apk "$serial"
  run_responsiveness_slice_case "$serial" "$MEDIUM_SLICE_PERF_STL" "responsiveness-medium-speed-structure" "0" "$artifact_dir/medium-speed-structure" "$stamp"
  run_responsiveness_slice_case "$serial" "$COMPLEX_SLICE_PERF_STL" "responsiveness-complex-vfa" "0" "$artifact_dir/complex-vfa" "$stamp"
  run_responsiveness_slice_case "$serial" "$STRESS_SLICE_PERF_STL" "responsiveness-stress-temperature-tower" "0" "$artifact_dir/stress-temperature-tower" "$stamp"

  {
    printf '# MobileSlicer Heavy Responsiveness Profile\n\n'
    printf -- '- serial: %s\n' "$serial"
    printf -- '- captured_at: %s\n' "$stamp"
    printf '\n## Case Reports\n\n'
    printf -- '- medium-speed-structure: medium-speed-structure/report.md\n'
    printf -- '- complex-vfa: complex-vfa/report.md\n'
    printf -- '- stress-temperature-tower: stress-temperature-tower/report.md\n'
  } > "$artifact_dir/report.md"
  log "Heavy responsiveness artifacts: $artifact_dir"
}

run_preview_interaction_profile() {
  local serial="$1"
  local churn_requests="${2:-0}"
  local lifecycle_cycles="${3:-0}"
  require_device_automation
  require_automation_fixture "$MEDIUM_SLICE_PERF_STL" "medium performance STL"
  local profile_name="preview-interaction"
  if [[ "$churn_requests" != "0" ]]; then
    profile_name="preview-churn"
  elif [[ "$lifecycle_cycles" != "0" ]]; then
    profile_name="preview-lifecycle"
  fi
  local artifact_root="$ROOT_DIR/artifacts/$profile_name"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$artifact_root/$stamp"
  mkdir -p "$artifact_dir"

  install_apk "$serial"
  local app_model_path
  app_model_path="$(stage_app_private_file "$serial" "$MEDIUM_SLICE_PERF_STL" | tail -n 1)"
  local status_path="/data/data/$PACKAGE_NAME/files/automation/$profile_name-$stamp.status.txt"

  log "Running $profile_name profile on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  adb_device "$serial" shell "CONFIG='$BENCHY_AUTOMATION_CONFIG'; am start -W \
    -a '$PREVIEW_INTERACTION_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es preview_profile_model_path '$app_model_path' \
    --es preview_profile_status_path '$status_path' \
    --ei preview_profile_churn_requests '$churn_requests' \
    --ei preview_profile_lifecycle_cycles '$lifecycle_cycles' \
    --es automation_config_json \"\$CONFIG\""

  local status
  if [[ "$lifecycle_cycles" != "0" ]]; then
    PERF_CURRENT_MEMINFO_DIR="$artifact_dir"
    PERF_CURRENT_CASE="preview-lifecycle"
    local lifecycle_status_tmp="$artifact_dir/status.wait.txt"
    wait_for_status_with_memory "$serial" "$status_path" 120 1 > "$lifecycle_status_tmp"
    status="$(cat "$lifecycle_status_tmp")"
    rm -f "$lifecycle_status_tmp"
    PERF_CURRENT_MEMINFO_DIR=""
    PERF_CURRENT_CASE=""
  else
    status="$(wait_for_status "$serial" "$status_path" 120 1)"
  fi
  printf '%s\n' "$status" > "$artifact_dir/status.txt"
  [[ "$status" == success:* ]] || fail "$profile_name profile did not report success."
  local metric_count first_ready rendered_frames churn_ready lifecycle_ready
  metric_count="$(status_metric "$status" "metrics")"
  first_ready="$(status_metric "$status" "firstReady")"
  rendered_frames="$(status_metric "$status" "renderedFrames")"
  churn_ready="$(status_metric "$status" "churnReady")"
  lifecycle_ready="$(status_metric "$status" "lifecycleReady")"
  [[ "${metric_count:-0}" =~ ^[0-9]+$ && "$metric_count" -gt 0 ]] ||
    fail "$profile_name profile did not capture preview runtime metrics."
  [[ "$first_ready" == "1" ]] ||
    fail "$profile_name profile did not render the first preview frame."
  if [[ "$churn_requests" != "0" ]]; then
    [[ "$churn_ready" == "1" ]] ||
      fail "$profile_name profile did not render the newest churn preview request."
  fi
  if [[ "$lifecycle_cycles" != "0" ]]; then
    [[ "$lifecycle_ready" == "$lifecycle_cycles" ]] ||
      fail "$profile_name profile rendered $lifecycle_ready of $lifecycle_cycles lifecycle cycles."
  fi
  [[ "${rendered_frames:-0}" =~ ^[0-9]+$ && "$rendered_frames" -gt 0 ]] ||
    fail "$profile_name profile did not render preview frames."

  adb_device "$serial" logcat -d -v time > "$artifact_dir/logcat.txt" 2>&1 || true
  grep -E 'MobileSlicerPerf|preview_profile:' "$artifact_dir/logcat.txt" > "$artifact_dir/timing-logcat.txt" || true
  adb_device "$serial" logcat -b crash -d -v time > "$artifact_dir/crash-logcat.txt" 2>&1 || true
  if [[ -s "$artifact_dir/crash-logcat.txt" ]]; then
    cat "$artifact_dir/crash-logcat.txt" >&2
    fail "Crash log buffer is not empty after $profile_name profile."
  fi

  local responsiveness_json="$artifact_dir/preview-responsiveness.json"
  local responsiveness_md="$artifact_dir/preview-responsiveness.md"
  local responsiveness_status=0
  local peak_pss_budget_kb peak_native_budget_kb final_native_budget_kb final_graphics_budget_kb
  local final_pss_kb="" final_native_heap_kb="" final_graphics_kb=""
  local memory_failures=()
  if [[ "$lifecycle_cycles" != "0" ]]; then
    peak_pss_budget_kb="$(env_int_or_default MOBILE_SLICER_PREVIEW_LIFECYCLE_MAX_PEAK_PSS_KB 665600)"
    peak_native_budget_kb="$(env_int_or_default MOBILE_SLICER_PREVIEW_LIFECYCLE_MAX_PEAK_NATIVE_HEAP_KB 286720)"
    final_native_budget_kb="$(env_int_or_default MOBILE_SLICER_PREVIEW_LIFECYCLE_MAX_FINAL_NATIVE_HEAP_KB 49152)"
    final_graphics_budget_kb="$(env_int_or_default MOBILE_SLICER_PREVIEW_LIFECYCLE_MAX_FINAL_GRAPHICS_KB 16384)"
    local final_meminfo_path="$artifact_dir/preview-lifecycle-final-meminfo.txt"
    if [[ -s "$final_meminfo_path" ]]; then
      local final_meminfo
      final_meminfo="$(cat "$final_meminfo_path")"
      final_pss_kb="$(meminfo_total_pss_kb "$final_meminfo")"
      final_native_heap_kb="$(meminfo_app_summary_kb "$final_meminfo" "Native Heap")"
      final_graphics_kb="$(meminfo_app_summary_kb "$final_meminfo" "Graphics")"
    fi
    add_memory_budget_failure memory_failures "peak PSS" "$PERF_LAST_PEAK_PSS_KB" "$peak_pss_budget_kb"
    add_memory_budget_failure memory_failures "peak native heap" "$PERF_LAST_PEAK_NATIVE_HEAP_KB" "$peak_native_budget_kb"
    add_memory_budget_failure memory_failures "final native heap" "$final_native_heap_kb" "$final_native_budget_kb"
    add_memory_budget_failure memory_failures "final graphics" "$final_graphics_kb" "$final_graphics_budget_kb"
  fi
  python3 "$ROOT_DIR/scripts/analyze_preview_responsiveness.py" \
    --status "$artifact_dir/status.txt" \
    --timing-log "$artifact_dir/timing-logcat.txt" \
    --profile "$profile_name" \
    --output-json "$responsiveness_json" \
    --output-md "$responsiveness_md" || responsiveness_status=$?

  {
    if [[ "$lifecycle_cycles" != "0" ]]; then
      printf '# MobileSlicer Preview Lifecycle Profile\n\n'
    elif [[ "$churn_requests" == "0" ]]; then
      printf '# MobileSlicer Preview Interaction Profile\n\n'
    else
      printf '# MobileSlicer Preview Churn Profile\n\n'
    fi
    printf -- '- serial: %s\n' "$serial"
    printf -- '- captured_at: %s\n' "$stamp"
    printf -- '- fixture: %s\n\n' "$MEDIUM_SLICE_PERF_STL"
    printf '## Status\n\n'
    printf -- '- preview_ranges: %s\n' "$(status_metric "$status" "previewRanges")"
    printf -- '- first_range_layers: %s\n' "$(status_metric "$status" "firstRangeLayers")"
    printf -- '- second_range_layers: %s\n' "$(status_metric "$status" "secondRangeLayers")"
    printf -- '- first_ready: %s\n' "$first_ready"
    printf -- '- second_ready: %s\n' "$(status_metric "$status" "secondReady")"
    printf -- '- churn_requests: %s\n' "$(status_metric "$status" "churnRequests")"
    printf -- '- churn_ready: %s\n' "$churn_ready"
    printf -- '- lifecycle_cycles: %s\n' "$(status_metric "$status" "lifecycleCycles")"
    printf -- '- lifecycle_ready: %s\n' "$lifecycle_ready"
    printf -- '- metrics: %s\n' "$metric_count"
    printf -- '- max_native_load: %s ms\n' "$(status_metric "$status" "maxNativeLoadMs")"
    printf -- '- max_first_frame: %s ms\n' "$(status_metric "$status" "maxFirstFrameMs")"
    printf -- '- max_frame: %s ms\n' "$(status_metric "$status" "maxFrameMs")"
    printf -- '- slow_frames: %s\n' "$(status_metric "$status" "slowFrames")"
    printf -- '- rendered_frames: %s\n\n' "$rendered_frames"
    if [[ "$lifecycle_cycles" != "0" ]]; then
      printf '## Peak Memory\n\n'
      printf -- '- peak_pss_kb: %s\n' "$PERF_LAST_PEAK_PSS_KB"
      printf -- '- peak_java_heap_kb: %s\n' "$PERF_LAST_PEAK_JAVA_HEAP_KB"
      printf -- '- peak_native_heap_kb: %s\n' "$PERF_LAST_PEAK_NATIVE_HEAP_KB"
      printf -- '- peak_graphics_kb: %s\n' "$PERF_LAST_PEAK_GRAPHICS_KB"
      printf -- '- peak_private_other_kb: %s\n' "$PERF_LAST_PEAK_PRIVATE_OTHER_KB"
      printf -- '- peak_system_kb: %s\n\n' "$PERF_LAST_PEAK_SYSTEM_KB"
      printf '## Final Memory\n\n'
      printf -- '- final_pss_kb: %s\n' "${final_pss_kb:-unknown}"
      printf -- '- final_native_heap_kb: %s\n' "${final_native_heap_kb:-unknown}"
      printf -- '- final_graphics_kb: %s\n\n' "${final_graphics_kb:-unknown}"
      printf '## Memory Budgets\n\n'
      printf -- '- max_peak_pss_kb: %s\n' "$peak_pss_budget_kb"
      printf -- '- max_peak_native_heap_kb: %s\n' "$peak_native_budget_kb"
      printf -- '- max_final_native_heap_kb: %s\n' "$final_native_budget_kb"
      printf -- '- max_final_graphics_kb: %s\n\n' "$final_graphics_budget_kb"
      if [[ "${#memory_failures[@]}" -gt 0 ]]; then
        printf '## Memory Gate Failures\n\n'
        printf -- '- %s\n' "${memory_failures[@]}"
        printf '\n'
      fi
    fi
    printf '## Responsiveness Gate\n\n'
    if [[ -s "$responsiveness_md" ]]; then
      sed 's/^/> /' "$responsiveness_md"
    else
      printf 'Preview responsiveness analysis did not produce a report.\n'
    fi
    printf '\n'
    printf '## Preview Runtime Summary\n\n'
    write_preview_runtime_summary "$artifact_dir/timing-logcat.txt"
    printf '\n## Timing Events\n\n'
    if [[ -s "$artifact_dir/timing-logcat.txt" ]]; then
      sed 's/^/- /' "$artifact_dir/timing-logcat.txt"
    else
      printf 'No preview timing events were captured.\n'
    fi
  } > "$artifact_dir/report.md"
  log "$profile_name artifacts: $artifact_dir"
  [[ "$responsiveness_status" -eq 0 ]] ||
    fail "$profile_name responsiveness analyzer failed; see artifacts: $artifact_dir"
  if [[ "${#memory_failures[@]}" -gt 0 ]]; then
    printf '%s\n' "${memory_failures[@]}" >&2
    fail "$profile_name memory gate failed; see artifacts: $artifact_dir"
  fi
}

stage_app_private_file() {
  local serial="$1"
  local local_path="$2"
  local basename
  basename="$(basename "$local_path")"
  local tmp_path="/data/local/tmp/mobileslicer-$basename"
  local app_path="/data/data/$PACKAGE_NAME/files/automation/$basename"

  [[ -f "$local_path" ]] || fail "STL not found: $local_path"
  log "Staging $local_path to app-private storage on $serial"
  adb_device "$serial" push "$local_path" "$tmp_path" >/dev/null
  adb_device "$serial" shell run-as "$PACKAGE_NAME" mkdir -p files/automation
  adb_device "$serial" shell run-as "$PACKAGE_NAME" cp "$tmp_path" "files/automation/$basename"
  adb_device "$serial" shell rm -f "$tmp_path"
  printf '%s\n' "$app_path"
}

wait_for_status() {
  local serial="$1"
  local status_path="$2"
  local attempts="${3:-120}"
  local delay_seconds="${4:-1}"

  for _ in $(seq 1 "$attempts"); do
    if adb_device "$serial" shell run-as "$PACKAGE_NAME" test -f "$status_path"; then
      adb_device "$serial" shell run-as "$PACKAGE_NAME" cat "$status_path"
      return 0
    fi
    sleep "$delay_seconds"
  done
  fail "Timed out waiting for automation status: $status_path"
}

device_pss_kb() {
  local serial="$1"
  adb_device "$serial" shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null |
    awk '/^[[:space:]]*TOTAL[[:space:]]/ {print $2; exit}'
}

device_meminfo() {
  local serial="$1"
  adb_device "$serial" shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null
}

app_cache_usage_kb() {
  local serial="$1"
  local selector="$2"
  local command
  case "$selector" in
  total)
    command='du -k -s cache 2>/dev/null | head -n 1 | cut -f1'
    ;;
  orca-temp)
    command='if [ -e cache/orca-temp ]; then du -k -s cache/orca-temp 2>/dev/null | head -n 1 | cut -f1; else printf 0; fi'
    ;;
  generated-gcode)
    command='total=0; for file in cache/latest-slice-* cache/latest-send-* cache/orca_wrapper_*.gcode; do [ -f "$file" ] || continue; kb=$(du -k "$file" 2>/dev/null | head -n 1 | cut -f1); total=$((total + ${kb:-0})); done; printf "%s" "$total"'
    ;;
  staged-model)
    command='total=0; for file in cache/selected-model-*; do [ -f "$file" ] || continue; kb=$(du -k "$file" 2>/dev/null | head -n 1 | cut -f1); total=$((total + ${kb:-0})); done; printf "%s" "$total"'
    ;;
  *)
    fail "unknown app cache selector: $selector"
    ;;
  esac
  adb_device "$serial" shell "run-as $PACKAGE_NAME sh -c '$command'" 2>/dev/null | tr -d '\r' | tail -n 1
}

capture_device_command() {
  local serial="$1"
  local output_path="$2"
  shift 2
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n\n'
    adb_device "$serial" shell "$@" 2>&1 || true
  } > "$output_path"
}

capture_device_shell_command() {
  local serial="$1"
  local output_path="$2"
  local command="$3"
  {
    printf '$ adb shell %s\n\n' "$command"
    adb_device "$serial" shell "$command" 2>&1 || true
  } > "$output_path"
}

capture_perf_device_state() {
  local serial="$1"
  local output_dir="$2"
  local label="$3"
  mkdir -p "$output_dir"
  capture_device_command "$serial" "$output_dir/$label-battery.txt" dumpsys battery
  capture_device_command "$serial" "$output_dir/$label-thermalservice.txt" dumpsys thermalservice
  capture_device_command "$serial" "$output_dir/$label-cpuinfo.txt" dumpsys cpuinfo
  capture_device_command "$serial" "$output_dir/$label-top.txt" top -b -n 1 -o PID,USER,PR,NI,VIRT,RES,SHR,S,%CPU,%MEM,TIME+,ARGS
  capture_device_command "$serial" "$output_dir/$label-getprop.txt" getprop
  capture_device_shell_command "$serial" "$output_dir/$label-thermal-zones.txt" 'for zone in /sys/class/thermal/thermal_zone*; do [ -r "$zone/type" ] || continue; printf "%s " "$zone"; cat "$zone/type"; [ -r "$zone/temp" ] && printf "temp=" && cat "$zone/temp"; done'
  capture_device_shell_command "$serial" "$output_dir/$label-cpufreq.txt" 'for cpu in /sys/devices/system/cpu/cpu[0-9]*; do printf "%s " "$cpu"; for file in scaling_governor scaling_cur_freq scaling_min_freq scaling_max_freq cpuinfo_cur_freq cpuinfo_min_freq cpuinfo_max_freq online; do [ -r "$cpu/cpufreq/$file" ] && printf "%s=" "$file" && cat "$cpu/cpufreq/$file"; [ -r "$cpu/$file" ] && printf "%s=" "$file" && cat "$cpu/$file"; done; done'
}

meminfo_app_summary_kb() {
  local meminfo="$1"
  local label="$2"
  MEMINFO_PAYLOAD="$meminfo" python3 - "$label" <<'PY'
import os
import re
import sys

label = re.escape(sys.argv[1])
match = re.search(rf"^\s*{label}:\s+([0-9]+)\b", os.environ["MEMINFO_PAYLOAD"], re.MULTILINE)
print(match.group(1) if match else "")
PY
}

meminfo_total_pss_kb() {
  local meminfo="$1"
  MEMINFO_PAYLOAD="$meminfo" python3 - <<'PY'
import os
import re

match = re.search(r"^\s*TOTAL PSS:\s+([0-9]+)\b", os.environ["MEMINFO_PAYLOAD"], re.MULTILINE)
if not match:
    match = re.search(r"^\s*TOTAL\s+([0-9]+)\b", os.environ["MEMINFO_PAYLOAD"], re.MULTILINE)
print(match.group(1) if match else "")
PY
}

write_peak_meminfo_snapshot() {
  local meminfo="$1"
  local pss_kb="$2"
  if [[ -z "$PERF_CURRENT_MEMINFO_DIR" || -z "$PERF_CURRENT_CASE" ]]; then
    return 0
  fi
  mkdir -p "$PERF_CURRENT_MEMINFO_DIR"
  printf '%s\n' "$meminfo" > "$PERF_CURRENT_MEMINFO_DIR/$PERF_CURRENT_CASE-peak-meminfo.txt"
  printf 'peakPssKb=%s\n' "$pss_kb" > "$PERF_CURRENT_MEMINFO_DIR/$PERF_CURRENT_CASE-peak-summary.txt"
}

update_peak_meminfo_metrics() {
  local meminfo="$1"
  local pss_kb java_heap_kb native_heap_kb graphics_kb private_other_kb system_kb
  pss_kb="$(meminfo_total_pss_kb "$meminfo")"
  java_heap_kb="$(meminfo_app_summary_kb "$meminfo" "Java Heap")"
  native_heap_kb="$(meminfo_app_summary_kb "$meminfo" "Native Heap")"
  graphics_kb="$(meminfo_app_summary_kb "$meminfo" "Graphics")"
  private_other_kb="$(meminfo_app_summary_kb "$meminfo" "Private Other")"
  system_kb="$(meminfo_app_summary_kb "$meminfo" "System")"

  if [[ "$pss_kb" =~ ^[0-9]+$ && "$pss_kb" -gt "$PERF_LAST_PEAK_PSS_KB" ]]; then
    PERF_LAST_PEAK_PSS_KB="$pss_kb"
    write_peak_meminfo_snapshot "$meminfo" "$pss_kb"
  fi
  if [[ "$java_heap_kb" =~ ^[0-9]+$ && "$java_heap_kb" -gt "$PERF_LAST_PEAK_JAVA_HEAP_KB" ]]; then
    PERF_LAST_PEAK_JAVA_HEAP_KB="$java_heap_kb"
  fi
  if [[ "$native_heap_kb" =~ ^[0-9]+$ && "$native_heap_kb" -gt "$PERF_LAST_PEAK_NATIVE_HEAP_KB" ]]; then
    PERF_LAST_PEAK_NATIVE_HEAP_KB="$native_heap_kb"
  fi
  if [[ "$graphics_kb" =~ ^[0-9]+$ && "$graphics_kb" -gt "$PERF_LAST_PEAK_GRAPHICS_KB" ]]; then
    PERF_LAST_PEAK_GRAPHICS_KB="$graphics_kb"
  fi
  if [[ "$private_other_kb" =~ ^[0-9]+$ && "$private_other_kb" -gt "$PERF_LAST_PEAK_PRIVATE_OTHER_KB" ]]; then
    PERF_LAST_PEAK_PRIVATE_OTHER_KB="$private_other_kb"
  fi
  if [[ "$system_kb" =~ ^[0-9]+$ && "$system_kb" -gt "$PERF_LAST_PEAK_SYSTEM_KB" ]]; then
    PERF_LAST_PEAK_SYSTEM_KB="$system_kb"
  fi
}

wait_for_status_with_memory() {
  local serial="$1"
  local status_path="$2"
  local attempts="${3:-120}"
  local delay_seconds="${4:-1}"
  PERF_LAST_PEAK_PSS_KB=0
  PERF_LAST_PEAK_JAVA_HEAP_KB=0
  PERF_LAST_PEAK_NATIVE_HEAP_KB=0
  PERF_LAST_PEAK_GRAPHICS_KB=0
  PERF_LAST_PEAK_PRIVATE_OTHER_KB=0
  PERF_LAST_PEAK_SYSTEM_KB=0

  for _ in $(seq 1 "$attempts"); do
    local meminfo
    meminfo="$(device_meminfo "$serial" || true)"
    update_peak_meminfo_metrics "$meminfo"
    if adb_device "$serial" shell run-as "$PACKAGE_NAME" test -f "$status_path"; then
      local final_meminfo
      final_meminfo="$(device_meminfo "$serial" || true)"
      update_peak_meminfo_metrics "$final_meminfo"
      if [[ -n "$PERF_CURRENT_MEMINFO_DIR" && -n "$PERF_CURRENT_CASE" ]]; then
        printf '%s\n' "$final_meminfo" > "$PERF_CURRENT_MEMINFO_DIR/$PERF_CURRENT_CASE-final-meminfo.txt"
      fi
      adb_device "$serial" shell run-as "$PACKAGE_NAME" cat "$status_path"
      return 0
    fi
    sleep "$delay_seconds"
  done
  fail "Timed out waiting for automation status: $status_path"
}

status_metric() {
  local status="$1"
  local metric="$2"
  STATUS_PAYLOAD="$status" python3 - "$metric" <<'PY'
import os
import re
import sys

metric = re.escape(sys.argv[1])
match = re.search(rf"(?:^|[ ,]){metric}=([0-9]+)", os.environ["STATUS_PAYLOAD"])
print(match.group(1) if match else "")
PY
}

assert_int_metric_at_most() {
  local label="$1"
  local metric_name="$2"
  local value="$3"
  local max_value="$4"
  [[ "$value" =~ ^[0-9]+$ ]] || fail "$label did not report numeric $metric_name."
  [[ "$max_value" =~ ^[0-9]+$ ]] || fail "Invalid max threshold for $label $metric_name: $max_value"
  [[ "$value" -le "$max_value" ]] ||
    fail "$label $metric_name exceeded threshold: ${value}ms > ${max_value}ms."
}

latest_automation_thumbnail_log() {
  local serial="$1"
  adb_device "$serial" logcat -d -s MobileSlicer:I MobileSlicerNative:I '*:S' 2>/dev/null |
    grep 'automation:slice_thumbnail_render' |
    tail -n 1 || true
}

assert_automation_thumbnail_scope() {
  local serial="$1"
  local label="$2"
  local expected_package="$3"
  local expected_rendered="${4:-}"
  local expected_renderer="${5:-}"
  local line
  line="$(latest_automation_thumbnail_log "$serial")"
  [[ -n "$line" ]] || fail "$label did not log automation thumbnail scope."
  [[ "$line" == *"packageThumbnails=$expected_package"* ]] ||
    fail "$label thumbnail scope mismatch. Expected packageThumbnails=$expected_package, got: $line"
  if [[ -n "$expected_rendered" ]]; then
    [[ "$line" == *"rendered=$expected_rendered"* ]] ||
      fail "$label thumbnail render count mismatch. Expected rendered=$expected_rendered, got: $line"
  fi
  if [[ -n "$expected_renderer" ]]; then
    [[ "$line" == *"renderers=$expected_renderer"* ]] ||
      fail "$label thumbnail renderer mismatch. Expected renderers=$expected_renderer, got: $line"
  fi
}

run_egl_thumbnail_smoke() {
  local serial="$1"
  require_device_automation
  install_apk "$serial"
  local width="${MOBILE_SLICER_EGL_THUMBNAIL_WIDTH:-128}"
  local height="${MOBILE_SLICER_EGL_THUMBNAIL_HEIGHT:-128}"
  local repeats="${MOBILE_SLICER_EGL_THUMBNAIL_REPEATS:-3}"
  [[ "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ && "$repeats" =~ ^[0-9]+$ ]] ||
    fail "EGL thumbnail smoke width/height/repeats must be numeric."
  local stamp status_path
  stamp="$(date +%Y%m%d-%H%M%S)"
  status_path="/data/data/$PACKAGE_NAME/files/automation/egl-thumbnail-smoke-$stamp.status.txt"
  set_current_automation_context "$serial" "egl-thumbnail-smoke" "" "$status_path"

  log "Running offscreen EGL thumbnail smoke on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local start_output
  start_output="$(adb_device "$serial" shell "am start -W \
    -a '$EGL_THUMBNAIL_SMOKE_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_status_path '$status_path' \
    --ei automation_width '$width' \
    --ei automation_height '$height' \
    --ei automation_repeats '$repeats'")"
  printf '%s\n' "$start_output"
  if printf '%s\n' "$start_output" | grep -Eq '^(Error|Exception):'; then
    fail "EGL thumbnail smoke activity did not start."
  fi

  log "EGL thumbnail smoke status"
  local status
  status="$(wait_for_status "$serial" "$status_path" 120 1)"
  printf '%s\n' "$status"
  [[ "$status" == success:* ]] || fail "EGL thumbnail smoke did not report success."

  local total_ms render_ms read_ms nontransparent gl_error
  total_ms="$(status_metric "$status" "totalMs")"
  render_ms="$(status_metric "$status" "renderMs")"
  read_ms="$(status_metric "$status" "readPixelsMs")"
  nontransparent="$(status_metric "$status" "nontransparentPixels")"
  gl_error="$(status_metric "$status" "glError")"
  assert_int_metric_at_most \
    "egl-thumbnail-smoke" \
    "totalMs" \
    "$total_ms" \
    "${MOBILE_SLICER_EGL_THUMBNAIL_TOTAL_MAX_MS:-1000}"
  assert_int_metric_at_most \
    "egl-thumbnail-smoke" \
    "renderMs" \
    "$render_ms" \
    "${MOBILE_SLICER_EGL_THUMBNAIL_RENDER_MAX_MS:-500}"
  assert_int_metric_at_most \
    "egl-thumbnail-smoke" \
    "readPixelsMs" \
    "$read_ms" \
    "${MOBILE_SLICER_EGL_THUMBNAIL_READ_MAX_MS:-500}"
  [[ "$nontransparent" =~ ^[0-9]+$ && "$nontransparent" -gt 0 ]] ||
    fail "EGL thumbnail smoke produced no visible pixels."
  [[ "$gl_error" == "0" ]] ||
    fail "EGL thumbnail smoke reported glError=$gl_error"
  adb_device "$serial" logcat -d -v time | grep 'egl_thumbnail_smoke' || true
  clear_current_automation_context
  assert_no_crash_after_launch "$serial"
}

run_egl_slice_thumbnail_smoke() {
  local serial="$1"
  require_device_automation
  install_apk "$serial"
  local width="${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_WIDTH:-128}"
  local height="${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_HEIGHT:-128}"
  [[ "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ ]] ||
    fail "EGL slice thumbnail smoke width/height must be numeric."
  local stamp status_path
  stamp="$(date +%Y%m%d-%H%M%S)"
  status_path="/data/data/$PACKAGE_NAME/files/automation/egl-slice-thumbnail-smoke-$stamp.status.txt"
  set_current_automation_context "$serial" "egl-slice-thumbnail-smoke" "" "$status_path"

  log "Running offscreen EGL slice thumbnail smoke on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local start_output
  start_output="$(adb_device "$serial" shell "am start -W \
    -a '$EGL_SLICE_THUMBNAIL_SMOKE_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_status_path '$status_path' \
    --ei automation_width '$width' \
    --ei automation_height '$height'")"
  printf '%s\n' "$start_output"
  if printf '%s\n' "$start_output" | grep -Eq '^(Error|Exception):'; then
    fail "EGL slice thumbnail smoke activity did not start."
  fi

  log "EGL slice thumbnail smoke status"
  local status
  status="$(wait_for_status "$serial" "$status_path" 120 1)"
  printf '%s\n' "$status"
  [[ "$status" == success:* ]] || fail "EGL slice thumbnail smoke did not report success."

  local total_ms draw_ms read_ms upload_ms nontransparent gl_error bbox_min_x bbox_max_x bbox_min_y bbox_max_y
  total_ms="$(status_metric "$status" "totalMs")"
  draw_ms="$(status_metric "$status" "drawMs")"
  read_ms="$(status_metric "$status" "readPixelsMs")"
  upload_ms="$(status_metric "$status" "uploadMs")"
  nontransparent="$(status_metric "$status" "nontransparentPixels")"
  gl_error="$(status_metric "$status" "glError")"
  bbox_min_x="$(status_metric "$status" "bboxMinX")"
  bbox_max_x="$(status_metric "$status" "bboxMaxX")"
  bbox_min_y="$(status_metric "$status" "bboxMinY")"
  bbox_max_y="$(status_metric "$status" "bboxMaxY")"
  assert_int_metric_at_most \
    "egl-slice-thumbnail-smoke" \
    "totalMs" \
    "$total_ms" \
    "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_TOTAL_MAX_MS:-1000}"
  assert_int_metric_at_most \
    "egl-slice-thumbnail-smoke" \
    "uploadMs" \
    "$upload_ms" \
    "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_UPLOAD_MAX_MS:-500}"
  assert_int_metric_at_most \
    "egl-slice-thumbnail-smoke" \
    "drawMs" \
    "$draw_ms" \
    "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_DRAW_MAX_MS:-500}"
  assert_int_metric_at_most \
    "egl-slice-thumbnail-smoke" \
    "readPixelsMs" \
    "$read_ms" \
    "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_READ_MAX_MS:-500}"
  [[ "$nontransparent" =~ ^[0-9]+$ && "$nontransparent" -gt 0 ]] ||
    fail "EGL slice thumbnail smoke produced no visible pixels."
  [[ "$nontransparent" -ge "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_MIN_VISIBLE_PIXELS:-256}" ]] ||
    fail "EGL slice thumbnail smoke produced too few visible pixels: $nontransparent"
  [[ "$bbox_min_x" =~ ^-?[0-9]+$ && "$bbox_max_x" =~ ^-?[0-9]+$ && "$bbox_min_y" =~ ^-?[0-9]+$ && "$bbox_max_y" =~ ^-?[0-9]+$ ]] ||
    fail "EGL slice thumbnail smoke bbox metrics were not numeric."
  [[ $((bbox_max_x - bbox_min_x + 1)) -ge "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_MIN_BBOX_PX:-12}" ]] ||
    fail "EGL slice thumbnail smoke bbox width is too small."
  [[ $((bbox_max_y - bbox_min_y + 1)) -ge "${MOBILE_SLICER_EGL_SLICE_THUMBNAIL_MIN_BBOX_PX:-12}" ]] ||
    fail "EGL slice thumbnail smoke bbox height is too small."
  [[ "$gl_error" == "0" ]] ||
    fail "EGL slice thumbnail smoke reported glError=$gl_error"
  adb_device "$serial" logcat -d -v time | grep 'egl_thumbnail_smoke' || true
  clear_current_automation_context
  assert_no_crash_after_launch "$serial"
}

run_egl_thumbnail_compare() {
  local serial="$1"
  require_device_automation
  local compare_model="${MOBILE_SLICER_EGL_COMPARE_MODEL:-$DEFAULT_SLICE_SMOKE_STL}"
  require_automation_fixture "$compare_model" "EGL thumbnail comparison STL"
  install_apk "$serial"
  local width="${MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_WIDTH:-128}"
  local height="${MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_HEIGHT:-128}"
  local bed_width="${MOBILE_SLICER_EGL_COMPARE_BED_WIDTH_MM:-270}"
  local bed_depth="${MOBILE_SLICER_EGL_COMPARE_BED_DEPTH_MM:-270}"
  local bed_height="${MOBILE_SLICER_EGL_COMPARE_BED_HEIGHT_MM:-256}"
  local source_layout="${MOBILE_SLICER_EGL_COMPARE_SOURCE_LAYOUT:-single}"
  local source_colors="${MOBILE_SLICER_EGL_COMPARE_SOURCE_COLORS:-}"
  [[ "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ ]] ||
    fail "EGL thumbnail compare width/height must be numeric."
  [[ "$bed_width" =~ ^[0-9]+([.][0-9]+)?$ && "$bed_depth" =~ ^[0-9]+([.][0-9]+)?$ && "$bed_height" =~ ^[0-9]+([.][0-9]+)?$ ]] ||
    fail "EGL thumbnail compare bed dimensions must be numeric."
  local app_model_path
  app_model_path="$(stage_app_private_file "$serial" "$compare_model" | tail -n 1)"
  local stamp status_path app_artifact_dir artifact_dir
  stamp="$(date +%Y%m%d-%H%M%S)"
  status_path="/data/data/$PACKAGE_NAME/files/automation/egl-thumbnail-compare-$stamp.status.txt"
  app_artifact_dir="/data/data/$PACKAGE_NAME/files/automation/egl-thumbnail-compare-$stamp"
  artifact_dir="$ROOT_DIR/artifacts/egl-thumbnail-compare/$stamp"
  mkdir -p "$artifact_dir"
  set_current_automation_context "$serial" "egl-thumbnail-compare" "" "$status_path"

  log "Running EGL thumbnail comparison on $serial with model $compare_model"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -rf "$status_path" "$app_artifact_dir"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local start_output
  start_output="$(adb_device "$serial" shell "am start -W \
    -a '$EGL_THUMBNAIL_COMPARE_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_status_path '$status_path' \
    --es automation_model_path '$app_model_path' \
    --es automation_artifact_dir '$app_artifact_dir' \
    --ei automation_width '$width' \
    --ei automation_height '$height' \
    --ef automation_bed_width_mm '$bed_width' \
    --ef automation_bed_depth_mm '$bed_depth' \
    --ef automation_bed_height_mm '$bed_height' \
    --es automation_thumbnail_source_layout '$source_layout' \
    --es automation_thumbnail_source_colors '$source_colors'")"
  printf '%s\n' "$start_output"
  if printf '%s\n' "$start_output" | grep -Eq '^(Error|Exception):'; then
    fail "EGL thumbnail comparison activity did not start."
  fi

  log "EGL thumbnail comparison status"
  local status
  status="$(wait_for_status "$serial" "$status_path" 120 1)"
  printf '%s\n' "$status"
  [[ "$status" == success:* ]] || fail "EGL thumbnail comparison did not report success."

  local metrics_path distinct_roles min_visible
  metrics_path="$app_artifact_dir/metrics.json"
  distinct_roles="$(status_metric "$status" "distinctEglRoles")"
  min_visible="$(status_metric "$status" "minVisible")"
  local source_count
  source_count="$(status_metric "$status" "sourceCount")"
  if [[ "$source_layout" == "two_filament_objects" ]]; then
    [[ "$source_count" == "2" ]] ||
      fail "EGL thumbnail comparison did not use two filament sources: sourceCount=$source_count"
  fi
  [[ "$distinct_roles" =~ ^[0-9]+$ && "$distinct_roles" -ge "${MOBILE_SLICER_EGL_COMPARE_MIN_DISTINCT_ROLES:-3}" ]] ||
    fail "EGL thumbnail comparison role outputs collapsed: distinct=$distinct_roles"
  local min_visible_floor
  if [[ -n "${MOBILE_SLICER_EGL_COMPARE_MIN_VISIBLE_PIXELS:-}" ]]; then
    min_visible_floor="$MOBILE_SLICER_EGL_COMPARE_MIN_VISIBLE_PIXELS"
  elif [[ "${MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE:-0}" == "1" ]]; then
    min_visible_floor="1"
  else
    min_visible_floor="64"
  fi
  [[ "$min_visible" =~ ^[0-9]+$ && "$min_visible" -ge "$min_visible_floor" ]] ||
    fail "EGL thumbnail comparison visible-pixel floor failed: minVisible=$min_visible"

  pull_app_private_file "$serial" "$metrics_path" "$artifact_dir/metrics.json"
  local role
  for role in plate no_light top pick; do
    pull_app_private_file "$serial" "$app_artifact_dir/software-$role.png" "$artifact_dir/software-$role.png"
    pull_app_private_file "$serial" "$app_artifact_dir/egl-$role.png" "$artifact_dir/egl-$role.png"
  done
  local min_bbox_floor
  if [[ -n "${MOBILE_SLICER_EGL_COMPARE_MIN_BBOX_PX:-}" ]]; then
    min_bbox_floor="$MOBILE_SLICER_EGL_COMPARE_MIN_BBOX_PX"
  elif [[ "${MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE:-0}" == "1" ]]; then
    min_bbox_floor="1"
  else
    min_bbox_floor="8"
  fi
  python3 - "$artifact_dir/metrics.json" \
    "${MOBILE_SLICER_EGL_COMPARE_TOTAL_MAX_MS:-1000}" \
    "${MOBILE_SLICER_EGL_COMPARE_DRAW_MAX_MS:-500}" \
    "${MOBILE_SLICER_EGL_COMPARE_READ_MAX_MS:-500}" \
    "$min_bbox_floor" <<'PY'
import json
import pathlib
import sys

metrics = json.loads(pathlib.Path(sys.argv[1]).read_text())
total_max = int(sys.argv[2])
draw_max = int(sys.argv[3])
read_max = int(sys.argv[4])
min_bbox = int(sys.argv[5])
roles = metrics.get("roles", [])
if len(roles) != 4:
    raise SystemExit(f"expected 4 roles, got {len(roles)}")
for role in roles:
    name = role["role"]
    egl = role["egl"]
    timing = role["eglTiming"]
    bbox_w = egl["bboxMaxX"] - egl["bboxMinX"] + 1
    bbox_h = egl["bboxMaxY"] - egl["bboxMinY"] + 1
    if egl["nontransparentPixels"] <= 0:
        raise SystemExit(f"{name}: blank EGL thumbnail")
    if bbox_w < min_bbox or bbox_h < min_bbox:
        raise SystemExit(f"{name}: EGL bbox too small {bbox_w}x{bbox_h}")
    if timing.get("glError") != 0:
        raise SystemExit(f"{name}: glError={timing.get('glError')}")
    if timing.get("totalMs", 0) > total_max:
        raise SystemExit(f"{name}: totalMs too high {timing.get('totalMs')}")
    if timing.get("drawMs", 0) > draw_max:
        raise SystemExit(f"{name}: drawMs too high {timing.get('drawMs')}")
    if timing.get("readPixelsMs", 0) > read_max:
        raise SystemExit(f"{name}: readPixelsMs too high {timing.get('readPixelsMs')}")
print(f"validated {len(roles)} EGL thumbnail roles from {sys.argv[1]}")
PY
  python3 "$ROOT_DIR/scripts/orca_thumbnail_visual_diff.py" \
    --mobile-dir "$artifact_dir" \
    --reference-dir "$artifact_dir" \
    --mobile-prefix "egl-" \
    --reference-prefix "software-" \
    --report-only \
    --output-json "$artifact_dir/software-vs-egl-visual-diff.json" \
    --output-md "$artifact_dir/software-vs-egl-visual-diff.md"
  local orca_reference_dir="${MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR:-$ROOT_DIR/regression-fixtures/orca-thumbnail-references/simple-cube}"
  local reference_args=()
  if [[ "${MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE:-0}" == "1" ]]; then
    reference_args+=(--require-reference)
  fi
  python3 "$ROOT_DIR/scripts/orca_thumbnail_visual_diff.py" \
    --mobile-dir "$artifact_dir" \
    --reference-dir "$orca_reference_dir" \
    --mobile-prefix "egl-" \
    --max-luma-delta "${MOBILE_SLICER_ORCA_VISUAL_MAX_LUMA_DELTA:-80.0}" \
    --max-alpha-coverage-delta "${MOBILE_SLICER_ORCA_VISUAL_MAX_ALPHA_COVERAGE_DELTA:-0.35}" \
    --max-bbox-delta-px "${MOBILE_SLICER_ORCA_VISUAL_MAX_BBOX_DELTA_PX:-32}" \
    --min-coverage-ratio "${MOBILE_SLICER_ORCA_VISUAL_MIN_COVERAGE_RATIO:-0.25}" \
    --max-coverage-ratio "${MOBILE_SLICER_ORCA_VISUAL_MAX_COVERAGE_RATIO:-4.0}" \
    --output-json "$artifact_dir/orca-reference-visual-diff.json" \
    --output-md "$artifact_dir/orca-reference-visual-diff.md" \
    "${reference_args[@]}"
  rm -rf "$ROOT_DIR/artifacts/egl-thumbnail-compare/latest"
  ln -sfn "$artifact_dir" "$ROOT_DIR/artifacts/egl-thumbnail-compare/latest"
  adb_device "$serial" logcat -d -v time | grep 'egl_thumbnail_smoke' || true
  log "EGL thumbnail comparison artifacts: $artifact_dir"
  clear_current_automation_context
  assert_no_crash_after_launch "$serial"
}

run_automation_slice() {
  local local_stl="$1"
  local serial="$2"
  local label="$3"
  local should_install="${4:-1}"
  local config_json="${5:-$BENCHY_AUTOMATION_CONFIG}"
  local collect_perf="${6:-0}"
  local output_suffix="${7:-.gcode}"
  local preserve_project_objects="${8:-0}"
  local two_filament_objects="${9:-0}"
  local multi_plate_package="${10:-0}"
  if [[ "$should_install" == "1" ]]; then
    install_apk "$serial"
  fi

  local app_model_path
  app_model_path="$(stage_app_private_file "$serial" "$local_stl" | tail -n 1)"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local output_path="/data/data/$PACKAGE_NAME/files/automation/$label-$stamp$output_suffix"
  local status_path="$output_path.status.txt"
  set_current_automation_context "$serial" "$label" "$output_path" "$status_path"

  log "Running automation slice '$label' on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$output_path" "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  local preserve_arg=""
  if [[ "$preserve_project_objects" == "1" ]]; then
    preserve_arg=" --ez automation_preserve_project_objects true"
  fi
  local two_filament_arg=""
  if [[ "$two_filament_objects" == "1" ]]; then
    two_filament_arg=" --ez automation_two_filament_objects true"
  fi
  local multi_plate_arg=""
  if [[ "$multi_plate_package" == "1" ]]; then
    multi_plate_arg=" --ez automation_multi_plate_package true"
  fi
  local start_output
  start_output="$(adb_device "$serial" shell "CONFIG='$config_json'; am start -W \
    -a '$AUTOMATION_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_model_path '$app_model_path' \
    --es automation_output_path '$output_path' \
    --es automation_status_path '$status_path' \
    --es automation_config_json \"\$CONFIG\"$preserve_arg$two_filament_arg$multi_plate_arg")"
  printf '%s\n' "$start_output"
  if printf '%s\n' "$start_output" | grep -Eq '^(Error|Exception):'; then
    fail "Automation activity did not start."
  fi

  log "Automation status"
  local status
  if [[ "$collect_perf" == "1" ]]; then
    local status_tmp
    local perf_status_attempts
    perf_status_attempts="$(env_int_or_default MOBILE_SLICER_PERF_STATUS_ATTEMPTS 720)"
    status_tmp="$(mktemp)"
    wait_for_status_with_memory "$serial" "$status_path" "$perf_status_attempts" 1 > "$status_tmp"
    status="$(cat "$status_tmp")"
    rm -f "$status_tmp"
  else
    status="$(wait_for_status "$serial" "$status_path")"
    PERF_LAST_PEAK_PSS_KB=0
  fi
  printf '%s\n' "$status"
  [[ "$status" == success:* ]] || fail "Automation slice did not report success."
  log "Output file"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" ls -lh "$output_path"
  local output_bytes
  output_bytes="$(adb_device "$serial" shell run-as "$PACKAGE_NAME" wc -c "$output_path" | awk '{print $1}')"
  [[ "$output_bytes" =~ ^[0-9]+$ ]] || fail "Unable to read automation output byte count."
  [[ "$output_bytes" -gt 1024 ]] || fail "Automation output is unexpectedly small: $output_bytes bytes."
  log "Automation output verified: $output_bytes bytes"
  AUTOMATION_LAST_OUTPUT_PATH="$output_path"
  AUTOMATION_LAST_STATUS="$status"
  AUTOMATION_LAST_BYTES="$output_bytes"
  AUTOMATION_LAST_STAGING_MS="$(status_metric "$status" "stagingMs")"
  AUTOMATION_LAST_NATIVE_LOAD_MS="$(status_metric "$status" "nativeLoadMs")"
  AUTOMATION_LAST_ELAPSED_MS="$(status_metric "$status" "elapsedMs")"
  AUTOMATION_LAST_PLACEMENT_MS="$(status_metric "$status" "placementMs")"
  AUTOMATION_LAST_CONFIG_MS="$(status_metric "$status" "configMs")"
  AUTOMATION_LAST_NATIVE_SLICE_MS="$(status_metric "$status" "nativeSliceMs")"
  AUTOMATION_LAST_THUMBNAIL_MS="$(status_metric "$status" "thumbnailMs")"
  AUTOMATION_LAST_WRITE_GCODE_MS="$(status_metric "$status" "writeGcodeMs")"
  AUTOMATION_LAST_PREVIEW_MOVES="$(status_metric "$status" "previewMoves")"
  AUTOMATION_LAST_PREVIEW_CACHE_BUILT="$(status_metric "$status" "previewCacheBuilt")"
  AUTOMATION_LAST_PREVIEW_CACHE_COMPLETE="$(status_metric "$status" "previewCacheComplete")"
  AUTOMATION_LAST_PREVIEW_CACHED_VERTICES="$(status_metric "$status" "previewCachedVertices")"
  AUTOMATION_LAST_PREVIEW_CACHE_BUILD_MS="$(status_metric "$status" "previewCacheBuildMs")"
  AUTOMATION_LAST_PREVIEW_INFO_RICH="$(status_metric "$status" "previewInfoRich")"
  AUTOMATION_LAST_PREVIEW_INFO_ENRICHED_RICH="$(status_metric "$status" "previewInfoEnrichedRich")"
  AUTOMATION_LAST_PREVIEW_INFO_LINE_TYPES="$(status_metric "$status" "previewInfoLineTypes")"
  AUTOMATION_LAST_PREVIEW_INFO_FILAMENTS="$(status_metric "$status" "previewInfoFilaments")"
  AUTOMATION_LAST_PREVIEW_INFO_LAYERS="$(status_metric "$status" "previewInfoLayers")"
  AUTOMATION_LAST_PREVIEW_PLAN_MS="$(status_metric "$status" "previewPlanMs")"
  AUTOMATION_LAST_PREVIEW_LOAD_MS="$(status_metric "$status" "previewLoadMs")"
  AUTOMATION_LAST_PREVIEW_RANGES="$(status_metric "$status" "previewRanges")"
  AUTOMATION_LAST_PREVIEW_REQUESTED_LAYERS="$(status_metric "$status" "previewRequestedLayers")"
  AUTOMATION_LAST_PREVIEW_LOADED_START="$(status_metric "$status" "previewLoadedStart")"
  AUTOMATION_LAST_PREVIEW_LOADED_END="$(status_metric "$status" "previewLoadedEnd")"
  AUTOMATION_LAST_PREVIEW_LOADED_LAYERS="$(status_metric "$status" "previewLoadedLayers")"
  AUTOMATION_LAST_PREVIEW_PLANNED_COVERED_LAYERS="$(status_metric "$status" "previewPlannedCoveredLayers")"
  AUTOMATION_LAST_PREVIEW_VERTEX_BUDGET="$(status_metric "$status" "previewVertexBudget")"
  AUTOMATION_LAST_PREVIEW_LOAD_SUCCESS="$(status_metric "$status" "previewLoadSuccess")"
  AUTOMATION_LAST_PREVIEW_LOAD_GL_UNAVAILABLE="$(status_metric "$status" "previewLoadGlUnavailable")"
  AUTOMATION_LAST_NATIVE_GCODE_BYTES="$(status_metric "$status" "nativeGcodeBytes")"
  AUTOMATION_LAST_PROCESSOR_MOVE_BYTES="$(status_metric "$status" "processorMoveBytes")"
  AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES="$(status_metric "$status" "processorLineEndBytes")"
  AUTOMATION_LAST_PROCESSOR_MOVE_BYTES_RETAINED="$(status_metric "$status" "processorMoveBytesRetained")"
  AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES_RETAINED="$(status_metric "$status" "processorLineEndBytesRetained")"
  AUTOMATION_LAST_PROCESSOR_RELEASE_MS="$(status_metric "$status" "processorReleaseMs")"
  AUTOMATION_LAST_PROCESSOR_MOVES_RELEASED_DURING_EXPORT="$(status_metric "$status" "processorMovesReleasedDuringExport")"
  AUTOMATION_LAST_NATIVE_EXPORT_START_RSS_KB="$(status_metric "$status" "nativeExportStartRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_SETUP_RSS_KB="$(status_metric "$status" "nativeAfterSetupRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_LAYERS_RSS_KB="$(status_metric "$status" "nativeAfterLayersRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_FOOTER_RSS_KB="$(status_metric "$status" "nativeAfterFooterRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_GENERATION_RSS_KB="$(status_metric "$status" "nativeAfterGenerationRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_FINALIZE_RSS_KB="$(status_metric "$status" "nativeAfterFinalizeRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_RELEASE_RSS_KB="$(status_metric "$status" "nativeAfterReleaseRssKb")"
  AUTOMATION_LAST_NATIVE_AFTER_STATS_RSS_KB="$(status_metric "$status" "nativeAfterStatsRssKb")"
  AUTOMATION_LAST_NATIVE_BEFORE_RETURN_RSS_KB="$(status_metric "$status" "nativeBeforeReturnRssKb")"
  AUTOMATION_LAST_PREVIEW_LAYER_COUNT_BYTES="$(status_metric "$status" "previewLayerCountBytes")"
  AUTOMATION_LAST_EXACT_PREVIEW_CACHE_ELIGIBLE="$(status_metric "$status" "exactPreviewCacheEligible")"
  AUTOMATION_LAST_PEAK_PSS_KB="$PERF_LAST_PEAK_PSS_KB"
  AUTOMATION_LAST_PEAK_JAVA_HEAP_KB="$PERF_LAST_PEAK_JAVA_HEAP_KB"
  AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB="$PERF_LAST_PEAK_NATIVE_HEAP_KB"
  AUTOMATION_LAST_PEAK_GRAPHICS_KB="$PERF_LAST_PEAK_GRAPHICS_KB"
  AUTOMATION_LAST_PEAK_PRIVATE_OTHER_KB="$PERF_LAST_PEAK_PRIVATE_OTHER_KB"
  AUTOMATION_LAST_PEAK_SYSTEM_KB="$PERF_LAST_PEAK_SYSTEM_KB"
  AUTOMATION_LAST_CACHE_TOTAL_KB="$(app_cache_usage_kb "$serial" total)"
  AUTOMATION_LAST_CACHE_ORCA_TEMP_KB="$(app_cache_usage_kb "$serial" orca-temp)"
  AUTOMATION_LAST_CACHE_GENERATED_GCODE_KB="$(app_cache_usage_kb "$serial" generated-gcode)"
  AUTOMATION_LAST_CACHE_STAGED_MODEL_KB="$(app_cache_usage_kb "$serial" staged-model)"
  clear_current_automation_context
}

run_sliced_3mf_metadata_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "sliced 3MF metadata smoke STL"
  install_apk "$serial"
  local config_json
  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "sliced-3mf-metadata" "0" "$config_json" "0" ".gcode.3mf"
  assert_automation_thumbnail_scope "$serial" "sliced-3mf-metadata" "true" "5" "offscreen_egl"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local package_path="$tmp_dir/sliced-metadata.gcode.3mf"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$package_path"
  log "Auditing sliced 3MF metadata package"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-3mf "$package_path" \
    --expect-3mf-thumbnail-entry Metadata/plate_1.png \
    --expect-3mf-thumbnail-entry Metadata/plate_1_small.png \
    --expect-3mf-thumbnail-entry Metadata/plate_no_light_1.png \
    --expect-3mf-thumbnail-entry Metadata/top_1.png \
    --expect-3mf-thumbnail-entry Metadata/pick_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1_small.png \
    --expect-3mf-entry Metadata/plate_1.json \
    --expect-3mf-bbox-json Metadata/plate_1.json \
    --require-distinct-3mf-thumbnail-entries \
    --require-3mf-thumbnail-visuals \
    --pretty
  rm -rf "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

run_step_sliced_3mf_source_metadata_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$STEP_IMPORT_SMOKE_MODEL" "STEP sliced 3MF source metadata smoke model"
  install_apk "$serial"
  local config_json
  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"")"
  run_automation_slice "$STEP_IMPORT_SMOKE_MODEL" "$serial" "step-sliced-3mf-source-metadata" "0" "$config_json" "0" ".gcode.3mf"
  assert_automation_thumbnail_scope "$serial" "step-sliced-3mf-source-metadata" "true" "5" "offscreen_egl"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local package_path="$tmp_dir/step-sliced-metadata.gcode.3mf"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$package_path"
  log "Auditing STEP sliced 3MF source metadata package"
  python3 "$ROOT_DIR/scripts/orca_3mf_project_preservation_audit.py" \
    --three-mf "$package_path" \
    --min-plate-count 1 \
    --min-object-count 1 \
    --require-object-names \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --require-step-source \
    --pretty
  rm -rf "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

run_step_multi_plate_sliced_3mf_source_metadata_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$STEP_IMPORT_SMOKE_MODEL" "STEP multi-plate sliced 3MF source metadata smoke model"
  install_apk "$serial"
  local config_json
  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"")"
  run_automation_slice "$STEP_IMPORT_SMOKE_MODEL" "$serial" "step-multi-plate-sliced-3mf-source-metadata" "0" "$config_json" "0" ".gcode.3mf" "0" "0" "1"
  assert_automation_thumbnail_scope "$serial" "step-multi-plate-sliced-3mf-source-metadata" "true" "5" "offscreen_egl"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local package_path="$tmp_dir/step-multi-plate-sliced-metadata.gcode.3mf"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$package_path"
  log "Auditing STEP multi-plate sliced 3MF source metadata package"
  python3 "$ROOT_DIR/scripts/orca_3mf_project_preservation_audit.py" \
    --three-mf "$package_path" \
    --min-plate-count 2 \
    --min-object-count 2 \
    --require-object-names \
    --require-filament-assignments \
    --require-project-thumbnails \
    --require-plate-json-metadata \
    --require-sliced-plate-gcode \
    --require-project-settings \
    --require-step-source \
    --min-step-source-object-count 2 \
    --pretty
  rm -rf "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

run_multi_plate_sliced_3mf_metadata_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "multi-plate sliced 3MF metadata smoke STL"
  install_apk "$serial"
  local config_json
  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "multi-plate-sliced-3mf-metadata" "0" "$config_json" "0" ".gcode.3mf" "0" "0" "1"
  assert_automation_thumbnail_scope "$serial" "multi-plate-sliced-3mf-metadata" "true" "5" "offscreen_egl"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local package_path="$tmp_dir/multi-plate-sliced-metadata.gcode.3mf"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$package_path"
  log "Auditing multi-plate sliced 3MF metadata package"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-3mf "$package_path" \
    --expect-3mf-entry Metadata/plate_1.gcode \
    --expect-3mf-entry Metadata/plate_2.gcode \
    --expect-3mf-thumbnail-entry Metadata/plate_1.png \
    --expect-3mf-thumbnail-entry Metadata/plate_1_small.png \
    --expect-3mf-thumbnail-entry Metadata/plate_no_light_1.png \
    --expect-3mf-thumbnail-entry Metadata/top_1.png \
    --expect-3mf-thumbnail-entry Metadata/pick_1.png \
    --expect-3mf-thumbnail-entry Metadata/plate_2.png \
    --expect-3mf-thumbnail-entry Metadata/plate_2_small.png \
    --expect-3mf-thumbnail-entry Metadata/plate_no_light_2.png \
    --expect-3mf-thumbnail-entry Metadata/top_2.png \
    --expect-3mf-thumbnail-entry Metadata/pick_2.png \
    --expect-3mf-relationship-target /Metadata/plate_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1_small.png \
    --expect-3mf-entry Metadata/plate_1.json \
    --expect-3mf-entry Metadata/plate_2.json \
    --expect-3mf-bbox-json Metadata/plate_1.json \
    --expect-3mf-bbox-json Metadata/plate_2.json \
    --expect-different-3mf-entry Metadata/plate_1.gcode:Metadata/plate_2.gcode \
    --expect-different-3mf-entry Metadata/plate_1.json:Metadata/plate_2.json \
    --require-distinct-3mf-thumbnail-entries \
    --require-3mf-thumbnail-visuals \
    --pretty
  rm -rf "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

run_orca_fixture_mobile_capture() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "Orca metadata fixture cube STL"
  install_apk "$serial"

  local fixture_dir="$ROOT_DIR/regression-fixtures/orca-metadata/mobile"
  mkdir -p "$fixture_dir"

  local config_json output_path

  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-simple-cube-png" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-simple-cube-png" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/simple-cube-png.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail:128x128 \
    --require-gcode-thumbnail-visuals \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"150x150\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-qidi-q2-legacy-png" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-qidi-q2-legacy-png" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/qidi-q2-legacy-png.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail:150x150 \
    --require-gcode-thumbnail-visuals \
    --forbid-thumbnail-tag gimage \
    --forbid-thumbnail-tag simage \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"96x96\"" "thumbnails_format=\"QOI\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-non-qidi-qoi" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-non-qidi-qoi" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/non-qidi-qoi.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail_QOI:96x96 \
    --require-gcode-thumbnail-payload-signatures \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"96x96\"" "thumbnails_format=\"JPG\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-jpg" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-jpg" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/jpg.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail_JPG:96x96 \
    --require-gcode-thumbnail-payload-signatures \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"70x70\"" "thumbnails_format=\"BTT_TFT\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-btt-tft" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-btt-tft" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/btt-tft.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail_BTT:70x70 \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"380x380,210x210\"" "thumbnails_format=\"COLPIC\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-qidi-colpic-gimage-simage" "0" "$config_json"
  assert_automation_thumbnail_scope "$serial" "fixture-qidi-colpic-gimage-simage" "false" "2" "offscreen_egl"
  output_path="$fixture_dir/qidi-colpic-gimage-simage.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail gimage \
    --expect-thumbnail simage \
    --require-print-time \
    --require-filament \
    --pretty

  config_json="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-sliced-3mf-metadata" "0" "$config_json" "0" ".gcode.3mf"
  assert_automation_thumbnail_scope "$serial" "fixture-sliced-3mf-metadata" "true" "5" "offscreen_egl"
  output_path="$fixture_dir/simple-cube.gcode.3mf"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-3mf "$output_path" \
    --expect-3mf-thumbnail-entry Metadata/plate_1.png \
    --expect-3mf-thumbnail-entry Metadata/plate_1_small.png \
    --expect-3mf-thumbnail-entry Metadata/plate_no_light_1.png \
    --expect-3mf-thumbnail-entry Metadata/top_1.png \
    --expect-3mf-thumbnail-entry Metadata/pick_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1_small.png \
    --expect-3mf-entry Metadata/plate_1.json \
    --expect-3mf-bbox-json Metadata/plate_1.json \
    --require-distinct-3mf-thumbnail-entries \
    --require-3mf-thumbnail-visuals \
    --pretty

  config_json="$(automation_config_with_overrides \
    "thumbnails=\"128x128\"" \
    "thumbnails_format=\"PNG\"" \
    'filament_type=["PLA","PLA"]' \
    'filament_settings_id=["Generic PLA A","Generic PLA B"]' \
    'filament_colour=["#F2754E","#4EA3F2"]' \
    'filament_diameter=[1.75,1.75]' \
    'nozzle_temperature_initial_layer=[210,210]' \
    'nozzle_temperature=[210,210]' \
    'filament_max_volumetric_speed=[50,50]' \
    'flush_multiplier=[1,1]' \
    'flush_volumes_matrix="0,0,0,0,0,0,0,0"')"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fixture-multi-filament" "0" "$config_json" "0" ".gcode" "0" "1"
  assert_automation_thumbnail_scope "$serial" "fixture-multi-filament" "false" "1" "offscreen_egl"
  output_path="$fixture_dir/multi-filament.gcode"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail:128x128 \
    --require-gcode-thumbnail-visuals \
    --require-print-time \
    --require-filament \
    --require-toolchange \
    --pretty

  run_orca_fixture_gate
  assert_no_crash_after_launch "$serial"
}

append_orca_metadata_benchmark_case() {
  local report_path="$1"
  local label="$2"
  local audit_path="$3"
  local logcat_path="$4"
  {
    printf '| `%s` | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
      "$label" \
      "${AUTOMATION_LAST_NATIVE_SLICE_MS:-}" \
      "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
      "${AUTOMATION_LAST_WRITE_GCODE_MS:-}" \
      "${AUTOMATION_LAST_ELAPSED_MS:-}" \
      "${AUTOMATION_LAST_BYTES:-}" \
      "${AUTOMATION_LAST_NATIVE_GCODE_BYTES:-}" \
      "$(basename "$audit_path")" \
      "$(basename "$logcat_path")"
  } >> "$report_path"
}

run_orca_metadata_benchmark_case() {
  local serial="$1"
  local artifact_dir="$2"
  local label="$3"
  local config_json="$4"
  local output_suffix="${5:-.gcode}"
  local audit_path_option="$6"
  shift 6
  local audit_args=("$@")

  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "$label" "0" "$config_json" "0" "$output_suffix"

  local output_path="$artifact_dir/$label$output_suffix"
  local audit_path="$artifact_dir/$label.audit.json"
  local logcat_path="$artifact_dir/$label.logcat.txt"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  adb_device "$serial" logcat -d > "$logcat_path" || true
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" "$audit_path_option" "$output_path" "${audit_args[@]}" --pretty > "$audit_path"
  append_orca_metadata_benchmark_case "$artifact_dir/report.md" "$label" "$audit_path" "$logcat_path"
}

run_fluidd_thumbnail_metadata_smoke() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "Fluidd thumbnail metadata cube STL"
  install_apk "$serial"

  local stamp artifact_dir report_path config_json output_path audit_path logcat_path
  stamp="$(date +%Y%m%d-%H%M%S)"
  artifact_dir="$ROOT_DIR/artifacts/fluidd-thumbnail-metadata/$stamp"
  mkdir -p "$artifact_dir"
  report_path="$artifact_dir/report.md"
  output_path="$artifact_dir/fluidd-thumbnail-metadata.gcode"
  audit_path="$artifact_dir/fluidd-thumbnail-metadata.audit.json"
  logcat_path="$artifact_dir/fluidd-thumbnail-metadata.logcat.txt"

  config_json="$(automation_config_with_overrides \
    "thumbnails=\"48x48/PNG,300x300/PNG\"" \
    "thumbnails_format=\"PNG\"" \
    "filament_type=\"PLA\"")"

  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "fluidd-thumbnail-metadata" "0" "$config_json" "0" ".gcode"
  assert_automation_thumbnail_scope "$serial" "fluidd-thumbnail-metadata" "false" "2" "offscreen_egl"
  assert_int_metric_at_most \
    "fluidd-thumbnail-metadata" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_FLUIDD_THUMBNAIL_MAX_MS:-500}"

  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$output_path"
  if ! grep -Eq '^; generated by OrcaSlicer .*MobileSlicer .* on ' "$output_path"; then
    fail "Fluidd thumbnail metadata gate expected an OrcaSlicer-compatible generator marker in $output_path"
  fi
  adb_device "$serial" logcat -d > "$logcat_path" || true
  python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" \
    --mobile-gcode "$output_path" \
    --expect-thumbnail thumbnail:48x48 \
    --expect-thumbnail thumbnail:300x300 \
    --require-gcode-thumbnail-visuals \
    --require-gcode-thumbnail-antialias \
    --require-print-time \
    --require-filament \
    --pretty > "$audit_path"

  {
    printf '# Fluidd Thumbnail Metadata Gate\n\n'
    printf -- '- captured_at: %s\n' "$stamp"
    printf -- '- serial: `%s`\n' "$serial"
    printf -- '- model: `%s`\n' "$DEFAULT_SLICE_SMOKE_STL"
    printf -- '- output: `%s`\n' "$output_path"
    printf -- '- audit: `%s`\n' "$audit_path"
    printf -- '- logcat: `%s`\n' "$logcat_path"
    printf -- '- thumbnailMs: `%s`\n' "${AUTOMATION_LAST_THUMBNAIL_MS:-}"
    printf -- '- elapsedMs: `%s`\n' "${AUTOMATION_LAST_ELAPSED_MS:-}"
    printf -- '- outputBytes: `%s`\n' "${AUTOMATION_LAST_BYTES:-}"
  } > "$report_path"

  verify_moonraker_fluidd_round_trip "$output_path" "$artifact_dir" "$report_path"

  rm -rf "$ROOT_DIR/artifacts/fluidd-thumbnail-metadata/latest"
  ln -sfn "$artifact_dir" "$ROOT_DIR/artifacts/fluidd-thumbnail-metadata/latest"
  log "Fluidd thumbnail metadata artifacts: $artifact_dir"
  assert_clean_crash_buffer "$serial"
}

urlencode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote
print(quote(sys.argv[1], safe=""))
PY
}

verify_moonraker_fluidd_round_trip() {
  local output_path="$1"
  local artifact_dir="$2"
  local report_path="$3"
  local base_url="${MOBILE_SLICER_MOONRAKER_URL:-}"
  if [[ -z "$base_url" ]]; then
    printf -- '- moonrakerRoundTrip: skipped (set MOBILE_SLICER_MOONRAKER_URL to verify Fluidd-visible metadata on a live host)\n' >> "$report_path"
    return 0
  fi

  command -v curl >/dev/null 2>&1 || fail "curl is required for MOBILE_SLICER_MOONRAKER_URL round-trip verification"

  base_url="${base_url%/}"
  local stamp remote_name encoded_name upload_json metadata_json delete_json metadata_url
  stamp="$(date +%Y%m%d-%H%M%S)"
  remote_name="${MOBILE_SLICER_MOONRAKER_UPLOAD_NAME:-MobileSlicer_fluidd_thumbnail_${stamp}.gcode}"
  encoded_name="$(urlencode "$remote_name")"
  upload_json="$artifact_dir/moonraker-upload.json"
  metadata_json="$artifact_dir/moonraker-metadata.json"
  delete_json="$artifact_dir/moonraker-delete.json"
  metadata_url="$base_url/server/files/metadata?filename=$encoded_name"

  local curl_headers=()
  if [[ -n "${MOBILE_SLICER_MOONRAKER_API_KEY:-}" ]]; then
    curl_headers=(-H "X-Api-Key: ${MOBILE_SLICER_MOONRAKER_API_KEY}")
  fi

  curl -fsS "${curl_headers[@]}" \
    -F "root=gcodes" \
    -F "print=false" \
    -F "file=@${output_path};filename=${remote_name}" \
    "$base_url/server/files/upload" > "$upload_json"

  local attempt
  for attempt in $(seq 1 20); do
    if curl -fsS "${curl_headers[@]}" "$metadata_url" > "$metadata_json"; then
      if python3 - "$metadata_json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)

thumbs = []

def walk(value):
    if isinstance(value, dict):
        if "width" in value and "height" in value:
            try:
                thumbs.append((int(value["width"]), int(value["height"])))
            except (TypeError, ValueError):
                pass
        for child in value.values():
            walk(child)
    elif isinstance(value, list):
        for child in value:
            walk(child)

walk(payload)
required = {(48, 48), (300, 300)}
missing = sorted(required.difference(thumbs))
if missing:
    print(f"missing thumbnail dimensions: {missing}; reported={sorted(set(thumbs))}", file=sys.stderr)
    sys.exit(1)
print(f"reported thumbnail dimensions: {sorted(set(thumbs))}")
PY
      then
        {
          printf -- '- moonrakerRoundTrip: passed\n'
          printf -- '- moonrakerUrl: `%s`\n' "$base_url"
          printf -- '- moonrakerRemoteFile: `%s`\n' "$remote_name"
          printf -- '- moonrakerUpload: `%s`\n' "$upload_json"
          printf -- '- moonrakerMetadata: `%s`\n' "$metadata_json"
        } >> "$report_path"
        if [[ "${MOBILE_SLICER_MOONRAKER_DELETE_AFTER:-1}" != "0" ]]; then
          curl -fsS -X DELETE "${curl_headers[@]}" "$base_url/server/files/gcodes/$encoded_name" > "$delete_json" || true
          printf -- '- moonrakerDelete: `%s`\n' "$delete_json" >> "$report_path"
        fi
        return 0
      fi
    fi
    sleep 1
  done

  fail "Moonraker metadata did not report 48x48 and 300x300 thumbnails for $remote_name after upload. Metadata artifact: $metadata_json"
}

run_orca_metadata_benchmark() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "Orca metadata benchmark cube STL"
  install_apk "$serial"

  local stamp artifact_dir report_path
  stamp="$(date +%Y%m%d-%H%M%S)"
  artifact_dir="$ROOT_DIR/artifacts/orca-metadata-benchmark/$stamp"
  mkdir -p "$artifact_dir"
  report_path="$artifact_dir/report.md"
  {
    printf '# Orca Metadata Benchmark\n\n'
    printf -- '- captured_at: %s\n' "$stamp"
    printf -- '- serial: `%s`\n' "$serial"
    printf -- '- model: `%s`\n\n' "$DEFAULT_SLICE_SMOKE_STL"
    printf '| case | nativeSliceMs | thumbnailMs | writeGcodeMs | elapsedMs | outputBytes | nativeGcodeBytes | audit | logcat |\n'
    printf '|---|---:|---:|---:|---:|---:|---:|---|---|\n'
  } > "$report_path"

  local baseline_config png_small_config png_config fluidd_config qoi_config jpg_config btt_config colpic_config package_config
  baseline_config="$(automation_config_with_overrides "filament_type=\"PLA\"")"
  png_small_config="$(automation_config_with_overrides "thumbnails=\"48x48\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  png_config="$(automation_config_with_overrides "thumbnails=\"128x128\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  fluidd_config="$(automation_config_with_overrides "thumbnails=\"48x48/PNG,300x300/PNG\"" "thumbnails_format=\"PNG\"" "filament_type=\"PLA\"")"
  qoi_config="$(automation_config_with_overrides "thumbnails=\"96x96\"" "thumbnails_format=\"QOI\"" "filament_type=\"PLA\"")"
  jpg_config="$(automation_config_with_overrides "thumbnails=\"96x96\"" "thumbnails_format=\"JPG\"" "filament_type=\"PLA\"")"
  btt_config="$(automation_config_with_overrides "thumbnails=\"70x70\"" "thumbnails_format=\"BTT_TFT\"" "filament_type=\"PLA\"")"
  colpic_config="$(automation_config_with_overrides "thumbnails=\"380x380,210x210\"" "thumbnails_format=\"COLPIC\"" "filament_type=\"PLA\"")"
  package_config="$png_config"

  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-baseline-no-thumbnail" "$baseline_config" ".gcode" \
    --mobile-gcode --require-print-time --require-filament
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-png-48" "$png_small_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail:48x48 --require-gcode-thumbnail-visuals --require-gcode-thumbnail-antialias --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-png-48" "false" "1" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-png-48" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_PNG_SMALL_THUMBNAIL_MAX_MS:-250}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-png-128" "$png_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail:128x128 --require-gcode-thumbnail-visuals --require-gcode-thumbnail-antialias --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-png-128" "false" "1" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-png-128" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_PNG_THUMBNAIL_MAX_MS:-250}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-fluidd-png" "$fluidd_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail:48x48 --expect-thumbnail thumbnail:300x300 --require-gcode-thumbnail-visuals --require-gcode-thumbnail-antialias --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-fluidd-png" "false" "2" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-fluidd-png" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_FLUIDD_THUMBNAIL_MAX_MS:-500}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-qoi-96" "$qoi_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail_QOI:96x96 --require-gcode-thumbnail-payload-signatures --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-qoi-96" "false" "1" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-qoi-96" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_QOI_THUMBNAIL_MAX_MS:-250}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-jpg-96" "$jpg_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail_JPG:96x96 --require-gcode-thumbnail-payload-signatures --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-jpg-96" "false" "1" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-jpg-96" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_JPG_THUMBNAIL_MAX_MS:-250}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-btt-tft-70" "$btt_config" ".gcode" \
    --mobile-gcode --expect-thumbnail thumbnail_BTT:70x70 --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-btt-tft-70" "false" "1" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-btt-tft-70" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_BTT_THUMBNAIL_MAX_MS:-250}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-colpic-gimage-simage" "$colpic_config" ".gcode" \
    --mobile-gcode --expect-thumbnail gimage --expect-thumbnail simage --require-print-time --require-filament
  assert_automation_thumbnail_scope "$serial" "metadata-colpic-gimage-simage" "false" "2" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-colpic-gimage-simage" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_COLPIC_THUMBNAIL_MAX_MS:-500}"
  run_orca_metadata_benchmark_case \
    "$serial" "$artifact_dir" "metadata-sliced-3mf-png" "$package_config" ".gcode.3mf" \
    --mobile-3mf \
    --expect-3mf-thumbnail-entry Metadata/plate_1.png \
    --expect-3mf-thumbnail-entry Metadata/plate_1_small.png \
    --expect-3mf-thumbnail-entry Metadata/plate_no_light_1.png \
    --expect-3mf-thumbnail-entry Metadata/top_1.png \
    --expect-3mf-thumbnail-entry Metadata/pick_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1.png \
    --expect-3mf-relationship-target /Metadata/plate_1_small.png \
    --expect-3mf-entry Metadata/plate_1.json \
    --expect-3mf-bbox-json Metadata/plate_1.json \
    --require-distinct-3mf-thumbnail-entries \
    --require-3mf-thumbnail-visuals
  assert_automation_thumbnail_scope "$serial" "metadata-sliced-3mf-png" "true" "5" "offscreen_egl"
  assert_int_metric_at_most \
    "metadata-sliced-3mf-png" \
    "thumbnailMs" \
    "${AUTOMATION_LAST_THUMBNAIL_MS:-}" \
    "${MOBILE_SLICER_METADATA_3MF_THUMBNAIL_MAX_MS:-500}"
  assert_int_metric_at_most \
    "metadata-sliced-3mf-png" \
    "writeGcodeMs" \
    "${AUTOMATION_LAST_WRITE_GCODE_MS:-}" \
    "${MOBILE_SLICER_METADATA_3MF_WRITE_MAX_MS:-1000}"

  log "Orca metadata benchmark report: $report_path"
  assert_clean_crash_buffer "$serial"
}

assert_automation_preview_info_ready() {
  local label="$1"
  [[ "$AUTOMATION_LAST_PREVIEW_INFO_RICH" == "1" || "$AUTOMATION_LAST_PREVIEW_INFO_ENRICHED_RICH" == "1" ]] ||
    fail "$label did not expose rich preview info summary data."
  [[ "${AUTOMATION_LAST_PREVIEW_INFO_LINE_TYPES:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_INFO_LINE_TYPES" -gt 0 ]] ||
    fail "$label did not report preview info line types."
  [[ "${AUTOMATION_LAST_PREVIEW_INFO_FILAMENTS:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_INFO_FILAMENTS" -gt 0 ]] ||
    fail "$label did not report preview info filaments."
  [[ "${AUTOMATION_LAST_PREVIEW_INFO_LAYERS:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_INFO_LAYERS" -gt 0 ]] ||
    fail "$label did not report preview info layers."
}

assert_automation_preview_plan_ready() {
  local label="$1"
  [[ "${AUTOMATION_LAST_PREVIEW_RANGES:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_RANGES" -gt 0 ]] ||
    fail "$label did not report planned preview ranges."
  [[ "${AUTOMATION_LAST_PREVIEW_LOADED_LAYERS:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_LOADED_LAYERS" -gt 0 ]] ||
    fail "$label did not report a selected preview range."
  [[ "${AUTOMATION_LAST_PREVIEW_REQUESTED_LAYERS:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_REQUESTED_LAYERS" -gt 0 ]] ||
    fail "$label did not report requested preview layers."
  [[ "${AUTOMATION_LAST_PREVIEW_PLANNED_COVERED_LAYERS:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_PLANNED_COVERED_LAYERS" -ge "$AUTOMATION_LAST_PREVIEW_REQUESTED_LAYERS" ]] ||
    fail "$label preview ranges do not cover the requested layer count."
  [[ "${AUTOMATION_LAST_PREVIEW_VERTEX_BUDGET:-0}" =~ ^[0-9]+$ && "$AUTOMATION_LAST_PREVIEW_VERTEX_BUDGET" -gt 0 ]] ||
    fail "$label did not report the preview vertex budget."
  [[ "${AUTOMATION_LAST_PREVIEW_PLAN_MS:-0}" =~ ^[0-9]+$ ]] ||
    fail "$label did not report preview range planning time."
  [[ "${AUTOMATION_LAST_PREVIEW_LOAD_MS:-0}" =~ ^[0-9]+$ ]] ||
    fail "$label did not report preview viewer load time."
  [[ "$AUTOMATION_LAST_PREVIEW_LOAD_SUCCESS" == "1" || "$AUTOMATION_LAST_PREVIEW_LOAD_GL_UNAVAILABLE" == "1" ]] ||
    fail "$label preview viewer load neither succeeded nor reported the expected non-UI OpenGL unavailability."
}

run_automation_slice_expect_failure() {
  local local_stl="$1"
  local serial="$2"
  local label="$3"
  local should_install="${4:-1}"
  local config_json="${5:-$BENCHY_AUTOMATION_CONFIG}"
  if [[ "$should_install" == "1" ]]; then
    install_apk "$serial"
  fi

  local app_model_path
  app_model_path="$(stage_app_private_file "$serial" "$local_stl" | tail -n 1)"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local output_path="/data/data/$PACKAGE_NAME/files/automation/$label-$stamp.gcode"
  local status_path="$output_path.status.txt"
  set_current_automation_context "$serial" "$label" "$output_path" "$status_path"

  log "Running expected-failure automation slice '$label' on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$output_path" "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  ensure_package_enabled "$serial"
  adb_device "$serial" logcat -c
  adb_device "$serial" shell "CONFIG='$config_json'; am start -W \
    -a '$AUTOMATION_ACTION' \
    -n '$MAIN_ACTIVITY' \
    --es automation_model_path '$app_model_path' \
    --es automation_output_path '$output_path' \
    --es automation_status_path '$status_path' \
    --es automation_config_json \"\$CONFIG\""

  log "Automation status"
  local status
  status="$(wait_for_status "$serial" "$status_path")"
  printf '%s\n' "$status"
  [[ "$status" == failed:* ]] || fail "Automation slice unexpectedly succeeded."
  if adb_device "$serial" shell run-as "$PACKAGE_NAME" test -e "$output_path"; then
    local output_bytes
    output_bytes="$(adb_device "$serial" shell run-as "$PACKAGE_NAME" wc -c "$output_path" | awk '{print $1}')"
    fail "Rejected automation slice left stale output at $output_path ($output_bytes bytes)."
  fi
  AUTOMATION_LAST_OUTPUT_PATH="$output_path"
  AUTOMATION_LAST_STATUS="$status"
  clear_current_automation_context
}

set_current_automation_context() {
  CURRENT_AUTOMATION_SERIAL="$1"
  CURRENT_AUTOMATION_LABEL="$2"
  CURRENT_AUTOMATION_OUTPUT_PATH="$3"
  CURRENT_AUTOMATION_STATUS_PATH="$4"
}

clear_current_automation_context() {
  CURRENT_AUTOMATION_SERIAL=""
  CURRENT_AUTOMATION_LABEL=""
  CURRENT_AUTOMATION_OUTPUT_PATH=""
  CURRENT_AUTOMATION_STATUS_PATH=""
}

run_benchy() {
  local local_stl="$1"
  local serial="$2"
  require_device_automation
  run_automation_slice "$local_stl" "$serial" "benchy"
}

append_perf_record() {
  local records_path="$1"
  local name="$2"
  local type="$3"
  local startup_ms="$4"
  local staging_ms="$5"
  local native_load_ms="$6"
  local placement_ms="$7"
  local config_ms="$8"
  local native_slice_ms="$9"
  local write_gcode_ms="${10}"
  local elapsed_ms="${11}"
  local preview_moves="${12}"
  local preview_cache_built="${13}"
  local preview_cache_complete="${14}"
  local preview_cached_vertices="${15}"
  local preview_cache_build_ms="${16}"
  local preview_plan_ms="${17}"
  local preview_load_ms="${18}"
  local preview_ranges="${19}"
  local preview_loaded_layers="${20}"
  local preview_load_success="${21}"
  local preview_load_gl_unavailable="${22}"
  local peak_pss_kb="${23}"
  local peak_java_heap_kb="${24}"
  local peak_native_heap_kb="${25}"
  local peak_graphics_kb="${26}"
  local peak_private_other_kb="${27}"
  local peak_system_kb="${28}"
  local bytes="${29}"
  local fixture_bytes="${30}"
  local device_output_path="${31}"
  local processor_moves_released="${32:-}"
  local processor_move_bytes_retained="${33:-}"
  local processor_line_end_bytes_retained="${34:-}"
  local native_after_finalize_rss_kb="${35:-}"
  local native_after_release_rss_kb="${36:-}"
  local native_after_stats_rss_kb="${37:-}"
  local native_before_return_rss_kb="${38:-}"
  local cache_total_kb="${39:-}"
  local cache_orca_temp_kb="${40:-}"
  local cache_generated_gcode_kb="${41:-}"
  local cache_staged_model_kb="${42:-}"
  python3 - "$records_path" "$name" "$type" "$startup_ms" "$staging_ms" "$native_load_ms" "$placement_ms" "$config_ms" "$native_slice_ms" "$write_gcode_ms" "$elapsed_ms" "$preview_moves" "$preview_cache_built" "$preview_cache_complete" "$preview_cached_vertices" "$preview_cache_build_ms" "$preview_plan_ms" "$preview_load_ms" "$preview_ranges" "$preview_loaded_layers" "$preview_load_success" "$preview_load_gl_unavailable" "$peak_pss_kb" "$peak_java_heap_kb" "$peak_native_heap_kb" "$peak_graphics_kb" "$peak_private_other_kb" "$peak_system_kb" "$bytes" "$fixture_bytes" "$device_output_path" "$processor_moves_released" "$processor_move_bytes_retained" "$processor_line_end_bytes_retained" "$native_after_finalize_rss_kb" "$native_after_release_rss_kb" "$native_after_stats_rss_kb" "$native_before_return_rss_kb" "$cache_total_kb" "$cache_orca_temp_kb" "$cache_generated_gcode_kb" "$cache_staged_model_kb" <<'PY'
import json
import sys

(
    path,
    name,
    record_type,
    startup_ms,
    staging_ms,
    native_load_ms,
    placement_ms,
    config_ms,
    native_slice_ms,
    write_gcode_ms,
    elapsed_ms,
    preview_moves,
    preview_cache_built,
    preview_cache_complete,
    preview_cached_vertices,
    preview_cache_build_ms,
    preview_plan_ms,
    preview_load_ms,
    preview_ranges,
    preview_loaded_layers,
    preview_load_success,
    preview_load_gl_unavailable,
    peak_pss_kb,
    peak_java_heap_kb,
    peak_native_heap_kb,
    peak_graphics_kb,
    peak_private_other_kb,
    peak_system_kb,
    bytes_value,
    fixture_bytes,
    device_output_path,
    processor_moves_released,
    processor_move_bytes_retained,
    processor_line_end_bytes_retained,
    native_after_finalize_rss_kb,
    native_after_release_rss_kb,
    native_after_stats_rss_kb,
    native_before_return_rss_kb,
    cache_total_kb,
    cache_orca_temp_kb,
    cache_generated_gcode_kb,
    cache_staged_model_kb,
) = sys.argv[1:]

def maybe_int(value):
    return int(value) if value.isdigit() else None

record = {
    "name": name,
    "type": record_type,
}
for key, value in [
    ("startup_ms", startup_ms),
    ("staging_ms", staging_ms),
    ("native_load_ms", native_load_ms),
    ("placement_ms", placement_ms),
    ("config_ms", config_ms),
    ("native_slice_ms", native_slice_ms),
    ("write_gcode_ms", write_gcode_ms),
    ("elapsed_ms", elapsed_ms),
    ("preview_moves", preview_moves),
    ("preview_cache_built", preview_cache_built),
    ("preview_cache_complete", preview_cache_complete),
    ("preview_cached_vertices", preview_cached_vertices),
    ("preview_cache_build_ms", preview_cache_build_ms),
    ("preview_plan_ms", preview_plan_ms),
    ("preview_load_ms", preview_load_ms),
    ("preview_ranges", preview_ranges),
    ("preview_loaded_layers", preview_loaded_layers),
    ("preview_load_success", preview_load_success),
    ("preview_load_gl_unavailable", preview_load_gl_unavailable),
    ("peak_pss_kb", peak_pss_kb),
    ("peak_java_heap_kb", peak_java_heap_kb),
    ("peak_native_heap_kb", peak_native_heap_kb),
    ("peak_graphics_kb", peak_graphics_kb),
    ("peak_private_other_kb", peak_private_other_kb),
    ("peak_system_kb", peak_system_kb),
    ("bytes", bytes_value),
    ("fixture_bytes", fixture_bytes),
    ("processor_moves_released_during_export", processor_moves_released),
    ("processor_move_bytes_retained", processor_move_bytes_retained),
    ("processor_line_end_bytes_retained", processor_line_end_bytes_retained),
    ("native_after_finalize_rss_kb", native_after_finalize_rss_kb),
    ("native_after_release_rss_kb", native_after_release_rss_kb),
    ("native_after_stats_rss_kb", native_after_stats_rss_kb),
    ("native_before_return_rss_kb", native_before_return_rss_kb),
    ("cache_total_kb", cache_total_kb),
    ("cache_orca_temp_kb", cache_orca_temp_kb),
    ("cache_generated_gcode_kb", cache_generated_gcode_kb),
    ("cache_staged_model_kb", cache_staged_model_kb),
]:
    parsed = maybe_int(value)
    if parsed is not None:
        record[key] = parsed
if device_output_path:
    record["device_output_path"] = device_output_path
with open(path, "a", encoding="utf-8") as handle:
    handle.write(json.dumps(record, sort_keys=True) + "\n")
PY
}

run_perf_slice_case() {
  local records_path="$1"
  local serial="$2"
  local name="$3"
  local fixture="$4"
  local config_json="$5"
  PERF_CURRENT_CASE="$name"
  run_automation_slice "$fixture" "$serial" "perf-$name" "0" "$config_json" "1"
  PERF_CURRENT_CASE=""
  assert_automation_preview_plan_ready "$name"
  local fixture_bytes
  fixture_bytes="$(wc -c < "$fixture" | tr -d ' ')"
  append_perf_record \
    "$records_path" \
    "$name" \
    "slice" \
    "" \
    "$AUTOMATION_LAST_STAGING_MS" \
    "$AUTOMATION_LAST_NATIVE_LOAD_MS" \
    "$AUTOMATION_LAST_PLACEMENT_MS" \
    "$AUTOMATION_LAST_CONFIG_MS" \
    "$AUTOMATION_LAST_NATIVE_SLICE_MS" \
    "$AUTOMATION_LAST_WRITE_GCODE_MS" \
    "$AUTOMATION_LAST_ELAPSED_MS" \
    "$AUTOMATION_LAST_PREVIEW_MOVES" \
    "$AUTOMATION_LAST_PREVIEW_CACHE_BUILT" \
    "$AUTOMATION_LAST_PREVIEW_CACHE_COMPLETE" \
    "$AUTOMATION_LAST_PREVIEW_CACHED_VERTICES" \
    "$AUTOMATION_LAST_PREVIEW_CACHE_BUILD_MS" \
    "$AUTOMATION_LAST_PREVIEW_PLAN_MS" \
    "$AUTOMATION_LAST_PREVIEW_LOAD_MS" \
    "$AUTOMATION_LAST_PREVIEW_RANGES" \
    "$AUTOMATION_LAST_PREVIEW_LOADED_LAYERS" \
    "$AUTOMATION_LAST_PREVIEW_LOAD_SUCCESS" \
    "$AUTOMATION_LAST_PREVIEW_LOAD_GL_UNAVAILABLE" \
    "$AUTOMATION_LAST_PEAK_PSS_KB" \
    "$AUTOMATION_LAST_PEAK_JAVA_HEAP_KB" \
    "$AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB" \
    "$AUTOMATION_LAST_PEAK_GRAPHICS_KB" \
    "$AUTOMATION_LAST_PEAK_PRIVATE_OTHER_KB" \
    "$AUTOMATION_LAST_PEAK_SYSTEM_KB" \
    "$AUTOMATION_LAST_BYTES" \
    "$fixture_bytes" \
    "$AUTOMATION_LAST_OUTPUT_PATH" \
    "$AUTOMATION_LAST_PROCESSOR_MOVES_RELEASED_DURING_EXPORT" \
    "$AUTOMATION_LAST_PROCESSOR_MOVE_BYTES_RETAINED" \
    "$AUTOMATION_LAST_PROCESSOR_LINE_END_BYTES_RETAINED" \
    "$AUTOMATION_LAST_NATIVE_AFTER_FINALIZE_RSS_KB" \
    "$AUTOMATION_LAST_NATIVE_AFTER_RELEASE_RSS_KB" \
    "$AUTOMATION_LAST_NATIVE_AFTER_STATS_RSS_KB" \
    "$AUTOMATION_LAST_NATIVE_BEFORE_RETURN_RSS_KB" \
    "$AUTOMATION_LAST_CACHE_TOTAL_KB" \
    "$AUTOMATION_LAST_CACHE_ORCA_TEMP_KB" \
    "$AUTOMATION_LAST_CACHE_GENERATED_GCODE_KB" \
    "$AUTOMATION_LAST_CACHE_STAGED_MODEL_KB"
}

perf_case_name() {
  local base_name="$1"
  local repeat_index="$2"
  local repeat_count="$3"
  if [[ "$repeat_count" -le 1 ]]; then
    printf '%s\n' "$base_name"
  else
    printf '%s-r%s\n' "$base_name" "$repeat_index"
  fi
}

run_performance_gate() {
  local serial="$1"
  local heavy_only="${2:-0}"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "small cube performance STL"
  require_automation_fixture "$SUPPORT_SLICE_SMOKE_STL" "bridge support performance STL"
  require_automation_fixture "$PERIMETER_ARRAY_SLICE_SMOKE_STL" "perimeter array performance STL"
  require_automation_fixture "$MEDIUM_SLICE_PERF_STL" "medium performance STL"
  require_automation_fixture "$COMPLEX_SLICE_PERF_STL" "complex performance STL"
  require_automation_fixture "$STRESS_SLICE_PERF_STL" "stress performance STL"
  local repeat_count="${MOBILE_SLICER_PERF_REPEAT_COUNT:-1}"
  [[ "$repeat_count" =~ ^[0-9]+$ ]] || fail "MOBILE_SLICER_PERF_REPEAT_COUNT must be a positive integer."
  repeat_count="$((10#$repeat_count))"
  [[ "$repeat_count" -ge 1 ]] || fail "MOBILE_SLICER_PERF_REPEAT_COUNT must be at least 1."
  install_perf_apk "$serial"

  local artifact_root="$ROOT_DIR/artifacts/performance"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local artifact_dir="$artifact_root/$stamp"
  mkdir -p "$artifact_dir"
  local records_path="$artifact_dir/records.jsonl"
  local report_json="$artifact_dir/report.json"
  local report_md="$artifact_dir/report.md"
  local device_state_dir="$artifact_dir/device-state"
  PERF_CURRENT_MEMINFO_DIR="$artifact_dir/meminfo"
  mkdir -p "$PERF_CURRENT_MEMINFO_DIR" "$device_state_dir"

  log "Running non-UI performance gate on $serial"
  capture_perf_device_state "$serial" "$device_state_dir" "before"
  if [[ "$heavy_only" != "1" ]]; then
    local startup_output startup_ms startup_meminfo startup_pss_kb startup_java_heap_kb startup_native_heap_kb startup_graphics_kb startup_private_other_kb startup_system_kb startup_cache_total_kb startup_cache_orca_temp_kb startup_cache_generated_gcode_kb startup_cache_staged_model_kb
    startup_output="$(launch_app_for_perf "$serial")"
    printf '%s\n' "$startup_output"
    startup_ms="$(printf '%s\n' "$startup_output" | tail -n 1)"
    sleep 2
    startup_meminfo="$(device_meminfo "$serial" || true)"
    startup_pss_kb="$(meminfo_total_pss_kb "$startup_meminfo")"
    startup_java_heap_kb="$(meminfo_app_summary_kb "$startup_meminfo" "Java Heap")"
    startup_native_heap_kb="$(meminfo_app_summary_kb "$startup_meminfo" "Native Heap")"
    startup_graphics_kb="$(meminfo_app_summary_kb "$startup_meminfo" "Graphics")"
    startup_private_other_kb="$(meminfo_app_summary_kb "$startup_meminfo" "Private Other")"
    startup_system_kb="$(meminfo_app_summary_kb "$startup_meminfo" "System")"
    startup_cache_total_kb="$(app_cache_usage_kb "$serial" total)"
    startup_cache_orca_temp_kb="$(app_cache_usage_kb "$serial" orca-temp)"
    startup_cache_generated_gcode_kb="$(app_cache_usage_kb "$serial" generated-gcode)"
    startup_cache_staged_model_kb="$(app_cache_usage_kb "$serial" staged-model)"
    printf '%s\n' "$startup_meminfo" > "$PERF_CURRENT_MEMINFO_DIR/cold-start-final-meminfo.txt"
    append_perf_record \
      "$records_path" \
      "cold-start" \
      "startup" \
      "$startup_ms" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "${startup_pss_kb:-0}" \
      "${startup_java_heap_kb:-0}" \
      "${startup_native_heap_kb:-0}" \
      "${startup_graphics_kb:-0}" \
      "${startup_private_other_kb:-0}" \
      "${startup_system_kb:-0}" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "" \
      "$startup_cache_total_kb" \
      "$startup_cache_orca_temp_kb" \
      "$startup_cache_generated_gcode_kb" \
      "$startup_cache_staged_model_kb"
    assert_no_crash_after_launch "$serial"
  fi

  local default_config support_config perimeter_config medium_config complex_config stress_config
  default_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  support_config="$(automation_config_with_overrides brim_width=0 enable_support=true support_type=normal\(auto\) support_style=default support_threshold_angle=10 support_on_build_plate_only=false)"
  perimeter_config="$(automation_config_with_overrides brim_width=0 wall_loops=3 sparse_infill_density=20 enable_support=false small_perimeter_speed=20)"
  medium_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false max_height_mm=320 printable_height=320)"
  complex_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false max_height_mm=320 printable_height=320)"
  stress_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=10 enable_support=false max_height_mm=720 printable_height=720)"

  if [[ "$repeat_count" -gt 1 ]]; then
    log "Repeating performance slice cases $repeat_count times for memory-growth checks"
  fi
  for repeat_index in $(seq 1 "$repeat_count"); do
    if [[ "$heavy_only" != "1" ]]; then
      run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "small-cube" "$repeat_index" "$repeat_count")" "$DEFAULT_SLICE_SMOKE_STL" "$default_config"
      run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "bridge-support" "$repeat_index" "$repeat_count")" "$SUPPORT_SLICE_SMOKE_STL" "$support_config"
      run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "perimeter-array" "$repeat_index" "$repeat_count")" "$PERIMETER_ARRAY_SLICE_SMOKE_STL" "$perimeter_config"
    fi
    run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "medium-speed-structure" "$repeat_index" "$repeat_count")" "$MEDIUM_SLICE_PERF_STL" "$medium_config"
    run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "complex-vfa" "$repeat_index" "$repeat_count")" "$COMPLEX_SLICE_PERF_STL" "$complex_config"
    if [[ "$heavy_only" == "1" || "${MOBILE_SLICER_PERF_INCLUDE_STRESS:-0}" =~ ^(1|true|TRUE|yes|YES)$ ]]; then
      run_perf_slice_case "$records_path" "$serial" "$(perf_case_name "stress-temperature-tower" "$repeat_index" "$repeat_count")" "$STRESS_SLICE_PERF_STL" "$stress_config"
    elif [[ "$repeat_index" == "1" ]]; then
      log "Skipping stress-temperature-tower; set MOBILE_SLICER_PERF_INCLUDE_STRESS=1 to include the 10MB stress fixture."
    fi
  done
  capture_perf_device_state "$serial" "$device_state_dir" "after"

  local baseline_args=()
  local default_baseline="$ROOT_DIR/performance-baselines/perf-heavy-device-baseline.json"
  local perf_baseline="${MOBILE_SLICER_PERF_BASELINE:-}"
  if [[ -z "$perf_baseline" && "$heavy_only" == "1" && -f "$default_baseline" ]]; then
    perf_baseline="$default_baseline"
    log "Using default heavy performance baseline: $perf_baseline"
  elif [[ "$perf_baseline" =~ ^(none|NONE|off|OFF|0)$ ]]; then
    perf_baseline=""
    log "Performance baseline comparison disabled by MOBILE_SLICER_PERF_BASELINE."
  fi
  if [[ -n "$perf_baseline" ]]; then
    [[ -f "$perf_baseline" ]] || fail "Performance baseline does not exist: $perf_baseline"
    baseline_args=(--baseline "$perf_baseline")
  fi
  local analyzer_status=0
  "$ROOT_DIR/scripts/analyze_mobile_performance.py" \
    --input "$records_path" \
    --output-json "$report_json" \
    --output-md "$report_md" \
    "${baseline_args[@]}" || analyzer_status=$?
  ln -sfn "$artifact_dir" "$artifact_root/latest"
  log "Performance artifacts: $artifact_dir"
  PERF_CURRENT_MEMINFO_DIR=""
  assert_no_crash_after_launch "$serial"
  [[ "$analyzer_status" -eq 0 ]] || fail "Performance analyzer failed; see artifacts: $artifact_dir"
}

pull_app_private_file() {
  local serial="$1"
  local app_path="$2"
  local local_path="$3"
  adb_device "$serial" exec-out run-as "$PACKAGE_NAME" cat "$app_path" > "$local_path"
  [[ -s "$local_path" ]] || fail "Pulled file is empty: $local_path"
}

analyze_slice_regression_matrix() {
  local directory="$1"
  python3 - "$directory" <<'PY'
import math
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
word_re = re.compile(r"([A-Z])\s*([-+]?(?:\d+(?:\.\d*)?|\.\d+))", re.I)

def parse(path):
    text = path.read_text(errors="replace")
    x = y = z = e = 0.0
    absolute_xyz = True
    absolute_e = True
    first_z = None
    bounds = [math.inf, -math.inf, math.inf, -math.inf]
    first_layer_feedrates = []
    all_feedrates = []
    feedrate = 0.0
    nozzle = set()
    bed = set()
    types = set()
    for raw in text.splitlines():
        if raw.startswith(";TYPE:") or raw.startswith("; FEATURE:"):
            types.add(raw.split(":", 1)[1].strip())
            continue
        command = raw.split(";", 1)[0].strip()
        if not command:
            continue
        upper = command.upper()
        if upper == "G90":
            absolute_xyz = True
            continue
        if upper == "G91":
            absolute_xyz = False
            continue
        if upper == "M82":
            absolute_e = True
            continue
        if upper == "M83":
            absolute_e = False
            continue
        words = {key.upper(): float(value) for key, value in word_re.findall(command)}
        if upper.startswith(("M104", "M109")) and "S" in words:
            nozzle.add(round(words["S"]))
        if upper.startswith(("M140", "M190")) and "S" in words:
            bed.add(round(words["S"]))
        if upper.startswith(("G0", "G1", "G2", "G3")):
            px, py, pe = x, y, e
            if "X" in words:
                x = words["X"] if absolute_xyz else x + words["X"]
            if "Y" in words:
                y = words["Y"] if absolute_xyz else y + words["Y"]
            if "Z" in words:
                z = words["Z"] if absolute_xyz else z + words["Z"]
            if "E" in words:
                e = words["E"] if absolute_e else e + words["E"]
            if "F" in words:
                feedrate = words["F"]
            if e - pe > 0:
                all_feedrates.append(feedrate)
                if first_z is None:
                    first_z = z
                if abs(z - first_z) <= 0.001:
                    first_layer_feedrates.append(feedrate)
                    bounds[0] = min(bounds[0], px, x)
                    bounds[1] = max(bounds[1], px, x)
                    bounds[2] = min(bounds[2], py, y)
                    bounds[3] = max(bounds[3], py, y)
    width = bounds[1] - bounds[0] if math.isfinite(bounds[0]) else 0.0
    depth = bounds[3] - bounds[2] if math.isfinite(bounds[2]) else 0.0
    return {
        "bytes": path.stat().st_size,
        "width": width,
        "depth": depth,
        "nozzle": nozzle,
        "bed": bed,
        "types": types,
        "max_first_layer_feedrate": max(first_layer_feedrates or [0.0]),
        "max_feedrate": max(all_feedrates or [0.0]),
    }

metrics = {path.stem: parse(path) for path in root.glob("*.gcode")}
required = {"baseline", "brim8", "wall4", "infill35", "support_on", "temp_hot", "speed_fast"}
missing = required - set(metrics)
if missing:
    raise SystemExit(f"missing matrix outputs: {sorted(missing)}")

base = metrics["baseline"]
assert base["bytes"] > 1024, base
assert metrics["brim8"]["width"] > base["width"] + 5.0, (base, metrics["brim8"])
assert metrics["brim8"]["depth"] > base["depth"] + 5.0, (base, metrics["brim8"])
assert metrics["wall4"]["bytes"] > base["bytes"], (base, metrics["wall4"])
assert metrics["infill35"]["bytes"] != base["bytes"], (base, metrics["infill35"])
assert any("support" in item.lower() for item in metrics["support_on"]["types"]), metrics["support_on"]
assert 235 in metrics["temp_hot"]["nozzle"], metrics["temp_hot"]
assert 70 in metrics["temp_hot"]["bed"], metrics["temp_hot"]
assert abs(metrics["speed_fast"]["max_feedrate"] - base["max_feedrate"]) > 1000.0, (base, metrics["speed_fast"])

for name in sorted(metrics):
    item = metrics[name]
    print(
        f"{name}: bytes={item['bytes']} firstLayer={item['width']:.2f}x{item['depth']:.2f} "
        f"maxFirstLayerF={item['max_first_layer_feedrate']:.0f} "
        f"nozzle={sorted(item['nozzle'])} bed={sorted(item['bed'])} "
        f"types={','.join(sorted(item['types']))}"
    )
PY
}

run_slice_regression_matrix() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "default slice smoke STL"
  require_automation_fixture "$SUPPORT_SLICE_SMOKE_STL" "support slice smoke STL"
  install_apk "$serial"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN

  local baseline_config brim_config wall_config infill_config support_config temp_config speed_config
  baseline_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false first_layer_print_speed=10 first_layer_infill_speed=22.5)"
  brim_config="$(automation_config_with_overrides brim_width=8 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  wall_config="$(automation_config_with_overrides brim_width=0 wall_loops=4 sparse_infill_density=15 enable_support=false)"
  infill_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=35 enable_support=false)"
  support_config="$(automation_config_with_overrides brim_width=0 enable_support=true support_type=normal\(auto\) support_style=default support_threshold_angle=10 support_on_build_plate_only=false)"
  temp_config="$(automation_config_with_overrides brim_width=0 nozzle_temperature=235 nozzle_temperature_initial_layer=235 bed_temperature=70 bed_temperature_initial_layer=70)"
  speed_config="$(automation_config_with_overrides brim_width=0 first_layer_print_speed=25 first_layer_infill_speed=45 sparse_infill_speed=80 print_speed_baseline=80)"

  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-baseline" "0" "$baseline_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/baseline.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-brim8" "0" "$brim_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/brim8.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-wall4" "0" "$wall_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/wall4.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-infill35" "0" "$infill_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/infill35.gcode"
  run_automation_slice "$SUPPORT_SLICE_SMOKE_STL" "$serial" "matrix-support" "0" "$support_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/support_on.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-temp-hot" "0" "$temp_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/temp_hot.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "matrix-speed-fast" "0" "$speed_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/speed_fast.gcode"

  log "Analyzing pulled G-code regression matrix"
  analyze_slice_regression_matrix "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

analyze_skirt_parity_matrix() {
  local directory="$1"
  python3 - "$directory" <<'PY'
import math
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
word_re = re.compile(r"([A-Z])\s*([-+]?(?:\d+(?:\.\d*)?|\.\d+))", re.I)
bed_min = -0.1
bed_max = 270.1

def update_bounds(bounds, px, py, x, y):
    bounds[0] = min(bounds[0], px, x)
    bounds[1] = max(bounds[1], px, x)
    bounds[2] = min(bounds[2], py, y)
    bounds[3] = max(bounds[3], py, y)

def finish_bounds(bounds):
    if not math.isfinite(bounds[0]) or not math.isfinite(bounds[2]):
        return None
    return {
        "xmin": bounds[0],
        "xmax": bounds[1],
        "ymin": bounds[2],
        "ymax": bounds[3],
        "width": bounds[1] - bounds[0],
        "depth": bounds[3] - bounds[2],
    }

def parse(path):
    text = path.read_text(errors="replace")
    x = y = z = e = 0.0
    absolute_xyz = True
    absolute_e = True
    first_z = None
    current_type = ""
    types = set()
    first_layer = [math.inf, -math.inf, math.inf, -math.inf]
    first_layer_model = [math.inf, -math.inf, math.inf, -math.inf]
    first_layer_skirt = [math.inf, -math.inf, math.inf, -math.inf]
    for raw in text.splitlines():
        if raw.startswith(";TYPE:") or raw.startswith("; FEATURE:"):
            current_type = raw.split(":", 1)[1].strip()
            types.add(current_type)
            continue
        command = raw.split(";", 1)[0].strip()
        if not command:
            continue
        upper = command.upper()
        if upper == "G90":
            absolute_xyz = True
            continue
        if upper == "G91":
            absolute_xyz = False
            continue
        if upper == "M82":
            absolute_e = True
            continue
        if upper == "M83":
            absolute_e = False
            continue
        words = {key.upper(): float(value) for key, value in word_re.findall(command)}
        if upper.startswith(("G0", "G1", "G2", "G3")):
            px, py, pe = x, y, e
            if "X" in words:
                x = words["X"] if absolute_xyz else x + words["X"]
            if "Y" in words:
                y = words["Y"] if absolute_xyz else y + words["Y"]
            if "Z" in words:
                z = words["Z"] if absolute_xyz else z + words["Z"]
            if "E" in words:
                e = words["E"] if absolute_e else e + words["E"]
            if e - pe <= 0:
                continue
            if first_z is None:
                first_z = z
            if abs(z - first_z) > 0.001:
                continue
            update_bounds(first_layer, px, py, x, y)
            type_lower = current_type.lower()
            if "skirt" in type_lower:
                update_bounds(first_layer_skirt, px, py, x, y)
            elif "brim" not in type_lower:
                update_bounds(first_layer_model, px, py, x, y)
    return {
        "bytes": path.stat().st_size,
        "types": types,
        "first_layer": finish_bounds(first_layer),
        "model": finish_bounds(first_layer_model),
        "skirt": finish_bounds(first_layer_skirt),
    }

def has_type(item, needle):
    return any(needle in value.lower() for value in item["types"])

def require(condition, message):
    if not condition:
        raise SystemExit(message)

def require_in_bed(bounds, name):
    require(bounds is not None, f"{name}: missing extrusion bounds")
    for axis in ("xmin", "xmax", "ymin", "ymax"):
        value = bounds[axis]
        require(math.isfinite(value), f"{name}: non-finite {axis}={value}")
    require(bed_min <= bounds["xmin"] <= bed_max, f"{name}: xmin outside bed: {bounds}")
    require(bed_min <= bounds["xmax"] <= bed_max, f"{name}: xmax outside bed: {bounds}")
    require(bed_min <= bounds["ymin"] <= bed_max, f"{name}: ymin outside bed: {bounds}")
    require(bed_min <= bounds["ymax"] <= bed_max, f"{name}: ymax outside bed: {bounds}")

metrics = {path.stem: parse(path) for path in root.glob("*.gcode")}
required = {"baseline", "combined", "perobject", "brim_skirt"}
missing = required - set(metrics)
require(not missing, f"missing skirt parity outputs: {sorted(missing)}")

baseline = metrics["baseline"]
combined = metrics["combined"]
perobject = metrics["perobject"]
brim_skirt = metrics["brim_skirt"]

require(baseline["bytes"] > 1024, f"baseline too small: {baseline}")
require(not has_type(baseline, "skirt"), f"baseline unexpectedly emitted skirt: {baseline['types']}")
require(has_type(combined, "skirt"), f"combined skirt missing: {combined['types']}")
require(has_type(perobject, "skirt"), f"per-object skirt missing: {perobject['types']}")
require(has_type(brim_skirt, "skirt"), f"brim+skirt missing skirt: {brim_skirt['types']}")
require(has_type(brim_skirt, "brim"), f"brim+skirt missing brim: {brim_skirt['types']}")

for name, item in metrics.items():
    require_in_bed(item["first_layer"], f"{name} first layer")
    if item["skirt"] is not None:
        require_in_bed(item["skirt"], f"{name} skirt")

require(combined["first_layer"]["width"] > baseline["first_layer"]["width"] + 1.0,
        f"combined skirt did not expand first-layer width: baseline={baseline['first_layer']} combined={combined['first_layer']}")
require(combined["first_layer"]["depth"] > baseline["first_layer"]["depth"] + 1.0,
        f"combined skirt did not expand first-layer depth: baseline={baseline['first_layer']} combined={combined['first_layer']}")

for name in sorted(metrics):
    item = metrics[name]
    skirt = item["skirt"]
    skirt_summary = "none" if skirt is None else f"{skirt['width']:.2f}x{skirt['depth']:.2f}"
    first = item["first_layer"]
    print(
        f"{name}: bytes={item['bytes']} firstLayer={first['width']:.2f}x{first['depth']:.2f} "
        f"skirt={skirt_summary} types={','.join(sorted(item['types']))}"
    )
PY
}

run_skirt_parity_matrix() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "default slice smoke STL"
  install_apk "$serial"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN

  local baseline_config combined_config perobject_config brim_skirt_config
  baseline_config="$(automation_config_with_overrides skirts=0 skirt_loops=0 brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  combined_config="$(automation_config_with_overrides skirts=2 skirt_loops=2 skirt_type=combined skirt_distance=2 min_skirt_length=0 skirt_height=1 brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  perobject_config="$(automation_config_with_overrides skirts=2 skirt_loops=2 skirt_type=perobject skirt_distance=2 min_skirt_length=0 skirt_height=1 brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  brim_skirt_config="$(automation_config_with_overrides skirts=2 skirt_loops=2 skirt_type=combined skirt_distance=2 min_skirt_length=0 skirt_height=1 brim_width=4 brim_type=outer_only wall_loops=2 sparse_infill_density=15 enable_support=false)"

  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "skirt-baseline" "0" "$baseline_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/baseline.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "skirt-combined" "0" "$combined_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/combined.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "skirt-perobject" "0" "$perobject_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/perobject.gcode"
  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "skirt-brim" "0" "$brim_skirt_config"
  pull_app_private_file "$serial" "$AUTOMATION_LAST_OUTPUT_PATH" "$tmp_dir/brim_skirt.gcode"

  log "Analyzing pulled skirt parity G-code"
  analyze_skirt_parity_matrix "$tmp_dir"
  assert_no_crash_after_launch "$serial"
}

run_slice_lifecycle_regression() {
  local serial="$1"
  require_device_automation
  require_automation_fixture "$DEFAULT_SLICE_SMOKE_STL" "default slice smoke STL"
  require_automation_fixture "$SUPPORT_SLICE_SMOKE_STL" "support slice smoke STL"
  install_apk "$serial"

  local tmp_dir invalid_stl baseline_config support_config
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  invalid_stl="$tmp_dir/rejected-empty.stl"
  printf 'solid rejected\nendsolid rejected\n' > "$invalid_stl"
  baseline_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  support_config="$(automation_config_with_overrides brim_width=0 enable_support=true support_type=normal\(auto\) support_style=default support_threshold_angle=10 support_on_build_plate_only=false)"

  run_automation_slice "$DEFAULT_SLICE_SMOKE_STL" "$serial" "lifecycle-valid-a" "0" "$baseline_config"
  assert_automation_preview_info_ready "lifecycle-valid-a"
  assert_automation_preview_plan_ready "lifecycle-valid-a"
  local first_bytes="$AUTOMATION_LAST_BYTES"
  run_automation_slice_expect_failure "$invalid_stl" "$serial" "lifecycle-rejected-empty" "0" "$baseline_config"
  [[ "$AUTOMATION_LAST_STATUS" == *"nativeLoadModel failed"* || "$AUTOMATION_LAST_STATUS" == *"load"* ]] ||
    fail "Rejected lifecycle load did not report a load failure: $AUTOMATION_LAST_STATUS"
  run_automation_slice "$SUPPORT_SLICE_SMOKE_STL" "$serial" "lifecycle-valid-b" "0" "$support_config"
  assert_automation_preview_info_ready "lifecycle-valid-b"
  assert_automation_preview_plan_ready "lifecycle-valid-b"
  local second_bytes="$AUTOMATION_LAST_BYTES"
  [[ "$first_bytes" != "$second_bytes" ]] ||
    fail "Lifecycle reload emitted the same byte count after model replacement; expected a distinct support fixture output."
  assert_no_crash_after_launch "$serial"
}

mode="${1:-}"
case "$mode" in
  unit)
    run_unit
    ;;
  lint)
    run_lint
    ;;
  stubs)
    run_stub_inventory
    ;;
  script-tests)
    run_script_tests
    ;;
  orca-fixture-gate)
    run_orca_fixture_gate
    ;;
  orca-gcode-metadata-parity)
    run_orca_gcode_metadata_parity
    ;;
  orca-thumbnail-reference-fixtures)
    run_orca_thumbnail_reference_fixture_gate
    ;;
  orca-3mf-project-preservation)
    run_orca_3mf_project_preservation_gate
    ;;
  orca-3mf-roundtrip-contract)
    run_orca_3mf_roundtrip_contract_gate
    ;;
  orca-3mf-roundtrip-device)
    run_orca_3mf_roundtrip_device_gate "$(device_serial "${2:-}")"
    ;;
  orca-rich-project-fixture)
    run_orca_rich_project_fixture_gate
    ;;
  orca-rich-project-roundtrip-contract)
    run_orca_rich_project_roundtrip_contract_gate
    ;;
  orca-rich-project-roundtrip-device)
    run_orca_rich_project_roundtrip_device_gate "$(device_serial "${2:-}")"
    ;;
  orca-modifier-project-fixture)
    run_orca_modifier_project_fixture_gate
    ;;
  orca-modifier-project-roundtrip-contract)
    run_orca_modifier_project_roundtrip_contract_gate
    ;;
  orca-modifier-project-roundtrip-device)
    run_orca_modifier_project_roundtrip_device_gate "$(device_serial "${2:-}")"
    ;;
  orca-height-range-project-fixture)
    run_orca_height_range_project_fixture_gate
    ;;
  orca-height-range-project-roundtrip-contract)
    run_orca_height_range_project_roundtrip_contract_gate
    ;;
  orca-height-range-project-roundtrip-device)
    run_orca_height_range_project_roundtrip_device_gate "$(device_serial "${2:-}")"
    ;;
  orca-step-sliced-source-fixture)
    run_orca_step_sliced_source_fixture_gate
    ;;
  orca-step-multi-plate-sliced-source-fixture)
    run_orca_step_multi_plate_sliced_source_fixture_gate
    ;;
  orca-project-parity-matrix)
    run_orca_project_parity_matrix_gate
    ;;
  orca-project-parity-device-matrix)
    run_orca_project_parity_device_matrix_gate "$(device_serial "${2:-}")"
    ;;
  orca-active-multifilament-reference-probe)
    run_orca_active_multifilament_reference_probe
    ;;
  orca-step-project-reference-probe)
    run_orca_step_project_reference_probe
    ;;
  orca-fixture-capture-mobile)
    run_orca_fixture_mobile_capture "$(device_serial "${2:-}")"
    ;;
  printer-thumbnail-compatibility)
    run_printer_thumbnail_compatibility_gate
    ;;
  fluidd-thumbnail-metadata)
    run_fluidd_thumbnail_metadata_smoke "$(device_serial "${2:-}")"
    ;;
  orca-metadata-benchmark)
    run_orca_metadata_benchmark "$(device_serial "${2:-}")"
    ;;
  egl-thumbnail-smoke)
    run_egl_thumbnail_smoke "$(device_serial "${2:-}")"
    ;;
  egl-slice-thumbnail-smoke)
    run_egl_slice_thumbnail_smoke "$(device_serial "${2:-}")"
    ;;
  egl-thumbnail-compare)
    run_egl_thumbnail_compare "$(device_serial "${2:-}")"
    ;;
  asset-tests)
    run_asset_generator_tests
    ;;
  apk)
    build_apk
    ;;
  release)
    build_release_apk
    ;;
  local)
    run_script_tests
    run_orca_fixture_gate_strict
    run_orca_gcode_metadata_parity_strict_contract
    run_orca_thumbnail_reference_fixture_gate
    run_orca_3mf_project_preservation_gate
    run_orca_rich_project_fixture_gate
    run_orca_3mf_roundtrip_contract_gate
    run_orca_rich_project_roundtrip_contract_gate
    run_orca_modifier_project_fixture_gate
    run_orca_modifier_project_roundtrip_contract_gate
    run_orca_height_range_project_fixture_gate
    run_orca_height_range_project_roundtrip_contract_gate
    run_printer_thumbnail_compatibility_gate
    run_stub_inventory
    run_asset_generator_tests
    run_lint
    run_unit
    build_apk
    ;;
  install)
    run_install_only "$(device_serial "${2:-}")"
    ;;
  device)
    run_install_only "$(device_serial "${2:-}")"
    ;;
  device-automation)
    run_device_automation_smoke "$(device_serial "${2:-}")"
    ;;
  slice-lifecycle)
    run_slice_lifecycle_regression "$(device_serial "${2:-}")"
    ;;
  slice-regression)
    run_slice_regression_matrix "$(device_serial "${2:-}")"
    ;;
  object-process)
    run_object_process_override_regression "$(device_serial "${2:-}")"
    ;;
  orca-object-label-parity)
    run_orca_object_label_parity_matrix "$(device_serial "${2:-}")"
    ;;
  orca-import-smoke)
    run_orca_import_smoke "$(device_serial "${2:-}")"
    ;;
  sliced-3mf-metadata)
    run_sliced_3mf_metadata_smoke "$(device_serial "${2:-}")"
    ;;
  step-sliced-3mf-source-metadata)
    run_step_sliced_3mf_source_metadata_smoke "$(device_serial "${2:-}")"
    ;;
  step-multi-plate-sliced-3mf-source-metadata)
    run_step_multi_plate_sliced_3mf_source_metadata_smoke "$(device_serial "${2:-}")"
    ;;
  multi-plate-sliced-3mf-metadata)
    run_multi_plate_sliced_3mf_metadata_smoke "$(device_serial "${2:-}")"
    ;;
  skirt-parity)
    run_skirt_parity_matrix "$(device_serial "${2:-}")"
    ;;
  profile-ui)
    run_profile_ui_smoke "$(device_serial "${2:-}")"
    ;;
  responsiveness)
    run_responsiveness_profile "$(device_serial "${2:-}")"
    ;;
  responsiveness-slice)
    run_responsiveness_slice_profile "$(device_serial "${2:-}")"
    ;;
  responsiveness-heavy)
    run_responsiveness_heavy_profile "$(device_serial "${2:-}")"
    ;;
  preview-interaction)
    run_preview_interaction_profile "$(device_serial "${2:-}")"
    ;;
  preview-churn)
    run_preview_interaction_profile "$(device_serial "${2:-}")" "12"
    ;;
  preview-lifecycle)
    run_preview_interaction_profile "$(device_serial "${2:-}")" "0" "${MOBILE_SLICER_PREVIEW_LIFECYCLE_CYCLES:-6}"
    ;;
  perf)
    run_performance_gate "$(device_serial "${2:-}")"
    ;;
  perf-heavy)
    run_performance_gate "$(device_serial "${2:-}")" "1"
    ;;
  benchy)
    [[ $# -ge 2 ]] || {
      usage
      fail "benchy mode requires a local STL path."
    }
    run_benchy "$2" "$(device_serial "${3:-}")"
    ;;
  all)
    run_script_tests
    run_stub_inventory
    run_asset_generator_tests
    run_lint
    run_unit
    build_release_apk
    install_apk "$(device_serial "${2:-}")"
    ;;
  -h|--help|help|"")
    usage
    ;;
  *)
    usage
    fail "unknown mode: $mode"
    ;;
esac
