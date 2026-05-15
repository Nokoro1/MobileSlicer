package com.mobileslicer.scanner

import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_SYNTHETIC_METRIC_FIXTURE_SUMMARY_PATH = "synthetic_metric_pose_fixture_summary.json"

internal data class ScannerSyntheticMetricPoseFixtureConfig(
    val frameIds: List<String> = listOf("low", "mid", "high"),
    val trackCount: Int = 48,
    val markerCornerObservationCount: Int = 16
)

internal data class ScannerSyntheticMetricPoseFixtureResult(
    val workspaceDir: File,
    val optimizer: ScannerPoseOptimizerResult,
    val sparseTriangulation: ScannerSparseTriangulationResult,
    val denseReconstruction: ScannerDenseReconstructionResult,
    val densePointCloud: ScannerDensePointCloudResult
)

internal fun runScannerSyntheticMetricPoseFixture(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig = ScannerSyntheticMetricPoseFixtureConfig()
): ScannerSyntheticMetricPoseFixtureResult {
    if (workspaceDir.exists()) workspaceDir.deleteRecursively()
    workspaceDir.mkdirs()
    writeSyntheticMetricPoseReconstructionJob(workspaceDir, config)
    writeSyntheticMetricPoseSolve(workspaceDir, config)
    writeSyntheticFeatureTracks(workspaceDir, config)
    writeSyntheticMetricMatchGraph(workspaceDir, config)
    writeSyntheticSparseReconstruction(workspaceDir, config)
    writeSyntheticBundleAdjustment(workspaceDir)
    val optimizer = optimizeScannerMetricPoses(
        workspaceDir = workspaceDir,
        limits = ScannerPoseOptimizerLimits(
            minPreparedTrackCount = config.trackCount,
            minOptimizedSparsePointCount = config.trackCount,
            minAcceptedMatchSpatialCells = 4
        ),
        nativeOptimizerStatusProvider = { syntheticMetricPoseOptimizerStatus() },
        nativeOptimizer = { syntheticMetricPoseOptimizerResult(config) }
    )
    val sparse = prepareScannerSparseTriangulation(
        workspaceDir = workspaceDir,
        limits = ScannerSparseTriangulationLimits(
            minPreparedTracks = config.trackCount,
            minMeasuredSparsePoints = config.trackCount
        )
    )
    val dense = admitScannerDenseReconstruction(
        workspaceDir = workspaceDir,
        limits = ScannerDenseReconstructionLimits(
            minMetricSparsePoints = config.trackCount,
            minObjectExtentMm = 5f
        )
    )
    val cloud = buildScannerDensePointCloud(
        workspaceDir = workspaceDir,
        limits = ScannerDensePointCloudLimits(
            minPointCountForSurface = config.trackCount,
            minObjectExtentMm = 5f
        )
    )
    val surface = reconstructScannerSurface(
        workspaceDir = workspaceDir,
        limits = ScannerSurfaceReconstructionLimits(
            minSurfacePointCount = config.trackCount,
            maxMeanPointSpacingMm = 10f,
            minSurfaceCoverageRatio = 0.30f
        )
    )
    workspaceDir.resolve(SCANNER_SYNTHETIC_METRIC_FIXTURE_SUMMARY_PATH).writeText(
        scannerSyntheticMetricPoseFixtureSummaryJson(
            workspaceDir = workspaceDir,
            optimizer = optimizer,
            sparse = sparse,
            dense = dense,
            cloud = cloud,
            surface = surface
        ).toString(2)
    )
    return ScannerSyntheticMetricPoseFixtureResult(
        workspaceDir = workspaceDir,
        optimizer = optimizer,
        sparseTriangulation = sparse,
        denseReconstruction = dense,
        densePointCloud = cloud
    )
}

internal fun scannerSyntheticMetricPoseFixtureSummaryJson(
    workspaceDir: File,
    optimizer: ScannerPoseOptimizerResult,
    sparse: ScannerSparseTriangulationResult,
    dense: ScannerDenseReconstructionResult,
    cloud: ScannerDensePointCloudResult,
    surface: ScannerSurfaceReconstructionResult? = null
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("created_at", Instant.now().toString())
        .put("workspace_dir", workspaceDir.absolutePath)
        .put("optimizer_allowed", optimizer.allowed)
        .put("sparse_metric", sparse.metric)
        .put("sparse_measured_point_count", sparse.measuredPointCount)
        .put("dense_admitted", dense.denseReconstructionAdmitted)
        .put("debug_point_cloud_ready", cloud.densePointCloudReady)
        .put("surface_ready", surface?.surfaceReconstructionReady ?: false)
        .put("surface_candidate", workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).absolutePath)
        .put("debug_point_cloud_ply", workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).absolutePath)
        .put("printable", false)
        .put("slicer_handoff_allowed", false)

private fun writeSyntheticMetricPoseReconstructionJob(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig
) {
    workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("scale_source", ScannerScaleSource.VerifiedMarkerMat.manifestValue)
            .put(
                "calibration",
                JSONObject()
                    .put("scale_confidence", 0.92)
                    .put("marker_reprojection_error_px", 1.0)
                    .put("scale_correction", 1.0)
            )
            .put(
                "frames",
                JSONArray().apply {
                    config.frameIds.forEach { put(syntheticMetricFrame(it)) }
                }
            )
            .put("marker_corner_observations", syntheticMetricMarkerCorners(config))
            .toString(2)
    )
}

private fun writeSyntheticMetricPoseSolve(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig
) {
    workspaceDir.resolve("poses").mkdirs()
    workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("allowed", true)
            .put("metric", true)
            .put(
                "camera_pose_candidates",
                JSONArray().apply {
                    config.frameIds.forEachIndexed { index, frameId ->
                        put(
                            JSONObject()
                                .put("frame_id", frameId)
                                .put("rotation_row_major", JSONArray(identityRotation()))
                                .put("translation_graph_units", JSONArray(listOf(index.toDouble(), 0.0, 0.0)))
                                .put("metric_validated", true)
                        )
                    }
                }
            )
            .toString(2)
    )
}

private fun writeSyntheticFeatureTracks(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig
) {
    workspaceDir.resolve("features").mkdirs()
    workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("allowed", true)
            .put("errors", JSONArray())
            .put("track_count", config.trackCount)
            .put("long_track_count", config.trackCount)
            .put("spatial_cell_count", 12)
            .put(
                "tracks",
                JSONArray().apply {
                    repeat(config.trackCount) { trackIndex ->
                        put(
                            JSONObject()
                                .put("track_id", "track_${trackIndex.toString().padStart(3, '0')}")
                                .put(
                                    "observations",
                                    JSONArray().apply {
                                        config.frameIds.forEachIndexed { frameIndex, frameId ->
                                            put(
                                                JSONObject()
                                                    .put("frame_id", frameId)
                                                    .put("x", 180 + (trackIndex % 12) * 18 + frameIndex)
                                                    .put("y", 140 + (trackIndex / 12) * 22)
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            )
            .toString(2)
    )
}

private fun writeSyntheticMetricMatchGraph(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig
) {
    workspaceDir.resolve("matches").mkdirs()
    workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("allowed", true)
            .put("errors", JSONArray())
            .put(
                "pairs",
                JSONArray().apply {
                    config.frameIds.zipWithNext().forEach { (a, b) ->
                        put(
                            JSONObject()
                                .put("frame_a", a)
                                .put("frame_b", b)
                                .put("match_count", 32)
                                .put("accepted", true)
                                .put("average_descriptor_distance", 2.0)
                                .put("spatial_cell_count", 6)
                                .put("average_mask_support", 230.0)
                        )
                    }
                }
            )
            .toString(2)
    )
}

private fun writeSyntheticSparseReconstruction(
    workspaceDir: File,
    config: ScannerSyntheticMetricPoseFixtureConfig
) {
    workspaceDir.resolve("sparse").mkdirs()
    workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("allowed", true)
            .put("metric", false)
            .put("prepared_track_count", config.trackCount)
            .put("errors", JSONArray())
            .put(
                "tracks",
                JSONArray().apply {
                    repeat(config.trackCount) { trackIndex ->
                        put(
                            JSONObject()
                                .put("track_id", "track_${trackIndex.toString().padStart(3, '0')}")
                                .put("observation_count", config.frameIds.size)
                                .put(
                                    "normalized_observations",
                                    JSONArray().apply {
                                        config.frameIds.forEachIndexed { frameIndex, frameId ->
                                            put(
                                                JSONObject()
                                                    .put("frame_id", frameId)
                                                    .put("x_normalized_camera", -0.15 + trackIndex * 0.002 + frameIndex * 0.01)
                                                    .put("y_normalized_camera", -0.08 + (trackIndex % 8) * 0.01)
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            )
            .toString(2)
    )
}

private fun writeSyntheticBundleAdjustment(workspaceDir: File) {
    workspaceDir.resolve("poses").mkdirs()
    workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("allowed", true)
            .put("metric", true)
            .put("pose_candidate_coverage", 1.0)
            .put("frame_residual_max_px", 0.8)
            .put("track_residual_max_px", 0.9)
            .put("marker_residual_px", 0.9)
            .put("scale_residual_percent", 0.0)
            .put("errors", JSONArray())
            .toString(2)
    )
}

private fun syntheticMetricPoseOptimizerStatus(): ScannerNativePoseOptimizerStatus =
    ScannerNativePoseOptimizerStatus(
        available = true,
        status = "ready_synthetic_metric_pose_fixture",
        detail = "Synthetic metric pose fixture optimizer.",
        solverName = ScannerNativePoseOptimizerBridge.SolverName,
        ceresLinked = false,
        optimizerLinked = true
    )

private fun syntheticMetricPoseOptimizerResult(
    config: ScannerSyntheticMetricPoseFixtureConfig
): ScannerNativePoseOptimizerResult =
    ScannerNativePoseOptimizerResult(
        success = true,
        status = "ok",
        detail = "Synthetic metric optimizer output accepted under normal gates.",
        solverName = ScannerNativePoseOptimizerBridge.SolverName,
        ceresLinked = false,
        optimizerLinked = true,
        optimizedCameraPoses = config.frameIds.mapIndexed { index, frameId ->
            ScannerNativeOptimizedCameraPose(
                frameId = frameId,
                rotationRowMajor = identityRotation(),
                translationMm = listOf(index * 45.0, 0.0, 0.0),
                residualPx = 0.55f,
                metricValidated = true
            )
        },
        optimizedSparsePoints = List(config.trackCount) { index ->
            ScannerNativeOptimizedSparsePoint(
                pointId = "opt_point_${index.toString().padStart(3, '0')}",
                sourceTrackId = "track_${index.toString().padStart(3, '0')}",
                xyzMm = listOf((index % 12) * 4.0, (index / 12) * 5.0, 500.0 + (index % 4)),
                reprojectionResidualPx = 0.7f,
                provenanceClass = if (index % 6 == 0) "measured_low" else "measured_high"
            )
        },
        perFrameResiduals = config.frameIds.map { JSONObject().put("frame_id", it).put("residual_px", 0.55) },
        perTrackResiduals = emptyList(),
        markerResidualPx = 0.9f,
        scaleResidualPercent = 0f,
        markerCornerResiduals = List(config.markerCornerObservationCount) { index ->
            JSONObject().put("frame_id", config.frameIds[index % config.frameIds.size]).put("residual_px", 0.9)
        },
        markerCornerResidualCount = config.markerCornerObservationCount,
        markerCornerResidualMaxPx = 0.9f,
        markerCornerResidualMeanPx = 0.7f,
        markerCornerResidualBlocksEnabled = true,
        uncertaintyByFrame = config.frameIds.map { JSONObject().put("frame_id", it).put("uncertainty_class", "measured_high") },
        poseConditioning = config.frameIds.map { JSONObject().put("frame_id", it).put("conditioning_class", "well_conditioned") },
        prunedTrackReports = emptyList(),
        robustLoss = "huber",
        robustDeltaPx = 2.5f,
        outlierGatePx = 7.5f,
        iterativeOutlierPruningEnabled = true,
        rejectedObservations = emptyList(),
        rejectedTracks = emptyList(),
        solverIterations = 8,
        solverRuntimeMs = 10L
    )

private fun syntheticMetricFrame(frameId: String): JSONObject =
    JSONObject()
        .put("frame_id", frameId)
        .put(
            "intrinsics",
            JSONObject()
                .put("fx", 1000.0)
                .put("fy", 1000.0)
                .put("cx", 500.0)
                .put("cy", 400.0)
                .put("width", 1000)
                .put("height", 800)
        )

private fun syntheticMetricMarkerCorners(config: ScannerSyntheticMetricPoseFixtureConfig): JSONArray =
    JSONArray().apply {
        repeat(config.markerCornerObservationCount) { index ->
            put(
                JSONObject()
                    .put("frame_id", config.frameIds[index % config.frameIds.size])
                    .put("marker_id", (index / 4).toString())
                    .put("corner_index", index % 4)
                    .put("observed_x_normalized_camera", 0.01 * index)
                    .put("observed_y_normalized_camera", 0.005 * index)
                    .put("world_xyz_mm", JSONArray(listOf(index.toDouble(), 0.0, 500.0)))
            )
        }
    }

private fun identityRotation(): List<Double> =
    listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
