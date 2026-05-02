#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_SCRIPT="$ROOT_DIR/scripts/verify_android.sh"
DEFAULT_SERIAL="RFCYA01ANVE"
SERIAL="${1:-${ANDROID_SERIAL:-$DEFAULT_SERIAL}}"
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
  4. heavy performance gate with repeat count defaulting to 2
  5. final git status capture

Reports are written under artifacts/release-gates/<timestamp>/.

Environment:
  ANDROID_SERIAL                     Default device serial fallback.
  MOBILE_SLICER_PERF_REPEAT_COUNT    Defaults to 2 for this release gate.
  MOBILE_SLICER_PERF_BASELINE        Passed through to verify_android.sh.
USAGE
}

if [[ "${SERIAL:-}" == "-h" || "${SERIAL:-}" == "--help" ]]; then
  usage
  exit 0
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
run_step "perf-heavy" env \
  MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
  MOBILE_SLICER_PERF_REPEAT_COUNT="${MOBILE_SLICER_PERF_REPEAT_COUNT:-2}" \
  "$VERIFY_SCRIPT" perf-heavy "$SERIAL"
run_step "git-status" git -C "$ROOT_DIR" status --short --branch
