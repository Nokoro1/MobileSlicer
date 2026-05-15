#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="$ROOT_DIR/scripts/verify_android.sh"
SERIAL="${1:-${ANDROID_SERIAL:-}}"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_ROOT="$ROOT_DIR/artifacts/release-gates"
RUN_DIR="$ARTIFACT_ROOT/$STAMP"
SUMMARY_PATH="$RUN_DIR/summary.md"
STATUS_PATH="$RUN_DIR/status.env"
START_EPOCH="$(date +%s)"

mkdir -p "$RUN_DIR/logs"

log() {
  printf '[release_gate_android] %s\n' "$*"
}

fail() {
  printf '[release_gate_android] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage:
  scripts/release_gate_android.sh [serial]

Runs the Android release confidence gate:
  1. local verification
  2. device slice lifecycle
  3. device slice regression
  4. object-scoped process override regression
  5. Orca import smoke for STL, 3MF, and STEP
  6. offscreen EGL thumbnail renderer smoke
  7. full desktop-Orca thumbnail reference matrix
  8. G-code visible metadata parity audit
  9. printer thumbnail compatibility claim audit
  10. sliced 3MF metadata/package parity smoke
  11. Orca 3MF project round-trip preservation gate
  12. Fluidd/Moonraker thumbnail metadata gate
  13. heavy performance gate with repeat count defaulting to 2
  14. final git status capture

Reports are written under artifacts/release-gates/<timestamp>/.

Environment:
  ANDROID_SERIAL                     Default device serial fallback.
  MOBILE_SLICER_PERF_REPEAT_COUNT    Defaults to 2 for this release gate.
  MOBILE_SLICER_PERF_BASELINE        Passed through to verify_android.sh.
  MOBILE_SLICER_METADATA_PNG_THUMBNAIL_MAX_MS
                                     Plain G-code PNG thumbnail threshold.
  MOBILE_SLICER_METADATA_PNG_SMALL_THUMBNAIL_MAX_MS
                                     Small plain G-code PNG thumbnail threshold.
  MOBILE_SLICER_METADATA_QOI_THUMBNAIL_MAX_MS
                                     Plain G-code QOI thumbnail threshold.
  MOBILE_SLICER_METADATA_FLUIDD_THUMBNAIL_MAX_MS
                                     Fluidd/Moonraker two-thumbnail G-code
                                     threshold.
  MOBILE_SLICER_MOONRAKER_URL        Optional live Moonraker host for the
                                     Fluidd thumbnail round-trip gate. When
                                     set, the gate uploads the generated
                                     G-code and requires Moonraker metadata to
                                     report 48x48 and 300x300 thumbnails.
  MOBILE_SLICER_MOONRAKER_API_KEY    Optional Moonraker X-Api-Key header.
  MOBILE_SLICER_MOONRAKER_DELETE_AFTER
                                     Defaults to 1. Set to 0 to keep the
                                     uploaded verification G-code on the host.
  MOBILE_SLICER_FAIL_ORCA_METADATA_DRIFT
                                     Defaults to 0. Keep this disabled until
                                     desktop Orca and MobileSlicer reference
                                     fixtures are generated from exactly
                                     matched profile bundles.
  MOBILE_SLICER_METADATA_3MF_THUMBNAIL_MAX_MS
                                     Sliced 3MF package thumbnail threshold.
  MOBILE_SLICER_METADATA_3MF_WRITE_MAX_MS
                                     Sliced 3MF write threshold.
  MOBILE_SLICER_EGL_THUMBNAIL_TOTAL_MAX_MS
                                     Offscreen EGL smoke total threshold.
  MOBILE_SLICER_EGL_THUMBNAIL_RENDER_MAX_MS
                                     Offscreen EGL smoke draw threshold.
  MOBILE_SLICER_EGL_THUMBNAIL_READ_MAX_MS
                                     Offscreen EGL smoke readback threshold.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_TOTAL_MAX_MS
                                     Offscreen EGL slice-thumbnail total threshold.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_UPLOAD_MAX_MS
                                     Offscreen EGL slice-thumbnail upload threshold.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_DRAW_MAX_MS
                                     Offscreen EGL slice-thumbnail draw threshold.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_READ_MAX_MS
                                     Offscreen EGL slice-thumbnail readback threshold.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_MIN_VISIBLE_PIXELS
                                     Offscreen EGL slice-thumbnail visible-pixel floor.
  MOBILE_SLICER_EGL_SLICE_THUMBNAIL_MIN_BBOX_PX
                                     Offscreen EGL slice-thumbnail bbox size floor.
  MOBILE_SLICER_EGL_COMPARE_TOTAL_MAX_MS
                                     EGL fixture comparison total threshold.
  MOBILE_SLICER_EGL_COMPARE_DRAW_MAX_MS
                                     EGL fixture comparison draw threshold.
  MOBILE_SLICER_EGL_COMPARE_READ_MAX_MS
                                     EGL fixture comparison readback threshold.
  MOBILE_SLICER_EGL_COMPARE_MIN_VISIBLE_PIXELS
                                     EGL fixture comparison visible-pixel floor.
  MOBILE_SLICER_EGL_COMPARE_MIN_BBOX_PX
                                     EGL fixture comparison bbox size floor.
  MOBILE_SLICER_EGL_COMPARE_MIN_DISTINCT_ROLES
                                     EGL fixture comparison role distinctness floor.
  MOBILE_SLICER_ORCA_VISUAL_MAX_LUMA_DELTA
                                     Desktop-Orca visual diff luminance delta.
  MOBILE_SLICER_ORCA_VISUAL_MAX_ALPHA_COVERAGE_DELTA
                                     Desktop-Orca visual diff coverage delta.
  MOBILE_SLICER_ORCA_VISUAL_MAX_BBOX_DELTA_PX
                                     Desktop-Orca visual diff normalized bbox delta.
  MOBILE_SLICER_ORCA_VISUAL_MIN_COVERAGE_RATIO
                                     Desktop-Orca visual diff minimum coverage ratio.
  MOBILE_SLICER_ORCA_VISUAL_MAX_COVERAGE_RATIO
                                     Desktop-Orca visual diff maximum coverage ratio.
  MOBILE_SLICER_RELEASE_MIN_IDLE_CPU Defaults to 650, measured from top's
                                     aggregate idle value on an 8-core device.
  MOBILE_SLICER_RELEASE_IDLE_WAIT_SECONDS
                                     Defaults to 180.
USAGE
}

if [[ "${SERIAL:-}" == "-h" || "${SERIAL:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$SERIAL" ]]; then
  fail "Device serial not provided. Pass a serial argument or set ANDROID_SERIAL."
fi

if [[ ! -x "$VERIFY_SCRIPT" ]]; then
  fail "Verification script is not executable: $VERIFY_SCRIPT"
fi

append_summary_header() {
  {
    printf '# Android Release Gate\n\n'
    printf '%s\n' "- Started: \`$(date -Is)\`"
    printf '%s\n' "- Device serial: \`$SERIAL\`"
    printf '%s\n' "- Git HEAD: \`$(git -C "$ROOT_DIR" rev-parse --short HEAD)\`"
    printf '%s\n' "- Branch: \`$(git -C "$ROOT_DIR" branch --show-current)\`"
    printf '%s\n\n' "- Artifact directory: \`$RUN_DIR\`"
    printf '## Results\n\n'
    printf '| Step | Result | Seconds | Log |\n'
    printf '| --- | --- | ---: | --- |\n'
  } > "$SUMMARY_PATH"
}

append_summary_result() {
  local name="$1"
  local result="$2"
  local seconds="$3"
  local log_path="$4"
  local rel_log="${log_path#$RUN_DIR/}"
  printf '| `%s` | %s | %s | `%s` |\n' "$name" "$result" "$seconds" "$rel_log" >> "$SUMMARY_PATH"
}

write_context_files() {
  git -C "$ROOT_DIR" status --short --branch > "$RUN_DIR/git-status-before.txt"
  git -C "$ROOT_DIR" log --oneline -20 > "$RUN_DIR/git-log-before.txt"
  {
    printf 'serial=%s\n' "$SERIAL"
    printf 'started_at=%s\n' "$(date -Is)"
    printf 'git_head=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD)"
    printf 'git_branch=%s\n' "$(git -C "$ROOT_DIR" branch --show-current)"
    printf 'perf_repeat_count=%s\n' "${MOBILE_SLICER_PERF_REPEAT_COUNT:-2}"
    printf 'perf_baseline=%s\n' "${MOBILE_SLICER_PERF_BASELINE:-default}"
    printf 'min_idle_cpu=%s\n' "${MOBILE_SLICER_RELEASE_MIN_IDLE_CPU:-650}"
    printf 'idle_wait_seconds=%s\n' "${MOBILE_SLICER_RELEASE_IDLE_WAIT_SECONDS:-180}"
  } > "$STATUS_PATH"
}

finish_context_files() {
  local exit_code="$1"
  local end_epoch
  end_epoch="$(date +%s)"
  git -C "$ROOT_DIR" status --short --branch > "$RUN_DIR/git-status-after.txt"
  {
    printf '\n## Final State\n\n'
    printf '%s\n' "- Finished: \`$(date -Is)\`"
    printf '%s\n' "- Exit code: \`$exit_code\`"
    printf '%s\n' "- Total seconds: \`$((end_epoch - START_EPOCH))\`"
    printf '\n### Git Status\n\n'
    printf '```text\n'
    cat "$RUN_DIR/git-status-after.txt"
    printf '```\n'
  } >> "$SUMMARY_PATH"
  {
    printf 'finished_at=%s\n' "$(date -Is)"
    printf 'exit_code=%s\n' "$exit_code"
    printf 'total_seconds=%s\n' "$((end_epoch - START_EPOCH))"
  } >> "$STATUS_PATH"
}

run_step() {
  local name="$1"
  shift
  local log_path="$RUN_DIR/logs/$name.log"
  local step_start
  step_start="$(date +%s)"
  log "Starting $name"
  set +e
  (
    cd "$ROOT_DIR"
    "$@"
  ) 2>&1 | tee "$log_path"
  local command_status="${PIPESTATUS[0]}"
  set -e
  local step_end
  step_end="$(date +%s)"
  local seconds="$((step_end - step_start))"
  if [[ "$command_status" -eq 0 ]]; then
    append_summary_result "$name" "PASS" "$seconds" "$log_path"
    log "Passed $name in ${seconds}s"
  else
    append_summary_result "$name" "FAIL" "$seconds" "$log_path"
    log "Failed $name in ${seconds}s"
    return "$command_status"
  fi
}

sample_device_idle_cpu() {
  adb -s "$SERIAL" shell top -b -n 1 -o PID,USER,%CPU,%MEM,TIME+,ARGS 2>/dev/null |
    awk '/%cpu/ { for (i = 1; i <= NF; ++i) if ($i ~ /%idle$/) { sub(/%idle$/, "", $i); print $i; exit } }'
}

wait_for_device_idle() {
  local min_idle="${MOBILE_SLICER_RELEASE_MIN_IDLE_CPU:-650}"
  local max_wait="${MOBILE_SLICER_RELEASE_IDLE_WAIT_SECONDS:-180}"
  local started_at
  started_at="$(date +%s)"
  local consecutive=0
  while true; do
    local idle now elapsed
    idle="$(sample_device_idle_cpu || true)"
    now="$(date +%s)"
    elapsed="$((now - started_at))"
    if [[ "$idle" =~ ^[0-9]+$ && "$idle" -ge "$min_idle" ]]; then
      consecutive="$((consecutive + 1))"
      log "Device idle sample ${consecutive}/2: ${idle} >= ${min_idle}"
      if [[ "$consecutive" -ge 2 ]]; then
        return 0
      fi
    else
      consecutive=0
      log "Waiting for device idle before perf: idle=${idle:-unknown}, required>=${min_idle}, elapsed=${elapsed}s"
    fi
    if [[ "$elapsed" -ge "$max_wait" ]]; then
      fail "Device did not reach idle CPU threshold before perf within ${max_wait}s."
    fi
    sleep 5
  done
}

on_exit() {
  local exit_code="$?"
  finish_context_files "$exit_code"
  if [[ "$exit_code" -eq 0 ]]; then
    ln -sfn "$RUN_DIR" "$ARTIFACT_ROOT/latest"
    log "Release gate passed. Summary: $SUMMARY_PATH"
  else
    log "Release gate failed. Summary: $SUMMARY_PATH"
  fi
}

trap on_exit EXIT

append_summary_header
write_context_files

log "Writing artifacts to $RUN_DIR"

run_step "local" "$VERIFY_SCRIPT" local
run_step "slice-lifecycle" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" slice-lifecycle "$SERIAL"
run_step "slice-regression" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" slice-regression "$SERIAL"
run_step "object-process" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" object-process "$SERIAL"
run_step "orca-object-label-parity" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" orca-object-label-parity "$SERIAL"
run_step "orca-import-smoke" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" orca-import-smoke "$SERIAL"
run_step "egl-thumbnail-smoke" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" egl-thumbnail-smoke "$SERIAL"
run_step "egl-slice-thumbnail-smoke" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" egl-slice-thumbnail-smoke "$SERIAL"
run_step "orca-thumbnail-reference-fixtures" "$VERIFY_SCRIPT" orca-thumbnail-reference-fixtures
run_step "orca-project-parity-matrix" "$VERIFY_SCRIPT" orca-project-parity-matrix
run_step "orca-thumbnail-reference-matrix" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$ROOT_DIR/scripts/run_orca_thumbnail_reference_matrix.sh" "$SERIAL"
run_step "orca-gcode-metadata-parity" "$VERIFY_SCRIPT" orca-gcode-metadata-parity
run_step "printer-thumbnail-compatibility" "$VERIFY_SCRIPT" printer-thumbnail-compatibility
run_step "sliced-3mf-metadata" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" sliced-3mf-metadata "$SERIAL"
run_step "multi-plate-sliced-3mf-metadata" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" multi-plate-sliced-3mf-metadata "$SERIAL"
run_step "orca-project-parity-device-matrix" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" orca-project-parity-device-matrix "$SERIAL"
run_step "fluidd-thumbnail-metadata" env MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 "$VERIFY_SCRIPT" fluidd-thumbnail-metadata "$SERIAL"
run_step "device-idle-preflight" wait_for_device_idle
run_step "perf-heavy" env \
  MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
  MOBILE_SLICER_PERF_REPEAT_COUNT="${MOBILE_SLICER_PERF_REPEAT_COUNT:-2}" \
  "$VERIFY_SCRIPT" perf-heavy "$SERIAL"
run_step "git-status" git -C "$ROOT_DIR" status --short --branch
