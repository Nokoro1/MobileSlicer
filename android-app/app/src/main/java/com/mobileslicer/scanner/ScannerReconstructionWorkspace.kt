package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION = 1
internal const val SCANNER_RECONSTRUCTION_MANIFEST_PATH = "reconstruction_job.json"
internal const val SCANNER_RECONSTRUCTION_INPUT_HASHES_PATH = "input_hashes.json"
private const val SCANNER_MARKER_OBSERVATIONS_PATH = "diagnostics/marker_observations.json"

internal data class ScannerReconstructionWorkspaceLimits(
    val preflightLimits: ScannerLocalReconstructionLimits = ScannerLocalReconstructionLimits(),
    val maxStagedFrames: Int = preflightLimits.maxInputFrames
)

internal data class ScannerReconstructionWorkspaceFrame(
    val frameId: String,
    val sourceImagePath: String,
    val stagedImagePath: String,
    val stagedImageSha256: String,
    val sourceMaskPath: String?,
    val stagedMaskPath: String?,
    val stagedMaskSha256: String?,
    val sourceDepth16Path: String?,
    val stagedDepth16Path: String?,
    val stagedDepth16Sha256: String?,
    val sourceDepthConfidencePath: String?,
    val stagedDepthConfidencePath: String?,
    val stagedDepthConfidenceSha256: String?,
    val capturePass: ScannerCapturePass,
    val intrinsics: CameraIntrinsicsData,
    val poseWorldFromCamera: FloatArray?,
    val blurScore: Float,
    val exposureScore: Float,
    val materialRisk: Float,
    val depthCoverage: Float?
)

internal data class ScannerReconstructionWorkspaceResult(
    val created: Boolean,
    val workspaceDir: File,
    val manifestPath: String?,
    val errors: List<String>,
    val warnings: List<String>,
    val stagedFrames: List<ScannerReconstructionWorkspaceFrame>
)

internal fun buildLocalReconstructionWorkspace(
    packageDir: File,
    workspaceDir: File,
    limits: ScannerReconstructionWorkspaceLimits = ScannerReconstructionWorkspaceLimits()
): ScannerReconstructionWorkspaceResult {
    val validation = validateScannerPackageDirectory(packageDir)
    val manifest = validation.manifest
    if (!validation.valid || manifest == null) {
        return ScannerReconstructionWorkspaceResult(
            created = false,
            workspaceDir = workspaceDir,
            manifestPath = null,
            errors = listOf("package_validation_failed") + validation.errors,
            warnings = validation.warnings,
            stagedFrames = emptyList()
        )
    }

    val preflight = evaluateLocalReconstructionPreflight(manifest, limits.preflightLimits)
    if (!preflight.allowed) {
        return ScannerReconstructionWorkspaceResult(
            created = false,
            workspaceDir = workspaceDir,
            manifestPath = null,
            errors = listOf("local_reconstruction_preflight_failed") + preflight.reasons,
            warnings = validation.warnings,
            stagedFrames = emptyList()
        )
    }

    workspaceDir.deleteRecursively()
    require(workspaceDir.mkdirs() || workspaceDir.isDirectory) {
        "Unable to create reconstruction workspace: ${workspaceDir.absolutePath}"
    }

    val acceptedFrames = manifest.frames
        .filter { it.quality.accepted }
        .sortedWith(compareBy<ScanFrame> { reconstructionCapturePassOrder(it.capturePass) }.thenBy { it.id })
        .take(limits.maxStagedFrames)
    val stagedFrames = acceptedFrames.mapIndexed { index, frame ->
        stageReconstructionFrame(
            packageDir = packageDir,
            workspaceDir = workspaceDir,
            stageIndex = index + 1,
            frame = frame
        )
    }
    val jobJson = reconstructionJobJson(
        packageDir = packageDir,
        manifest = manifest,
        preflight = preflight,
        frames = stagedFrames,
        limits = limits
    )
    workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(jobJson.toString(2))
    workspaceDir.resolve(SCANNER_RECONSTRUCTION_INPUT_HASHES_PATH)
        .writeText(buildPackageHashes(workspaceDir).toString(2))

    return ScannerReconstructionWorkspaceResult(
        created = true,
        workspaceDir = workspaceDir,
        manifestPath = SCANNER_RECONSTRUCTION_MANIFEST_PATH,
        errors = emptyList(),
        warnings = validation.warnings,
        stagedFrames = stagedFrames
    )
}

private fun stageReconstructionFrame(
    packageDir: File,
    workspaceDir: File,
    stageIndex: Int,
    frame: ScanFrame
): ScannerReconstructionWorkspaceFrame {
    val intrinsics = requireNotNull(frame.intrinsics) {
        "Accepted frame ${frame.id} reached reconstruction staging without camera intrinsics"
    }
    val frameStem = stageIndex.toString().padStart(6, '0')
    val stagedImagePath = "images/$frameStem.jpg"
    copyScannerPackageFile(
        source = packageDir.resolve(frame.rgbPath),
        target = workspaceDir.resolve(stagedImagePath)
    )
    val stagedMaskPath = frame.maskPath?.let {
        val targetPath = "masks/${frameStem}_mask.png"
        copyScannerPackageFile(
            source = packageDir.resolve(it),
            target = workspaceDir.resolve(targetPath)
        )
        targetPath
    }
    val stagedDepth16Path = frame.depth16Path?.let {
        val targetPath = "depth/${frameStem}_depth16.png"
        copyScannerPackageFile(
            source = packageDir.resolve(it),
            target = workspaceDir.resolve(targetPath)
        )
        targetPath
    }
    val stagedDepthConfidencePath = frame.depthConfidencePath?.let {
        val targetPath = "depth/${frameStem}_confidence.png"
        copyScannerPackageFile(
            source = packageDir.resolve(it),
            target = workspaceDir.resolve(targetPath)
        )
        targetPath
    }
    return ScannerReconstructionWorkspaceFrame(
        frameId = frame.id,
        sourceImagePath = frame.rgbPath,
        stagedImagePath = stagedImagePath,
        stagedImageSha256 = sha256Hex(workspaceDir.resolve(stagedImagePath)),
        sourceMaskPath = frame.maskPath,
        stagedMaskPath = stagedMaskPath,
        stagedMaskSha256 = stagedMaskPath?.let { sha256Hex(workspaceDir.resolve(it)) },
        sourceDepth16Path = frame.depth16Path,
        stagedDepth16Path = stagedDepth16Path,
        stagedDepth16Sha256 = stagedDepth16Path?.let { sha256Hex(workspaceDir.resolve(it)) },
        sourceDepthConfidencePath = frame.depthConfidencePath,
        stagedDepthConfidencePath = stagedDepthConfidencePath,
        stagedDepthConfidenceSha256 = stagedDepthConfidencePath?.let { sha256Hex(workspaceDir.resolve(it)) },
        capturePass = frame.capturePass,
        intrinsics = intrinsics,
        poseWorldFromCamera = frame.poseWorldFromCamera,
        blurScore = frame.quality.blurScore,
        exposureScore = frame.quality.exposureScore,
        materialRisk = frame.quality.materialRisk,
        depthCoverage = frame.quality.depthCoverage
    )
}

private fun copyScannerPackageFile(source: File, target: File) {
    require(source.isFile) { "Missing reconstruction source file: ${source.absolutePath}" }
    target.parentFile?.mkdirs()
    source.copyTo(target, overwrite = true)
}

private fun reconstructionCapturePassOrder(pass: ScannerCapturePass): Int =
    when (pass) {
        ScannerCapturePass.LowRing -> 0
        ScannerCapturePass.MidRing -> 1
        ScannerCapturePass.HighRing -> 2
        ScannerCapturePass.TopDetail -> 3
        ScannerCapturePass.Underside -> 4
    }

private fun reconstructionJobJson(
    packageDir: File,
    manifest: ScanPackageManifest,
    preflight: ScannerLocalReconstructionPreflightResult,
    frames: List<ScannerReconstructionWorkspaceFrame>,
    limits: ScannerReconstructionWorkspaceLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("scan_id", manifest.scanId)
        .put("source_package_schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("units", SCANNER_PACKAGE_UNITS)
        .put("mode", manifest.mode.manifestValue)
        .put("capture_profile", manifest.captureProfile.manifestValue)
        .put("scale_source", manifest.scaleSource.manifestValue)
        .put("calibration", manifest.calibration?.toReconstructionJson() ?: JSONObject.NULL)
        .put("two_sided_alignment", manifest.twoSidedAlignment.toReconstructionJson())
        .put("preflight", localReconstructionPreflightJson(preflight))
        .put("marker_mat_layout", markerMatLayoutJson(defaultMobileSlicerMarkerMatLayout()))
        .put("marker_corner_observations", markerCornerObservationsJson(packageDir, frames))
        .put(
            "limits",
            JSONObject()
                .put("max_staged_frames", limits.maxStagedFrames)
                .put("min_accepted_frames", limits.preflightLimits.minAcceptedFrames)
                .put("minimum_scale_confidence", limits.preflightLimits.minimumScaleConfidence.toDouble())
        )
        .put("frame_count", frames.size)
        .put(
            "frames",
            JSONArray().apply {
                frames.forEach { put(it.toJson()) }
            }
        )

private fun markerCornerObservationsJson(
    packageDir: File,
    frames: List<ScannerReconstructionWorkspaceFrame>
): JSONArray {
    val observationsFile = packageDir.resolve(SCANNER_MARKER_OBSERVATIONS_PATH)
    if (!observationsFile.isFile) return JSONArray()
    val observations = runCatching {
        JSONObject(observationsFile.readText()).optJSONArray("observations") ?: JSONArray()
    }.getOrElse { JSONArray() }
    val framesById = frames.associateBy { it.frameId }
    val markerLayoutById = defaultMobileSlicerMarkerMatLayout()
        .markers
        .associateBy { it.id.toString() }
    return JSONArray().apply {
        repeat(observations.length()) { observationIndex ->
            val observation = observations.optJSONObject(observationIndex) ?: return@repeat
            val frameId = observation.optString("frame_id")
            val markerId = observation.optString("marker_id")
            val frame = framesById[frameId] ?: return@repeat
            val marker = markerLayoutById[markerId] ?: return@repeat
            val cornersPx = observation.optJSONArray("corners_px") ?: return@repeat
            if (cornersPx.length() != 4) return@repeat
            val worldCornersMm = listOf(
                marker.xMm to marker.yMm,
                marker.xMm + marker.sizeMm to marker.yMm,
                marker.xMm + marker.sizeMm to marker.yMm + marker.sizeMm,
                marker.xMm to marker.yMm + marker.sizeMm
            )
            repeat(4) { cornerIndex ->
                val cornerPx = cornersPx.optJSONArray(cornerIndex) ?: return@repeat
                val observedX = cornerPx.optDouble(0, Double.NaN)
                val observedY = cornerPx.optDouble(1, Double.NaN)
                if (!observedX.isFinite() || !observedY.isFinite()) return@repeat
                val world = worldCornersMm[cornerIndex]
                put(
                    JSONObject()
                        .put("frame_id", frameId)
                        .put("marker_id", markerId)
                        .put("corner_index", cornerIndex)
                        .put("observed_x_normalized_camera", (observedX - frame.intrinsics.cx) / frame.intrinsics.fx)
                        .put("observed_y_normalized_camera", (observedY - frame.intrinsics.cy) / frame.intrinsics.fy)
                        .put("observed_x_px", observedX)
                        .put("observed_y_px", observedY)
                        .put("world_xyz_mm", JSONArray().put(world.first.toDouble()).put(world.second.toDouble()).put(0.0))
                        .put("marker_reprojection_error_px", observation.opt("reprojection_error_px") ?: JSONObject.NULL)
                        .put("hamming", observation.opt("hamming") ?: JSONObject.NULL)
                        .put("decision_margin", observation.opt("decision_margin") ?: JSONObject.NULL)
                )
            }
        }
    }
}

private fun ScannerReconstructionWorkspaceFrame.toJson(): JSONObject =
    JSONObject()
        .put("frame_id", frameId)
        .put("source_image", sourceImagePath)
        .put("staged_image", stagedImagePath)
        .put("staged_image_sha256", stagedImageSha256)
        .put("source_mask", sourceMaskPath ?: JSONObject.NULL)
        .put("staged_mask", stagedMaskPath ?: JSONObject.NULL)
        .put("staged_mask_sha256", stagedMaskSha256 ?: JSONObject.NULL)
        .put("source_depth16", sourceDepth16Path ?: JSONObject.NULL)
        .put("staged_depth16", stagedDepth16Path ?: JSONObject.NULL)
        .put("staged_depth16_sha256", stagedDepth16Sha256 ?: JSONObject.NULL)
        .put("source_depth_confidence", sourceDepthConfidencePath ?: JSONObject.NULL)
        .put("staged_depth_confidence", stagedDepthConfidencePath ?: JSONObject.NULL)
        .put("staged_depth_confidence_sha256", stagedDepthConfidenceSha256 ?: JSONObject.NULL)
        .put("capture_pass", capturePass.manifestValue)
        .put(
            "intrinsics",
            JSONObject()
                .put("fx", intrinsics.fx.toDouble())
                .put("fy", intrinsics.fy.toDouble())
                .put("cx", intrinsics.cx.toDouble())
                .put("cy", intrinsics.cy.toDouble())
                .put("width", intrinsics.imageWidth)
                .put("height", intrinsics.imageHeight)
        )
        .put(
            "pose_world_from_camera",
            poseWorldFromCamera?.let { pose ->
                JSONArray().apply { pose.forEach { value -> put(value.toDouble()) } }
            } ?: JSONObject.NULL
        )
        .put(
            "quality",
            JSONObject()
                .put("blur_score", blurScore.toDouble())
                .put("exposure_score", exposureScore.toDouble())
                .put("material_risk", materialRisk.toDouble())
                .put("depth_coverage", depthCoverage ?: JSONObject.NULL)
        )

private fun ScannerCalibration.toReconstructionJson(): JSONObject =
    JSONObject()
        .put("marker_type", markerType)
        .put("marker_size_mm", markerSizeMm.toDouble())
        .put("printed_scale_bar_expected_mm", printedScaleBarExpectedMm.toDouble())
        .put("printed_scale_bar_measured_mm", printedScaleBarMeasuredMm ?: JSONObject.NULL)
        .put("scale_correction", scaleCorrection ?: JSONObject.NULL)
        .put("scale_confidence", scaleConfidence.toDouble())
        .put("marker_reprojection_error_px", markerReprojectionErrorPx ?: JSONObject.NULL)

private fun ScannerTwoSidedAlignment.toReconstructionJson(): JSONObject =
    JSONObject()
        .put("object_moved_during_session", objectMovedDuringSession)
        .put("alignment_method", alignmentMethod.manifestValue)
        .put("alignment_confidence", alignmentConfidence ?: JSONObject.NULL)
        .put("pass_count", passCount)
