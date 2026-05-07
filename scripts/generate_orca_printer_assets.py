#!/usr/bin/env python3
import argparse
import json
import shutil
import subprocess
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


def rel(path: Path, root: Path) -> str:
    return str(path.relative_to(root)).replace("\\", "/")


def profile_family(path: Path, profiles_root: Path) -> str:
    return path.relative_to(profiles_root).parts[0]


def profile_family_root(path: Path, profiles_root: Path) -> Path:
    return profiles_root / profile_family(path, profiles_root)


def parse_float(value):
    if isinstance(value, list):
        return parse_float(value[0]) if value else None
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return None


def parse_nozzles(value):
    if isinstance(value, list):
        values = value
    else:
        values = str(value or "").replace(",", ";").split(";")
    nozzles = []
    for item in values:
        parsed = parse_float(item)
        if parsed is not None and parsed > 0 and parsed not in nozzles:
            nozzles.append(parsed)
    return sorted(nozzles)


def write_thumbnail(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    magick = shutil.which("magick")
    if magick:
        result = subprocess.run(
            [magick, str(source), "-thumbnail", "160x160>", str(destination)],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0 and destination.exists():
            return
    shutil.copyfile(source, destination)


def copy_profile_asset(source: Path, destination: Path) -> str:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    return str(destination).replace("\\", "/")


def rasterize_profile_texture(source: Path, destination: Path) -> bool:
    destination.parent.mkdir(parents=True, exist_ok=True)
    suffix = source.suffix.lower()
    if suffix == ".png":
        shutil.copyfile(source, destination)
        return True
    if suffix != ".svg":
        return False
    try:
        import cairosvg

        cairosvg.svg2png(url=str(source), write_to=str(destination), output_width=1024, output_height=1024)
        return destination.exists() and destination.stat().st_size > 0
    except Exception:
        pass
    magick = shutil.which("magick")
    if magick:
        result = subprocess.run(
            [magick, "-background", "none", str(source), "-resize", "1024x1024", str(destination)],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return result.returncode == 0 and destination.exists() and destination.stat().st_size > 0
    return False


def parse_point(value):
    if isinstance(value, str) and "x" in value:
        x, y = value.lower().split("x", 1)
        return float(x), float(y)
    if isinstance(value, (list, tuple)) and len(value) >= 2:
        return float(value[0]), float(value[1])
    return None


def printable_bounds(printable_area):
    points = []
    if isinstance(printable_area, list):
        for item in printable_area:
            try:
                point = parse_point(item)
            except (TypeError, ValueError):
                point = None
            if point is not None:
                points.append(point)
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return {
        "width": max(xs) - min(xs),
        "depth": max(ys) - min(ys),
    }


def resolve_machine(machine_path: Path, machines_by_name: dict[str, tuple[Path, dict]]):
    chain = []
    merged = {}
    seen = set()
    current = machine_path

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
        current = machines_by_name.get(parent_ref, (None, None))[0] if parent_ref else None

    for path in reversed(chain):
        data = json.loads(path.read_text(encoding="utf-8"))
        merged.update(data)
    return merged, chain


def resolve_profile(profile_path: Path, profiles_by_name: dict[str, tuple[Path, dict]]):
    chain = []
    merged = {}
    seen = set()
    current = profile_path

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
        current = profiles_by_name.get(parent_ref, (None, None))[0] if parent_ref else None

    for path in reversed(chain):
        data = json.loads(path.read_text(encoding="utf-8"))
        merged.update(data)
    return merged, chain


def is_true(value) -> bool:
    return str(value).strip().lower() == "true"


def display_process_name(raw_name: str) -> str:
    return raw_name.split("@", 1)[0].strip()


def process_nozzle_from_name(raw_name: str):
    lowered = raw_name.lower()
    marker = " nozzle"
    if marker not in lowered:
        return None
    prefix = lowered.split(marker, 1)[0].split()[-1]
    return parse_float(prefix)


def clean_profile_key(value: str) -> str:
    return " ".join(
        "".join(ch.lower() if ch.isalnum() else " " for ch in str(value or "")).split()
    )


def remove_nozzle_suffix(value: str) -> str:
    import re

    return re.sub(r"\s+\d+(?:\.\d+)?\s*nozzle.*$", "", str(value or ""), flags=re.IGNORECASE).strip()


def process_target_from_name(raw_name: str):
    if "@" not in raw_name:
        return None
    return raw_name.split("@", 1)[1].strip()


def process_nozzle_from_target(target: str):
    compact_match = None
    parts = target.strip().split()
    if parts:
        compact_match = parse_float(parts[-1])
    if compact_match is not None:
        return compact_match

    import re

    match = re.search(r"(?<!\d)(\d+(?:\.\d+)?)(?!\d)\s*$", target)
    if not match:
        return None
    return parse_float(match.group(1))


def process_target_model_key(target: str):
    import re

    without_nozzle = re.sub(r"\s+\d+(?:\.\d+)?\s*(?:nozzle)?\s*$", "", target, flags=re.IGNORECASE)
    without_nozzle = re.sub(r"(?<!\d)(\d+(?:\.\d+)?)(?!\d)\s*$", "", without_nozzle).strip()
    return clean_profile_key(without_nozzle)


def matches_process_name_target(raw_process_name: str, family: str, resolved_machine: dict, machine_nozzle):
    target = process_target_from_name(raw_process_name)
    if not target or machine_nozzle is None:
        return False
    process_nozzle = process_nozzle_from_target(target)
    if process_nozzle is None or abs(machine_nozzle - process_nozzle) >= 0.001:
        return False

    target_key = process_target_model_key(target)
    if not target_key:
        return False
    machine_keys = {
        clean_profile_key(resolved_machine.get("printer_model")),
        clean_profile_key(resolved_machine.get("name")),
        clean_profile_key(str(resolved_machine.get("name") or "").replace(family, "", 1).strip()),
        clean_profile_key(str(resolved_machine.get("printer_model") or "").replace(family, "", 1).strip()),
    }
    return target_key in {key for key in machine_keys if key}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    repo_root = Path(args.repo_root)
    profiles_root = repo_root / "vendor" / "orcaslicer" / "resources" / "profiles"
    output_dir = Path(args.output_dir)
    covers_dir = output_dir / "covers"
    bundles_dir = output_dir / "bundles"
    manifest_path = output_dir / "printer_presets.json"

    if not profiles_root.exists():
        raise SystemExit(f"Missing Orca profiles directory: {profiles_root}")

    if output_dir.exists():
        remove_generated_dir(output_dir)
    covers_dir.mkdir(parents=True, exist_ok=True)
    bundles_dir.mkdir(parents=True, exist_ok=True)

    presets = []
    machine_json_paths = list(profiles_root.glob("*/machine/**/*.json"))
    machines_by_family: dict[str, list[tuple[Path, dict]]] = {}
    for machine_path in machine_json_paths:
        try:
            data = json.loads(machine_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        family = profile_family(machine_path, profiles_root)
        machines_by_family.setdefault(family, []).append((machine_path, data))

    for model_path in sorted(profiles_root.glob("*/machine/**/*.json")):
        try:
            data = json.loads(model_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        if data.get("type") != "machine_model":
            continue

        name = data.get("name") or model_path.stem
        family_root = profile_family_root(model_path, profiles_root)
        family = data.get("family") or family_root.name
        family_machines = machines_by_family.get(family_root.name, [])
        family_processes = []
        process_paths = list((family_root / "process").rglob("*.json"))
        processes_by_name = {}
        for process_path in process_paths:
            try:
                process_data = json.loads(process_path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                continue
            processes_by_name[process_data.get("name") or process_path.stem] = (process_path, process_data)
        for process_path, process_data in processes_by_name.values():
            if process_data.get("type") != "process" or not is_true(process_data.get("instantiation")):
                continue
            resolved_process, process_chain = resolve_profile(process_path, processes_by_name)
            compatible_printers = resolved_process.get("compatible_printers") or []
            if isinstance(compatible_printers, str):
                compatible_printers = [compatible_printers]
            family_processes.append(
                {
                    "path": process_path,
                    "raw": process_data,
                    "resolved": resolved_process,
                    "chain": process_chain,
                    "compatible_printers": compatible_printers,
                }
            )
        machines_by_name = {
            item.get("name") or path.stem: (path, item)
            for path, item in family_machines
        }
        nozzle_machines = [
            (path, item)
            for path, item in family_machines
            if item.get("type") == "machine"
            and (
                item.get("printer_model") == name
                or remove_nozzle_suffix(item.get("name") or path.stem) == name
            )
        ]
        resolved_nozzle_machines = [
            (path, item, *resolve_machine(path, machines_by_name))
            for path, item in nozzle_machines
        ]
        nozzles = parse_nozzles(data.get("nozzle_diameter"))
        for _, item, resolved, _ in resolved_nozzle_machines:
            for nozzle in parse_nozzles(resolved.get("nozzle_diameter") or item.get("nozzle_diameter")):
                if nozzle not in nozzles:
                    nozzles.append(nozzle)
        nozzles = sorted(nozzles)
        default_machine = next((machine for machine in resolved_nozzle_machines if parse_nozzles(machine[2].get("nozzle_diameter")) == [0.4]), None)
        if default_machine is None and resolved_nozzle_machines:
            default_machine = resolved_nozzle_machines[0]
        resolved_defaults = default_machine[2] if default_machine else {}
        bounds = printable_bounds(resolved_defaults.get("printable_area"))
        max_height = parse_float(resolved_defaults.get("printable_height")) or 220.0
        active_nozzle = parse_nozzles(resolved_defaults.get("nozzle_diameter"))
        bed_model_name = str(resolved_defaults.get("bed_model") or data.get("bed_model") or "").strip()
        bed_model_asset = ""
        if bed_model_name:
            bed_model_source = family_root / bed_model_name
            if bed_model_source.exists() and bed_model_source.is_file():
                bed_model_asset = f"orca-printers/bed-models/{safe_asset_name(family + '_' + bed_model_source.name)}"
                copy_profile_asset(bed_model_source, output_dir / "bed-models" / Path(bed_model_asset).name)
        bed_texture_name = str(resolved_defaults.get("bed_texture") or data.get("bed_texture") or "").strip()
        bed_texture_asset = ""
        if bed_texture_name:
            bed_texture_source = family_root / bed_texture_name
            if bed_texture_source.exists() and bed_texture_source.is_file():
                bed_texture_asset = f"orca-printers/bed-textures/{safe_asset_name(family + '_' + bed_texture_source.stem)}.png"
                if not rasterize_profile_texture(bed_texture_source, output_dir / "bed-textures" / Path(bed_texture_asset).name):
                    bed_texture_asset = ""
        cover = family_root / f"{name}_cover.png"
        cover_asset = ""
        if cover.exists():
            cover_asset = f"orca-printers/covers/{safe_asset_name(family + '_' + name)}.png"
            write_thumbnail(cover, output_dir / "covers" / Path(cover_asset).name)

        bundle_asset = f"orca-printers/bundles/{safe_asset_name(family + '_' + name + '_' + model_path.stem)}.json"
        process_presets = []
        for machine_path, _, resolved_machine, _ in resolved_nozzle_machines:
            machine_name = resolved_machine.get("name") or machine_path.stem
            machine_nozzles = parse_nozzles(resolved_machine.get("nozzle_diameter"))
            machine_nozzle = machine_nozzles[0] if machine_nozzles else None
            for process in family_processes:
                raw_process_name = process["raw"].get("name") or process["path"].stem
                compatible_printers = process["compatible_printers"]
                is_compatible = machine_name in compatible_printers
                if not compatible_printers:
                    process_nozzle = process_nozzle_from_name(raw_process_name)
                    if process_nozzle is None:
                        process_nozzle = 0.4
                    is_compatible = (
                        raw_process_name.endswith(f"@{family}")
                        and machine_nozzle is not None
                        and abs(machine_nozzle - process_nozzle) < 0.001
                    ) or matches_process_name_target(raw_process_name, family, resolved_machine, machine_nozzle)
                if not is_compatible:
                    continue
                process_presets.append(
                    {
                        "machineName": machine_name,
                        "nozzleDiameterMm": machine_nozzle or 0.4,
                        "name": display_process_name(raw_process_name),
                        "rawName": raw_process_name,
                        "profilePath": rel(process["path"], profiles_root),
                        "rawProcessJson": json.dumps(process["raw"], **COMPACT_JSON),
                        "resolvedProcessJson": json.dumps(process["resolved"], **COMPACT_JSON),
                        "resolvedSourceChain": [rel(chain_path, profiles_root) for chain_path in process["chain"]],
                    }
                )
        bundle = {
            "machineModelJson": json.dumps(data, **COMPACT_JSON),
            "resolvedMachineJson": json.dumps(resolved_defaults, **COMPACT_JSON),
            "nozzleMachineJsons": [
                json.dumps(item, **COMPACT_JSON)
                for _, item, _, _ in resolved_nozzle_machines
            ],
            "resolvedMachineJsons": [
                json.dumps(resolved, **COMPACT_JSON)
                for _, _, resolved, _ in resolved_nozzle_machines
            ],
            "processPresets": process_presets,
        }
        bundle_destination = output_dir / "bundles" / Path(bundle_asset).name
        bundle_destination.parent.mkdir(parents=True, exist_ok=True)
        bundle_destination.write_text(
            json.dumps(bundle, **COMPACT_JSON),
            encoding="utf-8",
        )

        nozzle_label = " ".join(f"{nozzle:g}" for nozzle in nozzles)
        search_text = " ".join(
            part
            for part in [name, family, model_path.stem, nozzle_label]
            if part
        ).casefold()

        presets.append(
            {
                "name": name,
                "family": family,
                "searchText": search_text,
                "nozzleDiameters": ";".join(f"{nozzle:g}" for nozzle in nozzles),
                "profilePath": str(model_path.relative_to(profiles_root)).replace("\\", "/"),
                "coverAssetPath": cover_asset,
                "importBundleAssetPath": bundle_asset,
                "bedModelAssetPath": bed_model_asset,
                "bedTextureAssetPath": bed_texture_asset,
                "bedWidthMm": bounds["width"] if bounds else 220.0,
                "bedDepthMm": bounds["depth"] if bounds else 220.0,
                "maxHeightMm": max_height,
                "activeNozzleDiameterMm": active_nozzle[0] if active_nozzle else (nozzles[0] if nozzles else 0.4),
                "nozzleMachinePaths": [rel(path, profiles_root) for path, _, _, _ in resolved_nozzle_machines],
                "resolvedSourceChains": [
                    [rel(chain_path, profiles_root) for chain_path in chain]
                    for _, _, _, chain in resolved_nozzle_machines
                ],
            }
        )

    presets.sort(key=lambda item: (item["family"].casefold(), item["name"].casefold()))
    manifest_path.write_text(json.dumps(presets, **COMPACT_JSON), encoding="utf-8")


if __name__ == "__main__":
    main()
