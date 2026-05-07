#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/android-app"
PACKAGE_NAME="${PAINT_PROOF_PACKAGE_NAME:-com.mobileslicer}"
DEVICE_PAYLOAD_DIR="${PAINT_PROOF_DEVICE_PAYLOAD_DIR:-files/paint-proof}"
WORK_DIR="${PAINT_PROOF_WORK_DIR:-/tmp/mobileslicer_paint_unified_app_path_proof}"
LOCAL_MODEL="${PAINT_PROOF_MODEL_LOCAL:-$ROOT_DIR/engine-wrapper/orca-android-libslic3r/testdata/paint_support_overhang_ascii.stl}"
DEVICE_MODEL="${PAINT_PROOF_MODEL_DEVICE:-/data/local/tmp/paint_full_app_path_fixture.stl}"
DEVICE_CONFIG="${PAINT_PROOF_CONFIG_DEVICE:-/data/local/tmp/mobileslicer_paint_support_probe_config.json}"
LOCAL_CONFIG="${PAINT_PROOF_CONFIG_LOCAL:-$ROOT_DIR/engine-wrapper/orca-android-libslic3r/testdata/paint_support_probe_config.json}"
RUN_UI_AUTOMATION="${PAINT_PROOF_RUN_UI_AUTOMATION:-1}"
SKIP_BUILD="${PAINT_PROOF_SKIP_BUILD:-0}"

mkdir -p "$WORK_DIR"
rm -f "$WORK_DIR"/*_ui_payload.json \
  "$WORK_DIR"/*_app_replay_payload.json \
  "$WORK_DIR"/*_external_replay.txt \
  "$WORK_DIR"/ui-status.txt

log() {
  printf '[paint-proof] %s\n' "$*"
}

require_file() {
  if [[ ! -f "$1" ]]; then
    printf 'missing required file: %s\n' "$1" >&2
    exit 1
  fi
}

require_file "$LOCAL_MODEL"
require_file "$LOCAL_CONFIG"

if [[ "$SKIP_BUILD" != "1" ]]; then
  log "building debug APK and native probe"
  (cd "$APP_DIR" && ./gradlew :app:assembleDebug)
  PROBE_PATH="$(find "$APP_DIR/app/build/intermediates/cxx/Debug" -path '*/obj/arm64-v8a/orca_engine_v2_paint_replay_probe' -type f | sort | tail -n 1)"
  if [[ -n "$PROBE_PATH" ]]; then
    CMAKE_ID="$(basename "$(dirname "$(dirname "$(dirname "$PROBE_PATH")")")")"
    CMAKE_BUILD_DIR="$APP_DIR/app/.cxx/Debug/$CMAKE_ID/arm64-v8a"
    cmake --build "$CMAKE_BUILD_DIR" --target orca_engine_v2_paint_replay_probe -j2
  fi
fi

PROBE_PATH="$(find "$APP_DIR/app/build/intermediates/cxx/Debug" -path '*/obj/arm64-v8a/orca_engine_v2_paint_replay_probe' -type f | sort | tail -n 1)"
CORE_LIB_PATH="$(find "$APP_DIR/app/build/intermediates/cxx/Debug" -path '*/obj/arm64-v8a/liborca_core_android.so' -type f | sort | tail -n 1)"
APK_PATH="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"

require_file "$PROBE_PATH"
require_file "$CORE_LIB_PATH"
require_file "$APK_PATH"

log "staging native probe and fixtures"
adb push "$PROBE_PATH" /data/local/tmp/orca_engine_v2_paint_replay_probe >/dev/null
adb shell chmod 755 /data/local/tmp/orca_engine_v2_paint_replay_probe
adb push "$CORE_LIB_PATH" /data/local/tmp/liborca_core_android.so >/dev/null
adb push "$LOCAL_MODEL" "$DEVICE_MODEL" >/dev/null
adb push "$LOCAL_CONFIG" "$DEVICE_CONFIG" >/dev/null

if [[ "$RUN_UI_AUTOMATION" == "1" ]]; then
  log "running clean-install UI paint automation to create real UI payload artifacts"
  adb shell am force-stop "$PACKAGE_NAME" >/dev/null 2>&1 || true
  adb uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
  adb install -r "$APK_PATH" >/dev/null
  adb shell "run-as $PACKAGE_NAME mkdir -p $DEVICE_PAYLOAD_DIR"
  adb shell "run-as $PACKAGE_NAME cp $DEVICE_MODEL $DEVICE_PAYLOAD_DIR/paint_full_app_path_fixture.stl"
  adb shell "run-as $PACKAGE_NAME rm -f $DEVICE_PAYLOAD_DIR/status.txt $DEVICE_PAYLOAD_DIR/paint-payload-*.json"
  adb shell am start -W -f 0x10008000 \
    -n "$PACKAGE_NAME/.MainActivity" \
    -a com.mobileslicer.action.PAINT_INTERACTION_PROOF \
    --es paint_proof_model_path "/data/user/0/$PACKAGE_NAME/$DEVICE_PAYLOAD_DIR/paint_full_app_path_fixture.stl" \
    --es paint_proof_status_path "/data/user/0/$PACKAGE_NAME/$DEVICE_PAYLOAD_DIR/status.txt" >/dev/null
  for _ in $(seq 1 120); do
    if adb shell "run-as $PACKAGE_NAME test -f $DEVICE_PAYLOAD_DIR/status.txt" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  adb shell "run-as $PACKAGE_NAME cat $DEVICE_PAYLOAD_DIR/status.txt" | tee "$WORK_DIR/ui-status.txt"
  if ! grep -q '^success:' "$WORK_DIR/ui-status.txt"; then
    printf 'UI paint automation did not report success. See %s\n' "$WORK_DIR/ui-status.txt" >&2
    exit 1
  fi
fi

pull_payload() {
  local remote_name="$1"
  local local_path="$2"
  if ! adb exec-out "run-as $PACKAGE_NAME cat $DEVICE_PAYLOAD_DIR/$remote_name" > "$local_path"; then
    printf 'failed to pull UI payload %s from %s\n' "$remote_name" "$DEVICE_PAYLOAD_DIR" >&2
    exit 1
  fi
  if [[ ! -s "$local_path" ]]; then
    printf 'pulled UI payload is empty: %s\n' "$local_path" >&2
    exit 1
  fi
}

run_mode_proof() {
  local label="$1"
  local paint_mode="$2"
  local probe_mode="$3"
  local payload_name="$4"
  local ui_payload="$WORK_DIR/${label}_ui_payload.json"
  local app_replay_payload="$WORK_DIR/${label}_app_replay_payload.json"
  local device_replay_payload="/data/local/tmp/${label}_app_replay_payload.json"
  local output="$WORK_DIR/${label}_external_replay.txt"

  log "proving $label with UI payload $payload_name"
  pull_payload "$payload_name" "$ui_payload"
  (
    cd "$APP_DIR"
    PAINT_PROOF_PAYLOAD_PATH="$ui_payload" \
    PAINT_PROOF_REPLAY_OUTPUT_PATH="$app_replay_payload" \
    PAINT_PROOF_MODE="$paint_mode" \
    PAINT_PROOF_MODEL_PATH="$DEVICE_MODEL" \
    ./gradlew --no-build-cache :app:testDebugUnitTest --rerun-tasks \
      -x :app:generateOrcaFilamentAssets \
      -x :app:generateOrcaPrinterAssets \
      -x :app:generateOrcaSettingMetadata \
      --tests 'com.mobileslicer.storage.PaintFullAppPathProofTest.nativePayloadSurvivesSavedProjectAndWritesReplayPayloadForNativeProof'
  )
  adb push "$app_replay_payload" "$device_replay_payload" >/dev/null
  adb shell "LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/orca_engine_v2_paint_replay_probe $DEVICE_MODEL $DEVICE_CONFIG $probe_mode $device_replay_payload" | tee "$output"
  grep -q 'result=v2_replay_changed_slice_output' "$output"
}

run_mode_proof support Support support paint-payload-mode-support-circle.json
run_mode_proof seam Seam seam paint-payload-mode-seam-circle.json
run_mode_proof color Color color paint-payload-mode-color-circle.json
run_mode_proof fuzzy FuzzySkin fuzzy paint-payload-mode-fuzzy-circle.json
run_mode_proof support_fill Support support paint-payload-mode-support-fill.json
run_mode_proof color_fill Color color paint-payload-mode-color-fill.json
run_mode_proof fuzzy_fill FuzzySkin fuzzy paint-payload-mode-fuzzy-fill.json

log "running focused mutation/cache/persistence regression tests"
(cd "$APP_DIR" && ./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.ModelLoaderPlateActionsTest' \
  --tests 'com.mobileslicer.workspace.PlatePaintAnnotationsTest' \
  --tests 'com.mobileslicer.storage.SavedProjectRepositoryTest' \
  --tests 'com.mobileslicer.NativeSliceConfigTest')

log "full UI-created payload app-path proof passed for support, seam, color, fuzzy, support_fill, color_fill, and fuzzy_fill"
log "artifacts: $WORK_DIR"
