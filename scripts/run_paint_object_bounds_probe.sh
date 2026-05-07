#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/android-app"
LOCAL_MODEL="${PAINT_BOUNDS_PROBE_MODEL_LOCAL:-$ROOT_DIR/engine-wrapper/orca-android-libslic3r/testdata/cube_20mm_ascii.stl}"
DEVICE_MODEL="${PAINT_BOUNDS_PROBE_MODEL_DEVICE:-/data/local/tmp/mobileslicer_paint_bounds_cube.stl}"
DEVICE_PROBE="${PAINT_BOUNDS_PROBE_DEVICE_BIN:-/data/local/tmp/orca_engine_paint_object_bounds_probe}"
WORK_DIR="${PAINT_BOUNDS_PROBE_WORK_DIR:-/tmp/mobileslicer_paint_object_bounds_probe}"
SKIP_BUILD="${PAINT_BOUNDS_PROBE_SKIP_BUILD:-0}"
SERIAL="${PAINT_BOUNDS_PROBE_SERIAL:-${ADB_SERIAL:-}}"

mkdir -p "$WORK_DIR"

log() {
  printf '[paint-bounds-probe] %s\n' "$*"
}

require_file() {
  if [[ ! -f "$1" ]]; then
    printf 'missing required file: %s\n' "$1" >&2
    exit 1
  fi
}

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then
    adb -s "$SERIAL" "$@"
  else
    adb "$@"
  fi
}

find_arm64_artifact() {
  local name="$1"
  find "$APP_DIR/app/build/intermediates/cxx/Debug" -path "*/obj/arm64-v8a/$name" -type f | sort | tail -n 1
}

require_file "$LOCAL_MODEL"

if [[ "$SKIP_BUILD" != "1" ]]; then
  log "building debug APK and native paint bounds probe"
  (cd "$APP_DIR" && ./gradlew --no-daemon :app:assembleDebug)
  PROBE_PATH="$(find_arm64_artifact orca_engine_paint_object_bounds_probe)"
  if [[ -n "$PROBE_PATH" ]]; then
    CMAKE_ID="$(basename "$(dirname "$(dirname "$(dirname "$PROBE_PATH")")")")"
    CMAKE_BUILD_DIR="$APP_DIR/app/.cxx/Debug/$CMAKE_ID/arm64-v8a"
    cmake --build "$CMAKE_BUILD_DIR" --target orca_engine_paint_object_bounds_probe -j2
  fi
fi

PROBE_PATH="$(find_arm64_artifact orca_engine_paint_object_bounds_probe)"
CORE_LIB_PATH="$(find_arm64_artifact liborca_core_android.so)"

require_file "$PROBE_PATH"
require_file "$CORE_LIB_PATH"

log "staging native probe and fixture"
adb_cmd push "$PROBE_PATH" "$DEVICE_PROBE" >/dev/null
adb_cmd shell chmod 755 "$DEVICE_PROBE"
adb_cmd push "$CORE_LIB_PATH" /data/local/tmp/liborca_core_android.so >/dev/null
adb_cmd push "$LOCAL_MODEL" "$DEVICE_MODEL" >/dev/null

OUTPUT="$WORK_DIR/paint-object-bounds-probe.txt"
log "running native paint object bounds probe"
adb_cmd shell "LD_LIBRARY_PATH=/data/local/tmp $DEVICE_PROBE $DEVICE_MODEL" | tee "$OUTPUT"
log "native paint object bounds probe passed"
log "artifacts: $OUTPUT"
