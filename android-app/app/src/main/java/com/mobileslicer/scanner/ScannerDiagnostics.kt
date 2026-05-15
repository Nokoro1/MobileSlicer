package com.mobileslicer.scanner

import android.content.Context
import android.os.Build
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class ScannerReadinessSummary(
    val captureReadiness: Float,
    val scaleReadiness: Float,
    val coverageReadiness: Float,
    val materialReadiness: Float,
    val maskReadiness: Float,
    val lightingReadiness: Float,
    val depthPoseReadiness: Float,
    val printableReadiness: Float
)

internal fun scannerReadinessSummary(
    frames: List<ScanFrame>,
    calibrationGate: PrintableCalibrationGateResult,
    markerEvidence: MarkerEvidenceSummary = emptyMarkerEvidence(),
    maskSummary: ScannerMaskQualitySummary = scannerMaskQualitySummary(
        frames
            .filter { it.maskPath != null }
            .map {
                ScannerSoftMaskResult(
                    maskPath = requireNotNull(it.maskPath),
                    maskSha256 = it.maskSha256 ?: "",
                    generatorName = "manifest_mask",
                    generatorStatus = "manifest_only",
                    objectRoi = defaultScannerObjectRoi(),
                    coverageRatio = 0f,
                    edgeUncertainty = 1f,
                    centerSupport = 0f,
                    warnings = listOf("mask_metrics_unavailable")
                )
            }
    ),
    depthPoseReadiness: Float = 0f
): ScannerReadinessSummary {
    val acceptedFrames = frames.filter { it.quality.accepted }
    val averageSharpness = acceptedFrames
        .map { scannerImageSharpnessReadiness(it.quality.blurScore) }
        .ifEmpty { listOf(0f) }
        .average()
        .toFloat()
    val lightingReadiness = acceptedFrames
        .map { scannerLightingReadiness(it.quality.exposureScore, 0f) }
        .ifEmpty { listOf(0f) }
        .average()
        .toFloat()
    val passCoverage = ScannerCapturePass.entries.count { pass ->
        frames.any { it.capturePass == pass && it.quality.accepted }
    }.toFloat() / ScannerCapturePass.entries.size.toFloat()
    val scaleReadiness = if (calibrationGate.allowed) {
        1f
    } else {
        markerEvidence.markerScaleConfidence.coerceIn(0f, 1f)
    }
    val materialReadiness = 1f - frames
        .map { it.quality.materialRisk.coerceIn(0f, 1f) }
        .ifEmpty { listOf(0f) }
        .average()
        .toFloat()
    val captureReadiness = scannerReadinessScore(
        ScannerReadinessInput(
            angleCoverage = passCoverage,
            imageSharpness = averageSharpness,
            scaleConfidence = scaleReadiness,
            maskConsistency = maskSummary.consistency,
            lightingScore = lightingReadiness,
            depthPoseSupport = depthPoseReadiness
        )
    )
    val printableReadiness = minOf(captureReadiness, scaleReadiness, passCoverage, maskSummary.consistency, materialReadiness)
    return ScannerReadinessSummary(
        captureReadiness = captureReadiness,
        scaleReadiness = scaleReadiness,
        coverageReadiness = passCoverage,
        materialReadiness = materialReadiness,
        maskReadiness = maskSummary.consistency,
        lightingReadiness = lightingReadiness,
        depthPoseReadiness = depthPoseReadiness.coerceIn(0f, 1f),
        printableReadiness = printableReadiness
    )
}

internal fun scannerDepthPoseReadiness(frames: List<ScanFrame>): Float {
    val acceptedFrames = frames.filter { it.quality.accepted }
    if (acceptedFrames.isEmpty()) return 0f
    val poseRatio = acceptedFrames
        .count { it.poseWorldFromCamera?.size == 16 }
        .toFloat() / acceptedFrames.size.toFloat()
    val depthCoverage = acceptedFrames
        .mapNotNull { it.quality.depthCoverage }
        .ifEmpty { listOf(0f) }
        .average()
        .toFloat()
        .coerceIn(0f, 1f)
    return (0.55f * poseRatio + 0.45f * depthCoverage).coerceIn(0f, 1f)
}

internal fun writeScannerDiagnostics(
    directory: File,
    manifest: ScanPackageManifest,
    calibrationGate: PrintableCalibrationGateResult,
    readiness: ScannerReadinessSummary,
    rejectedDiagnosticFrames: List<ScanFrame> = manifest.frames.filter { !it.quality.accepted },
    markerObservations: List<MarkerObservation> = emptyList(),
    markerEvidence: MarkerEvidenceSummary = emptyMarkerEvidence(),
    softMasks: List<ScannerSoftMaskResult> = emptyList(),
    context: Context? = null
) {
    val diagnosticsDir = directory.resolve("diagnostics").also { it.mkdirs() }
    val maskSummary = scannerMaskQualitySummary(softMasks)
    val depthPriorStatus = scannerDepthAnythingV2SmallStatus { path ->
        context?.assets?.open(path)?.use { true } ?: false
    }
    val guidance = scannerCaptureGuidanceReport(
        manifest = manifest,
        readiness = readiness,
        maskSummary = maskSummary,
        depthPriorStatus = depthPriorStatus
    )
    diagnosticsDir.resolve("rejected_frames.json").writeText(rejectedFramesJson(rejectedDiagnosticFrames).toString(2))
    diagnosticsDir.resolve("coverage_map.json").writeText(coverageMapJson(manifest, readiness).toString(2))
    diagnosticsDir.resolve("local_reconstruction_preflight.json").writeText(
        localReconstructionPreflightJson(evaluateLocalReconstructionPreflight(manifest)).toString(2)
    )
    diagnosticsDir.resolve("marker_observations.json").writeText(
        markerObservationsJson(markerObservations, markerEvidence).toString(2)
    )
    diagnosticsDir.resolve("soft_masks.json").writeText(softMasksJson(softMasks).toString(2))
    diagnosticsDir.resolve("local_ai_status.json").writeText(
        localAiStatusJson(
            maskSummary = maskSummary,
            depthPriorStatus = depthPriorStatus,
            guidance = guidance,
            mediaPipeStatus = context?.let { scannerMediaPipeInteractiveSegmenterStatus(it) }
        ).toString(2)
    )
    diagnosticsDir.resolve("device_capabilities.json").writeText(deviceCapabilitiesJson(context).toString(2))
    diagnosticsDir.resolve("printable_gate.json").writeText(
        JSONObject()
            .put("allowed", calibrationGate.allowed)
            .put("reasons", JSONArray(calibrationGate.reasons))
            .toString(2)
        )
}

internal fun localAiStatusJson(
    maskSummary: ScannerMaskQualitySummary,
    depthPriorStatus: ScannerDepthPriorStatus,
    guidance: ScannerCaptureGuidanceReport,
    mediaPipeStatus: ScannerMediaPipeSoftMaskStatus? = null
): JSONObject {
    val stack = scannerLocalAiStackStatus(
        maskSummary = maskSummary,
        mediaPipeStatus = mediaPipeStatus,
        depthPriorStatus = depthPriorStatus,
        guidance = guidance
    )
    return JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("stack", scannerLocalAiStackStatusJson(stack))
        .put("mask_quality", scannerMaskQualityJson(maskSummary))
        .put("mediapipe_interactive_segmenter", scannerMediaPipeStatusJson(mediaPipeStatus))
        .put("depth_prior", scannerDepthPriorStatusJson(depthPriorStatus))
        .put("guidance", scannerCaptureGuidanceJson(guidance))
}

internal fun scannerMediaPipeStatusJson(status: ScannerMediaPipeSoftMaskStatus?): JSONObject =
    if (status == null) {
        JSONObject()
            .put("available", false)
            .put("production_ready", false)
            .put("status", "context_unavailable")
            .put("detail", "Android context was not available for MediaPipe model asset inspection.")
            .put("model_asset_path", SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL)
            .put("source_url", SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_SOURCE_URL)
            .put("expected_sha256", SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256)
            .put("actual_sha256", JSONObject.NULL)
            .put("expected_bytes", SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_BYTES)
            .put("actual_bytes", JSONObject.NULL)
    } else {
        JSONObject()
            .put("available", status.available)
            .put("production_ready", status.productionReady)
            .put("status", status.status)
            .put("detail", status.detail)
            .put("model_asset_path", status.modelAssetPath)
            .put("source_url", status.sourceUrl)
            .put("expected_sha256", status.expectedSha256)
            .put("actual_sha256", status.actualSha256 ?: JSONObject.NULL)
            .put("expected_bytes", status.expectedBytes)
            .put("actual_bytes", status.actualBytes ?: JSONObject.NULL)
    }

internal fun softMasksJson(softMasks: List<ScannerSoftMaskResult>): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("mask_count", softMasks.size)
        .put("has_masks", softMasks.isNotEmpty())
        .put(
            "masks",
            JSONArray().apply {
                softMasks.forEach { mask ->
                    put(
                        JSONObject()
                            .put("mask", mask.maskPath)
                            .put("mask_sha256", mask.maskSha256)
                            .put("generator_name", mask.generatorName)
                            .put("generator_status", mask.generatorStatus)
                            .put(
                                "object_roi",
                                JSONObject()
                                    .put("source", mask.objectRoi.source.manifestValue)
                                    .put("tap_x_normalized", mask.objectRoi.xNormalized.toDouble())
                                    .put("tap_y_normalized", mask.objectRoi.yNormalized.toDouble())
                            )
                            .put("coverage_ratio", mask.coverageRatio.toDouble())
                            .put("edge_uncertainty", mask.edgeUncertainty.toDouble())
                            .put("center_support", mask.centerSupport.toDouble())
                            .put("warnings", JSONArray(mask.warnings))
                    )
                }
            }
        )

internal fun localReconstructionPreflightJson(result: ScannerLocalReconstructionPreflightResult): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("reasons", JSONArray(result.reasons))
        .put("accepted_frame_count", result.acceptedFrameCount)
        .put("frame_count", result.frameCount)
        .put(
            "accepted_passes",
            JSONArray().apply {
                result.acceptedPasses
                    .sortedBy { it.ordinal }
                    .forEach { put(it.manifestValue) }
            }
        )
        .put("scale_source", result.scaleSource.manifestValue)
        .put("scale_confidence", result.scaleConfidence.toDouble())
        .put("has_masks", result.hasMasks)
        .put("average_material_risk", result.averageMaterialRisk.toDouble())
        .put("forced_frame_ratio", result.forcedFrameRatio.toDouble())
        .put("alignment_method", result.alignmentMethod.manifestValue)

internal fun markerObservationsJson(
    observations: List<MarkerObservation>,
    evidence: MarkerEvidenceSummary
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("detector_name", evidence.detectorName)
        .put("detector_status", evidence.detectorStatus)
        .put(
            "summary",
            JSONObject()
                .put("observation_count", evidence.observationCount)
                .put("calibrated_observation_count", evidence.calibratedObservationCount)
                .put("observed_frame_count", evidence.observedFrameCount)
                .put("unique_marker_count", evidence.uniqueMarkerCount)
                .put("average_reprojection_error_px", evidence.averageReprojectionErrorPx ?: JSONObject.NULL)
                .put("marker_visibility", evidence.markerVisibility.toDouble())
                .put("marker_scale_confidence", evidence.markerScaleConfidence.toDouble())
                .put("reasons", JSONArray(evidence.reasons))
        )
        .put(
            "observations",
            JSONArray().apply {
                observations.forEach { observation ->
                    put(
                        JSONObject()
                            .put("marker_system", observation.markerSystem.manifestValue)
                            .put("marker_id", observation.markerId)
                            .put("frame_id", observation.frameId)
                            .put("frame_path", observation.framePath)
                            .put("marker_size_mm", observation.markerSizeMm.toDouble())
                            .put("hamming", observation.hamming ?: JSONObject.NULL)
                            .put("decision_margin", observation.decisionMargin ?: JSONObject.NULL)
                            .put("reprojection_error_px", observation.reprojectionErrorPx ?: JSONObject.NULL)
                            .put(
                                "corners_px",
                                JSONArray().apply {
                                    observation.cornersPx.forEach { corner ->
                                        put(JSONArray().put(corner.first.toDouble()).put(corner.second.toDouble()))
                                    }
                                }
                            )
                    )
                }
            }
        )

internal fun rejectedFramesJson(manifest: ScanPackageManifest): JSONObject =
    rejectedFramesJson(manifest.frames.filter { !it.quality.accepted })

internal fun rejectedFramesJson(frames: List<ScanFrame>): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("rejected_frame_count", frames.size)
        .put(
            "frames",
            JSONArray().apply {
                frames.forEach { frame ->
                    put(
                        JSONObject()
                            .put("frame_id", frame.id)
                            .put("image", frame.rgbPath)
                            .put("capture_pass", frame.capturePass.manifestValue)
                            .put("reasons", JSONArray(frame.quality.rejectionReasons))
                            .put("blur_score", frame.quality.blurScore.toDouble())
                            .put("exposure_score", frame.quality.exposureScore.toDouble())
                            .put("marker_visibility", frame.quality.markerVisibility.toDouble())
                            .put("scale_confidence", frame.quality.scaleConfidence.toDouble())
                    )
                }
            }
        )

internal fun coverageMapJson(
    manifest: ScanPackageManifest,
    readiness: ScannerReadinessSummary
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put(
            "passes",
            JSONArray().apply {
                ScannerCapturePass.entries.forEach { pass ->
                    val frames = manifest.frames.filter { it.capturePass == pass }
                    put(
                        JSONObject()
                            .put("capture_pass", pass.manifestValue)
                            .put("display_name", pass.displayName)
                            .put("frame_count", frames.size)
                            .put("accepted_frame_count", frames.count { it.quality.accepted })
                            .put("rejected_frame_count", frames.count { !it.quality.accepted })
                    )
                }
            }
        )
        .put(
            "readiness",
            JSONObject()
                .put("capture", readiness.captureReadiness.toDouble())
                .put("scale", readiness.scaleReadiness.toDouble())
                .put("coverage", readiness.coverageReadiness.toDouble())
                .put("material", readiness.materialReadiness.toDouble())
                .put("mask", readiness.maskReadiness.toDouble())
                .put("lighting", readiness.lightingReadiness.toDouble())
                .put("depth_pose", readiness.depthPoseReadiness.toDouble())
                .put("printable", readiness.printableReadiness.toDouble())
        )

internal fun deviceCapabilitiesJson(context: Context?): JSONObject {
    val packageManager = context?.packageManager
    val cameraDepthCapability = context?.let { scannerDeviceDepthCapability(it) }
    val arCoreAvailability = context?.let { scannerArCoreAvailability(it) }
    return JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("manufacturer", Build.MANUFACTURER ?: "unknown")
        .put("model", Build.MODEL ?: "unknown")
        .put("sdk_int", Build.VERSION.SDK_INT)
        .put("has_camera", packageManager?.hasSystemFeature("android.hardware.camera") ?: JSONObject.NULL)
        .put("has_camera_ar", packageManager?.hasSystemFeature("android.hardware.camera.ar") ?: JSONObject.NULL)
        .put("arcore_checked", arCoreAvailability?.checked ?: false)
        .put("arcore_depth_checked", false)
        .put("arcore", scannerArCoreAvailabilityJson(arCoreAvailability))
        .put("camera2_depth_output", scannerDeviceDepthCapabilityJson(cameraDepthCapability))
}

internal fun scannerArCoreAvailabilityJson(availability: ScannerArCoreAvailability?): JSONObject =
    if (availability == null) {
        JSONObject()
            .put("checked", false)
            .put("installed", false)
            .put("supported", false)
            .put("transient", false)
            .put("status", "context_unavailable")
    } else {
        JSONObject()
            .put("checked", availability.checked)
            .put("installed", availability.installed)
            .put("supported", availability.supported)
            .put("transient", availability.transient)
            .put("status", availability.status)
    }

internal fun scannerDeviceDepthCapabilityJson(capability: ScannerDeviceDepthCapability?): JSONObject =
    if (capability == null) {
        JSONObject()
            .put("checked", false)
            .put("depth_output_supported", false)
            .put("back_camera_checked", false)
            .put("reason", "context_unavailable")
    } else {
        JSONObject()
            .put("checked", capability.checked)
            .put("depth_output_supported", capability.depthOutputSupported)
            .put("back_camera_checked", capability.backCameraChecked)
            .put("reason", capability.reason)
    }
