from __future__ import annotations

import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path


SCRIPT = Path(__file__).with_name("extract_orca_thumbnail_references.py")


def png_rgba(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    raw_rows = bytearray()
    for y in range(height):
        raw_rows.append(0)
        for pixel in pixels[y * width:(y + 1) * width]:
            raw_rows.extend(pixel)
    compressed = zlib.compress(bytes(raw_rows))

    def chunk(name: bytes, payload: bytes) -> bytes:
        crc = zlib.crc32(name)
        crc = zlib.crc32(payload, crc) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + name + payload + struct.pack(">I", crc)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", compressed)
        + chunk(b"IEND", b"")
    )


def role_png(red: int) -> bytes:
    return png_rgba(
        2,
        2,
        [
            (0, 0, 0, 0),
            (red, 80, 120, 255),
            (red, 80, 120, 255),
            (0, 0, 0, 0),
        ],
    )


def write_required_roles(archive: zipfile.ZipFile) -> None:
    archive.writestr("Metadata/plate_1.png", role_png(100))
    archive.writestr("Metadata/plate_no_light_1.png", role_png(90))
    archive.writestr("Metadata/top_1.png", role_png(110))
    archive.writestr("Metadata/pick_1.png", role_png(120))


def write_multifilament_metadata(archive: zipfile.ZipFile, active_filaments: int) -> None:
    ids = list(range(active_filaments))
    colors = ["#F2754E", "#4EA3F2"][:active_filaments]
    archive.writestr(
        "Metadata/plate_1.json",
        json.dumps(
            {
                "filament_ids": ids,
                "filament_colors": colors,
            }
        ),
    )
    filament_xml = "\n".join(
        f'    <filament id="{index + 1}" type="PLA" color="{color}" used_m="1.0" />'
        for index, color in enumerate(colors)
    )
    archive.writestr(
        "Metadata/slice_info.config",
        f"""<?xml version="1.0" encoding="UTF-8"?>
<config>
  <plate>
{filament_xml}
  </plate>
</config>
""",
    )
    object_xml = "\n".join(
        f"""  <object id="{index + 1}">
    <metadata key="extruder" value="{index + 1}"/>
  </object>"""
        for index in range(active_filaments)
    )
    archive.writestr(
        "Metadata/model_settings.config",
        f"""<?xml version="1.0" encoding="UTF-8"?>
<config>
{object_xml}
</config>
""",
    )


class ExtractOrcaThumbnailReferencesTest(unittest.TestCase):
    def test_extracts_required_role_pngs_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "desktop-orca.gcode.3mf"
            output = root / "refs"
            with zipfile.ZipFile(source, "w") as archive:
                write_required_roles(archive)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--source-3mf",
                    str(source),
                    "--output-dir",
                    str(output),
                    "--orca-build",
                    "test-build",
                    "--model",
                    "cube.stl",
                    "--printer-profile",
                    "Qidi Q2",
                    "--filament-profile",
                    "Generic PLA",
                    "--process-profile",
                    "0.20mm Standard",
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("test-build", payload["orca_build"])
            self.assertEqual({"plate.png", "no_light.png", "top.png", "pick.png"}, {path.name for path in output.glob("*.png")})
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(4, len(manifest["roles"]))
            self.assertEqual("single", manifest["source_layout"])
            self.assertEqual(0, manifest["requirements"]["active_filaments"])
            self.assertTrue((output / "desktop-orca.gcode.3mf").is_file())
            self.assertTrue((output / "README.md").is_file())

    def test_requires_active_multifilament_metadata_when_requested(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "desktop-orca.gcode.3mf"
            output = root / "refs"
            with zipfile.ZipFile(source, "w") as archive:
                write_required_roles(archive)
                write_multifilament_metadata(archive, active_filaments=2)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--source-3mf",
                    str(source),
                    "--output-dir",
                    str(output),
                    "--orca-build",
                    "test-build",
                    "--model",
                    "two-cubes.stl",
                    "--printer-profile",
                    "Bambu P1P",
                    "--filament-profile",
                    "PLA Red, PLA Blue",
                    "--process-profile",
                    "0.20mm Standard",
                    "--source-layout",
                    "two_filament_objects",
                    "--source-colors",
                    "#F2754E,#4EA3F2",
                    "--require-active-filaments",
                    "2",
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("two_filament_objects", payload["source_layout"])
            self.assertEqual(2, payload["requirements"]["active_filaments"])
            self.assertEqual(["0", "1"], payload["package_metadata"]["plate_json_filament_ids"])
            self.assertEqual(["1", "2"], payload["package_metadata"]["slice_info_filament_ids"])
            self.assertEqual(["1", "2"], payload["package_metadata"]["model_object_extruders"])

    def test_rejects_collapsed_multifilament_metadata_when_requested(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "desktop-orca.gcode.3mf"
            output = root / "refs"
            with zipfile.ZipFile(source, "w") as archive:
                write_required_roles(archive)
                write_multifilament_metadata(archive, active_filaments=1)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--source-3mf",
                    str(source),
                    "--output-dir",
                    str(output),
                    "--orca-build",
                    "test-build",
                    "--model",
                    "two-cubes.stl",
                    "--printer-profile",
                    "Bambu P1P",
                    "--filament-profile",
                    "PLA Red, PLA Blue",
                    "--process-profile",
                    "0.20mm Standard",
                    "--source-layout",
                    "two_filament_objects",
                    "--require-active-filaments",
                    "2",
                ],
                text=True,
                stderr=subprocess.PIPE,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("does not prove active multi-filament metadata", result.stderr)

    def test_fails_when_required_role_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "desktop-orca.gcode.3mf"
            output = root / "refs"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("Metadata/plate_1.png", role_png(100))

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--source-3mf",
                    str(source),
                    "--output-dir",
                    str(output),
                    "--orca-build",
                    "test-build",
                    "--model",
                    "cube.stl",
                    "--printer-profile",
                    "Qidi Q2",
                    "--filament-profile",
                    "Generic PLA",
                    "--process-profile",
                    "0.20mm Standard",
                ],
                text=True,
                stderr=subprocess.PIPE,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing required Orca thumbnail entries", result.stderr)


if __name__ == "__main__":
    unittest.main()
