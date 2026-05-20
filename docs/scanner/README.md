# MobileSlicer Scanner Implementation Plan

MobileSlicer Scanner is a real in-app 3D scanner for printable objects. It is
not an import shortcut, a camera demo, or a fake mesh generator. The scanner must
capture measurable evidence, reconstruct real geometry, validate the result, and
only hand a model to the slicer when the model earns that handoff.

The target is local printable scanning first, using photogrammetry and local AI.
Cloud reconstruction is optional and paid, used for harder objects, higher
quality, larger jobs, or pro printable output. Cloud must never be required for
the app to have a meaningful scanner, but cloud can be the higher-success path.

No fake meshes. No placeholder cubes. No synthetic "scan complete" state. No
workspace handoff unless a real metric mesh passes validation.

## Product Promise

Do not promise universal "perfect 1:1" scans in product language. That sounds
like a metrology claim, and a phone camera cannot guarantee that for every
material, object class, lighting condition, and tolerance.

The internal ambition is exact real-world scale. The written product standard is:

```text
MobileSlicer Scanner produces validated scan packages and only hands metric
meshes to the slicer when scale, coverage, topology, provenance, and
printability checks pass.
```

Accuracy must be reported as tolerance classes, not vibes.

| Class | Intended use | Requirement |
| --- | --- | --- |
| Visual | Preview, AR, textured display | Looks coherent; not dimensionally trusted |
| General Mesh | Non-critical model export | Loads cleanly; reasonable topology |
| Consumer Printable | Decorative or forgiving prints | Slicer load passes; scale confidence passes V1 gate |
| Calibrated Printable | Better printable output | Verified scale source; stricter evidence/provenance gates |
| Fit-Critical | Future only | Requires proven tighter tolerances on supported object classes |

V1 printable consumer gate:

```text
Known block dimensions within <= 1.5 mm or <= 1.5%, whichever is larger
Calibration marker scale error <= 1.0%
Repeatability tracked across 3 captures
```

Future fit-critical gate:

```text
<= 0.5 mm on supported object classes only
No fit-critical marketing until the benchmark data proves it
```

## Product Modes

| Mode | Runs where | Cost | Output | Purpose |
| --- | --- | --- | --- | --- |
| Local Printable Scan | Phone | Free | Metric STL/3MF only if validation passes | Local-first printable scanner |
| Cloud Advanced Scan | Cloud GPU | Credits | General mesh plus visual outputs | Better reconstruction and textured preview |
| Cloud Pro Printable Scan | Cloud GPU | More credits | Validated metric STL/3MF plus reports | Best print-focused scanner |

Local Printable Scan must attempt a real local reconstruction. If validation
fails, it fails honestly and keeps the scan package for retry, review, or cloud
upgrade.

Cloud Advanced Scan does not hand off to the slicer by default. It can display
and export general or visual outputs, but slicer workspace handoff requires the
same metric validation used by Cloud Pro Printable Scan.

## Model Types

| Type | AI can invent geometry? | Use |
| --- | --- | --- |
| Metric Mesh | No silent invention. Repairs must be marked and bounded. | Printing, dimensions, fit checking |
| Visual Mesh | Yes, if clearly labeled visual-only. | AR, product preview, textured display |

Metric and visual outputs must never be confused. The slicer workspace accepts
only validated metric outputs.

## Mesh Provenance

Every metric mesh must carry machine-readable provenance. This is how the app
enforces "no fake meshes" after reconstruction and repair.

Face or region confidence classes:

| Class | Meaning | Slicer eligibility |
| --- | --- | --- |
| measured_high | Supported by multiple sharp calibrated views | Allowed |
| measured_low | Supported by weak texture, shallow angle, blur, or low depth confidence | Allowed only below threshold |
| repaired_small | Conservative hole fill below printable threshold | Allowed only below threshold |
| repaired_large | Large filled region, not dimensionally trustworthy | Blocks handoff above threshold |
| visual_only | AI-generated visual geometry | Never allowed in metric mesh |

Required provenance output:

```json
{
  "face_confidence_schema": 1,
  "confidence_classes": {
    "measured_high": "Supported by multiple sharp frames and calibrated scale",
    "measured_low": "Supported by weak texture, shallow angle, blur, or low depth confidence",
    "repaired_small": "Small conservative repair below printable threshold",
    "repaired_large": "Large filled region, not dimensionally trustworthy",
    "visual_only": "Generated for visual mesh only, not allowed in metric mesh"
  }
}
```

The scanner viewer should show a confidence preview:

- green: measured high
- yellow: measured low
- orange: repaired
- red: not printable or visual-only

For STL, provenance lives in sidecar reports. For 3MF, embed report metadata
where practical. The workspace must read the report before accepting the model.

## Non-Negotiables

- No fake meshes.
- No proxy cubes, fixture STLs, placeholder scans, or fallback geometry.
- No "reconstruction complete" state unless a real reconstruction pipeline
  produced a mesh.
- No AI-inferred geometry inside a metric mesh unless provenance and uncertainty
  are recorded.
- No visual mesh handoff to the slicer as a printable model.
- No Cloud Advanced slicer handoff unless printable validation also passes.
- No public cloud launch without server-side credit ledger, Play Billing
  verification, Play Integrity gate, per-job runtime caps, provider spend caps,
  and global cloud kill switches.
- No paid production use of unclear or non-commercial model/tool licenses.
- No release exposure until capture, reconstruction, validation, and device tests
  pass on the required matrix.

## Device Tiers

Scanner must degrade honestly by device capability.

| Tier | Requirement | Behavior |
| --- | --- | --- |
| Minimum | Android 10+, 4 GB RAM, decent rear camera | Guided capture, package export, cloud-ready package, local attempt only if feasible |
| Recommended | Android 12+, 6-8 GB RAM, ARCore support | Local AI masks, coverage guidance, local printable attempt |
| Best | Android 13+, 8-12 GB RAM, ARCore Depth, strong GPU/NPU | Best local reconstruction, depth assist, faster validation |
| Weak devices | Low RAM or weak GPU | Disable local reconstruction; allow capture/package/cloud path only |

ARCore is optional, not required. Photo capture must work without ARCore.
ARCore pose, depth, raw depth, confidence, and point cloud are accelerators, not
the foundation of truth.

Implementation decision:

```text
Minimum supported scanner OS: Android 10
Recommended scanner target: Android 12+
Best scanner target: Android 13+ with ARCore Depth and strong GPU/NPU
ARCore requirement: optional
```

Do not block the scanner on ARCore. The base scanner is photo-based calibrated
photogrammetry. ARCore improves guidance, pose priors, scale confidence, depth
support, and capture diagnostics when available.

## Capture Profiles

Use multiple capture profiles, not one overloaded camera path.

| Profile | API | Purpose | Tradeoff |
| --- | --- | --- | --- |
| High-res Photo Mode | CameraX/Camera2 | Best image detail for photogrammetry | Weaker synchronization with AR pose/depth |
| AR Synchronized Mode | ARCore Frame API | RGB, pose, depth, confidence, point cloud, tracking | Lower/normal RGB quality and device-dependent depth |
| Hybrid Session | CameraX/Camera2 plus ARCore assist | Best practical scanner path | More complex scheduling and metadata alignment |

Implementation order:

```text
1. CameraX/Camera2 high-res capture first.
2. ARCore assist second.
3. Hybrid session third.
```

Reason:

- high-resolution keyframes are the best foundation for photogrammetry detail
- ARCore synchronized frames are the best source of pose/depth evidence
- hybrid mode should combine both only after each profile works independently

## Capture UX

The scanner should guide users like a serious capture tool.

1. Select scan type.
2. Show object eligibility warnings.
3. Print or select calibration kit.
4. Verify printed scale if printable mode is selected.
5. Place object on marker mat/riser.
6. Tap object.
7. App locks an object mask as soft evidence.
8. Capture mid ring.
9. Capture low ring.
10. Capture high ring.
11. Capture top/detail shots.
12. Capture underside/two-sided pass if required.
13. Show readiness by category.
14. Run local reconstruction or offer cloud quote.
15. Show mesh preview, confidence preview, and printability report.
16. Import STL/3MF only if metric validation passes.

Object eligibility warnings before capture:

| Good targets | Difficult targets |
| --- | --- |
| matte plastic | shiny metal |
| textured surfaces | glass |
| visible edges | transparent plastic |
| stable rigid objects | plain white objects |
| enough space to walk around | black glossy objects |
| object fits on marker mat | thin wires, hair-like features |
| object can be flipped or raised safely | flexible cloth or moving objects |

Plain guidance during capture:

- Move slower.
- Too blurry.
- Object is too shiny.
- Lighting is uneven.
- Need more views of the back.
- Need more high-angle views.
- Need more low-angle views.
- Scale markers not visible.
- Add underside photos.
- Object merged with table.
- Mask is uncertain around thin features.
- Local reconstruction failed.
- Printable handoff blocked by weak scale.
- Printable handoff blocked by repaired geometry.

Readiness must be split by cause:

```text
capture_readiness
scale_readiness
coverage_readiness
material_readiness
printable_readiness
```

Do not hide the reason for failure behind one score.

## Calibration Kit

The calibration kit is central for printable scans. Printable mode must require
a reliable scale source.

Current implementation decision:

Calibration is not abandoned. It is deferred as a required printable validation
gate while the team finishes the capture, local AI, and reconstruction core. The
app may let users capture without a marker mat during this phase, but it must not
claim calibrated printable scale from those captures. Any slicer-ready metric
handoff still needs a verified scale source before release.

MobileSlicer should provide printable assets:

- marker mat PDF
- outer marker ring
- 100 mm scale bar
- object riser
- matte turntable disc
- underside support jig
- phone stand guides
- calibration cube/block

Current app-generated marker mat:

```text
mobile_slicer_marker_mat_a4.svg
mobile_slicer_marker_mat_a4_layout.json
mobile_slicer_marker_mat_us_letter.svg
mobile_slicer_marker_mat_us_letter_layout.json
README.txt
```

On device, the scanner writes these files to the public Downloads folder when
the user taps `Generate marker mat`:

```text
Downloads/MobileSlicer/scanner-marker-mat/
```

If Android blocks public Downloads writes, the app falls back to its app-specific
Documents/scanner-marker-mat folder and shows that path.

The SVG is the printable guide marker mat. Choose the file that matches the
physical paper loaded in the printer:

| File | Paper |
| --- | --- |
| `mobile_slicer_marker_mat_a4.svg` | A4, 210 x 297 mm |
| `mobile_slicer_marker_mat_us_letter.svg` | US Letter, 8.5 x 11 in / 215.9 x 279.4 mm |

Print at 100% scale with printer page scaling disabled. Do not use fit-to-page,
shrink-to-fit, borderless scaling, or any automatic page scaling. The object
goes inside the ring/field of AprilTag markers, not on top of every marker. The
markers must stay visible around the object from multiple camera angles. The
100 mm scale bar on the sheet must be measured and entered in the app before a
scan can qualify for printable scale.

Use AprilTag as the primary printable marker system. Support ArUco/ChArUco as a
secondary calibration/debug system.

Marker decision:

```text
Primary printable mat: AprilTag grid/ring plus measured scale bar
Calibration/debug board: ChArUco/ArUco support
```

Reason:

- AprilTag is the primary choice for robust marker pose and printable scale proof
- ChArUco/ArUco is useful for OpenCV-based camera calibration, debugging, and
  fallback experiments
- both should share the same calibration report model

Do not put every marker under the object; the object will hide them. Markers must
remain visible around the object from multiple angles.

Printed markers are not automatically accurate because printers can scale PDFs.
Printable mode requires scale verification:

1. User prints the marker mat.
2. User measures the printed 100 mm scale bar with calipers or a ruler.
3. User enters measured length.
4. App stores measured scale correction.
5. App warns if measured length differs from expected beyond tolerance.

Scale sources, strongest first:

| Source | Use |
| --- | --- |
| Verified marker mat | Preferred printable scale |
| Turntable diameter | Good for repeatable setups |
| Known object dimension | Useful fallback when entered carefully |
| Manual measurement | Useful but user-error prone |
| ARCore depth | Assist only, not exact scale by itself |

Large-object calibration path:

Objects do not have to fit on one sheet of paper. The single A4/Letter mat is
only the small tabletop path. Larger objects need a later large-object workflow:

- individual AprilTag cards around the object perimeter
- multiple marker sheets on the table/floor plane
- a longer verified scale reference, such as 200 mm, 500 mm, or 1 m
- optional measured distance between specific tags
- app-side marker visibility checks from multiple sides
- printable reconstruction blocked if scale/marker evidence is weak

This large-object calibration workflow is deferred with the rest of calibration.
It must be implemented before claiming accurate printable scale for objects that
do not fit inside the standard marker mat.

Printable handoff is blocked when:

- no reliable scale source exists
- marker scale is unverified
- marker observations are mostly from one angle
- marker reprojection error is too high
- object was moved without a valid alignment method
- scale confidence is below threshold

Calibration metadata:

```json
{
  "marker_type": "apriltag",
  "marker_size_mm": 40.0,
  "printed_scale_bar_expected_mm": 100.0,
  "printed_scale_bar_measured_mm": 99.2,
  "scale_correction": 1.0080645,
  "scale_confidence": 0.91,
  "marker_reprojection_error_px": 1.6,
  "detected_markers": [
    {
      "id": 12,
      "frame_id": "000010",
      "corners_px": [[100, 100], [180, 100], [180, 180], [100, 180]]
    }
  ]
}
```

## Underside And Two-Sided Scans

Underside capture is required for many printable objects, but flipping the object
changes the coordinate system. Treat underside scanning as its own workflow.

Supported alignment methods:

| Method | Requirement |
| --- | --- |
| marker_alignment | Markers visible before and after flipping |
| icp_overlap | Enough overlapping object geometry between passes |
| turntable_jig | Known fixture geometry and repeatable placement |
| unknown | Not acceptable for printable handoff |

Two-sided scan metadata:

```json
{
  "object_moved_during_session": true,
  "alignment_method": "marker_alignment",
  "alignment_confidence": 0.84,
  "pass_count": 2
}
```

If `alignment_method` is `unknown`, Cloud Pro Printable and Local Printable
handoff are blocked.

## Frame Quality Scoring

Most bad scans are caused by bad capture. Reject bad frames before local compute
or cloud spend.

Metrics:

- blur score
- exposure score
- clipped highlights
- object mask area
- overlap with accepted frames
- new coverage gain
- tracking state if ARCore exists
- depth coverage if available
- marker visibility
- marker reprojection error
- scale confidence
- material risk score
- forced capture flag

Initial thresholds:

```text
Clipped highlights: reject > 5% inside object mask
Object mask area: reject < 8%
Overlap: reject < 35%
Coverage gain: reject < 2% late in capture
Depth coverage: require 30%+ when depth mode is mandatory
Tracking: reject if not tracking
Marker observations: require multiple angles for printable mode
```

Readiness score:

```text
0.30 angle coverage
0.20 image sharpness
0.15 scale confidence
0.15 mask consistency
0.10 lighting score
0.10 depth/pose support when available
```

Forced frames are allowed only as evidence, not as full-quality accepted frames.
They must be tagged separately and must not inflate readiness.

## Local AI Policy

Local AI improves capture and local reconstruction. It does not replace measured
geometry.

Local AI jobs:

- object segmentation
- background/table masking
- blur, glare, exposure detection
- frame rejection
- coverage guidance
- monocular depth prior
- material risk classification
- mask consistency scoring
- hole/defect classification
- confidence heatmap
- repair suggestions

Mask policy:

| Output | Mask behavior |
| --- | --- |
| Metric reconstruction | Original RGB preserved; masks used as soft weights |
| Visual reconstruction | Masks may be more aggressive |
| Thin structures | Protect when detected across frames |
| Low-confidence mask edge | Dilate/soften instead of deleting geometry |

Do not let local AI masks hard-delete real metric geometry. A bad mask can cut
off handles, hooks, brackets, tabs, wires, or thin edges.

Local AI stack decision:

| Task | Tool |
| --- | --- |
| Object mask | MediaPipe Interactive Segmenter |
| Primary Android inference | LiteRT |
| Secondary inference path | ONNX Runtime Mobile |
| PyTorch mobile path | ExecuTorch only if a selected model justifies it |
| Optional depth prior | Depth Anything V2 Small only if license remains acceptable |
| Custom QA | Small classifier trained on scan failures |

Use a multi-model local AI stack. Do not look for one magic local model.

Implementation priority:

```text
1. LiteRT runtime foundation.
2. MediaPipe Interactive Segmenter for tap-to-object masks.
3. Custom scan-quality classifier for blur, glare, material risk, and failure prediction.
4. Depth Anything V2 Small as optional depth prior only after license/runtime validation.
5. ONNX Runtime Mobile only for models that are materially better or easier to ship there.
6. ExecuTorch only for a PyTorch model that is worth the extra runtime surface.
```

Depth Anything V2 Small can be considered only while its Apache-2.0 licensing is
confirmed. Larger non-commercial variants are blocked from paid/commercial paths
unless separately licensed.

Local AI depth is a prior, preview aid, or weak evidence source. It is not metric
truth.

## ARCore Assist

ARCore must be optional. Use it when available for synchronized evidence:

- camera pose
- tracking state
- image intrinsics
- depth image
- raw depth image
- raw depth confidence
- point cloud
- timestamps

Preserve all available camera metadata:

- intrinsics
- distortion
- focal length
- exposure time
- ISO
- focus distance
- white balance mode
- lens facing
- sensor orientation

High-resolution CameraX/Camera2 capture and ARCore synchronized capture have
tradeoffs.

| Profile | Use |
| --- | --- |
| AR synchronized profile | Lower/normal-res RGB, pose, depth, confidence, point cloud |
| High-res photo profile | Higher image quality, weaker synchronization, possible depth tradeoff |
| Hybrid profile | ARCore guidance/pose/depth plus high-res keyframes when needed |

For Cloud Pro Printable, prefer synchronized evidence over raw megapixels unless
the object has tiny details that require high-resolution photos.

## Scan Package Format

Every scan must be exportable and reproducible. The same package should be usable
by local reconstruction, cloud reconstruction, future desktop reconstruction, and
debug tools.

```text
scan_package.zip
  manifest.json
  package_hashes.json
  frames/
    000001.jpg
    000001_mask.png
    000001_depth16.png
    000001_confidence.png
    000001_meta.json
  calibration/
    device.json
    scale_markers.json
    camera_profile.json
    two_sided_alignment.json
  thumbnails/
  diagnostics/
    coverage_map.json
    rejected_frames.json
    device_capabilities.json
    material_risk.json
  local_preview/
  reports/
```

Manifest:

```json
{
  "scan_id": "scan_123",
  "schema_version": 1,
  "created_at": "2026-05-08T12:00:00Z",
  "mode": "local_printable",
  "units": "millimeters",
  "frame_count": 84,
  "accepted_frame_count": 72,
  "forced_frame_count": 3,
  "rejected_frame_count": 9,
  "has_arcore_poses": true,
  "has_depth": true,
  "has_masks": true,
  "scale_source": "verified_marker_mat",
  "object_moved_during_session": false,
  "requested_outputs": ["stl", "3mf"]
}
```

Per-frame metadata:

```json
{
  "frame_id": "000001",
  "timestamp_ns": 123456789,
  "image": "frames/000001.jpg",
  "image_sha256": "abc123",
  "mask": "frames/000001_mask.png",
  "mask_sha256": "def456",
  "depth16": "frames/000001_depth16.png",
  "depth_sha256": "ghi789",
  "confidence": "frames/000001_confidence.png",
  "pose_world_from_camera": [1.0, 0.0, 0.0, 0.1],
  "intrinsics": {
    "fx": 1430.2,
    "fy": 1431.1,
    "cx": 960.0,
    "cy": 540.0,
    "width": 1920,
    "height": 1080
  },
  "distortion": {
    "model": "brown_conrady",
    "coefficients": [0.0, 0.0, 0.0, 0.0, 0.0]
  },
  "camera": {
    "lens_facing": "back",
    "focal_length_mm": 5.43,
    "exposure_time_ns": 8333333,
    "iso": 160,
    "focus_distance": 0.72,
    "white_balance_mode": "auto"
  },
  "quality": {
    "blur_score": 182.4,
    "exposure_score": 0.93,
    "depth_coverage": 0.61,
    "accepted": true,
    "forced_capture": false
  }
}
```

Package acceptance:

- package can be loaded twice and produce identical manifest validation
- all referenced files exist
- all file hashes match
- package schema version is supported
- old schema versions migrate or fail cleanly
- forced frames are not counted as full-quality frames
- workspace handoff is impossible from raw Local Capture state

## Android Implementation Modules

The current scanner implementation has been removed. Rebuild from clean modules
rather than polishing the deleted prototype.

Initial app code can live under:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/
```

Keep boundaries explicit from the start:

```text
scanner-capture
scanner-quality
scanner-calibration
scanner-local-ai
scanner-reconstruction
scanner-packaging
scanner-cloud
scanner-billing
scanner-reports
scanner-viewer
scanner-workspace-handoff
```

Core Kotlin data model:

```kotlin
data class ScanFrame(
    val id: String,
    val timestampNs: Long,
    val rgbPath: String,
    val rgbSha256: String,
    val maskPath: String?,
    val maskSha256: String?,
    val depth16Path: String?,
    val depthSha256: String?,
    val depthConfidencePath: String?,
    val poseWorldFromCamera: FloatArray?,
    val intrinsics: CameraIntrinsicsData?,
    val distortion: CameraDistortionData?,
    val lensFacing: String,
    val focalLengthMm: Float?,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val focusDistance: Float?,
    val whiteBalanceMode: String?,
    val width: Int,
    val height: Int,
    val quality: FrameQuality,
    val forcedCapture: Boolean
)

data class CameraIntrinsicsData(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val imageWidth: Int,
    val imageHeight: Int
)

data class CameraDistortionData(
    val model: String,
    val coefficients: FloatArray
)

data class FrameQuality(
    val blurScore: Float,
    val exposureScore: Float,
    val overlapScore: Float,
    val trackingGood: Boolean?,
    val depthCoverage: Float?,
    val markerVisibility: Float,
    val scaleConfidence: Float,
    val materialRisk: Float,
    val accepted: Boolean
)
```

## Local Printable Reconstruction

Local printable reconstruction is the hardest requirement. It must still be the
core direction.

There are three possible local reconstruction paths:

| Option | Description | Decision |
| --- | --- | --- |
| A | Port a full photogrammetry/MVS stack to Android | Too heavy for first path |
| B | Build a small object-focused photogrammetry pipeline from scratch | Too much R&D risk for first path |
| C | Hybrid photogrammetry with soft AI masks, calibration, and optional ARCore priors | Chosen path |

Chosen local path:

```text
high-res keyframes
  + verified calibration
  + soft AI masks
  + feature matching
  + ARCore pose/depth priors when available
  -> local pose refinement
  -> dense reconstruction
  -> conservative surface reconstruction
  -> mesh cleanup and repair
  -> provenance classification
  -> print validation
  -> STL/3MF handoff only if valid
```

Local reconstruction must have:

- memory cap
- time cap
- frame cap
- battery/thermal awareness
- progress reporting
- cancel support
- reproducible package input
- failure states
- no fake mesh generation

Local rough preview is allowed only if labeled as preview. It cannot enter the
slicer unless it passes metric validation.

The local scanner must be built to produce printable output, not just a preview.
However, every local failure state must be honest:

```text
capture incomplete
scale unverified
not enough angle coverage
material unsuitable
local compute budget exceeded
reconstruction failed
mesh not printable
slicer handoff blocked
```

## Cloud Upload

Cloud upload must be package-first and backend-validated.

Add:

- package schema version
- manifest hash
- per-file SHA-256
- declared compressed size
- declared decompressed size
- frame count by pass
- accepted frame count
- forced frame count
- rejected frame count
- camera model summary
- device capability summary
- quote expiry
- chunked uploads
- single-use upload URLs

Do not let the backend trust the app's quality scores. The backend recomputes
cheap checks:

- decode images
- verify dimensions
- verify hashes
- verify frame count
- verify zip structure
- reject path traversal
- reject zip bombs
- reject huge decompressed size
- recompute blur score
- recompute exposure clipping
- recompute marker visibility
- recompute mask coverage
- recompute basic coverage

Credits are reserved only after server package validation passes and before queue
admission.

Quote response:

```json
{
  "quote_id": "quote_123",
  "expires_at": "2026-05-08T12:15:00Z",
  "tier": "printable",
  "credits_required": 8,
  "max_gpu_seconds": 1800,
  "max_frames": 180,
  "max_zip_mb": 1500
}
```

Job creation requires:

- valid quote
- valid uploaded package
- valid package hash
- enough credits
- quota available
- global budget available
- provider budget available
- Play Integrity accepted

## Cloud Reconstruction

Cloud is for higher success rate and harder jobs. Production cloud
reconstruction must be license-approved before paid launch.

Pipeline:

1. Validate package.
2. Recompute quality metrics.
3. Select keyframes.
4. Build image overlap graph.
5. Detect calibration markers.
6. Estimate initial scale.
7. Apply masks as soft weights.
8. Run SfM and bundle adjustment.
9. Refine camera intrinsics and distortion.
10. Build dense point cloud.
11. Fuse ARCore depth only where confidence is high.
12. Use approved AI priors only when needed.
13. Build mesh.
14. Remove background/table/floor.
15. Repair mesh conservatively.
16. Preserve sharp edges where supported by evidence.
17. Compute per-face source confidence.
18. Bake texture after final geometry.
19. Validate metric output.
20. Validate slicer load.
21. Export outputs.
22. Generate report.

Candidate tools:

| Tool/model | Use | Production status |
| --- | --- | --- |
| COLMAP | SfM, pose refinement, sparse/dense baseline | Good candidate; review third-party dependency licenses |
| Open3D | Point cloud and mesh processing | Good candidate |
| CGAL | Repair/topology algorithms | Package-by-package license review required |
| OpenMVS | Dense reconstruction/texturing | Blocked until AGPL impact is accepted or alternative chosen |
| VGGT | AI geometry/camera/depth prior | Commercial checkpoint only after approval |
| SAM 3D Objects | Visual mesh experiments | Visual only until license/output policy is cleared |
| MASt3R/DUSt3R-style models | AI reconstruction | Blocked unless separately licensed for commercial use |
| Depth Anything V2 Small | Depth prior | Allowed only while Apache-2.0 status remains confirmed |
| Larger Depth Anything variants | Depth prior | Blocked unless commercial permission exists |

M6 initial production target should be COLMAP plus Open3D plus conservative mesh
validation. Add commercial AI geometry only after legal review and benchmark
review.

## Output Files

Local Printable and Cloud Pro Printable output:

```text
model_metric.stl
model_metric.3mf
scan_report.json
repair_report.json
provenance_report.json
coverage_heatmap.png
uncertainty_heatmap.png
confidence_preview.glb
```

Cloud Advanced visual output may include:

```text
model_visual.glb
model_visual.obj
model_visual.mtl
model_visual_texture.png
visual_report.json
```

Output naming must make the difference obvious:

- `model_metric.*` means measured/validated for print.
- `model_visual.*` means appearance-focused and not slicer-trusted.

## Print Validation And Slicer Handoff

A mesh can enter the workspace only if validation passes. Topology alone is not
enough. Watertight and manifold do not prove dimensions are right.

Validation groups:

Topology:

- watertight
- manifold
- finite bounds
- no self-intersections
- sane normals
- disconnected island checks
- triangle budget

Scale:

- scale confidence
- scale source
- marker reprojection error
- scale covariance
- units are millimeters

Evidence:

- measured surface area percent
- weak surface area percent
- repaired surface area percent
- largest repaired region
- source frame count per mesh region
- observed angle diversity per region
- shiny/textureless risk
- underside alignment confidence

Slicer:

- native slicer load succeeds
- units are interpreted correctly
- mesh bounds are sane
- no impossible zero-volume output
- triangle count keeps workspace responsive

Hard handoff blocks:

```text
mesh is not watertight
mesh is not manifold
slicer load fails
scale confidence < threshold
marker reprojection error > threshold
repaired surface area > threshold
largest repaired hole > threshold
repaired_large exists above threshold
alignment_method == unknown for two-sided scan
visual_only geometry appears in metric mesh
```

Initial thresholds should be tuned by benchmark data, but V1 must define them
before release.

Printability report:

```json
{
  "recommended_for_printing": true,
  "topology": {
    "watertight": true,
    "manifold": true,
    "self_intersections": 0,
    "disconnected_islands": 0
  },
  "scale": {
    "scale_confidence": 0.88,
    "scale_source": "verified_marker_mat",
    "marker_reprojection_error_px": 1.6
  },
  "evidence": {
    "measured_surface_area_percent": 91.2,
    "weak_surface_area_percent": 6.7,
    "repaired_surface_area_percent": 2.1,
    "largest_repaired_hole_mm": 8.4
  },
  "slicer": {
    "load_test_passed": true,
    "units": "mm",
    "triangle_count": 842311
  },
  "warnings": [
    "Underside partially repaired",
    "Glossy region has low surface confidence"
  ]
}
```

Workspace handoff contract:

```text
Workspace accepts scanner output only when:
  model_metric.stl or model_metric.3mf exists
  printability report exists
  provenance report exists
  recommended_for_printing is true
  slicer load test passed
  scale source is valid
  units are millimeters
  visual-only geometry is absent
```

Anything else stays in scanner preview/export and cannot enter the slicer as a
printable model.

## Billing And Credits

Use credits, never unlimited scans. Backend owns credits. The app never tells the
backend how many credits the user has.

V1 products are one-time consumable scan packs only:

```text
Small Scan Pack: $2.99, 3 credits
Maker Pack: $7.99, 10 credits
Pro Pack: $19.99, 30 credits
```

No monthly subscription in V1. A future Monthly Supporter product must be a real
subscription with fixed monthly credits, never unlimited scans.

Credit costs:

```text
Cloud Advanced: 3 credits
Cloud Pro Printable: 8 credits
```

Do not allow high-cost GPU classes unless margin checks pass:

```text
Cloud Advanced cannot use H100-class GPUs by default.
Cloud Pro Printable can use H100-class GPUs only if margin check passes.
GPU provider selection must be tier-aware.
```

Backend owns:

- purchases
- purchase verification
- credit grants
- credit reservations
- job consumption
- refunds
- negative balances
- abuse decisions

If a purchase is refunded or voided after credits are used:

```text
mark account negative
block future cloud jobs
require a new purchase to clear balance
record fraud/reversal event
```

## Bankruptcy Protection

Required before public cloud launch.

Per-job caps:

- max GPU seconds
- max CPU seconds
- max frames
- max zip size
- max decompressed size
- max retries
- max output size
- max intermediate storage
- worker self-timeout

Per-user caps:

- one active GPU job
- queue cap
- daily cap
- monthly cap
- failed-job throttle
- pro job cap
- account-age restrictions for trials

Global caps:

- daily GPU budget
- monthly GPU budget
- cloud kill switch
- provider kill switch
- max concurrent workers
- max queued GPU jobs
- provider API quota cap
- provider spending cap

Prototype budget:

```text
Daily GPU budget: $25
Monthly GPU budget: $300
Disable new jobs when hit: true
```

Credit liability controls:

- disable credit purchases when cloud capacity is low
- disable credit purchases when monthly budget is near exhaustion
- disable credit purchases when provider quota is exhausted
- disable credit purchases when average queue time exceeds threshold
- estimate outstanding prepaid credit liability
- scale monthly budget with paid credit sales before public launch

Suggested field:

```text
credit_liability_usd_estimate
```

This prevents selling more credits than the system can process.

## Abuse Protection

Before cloud job:

- Play Integrity token
- backend token verification
- official signed Play build required for paid cloud jobs
- purchase verification
- user risk score
- device risk score
- IP rate limits
- device fingerprint limits
- upload rate limits
- zip bomb checks
- virus scan
- upload size checks
- repeated failure throttle

Open-source policy:

```text
Local app can be open source.
Paid cloud jobs require the official signed Play build.
Self-built or sideloaded builds can use Local Capture, but not paid cloud
reconstruction unless separately authorized.
```

Job tokens:

- bound to `user_id`
- bound to `scan_id`
- bound to integrity verdict
- expire quickly
- usable once

Upload/download URLs:

- signed
- short-lived
- one package per upload URL
- output URLs expire quickly

## Backend API

Design backend contracts now. Defer real backend, billing, GPU workers, and
admin implementation until local capture/reconstruction proves the package and
report contracts.

Build now:

```text
scan package schema
printability report schema
provenance report schema
workspace handoff contract
cloud API interface definitions
billing data model docs
credit ledger docs
upload validation rules
job state model
```

Defer until needed:

```text
real backend service
real Play Billing integration
real GPU workers
real cloud reconstruction
admin dashboard
provider spend automation
```

Endpoints:

```text
POST /v1/auth/session
POST /v1/integrity/verify

GET  /v1/billing/products
POST /v1/billing/google/verify
POST /v1/billing/google/rtdn

POST /v1/scans
POST /v1/scans/{scan_id}/upload-url
POST /v1/scans/{scan_id}/complete-upload

POST /v1/jobs/quote
POST /v1/jobs
GET  /v1/jobs/{job_id}
GET  /v1/jobs/{job_id}/outputs
POST /v1/jobs/{job_id}/cancel

GET  /v1/users/me/credits
GET  /v1/users/me/limits
```

Quote response:

```json
{
  "quote_id": "quote_123",
  "tier": "printable",
  "credits_required": 8,
  "max_gpu_seconds": 1800,
  "estimated_wait_seconds": 60,
  "expires_at": "2026-05-08T12:15:00Z",
  "limits": {
    "max_frames": 180,
    "max_zip_mb": 1500
  },
  "warnings": [
    "Glossy object detected",
    "Underside coverage appears incomplete"
  ]
}
```

## Database

Use Postgres.

Tables:

```text
users
devices
purchases
credit_ledger
scans
scan_frames
scan_jobs
job_outputs
quota_counters
global_spend
provider_spend
credit_liability
abuse_events
deletion_audits
```

Required ledger events:

```text
purchase_granted
job_reserved
job_consumed
job_refunded
admin_adjustment
fraud_reversal
negative_balance_created
negative_balance_cleared
```

## Privacy

Scans can contain photos of homes, desks, documents, faces, addresses, and
proprietary parts. Treat scan packages as private user data.

Policy:

- local scans stay local unless user uploads
- show cloud upload privacy warning
- strip EXIF GPS before upload unless explicitly required
- do not log image contents
- do not log personal image filenames
- encrypt object storage
- encrypt sensitive scan metadata where practical
- cloud input deleted after 7 days by default
- cloud output deleted after 30 days by default
- failed-job files deleted
- intermediate reconstruction files deleted
- immediate delete button
- deletion audit record
- signed URLs expire
- no training without explicit opt-in
- no hidden training use of scans

Cloud upload warning:

```text
Your scan package contains photos of the object and surrounding area. Only upload
if you are comfortable sending those images to cloud processing.
```

## Licensing

Must review before committing to the cloud stack.

Known cautions:

- Depth Anything V2 Small is acceptable only if Apache-2.0 remains true.
- Larger Depth Anything V2 models are non-commercial unless separately licensed.
- MASt3R is non-commercial unless separately licensed.
- DUSt3R-style models require license review before any paid path.
- VGGT original checkpoint is non-commercial; commercial checkpoint needs
  approval.
- CGAL package licenses vary; some packages may require commercial licensing.
- OpenMVS is blocked for production until AGPL impact is accepted or avoided.
- SAM 3D license must be reviewed before shipping.
- COLMAP dependency licenses must be reviewed, not only COLMAP itself.

Commercial production rule:

```text
If license status is unclear, the tool/model is blocked from paid production.
```

## Verification Dataset

Create physical fixtures:

- calibration cube
- dimensional block
- 100 mm scale bar
- figurine
- mechanical bracket
- shiny object
- dark object
- thin object
- underside-heavy object
- textureless object
- transparent object
- symmetric object
- matte white object
- object with small holes
- object with overhangs

Minimum physical fixture set before trusting reconstruction:

```text
calibration cube
dimensional block
100 mm scale bar
matte figurine
mechanical bracket
dark object
glossy object
thin object
textureless object
object with underside
```

Fixture protocol:

```text
1. Measure object with calipers or a ruler.
2. Scan the object at least 3 times.
3. Compare reconstructed dimensions.
4. Compare repeatability.
5. Check slicer load.
6. Check printability report.
7. Record failure reason.
```

Device matrix:

- Pixel flagship
- Samsung flagship
- Samsung midrange
- OnePlus or Xiaomi device
- low-end Android device
- ARCore device without Depth API
- non-ARCore device

Material matrix:

- matte colored plastic
- matte white object
- glossy object
- dark object
- transparent object
- thin object
- symmetric object
- featureless object
- mechanical bracket
- organic figurine
- object with underside

Metrics:

- dimension error vs calipers
- marker reprojection error
- scale confidence vs actual error
- repeatability over three scans
- reconstruction time
- memory use
- thermal behavior
- battery impact
- GPU seconds
- cost
- slicer load success
- printability report correctness
- false pass rate
- false fail rate

Output gates:

| Output class | Gate |
| --- | --- |
| Visual Mesh | Looks complete from normal viewing angles |
| General Mesh | Loads in viewer, reasonable topology, no major junk |
| Printable Mesh | Slicer loads, watertight, scaled, report passes |
| Fit-Critical Mesh | Future only after tighter tolerance proof |

## Milestones

M0: Scanner foundation reset

- Keep current scanner code removed.
- Keep this plan as the scanner contract.
- Scanner may be visible in isolated local/dev builds.
- Public/release exposure remains blocked until milestones pass.
- Workspace handoff remains blocked unless metric validation passes.
- No fake mesh path exists.

M1: Calibrated local capture package

- CameraX/Camera2 high-res capture.
- Calibration mat workflow.
- Printed scale verification.
- Per-frame metadata.
- Per-file hashes.
- Rejected frame diagnostics.
- Coverage map.
- Package export.
- No mesh generation yet unless real reconstruction exists.

M2: Local AI capture assistance

- LiteRT runtime foundation.
- MediaPipe Interactive Segmenter.
- Tap-to-mask.
- Soft mask storage.
- Blur/exposure/glare/material scoring.
- Coverage guidance.
- Readiness categories.
- Bad-target warnings.

M3: Local printable reconstruction spike

- Hybrid local reconstruction path.
- Local feature matching.
- Optional ARCore pose/depth priors.
- Pose refinement.
- Dense reconstruction attempt.
- Conservative mesh generation.
- Local validation.
- Provenance report.
- Failure path.

M4: Local printable V1

- STL/3MF output.
- Workspace handoff gate.
- Slicer load test.
- Printability report.
- Benchmark fixture pass.
- Device matrix pass.

M5: Cloud contracts and billing foundation

- Backend API contracts.
- Billing data model docs.
- Credit ledger schema.
- Play Integrity contract.
- Upload validation contract.
- Quota model.
- Budget cap model.
- Provider cap model.
- Credit liability model.

M6: Cloud reconstruction V1

- License-approved reconstruction stack.
- Package validation.
- Keyframe selection.
- Cloud photogrammetry.
- Mesh cleanup.
- Provenance report.
- Visual/general output.

M7: Cloud Pro Printable

- Verified scale pipeline.
- Conservative repair.
- Per-face confidence.
- Slicer handoff gate.
- Printability report.
- Refund/failure policy.
- Admin dashboard.

## Immediate Implementation Order

1. Scaffold scanner modules from scratch.
2. Add scanner data models and package schema.
3. Add CameraX/Camera2 high-res capture.
4. Add per-frame metadata and hashes.
5. Add scan package ZIP export/import validation.
6. Add calibration mat assets and scale verification.
7. Add AprilTag marker detection and calibration reports.
8. Add ChArUco/ArUco calibration/debug support.
9. Add frame quality scoring.
10. Add LiteRT runtime foundation.
11. Add MediaPipe segmentation with soft-mask policy.
12. Add capture coverage heatmap.
13. Add ARCore assist profile.
14. Add hybrid capture profile.
15. Add local reconstruction spike.
16. Add native metric pose solver boundary.
17. Link and validate real OpenCV/geometry solver dependencies.
18. Add calibrated metric pose solving and bundle-adjustment output.
19. Add dense reconstruction only after metric pose solving is real.
20. Add local mesh validation and provenance reports.
21. Add workspace handoff gate.
22. Add cloud API contracts and stubs.
23. Add billing/credit schema and stubs.
24. Add cloud upload validation contract.
25. Defer real backend, GPU workers, Play Billing, and admin controls until local scanner contracts are proven.

## Implementation Progress

This section tracks actual repo implementation, not intent.

### 2026-05-07 M1 Foundation Start

Completed:

- Added scanner core package under `android-app/app/src/main/java/com/mobileslicer/scanner/`.
- Added scan package data models:
  - `ScanPackageManifest`
  - `ScanFrame`
  - `FrameQuality`
  - camera intrinsics/distortion metadata
  - calibration metadata
  - two-sided alignment metadata
- Added JSON serialization/parsing for scanner manifests and frames.
- Added SHA-256 package hashing.
- Added scanner package directory validation.
- Added scanner package ZIP export and validation.
- Added zip path traversal rejection.
- Added frame count, accepted count, forced count, rejected count, and referenced-file validation.
- Added frame quality gate logic with explicit rejection reasons.
- Added readiness score calculation using the planned weighted categories.
- Added printed scale verification logic.
- Added printable calibration gate logic.
- Added AprilTag/ArUco/ChArUco marker system metadata.
- Added a marker detector interface that fails closed until real detection is
  implemented.
- Added CameraX/Camera2 high-res capture foundation using CameraX 1.4.2.
- Added Android camera permission and optional camera hardware feature.
- Added local/dev scanner navigation entry from Home.
- Added `ScannerScreen` that captures high-res JPEG frames into a scan session.
- Added scanner UI field for measured 100 mm scale bar verification.
- Added package export from captured frames.
- Exported M1 packages record `verified_marker_mat` calibration when the entered
  printed scale measurement is within tolerance.
- Kept reconstruction and workspace handoff blocked.

Verification:

```text
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.ScannerPackageTest' --tests 'com.mobileslicer.ModelLoaderNavigationTest'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- CameraX 1.5.2 was not used because it requires Android Gradle Plugin 8.6+.
  This repo currently uses AGP 8.5.2, so scanner capture is pinned to CameraX
  1.4.2 until the build system is intentionally upgraded.
- Captured M1 packages are evidence packages only. They are not reconstructed
  meshes and cannot enter the slicer workspace.
- Marker detection is still fail-closed. Scale can be recorded, but printable
  readiness remains blocked until real marker observations/reprojection exist.
- ARCore assist and hybrid capture are still pending.
- Local AI and local reconstruction are still pending.

### 2026-05-07 M1.5 Capture Quality And Diagnostics

Completed:

- Added actual JPEG quality analysis for captured frames.
- Added luma-based Laplacian variance blur scoring.
- Added exposure score and clipped highlight ratio.
- Captured frames now record real image dimensions from JPEG decode.
- Captured frames are accepted or rejected with concrete quality reasons instead
  of `quality_analysis_pending`.
- Added capture pass tracking on each frame:
  - `mid_ring`
  - `low_ring`
  - `high_ring`
  - `top_detail`
  - `underside`
- Added scanner UI capture checklist/pass selector.
- Added accepted/rejected frame counts per pass in the UI.
- Added readiness summary model:
  - capture readiness
  - scale readiness
  - coverage readiness
  - material readiness
  - printable readiness
- Added diagnostics writing during package export:
  - `diagnostics/rejected_frames.json`
  - `diagnostics/coverage_map.json`
  - `diagnostics/device_capabilities.json`
  - `diagnostics/printable_gate.json`
- Added package hash coverage for diagnostics written before `package_hashes.json`.
- Added tests for image quality math.
- Added tests for diagnostics JSON and readiness summaries.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- Blur/exposure scoring is real now, but still simple. It should be benchmarked
  against fixture captures and tuned per device class.
- Marker detection is still not implemented. The detector interface still fails
  closed, and printable handoff remains blocked.
- Coverage is pass-based scaffolding, not true camera-angle reconstruction yet.
- Underside frames mark the package as object-moved/two-sided with unknown
  alignment until real alignment exists.

### 2026-05-07 Marker Calibration Evidence

Completed:

- Added marker observation schema:
  - marker system
  - marker ID
  - frame ID/path
  - marker size in millimeters
  - corner coordinates
  - hamming/decision margin fields
  - reprojection error field
- Added marker evidence summary:
  - detector name/status
  - observation count
  - observed frame count
  - unique marker count
  - average reprojection error
  - marker visibility
  - marker-derived scale confidence
  - fail-closed reasons
- Added marker observation diagnostics:
  - `diagnostics/marker_observations.json`
- Added marker mat layout generation:
  - `mobile_slicer_marker_mat_layout.json`
  - `mobile_slicer_marker_mat_preview.svg`
  - `README.txt`
- Added AprilRobotics `tag36h11` marker PNG assets for IDs 0-7 under
  `android-app/app/src/main/assets/scanner/apriltag/tag36h11/`.
- Added AprilRobotics asset license text at
  `android-app/app/src/main/assets/scanner/apriltag/LICENSE-AprilRobotics-apriltag-imgs.txt`.
- Added scanner UI action to write marker mat layout assets.
- Marker mat preview generation now embeds real tag36h11 PNG assets when the
  Android asset provider supplies them.
- Added package export marker detection boundary. Export now runs the detector
  interface, records observations, computes marker evidence, and feeds that into
  calibration/readiness.
- Vendored the AprilRobotics AprilTag C detector source under
  `android-app/app/src/main/cpp/third_party/apriltag/`.
- Added a separate native `scanner_apriltag` JNI library target that links the
  vendored tag36h11 detector.
- Updated the Kotlin detector bridge to decode captured JPEG frames into
  grayscale pixels before native detection.
- Added calibration gate requirements for minimum marker observations and marker
  frame coverage.
- Updated frame metadata during export with marker visibility and marker-derived
  scale confidence.
- Added tests for marker evidence, marker observation diagnostics, and marker mat
  asset generation.

Detector decision:

```text
Primary production detector: AprilRobotics AprilTag tag36h11 via native JNI
Secondary/debug detector: OpenCV ArUco/ChArUco after OpenCV dependency review
Current implementation: native AprilRobotics tag36h11 detector linked for marker
observations; printable scale still fails closed until reprojection/pose
calibration is implemented
```

Reason:

- AprilTag remains the primary printable calibration marker family.
- OpenCV ArUco/ChArUco is useful for debug/calibration support but should not
  replace the AprilTag printable mat path without accuracy testing.
- The current implementation can detect tag IDs/corners, but it does not pretend
  that corner detection alone proves printable scale. Until marker pose and
  reprojection calibration are implemented, marker evidence still reports
  `marker_reprojection_missing` and printable handoff remains blocked.

Verification:

```text
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*' --tests 'com.mobileslicer.ModelLoaderNavigationTest'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- The generated SVG preview now embeds real AprilRobotics tag36h11 marker
  bitmaps at the metric positions in the JSON layout when generated from the
  Android app assets.
- The native detector now reports `ready` when the JNI library loads and can
  return real tag36h11 marker IDs/corners/hamming/decision margin from captured
  grayscale frames.
- Printable readiness still stays blocked until marker pose/reprojection
  calibration is implemented. We do not fabricate reprojection error or scale
  confidence just because a tag was detected.
- Scale-bar verification alone is not enough for printable handoff.

### 2026-05-07 Marker Pose And Reprojection Calibration

Completed:

- Added marker pose/reprojection calibration math in
  `ScannerMarkerPose.kt`.
- Added planar AprilTag pose estimation from:
  - detected marker corners
  - known marker size in millimeters
  - per-frame camera intrinsics
- Added calibrated marker observation enrichment during package export.
- Added `calibrated_observation_count` to marker evidence summaries and
  diagnostics.
- Added fail-closed reason `marker_pose_intrinsics_missing` when tags are
  detected but cannot be converted into calibrated metric evidence.
- Added Camera2-derived rear-camera intrinsics capture for CameraX frames:
  - uses `LENS_INTRINSIC_CALIBRATION` when available
  - falls back to focal length plus physical sensor size when needed
  - records intrinsics in every captured `ScanFrame`
- Updated package export so calibration uses calibrated observations, not raw
  corner detections.
- Added unit tests for:
  - low-error synthetic marker pose estimation
  - reprojection evidence only appearing when frame intrinsics exist
  - partial calibration failing with explicit intrinsics-missing reason

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- Marker corner detection alone still does not prove printable scale.
- Camera2-derived intrinsics are best-effort device evidence, not a full camera
  calibration session. Benchmarking must decide whether a device/camera path is
  accurate enough for printable mode.
- Printable handoff remains blocked unless marker observations, frame coverage,
  scale-bar verification, reprojection error, and two-sided alignment all pass
  the gate.
- Distortion is still recorded as package metadata when available, but marker
  pose currently uses a pinhole model. A later step should use distortion
  coefficients or undistort marker corners before final printable gating.

### 2026-05-07 M2 Soft-Mask Capture Assistance Foundation

Completed:

- Added scanner soft-mask package/runtime boundary in `ScannerSoftMask.kt`.
- Added `ScannerSoftMaskGenerator` so MediaPipe/LiteRT can replace the current
  generator without changing scan package contracts.
- Added a deterministic local soft-mask fallback:
  `heuristic_center_background_soft_mask_v1`.
- Soft masks are written as PNG files next to captured frames:
  `frames/000001_mask.png`.
- Exported frames now populate:
  - `maskPath`
  - `maskSha256`
  - package `hasMasks`
- Added mask files to scanner package hashing and existing package validation.
- Added soft-mask diagnostics:
  - `diagnostics/soft_masks.json`
  - generator name/status
  - coverage ratio
  - edge uncertainty
  - center support
  - warnings
- Added warnings that enforce the product rule:
  - `heuristic_soft_mask_not_ai`
  - `mask_is_soft_evidence_only`
  - `mask_coverage_low`
  - `mask_coverage_high`
  - `mask_boundary_uncertain`
  - `object_center_support_low`
- Added tests for:
  - soft-mask alpha generation
  - high-coverage mask warnings
  - soft-mask diagnostics
  - package validation with referenced mask files

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- The current mask generator is intentionally labeled as a non-AI heuristic
  fallback. It is not a MediaPipe Interactive Segmenter replacement.
- Masks are soft evidence only. The original RGB frame is preserved and remains
  the source of truth for metric reconstruction.
- This step makes the package contract ready for MediaPipe/LiteRT masks without
  pretending that a model is already integrated.
- Reconstruction and workspace handoff remain blocked.

### 2026-05-08 M2 MediaPipe Interactive Segmenter Boundary

Completed:

- Added pinned MediaPipe Tasks Vision dependency:
  `com.google.mediapipe:tasks-vision:0.10.15`.
- Added production model slot documentation at:
  `android-app/app/src/main/assets/scanner/mediapipe/README.md`.
- Added expected model asset path:
  `scanner/mediapipe/magic_touch.tflite`.
- Added MediaPipe Interactive Segmenter status check:
  - `model_asset_available`
  - `model_asset_missing`
- Added `MediaPipeInteractiveScannerSoftMaskGenerator`.
- Added `ScannerCompositeSoftMaskGenerator`:
  - tries MediaPipe Interactive Segmenter first
  - falls back to heuristic soft masks only when MediaPipe is unavailable
  - records fallback warnings in diagnostics
- Added MediaPipe mask conversion through:
  - `BitmapImageBuilder`
  - `InteractiveSegmenter.RegionOfInterest`
  - center tap default ROI
  - category mask extraction
  - PNG package output through the existing soft-mask path
- Added reusable mask analysis/writing helpers so MediaPipe and heuristic masks
  share the same package output and diagnostics contract.
- Added tests for raw alpha mask analysis.

Verification:

```text
./gradlew :app:compileDebugKotlin
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This wires the real MediaPipe Android runtime path, but the production model
  asset is intentionally not bundled yet.
- Until `scanner/mediapipe/magic_touch.tflite` is added after license/model
  approval, exports record `model_asset_missing` and use the non-AI heuristic
  fallback.
- The fallback remains soft evidence only and is never allowed to delete metric
  geometry.

### 2026-05-08 M2 Tap-To-Object ROI And Mask Intent

Completed:

- Added scanner object ROI provenance:
  - `ScannerObjectRoi`
  - `ScannerObjectRoiSource.UserTap`
  - `ScannerObjectRoiSource.DefaultCenter`
- Added coordinate clamping so invalid normalized ROI values cannot escape into
  package metadata.
- Added warning provenance:
  - `user_object_tap_recorded`
  - `default_center_roi_weak_evidence`
- Added tap handling to the scanner preview:
  - user taps the object in the camera preview
  - normalized tap coordinates are stored for the scan session
  - package export uses that ROI for every generated mask
  - the preview draws a visible ROI target
  - the screen shows the current ROI source and normalized coordinates
- Updated MediaPipe Interactive Segmenter integration to use the scan ROI
  instead of a hard-coded center point.
- Updated heuristic fallback masks to record the same ROI provenance while
  still labeling themselves as non-AI fallback evidence.
- Added `object_roi` to `diagnostics/soft_masks.json`:

```json
{
  "object_roi": {
    "source": "user_tap",
    "tap_x_normalized": 0.25,
    "tap_y_normalized": 0.75
  }
}
```

- Added tests for:
  - ROI coordinate clamping
  - default-center weak-evidence warning
  - user-tap provenance warning
  - soft-mask diagnostics ROI persistence

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- Object selection is now explicit capture evidence, not an invisible
  assumption.
- Default-center ROI remains available only as a weak fallback when the user has
  not tapped the object.
- The original RGB frames remain the metric source of truth. Masks and ROI are
  soft capture assistance and reconstruction hints only.
- Reconstruction and workspace handoff remain blocked until a real validated
  metric mesh pipeline exists.

### 2026-05-08 M3 Local Reconstruction Preflight Gate

Completed:

- Added a local reconstruction preflight contract before implementing any mesh
  generation:
  - `ScannerLocalReconstructionLimits`
  - `ScannerLocalReconstructionPreflightResult`
  - `evaluateLocalReconstructionPreflight`
- Added hard blockers for local printable reconstruction:
  - empty capture
  - input frame cap exceeded
  - not enough accepted frames
  - missing required low/mid/high capture passes
  - unverified scale
  - missing calibration
  - low scale confidence
  - missing object masks
  - missing accepted-frame camera intrinsics
  - unknown two-sided/underside alignment
  - too many forced frames
  - unsuitable material risk
- Added package diagnostics output:

```text
diagnostics/local_reconstruction_preflight.json
```

Example blocked output:

```json
{
  "allowed": false,
  "reasons": [
    "not_enough_accepted_frames",
    "scale_unverified",
    "object_masks_missing"
  ],
  "accepted_frame_count": 4,
  "frame_count": 5,
  "accepted_passes": ["mid_ring"],
  "scale_source": "none",
  "scale_confidence": 0.0,
  "has_masks": false,
  "alignment_method": "not_moved"
}
```

- Added tests proving:
  - under-captured packages are blocked
  - complete calibrated masked evidence can pass preflight
  - unknown underside alignment blocks local reconstruction
  - preflight diagnostics serialize machine-readable blockers

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This is not reconstruction yet. It is the admission gate that prevents local
  photogrammetry from starting on bad evidence.
- The gate intentionally blocks most early packages because they do not yet
  contain enough calibrated, masked, multi-pass evidence for printable output.
- No fake mesh path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Reconstruction Workspace Builder

Completed:

- Added a deterministic reconstruction staging layer:
  - `ScannerReconstructionWorkspaceLimits`
  - `ScannerReconstructionWorkspaceFrame`
  - `ScannerReconstructionWorkspaceResult`
  - `buildLocalReconstructionWorkspace`
- The builder refuses to create a workspace unless:
  - package validation passes
  - local reconstruction preflight passes
  - accepted frames have camera intrinsics
  - referenced image and mask files exist and hash through package validation
- Added deterministic staging layout:

```text
reconstruction_job.json
input_hashes.json
images/
  000001.jpg
  000002.jpg
masks/
  000001_mask.png
  000002_mask.png
```

- Added deterministic accepted-frame order:
  1. low ring
  2. mid ring
  3. high ring
  4. top/detail
  5. underside
  6. frame id within each pass
- Added reconstruction job manifest fields:
  - scan id
  - source schema version
  - units
  - mode
  - capture profile
  - scale source
  - calibration
  - two-sided alignment
  - preflight result
  - staging limits
  - staged frame paths
  - staged frame hashes
  - mask paths and hashes
  - camera intrinsics
  - quality inputs
- Added `input_hashes.json` for the reconstruction workspace itself, so staged
  inputs can be independently verified.
- Added tests proving:
  - invalid packages are rejected before preflight
  - preflight-failed packages stage no files
  - complete calibrated masked evidence packages stage deterministically

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This still does not reconstruct a mesh. It creates the stable input contract
  that local feature matching, pose refinement, and dense reconstruction will
  consume.
- Staging copies only accepted evidence. Rejected frames remain in the source
  package diagnostics but do not enter reconstruction.
- No fake mesh path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Local Feature Extraction And Match Graph

Completed:

- Added local feature/match scaffolding after reconstruction workspace staging:
  - `ScannerFeatureExtractionLimits`
  - `ScannerLumaImage`
  - `ScannerImageFeature`
  - `ScannerFrameFeatureReport`
  - `ScannerPairMatchReport`
  - `ScannerFeatureMatchGraphResult`
  - `buildScannerFeatureMatchGraph`
- Added deterministic feature extraction from decoded staged images:
  - Android image decode path for real camera files
  - deterministic plain grayscale fallback for JVM tests
  - gradient/laplacian corner scoring
  - patch descriptors
  - per-frame feature caps
- Added pairwise descriptor matching:
  - Hamming distance threshold
  - one-to-one greedy descriptor matching
  - minimum accepted matches per frame pair
- Added match graph gates:
  - missing reconstruction job blocks matching
  - undecodable frame blocks matching
  - low feature count blocks matching
  - disconnected accepted-pair graph blocks pose estimation
  - fewer than two frames blocks matching
- Added workspace diagnostics:

```text
features/features.json
matches/match_graph.json
```

`features/features.json` records:

- frame id
- staged image path
- capture pass
- image dimensions
- feature count
- warnings
- feature coordinates
- feature scores
- descriptor hashes

`matches/match_graph.json` records:

- allowed / blocked
- errors
- warnings
- minimum pair-match threshold
- connected component count
- all pair match counts
- accepted pair flags

Added tests proving:

- feature extraction is deterministic on synthetic textured input
- shared descriptors create accepted pair matches
- blank/weak frames are rejected
- connected workspaces write feature and match diagnostics
- disconnected workspaces block the next reconstruction step

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This is still not pose estimation, dense reconstruction, meshing, STL export,
  or workspace handoff.
- This creates the measurable overlap graph needed before local pose refinement.
- The current extractor is an Android-safe deterministic first implementation.
  It gives us the contract, diagnostics, gates, and test surface needed before
  replacing or augmenting it with ORB/OpenCV/native feature code.
- No fake mesh path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Pose Initialization Scaffold

Completed:

- Added pose initialization scaffolding after feature/match graph generation:
  - `ScannerPoseInitializationLimits`
  - `ScannerPoseEstimateStatus`
  - `ScannerPoseNode`
  - `ScannerPoseEdge`
  - `ScannerPoseInitializationResult`
  - `initializeScannerPoseGraph`
- Added strict pose initialization blockers:
  - missing match graph
  - blocked match graph
  - missing reconstruction job
  - missing low/mid/high anchor pass
  - insufficient accepted pair support
  - insufficient anchor pair support
- Added explicit non-metric provenance:
  - every initialized pose node is marked `initial_unmetric`
  - pose initialization emits `pose_initialization_not_metric`
  - `metric` is always `false` at this stage
- Added deterministic anchor selection:
  - one anchor from each required low/mid/high pass
  - highest accepted edge support wins
  - frame id breaks ties
- Added workspace output:

```text
poses/pose_initialization.json
```

`poses/pose_initialization.json` records:

- allowed / blocked
- metric flag
- errors
- warnings
- required anchor passes
- pair support thresholds
- anchors
- all pose nodes
- accepted match edges

Added tests proving:

- missing match graph blocks pose initialization
- blocked match graph propagates failure reasons
- connected low/mid/high graph creates three anchors
- missing required anchor pass blocks initialization
- output stays explicitly non-metric

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This is not bundle adjustment, camera pose solving, dense reconstruction,
  meshing, STL export, or workspace handoff.
- It is the pose graph admission layer. The next stage must estimate/refine
  real relative camera poses and reject bad geometry before any mesh exists.
- No fake mesh path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Pose Refinement Gate

Completed:

- Added pose refinement scaffolding after pose initialization:
  - `ScannerPoseRefinementLimits`
  - `ScannerPoseRefinementNode`
  - `ScannerPoseRefinementResult`
  - `refineScannerPoseGraph`
- Added strict pose refinement blockers:
  - missing pose initialization
  - blocked pose initialization
  - missing pose nodes
  - missing pose edges
  - low average support edges
  - low average match count
  - low refinement confidence
  - non-metric pose initialization
- Added explicit dense-reconstruction gate:
  - `dense_reconstruction_allowed` remains `false`
  - `metric` remains `false`
  - every non-metric refinement emits
    `dense_reconstruction_blocked_until_metric_pose_solve`
- Added deterministic support metrics:
  - average support edges per frame
  - average accepted match count
  - refinement confidence
  - per-node support and confidence
- Added workspace output:

```text
poses/pose_refinement.json
```

`poses/pose_refinement.json` records:

- allowed / blocked
- dense reconstruction gate
- metric flag
- errors
- warnings
- refinement confidence
- support thresholds
- per-node support/confidence

Added tests proving:

- missing pose initialization blocks refinement
- blocked pose initialization propagates failure reasons
- strong non-metric initialization may pass scaffold confidence but still blocks
  dense reconstruction
- low support and low match counts block refinement

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This is still not a metric pose solver, bundle adjustment, dense
  reconstruction, mesh generation, STL export, or workspace handoff.
- This makes the next boundary explicit: dense reconstruction cannot start
  until a real calibrated metric pose solve exists.
- No fake mesh path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Metric Pose Solve Boundary

Completed:

- Added metric pose solve scaffolding after pose refinement:
  - `ScannerMetricPoseSolveLimits`
  - `ScannerMetricPoseSolverStatus`
  - `ScannerMetricPoseSolveResult`
  - `solveScannerMetricPoses`
- Added strict metric solve blockers:
  - missing reconstruction job
  - missing pose refinement
  - blocked pose refinement
  - low pose refinement confidence
  - missing verified marker-mat scale source
  - missing calibration
  - low scale confidence
  - missing marker reprojection evidence
  - high marker reprojection error
  - real metric solver not implemented
- Added explicit solver boundary:
  - `solver_status = not_implemented`
  - `metric = false`
  - `dense_reconstruction_allowed = false`
  - `metric_pose_solver_not_implemented`
  - `dense_reconstruction_blocked_until_metric_pose_solve`
- Added workspace output:

```text
poses/metric_pose_solve.json
```

`poses/metric_pose_solve.json` records:

- allowed / blocked
- dense reconstruction gate
- metric flag
- solver status
- errors
- warnings
- refinement confidence
- scale confidence
- marker reprojection error
- solve thresholds

Added tests proving:

- missing reconstruction job blocks metric solving
- missing pose refinement blocks metric solving
- missing calibration and scale evidence block metric solving
- high marker reprojection error blocks metric solving
- even strong evidence keeps dense reconstruction blocked until a real solver is
  implemented

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

Important implementation notes:

- This is still not a calibrated camera pose solver, bundle adjustment, dense
  reconstruction, mesh generation, STL export, or workspace handoff.
- This creates the final M3 admission boundary before dense reconstruction:
  dense reconstruction cannot start unless metric pose solving becomes real.
- No fake pose, fake mesh, or fake slicer handoff path was added.
- Workspace handoff remains blocked.

### 2026-05-08 M3 Native Metric Pose Solver Boundary

Completed:

- Added a dedicated Android native metric pose solver bridge:
  - `ScannerNativePoseSolverBridge`
  - `ScannerNativePoseSolverStatus`
  - `scanner_pose_solver_stub.cpp`
  - CMake target `scanner_pose_solver`
- The bridge reports a machine-readable native solver status:
  - solver name
  - availability
  - OpenCV link state
  - status string
  - human-readable detail
- The current production build still does not link OpenCV for scanner metric
  pose solving, so the bridge correctly reports:

```json
{
  "available": false,
  "status": "opencv_unavailable",
  "solver_name": "native_opencv_metric_pose_solver",
  "opencv_linked": false
}
```

- JVM unit tests fail closed when the native library cannot be loaded, which
  keeps local tests honest without requiring Android native loading.
- `poses/metric_pose_solve.json` now records native solver state under:

```json
{
  "native_solver": {
    "available": false,
    "status": "native_library_unavailable | opencv_unavailable | ready",
    "detail": "...",
    "solver_name": "native_opencv_metric_pose_solver",
    "opencv_linked": false
  }
}
```

- Metric solving now adds explicit blockers when the native solver is not
  usable:
  - `native_metric_solver_unavailable:<status>`
  - `opencv_metric_pose_solver_not_linked`
  - `metric_pose_solver_not_implemented`
  - `dense_reconstruction_blocked_until_metric_pose_solve`

Why this matters:

- Dense reconstruction must not start from uncalibrated scaffold poses.
- Local printable output must not be generated until the app has a real
  calibrated metric pose solve with scale evidence.
- This makes the OpenCV/native dependency an observable runtime gate instead
  of a hidden TODO.

Still not implemented:

- OpenCV Android dependency vendoring/linking.
- Essential/fundamental matrix estimation.
- PnP from marker observations.
- Multi-view triangulation.
- Bundle adjustment.
- Camera distortion refinement.
- Metric sparse cloud output.
- Dense reconstruction.
- Mesh generation.
- Provenance face labeling.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Vendor or build an Android-safe OpenCV module for `arm64-v8a`.
2. Link it into `scanner_pose_solver`.
3. Replace the current status-only bridge with a real native solve request:
   - accepted keyframes
   - feature tracks
   - marker observations
   - intrinsics
   - distortion
   - verified scale source
4. Return calibrated camera poses, scale residuals, marker reprojection error,
   triangulated sparse points, and per-frame residual diagnostics.
5. Keep `dense_reconstruction_allowed=false` until the solver passes hard
   accuracy gates in tests.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:testDebugUnitTest :app:lintDebug
scripts/verify_android.sh local
```

Result:

```text
BUILD SUCCESSFUL
APK ready: android-app/app/build/outputs/apk/debug/app-debug.apk
```

### 2026-05-08 M3 OpenCV Relative Pose Solver Integration

Completed:

- Added the official OpenCV Android AAR dependency:
  - `org.opencv:opencv:4.13.0`
- Kept OpenCV out of the app CMake link path for now because the official
  OpenCV Prefab native library requires `c++_shared`, while the existing Orca
  slicer native stack is configured with `c++_static`.
- This is intentional. Changing the app-wide STL setting is a slicer-engine
  risk and should be done only as a planned native-runtime migration.
- Added an Android OpenCV solver path through the Java API:
  - `ScannerAndroidOpenCvPoseSolver`
  - `ScannerOpenCvPoseSolverStatus`
- The solver initializes OpenCV with `OpenCVLoader.initLocal()`.
- The solver uses real calibrated relative-pose operations:
  - `Calib3d.findEssentialMat`
  - `Calib3d.recoverPose`
- Added deterministic 2D correspondence output to the scanner match graph:

```json
{
  "frame_a": "000001",
  "frame_b": "000002",
  "match_count": 42,
  "accepted": true,
  "matches": [
    {
      "ax": 120,
      "ay": 244,
      "bx": 131,
      "by": 241,
      "descriptor_distance": 6
    }
  ]
}
```

- `solveScannerMetricPoses` now attempts a real relative pair solve when the
  Android OpenCV solver is available.
- The relative solve records:
  - success flag
  - status
  - inlier count
  - inlier ratio
  - 3x3 rotation matrix
  - unit translation vector
- The output is still not metric scale. Translation from essential-matrix
  recovery is direction-only until scale is fused from markers, depth, or
  measured calibration.
- Metric mesh generation and dense reconstruction remain blocked.

`poses/metric_pose_solve.json` now records both solver layers:

```json
{
  "opencv_solver": {
    "available": true,
    "status": "ready",
    "solver_name": "android_opencv_relative_pose_solver"
  },
  "native_solver": {
    "available": false,
    "status": "opencv_unavailable",
    "solver_name": "native_opencv_metric_pose_solver",
    "opencv_linked": false
  },
  "relative_pair_solve": {
    "success": true,
    "status": "relative_pose_recovered",
    "inlier_count": 31,
    "inlier_ratio": 0.74,
    "rotation_row_major": [],
    "translation_unit": []
  }
}
```

Important implementation notes:

- The C++ native OpenCV bridge remains fail-closed because of the `c++_static`
  versus `c++_shared` conflict.
- Android runtime can use OpenCV through the Java API immediately.
- JVM tests cannot initialize Android OpenCV and correctly fail closed there.
- This is now a real relative pose step, not a fake metric solve.
- It is still not a multi-view metric solver, bundle adjustment, dense
  reconstruction, mesh generation, STL/3MF export, or workspace handoff.

Next implementation step:

1. Promote pairwise correspondences into multi-view feature tracks.
2. Use marker observations and verified marker scale to metric-scale the sparse
   reconstruction.
3. Add multi-view pose graph optimization / bundle adjustment diagnostics.
4. Require hard residual gates before `metric=true`.
5. Only then allow dense reconstruction to begin.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
APK ready: android-app/app/build/outputs/apk/debug/app-debug.apk
```

### 2026-05-08 Local AI Capture Guidance Contracts

Completed:

- Added local AI capture guidance contracts:
  - `ScannerCaptureGuidanceReport`
  - `ScannerMaskQualitySummary`
  - `ScannerDepthPriorStatus`
  - `scannerCaptureGuidanceReport`
  - `scannerMaskQualitySummary`
  - `scannerDepthAnythingV2SmallStatus`
- Added `diagnostics/local_ai_status.json`.
- `local_ai_status.json` records:
  - mask count
  - AI mask count
  - heuristic mask count
  - mask coverage
  - mask edge uncertainty
  - mask center/tap support
  - mask consistency
  - depth-prior model status
  - user-facing guidance messages
  - blocking reasons
- Readiness now includes separate categories:
  - capture
  - scale
  - coverage
  - material
  - mask
  - lighting
  - depth/pose
  - printable
- `scannerReadinessSummary` now uses actual soft-mask consistency instead of
  hardcoded `maskConsistency = 0`.
- `ScannerScreen` passes generated soft-mask results into readiness scoring
  before writing diagnostics.
- Added a model slot for optional local depth prior:

```text
android-app/app/src/main/assets/scanner/depth/depth_anything_v2_small.tflite
```

- Added an asset README documenting that the depth model is optional and may
  only be used as relative-depth prior, not metric truth.

Guidance messages now cover the capture problems that usually ruin scans:

- missing low/mid/high/top/underside passes
- blur
- bad lighting
- clipped highlights / glare
- object too small
- missing AI object mask
- uncertain mask edges
- unverified scale
- risky shiny/transparent material
- missing local depth prior

Important implementation notes:

- MediaPipe Interactive Segmenter remains the preferred local object-mask path.
- The heuristic mask path is still allowed as an explicitly labeled weak
  fallback for internal/dev builds, but it is never treated as measured
  geometry.
- Depth Anything V2 Small is only an optional local prior. It cannot make a
  printable metric mesh by itself.
- Depth Anything V2 Base/Large/Giant remain blocked for commercial use unless
  separately licensed because their public license is non-commercial.
- No dense reconstruction, mesh generation, STL/3MF export, or workspace
  handoff was unblocked.

Next implementation step:

1. Bundle or download a reviewed MediaPipe Interactive Segmenter model.
2. Add a device-side smoke test for mask generation on real Android hardware.
3. Add UI rendering for `local_ai_status.json` guidance messages during
   capture, not only after export.
4. Add optional Depth Anything V2 Small LiteRT/TFLite model conversion and
   performance gate.
5. Feed mask consistency and depth-prior confidence into the multi-view pose
   and reconstruction pipeline only as soft evidence.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Multi-View Feature Track Generation

Completed:

- Added a dedicated multi-view feature-track stage:
  - `ScannerFeatureTrackLimits`
  - `ScannerFeatureTrackObservation`
  - `ScannerFeatureTrack`
  - `ScannerFeatureTrackResult`
  - `buildScannerFeatureTracks`
- Added workspace output:

```text
features/tracks.json
```

- `features/tracks.json` records:
  - allowed / blocked
  - track count
  - long-track count
  - max track length
  - observed capture passes
  - spatial grid coverage
  - per-track observations
  - validation limits
  - blocking errors
- Tracks are built from existing accepted pair correspondences in
  `matches/match_graph.json`.
- The track builder merges pairwise observations into shared multi-frame
  components with a union-find structure.
- Duplicate observations in the same frame are collapsed to one representative
  feature, which keeps tracks usable for later pose solving.
- Added conservative track gates:
  - minimum total track count
  - minimum long-track count
  - minimum track length
  - minimum long-track length
  - minimum spatial-cell coverage
  - required low/mid/high pass coverage
- Added metric-pose gate integration:
  - `feature_tracks_missing`
  - `feature_tracks_blocked`
  - `feature_track_count_low`
  - `long_feature_track_count_low`
  - `feature_tracks:<track_error>`
- `poses/metric_pose_solve.json` now includes a `feature_tracks` metric gate
  summary so dense reconstruction cannot ignore missing tracks.

Why this matters:

- Pairwise relative pose is not enough for a printable local scan.
- Multi-view tracks are the evidence needed for triangulation, scale fusion,
  pose graph optimization, and bundle adjustment.
- This moves the scanner from "best pair" geometry toward actual multi-frame
  reconstruction evidence.

Still not implemented:

- Track reprojection residuals.
- Triangulated sparse metric points.
- Marker-scale fusion into the track graph.
- Bundle adjustment.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Add sparse triangulation inputs from feature tracks and current relative
   pose evidence.
2. Fuse verified marker scale into the sparse reconstruction.
3. Compute per-track/per-frame reprojection residuals.
4. Add bundle-adjustment diagnostics and hard residual gates.
5. Keep `metric=false` and `dense_reconstruction_allowed=false` until those
   gates pass.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Sparse Reconstruction Input Gate

Completed:

- Added sparse reconstruction diagnostics:
  - `ScannerSparseReconstructionLimits`
  - `ScannerSparseTrackInput`
  - `ScannerSparseNormalizedObservation`
  - `ScannerSparseReconstructionResult`
  - `prepareScannerSparseReconstruction`
- Added workspace output:

```text
sparse/sparse_reconstruction.json
```

- `sparse/sparse_reconstruction.json` records:
  - allowed / blocked
  - metric flag
  - triangulation-ready flag
  - prepared track count
  - scale confidence
  - marker reprojection error
  - normalized camera observations per track
  - sparse reconstruction limits
  - blocking errors
- Track observations are normalized with camera intrinsics:

```text
x_normalized_camera = (x_px - cx) / fx
y_normalized_camera = (y_px - cy) / fy
```

- Added strict blockers:
  - missing reconstruction job
  - missing feature tracks
  - blocked feature tracks
  - not enough prepared tracks
  - missing verified marker mat
  - missing calibration
  - low scale confidence
  - missing/high marker reprojection error
  - missing metric camera poses
  - triangulation blocked until metric poses
  - bundle adjustment required
- Added metric-pose gate integration:
  - `sparse_reconstruction_missing`
  - `sparse_reconstruction_blocked`
  - `sparse_prepared_track_count_low`
  - `sparse_reconstruction:<sparse_error>`
- `poses/metric_pose_solve.json` now includes a `sparse_reconstruction`
  gate summary.

Why this matters:

- This prepares the exact normalized observations needed for triangulation.
- It avoids the bad shortcut of fabricating 3D points from unmetric relative
  poses.
- It creates the next hard boundary before any metric sparse cloud, dense
  reconstruction, or mesh output can exist.

Still not implemented:

- Metric camera pose solve.
- Actual triangulated sparse 3D points.
- Reprojection residuals.
- Marker-scale fusion into 3D points.
- Bundle adjustment.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Build the metric pose graph input from relative pair constraints.
2. Add marker-scale constraints to the pose graph.
3. Implement conservative sparse triangulation only for tracks with calibrated
   metric poses.
4. Compute reprojection residuals per track and per frame.
5. Keep `metric=false` until residual and scale gates pass.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Metric Pose Graph Constraint Gate

Completed:

- Added metric pose graph diagnostics:
  - `ScannerMetricPoseGraphLimits`
  - `ScannerMetricPoseGraphNode`
  - `ScannerMetricPoseGraphConstraint`
  - `ScannerMetricPoseGraphResult`
  - `buildScannerMetricPoseGraph`
- Added workspace output:

```text
poses/metric_pose_graph.json
```

- `poses/metric_pose_graph.json` records:
  - allowed / blocked
  - metric flag
  - bundle-adjustment-ready flag
  - graph nodes by frame and capture pass
  - relative pose constraints
  - OpenCV relative pose solver status
  - connected component count
  - verified marker scale confidence
  - marker reprojection error
  - graph limits
  - blocking errors
- Relative pose constraints are built from accepted match-graph pairs. Each
  constraint records:
  - frame A
  - frame B
  - match count
  - solve status
  - relative-pose availability
  - inlier count
  - inlier ratio
  - rotation matrix
  - unit translation direction
- The graph only becomes `allowed=true` when:
  - pose refinement is allowed
  - the match graph is allowed
  - OpenCV relative pose solving is available
  - enough relative-pose constraints solve successfully
  - the solved constraint graph is connected
  - low, mid, and high capture passes are represented
  - verified marker-mat scale evidence exists
  - scale confidence meets the configured threshold
  - marker reprojection error exists and is below the configured threshold
- The graph remains `metric=false` even when allowed. It is only a verified
  constraint input for global metric pose solving and bundle adjustment.
- Added metric-pose solve integration:
  - `metric_pose_graph_missing`
  - `metric_pose_graph_blocked`
  - `relative_pose_constraint_count_low`
  - `metric_pose_graph:<graph_error>`
- `poses/metric_pose_solve.json` now includes a `metric_pose_graph`
  gate summary.

Why this matters:

- This is the missing bridge between individual pair solves and a real
  multi-view camera solution.
- It prevents the app from treating one good pair as a solved object scan.
- It forces the reconstruction chain to prove connectivity, angle coverage,
  scale evidence, and relative pose support before sparse triangulation can
  start.

Still not implemented:

- Global metric camera pose solve.
- Bundle adjustment.
- Actual triangulated sparse 3D points.
- Reprojection residuals.
- Per-track and per-frame residual gates.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Add a global metric pose solve boundary that consumes
   `poses/metric_pose_graph.json`.
2. Produce camera-pose candidates only when the graph is connected and scaled.
3. Add bundle-adjustment diagnostics:
   - per-frame residual
   - per-track residual
   - marker residual
   - scale residual
   - rejected outlier constraints
4. Keep output `metric=false` until the residual thresholds pass.
5. Only after that, allow sparse triangulation to produce measured 3D points.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Global Metric Pose Solve Boundary

Completed:

- Extended `poses/metric_pose_solve.json` with a global metric pose solve
  boundary.
- Added metric solve data contracts:
  - `ScannerMetricCameraPoseCandidate`
  - `ScannerMetricBundleAdjustmentDiagnostics`
- `solveScannerMetricPoses` now consumes `poses/metric_pose_graph.json` and
  records:
  - camera pose candidates
  - bundle adjustment status
  - bundle adjustment residual limits
  - bundle adjustment blocking errors
- Camera pose candidates are emitted only when the metric pose graph is:
  - present
  - `allowed=true`
  - `bundle_adjustment_ready=true`
  - connected through usable relative pose constraints
- Candidate pose output records:
  - frame id
  - source frame id
  - rotation matrix from the source relative-pose constraint
  - graph-unit translation
  - source constraint status
  - `metric_validated=false`
- Bundle adjustment diagnostics currently block with:
  - `bundle_adjustment_not_implemented`
  - `bundle_adjustment_frame_residuals_missing`
  - `bundle_adjustment_track_residuals_missing`
  - `bundle_adjustment_marker_residual_missing`
  - `bundle_adjustment_scale_residual_missing`

Why this matters:

- This creates the next explicit boundary between a connected/scaled pose graph
  and a real metric camera solution.
- It lets later stages consume a stable candidate-pose contract without
  pretending the candidates are validated metric camera poses.
- It keeps dense reconstruction, sparse triangulation, STL/3MF export, and
  workspace handoff blocked until residuals prove the solve is trustworthy.

Still not implemented:

- Actual global optimization / bundle adjustment.
- Per-frame reprojection residual computation.
- Per-track reprojection residual computation.
- Marker residual computation.
- Scale residual computation.
- Outlier constraint rejection.
- Metric camera-pose acceptance.
- Sparse 3D point triangulation.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Implement bundle-adjustment residual diagnostics over:
   - camera pose candidates
   - feature tracks
   - normalized sparse observations
   - marker scale evidence
2. Reject outlier constraints and report them.
3. Keep `metric=false` until all residual limits pass.
4. Only then allow sparse triangulation to produce measured 3D points.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Bundle Adjustment Residual Diagnostics Gate

Completed:

- Added bundle-adjustment residual diagnostics:
  - `ScannerBundleAdjustmentLimits`
  - `ScannerBundleAdjustmentFrameResidual`
  - `ScannerBundleAdjustmentTrackResidual`
  - `ScannerBundleAdjustmentResult`
  - `analyzeScannerBundleAdjustment`
- Added workspace output:

```text
poses/bundle_adjustment.json
```

- `poses/bundle_adjustment.json` consumes:
  - `reconstruction_job.json`
  - `poses/metric_pose_graph.json`
  - `sparse/sparse_reconstruction.json`
- It records:
  - allowed / blocked
  - metric flag
  - status
  - pose candidate count
  - pose candidate coverage
  - prepared track count
  - max frame constraint residual estimate
  - max track observation spread estimate
  - marker residual
  - scale residual percent
  - rejected constraint count
  - per-frame residual entries
  - per-track residual entries
  - limits
  - blocking errors
- It also updates `solveScannerMetricPoses` so
  `poses/metric_pose_solve.json` consumes the external
  `poses/bundle_adjustment.json` gate when present.

Important accuracy note:

- This is not a real optimizer yet.
- The current residuals are deterministic preflight estimates:
  - frame residuals compare candidate graph translations against relative-pose
    constraint directions
  - track residuals measure normalized observation spread converted through
    frame focal length
  - marker residual comes from marker reprojection evidence
  - scale residual comes from verified printed-scale correction
- Because these are not full bundle-adjusted reprojection residuals, this stage
  intentionally fails closed with:
  - `global_bundle_adjustment_not_implemented`
  - `metric_pose_acceptance_blocked_until_global_optimization`

Why this matters:

- The pipeline now has a concrete residual diagnostics artifact instead of a
  vague "bundle adjustment later" note.
- It can reject obvious bad graph/track/marker/scale evidence before real
  optimization exists.
- It keeps metric pose acceptance, sparse triangulation, dense reconstruction,
  mesh generation, STL/3MF export, and workspace handoff blocked until a real
  global optimizer replaces the preflight-only residual gate.

Still not implemented:

- Actual nonlinear bundle adjustment.
- Jacobian/residual minimization over camera poses and sparse points.
- True reprojection residuals from triangulated 3D points.
- Outlier removal followed by re-optimization.
- Metric camera-pose acceptance.
- Sparse 3D point triangulation.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Add conservative sparse triangulation scaffolding that consumes only:
   - candidate camera poses
   - prepared sparse observations
   - verified marker scale evidence
   - bundle-adjustment diagnostic status
2. Keep triangulation blocked while `poses/bundle_adjustment.json` has
   `metric=false`.
3. Define the output contract for measured sparse points:
   - point id
   - source track id
   - contributing frames
   - estimated XYZ in metric units
   - reprojection residual
   - provenance/confidence class
4. Do not generate dense geometry until sparse points and residual gates are
   real.

Verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Sparse Triangulation Scaffold

Completed:

- Added conservative sparse triangulation scaffolding:
  - `ScannerSparseTriangulationLimits`
  - `ScannerSparseMeasuredPoint`
  - `ScannerSparseTriangulationResult`
  - `prepareScannerSparseTriangulation`
- Added workspace output:

```text
sparse/sparse_triangulation.json
```

- `sparse/sparse_triangulation.json` consumes:
  - `reconstruction_job.json`
  - `poses/metric_pose_solve.json`
  - `sparse/sparse_reconstruction.json`
  - `poses/bundle_adjustment.json`
- It records:
  - allowed / blocked
  - metric flag
  - dense-reconstruction-ready flag
  - triangulation status
  - prepared track count
  - pose candidate count
  - bundle-adjustment metric flag
  - measured point count
  - point schema
  - measured points
  - limits
  - blocking errors
- The measured point schema is now explicit:

```text
point_id
source_track_id
contributing_frames
xyz_mm
reprojection_residual_px
provenance_class
```

- Valid provenance classes:

```text
measured_high
measured_low
rejected
blocked_scaffold
```

Fail-closed rules:

- Sparse triangulation remains blocked when:
  - verified marker scale is missing
  - metric pose solve is blocked
  - metric camera poses are missing
  - bundle adjustment is blocked
  - bundle adjustment is not metric
  - sparse reconstruction input is blocked
  - prepared track count is too low
  - pose candidates are missing
  - pose candidate coverage is too low
  - sparse tracks are missing
  - the real triangulation solver is not implemented
- It also always records:

```text
dense_reconstruction_blocked_until_sparse_points_are_metric
```

Important accuracy note:

- This stage does not generate fake XYZ points.
- The point contract exists so downstream dense reconstruction can be built
  against a stable schema.
- `measured_points` remains empty until metric pose solving and bundle
  adjustment are genuinely validated.

Why this matters:

- The scanner pipeline now has an explicit boundary between validated camera
  evidence and measured sparse 3D evidence.
- It prevents dense reconstruction from starting from unvalidated candidate
  poses or residual preflight estimates.
- It preserves the core rule: no printable geometry unless the app can prove
  where the measured points came from.

Still not implemented:

- Real sparse triangulation.
- True reprojection residuals from triangulated 3D points.
- Metric camera-pose acceptance.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Corrected next implementation step after review:

1. Add a real metric pose optimization boundary before any real
   triangulation.
2. Model the Ceres-style bundle-adjustment contract:
   - fixed intrinsics first
   - optimized camera extrinsics
   - optimized sparse 3D point positions
   - feature-track reprojection residuals
   - verified marker-scale constraint
3. Only after optimized metric poses and optimized sparse points are accepted
   should `sparse/sparse_triangulation.json` contain measured XYZ points.
4. Keep dense reconstruction blocked until sparse point count, spatial
   coverage, residuals, and scale gates are all acceptable.

### 2026-05-08 M3 Metric Pose Optimizer Boundary

Completed:

- Added the metric pose optimizer boundary:
  - `ScannerPoseOptimizerLimits`
  - `ScannerOptimizedPoseCandidate`
  - `ScannerPoseOptimizerResult`
  - `optimizeScannerMetricPoses`
- Added workspace outputs:

```text
poses/optimized_metric_poses.json
sparse/optimized_sparse_points.json
```

- `poses/optimized_metric_poses.json` records:
  - allowed / blocked
  - metric flag
  - optimizer status
  - pose candidate count
  - optimized pose count
  - residual/scale limits
  - optimized camera poses
  - blocking errors
- `sparse/optimized_sparse_points.json` records:
  - allowed / blocked
  - metric flag
  - optimizer status
  - prepared track count
  - optimized sparse point count
  - point schema
  - optimized points
  - blocking errors
- Sparse triangulation now also requires:
  - `poses/optimized_metric_poses.json`
  - `sparse/optimized_sparse_points.json`

Why this matters:

- This fixes the ordering problem identified in review. OpenCV two-view
  triangulation is not enough until camera projection matrices are valid and
  metric. The optimizer boundary is where those metric camera poses must be
  accepted.
- This keeps us aligned with the standard SfM order used by tools like COLMAP
  and OpenMVG: register/estimate poses, triangulate structure, then refine with
  bundle adjustment before trusting metric output.
- It keeps the local printable scanner honest: sparse triangulation and dense
  reconstruction cannot consume raw candidate poses.

Fail-closed rules:

- The optimizer currently blocks with:

```text
native_pose_optimizer_unavailable:<status>
native_metric_optimizer_not_linked
optimized_metric_pose_acceptance_blocked
```

- The optimized artifacts remain contracts until a real native optimizer is
  linked and validated.

### 2026-05-08 M3 Native Pose Optimizer Bridge Boundary

Completed:

- Added native optimizer bridge status boundary:
  - `ScannerNativePoseOptimizerBridge`
  - `ScannerNativePoseOptimizerStatus`
- Added native C++ stub:

```text
android-app/app/src/main/cpp/scanner_pose_optimizer_stub.cpp
```

- Added CMake target:

```text
scanner_pose_optimizer
```

- Added scanner test coverage:
  - `ScannerNativePoseOptimizerBridgeTest`
- `optimizeScannerMetricPoses` now records native optimizer status in both:

```text
poses/optimized_metric_poses.json
sparse/optimized_sparse_points.json
```

- The native optimizer status records:
  - available
  - status
  - detail
  - solver name
  - Ceres link state

Current expected status:

```text
available=true on Android native builds
status=ready_eigen_extrinsic
ceres_linked=false
optimizer_linked=true
```

Why this matters:

- The app now has an explicit native boundary for the real bundle-adjustment
  optimizer instead of burying that dependency behind Kotlin checks.
- Future Ceres integration can replace the current Eigen backend without
  changing the scanner artifact chain.
- Metric pose acceptance remains blocked until the native optimizer is linked,
  returns optimized camera poses, returns optimized sparse points, and passes
  all residual gates.

Still not implemented:

- Native Ceres nonlinear bundle-adjustment optimizer.
- Real reprojection residual minimization.
- Optimized metric camera-pose acceptance.
- Optimized sparse 3D point output.
- Real sparse triangulation from optimized poses.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Add the native optimizer request/response contract so the Kotlin pipeline
   sends a real optimization problem to native code.
2. Keep the native stub fail-closed until a Ceres/equivalent backend is linked.
3. Record the request and native response in the scanner artifacts for
   debugging and reproducibility.
4. Continue blocking dense reconstruction, mesh generation, STL/3MF export,
   and workspace handoff until the native optimizer returns measured metric
   output.

### 2026-05-08 M3 Native Pose Optimizer Request/Response Contract

Completed:

- Added native optimizer solve boundary:
  - `ScannerNativePoseOptimizerResult`
  - `ScannerNativeOptimizedCameraPose`
  - `ScannerNativeOptimizedSparsePoint`
  - `ScannerNativePoseOptimizerBridge.optimize(requestJson)`
  - `nativeOptimizeJson(requestJson)`
- `optimizeScannerMetricPoses` now builds a concrete optimizer request and
  passes it through the native bridge.
- Added native C++ JNI solve stub in:

```text
android-app/app/src/main/cpp/scanner_pose_optimizer_stub.cpp
```

- Added scanner test coverage for:
  - JVM fail-closed native solve behavior
  - optimizer artifact request schema
  - optimizer artifact native-response schema

Optimizer request schema:

```json
{
  "schema_version": 1,
  "solver_name": "native_ceres_metric_pose_optimizer",
  "units": "millimeters",
  "fixed_intrinsics_by_frame": {
    "frame_id": {
      "fx": 1000.0,
      "fy": 1000.0,
      "cx": 500.0,
      "cy": 400.0,
      "width": 1000,
      "height": 800
    }
  },
  "candidate_camera_poses": [],
  "feature_track_observations": [],
  "marker_scale_constraint": {
    "scale_source": "verified_marker_mat",
    "units": "millimeters",
    "verified_marker_mat": true,
    "scale_confidence": 0.9,
    "marker_reprojection_error_px": 1.0,
    "calibration": {}
  },
  "bundle_adjustment_preflight": {
    "pose_candidate_coverage": 1.0,
    "frame_residual_max_px": 1.0,
    "track_residual_max_px": 1.0,
    "marker_residual_px": 1.0,
    "scale_residual_percent": 0.0,
    "allowed": false,
    "metric": false
  },
  "residual_limits": {
    "max_frame_residual_px": 2.0,
    "max_track_residual_px": 2.5,
    "max_marker_residual_px": 3.0,
    "max_scale_residual_percent": 1.5,
    "min_pose_candidate_coverage": 1.0,
    "min_prepared_track_count": 40,
    "require_verified_marker_mat": true
  },
  "solver_limits": {
    "fixed_intrinsics": true,
    "max_iterations": 50,
    "max_runtime_ms": 2000,
    "robust_loss": "huber",
    "optimize_camera_poses": true,
    "optimize_sparse_points": true,
    "optimize_intrinsics": false
  }
}
```

Native response schema:

```json
{
  "success": false,
  "status": "ceres_unavailable",
  "detail": "Ceres-backed scanner pose optimizer is not linked in this build.",
  "solver_name": "native_ceres_metric_pose_optimizer",
  "ceres_linked": false,
  "optimizer_linked": false,
  "optimized_camera_poses": [],
  "optimized_sparse_points": [],
  "per_frame_residuals": [],
  "per_track_residuals": [],
  "marker_residual_px": null,
  "scale_residual_percent": null,
  "rejected_observations": [],
  "rejected_tracks": [],
  "solver_iterations": 0,
  "solver_runtime_ms": 0
}
```

Artifact changes:

- `poses/optimized_metric_poses.json` now records:
  - `optimizer_request`
  - `native_optimizer_response`
  - existing status, limits, errors, and optimized pose contract
- `sparse/optimized_sparse_points.json` now records:
  - `optimizer_request`
  - `native_optimizer_response`
  - existing point schema, limits, errors, and optimized sparse point contract

Fail-closed rules:

- Metric pose optimization remains blocked when:
  - native library is unavailable
  - native optimizer backend is not linked
  - native optimize response is unsuccessful
  - optimized camera poses are missing
  - optimized sparse points are missing
  - upstream metric-pose, sparse-reconstruction, or residual gates are blocked
- The expected current errors include:

```text
native_pose_optimizer_unavailable:<status>
native_metric_optimizer_not_linked
native_pose_optimizer_failed:<status>
optimized_pose_output_missing
optimized_sparse_point_output_missing
optimized_metric_pose_acceptance_blocked
```

Why this matters:

- The scanner now has a real handoff contract for metric optimization instead
  of only checking whether a native library exists.
- The request contains the evidence the solver must optimize:
  - fixed camera intrinsics
  - candidate camera poses
  - normalized feature-track observations
  - verified marker-scale constraint
  - residual gates
  - solver runtime/iteration limits
- The response contract defines exactly what native code must return before
  any local printable pipeline can move forward:
  - optimized camera poses
  - optimized metric sparse points
  - residual diagnostics
  - rejected observations/tracks
  - runtime and iteration counts
- This keeps the implementation aligned with the project rule: no dense mesh,
  printable STL, 3MF, or workspace handoff until measured metric evidence
  exists and passes gates.

Still not implemented:

- Native Ceres or equivalent nonlinear optimizer.
- Solver-side residual minimization.
- Solver-side outlier rejection and re-optimization.
- Metric pose acceptance.
- Optimized sparse point acceptance.
- Real sparse triangulation from optimized poses.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Wire the existing reconstruction stages into one scanner-owned pipeline
   runner.
2. Write a single `reconstruction_summary.json` that records the stage order,
   artifacts, blocking errors, and dense-reconstruction readiness.
3. Add an end-to-end scanner test that proves the full chain runs and fails
   closed before dense reconstruction.
4. Then implement the native optimizer backend behind the existing
   request/response contract.

### 2026-05-08 M3 End-To-End Local Reconstruction Pipeline Runner

Completed:

- Added scanner-owned reconstruction pipeline runner:
  - `ScannerReconstructionPipelineLimits`
  - `ScannerReconstructionPipelineStage`
  - `ScannerReconstructionPipelineResult`
  - `runScannerLocalReconstructionPipeline`
- Added workspace output:

```text
reconstruction_summary.json
```

- The pipeline runs the current local scanner stages in this order:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
```

- `reconstruction_summary.json` records:
  - schema version
  - completed flag
  - metric flag
  - dense reconstruction readiness
  - workspace path
  - blocking errors
  - canonical stage order
  - per-stage artifact path
  - per-stage artifact existence
  - per-stage allowed/metric flags
  - per-stage errors/warnings
  - key pipeline limits
- Added scanner test coverage:

```text
ScannerReconstructionPipelineTest
```

- The end-to-end test builds a real local scan package fixture, runs every
  current stage, verifies all expected artifacts exist, and confirms the final
  state remains blocked before dense reconstruction.
- The scanner screen now exposes the pipeline through a local dev action:

```text
Run local pipeline
```

- That action consumes the exported local scan package directory, writes a
  workspace under app cache, records the `reconstruction_summary.json` path,
  and reports the first blocking errors in the status panel. It still does not
  hand anything to the workspace or export a mesh.

Expected current fail-closed blockers:

```text
metric_pose_optimizer:native_pose_optimizer_failed:<status>
metric_pose_optimizer:optimized_pose_output_missing
metric_pose_optimizer:optimized_sparse_point_output_missing
sparse_triangulation:dense_reconstruction_blocked_until_sparse_points_are_metric
```

Why this matters:

- The scanner implementation now has one verifiable local reconstruction chain
  instead of separate callable pieces that only unit tests know how to invoke.
- This makes the next native optimizer work measurable: the solver must turn
  the existing pipeline summary from blocked into metric-ready by returning
  accepted optimized poses and sparse points through the already documented
  native response schema.
- Dense reconstruction, mesh generation, STL/3MF export, and workspace handoff
  remain blocked until the pipeline summary proves metric sparse evidence is
  accepted.

Still not implemented:

- Native Ceres or equivalent nonlinear optimizer.
- Solver-side residual minimization.
- Solver-side outlier rejection and re-optimization.
- Metric pose acceptance.
- Optimized sparse point acceptance.
- Real sparse triangulation from optimized poses.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Implement a conservative native optimizer backend behind the existing
   request/response contract.
2. Use repo-vendored Eigen and nlohmann JSON for the first Android-native
   backend.
3. Produce candidate optimized camera poses, sparse points, per-frame
   residuals, per-track residuals, and rejected tracks.
4. Keep full metric acceptance blocked until upstream metric pose gates and
   residual gates accept the native output.

### 2026-05-08 M3 Native Eigen Optimizer Backend

Completed:

- Replaced the fail-closed native optimizer stub with a linked Eigen/nlohmann
  backend in:

```text
android-app/app/src/main/cpp/scanner_pose_optimizer_stub.cpp
```

- Updated the native CMake target to include:
  - repo-vendored `nlohmann/json.hpp`
  - repo-vendored Eigen
- The native backend now:
  - parses the optimizer request JSON
  - reads fixed intrinsics
  - reads candidate camera poses
  - reads normalized feature-track observations
  - triangulates sparse candidate points from two or more rays
  - refines sparse points with fixed-intrinsics Gauss-Newton iterations
  - refines camera rotations with a conservative small-angle SO(3)
    parameterization while keeping the first camera anchored
  - refines camera translations while keeping the first camera anchored
  - orthonormalizes refined rotations after each update
  - enforces verified marker-mat scale evidence before optimization
  - enforces marker reprojection residual limits before optimization
  - enforces scale residual limits before optimization
  - computes per-track reprojection residuals
  - computes per-frame residual summaries
  - rejects degenerate tracks
  - rejects tracks above residual limits
  - returns optimized camera-pose candidates
  - returns optimized sparse point candidates
  - returns rejected track ids
  - returns solver runtime and iteration count
- Kotlin now distinguishes:

```text
ceres_linked=false
optimizer_linked=true
```

This matters because the current backend is a real native optimizer boundary,
but it is not Ceres and not full nonlinear bundle adjustment.

Expected Android native status:

```text
available=true
status=ready_eigen_extrinsic
backend_name=eigen_extrinsic_metric_pose_optimizer
ceres_linked=false
optimizer_linked=true
```

Important accuracy boundary:

- This backend is intentionally conservative and limited.
- It does not claim final metric validity.
- It does not perform full nonlinear bundle adjustment.
- It refines sparse points, camera rotations, and camera translations, but it is
  still a conservative Eigen backend rather than a production Ceres/equivalent
  bundle-adjustment implementation.
- It enforces marker-scale evidence as an admission gate before optimization,
  but it does not yet optimize marker observations as direct residual blocks.
- It treats current graph translations as candidate millimeter translations only
  for diagnostics; Kotlin gates still block metric acceptance unless the
  upstream pose solve becomes genuinely metric.
- The response can contain native sparse point candidates, but dense
  reconstruction and mesh export remain blocked unless the Kotlin gates accept
  the full chain.

Still not implemented:

- Full Ceres/equivalent nonlinear bundle adjustment.
- Production marker observation residual blocks in Ceres/equivalent nonlinear
  bundle adjustment.
- More advanced robust loss selection beyond Huber weighting.
- Full outlier re-optimization policy with stable per-track rejection provenance.
- Accepted metric pose output.
- Accepted metric sparse point output.
- Real sparse triangulation handoff from accepted optimized points.
- Dense reconstruction.
- Mesh generation.
- STL/3MF export.
- Workspace handoff.

Next implementation step:

1. Promote the native optimizer from conservative Eigen extrinsic refinement to
   production Ceres/equivalent bundle adjustment.
2. Add the missing solver pieces:
   - production marker corner residual blocks
   - configurable robust loss selection
   - stable outlier rejection provenance and re-optimization reports
   - covariance/uncertainty reporting
3. Keep output blocked until optimized pose count, optimized sparse point
   count, residuals, marker scale, and upstream metric gates all pass.

Verification status:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Optimizer Provenance And Uncertainty Diagnostics

Completed:

- Added stable solver-side provenance for feature tracks pruned during native
  iterative outlier rejection.
- Native optimizer responses now include:
  - `pruned_track_reports`
  - rejected track reason
  - rejected track residual
  - rejected track mean residual
  - rejected track residual count
  - active outlier gate
- Added per-frame uncertainty diagnostics:
  - feature observation count
  - marker-corner observation count
  - feature residual
  - marker-corner residual
  - confidence score
  - uncertainty class
- Kotlin now preserves these native diagnostics in scanner artifacts:
  - `uncertainty_by_frame`
  - `pruned_track_reports`
- Scanner optimizer contract tests now assert those artifact fields exist.

Why this matters:

- Dense reconstruction must not consume opaque camera poses. Each accepted or
  rejected track needs explainable provenance before we can trust later point
  cloud, surface, and mesh stages.
- Frame-level uncertainty is the start of the printable evidence map. It gives
  the next stages a machine-readable way to distinguish strong frames from weak
  frames instead of treating every camera pose equally.
- This keeps the local printable path aligned with the plan: measured evidence
  first, confidence/provenance recorded, no mesh handoff until the gates pass.

Still blocked by design:

- The uncertainty score is a conservative diagnostic, not a statistical
  covariance from production bundle adjustment.
- Ceres/equivalent nonlinear BA is still not linked.
- Dense reconstruction remains blocked.
- Mesh generation remains blocked.
- STL/3MF export remains blocked.
- Workspace handoff remains blocked.

Verification status:

```text
./gradlew ':app:buildCMakeDebug[arm64-v8a]'
```

Current result:

```text
BUILD SUCCESSFUL
```

Scanner unit test status:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Current result:

```text
BLOCKED OUTSIDE SCANNER
```

Known unrelated Kotlin blocker:

```text
android-app/app/src/main/java/com/mobileslicer/modelsearch/thingiverse/ThingiverseApiClient.kt:58
Unresolved reference 'headerField'.
android-app/app/src/main/java/com/mobileslicer/modelsearch/thingiverse/ThingiverseApiClient.kt:60
'return' is prohibited here.
```

### 2026-05-08 M3 Pose Conditioning Diagnostics

Completed:

- Added native pose-conditioning diagnostics derived from the current Eigen
  normal equations.
- Native optimizer responses now include:
  - `pose_conditioning`
  - residual block count per frame
  - condition number per frame
  - rotation sigma diagnostic
  - translation sigma diagnostic
  - conditioning class
- Kotlin now preserves `pose_conditioning` in scanner optimizer artifacts.
- Scanner optimizer contract tests now assert the artifact field exists.

Why this matters:

- Residuals alone are not enough. A pose can have low residuals and still be
  poorly constrained if all evidence comes from weak geometry or a narrow view
  spread.
- Pose conditioning gives dense reconstruction a machine-readable way to reject
  or downweight weak camera poses before point-cloud fusion or meshing.
- This is still an Eigen diagnostic, not production covariance from Ceres or an
  equivalent nonlinear solver.

Still blocked by design:

- Ceres/equivalent nonlinear bundle adjustment is still not linked.
- The sigma values are diagnostic approximations from the local normal
  equations, not final metrology covariance.
- Dense reconstruction remains blocked.
- Mesh generation remains blocked.
- STL/3MF export remains blocked.
- Workspace handoff remains blocked.

Verification status:

```text
./gradlew ':app:buildCMakeDebug[arm64-v8a]'
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
./gradlew :app:assembleDebug
./gradlew :app:compileReleaseKotlin
./gradlew :app:lintDebug
```

Current result:

```text
BUILD SUCCESSFUL
```

Broader lint status:

```text
./gradlew :app:lintDebug
```

Current result:

```text
BLOCKED OUTSIDE SCANNER
```

### 2026-05-08 M3 Native Extrinsic Refinement And Marker Scale Gate

Completed:

- Upgraded the native Eigen optimizer backend in:

```text
android-app/app/src/main/cpp/scanner_pose_optimizer_stub.cpp
```

- The backend now performs conservative local extrinsic refinement:
  - first camera remains the gauge anchor
  - sparse points are refined with fixed intrinsics
  - non-anchor camera translations are refined
  - non-anchor camera rotations are refined with small-angle SO(3) updates
  - each rotation update is clamped to 0.05 radians per iteration
  - each translation update is clamped to 25 mm per iteration
  - refined rotations are re-orthonormalized before output
  - projection residuals are Huber-weighted before contributing to point and
    extrinsic normal equations
  - extreme sparse-track outliers are pruned between solve iterations before
    they can pull later camera updates
- The backend now fails closed before optimization when metric scale evidence
  is not good enough:
  - `marker_scale_unverified`
  - `scale_confidence_low`
  - `marker_residual_missing`
  - `marker_residual_high`
  - `scale_residual_missing`
  - `scale_residual_high`
- Successful native responses now report:
  - marker residual
  - scale residual
  - marker scale gate application
  - scale confidence
  - rotation refinement enabled
  - translation refinement enabled
  - sparse point refinement enabled
  - robust loss mode
  - robust delta
  - outlier gate
  - iterative outlier-pruning enabled

Why this matters:

- Local printable scanning needs metric evidence before any dense geometry is
  allowed. The native layer now independently enforces the same marker-scale
  and residual gates that the Kotlin stages enforce.
- The optimizer no longer leaves camera rotations frozen. This makes the local
  reconstruction chain closer to a real SfM/bundle-adjustment pipeline while
  still refusing to claim a printable mesh.
- Huber weighting and explicit outlier pruning make the native boundary less
  fragile when a bad feature track slips through earlier matching gates.
- This is still not the final solver. It is a verifiable native metric boundary
  that can be replaced by Ceres or an equivalent production nonlinear optimizer
  without changing the scanner artifact order.

Still blocked by design:

- Production Ceres/equivalent marker-corner residual blocks are not yet linked.
- More advanced robust loss selection is not yet implemented beyond Huber
  weighting.
- Intrinsics are fixed.
- Dense reconstruction remains blocked.
- Mesh generation remains blocked.
- STL/3MF export remains blocked.
- Workspace handoff remains blocked.

Verification status:

```text
./gradlew ':app:buildCMakeDebug[arm64-v8a]'
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
./gradlew :app:assembleDebug
./gradlew :app:compileReleaseKotlin
```

Current result:

```text
BUILD SUCCESSFUL
```

Broader lint status:

```text
./gradlew :app:lintDebug
```

Current result:

```text
BLOCKED OUTSIDE SCANNER
```

Known unrelated blocker:

```text
android-app/app/src/main/java/com/mobileslicer/workspace/WorkspaceTransformControls.kt:164
Modifier parameter should be the first optional parameter.
```

### 2026-05-08 M3 Direct Marker Corner Residual Blocks

Completed:

- Staged calibrated package marker observations into the local reconstruction
  workspace:

```text
reconstruction_job.json
  marker_mat_layout
  marker_corner_observations
```

- Each marker corner observation now carries:
  - frame id
  - marker id
  - corner index
  - observed normalized camera coordinate
  - observed pixel coordinate
  - metric marker-mat world coordinate in millimeters
  - original detector reprojection error when available
  - hamming/decision-margin diagnostics when available
- The optimizer request now forwards `marker_corner_observations` to native
  code and enforces `min_marker_corner_observation_count`.
- The native Eigen optimizer now parses marker-corner observations and uses
  them as direct 2D/3D residual blocks during extrinsic refinement.
- The native solver now:
  - runs feature-track sparse-point refinement
  - prunes extreme feature-track outliers
  - runs feature-track extrinsic refinement
  - runs marker-corner extrinsic refinement
  - rejects missing marker-corner residual evidence
  - rejects high marker-corner residuals after refinement
- Kotlin now preserves native marker-corner diagnostics in optimizer artifacts:
  - `marker_corner_residuals`
  - `marker_corner_residual_count`
  - `marker_corner_residual_max_px`
  - `marker_corner_residual_mean_px`
  - `marker_corner_residual_blocks_enabled`

Why this matters:

- Marker scale is no longer only a preflight field. The native optimizer now has
  direct measured marker-corner evidence attached to camera extrinsic updates.
- This is a necessary step toward local printable reconstruction because the
  metric camera solution must be tied to the printed calibration mat, not only
  to feature tracks on the scanned object.
- The implementation still fails closed. If marker observations are missing,
  too few, or produce high residuals, the optimizer refuses metric success.

Still blocked by design:

- Marker residuals currently assume the default MobileSlicer marker-mat layout.
  User-imported/custom marker mat layouts still need package-level layout
  storage before they can be accepted.
- Marker residuals refine camera extrinsics, but intrinsics remain fixed.
- This is still an Eigen implementation, not production Ceres/equivalent bundle
  adjustment.
- Dense reconstruction remains blocked.
- Mesh generation remains blocked.
- STL/3MF export remains blocked.
- Workspace handoff remains blocked.

Verification status:

```text
./gradlew ':app:buildCMakeDebug[arm64-v8a]'
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Current result:

```text
BUILD SUCCESSFUL
```

Reason:

```text
android-app/app/src/main/java/com/mobileslicer/workspace/WorkspaceTransformControls.kt
```

The active non-scanner worktree currently has a Compose lint
`ModifierParameter` issue in `WorkspaceTransformControls.kt`. Scanner work is
kept isolated and that file is not edited by scanner implementation steps.

### 2026-05-08 M3 Metric Sparse Acceptance Gate

Completed:

- Replaced the unconditional optimizer acceptance blocker in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerPoseOptimizer.kt
```

- The optimizer stage can now mark its output as `allowed=true` and
  `metric=true`, but only when all strict metric gates pass:
  - verified marker-mat scale source
  - upstream metric pose solve is allowed and metric
  - upstream bundle diagnostics are allowed and metric
  - sparse reconstruction inputs are allowed
  - pose candidate coverage meets the configured threshold
  - prepared track count meets the configured threshold
  - frame, track, marker, and scale residuals are present and under limit
  - native optimizer is available and linked
  - native optimizer reports success
  - optimized pose count meets the configured threshold
  - optimized sparse point count meets the configured threshold
  - optimized poses are metric-validated
  - optimized pose rotations/translations have valid shapes
  - optimized pose residuals are present and under limit
  - optimized sparse point coordinates have valid shapes
  - optimized sparse point residuals are present and under limit
  - rejected optimized sparse points are not accepted
  - native marker-corner residual count meets the configured threshold
  - native marker-corner residuals are present and under limit
  - native scale residual is present and under limit
  - pose conditioning diagnostics exist
  - ill-conditioned, underdetermined, or missing-intrinsics poses block output
- Added top-level optimized sparse point artifacts to:

```text
sparse/optimized_sparse_points.json
```

- Accepted optimized sparse points now include:
  - stable point id
  - source feature track id
  - metric XYZ coordinates in millimeters
  - post-optimization reprojection residual
  - provenance class
- Replaced the unconditional sparse-triangulation implementation blocker in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerSparseTriangulation.kt
```

- Sparse triangulation now consumes accepted optimized sparse points instead of
  generating zero-coordinate scaffold points.
- Sparse triangulation now preserves source evidence by joining each optimized
  point back to its source feature track and contributing frame ids.
- `sparse/sparse_triangulation.json` can now become:

```json
{
  "allowed": true,
  "metric": true,
  "dense_reconstruction_ready": true,
  "status": "accepted_metric_sparse_points"
}
```

  only when the optimized sparse point artifact is already metric and the
  measured sparse point count, residuals, provenance, scale, upstream pose, and
  bundle gates pass.
- Added scanner tests for:
  - blocked optimizer output
  - blocked sparse triangulation output
  - accepted synthetic metric sparse point handoff

Why this matters:

- This is the first implementation step where the local reconstruction pipeline
  can move from "all metric geometry blocked" to "metric sparse evidence may be
  accepted when proven."
- Dense reconstruction still cannot start from camera poses alone. It now has a
  concrete machine-readable admission gate: accepted metric sparse points with
  residuals, provenance, and source frame evidence.
- The implementation keeps the no-fake-geometry rule intact. If optimized
  points are missing, weak, rejected, unsupported by source tracks, or above
  residual limits, dense reconstruction remains blocked.

Still blocked by design:

- Dense point-cloud reconstruction is not implemented yet.
- Surface reconstruction is not implemented yet.
- Mesh generation is not implemented yet.
- STL/3MF export is not implemented yet.
- Workspace handoff remains blocked until dense mesh validation, printability,
  and provenance gates exist.
- The current accepted optimizer backend is the strict Eigen backend. Production
  Ceres or an equivalent nonlinear bundle-adjustment backend is still
  recommended before public release or precision claims.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSparseTriangulationTest' \
  --tests 'com.mobileslicer.scanner.ScannerPoseOptimizerTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Dense Reconstruction Admission Gate

Completed:

- Added a dedicated dense reconstruction admission artifact:

```text
dense/dense_reconstruction.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDenseReconstruction.kt
```

- Added the stage to the end-to-end local pipeline after
  `sparse_triangulation`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
```

- The dense stage now consumes only:

```text
sparse/sparse_triangulation.json
```

  and refuses to admit dense reconstruction unless that artifact is already:

```json
{
  "allowed": true,
  "metric": true,
  "dense_reconstruction_ready": true
}
```

- The dense admission gate validates:
  - metric sparse point count
  - distinct contributing frame count
  - sparse point reprojection residuals
  - rejected provenance absence
  - measured-high provenance ratio
  - metric sparse point bounding-box extent
- Accepted dense admission writes:

```json
{
  "allowed": true,
  "metric": true,
  "dense_reconstruction_admitted": true,
  "mesh_generation_ready": false,
  "status": "dense_reconstruction_admitted"
}
```

- The artifact records:
  - dense seed point ids
  - source feature track ids
  - contributing frame ids
  - metric XYZ coordinates in millimeters
  - sparse reprojection residuals
  - provenance classes
  - metric bounding box
  - all active admission limits
- Added tests for:
  - missing sparse triangulation artifact
  - sparse triangulation that is not metric
  - accepted synthetic metric sparse seed cloud
  - pipeline stage ordering with the new dense admission stage

Why this matters:

- Dense reconstruction now has its own machine-readable admission contract. It
  no longer depends on a loose boolean from the sparse stage alone.
- The scanner still does not create surface geometry just because sparse points
  exist. The dense artifact only says the next densification implementation may
  start from these measured metric seeds.
- Mesh generation remains explicitly blocked through
  `mesh_generation_ready=false` until a real dense/surface reconstruction
  implementation exists and passes validation.

Still blocked by design:

- No dense point-cloud expansion has been implemented yet.
- No TSDF, depth fusion, Poisson, ball-pivoting, or MVS surface stage exists
  yet.
- No mesh repair, printability report, STL export, 3MF export, or workspace
  handoff exists yet.
- Dense admission is not a printable result. It is the gate before the first
  real dense reconstruction implementation.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDenseReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M2 Verified MediaPipe MagicTouch Local AI

Completed:

- Bundled the official MediaPipe MagicTouch Interactive Segmenter model:

```text
android-app/app/src/main/assets/scanner/mediapipe/magic_touch.tflite
```

- Source URL:

```text
https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite
```

- Pinned model integrity in code:

```text
sha256 = e24338a717c1b7ad8d159666677ef400babb7f33b8ad60c4d96db4ecf694cd25
bytes  = 6227884
```

- Updated:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerMediaPipeSoftMask.kt
```

  so the MediaPipe path is considered production-ready only when the bundled
  asset exists and matches the approved SHA-256 and byte size.
- Updated the composite mask generator so it uses MediaPipe only when
  `productionReady=true`. If the asset is missing, truncated, or hash-mismatched,
  the scanner falls back to the heuristic mask and records that fallback as weak
  non-AI evidence.
- Updated `diagnostics/local_ai_status.json` to include:
  - MediaPipe availability
  - production readiness
  - model asset path
  - source URL
  - expected SHA-256
  - actual SHA-256
  - expected byte size
  - actual byte size
- Updated the asset README:

```text
android-app/app/src/main/assets/scanner/mediapipe/README.md
```

- Added tests for:
  - missing model asset
  - byte-size mismatch
  - verified model hash/size status
  - raw model asset SHA/size inspection

Why this matters:

- Local AI segmentation is no longer just a runtime slot. The Android app now
  carries the approved MagicTouch model and verifies it before treating
  MediaPipe object masks as real AI evidence.
- This still follows the scanner rule: AI masks are soft capture evidence only.
  They may guide segmentation, readiness, and reconstruction weighting, but they
  cannot hard-delete metric geometry or fabricate printable surfaces.
- The heuristic fallback remains available for isolated/dev builds, but it is
  explicitly labeled as weak non-AI evidence.

Still blocked by design:

- No local AI depth model is bundled yet.
- Depth Anything V2 Small remains optional and disabled until a reviewed
  LiteRT/TFLite asset is added.
- No AI model is allowed to generate metric geometry.
- Local printable output still requires measured photogrammetry, calibration,
  dense/surface reconstruction, mesh validation, and slicer gates.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSoftMaskTest' \
  --tests 'com.mobileslicer.scanner.ScannerDiagnosticsTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Accuracy Benchmark Harness

Completed:

- Added a scanner accuracy benchmark artifact:

```text
benchmark/accuracy_report.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerAccuracyBenchmark.kt
```

- Added `ScannerBenchmarkFixture` so known physical fixtures can define:
  - fixture id
  - expected dimensions in millimeters
  - maximum dimension error in millimeters
  - maximum dimension error percent
  - minimum scale confidence
  - maximum marker reprojection error
- Added `runScannerAccuracyBenchmark(...)` to compare pipeline output against a
  known fixture after dense admission.
- The benchmark reads:
  - `reconstruction_job.json`
  - `dense/dense_reconstruction.json`
- The benchmark validates:
  - dense reconstruction admission exists
  - scale confidence exists and passes threshold
  - marker reprojection error exists and passes threshold
  - measured bounding-box extents exist
  - sorted measured extents match sorted expected fixture dimensions
  - every dimension passes `max(absolute_mm_tolerance, percent_tolerance)`
- The report writes:
  - expected dimensions
  - measured dimensions
  - absolute error per dimension
  - percent error per dimension
  - tolerance per dimension
  - max absolute error
  - max percent error
  - pass/fail status
  - blocking errors
- Added tests for:
  - known block passing tolerance
  - known block failing tolerance
  - missing dense/scale/marker evidence failing honestly

Why this matters:

- This is the measurement gate that prevents the scanner from drifting into
  confidence theater. Later dense reconstruction, surface reconstruction, repair,
  and export work must be judged against known physical dimensions.
- The benchmark compares sorted extents, not pose. That is intentional for a
  first fixture gate: it measures dimensional scale without requiring the scan
  coordinate frame to match the fixture coordinate frame.
- This benchmark does not replace topology, watertightness, provenance, or
  slicer-load validation. It is the dimensional accuracy gate before those later
  gates can mean anything.

Still blocked by design:

- No real captured benchmark package is checked in yet.
- No device matrix benchmark runner exists yet.
- No material matrix benchmark runner exists yet.
- Dense/surface reconstruction should not be tuned by visual output alone; it
  should be tuned against this benchmark report and later real fixture packages.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerAccuracyBenchmarkTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Real-World Benchmark Capture Workflow

Completed:

- Added benchmark fixture input parsing in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerAccuracyBenchmark.kt
```

- Added strict fixture input validation:
  - fixture id is deterministic and sanitized
  - blank fixture id falls back to `manual_known_fixture`
  - X/Y/Z dimensions are required
  - dimensions must parse as finite positive millimeter values
  - invalid inputs emit stable error codes such as
    `dimension_x_missing`, `dimension_y_invalid`, and
    `dimension_z_invalid`
- Added scanner UI controls for:
  - known fixture id
  - expected X/Y/Z fixture dimensions in millimeters
  - benchmark readiness/failure state
  - running the benchmark after the local reconstruction pipeline creates a
    workspace
  - displaying the generated benchmark report path
- Added the benchmark action to the current scanner workflow:

```text
Capture frame(s)
  -> Export package
  -> Run local pipeline
  -> Run benchmark against known physical fixture
  -> Write benchmark/accuracy_report.json
```

- Updated the scanner scope panel so the app no longer describes itself as only
  an M1 capture package tool. The current screen now accurately describes:
  - high-res JPEG evidence capture
  - metadata and SHA-256 package validation
  - fail-closed reconstruction pipeline execution
  - known-fixture dimensional benchmark execution
  - no mesh export yet
- Added parser tests for:
  - valid manual fixture dimensions
  - missing and invalid dimension input failures

Why this matters:

- We now have an operator-facing way to test the scanner against a real
  measured object instead of only testing synthetic JSON artifacts.
- This keeps the implementation aligned with the product rule: printable output
  must be earned by measured evidence and benchmarked dimensions, not a good
  looking preview.
- Failed benchmarks are first-class results. The app writes the report and
  shows the failure instead of hiding it.

Still blocked by design:

- Real captured benchmark packages still need to be created from physical
  fixtures on target devices.
- Dense/surface reconstruction is still not producing a printable mesh.
- Benchmark passing does not replace future topology, watertightness,
  provenance, slicer-load, 3MF/STL export, and workspace handoff gates.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerAccuracyBenchmarkTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

Additional verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
./gradlew ':app:buildCMakeDebug[arm64-v8a]' \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  :app:assembleDebug \
  :app:lintDebug
git diff --check -- \
  android-app/app/src/main/java/com/mobileslicer/scanner \
  android-app/app/src/test/java/com/mobileslicer/scanner \
  android-app/app/src/main/assets/scanner/mediapipe \
  docs/scanner
```

Current result:

```text
BUILD SUCCESSFUL
diff check clean
```

### 2026-05-08 Scanner Flow Redesign

Completed:

- Replaced the diagnostic-first scanner screen with a staged capture flow:

```text
Prep -> Capture -> Review -> Reconstruct
```

- Prep stage:
  - shows object/material guidance before camera work
  - warns users to use matte objects, textured surfaces, and steady lighting
  - warns against glass, chrome, and transparent plastic
  - keeps marker-mat asset generation and printed scale-bar measurement before
    printable reconstruction
  - starts with `Start printable scan`
- Object lock:
  - camera opens as the primary surface
  - user taps the object
  - selected object region is shown as a clean region overlay
  - capture text is limited to `Tap object` or `Object locked`
- Guided capture:
  - removed the giant diagnostic cards from capture
  - shows compact pass progress:

```text
Mid 0/12
High 0/12
Low 0/12
Detail 0/8
```

  - manual `Capture frame` is the only capture trigger
  - tapping the object locks/focuses the object but does not start auto capture
  - quality/admission gates still decide accepted vs rejected frames
- Live coaching:
  - capture shows one plain instruction at a time
  - current coaching messages include:

```text
Tap object
Move slower
Too much glare
Need more views of the back
Get closer for details
Depth weak, add photos
High angle needed
```

- Review stage:
  - shows accepted and rejected frame counts
  - shows pass coverage heatmap
  - shows blockers such as missing high angle, scale not verified, and too few
    sharp frames
  - allows individual frame deletion from the scan package before export
- Reconstruct stage:
  - runs the local printable pipeline only after a valid package export
  - shows exact reconstruction blockers returned by the pipeline
  - keeps no-fake-mesh behavior: blocked scans fail honestly instead of handing
    fake geometry to the workspace

Why this matters:

- The scanner now behaves like a scanner product instead of an engineering test
  harness.
- Users no longer choose between implementation details such as photo vs AR
  depth. The app chooses the best available evidence path internally.
- Capture is still strict. The flow became easier, but the validation gates did
  not get weakened.

### 2026-05-08 M3 Dense Point Cloud Boundary

Completed:

- Added a dedicated dense point-cloud artifact:

```text
dense/dense_point_cloud.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDensePointCloud.kt
```

- Added the stage to the end-to-end local pipeline after
  `dense_reconstruction`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
```

- The dense point-cloud stage consumes only:

```text
dense/dense_reconstruction.json
```

  and refuses to proceed unless dense reconstruction has already been admitted:

```json
{
  "allowed": true,
  "metric": true,
  "dense_reconstruction_admitted": true
}
```

- The artifact records:
  - stable dense point ids
  - source dense-admission seed ids
  - original source track ids
  - contributing frame ids
  - metric XYZ coordinates in millimeters
  - reprojection residuals
  - provenance classes
  - measured-high ratio
  - contributing frame count
  - mean nearest-neighbor spacing
  - metric bounding box
  - surface readiness gates
- The implementation deliberately does not interpolate, hallucinate, or AI-fill
  geometry. It carries measured seed points forward and marks
  `surface_reconstruction_ready=false` unless the point-count and spacing gates
  pass.
- Added tests for:
  - missing dense admission artifact
  - blocked/non-metric dense admission
  - accepted measured seed point cloud with surface still blocked
  - dense measured fixture that can mark surface reconstruction ready
  - end-to-end pipeline stage ordering with `dense_point_cloud`

Why this matters:

- This is the first explicit artifact between dense admission and surface
  reconstruction. The scanner can now distinguish:
  - sparse metric seed evidence
  - dense point-cloud readiness
  - surface reconstruction readiness
  - mesh generation readiness
- The no-fake-geometry rule is still intact. The stage does not create new
  geometry beyond measured inputs. It only makes the next surface stage possible
  when the measured point cloud is dense enough.
- Surface reconstruction, mesh repair, STL/3MF export, and workspace handoff
  remain blocked unless this artifact proves the point cloud is ready.

Still blocked by design:

- No image-space patch densification exists yet.
- No depth/TSDF fusion exists yet.
- No Poisson, ball-pivoting, marching cubes, or MVS surface stage exists yet.
- No mesh repair, printability report, STL export, 3MF export, or workspace
  handoff exists yet.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDensePointCloudTest' \
  --tests 'com.mobileslicer.scanner.ScannerDenseReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Surface Reconstruction Boundary

Completed:

- Added a dedicated surface reconstruction artifact:

```text
surface/surface_reconstruction.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerSurfaceReconstruction.kt
```

- Added the stage to the end-to-end local pipeline after
  `dense_point_cloud`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
```

- The surface stage consumes only:

```text
dense/dense_point_cloud.json
```

  and refuses to proceed unless the dense point cloud is already:

```json
{
  "allowed": true,
  "metric": true,
  "surface_reconstruction_ready": true
}
```

- The artifact records:
  - source point count
  - contributing frame count
  - measured-high ratio
  - measured surface support ratio
  - repaired surface ratio
  - mean point spacing
  - metric bounding box
  - measured surface patch records
  - active reconstruction limits
- Added hard gates for:
  - blocked dense point cloud
  - missing metric point cloud
  - point cloud not admitted for surface reconstruction
  - low surface point count
  - low measured-high ratio
  - missing/high point spacing
  - missing surface bounds
  - low measured surface ratio
  - nonzero repaired surface ratio before a repair stage exists
- Added tests for:
  - missing dense point cloud
  - blocked/non-metric dense point cloud
  - accepted measured surface boundary with mesh still blocked
  - low measured coverage failure
  - end-to-end pipeline stage ordering with `surface_reconstruction`

Why this matters:

- The scanner now has a machine-readable boundary between dense point evidence
  and mesh/topology generation. A future mesh stage can consume this artifact
  without confusing "surface support exists" with "printable mesh exists."
- This still does not export STL/3MF and does not hand anything to the
  workspace. It only says whether measured surface evidence is good enough for
  the next topology/mesh stage.
- Repair remains zero by design at this boundary. Any later hole fill or repair
  must be explicitly recorded as repaired geometry before slicer handoff.

Still blocked by design:

- No triangle mesh has been generated yet.
- No watertightness/manifold/self-intersection validation exists yet.
- No hole repair, provenance coloring, printability report, STL export, 3MF
  export, or workspace handoff exists yet.
- Surface readiness is not printability. It is the gate before topology
  reconstruction and mesh validation.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSurfaceReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerDensePointCloudTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Mesh Topology Admission Boundary

Completed:

- Added a dedicated mesh topology admission artifact:

```text
mesh/mesh_topology.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerMeshTopology.kt
```

- Added the stage to the end-to-end local pipeline after
  `surface_reconstruction`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
```

- The mesh topology stage consumes only:

```text
surface/surface_reconstruction.json
```

  and refuses to proceed unless the surface artifact is already:

```json
{
  "allowed": true,
  "metric": true,
  "surface_reconstruction_ready": true
}
```

- The artifact records:
  - topology reconstruction readiness
  - metric mesh readiness
  - printability validation readiness
  - source surface patch count
  - source measured point count
  - contributing frame count
  - measured-high ratio
  - measured surface ratio
  - repaired surface ratio
  - point spacing
  - metric bounds
  - per-patch topology class
  - provenance schema including `visual_only` as never allowed in metric meshes
- Added hard gates for:
  - missing/blocked surface reconstruction
  - non-metric surface reconstruction
  - surface not ready for topology
  - low surface patch count
  - low source point count
  - low measured-high ratio
  - low measured surface ratio
  - any repaired surface before a repair stage exists
  - rejected or visual-only patch provenance
  - missing/high point spacing
  - missing metric bounds
- Added tests for:
  - missing surface reconstruction
  - blocked/non-metric surface reconstruction
  - accepted measured surface admission with metric mesh still blocked
  - sparse or repaired measured surface failure
  - end-to-end pipeline stage ordering with `mesh_topology`

Why this matters:

- The scanner now has a machine-readable admission contract before triangle
  generation. Later mesh generation cannot claim metric output unless measured
  surface support, provenance, spacing, and repaired-area gates are already
  satisfied.
- This is still not a triangle mesh. It does not create faces, edges, holes,
  repairs, STL, 3MF, or workspace handoff. It only proves that the measured
  surface evidence is eligible for a real topology generator.
- Metric mesh readiness remains false by design. Printability validation
  readiness remains false by design. Export and slicer handoff remain blocked.

Still blocked by design:

- No triangle mesh has been generated yet.
- No watertightness/manifold/self-intersection validation exists yet.
- No hole repair, provenance coloring, printability report, STL export, 3MF
  export, or workspace handoff exists yet.
- Topology admission is not printability. It is the gate before triangle
  generation and mesh validation.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMeshTopologyTest' \
  --tests 'com.mobileslicer.scanner.ScannerSurfaceReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Metric Mesh Candidate Boundary

Completed:

- Added a measured-only triangle mesh candidate artifact:

```text
mesh/metric_mesh_candidate.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerMetricMeshCandidate.kt
```

- Added the stage to the end-to-end local pipeline after `mesh_topology`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
```

- The mesh candidate stage consumes:

```text
mesh/mesh_topology.json
dense/dense_point_cloud.json
```

  and refuses to proceed unless topology is already:

```json
{
  "allowed": true,
  "metric": true,
  "topology_reconstruction_ready": true
}
```

  and the dense point cloud is already:

```json
{
  "allowed": true,
  "metric": true
}
```

- The artifact records:
  - triangle mesh candidate readiness
  - metric mesh readiness
  - printability validation readiness
  - measured vertex count
  - candidate triangle count
  - measured-high ratio
  - repaired surface ratio
  - max candidate triangle edge
  - min candidate triangle area
  - metric bounds
  - measured source vertices
  - measured candidate triangles
  - explicit contract that STL/3MF and workspace handoff remain blocked
- Added hard gates for:
  - missing/blocked topology
  - topology not metric
  - topology not ready
  - missing/blocked dense point cloud
  - dense point cloud not metric
  - low vertex count
  - low candidate triangle count
  - low measured-high ratio
  - repaired surface ratio above zero before repair exists
  - rejected or visual-only vertex provenance
  - invalid triangle provenance
  - high triangle edge length
  - low triangle area
  - missing metric bounds
- Added tests for:
  - missing topology
  - missing dense point cloud
  - blocked/non-metric topology
  - accepted measured triangle candidate with metric mesh still blocked
  - rejected visual-only or repaired evidence
  - end-to-end pipeline stage ordering with `metric_mesh_candidate`

Why this matters:

- This is the first stage that creates measured triangle candidates, but it is
  still intentionally not a printable mesh. Vertices come from measured dense
  point evidence only. No AI fill, no repair, no hole fill, and no synthetic
  fallback geometry are created.
- The artifact gives later validation code a concrete object to inspect without
  letting the app export STL/3MF early.
- `metric_mesh_ready=false` and `printability_validation_ready=false` remain
  hardcoded by design until topology validation, provenance validation,
  watertightness/manifold checks, repair reporting, and slicer-load gates exist.

Still blocked by design:

- No validated metric mesh exists yet.
- No watertightness/manifold/self-intersection validation exists yet.
- No hole repair, provenance coloring, printability report, STL export, 3MF
  export, or workspace handoff exists yet.
- Candidate triangles are not enough for printing. They are the measured input
  to the validation and repair stages.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMetricMeshCandidateTest' \
  --tests 'com.mobileslicer.scanner.ScannerMeshTopologyTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Mesh Validation Boundary

Completed:

- Added a mesh validation artifact:

```text
mesh/mesh_validation.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerMeshValidation.kt
```

- Added the stage to the end-to-end local pipeline after
  `metric_mesh_candidate`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
```

- The validation stage consumes:

```text
mesh/metric_mesh_candidate.json
```

  and refuses to proceed unless the candidate is already:

```json
{
  "allowed": true,
  "metric": true,
  "triangle_mesh_candidate_ready": true
}
```

- The artifact records:
  - topology validation readiness
  - metric mesh readiness
  - printability validation readiness
  - vertex count
  - triangle count
  - unique edge count
  - boundary edge count
  - non-manifold edge count
  - degenerate triangle count
  - visual-only/rejected element count
  - repaired surface ratio
- Added hard gates for:
  - missing/blocked mesh candidate
  - non-metric mesh candidate
  - candidate not ready
  - low vertex count
  - low triangle count
  - boundary edges
  - non-manifold edges
  - degenerate triangles
  - visual-only or rejected provenance
  - repaired surface ratio above zero before repair exists
- Added tests for:
  - missing metric mesh candidate
  - open candidate blocked by boundary edges
  - closed measured tetrahedron candidate accepted by topology validation
  - visual-only provenance blocking
  - end-to-end pipeline stage ordering with `mesh_validation`

Why this matters:

- This is the first topology validation gate after measured triangle candidate
  generation. It checks that a candidate mesh is closed at the edge level and
  rejects non-manifold, degenerate, repaired, or visual-only geometry before any
  printability report or export path can exist.
- Even when topology validation passes, STL/3MF export and workspace handoff
  remain blocked. A closed measured candidate still needs printability analysis,
  slicer-load validation, units/scale verification, repair reporting, and final
  export gates.

Still blocked by design:

- No printability report exists yet.
- No slicer-load validation exists yet.
- No STL export, 3MF export, or workspace handoff exists yet.
- Mesh validation is necessary, but it is not enough to claim a printable scan.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMeshValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerMetricMeshCandidateTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Printability Report Boundary

Completed:

- Added a printability report artifact:

```text
reports/printability_report.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerPrintabilityReport.kt
```

- Added the stage to the end-to-end local pipeline after `mesh_validation`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
printability_report
```

- The printability stage consumes:

```text
mesh/mesh_validation.json
```

  and refuses to proceed unless validation is already:

```json
{
  "allowed": true,
  "metric": true,
  "metric_mesh_ready": true,
  "printability_validation_ready": true
}
```

- The artifact records:
  - printability report readiness
  - recommendation for printing
  - slicer-load validation readiness
  - export readiness
  - workspace handoff readiness
  - watertight/manifold topology result
  - vertex and triangle counts
  - degenerate triangle count
  - repaired surface ratio
  - scale confidence when available
  - explicit handoff contract with STL/3MF/workspace still false
- Added hard gates for:
  - missing/blocked mesh validation
  - non-metric mesh validation
  - metric mesh not ready
  - printability validation not ready
  - low vertex count
  - low triangle count
  - boundary edges
  - non-manifold edges
  - degenerate triangles
  - repaired surface ratio above zero before repair reporting exists
- Added tests for:
  - missing mesh validation
  - failed mesh validation
  - accepted printability report with export still blocked
  - end-to-end pipeline stage ordering with `printability_report`

Why this matters:

- The scanner now has a machine-readable printability report boundary before
  export. This is the stage where measured mesh topology becomes a reportable
  print candidate, but the app still refuses to export or hand off until the
  slicer-load gate exists.
- The report keeps the distinction between "topology appears printable" and
  "the slicer accepted this model as a valid import." That distinction is
  required by the no-fake-mesh rule.

Still blocked by design:

- No slicer-load validation exists yet.
- No STL export, 3MF export, or workspace handoff exists yet.
- No repair report exists yet.
- A printability report is not final permission to send a model to the slicer.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerPrintabilityReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerMeshValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Slicer-Load Validation Boundary

Completed:

- Added a slicer-load validation artifact:

```text
reports/slicer_load_validation.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerSlicerLoadValidation.kt
```

- Added the stage to the end-to-end local pipeline after
  `printability_report`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
printability_report
slicer_load_validation
```

- The slicer-load validation stage consumes:

```text
reports/printability_report.json
mesh/metric_mesh_candidate.json
```

  and refuses to proceed unless the printability report is already:

```json
{
  "allowed": true,
  "metric": true,
  "printability_report_ready": true,
  "recommended_for_printing": true
}
```

  and the mesh candidate is already:

```json
{
  "allowed": true,
  "metric": true,
  "triangle_mesh_candidate_ready": true
}
```

- The artifact records:
  - slicer-load validation readiness
  - export readiness
  - workspace handoff readiness
  - vertex count
  - triangle count
  - units
  - scale confidence
  - internal slicer import contract readiness
  - explicit validated contract with STL/3MF/workspace still false
- Added hard gates for:
  - missing/blocked printability report
  - non-metric printability report
  - report not recommended for printing
  - missing/blocked metric mesh candidate
  - candidate not metric
  - candidate not ready
  - vertex count mismatch between report and candidate
  - triangle count mismatch between report and candidate
  - low vertex count
  - low triangle count
  - visual-only or rejected candidate geometry
  - non-millimeter units
  - low scale confidence when a minimum is configured
  - internal slicer import contract not ready
- Added tests for:
  - missing printability report
  - missing metric mesh candidate
  - failed printability report
  - count mismatch
  - visual-only geometry
  - accepted internal slicer-load contract with export still blocked
  - end-to-end pipeline stage ordering with `slicer_load_validation`

Why this matters:

- The scanner now distinguishes "printability report passed" from "the app has
  a validated internal slicer import contract." That is the last required gate
  before an export writer can exist.
- Even when slicer-load validation passes, STL/3MF export and workspace handoff
  remain blocked. The next implementation must be a metric export writer that
  consumes this validation artifact and refuses everything else.

Still blocked by design:

- No STL export exists yet.
- No 3MF export exists yet.
- No workspace handoff exists yet.
- No repair report exists yet.
- Slicer-load validation is permission to build an export writer, not permission
  to bypass export validation.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSlicerLoadValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerPrintabilityReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Metric Export Writer Boundary

Completed:

- Added gated metric export artifacts:

```text
outputs/model_metric.stl
outputs/model_metric.3mf
reports/export_manifest.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerMetricExport.kt
```

- Added the stage to the end-to-end local pipeline after
  `slicer_load_validation`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
printability_report
slicer_load_validation
metric_export
```

- The metric export writer consumes:

```text
reports/slicer_load_validation.json
mesh/metric_mesh_candidate.json
```

  and refuses to proceed unless slicer-load validation is already:

```json
{
  "allowed": true,
  "metric": true,
  "slicer_load_validated": true
}
```

  and the mesh candidate is already:

```json
{
  "allowed": true,
  "metric": true,
  "triangle_mesh_candidate_ready": true
}
```

- The export writer now creates:
  - ASCII STL in millimeters
  - minimal 3MF package in millimeters
  - export manifest with source validation paths
  - explicit workspace handoff contract with handoff still false
- Added hard gates for:
  - missing/blocked slicer-load validation
  - non-metric slicer-load validation
  - missing/blocked metric mesh candidate
  - candidate not metric
  - candidate not ready
  - low vertex count
  - low triangle count
  - rejected/visual-only vertex provenance
  - rejected/visual-only triangle provenance
  - invalid triangle vertex references
- Added tests for:
  - missing slicer-load validation
  - missing metric mesh candidate
  - failed slicer-load validation
  - valid STL and 3MF export with workspace handoff still blocked
  - generated STL parsing through the existing viewer STL parser
  - 3MF ZIP structure checks
  - visual-only candidate geometry rejection
  - end-to-end pipeline stage ordering with `metric_export`

Why this matters:

- The scanner can now produce actual metric STL and 3MF files, but only from the
  validated metric candidate path. There is still no fallback geometry and no
  fake success state.
- Workspace handoff remains blocked by design. Exported files are artifacts;
  importing them into the workspace still requires the next handoff validation
  stage.

Still blocked by design:

- No workspace handoff exists yet.
- No scan repair report exists yet.
- The 3MF export is intentionally minimal and metric; richer 3MF metadata can be
  added after the handoff gate is stable.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMetricExportTest' \
  --tests 'com.mobileslicer.scanner.ScannerSlicerLoadValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Workspace Handoff Validation Boundary

Completed:

- Added a scanner workspace handoff validation artifact:

```text
reports/workspace_handoff.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerWorkspaceHandoff.kt
```

- Added the stage to the end-to-end local pipeline after `metric_export`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
printability_report
slicer_load_validation
metric_export
workspace_handoff
```

- The handoff stage consumes:

```text
reports/export_manifest.json
```

  and refuses to proceed unless export is already:

```json
{
  "allowed": true,
  "metric": true,
  "stl_exported": true,
  "three_mf_exported": true
}
```

- The artifact records:
  - workspace handoff readiness
  - metric STL path
  - metric 3MF path
  - vertex count
  - triangle count
  - units
  - validated handoff contract
- Added hard gates for:
  - missing export manifest
  - blocked/non-metric export manifest
  - missing STL export
  - missing 3MF export
  - missing or empty exported files
  - low vertex count
  - low triangle count
  - non-millimeter units
- Added tests for:
  - missing export manifest
  - failed export manifest
  - accepted validated metric exports
  - end-to-end pipeline stage ordering with `workspace_handoff`

Why this matters:

- The scanner now has a complete local artifact chain from capture package
  staging through metric STL/3MF export and scanner-side workspace handoff
  validation.
- This stage still does not mutate shared workspace state. That is intentional
  while other code may be in motion. The scanner now produces a validated
  handoff manifest that a future workspace integration can consume.

Still blocked by design:

- The shared workspace UI/import code is not invoked by the scanner gate yet.
- Real-world scan quality still depends on completing true dense reconstruction
  beyond the current measured candidate scaffolding and benchmark capture sets.
- Repair reporting is still not implemented.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerWorkspaceHandoffTest' \
  --tests 'com.mobileslicer.scanner.ScannerMetricExportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Repair Report Boundary

Completed:

- Added a repair report artifact:

```text
reports/repair_report.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerRepairReport.kt
```

- Added the stage to the end-to-end local pipeline after `mesh_validation` and
  before `printability_report`:

```text
workspace
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
repair_report
printability_report
slicer_load_validation
metric_export
workspace_handoff
```

- The repair report stage consumes:

```text
mesh/mesh_validation.json
```

  and refuses to proceed unless mesh validation is already:

```json
{
  "allowed": true,
  "metric": true,
  "metric_mesh_ready": true
}
```

- The artifact records:
  - repair report readiness
  - whether repair was applied
  - repaired surface ratio
  - repaired hole count
  - largest repaired hole size
  - visual-only element count
  - explicit repair contract
- Added hard gates for:
  - missing/blocked mesh validation
  - non-metric mesh validation
  - metric mesh not ready
  - repaired surface without an explicit repair pipeline
  - visual-only geometry in a metric repair report
- Printability now requires `reports/repair_report.json` before it can pass.
- Added tests for:
  - missing mesh validation
  - validated mesh with no repair
  - repaired surface blocking printability
  - printability consuming repair report
  - end-to-end pipeline stage ordering with `repair_report`

Why this matters:

- The scanner can no longer jump from topology validation to printability without
  a machine-readable statement about repairs. This is required for the "no fake
  mesh" rule because filled, inferred, or repaired regions must be reported
  before any printable handoff.

Still blocked by design:

- No actual repair algorithm exists yet.
- Repaired geometry remains blocked for metric printability until an explicit
  conservative repair pipeline and thresholds are implemented.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerRepairReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerPrintabilityReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

The earlier unrelated `FindAndImportModelScreen.kt` compile blocker is no
longer present in the current workspace state. Scanner-scoped diff check is
clean.

### 2026-05-08 M3 Depth Fusion Evidence Boundary

Completed:

- Added depth staging to the local reconstruction workspace. Accepted frames now
  preserve optional depth and depth-confidence evidence:

```text
depth/000001_depth16.png
depth/000001_confidence.png
```

- Added a depth fusion evidence artifact:

```text
depth/depth_fusion.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDepthFusion.kt
```

- Added the stage to the end-to-end local pipeline immediately after workspace
  staging:

```text
workspace
depth_fusion
feature_match_graph
feature_tracks
pose_initialization
pose_refinement
sparse_reconstruction_inputs
metric_pose_graph
bundle_adjustment_diagnostics
metric_pose_solve
metric_pose_optimizer
sparse_triangulation
dense_reconstruction
dense_point_cloud
surface_reconstruction
mesh_topology
metric_mesh_candidate
mesh_validation
repair_report
printability_report
slicer_load_validation
metric_export
workspace_handoff
```

- The depth fusion stage consumes:

```text
reconstruction_job.json
depth/*.png
```

  and records:
  - measured depth frame count
  - average depth coverage
  - staged depth hashes
  - staged confidence hashes when available
  - dense reconstruction assist readiness
- Added hard gates for:
  - missing reconstruction workspace
  - low depth frame count
  - low average depth coverage
  - missing confidence images when confidence is required
  - missing staged depth hashes
- Added tests for:
  - missing workspace
  - workspace without depth frames
  - accepted measured depth evidence
  - reconstruction workspace staging depth and confidence files
  - reconstruction workspace preserving camera pose evidence when present
  - depth fusion carrying camera pose evidence forward with depth frames
  - end-to-end pipeline stage ordering with `depth_fusion`
- Scanner package export now marks `has_depth=true` and uses the `hybrid`
  capture profile when captured frames include depth files.

Why this matters:

- The scanner no longer drops captured depth evidence during reconstruction
  staging. Depth can now become measured assist input for dense reconstruction
  without being treated as standalone geometry or a fake mesh.
- The scanner no longer drops captured pose evidence during reconstruction
  staging. Future raw-depth back-projection has a real pose field to consume
  instead of relying on implicit state.

Still blocked by design:

- Depth fusion does not generate a mesh.
- Depth fusion does not replace photogrammetry, pose validation, topology
  validation, repair report, printability, export, or workspace handoff gates.
- Current camera capture still needs real depth file production on devices that
  support it.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDepthFusionTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionWorkspaceTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

### 2026-05-08 M3 Device Depth Capability Diagnostics

Completed:

- Added camera depth capability reporting to scan package diagnostics:

```text
diagnostics/device_capabilities.json
```

- Added the implementation in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDeviceDepthCapability.kt
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDiagnostics.kt
```

- `device_capabilities.json` now records:
  - whether a Camera2 depth capability check ran
  - whether the back camera was found
  - whether `REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT` is advertised
  - the reason depth output is or is not considered available
- Added test coverage for:
  - missing depth capabilities
  - absent depth output capability
  - present depth output capability
  - diagnostics JSON when Android context is unavailable
  - diagnostics JSON for a checked, supported depth-capable camera

Why this matters:

- Depth capture must be honest. The app can now record whether local depth is
  blocked by missing device capability versus simply missing captured depth
  frames. That keeps depth evidence auditable and prevents generic status text
  from hiding device limitations.

Still blocked by design:

- This does not capture depth images yet.
- This does not prove ARCore Raw Depth support. Camera2 depth output and ARCore
  Depth API support are related device signals, but they are not identical.
- Real capture must still write synchronized depth and confidence frames before
  `depth_fusion` can assist dense reconstruction.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDeviceDepthCapabilityTest' \
  --tests 'com.mobileslicer.scanner.ScannerDiagnosticsTest'
```

Current result:

```text
BUILD SUCCESSFUL
```

Scanner-owned diff check:

```text
git diff --check -- \
  android-app/app/src/main/java/com/mobileslicer/scanner \
  android-app/app/src/test/java/com/mobileslicer/scanner \
  docs/scanner
```

Current result:

```text
clean
```

### 2026-05-08 Capture UI Containment And Depth Status

Completed:

- Replaced the fixed `560.dp` camera preview height with a responsive scanner
  viewport:

```text
target height = 42% of screen height
minimum = 300 dp
maximum = 430 dp
```

- Surfaced depth capability status directly in the capture HUD and workflow
  panel.
- Replaced hardcoded `depth_pose=0` package readiness with a measured score
  based on accepted frames that include camera pose and depth coverage.
- Kept the screen scanner-owned and did not alter workspace/import behavior.

Why this matters:

- The capture UI must keep the camera framed inside usable bounds on tall phones
  instead of letting the preview dominate the whole workflow.
- The operator can now see whether the current device advertises depth support
  while still capturing photo evidence.
- Future AR/depth capture frames will now move package readiness instead of
  being flattened to zero in diagnostics.

Still blocked by design:

- UI containment does not create depth images.
- Real ARCore synchronized capture remains a separate implementation step.
- Workspace handoff remains controlled by metric export and validation gates.

### 2026-05-08 M3 Depth Back-Projection Primitive

Completed:

- Added measured-depth back-projection primitives in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerDepthBackProjection.kt
```

- Added tests in:

```text
android-app/app/src/test/java/com/mobileslicer/scanner/ScannerDepthBackProjectionTest.kt
```

- The primitive supports:
  - raw depth pixel samples
  - unsigned 8-bit confidence values
  - intrinsics-based camera-space projection
  - optional `pose_world_from_camera` transform into world coordinates
  - confidence-based provenance classes
  - depth coverage scoring
  - uniform depth sampling
  - voxel quantization keys for future duplicate measured-point suppression

Why this matters:

- ARCore Raw Depth produces per-pixel depth and confidence. This primitive is
  the measured math needed to convert those pixels into metric world-space
  points without hallucinating geometry.
- This is a required building block before real depth fusion can improve the
  dense point cloud.

Still blocked by design:

- This does not read ARCore `Image` objects yet.
- This does not create a mesh.
- Depth points remain measured assist evidence; photogrammetry, topology,
  printability, export, and workspace handoff gates still control success.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.*'

./gradlew ':app:buildCMakeDebug[arm64-v8a]' \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  :app:assembleDebug \
  :app:lintDebug

git diff --check -- \
  android-app/app/src/main/java/com/mobileslicer/scanner \
  android-app/app/src/test/java/com/mobileslicer/scanner \
  docs/scanner
```

Current result:

```text
BUILD SUCCESSFUL
scanner-owned diff check clean
```

### 2026-05-08 M4 ARCore Raw Depth Capture Boundary

Completed:

- Added ARCore as an optional dependency:

```text
com.google.ar:core:1.53.0
```

- Declared ARCore optional in the manifest:

```xml
<meta-data
    android:name="com.google.ar.core"
    android:value="optional" />
```

- Added raw-depth capture boundary code in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerArCoreRawDepthCapture.kt
```

- The boundary supports:
  - ARCore availability checks
  - fail-closed raw-depth session configuration
  - `RAW_DEPTH_ONLY` when supported, `AUTOMATIC` as fallback
  - synchronized `Frame.acquireCameraImage()`
  - `Frame.acquireRawDepthImage16Bits()`
  - `Frame.acquireRawDepthConfidenceImage()`
  - ARCore camera pose preservation
  - ARCore camera intrinsics preservation
  - RGB JPEG writing
  - 16-bit depth preservation in lossless RGBA PNG channels
  - confidence PNG writing
  - `ScanFrame` creation with depth, confidence, pose, intrinsics, and quality
    evidence

- Added ARCore availability status to:

```text
diagnostics/device_capabilities.json
```

Why this matters:

- The app now has a real ARCore Raw Depth capture boundary instead of only a
  generic "depth later" placeholder. This is the Android API surface that can
  feed measured depth evidence into the local reconstruction workspace.
- ARCore remains optional. Non-AR devices stay on calibrated photo capture.

Still blocked by design:

- The Compose screen still uses CameraX high-resolution capture as the primary
  visible capture path.
- ARCore capture needs a preview/session owner before it is exposed as the main
  capture button. ARCore owns the camera while running, so it must be scheduled
  deliberately instead of being bolted onto CameraX.
- Depth remains measured assist evidence. It does not bypass photogrammetry,
  calibration, topology, printability, export, or workspace handoff gates.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDiagnosticsTest' \
  --tests 'com.mobileslicer.scanner.ScannerDepthBackProjectionTest'

./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.*'

./gradlew ':app:buildCMakeDebug[arm64-v8a]' \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  :app:assembleDebug \
  :app:lintDebug

git diff --check -- \
  android-app/app/src/main/java/com/mobileslicer/scanner \
  android-app/app/src/test/java/com/mobileslicer/scanner \
  docs/scanner \
  android-app/app/build.gradle.kts \
  android-app/gradle/libs.versions.toml \
  android-app/app/src/main/AndroidManifest.xml
```

Current result:

```text
BUILD SUCCESSFUL
diff check clean
```

### 2026-05-08 M4 ARCore Capture Mode And Depth Assist Handoff

Completed:

- Added one combined scanner capture path with automatic evidence selection:

```text
ARCore raw depth + RGB + pose when supported
CameraX photo package fallback when ARCore is unavailable
```

- Added scanner-owned ARCore preview/session ownership in:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/ScannerArCoreCaptureView.kt
```

- The ARCore view:
  - owns the ARCore `Session`
  - configures raw depth through the scanner depth config gate
  - owns the external camera texture
  - renders the camera stream through `GLSurfaceView`
  - captures keyframes on the GL thread from the same ARCore `Frame`
  - closes the ARCore session when the scanner leaves AR mode
- Updated the scanner UI:
  - removed the user-facing Photo vs AR Depth decision
  - presents the scanner as one capture workflow
  - uses ARCore raw depth automatically on supported devices
  - keeps the CameraX photo package path as a device-capability fallback
  - unbinds CameraX before ARCore owns the camera
  - capture status now shows the evidence profile without asking users to pick
    an implementation detail
- Updated depth fusion:
  - decodes the lossless encoded depth PNG
  - reads confidence PNG values
  - scales camera intrinsics from RGB resolution to depth resolution
  - back-projects depth pixels into metric world-space points
  - deduplicates points by 1 mm voxel key
  - writes `back_projected_points` to:

```text
depth/depth_fusion.json
```

- Updated dense point-cloud construction:
  - consumes `depth/depth_fusion.json`
  - imports ARCore raw-depth points only when sparse dense admission is already
    metric and allowed
  - labels depth assist points with `arcore_raw_depth:<frame_id>` provenance
- Fixed scan-package capability metadata:
  - `has_arcore_poses` is derived from frame pose matrices
  - package validation now rejects stale `has_arcore_poses`, `has_depth`, or
    `has_masks` flags that disagree with frame metadata

Device log audit:

- Test scan `scan_62ca9447-4735-4595-add8-7599a383af98` captured 36 frames.
- 16 frames were accepted and 20 were rejected.
- Rejection reasons were quality-gate driven:
  - `insufficient_depth_coverage`
  - `clipped_highlights`
  - `too_blurry`
- The scan package wrote RGB frames, raw-depth PNGs, confidence PNGs, masks,
  per-frame pose matrices, hashes, diagnostics, and an export zip.
- Local reconstruction correctly blocked workspace handoff because the test
  scan did not meet printable gates:
  - not enough accepted frames
  - missing high-ring pass
  - no verified scale
  - no calibration
  - low scale confidence

Why this matters:

- ARCore depth is no longer just a helper class. It now participates in the
  single scanner workflow with camera ownership, preview, synchronized keyframe
  capture, package staging, depth fusion, and dense point-cloud handoff.
- Depth still cannot create a printable model by itself. It is measured assist
  evidence layered onto calibrated photogrammetry.

Still blocked by design:

- AR Depth mode needs on-device validation across ARCore Depth devices before
  it can be considered reliable.
- The OpenGL camera preview is intentionally basic; UX polish should happen
  after capture correctness is proven on device.
- Dense reconstruction is still conservative. Depth assist points improve the
  point cloud only after sparse metric evidence has passed.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDepthBackProjectionTest' \
  --tests 'com.mobileslicer.scanner.ScannerDepthFusionTest' \
  --tests 'com.mobileslicer.scanner.ScannerDensePointCloudTest'

./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.*'

./gradlew ':app:buildCMakeDebug[arm64-v8a]' \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  :app:assembleDebug \
  :app:lintDebug

git diff --check -- \
  android-app/app/src/main/java/com/mobileslicer/scanner \
  android-app/app/src/test/java/com/mobileslicer/scanner \
  docs/scanner \
  android-app/app/build.gradle.kts \
  android-app/gradle/libs.versions.toml \
  android-app/app/src/main/AndroidManifest.xml
```

Current result:

```text
BUILD SUCCESSFUL
diff check clean
```

### 2026-05-08 M4 Calibration-First Printable Readiness UX

Completed:

- Made the marker mat a first-class scanner step instead of hidden diagnostics.
- Renamed the prep action to `Generate marker mat`.
- The app now writes marker mat assets to public Downloads through MediaStore on
  modern Android:

```text
Downloads/MobileSlicer/scanner-marker-mat/
```

- The marker mat generator now creates calibrated standard paper-size assets:
  - `mobile_slicer_marker_mat_a4.svg`
  - `mobile_slicer_marker_mat_a4_layout.json`
  - `mobile_slicer_marker_mat_us_letter.svg`
  - `mobile_slicer_marker_mat_us_letter_layout.json`
  - `README.txt`
- The prep screen now explains what the marker mat is:
  - printable guide sheet
  - A4 and US Letter variants
  - AprilTag marker ring around the object area
  - 100 mm scale bar
  - print at 100% scale
  - keep tags visible around the object
- Added calibration readiness UI:
  - scale bar state
  - marker detector state
  - marker observation count
  - marker frame coverage
  - reprojection error
  - scale confidence
  - printable readiness
- Added capture overlay chips for:
  - scale readiness
  - marker readiness
- Review and reconstruct now show exact marker/scale blockers after export,
  including:
  - no marker detections
  - missing reprojection
  - detector not ready
  - too few marker observations
  - low scale confidence
  - printed scale outside tolerance

Why this matters:

- Printable scanning now teaches the user what proof the app needs before it can
  attempt reconstruction.
- The marker mat path is visible and pullable from the device for audit/testing.
- A scan can still capture without the mat, but printable reconstruction remains
  blocked until marker/scale evidence passes.

Verification status:

```text
./gradlew :app:compileDebugKotlin
BUILD SUCCESSFUL
```

### 2026-05-08 Calibration Deferred, Not Removed

Decision:

- Calibration remains part of the product architecture and release gate.
- Calibration is deferred for now so implementation can focus on the core local
  scanner path:
  - guided capture
  - local AI masking/guidance
  - feature matching and pose solving
  - dense reconstruction
  - mesh validation/export boundaries
- During this deferral, non-calibrated scans are allowed only as unverified scan
  evidence or reconstruction experiments.
- The app must not claim calibrated printable scale without:
  - verified scale source
  - marker or equivalent scale evidence
  - reprojection/residual report
  - printability validation

Large-object calibration is also deferred, not rejected. The later workflow must
support individual tag cards, multiple sheets, and long measured scale references
for objects larger than A4/US Letter mats.

Next engineering focus after this decision:

```text
core local reconstruction quality before calibration polish
```

Specifically:

1. Keep the scanner capture flow usable without forcing marker-mat setup.
2. Keep metric/slicer-ready handoff blocked unless validation passes.
3. Build the next real reconstruction step that improves measured geometry,
   not UI-only readiness.

### 2026-05-08 M3 Calibration-Deferred Sparse Preview

Completed:

- Updated the local reconstruction pipeline default so calibration can be
  deferred without blocking all reconstruction work at workspace creation.
- The workspace/preflight stage can now stage uncalibrated capture evidence for
  reconstruction experiments when:
  - enough accepted frames exist
  - required capture passes exist
  - masks exist
  - camera intrinsics exist
  - material/forced-frame gates pass
- Strict metric stages still require verified scale and marker evidence. This
  means calibration deferral does not unlock slicer-ready output.
- Added a new reconstruction artifact:

```text
sparse/experimental_sparse_preview.json
```

- The artifact records:
  - feature track count
  - AR depth back-projected preview point count
  - non-metric bounding box for inspection
  - preview points from measured depth assist when available
  - explicit contract that metric scale, STL/3MF export, and workspace handoff
    are not allowed
- Added the stage to the pipeline summary as:

```text
experimental_sparse_preview
```

Why this matters:

- We can keep improving the local reconstruction core while calibration is
  deferred.
- Real captured evidence can now produce inspectable sparse/depth geometry
  artifacts instead of stopping at calibration setup.
- The implementation still refuses to call this printable, metric, or
  slicer-ready.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerExperimentalSparsePreviewTest' \
  --tests 'com.mobileslicer.scanner.ScannerLocalReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest' \
  :app:compileDebugKotlin

BUILD SUCCESSFUL
```

### 2026-05-09 M3 Metric Pose Admission Unblocked

Completed:

- Split sparse reconstruction input readiness from metric triangulation output:
  - prepared feature-track inputs can now be `allowed=true`
  - metric 3D sparse points still remain blocked until optimized poses exist
  - the artifact still records `metric=false` and `triangulation_ready=false`
    before the optimizer/triangulation stages pass
- Updated bundle adjustment diagnostics from a permanent fail-closed placeholder
  into an optimizer admission gate:
  - residual diagnostics must pass
  - pose candidate coverage must pass
  - marker residual and scale residual must pass
  - native optimization is still required before final metric sparse points
- Updated metric pose solve so it can accept a connected calibrated pose graph
  when:
  - pose refinement passes
  - feature tracks pass
  - sparse track inputs are prepared
  - metric pose graph constraints pass
  - bundle/optimizer admission diagnostics pass
  - marker scale and reprojection gates pass
- Removed the unconditional blockers:

```text
metric_pose_solver_not_implemented
dense_reconstruction_blocked_until_metric_pose_solve
global_bundle_adjustment_not_implemented
metric_pose_acceptance_blocked_until_global_optimization
```

- Added positive test coverage proving that a connected calibrated pose graph can
  now produce:

```json
{
  "allowed": true,
  "metric": true,
  "solver_status": "accepted"
}
```

Why this matters:

- The local pipeline is no longer artificially stopped at "not implemented"
  once the required evidence gates pass.
- The optimizer can now receive real prepared sparse tracks, pose candidates,
  calibration evidence, and residual diagnostics instead of being blocked by
  upstream placeholder states.
- This is still not permission to fake dense geometry. The optimizer and sparse
  triangulation gates must still produce accepted metric sparse points before
  dense reconstruction, mesh generation, STL/3MF export, or workspace handoff can
  pass.

Still blocked by design:

- Uncalibrated scans remain non-metric experiments only.
- Sparse triangulation still requires accepted optimized metric poses and
  optimized sparse points.
- Dense reconstruction, mesh validation, export, and handoff still depend on the
  downstream metric artifacts proving their gates.
- The current bundle stage is an optimizer admission gate, not a full
  production-grade Ceres bundle adjustment replacement.

Verification status:

```text
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'

BUILD SUCCESSFUL
```

### 2026-05-09 M3 Native Optimizer Sparse Acceptance Path

Completed:

- Added an injectable native optimizer boundary to
  `optimizeScannerMetricPoses(...)` so the Android-native optimizer contract can
  be tested from host unit tests without weakening runtime behavior.
- Added positive optimizer test coverage proving that, when all gates pass, the
  optimizer can accept:
  - calibrated reconstruction job
  - accepted metric pose solve
  - prepared sparse feature tracks
  - accepted bundle/optimizer admission diagnostics
  - native optimized camera poses
  - native optimized sparse points
  - marker residuals, scale residuals, frame uncertainty, and pose conditioning
- The accepted optimizer artifacts now prove:

```json
{
  "allowed": true,
  "metric": true,
  "status": "accepted_metric_sparse_optimizer_output"
}
```

- Verified the downstream sparse triangulation gate can consume accepted
  optimizer output and produce:

```json
{
  "allowed": true,
  "metric": true,
  "dense_reconstruction_ready": true,
  "status": "accepted_metric_sparse_points"
}
```

Why this matters:

- The local pipeline now has a verified path from calibrated pose evidence to
  accepted optimized metric sparse points.
- This is the exact boundary dense reconstruction needs before it can honestly
  start. The app still cannot skip directly from capture to mesh; the sparse
  metric evidence has to pass first.
- Host tests now cover the native contract without requiring the Android `.so`
  to load in the JVM test process.

Still blocked by design:

- Real captured packages still need on-device validation through the Android
  native optimizer.
- Dense reconstruction must still prove measured point density, surface support,
  topology, printability, export, and workspace handoff gates.
- Uncalibrated packages remain non-metric experiments.

Verification status:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerPoseOptimizerTest' \
  --tests 'com.mobileslicer.scanner.ScannerSparseTriangulationTest'

BUILD SUCCESSFUL
```

### 2026-05-10 M3 Real-Device Reconstruction Audit Artifact

Completed:

- Added `reconstruction_audit.json` as a first-class local reconstruction
  artifact written beside `reconstruction_summary.json`.
- The audit report records:
  - scan id
  - workspace path
  - summary path
  - audit path
  - first failing stage
  - first failing stage index
  - raw blocker count
  - stage-by-stage artifact existence
  - raw blocker list
  - deterministic next actions
- The audit is written automatically by `runScannerLocalReconstructionPipeline`
  after every pipeline run, including preflight-blocked packages.
- The scanner reconstruct screen now surfaces the audit report path so a real
  device capture can be pulled and reviewed without guessing where the blocker
  lives.
- Added test coverage proving:
  - the audit file is written for full pipeline runs
  - the first failing stage is captured
  - stage artifact existence is recorded
  - preflight-blocked packages still write an audit
  - next actions include capture guidance when frame/pass evidence is
    insufficient

Why this matters:

- The next scanner work must be driven by real device evidence, not by more
  UI or downstream mesh gates.
- A captured package now produces a machine-readable answer to:

```text
What failed first?
Which artifact proves it?
What should we fix next?
```

- This keeps the project aligned with the no-fake-mesh rule. If local printable
  reconstruction is blocked, the app now records the exact blocked stage instead
  of hiding behind a generic reconstruction failure.

Next verification target:

- Capture a known matte object on a real Android device.
- Export the package.
- Run local reconstruction checks.
- Pull:

```text
reconstruction_summary.json
reconstruction_audit.json
depth/depth_fusion.json
features/feature_tracks.json
poses/metric_pose_solve.json
poses/optimized_metric_poses.json
sparse/sparse_triangulation.json
dense/dense_reconstruction.json
dense/dense_point_cloud.json
```

Helper command:

```bash
scripts/scanner_pull_latest_audit.sh
```

The helper uses `adb run-as com.mobileslicer` to archive the newest debug
workspace under `cache/scanner-workspaces/scan_*`, pulls it into
`scanner-audits/<scan_id>/`, and prints the summary/audit paths.

- Fix the first failing real-data stage before moving deeper into dense surface
  reconstruction or STL/3MF export.

### 2026-05-10 Capture Admission Tightened After Real Device Test

Completed:

- Removed auto capture from the scanner UI. Capture is manual-only:
  - tap object to lock/focus
  - press `Capture frame`
  - review accepted/rejected frames
- Fixed AR capture admission so raw depth is optional evidence, not a hard
  prerequisite for saving RGB + pose frames.
- Added an additional frame-admission gate after image quality analysis:
  - reject rapid duplicate captures
  - reject same-pass frames without enough view translation when AR pose exists
  - reject frames with very weak depth coverage when a depth frame is present
- Added explicit scanner logs for:
  - accepted/rejected state
  - blur score
  - exposure score
  - clipped/high-risk proxy
  - depth coverage
  - capture pass
  - rejection reasons
- Added test coverage proving rapid duplicate frames and weak-depth frames are
  rejected while separated viewpoints remain accepted.
- Added a capture-plan completion gate:
  - accepted frames count toward Mid, High, Low, and Detail targets
  - rejected diagnostic frames do not satisfy those targets
  - the capture button disables once all required accepted targets are complete
  - the user is steered to review/export instead of continuing to generate
    duplicate rejected frames
- Replaced the overly blunt same-pass view-change gate with a less punishing
  duplicate guard:
  - rapid captures are still rejected
  - near-identical same-pass AR poses are still rejected
  - the movement threshold is intentionally small enough that normal hand motion
    does not force dozens of wasted captures
- Added a retained-rejection cap per capture pass:
  - the app keeps the first rejected examples for diagnostics
  - repeated rejected frames after that cap are deleted from the session assets
  - the user gets movement guidance instead of an endlessly growing frame list
- Changed package export to keep reconstruction input compact:
  - accepted frames are copied into a clean package directory
  - rejected image/depth files are not exported by default
  - rejected frame IDs, passes, quality scores, and rejection reasons are written
    into diagnostics JSON
  - reconstruction operates on accepted evidence, not on a bloated mix of useful
    and known-bad frames
- Fixed the local reconstruction handoff after compact export:
  - export now records the clean package directory as well as the zip path
  - local reconstruction runs against that validated package directory
  - editing/deleting frames invalidates both the zip path and package directory
  - this prevents reconstruction from accidentally reading the raw camera session
    folder, which no longer contains `manifest.json`
- Added automated regression coverage for the compact export handoff:
  - `ScannerExportPipelineHandoffTest` builds a raw camera session with accepted
    and rejected frames
  - export must produce a validated clean package containing accepted frames only
  - rejected frames must be preserved as compact diagnostics
  - the local reconstruction pipeline must run from the returned clean package
    directory without package validation or missing-manifest errors
- Added a device smoke helper:
  - `scripts/scanner_device_smoke.sh`
  - runs scanner JVM tests
  - builds and installs the debug APK
  - clears logcat and launches the app
  - waits while the operator performs the physical camera scan
  - summarizes accepted/rejected/retained capture counts
  - prints export and pipeline result lines
  - pulls the newest reconstruction audit workspace

Why this matters:

- The scanner should never blindly accept every button press.
- Manual capture can be instant, but the program still has to reject frames that
  do not add useful measured evidence.
- More frames are not automatically better evidence. Once the planned coverage
  is complete, the next correct action is review, deletion of bad frames, export,
  and reconstruction audit.
- Rejected captures are useful as diagnostics, but they should not become heavy
  reconstruction payload unless a later debug mode explicitly asks for that.
- The package-dir handoff regression must be caught by unit tests. The physical
  scan still needs a person until we add synthetic camera/AR frame injection, but
  build/install/log/audit plumbing should be automated.
- This keeps the workflow aligned with the plan: capture quality and evidence
  gates come before reconstruction, dense surface work, export, or slicer
  handoff.

Automated scanner checks:

```bash
cd android-app
./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Synthetic no-camera regression:

```bash
scripts/scanner_synthetic_regression.sh
```

The synthetic regression path creates deterministic capture frames without
CameraX or ARCore, applies the same admission rules, exports the same compact
scan package format, writes rejected-capture diagnostics, and runs the local
reconstruction pipeline from the exported package directory. This is the default
regression path for package/export/reconstruction bugs so a person does not need
to physically rescan after every code change.

Device smoke test:

```bash
scripts/scanner_device_smoke.sh
```

The device smoke script does not fake camera input. It automates everything
around the physical scan and then produces the log/audit evidence needed for the
next implementation decision.

Synthetic capture does not prove autofocus, ARCore tracking, real depth,
lighting, glare, or material behavior. It is a regression harness for scanner
logic and package contracts. Real-device scans still gate camera UX and measured
evidence quality.

### 2026-05-10 Local AI Stack Contract And Synthetic Mask Regression

Completed:

- Added a single local AI stack status contract:
  - `ScannerLocalAiStackStatus`
  - `ScannerLocalAiComponentStatus`
  - `scannerLocalAiStackStatus`
  - `scannerLocalAiStackStatusJson`
- `diagnostics/local_ai_status.json` now records a top-level `stack` object with:
  - overall local AI state
  - production readiness
  - explicit `metric_truth=false`
  - component statuses
  - warnings
  - blocking reasons
- Local AI components are reported separately:
  - `tap_object_segmentation`
  - `capture_quality_guidance`
  - `monocular_depth_prior`
- MediaPipe MagicTouch remains the production object-mask path only when the
  bundled model matches the approved SHA-256 and byte count.
- Heuristic masks remain weak non-AI fallback evidence. They are not allowed to
  create or delete metric geometry.
- Depth Anything V2 Small remains disabled because no reviewed LiteRT/TFLite
  model asset is bundled yet. This is intentional; monocular AI depth is not
  metric truth.
- The synthetic no-camera regression now generates deterministic soft masks
  instead of bypassing masks entirely.
- Synthetic exported packages now validate:
  - `hasMasks=true`
  - mask files are copied into the clean package
  - local AI diagnostics include mask count
  - local AI diagnostics explicitly mark AI output as non-metric truth

Why this matters:

- The scanner plan requires local AI to help capture without pretending to be
  measured geometry.
- Before this step, the physical scan path could produce masks, but the
  synthetic no-camera regression did not exercise the mask/package/diagnostic
  contract. That made it too easy to break local AI packaging and only find out
  after a real phone scan.
- The stack contract makes the current local AI truth machine-readable:
  MediaPipe segmentation and quality guidance are implemented; optional
  monocular depth prior is not.
- This keeps the next implementation decisions honest. Local printable output is
  still blocked until measured photogrammetry, calibration, dense/surface
  reconstruction, mesh validation, and slicer handoff gates pass.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerDiagnosticsTest' \
  --tests 'com.mobileslicer.scanner.ScannerSyntheticCaptureTest' \
  --tests 'com.mobileslicer.scanner.ScannerSoftMaskTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Reconstruction Evidence Pass: Soft Masks And Debug Point Cloud

Completed:

- Changed reconstruction feature extraction so local AI masks are soft evidence,
  not hard object deletion:
  - features now carry `mask_support`
  - near-zero mask support can still reject obvious background
  - low but nonzero mask support keeps edge/thin-structure candidates alive
  - feature score is weighted by mask support instead of cropped by mask alone
- Added match diagnostics to `matches/match_graph.json`:
  - `average_descriptor_distance`
  - `spatial_cell_count`
  - `average_mask_support`
- Kept original RGB/image evidence as the source of truth. Masks only guide
  feature weighting and diagnostics.
- Added a debug point-cloud artifact:

```text
dense/debug_point_cloud.ply
```

- The PLY file is written only when measured dense point evidence exists.
- The point-cloud JSON now records:
  - `debug_point_cloud_ply`
  - `debug_point_cloud_printable=false`
  - `debug_point_cloud_slicer_handoff_allowed=false`
- PLY colors encode provenance:
  - green = `measured_high`
  - yellow = `measured_low`
  - red = unexpected/rejected class
- Added tests proving:
  - masks preserve soft edge candidates instead of hard-deleting them
  - match graph diagnostics include descriptor/spatial evidence
  - debug point clouds are inspectable but cannot be treated as printable output

Why this matters:

- This is the first reconstruction step that directly enforces the plan’s local
  AI policy in the geometry pipeline.
- The app can now inspect whether local AI masks are helping feature selection
  without letting the mask fabricate or erase metric geometry.
- A debug PLY gives us an auditable reconstruction artifact before mesh
  generation. It is deliberately not an STL/3MF and cannot go to the slicer.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerFeatureExtractionTest' \
  --tests 'com.mobileslicer.scanner.ScannerDensePointCloudTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Metric Pose Reliability And Synthetic Sparse Fixture

Completed:

- Strengthened the metric pose optimizer boundary:
  - optimizer artifacts now include `evidence_diagnostics`
  - diagnostics count candidate poses, fixed intrinsics, prepared tracks, marker
    corners, accepted match pairs, spatial match coverage, and mask support
  - blocking errors now produce actionable `blocker_hints`
- The native optimizer request now carries more of the reconstruction evidence:
  - fixed intrinsics by frame
  - candidate camera poses
  - feature track observations
  - feature track summary
  - match graph evidence
  - marker corner observations
  - marker scale constraint
  - bundle adjustment preflight residuals
- Added explicit optimizer gates for:
  - missing fixed intrinsics for pose candidates
  - missing accepted match pairs
  - weak accepted match spatial distribution
  - weak accepted match mask support
- Added `ScannerSyntheticMetricPoseFixture`:
  - creates a calibrated synthetic reconstruction workspace
  - writes metric pose candidates
  - writes feature tracks and match graph evidence
  - injects accepted native optimizer output under normal optimizer gates
  - runs sparse triangulation
  - runs dense reconstruction admission
  - writes a debug point cloud
  - keeps `printable=false` and `slicer_handoff_allowed=false`
- Added tests proving the metric sparse path can pass without a physical rescan
  while still blocking mesh/slicer handoff.

Why this matters:

- This gives us a repeatable metric-pose success fixture. That is different
  from pretending real phone scans are solved; the fixture proves the pipeline
  contracts and gates can carry valid metric evidence end-to-end.
- Real captures still need to satisfy the same gates. If a scan has weak
  tracks, missing scale, bad marker geometry, poor match spread, bad residuals,
  or native optimizer failure, it remains blocked.
- This is the correct bridge between capture/local AI and future dense/surface
  work: no dense mesh should be attempted until metric poses and measured sparse
  points are accepted.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerPoseOptimizerTest' \
  --tests 'com.mobileslicer.scanner.ScannerSyntheticMetricPoseFixtureTest' \
  --tests 'com.mobileslicer.scanner.ScannerSparseTriangulationTest' \
  --tests 'com.mobileslicer.scanner.ScannerDensePointCloudTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Measured Surface Candidate Artifact

Completed:

- Upgraded the surface reconstruction stage from admission-only JSON into a
  concrete measured surface candidate contract.
- Added:

```text
surface/measured_surface_candidate.json
```

- The surface candidate records:
  - source dense point count
  - measured-high ratio
  - measured surface ratio
  - repaired surface ratio
  - bounding box
  - candidate measured points
  - contributing frames
  - source point and source track ids
  - provenance class per candidate point
  - 4 x 4 coverage cells across the measured bounds
- `surface/surface_reconstruction.json` now records:
  - `surface_candidate`
  - `surface_candidate_printable=false`
  - `surface_candidate_slicer_handoff_allowed=false`
  - `coverage_cell_count`
  - coverage grid dimensions
- Surface coverage now includes point spacing, point count, and occupied
  coverage cells. A dense cluster in one corner cannot look as healthy as a
  broader measured surface.
- The synthetic metric pose fixture now runs through:
  - metric optimizer
  - metric sparse triangulation
  - dense reconstruction admission
  - dense point cloud
  - measured surface candidate generation
- The fixture still records:
  - `printable=false`
  - `slicer_handoff_allowed=false`

Why this matters:

- This is the next honest geometry artifact after the debug point cloud. It
  gives us measured surface evidence that can be audited without pretending we
  have a printable mesh.
- The candidate is not a triangle mesh, not an STL, not a 3MF, and not eligible
  for workspace handoff.
- Future topology/triangle stages can consume this surface candidate and its
  coverage/provenance diagnostics instead of guessing from raw point count.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSurfaceReconstructionTest' \
  --tests 'com.mobileslicer.scanner.ScannerSyntheticMetricPoseFixtureTest' \
  --tests 'com.mobileslicer.scanner.ScannerMeshTopologyTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Topology And Triangle Candidate Hardening

Completed:

- Topology admission now consumes the measured surface candidate artifact:

```text
surface/measured_surface_candidate.json
```

- `mesh/mesh_topology.json` now records:
  - `surface_candidate`
  - `candidate_point_count`
  - `coverage_cell_count`
  - `min_surface_coverage_cell_count`
- Topology blocks when measured surface coverage is too narrow. A high raw
  point count without enough occupied coverage cells is no longer enough to
  pass toward mesh generation.
- Topology blocks sparse measured candidates with:
  - `surface_candidate_point_count_low_for_topology`
  - `surface_coverage_cell_count_low`
- The triangle candidate stage now prefers measured surface candidate points
  over raw dense point cloud points. Dense points are only a fallback when the
  surface candidate artifact is absent.
- `mesh/metric_mesh_candidate.json` now records:
  - `coverage_cell_count`
  - `surface_candidate`
  - `min_coverage_cell_count`
- Triangle generation now builds local measured neighborhoods from grid-like
  surface rows and columns before falling back to sorted triples. This makes the
  candidate artifact more faithful to local surface evidence and less dependent
  on arbitrary point ordering.
- Candidate triangles still enforce:
  - measured-only vertex provenance
  - measured-only triangle provenance
  - maximum triangle edge length
  - minimum triangle area
  - zero repaired surface ratio
  - enough occupied coverage cells

Why this matters:

- This keeps the implementation aligned with the scanner plan: every step must
  carry measured evidence forward instead of turning a point cloud into a fake
  printable mesh.
- Coverage cells and per-point provenance are now part of the topology and
  triangle-candidate contract, so later validation can audit where geometry came
  from.
- The metric mesh is still not ready. STL/3MF export, printability validation,
  and workspace handoff remain blocked until the next validation stages prove
  topology, manifoldness, scale, and slicer load safety.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMeshTopologyTest' \
  --tests 'com.mobileslicer.scanner.ScannerMetricMeshCandidateTest' \
  --tests 'com.mobileslicer.scanner.ScannerSyntheticMetricPoseFixtureTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Metric Mesh Validation Hardening

Completed:

- Strengthened the mesh validation gate and moved the shared validation
  artifact path to:

```text
mesh/metric_mesh_validation.json
```

- The validation stage now checks and records:
  - vertex count
  - triangle count
  - unique edge count
  - boundary edge count
  - non-manifold edge count
  - degenerate triangle count
  - missing vertex reference count
  - duplicate vertex id count
  - duplicate triangle count
  - duplicate vertex references inside a triangle
  - disconnected component count
  - unreferenced vertex count
  - inconsistent normal edge count
  - visual-only or rejected geometry count
  - repaired surface ratio
- Added explicit blockers:
  - `mesh_missing_vertex_references_present`
  - `mesh_duplicate_vertex_ids_present`
  - `mesh_duplicate_triangles_present`
  - `mesh_duplicate_triangle_vertices_present`
  - `mesh_disconnected_components_present`
  - `mesh_unreferenced_vertices_present`
  - `mesh_inconsistent_normals_present`
- Validation computes triangle area from vertex coordinates instead of trusting
  the candidate JSON blindly.
- Validation computes closed-surface topology from normalized edge use and
  normal consistency from directed edge use.
- Validation computes connected components from referenced mesh vertices. Two
  separate closed shells are blocked until a later explicit multi-part policy
  exists.
- Existing downstream stages now read the renamed validation artifact through
  `SCANNER_MESH_VALIDATION_PATH`, so repair, printability, slicer-load, export,
  and handoff stay on the same contract.

Why this matters:

- A measured triangle candidate is not enough. The app now proves whether the
  candidate is structurally coherent before it can become a metric mesh.
- This catches broken topology early: holes, non-manifold edges, duplicate
  faces, disconnected shells, bad references, duplicated vertex ids, degenerate
  faces, inconsistent winding, and visual/rejected provenance.
- Export and workspace handoff remain blocked. Passing this gate only means the
  metric mesh candidate is ready for repair-report and printability validation.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMeshValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerRepairReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerPrintabilityReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Printability Report Hardening

Completed:

- Hardened `reports/printability_report.json` so it consumes the richer metric
  mesh validation artifact instead of only checking basic counts.
- The report now separates scanner output into structured sections:
  - `topology`
  - `mesh`
  - `repair`
  - `evidence`
  - `scale`
  - `slicer_readiness`
- The printability gate now reads and reports:
  - boundary edges
  - non-manifold edges
  - degenerate triangles
  - missing vertex references
  - duplicate vertex ids
  - duplicate triangles
  - duplicate vertex references inside triangles
  - disconnected components
  - unreferenced vertices
  - inconsistent normal edges
  - visual-only or rejected geometry
  - repair applied flag
  - repaired surface ratio
  - repaired hole count
  - largest repaired hole size
  - scale confidence
- Added explicit printability blockers:
  - `printability_missing_vertex_references_present`
  - `printability_duplicate_vertex_ids_present`
  - `printability_duplicate_triangles_present`
  - `printability_duplicate_triangle_vertices_present`
  - `printability_disconnected_components_present`
  - `printability_unreferenced_vertices_present`
  - `printability_inconsistent_normals_present`
  - `printability_visual_or_rejected_geometry_present`
  - `printability_scale_confidence_low`
- Scale confidence is reported on every printability report. Because marker-mat
  calibration is intentionally parked for the current local-dev phase, scale is
  not a hard blocker by default. The gate can enforce it with
  `requireScaleConfidenceForRecommendation=true`.
- Repair remains conservative. Any repair applied without an explicit printable
  repair policy blocks printability.
- Export and workspace handoff remain blocked. Passing printability only makes
  the mesh eligible for slicer-load validation.

Why this matters:

- This turns validation diagnostics into printer-facing decisions. A user should
  eventually see exact reasons like open boundary, non-manifold edge, disconnected
  shell, weak scale, or unsupported repair instead of a vague scan failure.
- The scanner remains aligned with the core plan: measured evidence first,
  machine-readable provenance, no visual geometry in metric output, and no STL
  or 3MF until the slicer-load gate passes.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerPrintabilityReportTest' \
  --tests 'com.mobileslicer.scanner.ScannerSlicerLoadValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Slicer-Load Validation Hardening

Completed:

- Hardened `reports/slicer_load_validation.json` so it enforces the structured
  printability report before any export writer can run.
- The slicer-load stage now consumes:
  - `topology`
  - `mesh`
  - `repair`
  - `scale`
  - `slicer_readiness`
- It blocks if printability reports:
  - boundary edges
  - non-manifold edges
  - degenerate triangles
  - disconnected components
  - inconsistent normal edges
  - repair applied without import policy
  - premature export readiness
  - premature workspace handoff readiness
  - weak or missing scale when the printability gate requires it
- It revalidates the metric mesh candidate before import:
  - vertex count must match the printability report
  - triangle count must match the printability report
  - every triangle must reference existing vertices
  - every triangle must have three unique vertex refs
  - candidate vertices/triangles cannot be visual-only or rejected
- Added structured contract output:
  - `printability_contract`
  - `candidate_contract`
  - exact candidate reference/provenance diagnostic counts
- The stage still sets:
  - `export_ready=false`
  - `workspace_handoff_ready=false`

Why this matters:

- This is the final pre-export internal gate. It prevents the export writer from
  treating a vague "recommended for printing" flag as enough.
- The slicer import contract now proves topology, repair policy, scale policy,
  candidate counts, triangle references, and provenance before STL/3MF writing
  can be considered.
- This stays aligned with the scanner plan: no fake success, no visual geometry
  in metric meshes, no handoff until every gate is machine-readable and passes.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerSlicerLoadValidationTest' \
  --tests 'com.mobileslicer.scanner.ScannerMetricExportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Metric Export Writer Hardening

Completed:

- Hardened the metric export writer so STL/3MF export consumes the validated
  slicer-load contract instead of only trusting the mesh candidate.
- Export now refuses to run unless:
  - slicer-load validation exists
  - slicer-load validation is allowed
  - slicer-load validation is metric
  - `slicer_load_validated=true`
  - `internal_import_contract_ready=true`
  - `export_ready=false` before the writer runs
  - `workspace_handoff_ready=false` before the writer runs
  - slicer-load vertex/triangle counts match the candidate
- After writing outputs, export verifies:
  - STL exists
  - STL byte size is nonzero
  - STL triangle count matches candidate triangle count
  - 3MF exists
  - 3MF byte size is nonzero
  - 3MF contains `3D/3dmodel.model`
  - 3MF unit is `millimeter`
  - 3MF vertex count matches candidate vertex count
  - 3MF triangle count matches candidate triangle count
  - STL and 3MF SHA-256 hashes are recorded
- `reports/export_manifest.json` now records:
  - source slicer-load validation path
  - source metric mesh candidate path
  - STL path, byte size, SHA-256, verified triangle count
  - 3MF path, byte size, SHA-256, verified vertex count, verified triangle count
  - post-write verification booleans
  - workspace handoff remains blocked
- Added export blockers:
  - `slicer_load_contract_not_ready`
  - `slicer_load_export_ready_before_writer`
  - `slicer_load_handoff_ready_before_writer`
  - `export_vertex_count_mismatch_slicer_contract`
  - `export_triangle_count_mismatch_slicer_contract`
  - `export_stl_missing`
  - `export_stl_empty`
  - `export_stl_triangle_count_mismatch`
  - `export_3mf_missing`
  - `export_3mf_empty`
  - `export_3mf_model_missing`
  - `export_3mf_units_not_millimeter`
  - `export_3mf_vertex_count_mismatch`
  - `export_3mf_triangle_count_mismatch`
  - `export_stl_hash_missing`
  - `export_3mf_hash_missing`

Why this matters:

- This is the first gate that writes actual local metric STL/3MF files, but it
  now proves the files exist, are nonempty, parse back to the expected counts,
  use millimeter units, and have stable content hashes.
- Workspace handoff is still blocked. Export success only means the output files
  are ready for the next workspace handoff validation gate.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerMetricExportTest' \
  --tests 'com.mobileslicer.scanner.ScannerWorkspaceHandoffTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Workspace Handoff Hardening

Completed:

- Hardened `reports/workspace_handoff.json` so the handoff gate re-verifies
  exported files instead of trusting `reports/export_manifest.json` blindly.
- The handoff gate now consumes:
  - export manifest top-level status
  - `outputs.stl`
  - `outputs.three_mf`
  - `verification`
  - source paths and counts
- It blocks if:
  - export manifest is blocked or non-metric
  - export manifest already claims workspace handoff readiness
  - STL/3MF export flags are missing
  - output files are missing or empty
  - manifest hashes are missing
  - on-disk SHA-256 hashes do not match the export manifest
  - on-disk byte sizes do not match the export manifest
  - STL triangle count does not match the manifest
  - 3MF model entry is missing
  - 3MF unit is not `millimeter`
  - 3MF vertex/triangle counts do not match the manifest
  - export manifest verification booleans are absent or false
  - vertex/triangle counts are below handoff limits
  - units are not millimeters
- The handoff artifact now records:
  - STL path, byte size, SHA-256, verified triangle count
  - 3MF path, byte size, SHA-256, verified vertex count, verified triangle count
  - handoff verification booleans
  - `shared_workspace_import_invoked=false`
- Added regression coverage for:
  - missing export manifest
  - blocked export manifest
  - valid handoff manifest
  - tampered STL file
  - missing hashes
  - count mismatch
  - premature export-manifest handoff readiness

Why this matters:

- This is the boundary that turns validated exported files into something the
  workspace may import. It now verifies the actual bytes on disk, not just
  internal flags.
- The scanner still does not directly invoke shared workspace import in this
  gate. It produces a verified handoff manifest that workspace integration can
  consume next.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerWorkspaceHandoffTest' \
  --tests 'com.mobileslicer.scanner.ScannerMetricExportTest' \
  --tests 'com.mobileslicer.scanner.ScannerReconstructionPipelineTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Workspace Import Integration

Implemented the next scanner-plan gate: a validated scanner handoff can now be
imported through the shared workspace model path, but only after a second
workspace-import check re-verifies the handoff artifact and exported model bytes.

Files added or changed:

- `android-app/app/src/main/java/com/mobileslicer/scanner/ScannerWorkspaceImport.kt`
- `android-app/app/src/main/java/com/mobileslicer/scanner/ScannerScreen.kt`
- `android-app/app/src/main/java/com/mobileslicer/ModelLoaderScreen.kt`
- `android-app/app/src/main/java/com/mobileslicer/MainActivity.kt`
- `android-app/app/src/main/java/com/mobileslicer/MainActivityModelLoading.kt`
- `android-app/app/src/main/java/com/mobileslicer/ModelLoaderProfileGate.kt`
- `android-app/app/src/test/java/com/mobileslicer/scanner/ScannerWorkspaceImportTest.kt`

New artifact:

```text
reports/workspace_import_receipt.json
```

The import plan consumes:

- `reports/workspace_handoff.json`
- `outputs.stl.path`
- `outputs.stl.byte_size`
- `outputs.stl.sha256`
- handoff `allowed`
- handoff `metric`
- handoff `workspace_handoff_ready`

It blocks import if:

- the workspace handoff report is missing
- handoff `allowed=false`
- handoff `metric=false`
- handoff `workspace_handoff_ready=false`
- the selected model path is missing
- the selected model file is missing or empty
- byte size is missing or no longer matches the handoff report
- SHA-256 is missing or no longer matches the handoff report

The scanner UI now exposes `Import validated scan to workspace` only after the
pipeline writes a ready metric handoff. Pressing it does not load arbitrary
scanner files. It first calls `planScannerWorkspaceImport(...)`, then invokes
the existing workspace model import path with the validated STL. The workspace
import receipt records whether shared import was invoked and the model-loader
message returned by the app.

Main activity integration:

- Added `loadScannerWorkspaceModelFromFile(...)`.
- It uses the same native model loader used by normal imports.
- It clears generated G-code before load.
- It parses STL bounds for workspace state.
- It returns a normal `ModelLoadResult` so `ModelLoaderScreen` applies the same
  plate-object, legacy-state, workspace-navigation, and snapshot logic used by
  file imports.

Regression coverage added:

- missing handoff blocks
- blocked/non-metric/not-ready handoff blocks
- validated metric handoff allows import planning
- missing hash blocks
- tampered model hash blocks
- byte-size mismatch blocks
- import receipt is written before and after shared workspace import invocation

Why this matters:

- This closes the local scanner loop from validated metric export to workspace
  import without weakening the plan.
- The slicer still never accepts camera captures, previews, sparse points, or
  visual geometry directly.
- A scanner model reaches the workspace only after package, reconstruction,
  mesh, printability, slicer-load, export, handoff, and import-boundary checks
  agree on the same metric STL bytes.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerWorkspaceImportTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 Real Device Feature Track Replay

Pulled the latest real phone scan from ADB and used it to drive the next local
reconstruction fix instead of guessing from synthetic data.

Real scan observed on device:

- Scan id: `scan_c2a4f330-2251-4686-8076-d1729b571cd2`
- Capture mode: AR RGB + pose + raw depth
- Exported package: valid
- Accepted package frames: `44`
- Capture pass coverage:
  - `12` mid ring
  - `12` high ring
  - `12` low ring
  - `8` top/detail
- Depth fusion:
  - `depth_fusion_ready=true`
  - `44` depth frames
  - average depth coverage about `0.377`
  - `3098` back-projected depth points
- Match graph:
  - connected
  - `946` accepted image pairs
  - pair matches were plentiful
- Original feature-track result:
  - `track_count=44`
  - `long_track_count=10`
  - blocker: `not_enough_long_feature_tracks`

Root finding:

- Capture/export/depth were not the immediate blocker.
- The match graph was connected, so this was not a total matching failure.
- The weak point was track construction: exact pair-union coordinates produced
  too few stable multi-frame tracks from a real phone scan.

Implementation:

- Added a compact real-scan fixture under:

```text
android-app/app/src/test/resources/scanner/real_scan_c2a4f330/
```

- Kept only the artifacts needed for replaying this stage:
  - `features/features.json`
  - `matches/match_graph.json`
  - `reconstruction_summary.json`
  - `reconstruction_audit.json`
- Added a real-device regression in `ScannerFeatureTracksTest`.
- Extended `ScannerFeatureTracks.kt` with descriptor-seeded supplemental tracks.

Descriptor-seeded track rules:

- Existing pair-union tracks still run first.
- Descriptor-seeded tracks are enabled by default but capped.
- A descriptor-seeded track must:
  - appear in at least `minLongTrackLength` distinct frames
  - appear in no more than `maxDescriptorSeededFrameCount`
  - meet `minDescriptorSeededAverageScore`
  - cover at least two capture passes when multiple passes are required
  - cover at least two spatial cells
- Descriptor-seeded tracks are limited by `maxDescriptorSeededTracks`.
- They emit the warning:

```text
descriptor_seeded_tracks_require_metric_pose_validation
```

Why this is not a shortcut:

- The metric gates were not relaxed.
- Calibration and marker/scale gates still block printable output.
- Pose solve, bundle adjustment, sparse reconstruction, mesh validation,
  printability, export, handoff, and workspace import still have to pass.
- Descriptor-seeded tracks only give the pose pipeline better visual evidence
  to evaluate; they do not make the scan metric by themselves.

New feature-track JSON fields:

```text
descriptor_seeded_track_count
limits.enable_descriptor_seeded_tracks
limits.max_descriptor_seeded_tracks
limits.max_descriptor_seeded_frame_count
limits.min_descriptor_seeded_average_score
```

Regression coverage added:

- The pulled real scan now replays through `buildScannerFeatureTracks(...)`.
- It must produce enough total tracks and long tracks without changing metric
  gates.
- It must record descriptor-seeded track usage and the metric-validation warning.

Verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests 'com.mobileslicer.scanner.ScannerFeatureTracksTest'
```

Result:

```text
BUILD SUCCESSFUL
```

### 2026-05-10 App-Level Synthetic Scanner Smoke

Problem:

- The JVM synthetic regression automated scanner logic, package export, and
  reconstruction handoff.
- The device smoke script automated build/install/log/audit collection, but
  still required a person to perform a physical camera scan.
- That left a gap: Android app wiring could regress without being caught until
  someone manually rescanned on a phone.

Implementation:

- Added an internal debug/perf automation intent:

```text
com.mobileslicer.action.AUTOMATE_SCANNER_SYNTHETIC
```

- Added `ScannerAutomationRunner`.
- The runner executes synthetic scanner fixtures inside the installed Android
  app process.
- It writes machine-readable app artifacts:

```text
files/automation/scanner-status.json
files/automation/scanner-summary.json
```

- Added script:

```bash
scripts/scanner_app_synthetic_smoke.sh
```

The script:

- runs the scanner JVM regression for the same core contract
- builds the debug APK
- installs it through ADB
- launches `MainActivity` with the scanner automation intent twice:
  - `capture_package`
  - `metric_reconstruction`
- waits for fixture-specific `scanner-*-status.json` files
- validates the `capture_package` fixture:
  - status is `passed`
  - mode is `synthetic_app_scanner_smoke`
  - fixture kind is `capture_package`
  - accepted frame count is `44`
  - package validation passed
  - local reconstruction pipeline completed
  - package validation did not become a blocker
  - reconstruction summary/audit paths exist in status
- pulls the latest app scanner audit workspace
- validates the `metric_reconstruction` fixture:
  - status is `passed`
  - mode is `synthetic_app_scanner_smoke`
  - fixture kind is `metric_reconstruction`
  - metric pose optimizer is allowed and metric
  - sparse triangulation is metric
  - at least `48` measured sparse points exist
  - dense reconstruction is admitted
  - debug dense point cloud is ready
  - surface reconstruction is ready
  - feature, pose, sparse, dense, point-cloud, and surface blockers are absent

Why this is still aligned with the scanner plan:

- This is not fake camera quality proof.
- It does not claim autofocus, ARCore pose/depth, material handling, lighting,
  glare handling, or real object accuracy.
- It verifies Android app runtime wiring, internal storage paths, scan package
  export, synthetic local AI mask contract, reconstruction handoff, summary
  writing, metric pose optimization, sparse metric handoff, dense admission,
  point-cloud generation, surface candidate generation, and audit artifact
  collection without forcing a manual rescan after every code change.
- Real-device scans remain required for camera UX, autofocus, AR/depth,
  orientation, material behavior, and measured reconstruction quality.

Recommended scanner verification ladder:

```bash
scripts/scanner_synthetic_regression.sh
scripts/scanner_app_synthetic_smoke.sh
scripts/scanner_real_replay.sh
scripts/scanner_device_smoke.sh
```

- Use `scanner_synthetic_regression.sh` for fast no-device scanner logic.
- Use `scanner_app_synthetic_smoke.sh` for installed-app scanner wiring.
- Use `scanner_real_replay.sh` for replaying the pulled phone scan through
  feature tracks, pose scaffolding, sparse input gates, and metric pose
  diagnostics without physically rescanning.
- Use `scanner_device_smoke.sh` only when the change touches real camera, AR,
  depth, autofocus, orientation, capture UX, or real reconstruction evidence.

### 2026-05-10 Real Device Replay Bridge

Problem:

- The pulled phone scan fixture was only protecting the feature-track fix.
- That meant we could prove the old `not_enough_long_feature_tracks` blocker was
  gone, but we were not automatically replaying the scan into the next local
  reconstruction gates.
- Requiring a new physical scan for every downstream reconstruction change is
  too slow and makes debugging noisy.

Implementation:

- Added `ScannerRealDeviceReplayTest`.
- Added script:

```bash
scripts/scanner_real_replay.sh
```

- The replay uses the pulled real phone artifacts:

```text
android-app/app/src/test/resources/scanner/real_scan_c2a4f330/
```

- It copies:
  - `features/features.json`
  - `matches/match_graph.json`
- It synthesizes only the minimum reconstruction manifest metadata needed to
  replay downstream gates:
  - frame ids
  - capture passes
  - image dimensions
  - approximate intrinsics from frame dimensions
  - `scale_source = none`
- It does not synthesize calibration success, marker observations, or metric
  scale. Those remain blocked.

Replay coverage:

- feature match graph
- feature tracks
- pose initialization
- pose refinement
- sparse reconstruction inputs
- metric pose graph diagnostics
- bundle adjustment diagnostics
- metric pose solve diagnostics

Assertions:

- the real scan match graph remains allowed
- feature tracks are now allowed
- `not_enough_long_feature_tracks` must not reappear
- descriptor-seeded tracks must be recorded
- descriptor-seeded usage must keep the warning:

```text
descriptor_seeded_tracks_require_metric_pose_validation
```

- pose initialization/refinement can proceed only as non-metric scaffolding
- sparse and metric pose gates must still block on:

```text
verified_marker_mat_required
calibration_missing
scale_confidence_low
marker_reprojection_missing
```

Why this is not a shortcut:

- The replay does not fake a printable mesh.
- The replay does not fake verified scale.
- The replay does not fake marker observations.
- The replay does not claim metric accuracy.
- It proves the real scan now reaches the correct next blockers: calibration,
  scale, and metric pose validation.

This is the correct bridge between synthetic-good and phone-good. Synthetic
fixtures prove the pipeline can go deep with valid metric inputs. Real replay
proves the actual phone capture is no longer stuck at the old feature-track
blocker and shows the next honest gate.

## Definition Of Done

Scanner is not done when the camera opens. It is not done when a pretty preview
appears. It is done when the product can prove what it measured and refuse what
it cannot prove.

V1 is done when:

- local capture works
- calibration scale verification works
- local AI assists capture without deleting metric geometry
- local photogrammetry reconstructs real geometry
- local printable STL/3MF passes validation
- bad scans fail honestly
- metric and visual meshes are separated
- mesh provenance is machine-readable
- workspace handoff only accepts validated metric meshes
- cloud jobs are quota-protected
- paid scans cannot bankrupt us
- credit sales cannot exceed processing capacity
- licensing gates block unsafe tools
- privacy deletion paths are auditable
- benchmark fixtures and device matrix pass

The coherent direction is local printable first, cloud better, no fake geometry,
strict validation, calibrated scale, measured provenance, and billing controls
before public cloud release.

## Current Scanner Status

Last updated: 2026-05-16.

Current state:

- The scanner direction is local printable first, with optional cloud later.
- Fake meshes, fallback cubes, and pretend scanner success are not allowed.
- Local capture/package/export exists as an experimental foundation.
- Local AI is currently capture assistance and soft-mask evidence, not metric
  truth.
- The app has synthetic scanner automation for Android runtime verification.
- A pulled real phone scan is preserved as a replay fixture.
- Real capture still requires real-device validation for camera, autofocus,
  AR/depth, orientation, material behavior, and actual reconstruction quality.

Key files:

```text
android-app/app/src/main/java/com/mobileslicer/scanner/
android-app/app/src/test/java/com/mobileslicer/scanner/
android-app/app/src/test/resources/scanner/real_scan_c2a4f330/
scripts/scanner_synthetic_regression.sh
scripts/scanner_app_synthetic_smoke.sh
scripts/scanner_real_replay.sh
scripts/scanner_device_smoke.sh
scripts/scanner_pull_latest_audit.sh
```

Verification ladder:

```bash
scripts/scanner_synthetic_regression.sh
scripts/scanner_app_synthetic_smoke.sh
scripts/scanner_real_replay.sh
cd android-app && ./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*'
```

Use `scripts/scanner_device_smoke.sh` only when checking real camera/AR/depth
behavior, because that still requires a physical scan.

Last known verified results:

- Full scanner JVM suite passed.
- `scripts/scanner_app_synthetic_smoke.sh` passed.
- `scripts/scanner_real_replay.sh` passed.
- The real scan replay no longer fails at:

```text
feature_tracks:not_enough_long_feature_tracks
```

- The real scan replay now reaches the correct next blockers:

```text
verified_marker_mat_required
calibration_missing
scale_confidence_low
marker_reprojection_missing
```

Current blockers:

- Real printable output is not done.
- Verified calibration/scale is not implemented end-to-end for real scans.
- Marker observation detection is not production-ready.
- Real metric pose validation is still blocked without verified scale and marker
  evidence.
- Dense/surface/mesh/printability/export stages are protected by gates and must
  not be bypassed.
- Real camera behavior still needs device testing after any CameraX/ARCore/UI
  capture change.

Next best implementation step:

Implement the calibration/scale bridge for real captures:

1. Preserve the current decision to skip mandatory marker mats in UX for now,
   but keep calibration support in the pipeline.
2. Add a real calibration input path that can record one of:
   - measured scale bar
   - known object dimension
   - later marker/card observations
3. Write calibration metadata into the scan package and reconstruction manifest.
4. Update real replay with a calibrated fixture variant that is allowed to move
   past `verified_marker_mat_required`, `calibration_missing`,
   `scale_confidence_low`, and `marker_reprojection_missing` only when evidence
   exists.
5. Keep metric/export/workspace handoff blocked until pose, sparse, dense,
   mesh, printability, and slicer-load gates pass.

Do not start by polishing UI unless the next change touches real capture
workflow. The highest-value next work is making scale/calibration evidence
machine-readable so the real phone scan can advance beyond the current metric
blockers without weakening the gates.
