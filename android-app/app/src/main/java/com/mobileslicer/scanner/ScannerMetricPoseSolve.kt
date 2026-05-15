package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_METRIC_POSE_SOLVE_PATH = "poses/metric_pose_solve.json"

internal enum class ScannerMetricPoseSolverStatus(val manifestValue: String) {
    Accepted("accepted"),
    NotImplemented("not_implemented"),
    Blocked("blocked")
}

internal data class ScannerMetricPoseSolveLimits(
    val minRefinementConfidence: Float = 0.70f,
    val minScaleConfidence: Float = 0.85f,
    val maxMarkerReprojectionErrorPx: Float = 3.0f,
    val minFeatureTrackCount: Int = 40,
    val minLongFeatureTrackCount: Int = 12,
    val minNativePairInliers: Int = 16,
    val maxNativePairReprojectionErrorPx: Double = 2.5,
    val requireVerifiedMarkerMat: Boolean = true,
    val maxBundleAdjustmentFrameResidualPx: Float = 2.0f,
    val maxBundleAdjustmentTrackResidualPx: Float = 2.5f,
    val maxBundleAdjustmentMarkerResidualPx: Float = 3.0f,
    val maxBundleAdjustmentScaleResidualPercent: Float = 1.5f
)

internal data class ScannerMetricCameraPoseCandidate(
    val frameId: String,
    val sourceFrameId: String?,
    val rotationRowMajor: List<Double>,
    val translationGraphUnits: List<Double>,
    val sourceConstraintStatus: String
)

internal data class ScannerMetricBundleAdjustmentDiagnostics(
    val status: String,
    val ready: Boolean,
    val frameResidualMaxPx: Float?,
    val trackResidualMaxPx: Float?,
    val markerResidualMaxPx: Float?,
    val scaleResidualPercent: Float?,
    val rejectedConstraintCount: Int,
    val errors: List<String>
)

internal data class ScannerMetricPoseSolveResult(
    val allowed: Boolean,
    val denseReconstructionAllowed: Boolean,
    val metric: Boolean,
    val solverStatus: ScannerMetricPoseSolverStatus,
    val openCvSolverStatus: ScannerOpenCvPoseSolverStatus,
    val nativeSolverStatus: ScannerNativePoseSolverStatus,
    val nativePairSolveResult: ScannerNativeRelativePairSolveResult?,
    val errors: List<String>,
    val warnings: List<String>,
    val refinementConfidence: Float,
    val scaleConfidence: Float,
    val markerReprojectionErrorPx: Float?,
    val poseCandidates: List<ScannerMetricCameraPoseCandidate>,
    val bundleAdjustment: ScannerMetricBundleAdjustmentDiagnostics
)

internal fun solveScannerMetricPoses(
    workspaceDir: File,
    limits: ScannerMetricPoseSolveLimits = ScannerMetricPoseSolveLimits()
): ScannerMetricPoseSolveResult {
    val openCvSolverStatus = ScannerAndroidOpenCvPoseSolver.status()
    val nativeSolverStatus = ScannerNativePoseSolverBridge.status()
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (reconstructionJob == null) {
        val result = blockedScannerMetricPoseSolve(
            errors = listOf("reconstruction_job_missing"),
            openCvSolverStatus = openCvSolverStatus,
            nativeSolverStatus = nativeSolverStatus
        )
        writeScannerMetricPoseSolve(workspaceDir, result, limits)
        return result
    }

    val refinementFile = workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH)
    if (!refinementFile.isFile) {
        val result = blockedScannerMetricPoseSolve(
            errors = listOf("pose_refinement_missing"),
            openCvSolverStatus = openCvSolverStatus,
            nativeSolverStatus = nativeSolverStatus
        )
        writeScannerMetricPoseSolve(workspaceDir, result, limits)
        return result
    }

    val refinement = JSONObject(refinementFile.readText())
    val refinementAllowed = refinement.optBoolean("allowed", false)
    val refinementErrors = refinement.optJSONArray("errors").scannerMetricPoseStringList()
    val refinementConfidence = refinement.optDouble("refinement_confidence", 0.0).toFloat()
    val calibration = reconstructionJob.optJSONObject("calibration")
    val scaleSource = reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue)
    val scaleConfidence = calibration?.optDouble("scale_confidence", 0.0)?.toFloat() ?: 0f
    val markerReprojectionError = calibration
        ?.takeIf { !it.isNull("marker_reprojection_error_px") }
        ?.optDouble("marker_reprojection_error_px")
        ?.toFloat()
    val relativePairSolveResult = if (openCvSolverStatus.available) {
        solveBestOpenCvRelativePair(workspaceDir, reconstructionJob, limits)
    } else if (nativeSolverStatus.available && nativeSolverStatus.opencvLinked) {
        solveBestNativeRelativePair(workspaceDir, reconstructionJob, limits)
    } else {
        null
    }
    val trackDiagnostics = workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val tracksAllowed = trackDiagnostics?.optBoolean("allowed", false) ?: false
    val trackCount = trackDiagnostics?.optInt("track_count", 0) ?: 0
    val longTrackCount = trackDiagnostics?.optInt("long_track_count", 0) ?: 0
    val trackErrors = trackDiagnostics?.optJSONArray("errors").scannerMetricPoseStringList()
    val sparseDiagnostics = workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val sparseAllowed = sparseDiagnostics?.optBoolean("allowed", false) ?: false
    val sparsePreparedTrackCount = sparseDiagnostics?.optInt("prepared_track_count", 0) ?: 0
    val sparseErrors = sparseDiagnostics?.optJSONArray("errors").scannerMetricPoseStringList()
    val metricPoseGraph = workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val metricPoseGraphAllowed = metricPoseGraph?.optBoolean("allowed", false) ?: false
    val metricPoseGraphErrors = metricPoseGraph?.optJSONArray("errors").scannerMetricPoseStringList()
    val relativePoseConstraintCount = metricPoseGraph
        ?.optJSONArray("relative_pose_constraints")
        ?.let { constraints ->
            List(constraints.length()) { index -> constraints.getJSONObject(index) }
                .count { it.optBoolean("relative_pose_available", false) }
        }
        ?: 0
    val poseCandidates = buildMetricPoseCandidates(metricPoseGraph)
    val bundleAdjustmentJson = workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val bundleAdjustment = bundleAdjustmentJson?.toMetricBundleAdjustmentDiagnostics()
        ?: evaluateMetricBundleAdjustmentGate(metricPoseGraph, poseCandidates, limits)

    val errors = buildList {
        if (!refinementAllowed) add("pose_refinement_blocked")
        addAll(refinementErrors.map { "pose_refinement:$it" })
        if (refinementConfidence < limits.minRefinementConfidence) add("pose_refinement_confidence_low")
        if (limits.requireVerifiedMarkerMat && scaleSource != ScannerScaleSource.VerifiedMarkerMat.manifestValue) {
            add("verified_marker_mat_required")
        }
        if (calibration == null) add("calibration_missing")
        if (scaleConfidence < limits.minScaleConfidence) add("scale_confidence_low")
        if (markerReprojectionError == null) add("marker_reprojection_missing")
        if (markerReprojectionError != null && markerReprojectionError > limits.maxMarkerReprojectionErrorPx) {
            add("marker_reprojection_error_high")
        }
        if (trackDiagnostics == null) add("feature_tracks_missing")
        if (!tracksAllowed) add("feature_tracks_blocked")
        addAll(trackErrors.orEmpty().map { "feature_tracks:$it" })
        if (trackCount < limits.minFeatureTrackCount) add("feature_track_count_low")
        if (longTrackCount < limits.minLongFeatureTrackCount) add("long_feature_track_count_low")
        if (sparseDiagnostics == null) add("sparse_reconstruction_missing")
        if (!sparseAllowed) add("sparse_reconstruction_blocked")
        addAll(sparseErrors.orEmpty().map { "sparse_reconstruction:$it" })
        if (sparsePreparedTrackCount < limits.minFeatureTrackCount) add("sparse_prepared_track_count_low")
        if (metricPoseGraph == null) add("metric_pose_graph_missing")
        if (!metricPoseGraphAllowed) add("metric_pose_graph_blocked")
        addAll(metricPoseGraphErrors.orEmpty().map { "metric_pose_graph:$it" })
        if (relativePoseConstraintCount < 2) add("relative_pose_constraint_count_low")
        if (metricPoseGraphAllowed && poseCandidates.isEmpty()) add("metric_pose_candidates_missing")
        if (bundleAdjustmentJson == null) add("bundle_adjustment_missing")
        if (bundleAdjustmentJson != null && !bundleAdjustmentJson.optBoolean("allowed", false)) {
            add("bundle_adjustment_blocked")
        }
        addAll(bundleAdjustment.errors)
        if (relativePairSolveResult != null && !relativePairSolveResult.success) {
            add("relative_pair_solve_failed:${relativePairSolveResult.status}")
        }
    }.distinct()
    val accepted = errors.isEmpty()

    val result = ScannerMetricPoseSolveResult(
        allowed = accepted,
        denseReconstructionAllowed = accepted,
        metric = accepted,
        solverStatus = if (accepted) ScannerMetricPoseSolverStatus.Accepted else ScannerMetricPoseSolverStatus.Blocked,
        openCvSolverStatus = openCvSolverStatus,
        nativeSolverStatus = nativeSolverStatus,
        nativePairSolveResult = relativePairSolveResult,
        errors = errors,
        warnings = buildList {
            if (accepted) {
                add("metric_pose_candidates_accepted_for_native_optimizer")
                add("native_optimizer_required_before_sparse_triangulation")
            } else {
                add("metric_pose_solver_blocked_by_input_gates")
            }
            if (!openCvSolverStatus.available) {
                add("android_opencv_relative_pair_probe_unavailable:${openCvSolverStatus.status}")
            }
            if (!nativeSolverStatus.available || !nativeSolverStatus.opencvLinked) {
                add("native_relative_pair_solver_unavailable:${nativeSolverStatus.status}")
            }
        },
        refinementConfidence = refinementConfidence,
        scaleConfidence = scaleConfidence,
        markerReprojectionErrorPx = markerReprojectionError,
        poseCandidates = poseCandidates,
        bundleAdjustment = bundleAdjustment
    )
    writeScannerMetricPoseSolve(workspaceDir, result, limits)
    return result
}

private fun blockedScannerMetricPoseSolve(
    errors: List<String>,
    openCvSolverStatus: ScannerOpenCvPoseSolverStatus,
    nativeSolverStatus: ScannerNativePoseSolverStatus
): ScannerMetricPoseSolveResult =
    ScannerMetricPoseSolveResult(
        allowed = false,
        denseReconstructionAllowed = false,
        metric = false,
        solverStatus = ScannerMetricPoseSolverStatus.Blocked,
        openCvSolverStatus = openCvSolverStatus,
        nativeSolverStatus = nativeSolverStatus,
        nativePairSolveResult = null,
        errors = errors.distinct(),
        warnings = emptyList(),
        refinementConfidence = 0f,
        scaleConfidence = 0f,
        markerReprojectionErrorPx = null,
        poseCandidates = emptyList(),
        bundleAdjustment = ScannerMetricBundleAdjustmentDiagnostics(
            status = "blocked",
            ready = false,
            frameResidualMaxPx = null,
            trackResidualMaxPx = null,
            markerResidualMaxPx = null,
            scaleResidualPercent = null,
            rejectedConstraintCount = 0,
            errors = listOf("bundle_adjustment_blocked")
        )
    )

private fun writeScannerMetricPoseSolve(
    workspaceDir: File,
    result: ScannerMetricPoseSolveResult,
    limits: ScannerMetricPoseSolveLimits
) {
    workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMetricPoseSolveJson(result, limits).toString(2))
    }
}

internal fun scannerMetricPoseSolveJson(
    result: ScannerMetricPoseSolveResult,
    limits: ScannerMetricPoseSolveLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("dense_reconstruction_allowed", result.denseReconstructionAllowed)
        .put("metric", result.metric)
        .put("solver_status", result.solverStatus.manifestValue)
        .put(
            "opencv_solver",
            JSONObject()
                .put("available", result.openCvSolverStatus.available)
                .put("status", result.openCvSolverStatus.status)
                .put("detail", result.openCvSolverStatus.detail)
                .put("solver_name", result.openCvSolverStatus.solverName)
        )
        .put(
            "native_solver",
            JSONObject()
                .put("available", result.nativeSolverStatus.available)
                .put("status", result.nativeSolverStatus.status)
                .put("detail", result.nativeSolverStatus.detail)
                .put("solver_name", result.nativeSolverStatus.solverName)
                .put("opencv_linked", result.nativeSolverStatus.opencvLinked)
        )
        .put("relative_pair_solve", result.nativePairSolveResult?.toJson() ?: JSONObject.NULL)
        .put("camera_pose_candidates", cameraPoseCandidatesJson(result.poseCandidates, result.allowed && result.metric))
        .put("bundle_adjustment", bundleAdjustmentJson(result.bundleAdjustment, limits))
        .put("feature_tracks", featureTracksMetricGateJson(result, limits))
        .put("sparse_reconstruction", sparseReconstructionMetricGateJson(result, limits))
        .put("metric_pose_graph", metricPoseGraphMetricGateJson(result))
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("refinement_confidence", result.refinementConfidence.toDouble())
        .put("scale_confidence", result.scaleConfidence.toDouble())
        .put("marker_reprojection_error_px", result.markerReprojectionErrorPx ?: JSONObject.NULL)
        .put(
            "limits",
            JSONObject()
                .put("min_refinement_confidence", limits.minRefinementConfidence.toDouble())
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("max_marker_reprojection_error_px", limits.maxMarkerReprojectionErrorPx.toDouble())
                .put("min_feature_track_count", limits.minFeatureTrackCount)
                .put("min_long_feature_track_count", limits.minLongFeatureTrackCount)
                .put("min_native_pair_inliers", limits.minNativePairInliers)
                .put("max_native_pair_reprojection_error_px", limits.maxNativePairReprojectionErrorPx)
                .put("require_verified_marker_mat", limits.requireVerifiedMarkerMat)
                .put("max_bundle_adjustment_frame_residual_px", limits.maxBundleAdjustmentFrameResidualPx.toDouble())
                .put("max_bundle_adjustment_track_residual_px", limits.maxBundleAdjustmentTrackResidualPx.toDouble())
                .put("max_bundle_adjustment_marker_residual_px", limits.maxBundleAdjustmentMarkerResidualPx.toDouble())
                .put("max_bundle_adjustment_scale_residual_percent", limits.maxBundleAdjustmentScaleResidualPercent.toDouble())
        )

private fun JSONArray?.scannerMetricPoseStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }

private fun solveBestOpenCvRelativePair(
    workspaceDir: File,
    reconstructionJob: JSONObject,
    limits: ScannerMetricPoseSolveLimits
): ScannerNativeRelativePairSolveResult? =
    solveBestRelativePair(
        workspaceDir = workspaceDir,
        reconstructionJob = reconstructionJob,
        limits = limits,
        solver = ScannerAndroidOpenCvPoseSolver::solveRelativePair
    )

private fun solveBestNativeRelativePair(
    workspaceDir: File,
    reconstructionJob: JSONObject,
    limits: ScannerMetricPoseSolveLimits
): ScannerNativeRelativePairSolveResult? =
    solveBestRelativePair(
        workspaceDir = workspaceDir,
        reconstructionJob = reconstructionJob,
        limits = limits,
        solver = ScannerNativePoseSolverBridge::solveRelativePair
    )

private fun solveBestRelativePair(
    workspaceDir: File,
    reconstructionJob: JSONObject,
    limits: ScannerMetricPoseSolveLimits,
    solver: (
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        intrinsicsA: CameraIntrinsicsData,
        intrinsicsB: CameraIntrinsicsData,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ) -> ScannerNativeRelativePairSolveResult
): ScannerNativeRelativePairSolveResult? {
    val featuresFile = workspaceDir.resolve(SCANNER_FEATURES_PATH)
    val matchGraphFile = workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH)
    if (!featuresFile.isFile || !matchGraphFile.isFile) return null

    val frameIntrinsics = parseMetricSolveFrameIntrinsics(reconstructionJob)
    val pair = JSONObject(matchGraphFile.readText())
        .optJSONArray("pairs")
        .bestMetricSolvePair()
        ?: return null
    val matches = pair.optJSONArray("matches") ?: return null
    val frameA = pair.getString("frame_a")
    val frameB = pair.getString("frame_b")
    val intrinsicsA = frameIntrinsics[frameA] ?: return null
    val intrinsicsB = frameIntrinsics[frameB] ?: return null
    val pointsA = DoubleArray(matches.length() * 2)
    val pointsB = DoubleArray(matches.length() * 2)
    for (index in 0 until matches.length()) {
        val match = matches.getJSONObject(index)
        pointsA[index * 2] = match.getDouble("ax")
        pointsA[index * 2 + 1] = match.getDouble("ay")
        pointsB[index * 2] = match.getDouble("bx")
        pointsB[index * 2 + 1] = match.getDouble("by")
    }
    return solver(
        pointsA,
        pointsB,
        intrinsicsA,
        intrinsicsB,
        limits.minNativePairInliers,
        limits.maxNativePairReprojectionErrorPx
    )
}

private fun parseMetricSolveFrameIntrinsics(reconstructionJob: JSONObject): Map<String, CameraIntrinsicsData> {
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

private fun JSONArray?.bestMetricSolvePair(): JSONObject? {
    if (this == null) return null
    return List(length()) { getJSONObject(it) }
        .filter { it.optBoolean("accepted", false) }
        .filter { (it.optJSONArray("matches")?.length() ?: 0) > 0 }
        .maxWithOrNull(
            compareBy<JSONObject> { it.optJSONArray("matches")?.length() ?: 0 }
                .thenBy { it.optInt("match_count", 0) }
        )
}

private fun ScannerNativeRelativePairSolveResult.toJson(): JSONObject =
    JSONObject()
        .put("success", success)
        .put("status", status)
        .put("detail", detail)
        .put("inlier_count", inlierCount)
        .put("inlier_ratio", inlierRatio)
        .put("rotation_row_major", JSONArray(rotationRowMajor))
        .put("translation_unit", JSONArray(translationUnit))

private fun buildMetricPoseCandidates(metricPoseGraph: JSONObject?): List<ScannerMetricCameraPoseCandidate> {
    if (metricPoseGraph == null) return emptyList()
    if (!metricPoseGraph.optBoolean("allowed", false)) return emptyList()
    if (!metricPoseGraph.optBoolean("bundle_adjustment_ready", false)) return emptyList()

    val nodes = metricPoseGraph.optJSONArray("nodes") ?: JSONArray()
    val frameIds = List(nodes.length()) { index -> nodes.getJSONObject(index).getString("frame_id") }
    if (frameIds.isEmpty()) return emptyList()
    val constraints = metricPoseGraph.optJSONArray("relative_pose_constraints") ?: JSONArray()
    val usableConstraints = List(constraints.length()) { index -> constraints.getJSONObject(index) }
        .filter { it.optBoolean("relative_pose_available", false) }
    if (usableConstraints.isEmpty()) return emptyList()

    val adjacency = mutableMapOf<String, MutableList<MetricPoseCandidateEdge>>()
    usableConstraints.forEach { constraint ->
        val frameA = constraint.getString("frame_a")
        val frameB = constraint.getString("frame_b")
        val translation = constraint.optJSONArray("translation_unit").scannerMetricPoseDoubleList()
        val rotation = constraint.optJSONArray("rotation_row_major").scannerMetricPoseDoubleList()
        val status = constraint.optString("status", "relative_pose_available")
        adjacency.getOrPut(frameA) { mutableListOf() } += MetricPoseCandidateEdge(
            from = frameA,
            to = frameB,
            translation = translation,
            rotation = rotation,
            status = status
        )
        adjacency.getOrPut(frameB) { mutableListOf() } += MetricPoseCandidateEdge(
            from = frameB,
            to = frameA,
            translation = translation.map { -it },
            rotation = rotation,
            status = status
        )
    }

    val root = frameIds.first()
    val candidates = linkedMapOf(
        root to ScannerMetricCameraPoseCandidate(
            frameId = root,
            sourceFrameId = null,
            rotationRowMajor = identityRotationRowMajor(),
            translationGraphUnits = listOf(0.0, 0.0, 0.0),
            sourceConstraintStatus = "anchor"
        )
    )
    val queue = ArrayDeque<String>()
    queue += root
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val currentPose = candidates.getValue(current)
        adjacency[current].orEmpty()
            .sortedBy { it.to }
            .forEach { edge ->
                if (edge.to !in candidates) {
                    candidates[edge.to] = ScannerMetricCameraPoseCandidate(
                        frameId = edge.to,
                        sourceFrameId = current,
                        rotationRowMajor = edge.rotation.takeIf { it.size == 9 } ?: identityRotationRowMajor(),
                        translationGraphUnits = addMetricPoseVectors(currentPose.translationGraphUnits, edge.translation),
                        sourceConstraintStatus = edge.status
                    )
                    queue += edge.to
                }
            }
    }
    return frameIds.mapNotNull { candidates[it] }
}

private data class MetricPoseCandidateEdge(
    val from: String,
    val to: String,
    val translation: List<Double>,
    val rotation: List<Double>,
    val status: String
)

private fun evaluateMetricBundleAdjustmentGate(
    metricPoseGraph: JSONObject?,
    poseCandidates: List<ScannerMetricCameraPoseCandidate>,
    limits: ScannerMetricPoseSolveLimits
): ScannerMetricBundleAdjustmentDiagnostics {
    val rejectedConstraintCount = metricPoseGraph
        ?.optJSONArray("relative_pose_constraints")
        ?.let { constraints ->
            List(constraints.length()) { index -> constraints.getJSONObject(index) }
                .count { !it.optBoolean("relative_pose_available", false) }
        }
        ?: 0
    val errors = buildList {
        if (metricPoseGraph == null) add("bundle_adjustment_input_missing")
        if (metricPoseGraph != null && !metricPoseGraph.optBoolean("allowed", false)) {
            add("bundle_adjustment_pose_graph_blocked")
        }
        if (poseCandidates.isEmpty()) add("bundle_adjustment_pose_candidates_missing")
        add("bundle_adjustment_not_implemented")
        add("bundle_adjustment_frame_residuals_missing")
        add("bundle_adjustment_track_residuals_missing")
        add("bundle_adjustment_marker_residual_missing")
        add("bundle_adjustment_scale_residual_missing")
    }.distinct()
    return ScannerMetricBundleAdjustmentDiagnostics(
        status = if (errors.contains("bundle_adjustment_not_implemented")) "not_implemented" else "blocked",
        ready = false,
        frameResidualMaxPx = null,
        trackResidualMaxPx = null,
        markerResidualMaxPx = null,
        scaleResidualPercent = null,
        rejectedConstraintCount = rejectedConstraintCount,
        errors = errors
    )
}

private fun JSONObject.toMetricBundleAdjustmentDiagnostics(): ScannerMetricBundleAdjustmentDiagnostics =
    ScannerMetricBundleAdjustmentDiagnostics(
        status = optString("status", "blocked"),
        ready = optBoolean("allowed", false) && optBoolean("metric", false),
        frameResidualMaxPx = nullableMetricFloat("frame_residual_max_px"),
        trackResidualMaxPx = nullableMetricFloat("track_residual_max_px"),
        markerResidualMaxPx = nullableMetricFloat("marker_residual_px"),
        scaleResidualPercent = nullableMetricFloat("scale_residual_percent"),
        rejectedConstraintCount = optInt("rejected_constraint_count", 0),
        errors = optJSONArray("errors").scannerMetricPoseStringList()
    )

private fun JSONObject.nullableMetricFloat(name: String): Float? =
    if (isNull(name)) null else optDouble(name).toFloat()

private fun cameraPoseCandidatesJson(
    candidates: List<ScannerMetricCameraPoseCandidate>,
    metricValidated: Boolean
): JSONArray =
    JSONArray().apply {
        candidates.forEach {
            put(
                JSONObject()
                    .put("frame_id", it.frameId)
                    .put("source_frame_id", it.sourceFrameId ?: JSONObject.NULL)
                    .put("rotation_row_major", JSONArray(it.rotationRowMajor))
                    .put("translation_graph_units", JSONArray(it.translationGraphUnits))
                    .put("source_constraint_status", it.sourceConstraintStatus)
                    .put("metric_validated", metricValidated)
            )
        }
    }

private fun bundleAdjustmentJson(
    diagnostics: ScannerMetricBundleAdjustmentDiagnostics,
    limits: ScannerMetricPoseSolveLimits
): JSONObject =
    JSONObject()
        .put("status", diagnostics.status)
        .put("ready", diagnostics.ready)
        .put("frame_residual_max_px", diagnostics.frameResidualMaxPx ?: JSONObject.NULL)
        .put("track_residual_max_px", diagnostics.trackResidualMaxPx ?: JSONObject.NULL)
        .put("marker_residual_max_px", diagnostics.markerResidualMaxPx ?: JSONObject.NULL)
        .put("scale_residual_percent", diagnostics.scaleResidualPercent ?: JSONObject.NULL)
        .put("rejected_constraint_count", diagnostics.rejectedConstraintCount)
        .put("errors", JSONArray(diagnostics.errors))
        .put(
            "limits",
            JSONObject()
                .put("max_frame_residual_px", limits.maxBundleAdjustmentFrameResidualPx.toDouble())
                .put("max_track_residual_px", limits.maxBundleAdjustmentTrackResidualPx.toDouble())
                .put("max_marker_residual_px", limits.maxBundleAdjustmentMarkerResidualPx.toDouble())
                .put("max_scale_residual_percent", limits.maxBundleAdjustmentScaleResidualPercent.toDouble())
        )

private fun addMetricPoseVectors(a: List<Double>, b: List<Double>): List<Double> =
    List(3) { index -> (a.getOrNull(index) ?: 0.0) + (b.getOrNull(index) ?: 0.0) }

private fun identityRotationRowMajor(): List<Double> =
    listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

private fun JSONArray?.scannerMetricPoseDoubleList(): List<Double> =
    if (this == null) emptyList() else List(length()) { getDouble(it) }

private fun featureTracksMetricGateJson(
    result: ScannerMetricPoseSolveResult,
    limits: ScannerMetricPoseSolveLimits
): JSONObject =
    JSONObject()
        .put("required", true)
        .put("min_track_count", limits.minFeatureTrackCount)
        .put("min_long_track_count", limits.minLongFeatureTrackCount)
        .put(
            "blocking_errors",
            JSONArray(
                result.errors.filter {
                    it == "feature_tracks_missing" ||
                        it == "feature_tracks_blocked" ||
                        it == "feature_track_count_low" ||
                        it == "long_feature_track_count_low" ||
                        it.startsWith("feature_tracks:")
                }
            )
        )

private fun sparseReconstructionMetricGateJson(
    result: ScannerMetricPoseSolveResult,
    limits: ScannerMetricPoseSolveLimits
): JSONObject =
    JSONObject()
        .put("required", true)
        .put("min_prepared_tracks", limits.minFeatureTrackCount)
        .put(
            "blocking_errors",
            JSONArray(
                result.errors.filter {
                    it == "sparse_reconstruction_missing" ||
                        it == "sparse_reconstruction_blocked" ||
                        it == "sparse_prepared_track_count_low" ||
                        it.startsWith("sparse_reconstruction:")
                }
            )
        )

private fun metricPoseGraphMetricGateJson(result: ScannerMetricPoseSolveResult): JSONObject =
    JSONObject()
        .put("required", true)
        .put(
            "blocking_errors",
            JSONArray(
                result.errors.filter {
                    it == "metric_pose_graph_missing" ||
                        it == "metric_pose_graph_blocked" ||
                        it == "relative_pose_constraint_count_low" ||
                        it.startsWith("metric_pose_graph:")
                }
            )
        )
