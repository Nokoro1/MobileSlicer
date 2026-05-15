#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_ORCA_BIN="$ROOT_DIR/vendor/orcaslicer/build/package/bin/orca-slicer"
ORCA_BIN="${ORCA_SLICER_BIN:-$DEFAULT_ORCA_BIN}"
STEP_MODEL="${MOBILE_SLICER_ORCA_STEP_PROJECT_MODEL:-$ROOT_DIR/regression-fixtures/import/occt_screw.step}"
ARTIFACT_ROOT="${MOBILE_SLICER_ORCA_STEP_REFERENCE_PROBE_ARTIFACT_ROOT:-$ROOT_DIR/artifacts/orca-project-fixtures}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ARTIFACT_ROOT/step-reference-probe-$STAMP"
ORCA_LIB_SHIM="$ROOT_DIR/tools/orcaslicer/lib-shim/usr/lib64:$ROOT_DIR/tools/orcaslicer/lib-shim"

usage() {
  cat <<'USAGE'
Usage:
  scripts/probe_orca_step_project_reference.sh

Probes whether the local desktop Orca CLI can generate a project/package
reference directly from a STEP/STP input. This is intentionally a limitation
gate, not a fixture generator: the current vendored Orca CLI rejects STEP both
as a positional input and as an assemble-list object, while MobileSlicer's
Android app supports STEP through its OCCT-backed conversion path.

Environment:
  ORCA_SLICER_BIN
      Orca binary to run. Defaults to vendor/orcaslicer/build/package/bin/orca-slicer.
  MOBILE_SLICER_ORCA_STEP_PROJECT_MODEL
      STEP/STP model to probe. Defaults to regression-fixtures/import/occt_screw.step.
  MOBILE_SLICER_ORCA_STEP_REFERENCE_PROBE_ARTIFACT_ROOT
      Artifact root for probe logs.
USAGE
}

log() {
  printf '[probe_orca_step_project_reference] %s\n' "$*"
}

fail() {
  printf '[probe_orca_step_project_reference] ERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -x "$ORCA_BIN" ]] || fail "Orca binary is not executable: $ORCA_BIN"
[[ -f "$STEP_MODEL" ]] || fail "STEP model missing: $STEP_MODEL"
case "${STEP_MODEL,,}" in
  *.step|*.stp) ;;
  *) fail "Probe model must end in .step or .stp: $STEP_MODEL" ;;
esac

mkdir -p "$RUN_DIR/direct" "$RUN_DIR/assemble"
ORCA_ENV=(
  env
  -u WAYLAND_DISPLAY
  -u EGL_PLATFORM
  -u OBS_VKCAPTURE
  -u OBS_VKCAPTURE_QUIET
  -u VK_INSTANCE_LAYERS
  -u QT_WAYLAND_RECONNECT
  -u DRI_PRIME
  -u MANGOHUD
  "LD_LIBRARY_PATH=$ORCA_LIB_SHIM${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  "XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
  "XDG_SESSION_TYPE=x11"
  "GLFW_PLATFORM=x11"
  "GDK_BACKEND=x11"
  "QT_QPA_PLATFORM=xcb"
)

run_expected_failure() {
  local label="$1"
  local log_path="$2"
  shift 2
  set +e
  "${ORCA_ENV[@]}" "$ORCA_BIN" "$@" >"$log_path" 2>&1
  local status="$?"
  set -e
  if [[ "$status" -eq 0 ]]; then
    fail "$label unexpectedly succeeded. A real STEP reference fixture should be generated instead."
  fi
  if ! grep -Eiq 'unsupported file|Unknown file format|Input file must have' "$log_path"; then
    tail -80 "$log_path" >&2 || true
    fail "$label failed for an unexpected reason; log: $log_path"
  fi
  log "$label rejected STEP as expected with status $status"
}

cat > "$RUN_DIR/assemble/assemble-list.json" <<EOF
{
  "plates": [
    {
      "plate_name": "STEP probe plate",
      "need_arrange": false,
      "objects": [
        {
          "path": "$STEP_MODEL",
          "count": 1,
          "filaments": [1],
          "pos_x": [135],
          "pos_y": [135],
          "pos_z": [0],
          "assemble_index": [0]
        }
      ]
    }
  ]
}
EOF

run_expected_failure \
  "direct STEP input" \
  "$RUN_DIR/direct/orca.log" \
  --outputdir "$RUN_DIR/direct" \
  --export-3mf step-direct-probe.3mf \
  "$STEP_MODEL"

run_expected_failure \
  "assemble-list STEP input" \
  "$RUN_DIR/assemble/orca.log" \
  --outputdir "$RUN_DIR/assemble" \
  --export-3mf step-assemble-probe.3mf \
  --load-assemble-list "$RUN_DIR/assemble/assemble-list.json"

cat > "$RUN_DIR/report.json" <<EOF
{
  "ok": true,
  "orcaBinary": "$ORCA_BIN",
  "stepModel": "$STEP_MODEL",
  "verdict": "desktop_orca_cli_step_reference_not_available",
  "directLog": "$RUN_DIR/direct/orca.log",
  "assembleLog": "$RUN_DIR/assemble/orca.log"
}
EOF

log "Probe report: $RUN_DIR/report.json"
