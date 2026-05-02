#!/usr/bin/env python3
"""Analyze MobileSlicer preview interaction responsiveness artifacts."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
from typing import Any


STATUS_FIELD_RE = re.compile(r"([A-Za-z][A-Za-z0-9_]*)=([^ \n\r\t]+)")
RUNTIME_FIELD_RE = re.compile(r"([A-Za-z][A-Za-z0-9_]*)=([0-9]+)")


def env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise SystemExit(f"{name} must be an integer, got {raw!r}") from exc


def maybe_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def parse_status(path: pathlib.Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    fields: dict[str, Any] = {
        "raw": text,
        "success": text.startswith("success:"),
    }
    for key, value in STATUS_FIELD_RE.findall(text):
        parsed = maybe_int(value)
        fields[key] = parsed if parsed is not None else value
    return fields


def parse_runtime_events(path: pathlib.Path | None) -> list[dict[str, int]]:
    if path is None or not path.exists():
        return []
    events: list[dict[str, int]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "workspace_preview_runtime" not in line:
            continue
        fields = {key: int(value) for key, value in RUNTIME_FIELD_RE.findall(line)}
        if fields:
            events.append(fields)
    return events


def max_metric(events: list[dict[str, int]], key: str) -> int | None:
    values = [event[key] for event in events if key in event]
    return max(values) if values else None


def last_metric(events: list[dict[str, int]], key: str) -> int | None:
    for event in reversed(events):
        if key in event:
            return event[key]
    return None


def first_present_int(primary: dict[str, Any], events: list[dict[str, int]], status_key: str, event_key: str) -> int | None:
    status_value = maybe_int(primary.get(status_key))
    return status_value if status_value is not None else max_metric(events, event_key)


def add_max_budget_failure(
    failures: list[str],
    value: int | None,
    budget: int,
    label: str,
    missing_message: str | None = None,
) -> None:
    if value is None:
        if missing_message is not None:
            failures.append(missing_message)
        return
    if value > budget:
        failures.append(f"{label} {value}ms exceeds budget {budget}ms")


def analyze(status: dict[str, Any], events: list[dict[str, int]], profile: str) -> tuple[dict[str, Any], list[str]]:
    max_native_load_ms = first_present_int(status, events, "maxNativeLoadMs", "nativeLoadMs")
    max_first_frame_ms = first_present_int(status, events, "maxFirstFrameMs", "firstFrameMs")
    max_frame_ms = first_present_int(status, events, "maxFrameMs", "lastFrameMs")
    max_native_selected_parse_ms = max_metric(events, "nativeSelectedParseMs")
    max_native_libvgcode_load_ms = max_metric(events, "nativeLibvgcodeLoadMs")
    max_native_total_load_ms = max_metric(events, "nativeTotalLoadMs")
    max_native_loaded_vertices = max_metric(events, "nativeLoadedVertices")
    max_native_cached_vertices = max_metric(events, "nativeCachedVertices")
    max_native_cached_layers = max_metric(events, "nativeCachedLayers")
    max_native_cache_hit = max_metric(events, "nativeCacheHit")
    max_native_cache_built = max_metric(events, "nativeCacheBuilt")
    slow_frames = first_present_int(status, events, "slowFrames", "slowFrames")
    rendered_frames = maybe_int(status.get("renderedFrames")) or last_metric(events, "frames")
    metric_count = maybe_int(status.get("metrics")) or len(events)
    churn_requests = maybe_int(status.get("churnRequests")) or 0
    lifecycle_cycles = maybe_int(status.get("lifecycleCycles")) or 0
    lifecycle_ready = maybe_int(status.get("lifecycleReady")) or 0

    summary: dict[str, Any] = {
        "schema": 1,
        "profile": profile,
        "status_success": bool(status.get("success")),
        "metric_count": metric_count,
        "runtime_event_count": len(events),
        "preview_ranges": maybe_int(status.get("previewRanges")),
        "first_range_layers": maybe_int(status.get("firstRangeLayers")),
        "second_range_layers": maybe_int(status.get("secondRangeLayers")),
        "first_ready": maybe_int(status.get("firstReady")),
        "second_ready": maybe_int(status.get("secondReady")),
        "churn_requests": churn_requests,
        "churn_ready": maybe_int(status.get("churnReady")),
        "lifecycle_cycles": lifecycle_cycles,
        "lifecycle_ready": lifecycle_ready,
        "max_native_load_ms": max_native_load_ms,
        "max_native_selected_parse_ms": max_native_selected_parse_ms,
        "max_native_libvgcode_load_ms": max_native_libvgcode_load_ms,
        "max_native_total_load_ms": max_native_total_load_ms,
        "max_native_loaded_vertices": max_native_loaded_vertices,
        "max_native_cached_vertices": max_native_cached_vertices,
        "max_native_cached_layers": max_native_cached_layers,
        "max_native_cache_hit": max_native_cache_hit,
        "max_native_cache_built": max_native_cache_built,
        "max_first_frame_ms": max_first_frame_ms,
        "max_frame_ms": max_frame_ms,
        "slow_frames": slow_frames,
        "rendered_frames": rendered_frames,
        "elapsed_ms": maybe_int(status.get("elapsedMs")),
    }

    failures: list[str] = []
    max_native_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_NATIVE_LOAD_MS", 2_500)
    max_native_selected_parse_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_NATIVE_SELECTED_PARSE_MS", 900)
    max_native_libvgcode_load_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_NATIVE_LIBVGCODE_LOAD_MS", 300)
    max_native_total_load_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_NATIVE_TOTAL_LOAD_MS", 1_200)
    max_first_frame_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_FIRST_FRAME_MS", 3_000)
    max_frame_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_FRAME_MS", 64)
    max_slow_frames_budget = env_int("MOBILE_SLICER_PREVIEW_MAX_SLOW_FRAMES", 0)
    min_metrics = env_int("MOBILE_SLICER_PREVIEW_MIN_METRICS", 1)
    min_rendered_frames = env_int("MOBILE_SLICER_PREVIEW_MIN_RENDERED_FRAMES", 1)

    if not status.get("success"):
        failures.append("status did not report success")
    if maybe_int(status.get("firstReady")) != 1:
        failures.append("first preview request did not render")
    if churn_requests > 0 and maybe_int(status.get("churnReady")) != 1:
        failures.append("newest churn preview request did not render")
    if lifecycle_cycles > 0 and lifecycle_ready != lifecycle_cycles:
        failures.append(f"lifecycle preview cycles rendered {lifecycle_ready} of {lifecycle_cycles}")
    if metric_count is None or metric_count < min_metrics:
        failures.append(f"captured metrics {metric_count or 0} below minimum {min_metrics}")
    if rendered_frames is None or rendered_frames < min_rendered_frames:
        failures.append(f"rendered frames {rendered_frames or 0} below minimum {min_rendered_frames}")
    if max_native_load_ms is None:
        failures.append("missing native preview load timing")
    elif max_native_load_ms > max_native_budget:
        failures.append(f"native preview load {max_native_load_ms}ms exceeds budget {max_native_budget}ms")
    add_max_budget_failure(
        failures,
        max_native_selected_parse_ms,
        max_native_selected_parse_budget,
        "native selected preview parse",
        "missing native selected preview parse timing" if events else None,
    )
    add_max_budget_failure(
        failures,
        max_native_libvgcode_load_ms,
        max_native_libvgcode_load_budget,
        "native libvgcode preview load",
        "missing native libvgcode preview load timing" if events else None,
    )
    add_max_budget_failure(
        failures,
        max_native_total_load_ms,
        max_native_total_load_budget,
        "native total preview load",
        "missing native total preview load timing" if events else None,
    )
    if max_first_frame_ms is None:
        failures.append("missing first preview frame timing")
    elif max_first_frame_ms > max_first_frame_budget:
        failures.append(f"first preview frame {max_first_frame_ms}ms exceeds budget {max_first_frame_budget}ms")
    if max_frame_ms is None:
        failures.append("missing preview frame timing")
    elif max_frame_ms > max_frame_budget:
        failures.append(f"preview frame {max_frame_ms}ms exceeds budget {max_frame_budget}ms")
    if slow_frames is None:
        failures.append("missing slow-frame count")
    elif slow_frames > max_slow_frames_budget:
        failures.append(f"slow frames {slow_frames} exceeds budget {max_slow_frames_budget}")

    summary["budgets"] = {
        "max_native_load_ms": max_native_budget,
        "max_native_selected_parse_ms": max_native_selected_parse_budget,
        "max_native_libvgcode_load_ms": max_native_libvgcode_load_budget,
        "max_native_total_load_ms": max_native_total_load_budget,
        "max_first_frame_ms": max_first_frame_budget,
        "max_frame_ms": max_frame_budget,
        "max_slow_frames": max_slow_frames_budget,
        "min_metrics": min_metrics,
        "min_rendered_frames": min_rendered_frames,
    }
    summary["failures"] = failures
    return summary, failures


def build_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# MobileSlicer Preview Responsiveness",
        "",
        f"- Profile: `{summary['profile']}`",
        f"- Result: `{'PASS' if not summary['failures'] else 'FAIL'}`",
        f"- Runtime events: `{summary['runtime_event_count']}`",
        f"- Metrics reported: `{summary.get('metric_count')}`",
        "",
        "## Timings",
        "",
        f"- Native preview load max: `{summary.get('max_native_load_ms')}` ms",
        f"- Native selected parse max: `{summary.get('max_native_selected_parse_ms')}` ms",
        f"- Native libvgcode load max: `{summary.get('max_native_libvgcode_load_ms')}` ms",
        f"- Native loaded vertices max: `{summary.get('max_native_loaded_vertices')}`",
        f"- Native cached vertices max: `{summary.get('max_native_cached_vertices')}`",
        f"- Native cached layers max: `{summary.get('max_native_cached_layers')}`",
        f"- Native cache hit observed: `{summary.get('max_native_cache_hit')}`",
        f"- Native cache build observed: `{summary.get('max_native_cache_built')}`",
        f"- First frame max: `{summary.get('max_first_frame_ms')}` ms",
        f"- Frame max: `{summary.get('max_frame_ms')}` ms",
        f"- Slow frames: `{summary.get('slow_frames')}`",
        f"- Rendered frames: `{summary.get('rendered_frames')}`",
        f"- Automation elapsed: `{summary.get('elapsed_ms')}` ms",
        "",
        "## Readiness",
        "",
        f"- Preview ranges: `{summary.get('preview_ranges')}`",
        f"- First ready: `{summary.get('first_ready')}`",
        f"- Second ready: `{summary.get('second_ready')}`",
        f"- Churn requests: `{summary.get('churn_requests')}`",
        f"- Churn ready: `{summary.get('churn_ready')}`",
        f"- Lifecycle cycles: `{summary.get('lifecycle_cycles')}`",
        f"- Lifecycle ready: `{summary.get('lifecycle_ready')}`",
        "",
        "## Budgets",
        "",
    ]
    budgets = summary["budgets"]
    lines.extend(
        [
            f"- Native preview load max: `{budgets['max_native_load_ms']}` ms",
            f"- Native selected parse max: `{budgets['max_native_selected_parse_ms']}` ms",
            f"- Native libvgcode load max: `{budgets['max_native_libvgcode_load_ms']}` ms",
            f"- Native total load max: `{budgets['max_native_total_load_ms']}` ms",
            f"- First frame max: `{budgets['max_first_frame_ms']}` ms",
            f"- Frame max: `{budgets['max_frame_ms']}` ms",
            f"- Slow frames max: `{budgets['max_slow_frames']}`",
            f"- Metrics min: `{budgets['min_metrics']}`",
            f"- Rendered frames min: `{budgets['min_rendered_frames']}`",
        ]
    )
    if summary["failures"]:
        lines.extend(["", "## Failures", ""])
        lines.extend(f"- {failure}" for failure in summary["failures"])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--status", required=True, type=pathlib.Path, help="Preview automation status file")
    parser.add_argument("--timing-log", type=pathlib.Path, help="Filtered logcat timing file")
    parser.add_argument("--profile", required=True, help="Profile name, such as preview-interaction")
    parser.add_argument("--output-json", required=True, type=pathlib.Path, help="JSON analysis output")
    parser.add_argument("--output-md", required=True, type=pathlib.Path, help="Markdown analysis output")
    args = parser.parse_args()

    status = parse_status(args.status)
    events = parse_runtime_events(args.timing_log)
    summary, failures = analyze(status, events, args.profile)

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.output_md.write_text(build_markdown(summary), encoding="utf-8")

    print(
        f"{args.profile}: maxNativeLoadMs={summary.get('max_native_load_ms')} "
        f"maxFirstFrameMs={summary.get('max_first_frame_ms')} "
        f"maxFrameMs={summary.get('max_frame_ms')} slowFrames={summary.get('slow_frames')} "
        f"renderedFrames={summary.get('rendered_frames')}"
    )
    if failures:
        print("Preview responsiveness gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"Preview responsiveness report: {args.output_json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
