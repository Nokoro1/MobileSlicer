#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_ORCA_BIN="$ROOT_DIR/vendor/orcaslicer/build/package/bin/orca-slicer"
ORCA_BIN="${ORCA_SLICER_BIN:-$DEFAULT_ORCA_BIN}"
BASE_FIXTURE="${MOBILE_SLICER_ORCA_RICH_PROJECT_FIXTURE:-$ROOT_DIR/regression-fixtures/orca-project-references/rich-object-settings/rich-object-settings.gcode.3mf}"
ARTIFACT_ROOT="${MOBILE_SLICER_ORCA_MODIFIER_PROJECT_ARTIFACT_ROOT:-$ROOT_DIR/artifacts/orca-project-fixtures}"
REFERENCE_DIR="${MOBILE_SLICER_ORCA_MODIFIER_PROJECT_REFERENCE_DIR:-$ROOT_DIR/regression-fixtures/orca-project-references/modifier-object-settings}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ARTIFACT_ROOT/modifier-object-settings-$STAMP"
ORCA_LIB_SHIM="$ROOT_DIR/tools/orcaslicer/lib-shim/usr/lib64:$ROOT_DIR/tools/orcaslicer/lib-shim"

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_orca_modifier_project_fixture.sh

Generates a desktop-Orca canonical sliced .gcode.3mf fixture containing:
  - two plates,
  - named objects,
  - two object-to-filament assignments,
  - object-scoped process settings,
  - one parameter modifier volume with modifier-scoped process settings,
  - project settings/config entries,
  - Orca package thumbnails.

The seed project is built from the richer desktop-Orca project fixture and
Orca's own 3MF model_settings.config schema. The committed reference is not the
seed. The committed reference is the package after Orca imports that seed,
slices it, re-exports it, and the strict audit proves the modifier survived.

Environment:
  ORCA_SLICER_BIN
      Orca binary to canonicalize the seed. Defaults to
      vendor/orcaslicer/build/package/bin/orca-slicer.
  MOBILE_SLICER_ORCA_RICH_PROJECT_FIXTURE
      Base desktop-Orca fixture used as the seed source.
  MOBILE_SLICER_ORCA_MODIFIER_PROJECT_ARTIFACT_ROOT
      Artifact root for generated seed, logs, and canonical output package.
  MOBILE_SLICER_ORCA_MODIFIER_PROJECT_REFERENCE_DIR
      Checked reference directory populated after the canonical audit passes.
USAGE
}

log() {
  printf '[generate_orca_modifier_project_fixture] %s\n' "$*"
}

fail() {
  printf '[generate_orca_modifier_project_fixture] ERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

[[ -x "$ORCA_BIN" ]] || fail "Orca binary is not executable: $ORCA_BIN"
[[ -f "$BASE_FIXTURE" ]] || fail "Missing rich base fixture: $BASE_FIXTURE"
mkdir -p "$RUN_DIR/work" "$RUN_DIR/output"

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

SEED_PATH="$RUN_DIR/modifier-seed.3mf"
PACKAGE_NAME="modifier-object-settings.gcode.3mf"
PACKAGE_PATH="$RUN_DIR/output/$PACKAGE_NAME"
LOG_PATH="$RUN_DIR/orca.log"

python3 - "$BASE_FIXTURE" "$RUN_DIR/work" "$SEED_PATH" <<'PY'
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

base_fixture = Path(sys.argv[1])
work = Path(sys.argv[2])
seed_path = Path(sys.argv[3])

if work.exists():
    shutil.rmtree(work)
work.mkdir(parents=True)
with zipfile.ZipFile(base_fixture) as source:
    source.extractall(work)

core_ns = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"
bambu_ns = "http://schemas.bambulab.com/package/2021"
prod_ns = "http://schemas.microsoft.com/3dmanufacturing/production/2015/06"
rels_ns = "http://schemas.openxmlformats.org/package/2006/relationships"
ET.register_namespace("", core_ns)
ET.register_namespace("BambuStudio", bambu_ns)
ET.register_namespace("p", prod_ns)

modifier_model = work / "3D/Objects/modifier_cube_1_4.model"
half = 4.0
vertices = [
    (half, -half, -half),
    (-half, -half, -half),
    (half, half, -half),
    (-half, half, -half),
    (half, half, half),
    (-half, -half, half),
    (half, -half, half),
    (-half, half, half),
]
triangles = [
    (0, 1, 2),
    (2, 1, 3),
    (4, 5, 6),
    (7, 5, 4),
    (5, 1, 6),
    (6, 1, 0),
    (6, 0, 4),
    (4, 0, 2),
    (4, 2, 7),
    (7, 2, 3),
    (7, 3, 5),
    (5, 3, 1),
]
lines = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02" xmlns:BambuStudio="http://schemas.bambulab.com/package/2021" xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06" requiredextensions="p">',
    ' <metadata name="BambuStudio:3mfVersion">1</metadata>',
    " <resources>",
    '  <object id="7" p:UUID="00040000-81cb-4c03-9d28-80fed5dfa1dc" type="model">',
    "   <mesh>",
    "    <vertices>",
]
for x, y, z in vertices:
    lines.append(f'     <vertex x="{x:g}" y="{y:g}" z="{z:g}"/>')
lines.extend(["    </vertices>", "    <triangles>"])
for first, second, third in triangles:
    lines.append(f'     <triangle v1="{first}" v2="{second}" v3="{third}"/>')
lines.extend(["    </triangles>", "   </mesh>", "  </object>", " </resources>", " <build/>", "</model>"])
modifier_model.write_text("\n".join(lines) + "\n", encoding="utf-8")

model_path = work / "3D/3dmodel.model"
model_tree = ET.parse(model_path)
model_root = model_tree.getroot()
for obj in model_root.findall(f".//{{{core_ns}}}object"):
    if obj.get("id") == "4":
        components = obj.find(f"{{{core_ns}}}components")
        if components is None:
            raise RuntimeError("Object id 4 has no components element")
        ET.SubElement(
            components,
            f"{{{core_ns}}}component",
            {
                f"{{{prod_ns}}}path": "/3D/Objects/modifier_cube_1_4.model",
                "objectid": "7",
                f"{{{prod_ns}}}UUID": "00040000-b206-40ff-9872-83e8017abed1",
                "transform": "1 0 0 0 1 0 0 0 1 155 135 6",
            },
        )
        break
else:
    raise RuntimeError("Object id 4 was not found in 3D/3dmodel.model")
model_tree.write(model_path, encoding="UTF-8", xml_declaration=True)

ET.register_namespace("", rels_ns)
rels_path = work / "3D/_rels/3dmodel.model.rels"
rels_tree = ET.parse(rels_path)
rels_root = rels_tree.getroot()
ET.SubElement(
    rels_root,
    f"{{{rels_ns}}}Relationship",
    {
        "Target": "/3D/Objects/modifier_cube_1_4.model",
        "Id": "rel-4",
        "Type": "http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel",
    },
)
rels_tree.write(rels_path, encoding="UTF-8", xml_declaration=True)

config_path = work / "Metadata/model_settings.config"
config_tree = ET.parse(config_path)
config_root = config_tree.getroot()
for obj in config_root.findall("object"):
    if obj.get("id") == "4":
        modifier = ET.Element("part", {"id": "7", "subtype": "modifier_part"})
        metadata = [
            ("name", "Infill modifier"),
            ("matrix", "1 0 0 155 0 1 0 135 0 0 1 6 0 0 0 1"),
            ("source_file", "modifier_cube.stl"),
            ("source_object_id", "1"),
            ("source_volume_id", "1"),
            ("source_offset_x", "0"),
            ("source_offset_y", "0"),
            ("source_offset_z", "4"),
            ("sparse_infill_density", "80%"),
            ("wall_loops", "1"),
        ]
        for key, value in metadata:
            ET.SubElement(modifier, "metadata", {"key": key, "value": value})
        ET.SubElement(
            modifier,
            "mesh_stat",
            {
                "edges_fixed": "0",
                "degenerate_facets": "0",
                "facets_removed": "0",
                "facets_reversed": "0",
                "backwards_edges": "0",
            },
        )
        obj.append(modifier)
        break
else:
    raise RuntimeError("Object id 4 was not found in Metadata/model_settings.config")
config_tree.write(config_path, encoding="UTF-8", xml_declaration=True)

if seed_path.exists():
    seed_path.unlink()
with zipfile.ZipFile(seed_path, "w", compression=zipfile.ZIP_DEFLATED) as target:
    for path in sorted(work.rglob("*")):
        if path.is_file():
            target.write(path, path.relative_to(work).as_posix())
PY

log "Artifacts: $RUN_DIR"
log "Orca binary: $ORCA_BIN"
set +e
python3 - "$LOG_PATH" "${ORCA_ENV[@]}" "$ORCA_BIN" \
  --slice 0 \
  --outputdir "$RUN_DIR/output" \
  --export-3mf "$PACKAGE_NAME" \
  "$SEED_PATH" <<'PY'
import resource
import subprocess
import sys

log_path = sys.argv[1]
command = sys.argv[2:]
resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
with open(log_path, "wb") as log:
    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=False)
raise SystemExit(result.returncode if result.returncode >= 0 else 128 + abs(result.returncode))
PY
ORCA_STATUS="$?"
set -e
[[ "$ORCA_STATUS" -eq 0 ]] || fail "Orca exited with status $ORCA_STATUS; log: $LOG_PATH"
[[ -f "$PACKAGE_PATH" ]] || fail "Orca did not write expected package: $PACKAGE_PATH"

python3 "$ROOT_DIR/scripts/orca_3mf_project_preservation_audit.py" \
  --three-mf "$PACKAGE_PATH" \
  --min-plate-count 2 \
  --min-object-count 2 \
  --require-plate-names \
  --require-object-names \
  --require-filament-assignments \
  --require-object-settings \
  --require-modifier-volumes \
  --require-modifier-settings \
  --require-project-thumbnails \
  --require-project-settings \
  --pretty > "$RUN_DIR/audit.json"

rm -rf "$REFERENCE_DIR"
mkdir -p "$REFERENCE_DIR"
cp "$PACKAGE_PATH" "$REFERENCE_DIR/$PACKAGE_NAME"
cp "$SEED_PATH" "$REFERENCE_DIR/modifier-seed.3mf"
cp "$RUN_DIR/audit.json" "$REFERENCE_DIR/audit.json"
cat > "$REFERENCE_DIR/README.md" <<EOF
# Modifier Object Settings Orca Project Fixture

Generated by \`scripts/generate_orca_modifier_project_fixture.sh\`.

- Orca binary: \`$ORCA_BIN\`
- Source artifact directory: \`$RUN_DIR\`
- Seed package: \`modifier-seed.3mf\`
- Canonical Orca package: \`$PACKAGE_NAME\`

The seed package is generated from the richer desktop-Orca project fixture plus
Orca's own \`Metadata/model_settings.config\` modifier schema. The checked
reference is the canonical package after Orca imports that seed, slices it, and
re-exports it.

The audit proves desktop Orca preserved:

- two plates,
- named objects,
- two object-to-filament assignments,
- object-scoped process metadata,
- one \`modifier_part\` parameter modifier volume,
- modifier-scoped process metadata,
- project/package thumbnails,
- project, model, and slice config entries.

The installed Flatpak OrcaSlicer 2.3.2-rc on this machine rejects or crashes on
this newer modifier seed path. The canonical reference is therefore generated
with the vendored desktop Orca build used by this project.
EOF

log "Generated modifier Orca project fixture: $REFERENCE_DIR"
