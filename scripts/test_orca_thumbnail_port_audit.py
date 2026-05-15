#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("orca_thumbnail_port_audit.py")


def write(root: Path, relative: str, content: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class OrcaThumbnailPortAuditTest(unittest.TestCase):
    def test_current_repository_contract_passes(self) -> None:
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
        self.assertGreaterEqual(payload["summary"]["checked"], 10)

    def test_reports_missing_source_contract(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.hpp", "")
            write(root, "vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp", "")
            write(root, "vendor/orcaslicer/src/OrcaSlicer.cpp", "")
            write(root, "vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp", "")
            write(root, "android-app/app/src/main/cpp/CMakeLists.txt", "")
            write(root, "android-app/app/src/main/java/com/mobileslicer/MainActivitySlicing.kt", "")
            write(root, "android-app/app/src/main/java/com/mobileslicer/automation/AutomationSliceRunner.kt", "")
            write(root, "scripts/verify_android.sh", "")

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(root)],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

        self.assertNotEqual(result.returncode, 0)
        payload = json.loads(result.stdout)
        self.assertFalse(payload["ok"])
        self.assertIn("orca-framebuffer-entry", payload["failures"])
        self.assertIn("production-egl-renderer", payload["failures"])


if __name__ == "__main__":
    unittest.main()
