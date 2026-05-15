package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPoseOptimizerTest {
    @Test
    fun optimizerWritesMetricPoseAndSparsePointContractsButFailsClosed() {
        val workspaceDir = tempDir("scanner-pose-optimizer")
        writeReconstructionJob(workspaceDir)
        writeMetricPoseSolve(workspaceDir)
        writeFeatureTracks(workspaceDir, trackCount = 4)
        writeMatchGraph(workspaceDir)
        writeSparseReconstruction(workspaceDir, preparedTracks = 4)
        writeBundleAdjustment(workspaceDir)

        val result = optimizeScannerMetricPoses(
            workspaceDir,
            ScannerPoseOptimizerLimits(minPreparedTrackCount = 4)
        )

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertEquals("optimizer_boundary_blocked", result.status)
        assertEquals(3, result.poseCandidateCount)
        assertEquals(4, result.preparedTrackCount)
        assertEquals(0, result.optimizedPoseCount)
        assertEquals(0, result.optimizedSparsePointCount)
        assertTrue(result.errors.contains("metric_pose_solve_blocked"))
        assertTrue(result.errors.contains("bundle_adjustment_blocked"))
        assertTrue(result.errors.contains("sparse_reconstruction_blocked"))
        assertTrue(result.errors.contains("native_metric_optimizer_not_linked"))
        assertTrue(result.errors.any { it.startsWith("native_pose_optimizer_failed:") })
        assertTrue(result.errors.contains("optimized_pose_output_missing"))
        assertTrue(result.errors.contains("optimized_sparse_point_output_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).isFile)
        val poses = JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).readText())
        val points = JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).readText())
        assertFalse(poses.getBoolean("allowed"))
        assertFalse(points.getBoolean("allowed"))
        assertTrue(poses.has("optimizer_request"))
        assertTrue(poses.has("native_optimizer_response"))
        assertTrue(poses.has("evidence_diagnostics"))
        assertEquals(2, poses.getJSONObject("evidence_diagnostics").getInt("accepted_match_pair_count"))
        assertTrue(poses.getJSONObject("optimizer_request").has("match_graph_evidence"))
        assertEquals(3, poses.getJSONObject("optimizer_request").getJSONArray("candidate_camera_poses").length())
        assertEquals(4, poses.getJSONObject("optimizer_request").getJSONArray("feature_track_observations").length())
        assertEquals(16, poses.getJSONObject("optimizer_request").getJSONArray("marker_corner_observations").length())
        assertFalse(poses.getJSONObject("native_optimizer_response").getBoolean("success"))
        assertTrue(poses.getJSONObject("native_optimizer_response").has("uncertainty_by_frame"))
        assertTrue(poses.getJSONObject("native_optimizer_response").has("pose_conditioning"))
        assertTrue(poses.getJSONObject("native_optimizer_response").has("pruned_track_reports"))
        assertTrue(points.has("point_schema"))
        assertTrue(points.has("native_optimizer_response"))
    }

    @Test
    fun optimizerAcceptsNativeMetricSparseOutputWhenAllGatesPass() {
        val workspaceDir = tempDir("scanner-pose-optimizer-accepted")
        writeReconstructionJob(workspaceDir)
        writeMetricPoseSolve(workspaceDir, allowed = true, metric = true)
        writeFeatureTracks(workspaceDir, trackCount = 48)
        writeMatchGraph(workspaceDir)
        writeSparseReconstruction(workspaceDir, preparedTracks = 48, allowed = true)
        writeBundleAdjustment(workspaceDir, allowed = true, metric = true)

        val result = optimizeScannerMetricPoses(
            workspaceDir,
            ScannerPoseOptimizerLimits(
                minPreparedTrackCount = 48,
                minOptimizedSparsePointCount = 48
            ),
            nativeOptimizerStatusProvider = { readyNativeOptimizerStatus() },
            nativeOptimizer = { acceptedNativeOptimizerResult(pointCount = 48) }
        )

        assertTrue(result.errors.joinToString(), result.allowed)
        assertTrue(result.metric)
        assertEquals("accepted_metric_sparse_optimizer_output", result.status)
        assertEquals(3, result.optimizedPoseCount)
        assertEquals(48, result.optimizedSparsePointCount)

        val poses = JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).readText())
        val points = JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).readText())
        assertTrue(poses.getBoolean("allowed"))
        assertTrue(points.getBoolean("allowed"))
        assertEquals(3, poses.getJSONArray("poses").length())
        assertEquals(48, points.getJSONArray("points").length())
        assertEquals("measured_high", points.getJSONArray("points").getJSONObject(0).getString("provenance_class"))
        assertEquals(2, poses.getJSONObject("evidence_diagnostics").getInt("accepted_match_pair_count"))
        assertEquals(4, poses.getJSONObject("evidence_diagnostics").getInt("max_match_spatial_cell_count"))
    }

    @Test
    fun optimizerBlocksMissingInputs() {
        val workspaceDir = tempDir("scanner-pose-optimizer-missing")

        val result = optimizeScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("reconstruction_job_missing"))
        assertTrue(result.errors.contains("metric_pose_solve_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_missing"))
        assertTrue(result.errors.contains("bundle_adjustment_missing"))
    }

    private fun writeReconstructionJob(workspaceDir: File) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("scale_source", ScannerScaleSource.VerifiedMarkerMat.manifestValue)
                .put(
                    "calibration",
                    JSONObject()
                        .put("scale_confidence", 0.9)
                        .put("marker_reprojection_error_px", 1.0)
                )
                .put(
                    "frames",
                    JSONArray().apply {
                        put(frame("low"))
                        put(frame("mid"))
                        put(frame("high"))
                    }
                )
                .put("marker_corner_observations", markerCornerObservations())
                .toString(2)
        )
    }

    private fun writeMetricPoseSolve(
        workspaceDir: File,
        allowed: Boolean = false,
        metric: Boolean = false
    ) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put(
                    "camera_pose_candidates",
                    JSONArray().apply {
                        put(pose("low"))
                        put(pose("mid"))
                        put(pose("high"))
                    }
                )
                .toString(2)
        )
    }

    private fun writeSparseReconstruction(
        workspaceDir: File,
        preparedTracks: Int,
        allowed: Boolean = false
    ) {
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("prepared_track_count", preparedTracks)
                .put(
                    "tracks",
                    JSONArray().apply {
                        repeat(preparedTracks) { index ->
                            put(
                                JSONObject()
                                    .put("track_id", "track_${index.toString().padStart(3, '0')}")
                                    .put("observation_count", 3)
                                    .put(
                                        "normalized_observations",
                                        JSONArray().apply {
                                            put(normalizedObservation("low", -0.1 + index * 0.001, 0.0))
                                            put(normalizedObservation("mid", 0.0 + index * 0.001, 0.0))
                                            put(normalizedObservation("high", 0.1 + index * 0.001, 0.0))
                                        }
                                    )
                            )
                        }
                    }
                )
                .toString(2)
        )
    }

    private fun writeFeatureTracks(
        workspaceDir: File,
        trackCount: Int
    ) {
        workspaceDir.resolve("features").mkdirs()
        workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("track_count", trackCount)
                .put("long_track_count", trackCount / 2)
                .put("spatial_cell_count", 8)
                .put("errors", JSONArray())
                .toString(2)
        )
    }

    private fun writeMatchGraph(workspaceDir: File) {
        workspaceDir.resolve("matches").mkdirs()
        workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("errors", JSONArray())
                .put(
                    "pairs",
                    JSONArray().apply {
                        put(matchPair("low", "mid", 24))
                        put(matchPair("mid", "high", 24))
                    }
                )
                .toString(2)
        )
    }

    private fun matchPair(frameA: String, frameB: String, count: Int): JSONObject =
        JSONObject()
            .put("frame_a", frameA)
            .put("frame_b", frameB)
            .put("match_count", count)
            .put("accepted", true)
            .put("average_descriptor_distance", 2.0)
            .put("spatial_cell_count", 4)
            .put("average_mask_support", 220.0)

    private fun writeBundleAdjustment(
        workspaceDir: File,
        allowed: Boolean = false,
        metric: Boolean = false
    ) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("pose_candidate_coverage", 1.0)
                .put("frame_residual_max_px", 1.0)
                .put("track_residual_max_px", 1.0)
                .put("marker_residual_px", 1.0)
                .put("scale_residual_percent", 0.0)
                .toString(2)
        )
    }

    private fun pose(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("rotation_row_major", JSONArray(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)))
            .put("translation_graph_units", JSONArray(listOf(0.0, 0.0, 0.0)))

    private fun frame(frameId: String): JSONObject =
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

    private fun normalizedObservation(frameId: String, x: Double, y: Double): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("x_normalized_camera", x)
            .put("y_normalized_camera", y)

    private fun markerCornerObservations(): JSONArray =
        JSONArray().apply {
            listOf("low", "mid", "high", "low").forEachIndexed { markerId, frameId ->
                val baseX = markerId * 40.0
                val baseY = markerId * 10.0
                listOf(
                    baseX to baseY,
                    baseX + 32.0 to baseY,
                    baseX + 32.0 to baseY + 32.0,
                    baseX to baseY + 32.0
                ).forEachIndexed { cornerIndex, world ->
                    put(
                        JSONObject()
                            .put("frame_id", frameId)
                            .put("marker_id", markerId.toString())
                            .put("corner_index", cornerIndex)
                            .put("observed_x_normalized_camera", world.first / 500.0)
                            .put("observed_y_normalized_camera", world.second / 500.0)
                            .put("world_xyz_mm", JSONArray(listOf(world.first, world.second, 500.0)))
                    )
                }
            }
        }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()

    private fun readyNativeOptimizerStatus(): ScannerNativePoseOptimizerStatus =
        ScannerNativePoseOptimizerStatus(
            available = true,
            status = "ready_eigen_extrinsic",
            detail = "test native optimizer ready",
            solverName = ScannerNativePoseOptimizerBridge.SolverName,
            ceresLinked = false,
            optimizerLinked = true
        )

    private fun acceptedNativeOptimizerResult(pointCount: Int): ScannerNativePoseOptimizerResult =
        ScannerNativePoseOptimizerResult(
            success = true,
            status = "ok",
            detail = "test native optimizer accepted",
            solverName = ScannerNativePoseOptimizerBridge.SolverName,
            ceresLinked = false,
            optimizerLinked = true,
            optimizedCameraPoses = listOf("low", "mid", "high").mapIndexed { index, frameId ->
                ScannerNativeOptimizedCameraPose(
                    frameId = frameId,
                    rotationRowMajor = listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
                    translationMm = listOf(index * 40.0, 0.0, 0.0),
                    residualPx = 0.6f,
                    metricValidated = true
                )
            },
            optimizedSparsePoints = List(pointCount) { index ->
                ScannerNativeOptimizedSparsePoint(
                    pointId = "opt_point_${index + 1}",
                    sourceTrackId = "track_${index.toString().padStart(3, '0')}",
                    xyzMm = listOf(index.toDouble(), 10.0, 500.0),
                    reprojectionResidualPx = 0.7f,
                    provenanceClass = "measured_high"
                )
            },
            perFrameResiduals = listOf("low", "mid", "high").map { frameId ->
                JSONObject()
                    .put("frame_id", frameId)
                    .put("observation_count", 12)
                    .put("residual_px", 0.6)
            },
            perTrackResiduals = emptyList(),
            markerResidualPx = 1.0f,
            scaleResidualPercent = 0.0f,
            markerCornerResiduals = markerCornerObservations().let { observations ->
                List(observations.length()) { index ->
                    val observation = observations.getJSONObject(index)
                    JSONObject()
                        .put("frame_id", observation.getString("frame_id"))
                        .put("marker_id", observation.getString("marker_id"))
                        .put("corner_index", observation.getInt("corner_index"))
                        .put("residual_px", 1.0)
                }
            },
            markerCornerResidualCount = 16,
            markerCornerResidualMaxPx = 1.0f,
            markerCornerResidualMeanPx = 0.8f,
            markerCornerResidualBlocksEnabled = true,
            uncertaintyByFrame = listOf("low", "mid", "high").map { frameId ->
                JSONObject()
                    .put("frame_id", frameId)
                    .put("uncertainty_class", "measured_high")
            },
            poseConditioning = listOf("low", "mid", "high").map { frameId ->
                JSONObject()
                    .put("frame_id", frameId)
                    .put("conditioning_class", "well_conditioned")
            },
            prunedTrackReports = emptyList(),
            robustLoss = "huber",
            robustDeltaPx = 2.5f,
            outlierGatePx = 7.5f,
            iterativeOutlierPruningEnabled = true,
            rejectedObservations = emptyList(),
            rejectedTracks = emptyList(),
            solverIterations = 8,
            solverRuntimeMs = 12L
        )
}
