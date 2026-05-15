package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMeshTopologyTest {
    @Test
    fun topologyBlocksWhenSurfaceReconstructionIsMissing() {
        val workspaceDir = tempDir("scanner-topology-missing")

        val result = admitScannerMeshTopology(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.topologyReconstructionReady)
        assertFalse(result.metricMeshReady)
        assertFalse(result.printabilityValidationReady)
        assertTrue(result.errors.contains("surface_reconstruction_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH).isFile)
    }

    @Test
    fun topologyBlocksWhenSurfaceIsNotMetricReady() {
        val workspaceDir = tempDir("scanner-topology-surface-blocked")
        writeSurfaceReconstruction(workspaceDir, allowed = false, metric = false, surfaceReady = false)

        val result = admitScannerMeshTopology(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.topologyReconstructionReady)
        assertTrue(result.errors.contains("surface_reconstruction_blocked"))
        assertTrue(result.errors.contains("metric_surface_reconstruction_missing"))
        assertTrue(result.errors.contains("surface_reconstruction_not_ready_for_topology"))
        assertTrue(result.errors.contains("topology_generation_blocked_until_surface_reconstruction"))
    }

    @Test
    fun topologyAcceptsMeasuredSurfaceButKeepsMetricMeshBlocked() {
        val workspaceDir = tempDir("scanner-topology-ready")
        writeSurfaceReconstruction(workspaceDir, allowed = true, metric = true, surfaceReady = true)

        val result = admitScannerMeshTopology(
            workspaceDir,
            ScannerMeshTopologyLimits(
                minSourcePointCount = 40,
                minMeasuredSurfaceRatio = 0.60f,
                maxMeanPointSpacingMm = 5f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.topologyReconstructionReady)
        assertFalse(result.metricMeshReady)
        assertFalse(result.printabilityValidationReady)
        assertEquals("topology_reconstruction_ready_mesh_blocked", result.status)
        assertTrue(result.errors.contains("metric_mesh_blocked_until_triangle_generation"))
        assertTrue(result.warnings.contains("stl_3mf_export_remains_blocked"))
        assertTrue(result.warnings.contains("workspace_handoff_remains_blocked"))
        assertEquals(1, result.patches.size)
        assertEquals(64, result.candidatePointCount)
        assertEquals(8, result.coverageCellCount)
        assertEquals("measured_metric_surface", result.patches.first().topologyClass)

        val json = JSONObject(workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("topology_reconstruction_ready"))
        assertFalse(json.getBoolean("metric_mesh_ready"))
        assertFalse(json.getBoolean("printability_validation_ready"))
        assertEquals(1, json.getJSONArray("patches").length())
        assertEquals(SCANNER_SURFACE_CANDIDATE_PATH, json.getString("surface_candidate"))
        assertEquals(64, json.getInt("candidate_point_count"))
        assertEquals(8, json.getInt("coverage_cell_count"))
        assertTrue(json.getJSONObject("provenance_schema").has("visual_only"))
    }

    @Test
    fun topologyBlocksRepairedOrSparseSurfaceForMetricMesh() {
        val workspaceDir = tempDir("scanner-topology-repaired")
        writeSurfaceReconstruction(
            workspaceDir = workspaceDir,
            allowed = true,
            metric = true,
            surfaceReady = true,
            sourcePointCount = 24,
            measuredSurfaceRatio = 0.40f,
            repairedSurfaceRatio = 0.12f
        )

        val result = admitScannerMeshTopology(
            workspaceDir,
            ScannerMeshTopologyLimits(
                minSourcePointCount = 40,
                minMeasuredSurfaceRatio = 0.60f
            )
        )

        assertFalse(result.allowed)
        assertFalse(result.topologyReconstructionReady)
        assertTrue(result.errors.contains("source_point_count_low_for_topology"))
        assertTrue(result.errors.contains("measured_surface_ratio_low"))
        assertTrue(result.errors.contains("repaired_surface_ratio_high_for_metric_mesh"))
    }

    private fun writeSurfaceReconstruction(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        surfaceReady: Boolean,
        sourcePointCount: Int = 64,
        measuredSurfaceRatio: Float = 0.78f,
        repairedSurfaceRatio: Float = 0f
    ) {
        workspaceDir.resolve("surface").mkdirs()
        if (allowed && sourcePointCount > 0) {
            workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).writeText(
                JSONObject()
                    .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                    .put("metric", metric)
                    .put("printable", false)
                    .put("slicer_handoff_allowed", false)
                    .put(
                        "coverage_cells",
                        JSONArray().apply {
                            repeat(8) { index ->
                                put(
                                    JSONObject()
                                        .put("cell_id", "cell_$index")
                                        .put("column", index % 4)
                                        .put("row", index / 4)
                                        .put("point_count", sourcePointCount / 8)
                                        .put("measured_high_count", sourcePointCount / 8)
                                        .put("provenance_class", "measured_high")
                                )
                            }
                        }
                    )
                    .put(
                        "candidate_points",
                        JSONArray().apply {
                            repeat(sourcePointCount) { index ->
                                put(
                                    JSONObject()
                                        .put("point_id", "dense_${index.toString().padStart(6, '0')}")
                                        .put("source_point_id", "point_$index")
                                        .put("source_track_id", "track_$index")
                                        .put("contributing_frames", JSONArray(listOf("000001", "000002", "000003")))
                                        .put("xyz_mm", JSONObject().put("x", (index % 8).toDouble()).put("y", (index / 8).toDouble()).put("z", 500.0))
                                        .put("provenance_class", "measured_high")
                                )
                            }
                        }
                    )
                    .toString(2)
            )
        }
        workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("surface_reconstruction_ready", surfaceReady)
                .put("mesh_generation_ready", false)
                .put("source_point_count", sourcePointCount)
                .put("contributing_frame_count", if (sourcePointCount > 0) 4 else 0)
                .put("measured_high_ratio", if (sourcePointCount > 0) 0.84 else 0.0)
                .put("measured_surface_ratio", measuredSurfaceRatio.toDouble())
                .put("repaired_surface_ratio", repairedSurfaceRatio.toDouble())
                .put("coverage_cell_count", if (sourcePointCount > 0) 8 else 0)
                .put("mean_point_spacing_mm", if (sourcePointCount > 0) 2.0 else JSONObject.NULL)
                .put(
                    "bounding_box_mm",
                    if (sourcePointCount > 0) {
                        JSONObject()
                            .put("min", JSONObject().put("x", 0.0).put("y", 0.0).put("z", 500.0))
                            .put("max", JSONObject().put("x", 36.0).put("y", 28.0).put("z", 518.0))
                            .put("extent", JSONObject().put("x", 36.0).put("y", 28.0).put("z", 18.0))
                    } else {
                        JSONObject.NULL
                    }
                )
                .put(
                    "patches",
                    if (allowed && sourcePointCount > 0) {
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("patch_id", "measured_surface_patch_0")
                                    .put("source_point_count", sourcePointCount)
                                    .put("contributing_frame_count", 4)
                                    .put("provenance_class", "measured_high")
                                    .put("measured_surface_ratio", measuredSurfaceRatio.toDouble())
                                    .put("repaired_surface_ratio", repairedSurfaceRatio.toDouble())
                                    .put("mean_point_spacing_mm", 2.0)
                            )
                    } else {
                        JSONArray()
                    }
                )
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
