package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSparseTriangulationTest {
    @Test
    fun sparseTriangulationWritesPointContractButBlocksWithoutMetricBundleAdjustment() {
        val workspaceDir = tempDir("scanner-sparse-triangulation")
        writeReconstructionJob(workspaceDir)
        writeMetricPoseSolve(workspaceDir)
        writeSparseReconstruction(workspaceDir, preparedTracks = 4)
        writeBundleAdjustment(workspaceDir)
        writeOptimizedOutputs(workspaceDir)

        val result = prepareScannerSparseTriangulation(
            workspaceDir,
            ScannerSparseTriangulationLimits(minPreparedTracks = 4)
        )

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionReady)
        assertEquals("metric_sparse_points_blocked", result.status)
        assertEquals(4, result.preparedTrackCount)
        assertEquals(3, result.poseCandidateCount)
        assertEquals(0, result.measuredPointCount)
        assertTrue(result.errors.contains("metric_pose_solve_blocked"))
        assertTrue(result.errors.contains("metric_camera_poses_missing"))
        assertTrue(result.errors.contains("bundle_adjustment_blocked"))
        assertTrue(result.errors.contains("bundle_adjustment_metric_missing"))
        assertTrue(result.errors.contains("optimized_metric_poses_blocked"))
        assertTrue(result.errors.contains("optimized_metric_poses_missing"))
        assertTrue(result.errors.contains("optimized_sparse_points_blocked"))
        assertTrue(result.errors.contains("optimized_sparse_points_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_blocked"))
        assertTrue(result.errors.contains("dense_reconstruction_blocked_until_sparse_points_are_metric"))
        val json = JSONObject(workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).readText())
        assertFalse(json.getBoolean("allowed"))
        assertEquals("metric_sparse_points_blocked", json.getString("status"))
        assertTrue(json.has("point_schema"))
        assertEquals(0, json.getJSONArray("measured_points").length())
    }

    @Test
    fun sparseTriangulationAcceptsValidatedOptimizedMetricSparsePoints() {
        val workspaceDir = tempDir("scanner-sparse-triangulation-accepted")
        writeReconstructionJob(workspaceDir)
        writeMetricPoseSolve(workspaceDir, allowed = true, metric = true)
        writeSparseReconstruction(workspaceDir, preparedTracks = 4, allowed = true, metric = true)
        writeBundleAdjustment(workspaceDir, allowed = true, metric = true)
        writeOptimizedOutputs(workspaceDir, allowed = true, metric = true, pointCount = 4)

        val result = prepareScannerSparseTriangulation(
            workspaceDir,
            ScannerSparseTriangulationLimits(
                minPreparedTracks = 4,
                minMeasuredSparsePoints = 4
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.denseReconstructionReady)
        assertEquals("accepted_metric_sparse_points", result.status)
        assertEquals(4, result.measuredPointCount)
        assertTrue(result.errors.isEmpty())

        val json = JSONObject(workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("dense_reconstruction_ready"))
        assertEquals(4, json.getJSONArray("measured_points").length())
        assertEquals("measured_high", json.getJSONArray("measured_points").getJSONObject(0).getString("provenance_class"))
    }

    @Test
    fun sparseTriangulationBlocksWhenInputsAreMissing() {
        val workspaceDir = tempDir("scanner-sparse-triangulation-missing")

        val result = prepareScannerSparseTriangulation(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("reconstruction_job_missing"))
        assertTrue(result.errors.contains("metric_pose_solve_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_missing"))
        assertTrue(result.errors.contains("bundle_adjustment_missing"))
        assertTrue(result.errors.contains("optimized_metric_poses_missing"))
        assertTrue(result.errors.contains("optimized_sparse_points_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).isFile)
    }

    private fun writeReconstructionJob(workspaceDir: File) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("scale_source", ScannerScaleSource.VerifiedMarkerMat.manifestValue)
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
                        put(poseCandidate("low"))
                        put(poseCandidate("mid"))
                        put(poseCandidate("high"))
                    }
                )
                .toString(2)
        )
    }

    private fun writeSparseReconstruction(
        workspaceDir: File,
        preparedTracks: Int,
        allowed: Boolean = false,
        metric: Boolean = false
    ) {
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("prepared_track_count", preparedTracks)
                .put(
                    "tracks",
                    JSONArray().apply {
                        repeat(preparedTracks) { index -> put(track(index)) }
                    }
                )
                .toString(2)
        )
    }

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
                .toString(2)
        )
    }

    private fun writeOptimizedOutputs(
        workspaceDir: File,
        allowed: Boolean = false,
        metric: Boolean = false,
        pointCount: Int = 0
    ) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("poses", JSONArray().apply {
                    listOf("low", "mid", "high").forEach { frameId ->
                        put(
                            JSONObject()
                                .put("frame_id", frameId)
                                .put("metric_validated", metric)
                        )
                    }
                })
                .toString(2)
        )
        workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("points", JSONArray().apply {
                    repeat(pointCount) { index ->
                        put(
                            JSONObject()
                                .put("point_id", "opt_point_${index + 1}")
                                .put("source_track_id", "track_$index")
                                .put("xyz_mm", JSONArray(listOf(index.toDouble(), 10.0, 500.0)))
                                .put("reprojection_residual_px", 0.8)
                                .put("provenance_class", "measured_high")
                        )
                    }
                })
                .toString(2)
        )
    }

    private fun poseCandidate(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("metric_validated", false)

    private fun track(index: Int): JSONObject =
        JSONObject()
            .put("track_id", "track_$index")
            .put("observation_count", 3)
            .put(
                "normalized_observations",
                JSONArray().apply {
                    put(observation("low"))
                    put(observation("mid"))
                    put(observation("high"))
                }
            )

    private fun observation(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("x_normalized_camera", 0.01)
            .put("y_normalized_camera", 0.02)

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
