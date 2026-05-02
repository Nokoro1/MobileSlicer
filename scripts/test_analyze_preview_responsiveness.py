#!/usr/bin/env python3
"""Tests for preview responsiveness artifact analysis."""

from __future__ import annotations

import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "analyze_preview_responsiveness.py"
SPEC = importlib.util.spec_from_file_location("analyze_preview_responsiveness", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
analyzer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(analyzer)


class AnalyzePreviewResponsivenessTest(unittest.TestCase):
    def test_parse_status_extracts_numeric_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            status_path = pathlib.Path(directory) / "status.txt"
            status_path.write_text(
                "success: firstReady=1 churnRequests=12 churnReady=1 maxFirstFrameMs=723",
                encoding="utf-8",
            )

            status = analyzer.parse_status(status_path)

        self.assertTrue(status["success"])
        self.assertEqual(1, status["firstReady"])
        self.assertEqual(12, status["churnRequests"])
        self.assertEqual(723, status["maxFirstFrameMs"])

    def test_parse_runtime_events_extracts_preview_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            timing_path = pathlib.Path(directory) / "timing-logcat.txt"
            timing_path.write_text(
                """
                05-02 10:00:00 I MobileSlicerPerf: workspace_preview_runtime previewKey=7 layerStart=0 layerEnd=20 vertexBudget=2000000 nativeLoadMs=349 firstFrameMs=701 lastFrameMs=16 slowFrames=0 frames=1
                ignored line
                05-02 10:00:01 I MobileSlicerPerf: workspace_preview_runtime previewKey=7 layerStart=0 layerEnd=20 vertexBudget=2000000 nativeLoadMs=349 firstFrameMs=701 lastFrameMs=18 slowFrames=0 frames=40
                """,
                encoding="utf-8",
            )

            events = analyzer.parse_runtime_events(timing_path)

        self.assertEqual(2, len(events))
        self.assertEqual(349, events[0]["nativeLoadMs"])
        self.assertEqual(40, events[1]["frames"])

    def test_analyze_passes_with_status_metrics(self) -> None:
        status = {
            "success": True,
            "firstReady": 1,
            "churnRequests": 0,
            "metrics": 2,
            "renderedFrames": 30,
            "maxNativeLoadMs": 400,
            "maxFirstFrameMs": 750,
            "maxFrameMs": 17,
            "slowFrames": 0,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            summary, failures = analyzer.analyze(status, [], "preview-interaction")

        self.assertEqual([], failures)
        self.assertEqual(400, summary["max_native_load_ms"])
        self.assertEqual(30, summary["rendered_frames"])

    def test_analyze_fails_when_first_frame_exceeds_budget(self) -> None:
        status = {
            "success": True,
            "firstReady": 1,
            "metrics": 1,
            "renderedFrames": 1,
            "maxNativeLoadMs": 400,
            "maxFirstFrameMs": 3_500,
            "maxFrameMs": 16,
            "slowFrames": 0,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            _, failures = analyzer.analyze(status, [], "preview-interaction")

        self.assertIn("first preview frame 3500ms exceeds budget 3000ms", failures)

    def test_analyze_requires_churn_ready_for_churn_profile(self) -> None:
        status = {
            "success": True,
            "firstReady": 1,
            "churnRequests": 12,
            "churnReady": 0,
            "metrics": 1,
            "renderedFrames": 1,
            "maxNativeLoadMs": 400,
            "maxFirstFrameMs": 750,
            "maxFrameMs": 16,
            "slowFrames": 0,
        }

        with mock.patch.dict(os.environ, {}, clear=True):
            _, failures = analyzer.analyze(status, [], "preview-churn")

        self.assertIn("newest churn preview request did not render", failures)


if __name__ == "__main__":
    raise SystemExit(unittest.main())
