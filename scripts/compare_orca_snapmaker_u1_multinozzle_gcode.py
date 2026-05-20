#!/usr/bin/env python3
"""Compare MobileSlicer Snapmaker U1 multi-nozzle G-code with an Orca reference."""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path


TOOL_RE = re.compile(r"^T(\d+)\b", re.MULTILINE)
PRIME_TOWER_RE = re.compile(r"^;\s*TYPE:\s*Prime tower\s*$", re.IGNORECASE | re.MULTILINE)
THUMBNAIL_RE = re.compile(r"^;\s*thumbnail begin\s+(\d+)x(\d+)\s+(\d+)\s*$", re.IGNORECASE | re.MULTILINE)


def read_reference_gcode(path: Path) -> str:
    if path.suffix.lower() == ".gcode":
        return path.read_text(encoding="utf-8", errors="replace")
    with zipfile.ZipFile(path) as zf:
        candidates = sorted(
            name for name in zf.namelist()
            if re.fullmatch(r"Metadata/plate_\d+\.gcode", name)
        )
        if not candidates:
            raise ValueError(f"{path} does not contain Metadata/plate_*.gcode")
        return zf.read(candidates[0]).decode("utf-8", errors="replace")


def gcode_summary(text: str) -> dict[str, object]:
    tools = sorted({int(match.group(1)) for match in TOOL_RE.finditer(text)})
    scalar_fields: dict[str, str] = {}
    for line in text.splitlines():
        key, separator, value = line.partition("=")
        if separator != "=":
            continue
        key = key.removeprefix(";").strip()
        scalar_fields[key] = value.strip()
    thumbnails = [
        {"width": int(match.group(1)), "height": int(match.group(2)), "bytes": int(match.group(3))}
        for match in THUMBNAIL_RE.finditer(text)
    ]
    def list_count(value: str) -> int:
        return len([part for part in re.split(r"[;,]", value) if part.strip()])

    return {
        "tool_ids": tools,
        "tool_count": len(tools),
        "has_toolchange": len(tools) > 1,
        "prime_tower_feature_count": len(PRIME_TOWER_RE.findall(text)),
        "has_prime_tower_geometry": bool(PRIME_TOWER_RE.search(text)),
        "thumbnail_count": len(thumbnails),
        "thumbnails": thumbnails,
        "filament": scalar_fields.get("filament", ""),
        "filament_colour": scalar_fields.get("filament_colour", ""),
        "filament_colour_count": list_count(scalar_fields.get("filament_colour", "")),
        "filament_map": scalar_fields.get("filament_map", ""),
        "filament_map_count": list_count(scalar_fields.get("filament_map", "")),
        "nozzle_diameter": scalar_fields.get("nozzle_diameter", ""),
        "nozzle_diameter_count": list_count(scalar_fields.get("nozzle_diameter", "")),
        "purge_in_prime_tower": scalar_fields.get("purge_in_prime_tower", ""),
        "single_extruder_multi_material": scalar_fields.get("single_extruder_multi_material", ""),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", required=True, type=Path, help="Desktop Orca .gcode.3mf or .gcode reference")
    parser.add_argument("--mobile-gcode", required=True, type=Path, help="MobileSlicer G-code to compare")
    parser.add_argument("--min-tools", type=int, default=2)
    parser.add_argument("--require-prime-tower", action="store_true")
    parser.add_argument("--require-thumbnails", action="store_true")
    parser.add_argument("--require-reference-filament-map", action="store_true")
    parser.add_argument("--require-reference-filament-colour", action="store_true")
    parser.add_argument("--require-reference-nozzle-diameter", action="store_true")
    parser.add_argument("--require-reference-prime-tower-settings", action="store_true")
    parser.add_argument("--min-prime-tower-features", type=int, default=1)
    args = parser.parse_args()

    reference_text = read_reference_gcode(args.reference)
    mobile_text = read_reference_gcode(args.mobile_gcode)
    reference = gcode_summary(reference_text)
    mobile = gcode_summary(mobile_text)
    report = {"reference": reference, "mobile": mobile}
    print(json.dumps(report, indent=2, sort_keys=True))

    failures: list[dict[str, object]] = []

    def add_failure(
        check: str,
        message: str,
        *,
        exit_code: int = 1,
        reference_value: object | None = None,
        mobile_value: object | None = None,
    ) -> None:
        failure: dict[str, object] = {
            "check": check,
            "message": message,
            "exit_code": exit_code,
        }
        if reference_value is not None:
            failure["reference"] = reference_value
        if mobile_value is not None:
            failure["mobile"] = mobile_value
        failures.append(failure)

    reference_tools = set(reference["tool_ids"])
    mobile_tools = set(mobile["tool_ids"])
    if len(reference_tools) < args.min_tools:
        add_failure(
            "reference_tool_count",
            "reference does not prove multi-tool output",
            exit_code=2,
            reference_value=sorted(reference_tools),
        )
    if len(mobile_tools) < args.min_tools:
        add_failure(
            "mobile_tool_count",
            "mobile output is not multi-tool",
            mobile_value=sorted(mobile_tools),
        )
    if not reference_tools.issubset(mobile_tools):
        add_failure(
            "tool_ids",
            "mobile output is missing reference tools",
            reference_value=sorted(reference_tools),
            mobile_value=sorted(mobile_tools),
        )
    if args.require_prime_tower and not mobile["has_prime_tower_geometry"]:
        add_failure(
            "prime_tower_geometry",
            "mobile output is missing ;TYPE:Prime tower geometry",
            mobile_value=mobile["has_prime_tower_geometry"],
        )
    if args.require_prime_tower and int(mobile["prime_tower_feature_count"]) < args.min_prime_tower_features:
        add_failure(
            "prime_tower_feature_count",
            f"mobile prime tower feature count is below {args.min_prime_tower_features}",
            mobile_value=mobile["prime_tower_feature_count"],
        )
    if args.require_thumbnails and int(mobile["thumbnail_count"]) <= 0:
        add_failure(
            "thumbnails",
            "mobile output is missing embedded thumbnail blocks",
            mobile_value=mobile["thumbnail_count"],
        )
    if args.require_reference_filament_map:
        reference_map = str(reference["filament_map"]).strip()
        mobile_map = str(mobile["filament_map"]).strip()
        if not reference_map:
            add_failure("filament_map", "reference output has no filament_map to compare", exit_code=2)
        elif mobile_map != reference_map:
            add_failure(
                "filament_map",
                "mobile filament_map does not match reference",
                reference_value=reference_map,
                mobile_value=mobile_map,
            )
    if args.require_reference_filament_colour:
        reference_colour = str(reference["filament_colour"]).strip()
        mobile_colour = str(mobile["filament_colour"]).strip()
        if not reference_colour:
            add_failure("filament_colour", "reference output has no filament_colour to compare", exit_code=2)
        elif mobile_colour != reference_colour:
            add_failure(
                "filament_colour",
                "mobile filament_colour does not match reference",
                reference_value=reference_colour,
                mobile_value=mobile_colour,
            )
    if args.require_reference_nozzle_diameter:
        reference_nozzles = str(reference["nozzle_diameter"]).strip()
        mobile_nozzles = str(mobile["nozzle_diameter"]).strip()
        if not reference_nozzles:
            add_failure("nozzle_diameter", "reference output has no nozzle_diameter to compare", exit_code=2)
        elif mobile_nozzles != reference_nozzles:
            add_failure(
                "nozzle_diameter",
                "mobile nozzle_diameter does not match reference",
                reference_value=reference_nozzles,
                mobile_value=mobile_nozzles,
            )
    if args.require_reference_prime_tower_settings:
        for key in ("purge_in_prime_tower", "single_extruder_multi_material"):
            reference_value = str(reference[key]).strip()
            mobile_value = str(mobile[key]).strip()
            if not reference_value:
                add_failure(key, f"reference output has no {key} to compare", exit_code=2)
            elif mobile_value != reference_value:
                add_failure(
                    key,
                    f"mobile {key} does not match reference",
                    reference_value=reference_value,
                    mobile_value=mobile_value,
                )
    if failures:
        print(json.dumps({"failures": failures}, indent=2, sort_keys=True), file=sys.stderr)
        return max(int(failure["exit_code"]) for failure in failures)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
