#!/usr/bin/env python3
"""Audit printer thumbnail compatibility claims.

The compatibility matrix is intentionally stricter than a feature checklist:
entries may only claim live support when there is a gate and live-host
evidence, and unsupported/pending families must stay visible until a fixture
or device run proves them.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ALLOWED_STATUSES = {
    "proven_live",
    "gated",
    "source_supported",
    "pending_live_validation",
    "not_supported",
}

REQUIRED_ENTRY_IDS = {
    "qidi-q2-fluidd-moonraker-png",
    "generic-fluidd-moonraker-png",
    "mainsail-moonraker-png",
    "octoprint-png",
    "qidi-colpic-gimage-simage",
    "btt-tft",
    "qoi-gcode",
    "jpg-gcode",
    "bambu-orca-sliced-3mf",
    "multi-plate-sliced-3mf",
}

REQUIRED_RELEASE_GATES = {
    "fluidd-thumbnail-metadata",
    "sliced-3mf-metadata",
    "multi-plate-sliced-3mf-metadata",
    "orca-thumbnail-reference-matrix",
}
REQUIRED_VERIFICATION_GATES = {
    "orca-metadata-benchmark",
}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def gate_is_wired(root: Path, gate: str) -> bool:
    verify = read_text(root / "scripts/verify_android.sh")
    release = read_text(root / "scripts/release_gate_android.sh")
    if gate == "orca-thumbnail-reference-matrix":
        return "run_orca_thumbnail_reference_matrix.sh" in release
    return gate in verify and gate in release


def verification_gate_exists(root: Path, gate: str) -> bool:
    return gate in read_text(root / "scripts/verify_android.sh")


def validate(root: Path, matrix_path: Path) -> list[str]:
    failures: list[str] = []
    matrix = json.loads(matrix_path.read_text(encoding="utf-8"))

    if matrix.get("schema_version") != 1:
        failures.append("schema_version must be 1")

    entries = matrix.get("entries")
    if not isinstance(entries, list) or not entries:
        failures.append("entries must be a non-empty list")
        return failures

    policy_gates = set(matrix.get("policy", {}).get("required_release_gates", []))
    missing_policy_gates = REQUIRED_RELEASE_GATES - policy_gates
    if missing_policy_gates:
        failures.append(f"policy missing required release gates: {sorted(missing_policy_gates)}")
    policy_verification_gates = set(matrix.get("policy", {}).get("required_verification_gates", []))
    missing_policy_verification_gates = REQUIRED_VERIFICATION_GATES - policy_verification_gates
    if missing_policy_verification_gates:
        failures.append(f"policy missing required verification gates: {sorted(missing_policy_verification_gates)}")

    ids = [entry.get("id") for entry in entries]
    duplicate_ids = sorted({entry_id for entry_id in ids if ids.count(entry_id) > 1})
    if duplicate_ids:
        failures.append(f"duplicate entry ids: {duplicate_ids}")

    missing_ids = REQUIRED_ENTRY_IDS - set(ids)
    if missing_ids:
        failures.append(f"missing required compatibility entries: {sorted(missing_ids)}")

    for gate in REQUIRED_RELEASE_GATES:
        if not gate_is_wired(root, gate):
            failures.append(f"required gate is not wired into release verification: {gate}")
    for gate in REQUIRED_VERIFICATION_GATES:
        if not verification_gate_exists(root, gate):
            failures.append(f"required gate is not wired into verification: {gate}")

    for entry in entries:
        entry_id = entry.get("id", "<missing>")
        status = entry.get("status")
        evidence = entry.get("evidence", {})
        formats = entry.get("formats", [])

        if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", str(entry_id)):
            failures.append(f"{entry_id}: id must be lowercase kebab-case")
        if status not in ALLOWED_STATUSES:
            failures.append(f"{entry_id}: invalid status {status!r}")
        if not isinstance(formats, list) or not formats:
            failures.append(f"{entry_id}: formats must be a non-empty list")
        if not isinstance(evidence, dict):
            failures.append(f"{entry_id}: evidence must be an object")
            continue

        gate = evidence.get("gate")
        if status in {"proven_live", "gated"}:
            if not gate:
                failures.append(f"{entry_id}: {status} entries require an evidence.gate")
            elif gate in REQUIRED_VERIFICATION_GATES:
                if not verification_gate_exists(root, str(gate)):
                    failures.append(f"{entry_id}: evidence gate is not wired into verification: {gate}")
            elif not gate_is_wired(root, str(gate)):
                failures.append(f"{entry_id}: evidence gate is not wired into release verification: {gate}")

        if status == "proven_live":
            if not evidence.get("live_host"):
                failures.append(f"{entry_id}: proven_live entries require evidence.live_host")
            if not evidence.get("latest_report") and not evidence.get("moonraker_metadata"):
                failures.append(f"{entry_id}: proven_live entries require a latest report or host metadata artifact")

        if status == "source_supported":
            source_paths = evidence.get("source_paths")
            if not isinstance(source_paths, list) or not source_paths:
                failures.append(f"{entry_id}: source_supported entries require evidence.source_paths")
            else:
                for relative in source_paths:
                    if not (root / relative).exists():
                        failures.append(f"{entry_id}: missing source evidence path {relative}")

        if status in {"pending_live_validation", "not_supported"} and not evidence.get("notes"):
            failures.append(f"{entry_id}: {status} entries require evidence.notes")

        if entry_id == "multi-plate-sliced-3mf":
            if status == "not_supported":
                failures.append("multi-plate-sliced-3mf now has a plate_2 package gate and must not remain not_supported")
            if gate != "multi-plate-sliced-3mf-metadata":
                failures.append("multi-plate-sliced-3mf must use the plate_2 package fixture gate")

    return failures


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--matrix",
        type=Path,
        default=root / "regression-fixtures/printer-thumbnail-compatibility/matrix.json",
    )
    parser.add_argument("--root", type=Path, default=root)
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    failures = validate(args.root, args.matrix)
    payload = {
        "ok": not failures,
        "matrix": str(args.matrix),
        "failures": failures,
    }
    print(json.dumps(payload, indent=2 if args.pretty else None, sort_keys=True))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
