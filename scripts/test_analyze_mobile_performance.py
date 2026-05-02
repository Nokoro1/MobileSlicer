#!/usr/bin/env python3
"""Tests for MobileSlicer performance report analysis."""

from __future__ import annotations

import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "analyze_mobile_performance.py"
SPEC = importlib.util.spec_from_file_location("analyze_mobile_performance", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
analyzer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(analyzer)


class AnalyzeMobilePerformanceTest(unittest.TestCase):
    def test_load_baseline_indexes_repeated_records_by_base_name(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            baseline_path = pathlib.Path(directory) / "baseline.json"
            baseline_path.write_text(
                """
                {
                  "records": [
                    {
                      "name": "medium-speed-structure-r1",
                      "type": "slice",
                      "elapsed_ms": 10000
                    }
                  ]
                }
                """,
                encoding="utf-8",
            )

            baseline = analyzer.load_baseline(baseline_path)

        self.assertEqual(10000, baseline["medium-speed-structure-r1"]["elapsed_ms"])
        self.assertEqual(10000, baseline["medium-speed-structure"]["elapsed_ms"])

    def test_regression_requires_percent_and_absolute_growth(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 1100,
            "bytes": 2048,
        }
        baseline = {
            "medium-speed-structure": {
                "name": "medium-speed-structure-r1",
                "type": "slice",
                "elapsed_ms": 800,
                "bytes": 2048,
            }
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], baseline)

        self.assertEqual([], failures)

    def test_large_baseline_regression_fails(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 13000,
            "bytes": 2048,
        }
        baseline = {
            "medium-speed-structure": {
                "name": "medium-speed-structure-r1",
                "type": "slice",
                "elapsed_ms": 10000,
                "bytes": 2048,
            }
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], baseline)

        self.assertIn("medium-speed-structure: elapsed_ms regressed", "\n".join(failures))


if __name__ == "__main__":
    raise SystemExit(unittest.main())
