package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSparseReconstructionTest {
    @Test
    fun sparseReconstructionPreparesNormalizedTrackInputsButKeepsTriangulationBlocked() {
        val workspaceDir = tempDir("scanner-sparse-inputs")
        writeReconstructionJob(workspaceDir)
        writeTracks(workspaceDir, allowed = true, trackCount = 4)

        val result = prepareScannerSparseReconstruction(
            workspaceDir,
            ScannerSparseReconstructionLimits(minPreparedTracks = 4)
        )

        assertTrue(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.triangulationReady)
        assertEquals(4, result.preparedTrackCount)
        assertEquals(0.91f, result.scaleConfidence, 0.0001f)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.contains("metric_camera_poses_missing"))
        assertTrue(result.warnings.contains("triangulation_blocked_until_metric_poses"))
        val json = JSONObject(workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertEquals(4, json.getJSONArray("tracks").length())
        val observation = json.getJSONArray("tracks")
            .getJSONObject(0)
            .getJSONArray("normalized_observations")
            .getJSONObject(0)
        assertEquals(-0.16, observation.getDouble("x_normalized_camera"), 0.0001)
    }

    @Test
    fun sparseReconstructionBlocksMissingScaleEvidence() {
        val workspaceDir = tempDir("scanner-sparse-no-scale")
        writeReconstructionJob(
            workspaceDir = workspaceDir,
            scaleSource = ScannerScaleSource.None.manifestValue,
            calibration = null
        )
        writeTracks(workspaceDir, allowed = true, trackCount = 4)

        val result = prepareScannerSparseReconstruction(
            workspaceDir,
            ScannerSparseReconstructionLimits(minPreparedTracks = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("verified_marker_mat_required"))
        assertTrue(result.errors.contains("calibration_missing"))
        assertTrue(result.errors.contains("scale_confidence_low"))
        assertTrue(result.errors.contains("marker_reprojection_missing"))
    }

    private fun writeReconstructionJob(
        workspaceDir: File,
        scaleSource: String = ScannerScaleSource.VerifiedMarkerMat.manifestValue,
        calibration: JSONObject? = calibrationJson()
    ) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("scale_source", scaleSource)
                .put("calibration", calibration ?: JSONObject.NULL)
                .put(
                    "frames",
                    JSONArray().apply {
                        put(reconstructionFrame("low"))
                        put(reconstructionFrame("mid"))
                        put(reconstructionFrame("high"))
                    }
                )
                .toString(2)
        )
    }

    private fun reconstructionFrame(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put(
                "intrinsics",
                JSONObject()
                    .put("fx", 500.0)
                    .put("fy", 500.0)
                    .put("cx", 100.0)
                    .put("cy", 100.0)
                    .put("width", 200)
                    .put("height", 200)
            )

    private fun writeTracks(
        workspaceDir: File,
        allowed: Boolean,
        trackCount: Int
    ) {
        workspaceDir.resolve("features").mkdirs()
        workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("errors", JSONArray())
                .put("track_count", trackCount)
                .put("long_track_count", trackCount)
                .put("tracks", JSONArray().apply { repeat(trackCount) { put(trackJson(it)) } })
                .toString(2)
        )
    }

    private fun trackJson(index: Int): JSONObject {
        val x = 20 + index * 10
        val y = 30 + index * 8
        return JSONObject()
            .put("track_id", "track_${index.toString().padStart(5, '0')}")
            .put("frame_count", 3)
            .put("pass_count", 3)
            .put(
                "observations",
                JSONArray()
                    .put(trackObservation("low", x, y))
                    .put(trackObservation("mid", x + 2, y + 1))
                    .put(trackObservation("high", x + 4, y + 2))
            )
    }

    private fun trackObservation(frameId: String, x: Int, y: Int): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("x", x)
            .put("y", y)
            .put("capture_pass", ScannerCapturePass.MidRing.manifestValue)
            .put("image_width", 200)
            .put("image_height", 200)

    private fun calibrationJson(): JSONObject =
        JSONObject()
            .put("marker_type", "apriltag")
            .put("marker_size_mm", 40.0)
            .put("printed_scale_bar_expected_mm", 100.0)
            .put("printed_scale_bar_measured_mm", 100.0)
            .put("scale_correction", 1.0)
            .put("scale_confidence", 0.91)
            .put("marker_reprojection_error_px", 1.6)

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
