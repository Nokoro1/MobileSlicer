#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("printer_thumbnail_compatibility_audit.py")
ROOT = Path(__file__).resolve().parents[1]


class PrinterThumbnailCompatibilityAuditTest(unittest.TestCase):
    def test_current_matrix_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--pretty"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["ok"])

    def test_rejects_unproven_live_claim(self) -> None:
        matrix = json.loads(
            (ROOT / "regression-fixtures/printer-thumbnail-compatibility/matrix.json").read_text(
                encoding="utf-8"
            )
        )
        for entry in matrix["entries"]:
            if entry["id"] == "mainsail-moonraker-png":
                entry["status"] = "proven_live"
                entry["evidence"].pop("live_host", None)
                break

        with tempfile.TemporaryDirectory() as tmp:
            matrix_path = Path(tmp) / "matrix.json"
            matrix_path.write_text(json.dumps(matrix), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--matrix", str(matrix_path)],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

        self.assertNotEqual(result.returncode, 0)
        payload = json.loads(result.stdout)
        self.assertIn("mainsail-moonraker-png: proven_live entries require evidence.live_host", payload["failures"])

    def test_rejects_multi_plate_claim_without_plate_fixture_gate(self) -> None:
        matrix = json.loads(
            (ROOT / "regression-fixtures/printer-thumbnail-compatibility/matrix.json").read_text(
                encoding="utf-8"
            )
        )
        for entry in matrix["entries"]:
            if entry["id"] == "multi-plate-sliced-3mf":
                entry["evidence"]["gate"] = "sliced-3mf-metadata"
                break

        with tempfile.TemporaryDirectory() as tmp:
            matrix_path = Path(tmp) / "matrix.json"
            matrix_path.write_text(json.dumps(matrix), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--matrix", str(matrix_path)],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

        self.assertNotEqual(result.returncode, 0)
        payload = json.loads(result.stdout)
        self.assertIn(
            "multi-plate-sliced-3mf must use the plate_2 package fixture gate",
            payload["failures"],
        )

    def test_rejects_multi_plate_not_supported_after_fixture_gate(self) -> None:
        matrix = json.loads(
            (ROOT / "regression-fixtures/printer-thumbnail-compatibility/matrix.json").read_text(
                encoding="utf-8"
            )
        )
        for entry in matrix["entries"]:
            if entry["id"] == "multi-plate-sliced-3mf":
                entry["status"] = "not_supported"
                break

        with tempfile.TemporaryDirectory() as tmp:
            matrix_path = Path(tmp) / "matrix.json"
            matrix_path.write_text(json.dumps(matrix), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--matrix", str(matrix_path)],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

        self.assertNotEqual(result.returncode, 0)
        payload = json.loads(result.stdout)
        self.assertIn(
            "multi-plate-sliced-3mf now has a plate_2 package gate and must not remain not_supported",
            payload["failures"],
        )


if __name__ == "__main__":
    unittest.main()
