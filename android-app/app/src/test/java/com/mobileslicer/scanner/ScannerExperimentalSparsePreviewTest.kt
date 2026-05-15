package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerExperimentalSparsePreviewTest {
    @Test
    fun previewWritesNonMetricDepthAndTrackArtifact() {
        val workspaceDir = tempDir("scanner-experimental-sparse-preview")
        writeDepthFusion(workspaceDir, pointCount = 4)
        writeFeatureTracks(workspaceDir, trackCount = 4)

        val result = buildScannerExperimentalSparsePreview(
            workspaceDir,
            ScannerExperimentalSparsePreviewLimits(
                maxPreviewPoints = 10,
                minDepthPreviewPoints = 4,
                minFeatureTracks = 4
            )
        )

        assertTrue(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.printable)
        assertEquals("experimental_sparse_preview_ready_not_metric", result.status)
        assertEquals(4, result.previewPointCount)
        assertEquals(4, result.featureTrackCount)
        assertTrue(result.errors.contains("calibration_deferred_preview_is_not_metric"))
        assertTrue(result.errors.contains("printable_handoff_blocked_for_experimental_preview"))
        assertTrue(workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH).isFile)

        val json = JSONObject(workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH).readText())
        assertFalse(json.getBoolean("metric"))
        assertFalse(json.getBoolean("printable"))
        assertFalse(json.getJSONObject("contract").getBoolean("slicer_handoff_allowed"))
        assertEquals(4, json.getJSONArray("preview_points").length())
    }

    @Test
    fun previewBlocksWhenDepthAndTracksAreMissing() {
        val workspaceDir = tempDir("scanner-experimental-sparse-preview-missing")

        val result = buildScannerExperimentalSparsePreview(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertEquals("experimental_sparse_preview_blocked", result.status)
        assertTrue(result.errors.contains("depth_fusion_missing"))
        assertTrue(result.errors.contains("feature_tracks_missing"))
        assertTrue(result.errors.contains("depth_preview_point_count_low"))
        assertTrue(result.errors.contains("feature_track_count_low"))
    }

    private fun writeDepthFusion(workspaceDir: File, pointCount: Int) {
        workspaceDir.resolve("depth").mkdirs()
        workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("back_projected_points", JSONArray().apply {
                    repeat(pointCount) { index ->
                        put(
                            JSONObject()
                                .put("point_id", "depth_$index")
                                .put("frame_id", "000001")
                                .put(
                                    "xyz_mm",
                                    JSONObject()
                                        .put("x", index.toDouble())
                                        .put("y", 10.0)
                                        .put("z", 500.0)
                                )
                                .put("provenance_class", "arcore_raw_depth_assist")
                        )
                    }
                })
                .toString(2)
        )
    }

    private fun writeFeatureTracks(workspaceDir: File, trackCount: Int) {
        workspaceDir.resolve("features").mkdirs()
        workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("track_count", trackCount)
                .put("tracks", JSONArray())
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
