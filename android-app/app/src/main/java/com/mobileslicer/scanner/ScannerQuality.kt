package com.mobileslicer.scanner

import kotlin.math.sqrt

internal const val SCANNER_MAX_RETAINED_REJECTED_FRAMES_PER_PASS = 8

internal data class ScannerFrameQualityThresholds(
    val minBlurScore: Float = 80f,
    val minExposureScore: Float = 0.55f,
    val minOverlapScore: Float = 0.35f,
    val minLateCoverageGain: Float = 0.02f,
    val minObjectMaskArea: Float = 0.08f,
    val maxClippedHighlightRatio: Float = 0.05f,
    val minDepthCoverageWhenRequired: Float = 0.30f,
    val minMarkerVisibilityWhenRequired: Float = 0.20f,
    val minScaleConfidenceWhenRequired: Float = 0.80f,
    val maxMaterialRisk: Float = 0.75f
)

internal data class ScannerFrameAdmissionThresholds(
    val minAcceptedFrameSpacingNs: Long = 650_000_000L,
    val minSamePassViewTranslationMm: Float = 12f,
    val minDepthCoverageWhenPresent: Float = 0.12f
)

internal data class ScannerFrameQualityInput(
    val blurScore: Float,
    val exposureScore: Float,
    val overlapScore: Float,
    val coverageGain: Float,
    val objectMaskArea: Float,
    val clippedHighlightRatio: Float,
    val trackingGood: Boolean?,
    val depthCoverage: Float?,
    val markerVisibility: Float,
    val scaleConfidence: Float,
    val materialRisk: Float,
    val depthRequired: Boolean,
    val markersRequired: Boolean,
    val scaleRequired: Boolean,
    val lateCapture: Boolean
)

internal fun evaluateScannerFrameQuality(
    input: ScannerFrameQualityInput,
    thresholds: ScannerFrameQualityThresholds = ScannerFrameQualityThresholds()
): FrameQuality {
    val rejectionReasons = buildList {
        if (input.blurScore < thresholds.minBlurScore) add("too_blurry")
        if (input.exposureScore < thresholds.minExposureScore) add("bad_exposure")
        if (input.overlapScore < thresholds.minOverlapScore) add("insufficient_overlap")
        if (input.lateCapture && input.coverageGain < thresholds.minLateCoverageGain) {
            add("insufficient_new_coverage")
        }
        if (input.objectMaskArea < thresholds.minObjectMaskArea) add("object_too_small")
        if (input.clippedHighlightRatio > thresholds.maxClippedHighlightRatio) add("clipped_highlights")
        if (input.trackingGood == false) add("tracking_not_ready")
        if (input.depthRequired && (input.depthCoverage ?: 0f) < thresholds.minDepthCoverageWhenRequired) {
            add("insufficient_depth_coverage")
        }
        if (input.markersRequired && input.markerVisibility < thresholds.minMarkerVisibilityWhenRequired) {
            add("markers_not_visible")
        }
        if (input.scaleRequired && input.scaleConfidence < thresholds.minScaleConfidenceWhenRequired) {
            add("scale_unverified")
        }
        if (input.materialRisk > thresholds.maxMaterialRisk) add("material_risk_high")
    }
    return FrameQuality(
        blurScore = input.blurScore,
        exposureScore = input.exposureScore,
        overlapScore = input.overlapScore,
        trackingGood = input.trackingGood,
        depthCoverage = input.depthCoverage,
        markerVisibility = input.markerVisibility,
        scaleConfidence = input.scaleConfidence,
        materialRisk = input.materialRisk,
        accepted = rejectionReasons.isEmpty(),
        rejectionReasons = rejectionReasons
    )
}

internal fun applyScannerFrameAdmissionQuality(
    frame: ScanFrame,
    previousFrames: List<ScanFrame>,
    thresholds: ScannerFrameAdmissionThresholds = ScannerFrameAdmissionThresholds()
): ScanFrame {
    if (!frame.quality.accepted) return frame
    val accepted = previousFrames.filter { it.quality.accepted }
    val reasons = buildList {
        val newestAccepted = accepted.maxByOrNull { it.timestampNs }
        if (
            newestAccepted != null &&
            frame.timestampNs > newestAccepted.timestampNs &&
            frame.timestampNs - newestAccepted.timestampNs < thresholds.minAcceptedFrameSpacingNs
        ) {
            add("capture_too_fast")
        }
        val samePassReference = accepted
            .asReversed()
            .firstOrNull { it.capturePass == frame.capturePass && it.poseWorldFromCamera != null }
        val poseDeltaMm = samePassReference?.poseWorldFromCamera?.let { referencePose ->
            scannerPoseTranslationDeltaMm(referencePose, frame.poseWorldFromCamera)
        }
        if (poseDeltaMm != null && poseDeltaMm < thresholds.minSamePassViewTranslationMm) {
            add("insufficient_view_change")
        }
        val depthCoverage = frame.quality.depthCoverage
        if (depthCoverage != null && depthCoverage < thresholds.minDepthCoverageWhenPresent) {
            add("depth_coverage_weak")
        }
    }
    if (reasons.isEmpty()) return frame
    return frame.copy(
        quality = frame.quality.copy(
            accepted = false,
            rejectionReasons = (frame.quality.rejectionReasons + reasons).distinct()
        )
    )
}

internal fun scannerPoseTranslationDeltaMm(
    first: FloatArray?,
    second: FloatArray?
): Float? {
    if (first == null || second == null || first.size != 16 || second.size != 16) return null
    val columnMajorDelta = translationDistance(first, second, 12, 13, 14)
    val rowMajorDelta = translationDistance(first, second, 3, 7, 11)
    val delta = maxOf(columnMajorDelta, rowMajorDelta)
    return if (delta < 10f) delta * 1000f else delta
}

private fun translationDistance(
    first: FloatArray,
    second: FloatArray,
    xIndex: Int,
    yIndex: Int,
    zIndex: Int
): Float {
    val dx = second[xIndex] - first[xIndex]
    val dy = second[yIndex] - first[yIndex]
    val dz = second[zIndex] - first[zIndex]
    return sqrt(dx * dx + dy * dy + dz * dz)
}

internal data class ScannerReadinessInput(
    val angleCoverage: Float,
    val imageSharpness: Float,
    val scaleConfidence: Float,
    val maskConsistency: Float,
    val lightingScore: Float,
    val depthPoseSupport: Float
)

internal fun scannerReadinessScore(input: ScannerReadinessInput): Float =
    (
        0.30f * input.angleCoverage.coerceIn(0f, 1f) +
            0.20f * input.imageSharpness.coerceIn(0f, 1f) +
            0.15f * input.scaleConfidence.coerceIn(0f, 1f) +
            0.15f * input.maskConsistency.coerceIn(0f, 1f) +
            0.10f * input.lightingScore.coerceIn(0f, 1f) +
            0.10f * input.depthPoseSupport.coerceIn(0f, 1f)
        ).coerceIn(0f, 1f)
