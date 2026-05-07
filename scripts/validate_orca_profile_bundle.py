#!/usr/bin/env python3
"""Validate Orca-style profile bundle structure used by MobileSlicer exports."""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from functools import lru_cache
from pathlib import Path
from typing import Any

from generate_orca_setting_metadata import parse_config_defs


PRINTER_BUNDLE = "printer config bundle"
FILAMENT_BUNDLE = "filament config bundle"
EMPTY_SCALAR_ALLOWLIST = {
    "inherits",
    "compatible_printers_condition",
    "compatible_prints_condition",
}
ORCA_MIN_IRONING_SPACING = 0.05


class BundleValidationError(Exception):
    pass


def _read_json(zip_file: zipfile.ZipFile, name: str) -> dict[str, Any]:
    try:
        with zip_file.open(name) as handle:
            loaded = json.loads(handle.read().decode("utf-8"))
    except KeyError as exc:
        raise BundleValidationError(f"Missing bundle entry: {name}") from exc
    except json.JSONDecodeError as exc:
        raise BundleValidationError(f"Invalid JSON in {name}: {exc}") from exc
    if not isinstance(loaded, dict):
        raise BundleValidationError(f"{name} must contain a JSON object")
    return loaded


def _string_list(value: Any, field: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(item, str) and item for item in value):
        raise BundleValidationError(f"{field} must be a list of non-empty strings")
    return value


def _scalar_text(value: Any) -> str:
    if isinstance(value, list):
        if not value:
            return ""
        return _scalar_text(value[0])
    if value is None:
        return ""
    return str(value).strip()


def _require_nonblank(config: dict[str, Any], key: str, path: str) -> None:
    if not _scalar_text(config.get(key)):
        raise BundleValidationError(f"{path} is missing non-empty {key}")


def _require_empty_secret(config: dict[str, Any], key: str, path: str) -> None:
    if _scalar_text(config.get(key)):
        raise BundleValidationError(f"{path} must not export {key}")


@lru_cache(maxsize=1)
def _orca_config_types() -> dict[str, str]:
    repo_root = Path(__file__).resolve().parents[1]
    defs = parse_config_defs(repo_root / "vendor" / "orcaslicer" / "src" / "libslic3r" / "PrintConfig.cpp")
    return {key: str(value.get("configType", "")) for key, value in defs.items()}


def _is_vector_config_type(config_type: str) -> bool:
    return config_type == "coPointsGroups" or (config_type.startswith("co") and config_type.endswith("s"))


def _validate_no_empty_vector_values(config: dict[str, Any], path: str) -> None:
    config_types = _orca_config_types()
    for key, value in config.items():
        if key in EMPTY_SCALAR_ALLOWLIST:
            continue
        if _is_vector_config_type(config_types.get(key, "")) and (value == [] or (isinstance(value, str) and not value.strip())):
            raise BundleValidationError(
                f"{path}.{key} must be omitted instead of exported empty; "
                "desktop Orca may deserialize it as an empty vector and crash during import"
            )


def _validate_orca_stable_process_values(config: dict[str, Any], path: str) -> None:
    for key in ("ironing_spacing", "support_ironing_spacing"):
        value = _scalar_text(config.get(key))
        try:
            numeric_value = float(value) if value else None
        except ValueError as exc:
            raise BundleValidationError(f"{path}.{key} must be numeric when exported") from exc
        if numeric_value is not None and numeric_value < ORCA_MIN_IRONING_SPACING:
            raise BundleValidationError(
                f"{path}.{key}={value!r} will be auto-corrected by desktop Orca; export 0.1 or omit it"
            )


def _validate_mobileslicer_user_identity(config: dict[str, Any], path: str) -> None:
    if _scalar_text(config.get("from")) != "User":
        raise BundleValidationError(f"{path} must export from=User so Orca imports it as a user preset")
    if _scalar_text(config.get("inherits")):
        raise BundleValidationError(f"{path} must export an empty inherits value for flattened MobileSlicer bundles")
    if _scalar_text(config.get("is_custom_defined")) != "1":
        raise BundleValidationError(f"{path} must export is_custom_defined=1")


def _validate_printer_config(config: dict[str, Any], path: str, enforce_no_empty_vectors: bool = False) -> None:
    if enforce_no_empty_vectors:
        _validate_no_empty_vector_values(config, path)
    _require_nonblank(config, "name", path)
    _require_nonblank(config, "printer_settings_id", path)
    _require_nonblank(config, "nozzle_diameter", path)
    _require_nonblank(config, "printable_height", path)
    if "printable_area" not in config:
        raise BundleValidationError(f"{path} is missing printable_area")
    _require_empty_secret(config, "printhost_apikey", path)
    _require_empty_secret(config, "printhost_user", path)
    _require_empty_secret(config, "printhost_password", path)


def _validate_filament_config(config: dict[str, Any], path: str, enforce_no_empty_vectors: bool = False) -> None:
    if enforce_no_empty_vectors:
        _validate_no_empty_vector_values(config, path)
    _require_nonblank(config, "name", path)
    _require_nonblank(config, "filament_settings_id", path)
    _require_nonblank(config, "filament_type", path)


def _validate_process_config(config: dict[str, Any], path: str, enforce_no_empty_vectors: bool = False) -> None:
    if enforce_no_empty_vectors:
        _validate_no_empty_vector_values(config, path)
        _validate_orca_stable_process_values(config, path)
    _require_nonblank(config, "name", path)
    _require_nonblank(config, "print_settings_id", path)
    _require_nonblank(config, "layer_height", path)
    _require_nonblank(config, "wall_loops", path)
    compatible = _string_list(config.get("compatible_printers"), f"{path}.compatible_printers")
    if not compatible:
        raise BundleValidationError(f"{path} must include at least one compatible_printers entry")


def validate_bundle(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise BundleValidationError(f"Bundle does not exist: {path}")
    try:
        with zipfile.ZipFile(path) as zip_file:
            names = set(zip_file.namelist())
            structure = _read_json(zip_file, "bundle_structure.json")
            bundle_type = structure.get("bundle_type")
            if bundle_type not in {PRINTER_BUNDLE, FILAMENT_BUNDLE}:
                raise BundleValidationError(f"Unsupported bundle_type: {bundle_type!r}")

            if bundle_type == PRINTER_BUNDLE:
                printer_paths = _string_list(structure.get("printer_config"), "printer_config")
                filament_paths = _string_list(structure.get("filament_config"), "filament_config")
                process_paths = _string_list(structure.get("process_config"), "process_config")
                if not printer_paths:
                    raise BundleValidationError("printer_config must not be empty")
                printer_preset_name = _scalar_text(structure.get("printer_preset_name"))
                is_mobileslicer_export = "mobileslicer" in printer_preset_name.lower()
                for entry in printer_paths + filament_paths + process_paths:
                    if entry not in names:
                        raise BundleValidationError(f"bundle_structure references missing entry: {entry}")
                for entry in printer_paths:
                    config = _read_json(zip_file, entry)
                    _validate_printer_config(config, entry, enforce_no_empty_vectors=is_mobileslicer_export)
                    if is_mobileslicer_export:
                        _validate_mobileslicer_user_identity(config, entry)
                for entry in filament_paths:
                    config = _read_json(zip_file, entry)
                    _validate_filament_config(config, entry, enforce_no_empty_vectors=is_mobileslicer_export)
                    if is_mobileslicer_export:
                        _validate_mobileslicer_user_identity(config, entry)
                        compatible = _string_list(config.get("compatible_printers"), f"{entry}.compatible_printers")
                        if printer_preset_name not in compatible:
                            raise BundleValidationError(f"{entry} must be compatible with exported printer {printer_preset_name!r}")
                for entry in process_paths:
                    config = _read_json(zip_file, entry)
                    _validate_process_config(config, entry, enforce_no_empty_vectors=is_mobileslicer_export)
                    if is_mobileslicer_export:
                        _validate_mobileslicer_user_identity(config, entry)
                        compatible = _string_list(config.get("compatible_printers"), f"{entry}.compatible_printers")
                        if printer_preset_name not in compatible:
                            raise BundleValidationError(f"{entry} must be compatible with exported printer {printer_preset_name!r}")
                return {
                    "bundle_type": bundle_type,
                    "printers": len(printer_paths),
                    "filaments": len(filament_paths),
                    "processes": len(process_paths),
                }

            printer_vendor = structure.get("printer_vendor")
            if not isinstance(printer_vendor, list) or not printer_vendor:
                raise BundleValidationError("printer_vendor must be a non-empty list")
            filament_paths: list[str] = []
            for item in printer_vendor:
                if not isinstance(item, dict):
                    raise BundleValidationError("printer_vendor items must be objects")
                vendor = item.get("vendor")
                if not isinstance(vendor, str) or not vendor.strip():
                    raise BundleValidationError("printer_vendor item is missing vendor")
                filament_paths.extend(_string_list(item.get("filament_path"), f"printer_vendor[{vendor}].filament_path"))
            for entry in filament_paths:
                if entry not in names:
                    raise BundleValidationError(f"bundle_structure references missing entry: {entry}")
                _validate_filament_config(_read_json(zip_file, entry), entry)
            return {
                "bundle_type": bundle_type,
                "printers": 0,
                "filaments": len(filament_paths),
                "processes": 0,
            }
    except zipfile.BadZipFile as exc:
        raise BundleValidationError(f"Invalid ZIP bundle: {path}") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="Path to .orca_printer, .orca_filament, or Orca-style ZIP bundle")
    args = parser.parse_args(argv)
    try:
        result = validate_bundle(args.bundle)
    except BundleValidationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
