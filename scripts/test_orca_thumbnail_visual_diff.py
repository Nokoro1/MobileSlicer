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


SCRIPT = Path(__file__).with_name("orca_thumbnail_visual_diff.py")


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


def role_png(red: int = 180, green: int = 120, blue: int = 80) -> bytes:
    return png_rgba(
        4,
        4,
        [
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (red, green, blue, 255),
            (red, green, blue, 255),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (red, green, blue, 255),
            (red, green, blue, 255),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
            (0, 0, 0, 0),
        ],
    )


def role_png_8x8(red: int = 180, green: int = 120, blue: int = 80) -> bytes:
    pixels = [(0, 0, 0, 0)] * 64
    for y in range(2, 6):
        for x in range(2, 6):
            pixels[y * 8 + x] = (red, green, blue, 255)
    return png_rgba(8, 8, pixels)


def blank_png() -> bytes:
    return png_rgba(4, 4, [(0, 0, 0, 0)] * 16)


class OrcaThumbnailVisualDiffTest(unittest.TestCase):
    def test_compares_directory_role_pngs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"egl-{role}.png").write_bytes(role_png())
                (root / f"software-{role}.png").write_bytes(role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root),
                    "--mobile-prefix",
                    "egl-",
                    "--reference-prefix",
                    "software-",
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("pass", payload["status"])
            self.assertEqual(4, len(payload["role_diffs"]))

    def test_normalizes_bbox_and_coverage_across_thumbnail_sizes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"egl-{role}.png").write_bytes(role_png())
                (root / f"orca-{role}.png").write_bytes(role_png_8x8())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root),
                    "--mobile-prefix",
                    "egl-",
                    "--reference-prefix",
                    "orca-",
                    "--max-bbox-delta-px",
                    "0",
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("pass", payload["status"])
            self.assertEqual(1.0, payload["role_diffs"][0]["coverage_ratio"])

    def test_reports_missing_orca_reference_without_failing_by_default(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"egl-{role}.png").write_bytes(role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root / "missing-reference"),
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("reference_missing", payload["status"])
            self.assertEqual(["plate", "no_light", "top", "pick"], payload["missing_reference_roles"])

    def test_fails_when_reference_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"egl-{role}.png").write_bytes(role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root / "missing-reference"),
                    "--require-reference",
                ],
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing reference roles", result.stdout)

    def test_fails_blank_mobile_role(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"software-{role}.png").write_bytes(role_png())
                (root / f"egl-{role}.png").write_bytes(blank_png() if role == "pick" else role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root),
                    "--mobile-prefix",
                    "egl-",
                    "--reference-prefix",
                    "software-",
                ],
                text=True,
                stdout=subprocess.PIPE,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("pick: mobile role image is blank", result.stdout)

    def test_report_only_keeps_nonzero_findings_nonfatal(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for role in ("plate", "no_light", "top", "pick"):
                (root / f"software-{role}.png").write_bytes(role_png())
                (root / f"egl-{role}.png").write_bytes(blank_png() if role == "top" else role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(root),
                    "--reference-dir",
                    str(root),
                    "--mobile-prefix",
                    "egl-",
                    "--reference-prefix",
                    "software-",
                    "--report-only",
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("fail", payload["status"])
            self.assertTrue(payload["failures"])

    def test_reads_orca_style_3mf_thumbnail_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            mobile = root / "mobile"
            mobile.mkdir()
            for role in ("plate", "no_light", "top", "pick"):
                (mobile / f"egl-{role}.png").write_bytes(role_png())
            package = root / "orca.gcode.3mf"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("Metadata/plate_1.png", role_png())
                archive.writestr("Metadata/plate_no_light_1.png", role_png())
                archive.writestr("Metadata/top_1.png", role_png())
                archive.writestr("Metadata/pick_1.png", role_png())

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--mobile-dir",
                    str(mobile),
                    "--reference-dir",
                    str(package),
                ],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            )

            payload = json.loads(result.stdout)
            self.assertEqual("pass", payload["status"])


if __name__ == "__main__":
    unittest.main()
