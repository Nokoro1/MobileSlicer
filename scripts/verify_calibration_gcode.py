#!/usr/bin/env python3
"""Run Mobile Slicer calibration slices on an ADB device and check emitted G-code.

This is intentionally narrow: it proves the calibration-specific commands that
matter for Orca parity are present in the actual app/native wrapper output.
"""

from __future__ import annotations

import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.mobileslicer"
MAIN_ACTIVITY = "com.mobileslicer/.MainActivity"
AUTOMATION_ACTION = "com.mobileslicer.action.AUTOMATE_SLICE"
DEVICE_AUTOMATION_DIR = f"/data/data/{PACKAGE}/files/automation"

BASE_CONFIG = {
    "bed_width_mm": 270,
    "bed_depth_mm": 270,
    "max_height_mm": 256,
    "nozzle_diameter": 0.4,
    "filament_diameter": 1.75,
    "filament_type": "PLA",
    "filament_flow_ratio": 1.0,
    "filament_max_volumetric_speed": 50,
    "nozzle_temperature_initial_layer": 210,
    "nozzle_temperature": 210,
    "bed_temperature_initial_layer": 60,
    "bed_temperature": 60,
    "cooling_baseline": 100,
    "close_fan_the_first_x_layers": 1,
    "layer_height": 0.2,
    "first_layer_height": 0.2,
    "first_layer_print_speed": 10,
    "first_layer_infill_speed": 22.5,
    "initial_layer_travel_speed_percent": 50,
    "slow_down_layers": 0,
    "outer_wall_speed": 30,
    "inner_wall_speed": 30,
    "top_surface_speed": 30,
    "travel_speed": 120,
    "default_acceleration": 500,
    "outer_wall_acceleration": 500,
    "inner_wall_acceleration": 500,
    "top_surface_acceleration": 500,
    "sparse_infill_acceleration": 500,
    "bridge_speed": 10,
    "small_perimeter_speed": 15,
    "small_perimeter_threshold": 0,
    "sparse_infill_speed": 80,
    "internal_solid_infill_speed": 30,
    "gap_infill_speed": 15,
    "top_shell_layers": 4,
    "bottom_shell_layers": 3,
    "seam_position": "aligned",
    "precise_outer_wall": True,
    "only_one_wall_top": True,
    "top_surface_pattern": "monotonicline",
    "sparse_infill_density": 15,
    "sparse_infill_pattern": "grid",
    "wall_loops": 2,
    "print_speed_baseline": 60,
    "skirts": 0,
    "brim_width": 0,
    "start_gcode": "",
    "end_gcode": "",
    "gcode_comments": True,
}


CASES = [
    {
        "name": "pa_pattern",
        "asset": "android-app/app/src/main/assets/calib_stl/pressure_advance/pa_pattern.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "PressureAdvance",
            "calibration_pa_method": "PA Pattern",
            "calibration_pa_start": 0.0,
            "calibration_pa_end": 0.04,
            "calibration_pa_step": 0.02,
            "calibration_pa_pattern_speeds": "30",
            "calibration_pa_pattern_accelerations": "500",
        },
        "checks": [
            ["; start pressure advance pattern for layer"],
            ["pressure advance pattern"],
            ["M900", "SET_PRESSURE_ADVANCE"],
        ],
    },
    {
        "name": "temperature_tower",
        "asset": "android-app/app/src/main/assets/calib_stl/temperature_tower/temperature_tower.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "TemperatureTower",
            "nozzle_temperature_initial_layer": 230,
            "nozzle_temperature": 230,
            "calibration_temp_start": 230,
            "calibration_temp_end": 220,
            "calibration_temp_step": 5,
        },
        "checks": [
            ["M104 S230", "M109 S230"],
            ["M104 S225", "M109 S225"],
            ["M104 S220", "M109 S220"],
        ],
    },
    {
        "name": "flow_rate",
        "asset": "android-app/app/src/main/assets/calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_20.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "FlowRate",
            "calibration_flow_pass": "Pass 1",
            "calibration_flow_start": 0.80,
            "calibration_flow_end": 1.20,
            "calibration_flow_step": 0.05,
        },
        "checks": [["flowrate_20"], ["; printing object"], ["; filament used", "; total filament used"]],
    },
    {
        "name": "max_volumetric",
        "asset": "android-app/app/src/main/assets/calib_stl/volumetric_speed/SpeedTestStructure.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "MaxVolumetricSpeed",
            "calibration_volumetric_start": 5,
            "calibration_volumetric_end": 8,
            "calibration_volumetric_step": 1,
        },
        # Orca applies this calibration by translating volumetric targets into
        # tower feedrates. The Android wrapper intentionally checks the emitted
        # behavior, because this path does not preserve Orca's desktop UI label
        # comments in the G-code.
        "checks": [["G1 F1620"], ["G1 F1680"], ["G1 F1740"], ["G1 F1800"]],
        "expected_feedrates": [1620, 1680, 1740, 1800],
    },
    {
        "name": "vfa",
        "asset": "android-app/app/src/main/assets/calib_stl/vfa/vfa.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "Vfa",
            "calibration_vfa_start": 40,
            "calibration_vfa_end": 70,
            "calibration_vfa_step": 10,
        },
        "checks": [["G1 F2400"], ["G1 F3000"], ["G1 F3600"], ["G1 F4200"]],
    },
    {
        "name": "retraction",
        "asset": "android-app/app/src/main/assets/calib_stl/retraction/retraction_tower.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "Retraction",
            "calibration_retraction_start": 0.0,
            "calibration_retraction_end": 0.4,
            "calibration_retraction_step": 0.2,
        },
        "checks": [["Calib_Retraction_tower"], ["length:0.2", "length:0.4"]],
    },
    {
        "name": "input_shaping_frequency",
        "asset": "android-app/app/src/main/assets/calib_stl/input_shaping/ringing_tower.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "InputShapingFrequency",
            "gcode_flavor": "marlin2",
            "calibration_input_shaping_start": 15,
            "calibration_input_shaping_end": 25,
            "calibration_input_shaping_step": 5,
            "calibration_test_model": 0,
            "calibration_shaper_type": "mzv",
        },
        "checks": [["M593", "SET_INPUT_SHAPER"], ["15", "20", "25"]],
    },
    {
        "name": "input_shaping_damping",
        "asset": "android-app/app/src/main/assets/calib_stl/input_shaping/fast_tower_test.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "InputShapingDamping",
            "gcode_flavor": "marlin2",
            "calibration_input_shaping_start": 0.05,
            "calibration_input_shaping_end": 0.15,
            "calibration_input_shaping_step": 0.05,
            "calibration_test_model": 1,
            "calibration_shaper_type": "mzv",
        },
        "checks": [["M593", "SET_INPUT_SHAPER"], ["D0.05", "D0.1", "D0.15"]],
    },
    {
        "name": "cornering",
        "asset": "android-app/app/src/main/assets/calib_stl/cornering/SCV-V2.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "Cornering",
            "calibration_cornering_start": 5,
            "calibration_cornering_end": 9,
            "calibration_cornering_step": 2,
            "calibration_test_model": 2,
        },
        "checks": [["M205", "M566", "SET_VELOCITY_LIMIT"]],
    },
    {
        "name": "tolerance",
        "asset": "android-app/app/src/main/assets/calib_stl/tolerance/OrcaToleranceTest.stl",
        "config": {
            "mobile_slicer_calibration_active": True,
            "mobile_slicer_calibration_type": "Tolerance",
            "mobile_slicer_calibration_tolerance_model": True,
        },
        "checks": [["; filament used", "; total filament used", "G1"]],
    },
]


def run(
    cmd: list[str],
    *,
    check: bool = True,
    text: bool = True,
    timeout_s: int = 120,
) -> subprocess.CompletedProcess[str]:
    try:
        proc = subprocess.run(
            cmd,
            check=False,
            text=text,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout_s,
        )
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        raise TimeoutError(f"{' '.join(cmd)} timed out after {timeout_s}s\n{output}") from exc
    if check and proc.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed with {proc.returncode}\n{proc.stdout}")
    return proc


def adb(serial: str | None, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return run(cmd, check=check)


def adb_shell(serial: str | None, command: str, *, check: bool = True) -> str:
    return adb(serial, "shell", command, check=check).stdout.strip()


def wait_for_status(serial: str | None, status_path: str, timeout_s: int = 180) -> str:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        exists = adb(serial, "shell", "run-as", PACKAGE, "test", "-f", status_path, check=False)
        if exists.returncode == 0:
            return adb(serial, "shell", "run-as", PACKAGE, "cat", status_path).stdout.strip()
        time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {status_path}")


def stage_model(serial: str | None, local_path: Path) -> str:
    remote_tmp = f"/data/local/tmp/{local_path.name}"
    app_path = f"{DEVICE_AUTOMATION_DIR}/{local_path.name}"
    adb(serial, "push", str(local_path), remote_tmp)
    adb_shell(serial, f"run-as {PACKAGE} mkdir -p files/automation")
    adb_shell(serial, f"run-as {PACKAGE} cp {remote_tmp} {app_path}")
    adb_shell(serial, f"rm -f {remote_tmp}")
    return app_path


def merged_config(case_config: dict) -> str:
    config = dict(BASE_CONFIG)
    config.update(case_config)
    return json.dumps(config, separators=(",", ":"))


def inclusive_values(start: float, end: float, step: float, *, precision: int = 4) -> list[float]:
    values: list[float] = []
    current = start
    guard = 0
    while current <= end + (abs(step) / 1000.0) and guard < 1000:
        values.append(round(current, precision))
        current += step
        guard += 1
    return values


def expected_descending_ints(start: int, end: int, step: int) -> set[int]:
    return set(range(start, end - 1, -abs(step)))


def expected_feedrates_mm_min(start: float, end: float, step: float) -> set[int]:
    return {int(round(value * 60.0)) for value in inclusive_values(start, end, step)}


def assert_gcode(case: dict, gcode: str) -> list[str]:
    failures: list[str] = []
    for alternatives in case["checks"]:
        if not any(needle in gcode for needle in alternatives):
            failures.append(" or ".join(alternatives))
    if case["name"] == "pa_pattern":
        start = float(case["config"]["calibration_pa_start"])
        end = float(case["config"]["calibration_pa_end"])
        step = float(case["config"]["calibration_pa_step"])
        expected = set(inclusive_values(start, end, step))
        emitted = {
            round(float(match.group(1)), 4)
            for match in re.finditer(r"\b(?:M900\s+K|SET_PRESSURE_ADVANCE\s+ADVANCE=)([0-9]+(?:\.[0-9]+)?)", gcode)
        }
        missing = sorted(expected - emitted)
        out_of_range = sorted(value for value in emitted if value < start - 1e-6 or value > end + 1e-6)
        if missing:
            failures.append(f"PA pattern missing pressure advance values {missing}")
        if out_of_range:
            failures.append(f"PA pattern emitted out-of-range pressure advance values {out_of_range}")
    if case["name"] == "temperature_tower":
        start = int(case["config"]["calibration_temp_start"])
        end = int(case["config"]["calibration_temp_end"])
        step = int(case["config"]["calibration_temp_step"])
        expected = expected_descending_ints(start, end, step)
        emitted = {
            int(round(float(match.group(1))))
            for match in re.finditer(r"\bM10[49]\s+S([0-9]+(?:\.[0-9]+)?)", gcode)
            if float(match.group(1)) > 0
        }
        out_of_range = sorted(temp for temp in emitted if temp < end or temp > start)
        missing = sorted(expected - emitted, reverse=True)
        if out_of_range:
            failures.append(f"temperature tower emitted out-of-range nozzle temps {out_of_range}")
        if missing:
            failures.append(f"temperature tower missing nozzle temps {missing}")
    if case["name"] == "retraction":
        start = float(case["config"]["calibration_retraction_start"])
        end = float(case["config"]["calibration_retraction_end"])
        emitted = {
            round(float(match.group(1)), 4)
            for match in re.finditer(r"Calib_Retraction_tower:.*?length:([0-9]+(?:\.[0-9]+)?)", gcode)
        }
        out_of_range = sorted(value for value in emitted if value < start - 1e-6 or value > end + 1e-6)
        if out_of_range:
            failures.append(f"retraction tower emitted out-of-range lengths {out_of_range}")
    if case["name"] in {"max_volumetric", "vfa"}:
        if case["name"] == "max_volumetric":
            expected = set(case.get("expected_feedrates", []))
        else:
            start = float(case["config"]["calibration_vfa_start"])
            end = float(case["config"]["calibration_vfa_end"])
            step = float(case["config"]["calibration_vfa_step"])
            expected = expected_feedrates_mm_min(start, end, step)
        feedrates = {
            int(float(match.group(1)))
            for match in re.finditer(r"^G1 F([0-9]+(?:\.[0-9]+)?)$", gcode, re.MULTILINE)
        }
        missing = sorted(expected - feedrates)
        if missing:
            failures.append(f"{case['name']} missing stepped feedrates {missing}")
    if case["name"] == "input_shaping_frequency":
        start = float(case["config"]["calibration_input_shaping_start"])
        end = float(case["config"]["calibration_input_shaping_end"])
        emitted = {
            round(float(match.group(1)), 3)
            for match in re.finditer(r"\bM593(?:\s+\w)?\s+F([0-9]+(?:\.[0-9]+)?)", gcode)
        }
        out_of_range = sorted(value for value in emitted if value < start - 0.01 or value > end + 0.01)
        if not any(abs(value - start) <= 0.01 for value in emitted) or not any(abs(value - end) <= 0.01 for value in emitted):
            failures.append(f"input shaping frequency missing endpoints {start}/{end}")
        if out_of_range:
            failures.append(f"input shaping frequency emitted out-of-range frequencies {out_of_range[:10]}")
    if case["name"] == "input_shaping_damping":
        start = float(case["config"]["calibration_input_shaping_start"])
        end = float(case["config"]["calibration_input_shaping_end"])
        emitted = {
            round(float(match.group(1)), 4)
            for match in re.finditer(r"\bM593\s+D([0-9]+(?:\.[0-9]+)?)", gcode)
        }
        out_of_range = sorted(value for value in emitted if value < start - 0.001 or value > end + 0.001)
        if not any(abs(value - start) <= 0.001 for value in emitted) or not any(abs(value - end) <= 0.001 for value in emitted):
            failures.append(f"input shaping damping missing endpoints {start}/{end}")
        if out_of_range:
            failures.append(f"input shaping damping emitted out-of-range damping values {out_of_range[:10]}")
    if case["name"] == "cornering":
        start = float(case["config"]["calibration_cornering_start"])
        end = float(case["config"]["calibration_cornering_end"])
        emitted = {
            round(float(match.group(1)), 4)
            for match in re.finditer(r"\bM205\s+X([0-9]+(?:\.[0-9]+)?)\s+Y\1\b.*?;\s*adjust jerk", gcode)
        }
        out_of_range = sorted(value for value in emitted if value < start - 0.01 or value > end + 0.01)
        if not any(abs(value - start) <= 0.01 for value in emitted) or not any(abs(value - end) <= 0.01 for value in emitted):
            failures.append(f"cornering missing endpoints {start}/{end}")
        if out_of_range:
            failures.append(f"cornering emitted out-of-range jerk values {out_of_range[:10]}")
    return failures


def run_case(serial: str | None, case: dict, out_dir: Path) -> tuple[bool, str]:
    asset = ROOT / case["asset"]
    if not asset.exists():
        return False, f"{case['name']}: missing asset {asset}"

    app_model_path = stage_model(serial, asset)
    output_path = f"{DEVICE_AUTOMATION_DIR}/calib-{case['name']}.gcode"
    status_path = f"{output_path}.status.txt"
    config = merged_config(case["config"])
    adb_shell(serial, f"run-as {PACKAGE} rm -f {output_path} {status_path}")
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    adb(serial, "shell", "logcat", "-c")
    command = (
        f"CONFIG={shlex.quote(config)}; am start -W "
        f"-a {shlex.quote(AUTOMATION_ACTION)} "
        f"-n {shlex.quote(MAIN_ACTIVITY)} "
        f"--es automation_model_path {shlex.quote(app_model_path)} "
        f"--es automation_output_path {shlex.quote(output_path)} "
        f"--es automation_status_path {shlex.quote(status_path)} "
        f"--es automation_config_json \"$CONFIG\""
    )
    adb_shell(serial, command)
    status = wait_for_status(serial, status_path)
    if not status.startswith("success:"):
        return False, f"{case['name']}: automation failed: {status}"

    local_gcode = out_dir / f"{case['name']}.gcode"
    pulled = adb(serial, "exec-out", "run-as", PACKAGE, "cat", output_path)
    local_gcode.write_text(pulled.stdout, encoding="utf-8", errors="replace")
    failures = assert_gcode(case, pulled.stdout)
    if failures:
        return False, f"{case['name']}: missing {failures}; gcode={local_gcode}"
    return True, f"{case['name']}: ok bytes={len(pulled.stdout.encode('utf-8'))} gcode={local_gcode}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=None)
    parser.add_argument("--out-dir", default=str(ROOT / "build" / "calibration-gcode-verification"))
    parser.add_argument("--case", action="append", choices=[case["name"] for case in CASES])
    args = parser.parse_args()

    selected = [case for case in CASES if not args.case or case["name"] in args.case]
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    failures = []
    for case in selected:
        print(f"== {case['name']} ==", flush=True)
        ok, message = run_case(args.serial, case, out_dir)
        print(message, flush=True)
        if not ok:
            failures.append(message)

    if failures:
        print("\nFailures:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
