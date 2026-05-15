#!/usr/bin/env python3
"""Audit checked-in desktop Orca thumbnail visual references.

This is the fast local gate for the visual-reference matrix used by
`scripts/run_orca_thumbnail_reference_matrix.sh`. It validates the matrix,
reference manifests, source packages, role PNGs, and basic image metrics before
any ADB/device comparison starts.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

sys.path.insert(0, str(Path(__file__).resolve().parent))

from orca_metadata_audit import PngImageMetrics, png_image_metrics


DEFAULT_MATRIX = Path("regression-fixtures/orca-thumbnail-references/matrix.json")
ROLES = ("plate", "no_light", "top", "pick")
ROLE_ENTRIES = {
    "plate": "Metadata/plate_1.png",
    "no_light": "Metadata/plate_no_light_1.png",
    "top": "Metadata/top_1.png",
    "pick": "Metadata/pick_1.png",
}


@dataclass(frozen=True)
class AuditFailure:
    case_id: str
    check: str
    message: str


def repo_path(repo_root: Path, raw_path: str | None) -> Path | None:
    if not raw_path:
        return None
    path = Path(raw_path)
    return path if path.is_absolute() else repo_root / path


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def metric_summary(metrics: PngImageMetrics) -> dict[str, Any]:
    return {
        "width": metrics.width,
        "height": metrics.height,
        "nontransparent_pixels": metrics.nontransparent_pixels,
        "alpha_coverage": metrics.alpha_coverage,
        "average_luma": metrics.average_luma,
        "bbox": metrics.bbox,
        "unique_rgba": metrics.unique_rgba,
    }


def role_from_manifest(manifest: dict[str, Any], role: str) -> dict[str, Any] | None:
    roles = manifest.get("roles", [])
    if not isinstance(roles, list):
        return None
    for item in roles:
        if isinstance(item, dict) and item.get("role") == role:
            return item
    return None


def unique_nonblank(values: list[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value).strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


def xml_metadata_values(payload: bytes, key: str) -> list[str]:
    try:
        root = ElementTree.fromstring(payload)
    except ElementTree.ParseError:
        return []
    values: list[str] = []
    for element in root.iter():
        if element.tag.endswith("metadata") and element.attrib.get("key") == key:
            values.append(element.attrib.get("value", ""))
    return values


def inspect_active_filament_metadata(package: Path) -> dict[str, Any]:
    with zipfile.ZipFile(package) as archive:
        names = set(archive.namelist())
        plate_json: dict[str, Any] = {}
        if "Metadata/plate_1.json" in names:
            plate_json = json.loads(archive.read("Metadata/plate_1.json").decode("utf-8"))
        slice_info = archive.read("Metadata/slice_info.config") if "Metadata/slice_info.config" in names else b""
        model_settings = archive.read("Metadata/model_settings.config") if "Metadata/model_settings.config" in names else b""

    slice_filament_ids: list[str] = []
    slice_filament_colors: list[str] = []
    if slice_info:
        try:
            root = ElementTree.fromstring(slice_info)
            for element in root.iter():
                if element.tag.endswith("filament"):
                    slice_filament_ids.append(element.attrib.get("id", ""))
                    slice_filament_colors.append(element.attrib.get("color", ""))
        except ElementTree.ParseError:
            pass

    return {
        "plate_json_filament_ids": unique_nonblank(plate_json.get("filament_ids", [])),
        "plate_json_filament_colors": unique_nonblank(plate_json.get("filament_colors", [])),
        "slice_info_filament_ids": unique_nonblank(slice_filament_ids),
        "slice_info_filament_colors": unique_nonblank(slice_filament_colors),
        "slice_info_filament_maps": unique_nonblank(xml_metadata_values(slice_info, "filament_maps")) if slice_info else [],
        "model_object_extruders": unique_nonblank(xml_metadata_values(model_settings, "extruder")) if model_settings else [],
    }


def active_filament_requirement(case: dict[str, Any], manifest: dict[str, Any]) -> int:
    requirements = case.get("requirements") if isinstance(case.get("requirements"), dict) else {}
    manifest_requirements = manifest.get("requirements") if isinstance(manifest.get("requirements"), dict) else {}
    explicit = requirements.get("active_filaments", manifest_requirements.get("active_filaments", 0))
    try:
        explicit_count = int(explicit)
    except (TypeError, ValueError):
        explicit_count = 0
    if explicit_count > 0:
        return explicit_count
    if case.get("source_layout") == "two_filament_objects" or manifest.get("source_layout") == "two_filament_objects":
        return 2
    return 0


def audit_case(repo_root: Path, case: dict[str, Any]) -> tuple[dict[str, Any], list[AuditFailure]]:
    case_id = str(case.get("id", "<missing>"))
    failures: list[AuditFailure] = []
    model_path = repo_path(repo_root, case.get("model"))
    reference_dir = repo_path(repo_root, case.get("reference_dir"))
    bed = case.get("bed") if isinstance(case.get("bed"), dict) else {}
    normalized_bed = {
        "width_mm": bed.get("width_mm", 270),
        "depth_mm": bed.get("depth_mm", 270),
        "height_mm": bed.get("height_mm", 256),
    }
    output: dict[str, Any] = {
        "id": case_id,
        "model": str(model_path) if model_path else None,
        "reference_dir": str(reference_dir) if reference_dir else None,
        "bed": normalized_bed,
        "profiles": case.get("profiles"),
        "source_layout": case.get("source_layout", "single"),
        "source_colors": case.get("source_colors", []),
        "roles": {},
    }

    if model_path is None or not model_path.is_file():
        failures.append(AuditFailure(case_id, "missing-model", f"missing model: {model_path}"))
    if reference_dir is None or not reference_dir.is_dir():
        failures.append(AuditFailure(case_id, "missing-reference-dir", f"missing reference dir: {reference_dir}"))
        output["status"] = "failed"
        return output, failures

    manifest_path = reference_dir / "manifest.json"
    if not manifest_path.is_file():
        failures.append(AuditFailure(case_id, "missing-manifest", f"missing manifest: {manifest_path}"))
        output["status"] = "failed"
        return output, failures
    try:
        manifest = load_json(manifest_path)
    except json.JSONDecodeError as exc:
        failures.append(AuditFailure(case_id, "invalid-manifest-json", f"{manifest_path}: {exc}"))
        output["status"] = "failed"
        return output, failures

    output["manifest"] = str(manifest_path)
    if manifest.get("schema_version") != 1:
        failures.append(AuditFailure(case_id, "manifest-schema", "manifest schema_version must be 1"))
    if not str(manifest.get("orca_build", "")).strip():
        failures.append(AuditFailure(case_id, "manifest-orca-build", "manifest must record orca_build"))

    source_3mf = repo_path(repo_root, manifest.get("source_3mf"))
    if source_3mf is None or not source_3mf.is_file():
        local_packages = sorted(reference_dir.glob("*.gcode.3mf"))
        source_3mf = local_packages[0] if local_packages else source_3mf
    if source_3mf is None or not source_3mf.is_file():
        failures.append(AuditFailure(case_id, "missing-source-3mf", f"missing source_3mf: {source_3mf}"))
    else:
        output["source_3mf"] = str(source_3mf)
        try:
            with zipfile.ZipFile(source_3mf) as archive:
                names = set(archive.namelist())
        except zipfile.BadZipFile as exc:
            failures.append(AuditFailure(case_id, "invalid-source-3mf", f"{source_3mf}: {exc}"))
            names = set()
        for role, entry in ROLE_ENTRIES.items():
            if entry not in names:
                failures.append(AuditFailure(case_id, f"missing-3mf-{role}", f"{source_3mf} lacks {entry}"))
        minimum_active_filaments = active_filament_requirement(case, manifest)
        if minimum_active_filaments > 0 and names:
            try:
                active_metadata = inspect_active_filament_metadata(source_3mf)
            except (json.JSONDecodeError, UnicodeDecodeError, zipfile.BadZipFile) as exc:
                failures.append(
                    AuditFailure(case_id, "active-filament-metadata-parse", f"{source_3mf}: {exc}")
                )
                active_metadata = {}
            output["active_filament_metadata"] = active_metadata
            required_fields = (
                "plate_json_filament_ids",
                "plate_json_filament_colors",
                "slice_info_filament_ids",
                "slice_info_filament_colors",
                "model_object_extruders",
            )
            for field in required_fields:
                values = active_metadata.get(field, [])
                count = len(values) if isinstance(values, list) else 0
                if count < minimum_active_filaments:
                    failures.append(
                        AuditFailure(
                            case_id,
                            "active-filament-metadata",
                            f"{source_3mf}:{field} has {count}, expected >= {minimum_active_filaments}",
                        )
                    )

    seen_roles: set[str] = set()
    for role in ROLES:
        png_path = reference_dir / f"{role}.png"
        manifest_role = role_from_manifest(manifest, role)
        if manifest_role is None:
            failures.append(AuditFailure(case_id, f"missing-manifest-role-{role}", f"manifest lacks role {role}"))
        else:
            seen_roles.add(role)
        if not png_path.is_file():
            failures.append(AuditFailure(case_id, f"missing-role-png-{role}", f"missing {png_path}"))
            continue
        try:
            metrics = png_image_metrics(png_path.read_bytes())
        except ValueError as exc:
            failures.append(AuditFailure(case_id, f"invalid-role-png-{role}", f"{png_path}: {exc}"))
            continue
        if metrics.width != 512 or metrics.height != 512:
            failures.append(
                AuditFailure(case_id, f"role-dimensions-{role}", f"{png_path} is {metrics.width}x{metrics.height}, expected 512x512")
            )
        if metrics.nontransparent_pixels <= 0:
            failures.append(AuditFailure(case_id, f"blank-role-png-{role}", f"{png_path} is blank"))
        if metrics.bbox is None:
            failures.append(AuditFailure(case_id, f"missing-role-bbox-{role}", f"{png_path} has no visible bbox"))
        output["roles"][role] = {
            "path": str(png_path),
            "source_entry": ROLE_ENTRIES[role],
            "metrics": metric_summary(metrics),
        }
    extra_roles = sorted(
        str(item.get("role"))
        for item in manifest.get("roles", [])
        if isinstance(item, dict) and item.get("role") not in ROLES
    )
    if extra_roles:
        failures.append(AuditFailure(case_id, "extra-manifest-roles", f"unexpected roles: {', '.join(extra_roles)}"))

    output["status"] = "failed" if failures else "checked"
    return output, failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    parser.add_argument("--case", action="append", default=[], help="run only this matrix case id")
    parser.add_argument("--list-shell", action="store_true", help="print tab-separated case, model, and reference_dir triples for shell scripts")
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    matrix_path = args.matrix if args.matrix.is_absolute() else repo_root / args.matrix
    matrix = load_json(matrix_path)
    cases = matrix.get("cases", [])
    if matrix.get("schema_version") != 1:
        raise SystemExit("matrix schema_version must be 1")
    if not isinstance(cases, list):
        raise SystemExit("matrix cases must be a list")
    selected = set(args.case)

    if args.list_shell:
        for case in cases:
            if not isinstance(case, dict):
                continue
            case_id = str(case.get("id", ""))
            if selected and case_id not in selected:
                continue
            model_path = repo_path(repo_root, case.get("model"))
            reference_dir = repo_path(repo_root, case.get("reference_dir"))
            profiles = case.get("profiles") if isinstance(case.get("profiles"), dict) else {}
            bed = case.get("bed") if isinstance(case.get("bed"), dict) else {}
            source_colors = case.get("source_colors", [])
            if not isinstance(source_colors, list):
                source_colors = []
            fields = [
                case_id,
                str(model_path),
                str(reference_dir),
                str(repo_path(repo_root, profiles.get("machine")) or ""),
                str(repo_path(repo_root, profiles.get("process")) or ""),
                str(repo_path(repo_root, profiles.get("filament")) or ""),
                str(profiles.get("printer_name", "")),
                str(profiles.get("process_name", "")),
                str(profiles.get("filament_name", "")),
                str(bed.get("width_mm", 270)),
                str(bed.get("depth_mm", 270)),
                str(bed.get("height_mm", 256)),
                str(case.get("source_layout", "single")),
                ",".join(str(color) for color in source_colors),
            ]
            print("\t".join(fields))
        return 0

    outputs: list[dict[str, Any]] = []
    failures: list[AuditFailure] = []
    seen_ids: set[str] = set()
    for case in cases:
        if not isinstance(case, dict):
            failures.append(AuditFailure("<matrix>", "invalid-case", "case entry must be an object"))
            continue
        case_id = str(case.get("id", ""))
        if not case_id:
            failures.append(AuditFailure("<matrix>", "missing-case-id", "case entry lacks id"))
            continue
        if case_id in seen_ids:
            failures.append(AuditFailure(case_id, "duplicate-case-id", f"duplicate case id {case_id}"))
            continue
        seen_ids.add(case_id)
        if selected and case_id not in selected:
            continue
        output, case_failures = audit_case(repo_root, case)
        outputs.append(output)
        failures.extend(case_failures)
    for missing in sorted(selected.difference(seen_ids)):
        failures.append(AuditFailure(missing, "unknown-case", f"matrix has no case {missing!r}"))

    payload = {
        "ok": not failures,
        "matrix": str(matrix_path),
        "cases": outputs,
        "summary": {
            "cases": len(outputs),
            "failures": len(failures),
        },
        "failures": [asdict(failure) for failure in failures],
    }
    print(json.dumps(payload, indent=2 if args.pretty else None, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
