#!/usr/bin/env python3
import argparse
import json
import re
import shutil
import time
from pathlib import Path

COMPACT_JSON = {"sort_keys": True, "separators": (",", ":")}


def remove_generated_dir(path: Path) -> None:
    for attempt in range(5):
        try:
            shutil.rmtree(path)
            return
        except FileNotFoundError:
            return
        except OSError:
            if attempt == 4:
                raise
            time.sleep(0.1 * (attempt + 1))


def safe_asset_name(name: str) -> str:
    return "".join(ch if ch.isalnum() else "_" for ch in name).strip("_").lower()


def clean_profile_match_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", str(value).lower()).strip()


def rel(path: Path, root: Path) -> str:
    return str(path.relative_to(root)).replace("\\", "/")


def resolve_preset(preset_path: Path, presets_by_name: dict[str, tuple[Path, dict]]):
    chain = []
    merged = {}
    seen = set()
    current = preset_path

    while current is not None:
        if current in seen:
            break
        seen.add(current)
        try:
            data = json.loads(current.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            break
        chain.append(current)
        parent_ref = data.get("inherits")
        current = presets_by_name.get(parent_ref, (None, None))[0] if parent_ref else None

    for path in reversed(chain):
        data = json.loads(path.read_text(encoding="utf-8"))
        merged.update(data)
    return merged, chain


def first_string(value, default=""):
    if isinstance(value, list):
        return first_string(value[0], default) if value else default
    if value is None:
        return default
    return str(value)


def display_filament_name(raw_name: str, material: str) -> str:
    name = raw_name.split("@", 1)[0]
    is_generic = "generic" in name.casefold()
    name = name.replace("Generic", "")
    name = " ".join(part for part in name.replace("_", " ").split() if part)
    material = " ".join(str(material).replace("_", " ").split())
    if is_generic and material:
        return material.upper()
    return name if name else material.upper()


def display_filament_vendor(raw_name: str, display_name: str, material: str, vendor: str, family: str) -> str:
    if clean_profile_match_key(vendor) != "generic":
        return vendor
    display_key = clean_profile_match_key(display_name)
    material_key = clean_profile_match_key(material)
    if display_key and display_key == material_key:
        return vendor
    inferred_vendor = infer_vendor_from_display_name(display_name, material)
    if inferred_vendor:
        return inferred_vendor
    raw_base_key = clean_profile_match_key(raw_name.split("@", 1)[0])
    family_key = clean_profile_match_key(family)
    if family_key and raw_base_key.startswith(family_key):
        return family
    return vendor


def infer_vendor_from_display_name(display_name: str, material: str) -> str:
    tokens = display_name.split()
    if len(tokens) < 2:
        return ""
    material_markers = material_markers_for(material)
    for index, token in enumerate(tokens):
        if index == 0:
            continue
        token_key = clean_profile_match_key(token).replace(" ", "")
        if token_key in material_markers or any(token_key.endswith(marker) for marker in material_markers):
            return " ".join(tokens[:index])
    return ""


def material_markers_for(material: str) -> set[str]:
    material_key = clean_profile_match_key(material).replace(" ", "")
    markers = {
        "abs", "asa", "flex", "hips", "pa", "pacf", "paht", "pahtcf",
        "pc", "petg", "petgcf", "pla", "placf", "tpu"
    }
    if material_key:
        markers.add(material_key)
    if material_key in {"pa", "pacf"}:
        markers.update({"pa", "paht", "pahtcf", "pacf", "copa"})
    if material_key.startswith("pla"):
        markers.update({"pla", "plaplus", "plapro", "plasilk", "plasparkle", "placf"})
    return markers


def list_strings(value) -> list[str]:
    if isinstance(value, list):
        return [str(item) for item in value if item is not None and str(item).strip()]
    if value is None:
        return []
    text = str(value).strip()
    return [text] if text else []


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    repo_root = Path(args.repo_root)
    profiles_root = repo_root / "vendor" / "orcaslicer" / "resources" / "profiles"
    output_dir = Path(args.output_dir)
    bundles_dir = output_dir / "bundles"
    manifest_path = output_dir / "filament_presets.json"

    if not profiles_root.exists():
        raise SystemExit(f"Missing Orca profiles directory: {profiles_root}")

    if output_dir.exists():
        remove_generated_dir(output_dir)
    bundles_dir.mkdir(parents=True, exist_ok=True)

    presets = []
    filament_json_paths = list(profiles_root.glob("*/filament/**/*.json"))
    filaments_by_family: dict[str, list[tuple[Path, dict]]] = {}
    for filament_path in filament_json_paths:
        try:
            data = json.loads(filament_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        if data.get("type") != "filament":
            continue
        family = filament_path.relative_to(profiles_root).parts[0]
        filaments_by_family.setdefault(family, []).append((filament_path, data))

    for filament_path in sorted(filament_json_paths):
        family = filament_path.relative_to(profiles_root).parts[0]
        try:
            data = json.loads(filament_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        if data.get("type") != "filament":
            continue
        if str(data.get("instantiation", "")).lower() != "true":
            continue

        family_presets = filaments_by_family.get(family, [])
        presets_by_name = {
            item.get("name") or path.stem: (path, item)
            for path, item in family_presets
        }
        resolved, chain = resolve_preset(filament_path, presets_by_name)
        name = data.get("name") or filament_path.stem
        material = first_string(resolved.get("filament_type"), "PLA")
        resolved_vendor = first_string(resolved.get("filament_vendor"), family)
        display_name = display_filament_name(name, material)
        vendor = display_filament_vendor(name, display_name, material, resolved_vendor, family)
        compatible_printers = list_strings(resolved.get("compatible_printers"))
        compatible_printer_keys = [
            key
            for key in (clean_profile_match_key(value) for value in compatible_printers)
            if key
        ]
        picker_duplicate_key = "|".join(
            clean_profile_match_key(value)
            for value in [display_name, material, vendor]
        )
        color = first_string(resolved.get("default_filament_colour"))
        bundle_asset = f"orca-filaments/bundles/{safe_asset_name(rel(filament_path, profiles_root))}.json"
        bundle = {
            "rawFilamentJson": json.dumps(data, **COMPACT_JSON),
            "resolvedFilamentJson": json.dumps(resolved, **COMPACT_JSON),
            "resolvedSourceChain": [rel(chain_path, profiles_root) for chain_path in chain],
        }
        (output_dir / "bundles" / Path(bundle_asset).name).write_text(
            json.dumps(bundle, **COMPACT_JSON),
            encoding="utf-8",
        )

        search_text = " ".join(
            part
            for part in [display_name, name, family, material, vendor, filament_path.stem]
            if part
        ).casefold()
        presets.append({
            "name": display_name,
            "rawName": name,
            "family": family,
            "materialType": material,
            "vendor": vendor,
            "defaultFilamentColor": color,
            "profilePath": rel(filament_path, profiles_root),
            "importBundleAssetPath": bundle_asset,
            "searchText": search_text,
            "compatiblePrinters": compatible_printers,
            "compatiblePrinterKeys": compatible_printer_keys,
            "pickerDuplicateKey": picker_duplicate_key,
        })

    presets.sort(key=lambda item: (item["family"].casefold(), item["name"].casefold(), item["rawName"].casefold()))
    manifest_path.write_text(json.dumps(presets, **COMPACT_JSON), encoding="utf-8")


if __name__ == "__main__":
    main()
