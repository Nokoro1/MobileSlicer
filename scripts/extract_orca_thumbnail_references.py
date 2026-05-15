#!/usr/bin/env python3
"""Extract desktop Orca package thumbnail roles into a visual reference folder."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

sys.path.insert(0, str(Path(__file__).resolve().parent))

from orca_metadata_audit import PngImageMetrics, png_image_metrics


ROLE_ENTRIES = {
    "plate": "Metadata/plate_1.png",
    "no_light": "Metadata/plate_no_light_1.png",
    "top": "Metadata/top_1.png",
    "pick": "Metadata/pick_1.png",
}


@dataclass(frozen=True)
class ExtractedRole:
    role: str
    source_entry: str
    output_file: str
    metrics: PngImageMetrics


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-3mf", type=Path, required=True, help="Desktop Orca .gcode.3mf package.")
    parser.add_argument("--output-dir", type=Path, required=True, help="Reference directory to populate.")
    parser.add_argument("--orca-build", required=True, help="Desktop Orca build/commit used for the source package.")
    parser.add_argument("--model", required=True, help="Input model identity/path used for the source package.")
    parser.add_argument("--printer-profile", required=True)
    parser.add_argument("--filament-profile", required=True)
    parser.add_argument("--process-profile", required=True)
    parser.add_argument("--notes", default="")
    parser.add_argument(
        "--source-layout",
        default="single",
        help="Renderer source layout this reference proves, such as single or two_filament_objects.",
    )
    parser.add_argument(
        "--source-colors",
        default="",
        help="Comma-separated source colors used by the paired MobileSlicer matrix case.",
    )
    parser.add_argument(
        "--require-active-filaments",
        type=int,
        default=0,
        help=(
            "Require the source package to prove at least this many active filaments "
            "in plate JSON, slice_info.config, and object extruder metadata."
        ),
    )
    parser.add_argument("--allow-overwrite", action="store_true")
    return parser.parse_args()


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


def validate_active_filaments(package_metadata: dict[str, Any], minimum: int) -> list[str]:
    failures: list[str] = []
    required_fields = (
        "plate_json_filament_ids",
        "plate_json_filament_colors",
        "slice_info_filament_ids",
        "slice_info_filament_colors",
        "model_object_extruders",
    )
    for field in required_fields:
        values = package_metadata.get(field, [])
        if not isinstance(values, list) or len(values) < minimum:
            failures.append(f"{field} has {len(values) if isinstance(values, list) else 0}, expected >= {minimum}")
    return failures


def extract_references(args: argparse.Namespace) -> dict[str, object]:
    if not args.source_3mf.is_file():
        raise SystemExit(f"source package does not exist: {args.source_3mf}")
    if args.output_dir.exists() and any(args.output_dir.iterdir()) and not args.allow_overwrite:
        raise SystemExit(f"output directory is not empty: {args.output_dir}; pass --allow-overwrite")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    extracted: list[ExtractedRole] = []
    missing: list[str] = []
    with zipfile.ZipFile(args.source_3mf) as archive:
        names = set(archive.namelist())
        for role, entry in ROLE_ENTRIES.items():
            if entry not in names:
                missing.append(entry)
                continue
            payload = archive.read(entry)
            try:
                metrics = png_image_metrics(payload)
            except ValueError as exc:
                raise SystemExit(f"{args.source_3mf}:{entry}: {exc}") from exc
            if metrics.nontransparent_pixels <= 0:
                raise SystemExit(f"{args.source_3mf}:{entry}: blank thumbnail")
            output_file = args.output_dir / f"{role}.png"
            output_file.write_bytes(payload)
            extracted.append(
                ExtractedRole(
                    role=role,
                    source_entry=entry,
                    output_file=output_file.name,
                    metrics=metrics,
                )
            )
    if missing:
        raise SystemExit(f"source package is missing required Orca thumbnail entries: {', '.join(missing)}")

    package_metadata = inspect_active_filament_metadata(args.source_3mf)
    if args.require_active_filaments > 0:
        active_failures = validate_active_filaments(package_metadata, args.require_active_filaments)
        if active_failures:
            raise SystemExit(
                "source package does not prove active multi-filament metadata: "
                + "; ".join(active_failures)
            )

    source_copy = args.output_dir / args.source_3mf.name
    if args.source_3mf.resolve() != source_copy.resolve():
        shutil.copy2(args.source_3mf, source_copy)
    manifest = {
        "schema_version": 1,
        "source_3mf": source_copy.name,
        "orca_build": args.orca_build,
        "model": args.model,
        "printer_profile": args.printer_profile,
        "filament_profile": args.filament_profile,
        "process_profile": args.process_profile,
        "notes": args.notes,
        "source_layout": args.source_layout,
        "source_colors": [color.strip() for color in args.source_colors.split(",") if color.strip()],
        "requirements": {
            "active_filaments": args.require_active_filaments,
        },
        "package_metadata": package_metadata,
        "roles": [asdict(role) for role in extracted],
    }
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (args.output_dir / "README.md").write_text(
        "\n".join(
            [
                "# Orca Thumbnail Visual Reference",
                "",
                f"- Orca build: `{args.orca_build}`",
                f"- Source package: `{source_copy.name}`",
                f"- Model: `{args.model}`",
                f"- Printer profile: `{args.printer_profile}`",
                f"- Filament profile: `{args.filament_profile}`",
                f"- Process profile: `{args.process_profile}`",
                "",
                "Required role PNGs:",
                "",
                "- `plate.png` from `Metadata/plate_1.png`",
                "- `no_light.png` from `Metadata/plate_no_light_1.png`",
                "- `top.png` from `Metadata/top_1.png`",
                "- `pick.png` from `Metadata/pick_1.png`",
                "",
                "Use this directory with:",
                "",
                "```sh",
                "MOBILE_SLICER_ORCA_THUMBNAIL_REFERENCE_DIR=this/directory \\",
                "MOBILE_SLICER_REQUIRE_ORCA_THUMBNAIL_REFERENCE=1 \\",
                "scripts/verify_android.sh egl-thumbnail-compare <serial>",
                "```",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


def main() -> int:
    manifest = extract_references(parse_args())
    print(json.dumps(manifest, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
