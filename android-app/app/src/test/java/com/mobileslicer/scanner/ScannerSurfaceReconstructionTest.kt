package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSurfaceReconstructionTest {
    @Test
    fun surfaceBlocksWhenDensePointCloudIsMissing() {
        val workspaceDir = tempDir("scanner-surface-missing")

        val result = reconstructScannerSurface(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.surfaceReconstructionReady)
        assertFalse(result.meshGenerationReady)
        assertTrue(result.errors.contains("dense_point_cloud_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH).isFile)
    }

    @Test
    fun surfaceBlocksWhenPointCloudIsNotReady() {
        val workspaceDir = tempDir("scanner-surface-cloud-blocked")
        writeDensePointCloud(workspaceDir, allowed = false, metric = false, surfaceReady = false, pointCount = 0)

        val result = reconstructScannerSurface(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.surfaceReconstructionReady)
        assertTrue(result.errors.contains("dense_point_cloud_blocked"))
        assertTrue(result.errors.contains("metric_dense_point_cloud_missing"))
        assertTrue(result.errors.contains("surface_reconstruction_not_admitted_by_point_cloud"))
        assertTrue(result.errors.contains("mesh_generation_blocked_until_surface_reconstruction"))
    }

    @Test
    fun surfaceAcceptsMeasuredPointCloudButKeepsMeshBlocked() {
        val workspaceDir = tempDir("scanner-surface-ready")
        writeDensePointCloud(workspaceDir, allowed = true, metric = true, surfaceReady = true, pointCount = 36)

        val result = reconstructScannerSurface(
            workspaceDir,
            ScannerSurfaceReconstructionLimits(
                minSurfacePointCount = 30,
                maxMeanPointSpacingMm = 10f,
                minSurfaceCoverageRatio = 0.30f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.surfaceReconstructionReady)
        assertFalse(result.meshGenerationReady)
        assertEquals("surface_reconstruction_ready_mesh_blocked", result.status)
        assertTrue(result.errors.contains("mesh_generation_blocked_until_topology_reconstruction"))
        assertEquals(36, result.sourcePointCount)
        assertEquals(1, result.patches.size)
        assertEquals(36, result.candidatePoints.size)
        assertTrue(result.coverageCells.isNotEmpty())
        assertEquals(0f, result.repairedSurfaceRatio, 0.0f)

        val json = JSONObject(workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("surface_reconstruction_ready"))
        assertFalse(json.getBoolean("mesh_generation_ready"))
        assertEquals(SCANNER_SURFACE_CANDIDATE_PATH, json.getString("surface_candidate"))
        assertFalse(json.getBoolean("surface_candidate_printable"))
        assertFalse(json.getBoolean("surface_candidate_slicer_handoff_allowed"))
        assertTrue(json.getInt("coverage_cell_count") > 0)
        assertEquals(1, json.getJSONArray("patches").length())
        val candidate = JSONObject(workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).readText())
        assertFalse(candidate.getBoolean("printable"))
        assertFalse(candidate.getBoolean("slicer_handoff_allowed"))
        assertEquals(36, candidate.getJSONArray("candidate_points").length())
        assertTrue(candidate.getJSONArray("coverage_cells").length() > 0)
    }

    @Test
    fun surfaceBlocksSparseMeasuredCoverage() {
        val workspaceDir = tempDir("scanner-surface-sparse")
        writeDensePointCloud(workspaceDir, allowed = true, metric = true, surfaceReady = true, pointCount = 8)

        val result = reconstructScannerSurface(
            workspaceDir,
            ScannerSurfaceReconstructionLimits(
                minSurfacePointCount = 30,
                minSurfaceCoverageRatio = 0.80f
            )
        )

        assertFalse(result.allowed)
        assertFalse(result.surfaceReconstructionReady)
        assertTrue(result.errors.contains("surface_point_count_low"))
        assertTrue(result.errors.contains("measured_surface_ratio_low"))
    }

    private fun writeDensePointCloud(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        surfaceReady: Boolean,
        pointCount: Int
    ) {
        workspaceDir.resolve("dense").mkdirs()
        workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("dense_point_cloud_ready", allowed)
                .put("surface_reconstruction_ready", surfaceReady)
                .put("point_count", pointCount)
                .put("contributing_frame_count", if (pointCount > 0) 3 else 0)
                .put("measured_high_ratio", if (pointCount > 0) 0.9 else 0.0)
                .put("mean_nearest_neighbor_mm", if (pointCount > 0) 2.0 else JSONObject.NULL)
                .put(
                    "bounding_box_mm",
                    if (pointCount > 0) {
                        JSONObject()
                            .put("min", JSONObject().put("x", 0.0).put("y", 0.0).put("z", 500.0))
                            .put("max", JSONObject().put("x", 20.0).put("y", 20.0).put("z", 506.0))
                            .put("extent", JSONObject().put("x", 20.0).put("y", 20.0).put("z", 6.0))
                    } else {
                        JSONObject.NULL
                    }
                )
                .put(
                    "points",
                    JSONArray().apply {
                        repeat(pointCount) { index ->
                            put(
                                JSONObject()
                                    .put("point_id", "dense_${index.toString().padStart(6, '0')}")
                                    .put("source_point_id", "point_$index")
                                    .put("source_track_id", "track_$index")
                                    .put("contributing_frames", JSONArray(listOf("000001", "000002", "000003")))
                                    .put(
                                        "xyz_mm",
                                        JSONObject()
                                            .put("x", (index % 6).toDouble() * 3.5)
                                            .put("y", (index / 6).toDouble() * 3.5)
                                            .put("z", 500.0 + (index % 3).toDouble())
                                    )
                                    .put("reprojection_residual_px", 0.4)
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
