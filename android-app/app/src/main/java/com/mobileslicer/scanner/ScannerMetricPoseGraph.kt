package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_METRIC_POSE_GRAPH_PATH = "poses/metric_pose_graph.json"

internal data class ScannerMetricPoseGraphLimits(
    val minRelativePoseConstraints: Int = 2,
    val minRelativePoseInliers: Int = 16,
    val minRelativePoseInlierRatio: Double = 0.55,
    val minScaleConfidence: Float = 0.85f,
    val maxMarkerReprojectionErrorPx: Float = 3.0f,
    val maxPairConstraints: Int = 24,
    val requiredPasses: Set<ScannerCapturePass> = setOf(
        ScannerCapturePass.LowRing,
        ScannerCapturePass.MidRing,
        ScannerCapturePass.HighRing
    )
)

internal data class ScannerMetricPoseGraphNode(
    val frameId: String,
    val capturePass: ScannerCapturePass
)

internal data class ScannerMetricPoseGraphConstraint(
    val frameA: String,
    val frameB: String,
    val matchCount: Int,
    val status: String,
    val relativePoseAvailable: Boolean,
    val inlierCount: Int,
    val inlierRatio: Double,
    val rotationRowMajor: List<Double>,
    val translationUnit: List<Double>
)

internal data class ScannerMetricPoseGraphResult(
    val allowed: Boolean,
    val metric: Boolean,
    val bundleAdjustmentReady: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val nodes: List<ScannerMetricPoseGraphNode>,
    val constraints: List<ScannerMetricPoseGraphConstraint>,
    val connectedComponentCount: Int,
    val scaleConfidence: Float,
    val markerReprojectionErrorPx: Float?
)

internal fun buildScannerMetricPoseGraph(
    workspaceDir: File,
    limits: ScannerMetricPoseGraphLimits = ScannerMetricPoseGraphLimits(),
    openCvSolverStatus: ScannerOpenCvPoseSolverStatus = ScannerAndroidOpenCvPoseSolver.status(),
    relativePairSolver: (
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        intrinsicsA: CameraIntrinsicsData,
        intrinsicsB: CameraIntrinsicsData,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ) -> ScannerNativeRelativePairSolveResult = ScannerAndroidOpenCvPoseSolver::solveRelativePair
): ScannerMetricPoseGraphResult {
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val refinement = workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val matchGraph = workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (reconstructionJob == null || refinement == null || matchGraph == null) {
        val result = blockedScannerMetricPoseGraph(
            errors = buildList {
                if (reconstructionJob == null) add("reconstruction_job_missing")
                if (refinement == null) add("pose_refinement_missing")
                if (matchGraph == null) add("match_graph_missing")
            }
        )
        writeScannerMetricPoseGraph(workspaceDir, result, limits, openCvSolverStatus)
        return result
    }

    val framePasses = parseMetricPoseGraphFramePasses(reconstructionJob)
    val frameIntrinsics = parseMetricPoseGraphIntrinsics(reconstructionJob)
    val nodes = framePasses.map { (frameId, pass) ->
        ScannerMetricPoseGraphNode(frameId = frameId, capturePass = pass)
    }.sortedWith(compareBy<ScannerMetricPoseGraphNode> { it.capturePass.ordinal }.thenBy { it.frameId })
    val calibration = reconstructionJob.optJSONObject("calibration")
    val scaleSource = reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue)
    val scaleConfidence = calibration?.optDouble("scale_confidence", 0.0)?.toFloat() ?: 0f
    val markerReprojectionError = calibration
        ?.takeIf { !it.isNull("marker_reprojection_error_px") }
        ?.optDouble("marker_reprojection_error_px")
        ?.toFloat()
    val matchPairs = parseMetricPoseGraphPairs(matchGraph, limits.maxPairConstraints)
    val constraints = if (openCvSolverStatus.available) {
        matchPairs.map { pair ->
            pair.solveMetricPoseGraphConstraint(
                frameIntrinsics = frameIntrinsics,
                limits = limits,
                relativePairSolver = relativePairSolver
            )
        }
    } else {
        matchPairs.map {
            ScannerMetricPoseGraphConstraint(
                frameA = it.frameA,
                frameB = it.frameB,
                matchCount = it.matchCount,
                status = "opencv_relative_pose_solver_unavailable:${openCvSolverStatus.status}",
                relativePoseAvailable = false,
                inlierCount = 0,
                inlierRatio = 0.0,
                rotationRowMajor = emptyList(),
                translationUnit = emptyList()
            )
        }
    }
    val usableConstraints = constraints.filter {
        it.relativePoseAvailable &&
            it.inlierCount >= limits.minRelativePoseInliers &&
            it.inlierRatio >= limits.minRelativePoseInlierRatio
    }
    val componentCount = metricPoseGraphConnectedComponents(nodes.map { it.frameId }, usableConstraints)
    val refinementAllowed = refinement.optBoolean("allowed", false)
    val refinementErrors = refinement.optJSONArray("errors").metricPoseGraphStringList()
    val observedPasses = nodes.map { it.capturePass }.toSet()
    val errors = buildList {
        if (!refinementAllowed) add("pose_refinement_blocked")
        addAll(refinementErrors.map { "pose_refinement:$it" })
        if (!matchGraph.optBoolean("allowed", false)) add("match_graph_blocked")
        addAll(matchGraph.optJSONArray("errors").metricPoseGraphStringList().map { "match_graph:$it" })
        if (!openCvSolverStatus.available) add("android_opencv_relative_pose_solver_unavailable:${openCvSolverStatus.status}")
        if (nodes.size < 3) add("not_enough_pose_graph_nodes")
        limits.requiredPasses
            .filterNot { it in observedPasses }
            .forEach { add("pose_graph_pass_missing:${it.manifestValue}") }
        if (usableConstraints.size < limits.minRelativePoseConstraints) add("not_enough_relative_pose_constraints")
        if (nodes.isNotEmpty() && componentCount != 1) add("pose_graph_not_connected")
        if (constraints.any { !it.relativePoseAvailable }) add("relative_pose_constraint_failed")
        if (scaleSource != ScannerScaleSource.VerifiedMarkerMat.manifestValue) add("verified_marker_mat_required")
        if (calibration == null) add("calibration_missing")
        if (scaleConfidence < limits.minScaleConfidence) add("scale_confidence_low")
        if (markerReprojectionError == null) add("marker_reprojection_missing")
        if (markerReprojectionError != null && markerReprojectionError > limits.maxMarkerReprojectionErrorPx) {
            add("marker_reprojection_error_high")
        }
    }.distinct()
    val result = ScannerMetricPoseGraphResult(
        allowed = errors.isEmpty(),
        metric = false,
        bundleAdjustmentReady = errors.isEmpty(),
        errors = errors,
        warnings = listOf("pose_graph_constraints_only", "global_bundle_adjustment_required"),
        nodes = nodes,
        constraints = constraints,
        connectedComponentCount = componentCount,
        scaleConfidence = scaleConfidence,
        markerReprojectionErrorPx = markerReprojectionError
    )
    writeScannerMetricPoseGraph(workspaceDir, result, limits, openCvSolverStatus)
    return result
}

private data class MetricPoseGraphPair(
    val frameA: String,
    val frameB: String,
    val matchCount: Int,
    val matches: JSONArray
)

private fun blockedScannerMetricPoseGraph(errors: List<String>): ScannerMetricPoseGraphResult =
    ScannerMetricPoseGraphResult(
        allowed = false,
        metric = false,
        bundleAdjustmentReady = false,
        errors = errors.distinct(),
        warnings = emptyList(),
        nodes = emptyList(),
        constraints = emptyList(),
        connectedComponentCount = 0,
        scaleConfidence = 0f,
        markerReprojectionErrorPx = null
    )

private fun parseMetricPoseGraphFramePasses(reconstructionJob: JSONObject): Map<String, ScannerCapturePass> {
    val frames = reconstructionJob.optJSONArray("frames") ?: JSONArray()
    return List(frames.length()) { index -> frames.getJSONObject(index) }
        .associate { frame ->
            frame.getString("frame_id") to ScannerCapturePass.entries.single {
                it.manifestValue == frame.getString("capture_pass")
            }
        }
}

private fun parseMetricPoseGraphIntrinsics(reconstructionJob: JSONObject): Map<String, CameraIntrinsicsData> {
    val frames = reconstructionJob.optJSONArray("frames") ?: JSONArray()
    return List(frames.length()) { index -> frames.getJSONObject(index) }
        .mapNotNull { frame ->
            val intrinsics = frame.optJSONObject("intrinsics") ?: return@mapNotNull null
            frame.getString("frame_id") to CameraIntrinsicsData(
                fx = intrinsics.getDouble("fx").toFloat(),
                fy = intrinsics.getDouble("fy").toFloat(),
                cx = intrinsics.getDouble("cx").toFloat(),
                cy = intrinsics.getDouble("cy").toFloat(),
                imageWidth = intrinsics.getInt("width"),
                imageHeight = intrinsics.getInt("height")
            )
        }
        .toMap()
}

private fun parseMetricPoseGraphPairs(matchGraph: JSONObject, maxPairConstraints: Int): List<MetricPoseGraphPair> {
    val pairs = matchGraph.optJSONArray("pairs") ?: JSONArray()
    return List(pairs.length()) { index -> pairs.getJSONObject(index) }
        .filter { it.optBoolean("accepted", false) }
        .filter { (it.optJSONArray("matches")?.length() ?: 0) > 0 }
        .sortedWith(compareByDescending<JSONObject> { it.optInt("match_count", 0) }.thenBy { it.getString("frame_a") })
        .take(maxPairConstraints)
        .map {
            MetricPoseGraphPair(
                frameA = it.getString("frame_a"),
                frameB = it.getString("frame_b"),
                matchCount = it.optInt("match_count", it.optJSONArray("matches")?.length() ?: 0),
                matches = it.getJSONArray("matches")
            )
        }
}

private fun MetricPoseGraphPair.solveMetricPoseGraphConstraint(
    frameIntrinsics: Map<String, CameraIntrinsicsData>,
    limits: ScannerMetricPoseGraphLimits,
    relativePairSolver: (
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        intrinsicsA: CameraIntrinsicsData,
        intrinsicsB: CameraIntrinsicsData,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ) -> ScannerNativeRelativePairSolveResult
): ScannerMetricPoseGraphConstraint {
    val intrinsicsA = frameIntrinsics[frameA]
    val intrinsicsB = frameIntrinsics[frameB]
    if (intrinsicsA == null || intrinsicsB == null) {
        return ScannerMetricPoseGraphConstraint(
            frameA = frameA,
            frameB = frameB,
            matchCount = matchCount,
            status = "intrinsics_missing",
            relativePoseAvailable = false,
            inlierCount = 0,
            inlierRatio = 0.0,
            rotationRowMajor = emptyList(),
            translationUnit = emptyList()
        )
    }
    val pointsA = DoubleArray(matches.length() * 2)
    val pointsB = DoubleArray(matches.length() * 2)
    for (index in 0 until matches.length()) {
        val match = matches.getJSONObject(index)
        pointsA[index * 2] = match.getDouble("ax")
        pointsA[index * 2 + 1] = match.getDouble("ay")
        pointsB[index * 2] = match.getDouble("bx")
        pointsB[index * 2 + 1] = match.getDouble("by")
    }
    val solved = relativePairSolver(
        pointsA,
        pointsB,
        intrinsicsA,
        intrinsicsB,
        limits.minRelativePoseInliers,
        limits.maxMarkerReprojectionErrorPx.toDouble()
    )
    return ScannerMetricPoseGraphConstraint(
        frameA = frameA,
        frameB = frameB,
        matchCount = matchCount,
        status = solved.status,
        relativePoseAvailable = solved.success,
        inlierCount = solved.inlierCount,
        inlierRatio = solved.inlierRatio,
        rotationRowMajor = solved.rotationRowMajor,
        translationUnit = solved.translationUnit
    )
}

private fun metricPoseGraphConnectedComponents(
    frameIds: List<String>,
    constraints: List<ScannerMetricPoseGraphConstraint>
): Int {
    if (frameIds.isEmpty()) return 0
    val parent = frameIds.associateWith { it }.toMutableMap()
    fun find(frameId: String): String {
        val current = parent.getValue(frameId)
        if (current == frameId) return frameId
        val root = find(current)
        parent[frameId] = root
        return root
    }
    fun union(a: String, b: String) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) parent[rootB] = rootA
    }
    constraints.forEach { union(it.frameA, it.frameB) }
    return frameIds.map { find(it) }.toSet().size
}

private fun writeScannerMetricPoseGraph(
    workspaceDir: File,
    result: ScannerMetricPoseGraphResult,
    limits: ScannerMetricPoseGraphLimits,
    openCvSolverStatus: ScannerOpenCvPoseSolverStatus
) {
    workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMetricPoseGraphJson(result, limits, openCvSolverStatus).toString(2))
    }
}

internal fun scannerMetricPoseGraphJson(
    result: ScannerMetricPoseGraphResult,
    limits: ScannerMetricPoseGraphLimits,
    openCvSolverStatus: ScannerOpenCvPoseSolverStatus
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("bundle_adjustment_ready", result.bundleAdjustmentReady)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put(
            "opencv_solver",
            JSONObject()
                .put("available", openCvSolverStatus.available)
                .put("status", openCvSolverStatus.status)
                .put("detail", openCvSolverStatus.detail)
                .put("solver_name", openCvSolverStatus.solverName)
        )
        .put(
            "limits",
            JSONObject()
                .put("min_relative_pose_constraints", limits.minRelativePoseConstraints)
                .put("min_relative_pose_inliers", limits.minRelativePoseInliers)
                .put("min_relative_pose_inlier_ratio", limits.minRelativePoseInlierRatio)
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("max_marker_reprojection_error_px", limits.maxMarkerReprojectionErrorPx.toDouble())
                .put("max_pair_constraints", limits.maxPairConstraints)
                .put(
                    "required_passes",
                    JSONArray().apply {
                        limits.requiredPasses.sortedBy { it.ordinal }.forEach { put(it.manifestValue) }
                    }
                )
        )
        .put("connected_component_count", result.connectedComponentCount)
        .put("scale_confidence", result.scaleConfidence.toDouble())
        .put("marker_reprojection_error_px", result.markerReprojectionErrorPx ?: JSONObject.NULL)
        .put(
            "nodes",
            JSONArray().apply {
                result.nodes.forEach {
                    put(
                        JSONObject()
                            .put("frame_id", it.frameId)
                            .put("capture_pass", it.capturePass.manifestValue)
                    )
                }
            }
        )
        .put(
            "relative_pose_constraints",
            JSONArray().apply {
                result.constraints.forEach {
                    put(
                        JSONObject()
                            .put("frame_a", it.frameA)
                            .put("frame_b", it.frameB)
                            .put("match_count", it.matchCount)
                            .put("status", it.status)
                            .put("relative_pose_available", it.relativePoseAvailable)
                            .put("inlier_count", it.inlierCount)
                            .put("inlier_ratio", it.inlierRatio)
                            .put("rotation_row_major", JSONArray(it.rotationRowMajor))
                            .put("translation_unit", JSONArray(it.translationUnit))
                    )
                }
            }
        )

private fun JSONArray?.metricPoseGraphStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
