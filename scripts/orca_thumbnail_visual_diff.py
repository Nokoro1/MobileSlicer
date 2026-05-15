#!/usr/bin/env python3
"""Compare MobileSlicer thumbnail role images against Orca-style references.

This script is intentionally metric-based. It does not claim pixel identity:
different GL drivers and render paths can produce different antialiasing, but
role images still need comparable framing, coverage, and brightness.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from orca_metadata_audit import PngImageMetrics, png_image_metrics


ROLES = ("plate", "no_light", "top", "pick")

ROLE_ALIASES = {
    "plate": ("{prefix}plate.png", "plate.png", "plate_1.png", "Metadata/plate_1.png"),
    "no_light": (
        "{prefix}no_light.png",
        "no_light.png",
        "plate_no_light_1.png",
        "Metadata/plate_no_light_1.png",
    ),
    "top": ("{prefix}top.png", "top.png", "top_1.png", "Metadata/top_1.png"),
    "pick": ("{prefix}pick.png", "pick.png", "pick_1.png", "Metadata/pick_1.png"),
}


@dataclass(frozen=True)
class LoadedRoleImage:
    role: str
    source: str
    metrics: PngImageMetrics


@dataclass(frozen=True)
class RoleDiff:
    role: str
    mobile_source: str
    reference_source: str
    mobile: dict[str, object]
    reference: dict[str, object]
    alpha_coverage_delta: float
    average_luma_delta: float
    bbox_max_delta_px: int | None
    coverage_ratio: float | None
    failures: list[str]


def read_role_bytes(source: Path, role: str, prefix: str) -> tuple[str, bytes] | None:
    aliases = [name.format(prefix=prefix) for name in ROLE_ALIASES[role]]
    if source.is_dir():
        for alias in aliases:
            candidates = [source / alias]
            if alias.startswith("Metadata/"):
                candidates.append(source / alias.removeprefix("Metadata/"))
            for candidate in candidates:
                if candidate.is_file():
                    return str(candidate), candidate.read_bytes()
        return None
    if source.is_file():
        with zipfile.ZipFile(source) as archive:
            names = set(archive.namelist())
            for alias in aliases:
                if alias in names:
                    return f"{source}:{alias}", archive.read(alias)
                metadata_alias = f"Metadata/{alias}"
                if metadata_alias in names:
                    return f"{source}:{metadata_alias}", archive.read(metadata_alias)
    return None


def load_role_images(source: Path, prefix: str) -> tuple[dict[str, LoadedRoleImage], list[str]]:
    images: dict[str, LoadedRoleImage] = {}
    missing: list[str] = []
    for role in ROLES:
        loaded = read_role_bytes(source, role, prefix)
        if loaded is None:
            missing.append(role)
            continue
        source_name, payload = loaded
        try:
            metrics = png_image_metrics(payload)
        except ValueError as exc:
            raise SystemExit(f"{source_name}: {exc}") from exc
        images[role] = LoadedRoleImage(role=role, source=source_name, metrics=metrics)
    return images, missing


def scale_bbox(
    bbox: list[int] | None,
    source_width: int,
    source_height: int,
    target_width: int,
    target_height: int,
) -> list[int] | None:
    if bbox is None:
        return None
    if source_width <= 0 or source_height <= 0:
        return None
    scale_x = target_width / source_width
    scale_y = target_height / source_height
    return [
        round(bbox[0] * scale_x),
        round(bbox[1] * scale_y),
        round((bbox[2] + 1) * scale_x) - 1,
        round((bbox[3] + 1) * scale_y) - 1,
    ]


def bbox_delta(a: list[int] | None, b: list[int] | None) -> int | None:
    if a is None or b is None:
        return None
    return max(abs(left - right) for left, right in zip(a, b))


def compare_role(
    mobile: LoadedRoleImage,
    reference: LoadedRoleImage,
    max_luma_delta: float,
    max_alpha_coverage_delta: float,
    max_bbox_delta_px: int,
    min_coverage_ratio: float,
    max_coverage_ratio: float,
) -> RoleDiff:
    mobile_metrics = mobile.metrics
    reference_metrics = reference.metrics
    failures: list[str] = []
    alpha_delta = abs(mobile_metrics.alpha_coverage - reference_metrics.alpha_coverage)
    luma_delta = abs(mobile_metrics.average_luma - reference_metrics.average_luma)
    mobile_bbox = scale_bbox(
        mobile_metrics.bbox,
        mobile_metrics.width,
        mobile_metrics.height,
        reference_metrics.width,
        reference_metrics.height,
    )
    delta_bbox = bbox_delta(mobile_bbox, reference_metrics.bbox)
    ratio = (
        mobile_metrics.alpha_coverage / reference_metrics.alpha_coverage
        if reference_metrics.alpha_coverage
        else None
    )
    if mobile_metrics.nontransparent_pixels <= 0:
        failures.append("mobile role image is blank")
    if reference_metrics.nontransparent_pixels <= 0:
        failures.append("reference role image is blank")
    if alpha_delta > max_alpha_coverage_delta:
        failures.append(
            f"alpha coverage delta {alpha_delta:.4f} exceeds {max_alpha_coverage_delta:.4f}"
        )
    if luma_delta > max_luma_delta:
        failures.append(f"average luma delta {luma_delta:.2f} exceeds {max_luma_delta:.2f}")
    if delta_bbox is not None and delta_bbox > max_bbox_delta_px:
        failures.append(f"bbox max delta {delta_bbox}px exceeds {max_bbox_delta_px}px")
    if ratio is not None and (ratio < min_coverage_ratio or ratio > max_coverage_ratio):
        failures.append(
            f"coverage ratio {ratio:.3f} outside {min_coverage_ratio:.3f}..{max_coverage_ratio:.3f}"
        )
    return RoleDiff(
        role=mobile.role,
        mobile_source=mobile.source,
        reference_source=reference.source,
        mobile=asdict(mobile_metrics),
        reference=asdict(reference_metrics),
        alpha_coverage_delta=alpha_delta,
        average_luma_delta=luma_delta,
        bbox_max_delta_px=delta_bbox,
        coverage_ratio=ratio,
        failures=failures,
    )


def write_markdown(path: Path, payload: dict[str, object]) -> None:
    status = payload["status"]
    lines = [
        "# Orca Thumbnail Visual Diff",
        "",
        f"- status: {status}",
        f"- mobile: {payload['mobile_source']}",
        f"- reference: {payload.get('reference_source', 'none')}",
        "",
    ]
    role_diffs = payload.get("role_diffs", [])
    if role_diffs:
        lines.extend(
            [
                "| role | coverage delta | luma delta | bbox delta px | coverage ratio | result |",
                "| --- | ---: | ---: | ---: | ---: | --- |",
            ]
        )
        for role in role_diffs:
            bbox = role["bbox_max_delta_px"]
            ratio = role["coverage_ratio"]
            result = "pass" if not role["failures"] else "; ".join(role["failures"])
            lines.append(
                "| {role} | {coverage:.4f} | {luma:.2f} | {bbox} | {ratio} | {result} |".format(
                    role=role["role"],
                    coverage=role["alpha_coverage_delta"],
                    luma=role["average_luma_delta"],
                    bbox="n/a" if bbox is None else bbox,
                    ratio="n/a" if ratio is None else f"{ratio:.3f}",
                    result=result,
                )
            )
    missing = payload.get("missing_reference_roles") or []
    if missing:
        lines.extend(["", f"Missing reference roles: {', '.join(missing)}"])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mobile-dir", type=Path, required=True, help="Directory or .gcode.3mf containing mobile PNGs.")
    parser.add_argument("--reference-dir", type=Path, help="Directory or .gcode.3mf containing Orca/reference PNGs.")
    parser.add_argument("--mobile-prefix", default="egl-", help="Filename prefix for mobile role PNGs.")
    parser.add_argument("--reference-prefix", default="", help="Filename prefix for reference role PNGs.")
    parser.add_argument("--require-reference", action="store_true", help="Fail when reference PNGs are missing.")
    parser.add_argument("--report-only", action="store_true", help="Write failures into the report but exit successfully.")
    parser.add_argument("--max-luma-delta", type=float, default=80.0)
    parser.add_argument("--max-alpha-coverage-delta", type=float, default=0.35)
    parser.add_argument("--max-bbox-delta-px", type=int, default=32)
    parser.add_argument("--min-coverage-ratio", type=float, default=0.25)
    parser.add_argument("--max-coverage-ratio", type=float, default=4.0)
    parser.add_argument("--output-json", type=Path)
    parser.add_argument("--output-md", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    mobile_images, missing_mobile = load_role_images(args.mobile_dir, args.mobile_prefix)
    reference_images: dict[str, LoadedRoleImage] = {}
    missing_reference = list(ROLES)
    reference_source = None
    if args.reference_dir is not None and args.reference_dir.exists():
        reference_source = str(args.reference_dir)
        reference_images, missing_reference = load_role_images(args.reference_dir, args.reference_prefix)
    elif args.reference_dir is not None:
        reference_source = str(args.reference_dir)

    role_diffs: list[RoleDiff] = []
    failures: list[str] = []
    if missing_mobile:
        failures.append(f"missing mobile roles: {', '.join(missing_mobile)}")
    if missing_reference:
        message = f"missing reference roles: {', '.join(missing_reference)}"
        if args.require_reference:
            failures.append(message)

    for role in ROLES:
        mobile = mobile_images.get(role)
        reference = reference_images.get(role)
        if mobile is None or reference is None:
            continue
        diff = compare_role(
            mobile,
            reference,
            max_luma_delta=args.max_luma_delta,
            max_alpha_coverage_delta=args.max_alpha_coverage_delta,
            max_bbox_delta_px=args.max_bbox_delta_px,
            min_coverage_ratio=args.min_coverage_ratio,
            max_coverage_ratio=args.max_coverage_ratio,
        )
        role_diffs.append(diff)
        failures.extend(f"{role}: {failure}" for failure in diff.failures)

    status = "pass"
    if failures:
        status = "fail"
    elif missing_reference:
        status = "reference_missing"
    payload: dict[str, object] = {
        "status": status,
        "mobile_source": str(args.mobile_dir),
        "reference_source": reference_source,
        "missing_mobile_roles": missing_mobile,
        "missing_reference_roles": missing_reference,
        "role_diffs": [asdict(diff) for diff in role_diffs],
        "failures": failures,
    }
    output = json.dumps(payload, indent=2, sort_keys=True)
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(output + "\n", encoding="utf-8")
    else:
        print(output)
    if args.output_md:
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        write_markdown(args.output_md, payload)
    return 0 if args.report_only else (1 if failures else 0)


if __name__ == "__main__":
    raise SystemExit(main())
