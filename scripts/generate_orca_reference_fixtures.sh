#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_PATH="$ROOT_DIR/regression-fixtures/orca-metadata/manifest.json"
REFERENCE_DIR="$ROOT_DIR/regression-fixtures/orca-metadata/references"
REFERENCE_INPUT_DIR="$ROOT_DIR/regression-fixtures/orca-metadata/reference-inputs"
BASELINE_CONFIG="$ROOT_DIR/regression-fixtures/orca-metadata/mobile-baseline-config.json"
MODEL="$ROOT_DIR/regression-fixtures/slicing/mobileslicer_test_cube.stl"
DEFAULT_ORCA_APPIMAGE="$ROOT_DIR/tools/orcaslicer/OrcaSlicer_Linux_AppImage_Ubuntu2404_V2.3.2.AppImage"
ORCA_SLICER_BIN="${ORCA_SLICER_BIN:-$DEFAULT_ORCA_APPIMAGE}"
ORCA_LIB_SHIM="$ROOT_DIR/tools/orcaslicer/lib-shim/usr/lib64:$ROOT_DIR/tools/orcaslicer/lib-shim"
QIDI_MACHINE="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/machine/Qidi Q2 0.4 nozzle.json"
QIDI_PROCESS="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/process/0.20mm Standard @Qidi Q2.json"
QIDI_FILAMENT="$ROOT_DIR/vendor/orcaslicer/resources/profiles/Qidi/filament/Q2/Generic PLA @Qidi Q2 0.4 nozzle.json"

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_orca_reference_fixtures.sh
  ORCA_SLICER_BIN=/path/to/OrcaSlicer scripts/generate_orca_reference_fixtures.sh

Generates the desktop OrcaSlicer CLI metadata references that are possible from
the official AppImage. Orca's CLI path calls export_gcode(..., nullptr), so it
does not emit G-code thumbnail blocks; thumbnail parity is therefore verified
from Orca source contracts plus MobileSlicer fixture outputs, not from CLI
G-code thumbnail references.
USAGE
}

fail() {
  printf '[generate_orca_reference_fixtures] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[generate_orca_reference_fixtures] %s\n' "$*"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -x "$ORCA_SLICER_BIN" ]] || fail "Orca binary is not executable: $ORCA_SLICER_BIN"
[[ -f "$MANIFEST_PATH" ]] || fail "Fixture manifest missing: $MANIFEST_PATH"
[[ -f "$BASELINE_CONFIG" ]] || fail "Mobile baseline config missing: $BASELINE_CONFIG"
[[ -f "$MODEL" ]] || fail "Fixture model missing: $MODEL"
[[ -f "$QIDI_MACHINE" ]] || fail "Qidi machine profile missing: $QIDI_MACHINE"
[[ -f "$QIDI_PROCESS" ]] || fail "Qidi process profile missing: $QIDI_PROCESS"
[[ -f "$QIDI_FILAMENT" ]] || fail "Qidi filament profile missing: $QIDI_FILAMENT"
mkdir -p "$REFERENCE_DIR" "$REFERENCE_INPUT_DIR"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

ORCA_LD_LIBRARY_PATH="$ORCA_LIB_SHIM${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
ORCA_ENV=(env -u DRI_PRIME -u MANGOHUD "LD_LIBRARY_PATH=$ORCA_LD_LIBRARY_PATH")

log "Orca binary: $ORCA_SLICER_BIN"
"${ORCA_ENV[@]}" "$ORCA_SLICER_BIN" --help > "$REFERENCE_DIR/orca-version.txt" 2>&1 || true

write_reference_profiles() {
  local case_name="$1"
  local thumbnails="$2"
  local format="$3"
  local legacy_thumbnail_size="${4:-0}"
  local output_dir="$REFERENCE_INPUT_DIR/$case_name"
  mkdir -p "$output_dir"
  python3 - \
    "$QIDI_MACHINE" \
    "$QIDI_PROCESS" \
    "$QIDI_FILAMENT" \
    "$BASELINE_CONFIG" \
    "$MODEL" \
    "$output_dir" \
    "$thumbnails" \
    "$format" \
    "$legacy_thumbnail_size" \
    "$case_name" <<'PY'
import json
import hashlib
import sys
from pathlib import Path

machine_source = Path(sys.argv[1])
process_source = Path(sys.argv[2])
filament_source = Path(sys.argv[3])
baseline_path = Path(sys.argv[4])
model_path = Path(sys.argv[5])
output_dir = Path(sys.argv[6])
thumbnails = sys.argv[7]
fmt = sys.argv[8]
legacy_thumbnail_size = sys.argv[9] == "1"
case_name = sys.argv[10]

machine = json.loads(machine_source.read_text(encoding="utf-8"))
process = json.loads(process_source.read_text(encoding="utf-8"))
filament = json.loads(filament_source.read_text(encoding="utf-8"))
baseline = json.loads(baseline_path.read_text(encoding="utf-8"))


def plain(value):
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def percent(value):
    text = plain(value)
    return text if text.endswith("%") else f"{text}%"


def array(value):
    if isinstance(value, list):
        return [plain(item) for item in value]
    return [plain(value)]


def put_process(source_key, target_key=None, converter=plain):
    if source_key in baseline:
        process[target_key or source_key] = converter(baseline[source_key])


def put_filament(source_key, target_key=None, converter=array):
    if source_key in baseline:
        filament[target_key or source_key] = converter(baseline[source_key])


width = baseline.get("bed_width_mm")
depth = baseline.get("bed_depth_mm")
height = baseline.get("max_height_mm", baseline.get("printable_height"))
if width is not None and depth is not None:
    w = plain(width)
    d = plain(depth)
    machine["printable_area"] = [f"0x0", f"{w}x0", f"{w}x{d}", f"0x{d}"]
if height is not None:
    machine["printable_height"] = plain(height)
if "nozzle_diameter" in baseline:
    machine["nozzle_diameter"] = array(baseline["nozzle_diameter"])
machine["machine_start_gcode"] = "G90\nG21\nM83 ; use relative distances for extrusion\nM106 S0 ; disable fan"
machine["layer_change_gcode"] = ""

if legacy_thumbnail_size:
    machine.pop("thumbnails", None)
    machine["thumbnail_size"] = [thumbnails]
else:
    machine.pop("thumbnail_size", None)
    machine["thumbnails"] = thumbnails
machine["thumbnails_format"] = fmt

process["name"] = f"MobileSlicer baseline {case_name}"
process["setting_id"] = f"MOBILE-{case_name}"
put_process("layer_height")
put_process("first_layer_height", "initial_layer_print_height")
put_process("first_layer_print_speed", "initial_layer_speed")
put_process("first_layer_infill_speed", "initial_layer_infill_speed")
put_process("initial_layer_travel_speed_percent", "initial_layer_travel_speed", percent)
for key in [
    "outer_wall_speed",
    "inner_wall_speed",
    "top_surface_speed",
    "travel_speed",
    "outer_wall_acceleration",
    "inner_wall_acceleration",
    "top_surface_acceleration",
    "sparse_infill_acceleration",
    "bridge_speed",
    "small_perimeter_speed",
    "small_perimeter_threshold",
    "sparse_infill_speed",
    "internal_solid_infill_speed",
    "gap_infill_speed",
    "top_shell_layers",
    "bottom_shell_layers",
    "seam_position",
    "precise_outer_wall",
    "only_one_wall_top",
    "top_surface_pattern",
    "sparse_infill_pattern",
    "wall_loops",
    "skirts",
    "skirt_loops",
    "brim_width",
]:
    put_process(key)
put_process("sparse_infill_density", converter=percent)

filament["name"] = f"MobileSlicer baseline {case_name} filament"
filament["setting_id"] = f"MOBILE-FIL-{case_name}"
for key in [
    "filament_diameter",
    "filament_type",
    "filament_max_volumetric_speed",
    "nozzle_temperature_initial_layer",
    "nozzle_temperature",
    "close_fan_the_first_x_layers",
]:
    put_filament(key)
put_filament("cooling_baseline", "fan_max_speed")
put_filament("bed_temperature_initial_layer", "hot_plate_temp_initial_layer")
put_filament("bed_temperature", "hot_plate_temp")
put_filament("bed_temperature_initial_layer", "cool_plate_temp_initial_layer")
put_filament("bed_temperature", "cool_plate_temp")
put_filament("bed_temperature_initial_layer", "eng_plate_temp_initial_layer")
put_filament("bed_temperature", "eng_plate_temp")
put_filament("bed_temperature_initial_layer", "supertack_plate_temp_initial_layer")
put_filament("bed_temperature", "supertack_plate_temp")

paths = {
    "machine": output_dir / "machine.json",
    "process": output_dir / "process.json",
    "filament": output_dir / "filament.json",
}
paths["machine"].write_text(json.dumps(machine, indent=2, sort_keys=True) + "\n", encoding="utf-8")
paths["process"].write_text(json.dumps(process, indent=2, sort_keys=True) + "\n", encoding="utf-8")
paths["filament"].write_text(json.dumps(filament, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


manifest = {
    "case": case_name,
    "model": str(model_path.relative_to(Path.cwd()) if model_path.is_relative_to(Path.cwd()) else model_path),
    "modelSha256": sha256(model_path),
    "baselineConfig": str(baseline_path),
    "baselineConfigSha256": sha256(baseline_path),
    "sources": {
        "machine": str(machine_source),
        "machineSha256": sha256(machine_source),
        "process": str(process_source),
        "processSha256": sha256(process_source),
        "filament": str(filament_source),
        "filamentSha256": sha256(filament_source),
    },
    "generated": {name: {"path": str(path), "sha256": sha256(path)} for name, path in paths.items()},
    "thumbnail": {
        "thumbnails": thumbnails,
        "thumbnails_format": fmt,
        "legacy_thumbnail_size": legacy_thumbnail_size,
    },
}
(output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(paths["machine"])
print(paths["process"])
print(paths["filament"])
PY
}

run_orca_slice() {
  local label="$1"
  local profile_dir="$2"
  local output="$3"
  local out_dir="$TMP_DIR/$label"
  local machine="$profile_dir/machine.json"
  local process="$profile_dir/process.json"
  local filament="$profile_dir/filament.json"
  mkdir -p "$out_dir"
  log "Generating $output"
  "${ORCA_ENV[@]}" "$ORCA_SLICER_BIN" \
    --slice 0 \
    --outputdir "$out_dir" \
    --load-settings "$machine;$process" \
    --load-filaments "$filament" \
    "$MODEL" > "$out_dir/orca.log" 2>&1
  [[ -f "$out_dir/plate_1.gcode" ]] || {
    sed -n '1,160p' "$out_dir/orca.log" >&2
    fail "Orca did not produce plate_1.gcode for $label"
  }
  cp "$out_dir/plate_1.gcode" "$REFERENCE_DIR/$output"
}

run_orca_sliced_3mf() {
  local out_dir="$TMP_DIR/sliced-3mf"
  local profile_dir="$REFERENCE_INPUT_DIR/simple-cube-png"
  local machine="$profile_dir/machine.json"
  local process="$profile_dir/process.json"
  local filament="$profile_dir/filament.json"
  mkdir -p "$out_dir"
  log "Generating simple-cube.gcode.3mf"
  "${ORCA_ENV[@]}" "$ORCA_SLICER_BIN" \
    --slice 0 \
    --outputdir "$out_dir" \
    --export-3mf simple-cube.gcode.3mf \
    --load-settings "$machine;$process" \
    --load-filaments "$filament" \
    "$MODEL" > "$out_dir/orca.log" 2>&1
  [[ -f "$out_dir/simple-cube.gcode.3mf" ]] || {
    sed -n '1,160p' "$out_dir/orca.log" >&2
    fail "Orca did not produce simple-cube.gcode.3mf"
  }
  cp "$out_dir/simple-cube.gcode.3mf" "$REFERENCE_DIR/simple-cube.gcode.3mf"
}

write_reference_profiles simple-cube-png "128x128/PNG" "PNG" 0 >/dev/null
write_reference_profiles qidi-q2-legacy-png "150x150" "PNG" 1 >/dev/null
write_reference_profiles non-qidi-qoi "96x96/QOI" "QOI" 0 >/dev/null

run_orca_slice simple-cube-png "$REFERENCE_INPUT_DIR/simple-cube-png" simple-cube-png.gcode
run_orca_slice qidi-q2-legacy-png "$REFERENCE_INPUT_DIR/qidi-q2-legacy-png" qidi-q2-legacy-png.gcode
run_orca_slice non-qidi-qoi "$REFERENCE_INPUT_DIR/non-qidi-qoi" non-qidi-qoi.gcode
run_orca_sliced_3mf

cat > "$REFERENCE_DIR/README.generated.md" <<'NOTE'
# Generated Orca CLI References

These files were produced by OrcaSlicer CLI slicing with generated Qidi Q2
profiles under `regression-fixtures/orca-metadata/reference-inputs/`. Those
profiles are derived from the same MobileSlicer baseline automation config used
by `scripts/verify_android.sh orca-fixture-capture-mobile`, so visible metadata
drift is not hidden by accidentally comparing unrelated settings.

Orca's CLI path in `vendor/orcaslicer/src/OrcaSlicer.cpp` calls
`export_gcode(..., nullptr)`, so CLI-generated G-code does not contain thumbnail
blocks even when `thumbnails` is configured. Use these files for print-time,
filament, and non-thumbnail metadata comparisons only.

Thumbnail block parity is validated against MobileSlicer fixture outputs and the
Orca source contract in `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.hpp`
and `vendor/orcaslicer/src/libslic3r/GCode/Thumbnails.cpp`.
NOTE

log "Reference metadata audit"
python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" --orca-gcode "$REFERENCE_DIR/simple-cube-png.gcode" --require-print-time --require-filament --pretty >/dev/null
python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" --orca-gcode "$REFERENCE_DIR/qidi-q2-legacy-png.gcode" --require-print-time --require-filament --pretty >/dev/null
python3 "$ROOT_DIR/scripts/orca_metadata_audit.py" --orca-gcode "$REFERENCE_DIR/non-qidi-qoi.gcode" --require-print-time --require-filament --pretty >/dev/null
log "Done. Thumbnail references are intentionally not claimed from Orca CLI."
