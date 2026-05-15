package com.mobileslicer.scanner

internal data class ScannerLocalReconstructionLimits(
    val minAcceptedFrames: Int = 36,
    val maxInputFrames: Int = 160,
    val requiredPasses: Set<ScannerCapturePass> = setOf(
        ScannerCapturePass.LowRing,
        ScannerCapturePass.MidRing,
        ScannerCapturePass.HighRing
    ),
    val requireVerifiedScale: Boolean = true,
    val minimumScaleConfidence: Float = 0.85f,
    val requireSoftMasks: Boolean = true,
    val requireCameraIntrinsics: Boolean = true,
    val maxForcedFrameRatio: Float = 0.10f,
    val maxAverageMaterialRisk: Float = 0.55f
)

internal data class ScannerLocalReconstructionPreflightResult(
    val allowed: Boolean,
    val reasons: List<String>,
    val acceptedFrameCount: Int,
    val frameCount: Int,
    val acceptedPasses: Set<ScannerCapturePass>,
    val scaleSource: ScannerScaleSource,
    val scaleConfidence: Float,
    val hasMasks: Boolean,
    val averageMaterialRisk: Float,
    val forcedFrameRatio: Float,
    val alignmentMethod: ScannerAlignmentMethod
)

internal fun evaluateLocalReconstructionPreflight(
    manifest: ScanPackageManifest,
    limits: ScannerLocalReconstructionLimits = ScannerLocalReconstructionLimits()
): ScannerLocalReconstructionPreflightResult {
    val acceptedFrames = manifest.frames.filter { it.quality.accepted }
    val acceptedPasses = acceptedFrames.map { it.capturePass }.toSet()
    val scaleConfidence = manifest.calibration?.scaleConfidence ?: 0f
    val averageMaterialRisk = acceptedFrames
        .map { it.quality.materialRisk.coerceIn(0f, 1f) }
        .ifEmpty { listOf(1f) }
        .average()
        .toFloat()
    val forcedFrameRatio = if (manifest.frameCount == 0) {
        0f
    } else {
        manifest.forcedFrameCount.toFloat() / manifest.frameCount.toFloat()
    }
    val reasons = buildList {
        if (manifest.frameCount == 0) add("capture_empty")
        if (manifest.frameCount > limits.maxInputFrames) add("input_frame_cap_exceeded")
        if (acceptedFrames.size < limits.minAcceptedFrames) add("not_enough_accepted_frames")
        limits.requiredPasses
            .filterNot { it in acceptedPasses }
            .forEach { add("missing_capture_pass:${it.manifestValue}") }
        if (limits.requireVerifiedScale && manifest.scaleSource != ScannerScaleSource.VerifiedMarkerMat) {
            add("scale_unverified")
        }
        if (limits.requireVerifiedScale && manifest.calibration == null) {
            add("calibration_missing")
        }
        if (limits.requireVerifiedScale && scaleConfidence < limits.minimumScaleConfidence) {
            add("scale_confidence_low")
        }
        if (limits.requireSoftMasks && !manifest.hasMasks) {
            add("object_masks_missing")
        }
        if (limits.requireCameraIntrinsics && acceptedFrames.any { it.intrinsics == null }) {
            add("camera_intrinsics_missing")
        }
        if (
            manifest.twoSidedAlignment.objectMovedDuringSession &&
            manifest.twoSidedAlignment.alignmentMethod == ScannerAlignmentMethod.Unknown
        ) {
            add("two_sided_alignment_unknown")
        }
        if (forcedFrameRatio > limits.maxForcedFrameRatio) {
            add("too_many_forced_frames")
        }
        if (averageMaterialRisk > limits.maxAverageMaterialRisk) {
            add("material_unsuitable")
        }
    }
    return ScannerLocalReconstructionPreflightResult(
        allowed = reasons.isEmpty(),
        reasons = reasons,
        acceptedFrameCount = acceptedFrames.size,
        frameCount = manifest.frameCount,
        acceptedPasses = acceptedPasses,
        scaleSource = manifest.scaleSource,
        scaleConfidence = scaleConfidence,
        hasMasks = manifest.hasMasks,
        averageMaterialRisk = averageMaterialRisk,
        forcedFrameRatio = forcedFrameRatio,
        alignmentMethod = manifest.twoSidedAlignment.alignmentMethod
    )
}
