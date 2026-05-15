#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_APPIMAGE="$ROOT_DIR/tools/orcaslicer/OrcaSlicer_Linux_AppImage_Ubuntu2404_V2.3.2.AppImage"
DEFAULT_PACKAGE_BIN="$ROOT_DIR/vendor/orcaslicer/build/package/bin/orca-slicer"
if [[ -x "${ORCA_SLICER_BIN:-}" ]]; then
  ORCA_BIN="$ORCA_SLICER_BIN"
elif [[ -x "$DEFAULT_PACKAGE_BIN" ]]; then
  ORCA_BIN="$DEFAULT_PACKAGE_BIN"
else
  ORCA_BIN="$DEFAULT_APPIMAGE"
fi
ARTIFACT_ROOT="${MOBILE_SLICER_ORCA_MULTIFILAMENT_PROBE_ARTIFACT_ROOT:-$ROOT_DIR/artifacts/orca-thumbnail-reference-capture}"
REFERENCE_DIR="${MOBILE_SLICER_ORCA_ACTIVE_MULTIFILAMENT_REFERENCE_DIR:-$ROOT_DIR/regression-fixtures/orca-thumbnail-references/active-multifilament-two-objects}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ARTIFACT_ROOT/active-multifilament-reference-probe-$STAMP"
REPORT_PATH="$RUN_DIR/probe-report.jsonl"
ORCA_LIB_SHIM="$ROOT_DIR/tools/orcaslicer/lib-shim/usr/lib64:$ROOT_DIR/tools/orcaslicer/lib-shim"

usage() {
  cat <<'USAGE'
Usage:
  scripts/probe_orca_active_multifilament_reference.sh

Attempts to generate a desktop Orca sliced .gcode.3mf package that proves a
real active multi-filament thumbnail/metadata reference. The script rejects
packages that visually contain two source objects but collapse to one active
filament in Orca metadata.

Environment:
  ORCA_SLICER_BIN
      Orca binary/AppImage to run.
  MOBILE_SLICER_ORCA_ACTIVE_MULTIFILAMENT_REFERENCE_DIR
      Reference output directory populated only after the desktop package
      passes active multi-filament extraction.
  MOBILE_SLICER_ORCA_MULTIFILAMENT_PROBE_ARTIFACT_ROOT
      Artifact root for generated models, profiles, logs, and reports.

Success requires:
  - Metadata/plate_1.png
  - Metadata/plate_no_light_1.png
  - Metadata/top_1.png
  - Metadata/pick_1.png
  - at least two active entries in Metadata/plate_1.json filament fields
  - at least two active entries in Metadata/slice_info.config filament fields
  - at least two object extruder assignments in Metadata/model_settings.config
USAGE
}

log() {
  printf '[probe_orca_active_multifilament_reference] %s\n' "$*"
}

fail() {
  printf '[probe_orca_active_multifilament_reference] ERROR: %s\n' "$*" >&2
  exit 1
}

orca_build_label() {
  local help_text
  help_text="$("$ORCA_BIN" --help 2>/dev/null || true)"
  local first_line
  first_line="$(printf '%s\n' "$help_text" | head -n 1)"
  if [[ -n "$first_line" ]]; then
    printf '%s\n' "$first_line"
  else
    printf 'OrcaSlicer unknown\n'
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -x "$ORCA_BIN" ]] || fail "Orca binary is not executable: $ORCA_BIN"
mkdir -p "$RUN_DIR"
: > "$REPORT_PATH"

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

append_report() {
  local case_id="$1"
  local status="$2"
  local detail="$3"
  local package_path="$4"
  local log_path="$5"
  python3 - "$REPORT_PATH" "$case_id" "$status" "$detail" "$package_path" "$log_path" <<'PY'
import json
import sys
from pathlib import Path

report, case_id, status, detail, package_path, log_path = sys.argv[1:]
entry = {
    "case_id": case_id,
    "status": status,
    "detail": detail,
    "package": package_path,
    "log": log_path,
}
with Path(report).open("a", encoding="utf-8") as fh:
    fh.write(json.dumps(entry, sort_keys=True) + "\n")
PY
}

generate_probe_inputs() {
  local case_id="$1"
  local work_dir="$2"
  python3 - "$ROOT_DIR" "$case_id" "$work_dir" <<'PY'
import json
import math
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1])
case_id = sys.argv[2]
work = Path(sys.argv[3])
work.mkdir(parents=True, exist_ok=True)

def write_cube(path: Path, name: str) -> None:
    vertices = [
        (-5, -5, 0), (5, -5, 0), (5, 5, 0), (-5, 5, 0),
        (-5, -5, 10), (5, -5, 10), (5, 5, 10), (-5, 5, 10),
    ]
    faces = [
        (0, 1, 2), (0, 2, 3), (4, 6, 5), (4, 7, 6),
        (0, 4, 5), (0, 5, 1), (1, 5, 6), (1, 6, 2),
        (2, 6, 7), (2, 7, 3), (3, 7, 4), (3, 4, 0),
    ]
    data = bytearray(name.encode("ascii", "ignore")[:80].ljust(80, b"\0"))
    data.extend(struct.pack("<I", len(faces)))
    for a, b, c in faces:
        ax, ay, az = vertices[a]
        bx, by, bz = vertices[b]
        cx, cy, cz = vertices[c]
        ux, uy, uz = bx - ax, by - ay, bz - az
        vx, vy, vz = cx - ax, cy - ay, cz - az
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        data.extend(struct.pack("<12fH", nx / length, ny / length, nz / length, ax, ay, az, bx, by, bz, cx, cy, cz, 0))
    path.write_bytes(data)

profile_root = root / "vendor/orcaslicer/resources/profiles"
profile_paths = sorted(profile_root.glob("**/*.json"))

def load(path: str) -> dict:
    return json.loads((root / path).read_text(encoding="utf-8"))

def find_inherited_profile(parent_name: str, child_path: Path) -> Path:
    normalized = parent_name if parent_name.endswith(".json") else f"{parent_name}.json"
    matches = [path for path in profile_paths if path.name == normalized]
    if not matches:
        raise FileNotFoundError(f"Could not resolve inherited Orca profile {parent_name!r} for {child_path}")
    child_parts = child_path.parts
    scored = []
    for match in matches:
        common = 0
        for left, right in zip(child_parts, match.parts):
            if left != right:
                break
            common += 1
        scored.append((common, -len(match.parts), match))
    scored.sort(reverse=True)
    return scored[0][2]

def merge_profile(parent: dict, child: dict) -> dict:
    merged = dict(parent)
    merged.update(child)
    return merged

def load_profile(path: str, seen: set[Path] | None = None) -> dict:
    profile_path = root / path
    seen = set() if seen is None else seen
    if profile_path in seen:
        raise RuntimeError(f"Cycle while resolving Orca profile inheritance at {profile_path}")
    seen.add(profile_path)
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    inherits = profile.get("inherits")
    if not inherits:
        return profile
    parent_names = inherits if isinstance(inherits, list) else [inherits]
    merged: dict = {}
    for parent_name in parent_names:
        parent_path = find_inherited_profile(str(parent_name), profile_path)
        parent_rel = parent_path.relative_to(root).as_posix()
        merged = merge_profile(merged, load_profile(parent_rel, seen))
    return merge_profile(merged, profile)

write_cube(work / "left_cube.stl", "left_cube")
write_cube(work / "right_cube.stl", "right_cube")

if case_id.startswith("h2d"):
    machine = load_profile("vendor/orcaslicer/resources/profiles/BBL/machine/Bambu Lab H2D 0.4 nozzle.json")
    process = load_profile("vendor/orcaslicer/resources/profiles/BBL/process/0.20mm Standard @BBL H2D.json")
    filament_paths = [
        "vendor/orcaslicer/resources/profiles/BBL/filament/Generic PLA @BBL H2D.json",
        "vendor/orcaslicer/resources/profiles/BBL/filament/Generic PLA Silk @BBL H2D.json",
    ]
else:
    machine = load_profile("vendor/orcaslicer/resources/profiles/Custom/machine/MyToolChanger 0.4 nozzle.json")
    process = load_profile("vendor/orcaslicer/resources/profiles/Custom/process/0.20mm Standard @MyToolChanger.json")
    filament_paths = [
        "vendor/orcaslicer/resources/profiles/Custom/filament/Generic PLA @MyToolChanger.json",
        "vendor/orcaslicer/resources/profiles/Custom/filament/Generic PLA @MyToolChanger.json",
    ]

machine.pop("thumbnail_size", None)
machine["thumbnails"] = "128x128/PNG"
machine["thumbnails_format"] = "PNG"
machine["enable_long_retraction_when_cut"] = "0"
extruder_count = len(machine.get("nozzle_diameter", ["0.4"]))
machine["extruder_type"] = machine.get("extruder_type") or ["Direct Drive"] * extruder_count
machine["nozzle_volume_type"] = ["Standard"] * extruder_count
machine["extruder_variant_list"] = machine.get("extruder_variant_list") or ["Direct Drive Standard,Direct Drive High Flow"] * extruder_count
machine["physical_extruder_map"] = machine.get("physical_extruder_map") or [str(index) for index in range(extruder_count)]
machine["long_retractions_when_cut"] = ["0"] * extruder_count
machine["retraction_distances_when_cut"] = ["18"] * extruder_count
machine["nozzle_volume"] = ["0"] * extruder_count
machine["flush_multiplier"] = ["1"] * extruder_count
(work / "machine.json").write_text(json.dumps(machine, indent=2, sort_keys=True) + "\n", encoding="utf-8")
process["filament_map_mode"] = "Manual"
process["filament_map"] = ["1", "2"]
(work / "process.json").write_text(json.dumps(process, indent=2, sort_keys=True) + "\n", encoding="utf-8")

for index, (path, color) in enumerate(zip(filament_paths, ("#F2754E", "#4EA3F2")), start=1):
    filament = load_profile(path)
    filament["filament_colour"] = [color]
    filament["filament_settings_id"] = [f"MobileSlicer active multi-filament probe {index}"]
    filament["filament_type"] = ["PLA"]
    filament["filament_diameter"] = ["1.75"]
    filament["filament_max_volumetric_speed"] = ["12"]
    filament["filament_is_support"] = ["0"]
    filament["filament_long_retractions_when_cut"] = ["0"]
    filament["filament_retraction_distances_when_cut"] = ["18"]
    filament["nozzle_temperature"] = ["220"]
    filament["nozzle_temperature_initial_layer"] = ["220"]
    filament["bed_temperature"] = ["60"]
    filament["bed_temperature_initial_layer"] = ["60"]
    (work / f"filament{index}.json").write_text(json.dumps(filament, indent=2, sort_keys=True) + "\n", encoding="utf-8")

assemble = {
    "plates": [
        {
            "plate_name": "Plate 1",
            "need_arrange": False,
            "objects": [
                {
                    "path": str((work / "left_cube.stl").resolve()),
                    "count": 1,
                    "filaments": [1],
                    "pos_x": [125],
                    "pos_y": [140],
                    "pos_z": [0],
                    "assemble_index": [0],
                },
                {
                    "path": str((work / "right_cube.stl").resolve()),
                    "count": 1,
                    "filaments": [2],
                    "pos_x": [155],
                    "pos_y": [140],
                    "pos_z": [0],
                    "assemble_index": [0],
                },
            ],
        }
    ]
}
(work / "assemble-list.json").write_text(json.dumps(assemble, indent=2) + "\n", encoding="utf-8")
PY
}

run_case() {
  local case_id="$1"
  local mode="$2"
  local profile_label="$3"
  local case_dir="$RUN_DIR/$case_id"
  local work_dir="$case_dir/work"
  local output_dir="$case_dir/output"
  local package_name="$case_id.gcode.3mf"
  local package_path="$output_dir/$package_name"
  local log_path="$case_dir/orca.log"
  mkdir -p "$work_dir" "$output_dir"
  generate_probe_inputs "$case_id" "$work_dir"

  local args=(
    "$ORCA_BIN"
    --slice 0
    --outputdir "$output_dir"
    --export-3mf "$package_name"
    --load-settings "$work_dir/machine.json;$work_dir/process.json"
    --load-filaments "$work_dir/filament1.json;$work_dir/filament2.json"
    --allow-multicolor-oneplate
  )
  if [[ "$mode" == "assemble" ]]; then
    args+=(--load-assemble-list "$work_dir/assemble-list.json")
  else
    args+=(--load-filament-ids "1,2" "$work_dir/left_cube.stl" "$work_dir/right_cube.stl")
  fi

  log "Running $case_id ($mode, $profile_label)"
  set +e
  python3 - "$log_path" "${ORCA_ENV[@]}" "${args[@]}" <<'PY'
import resource
import subprocess
import sys

log_path = sys.argv[1]
command = sys.argv[2:]
resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
with open(log_path, "wb") as log:
    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=False)
return_code = result.returncode
if return_code < 0:
    return_code = 128 + abs(return_code)
raise SystemExit(return_code)
PY
  local orca_status="$?"
  set -e
  if [[ "$orca_status" -ne 0 ]]; then
    append_report "$case_id" "orca-failed" "Orca exited with status $orca_status" "$package_path" "$log_path"
    return 1
  fi
  if [[ ! -f "$package_path" ]]; then
    append_report "$case_id" "missing-package" "Orca exited successfully but did not write the expected package" "$package_path" "$log_path"
    return 1
  fi

  local extract_dir="$case_dir/extracted-reference"
  set +e
  python3 "$ROOT_DIR/scripts/extract_orca_thumbnail_references.py" \
    --source-3mf "$package_path" \
    --output-dir "$extract_dir" \
    --orca-build "$(orca_build_label)" \
    --model "two generated probe cubes" \
    --printer-profile "$profile_label" \
    --filament-profile "red PLA, blue PLA" \
    --process-profile "0.20mm Standard" \
    --source-layout two_filament_objects \
    --source-colors "#F2754E,#4EA3F2" \
    --require-active-filaments 2 \
    --allow-overwrite > "$case_dir/extract-report.json" 2> "$case_dir/extract-error.log"
  local extract_status="$?"
  set -e
  if [[ "$extract_status" -ne 0 ]]; then
    append_report "$case_id" "active-filament-proof-failed" "Extractor rejected package; see extract-error.log" "$package_path" "$log_path"
    return 1
  fi

  rm -rf "$REFERENCE_DIR"
  mkdir -p "$(dirname "$REFERENCE_DIR")"
  cp -a "$extract_dir" "$REFERENCE_DIR"
  append_report "$case_id" "passed" "Active multi-filament reference generated" "$package_path" "$log_path"
  log "Active multi-filament reference generated: $REFERENCE_DIR"
  return 0
}

log "Artifacts: $RUN_DIR"
log "Orca binary: $ORCA_BIN"
if run_case "h2d-direct-two-filament-objects" "direct" "Bambu Lab H2D 0.4 nozzle"; then
  exit 0
fi
if run_case "h2d-assemble-two-filament-objects" "assemble" "Bambu Lab H2D 0.4 nozzle"; then
  exit 0
fi
if run_case "toolchanger-direct-two-filament-objects" "direct" "MyToolChanger 0.4 nozzle"; then
  exit 0
fi
if run_case "toolchanger-assemble-two-filament-objects" "assemble" "MyToolChanger 0.4 nozzle"; then
  exit 0
fi

log "No valid active multi-filament desktop Orca reference was produced."
log "Report: $REPORT_PATH"
fail "desktop Orca CLI did not produce a package that satisfies the active multi-filament reference contract"
