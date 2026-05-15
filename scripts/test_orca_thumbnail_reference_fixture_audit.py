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


SCRIPT = Path(__file__).with_name("orca_thumbnail_reference_fixture_audit.py")


def run_script(root: Path, matrix: Path, *extra: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--repo-root",
            str(root),
            "--matrix",
            str(matrix),
            *extra,
        ],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


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
    pixels = [(red, 80, 120, 255) for _ in range(512 * 512)]
    return png_rgba(512, 512, pixels)


def write_fixture_reference(root: Path, active_filaments: int) -> None:
    (root / "model.stl").write_text("solid model\nendsolid model\n", encoding="utf-8")
    reference_dir = root / "refs" / "case-a"
    reference_dir.mkdir(parents=True)
    package = reference_dir / "case-a.gcode.3mf"
    roles = {
        "plate": ("Metadata/plate_1.png", role_png(100)),
        "no_light": ("Metadata/plate_no_light_1.png", role_png(90)),
        "top": ("Metadata/top_1.png", role_png(110)),
        "pick": ("Metadata/pick_1.png", role_png(120)),
    }
    for role, (_, payload) in roles.items():
        (reference_dir / f"{role}.png").write_bytes(payload)
    ids = list(range(active_filaments))
    colors = ["#F2754E", "#4EA3F2"][:active_filaments]
    with zipfile.ZipFile(package, "w") as archive:
        for _, (entry, payload) in roles.items():
            archive.writestr(entry, payload)
        archive.writestr("Metadata/plate_1.json", json.dumps({"filament_ids": ids, "filament_colors": colors}))
        archive.writestr(
            "Metadata/slice_info.config",
            "<config><plate>"
            + "".join(
                f'<filament id="{index + 1}" color="{color}" />'
                for index, color in enumerate(colors)
            )
            + "</plate></config>",
        )
        archive.writestr(
            "Metadata/model_settings.config",
            "<config>"
            + "".join(
                f'<object id="{index + 1}"><metadata key="extruder" value="{index + 1}"/></object>'
                for index in range(active_filaments)
            )
            + "</config>",
        )
    manifest = {
        "schema_version": 1,
        "source_3mf": str(package),
        "orca_build": "test-build",
        "source_layout": "two_filament_objects",
        "requirements": {"active_filaments": 2},
        "roles": [
            {"role": role, "source_entry": entry, "output_file": str(reference_dir / f"{role}.png")}
            for role, (entry, _) in roles.items()
        ],
    }
    (reference_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")


class OrcaThumbnailReferenceFixtureAuditTest(unittest.TestCase):
    def test_list_shell_reads_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            matrix = root / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "cases": [
                            {
                                "id": "case-a",
                                "model": "model.stl",
                                "reference_dir": "refs/case-a",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = run_script(root, matrix, "--list-shell")

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(
                f"case-a\t{root / 'model.stl'}\t{root / 'refs/case-a'}\t\t\t\t\t\t\t270\t270\t256\tsingle",
                result.stdout.strip(),
            )

    def test_list_shell_includes_case_profiles_and_bed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "profiles").mkdir()
            for name in ("machine.json", "process.json", "filament.json"):
                (root / "profiles" / name).write_text("{}", encoding="utf-8")
            matrix = root / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "cases": [
                            {
                                "id": "case-a",
                                "model": "model.stl",
                                "reference_dir": "refs/case-a",
                                "bed": {"width_mm": 220, "depth_mm": 221, "height_mm": 250},
                                "profiles": {
                                    "machine": "profiles/machine.json",
                                    "process": "profiles/process.json",
                                    "filament": "profiles/filament.json",
                                    "printer_name": "Printer",
                                    "process_name": "Process",
                                    "filament_name": "Filament",
                                },
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = run_script(root, matrix, "--list-shell")

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(
                "\t".join(
                    [
                        "case-a",
                        str(root / "model.stl"),
                        str(root / "refs/case-a"),
                        str(root / "profiles/machine.json"),
                        str(root / "profiles/process.json"),
                        str(root / "profiles/filament.json"),
                        "Printer",
                        "Process",
                        "Filament",
                        "220",
                        "221",
                        "250",
                        "single",
                    ]
                ),
                result.stdout.strip(),
            )

    def test_missing_fixture_fails_with_actionable_checks(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            matrix = root / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "cases": [
                            {
                                "id": "missing",
                                "model": "missing.stl",
                                "reference_dir": "missing-ref",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = run_script(root, matrix)

            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            payload = json.loads(result.stdout)
            checks = {failure["check"] for failure in payload["failures"]}
            self.assertIn("missing-model", checks)
            self.assertIn("missing-reference-dir", checks)

    def test_two_filament_reference_requires_active_package_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_fixture_reference(root, active_filaments=1)
            matrix = root / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "cases": [
                            {
                                "id": "case-a",
                                "model": "model.stl",
                                "reference_dir": "refs/case-a",
                                "source_layout": "two_filament_objects",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = run_script(root, matrix)

            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            payload = json.loads(result.stdout)
            checks = {failure["check"] for failure in payload["failures"]}
            self.assertIn("active-filament-metadata", checks)

    def test_two_filament_reference_passes_when_package_metadata_is_active(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_fixture_reference(root, active_filaments=2)
            matrix = root / "matrix.json"
            matrix.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "cases": [
                            {
                                "id": "case-a",
                                "model": "model.stl",
                                "reference_dir": "refs/case-a",
                                "source_layout": "two_filament_objects",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = run_script(root, matrix)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            payload = json.loads(result.stdout)
            self.assertEqual(
                ["1", "2"],
                payload["cases"][0]["active_filament_metadata"]["model_object_extruders"],
            )

    def test_real_checked_in_matrix_passes(self) -> None:
        root = Path(__file__).resolve().parents[1]
        matrix = root / "regression-fixtures/orca-thumbnail-references/matrix.json"

        result = run_script(root, matrix)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["ok"])
        self.assertGreaterEqual(payload["summary"]["cases"], 1)


if __name__ == "__main__":
    unittest.main()
