package com.mobileslicer.scanner

import org.json.JSONArray
import org.json.JSONObject

internal enum class ScannerLocalAiComponentState(val manifestValue: String) {
    Ready("ready"),
    Degraded("degraded"),
    Blocked("blocked"),
    Disabled("disabled")
}

internal data class ScannerLocalAiComponentStatus(
    val name: String,
    val state: ScannerLocalAiComponentState,
    val productionReady: Boolean,
    val metricTruth: Boolean,
    val detail: String,
    val warnings: List<String> = emptyList()
)

internal data class ScannerLocalAiStackStatus(
    val overallState: ScannerLocalAiComponentState,
    val productionReady: Boolean,
    val metricTruth: Boolean,
    val components: List<ScannerLocalAiComponentStatus>,
    val warnings: List<String>,
    val blockingReasons: List<String>
)

internal fun scannerLocalAiStackStatus(
    maskSummary: ScannerMaskQualitySummary,
    mediaPipeStatus: ScannerMediaPipeSoftMaskStatus?,
    depthPriorStatus: ScannerDepthPriorStatus,
    guidance: ScannerCaptureGuidanceReport
): ScannerLocalAiStackStatus {
    val segmentationState = when {
        mediaPipeStatus?.productionReady == true && maskSummary.aiMaskCount > 0 ->
            ScannerLocalAiComponentState.Ready
        mediaPipeStatus?.productionReady == true ->
            ScannerLocalAiComponentState.Degraded
        maskSummary.heuristicMaskCount > 0 ->
            ScannerLocalAiComponentState.Degraded
        else ->
            ScannerLocalAiComponentState.Blocked
    }
    val guidanceState = if (guidance.messages.isNotEmpty() || guidance.blockingReasons.isNotEmpty()) {
        ScannerLocalAiComponentState.Ready
    } else {
        ScannerLocalAiComponentState.Degraded
    }
    val depthState = if (depthPriorStatus.available) {
        ScannerLocalAiComponentState.Ready
    } else {
        ScannerLocalAiComponentState.Disabled
    }
    val components = listOf(
        ScannerLocalAiComponentStatus(
            name = "tap_object_segmentation",
            state = segmentationState,
            productionReady = mediaPipeStatus?.productionReady == true,
            metricTruth = false,
            detail = when (segmentationState) {
                ScannerLocalAiComponentState.Ready ->
                    "MediaPipe MagicTouch is verified and accepted frames have AI soft masks."
                ScannerLocalAiComponentState.Degraded ->
                    "Object masks are present but not all are verified AI masks. Treat them as weak soft evidence."
                ScannerLocalAiComponentState.Blocked ->
                    "No object masks are available. Local printable reconstruction remains blocked."
                ScannerLocalAiComponentState.Disabled ->
                    "Object segmentation is disabled."
            },
            warnings = maskSummary.warnings
        ),
        ScannerLocalAiComponentStatus(
            name = "capture_quality_guidance",
            state = guidanceState,
            productionReady = true,
            metricTruth = false,
            detail = "Local quality logic scores blur, lighting, coverage, material risk, mask consistency, and capture blockers.",
            warnings = guidance.warnings
        ),
        ScannerLocalAiComponentStatus(
            name = "monocular_depth_prior",
            state = depthState,
            productionReady = depthPriorStatus.available,
            metricTruth = false,
            detail = depthPriorStatus.detail,
            warnings = if (depthPriorStatus.available) {
                listOf("depth_prior_is_not_metric_truth")
            } else {
                listOf(depthPriorStatus.status)
            }
        )
    )
    val blockingReasons = buildList {
        if (segmentationState == ScannerLocalAiComponentState.Blocked) add("object_segmentation_unavailable")
        addAll(guidance.blockingReasons)
    }.distinct()
    val warnings = components.flatMap { it.warnings }.distinct()
    val overallState = when {
        blockingReasons.isNotEmpty() -> ScannerLocalAiComponentState.Blocked
        components.any { it.state == ScannerLocalAiComponentState.Degraded } -> ScannerLocalAiComponentState.Degraded
        else -> ScannerLocalAiComponentState.Ready
    }
    return ScannerLocalAiStackStatus(
        overallState = overallState,
        productionReady = components
            .filterNot { it.name == "monocular_depth_prior" }
            .all { it.productionReady },
        metricTruth = false,
        components = components,
        warnings = warnings,
        blockingReasons = blockingReasons
    )
}

internal fun scannerLocalAiStackStatusJson(status: ScannerLocalAiStackStatus): JSONObject =
    JSONObject()
        .put("overall_state", status.overallState.manifestValue)
        .put("production_ready", status.productionReady)
        .put("metric_truth", status.metricTruth)
        .put("warnings", JSONArray(status.warnings))
        .put("blocking_reasons", JSONArray(status.blockingReasons))
        .put(
            "components",
            JSONArray().apply {
                status.components.forEach { component ->
                    put(
                        JSONObject()
                            .put("name", component.name)
                            .put("state", component.state.manifestValue)
                            .put("production_ready", component.productionReady)
                            .put("metric_truth", component.metricTruth)
                            .put("detail", component.detail)
                            .put("warnings", JSONArray(component.warnings))
                    )
                }
            }
        )
