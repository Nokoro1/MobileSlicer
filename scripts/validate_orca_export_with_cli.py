#!/usr/bin/env python3
"""Validate MobileSlicer Orca exports with OrcaSlicer's own CLI config loader.

The validator is intentionally optional. Set ORCA_CLI_PATH or pass --orca-cli to
enable it in release dry runs. Without a binary it skips, so normal Android
development does not depend on a desktop OrcaSlicer build.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from validate_orca_profile_bundle import validate_bundle


class OrcaCliValidationError(Exception):
    pass


def _split_env_args(value: str | None) -> list[str]:
    if not value:
        return []
    import shlex

    return shlex.split(value)


def _extract_bundle(bundle: Path, destination: Path) -> tuple[list[Path], list[Path], list[Path]]:
    validate_bundle(bundle)
    with zipfile.ZipFile(bundle) as zip_file:
        zip_file.extractall(destination)
    printers = sorted((destination / "printer").glob("*.json"))
    filaments = sorted((destination / "filament").glob("*.json"))
    processes = sorted((destination / "process").glob("*.json"))
    if not printers:
        raise OrcaCliValidationError("No printer JSON files were found in the extracted bundle.")
    return printers, filaments, processes


def build_orca_cli_command(
    orca_cli: Path,
    printer_paths: list[Path],
    filament_paths: list[Path],
    process_paths: list[Path],
    extra_args: list[str] | None = None,
) -> list[str]:
    load_settings = [*(str(path) for path in printer_paths), *(str(path) for path in process_paths)]
    command = [str(orca_cli), "--load_settings", ";".join(load_settings)]
    if filament_paths:
        command += ["--load_filaments", ";".join(str(path) for path in filament_paths)]
    command += extra_args or []
    return command


def validate_with_orca_cli(
    bundle: Path,
    orca_cli: Path | None,
    extra_args: list[str] | None = None,
    skip_if_missing: bool = True,
) -> str:
    if orca_cli is None:
        if skip_if_missing:
            return "SKIP: ORCA_CLI_PATH was not set."
        raise OrcaCliValidationError("ORCA_CLI_PATH was not set.")
    resolved_cli = Path(orca_cli).expanduser()
    if not resolved_cli.exists():
        if skip_if_missing:
            return f"SKIP: Orca CLI binary does not exist: {resolved_cli}"
        raise OrcaCliValidationError(f"Orca CLI binary does not exist: {resolved_cli}")
    if not os.access(resolved_cli, os.X_OK):
        raise OrcaCliValidationError(f"Orca CLI binary is not executable: {resolved_cli}")

    with tempfile.TemporaryDirectory(prefix="mobileslicer-orca-cli-") as tmp:
        printer_paths, filament_paths, process_paths = _extract_bundle(bundle, Path(tmp))
        command = build_orca_cli_command(
            resolved_cli,
            printer_paths=printer_paths,
            filament_paths=filament_paths,
            process_paths=process_paths,
            extra_args=extra_args,
        )
        completed = subprocess.run(
            command,
            cwd=tmp,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    if completed.returncode != 0:
        raise OrcaCliValidationError(
            "Orca CLI rejected exported profile bundle.\n"
            f"command: {' '.join(command)}\n"
            f"exit: {completed.returncode}\n"
            f"stdout:\n{completed.stdout}\n"
            f"stderr:\n{completed.stderr}"
        )
    return "PASS: Orca CLI accepted exported profile bundle."


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="Exported .orca_printer bundle to validate")
    parser.add_argument("--orca-cli", type=Path, default=os.environ.get("ORCA_CLI_PATH"), help="Path to OrcaSlicer CLI binary")
    parser.add_argument(
        "--extra-arg",
        action="append",
        default=[],
        help="Additional argument to append to the Orca command. Can be repeated.",
    )
    parser.add_argument(
        "--extra-args-env",
        default=os.environ.get("ORCA_CLI_EXTRA_ARGS"),
        help="Optional shell-style extra args. Defaults to ORCA_CLI_EXTRA_ARGS.",
    )
    parser.add_argument("--require-cli", action="store_true", help="Fail instead of skipping when no Orca CLI is available")
    args = parser.parse_args(argv)

    extra_args = [*_split_env_args(args.extra_args_env), *args.extra_arg]
    try:
        message = validate_with_orca_cli(
            bundle=args.bundle,
            orca_cli=args.orca_cli,
            extra_args=extra_args,
            skip_if_missing=not args.require_cli,
        )
    except OrcaCliValidationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(message)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
