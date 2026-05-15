package com.mobileslicer.scanner

import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_BUNDLE_ADJUSTMENT_PATH = "poses/bundle_adjustment.json"

internal data class ScannerBundleAdjustmentLimits(
    val maxFrameConstraintResidualPx: Float = 2.0f,
    val maxTrackObservationSpreadPx: Float = 2.5f,
    val maxMarkerResidualPx: Float = 3.0f,
    val maxScaleResidualPercent: Float = 1.5f,
    val minPreparedTrackCount: Int = 40,
    val minPoseCandidateCoverage: Float = 1.0f
)

internal data class ScannerBundleAdjustmentFrameResidual(
    val frameId: String,
    val constraintCount: Int,
    val residualPxEstimate: Float
)

internal data class ScannerBundleAdjustmentTrackResidual(
    val trackId: String,
    val observationCount: Int,
    val residualPxEstimate: Float
)

internal data class ScannerBundleAdjustmentResult(
    val allowed: Boolean,
    val metric: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val poseCandidateCount: Int,
    val poseCandidateCoverage: Float,
    val preparedTrackCount: Int,
    val frameResidualMaxPx: Float?,
    val trackResidualMaxPx: Float?,
    val markerResidualPx: Float?,
    val scaleResidualPercent: Float?,
    val rejectedConstraintCount: Int,
    val frameResiduals: List<ScannerBundleAdjustmentFrameResidual>,
    val trackResiduals: List<ScannerBundleAdjustmentTrackResidual>
)

internal fun analyzeScannerBundleAdjustment(
    workspaceDir: File,
    limits: ScannerBundleAdjustmentLimits = ScannerBundleAdjustmentLimits()
): ScannerBundleAdjustmentResult {
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val metricPoseGraph = workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val sparseReconstruction = workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (reconstructionJob == null || metricPoseGraph == null || sparseReconstruction == null) {
        val result = blockedScannerBundleAdjustment(
            errors = buildList {
                if (reconstructionJob == null) add("reconstruction_job_missing")
                if (metricPoseGraph == null) add("metric_pose_graph_missing")
                if (sparseReconstruction == null) add("sparse_reconstruction_missing")
            }
        )
        writeScannerBundleAdjustment(workspaceDir, result, limits)
        return result
    }

    val frameIds = metricPoseGraphFrameIds(metricPoseGraph)
    val poseCandidates = scannerBundlePoseCandidates(metricPoseGraph)
    val constraints = metricPoseGraphConstraints(metricPoseGraph)
    val usableConstraints = constraints.filter { it.available }
    val frameResiduals = estimateFrameConstraintResiduals(frameIds, poseCandidates, usableConstraints)
    val trackResiduals = estimateTrackObservationResiduals(sparseReconstruction, reconstructionJob)
    val frameResidualMax = frameResiduals.maxOfOrNull { it.residualPxEstimate }
    val trackResidualMax = trackResiduals.maxOfOrNull { it.residualPxEstimate }
    val markerResidual = metricPoseGraph
        .takeIf { !it.isNull("marker_reprojection_error_px") }
        ?.optDouble("marker_reprojection_error_px")
        ?.toFloat()
    val scaleResidual = reconstructionJob.optJSONObject("calibration")
        ?.optDouble("scale_correction", 1.0)
        ?.let { abs(1.0 - it).toFloat() * 100f }
    val preparedTrackCount = sparseReconstruction.optInt("prepared_track_count", 0)
    val poseCoverage = if (frameIds.isEmpty()) {
        0f
    } else {
        poseCandidates.keys.count { it in frameIds }.toFloat() / frameIds.size.toFloat()
    }
    val rejectedConstraints = constraints.count { !it.available } +
        usableConstraints.count { constraint ->
            estimateConstraintResidualPx(
                poseCandidates[constraint.frameA]?.translationGraphUnits,
                poseCandidates[constraint.frameB]?.translationGraphUnits,
                constraint.translationUnit
            ) > limits.maxFrameConstraintResidualPx
        }
    val errors = buildList {
        if (!metricPoseGraph.optBoolean("allowed", false)) add("metric_pose_graph_blocked")
        if (!sparseReconstruction.optBoolean("allowed", false)) add("sparse_reconstruction_blocked")
        if (poseCandidates.isEmpty()) add("pose_candidates_missing")
        if (poseCoverage < limits.minPoseCandidateCoverage) add("pose_candidate_coverage_low")
        if (preparedTrackCount < limits.minPreparedTrackCount) add("prepared_track_count_low")
        if (frameResidualMax == null) add("frame_residuals_missing")
        if (trackResidualMax == null) add("track_residuals_missing")
        if (markerResidual == null) add("marker_residual_missing")
        if (scaleResidual == null) add("scale_residual_missing")
        if (frameResidualMax != null && frameResidualMax > limits.maxFrameConstraintResidualPx) {
            add("frame_constraint_residual_high")
        }
        if (trackResidualMax != null && trackResidualMax > limits.maxTrackObservationSpreadPx) {
            add("track_observation_spread_high")
        }
        if (markerResidual != null && markerResidual > limits.maxMarkerResidualPx) {
            add("marker_residual_high")
        }
        if (scaleResidual != null && scaleResidual > limits.maxScaleResidualPercent) {
            add("scale_residual_high")
        }
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerBundleAdjustmentResult(
        allowed = accepted,
        metric = accepted,
        status = if (accepted) "optimizer_admission_ready" else "residual_preflight_blocked",
        errors = errors,
        warnings = buildList {
            add("residual_estimates_feed_native_optimizer")
            if (accepted) {
                add("native_optimizer_still_required_for_final_metric_sparse_points")
            } else {
                add("metric_output_blocked")
            }
        },
        poseCandidateCount = poseCandidates.size,
        poseCandidateCoverage = poseCoverage,
        preparedTrackCount = preparedTrackCount,
        frameResidualMaxPx = frameResidualMax,
        trackResidualMaxPx = trackResidualMax,
        markerResidualPx = markerResidual,
        scaleResidualPercent = scaleResidual,
        rejectedConstraintCount = rejectedConstraints,
        frameResiduals = frameResiduals,
        trackResiduals = trackResiduals
    )
    writeScannerBundleAdjustment(workspaceDir, result, limits)
    return result
}

private data class BundlePoseCandidate(
    val frameId: String,
    val translationGraphUnits: List<Double>
)

private data class BundleConstraint(
    val frameA: String,
    val frameB: String,
    val available: Boolean,
    val translationUnit: List<Double>
)

private fun blockedScannerBundleAdjustment(errors: List<String>): ScannerBundleAdjustmentResult =
    ScannerBundleAdjustmentResult(
        allowed = false,
        metric = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        poseCandidateCount = 0,
        poseCandidateCoverage = 0f,
        preparedTrackCount = 0,
        frameResidualMaxPx = null,
        trackResidualMaxPx = null,
        markerResidualPx = null,
        scaleResidualPercent = null,
        rejectedConstraintCount = 0,
        frameResiduals = emptyList(),
        trackResiduals = emptyList()
    )

private fun metricPoseGraphFrameIds(metricPoseGraph: JSONObject): List<String> {
    val nodes = metricPoseGraph.optJSONArray("nodes") ?: JSONArray()
    return List(nodes.length()) { index -> nodes.getJSONObject(index).getString("frame_id") }
}

private fun scannerBundlePoseCandidates(metricPoseGraph: JSONObject): Map<String, BundlePoseCandidate> {
    if (!metricPoseGraph.optBoolean("allowed", false)) return emptyMap()
    if (!metricPoseGraph.optBoolean("bundle_adjustment_ready", false)) return emptyMap()
    val frameIds = metricPoseGraphFrameIds(metricPoseGraph)
    if (frameIds.isEmpty()) return emptyMap()
    val constraints = metricPoseGraphConstraints(metricPoseGraph).filter { it.available }
    if (constraints.isEmpty()) return emptyMap()
    val adjacency = mutableMapOf<String, MutableList<Pair<String, List<Double>>>>()
    constraints.forEach {
        adjacency.getOrPut(it.frameA) { mutableListOf() } += it.frameB to it.translationUnit
        adjacency.getOrPut(it.frameB) { mutableListOf() } += it.frameA to it.translationUnit.map { value -> -value }
    }
    val root = frameIds.first()
    val candidates = linkedMapOf(root to BundlePoseCandidate(root, listOf(0.0, 0.0, 0.0)))
    val queue = ArrayDeque<String>()
    queue += root
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val currentPose = candidates.getValue(current)
        adjacency[current].orEmpty()
            .sortedBy { it.first }
            .forEach { (next, delta) ->
                if (next !in candidates) {
                    candidates[next] = BundlePoseCandidate(
                        frameId = next,
                        translationGraphUnits = scannerBundleAdd(currentPose.translationGraphUnits, delta)
                    )
                    queue += next
                }
            }
    }
    return candidates
}

private fun metricPoseGraphConstraints(metricPoseGraph: JSONObject): List<BundleConstraint> {
    val constraints = metricPoseGraph.optJSONArray("relative_pose_constraints") ?: JSONArray()
    return List(constraints.length()) { index -> constraints.getJSONObject(index) }
        .map {
            BundleConstraint(
                frameA = it.getString("frame_a"),
                frameB = it.getString("frame_b"),
                available = it.optBoolean("relative_pose_available", false),
                translationUnit = it.optJSONArray("translation_unit").scannerBundleDoubleList()
            )
        }
}

private fun estimateFrameConstraintResiduals(
    frameIds: List<String>,
    poseCandidates: Map<String, BundlePoseCandidate>,
    constraints: List<BundleConstraint>
): List<ScannerBundleAdjustmentFrameResidual> =
    frameIds.mapNotNull { frameId ->
        val residuals = constraints
            .filter { it.frameA == frameId || it.frameB == frameId }
            .mapNotNull { constraint ->
                estimateConstraintResidualPx(
                    poseCandidates[constraint.frameA]?.translationGraphUnits,
                    poseCandidates[constraint.frameB]?.translationGraphUnits,
                    constraint.translationUnit
                ).takeIf { it.isFinite() }
            }
        if (residuals.isEmpty()) {
            null
        } else {
            ScannerBundleAdjustmentFrameResidual(
                frameId = frameId,
                constraintCount = residuals.size,
                residualPxEstimate = residuals.max()
            )
        }
    }

private fun estimateConstraintResidualPx(
    translationA: List<Double>?,
    translationB: List<Double>?,
    expectedDirection: List<Double>
): Float {
    if (translationA == null || translationB == null || expectedDirection.size < 3) return Float.POSITIVE_INFINITY
    val candidateDirection = scannerBundleNormalize(
        List(3) { index -> (translationB.getOrNull(index) ?: 0.0) - (translationA.getOrNull(index) ?: 0.0) }
    )
    val expected = scannerBundleNormalize(expectedDirection)
    val dot = (0 until 3).sumOf { index -> candidateDirection[index] * expected[index] }.coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(dot)).toFloat()
}

private fun estimateTrackObservationResiduals(
    sparseReconstruction: JSONObject,
    reconstructionJob: JSONObject
): List<ScannerBundleAdjustmentTrackResidual> {
    val focalByFrame = scannerBundleAverageFocalByFrame(reconstructionJob)
    val tracks = sparseReconstruction.optJSONArray("tracks") ?: JSONArray()
    return List(tracks.length()) { index -> tracks.getJSONObject(index) }
        .mapNotNull { track ->
            val observations = track.optJSONArray("normalized_observations") ?: return@mapNotNull null
            val points = List(observations.length()) { index -> observations.getJSONObject(index) }
            if (points.size < 2) return@mapNotNull null
            val centerX = points.map { it.getDouble("x_normalized_camera") }.average()
            val centerY = points.map { it.getDouble("y_normalized_camera") }.average()
            val residualPx = points.maxOf { observation ->
                val frameId = observation.getString("frame_id")
                val focal = focalByFrame[frameId] ?: 1.0
                val dx = observation.getDouble("x_normalized_camera") - centerX
                val dy = observation.getDouble("y_normalized_camera") - centerY
                sqrt(dx * dx + dy * dy) * focal
            }.toFloat()
            ScannerBundleAdjustmentTrackResidual(
                trackId = track.getString("track_id"),
                observationCount = points.size,
                residualPxEstimate = residualPx
            )
        }
}

private fun scannerBundleAverageFocalByFrame(reconstructionJob: JSONObject): Map<String, Double> {
    val frames = reconstructionJob.optJSONArray("frames") ?: JSONArray()
    return List(frames.length()) { index -> frames.getJSONObject(index) }
        .mapNotNull { frame ->
            val intrinsics = frame.optJSONObject("intrinsics") ?: return@mapNotNull null
            frame.getString("frame_id") to ((intrinsics.getDouble("fx") + intrinsics.getDouble("fy")) * 0.5)
        }
        .toMap()
}

private fun writeScannerBundleAdjustment(
    workspaceDir: File,
    result: ScannerBundleAdjustmentResult,
    limits: ScannerBundleAdjustmentLimits
) {
    workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerBundleAdjustmentJson(result, limits).toString(2))
    }
}

internal fun scannerBundleAdjustmentJson(
    result: ScannerBundleAdjustmentResult,
    limits: ScannerBundleAdjustmentLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("pose_candidate_count", result.poseCandidateCount)
        .put("pose_candidate_coverage", result.poseCandidateCoverage.toDouble())
        .put("prepared_track_count", result.preparedTrackCount)
        .put("frame_residual_max_px", result.frameResidualMaxPx ?: JSONObject.NULL)
        .put("track_residual_max_px", result.trackResidualMaxPx ?: JSONObject.NULL)
        .put("marker_residual_px", result.markerResidualPx ?: JSONObject.NULL)
        .put("scale_residual_percent", result.scaleResidualPercent ?: JSONObject.NULL)
        .put("rejected_constraint_count", result.rejectedConstraintCount)
        .put(
            "limits",
            JSONObject()
                .put("max_frame_constraint_residual_px", limits.maxFrameConstraintResidualPx.toDouble())
                .put("max_track_observation_spread_px", limits.maxTrackObservationSpreadPx.toDouble())
                .put("max_marker_residual_px", limits.maxMarkerResidualPx.toDouble())
                .put("max_scale_residual_percent", limits.maxScaleResidualPercent.toDouble())
                .put("min_prepared_track_count", limits.minPreparedTrackCount)
                .put("min_pose_candidate_coverage", limits.minPoseCandidateCoverage.toDouble())
        )
        .put(
            "frame_residuals",
            JSONArray().apply {
                result.frameResiduals.forEach {
                    put(
                        JSONObject()
                            .put("frame_id", it.frameId)
                            .put("constraint_count", it.constraintCount)
                            .put("residual_px_estimate", it.residualPxEstimate.toDouble())
                    )
                }
            }
        )
        .put(
            "track_residuals",
            JSONArray().apply {
                result.trackResiduals.forEach {
                    put(
                        JSONObject()
                            .put("track_id", it.trackId)
                            .put("observation_count", it.observationCount)
                            .put("residual_px_estimate", it.residualPxEstimate.toDouble())
                    )
                }
            }
        )

private fun scannerBundleAdd(a: List<Double>, b: List<Double>): List<Double> =
    List(3) { index -> (a.getOrNull(index) ?: 0.0) + (b.getOrNull(index) ?: 0.0) }

private fun scannerBundleNormalize(values: List<Double>): List<Double> {
    val length = sqrt((0 until 3).sumOf { index ->
        val value = values.getOrNull(index) ?: 0.0
        value * value
    })
    if (length <= 0.0) return listOf(0.0, 0.0, 0.0)
    return List(3) { index -> (values.getOrNull(index) ?: 0.0) / length }
}

private fun JSONArray?.scannerBundleDoubleList(): List<Double> =
    if (this == null) emptyList() else List(length()) { getDouble(it) }
