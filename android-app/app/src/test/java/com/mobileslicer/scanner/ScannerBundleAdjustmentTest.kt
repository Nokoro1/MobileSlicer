package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerBundleAdjustmentTest {
    @Test
    fun bundleAdjustmentAdmitsOptimizerWhenResidualDiagnosticsPass() {
        val workspaceDir = tempDir("scanner-bundle-adjustment")
        writeReconstructionJob(workspaceDir)
        writeMetricPoseGraph(workspaceDir)
        writeSparseReconstruction(workspaceDir, preparedTracks = 4)

        val result = analyzeScannerBundleAdjustment(
            workspaceDir,
            ScannerBundleAdjustmentLimits(minPreparedTrackCount = 4)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertEquals("optimizer_admission_ready", result.status)
        assertEquals(3, result.poseCandidateCount)
        assertEquals(4, result.preparedTrackCount)
        assertEquals(0, result.rejectedConstraintCount)
        assertTrue(result.frameResidualMaxPx != null)
        assertTrue(result.trackResidualMaxPx != null)
        assertEquals(1.4f, result.markerResidualPx ?: -1f, 0.0001f)
        assertEquals(0f, result.scaleResidualPercent ?: -1f, 0.0001f)
        assertTrue(result.errors.isEmpty())
        val json = JSONObject(workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertEquals("optimizer_admission_ready", json.getString("status"))
        assertEquals(3, json.getJSONArray("frame_residuals").length())
        assertEquals(4, json.getJSONArray("track_residuals").length())
    }

    @Test
    fun bundleAdjustmentBlocksWhenInputsAreMissing() {
        val workspaceDir = tempDir("scanner-bundle-adjustment-missing")

        val result = analyzeScannerBundleAdjustment(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("reconstruction_job_missing"))
        assertTrue(result.errors.contains("metric_pose_graph_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).isFile)
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
                        .put("scale_confidence", 0.92)
                        .put("scale_correction", 1.0)
                        .put("marker_reprojection_error_px", 1.4)
                )
                .put(
                    "frames",
                    JSONArray().apply {
                        put(frame("low"))
                        put(frame("mid"))
                        put(frame("high"))
                    }
                )
                .toString(2)
        )
    }

    private fun writeMetricPoseGraph(workspaceDir: File) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", false)
                .put("bundle_adjustment_ready", true)
                .put("errors", JSONArray())
                .put("marker_reprojection_error_px", 1.4)
                .put(
                    "nodes",
                    JSONArray().apply {
                        put(node("low"))
                        put(node("mid"))
                        put(node("high"))
                    }
                )
                .put(
                    "relative_pose_constraints",
                    JSONArray().apply {
                        put(constraint("low", "mid", listOf(1.0, 0.0, 0.0)))
                        put(constraint("mid", "high", listOf(1.0, 0.0, 0.0)))
                    }
                )
                .toString(2)
        )
    }

    private fun writeSparseReconstruction(workspaceDir: File, preparedTracks: Int) {
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", false)
                .put("prepared_track_count", preparedTracks)
                .put("errors", JSONArray(listOf("triangulation_blocked_until_metric_poses")))
                .put(
                    "tracks",
                    JSONArray().apply {
                        repeat(preparedTracks) { index ->
                            put(track(index))
                        }
                    }
                )
                .toString(2)
        )
    }

    private fun frame(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put(
                "intrinsics",
                JSONObject()
                    .put("fx", 900.0)
                    .put("fy", 900.0)
                    .put("cx", 320.0)
                    .put("cy", 240.0)
                    .put("width", 640)
                    .put("height", 480)
            )

    private fun node(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("capture_pass", ScannerCapturePass.MidRing.manifestValue)

    private fun constraint(frameA: String, frameB: String, translation: List<Double>): JSONObject =
        JSONObject()
            .put("frame_a", frameA)
            .put("frame_b", frameB)
            .put("relative_pose_available", true)
            .put("translation_unit", JSONArray(translation))

    private fun track(index: Int): JSONObject =
        JSONObject()
            .put("track_id", "track_$index")
            .put("observation_count", 3)
            .put(
                "normalized_observations",
                JSONArray().apply {
                    put(observation("low", index))
                    put(observation("mid", index))
                    put(observation("high", index))
                }
            )

    private fun observation(frameId: String, index: Int): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("x_normalized_camera", 0.01 * index)
            .put("y_normalized_camera", 0.02 * index)

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
