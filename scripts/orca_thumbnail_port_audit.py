#!/usr/bin/env python3
"""Audit the Orca thumbnail-renderer port boundary.

This is a source-level gate, not an image comparison. It records the exact
desktop Orca thumbnail entry points and the MobileSlicer Android handoff that
must stay intact while we decide how much of Orca's renderer stack is practical
to import.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class RequiredPattern:
    label: str
    path: str
    pattern: str
    reason: str


@dataclass
class PatternResult:
    label: str
    path: str
    found: bool
    reason: str
    evidence: str | None = None


REQUIRED_PATTERNS = [
    RequiredPattern(
        label="orca-framebuffer-entry",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.hpp",
        pattern=r"render_thumbnail_framebuffer\s*\(",
        reason="Desktop Orca exposes framebuffer thumbnail rendering through GLCanvas3D.",
    ),
    RequiredPattern(
        label="orca-framebuffer-ext-entry",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.hpp",
        pattern=r"render_thumbnail_framebuffer_ext\s*\(",
        reason="Desktop Orca has a second EXT framebuffer path that package export can select.",
    ),
    RequiredPattern(
        label="orca-render-internal",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp",
        pattern=r"void\s+GLCanvas3D::render_thumbnail_internal\s*\(",
        reason="The real Orca camera, visible-volume, light, no-light, and pick behavior lives here.",
    ),
    RequiredPattern(
        label="orca-top-plate-camera",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp",
        pattern=r"Camera::ViewAngleType::Top_Plate",
        reason="Top and pick thumbnails must keep Orca's full-plate top projection contract.",
    ),
    RequiredPattern(
        label="orca-picking-mode",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp",
        pattern=r"if\s*\(for_picking\)",
        reason="Pick thumbnails are object-id renders, not normal lit thumbnails.",
    ),
    RequiredPattern(
        label="orca-no-light-mode",
        path="vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp",
        pattern=r"ban_light",
        reason="No-light package thumbnails are a separate Orca role and must stay visually distinct.",
    ),
    RequiredPattern(
        label="orca-cli-package-plate",
        path="vendor/orcaslicer/src/OrcaSlicer.cpp",
        pattern=r"plate_data->plate_thumbnail",
        reason="Desktop Orca CLI/package export populates the main plate thumbnail.",
    ),
    RequiredPattern(
        label="orca-cli-package-no-light",
        path="vendor/orcaslicer/src/OrcaSlicer.cpp",
        pattern=r"no_light_thumbnail",
        reason="Desktop Orca package export populates the no-light package role.",
    ),
    RequiredPattern(
        label="orca-cli-package-top-pick",
        path="vendor/orcaslicer/src/OrcaSlicer.cpp",
        pattern=r"Camera::ViewAngleType::Top_Plate,\s*true,\s*true",
        reason="Desktop Orca package export populates top and pick roles together.",
    ),
    RequiredPattern(
        label="orca-3mf-writes-no-light",
        path="vendor/orcaslicer/src/libslic3r/Format/bbs_3mf.cpp",
        pattern=r"Metadata/plate_no_light",
        reason="The package writer must still know Orca's no-light thumbnail entry names.",
    ),
    RequiredPattern(
        label="orca-android-real-libslic3r",
        path="android-app/app/src/main/cpp/CMakeLists.txt",
        pattern=r"ORCA_SHIPPING_USE_REAL_LIBSLIC3R",
        reason="Android shipping builds must stay on the real Orca libslic3r wrapper.",
    ),
    RequiredPattern(
        label="orca-android-egl-boundary",
        path="android-app/app/src/main/cpp/CMakeLists.txt",
        pattern=r"find_library\(egl-lib EGL\)",
        reason="Current renderer work is bounded to Android EGL rather than desktop wxGLCanvas.",
    ),
    RequiredPattern(
        label="orca-android-libvgcode",
        path="android-app/app/src/main/cpp/CMakeLists.txt",
        pattern=r"add_subdirectory\(\s*\r?\n\s*\.\./\.\./\.\./\.\./\.\./vendor/orcaslicer/src/libvgcode",
        reason="The Android native build already imports Orca's GLES-compatible viewer/render support.",
    ),
    RequiredPattern(
        label="production-egl-renderer",
        path="android-app/app/src/main/java/com/mobileslicer/MainActivitySlicing.kt",
        pattern=r"OffscreenEglSliceThumbnailRenderer",
        reason="Production slicing should use the Android EGL thumbnail renderer contract.",
    ),
    RequiredPattern(
        label="android-orca-thumbnail-policy",
        path="android-app/app/src/main/java/com/mobileslicer/workspace/OrcaThumbnailRenderPolicy.kt",
        pattern=r"OrcaThumbnailRenderPolicy",
        reason="The Android renderer must expose the Orca thumbnail role/camera extraction contract as a testable policy.",
    ),
    RequiredPattern(
        label="android-orca-thumbnail-policy-source",
        path="android-app/app/src/main/java/com/mobileslicer/workspace/OrcaThumbnailRenderPolicy.kt",
        pattern=r"vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp",
        reason="The policy must point back to the desktop Orca source it mirrors.",
    ),
    RequiredPattern(
        label="android-small-thumbnail-supersampling",
        path="android-app/app/src/main/java/com/mobileslicer/workspace/OrcaThumbnailRenderPolicy.kt",
        pattern=r"SmallThumbnailSupersampleFactor",
        reason="Small Fluidd-style thumbnails should use bounded supersampling instead of direct aliased renders.",
    ),
    RequiredPattern(
        label="android-thumbnail-downsample",
        path="android-app/app/src/main/java/com/mobileslicer/workspace/OffscreenEglSliceThumbnailRenderer.kt",
        pattern=r"downsampleThumbnailRgba",
        reason="The EGL renderer must downsample supersampled thumbnail buffers before uploading them to Orca.",
    ),
    RequiredPattern(
        label="automation-egl-renderer",
        path="android-app/app/src/main/java/com/mobileslicer/automation/AutomationSliceRunner.kt",
        pattern=r"OffscreenEglSliceThumbnailRenderer",
        reason="Device gates must exercise the same renderer as production.",
    ),
    RequiredPattern(
        label="scope-gate-renderer",
        path="scripts/verify_android.sh",
        pattern=r"offscreen_egl",
        reason="Release automation must fail if thumbnail rendering drifts away from EGL.",
    ),
]


def _first_match_line(text: str, pattern: str) -> str | None:
    regex = re.compile(pattern, re.MULTILINE | re.DOTALL)
    match = regex.search(text)
    if not match:
        return None
    start = text.rfind("\n", 0, match.start()) + 1
    end = text.find("\n", match.start())
    if end == -1:
        end = len(text)
    return text[start:end].strip()


def audit(root: Path) -> tuple[list[PatternResult], list[str]]:
    results: list[PatternResult] = []
    failures: list[str] = []
    for requirement in REQUIRED_PATTERNS:
        path = root / requirement.path
        evidence = None
        found = False
        if path.exists():
            evidence = _first_match_line(path.read_text(encoding="utf-8", errors="replace"), requirement.pattern)
            found = evidence is not None
        if not found:
            failures.append(requirement.label)
        results.append(
            PatternResult(
                label=requirement.label,
                path=requirement.path,
                found=found,
                reason=requirement.reason,
                evidence=evidence,
            )
        )
    return results, failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    results, failures = audit(args.root)
    payload = {
        "ok": not failures,
        "root": str(args.root),
        "failures": failures,
        "checks": [asdict(result) for result in results],
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
