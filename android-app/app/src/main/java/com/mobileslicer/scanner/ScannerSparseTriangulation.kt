package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_SPARSE_TRIANGULATION_PATH = "sparse/sparse_triangulation.json"

internal data class ScannerSparseTriangulationLimits(
    val minPreparedTracks: Int = 40,
    val minMeasuredSparsePoints: Int = 40,
    val minPoseCandidateCoverage: Float = 1.0f,
    val maxPointReprojectionResidualPx: Float = 2.5f,
    val requireVerifiedMarkerMat: Boolean = true
)

internal data class ScannerSparseMeasuredPoint(
    val pointId: String,
    val sourceTrackId: String,
    val contributingFrames: List<String>,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val reprojectionResidualPx: Float,
    val provenanceClass: String
)

internal data class ScannerSparseTriangulationResult(
    val allowed: Boolean,
    val metric: Boolean,
    val denseReconstructionReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val preparedTrackCount: Int,
    val poseCandidateCount: Int,
    val bundleAdjustmentMetric: Boolean,
    val measuredPointCount: Int,
    val measuredPoints: List<ScannerSparseMeasuredPoint>
)

internal fun prepareScannerSparseTriangulation(
    workspaceDir: File,
    limits: ScannerSparseTriangulationLimits = ScannerSparseTriangulationLimits()
): ScannerSparseTriangulationResult {
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
    val optimizedMetricPoses = workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val optimizedSparsePoints = workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }

    if (
        reconstructionJob == null ||
        metricPoseSolve == null ||
        sparseReconstruction == null ||
        bundleAdjustment == null ||
        optimizedMetricPoses == null ||
        optimizedSparsePoints == null
    ) {
        val result = blockedScannerSparseTriangulation(
            errors = buildList {
                if (reconstructionJob == null) add("reconstruction_job_missing")
                if (metricPoseSolve == null) add("metric_pose_solve_missing")
                if (sparseReconstruction == null) add("sparse_reconstruction_missing")
                if (bundleAdjustment == null) add("bundle_adjustment_missing")
                if (optimizedMetricPoses == null) add("optimized_metric_poses_missing")
                if (optimizedSparsePoints == null) add("optimized_sparse_points_missing")
            }
        )
        writeScannerSparseTriangulation(workspaceDir, result, limits)
        return result
    }

    val scaleSource = reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue)
    val preparedTrackCount = sparseReconstruction.optInt("prepared_track_count", 0)
    val poseCandidates = metricPoseSolve.optJSONArray("camera_pose_candidates") ?: JSONArray()
    val poseCandidateCount = poseCandidates.length()
    val bundleMetric = bundleAdjustment.optBoolean("metric", false)
    val bundleAllowed = bundleAdjustment.optBoolean("allowed", false)
    val metricPoseMetric = metricPoseSolve.optBoolean("metric", false)
    val metricPoseAllowed = metricPoseSolve.optBoolean("allowed", false)
    val optimizedPosesMetric = optimizedMetricPoses.optBoolean("metric", false)
    val optimizedPosesAllowed = optimizedMetricPoses.optBoolean("allowed", false)
    val optimizedSparseMetric = optimizedSparsePoints.optBoolean("metric", false)
    val optimizedSparseAllowed = optimizedSparsePoints.optBoolean("allowed", false)
    val poseCoverage = bundleAdjustment.optDouble("pose_candidate_coverage", 0.0).toFloat()
    val sparseTracks = sparseReconstruction.optJSONArray("tracks") ?: JSONArray()
    val optimizedPointJson = optimizedSparsePoints.optJSONArray("points") ?: JSONArray()
    val measuredPoints = buildMeasuredSparsePointsFromOptimized(
        optimizedPointJson = optimizedPointJson,
        sparseTracks = sparseTracks
    )
    val errors = buildList {
        if (limits.requireVerifiedMarkerMat && scaleSource != ScannerScaleSource.VerifiedMarkerMat.manifestValue) {
            add("verified_marker_mat_required")
        }
        if (!metricPoseAllowed) add("metric_pose_solve_blocked")
        if (!metricPoseMetric) add("metric_camera_poses_missing")
        if (!bundleAllowed) add("bundle_adjustment_blocked")
        if (!bundleMetric) add("bundle_adjustment_metric_missing")
        if (!optimizedPosesAllowed) add("optimized_metric_poses_blocked")
        if (!optimizedPosesMetric) add("optimized_metric_poses_missing")
        if (!optimizedSparseAllowed) add("optimized_sparse_points_blocked")
        if (!optimizedSparseMetric) add("optimized_sparse_points_missing")
        if (!sparseReconstruction.optBoolean("allowed", false)) add("sparse_reconstruction_blocked")
        if (preparedTrackCount < limits.minPreparedTracks) add("prepared_track_count_low")
        if (poseCandidateCount == 0) add("pose_candidates_missing")
        if (poseCoverage < limits.minPoseCandidateCoverage) add("pose_candidate_coverage_low")
        if (sparseTracks.length() == 0) add("sparse_tracks_missing")
        if (optimizedPointJson.length() == 0) add("optimized_sparse_point_artifact_empty")
        if (measuredPoints.size < limits.minMeasuredSparsePoints) add("measured_sparse_point_count_low")
        if (measuredPoints.any { it.reprojectionResidualPx > limits.maxPointReprojectionResidualPx }) {
            add("measured_sparse_point_residual_high")
        }
        if (measuredPoints.any { it.provenanceClass == "rejected" || it.provenanceClass == "blocked_scaffold" }) {
            add("measured_sparse_point_provenance_rejected")
        }
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerSparseTriangulationResult(
        allowed = accepted,
        metric = accepted,
        denseReconstructionReady = accepted,
        status = if (accepted) "accepted_metric_sparse_points" else "metric_sparse_points_blocked",
        errors = if (accepted) emptyList() else errors + "dense_reconstruction_blocked_until_sparse_points_are_metric",
        warnings = if (accepted) {
            listOf("dense_reconstruction_may_start_but_mesh_export_remains_gated")
        } else {
            listOf("metric_sparse_points_required_before_dense_reconstruction")
        },
        preparedTrackCount = preparedTrackCount,
        poseCandidateCount = poseCandidateCount,
        bundleAdjustmentMetric = bundleMetric,
        measuredPointCount = if (accepted) measuredPoints.size else 0,
        measuredPoints = if (accepted) measuredPoints else emptyList()
    )
    writeScannerSparseTriangulation(workspaceDir, result, limits)
    return result
}

private fun blockedScannerSparseTriangulation(errors: List<String>): ScannerSparseTriangulationResult =
    ScannerSparseTriangulationResult(
        allowed = false,
        metric = false,
        denseReconstructionReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        preparedTrackCount = 0,
        poseCandidateCount = 0,
        bundleAdjustmentMetric = false,
        measuredPointCount = 0,
        measuredPoints = emptyList()
    )

private fun buildMeasuredSparsePointsFromOptimized(
    optimizedPointJson: JSONArray,
    sparseTracks: JSONArray
): List<ScannerSparseMeasuredPoint> {
    val contributingFramesByTrack = List(sparseTracks.length()) { index -> sparseTracks.getJSONObject(index) }
        .mapNotNull { track ->
            val observations = track.optJSONArray("normalized_observations") ?: return@mapNotNull null
            val contributingFrames = List(observations.length()) { index ->
                observations.getJSONObject(index).getString("frame_id")
            }.distinct()
            track.getString("track_id") to contributingFrames
        }.toMap()
    return List(optimizedPointJson.length()) { index -> optimizedPointJson.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONArray("xyz_mm") ?: return@mapNotNull null
            if (xyz.length() != 3) return@mapNotNull null
            val residual = point.takeUnless { it.isNull("reprojection_residual_px") }
                ?.optDouble("reprojection_residual_px")
                ?.toFloat()
                ?: return@mapNotNull null
            val sourceTrackId = point.optString("source_track_id")
            val contributingFrames = contributingFramesByTrack[sourceTrackId].orEmpty()
            if (contributingFrames.size < 2) return@mapNotNull null
            ScannerSparseMeasuredPoint(
                pointId = point.optString("point_id"),
                sourceTrackId = sourceTrackId,
                contributingFrames = contributingFrames,
                xMm = xyz.getDouble(0),
                yMm = xyz.getDouble(1),
                zMm = xyz.getDouble(2),
                reprojectionResidualPx = residual,
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }
}

private fun writeScannerSparseTriangulation(
    workspaceDir: File,
    result: ScannerSparseTriangulationResult,
    limits: ScannerSparseTriangulationLimits
) {
    workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerSparseTriangulationJson(result, limits).toString(2))
    }
}

internal fun scannerSparseTriangulationJson(
    result: ScannerSparseTriangulationResult,
    limits: ScannerSparseTriangulationLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("dense_reconstruction_ready", result.denseReconstructionReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("prepared_track_count", result.preparedTrackCount)
        .put("pose_candidate_count", result.poseCandidateCount)
        .put("bundle_adjustment_metric", result.bundleAdjustmentMetric)
        .put("measured_point_count", result.measuredPointCount)
        .put(
            "limits",
            JSONObject()
                .put("min_prepared_tracks", limits.minPreparedTracks)
                .put("min_measured_sparse_points", limits.minMeasuredSparsePoints)
                .put("min_pose_candidate_coverage", limits.minPoseCandidateCoverage.toDouble())
                .put("max_point_reprojection_residual_px", limits.maxPointReprojectionResidualPx.toDouble())
                .put("require_verified_marker_mat", limits.requireVerifiedMarkerMat)
        )
        .put(
            "point_schema",
            JSONObject()
                .put("point_id", "Stable point identifier")
                .put("source_track_id", "Feature track used as source evidence")
                .put("contributing_frames", "Frames that observe this point")
                .put("xyz_mm", "Metric XYZ position in millimeters after validated triangulation")
                .put("reprojection_residual_px", "Maximum reprojection residual in source frames")
                .put("provenance_class", "measured_high | measured_low | rejected")
        )
        .put(
            "measured_points",
            JSONArray().apply {
                result.measuredPoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_track_id", point.sourceTrackId)
                            .put("contributing_frames", JSONArray(point.contributingFrames))
                            .put(
                                "xyz_mm",
                                JSONObject()
                                    .put("x", point.xMm)
                                    .put("y", point.yMm)
                                    .put("z", point.zMm)
                            )
                            .put("reprojection_residual_px", point.reprojectionResidualPx.toDouble())
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )
