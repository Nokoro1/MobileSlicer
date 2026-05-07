#!/usr/bin/env python3
import unittest
from pathlib import Path


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


class ProofFixtureTest(unittest.TestCase):
    def test_support_release_fixture_has_non_degenerate_support_depth(self):
        _, y_bounds, z_bounds = ascii_stl_bounds(
            ROOT_DIR / "proof-fixtures" / "stage2_bridge_speed_fixture.stl"
        )

        self.assertGreaterEqual(y_bounds[1] - y_bounds[0], 20.0)
        self.assertGreater(z_bounds[1] - z_bounds[0], 10.0)


if __name__ == "__main__":
    unittest.main()
