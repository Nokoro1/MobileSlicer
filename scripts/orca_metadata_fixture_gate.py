#!/usr/bin/env python3
"""Run the committed Orca metadata parity fixture manifest.

This gate is intentionally split from ``orca_metadata_audit.py``. The audit
knows how to inspect one output. The fixture gate knows which Orca reference
outputs are required, which MobileSlicer outputs may be present, and when a
missing desktop reference should fail the build.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

import orca_metadata_audit as audit


DEFAULT_MANIFEST = Path("regression-fixtures/orca-metadata/manifest.json")


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid JSON manifest {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise SystemExit(f"manifest root must be an object: {path}")
    return value


def repo_path(repo_root: Path, raw_path: str | None) -> Path | None:
    if not raw_path:
        return None
    path = Path(raw_path)
    if path.is_absolute():
        return path
    return repo_root / path


def require_list(value: Any, field: str) -> list[Any]:
    if value is None:
        return []
    if not isinstance(value, list):
        raise ValueError(f"{field} must be a list")
    return value


def require_string_list(value: Any, field: str) -> list[str]:
    result: list[str] = []
    for item in require_list(value, field):
        if not isinstance(item, str):
            raise ValueError(f"{field} entries must be strings")
        result.append(item)
    return result


def parse_thumbnail_expectations(values: list[str]) -> list[audit.ThumbnailBlock]:
    parsed: list[audit.ThumbnailBlock] = []
    for value in values:
        parsed.append(audit.parse_expected_thumbnail(value))
    return parsed


def inspect_gcode_if_present(path: Path | None) -> audit.GcodeMetadata | None:
    if path is None or not path.exists():
        return None
    return audit.inspect_gcode(path)


def inspect_3mf_if_present(path: Path | None) -> dict[str, object] | None:
    if path is None or not path.exists():
        return None
    return audit.inspect_3mf(path)


def append_case_failures(case_output: dict[str, Any], failures: list[audit.AuditFailure]) -> None:
    if not failures:
        return
    existing = case_output.setdefault("failures", [])
    assert isinstance(existing, list)
    existing.extend(asdict(failure) for failure in failures)


def prefixed_failures(prefix: str, failures: list[audit.AuditFailure]) -> list[audit.AuditFailure]:
    return [
        audit.AuditFailure(
            check=f"{prefix}-{failure.check}",
            message=f"{prefix}: {failure.message}",
        )
        for failure in failures
    ]


def validate_manifest_case(case: dict[str, Any]) -> None:
    for field in ("id", "kind"):
        if not isinstance(case.get(field), str) or not case[field]:
            raise ValueError(f"case field {field!r} is required")
    if case["kind"] not in {"gcode", "3mf"}:
        raise ValueError(f"case {case['id']} has unsupported kind {case['kind']!r}")
    paths = case.get("paths", {})
    if not isinstance(paths, dict):
        raise ValueError(f"case {case['id']} paths must be an object")
    expectations = case.get("expectations", {})
    if not isinstance(expectations, dict):
        raise ValueError(f"case {case['id']} expectations must be an object")


def missing_reference_paths(repo_root: Path, case: dict[str, Any]) -> list[str]:
    paths = case.get("paths", {})
    assert isinstance(paths, dict)
    missing: list[str] = []
    for key in ("orca_gcode", "orca_3mf"):
        path = repo_path(repo_root, paths.get(key))
        if path is not None and not path.exists():
            missing.append(str(path))
    return missing


def evaluate_gcode_case(repo_root: Path, case: dict[str, Any], strict_references: bool) -> dict[str, Any]:
    paths = case.get("paths", {})
    expectations = case.get("expectations", {})
    assert isinstance(paths, dict)
    assert isinstance(expectations, dict)

    mobile_path = repo_path(repo_root, paths.get("mobile_gcode"))
    orca_path = repo_path(repo_root, paths.get("orca_gcode"))
    mobile = inspect_gcode_if_present(mobile_path)
    orca = inspect_gcode_if_present(orca_path)

    output: dict[str, Any] = {
        "id": case["id"],
        "kind": "gcode",
        "paths": {
            "mobile_gcode": str(mobile_path) if mobile_path else None,
            "orca_gcode": str(orca_path) if orca_path else None,
        },
        "status": "checked",
    }
    if mobile_path and not mobile_path.exists():
        output.setdefault("missing_outputs", []).append(str(mobile_path))
        if strict_references:
            append_case_failures(
                output,
                [audit.AuditFailure("missing-mobile-output", f"missing MobileSlicer fixture output: {mobile_path}")],
            )
    if orca_path and not orca_path.exists():
        output.setdefault("missing_references", []).append(str(orca_path))
        if strict_references:
            append_case_failures(
                output,
                [audit.AuditFailure("missing-orca-reference", f"missing Orca reference output: {orca_path}")],
            )

    if mobile is None and orca is None:
        output["status"] = "failed" if output.get("failures") else "skipped"
        return output

    expected_thumbnails = parse_thumbnail_expectations(
        require_string_list(expectations.get("expect_thumbnails"), "expect_thumbnails")
    )
    forbidden_tags = set(require_string_list(expectations.get("forbid_thumbnail_tags"), "forbid_thumbnail_tags"))
    require_print_time = bool(expectations.get("require_print_time", False))
    require_filament = bool(expectations.get("require_filament", False))
    require_toolchange = bool(expectations.get("require_toolchange", False))
    require_thumbnail_visuals = bool(expectations.get("require_gcode_thumbnail_visuals", False))
    require_thumbnail_antialias = bool(expectations.get("require_gcode_thumbnail_antialias", False))
    require_thumbnail_payload_signatures = bool(
        expectations.get("require_gcode_thumbnail_payload_signatures", False)
    )

    if mobile is not None:
        output["mobile"] = asdict(mobile)
        append_case_failures(
            output,
            audit.validate_gcode_expectations(
                metadata=mobile,
                expected_thumbnails=expected_thumbnails,
                forbidden_tags=forbidden_tags,
                require_print_time=require_print_time,
                require_filament=require_filament,
                require_toolchange=require_toolchange,
                require_thumbnail_visuals=require_thumbnail_visuals,
                require_thumbnail_antialias=require_thumbnail_antialias,
                require_thumbnail_payload_signatures=require_thumbnail_payload_signatures,
            ),
        )
    if orca is not None:
        output["orca"] = asdict(orca)
        validate_orca_thumbnails = bool(expectations.get("validate_orca_thumbnails", True))
        orca_expected_thumbnails = expected_thumbnails if validate_orca_thumbnails else []
        append_case_failures(
            output,
            prefixed_failures(
                "orca",
                audit.validate_gcode_expectations(
                    metadata=orca,
                    expected_thumbnails=orca_expected_thumbnails,
                    forbidden_tags=forbidden_tags,
                    require_print_time=require_print_time,
                    require_filament=require_filament,
                    require_toolchange=require_toolchange,
                    require_thumbnail_visuals=require_thumbnail_visuals and validate_orca_thumbnails,
                    require_thumbnail_antialias=require_thumbnail_antialias and validate_orca_thumbnails,
                    require_thumbnail_payload_signatures=require_thumbnail_payload_signatures
                    and validate_orca_thumbnails,
                ),
            ),
        )

    if mobile is not None and orca is not None:
        output["comparison"] = audit.compare_gcode(mobile, orca)["comparison"]
        comparison = output["comparison"]
        assert isinstance(comparison, dict)
        if expectations.get("require_stripped_hash_match", False) and not comparison.get("stripped_body_hash_match"):
            append_case_failures(
                output,
                [audit.AuditFailure("stripped-body-hash", "MobileSlicer and Orca stripped G-code body hashes differ")],
            )
    return output


def evaluate_3mf_case(repo_root: Path, case: dict[str, Any], strict_references: bool) -> dict[str, Any]:
    paths = case.get("paths", {})
    expectations = case.get("expectations", {})
    assert isinstance(paths, dict)
    assert isinstance(expectations, dict)

    mobile_path = repo_path(repo_root, paths.get("mobile_3mf"))
    orca_path = repo_path(repo_root, paths.get("orca_3mf"))
    mobile = inspect_3mf_if_present(mobile_path)
    orca = inspect_3mf_if_present(orca_path)

    output: dict[str, Any] = {
        "id": case["id"],
        "kind": "3mf",
        "paths": {
            "mobile_3mf": str(mobile_path) if mobile_path else None,
            "orca_3mf": str(orca_path) if orca_path else None,
        },
        "status": "checked",
    }
    if mobile_path and not mobile_path.exists():
        output.setdefault("missing_outputs", []).append(str(mobile_path))
        if strict_references:
            append_case_failures(
                output,
                [audit.AuditFailure("missing-mobile-output", f"missing MobileSlicer fixture output: {mobile_path}")],
            )
    if orca_path and not orca_path.exists():
        output.setdefault("missing_references", []).append(str(orca_path))
        if strict_references:
            append_case_failures(
                output,
                [audit.AuditFailure("missing-orca-reference", f"missing Orca reference output: {orca_path}")],
            )

    if mobile is None and orca is None:
        output["status"] = "failed" if output.get("failures") else "skipped"
        return output

    expected_entries = require_string_list(expectations.get("expect_3mf_entries"), "expect_3mf_entries")
    expected_thumbnail_entries = require_string_list(
        expectations.get("expect_3mf_thumbnail_entries"),
        "expect_3mf_thumbnail_entries",
    )
    expected_relationship_targets = require_string_list(
        expectations.get("expect_3mf_relationship_targets"),
        "expect_3mf_relationship_targets",
    )
    expected_bbox_json = require_string_list(expectations.get("expect_3mf_bbox_json"), "expect_3mf_bbox_json")
    expected_different_entries = require_string_list(
        expectations.get("expect_different_3mf_entries"),
        "expect_different_3mf_entries",
    )
    require_distinct = bool(expectations.get("require_distinct_3mf_thumbnail_entries", False))
    require_thumbnail_visuals = bool(expectations.get("require_3mf_thumbnail_visuals", False))

    if mobile is not None:
        output["mobile_3mf"] = mobile
        append_case_failures(
            output,
            audit.validate_3mf_expectations(
                metadata=mobile,
                expected_entries=expected_entries,
                expected_thumbnail_entries=expected_thumbnail_entries,
                expected_relationship_targets=expected_relationship_targets,
                expected_bbox_json_entries=expected_bbox_json,
                expected_different_entries=expected_different_entries,
                require_distinct_thumbnail_entries=require_distinct,
                require_thumbnail_visuals=require_thumbnail_visuals,
            ),
        )
    if orca is not None:
        output["orca_3mf"] = orca
        orca_expected_thumbnail_entries = (
            expected_thumbnail_entries if expectations.get("validate_orca_3mf_thumbnails", True) else []
        )
        orca_expected_relationship_targets = (
            expected_relationship_targets if expectations.get("validate_orca_3mf_relationship_targets", True) else []
        )
        append_case_failures(
            output,
            prefixed_failures(
                "orca",
                audit.validate_3mf_expectations(
                    metadata=orca,
                    expected_entries=expected_entries,
                    expected_thumbnail_entries=orca_expected_thumbnail_entries,
                    expected_relationship_targets=orca_expected_relationship_targets,
                    expected_bbox_json_entries=expected_bbox_json,
                    expected_different_entries=expected_different_entries,
                    require_distinct_thumbnail_entries=require_distinct
                    and expectations.get("validate_orca_3mf_thumbnails", True),
                    require_thumbnail_visuals=require_thumbnail_visuals
                    and expectations.get("validate_orca_3mf_thumbnails", True),
                    require_positive_first_layer_time=False,
                ),
            ),
        )
    return output


def evaluate_case(repo_root: Path, case: dict[str, Any], strict_references: bool) -> dict[str, Any]:
    validate_manifest_case(case)
    if case.get("status") == "pending":
        if strict_references:
            return {
                "id": case["id"],
                "kind": case["kind"],
                "status": "failed",
                "reason": str(case.get("notes") or "pending fixture"),
                "failures": [
                    asdict(
                        audit.AuditFailure(
                            "pending-fixture",
                            f"strict fixture gate cannot pass while case {case['id']!r} is pending",
                        )
                    )
                ],
            }
        return {
            "id": case["id"],
            "kind": case["kind"],
            "status": "skipped",
            "reason": str(case.get("notes") or "pending fixture"),
        }
    if case["kind"] == "gcode":
        return evaluate_gcode_case(repo_root, case, strict_references)
    return evaluate_3mf_case(repo_root, case, strict_references)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST, help="fixture manifest path")
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--case", action="append", default=[], help="run only this manifest case id")
    parser.add_argument(
        "--strict-references",
        action="store_true",
        help="fail when declared desktop Orca reference outputs are missing",
    )
    parser.add_argument("--pretty", action="store_true", help="pretty-print JSON output")
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    manifest_path = args.manifest if args.manifest.is_absolute() else repo_root / args.manifest
    manifest = load_json(manifest_path)
    cases = manifest.get("cases", [])
    if not isinstance(cases, list):
        raise SystemExit("manifest field 'cases' must be a list")
    selected = set(args.case)

    output: dict[str, Any] = {
        "manifest": str(manifest_path),
        "schema_version": manifest.get("schema_version"),
        "strict_references": args.strict_references,
        "cases": [],
    }
    if manifest.get("orca_reference"):
        output["orca_reference"] = manifest["orca_reference"]

    failures = 0
    for raw_case in cases:
        if not isinstance(raw_case, dict):
            raise SystemExit("manifest cases must be objects")
        case_id = raw_case.get("id")
        if selected and case_id not in selected:
            continue
        try:
            case_output = evaluate_case(repo_root, raw_case, args.strict_references)
        except (ValueError, argparse.ArgumentTypeError) as exc:
            case_output = {
                "id": str(case_id),
                "status": "invalid",
                "failures": [asdict(audit.AuditFailure("invalid-manifest-case", str(exc)))],
            }
        if case_output.get("failures"):
            failures += len(case_output["failures"])
        output["cases"].append(case_output)

    if selected:
        seen = {case["id"] for case in output["cases"] if isinstance(case, dict) and "id" in case}
        missing = sorted(selected.difference(seen))
        for case_id in missing:
            output["cases"].append(
                {
                    "id": case_id,
                    "status": "invalid",
                    "failures": [asdict(audit.AuditFailure("unknown-case", f"manifest has no case {case_id!r}"))],
                }
            )
            failures += 1

    output["summary"] = {
        "cases": len(output["cases"]),
        "failures": failures,
        "skipped": sum(1 for case in output["cases"] if isinstance(case, dict) and case.get("status") == "skipped"),
    }
    json.dump(output, sys.stdout, indent=2 if args.pretty else None, sort_keys=True)
    sys.stdout.write("\n")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
