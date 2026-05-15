package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDenseReconstructionTest {
    @Test
    fun denseReconstructionBlocksWhenMetricSparsePointsAreMissing() {
        val workspaceDir = tempDir("scanner-dense-missing")

        val result = admitScannerDenseReconstruction(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionAdmitted)
        assertFalse(result.meshGenerationReady)
        assertTrue(result.errors.contains("sparse_triangulation_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).isFile)
    }

    @Test
    fun denseReconstructionBlocksSparseTriangulationThatIsNotMetric() {
        val workspaceDir = tempDir("scanner-dense-blocked")
        writeSparseTriangulation(workspaceDir, allowed = false, metric = false, denseReady = false, pointCount = 0)

        val result = admitScannerDenseReconstruction(
            workspaceDir,
            ScannerDenseReconstructionLimits(minMetricSparsePoints = 4)
        )

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionAdmitted)
        assertTrue(result.errors.contains("sparse_triangulation_blocked"))
        assertTrue(result.errors.contains("metric_sparse_points_missing"))
        assertTrue(result.errors.contains("dense_reconstruction_not_admitted_by_sparse_stage"))
        assertTrue(result.errors.contains("mesh_generation_blocked_until_dense_reconstruction"))
    }

    @Test
    fun denseReconstructionAdmitsValidatedMetricSparseSeedsButKeepsMeshBlocked() {
        val workspaceDir = tempDir("scanner-dense-admitted")
        writeSparseTriangulation(workspaceDir, allowed = true, metric = true, denseReady = true, pointCount = 6)

        val result = admitScannerDenseReconstruction(
            workspaceDir,
            ScannerDenseReconstructionLimits(
                minMetricSparsePoints = 4,
                minContributingFrameCount = 3,
                minObjectExtentMm = 5f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.denseReconstructionAdmitted)
        assertFalse(result.meshGenerationReady)
        assertEquals("dense_reconstruction_admitted", result.status)
        assertEquals(6, result.sparsePointCount)
        assertEquals(3, result.contributingFrameCount)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.boundingBox!!.maxExtent >= 5.0)

        val json = JSONObject(workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("dense_reconstruction_admitted"))
        assertFalse(json.getBoolean("mesh_generation_ready"))
        assertEquals(6, json.getJSONArray("seed_points").length())
        assertTrue(json.has("bounding_box_mm"))
    }

    private fun writeSparseTriangulation(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        denseReady: Boolean,
        pointCount: Int
    ) {
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("dense_reconstruction_ready", denseReady)
                .put("measured_point_count", pointCount)
                .put(
                    "measured_points",
                    JSONArray().apply {
                        repeat(pointCount) { index ->
                            put(
                                JSONObject()
                                    .put("point_id", "point_$index")
                                    .put("source_track_id", "track_$index")
                                    .put("contributing_frames", JSONArray(listOf("low", "mid", "high")))
                                    .put(
                                        "xyz_mm",
                                        JSONObject()
                                            .put("x", index.toDouble() * 2.0)
                                            .put("y", if (index % 2 == 0) 0.0 else 8.0)
                                            .put("z", 500.0 + index.toDouble())
                                    )
                                    .put("reprojection_residual_px", 0.8)
                                    .put("provenance_class", if (index < 4) "measured_high" else "measured_low")
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
