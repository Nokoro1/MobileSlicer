#!/usr/bin/env python3
"""Analyze MobileSlicer device performance benchmark records."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
from typing import Any


DEFAULT_SLICE_BUDGETS_MS = {
    "small-cube": 120_000,
    "bridge-support": 180_000,
    "perimeter-array": 180_000,
    "medium-speed-structure": 240_000,
    "complex-vfa": 300_000,
    "stress-temperature-tower": 600_000,
}


REGRESSION_METRIC_GROUPS = {
    "startup_ms": "startup",
    "elapsed_ms": "slice",
    "native_load_ms": "slice",
    "native_slice_ms": "slice",
    "write_gcode_ms": "slice",
    "preview_cache_build_ms": "slice",
    "preview_moves": "output",
    "preview_cached_vertices": "output",
    "peak_pss_kb": "memory",
    "peak_java_heap_kb": "memory",
    "peak_native_heap_kb": "memory",
    "peak_graphics_kb": "memory",
    "peak_private_other_kb": "memory",
    "bytes": "output",
}


def env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise SystemExit(f"{name} must be an integer, got {raw!r}") from exc


def env_float(name: str, default: float) -> float:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return float(raw)
    except ValueError as exc:
        raise SystemExit(f"{name} must be a number, got {raw!r}") from exc


def load_jsonl(path: pathlib.Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {exc}") from exc
            if not isinstance(record, dict):
                raise SystemExit(f"{path}:{line_number}: expected a JSON object")
            records.append(record)
    return records


def load_baseline(path: pathlib.Path | None) -> dict[str, dict[str, Any]]:
    if path is None:
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    records = payload["records"] if isinstance(payload, dict) and "records" in payload else payload
    if not isinstance(records, list):
        raise SystemExit(f"baseline must contain a record list: {path}")
    baseline: dict[str, dict[str, Any]] = {}
    for item in records:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", ""))
        if not name:
            continue
        baseline[name] = item
        base_name = base_record_name(name)
        baseline.setdefault(base_name, item)
    return baseline


def record_metric(record: dict[str, Any], metric: str) -> float | None:
    value = record.get(metric)
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def percent_change(current: float, previous: float) -> float:
    if previous == 0:
        return 0.0 if current == 0 else float("inf")
    return ((current - previous) / previous) * 100.0


REPEAT_SUFFIX_RE = re.compile(r"-r[0-9]+$")


def base_record_name(name: str) -> str:
    return REPEAT_SUFFIX_RE.sub("", name)


def build_markdown(records: list[dict[str, Any]], failures: list[str]) -> str:
    lines = [
        "# MobileSlicer Performance Report",
        "",
        "| Name | Type | Startup ms | Slice ms | Placement ms | Peak PSS KB | Bytes |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for record in records:
        lines.append(
            "| {name} | {type} | {startup} | {elapsed} | {placement} | {pss} | {bytes} |".format(
                name=record.get("name", ""),
                type=record.get("type", ""),
                startup=record.get("startup_ms", ""),
                elapsed=record.get("elapsed_ms", ""),
                placement=record.get("placement_ms", ""),
                pss=record.get("peak_pss_kb", ""),
                bytes=record.get("bytes", ""),
            )
        )
    if failures:
        lines.extend(["", "## Failures", ""])
        lines.extend(f"- {failure}" for failure in failures)
    slice_records = [record for record in records if record.get("type") == "slice"]
    if slice_records:
        lines.extend(
            [
                "",
                "## Slice Phases",
                "",
                "| Name | Fixture bytes | Stage ms | Native load ms | Placement ms | Config ms | Native slice ms | Write G-code ms | Preview moves | Preview vertices | Preview cache ms | Total ms |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for record in slice_records:
            lines.append(
                "| {name} | {fixture_bytes} | {stage} | {native_load} | {placement} | {config} | {native_slice} | {write_gcode} | {preview_moves} | {preview_vertices} | {preview_cache_ms} | {elapsed} |".format(
                    name=record.get("name", ""),
                    fixture_bytes=record.get("fixture_bytes", ""),
                    stage=record.get("staging_ms", ""),
                    native_load=record.get("native_load_ms", ""),
                    placement=record.get("placement_ms", ""),
                    config=record.get("config_ms", ""),
                    native_slice=record.get("native_slice_ms", ""),
                    write_gcode=record.get("write_gcode_ms", ""),
                    preview_moves=record.get("preview_moves", ""),
                    preview_vertices=record.get("preview_cached_vertices", ""),
                    preview_cache_ms=record.get("preview_cache_build_ms", ""),
                    elapsed=record.get("elapsed_ms", ""),
                )
            )
        lines.extend(
            [
                "",
                "## Memory Attribution",
                "",
                "| Name | Peak PSS KB | Java heap KB | Native heap KB | Graphics KB | Private other KB | System KB |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for record in records:
            lines.append(
                "| {name} | {pss} | {java} | {native} | {graphics} | {private_other} | {system} |".format(
                    name=record.get("name", ""),
                    pss=record.get("peak_pss_kb", ""),
                    java=record.get("peak_java_heap_kb", ""),
                    native=record.get("peak_native_heap_kb", ""),
                    graphics=record.get("peak_graphics_kb", ""),
                    private_other=record.get("peak_private_other_kb", ""),
                    system=record.get("peak_system_kb", ""),
                )
            )
    return "\n".join(lines) + "\n"


def format_delta(current: float | None, previous: float | None) -> str:
    if current is None or previous is None:
        return ""
    delta = current - previous
    change = percent_change(current, previous)
    sign = "+" if delta >= 0 else ""
    return f"{sign}{delta:.0f} ({sign}{change:.1f}%)"


def build_baseline_markdown(records: list[dict[str, Any]], baseline: dict[str, dict[str, Any]]) -> str:
    if not baseline:
        return ""
    lines = [
        "## Baseline Comparison",
        "",
        "| Name | Slice ms delta | PSS KB delta | Native heap KB delta | Java heap KB delta | Bytes delta | Preview moves delta |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    rows = 0
    for record in records:
        if record.get("type") != "slice":
            continue
        name = str(record.get("name", ""))
        previous = baseline.get(name) or baseline.get(base_record_name(name))
        if previous is None:
            continue
        rows += 1
        lines.append(
            "| {name} | {elapsed} | {pss} | {native} | {java} | {bytes} | {preview_moves} |".format(
                name=name,
                elapsed=format_delta(record_metric(record, "elapsed_ms"), record_metric(previous, "elapsed_ms")),
                pss=format_delta(record_metric(record, "peak_pss_kb"), record_metric(previous, "peak_pss_kb")),
                native=format_delta(
                    record_metric(record, "peak_native_heap_kb"),
                    record_metric(previous, "peak_native_heap_kb"),
                ),
                java=format_delta(
                    record_metric(record, "peak_java_heap_kb"),
                    record_metric(previous, "peak_java_heap_kb"),
                ),
                bytes=format_delta(record_metric(record, "bytes"), record_metric(previous, "bytes")),
                preview_moves=format_delta(
                    record_metric(record, "preview_moves"),
                    record_metric(previous, "preview_moves"),
                ),
            )
        )
    return "\n".join(lines) + "\n" if rows else ""


def regression_allowances() -> dict[str, tuple[float, float]]:
    startup_regression_percent = env_float("MOBILE_SLICER_PERF_STARTUP_REGRESSION_PERCENT", 25.0)
    slice_regression_percent = env_float("MOBILE_SLICER_PERF_SLICE_REGRESSION_PERCENT", 25.0)
    memory_regression_percent = env_float("MOBILE_SLICER_PERF_MEMORY_REGRESSION_PERCENT", 25.0)
    output_regression_percent = env_float("MOBILE_SLICER_PERF_OUTPUT_REGRESSION_PERCENT", 35.0)
    startup_regression_min = env_int("MOBILE_SLICER_PERF_STARTUP_REGRESSION_MIN_MS", 250)
    slice_regression_min = env_int("MOBILE_SLICER_PERF_SLICE_REGRESSION_MIN_MS", 750)
    memory_regression_min = env_int("MOBILE_SLICER_PERF_MEMORY_REGRESSION_MIN_KB", 98_304)
    output_regression_min = env_int("MOBILE_SLICER_PERF_OUTPUT_REGRESSION_MIN_UNITS", 1024)
    return {
        "startup": (startup_regression_percent, float(startup_regression_min)),
        "slice": (slice_regression_percent, float(slice_regression_min)),
        "memory": (memory_regression_percent, float(memory_regression_min)),
        "output": (output_regression_percent, float(output_regression_min)),
    }


def metric_regression_min_delta(metric: str, group_min_delta: float) -> float:
    env_name = f"MOBILE_SLICER_PERF_{metric.upper()}_REGRESSION_MIN"
    metric_defaults = {
        "peak_pss_kb": 131_072,
    }
    return float(env_int(env_name, int(metric_defaults.get(metric, group_min_delta))))


def analyze(records: list[dict[str, Any]], baseline: dict[str, dict[str, Any]]) -> list[str]:
    failures: list[str] = []
    max_startup_ms = env_int("MOBILE_SLICER_PERF_MAX_STARTUP_MS", 4_000)
    max_peak_pss_kb = env_int("MOBILE_SLICER_PERF_MAX_PEAK_PSS_KB", 900_000)
    max_java_heap_kb = env_int("MOBILE_SLICER_PERF_MAX_JAVA_HEAP_KB", 350_000)
    max_native_heap_kb = env_int("MOBILE_SLICER_PERF_MAX_NATIVE_HEAP_KB", 700_000)
    max_graphics_kb = env_int("MOBILE_SLICER_PERF_MAX_GRAPHICS_KB", 350_000)
    max_private_other_kb = env_int("MOBILE_SLICER_PERF_MAX_PRIVATE_OTHER_KB", 550_000)
    regression_groups = regression_allowances()
    repeat_memory_growth_percent = env_float("MOBILE_SLICER_PERF_REPEAT_MEMORY_GROWTH_PERCENT", 20.0)
    repeat_memory_growth_min_kb = env_int("MOBILE_SLICER_PERF_REPEAT_MEMORY_GROWTH_MIN_KB", 98_304)
    repeated_slices: dict[str, list[dict[str, Any]]] = {}

    for record in records:
        name = str(record.get("name", "unnamed"))
        base_name = base_record_name(name)
        record_type = record.get("type")
        startup_ms = record_metric(record, "startup_ms")
        elapsed_ms = record_metric(record, "elapsed_ms")
        peak_pss_kb = record_metric(record, "peak_pss_kb")
        peak_java_heap_kb = record_metric(record, "peak_java_heap_kb")
        peak_native_heap_kb = record_metric(record, "peak_native_heap_kb")
        peak_graphics_kb = record_metric(record, "peak_graphics_kb")
        peak_private_other_kb = record_metric(record, "peak_private_other_kb")
        output_bytes = record_metric(record, "bytes")

        if record_type == "startup":
            if startup_ms is None:
                failures.append(f"{name}: missing startup_ms")
            elif startup_ms > max_startup_ms:
                failures.append(f"{name}: startup {startup_ms:.0f}ms exceeds budget {max_startup_ms}ms")

        if record_type == "slice":
            if base_name != name:
                repeated_slices.setdefault(base_name, []).append(record)
            budget = env_int(
                f"MOBILE_SLICER_PERF_MAX_{base_name.upper().replace('-', '_')}_SLICE_MS",
                DEFAULT_SLICE_BUDGETS_MS.get(base_name, 180_000),
            )
            if elapsed_ms is None:
                failures.append(f"{name}: missing elapsed_ms")
            elif elapsed_ms > budget:
                failures.append(f"{name}: slice {elapsed_ms:.0f}ms exceeds budget {budget}ms")
            if output_bytes is None or output_bytes <= 1024:
                failures.append(f"{name}: G-code output is unexpectedly small")

        if peak_pss_kb is not None and peak_pss_kb > max_peak_pss_kb:
            failures.append(f"{name}: peak PSS {peak_pss_kb:.0f}KB exceeds budget {max_peak_pss_kb}KB")
        if peak_java_heap_kb is not None and peak_java_heap_kb > max_java_heap_kb:
            failures.append(f"{name}: Java heap {peak_java_heap_kb:.0f}KB exceeds budget {max_java_heap_kb}KB")
        if peak_native_heap_kb is not None and peak_native_heap_kb > max_native_heap_kb:
            failures.append(f"{name}: native heap {peak_native_heap_kb:.0f}KB exceeds budget {max_native_heap_kb}KB")
        if peak_graphics_kb is not None and peak_graphics_kb > max_graphics_kb:
            failures.append(f"{name}: graphics memory {peak_graphics_kb:.0f}KB exceeds budget {max_graphics_kb}KB")
        if peak_private_other_kb is not None and peak_private_other_kb > max_private_other_kb:
            failures.append(
                f"{name}: private other memory {peak_private_other_kb:.0f}KB exceeds budget {max_private_other_kb}KB"
            )

        previous = baseline.get(name) or baseline.get(base_name)
        if not previous:
            continue
        for metric, group_name in REGRESSION_METRIC_GROUPS.items():
            current_value = record_metric(record, metric)
            previous_value = record_metric(previous, metric)
            if current_value is None or previous_value in (None, 0):
                continue
            change = percent_change(current_value, previous_value)
            allowed_percent, allowed_min_delta = regression_groups[group_name]
            allowed_min_delta = metric_regression_min_delta(metric, allowed_min_delta)
            delta = current_value - previous_value
            if change > allowed_percent and delta > allowed_min_delta:
                failures.append(
                    f"{name}: {metric} regressed by {change:.1f}% "
                    f"({previous_value:.0f} -> {current_value:.0f}, allowed {allowed_percent:.1f}% "
                    f"and {allowed_min_delta:.0f} absolute growth)"
                )

    for base_name, repeat_records in sorted(repeated_slices.items()):
        if len(repeat_records) < 2:
            continue
        first = repeat_records[0]
        last = repeat_records[-1]
        for metric in [
            "peak_pss_kb",
            "peak_java_heap_kb",
            "peak_native_heap_kb",
            "peak_graphics_kb",
            "peak_private_other_kb",
        ]:
            first_value = record_metric(first, metric)
            last_value = record_metric(last, metric)
            if first_value is None or last_value is None:
                continue
            delta_kb = last_value - first_value
            growth = percent_change(last_value, first_value)
            if growth > repeat_memory_growth_percent and delta_kb > repeat_memory_growth_min_kb:
                failures.append(
                    f"{base_name}: {metric} grew by {growth:.1f}% across repeated runs "
                    f"({first_value:.0f} -> {last_value:.0f}, allowed {repeat_memory_growth_percent:.1f}% "
                    f"and {repeat_memory_growth_min_kb}KB)"
                )

    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=pathlib.Path, help="JSONL benchmark records")
    parser.add_argument("--output-json", required=True, type=pathlib.Path, help="Normalized JSON report")
    parser.add_argument("--output-md", required=True, type=pathlib.Path, help="Markdown report")
    parser.add_argument("--baseline", type=pathlib.Path, help="Optional previous report JSON for regression checks")
    args = parser.parse_args()

    records = load_jsonl(args.input)
    if not records:
        raise SystemExit("no performance records were provided")
    baseline = load_baseline(args.baseline)
    failures = analyze(records, baseline)

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "schema": 1,
        "baseline": str(args.baseline) if args.baseline is not None else None,
        "records": records,
        "failures": failures,
    }
    args.output_json.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    markdown = build_markdown(records, failures)
    baseline_markdown = build_baseline_markdown(records, baseline)
    if baseline_markdown:
        markdown += "\n" + baseline_markdown
    args.output_md.write_text(markdown, encoding="utf-8")

    for record in records:
        if record.get("type") == "startup":
            print(
                f"{record.get('name')}: startupMs={record.get('startup_ms')} "
                f"peakPssKb={record.get('peak_pss_kb')} javaHeapKb={record.get('peak_java_heap_kb')} "
                f"nativeHeapKb={record.get('peak_native_heap_kb')} graphicsKb={record.get('peak_graphics_kb')} "
                f"privateOtherKb={record.get('peak_private_other_kb')}"
            )
        elif record.get("type") == "slice":
            print(
                f"{record.get('name')}: elapsedMs={record.get('elapsed_ms')} "
                f"stagingMs={record.get('staging_ms')} nativeLoadMs={record.get('native_load_ms')} "
                f"placementMs={record.get('placement_ms')} configMs={record.get('config_ms')} "
                f"nativeSliceMs={record.get('native_slice_ms')} writeGcodeMs={record.get('write_gcode_ms')} "
                f"previewMoves={record.get('preview_moves')} "
                f"previewCacheBuilt={record.get('preview_cache_built')} "
                f"previewCacheComplete={record.get('preview_cache_complete')} "
                f"previewCachedVertices={record.get('preview_cached_vertices')} "
                f"previewCacheBuildMs={record.get('preview_cache_build_ms')} "
                f"peakPssKb={record.get('peak_pss_kb')} javaHeapKb={record.get('peak_java_heap_kb')} "
                f"nativeHeapKb={record.get('peak_native_heap_kb')} graphicsKb={record.get('peak_graphics_kb')} "
                f"privateOtherKb={record.get('peak_private_other_kb')} "
                f"bytes={record.get('bytes')}"
            )
    if baseline:
        print("Baseline comparison:")
        for record in records:
            if record.get("type") != "slice":
                continue
            name = str(record.get("name", ""))
            previous = baseline.get(name) or baseline.get(base_record_name(name))
            if previous is None:
                continue
            print(
                f"{name}: "
                f"elapsedDelta={format_delta(record_metric(record, 'elapsed_ms'), record_metric(previous, 'elapsed_ms'))} "
                f"pssDelta={format_delta(record_metric(record, 'peak_pss_kb'), record_metric(previous, 'peak_pss_kb'))} "
                f"nativeHeapDelta={format_delta(record_metric(record, 'peak_native_heap_kb'), record_metric(previous, 'peak_native_heap_kb'))} "
                f"javaHeapDelta={format_delta(record_metric(record, 'peak_java_heap_kb'), record_metric(previous, 'peak_java_heap_kb'))} "
                f"bytesDelta={format_delta(record_metric(record, 'bytes'), record_metric(previous, 'bytes'))}"
            )
    if failures:
        print("Performance gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"Performance report: {args.output_json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
