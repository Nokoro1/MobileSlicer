package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMetricMeshCandidateTest {
    @Test
    fun meshCandidateBlocksWhenTopologyIsMissing() {
        val workspaceDir = tempDir("scanner-mesh-candidate-missing")

        val result = buildScannerMetricMeshCandidate(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.triangleMeshCandidateReady)
        assertFalse(result.metricMeshReady)
        assertFalse(result.printabilityValidationReady)
        assertTrue(result.errors.contains("mesh_topology_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).isFile)
    }

    @Test
    fun meshCandidateBlocksWhenDensePointCloudIsMissing() {
        val workspaceDir = tempDir("scanner-mesh-candidate-no-points")
        writeMeshTopology(workspaceDir, allowed = true, metric = true, topologyReady = true)

        val result = buildScannerMetricMeshCandidate(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("dense_point_cloud_missing"))
    }

    @Test
    fun meshCandidateBlocksWhenTopologyIsNotReady() {
        val workspaceDir = tempDir("scanner-mesh-candidate-topology-blocked")
        writeMeshTopology(workspaceDir, allowed = false, metric = false, topologyReady = false)
        writeDensePointCloud(workspaceDir, pointCount = 12)

        val result = buildScannerMetricMeshCandidate(
            workspaceDir,
            ScannerMetricMeshCandidateLimits(minVertexCount = 9, minTriangleCount = 3, maxTriangleEdgeMm = 20f)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("mesh_topology_blocked"))
        assertTrue(result.errors.contains("metric_topology_missing"))
        assertTrue(result.errors.contains("topology_reconstruction_not_ready"))
        assertTrue(result.errors.contains("metric_mesh_blocked_until_triangle_candidate"))
    }

    @Test
    fun meshCandidateBuildsMeasuredTrianglesButKeepsMetricMeshBlocked() {
        val workspaceDir = tempDir("scanner-mesh-candidate-ready")
        writeMeshTopology(workspaceDir, allowed = true, metric = true, topologyReady = true)
        writeDensePointCloud(workspaceDir, pointCount = 18)

        val result = buildScannerMetricMeshCandidate(
            workspaceDir,
            ScannerMetricMeshCandidateLimits(
                minVertexCount = 12,
                minTriangleCount = 8,
                maxTriangleEdgeMm = 20f,
                minTriangleAreaMm2 = 0.01f
            )
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.triangleMeshCandidateReady)
        assertFalse(result.metricMeshReady)
        assertFalse(result.printabilityValidationReady)
        assertEquals("triangle_mesh_candidate_ready_validation_blocked", result.status)
        assertTrue(result.errors.contains("metric_mesh_blocked_until_topology_validation"))
        assertTrue(result.warnings.contains("candidate_uses_measured_vertices_only"))
        assertTrue(result.warnings.contains("stl_3mf_export_remains_blocked"))
        assertTrue(result.vertexCount >= 12)
        assertTrue(result.triangleCount >= 8)
        assertTrue(result.coverageCellCount >= 4)
        assertTrue(result.triangles.all { it.provenanceClass == "measured_high" })

        val json = JSONObject(workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("triangle_mesh_candidate_ready"))
        assertFalse(json.getBoolean("metric_mesh_ready"))
        assertFalse(json.getBoolean("printability_validation_ready"))
        assertTrue(json.getJSONArray("vertices").length() >= 12)
        assertTrue(json.getJSONArray("triangles").length() >= 8)
        assertTrue(json.getInt("coverage_cell_count") >= 4)
    }

    @Test
    fun meshCandidateRejectsVisualOrRepairedEvidence() {
        val workspaceDir = tempDir("scanner-mesh-candidate-rejected")
        writeMeshTopology(workspaceDir, allowed = true, metric = true, topologyReady = true, repairedSurfaceRatio = 0.10f)
        writeDensePointCloud(workspaceDir, pointCount = 18, visualOnlyLastPoint = true)

        val result = buildScannerMetricMeshCandidate(
            workspaceDir,
            ScannerMetricMeshCandidateLimits(
                minVertexCount = 12,
                minTriangleCount = 8,
                maxTriangleEdgeMm = 20f
            )
        )

        assertFalse(result.allowed)
        assertFalse(result.triangleMeshCandidateReady)
        assertTrue(result.errors.contains("repaired_surface_ratio_high"))
        assertTrue(result.errors.contains("mesh_vertex_provenance_rejected"))
    }

    private fun writeMeshTopology(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        topologyReady: Boolean,
        repairedSurfaceRatio: Float = 0f
    ) {
        workspaceDir.resolve("mesh").mkdirs()
        workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("topology_reconstruction_ready", topologyReady)
                .put("metric_mesh_ready", false)
                .put("printability_validation_ready", false)
                .put("source_point_count", 18)
                .put("measured_high_ratio", 1.0)
                .put("measured_surface_ratio", 0.80)
                .put("repaired_surface_ratio", repairedSurfaceRatio.toDouble())
                .put("coverage_cell_count", 6)
                .toString(2)
        )
    }

    private fun writeDensePointCloud(
        workspaceDir: File,
        pointCount: Int,
        visualOnlyLastPoint: Boolean = false
    ) {
        workspaceDir.resolve("dense").mkdirs()
        workspaceDir.resolve("surface").mkdirs()
        workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("metric", true)
                .put("printable", false)
                .put("slicer_handoff_allowed", false)
                .put(
                    "coverage_cells",
                    JSONArray().apply {
                        repeat(6) { index ->
                            put(
                                JSONObject()
                                    .put("cell_id", "cell_$index")
                                    .put("column", index % 3)
                                    .put("row", index / 3)
                                    .put("point_count", 3)
                                    .put("measured_high_count", 3)
                                    .put("provenance_class", "measured_high")
                            )
                        }
                    }
                )
                .put(
                    "candidate_points",
                    JSONArray().apply {
                        repeat(pointCount) { index ->
                            val x = (index % 6).toDouble() * 2.0
                            val y = (index / 6).toDouble() * 2.0
                            val z = if (index % 2 == 0) 500.0 else 501.0
                            put(
                                JSONObject()
                                    .put("point_id", "dense_${index.toString().padStart(6, '0')}")
                                    .put("source_point_id", "point_$index")
                                    .put("source_track_id", "track_$index")
                                    .put("contributing_frames", JSONArray(listOf("000001", "000002", "000003")))
                                    .put("xyz_mm", JSONObject().put("x", x).put("y", y).put("z", z))
                                    .put(
                                        "provenance_class",
                                        if (visualOnlyLastPoint && index == pointCount - 1) "visual_only" else "measured_high"
                                    )
                            )
                        }
                    }
                )
                .toString(2)
        )
        workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("dense_point_cloud_ready", true)
                .put("surface_reconstruction_ready", true)
                .put("point_count", pointCount)
                .put("contributing_frame_count", 4)
                .put("measured_high_ratio", 1.0)
                .put("mean_nearest_neighbor_mm", 2.0)
                .put(
                    "bounding_box_mm",
                    JSONObject()
                        .put("min", JSONObject().put("x", 0.0).put("y", 0.0).put("z", 500.0))
                        .put("max", JSONObject().put("x", 40.0).put("y", 40.0).put("z", 505.0))
                        .put("extent", JSONObject().put("x", 40.0).put("y", 40.0).put("z", 5.0))
                )
                .put(
                    "points",
                    JSONArray().apply {
                        repeat(pointCount) { index ->
                            val x = (index % 6).toDouble() * 2.0
                            val y = (index / 6).toDouble() * 2.0
                            val z = if (index % 2 == 0) 500.0 else 501.0
                            put(
                                JSONObject()
                                    .put("point_id", "dense_${index.toString().padStart(6, '0')}")
                                    .put("source_point_id", "point_$index")
                                    .put("source_track_id", "track_$index")
                                    .put("contributing_frames", JSONArray(listOf("000001", "000002", "000003")))
                                    .put("xyz_mm", JSONObject().put("x", x).put("y", y).put("z", z))
                                    .put("reprojection_residual_px", 0.4)
                                    .put(
                                        "provenance_class",
                                        if (visualOnlyLastPoint && index == pointCount - 1) "visual_only" else "measured_high"
                                    )
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
