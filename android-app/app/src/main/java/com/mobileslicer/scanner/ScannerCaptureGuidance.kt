package com.mobileslicer.scanner

import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_DEPTH_ANYTHING_V2_SMALL_MODEL =
    "scanner/depth/depth_anything_v2_small.tflite"

internal data class ScannerDepthPriorStatus(
    val available: Boolean,
    val status: String,
    val detail: String,
    val modelAssetPath: String = SCANNER_DEPTH_ANYTHING_V2_SMALL_MODEL,
    val metricTruth: Boolean = false
)

internal fun scannerDepthAnythingV2SmallStatus(assetExists: (String) -> Boolean): ScannerDepthPriorStatus {
    val exists = runCatching { assetExists(SCANNER_DEPTH_ANYTHING_V2_SMALL_MODEL) }.getOrDefault(false)
    return if (exists) {
        ScannerDepthPriorStatus(
            available = true,
            status = "model_asset_available",
            detail = "Depth Anything V2 Small model asset is present. Use only as relative-depth prior, not metric truth."
        )
    } else {
        ScannerDepthPriorStatus(
            available = false,
            status = "model_asset_missing",
            detail = "Depth Anything V2 Small model asset is not bundled. Local depth prior is disabled."
        )
    }
}

internal data class ScannerMaskQualitySummary(
    val maskCount: Int,
    val aiMaskCount: Int,
    val heuristicMaskCount: Int,
    val averageCoverageRatio: Float,
    val averageEdgeUncertainty: Float,
    val averageCenterSupport: Float,
    val consistency: Float,
    val warnings: List<String>
)

internal fun scannerMaskQualitySummary(softMasks: List<ScannerSoftMaskResult>): ScannerMaskQualitySummary {
    if (softMasks.isEmpty()) {
        return ScannerMaskQualitySummary(
            maskCount = 0,
            aiMaskCount = 0,
            heuristicMaskCount = 0,
            averageCoverageRatio = 0f,
            averageEdgeUncertainty = 1f,
            averageCenterSupport = 0f,
            consistency = 0f,
            warnings = listOf("object_masks_missing")
        )
    }
    val coverage = softMasks.map { it.coverageRatio.coerceIn(0f, 1f) }
    val averageCoverage = coverage.average().toFloat()
    val coverageSpread = (coverage.maxOrNull() ?: 0f) - (coverage.minOrNull() ?: 0f)
    val averageEdgeUncertainty = softMasks.map { it.edgeUncertainty.coerceIn(0f, 1f) }.average().toFloat()
    val averageCenterSupport = softMasks.map { it.centerSupport.coerceIn(0f, 1f) }.average().toFloat()
    val aiMasks = softMasks.count { it.generatorName.contains("mediapipe", ignoreCase = true) }
    val heuristicMasks = softMasks.count {
        it.generatorName.contains("heuristic", ignoreCase = true) ||
            it.warnings.any { warning -> warning == "heuristic_fallback_used" || warning == "heuristic_soft_mask_not_ai" }
    }
    val consistency = (
        (1f - coverageSpread.coerceIn(0f, 1f)) * 0.35f +
            (1f - averageEdgeUncertainty).coerceIn(0f, 1f) * 0.30f +
            averageCenterSupport.coerceIn(0f, 1f) * 0.25f +
            (aiMasks.toFloat() / softMasks.size.toFloat()).coerceIn(0f, 1f) * 0.10f
        ).coerceIn(0f, 1f)
    val warnings = buildList {
        if (aiMasks == 0) add("ai_object_masks_missing")
        if (heuristicMasks > 0) add("heuristic_masks_present")
        if (averageCoverage < 0.08f) add("average_mask_coverage_low")
        if (averageCoverage > 0.75f) add("average_mask_coverage_high")
        if (coverageSpread > 0.35f) add("mask_coverage_inconsistent")
        if (averageEdgeUncertainty > 0.45f) add("mask_edges_uncertain")
        if (averageCenterSupport < 0.40f) add("object_tap_support_low")
        softMasks.flatMapTo(this) { it.warnings }
    }.distinct()
    return ScannerMaskQualitySummary(
        maskCount = softMasks.size,
        aiMaskCount = aiMasks,
        heuristicMaskCount = heuristicMasks,
        averageCoverageRatio = averageCoverage,
        averageEdgeUncertainty = averageEdgeUncertainty,
        averageCenterSupport = averageCenterSupport,
        consistency = consistency,
        warnings = warnings
    )
}

internal data class ScannerCaptureGuidanceReport(
    val messages: List<String>,
    val blockingReasons: List<String>,
    val warnings: List<String>
)

internal fun scannerCaptureGuidanceReport(
    manifest: ScanPackageManifest,
    readiness: ScannerReadinessSummary,
    maskSummary: ScannerMaskQualitySummary,
    depthPriorStatus: ScannerDepthPriorStatus
): ScannerCaptureGuidanceReport {
    val acceptedFrames = manifest.frames.filter { it.quality.accepted }
    val rejectionReasons = manifest.frames.flatMap { it.quality.rejectionReasons }.toSet()
    val passCounts = ScannerCapturePass.entries.associateWith { pass ->
        acceptedFrames.count { it.capturePass == pass }
    }
    val messages = buildList {
        passCounts
            .filterValues { it == 0 }
            .keys
            .sortedBy { it.ordinal }
            .forEach { add("Need ${it.displayName.lowercase()} views") }
        if (rejectionReasons.contains("too_blurry")) add("Move slower")
        if (rejectionReasons.contains("bad_exposure") || readiness.lightingReadiness < 0.65f) add("Improve lighting")
        if (rejectionReasons.contains("clipped_highlights")) add("Reduce glare")
        if (rejectionReasons.contains("object_too_small") || maskSummary.averageCoverageRatio < 0.08f) add("Get closer to the object")
        if (maskSummary.aiMaskCount == 0) add("Tap the object and bundle the MediaPipe segmenter model for better masks")
        if (maskSummary.averageEdgeUncertainty > 0.45f) add("Retap the object or use a less cluttered background")
        if (readiness.scaleReadiness < 0.85f) add("Show the verified marker mat clearly")
        if (readiness.materialReadiness < 0.70f) add("Matte spray or avoid shiny/transparent materials")
        if (!depthPriorStatus.available) add("Depth prior unavailable; capture extra angles")
    }.distinct()
    val blockingReasons = buildList {
        if (acceptedFrames.size < 24) add("not_enough_accepted_frames")
        if (readiness.coverageReadiness < 0.80f) add("coverage_incomplete")
        if (readiness.scaleReadiness < 0.85f) add("scale_not_ready")
        if (readiness.materialReadiness < 0.55f) add("material_risk_high")
        if (maskSummary.consistency < 0.45f) add("mask_consistency_low")
    }
    val warnings = (maskSummary.warnings + depthPriorStatus.status).distinct()
    return ScannerCaptureGuidanceReport(
        messages = messages,
        blockingReasons = blockingReasons,
        warnings = warnings
    )
}

internal fun scannerMaskQualityJson(summary: ScannerMaskQualitySummary): JSONObject =
    JSONObject()
        .put("mask_count", summary.maskCount)
        .put("ai_mask_count", summary.aiMaskCount)
        .put("heuristic_mask_count", summary.heuristicMaskCount)
        .put("average_coverage_ratio", summary.averageCoverageRatio.toDouble())
        .put("average_edge_uncertainty", summary.averageEdgeUncertainty.toDouble())
        .put("average_center_support", summary.averageCenterSupport.toDouble())
        .put("consistency", summary.consistency.toDouble())
        .put("warnings", JSONArray(summary.warnings))

internal fun scannerDepthPriorStatusJson(status: ScannerDepthPriorStatus): JSONObject =
    JSONObject()
        .put("available", status.available)
        .put("status", status.status)
        .put("detail", status.detail)
        .put("model_asset_path", status.modelAssetPath)
        .put("metric_truth", status.metricTruth)

internal fun scannerCaptureGuidanceJson(report: ScannerCaptureGuidanceReport): JSONObject =
    JSONObject()
        .put("messages", JSONArray(report.messages))
        .put("blocking_reasons", JSONArray(report.blockingReasons))
        .put("warnings", JSONArray(report.warnings))
