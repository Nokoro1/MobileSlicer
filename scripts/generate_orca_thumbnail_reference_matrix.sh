#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_orca_thumbnail_reference_matrix.sh

Regenerates every checked-in desktop Orca thumbnail visual reference case using
scripts/generate_orca_thumbnail_reference_fixture.sh. The Orca binary is read
from ORCA_SLICER_BIN, or from that script's default.

Optional:
  ORCA_SLICER_BIN
  MOBILE_SLICER_USE_XVFB
  LIBGL_ALWAYS_SOFTWARE
USAGE
}

fail() {
  printf '[orca_thumbnail_reference_matrix_generate] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[orca_thumbnail_reference_matrix_generate] %s\n' "$*"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

mapfile -t CASES < <(python3 "$ROOT_DIR/scripts/orca_thumbnail_reference_fixture_audit.py" --list-shell)
[[ "${#CASES[@]}" -gt 0 ]] || fail "No Orca thumbnail reference matrix cases found."

for entry in "${CASES[@]}"; do
  IFS=$'\t' read -r \
    case_name \
    model_path \
    reference_dir \
    machine_profile \
    process_profile \
    filament_profile \
    printer_name \
    process_name \
    filament_name \
    bed_width \
    bed_depth \
    bed_height \
    _source_layout \
    _source_colors <<<"$entry"
  [[ -n "$case_name" ]] || fail "Invalid empty matrix case entry: $entry"
  [[ -f "$model_path" ]] || fail "$case_name model missing: $model_path"
  [[ -n "$reference_dir" ]] || fail "$case_name reference dir missing from matrix"
  [[ -z "$machine_profile" || -f "$machine_profile" ]] || fail "$case_name machine profile missing: $machine_profile"
  [[ -z "$process_profile" || -f "$process_profile" ]] || fail "$case_name process profile missing: $process_profile"
  [[ -z "$filament_profile" || -f "$filament_profile" ]] || fail "$case_name filament profile missing: $filament_profile"
  log "Generating $case_name"
  MOBILE_SLICER_ORCA_REFERENCE_CASE="$case_name" \
  MOBILE_SLICER_ORCA_REFERENCE_MODEL="$model_path" \
  MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR="$reference_dir" \
  MOBILE_SLICER_ORCA_MACHINE_PROFILE="${machine_profile:-}" \
  MOBILE_SLICER_ORCA_PROCESS_PROFILE="${process_profile:-}" \
  MOBILE_SLICER_ORCA_FILAMENT_PROFILE="${filament_profile:-}" \
  MOBILE_SLICER_ORCA_PRINTER_PROFILE_NAME="${printer_name:-}" \
  MOBILE_SLICER_ORCA_PROCESS_PROFILE_NAME="${process_name:-}" \
  MOBILE_SLICER_ORCA_FILAMENT_PROFILE_NAME="${filament_name:-}" \
    "$ROOT_DIR/scripts/generate_orca_thumbnail_reference_fixture.sh"
done

python3 "$ROOT_DIR/scripts/orca_thumbnail_reference_fixture_audit.py" --pretty >/dev/null
log "All desktop Orca thumbnail reference cases generated."
