#!/usr/bin/env python3
"""Compile and validate the non-shipping Orca thumbnail extraction probe.

The probe is intentionally smaller than Orca's GLCanvas3D renderer. It proves
the source-backed role/camera contract can live at a native boundary without
pulling in wxWidgets, GUI_App, MainFrame, Plater, GLVolumeCollection, or
desktop OpenGLManager state.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path


FORBIDDEN_PROBE_TOKENS = (
    "wx/",
    "wxGetApp",
    "GUI_App",
    "MainFrame",
    "Plater",
    "GLCanvas3D",
    "GLVolumeCollection",
    "GLShaderProgram",
    "OpenGLManager",
)

REQUIRED_ORCA_SOURCE_MARKERS = (
    "Camera::ViewAngleType::Top_Plate",
    "for_picking",
    "ban_light",
    "render_thumbnail_internal",
)

EXPECTED_ROLES = {
    "gcode": {"camera_mode": "angled_iso", "picking": False, "ban_light": False},
    "plate": {"camera_mode": "angled_iso", "picking": False, "ban_light": False},
    "no_light": {"camera_mode": "angled_iso", "picking": False, "ban_light": True},
    "top": {"camera_mode": "top_plate", "picking": False, "ban_light": False},
    "pick": {"camera_mode": "top_plate", "picking": True, "ban_light": True},
}


@dataclass(frozen=True)
class GateResult:
    label: str
    ok: bool
    detail: str


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_kotlin_float_constant(text: str, name: str) -> float:
    match = re.search(rf"const\s+val\s+{re.escape(name)}\s*=\s*([0-9.+-]+)f?", text)
    if not match:
        raise ValueError(f"Missing Kotlin float constant {name}")
    return float(match.group(1))


def parse_kotlin_int_constant(text: str, name: str) -> int:
    match = re.search(rf"const\s+val\s+{re.escape(name)}\s*=\s*([0-9]+)", text)
    if not match:
        raise ValueError(f"Missing Kotlin int constant {name}")
    return int(match.group(1))


def compile_and_run_probe(root: Path) -> dict[str, object]:
    compiler = shutil.which("g++") or shutil.which("clang++")
    if compiler is None:
        raise RuntimeError("Missing g++ or clang++ for local extraction probe compilation")
    source = root / "engine-wrapper/orca_thumbnail_render_contract_probe.cpp"
    with tempfile.TemporaryDirectory(prefix="orca-thumbnail-probe-") as tmp:
        binary = Path(tmp) / "orca_thumbnail_render_contract_probe"
        subprocess.run(
            [
                compiler,
                "-std=c++17",
                "-DMOBILE_SLICER_ORCA_THUMBNAIL_CONTRACT_PROBE_MAIN",
                str(source),
                "-o",
                str(binary),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        result = subprocess.run(
            [str(binary)],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
    return json.loads(result.stdout)


def audit(root: Path) -> tuple[list[GateResult], dict[str, object]]:
    results: list[GateResult] = []
    probe_source = root / "engine-wrapper/orca_thumbnail_render_contract_probe.cpp"
    probe_text = read_text(probe_source)
    policy_text = read_text(root / "android-app/app/src/main/java/com/mobileslicer/workspace/OrcaThumbnailRenderPolicy.kt")
    orca_text = read_text(root / "vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp")

    forbidden = [token for token in FORBIDDEN_PROBE_TOKENS if token in probe_text]
    # Allow explanatory comments and provenance strings to name the blocked
    # desktop types, but not code-level includes or references. Strip comments
    # and string literals for the hard check.
    code_only = re.sub(r"//.*?$|/\*.*?\*/", "", probe_text, flags=re.MULTILINE | re.DOTALL)
    code_only = re.sub(r'"(?:\\.|[^"\\])*"', '""', code_only)
    forbidden_code = [token for token in FORBIDDEN_PROBE_TOKENS if token in code_only]
    results.append(
        GateResult(
            label="probe-has-no-desktop-code-dependencies",
            ok=not forbidden_code,
            detail="no forbidden tokens in code" if not forbidden_code else ", ".join(forbidden_code),
        )
    )
    results.append(
        GateResult(
            label="probe-documents-blocked-desktop-types",
            ok=bool(forbidden),
            detail=", ".join(forbidden[:6]) if forbidden else "no blocker names documented",
        )
    )

    missing_orca_markers = [marker for marker in REQUIRED_ORCA_SOURCE_MARKERS if marker not in orca_text]
    results.append(
        GateResult(
            label="orca-source-still-exposes-thumbnail-markers",
            ok=not missing_orca_markers,
            detail="markers present" if not missing_orca_markers else ", ".join(missing_orca_markers),
        )
    )

    payload = compile_and_run_probe(root)
    roles = {role["role"]: role for role in payload.get("roles", [])}
    missing_roles = sorted(set(EXPECTED_ROLES) - set(roles))
    results.append(
        GateResult(
            label="probe-emits-all-thumbnail-roles",
            ok=not missing_roles,
            detail="roles present" if not missing_roles else ", ".join(missing_roles),
        )
    )
    role_failures: list[str] = []
    for role_name, expected in EXPECTED_ROLES.items():
        actual = roles.get(role_name)
        if actual is None:
            continue
        for key, expected_value in expected.items():
            if actual.get(key) != expected_value:
                role_failures.append(f"{role_name}.{key}={actual.get(key)!r} expected {expected_value!r}")
    results.append(
        GateResult(
            label="probe-role-semantics-match-orca-policy",
            ok=not role_failures,
            detail="role semantics match" if not role_failures else "; ".join(role_failures),
        )
    )

    float_constants = {
        "AngledPitchDegrees": "pitch_degrees",
        "AngledYawDegrees": "yaw_degrees",
        "AngledCameraDistanceFactor": "camera_distance_factor",
        "AngledZoomToBoxMarginFactor": "zoom_to_box_margin_factor",
        "AngledBroadFootprintZoomToBoxMarginFactor": "broad_footprint_zoom_to_box_margin_factor",
        "AngledBoxHorizontalMarginFactor": "box_horizontal_margin_factor",
        "AngledBoxVerticalMarginFactor": "box_vertical_margin_factor",
        "TopPlateMargin": "top_plate_margin",
    }
    constant_failures: list[str] = []
    plate_role = roles.get("plate", {})
    top_role = roles.get("top", {})
    for kotlin_name, json_name in float_constants.items():
        kotlin_value = parse_kotlin_float_constant(policy_text, kotlin_name)
        actual_value = top_role.get(json_name) if kotlin_name == "TopPlateMargin" else plate_role.get(json_name)
        if actual_value is None or abs(float(actual_value) - kotlin_value) > 0.0001:
            constant_failures.append(f"{kotlin_name}={actual_value!r} expected {kotlin_value}")
    supersampling = payload.get("supersampling", {})
    int_constants = {
        "SmallThumbnailSupersampleFactor": "small_thumbnail_supersample_factor",
        "PackageSupersampleMaxOutputDimension": "package_supersample_max_output_dimension",
        "GcodeSupersampleMaxOutputDimension": "gcode_supersample_max_output_dimension",
        "SupersampleMaxRenderDimension": "supersample_max_render_dimension",
    }
    for kotlin_name, json_name in int_constants.items():
        kotlin_value = parse_kotlin_int_constant(policy_text, kotlin_name)
        actual_value = supersampling.get(json_name)
        if actual_value != kotlin_value:
            constant_failures.append(f"{kotlin_name}={actual_value!r} expected {kotlin_value}")
    results.append(
        GateResult(
            label="probe-constants-match-kotlin-policy",
            ok=not constant_failures,
            detail="constants match" if not constant_failures else "; ".join(constant_failures),
        )
    )
    return results, payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    results, probe_payload = audit(args.root)
    failures = [result.label for result in results if not result.ok]
    payload = {
        "ok": not failures,
        "root": str(args.root),
        "failures": failures,
        "checks": [asdict(result) for result in results],
        "probe": probe_payload,
        "summary": {
            "checked": len(results),
            "passed": len(results) - len(failures),
            "failed": len(failures),
        },
    }
    print(json.dumps(payload, indent=2 if args.pretty else None, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
