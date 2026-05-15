package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_OPTIMIZED_METRIC_POSES_PATH = "poses/optimized_metric_poses.json"
internal const val SCANNER_OPTIMIZED_SPARSE_POINTS_PATH = "sparse/optimized_sparse_points.json"

internal data class ScannerPoseOptimizerLimits(
    val minPoseCandidateCoverage: Float = 1.0f,
    val minPreparedTrackCount: Int = 40,
    val minOptimizedPoseCount: Int = 3,
    val minOptimizedSparsePointCount: Int = 40,
    val maxFrameResidualPx: Float = 2.0f,
    val maxTrackResidualPx: Float = 2.5f,
    val maxMarkerResidualPx: Float = 3.0f,
    val maxScaleResidualPercent: Float = 1.5f,
    val minMarkerCornerObservationCount: Int = 16,
    val minAcceptedMatchSpatialCells: Int = 2,
    val minAverageMatchMaskSupport: Float = 16f,
    val requireVerifiedMarkerMat: Boolean = true
)

internal data class ScannerPoseEvidenceDiagnostics(
    val candidatePoseCount: Int,
    val fixedIntrinsicFrameCount: Int,
    val preparedTrackCount: Int,
    val markerCornerObservationCount: Int,
    val acceptedMatchPairCount: Int,
    val maxMatchSpatialCellCount: Int,
    val averageAcceptedMatchMaskSupport: Float?,
    val blockerHints: List<String>
)

internal data class ScannerOptimizedPoseCandidate(
    val frameId: String,
    val rotationRowMajor: List<Double>,
    val translationMm: List<Double>,
    val residualPx: Float?,
    val metricValidated: Boolean
)

internal data class ScannerOptimizedSparsePoint(
    val pointId: String,
    val sourceTrackId: String,
    val xyzMm: List<Double>,
    val reprojectionResidualPx: Float?,
    val provenanceClass: String
)

internal data class ScannerPoseOptimizerResult(
    val allowed: Boolean,
    val metric: Boolean,
    val status: String,
    val nativeOptimizerStatus: ScannerNativePoseOptimizerStatus,
    val nativeOptimizerResult: ScannerNativePoseOptimizerResult,
    val optimizerRequest: JSONObject?,
    val errors: List<String>,
    val warnings: List<String>,
    val evidenceDiagnostics: ScannerPoseEvidenceDiagnostics,
    val poseCandidateCount: Int,
    val preparedTrackCount: Int,
    val optimizedPoseCount: Int,
    val optimizedSparsePointCount: Int,
    val optimizedPoses: List<ScannerOptimizedPoseCandidate>,
    val optimizedSparsePoints: List<ScannerOptimizedSparsePoint>
)

internal fun optimizeScannerMetricPoses(
    workspaceDir: File,
    limits: ScannerPoseOptimizerLimits = ScannerPoseOptimizerLimits(),
    nativeOptimizerStatusProvider: () -> ScannerNativePoseOptimizerStatus = ScannerNativePoseOptimizerBridge::status,
    nativeOptimizer: (String) -> ScannerNativePoseOptimizerResult = ScannerNativePoseOptimizerBridge::optimize
): ScannerPoseOptimizerResult {
    val nativeOptimizerStatus = nativeOptimizerStatusProvider()
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val metricPoseSolve = workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val sparseReconstruction = workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val bundleAdjustment = workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val featureTracks = workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val matchGraph = workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (
        reconstructionJob == null ||
        metricPoseSolve == null ||
        sparseReconstruction == null ||
        bundleAdjustment == null
    ) {
        val result = blockedScannerPoseOptimizer(
            errors = buildList {
                if (reconstructionJob == null) add("reconstruction_job_missing")
                if (metricPoseSolve == null) add("metric_pose_solve_missing")
                if (sparseReconstruction == null) add("sparse_reconstruction_missing")
                if (bundleAdjustment == null) add("bundle_adjustment_missing")
            },
            nativeOptimizerStatus = nativeOptimizerStatus
        )
        writeScannerPoseOptimizerArtifacts(workspaceDir, result, limits)
        return result
    }

    val scaleSource = reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue)
    val poseCandidates = metricPoseSolve.optJSONArray("camera_pose_candidates") ?: JSONArray()
    val preparedTrackCount = sparseReconstruction.optInt("prepared_track_count", 0)
    val poseCoverage = bundleAdjustment.optDouble("pose_candidate_coverage", 0.0).toFloat()
    val frameResidual = bundleAdjustment.nullableOptimizerFloat("frame_residual_max_px")
    val trackResidual = bundleAdjustment.nullableOptimizerFloat("track_residual_max_px")
    val markerResidual = bundleAdjustment.nullableOptimizerFloat("marker_residual_px")
    val scaleResidual = bundleAdjustment.nullableOptimizerFloat("scale_residual_percent")
    val markerCornerObservationCount = reconstructionJob
        .optJSONArray("marker_corner_observations")
        ?.length()
        ?: 0
    val acceptedMatchPairs = matchGraph?.optJSONArray("pairs").optimizerObjectList()
        ?.filter { it.optBoolean("accepted", false) }
        .orEmpty()
    val maxMatchSpatialCells = acceptedMatchPairs.maxOfOrNull { it.optInt("spatial_cell_count", 0) } ?: 0
    val averageMatchMaskSupport = acceptedMatchPairs
        .mapNotNull { it.nullableOptimizerFloat("average_mask_support") }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
    val fixedIntrinsicFrameCount = (reconstructionJob.optJSONArray("frames") ?: JSONArray())
        .let { frames ->
            List(frames.length()) { index -> frames.getJSONObject(index) }
                .count { it.optJSONObject("intrinsics") != null }
        }
    val optimizerRequest = scannerPoseOptimizerRequestJson(
        reconstructionJob = reconstructionJob,
        metricPoseSolve = metricPoseSolve,
        sparseReconstruction = sparseReconstruction,
        bundleAdjustment = bundleAdjustment,
        featureTracks = featureTracks,
        matchGraph = matchGraph,
        limits = limits
    )
    val nativeOptimizerResult = nativeOptimizer(optimizerRequest.toString())
    val nativeOptimizedPoses = nativeOptimizerResult.optimizedCameraPoses
    val nativeOptimizedPoints = nativeOptimizerResult.optimizedSparsePoints
    val poseConditioningClasses = nativeOptimizerResult.poseConditioning.map {
        it.optString("conditioning_class", "missing")
    }
    val weakFrameClasses = nativeOptimizerResult.uncertaintyByFrame.map {
        it.optString("uncertainty_class", "weak")
    }
    val rejectedPointCount = nativeOptimizedPoints.count { it.provenanceClass == "rejected" }

    val errors = buildList {
        if (limits.requireVerifiedMarkerMat && scaleSource != ScannerScaleSource.VerifiedMarkerMat.manifestValue) {
            add("verified_marker_mat_required")
        }
        if (!metricPoseSolve.optBoolean("allowed", false)) add("metric_pose_solve_blocked")
        if (!metricPoseSolve.optBoolean("metric", false)) add("metric_pose_solve_not_metric")
        if (!bundleAdjustment.optBoolean("allowed", false)) add("bundle_adjustment_blocked")
        if (!bundleAdjustment.optBoolean("metric", false)) add("bundle_adjustment_not_metric")
        if (!sparseReconstruction.optBoolean("allowed", false)) add("sparse_reconstruction_blocked")
        if (poseCandidates.length() == 0) add("pose_candidates_missing")
        if (poseCoverage < limits.minPoseCandidateCoverage) add("pose_candidate_coverage_low")
        if (preparedTrackCount < limits.minPreparedTrackCount) add("prepared_track_count_low")
        if (frameResidual == null) add("frame_residual_missing")
        if (trackResidual == null) add("track_residual_missing")
        if (markerResidual == null) add("marker_residual_missing")
        if (scaleResidual == null) add("scale_residual_missing")
        if (markerCornerObservationCount < limits.minMarkerCornerObservationCount) {
            add("marker_corner_observation_count_low")
        }
        if (fixedIntrinsicFrameCount < poseCandidates.length()) add("fixed_intrinsics_missing_for_pose_candidates")
        if (matchGraph == null) add("match_graph_missing_for_optimizer")
        if (acceptedMatchPairs.isEmpty()) add("accepted_match_pairs_missing")
        if (acceptedMatchPairs.isNotEmpty() && maxMatchSpatialCells < limits.minAcceptedMatchSpatialCells) {
            add("accepted_match_spatial_distribution_low")
        }
        if (
            averageMatchMaskSupport != null &&
            averageMatchMaskSupport < limits.minAverageMatchMaskSupport
        ) {
            add("accepted_match_mask_support_low")
        }
        if (frameResidual != null && frameResidual > limits.maxFrameResidualPx) add("frame_residual_high")
        if (trackResidual != null && trackResidual > limits.maxTrackResidualPx) add("track_residual_high")
        if (markerResidual != null && markerResidual > limits.maxMarkerResidualPx) add("marker_residual_high")
        if (scaleResidual != null && scaleResidual > limits.maxScaleResidualPercent) add("scale_residual_high")
        if (!nativeOptimizerStatus.available) add("native_pose_optimizer_unavailable:${nativeOptimizerStatus.status}")
        if (!nativeOptimizerStatus.optimizerLinked) add("native_metric_optimizer_not_linked")
        if (!nativeOptimizerResult.success) add("native_pose_optimizer_failed:${nativeOptimizerResult.status}")
        if (nativeOptimizedPoses.isEmpty()) add("optimized_pose_output_missing")
        if (nativeOptimizedPoints.isEmpty()) add("optimized_sparse_point_output_missing")
        if (nativeOptimizedPoses.size < limits.minOptimizedPoseCount) add("optimized_pose_count_low")
        if (nativeOptimizedPoints.size < limits.minOptimizedSparsePointCount) add("optimized_sparse_point_count_low")
        if (nativeOptimizedPoses.any { !it.metricValidated }) add("optimized_pose_not_metric_validated")
        if (nativeOptimizedPoses.any { it.rotationRowMajor.size != 9 || it.translationMm.size != 3 }) {
            add("optimized_pose_shape_invalid")
        }
        if (nativeOptimizedPoses.any { it.residualPx == null }) add("optimized_pose_residual_missing")
        if (nativeOptimizedPoses.any { (it.residualPx ?: Float.MAX_VALUE) > limits.maxFrameResidualPx }) {
            add("optimized_pose_residual_high")
        }
        if (nativeOptimizedPoints.any { it.xyzMm.size != 3 }) add("optimized_sparse_point_shape_invalid")
        if (nativeOptimizedPoints.any { it.reprojectionResidualPx == null }) add("optimized_sparse_point_residual_missing")
        if (nativeOptimizedPoints.any { (it.reprojectionResidualPx ?: Float.MAX_VALUE) > limits.maxTrackResidualPx }) {
            add("optimized_sparse_point_residual_high")
        }
        if (rejectedPointCount > 0) add("optimized_sparse_point_rejected")
        if (nativeOptimizerResult.markerCornerResidualCount < limits.minMarkerCornerObservationCount) {
            add("native_marker_corner_residual_count_low")
        }
        if (nativeOptimizerResult.markerCornerResidualMaxPx == null) add("native_marker_corner_residual_missing")
        if ((nativeOptimizerResult.markerCornerResidualMaxPx ?: 0f) > limits.maxMarkerResidualPx) {
            add("native_marker_corner_residual_high")
        }
        if (nativeOptimizerResult.scaleResidualPercent == null) add("native_scale_residual_missing")
        if ((nativeOptimizerResult.scaleResidualPercent ?: 0f) > limits.maxScaleResidualPercent) {
            add("native_scale_residual_high")
        }
        if (poseConditioningClasses.isEmpty()) add("pose_conditioning_missing")
        if (poseConditioningClasses.any { it == "ill_conditioned" || it == "underdetermined" || it == "missing_intrinsics" }) {
            add("pose_conditioning_failed")
        }
    }.distinct()
    val optimizedPoses = if (errors.isEmpty()) {
        nativeOptimizedPoses.map { pose ->
            ScannerOptimizedPoseCandidate(
                frameId = pose.frameId,
                rotationRowMajor = pose.rotationRowMajor,
                translationMm = pose.translationMm,
                residualPx = pose.residualPx,
                metricValidated = pose.metricValidated
            )
        }
    } else {
        emptyList()
    }
    val optimizedSparsePoints = if (errors.isEmpty()) {
        nativeOptimizedPoints.map { point ->
            ScannerOptimizedSparsePoint(
                pointId = point.pointId,
                sourceTrackId = point.sourceTrackId,
                xyzMm = point.xyzMm,
                reprojectionResidualPx = point.reprojectionResidualPx,
                provenanceClass = point.provenanceClass
            )
        }
    } else {
        emptyList()
    }
    val accepted = errors.isEmpty()
    val evidenceDiagnostics = ScannerPoseEvidenceDiagnostics(
        candidatePoseCount = poseCandidates.length(),
        fixedIntrinsicFrameCount = fixedIntrinsicFrameCount,
        preparedTrackCount = preparedTrackCount,
        markerCornerObservationCount = markerCornerObservationCount,
        acceptedMatchPairCount = acceptedMatchPairs.size,
        maxMatchSpatialCellCount = maxMatchSpatialCells,
        averageAcceptedMatchMaskSupport = averageMatchMaskSupport,
        blockerHints = scannerPoseOptimizerBlockerHints(errors)
    )
    val result = ScannerPoseOptimizerResult(
        allowed = accepted,
        metric = accepted,
        status = if (accepted) "accepted_metric_sparse_optimizer_output" else "optimizer_boundary_blocked",
        nativeOptimizerStatus = nativeOptimizerStatus,
        nativeOptimizerResult = nativeOptimizerResult,
        optimizerRequest = optimizerRequest,
        errors = errors,
        warnings = buildList {
            if (nativeOptimizerResult.optimizerLinked && !nativeOptimizerResult.ceresLinked) {
                add("eigen_backend_accepted_under_strict_metric_gates")
                add("production_ceres_or_equivalent_bundle_adjustment_still_recommended")
            }
            if (poseConditioningClasses.any { it == "weakly_conditioned" }) add("weak_pose_conditioning_present")
            if (weakFrameClasses.any { it == "weak" }) add("weak_frame_uncertainty_present")
        },
        evidenceDiagnostics = evidenceDiagnostics,
        poseCandidateCount = poseCandidates.length(),
        preparedTrackCount = preparedTrackCount,
        optimizedPoseCount = optimizedPoses.size,
        optimizedSparsePointCount = optimizedSparsePoints.size,
        optimizedPoses = optimizedPoses,
        optimizedSparsePoints = optimizedSparsePoints
    )
    writeScannerPoseOptimizerArtifacts(workspaceDir, result, limits)
    return result
}

private fun blockedScannerPoseOptimizer(
    errors: List<String>,
    nativeOptimizerStatus: ScannerNativePoseOptimizerStatus
): ScannerPoseOptimizerResult =
    ScannerPoseOptimizerResult(
        allowed = false,
        metric = false,
        status = "blocked",
        nativeOptimizerStatus = nativeOptimizerStatus,
        nativeOptimizerResult = blockedNativePoseOptimizerResult(nativeOptimizerStatus),
        optimizerRequest = null,
        errors = errors.distinct(),
        warnings = emptyList(),
        evidenceDiagnostics = ScannerPoseEvidenceDiagnostics(
            candidatePoseCount = 0,
            fixedIntrinsicFrameCount = 0,
            preparedTrackCount = 0,
            markerCornerObservationCount = 0,
            acceptedMatchPairCount = 0,
            maxMatchSpatialCellCount = 0,
            averageAcceptedMatchMaskSupport = null,
            blockerHints = scannerPoseOptimizerBlockerHints(errors)
        ),
        poseCandidateCount = 0,
        preparedTrackCount = 0,
        optimizedPoseCount = 0,
        optimizedSparsePointCount = 0,
        optimizedPoses = emptyList(),
        optimizedSparsePoints = emptyList()
    )

private fun writeScannerPoseOptimizerArtifacts(
    workspaceDir: File,
    result: ScannerPoseOptimizerResult,
    limits: ScannerPoseOptimizerLimits
) {
    workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerOptimizedMetricPosesJson(result, limits).toString(2))
    }
    workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerOptimizedSparsePointsJson(result, limits).toString(2))
    }
}

internal fun scannerOptimizedMetricPosesJson(
    result: ScannerPoseOptimizerResult,
    limits: ScannerPoseOptimizerLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("status", result.status)
        .put("native_optimizer", nativePoseOptimizerStatusJson(result.nativeOptimizerStatus))
        .put("optimizer_request", result.optimizerRequest ?: JSONObject.NULL)
        .put("native_optimizer_response", nativePoseOptimizerResultJson(result.nativeOptimizerResult))
        .put("evidence_diagnostics", scannerPoseEvidenceDiagnosticsJson(result.evidenceDiagnostics))
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("pose_candidate_count", result.poseCandidateCount)
        .put("optimized_pose_count", result.optimizedPoseCount)
        .put(
            "limits",
            JSONObject()
                .put("min_pose_candidate_coverage", limits.minPoseCandidateCoverage.toDouble())
                .put("min_prepared_track_count", limits.minPreparedTrackCount)
                .put("min_optimized_pose_count", limits.minOptimizedPoseCount)
                .put("min_optimized_sparse_point_count", limits.minOptimizedSparsePointCount)
                .put("max_frame_residual_px", limits.maxFrameResidualPx.toDouble())
                .put("max_track_residual_px", limits.maxTrackResidualPx.toDouble())
                .put("max_marker_residual_px", limits.maxMarkerResidualPx.toDouble())
                .put("max_scale_residual_percent", limits.maxScaleResidualPercent.toDouble())
                .put("min_marker_corner_observation_count", limits.minMarkerCornerObservationCount)
                .put("min_accepted_match_spatial_cells", limits.minAcceptedMatchSpatialCells)
                .put("min_average_match_mask_support", limits.minAverageMatchMaskSupport.toDouble())
                .put("require_verified_marker_mat", limits.requireVerifiedMarkerMat)
        )
        .put(
            "poses",
            JSONArray().apply {
                result.optimizedPoses.forEach { pose ->
                    put(
                        JSONObject()
                            .put("frame_id", pose.frameId)
                            .put("rotation_row_major", JSONArray(pose.rotationRowMajor))
                            .put("translation_mm", JSONArray(pose.translationMm))
                            .put("residual_px", pose.residualPx ?: JSONObject.NULL)
                            .put("metric_validated", pose.metricValidated)
                    )
                }
            }
        )

internal fun scannerOptimizedSparsePointsJson(
    result: ScannerPoseOptimizerResult,
    limits: ScannerPoseOptimizerLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("status", result.status)
        .put("native_optimizer", nativePoseOptimizerStatusJson(result.nativeOptimizerStatus))
        .put("optimizer_request", result.optimizerRequest ?: JSONObject.NULL)
        .put("native_optimizer_response", nativePoseOptimizerResultJson(result.nativeOptimizerResult))
        .put("evidence_diagnostics", scannerPoseEvidenceDiagnosticsJson(result.evidenceDiagnostics))
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("prepared_track_count", result.preparedTrackCount)
        .put("optimized_sparse_point_count", result.optimizedSparsePointCount)
        .put(
            "point_schema",
            JSONObject()
                .put("point_id", "Stable optimized sparse point id")
                .put("source_track_id", "Feature track used as source evidence")
                .put("xyz_mm", "Optimized metric XYZ position in millimeters")
                .put("reprojection_residual_px", "Post-optimization reprojection residual")
                .put("provenance_class", "measured_high | measured_low | rejected")
        )
        .put(
            "limits",
            JSONObject()
                .put("min_prepared_track_count", limits.minPreparedTrackCount)
                .put("min_optimized_sparse_point_count", limits.minOptimizedSparsePointCount)
                .put("max_track_residual_px", limits.maxTrackResidualPx.toDouble())
        )
        .put(
            "points",
            JSONArray().apply {
                result.optimizedSparsePoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_track_id", point.sourceTrackId)
                            .put("xyz_mm", JSONArray(point.xyzMm))
                            .put("reprojection_residual_px", point.reprojectionResidualPx ?: JSONObject.NULL)
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )

private fun nativePoseOptimizerStatusJson(status: ScannerNativePoseOptimizerStatus): JSONObject =
    JSONObject()
        .put("available", status.available)
        .put("status", status.status)
        .put("detail", status.detail)
        .put("solver_name", status.solverName)
        .put("ceres_linked", status.ceresLinked)
        .put("optimizer_linked", status.optimizerLinked)

private fun nativePoseOptimizerResultJson(result: ScannerNativePoseOptimizerResult): JSONObject =
    JSONObject()
        .put("success", result.success)
        .put("status", result.status)
        .put("detail", result.detail)
        .put("solver_name", result.solverName)
        .put("ceres_linked", result.ceresLinked)
        .put("optimizer_linked", result.optimizerLinked)
        .put(
            "optimized_camera_poses",
            JSONArray().apply {
                result.optimizedCameraPoses.forEach { pose ->
                    put(
                        JSONObject()
                            .put("frame_id", pose.frameId)
                            .put("rotation_row_major", JSONArray(pose.rotationRowMajor))
                            .put("translation_mm", JSONArray(pose.translationMm))
                            .put("residual_px", pose.residualPx ?: JSONObject.NULL)
                            .put("metric_validated", pose.metricValidated)
                    )
                }
            }
        )
        .put(
            "optimized_sparse_points",
            JSONArray().apply {
                result.optimizedSparsePoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_track_id", point.sourceTrackId)
                            .put("xyz_mm", JSONArray(point.xyzMm))
                            .put("reprojection_residual_px", point.reprojectionResidualPx ?: JSONObject.NULL)
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )
        .put("per_frame_residuals", JSONArray(result.perFrameResiduals))
        .put("per_track_residuals", JSONArray(result.perTrackResiduals))
        .put("marker_residual_px", result.markerResidualPx ?: JSONObject.NULL)
        .put("scale_residual_percent", result.scaleResidualPercent ?: JSONObject.NULL)
        .put("marker_corner_residuals", JSONArray(result.markerCornerResiduals))
        .put("marker_corner_residual_count", result.markerCornerResidualCount)
        .put("marker_corner_residual_max_px", result.markerCornerResidualMaxPx ?: JSONObject.NULL)
        .put("marker_corner_residual_mean_px", result.markerCornerResidualMeanPx ?: JSONObject.NULL)
        .put("marker_corner_residual_blocks_enabled", result.markerCornerResidualBlocksEnabled)
        .put("uncertainty_by_frame", JSONArray(result.uncertaintyByFrame))
        .put("pose_conditioning", JSONArray(result.poseConditioning))
        .put("pruned_track_reports", JSONArray(result.prunedTrackReports))
        .put("robust_loss", result.robustLoss ?: JSONObject.NULL)
        .put("robust_delta_px", result.robustDeltaPx ?: JSONObject.NULL)
        .put("outlier_gate_px", result.outlierGatePx ?: JSONObject.NULL)
        .put("iterative_outlier_pruning_enabled", result.iterativeOutlierPruningEnabled)
        .put("rejected_observations", JSONArray(result.rejectedObservations))
        .put("rejected_tracks", JSONArray(result.rejectedTracks))
        .put("solver_iterations", result.solverIterations)
        .put("solver_runtime_ms", result.solverRuntimeMs)

private fun blockedNativePoseOptimizerResult(
    status: ScannerNativePoseOptimizerStatus
): ScannerNativePoseOptimizerResult =
    ScannerNativePoseOptimizerResult(
        success = false,
        status = status.status,
        detail = status.detail,
        solverName = status.solverName,
        ceresLinked = status.ceresLinked,
        optimizerLinked = status.optimizerLinked,
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

internal fun scannerPoseOptimizerRequestJson(
    reconstructionJob: JSONObject,
    metricPoseSolve: JSONObject,
    sparseReconstruction: JSONObject,
    bundleAdjustment: JSONObject,
    featureTracks: JSONObject? = null,
    matchGraph: JSONObject? = null,
    limits: ScannerPoseOptimizerLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("solver_name", ScannerNativePoseOptimizerBridge.SolverName)
        .put("units", "millimeters")
        .put("fixed_intrinsics_by_frame", optimizerIntrinsicsByFrameJson(reconstructionJob))
        .put("candidate_camera_poses", metricPoseSolve.optJSONArray("camera_pose_candidates") ?: JSONArray())
        .put("feature_track_observations", optimizerFeatureTrackObservationsJson(sparseReconstruction))
        .put("feature_track_summary", optimizerFeatureTrackSummaryJson(featureTracks))
        .put("match_graph_evidence", optimizerMatchGraphEvidenceJson(matchGraph))
        .put("marker_corner_observations", reconstructionJob.optJSONArray("marker_corner_observations") ?: JSONArray())
        .put("marker_scale_constraint", optimizerMarkerScaleConstraintJson(reconstructionJob))
        .put("bundle_adjustment_preflight", optimizerBundleAdjustmentPreflightJson(bundleAdjustment))
        .put(
            "residual_limits",
            JSONObject()
                .put("max_frame_residual_px", limits.maxFrameResidualPx.toDouble())
                .put("max_track_residual_px", limits.maxTrackResidualPx.toDouble())
                .put("max_marker_residual_px", limits.maxMarkerResidualPx.toDouble())
                .put("max_scale_residual_percent", limits.maxScaleResidualPercent.toDouble())
                .put("min_marker_corner_observation_count", limits.minMarkerCornerObservationCount)
                .put("min_accepted_match_spatial_cells", limits.minAcceptedMatchSpatialCells)
                .put("min_average_match_mask_support", limits.minAverageMatchMaskSupport.toDouble())
                .put("min_pose_candidate_coverage", limits.minPoseCandidateCoverage.toDouble())
                .put("min_prepared_track_count", limits.minPreparedTrackCount)
                .put("require_verified_marker_mat", limits.requireVerifiedMarkerMat)
        )
        .put(
            "solver_limits",
            JSONObject()
                .put("fixed_intrinsics", true)
                .put("max_iterations", 50)
                .put("max_runtime_ms", 2000)
                .put("robust_loss", "huber")
                .put("optimize_camera_poses", true)
                .put("optimize_sparse_points", true)
                .put("optimize_intrinsics", false)
        )

private fun optimizerIntrinsicsByFrameJson(reconstructionJob: JSONObject): JSONObject {
    val output = JSONObject()
    val frames = reconstructionJob.optJSONArray("frames") ?: JSONArray()
    repeat(frames.length()) { index ->
        val frame = frames.getJSONObject(index)
        val intrinsics = frame.optJSONObject("intrinsics") ?: return@repeat
        output.put(
            frame.getString("frame_id"),
            JSONObject()
                .put("fx", intrinsics.getDouble("fx"))
                .put("fy", intrinsics.getDouble("fy"))
                .put("cx", intrinsics.getDouble("cx"))
                .put("cy", intrinsics.getDouble("cy"))
                .put("width", intrinsics.getInt("width"))
                .put("height", intrinsics.getInt("height"))
        )
    }
    return output
}

private fun optimizerFeatureTrackObservationsJson(sparseReconstruction: JSONObject): JSONArray {
    val tracks = sparseReconstruction.optJSONArray("tracks") ?: JSONArray()
    return JSONArray().apply {
        repeat(tracks.length()) { index ->
            val track = tracks.getJSONObject(index)
            val normalizedObservations = track.optJSONArray("normalized_observations") ?: JSONArray()
            put(
                JSONObject()
                    .put("track_id", track.getString("track_id"))
                    .put("observation_count", track.optInt("observation_count", normalizedObservations.length()))
                    .put("normalized_observations", normalizedObservations)
            )
        }
    }
}

private fun optimizerMarkerScaleConstraintJson(reconstructionJob: JSONObject): JSONObject {
    val calibration = reconstructionJob.optJSONObject("calibration")
    return JSONObject()
        .put("scale_source", reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue))
        .put("units", "millimeters")
        .put("verified_marker_mat", reconstructionJob.optString("scale_source") == ScannerScaleSource.VerifiedMarkerMat.manifestValue)
        .put("scale_confidence", calibration?.optDouble("scale_confidence", 0.0) ?: JSONObject.NULL)
        .put(
            "marker_reprojection_error_px",
            calibration?.takeIf { !it.isNull("marker_reprojection_error_px") }
                ?.optDouble("marker_reprojection_error_px")
                ?: JSONObject.NULL
        )
        .put("calibration", calibration ?: JSONObject.NULL)
}

private fun optimizerBundleAdjustmentPreflightJson(bundleAdjustment: JSONObject): JSONObject =
    JSONObject()
        .put("pose_candidate_coverage", bundleAdjustment.optDouble("pose_candidate_coverage", 0.0))
        .put("frame_residual_max_px", bundleAdjustment.nullableOptimizerFloat("frame_residual_max_px") ?: JSONObject.NULL)
        .put("track_residual_max_px", bundleAdjustment.nullableOptimizerFloat("track_residual_max_px") ?: JSONObject.NULL)
        .put("marker_residual_px", bundleAdjustment.nullableOptimizerFloat("marker_residual_px") ?: JSONObject.NULL)
        .put("scale_residual_percent", bundleAdjustment.nullableOptimizerFloat("scale_residual_percent") ?: JSONObject.NULL)
        .put("allowed", bundleAdjustment.optBoolean("allowed", false))
        .put("metric", bundleAdjustment.optBoolean("metric", false))

private fun JSONObject.nullableOptimizerFloat(name: String): Float? =
    if (isNull(name)) null else optDouble(name).toFloat()

private fun optimizerFeatureTrackSummaryJson(featureTracks: JSONObject?): JSONObject =
    JSONObject()
        .put("available", featureTracks != null)
        .put("allowed", featureTracks?.optBoolean("allowed", false) ?: false)
        .put("track_count", featureTracks?.optInt("track_count", 0) ?: 0)
        .put("long_track_count", featureTracks?.optInt("long_track_count", 0) ?: 0)
        .put("spatial_cell_count", featureTracks?.optInt("spatial_cell_count", 0) ?: 0)
        .put("errors", featureTracks?.optJSONArray("errors") ?: JSONArray())

private fun optimizerMatchGraphEvidenceJson(matchGraph: JSONObject?): JSONObject {
    val pairs = matchGraph?.optJSONArray("pairs").optimizerObjectList()
        ?.filter { it.optBoolean("accepted", false) }
        .orEmpty()
    return JSONObject()
        .put("available", matchGraph != null)
        .put("allowed", matchGraph?.optBoolean("allowed", false) ?: false)
        .put("accepted_pair_count", pairs.size)
        .put("max_spatial_cell_count", pairs.maxOfOrNull { it.optInt("spatial_cell_count", 0) } ?: 0)
        .put(
            "average_mask_support",
            pairs.mapNotNull { it.nullableOptimizerFloat("average_mask_support") }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: JSONObject.NULL
        )
        .put(
            "pairs",
            JSONArray().apply {
                pairs.forEach {
                    put(
                        JSONObject()
                            .put("frame_a", it.optString("frame_a"))
                            .put("frame_b", it.optString("frame_b"))
                            .put("match_count", it.optInt("match_count", 0))
                            .put("spatial_cell_count", it.optInt("spatial_cell_count", 0))
                            .put("average_descriptor_distance", it.opt("average_descriptor_distance") ?: JSONObject.NULL)
                            .put("average_mask_support", it.opt("average_mask_support") ?: JSONObject.NULL)
                    )
                }
            }
        )
}

private fun scannerPoseEvidenceDiagnosticsJson(evidence: ScannerPoseEvidenceDiagnostics): JSONObject =
    JSONObject()
        .put("candidate_pose_count", evidence.candidatePoseCount)
        .put("fixed_intrinsic_frame_count", evidence.fixedIntrinsicFrameCount)
        .put("prepared_track_count", evidence.preparedTrackCount)
        .put("marker_corner_observation_count", evidence.markerCornerObservationCount)
        .put("accepted_match_pair_count", evidence.acceptedMatchPairCount)
        .put("max_match_spatial_cell_count", evidence.maxMatchSpatialCellCount)
        .put("average_accepted_match_mask_support", evidence.averageAcceptedMatchMaskSupport ?: JSONObject.NULL)
        .put("blocker_hints", JSONArray(evidence.blockerHints))

private fun scannerPoseOptimizerBlockerHints(errors: List<String>): List<String> =
    buildList {
        if (errors.any { it.contains("intrinsics") }) add("Capture frames with valid fixed camera intrinsics before pose solve.")
        if (errors.any { it.contains("match") || it.contains("track") }) {
            add("Capture more textured overlapping views; weak feature tracks cannot produce metric poses.")
        }
        if (errors.any { it.contains("marker") || it.contains("scale") || it.contains("calibration") }) {
            add("Verified scale and marker reprojection evidence are required for printable metric output.")
        }
        if (errors.any { it.contains("native_pose_optimizer") || it.contains("optimized_") }) {
            add("Native optimizer must return metric validated poses and measured sparse points before dense reconstruction.")
        }
        if (errors.any { it.contains("residual") || it.contains("conditioning") }) {
            add("Residual or conditioning gates failed; reject the scan instead of handing noisy geometry to the slicer.")
        }
    }.distinct()

private fun JSONArray?.optimizerObjectList(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }
