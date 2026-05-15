#!/usr/bin/env python3
import unittest
from pathlib import Path
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from orca_metadata_audit import png_image_metrics


ROOT_DIR = Path(__file__).resolve().parents[1]


def ascii_stl_bounds(path: Path):
    xs = []
    ys = []
    zs = []
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.strip().split()
        if len(parts) == 4 and parts[0] == "vertex":
            x, y, z = (float(value) for value in parts[1:])
            xs.append(x)
            ys.append(y)
            zs.append(z)
    if not xs:
        raise AssertionError(f"{path} contains no ASCII STL vertices")
    return (min(xs), max(xs)), (min(ys), max(ys)), (min(zs), max(zs))


class RegressionFixtureTest(unittest.TestCase):
    def test_support_release_fixture_has_non_degenerate_support_depth(self):
        _, y_bounds, z_bounds = ascii_stl_bounds(
            ROOT_DIR / "regression-fixtures/slicing" / "stage2_bridge_speed_fixture.stl"
        )

        self.assertGreaterEqual(y_bounds[1] - y_bounds[0], 20.0)
        self.assertGreater(z_bounds[1] - z_bounds[0], 10.0)

    def test_orca_thumbnail_reference_cases_have_required_roles(self):
        reference_root = ROOT_DIR / "regression-fixtures/orca-thumbnail-references"
        cases = {
            "simple-cube",
            "tall-box",
            "small-perimeter",
            "perimeter-array",
            "narrow-strip",
            "bridge-speed",
        }
        roles = {"plate", "no_light", "top", "pick"}

        for case in cases:
            with self.subTest(case=case):
                case_dir = reference_root / case
                self.assertTrue(case_dir.is_dir(), f"{case_dir} missing")
                manifest = json.loads((case_dir / "manifest.json").read_text(encoding="utf-8"))
                self.assertEqual(1, manifest["schema_version"])
                self.assertTrue(Path(manifest["source_3mf"]).name.endswith(".gcode.3mf"))
                self.assertEqual(roles, {entry["role"] for entry in manifest["roles"]})
                for role in roles:
                    png_path = case_dir / f"{role}.png"
                    self.assertTrue(png_path.is_file(), f"{png_path} missing")
                    metrics = png_image_metrics(png_path.read_bytes())
                    self.assertEqual(512, metrics.width)
                    self.assertEqual(512, metrics.height)
                    self.assertGreater(metrics.nontransparent_pixels, 0)


if __name__ == "__main__":
    unittest.main()
