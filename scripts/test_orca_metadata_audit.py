from __future__ import annotations

import base64
import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import orca_metadata_audit as audit


def png_rgba(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    if len(pixels) != width * height:
        raise ValueError("pixel count does not match dimensions")
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
        + chunk("IHDR".encode("ascii"), struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk("IDAT".encode("ascii"), compressed)
        + chunk("IEND".encode("ascii"), b"")
    )


def role_png(red: int, green: int, blue: int, alpha: int = 255) -> bytes:
    return png_rgba(
        2,
        2,
        [
            (0, 0, 0, 0),
            (red, green, blue, alpha),
            (max(0, red - 8), max(0, green - 8), max(0, blue - 8), alpha),
            (0, 0, 0, 0),
        ],
    )


def thumbnail_block(width: int, height: int, png: bytes) -> str:
    return tagged_thumbnail_block("thumbnail", width, height, png)


def tagged_thumbnail_block(tag: str, width: int, height: int, payload: bytes) -> str:
    encoded = base64.b64encode(payload).decode("ascii")
    return "\n".join(
        [
            "; THUMBNAIL_BLOCK_START",
            f"; {tag} begin {width}x{height} {len(encoded)}",
            f"; {encoded}",
            f"; {tag} end",
            "; THUMBNAIL_BLOCK_END",
        ]
    )


def legacy_thumbnail_block(width: int, height: int, png: bytes) -> str:
    encoded = base64.b64encode(png).decode("ascii")
    return "\n".join(
        [
            "; THUMBNAIL_BLOCK_START",
            f"; thumbnail begin {width}x{height} {len(encoded)}",
            f"; {encoded}",
            "; thumbnail end",
            "; THUMBNAIL_BLOCK_END",
        ]
    )


class OrcaMetadataAuditTest(unittest.TestCase):
    def test_inspect_gcode_reports_thumbnail_blocks_and_stripped_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mobile = root / "mobile.gcode"
            orca = root / "orca.gcode"
            mobile.write_text(
                "\n".join(
                    [
                        "; THUMBNAIL_BLOCK_START",
                        "; thumbnail begin 48x48 12",
                        "; abc",
                        "; thumbnail end",
                        "; THUMBNAIL_BLOCK_END",
                        "G1 X1 Y1 E1",
                        "; estimated print time = 00:01:00",
                        "; filament used [g] = 1.23",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            orca.write_text(
                "\n".join(
                    [
                        "; THUMBNAIL_BLOCK_START",
                        "; thumbnail begin 48x48 99",
                        "; abcdef",
                        "; thumbnail end",
                        "; THUMBNAIL_BLOCK_END",
                        "G1 X1 Y1 E1",
                        "; estimated print time = 00:01:00",
                        "; filament used [g] = 1.23",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            mobile_metadata = audit.inspect_gcode(mobile)
            orca_metadata = audit.inspect_gcode(orca)

            self.assertEqual(
                [audit.ThumbnailBlock(tag="thumbnail", width=48, height=48, encoded_size=12)],
                mobile_metadata.thumbnail_blocks,
            )
            self.assertEqual(mobile_metadata.stripped_sha256, orca_metadata.stripped_sha256)
            self.assertEqual(["; estimated print time = 00:01:00"], mobile_metadata.print_time_lines)
            self.assertEqual(["; filament used [g] = 1.23"], mobile_metadata.filament_lines)

    def test_inspect_gcode_reports_colpic_and_btt_payloads(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            gcode = root / "mobile.gcode"
            gcode.write_text(
                "\n".join(
                    [
                        ";gimage:abc123",
                        ";simage:def456",
                        ";00460046",
                        ";0000ffff",
                        ";ffff0000",
                        "; bigtree thumbnail end",
                        "G1 X1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            metadata = audit.inspect_gcode(gcode)

            self.assertEqual(
                [
                    audit.ThumbnailBlock(tag="gimage", width=None, height=None, encoded_size=6),
                    audit.ThumbnailBlock(tag="simage", width=None, height=None, encoded_size=6),
                    audit.ThumbnailBlock(tag="thumbnail_BTT", width=70, height=70, encoded_size=20),
                ],
                metadata.thumbnail_blocks,
            )

    def test_cli_requires_gcode_png_thumbnail_visuals(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "mobile.gcode"
            png = png_rgba(
                2,
                2,
                [
                    (0, 0, 0, 0),
                    (210, 80, 60, 255),
                    (160, 40, 30, 255),
                    (0, 0, 0, 0),
                ],
            )
            gcode.write_text(
                thumbnail_block(2, 2, png) + "\nG1 X1\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:2x2",
                    "--require-gcode-thumbnail-visuals",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            metrics = payload["gcode"]["mobile"]["thumbnail_image_metrics"]["thumbnail:2x2"]
            self.assertEqual(2, metrics["nontransparent_pixels"])
            self.assertEqual(2, metrics["transparent_pixels"])
            self.assertGreater(metrics["unique_rgba"], 1)

    def test_cli_rejects_flat_opaque_gcode_png_thumbnail_visuals(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "mobile.gcode"
            png = png_rgba(
                2,
                2,
                [
                    (120, 120, 120, 255),
                    (120, 120, 120, 255),
                    (120, 120, 120, 255),
                    (120, 120, 120, 255),
                ],
            )
            gcode.write_text(
                thumbnail_block(2, 2, png) + "\nG1 X1\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:2x2",
                    "--require-gcode-thumbnail-visuals",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            checks = [failure["check"] for failure in payload["failures"]]
            self.assertIn("gcode-thumbnail-visuals", checks)

    def test_cli_requires_gcode_thumbnail_antialias_variation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "mobile.gcode"
            png = png_rgba(
                3,
                2,
                [
                    (0, 0, 0, 0),
                    (210, 80, 60, 255),
                    (180, 60, 45, 190),
                    (80, 20, 10, 90),
                    (40, 10, 5, 40),
                    (0, 0, 0, 0),
                ],
            )
            gcode.write_text(thumbnail_block(3, 2, png) + "\nG1 X1\n", encoding="utf-8")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:3x2",
                    "--require-gcode-thumbnail-visuals",
                    "--require-gcode-thumbnail-antialias",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)

    def test_cli_rejects_gcode_thumbnail_without_antialias_variation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "mobile.gcode"
            png = png_rgba(
                2,
                2,
                [
                    (0, 0, 0, 0),
                    (210, 80, 60, 255),
                    (210, 80, 60, 255),
                    (0, 0, 0, 0),
                ],
            )
            gcode.write_text(thumbnail_block(2, 2, png) + "\nG1 X1\n", encoding="utf-8")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:2x2",
                    "--require-gcode-thumbnail-visuals",
                    "--require-gcode-thumbnail-antialias",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            checks = [failure["check"] for failure in payload["failures"]]
            self.assertIn("gcode-thumbnail-antialias", checks)

    def test_inspect_gcode_reports_colpic_payload(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "qidi.gcode"
            gcode.write_text(";gimage:abc123\n;simage:def456\nG1 X0\n", encoding="utf-8")

            metadata = audit.inspect_gcode(gcode)

            self.assertEqual(
                [
                    audit.ThumbnailBlock(tag="gimage", width=None, height=None, encoded_size=6),
                    audit.ThumbnailBlock(tag="simage", width=None, height=None, encoded_size=6),
                ],
                metadata.thumbnail_blocks,
            )

    def test_inspect_gcode_reports_commented_toolchange_commands(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "multi.gcode"
            gcode.write_text(
                "\n".join(
                    [
                        "; retract_length_toolchange = 10",
                        "T0 ; change extruder",
                        "G1 X1",
                        "T1 ; change extruder",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            metadata = audit.inspect_gcode(gcode)

            self.assertEqual(["T0 ; change extruder", "T1 ; change extruder"], metadata.toolchange_lines)

    def test_inspect_gcode_reports_btt_tft_header(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "btt.gcode"
            gcode.write_text(";00600040\r\n;FFFF0000\r\n; bigtree thumbnail end\r\nG1 X0\n", encoding="utf-8")

            metadata = audit.inspect_gcode(gcode)

            self.assertEqual(
                [audit.ThumbnailBlock(tag="thumbnail_BTT", width=96, height=64, encoded_size=10)],
                metadata.thumbnail_blocks,
            )

    def test_cli_validates_jpg_thumbnail_payload_signature(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "jpg.gcode"
            gcode.write_text(
                tagged_thumbnail_block("thumbnail_JPG", 96, 96, b"\xff\xd8jpeg-payload\xff\xd9") + "\nG1 X0\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail_JPG:96x96",
                    "--require-gcode-thumbnail-payload-signatures",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            metrics = payload["gcode"]["mobile"]["thumbnail_image_metrics"]["thumbnail_JPG:96x96"]
            self.assertEqual("JPG", metrics["format"])

    def test_cli_rejects_mislabeled_jpg_thumbnail_payload(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "jpg.gcode"
            gcode.write_text(
                tagged_thumbnail_block("thumbnail_JPG", 96, 96, b"not-a-jpeg") + "\nG1 X0\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail_JPG:96x96",
                    "--require-gcode-thumbnail-payload-signatures",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("thumbnail-payload-signature", payload["failures"][0]["check"])

    def test_cli_validates_qoi_thumbnail_payload_signature(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "qoi.gcode"
            qoi_payload = b"qoif" + struct.pack(">IIBB", 96, 96, 4, 0) + b"\x00\x00\x00\x00\x00\x00\x00\x01"
            gcode.write_text(
                tagged_thumbnail_block("thumbnail_QOI", 96, 96, qoi_payload) + "\nG1 X0\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail_QOI:96x96",
                    "--require-gcode-thumbnail-payload-signatures",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)
            payload = json.loads(result.stdout)
            metrics = payload["gcode"]["mobile"]["thumbnail_image_metrics"]["thumbnail_QOI:96x96"]
            self.assertEqual("QOI", metrics["format"])

    def test_inspect_3mf_reports_thumbnail_and_relationship_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", role_png(120, 160, 200))
                archive.writestr("Metadata/plate_1_small.png", b"png")
                archive.writestr("Metadata/plate_1.gcode", b"G1 X1\n")
                archive.writestr("Metadata/plate_1.gcode.md5", b"checksum")
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
                archive.writestr("_rels/.rels", "<Relationships />")
                archive.writestr("3D/3dmodel.model", "<model />")

            metadata = audit.inspect_3mf(package)

            self.assertEqual(
                [
                    "Metadata/plate_1.png",
                    "Metadata/plate_1_small.png",
                ],
                metadata["thumbnail_entries"],
            )
            self.assertEqual(["_rels/.rels"], metadata["relationship_entries"])
            self.assertEqual([], metadata["relationship_targets"])
            self.assertEqual(["Metadata/plate_1.json"], metadata["json_entries"])
            self.assertIn("bbox_objects", metadata["json_fields"]["Metadata/plate_1.json"])
            self.assertIn("Metadata/plate_1.png", metadata["entry_sha256"])
            self.assertIn("Metadata/plate_1.gcode", metadata["entry_sha256"])
            self.assertIn("Metadata/plate_1.json", metadata["entry_sha256"])
            plate_metrics = metadata["thumbnail_image_metrics"]["Metadata/plate_1.png"]
            self.assertEqual(2, plate_metrics["width"])
            self.assertEqual(2, plate_metrics["height"])
            self.assertEqual(2, plate_metrics["nontransparent_pixels"])
            self.assertEqual([0, 0, 1, 1], plate_metrics["bbox"])

    def test_inspect_3mf_reports_relationship_targets(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", b"png")
                archive.writestr(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Target="/Metadata/plate_1.png" Id="rel-2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"/>
 <Relationship Target="/Metadata/plate_1_small.png" Id="rel-5" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-small"/>
</Relationships>""",
                )

            metadata = audit.inspect_3mf(package)

            self.assertEqual(
                ["/Metadata/plate_1.png", "/Metadata/plate_1_small.png"],
                metadata["relationship_targets"],
            )

    def test_cli_compares_mobile_and_orca_gcode(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mobile = root / "mobile.gcode"
            orca = root / "orca.gcode"
            body = "G1 X1 Y1 E1\n"
            mobile.write_text(body, encoding="utf-8")
            orca.write_text(body, encoding="utf-8")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(mobile),
                    "--orca-gcode",
                    str(orca),
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertIs(payload["gcode"]["comparison"]["stripped_body_hash_match"], True)
            self.assertIs(payload["gcode"]["comparison"]["thumbnail_count_match"], True)

    def test_cli_fails_when_expected_thumbnail_signature_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "mobile.gcode"
            gcode.write_text(
                "\n".join(
                    [
                        "; THUMBNAIL_BLOCK_START",
                        "; thumbnail begin 96x96 12",
                        "; abc",
                        "; thumbnail end",
                        "; THUMBNAIL_BLOCK_END",
                        "G1 X1",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:150x150",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("thumbnail-signature", payload["failures"][0]["check"])

    def test_cli_passes_qidi_q2_orca_png_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            gcode = Path(temp_dir) / "qidi_q2.gcode"
            gcode.write_text(
                "\n".join(
                    [
                        "; THUMBNAIL_BLOCK_START",
                        "; thumbnail begin 150x150 12",
                        "; abc",
                        "; thumbnail end",
                        "; THUMBNAIL_BLOCK_END",
                        "G1 X1",
                        "; estimated printing time (normal mode) = 1m 2s",
                        "; filament used [g] = 1.23",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-gcode",
                    str(gcode),
                    "--expect-thumbnail",
                    "thumbnail:150x150",
                    "--forbid-thumbnail-tag",
                    "gimage",
                    "--forbid-thumbnail-tag",
                    "simage",
                    "--require-print-time",
                    "--require-filament",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)

    def test_cli_fails_when_expected_3mf_thumbnail_entry_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", b"png")
                archive.writestr("_rels/.rels", "<Relationships />")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1_small.png",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-thumbnail-entry", payload["failures"][0]["check"])

    def test_cli_fails_when_expected_3mf_bbox_json_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", b"png")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-bbox-json",
                    "Metadata/plate_1.json",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-bbox-json", payload["failures"][0]["check"])

    def test_cli_fails_when_expected_3mf_bbox_json_is_incomplete(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.json", json.dumps({"bbox_all": [0, 0, 1, 1]}))

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-bbox-json",
                    "Metadata/plate_1.json",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-bbox-json-fields", payload["failures"][0]["check"])

    def test_cli_fails_when_required_3mf_thumbnail_entries_are_identical(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", b"same")
                archive.writestr("Metadata/top_1.png", b"same")

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/top_1.png",
                    "--require-distinct-3mf-thumbnail-entries",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-distinct-thumbnail-entries", payload["failures"][0]["check"])

    def test_cli_fails_when_required_3mf_thumbnail_visual_is_blank(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr(
                    "Metadata/plate_1.png",
                    png_rgba(2, 2, [(0, 0, 0, 0)] * 4),
                )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1.png",
                    "--require-3mf-thumbnail-visuals",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-thumbnail-visual-blank", payload["failures"][0]["check"])

    def test_cli_fails_when_required_3mf_thumbnail_visual_is_flat(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr(
                    "Metadata/plate_1.png",
                    png_rgba(
                        2,
                        2,
                        [
                            (0, 0, 0, 0),
                            (120, 180, 220, 255),
                            (120, 180, 220, 255),
                            (0, 0, 0, 0),
                        ],
                    ),
                )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1.png",
                    "--require-3mf-thumbnail-visuals",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual(1, result.returncode)
            self.assertEqual("3mf-thumbnail-visual-variation", payload["failures"][0]["check"])

    def test_cli_passes_orca_sliced_3mf_thumbnail_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "slice.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", role_png(150, 205, 250))
                archive.writestr("Metadata/plate_1_small.png", role_png(150, 205, 250))
                archive.writestr("Metadata/plate_no_light_1.png", role_png(230, 245, 255))
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
 <Relationship Target="/Metadata/plate_1.png" Id="rel-2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"/>
 <Relationship Target="/Metadata/plate_1.png" Id="rel-4" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-middle"/>
 <Relationship Target="/Metadata/plate_1_small.png" Id="rel-5" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-small"/>
</Relationships>""",
                )

            result = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("orca_metadata_audit.py")),
                    "--mobile-3mf",
                    str(package),
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_1_small.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/plate_no_light_1.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/top_1.png",
                    "--expect-3mf-thumbnail-entry",
                    "Metadata/pick_1.png",
                    "--expect-3mf-relationship-target",
                    "/Metadata/plate_1.png",
                    "--expect-3mf-relationship-target",
                    "/Metadata/plate_1_small.png",
                    "--expect-3mf-entry",
                    "Metadata/plate_1.json",
                    "--expect-3mf-bbox-json",
                    "Metadata/plate_1.json",
                    "--require-distinct-3mf-thumbnail-entries",
                    "--require-3mf-thumbnail-visuals",
                ],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertEqual(0, result.returncode, result.stdout)


if __name__ == "__main__":
    unittest.main()
