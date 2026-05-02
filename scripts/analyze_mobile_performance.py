#!/usr/bin/env python3
"""Analyze MobileSlicer device performance benchmark records."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
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
    return {str(item.get("name")): item for item in records if isinstance(item, dict)}


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
                "| Name | Fixture bytes | Stage ms | Native load ms | Placement ms | Config ms | Native slice ms | Write G-code ms | Total ms |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for record in slice_records:
            lines.append(
                "| {name} | {fixture_bytes} | {stage} | {native_load} | {placement} | {config} | {native_slice} | {write_gcode} | {elapsed} |".format(
                    name=record.get("name", ""),
                    fixture_bytes=record.get("fixture_bytes", ""),
                    stage=record.get("staging_ms", ""),
                    native_load=record.get("native_load_ms", ""),
                    placement=record.get("placement_ms", ""),
                    config=record.get("config_ms", ""),
                    native_slice=record.get("native_slice_ms", ""),
                    write_gcode=record.get("write_gcode_ms", ""),
                    elapsed=record.get("elapsed_ms", ""),
                )
            )
    return "\n".join(lines) + "\n"


def analyze(records: list[dict[str, Any]], baseline: dict[str, dict[str, Any]]) -> list[str]:
    failures: list[str] = []
    max_startup_ms = env_int("MOBILE_SLICER_PERF_MAX_STARTUP_MS", 4_000)
    max_peak_pss_kb = env_int("MOBILE_SLICER_PERF_MAX_PEAK_PSS_KB", 900_000)
    startup_regression_percent = env_float("MOBILE_SLICER_PERF_STARTUP_REGRESSION_PERCENT", 25.0)
    slice_regression_percent = env_float("MOBILE_SLICER_PERF_SLICE_REGRESSION_PERCENT", 25.0)
    memory_regression_percent = env_float("MOBILE_SLICER_PERF_MEMORY_REGRESSION_PERCENT", 25.0)
    output_regression_percent = env_float("MOBILE_SLICER_PERF_OUTPUT_REGRESSION_PERCENT", 35.0)

    for record in records:
        name = str(record.get("name", "unnamed"))
        record_type = record.get("type")
        startup_ms = record_metric(record, "startup_ms")
        elapsed_ms = record_metric(record, "elapsed_ms")
        peak_pss_kb = record_metric(record, "peak_pss_kb")
        output_bytes = record_metric(record, "bytes")

        if record_type == "startup":
            if startup_ms is None:
                failures.append(f"{name}: missing startup_ms")
            elif startup_ms > max_startup_ms:
                failures.append(f"{name}: startup {startup_ms:.0f}ms exceeds budget {max_startup_ms}ms")

        if record_type == "slice":
            budget = env_int(
                f"MOBILE_SLICER_PERF_MAX_{name.upper().replace('-', '_')}_SLICE_MS",
                DEFAULT_SLICE_BUDGETS_MS.get(name, 180_000),
            )
            if elapsed_ms is None:
                failures.append(f"{name}: missing elapsed_ms")
            elif elapsed_ms > budget:
                failures.append(f"{name}: slice {elapsed_ms:.0f}ms exceeds budget {budget}ms")
            if output_bytes is None or output_bytes <= 1024:
                failures.append(f"{name}: G-code output is unexpectedly small")

        if peak_pss_kb is not None and peak_pss_kb > max_peak_pss_kb:
            failures.append(f"{name}: peak PSS {peak_pss_kb:.0f}KB exceeds budget {max_peak_pss_kb}KB")

        previous = baseline.get(name)
        if not previous:
            continue
        comparisons = [
            ("startup_ms", startup_regression_percent),
            ("elapsed_ms", slice_regression_percent),
            ("native_load_ms", slice_regression_percent),
            ("native_slice_ms", slice_regression_percent),
            ("write_gcode_ms", slice_regression_percent),
            ("peak_pss_kb", memory_regression_percent),
            ("bytes", output_regression_percent),
        ]
        for metric, allowed_percent in comparisons:
            current_value = record_metric(record, metric)
            previous_value = record_metric(previous, metric)
            if current_value is None or previous_value in (None, 0):
                continue
            change = percent_change(current_value, previous_value)
            if change > allowed_percent:
                failures.append(
                    f"{name}: {metric} regressed by {change:.1f}% "
                    f"({previous_value:.0f} -> {current_value:.0f}, allowed {allowed_percent:.1f}%)"
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
        "records": records,
        "failures": failures,
    }
    args.output_json.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.output_md.write_text(build_markdown(records, failures), encoding="utf-8")

    for record in records:
        if record.get("type") == "startup":
            print(f"{record.get('name')}: startupMs={record.get('startup_ms')} peakPssKb={record.get('peak_pss_kb')}")
        elif record.get("type") == "slice":
            print(
                f"{record.get('name')}: elapsedMs={record.get('elapsed_ms')} "
                f"stagingMs={record.get('staging_ms')} nativeLoadMs={record.get('native_load_ms')} "
                f"placementMs={record.get('placement_ms')} configMs={record.get('config_ms')} "
                f"nativeSliceMs={record.get('native_slice_ms')} writeGcodeMs={record.get('write_gcode_ms')} "
                f"peakPssKb={record.get('peak_pss_kb')} "
                f"bytes={record.get('bytes')}"
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
