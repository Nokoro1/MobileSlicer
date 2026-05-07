#!/usr/bin/env python3
"""Summarize the release worktree into reviewable changeset groups."""

from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class GroupRule:
    name: str
    prefixes: tuple[str, ...]
    exact: tuple[str, ...] = ()


GROUP_RULES = (
    GroupRule(
        name="release-gates-and-scripts",
        prefixes=(".github/workflows/", "scripts/", "performance-baselines/"),
        exact=(".gitignore",),
    ),
    GroupRule(
        name="native-orca-wrapper",
        prefixes=("engine-wrapper/", "android-app/app/src/main/cpp/"),
    ),
    GroupRule(
        name="android-workspace-and-render",
        prefixes=(
            "android-app/app/src/main/java/com/mobileslicer/viewer/",
            "android-app/app/src/main/java/com/mobileslicer/workspace/",
            "android-app/app/src/main/res/drawable/ic_tool_",
        ),
    ),
    GroupRule(
        name="profiles-storage-printer",
        prefixes=(
            "android-app/app/src/main/java/com/mobileslicer/profiles/",
            "android-app/app/src/main/java/com/mobileslicer/storage/",
            "android-app/app/src/main/java/com/mobileslicer/printerconnection/",
            "android-app/app/src/test/java/com/mobileslicer/printerconnection/",
            "android-app/app/src/test/java/com/mobileslicer/storage/",
        ),
    ),
    GroupRule(
        name="paint-cut-feature-surface",
        prefixes=(
            "android-app/app/src/main/java/com/mobileslicer/nativebridge/NativeCut",
            "android-app/app/src/main/java/com/mobileslicer/nativebridge/NativePaint",
            "android-app/app/src/main/java/com/mobileslicer/nativebridge/NativeSplit",
            "android-app/app/src/main/java/com/mobileslicer/PaintInteractionAutomation.kt",
            "android-app/app/src/test/java/com/mobileslicer/nativebridge/",
            "android-app/app/src/test/java/com/mobileslicer/workspace/",
            "android-app/app/src/test/java/com/mobileslicer/viewer/",
            "android-app/app/src/test/java/com/mobileslicer/storage/Paint",
        ),
    ),
    GroupRule(
        name="android-app-flow",
        prefixes=(
            "android-app/app/build.gradle.kts",
            "android-app/app/src/main/java/com/mobileslicer/",
            "android-app/app/src/main/assets/",
            "android-app/app/src/test/java/com/mobileslicer/",
        ),
    ),
    GroupRule(
        name="docs",
        prefixes=("README/", "docs/", "THIRD_PARTY_NOTICES.md"),
    ),
    GroupRule(
        name="website",
        prefixes=("Website/",),
    ),
    GroupRule(
        name="vendor-patches",
        prefixes=("vendor/orcaslicer/", "patches/orcaslicer/"),
    ),
    GroupRule(
        name="proof-fixtures",
        prefixes=("proof-fixtures/", "regression-fixtures/", "mobileslicer_test_cube.stl"),
    ),
)


def parse_status_line(line: str) -> tuple[str, str]:
    status = line[:2]
    path = line[3:]
    if " -> " in path:
        path = path.split(" -> ", 1)[1]
    return status, path


def group_for_path(path: str) -> str:
    for rule in GROUP_RULES:
        if path in rule.exact or any(path.startswith(prefix) for prefix in rule.prefixes):
            return rule.name
    return "uncategorized"


def status_lines(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return [line for line in result.stdout.splitlines() if line.strip()]


def summarize(lines: list[str]) -> dict[str, list[tuple[str, str]]]:
    groups: dict[str, list[tuple[str, str]]] = {}
    for line in lines:
        status, path = parse_status_line(line)
        groups.setdefault(group_for_path(path), []).append((status, path))
    return groups


def print_summary(groups: dict[str, list[tuple[str, str]]], limit: int) -> None:
    total = sum(len(entries) for entries in groups.values())
    print(f"changed_paths={total}")
    for group in sorted(groups):
        entries = groups[group]
        print(f"\n[{group}] count={len(entries)}")
        for status, path in entries[:limit]:
            print(f"{status} {path}")
        remaining = len(entries) - limit
        if remaining > 0:
            print(f"... {remaining} more")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--limit", type=int, default=40, help="maximum paths shown per group")
    parser.add_argument("--max-changed", type=int, default=None, help="fail if changed path count is above this")
    parser.add_argument("--fail-on-uncategorized", action="store_true")
    args = parser.parse_args()

    groups = summarize(status_lines(args.root))
    print_summary(groups, limit=max(args.limit, 0))

    failures: list[str] = []
    total = sum(len(entries) for entries in groups.values())
    if args.max_changed is not None and total > args.max_changed:
        failures.append(f"changed path count {total} exceeds --max-changed {args.max_changed}")
    if args.fail_on_uncategorized and groups.get("uncategorized"):
        failures.append(f"{len(groups['uncategorized'])} uncategorized changed paths")

    if failures:
        for failure in failures:
            print(f"error: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
