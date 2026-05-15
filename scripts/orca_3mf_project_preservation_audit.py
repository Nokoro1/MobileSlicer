#!/usr/bin/env python3
"""Audit Orca/Bambu 3MF project-package preservation evidence.

This intentionally inspects the 3MF as a ZIP/XML package. A successful slice is
not enough for Orca project parity: the package must still expose the objects,
plates, filament assignments, config files, and thumbnails that Orca users
expect to survive project import/export.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from xml.etree import ElementTree as ET


@dataclass(frozen=True)
class ObjectAssignment:
    object_name: str
    filament_index: int


@dataclass(frozen=True)
class ObjectSettingEvidence:
    object_name: str
    key: str
    value: str


@dataclass(frozen=True)
class ModifierEvidence:
    object_name: str
    modifier_name: str
    subtype: str
    settings: list[ObjectSettingEvidence]


@dataclass(frozen=True)
class LayerRangeEvidence:
    object_name: str
    object_index: int
    min_z: str
    max_z: str
    settings: list[ObjectSettingEvidence]


@dataclass(frozen=True)
class AuditFailure:
    check: str
    message: str


def read_zip_text(zip_file: zipfile.ZipFile, name: str) -> str | None:
    try:
        return zip_file.read(name).decode("utf-8", errors="replace")
    except KeyError:
        return None


def parse_xml(text: str | None) -> ET.Element | None:
    if not text:
        return None
    try:
        return ET.fromstring(text)
    except ET.ParseError:
        return None


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_metadata(element: ET.Element) -> dict[str, str]:
    result: dict[str, str] = {}
    for child in list(element):
        if local_name(child.tag) != "metadata":
            continue
        key = child.attrib.get("key") or child.attrib.get("name")
        value = child.attrib.get("value")
        if value is None:
            value = "".join(child.itertext())
        if key:
            result[key] = value
    return result


def append_source_file_evidence(target: list[str], metadata: dict[str, str]) -> None:
    for key in ("input_file", "source_file"):
        value = metadata.get(key, "").strip()
        if value and value not in target:
            target.append(value)


def iter_elements(root: ET.Element | None, name: str) -> list[ET.Element]:
    if root is None:
        return []
    return [element for element in root.iter() if local_name(element.tag) == name]


STRUCTURAL_OBJECT_METADATA_KEYS = {
    "assemble_item_index",
    "extruder",
    "identify_id",
    "input_file",
    "instance_id",
    "matrix",
    "name",
    "object_id",
    "source_file",
    "source_object_id",
    "source_offset_x",
    "source_offset_y",
    "source_offset_z",
    "source_volume_id",
}

STRUCTURAL_PART_METADATA_KEYS = STRUCTURAL_OBJECT_METADATA_KEYS | {
    "is_text",
    "matrix",
    "source_object_id",
    "source_volume_id",
}


STEP_SOURCE_EXTENSIONS = (".step", ".stp")


def is_thumbnail_entry(name: str) -> bool:
    lower = name.lower()
    if not lower.endswith(".png"):
        return False
    if "thumbnail" in lower or "/.thumbnails/" in lower:
        return True
    filename = lower.rsplit("/", 1)[-1]
    return (
        filename.startswith("plate_")
        or filename.startswith("plate_no_light_")
        or filename.startswith("top_")
        or filename.startswith("pick_")
    )


def numbered_entry_indices(entries: list[str], pattern: str) -> list[int]:
    regex = re.compile(pattern)
    indices: set[int] = set()
    for entry in entries:
        match = regex.fullmatch(entry)
        if match:
            indices.add(int(match.group(1)))
    return sorted(indices)


def thumbnail_plate_indices(thumbnail_entries: list[str]) -> list[int]:
    return numbered_entry_indices(
        thumbnail_entries,
        r"Metadata/(?:plate|plate_no_light|top|pick)_(\d+)(?:_small)?\.png",
    )


def inspect_3mf(path: Path) -> dict[str, object]:
    with zipfile.ZipFile(path) as zip_file:
        entries = zip_file.namelist()
        model_settings = parse_xml(read_zip_text(zip_file, "Metadata/model_settings.config"))
        slice_info = parse_xml(read_zip_text(zip_file, "Metadata/slice_info.config"))
        root_model = parse_xml(read_zip_text(zip_file, "3D/3dmodel.model"))
        layer_config_ranges = parse_xml(read_zip_text(zip_file, "Metadata/layer_config_ranges.xml"))

    config_entries = sorted(
        entry for entry in entries if entry.startswith("Metadata/") and entry.lower().endswith(".config")
    )
    thumbnail_entries = sorted(entry for entry in entries if is_thumbnail_entry(entry))
    plate_json_entries = sorted(
        entry for entry in entries if re.fullmatch(r"Metadata/plate_\d+\.json", entry)
    )
    sliced_plate_gcode_entries = sorted(
        entry for entry in entries if re.fullmatch(r"Metadata/plate_\d+\.gcode", entry)
    )
    object_names: list[str] = []
    source_file_evidence: list[str] = []
    object_name_by_model_settings_index: dict[int, str] = {}
    object_name_by_model_object_id: dict[int, str] = {}
    assignments: list[ObjectAssignment] = []
    object_settings: list[ObjectSettingEvidence] = []
    modifiers: list[ModifierEvidence] = []
    for object_index, object_element in enumerate(iter_elements(model_settings, "object"), start=1):
        metadata = child_metadata(object_element)
        name = metadata.get("name", "").strip()
        if name:
            object_names.append(name)
            object_name_by_model_settings_index[object_index] = name
            try:
                object_id = int(object_element.attrib.get("id", "0"))
            except ValueError:
                object_id = 0
            if object_id > 0:
                object_name_by_model_object_id[object_id] = name
        extruder = metadata.get("extruder", "").strip()
        if name and extruder.isdigit() and int(extruder) > 0:
            assignments.append(ObjectAssignment(object_name=name, filament_index=int(extruder)))
        append_source_file_evidence(source_file_evidence, metadata)
        for key, value in sorted(metadata.items()):
            if key not in STRUCTURAL_OBJECT_METADATA_KEYS:
                object_settings.append(ObjectSettingEvidence(object_name=name or "<unnamed object>", key=key, value=value))
        for part in [child for child in list(object_element) if local_name(child.tag) == "part"]:
            part_metadata = child_metadata(part)
            append_source_file_evidence(source_file_evidence, part_metadata)
            part_name = part_metadata.get("name", "").strip()
            subtype = part.attrib.get("subtype", "").strip()
            part_settings = [
                ObjectSettingEvidence(
                    object_name=name or "<unnamed object>",
                    key=key,
                    value=value,
                )
                for key, value in sorted(part_metadata.items())
                if key not in STRUCTURAL_PART_METADATA_KEYS
            ]
            is_modifier = subtype not in {"", "normal_part"} or "modifier" in subtype.lower()
            if is_modifier:
                modifiers.append(
                    ModifierEvidence(
                        object_name=name or "<unnamed object>",
                        modifier_name=part_name or f"part_{part.attrib.get('id', '')}".rstrip("_"),
                        subtype=subtype or "unknown",
                        settings=part_settings,
                    )
                )
            else:
                object_settings.extend(part_settings)

    model_object_ids: list[int] = []
    if root_model is not None:
        for child in list(root_model):
            if local_name(child.tag) != "resources":
                continue
            for resource in list(child):
                if local_name(resource.tag) != "object":
                    continue
                try:
                    object_id = int(resource.attrib.get("id", "0"))
                except ValueError:
                    object_id = 0
                if object_id > 0:
                    model_object_ids.append(object_id)
    object_name_by_one_based_index = {
        index: object_name_by_model_object_id.get(object_id, f"object_{object_id}")
        for index, object_id in enumerate(model_object_ids, start=1)
    }
    if not object_name_by_one_based_index:
        object_name_by_one_based_index = object_name_by_model_settings_index

    layer_ranges: list[LayerRangeEvidence] = []
    for object_element in iter_elements(layer_config_ranges, "object"):
        try:
            object_index = int(object_element.attrib.get("id", "0"))
        except ValueError:
            object_index = 0
        object_name = object_name_by_one_based_index.get(object_index, f"object_{object_index}")
        for range_element in [child for child in list(object_element) if local_name(child.tag) == "range"]:
            settings: list[ObjectSettingEvidence] = []
            for option in [child for child in list(range_element) if local_name(child.tag) == "option"]:
                key = option.attrib.get("opt_key", "").strip()
                value = "".join(option.itertext()).strip()
                if key:
                    settings.append(ObjectSettingEvidence(object_name=object_name, key=key, value=value))
            layer_ranges.append(
                LayerRangeEvidence(
                    object_name=object_name,
                    object_index=object_index,
                    min_z=range_element.attrib.get("min_z", "").strip(),
                    max_z=range_element.attrib.get("max_z", "").strip(),
                    settings=settings,
                )
            )

    plate_names: list[str] = []
    for index, plate in enumerate(iter_elements(model_settings, "plate"), start=1):
        metadata = child_metadata(plate)
        plate_names.append(metadata.get("plater_name", "").strip() or f"Plate {index}")
    if not plate_names:
        for plate in iter_elements(slice_info, "plate"):
            metadata = child_metadata(plate)
            index = metadata.get("index", "").strip()
            if index:
                plate_names.append(f"Plate {index}")

    filament_ids = {
        filament.attrib.get("id")
        for filament in iter_elements(slice_info, "filament")
        if filament.attrib.get("id")
    }
    filament_indices = {
        assignment.filament_index
        for assignment in assignments
        if assignment.filament_index > 0
    }
    for plate in iter_elements(model_settings, "plate"):
        metadata = child_metadata(plate)
        for token in metadata.get("filament_maps", "").replace(",", " ").split():
            if token.isdigit() and int(token) > 0:
                filament_indices.add(int(token))
    build_items = iter_elements(root_model, "item")

    preserved_features = []
    if "3D/3dmodel.model" in entries:
        preserved_features.append("mesh_geometry")
    if source_file_evidence:
        preserved_features.append("source_file_evidence")
    if any(value.lower().endswith(STEP_SOURCE_EXTENSIONS) for value in source_file_evidence):
        preserved_features.append("step_source_file_evidence")
    if object_names:
        preserved_features.append("object_names")
    if assignments:
        preserved_features.append("object_filament_assignments")
    if object_settings:
        preserved_features.append("object_settings")
    if modifiers:
        preserved_features.append("modifier_volumes")
    if any(modifier.settings for modifier in modifiers):
        preserved_features.append("modifier_settings")
    if layer_ranges:
        preserved_features.append("layer_config_ranges")
    if any(layer_range.settings for layer_range in layer_ranges):
        preserved_features.append("layer_range_settings")
    if plate_names:
        preserved_features.append("plate_metadata")
    filament_count = max(len(filament_ids), len(filament_indices))
    if filament_count > 0:
        preserved_features.append("filament_metadata")
    if thumbnail_entries:
        preserved_features.append("project_thumbnails")
    if plate_json_entries:
        preserved_features.append("plate_json_metadata")
    if sliced_plate_gcode_entries:
        preserved_features.append("sliced_plate_gcode_entries")
    if "Metadata/project_settings.config" in config_entries:
        preserved_features.append("project_settings")
    if "Metadata/model_settings.config" in config_entries:
        preserved_features.append("model_settings")
    if "Metadata/slice_info.config" in config_entries:
        preserved_features.append("slice_info")
    return {
        "path": str(path),
        "entry_count": len(entries),
        "entries": entries,
        "config_entries": config_entries,
        "thumbnail_entries": thumbnail_entries,
        "thumbnail_plate_indices": thumbnail_plate_indices(thumbnail_entries),
        "plate_json_entries": plate_json_entries,
        "plate_json_indices": numbered_entry_indices(plate_json_entries, r"Metadata/plate_(\d+)\.json"),
        "sliced_plate_gcode_entries": sliced_plate_gcode_entries,
        "sliced_plate_gcode_indices": numbered_entry_indices(
            sliced_plate_gcode_entries,
            r"Metadata/plate_(\d+)\.gcode",
        ),
        "plate_count": len(plate_names) or sum(
            1 for entry in entries if entry.startswith("Metadata/plate_") and entry.endswith(".json")
        ),
        "plate_names": plate_names,
        "object_count": len(object_names) or len(build_items),
        "object_names": object_names,
        "source_file_evidence": sorted(set(source_file_evidence)),
        "object_filament_assignments": [asdict(assignment) for assignment in assignments],
        "object_setting_evidence": [asdict(setting) for setting in object_settings],
        "modifier_evidence": [asdict(modifier) for modifier in modifiers],
        "layer_range_evidence": [asdict(layer_range) for layer_range in layer_ranges],
        "filament_count": filament_count,
        "preserved_features": preserved_features,
    }


def validate(
    metadata: dict[str, object],
    *,
    min_plate_count: int,
    min_object_count: int,
    require_object_names: bool = False,
    require_filament_assignments: bool = False,
    require_object_settings: bool = False,
    require_modifier_volumes: bool = False,
    require_modifier_settings: bool = False,
    require_layer_ranges: bool = False,
    require_layer_range_settings: bool = False,
    require_project_thumbnails: bool = False,
    require_plate_json_metadata: bool = False,
    require_sliced_plate_gcode: bool = False,
    require_project_settings: bool = False,
    require_step_source: bool = False,
) -> list[AuditFailure]:
    failures: list[AuditFailure] = []
    if int(metadata["plate_count"]) < min_plate_count:
        failures.append(AuditFailure("plate-count", f"expected at least {min_plate_count} plate(s)"))
    if int(metadata["object_count"]) < min_object_count:
        failures.append(AuditFailure("object-count", f"expected at least {min_object_count} object(s)"))
    if require_object_names and not metadata["object_names"]:
        failures.append(AuditFailure("object-names", "missing preserved object names"))
    if require_filament_assignments and not metadata["object_filament_assignments"]:
        failures.append(AuditFailure("filament-assignments", "missing object-to-filament assignments"))
    if require_object_settings and not metadata["object_setting_evidence"]:
        failures.append(AuditFailure("object-settings", "missing object-scoped setting evidence"))
    if require_modifier_volumes and not metadata["modifier_evidence"]:
        failures.append(AuditFailure("modifier-volumes", "missing modifier volume evidence"))
    if require_modifier_settings:
        modifier_settings = [
            setting
            for modifier in metadata["modifier_evidence"]
            for setting in modifier.get("settings", [])
        ]
        if not modifier_settings:
            failures.append(AuditFailure("modifier-settings", "missing modifier setting evidence"))
    if require_layer_ranges and not metadata["layer_range_evidence"]:
        failures.append(AuditFailure("layer-ranges", "missing layer/height range evidence"))
    if require_layer_range_settings:
        layer_range_settings = [
            setting
            for layer_range in metadata["layer_range_evidence"]
            for setting in layer_range.get("settings", [])
        ]
        if not layer_range_settings:
            failures.append(AuditFailure("layer-range-settings", "missing layer/height range setting evidence"))
    if require_project_thumbnails:
        if not metadata["thumbnail_entries"]:
            failures.append(AuditFailure("project-thumbnails", "missing project/plate thumbnail entries"))
        elif int(metadata["plate_count"]) > 0 and len(metadata["thumbnail_plate_indices"]) < int(metadata["plate_count"]):
            failures.append(
                AuditFailure(
                    "project-thumbnails",
                    f"thumbnail entries cover plate indices {metadata['thumbnail_plate_indices']}, expected at least {metadata['plate_count']} plate(s)",
                )
            )
    if require_plate_json_metadata:
        if not metadata["plate_json_entries"]:
            failures.append(AuditFailure("plate-json-metadata", "missing Metadata/plate_N.json entries"))
        elif int(metadata["plate_count"]) > 0 and len(metadata["plate_json_indices"]) < int(metadata["plate_count"]):
            failures.append(
                AuditFailure(
                    "plate-json-metadata",
                    f"plate JSON entries cover plate indices {metadata['plate_json_indices']}, expected at least {metadata['plate_count']} plate(s)",
                )
            )
    if require_sliced_plate_gcode:
        if not metadata["sliced_plate_gcode_entries"]:
            failures.append(AuditFailure("sliced-plate-gcode", "missing Metadata/plate_N.gcode entries"))
        elif int(metadata["plate_count"]) > 0 and len(metadata["sliced_plate_gcode_indices"]) < int(metadata["plate_count"]):
            failures.append(
                AuditFailure(
                    "sliced-plate-gcode",
                    f"sliced G-code entries cover plate indices {metadata['sliced_plate_gcode_indices']}, expected at least {metadata['plate_count']} plate(s)",
                )
            )
    if require_project_settings and "Metadata/project_settings.config" not in metadata["config_entries"]:
        failures.append(AuditFailure("project-settings", "missing Metadata/project_settings.config"))
    if require_step_source:
        step_sources = [
            value
            for value in metadata.get("source_file_evidence", [])
            if str(value).lower().endswith(STEP_SOURCE_EXTENSIONS)
        ]
        if not step_sources:
            failures.append(AuditFailure("step-source", "missing STEP/STP source file evidence"))
    return failures


def normalize_assignment(raw: dict[str, object]) -> tuple[str, int] | None:
    name = str(raw.get("object_name", "")).strip()
    try:
        filament_index = int(raw.get("filament_index", 0))
    except (TypeError, ValueError):
        return None
    if not name or filament_index <= 0:
        return None
    return (name, filament_index)


def normalize_setting(raw: dict[str, object]) -> tuple[str, str, str] | None:
    object_name = str(raw.get("object_name", "")).strip()
    key = str(raw.get("key", "")).strip()
    value = str(raw.get("value", "")).strip()
    if not object_name or not key:
        return None
    return (object_name, key, value)


def normalize_modifier(raw: dict[str, object]) -> tuple[str, str, str] | None:
    object_name = str(raw.get("object_name", "")).strip()
    modifier_name = str(raw.get("modifier_name", "")).strip()
    subtype = str(raw.get("subtype", "")).strip()
    if not object_name or not modifier_name:
        return None
    return (object_name, modifier_name, subtype)


def normalize_layer_range(raw: dict[str, object]) -> tuple[str, int, str, str] | None:
    object_name = str(raw.get("object_name", "")).strip()
    try:
        object_index = int(raw.get("object_index", 0))
    except (TypeError, ValueError):
        return None
    min_z = str(raw.get("min_z", "")).strip()
    max_z = str(raw.get("max_z", "")).strip()
    if not object_name or object_index <= 0 or not min_z or not max_z:
        return None
    return (object_name, object_index, min_z, max_z)


def compare_roundtrip(
    source: dict[str, object],
    roundtrip: dict[str, object],
    *,
    require_object_names: bool = False,
    require_filament_assignments: bool = False,
    require_object_settings: bool = False,
    require_modifier_volumes: bool = False,
    require_modifier_settings: bool = False,
    require_layer_ranges: bool = False,
    require_layer_range_settings: bool = False,
    require_project_thumbnails: bool = False,
    require_plate_json_metadata: bool = False,
    require_sliced_plate_gcode: bool = False,
    require_project_settings: bool = False,
    require_plate_names: bool = False,
    require_step_source: bool = False,
) -> list[AuditFailure]:
    failures: list[AuditFailure] = []
    source_plate_count = int(source["plate_count"])
    roundtrip_plate_count = int(roundtrip["plate_count"])
    if source_plate_count > 0 and roundtrip_plate_count < source_plate_count:
        failures.append(
            AuditFailure(
                "roundtrip-plate-count",
                f"round-trip has {roundtrip_plate_count} plate(s), source has {source_plate_count}",
            )
        )

    source_object_count = int(source["object_count"])
    roundtrip_object_count = int(roundtrip["object_count"])
    if source_object_count > 0 and roundtrip_object_count < source_object_count:
        failures.append(
            AuditFailure(
                "roundtrip-object-count",
                f"round-trip has {roundtrip_object_count} object(s), source has {source_object_count}",
            )
        )

    source_filament_count = int(source["filament_count"])
    roundtrip_filament_count = int(roundtrip["filament_count"])
    if source_filament_count > 0 and roundtrip_filament_count < source_filament_count:
        failures.append(
            AuditFailure(
                "roundtrip-filament-count",
                f"round-trip has {roundtrip_filament_count} filament(s), source has {source_filament_count}",
            )
        )

    if require_plate_names:
        source_names = set(str(value) for value in source["plate_names"])
        roundtrip_names = set(str(value) for value in roundtrip["plate_names"])
        missing = sorted(source_names - roundtrip_names)
        if missing:
            failures.append(AuditFailure("roundtrip-plate-names", f"missing plate names: {missing}"))

    if require_object_names:
        source_names = set(str(value) for value in source["object_names"])
        roundtrip_names = set(str(value) for value in roundtrip["object_names"])
        missing = sorted(source_names - roundtrip_names)
        if missing:
            failures.append(AuditFailure("roundtrip-object-names", f"missing object names: {missing}"))

    if require_filament_assignments:
        source_assignments = {
            assignment
            for assignment in (
                normalize_assignment(raw)
                for raw in source["object_filament_assignments"]
            )
            if assignment is not None
        }
        roundtrip_assignments = {
            assignment
            for assignment in (
                normalize_assignment(raw)
                for raw in roundtrip["object_filament_assignments"]
            )
            if assignment is not None
        }
        missing = sorted(source_assignments - roundtrip_assignments)
        if missing:
            failures.append(AuditFailure("roundtrip-filament-assignments", f"missing assignments: {missing}"))

    if require_object_settings:
        source_settings = {
            setting
            for setting in (
                normalize_setting(raw)
                for raw in source["object_setting_evidence"]
            )
            if setting is not None
        }
        roundtrip_settings = {
            setting
            for setting in (
                normalize_setting(raw)
                for raw in roundtrip["object_setting_evidence"]
            )
            if setting is not None
        }
        missing = sorted(source_settings - roundtrip_settings)
        if missing:
            failures.append(AuditFailure("roundtrip-object-settings", f"missing object settings: {missing}"))

    if require_modifier_volumes:
        source_modifiers = {
            modifier
            for modifier in (
                normalize_modifier(raw)
                for raw in source["modifier_evidence"]
            )
            if modifier is not None
        }
        roundtrip_modifiers = {
            modifier
            for modifier in (
                normalize_modifier(raw)
                for raw in roundtrip["modifier_evidence"]
            )
            if modifier is not None
        }
        missing = sorted(source_modifiers - roundtrip_modifiers)
        if missing:
            failures.append(AuditFailure("roundtrip-modifier-volumes", f"missing modifiers: {missing}"))

    if require_modifier_settings:
        def modifier_settings(metadata: dict[str, object]) -> set[tuple[str, str, str, str, str]]:
            result: set[tuple[str, str, str, str, str]] = set()
            for raw_modifier in metadata["modifier_evidence"]:
                modifier = normalize_modifier(raw_modifier)
                if modifier is None:
                    continue
                for raw_setting in raw_modifier.get("settings", []):
                    setting = normalize_setting(raw_setting)
                    if setting is None:
                        continue
                    _, key, value = setting
                    result.add((modifier[0], modifier[1], modifier[2], key, value))
            return result

        missing = sorted(modifier_settings(source) - modifier_settings(roundtrip))
        if missing:
            failures.append(AuditFailure("roundtrip-modifier-settings", f"missing modifier settings: {missing}"))

    if require_layer_ranges:
        source_ranges = {
            layer_range
            for layer_range in (
                normalize_layer_range(raw)
                for raw in source["layer_range_evidence"]
            )
            if layer_range is not None
        }
        roundtrip_ranges = {
            layer_range
            for layer_range in (
                normalize_layer_range(raw)
                for raw in roundtrip["layer_range_evidence"]
            )
            if layer_range is not None
        }
        missing = sorted(source_ranges - roundtrip_ranges)
        if missing:
            failures.append(AuditFailure("roundtrip-layer-ranges", f"missing layer ranges: {missing}"))

    if require_layer_range_settings:
        def layer_range_settings(metadata: dict[str, object]) -> set[tuple[str, int, str, str, str, str]]:
            result: set[tuple[str, int, str, str, str, str]] = set()
            for raw_range in metadata["layer_range_evidence"]:
                layer_range = normalize_layer_range(raw_range)
                if layer_range is None:
                    continue
                for raw_setting in raw_range.get("settings", []):
                    setting = normalize_setting(raw_setting)
                    if setting is None:
                        continue
                    _, key, value = setting
                    result.add((layer_range[0], layer_range[1], layer_range[2], layer_range[3], key, value))
            return result

        missing = sorted(layer_range_settings(source) - layer_range_settings(roundtrip))
        if missing:
            failures.append(AuditFailure("roundtrip-layer-range-settings", f"missing layer range settings: {missing}"))

    if require_project_thumbnails and source["thumbnail_entries"]:
        source_indices = set(int(value) for value in source["thumbnail_plate_indices"])
        roundtrip_indices = set(int(value) for value in roundtrip["thumbnail_plate_indices"])
        if not roundtrip["thumbnail_entries"]:
            failures.append(AuditFailure("roundtrip-project-thumbnails", "round-trip lost project thumbnail entries"))
        elif source_indices and not source_indices.issubset(roundtrip_indices):
            failures.append(
                AuditFailure(
                    "roundtrip-project-thumbnails",
                    f"round-trip thumbnail entries cover plate indices {sorted(roundtrip_indices)}, source has {sorted(source_indices)}",
                )
            )

    if require_plate_json_metadata and source["plate_json_entries"]:
        source_indices = set(int(value) for value in source["plate_json_indices"])
        roundtrip_indices = set(int(value) for value in roundtrip["plate_json_indices"])
        if not source_indices.issubset(roundtrip_indices):
            failures.append(
                AuditFailure(
                    "roundtrip-plate-json-metadata",
                    f"round-trip plate JSON entries cover plate indices {sorted(roundtrip_indices)}, source has {sorted(source_indices)}",
                )
            )

    if require_sliced_plate_gcode and source["sliced_plate_gcode_entries"]:
        source_indices = set(int(value) for value in source["sliced_plate_gcode_indices"])
        roundtrip_indices = set(int(value) for value in roundtrip["sliced_plate_gcode_indices"])
        if not source_indices.issubset(roundtrip_indices):
            failures.append(
                AuditFailure(
                    "roundtrip-sliced-plate-gcode",
                    f"round-trip sliced G-code entries cover plate indices {sorted(roundtrip_indices)}, source has {sorted(source_indices)}",
                )
            )

    if require_project_settings:
        required_configs = {
            "Metadata/model_settings.config",
            "Metadata/project_settings.config",
        }
        source_configs = set(str(value) for value in source["config_entries"])
        roundtrip_configs = set(str(value) for value in roundtrip["config_entries"])
        missing = sorted((required_configs & source_configs) - roundtrip_configs)
        if missing:
            failures.append(AuditFailure("roundtrip-config-entries", f"missing config entries: {missing}"))

    if require_step_source:
        source_step_sources = {
            str(value)
            for value in source.get("source_file_evidence", [])
            if str(value).lower().endswith(STEP_SOURCE_EXTENSIONS)
        }
        roundtrip_step_sources = {
            str(value)
            for value in roundtrip.get("source_file_evidence", [])
            if str(value).lower().endswith(STEP_SOURCE_EXTENSIONS)
        }
        missing = sorted(source_step_sources - roundtrip_step_sources)
        if missing:
            failures.append(AuditFailure("roundtrip-step-source", f"missing STEP/STP source evidence: {missing}"))

    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--three-mf", type=Path)
    parser.add_argument("--source-3mf", type=Path)
    parser.add_argument("--roundtrip-3mf", type=Path)
    parser.add_argument("--min-plate-count", type=int, default=1)
    parser.add_argument("--min-object-count", type=int, default=1)
    parser.add_argument("--require-object-names", action="store_true")
    parser.add_argument("--require-filament-assignments", action="store_true")
    parser.add_argument("--require-object-settings", action="store_true", help="Require object-scoped non-structural setting metadata.")
    parser.add_argument("--require-modifier-volumes", action="store_true", help="Require at least one non-normal part/modifier volume.")
    parser.add_argument("--require-modifier-settings", action="store_true", help="Require non-structural setting metadata on a modifier volume.")
    parser.add_argument("--require-layer-ranges", action="store_true", help="Require Metadata/layer_config_ranges.xml range evidence.")
    parser.add_argument("--require-layer-range-settings", action="store_true", help="Require settings inside Metadata/layer_config_ranges.xml ranges.")
    parser.add_argument("--require-project-thumbnails", action="store_true")
    parser.add_argument("--require-plate-json-metadata", action="store_true")
    parser.add_argument("--require-sliced-plate-gcode", action="store_true")
    parser.add_argument("--require-project-settings", action="store_true")
    parser.add_argument("--require-plate-names", action="store_true")
    parser.add_argument("--require-step-source", action="store_true", help="Require object source metadata ending in .step or .stp.")
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    if args.source_3mf or args.roundtrip_3mf:
        if not args.source_3mf or not args.roundtrip_3mf:
            parser.error("--source-3mf and --roundtrip-3mf must be provided together")
        source = inspect_3mf(args.source_3mf)
        roundtrip = inspect_3mf(args.roundtrip_3mf)
        failures = validate(
            roundtrip,
            min_plate_count=args.min_plate_count,
            min_object_count=args.min_object_count,
            require_object_names=args.require_object_names,
            require_filament_assignments=args.require_filament_assignments,
            require_object_settings=args.require_object_settings,
            require_modifier_volumes=args.require_modifier_volumes,
            require_modifier_settings=args.require_modifier_settings,
            require_layer_ranges=args.require_layer_ranges,
            require_layer_range_settings=args.require_layer_range_settings,
            require_project_thumbnails=args.require_project_thumbnails,
            require_plate_json_metadata=args.require_plate_json_metadata,
            require_sliced_plate_gcode=args.require_sliced_plate_gcode,
            require_project_settings=args.require_project_settings,
            require_step_source=args.require_step_source,
        )
        failures.extend(
            compare_roundtrip(
                source,
                roundtrip,
                require_object_names=args.require_object_names,
                require_filament_assignments=args.require_filament_assignments,
                require_object_settings=args.require_object_settings,
                require_modifier_volumes=args.require_modifier_volumes,
                require_modifier_settings=args.require_modifier_settings,
                require_layer_ranges=args.require_layer_ranges,
                require_layer_range_settings=args.require_layer_range_settings,
                require_project_thumbnails=args.require_project_thumbnails,
                require_plate_json_metadata=args.require_plate_json_metadata,
                require_sliced_plate_gcode=args.require_sliced_plate_gcode,
                require_project_settings=args.require_project_settings,
                require_plate_names=args.require_plate_names,
                require_step_source=args.require_step_source,
            )
        )
        output = {
            "source": source,
            "roundtrip": roundtrip,
            "failures": [asdict(failure) for failure in failures],
            "ok": not failures,
        }
    else:
        if not args.three_mf:
            parser.error("--three-mf is required unless --source-3mf/--roundtrip-3mf are provided")
        metadata = inspect_3mf(args.three_mf)
        failures = validate(
            metadata,
            min_plate_count=args.min_plate_count,
            min_object_count=args.min_object_count,
            require_object_names=args.require_object_names,
            require_filament_assignments=args.require_filament_assignments,
            require_object_settings=args.require_object_settings,
            require_modifier_volumes=args.require_modifier_volumes,
            require_modifier_settings=args.require_modifier_settings,
            require_layer_ranges=args.require_layer_ranges,
            require_layer_range_settings=args.require_layer_range_settings,
            require_project_thumbnails=args.require_project_thumbnails,
            require_plate_json_metadata=args.require_plate_json_metadata,
            require_sliced_plate_gcode=args.require_sliced_plate_gcode,
            require_project_settings=args.require_project_settings,
            require_step_source=args.require_step_source,
        )
        output = {
            "metadata": metadata,
            "failures": [asdict(failure) for failure in failures],
            "ok": not failures,
        }
    print(json.dumps(output, indent=2 if args.pretty else None, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
