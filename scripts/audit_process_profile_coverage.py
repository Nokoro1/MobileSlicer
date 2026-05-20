#!/usr/bin/env python3
"""Audit MobileSlicer process profile coverage against Orca setting surfaces."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROFILE_DIR = ROOT / "android-app/app/src/main/java/com/mobileslicer/profiles"


METADATA_FIELDS = {
    "id",
    "name",
    "subtitle",
    "builtIn",
    "profileSource",
    "printerProfileId",
    "printerVariantKey",
    "nozzleDiameterMm",
    "orcaFamily",
    "orcaProcessPath",
    "orcaRawProcessJson",
    "orcaResolvedProcessJson",
    "orcaProcessOverridesJson",
    "orcaResolvedSourceChain",
}

CONTAINER_FIELDS = {
    "qualitySurfaceDetails",
    "strengthInfillDetails",
    "primeTowerDetails",
    "specialModeDetails",
    "gcodeOutputDetails",
}

FILAMENT_OR_TOOL_FIELDS = {
    "printExtruderId",
    "printExtruderVariant",
    "sparseInfillFilament",
    "supportFilament",
    "supportInterfaceFilament",
    "filamentMapMode",
    "allowMixTemp",
    "allowMulticolorOnePlate",
    "singleExtruderMultiMaterialPriming",
}

BLOCKED_OR_STORED_ONLY_KEYS = {
    "detect_narrow_internal_solid_infill",
    "enable_extra_bridge_layer",
    "ensure_vertical_shell_thickness",
    "fill_multiline",
    "filter_out_gap_fill",
    "gap_fill_flow_ratio",
    "gcode_add_line_number",
    "min_bead_width",
    "min_feature_size",
    "post_process",
    "sparse_infill_filament",
    "sparse_infill_flow_ratio",
    "support_filament",
    "support_interface_filament",
    "timelapse_type",
    "wall_distribution_count",
    "wall_transition_angle",
    "wall_transition_filter_deviation",
    "wall_transition_length",
}

DOCUMENTED_NON_ORCA_KEYS = {
    "apply_top_surface_compensation",
    "first_layer_height",
    "first_layer_infill_speed",
    "first_layer_print_speed",
    "initial_layer_travel_speed_percent",
    "internal_bridge_support_thickness",
    "mobile_slicer_process_prime_tower_enabled",
    "skirts",
}


@dataclass(frozen=True)
class FieldCoverage:
    field: str
    category: str
    native_keys: tuple[str, ...]
    saved: bool
    restored: bool
    object_scope: str


def read(relative: str) -> str:
    return (ROOT / relative).read_text()


def native_config_constants() -> dict[str, str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/NativeSliceConfigKeys.kt")
    constants: dict[str, str] = {}
    stack: list[str] = []
    for line in source.splitlines():
        match = re.search(r"\s*object\s+(\w+)\s+\{", line)
        if match:
            stack.append(match.group(1))
            continue
        match = re.search(r'const val\s+(\w+)\s*=\s*"([^"]+)"', line)
        if match and stack:
            constants[f"NativeConfigKeys.{stack[-1]}.{match.group(1)}"] = match.group(2)
        if line.strip() == "}" and stack:
            stack.pop()
    return constants


def process_fields() -> set[str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/ProfileModels.kt")
    class_body = source.split("internal class ProcessProfile", 1)[1].split("\n}", 1)[0]
    return set(re.findall(r"^\s+val\s+(\w+)\s*:", class_body, re.MULTILINE)) - {"values"}


def derived_process_fields() -> set[str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/ProfileModels.kt")
    class_body = source.split("internal class ProcessProfile", 1)[1].split("\n}", 1)[0]
    return set(
        re.findall(
            r"^\s+val\s+(\w+)\s*:[^\n]+get\(\)\s*=\s*(?:qualitySurfaceDetails|strengthInfillDetails|primeTowerDetails|specialModeDetails|gcodeOutputDetails)\.",
            class_body,
            re.MULTILINE,
        )
    )


def default_fields() -> set[str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/ProcessProfileFactory.kt")
    defaults_source = source.split("private val processProfileLegacyIndexNames", 1)[0]
    return set(re.findall(r'"([^"]+)"\s+to', defaults_source))


def to_json_keys() -> set[str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/ProcessProfileJson.kt")
    json_source = source.split("internal fun JSONObject.toProcessProfile", 1)[0]
    return set(re.findall(r'\.put\("([^"]+)"', json_source))


def restored_json_keys(defaults: set[str]) -> set[str]:
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/ProcessProfileJson.kt")
    restore_source = source.split("private fun JSONObject.restoreSavedProcessProfileFields", 1)[1]
    restored = set(defaults)
    restored.update(re.findall(r'optional(?:String|Boolean|Int|Float)\("([^"]+)"', restore_source))
    restored.update(re.findall(r'optionalString\("([^"]+)"', restore_source))
    restored.update(re.findall(r'opt(?:String|Double|Int|Boolean|JSONArray)\("([^"]+)"', source))
    restored.update(re.findall(r'getString\("([^"]+)"', source))
    return restored


def generated_orca_keys() -> set[str]:
    source = read("android-app/app/build/generated/source/orcaSettingMetadata/com/mobileslicer/profiles/GeneratedOrcaSettingMetadata.kt")
    return set(re.findall(r'^\s*"([^"]+)"\s+to\s+OrcaSettingMetadata\(', source, re.MULTILINE))


def orca_config_keys() -> set[str]:
    print_config_cpp = read("vendor/orcaslicer/src/libslic3r/PrintConfig.cpp")
    print_config_hpp = read("vendor/orcaslicer/src/libslic3r/PrintConfig.hpp")
    return (
        generated_orca_keys()
        | set(re.findall(r'^\s*def\s*=.*add\("([^"]+)"\s*,', print_config_cpp, re.MULTILINE))
        | set(re.findall(r"\(\(ConfigOption[^,]+,\s*([A-Za-z0-9_]+)\)\)", print_config_hpp))
    )


def native_applied_keys() -> set[str]:
    source = read("engine-wrapper/orca_wrapper_applied_key_manifest.h")
    return set(re.findall(r'"([A-Za-z0-9_]+)"', source))


def native_field_keys() -> dict[str, set[str]]:
    constants = native_config_constants()
    source = read("android-app/app/src/main/java/com/mobileslicer/profiles/NativeSliceProcessConfiguration.kt")
    mapping: dict[str, set[str]] = {}
    for key, field in re.findall(r'\.put\("([^"]+)",\s*process\.(\w+)', source):
        mapping.setdefault(field, set()).add(key)
    for const, field in re.findall(r"\.put\((NativeConfigKeys\.[A-Za-z]+\.[A-Za-z]+),\s*process\.(\w+)", source):
        mapping.setdefault(field, set()).add(constants.get(const, const))
    if "nativeBrimTypeConfigValue()" in source:
        mapping.setdefault("brimType", set()).add("brim_type")
        mapping.setdefault("brimWidthMm", set()).add("brim_type")
    return mapping


def classify(field: str, keys: set[str], applied_keys: set[str]) -> tuple[str, str]:
    if field in METADATA_FIELDS:
        return "metadata", "not-object"
    if field in CONTAINER_FIELDS:
        return "container", "not-object"
    if field in FILAMENT_OR_TOOL_FIELDS:
        return "filament/tool-scoped", "not-object"
    if field.startswith("treeSupport") or field.startswith("support"):
        category = "process/support"
    elif field.startswith("wipeTower") or "Tower" in field or field.startswith("flushInto"):
        category = "process/multimaterial"
    elif field.startswith("gcode") or field in {"filenameFormat", "postProcessScripts", "notes"}:
        category = "process/output"
    else:
        category = "process"

    if not keys:
        return category, "stored-only"
    if keys & BLOCKED_OR_STORED_ONLY_KEYS:
        return category, "not-object"
    if keys <= DOCUMENTED_NON_ORCA_KEYS:
        return category, "workspace/plate-only"
    if keys & applied_keys:
        return category, "object-candidate"
    return category, "stored-only"


def collect_coverage() -> tuple[list[FieldCoverage], list[str]]:
    fields = process_fields()
    derived_fields = derived_process_fields()
    defaults = default_fields()
    saved_keys = to_json_keys()
    restored_keys = restored_json_keys(defaults)
    field_keys = native_field_keys()
    metadata_keys = orca_config_keys()
    applied_keys = native_applied_keys()

    problems: list[str] = []
    missing_defaults = sorted((fields - defaults) - derived_fields)
    if missing_defaults:
        problems.append("ProcessProfile fields missing defaults: " + ", ".join(missing_defaults))

    saved_missing_restore = sorted((saved_keys - restored_keys) - {"printSpeedMmPerSec"})
    if saved_missing_restore:
        problems.append("ProcessProfile JSON keys saved but not restored: " + ", ".join(saved_missing_restore))

    unknown_native = sorted(
        key
        for keys in field_keys.values()
        for key in keys
        if key not in metadata_keys and key not in DOCUMENTED_NON_ORCA_KEYS
    )
    if unknown_native:
        problems.append("Native process keys missing Orca metadata or alias classification: " + ", ".join(unknown_native))

    rows: list[FieldCoverage] = []
    for field in sorted(fields):
        keys = field_keys.get(field, set())
        category, object_scope = classify(field, keys, applied_keys)
        saved = field in saved_keys or field in CONTAINER_FIELDS
        restored = field in restored_keys or field in CONTAINER_FIELDS
        if not saved:
            problems.append(f"ProcessProfile field is not saved to JSON: {field}")
        if not restored:
            problems.append(f"ProcessProfile field is not restored from JSON: {field}")
        rows.append(
            FieldCoverage(
                field=field,
                category=category,
                native_keys=tuple(sorted(keys)),
                saved=saved,
                restored=restored,
                object_scope=object_scope,
            )
        )
    return rows, sorted(set(problems))


def render_report(rows: list[FieldCoverage]) -> str:
    counts: dict[str, int] = {}
    object_counts: dict[str, int] = {}
    for row in rows:
        counts[row.category] = counts.get(row.category, 0) + 1
        object_counts[row.object_scope] = object_counts.get(row.object_scope, 0) + 1

    lines = [
        "# Orca Process Setting Coverage",
        "",
        "Generated by `scripts/audit_process_profile_coverage.py`.",
        "",
        "This audit covers every `ProcessProfile` setting property and classifies whether it is saved, restored, mapped to native Orca config keys, and eligible for object-scoped override attempts.",
        "",
        "## Summary",
        "",
        "| Bucket | Count |",
        "| --- | ---: |",
    ]
    for key in sorted(counts):
        lines.append(f"| `{key}` | {counts[key]} |")
    for key in sorted(object_counts):
        lines.append(f"| object scope `{key}` | {object_counts[key]} |")
    lines.extend(
        [
            f"| total fields | {len(rows)} |",
            "",
            "## Fields",
            "",
            "| Field | Category | Object scope | Native Orca keys | Saved | Restored |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in rows:
        keys = ", ".join(f"`{key}`" for key in row.native_keys) if row.native_keys else "_stored only_"
        lines.append(
            f"| `{row.field}` | {row.category} | {row.object_scope} | {keys} | "
            f"{'yes' if row.saved else 'no'} | {'yes' if row.restored else 'no'} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    rows, problems = collect_coverage()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(render_report(rows))
    if args.check and problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        return 1
    print(f"audited {len(rows)} process profile fields")
    if args.report:
        print(f"wrote {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
