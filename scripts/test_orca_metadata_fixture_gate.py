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


SCRIPT = Path(__file__).with_name("orca_metadata_fixture_gate.py")


def write_manifest(root: Path, manifest: dict[str, object]) -> Path:
    path = root / "manifest.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    return path


def run_gate(root: Path, manifest: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--repo-root",
            str(root),
            "--manifest",
            str(manifest),
            *args,
        ],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def minimal_gcode() -> str:
    return "\n".join(
        [
            "; THUMBNAIL_BLOCK_START",
            "; thumbnail begin 128x128 12",
            "; abc",
            "; thumbnail end",
            "; THUMBNAIL_BLOCK_END",
            "G1 X1 Y1 E1",
            "; estimated printing time (normal mode) = 1m 2s",
            "; filament used [g] = 1.23",
        ]
    ) + "\n"


def png_rgba(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    raw_rows = bytearray()
    for y in range(height):
        raw_rows.append(0)
        for pixel in pixels[y * width:(y + 1) * width]:
            raw_rows.extend(pixel)
    compressed = zlib.compress(bytes(raw_rows))

    def chunk(name: bytes, payload: bytes) -> bytes:
        crc = zlib.crc32(name)
        crc = zlib.crc32(payload, crc) & 0xffffffff
        return struct.pack(">I", len(payload)) + name + payload + struct.pack(">I", crc)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", compressed)
        + chunk(b"IEND", b"")
    )


def role_png(red: int, green: int, blue: int) -> bytes:
    return png_rgba(
        2,
        2,
        [
            (0, 0, 0, 0),
            (red, green, blue, 255),
            (max(0, red - 8), max(0, green - 8), max(0, blue - 8), 255),
            (0, 0, 0, 0),
        ],
    )


def write_valid_3mf(path: Path) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("Metadata/plate_1.png", role_png(150, 205, 250))
        archive.writestr("Metadata/plate_1_small.png", role_png(150, 205, 250))
        archive.writestr("Metadata/plate_no_light_1.png", role_png(90, 125, 160))
        archive.writestr("Metadata/top_1.png", role_png(140, 190, 250))
        archive.writestr("Metadata/pick_1.png", role_png(255, 32, 32))
        archive.writestr(
            "Metadata/plate_1.json",
            json.dumps(
                {
                    "bbox_all": [0, 0, 10, 10],
                    "bbox_objects": [
                        {
                            "id": 1,
                            "bbox": [0, 0, 10, 10],
                            "area": 100,
                            "layer_height": 0.2,
                            "name": "cube",
                        }
                    ],
                    "filament_ids": [0],
                    "filament_colors": ["#FFFFFF"],
                    "is_seq_print": False,
                    "first_extruder": 0,
                    "nozzle_diameter": 0.4,
                    "bed_type": "textured_plate",
                    "first_layer_time": 12.3,
                    "version": 2,
                }
            ),
        )
        archive.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Target="/Metadata/plate_1.png" Id="rel-1" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"/>
 <Relationship Target="/Metadata/plate_1_small.png" Id="rel-2" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-small"/>
</Relationships>""",
        )


class OrcaMetadataFixtureGateTest(unittest.TestCase):
    def test_non_strict_skips_missing_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "missing",
                            "kind": "gcode",
                            "paths": {
                                "mobile_gcode": "mobile/missing.gcode",
                                "orca_gcode": "references/missing.gcode",
                            },
                            "expectations": {"require_stripped_hash_match": True},
                        }
                    ],
                },
            )

            result = run_gate(root, manifest)

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual("skipped", payload["cases"][0]["status"])
            self.assertEqual(1, payload["summary"]["skipped"])

    def test_strict_fails_missing_orca_reference(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "missing-ref",
                            "kind": "gcode",
                            "paths": {"orca_gcode": "references/missing.gcode"},
                            "expectations": {},
                        }
                    ],
                },
            )

            result = run_gate(root, manifest, "--strict-references")

            self.assertEqual(1, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual("failed", payload["cases"][0]["status"])
            self.assertEqual("missing-orca-reference", payload["cases"][0]["failures"][0]["check"])

    def test_strict_fails_missing_mobile_output(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "missing-mobile",
                            "kind": "gcode",
                            "paths": {"mobile_gcode": "mobile/missing.gcode"},
                            "expectations": {},
                        }
                    ],
                },
            )

            result = run_gate(root, manifest, "--strict-references")

            self.assertEqual(1, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual("failed", payload["cases"][0]["status"])
            self.assertEqual("missing-mobile-output", payload["cases"][0]["failures"][0]["check"])

    def test_strict_fails_pending_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "pending-case",
                            "kind": "gcode",
                            "status": "pending",
                            "paths": {},
                            "expectations": {},
                        }
                    ],
                },
            )

            result = run_gate(root, manifest, "--strict-references")

            self.assertEqual(1, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual("failed", payload["cases"][0]["status"])
            self.assertEqual("pending-fixture", payload["cases"][0]["failures"][0]["check"])

    def test_present_gcode_validates_metadata_and_hash_match(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "mobile").mkdir()
            (root / "references").mkdir()
            (root / "mobile/out.gcode").write_text(minimal_gcode(), encoding="utf-8")
            (root / "references/out.gcode").write_text(minimal_gcode(), encoding="utf-8")
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "gcode",
                            "kind": "gcode",
                            "paths": {
                                "mobile_gcode": "mobile/out.gcode",
                                "orca_gcode": "references/out.gcode",
                            },
                            "expectations": {
                                "expect_thumbnails": ["thumbnail:128x128"],
                                "require_print_time": True,
                                "require_filament": True,
                                "require_stripped_hash_match": True,
                            },
                        }
                    ],
                },
            )

            result = run_gate(root, manifest, "--strict-references")

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertTrue(payload["cases"][0]["comparison"]["stripped_body_hash_match"])

    def test_stripped_hash_mismatch_fails_when_required(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "mobile").mkdir()
            (root / "references").mkdir()
            (root / "mobile/out.gcode").write_text(minimal_gcode() + "G1 X2\n", encoding="utf-8")
            (root / "references/out.gcode").write_text(minimal_gcode(), encoding="utf-8")
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "gcode",
                            "kind": "gcode",
                            "paths": {
                                "mobile_gcode": "mobile/out.gcode",
                                "orca_gcode": "references/out.gcode",
                            },
                            "expectations": {"require_stripped_hash_match": True},
                        }
                    ],
                },
            )

            result = run_gate(root, manifest)

            self.assertEqual(1, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual("stripped-body-hash", payload["cases"][0]["failures"][0]["check"])

    def test_present_3mf_validates_entries_bbox_and_distinct_thumbnails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "mobile").mkdir()
            package = root / "mobile/out.gcode.3mf"
            write_valid_3mf(package)
            manifest = write_manifest(
                root,
                {
                    "schema_version": 1,
                    "cases": [
                        {
                            "id": "package",
                            "kind": "3mf",
                            "paths": {"mobile_3mf": "mobile/out.gcode.3mf"},
                            "expectations": {
                                "expect_3mf_entries": ["Metadata/plate_1.json"],
                                "expect_3mf_thumbnail_entries": [
                                    "Metadata/plate_1.png",
                                    "Metadata/plate_1_small.png",
                                    "Metadata/plate_no_light_1.png",
                                    "Metadata/top_1.png",
                                    "Metadata/pick_1.png",
                                ],
                                "expect_3mf_relationship_targets": [
                                    "/Metadata/plate_1.png",
                                    "/Metadata/plate_1_small.png",
                                ],
                                "expect_3mf_bbox_json": ["Metadata/plate_1.json"],
                                "require_distinct_3mf_thumbnail_entries": True,
                                "require_3mf_thumbnail_visuals": True,
                            },
                        }
                    ],
                },
            )

            result = run_gate(root, manifest)

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            self.assertEqual(0, payload["summary"]["failures"])


if __name__ == "__main__":
    unittest.main()
