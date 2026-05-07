#!/usr/bin/env python3
"""Convert Orca bundled calibration meshes into STL assets for Android.

The Android workspace currently owns STL plate objects. Orca's calibration
resources are a mix of 3MF and Draco-compressed DRC meshes, so this script
materializes exact geometry from those bundled resources as compact binary STL
files that the app can copy directly onto the build plate.
"""

from __future__ import annotations

import math
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ORCA_CALIB = ROOT / "vendor" / "orcaslicer" / "resources" / "calib"
ORCA_HANDY_MODELS = ROOT / "vendor" / "orcaslicer" / "resources" / "handy_models"
OUT_DIR = ROOT / "android-app" / "app" / "src" / "main" / "assets" / "calib_stl"


@dataclass(frozen=True)
class Asset:
    source: Path
    target: Path


ASSETS = [
    Asset(ORCA_CALIB / "pressure_advance" / "pressure_advance_test.drc", OUT_DIR / "pressure_advance" / "pressure_advance_test.stl"),
    Asset(ORCA_CALIB / "pressure_advance" / "tower_with_seam.drc", OUT_DIR / "pressure_advance" / "tower_with_seam.stl"),
    Asset(ORCA_CALIB / "pressure_advance" / "pa_pattern.3mf", OUT_DIR / "pressure_advance" / "pa_pattern.stl"),
    Asset(ORCA_CALIB / "pressure_advance" / "auto_pa_line_single.3mf", OUT_DIR / "pressure_advance" / "auto_pa_line_single.stl"),
    Asset(ORCA_CALIB / "pressure_advance" / "auto_pa_line_dual.3mf", OUT_DIR / "pressure_advance" / "auto_pa_line_dual.stl"),
    Asset(ORCA_CALIB / "filament_flow" / "Orca-LinearFlow.3mf", OUT_DIR / "filament_flow" / "Orca-LinearFlow.stl"),
    Asset(ORCA_CALIB / "filament_flow" / "Orca-LinearFlow_fine.3mf", OUT_DIR / "filament_flow" / "Orca-LinearFlow_fine.stl"),
    Asset(ORCA_CALIB / "filament_flow" / "flowrate-test-pass1.3mf", OUT_DIR / "filament_flow" / "flowrate-test-pass1.stl"),
    Asset(ORCA_CALIB / "filament_flow" / "flowrate-test-pass2.3mf", OUT_DIR / "filament_flow" / "flowrate-test-pass2.stl"),
    Asset(ORCA_CALIB / "filament_flow" / "pass1.3mf", OUT_DIR / "filament_flow" / "pass1.stl"),
    Asset(ORCA_CALIB / "temperature_tower" / "temperature_tower.drc", OUT_DIR / "temperature_tower" / "temperature_tower.stl"),
    Asset(ORCA_CALIB / "volumetric_speed" / "SpeedTestStructure.drc", OUT_DIR / "volumetric_speed" / "SpeedTestStructure.stl"),
    Asset(ORCA_CALIB / "retraction" / "retraction_tower.drc", OUT_DIR / "retraction" / "retraction_tower.stl"),
    Asset(ORCA_CALIB / "vfa" / "vfa.drc", OUT_DIR / "vfa" / "vfa.stl"),
    Asset(ORCA_CALIB / "input_shaping" / "ringing_tower.drc", OUT_DIR / "input_shaping" / "ringing_tower.stl"),
    Asset(ORCA_CALIB / "input_shaping" / "fast_tower_test.drc", OUT_DIR / "input_shaping" / "fast_tower_test.stl"),
    Asset(ORCA_CALIB / "cornering" / "SCV-V2.drc", OUT_DIR / "cornering" / "SCV-V2.stl"),
    Asset(ORCA_HANDY_MODELS / "OrcaToleranceTest.drc", OUT_DIR / "tolerance" / "OrcaToleranceTest.stl"),
]

FLOW_RATE_SPLIT_ASSETS = [
    (
        ORCA_CALIB / "filament_flow" / "flowrate-test-pass1.3mf",
        OUT_DIR / "filament_flow" / "flowrate-test-pass1-objects",
    ),
    (
        ORCA_CALIB / "filament_flow" / "flowrate-test-pass2.3mf",
        OUT_DIR / "filament_flow" / "flowrate-test-pass2-objects",
    ),
]


def transform_point(point: tuple[float, float, float], matrix: tuple[float, ...]) -> tuple[float, float, float]:
    x, y, z = point
    return (
        matrix[0] * x + matrix[3] * y + matrix[6] * z + matrix[9],
        matrix[1] * x + matrix[4] * y + matrix[7] * z + matrix[10],
        matrix[2] * x + matrix[5] * y + matrix[8] * z + matrix[11],
    )


IDENTITY = (1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0)


def combine_transform(a: tuple[float, ...], b: tuple[float, ...]) -> tuple[float, ...]:
    basis = [
        transform_point(transform_point((1, 0, 0), b), a),
        transform_point(transform_point((0, 1, 0), b), a),
        transform_point(transform_point((0, 0, 1), b), a),
        transform_point(transform_point((0, 0, 0), b), a),
    ]
    origin = basis[3]
    return (
        basis[0][0] - origin[0], basis[0][1] - origin[1], basis[0][2] - origin[2],
        basis[1][0] - origin[0], basis[1][1] - origin[1], basis[1][2] - origin[2],
        basis[2][0] - origin[0], basis[2][1] - origin[1], basis[2][2] - origin[2],
        origin[0], origin[1], origin[2],
    )


def parse_transform(value: str | None) -> tuple[float, ...]:
    if not value:
        return IDENTITY
    values = tuple(float(part) for part in value.split())
    if len(values) != 12:
        raise ValueError(f"Expected 12 transform values, got {len(values)}")
    return values


def parse_model_xml(data: bytes):
    root = ET.fromstring(data)
    ns = {"m": "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"}
    objects = {}
    for obj in root.findall(".//m:resources/m:object", ns):
        object_id = obj.attrib["id"]
        mesh = obj.find("m:mesh", ns)
        components = obj.find("m:components", ns)
        if mesh is not None:
            vertices = [
                (float(v.attrib["x"]), float(v.attrib["y"]), float(v.attrib["z"]))
                for v in mesh.findall("m:vertices/m:vertex", ns)
            ]
            triangles = [
                (int(t.attrib["v1"]), int(t.attrib["v2"]), int(t.attrib["v3"]))
                for t in mesh.findall("m:triangles/m:triangle", ns)
            ]
            objects[object_id] = ("mesh", vertices, triangles)
        elif components is not None:
            entries = []
            for component in components.findall("m:component", ns):
                entries.append((
                    component.attrib["objectid"],
                    component.attrib.get("{http://schemas.microsoft.com/3dmanufacturing/production/2015/06}path"),
                    parse_transform(component.attrib.get("transform")),
                ))
            objects[object_id] = ("components", entries)

    build = [
        (item.attrib["objectid"], parse_transform(item.attrib.get("transform")))
        for item in root.findall(".//m:build/m:item", ns)
    ]
    return objects, build


def convert_3mf(source: Path, target: Path) -> None:
    with zipfile.ZipFile(source) as archive:
        model_files = {
            name: parse_model_xml(archive.read(name))[0]
            for name in archive.namelist()
            if name.endswith(".model")
        }
        root_objects, build = parse_model_xml(archive.read("3D/3dmodel.model"))

        def resolve(object_id: str, path: str | None, transform: tuple[float, ...]):
            object_map = root_objects if path is None else model_files[path.lstrip("/")]
            item = object_map[object_id]
            if item[0] == "mesh":
                _, vertices, triangles = item
                for a, b, c in triangles:
                    yield (
                        transform_point(vertices[a], transform),
                        transform_point(vertices[b], transform),
                        transform_point(vertices[c], transform),
                    )
            else:
                _, components = item
                for child_id, child_path, child_transform in components:
                    yield from resolve(child_id, child_path, combine_transform(transform, child_transform))

        triangles = []
        for object_id, transform in build:
            triangles.extend(resolve(object_id, None, transform))
        write_binary_stl(target, source.stem, triangles)


def convert_3mf_build_items(source: Path, target_dir: Path) -> None:
    """Write each 3MF build item as a separate STL named after its Orca object.

    Flow Rate calibration depends on Orca object names like ``flowrate_m20``.
    Flattening the 3MF into one STL loses those names and therefore loses the
    per-object ``print_flow_ratio`` modifiers Orca applies during slicing.
    """
    with zipfile.ZipFile(source) as archive:
        model_files = {
            name: parse_model_xml(archive.read(name))[0]
            for name in archive.namelist()
            if name.endswith(".model")
        }
        root = ET.fromstring(archive.read("3D/3dmodel.model"))
        ns = {
            "m": "http://schemas.microsoft.com/3dmanufacturing/core/2015/02",
        }
        root_objects, build = parse_model_xml(archive.read("3D/3dmodel.model"))
        object_names = {
            obj.attrib["id"]: obj.attrib.get("name", f"object_{obj.attrib['id']}")
            for obj in root.findall(".//m:resources/m:object", ns)
        }

        def resolve(object_id: str, path: str | None, transform: tuple[float, ...]):
            object_map = root_objects if path is None else model_files[path.lstrip("/")]
            item = object_map[object_id]
            if item[0] == "mesh":
                _, vertices, triangles = item
                for a, b, c in triangles:
                    yield (
                        transform_point(vertices[a], transform),
                        transform_point(vertices[b], transform),
                        transform_point(vertices[c], transform),
                    )
            else:
                _, components = item
                for child_id, child_path, child_transform in components:
                    yield from resolve(child_id, child_path, combine_transform(transform, child_transform))

        target_dir.mkdir(parents=True, exist_ok=True)
        for stale_file in target_dir.glob("*.stl"):
            stale_file.unlink()
        for object_id, transform in build:
            object_name = object_names.get(object_id, f"object_{object_id}")
            triangles = list(resolve(object_id, None, transform))
            write_binary_stl(target_dir / f"{object_name}.stl", object_name, triangles)


def convert_drc(source: Path, target: Path) -> None:
    decoder = shutil.which("draco_decoder")
    if decoder is None:
        raise RuntimeError("draco_decoder is required to generate DRC calibration STL assets")
    with tempfile.TemporaryDirectory() as temp_dir:
        obj_path = Path(temp_dir) / f"{source.stem}.obj"
        subprocess.run([decoder, "-i", str(source), "-o", str(obj_path)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        vertices: list[tuple[float, float, float]] = []
        triangles = []
        for line in obj_path.read_text(encoding="utf-8").splitlines():
            if line.startswith("v "):
                _, x, y, z = line.split()[:4]
                vertices.append((float(x), float(y), float(z)))
            elif line.startswith("f "):
                parts = [part.split("/")[0] for part in line.split()[1:]]
                indices = [int(part) - 1 for part in parts]
                for i in range(1, len(indices) - 1):
                    triangles.append((vertices[indices[0]], vertices[indices[i]], vertices[indices[i + 1]]))
        write_binary_stl(target, source.stem, triangles)


def normal(a: tuple[float, float, float], b: tuple[float, float, float], c: tuple[float, float, float]) -> tuple[float, float, float]:
    ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    if length == 0:
        return (0.0, 0.0, 0.0)
    return (nx / length, ny / length, nz / length)


def write_ascii_stl(target: Path, name: str, triangles) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="ascii") as output:
        output.write(f"solid {name}\n")
        for a, b, c in triangles:
            nx, ny, nz = normal(a, b, c)
            output.write(f"  facet normal {nx:.7g} {ny:.7g} {nz:.7g}\n")
            output.write("    outer loop\n")
            for x, y, z in (a, b, c):
                output.write(f"      vertex {x:.7g} {y:.7g} {z:.7g}\n")
            output.write("    endloop\n")
            output.write("  endfacet\n")
        output.write(f"endsolid {name}\n")


def write_binary_stl(target: Path, name: str, triangles) -> None:
    triangles = list(triangles)
    target.parent.mkdir(parents=True, exist_ok=True)
    header = f"MobileSlicer Orca calibration asset: {name}".encode("ascii", "replace")[:80]
    header = header + b"\0" * (80 - len(header))
    with target.open("wb") as output:
        output.write(header)
        output.write(struct.pack("<I", len(triangles)))
        for a, b, c in triangles:
            output.write(struct.pack("<3f", *normal(a, b, c)))
            output.write(struct.pack("<3f", *a))
            output.write(struct.pack("<3f", *b))
            output.write(struct.pack("<3f", *c))
            output.write(struct.pack("<H", 0))


def main() -> int:
    for asset in ASSETS:
        if asset.source.suffix == ".3mf":
            convert_3mf(asset.source, asset.target)
        elif asset.source.suffix == ".drc":
            convert_drc(asset.source, asset.target)
        else:
            raise ValueError(asset.source)
        print(f"{asset.source.relative_to(ROOT)} -> {asset.target.relative_to(ROOT)}")
    for source, target_dir in FLOW_RATE_SPLIT_ASSETS:
        convert_3mf_build_items(source, target_dir)
        print(f"{source.relative_to(ROOT)} -> {target_dir.relative_to(ROOT)}/*.stl")
    return 0


if __name__ == "__main__":
    sys.exit(main())
