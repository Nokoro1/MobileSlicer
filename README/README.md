# Mobile Slicer

Mobile Slicer is a native Android 3D printing slicer built on top of the OrcaSlicer engine.

This project is designed with strict architectural separation:

* Native Android UI (Kotlin + Compose)
* C++ slicing engine (wrapped from OrcaSlicer)
* Maintainable upstream sync strategy
* Git-tracked handoff and review workflow

## Current Status

* `Prepare` currently ships as the working STL workspace on Android
* `Prepare` supports a multi-object plate session with append, select,
  selected-object transform, selected-object delete, and all-object slicing
* Imported Orca printer profiles now drive Orca-specific `Prepare` build
  plates: Android uses the generated Orca `bed_model` STL, rasterized
  `bed_texture` stickers/labels, and Orca-style grid when those assets exist,
  while keeping the old procedural plate only as the custom/missing-asset
  fallback
* `Slice` preflights transformed object bounds against the active printer
  printable volume before native slicing, so off-plate or over-height models
  fail immediately instead of waiting for a full native slice
* Home includes `Saved Projects` below `Profiles`; `Prepare` can save the
  current plate as a durable app-private project snapshot containing copied STL
  sources, transforms, a full active `ProfileStore` snapshot, save timestamp,
  and a real viewer-captured thumbnail. Saved clone plates preserve shared
  source identity for repeated objects so reopening a saved clone plate in the
  same app session can keep the native warm-slice path.
* Settings includes a Support tab with the free/open/ad-free project statement
  and an optional Ko-fi link that does not unlock app features
* The Android app logo now uses the `MobileSlicer.svg` orange-to-blue gradient
  mark in the launcher and Home header
* `Preview` currently renders sliced G-code through native Orca `libvgcode`
* The current real app boundary is:
  * Android owns the workspace shell, STL viewer, bed/grid shell, and Preview
    layer controls
  * the native wrapper owns slicing/export and G-code preview rendering through
    `libvgcode`
  * Preview layer scrubbing updates native visible-layer range live during drag,
    and release records the final range without triggering a filtered native
    reload
  * large Preview uses the normal native `libvgcode` path with exact selected
    ranges and an explicit limit message over the phone vertex cap
  * advanced STL features are tracked in `/README/ADVANCED_STL_FEATURES.md`;
    current implemented coverage is global fuzzy-skin Process config, while
    cut, painting, and multicolor still need project/facet state
* Workspace session truth now also includes:
  * portrait/landscape recreation keeps the active staged-model session
  * slicing after rotation works by reloading the retained staged STL into the
    recreated native engine before slicing
  * raw render views are still recreated normally
* The rejected viewer boundaries are now explicit:
  * Android-owned Preview geometry generation is the wrong long-term boundary
  * the attempted full native preview mesh export was too heavy for phone memory in its current form
* The working G-code-viewer path is documented in `/README/GCODE_VIEWER.md`;
  preserve its compact layer-ID and `EMoveType::Noop` path-break invariants
* The current phone G-code Preview fidelity contract is documented in
  `/README/GCODE_PREVIEW.md`; treat it as the authoritative source for exact
  range loading, vertex caps, and rejected Lite/striding behavior

## Important

All contributors MUST read:

* `/README/CORE_GOAL.md`
* `/README/ARCHITECTURE.md`
* `/README/KOTLIN_REFACTOR_MAP.md`
* `/README/PROOF_WORKFLOW.md`
* `/README/VERIFICATION.md`
* `/README/RELEASE_PERFORMANCE_HARDENING.md`
* `/README/OPTIMIZATION_NOTES.md`
* `/README/ADVANCED_STL_FEATURES.md`
* `/README/ORCA_MULTI_MATERIAL.md`
* `/README/GCODE_PREVIEW.md`
* `/README/ORCA_BUILD_PLATES.md`
* `/README/ORCA_CALIBRATIONS.md`
* `/README/SETTINGS_CHECKLIST.md`
* `/README/PRINTER_CONNECTION.md`
* `/RESUME_FROM_HANDOFF.md`
* `/STOP_FOR_THE_NIGHT.md`

Before making changes.

## Structure

* `android-app/` → Android UI
* `engine-wrapper/` → Native bridge layer
* `vendor/orcaslicer/` → Upstream fork
* `README/` → All documentation
* `.android-sdk/` → Project-local Android SDK copy
* `tools/adb` → Project-local ADB entrypoint

Device debugging and install runs for the physical Android target should use
wireless ADB. Prefer the current `_adb-tls-connect._tcp` endpoint advertised by
`tools/adb mdns services`, then install with `tools/adb -s <host:port> install
-r android-app/app/build/outputs/apk/debug/app-debug.apk`.

This repository enforces a documentation-first workflow.

Git is now part of the normal workflow for this project:

* use Git status/diff as the first source for exact file-change verification
* keep docs as the source of truth for milestone meaning and current state
* keep `README/SETTINGS_CHECKLIST.md` as the settings-coverage accountability surface
* when a run touches Orca-backed settings, update `README/SETTINGS_CHECKLIST.md` in the same run so status drift does not accumulate
* use `NEXT_PROMPT.md` as the operational next-step file
* use the GitHub `origin` remote for backup and synchronization when pushing verified local commits

Role ownership:

* the project supervisor owns `NEXT_PROMPT.md` and next-step framing
* the coder owns execution, evidence gathering, and verified-result doc updates for the assigned run
* the coder must not write, rewrite, draft, or suggest `NEXT_PROMPT.md` text unless explicitly acting in the supervisor role
* truth-bearing files must not be left untracked when they change proof wording, UI truth wording, or the native slice-input path
* `README/SETTINGS_CHECKLIST.md` must stay aligned with the real app/config surface:
  * use app-facing `Config only - Waydroid` / `Config only` / `Device tested` / `Missing`
  * use `Parent surfaced, subset only` when Orca parent coverage is partial
  * keep Orca-advanced visibility tagging in that file only
