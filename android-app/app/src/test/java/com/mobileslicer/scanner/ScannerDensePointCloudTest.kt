package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDensePointCloudTest {
    @Test
    fun pointCloudBlocksWhenDenseAdmissionIsMissing() {
        val workspaceDir = tempDir("scanner-dense-cloud-missing")

        val result = buildScannerDensePointCloud(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.densePointCloudReady)
        assertFalse(result.surfaceReconstructionReady)
        assertTrue(result.errors.contains("dense_reconstruction_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).isFile)
    }

    @Test
    fun pointCloudBlocksWhenDenseAdmissionIsNotMetric() {
        val workspaceDir = tempDir("scanner-dense-cloud-blocked")
        writeDenseAdmission(workspaceDir, allowed = false, metric = false, admitted = false, pointCount = 0)

        val result = buildScannerDensePointCloud(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.densePointCloudReady)
        assertTrue(result.errors.contains("dense_reconstruction_blocked"))
        assertTrue(result.errors.contains("metric_dense_seed_points_missing"))
        assertTrue(result.errors.contains("dense_reconstruction_not_admitted"))
        assertTrue(result.errors.contains("surface_reconstruction_blocked_until_dense_point_cloud"))
    }

    @Test
    fun pointCloudCarriesMeasuredSeedsButBlocksSurfaceWhenTooSparse() {
        val workspaceDir = tempDir("scanner-dense-cloud-sparse")
        writeDenseAdmission(workspaceDir, allowed = true, metric = true, admitted = true, pointCount = 6)

        val result = buildScannerDensePointCloud(
            workspaceDir,
            ScannerDensePointCloudLimits(
                minPointCountForSurface = 20,
                minContributingFrameCount = 3,
                minObjectExtentMm = 5f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.densePointCloudReady)
        assertFalse(result.surfaceReconstructionReady)
        assertEquals("dense_point_cloud_ready_surface_blocked", result.status)
        assertTrue(result.errors.contains("dense_point_count_low_for_surface"))
        assertEquals(6, result.pointCount)
        assertEquals(3, result.contributingFrameCount)
        assertTrue(result.warnings.contains("no_interpolated_or_ai_generated_points"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("dense_point_cloud_ready"))
        assertFalse(json.getBoolean("surface_reconstruction_ready"))
        assertEquals(6, json.getJSONArray("points").length())
        assertEquals(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH, json.getString("debug_point_cloud_ply"))
        assertFalse(json.getBoolean("debug_point_cloud_printable"))
        assertFalse(json.getBoolean("debug_point_cloud_slicer_handoff_allowed"))
        assertTrue(workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).readText().contains("element vertex 6"))
    }

    @Test
    fun pointCloudCanMarkSurfaceReadyForDenseMeasuredFixture() {
        val workspaceDir = tempDir("scanner-dense-cloud-ready")
        writeDenseAdmission(workspaceDir, allowed = true, metric = true, admitted = true, pointCount = 30)

        val result = buildScannerDensePointCloud(
            workspaceDir,
            ScannerDensePointCloudLimits(
                minPointCountForSurface = 20,
                maxMeanNearestNeighborMm = 10f,
                minObjectExtentMm = 5f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.densePointCloudReady)
        assertTrue(result.surfaceReconstructionReady)
        assertEquals("dense_point_cloud_surface_ready", result.status)
    }

    @Test
    fun pointCloudCarriesBackProjectedDepthAssistPointsWhenSparseAdmissionIsMetric() {
        val workspaceDir = tempDir("scanner-dense-cloud-depth")
        writeDenseAdmission(workspaceDir, allowed = true, metric = true, admitted = true, pointCount = 6)
        workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).also {
            it.parentFile?.mkdirs()
            it.writeText(
                JSONObject()
                    .put("allowed", true)
                    .put(
                        "back_projected_points",
                        JSONArray().apply {
                            put(
                                JSONObject()
                                    .put("point_id", "depth_000001_000000")
                                    .put("frame_id", "000001")
                                    .put("xyz_mm", JSONObject().put("x", 20.0).put("y", 10.0).put("z", 505.0))
                                    .put("provenance_class", "measured_high")
                            )
                        }
                    )
                    .toString(2)
            )
        }

        val result = buildScannerDensePointCloud(
            workspaceDir,
            ScannerDensePointCloudLimits(
                minPointCountForSurface = 20,
                minContributingFrameCount = 3,
                minObjectExtentMm = 5f
            )
        )

        assertTrue(result.allowed)
        assertEquals(7, result.pointCount)
        assertTrue(result.points.any { it.sourceTrackId == "arcore_raw_depth:000001" })
    }

    private fun writeDenseAdmission(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        admitted: Boolean,
        pointCount: Int
    ) {
        workspaceDir.resolve("dense").mkdirs()
        workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("dense_reconstruction_admitted", admitted)
                .put("mesh_generation_ready", false)
                .put(
                    "seed_points",
                    JSONArray().apply {
                        repeat(pointCount) { index ->
                            put(
                                JSONObject()
                                    .put("point_id", "seed_$index")
                                    .put("source_track_id", "track_$index")
                                    .put("contributing_frames", JSONArray(listOf("low", "mid", "high")))
                                    .put(
                                        "xyz_mm",
                                        JSONObject()
                                            .put("x", (index % 10).toDouble())
                                            .put("y", (index / 10).toDouble())
                                            .put("z", 500.0 + (index % 3).toDouble())
                                    )
                                    .put("reprojection_residual_px", 0.8)
                                    .put("provenance_class", if (index % 5 == 0) "measured_low" else "measured_high")
                            )
                        }
                    }
                )
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
