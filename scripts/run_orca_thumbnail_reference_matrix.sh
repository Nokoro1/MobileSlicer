#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage:
  scripts/run_orca_thumbnail_reference_matrix.sh [serial]

Runs the strict Android EGL thumbnail visual comparison against every checked-in
desktop Orca thumbnail reference case. Each case pairs its source STL with the
matching reference directory.

Requires:
  MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1

Optional:
  MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_WIDTH
  MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_HEIGHT
  MOBILE_SLICER_ORCA_VISUAL_MAX_LUMA_DELTA
  MOBILE_SLICER_ORCA_VISUAL_MAX_ALPHA_COVERAGE_DELTA
  MOBILE_SLICER_ORCA_VISUAL_MAX_BBOX_DELTA_PX
  MOBILE_SLICER_ORCA_VISUAL_MIN_COVERAGE_RATIO
  MOBILE_SLICER_ORCA_VISUAL_MAX_COVERAGE_RATIO
  MOBILE_SLICER_EGL_COMPARE_BED_WIDTH_MM
  MOBILE_SLICER_EGL_COMPARE_BED_DEPTH_MM
  MOBILE_SLICER_EGL_COMPARE_BED_HEIGHT_MM
  MOBILE_SLICER_EGL_COMPARE_SOURCE_LAYOUT
  MOBILE_SLICER_EGL_COMPARE_SOURCE_COLORS
USAGE
}

fail() {
  printf '[orca_thumbnail_reference_matrix] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[orca_thumbnail_reference_matrix] %s\n' "$*"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

python3 "$ROOT_DIR/scripts/orca_thumbnail_reference_fixture_audit.py" --pretty >/dev/null

SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  mapfile -t connected_devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#connected_devices[@]}" -eq 1 ]]; then
    SERIAL="${connected_devices[0]}"
  elif [[ "${#connected_devices[@]}" -eq 0 ]]; then
    fail "No connected ADB device. Reconnect wireless debugging and rerun this command."
  else
    fail "Multiple ADB devices connected; pass the target serial explicitly."
  fi
fi
[[ "${MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION:-0}" == "1" ]] ||
  fail "Set MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 to run device automation."

export MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_WIDTH="${MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_WIDTH:-512}"
export MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_HEIGHT="${MOBILE_SLICER_EGL_COMPARE_THUMBNAIL_HEIGHT:-512}"
export MOBILE_SLICER_ORCA_VISUAL_MAX_LUMA_DELTA="${MOBILE_SLICER_ORCA_VISUAL_MAX_LUMA_DELTA:-70.0}"
export MOBILE_SLICER_ORCA_VISUAL_MAX_ALPHA_COVERAGE_DELTA="${MOBILE_SLICER_ORCA_VISUAL_MAX_ALPHA_COVERAGE_DELTA:-0.08}"
export MOBILE_SLICER_ORCA_VISUAL_MAX_BBOX_DELTA_PX="${MOBILE_SLICER_ORCA_VISUAL_MAX_BBOX_DELTA_PX:-64}"
export MOBILE_SLICER_ORCA_VISUAL_MIN_COVERAGE_RATIO="${MOBILE_SLICER_ORCA_VISUAL_MIN_COVERAGE_RATIO:-0.90}"
export MOBILE_SLICER_ORCA_VISUAL_MAX_COVERAGE_RATIO="${MOBILE_SLICER_ORCA_VISUAL_MAX_COVERAGE_RATIO:-1.15}"

mapfile -t CASES < <(python3 "$ROOT_DIR/scripts/orca_thumbnail_reference_fixture_audit.py" --list-shell)
[[ "${#CASES[@]}" -gt 0 ]] || fail "No Orca thumbnail reference matrix cases found."

for entry in "${CASES[@]}"; do
  # Bash treats tab in IFS as whitespace and collapses consecutive delimiters,
  # which loses intentionally-empty optional profile columns. Convert the TSV
  # row to a non-whitespace separator before parsing so bed/source metadata
  # cannot shift into the wrong fields.
  entry_fields="${entry//$'\t'/$'\x1f'}"
  IFS=$'\x1f' read -r \
    case_name \
    model_path \
    reference_dir \
    _machine_profile \
    _process_profile \
    _filament_profile \
    _printer_name \
    _process_name \
    _filament_name \
    bed_width \
    bed_depth \
    bed_height \
    source_layout \
    source_colors <<<"$entry_fields"
  [[ -f "$model_path" ]] || fail "$case_name model missing: $model_path"
  [[ -d "$reference_dir" ]] || fail "$case_name reference missing: $reference_dir"
  compare_bed_width="${bed_width:-270}"
  compare_bed_depth="${bed_depth:-270}"
  compare_bed_height="${bed_height:-256}"
  log "Running $case_name"
  MOBILE_SLICER_EGL_COMPARE_MODEL="$model_path" \
  MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR="$reference_dir" \
  MOBILE_SLICER_EGL_COMPARE_BED_WIDTH_MM="$compare_bed_width" \
  MOBILE_SLICER_EGL_COMPARE_BED_DEPTH_MM="$compare_bed_depth" \
  MOBILE_SLICER_EGL_COMPARE_BED_HEIGHT_MM="$compare_bed_height" \
  MOBILE_SLICER_EGL_COMPARE_SOURCE_LAYOUT="${source_layout:-single}" \
  MOBILE_SLICER_EGL_COMPARE_SOURCE_COLORS="${source_colors:-}" \
  MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 \
  MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 \
    "$ROOT_DIR/scripts/verify_android.sh" egl-thumbnail-compare "$SERIAL"
done

log "All Orca thumbnail reference cases passed."
