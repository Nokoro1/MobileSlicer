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
DEFAULT_SERIAL="RFCYA01ANVE"
DEFAULT_SLICE_SMOKE_STL="$ROOT_DIR/mobileslicer_test_cube.stl"
SUPPORT_SLICE_SMOKE_STL="$ROOT_DIR/proof-fixtures/stage2_bridge_speed_fixture.stl"
PERIMETER_ARRAY_SLICE_SMOKE_STL="$ROOT_DIR/proof-fixtures/stage2_small_perimeter_array_fixture.stl"
MEDIUM_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/volumetric_speed/SpeedTestStructure.stl"
COMPLEX_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/vfa/vfa.stl"
STRESS_SLICE_PERF_STL="$ROOT_DIR/android-app/app/src/main/assets/calib_stl/temperature_tower/temperature_tower.stl"
BENCHY_AUTOMATION_CONFIG='{"bed_width_mm":270,"bed_depth_mm":270,"max_height_mm":256,"nozzle_diameter":0.4000000059604645,"filament_diameter":1.75,"filament_type":"PLA","filament_max_volumetric_speed":50,"nozzle_temperature_initial_layer":210,"nozzle_temperature":210,"bed_temperature_initial_layer":60,"bed_temperature":60,"cooling_baseline":100,"close_fan_the_first_x_layers":1,"layer_height":0.20000000298023224,"first_layer_height":0.20000000298023224,"first_layer_print_speed":10,"first_layer_infill_speed":22.5,"initial_layer_travel_speed_percent":50,"slow_down_layers":0,"outer_wall_speed":30,"inner_wall_speed":30,"top_surface_speed":30,"travel_speed":120,"outer_wall_acceleration":500,"inner_wall_acceleration":10000,"top_surface_acceleration":500,"sparse_infill_acceleration":500,"bridge_speed":10,"small_perimeter_speed":15,"small_perimeter_threshold":0,"sparse_infill_speed":300,"internal_solid_infill_speed":30,"gap_infill_speed":15,"top_shell_layers":4,"bottom_shell_layers":3,"seam_position":"aligned","precise_outer_wall":true,"only_one_wall_top":true,"top_surface_pattern":"monotonicline","sparse_infill_density":15,"sparse_infill_pattern":"grid","wall_loops":2,"print_speed_baseline":60,"skirts":2,"brim_width":0}'
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
AUTOMATION_LAST_PREVIEW_LAYER_COUNT_BYTES=""
AUTOMATION_LAST_EXACT_PREVIEW_CACHE_ELIGIBLE=""
AUTOMATION_LAST_PEAK_PSS_KB=""
AUTOMATION_LAST_PEAK_JAVA_HEAP_KB=""
AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB=""
AUTOMATION_LAST_PEAK_GRAPHICS_KB=""
AUTOMATION_LAST_PEAK_PRIVATE_OTHER_KB=""
AUTOMATION_LAST_PEAK_SYSTEM_KB=""
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
  scripts/verify_android.sh asset-tests
  scripts/verify_android.sh apk
  scripts/verify_android.sh release
  scripts/verify_android.sh local
  scripts/verify_android.sh install [serial]
  scripts/verify_android.sh device [serial]
  scripts/verify_android.sh device-automation [serial]
  scripts/verify_android.sh slice-lifecycle [serial]
  scripts/verify_android.sh slice-regression [serial]
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

Device serial defaults to $ANDROID_SERIAL, then RFCYA01ANVE.
USAGE
}

log() {
  printf '[verify_android] %s\n' "$*"
}

fail() {
  printf '[verify_android] ERROR: %s\n' "$*" >&2
  exit 1
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
    printf '%s\n' "$DEFAULT_SERIAL"
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
  python3 -m py_compile "$ROOT_DIR"/scripts/*.py
  (cd "$ROOT_DIR" && python3 scripts/test_analyze_mobile_performance.py)
  (cd "$ROOT_DIR" && python3 scripts/test_analyze_preview_responsiveness.py)
  (cd "$ROOT_DIR" && python3 scripts/test_gcode_preview_layer_counter.py)
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
}

install_perf_apk() {
  local serial="$1"
  build_perf_apk
  require_device "$serial"
  log "Installing perf debug APK on $serial"
  adb_device "$serial" install -r "$PERF_APK_PATH"
}

launch_app() {
  local serial="$1"
  log "Cold-launching $PACKAGE_NAME on $serial"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
  adb_device "$serial" logcat -c
  adb_device "$serial" shell am start -W -n "$MAIN_ACTIVITY"
  log "Recent app logs"
  adb_device "$serial" logcat -d -v brief | grep -E 'MobileSlicer|AndroidRuntime|FATAL EXCEPTION' || true
}

launch_app_for_perf() {
  local serial="$1"
  log "Cold-launching $PACKAGE_NAME on $serial for performance measurement"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
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
    printf -- '- after_release: %s KB\n\n' "${AUTOMATION_LAST_NATIVE_AFTER_RELEASE_RSS_KB:-unknown}"
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

run_automation_slice() {
  local local_stl="$1"
  local serial="$2"
  local label="$3"
  local should_install="${4:-1}"
  local config_json="${5:-$BENCHY_AUTOMATION_CONFIG}"
  local collect_perf="${6:-0}"
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

  log "Running automation slice '$label' on $serial"
  adb_device "$serial" shell run-as "$PACKAGE_NAME" rm -f "$output_path" "$status_path"
  adb_device "$serial" shell am force-stop "$PACKAGE_NAME"
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
  if [[ "$collect_perf" == "1" ]]; then
    local status_tmp
    status_tmp="$(mktemp)"
    wait_for_status_with_memory "$serial" "$status_path" > "$status_tmp"
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
  [[ "$output_bytes" =~ ^[0-9]+$ ]] || fail "Unable to read automation G-code byte count."
  [[ "$output_bytes" -gt 1024 ]] || fail "Automation G-code output is unexpectedly small: $output_bytes bytes."
  log "Automation G-code output verified: $output_bytes bytes"
  AUTOMATION_LAST_OUTPUT_PATH="$output_path"
  AUTOMATION_LAST_STATUS="$status"
  AUTOMATION_LAST_BYTES="$output_bytes"
  AUTOMATION_LAST_STAGING_MS="$(status_metric "$status" "stagingMs")"
  AUTOMATION_LAST_NATIVE_LOAD_MS="$(status_metric "$status" "nativeLoadMs")"
  AUTOMATION_LAST_ELAPSED_MS="$(status_metric "$status" "elapsedMs")"
  AUTOMATION_LAST_PLACEMENT_MS="$(status_metric "$status" "placementMs")"
  AUTOMATION_LAST_CONFIG_MS="$(status_metric "$status" "configMs")"
  AUTOMATION_LAST_NATIVE_SLICE_MS="$(status_metric "$status" "nativeSliceMs")"
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
  AUTOMATION_LAST_PREVIEW_LAYER_COUNT_BYTES="$(status_metric "$status" "previewLayerCountBytes")"
  AUTOMATION_LAST_EXACT_PREVIEW_CACHE_ELIGIBLE="$(status_metric "$status" "exactPreviewCacheEligible")"
  AUTOMATION_LAST_PEAK_PSS_KB="$PERF_LAST_PEAK_PSS_KB"
  AUTOMATION_LAST_PEAK_JAVA_HEAP_KB="$PERF_LAST_PEAK_JAVA_HEAP_KB"
  AUTOMATION_LAST_PEAK_NATIVE_HEAP_KB="$PERF_LAST_PEAK_NATIVE_HEAP_KB"
  AUTOMATION_LAST_PEAK_GRAPHICS_KB="$PERF_LAST_PEAK_GRAPHICS_KB"
  AUTOMATION_LAST_PEAK_PRIVATE_OTHER_KB="$PERF_LAST_PEAK_PRIVATE_OTHER_KB"
  AUTOMATION_LAST_PEAK_SYSTEM_KB="$PERF_LAST_PEAK_SYSTEM_KB"
  clear_current_automation_context
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
  python3 - "$records_path" "$name" "$type" "$startup_ms" "$staging_ms" "$native_load_ms" "$placement_ms" "$config_ms" "$native_slice_ms" "$write_gcode_ms" "$elapsed_ms" "$preview_moves" "$preview_cache_built" "$preview_cache_complete" "$preview_cached_vertices" "$preview_cache_build_ms" "$preview_plan_ms" "$preview_load_ms" "$preview_ranges" "$preview_loaded_layers" "$preview_load_success" "$preview_load_gl_unavailable" "$peak_pss_kb" "$peak_java_heap_kb" "$peak_native_heap_kb" "$peak_graphics_kb" "$peak_private_other_kb" "$peak_system_kb" "$bytes" "$fixture_bytes" "$device_output_path" <<'PY'
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
    "$AUTOMATION_LAST_OUTPUT_PATH"
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
    local startup_output startup_ms startup_meminfo startup_pss_kb startup_java_heap_kb startup_native_heap_kb startup_graphics_kb startup_private_other_kb startup_system_kb
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
      ""
    assert_no_crash_after_launch "$serial"
  fi

  local default_config support_config perimeter_config medium_config complex_config stress_config
  default_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  support_config="$(automation_config_with_overrides brim_width=0 enable_support=true support_type=normal\(auto\) support_style=default support_threshold_angle=10 support_on_build_plate_only=false)"
  perimeter_config="$(automation_config_with_overrides brim_width=0 wall_loops=3 sparse_infill_density=20 enable_support=false small_perimeter_speed=20)"
  medium_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  complex_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=15 enable_support=false)"
  stress_config="$(automation_config_with_overrides brim_width=0 wall_loops=2 sparse_infill_density=10 enable_support=false)"

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
        if raw.startswith(";TYPE:"):
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
