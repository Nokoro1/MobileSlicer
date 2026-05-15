#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_ORCA_APPIMAGE="$ROOT_DIR/tools/orcaslicer/OrcaSlicer_Linux_AppImage_Ubuntu2404_V2.3.2.AppImage"
ORCA_SLICER_BIN="${ORCA_SLICER_BIN:-$DEFAULT_ORCA_APPIMAGE}"
ORCA_LIB_SHIM="$ROOT_DIR/tools/orcaslicer/lib-shim/usr/lib64:$ROOT_DIR/tools/orcaslicer/lib-shim"
MODEL="${MOBILE_SLICER_ORCA_REFERENCE_MODEL:-$ROOT_DIR/regression-fixtures/slicing/mobileslicer_test_cube.stl}"
REFERENCE_CASE="${MOBILE_SLICER_ORCA_REFERENCE_CASE:-simple-cube}"
DEFAULT_MACHINE_PROFILE="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/machine/Qidi Q2 0.4 nozzle.json"
DEFAULT_PROCESS_PROFILE="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/process/0.20mm Standard @Qidi Q2.json"
DEFAULT_FILAMENT_PROFILE="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/filament/Q2/Generic PLA @Qidi Q2 0.4 nozzle.json"
MACHINE_SOURCE_PROFILE="${MOBILE_SLICER_ORCA_MACHINE_PROFILE:-$DEFAULT_MACHINE_PROFILE}"
PROCESS_PROFILE="${MOBILE_SLICER_ORCA_PROCESS_PROFILE:-$DEFAULT_PROCESS_PROFILE}"
FILAMENT_PROFILE="${MOBILE_SLICER_ORCA_FILAMENT_PROFILE:-$DEFAULT_FILAMENT_PROFILE}"
PRINTER_PROFILE_NAME="${MOBILE_SLICER_ORCA_PRINTER_PROFILE_NAME:-Qidi Q2 0.4 nozzle}"
PROCESS_PROFILE_NAME="${MOBILE_SLICER_ORCA_PROCESS_PROFILE_NAME:-0.20mm Standard @Qidi Q2}"
FILAMENT_PROFILE_NAME="${MOBILE_SLICER_ORCA_FILAMENT_PROFILE_NAME:-Generic PLA @Qidi Q2 0.4 nozzle}"
REFERENCE_DIR="${MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR:-$ROOT_DIR/regression-fixtures/orca-thumbnail-references/$REFERENCE_CASE}"
ARTIFACT_ROOT="$ROOT_DIR/artifacts/orca-thumbnail-reference-capture"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ARTIFACT_ROOT/$STAMP"
WORK_DIR="$RUN_DIR/work"
OUTPUT_DIR="$RUN_DIR/output"
PACKAGE_PATH="$OUTPUT_DIR/$REFERENCE_CASE.gcode.3mf"

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_orca_thumbnail_reference_fixture.sh

Generates the simple-cube desktop Orca thumbnail visual reference fixture from
a GUI/OpenGL-capable OrcaSlicer run. This is intentionally separate from
generate_orca_reference_fixtures.sh because CLI metadata references are not
enough for thumbnail visual parity.

Environment:
  ORCA_SLICER_BIN
      OrcaSlicer binary/AppImage to run.
  MOBILE_SLICER_USE_XVFB=1
      Run Orca under xvfb-run when xvfb-run is installed.
  MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR
      Output reference directory. Defaults to
      regression-fixtures/orca-thumbnail-references/simple-cube.
  MOBILE_SLICER_ORCA_REFERENCE_MODEL
      Model used for the reference. Defaults to the simple cube fixture.
  MOBILE_SLICER_ORCA_REFERENCE_CASE
      Stable fixture case name. Defaults to simple-cube. Used for output
      package and reference directory names.
  MOBILE_SLICER_ORCA_REFERENCE_SOURCE_LAYOUT
      Source layout recorded in the extracted reference manifest. Defaults to
      single. Use two_filament_objects only when the desktop package proves
      active multi-filament metadata.
  MOBILE_SLICER_ORCA_REFERENCE_SOURCE_COLORS
      Optional comma-separated source colors used by the paired MobileSlicer
      matrix case.
  MOBILE_SLICER_ORCA_REQUIRE_ACTIVE_FILAMENTS
      Optional active-filament proof count. Set to 2 for multi-filament
      references; extraction fails unless plate JSON, slice_info.config, and
      model_settings.config all prove at least two active filaments.

This script fails if the exported .gcode.3mf does not contain:
  Metadata/plate_1.png
  Metadata/plate_no_light_1.png
  Metadata/top_1.png
  Metadata/pick_1.png
USAGE
}

fail() {
  printf '[generate_orca_thumbnail_reference_fixture] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[generate_orca_thumbnail_reference_fixture] %s\n' "$*"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -x "$ORCA_SLICER_BIN" ]] || fail "Orca binary is not executable: $ORCA_SLICER_BIN"
[[ -f "$MODEL" ]] || fail "Reference model missing: $MODEL"
[[ -f "$MACHINE_SOURCE_PROFILE" ]] || fail "Machine profile missing: $MACHINE_SOURCE_PROFILE"
[[ -f "$PROCESS_PROFILE" ]] || fail "Process profile missing: $PROCESS_PROFILE"
[[ -f "$FILAMENT_PROFILE" ]] || fail "Filament profile missing: $FILAMENT_PROFILE"

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

RUNNER=()
if [[ "${MOBILE_SLICER_USE_XVFB:-0}" == "1" ]]; then
  if command -v xvfb-run >/dev/null 2>&1; then
    RUNNER=(xvfb-run -a -s "-screen 0 1280x1024x24")
  else
    fail "MOBILE_SLICER_USE_XVFB=1 but xvfb-run is not installed."
  fi
fi

ORCA_LD_LIBRARY_PATH="$ORCA_LIB_SHIM${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
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
  "LD_LIBRARY_PATH=$ORCA_LD_LIBRARY_PATH"
  "XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
  "XDG_SESSION_TYPE=x11"
  "GLFW_PLATFORM=x11"
  "GDK_BACKEND=x11"
  "QT_QPA_PLATFORM=xcb"
)
if [[ -n "${LIBGL_ALWAYS_SOFTWARE:-}" ]]; then
  ORCA_ENV+=("LIBGL_ALWAYS_SOFTWARE=$LIBGL_ALWAYS_SOFTWARE")
elif [[ "${MOBILE_SLICER_USE_XVFB:-0}" == "1" ]]; then
  ORCA_ENV+=("LIBGL_ALWAYS_SOFTWARE=1")
fi

MACHINE_PROFILE="$WORK_DIR/$REFERENCE_CASE-thumbnail-machine.json"
python3 - "$MACHINE_SOURCE_PROFILE" "$MACHINE_PROFILE" <<'PY'
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
data = json.loads(source.read_text(encoding="utf-8"))
data.pop("thumbnail_size", None)
data["thumbnails"] = "128x128/PNG"
data["thumbnails_format"] = "PNG"
target.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

log "Artifacts: $RUN_DIR"
log "Orca binary: $ORCA_SLICER_BIN"
log "Reference case: $REFERENCE_CASE"
log "Reference model: $MODEL"
log "Machine profile: $MACHINE_SOURCE_PROFILE"
log "Process profile: $PROCESS_PROFILE"
log "Filament profile: $FILAMENT_PROFILE"
log "Reference output: $REFERENCE_DIR"

set +e
"${ORCA_ENV[@]}" "${RUNNER[@]}" "$ORCA_SLICER_BIN" \
  --slice 0 \
  --outputdir "$OUTPUT_DIR" \
  --export-3mf "$(basename "$PACKAGE_PATH")" \
  --load-settings "$MACHINE_PROFILE;$PROCESS_PROFILE" \
  --load-filaments "$FILAMENT_PROFILE" \
  "$MODEL" > "$RUN_DIR/orca.log" 2>&1
ORCA_STATUS="$?"
set -e

find "$RUN_DIR" -maxdepth 3 -type f -printf '%p %s\n' | sort > "$RUN_DIR/files.txt"

if [[ "$ORCA_STATUS" -ne 0 ]]; then
  sed -n '1,220p' "$RUN_DIR/orca.log" >&2 || true
  fail "Orca exited with status $ORCA_STATUS. See $RUN_DIR"
fi
if [[ ! -f "$PACKAGE_PATH" ]]; then
  sed -n '1,220p' "$RUN_DIR/orca.log" >&2 || true
  fail "Orca did not produce $PACKAGE_PATH. See $RUN_DIR"
fi

set +e
python3 - "$PACKAGE_PATH" "$RUN_DIR/package-audit.json" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

package = Path(sys.argv[1])
audit_path = Path(sys.argv[2])
required = [
    "Metadata/plate_1.png",
    "Metadata/plate_no_light_1.png",
    "Metadata/top_1.png",
    "Metadata/pick_1.png",
]
with zipfile.ZipFile(package) as archive:
    names = sorted(archive.namelist())
missing = [entry for entry in required if entry not in names]
payload = {
    "package": str(package),
    "entries": names,
    "missing_thumbnail_entries": missing,
}
audit_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
if missing:
    raise SystemExit("missing required desktop Orca thumbnail entries: " + ", ".join(missing))
PY
PACKAGE_AUDIT_STATUS="$?"
set -e
if [[ "$PACKAGE_AUDIT_STATUS" -ne 0 ]]; then
  sed -n '1,220p' "$RUN_DIR/orca.log" >&2 || true
  printf '\nPackage audit:\n' >&2
  cat "$RUN_DIR/package-audit.json" >&2 || true
  fail "Desktop Orca package did not contain required thumbnail PNGs. This usually means the Orca run could not initialize OpenGL/display support. See $RUN_DIR"
fi

ORCA_BUILD="$("${ORCA_ENV[@]}" "$ORCA_SLICER_BIN" --help 2>/dev/null | head -n 1 || printf 'OrcaSlicer unknown')"

python3 "$ROOT_DIR/scripts/extract_orca_thumbnail_references.py" \
  --source-3mf "$PACKAGE_PATH" \
  --output-dir "$REFERENCE_DIR" \
  --orca-build "$ORCA_BUILD" \
  --model "$MODEL" \
  --printer-profile "$PRINTER_PROFILE_NAME" \
  --filament-profile "$FILAMENT_PROFILE_NAME" \
  --process-profile "$PROCESS_PROFILE_NAME" \
  --source-layout "${MOBILE_SLICER_ORCA_REFERENCE_SOURCE_LAYOUT:-single}" \
  --source-colors "${MOBILE_SLICER_ORCA_REFERENCE_SOURCE_COLORS:-}" \
  --require-active-filaments "${MOBILE_SLICER_ORCA_REQUIRE_ACTIVE_FILAMENTS:-0}" \
  --notes "Generated by scripts/generate_orca_thumbnail_reference_fixture.sh; artifacts at $RUN_DIR" \
  --allow-overwrite > "$RUN_DIR/extract-report.json"

log "Desktop Orca thumbnail reference generated: $REFERENCE_DIR"
log "Run strict device comparison:"
log "MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR=$REFERENCE_DIR MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 MOBILE_SLICER_ALLOW_DEVICE_AUTOMATION=1 scripts/verify_android.sh egl-thumbnail-compare <serial>"
