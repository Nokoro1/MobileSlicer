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

    def test_preview_plan_baseline_regression_fails(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 10_000,
            "preview_plan_ms": 2_000,
            "bytes": 2048,
        }
        baseline = {
            "medium-speed-structure": {
                "name": "medium-speed-structure",
                "type": "slice",
                "elapsed_ms": 10_000,
                "preview_plan_ms": 500,
                "bytes": 2048,
            }
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], baseline)

        self.assertIn("medium-speed-structure: preview_plan_ms regressed", "\n".join(failures))

    def test_preview_plan_hard_budget_fails(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 10_000,
            "preview_plan_ms": 1_501,
            "bytes": 2048,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], {})

        self.assertIn("medium-speed-structure: preview planning 1501ms exceeds budget 1500ms", failures)

    def test_large_preview_requires_processor_storage_release(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 10_000,
            "preview_moves": 600_000,
            "processor_moves_released_during_export": 0,
            "processor_move_bytes_retained": 1024,
            "processor_line_end_bytes_retained": 512,
            "native_after_finalize_rss_kb": 700_000,
            "native_after_release_rss_kb": 699_000,
            "bytes": 2048,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], {})

        joined = "\n".join(failures)
        self.assertIn("processor preview storage was not released during export", joined)
        self.assertIn("processor move buffer retained 1024 bytes", joined)
        self.assertIn("processor line-end buffer retained 512 bytes", joined)
        self.assertIn("native RSS release drop 1000KB below budget 16384KB", joined)
        self.assertIn("missing native post-release RSS checkpoints", joined)

    def test_large_preview_processor_storage_release_passes(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 10_000,
            "preview_moves": 600_000,
            "processor_moves_released_during_export": 1,
            "processor_move_bytes_retained": 0,
            "processor_line_end_bytes_retained": 0,
            "native_after_finalize_rss_kb": 700_000,
            "native_after_release_rss_kb": 650_000,
            "native_after_stats_rss_kb": 650_000,
            "native_before_return_rss_kb": 645_000,
            "bytes": 2048,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], {})

        self.assertEqual([], failures)

    def test_large_preview_fails_when_post_release_rss_regrows(self) -> None:
        current = {
            "name": "medium-speed-structure",
            "type": "slice",
            "elapsed_ms": 10_000,
            "preview_moves": 600_000,
            "processor_moves_released_during_export": 1,
            "processor_move_bytes_retained": 0,
            "processor_line_end_bytes_retained": 0,
            "native_after_finalize_rss_kb": 700_000,
            "native_after_release_rss_kb": 650_000,
            "native_after_stats_rss_kb": 660_000,
            "native_before_return_rss_kb": 670_000,
            "bytes": 2048,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            failures = analyzer.analyze([current], {})

        joined = "\n".join(failures)
        self.assertIn("native RSS grew 10000KB after stats update", joined)
        self.assertIn("native RSS grew 10000KB before slice return", joined)


if __name__ == "__main__":
    raise SystemExit(unittest.main())
