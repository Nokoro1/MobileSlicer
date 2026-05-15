package com.mobileslicer.scanner

import org.json.JSONObject

internal object ScannerNativePoseOptimizerBridge {
    const val SolverName = "native_ceres_metric_pose_optimizer"

    fun status(): ScannerNativePoseOptimizerStatus =
        runCatching {
            if (!libraryLoaded) {
                return@runCatching ScannerNativePoseOptimizerStatus(
                    available = false,
                    status = "native_library_unavailable",
                    detail = loadError ?: "Native scanner_pose_optimizer bridge is unavailable",
                    solverName = SolverName,
                    ceresLinked = false,
                    optimizerLinked = false
                )
            }
            parseNativePoseOptimizerStatus(nativeStatusJson())
        }.getOrElse {
            ScannerNativePoseOptimizerStatus(
                available = false,
                status = "native_bridge_unavailable",
                detail = it.message ?: "Native scanner pose optimizer bridge is unavailable",
                solverName = SolverName,
                ceresLinked = false,
                optimizerLinked = false
            )
        }

    fun optimize(requestJson: String): ScannerNativePoseOptimizerResult =
        runCatching {
            if (!libraryLoaded) {
                return@runCatching ScannerNativePoseOptimizerResult(
                    success = false,
                    status = "native_library_unavailable",
                    detail = loadError ?: "Native scanner_pose_optimizer bridge is unavailable",
                    solverName = SolverName,
                    ceresLinked = false,
                    optimizerLinked = false,
                    optimizedCameraPoses = emptyList(),
                    optimizedSparsePoints = emptyList(),
                    perFrameResiduals = emptyList(),
                    perTrackResiduals = emptyList(),
                    markerResidualPx = null,
                    scaleResidualPercent = null,
                    markerCornerResiduals = emptyList(),
                    markerCornerResidualCount = 0,
                    markerCornerResidualMaxPx = null,
                    markerCornerResidualMeanPx = null,
                    markerCornerResidualBlocksEnabled = false,
                    uncertaintyByFrame = emptyList(),
                    poseConditioning = emptyList(),
                    prunedTrackReports = emptyList(),
                    robustLoss = null,
                    robustDeltaPx = null,
                    outlierGatePx = null,
                    iterativeOutlierPruningEnabled = false,
                    rejectedObservations = emptyList(),
                    rejectedTracks = emptyList(),
                    solverIterations = 0,
                    solverRuntimeMs = 0
                )
            }
            parseNativePoseOptimizerResult(nativeOptimizeJson(requestJson))
        }.getOrElse {
            ScannerNativePoseOptimizerResult(
                success = false,
                status = "native_bridge_unavailable",
                detail = it.message ?: "Native scanner pose optimizer bridge is unavailable",
                solverName = SolverName,
                ceresLinked = false,
                optimizerLinked = false,
                optimizedCameraPoses = emptyList(),
                optimizedSparsePoints = emptyList(),
                perFrameResiduals = emptyList(),
                perTrackResiduals = emptyList(),
                markerResidualPx = null,
                scaleResidualPercent = null,
                markerCornerResiduals = emptyList(),
                markerCornerResidualCount = 0,
                markerCornerResidualMaxPx = null,
                markerCornerResidualMeanPx = null,
                markerCornerResidualBlocksEnabled = false,
                uncertaintyByFrame = emptyList(),
                poseConditioning = emptyList(),
                prunedTrackReports = emptyList(),
                robustLoss = null,
                robustDeltaPx = null,
                outlierGatePx = null,
                iterativeOutlierPruningEnabled = false,
                rejectedObservations = emptyList(),
                rejectedTracks = emptyList(),
                solverIterations = 0,
                solverRuntimeMs = 0
            )
        }

    private external fun nativeStatusJson(): String
    private external fun nativeOptimizeJson(requestJson: String): String

    private val loadAttempt = runCatching { System.loadLibrary("scanner_pose_optimizer") }
    private val libraryLoaded = loadAttempt.isSuccess
    private val loadError = loadAttempt.exceptionOrNull()?.message
}

internal data class ScannerNativePoseOptimizerStatus(
    val available: Boolean,
    val status: String,
    val detail: String,
    val solverName: String,
    val ceresLinked: Boolean,
    val optimizerLinked: Boolean
)

internal data class ScannerNativeOptimizedCameraPose(
    val frameId: String,
    val rotationRowMajor: List<Double>,
    val translationMm: List<Double>,
    val residualPx: Float?,
    val metricValidated: Boolean
)

internal data class ScannerNativeOptimizedSparsePoint(
    val pointId: String,
    val sourceTrackId: String,
    val xyzMm: List<Double>,
    val reprojectionResidualPx: Float?,
    val provenanceClass: String
)

internal data class ScannerNativePoseOptimizerResult(
    val success: Boolean,
    val status: String,
    val detail: String,
    val solverName: String,
    val ceresLinked: Boolean,
    val optimizerLinked: Boolean,
    val optimizedCameraPoses: List<ScannerNativeOptimizedCameraPose>,
    val optimizedSparsePoints: List<ScannerNativeOptimizedSparsePoint>,
    val perFrameResiduals: List<JSONObject>,
    val perTrackResiduals: List<JSONObject>,
    val markerResidualPx: Float?,
    val scaleResidualPercent: Float?,
    val markerCornerResiduals: List<JSONObject>,
    val markerCornerResidualCount: Int,
    val markerCornerResidualMaxPx: Float?,
    val markerCornerResidualMeanPx: Float?,
    val markerCornerResidualBlocksEnabled: Boolean,
    val uncertaintyByFrame: List<JSONObject>,
    val poseConditioning: List<JSONObject>,
    val prunedTrackReports: List<JSONObject>,
    val robustLoss: String?,
    val robustDeltaPx: Float?,
    val outlierGatePx: Float?,
    val iterativeOutlierPruningEnabled: Boolean,
    val rejectedObservations: List<JSONObject>,
    val rejectedTracks: List<String>,
    val solverIterations: Int,
    val solverRuntimeMs: Long
)

private fun parseNativePoseOptimizerStatus(jsonText: String): ScannerNativePoseOptimizerStatus {
    val json = JSONObject(jsonText)
    return ScannerNativePoseOptimizerStatus(
        available = json.optBoolean("available", false),
        status = json.optString("status", "native_optimizer_unavailable"),
        detail = json.optString("detail", ""),
        solverName = json.optString("solver_name", ScannerNativePoseOptimizerBridge.SolverName),
        ceresLinked = json.optBoolean("ceres_linked", false),
        optimizerLinked = json.optBoolean("optimizer_linked", json.optBoolean("ceres_linked", false))
    )
}

private fun parseNativePoseOptimizerResult(jsonText: String): ScannerNativePoseOptimizerResult {
    val json = JSONObject(jsonText)
    return ScannerNativePoseOptimizerResult(
        success = json.optBoolean("success", false),
        status = json.optString("status", "native_optimizer_failed"),
        detail = json.optString("detail", ""),
        solverName = json.optString("solver_name", ScannerNativePoseOptimizerBridge.SolverName),
        ceresLinked = json.optBoolean("ceres_linked", false),
        optimizerLinked = json.optBoolean("optimizer_linked", json.optBoolean("ceres_linked", false)),
        optimizedCameraPoses = parseNativeOptimizedCameraPoses(json.optJSONArray("optimized_camera_poses")),
        optimizedSparsePoints = parseNativeOptimizedSparsePoints(json.optJSONArray("optimized_sparse_points")),
        perFrameResiduals = json.optJSONArray("per_frame_residuals").nativeOptimizerObjectList(),
        perTrackResiduals = json.optJSONArray("per_track_residuals").nativeOptimizerObjectList(),
        markerResidualPx = json.nativeOptimizerNullableFloat("marker_residual_px"),
        scaleResidualPercent = json.nativeOptimizerNullableFloat("scale_residual_percent"),
        markerCornerResiduals = json.optJSONArray("marker_corner_residuals").nativeOptimizerObjectList(),
        markerCornerResidualCount = json.optInt("marker_corner_residual_count", 0),
        markerCornerResidualMaxPx = json.nativeOptimizerNullableFloat("marker_corner_residual_max_px"),
        markerCornerResidualMeanPx = json.nativeOptimizerNullableFloat("marker_corner_residual_mean_px"),
        markerCornerResidualBlocksEnabled = json.optBoolean("marker_corner_residual_blocks_enabled", false),
        uncertaintyByFrame = json.optJSONArray("uncertainty_by_frame").nativeOptimizerObjectList(),
        poseConditioning = json.optJSONArray("pose_conditioning").nativeOptimizerObjectList(),
        prunedTrackReports = json.optJSONArray("pruned_track_reports").nativeOptimizerObjectList(),
        robustLoss = json.optString("robust_loss").takeIf { it.isNotBlank() },
        robustDeltaPx = json.nativeOptimizerNullableFloat("robust_delta_px"),
        outlierGatePx = json.nativeOptimizerNullableFloat("outlier_gate_px"),
        iterativeOutlierPruningEnabled = json.optBoolean("iterative_outlier_pruning_enabled", false),
        rejectedObservations = json.optJSONArray("rejected_observations").nativeOptimizerObjectList(),
        rejectedTracks = json.optJSONArray("rejected_tracks").nativeOptimizerStringList(),
        solverIterations = json.optInt("solver_iterations", 0),
        solverRuntimeMs = json.optLong("solver_runtime_ms", 0L)
    )
}

private fun parseNativeOptimizedCameraPoses(
    poses: org.json.JSONArray?
): List<ScannerNativeOptimizedCameraPose> =
    if (poses == null) {
        emptyList()
    } else {
        List(poses.length()) { poses.getJSONObject(it) }.map { pose ->
            ScannerNativeOptimizedCameraPose(
                frameId = pose.optString("frame_id"),
                rotationRowMajor = pose.optJSONArray("rotation_row_major").nativeOptimizerDoubleList(),
                translationMm = pose.optJSONArray("translation_mm").nativeOptimizerDoubleList(),
                residualPx = pose.nativeOptimizerNullableFloat("residual_px"),
                metricValidated = pose.optBoolean("metric_validated", false)
            )
        }
    }

private fun parseNativeOptimizedSparsePoints(
    points: org.json.JSONArray?
): List<ScannerNativeOptimizedSparsePoint> =
    if (points == null) {
        emptyList()
    } else {
        List(points.length()) { points.getJSONObject(it) }.map { point ->
            ScannerNativeOptimizedSparsePoint(
                pointId = point.optString("point_id"),
                sourceTrackId = point.optString("source_track_id"),
                xyzMm = point.optJSONArray("xyz_mm").nativeOptimizerDoubleList(),
                reprojectionResidualPx = point.nativeOptimizerNullableFloat("reprojection_residual_px"),
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }
    }

private fun JSONObject.nativeOptimizerNullableFloat(name: String): Float? =
    if (!has(name) || isNull(name)) null else optDouble(name).toFloat()

private fun org.json.JSONArray?.nativeOptimizerDoubleList(): List<Double> =
    if (this == null) emptyList() else List(length()) { getDouble(it) }

private fun org.json.JSONArray?.nativeOptimizerStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }

private fun org.json.JSONArray?.nativeOptimizerObjectList(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }
