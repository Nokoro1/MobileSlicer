package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMeshValidationTest {
    @Test
    fun validationBlocksWhenCandidateIsMissing() {
        val workspaceDir = tempDir("scanner-mesh-validation-missing")

        val result = validateScannerMeshCandidate(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.topologyValidated)
        assertFalse(result.metricMeshReady)
        assertFalse(result.printabilityValidationReady)
        assertTrue(result.errors.contains("metric_mesh_candidate_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).isFile)
    }

    @Test
    fun validationBlocksOpenCandidateWithBoundaryEdges() {
        val workspaceDir = tempDir("scanner-mesh-validation-open")
        writeMeshCandidate(workspaceDir, closed = false)

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 2)
        )

        assertFalse(result.allowed)
        assertFalse(result.topologyValidated)
        assertTrue(result.boundaryEdgeCount > 0)
        assertTrue(result.errors.contains("mesh_boundary_edges_present"))
        assertTrue(result.errors.contains("printability_blocked_until_mesh_validation"))
    }

    @Test
    fun validationAcceptsClosedMeasuredCandidateButKeepsExportBlocked() {
        val workspaceDir = tempDir("scanner-mesh-validation-closed")
        writeMeshCandidate(workspaceDir, closed = true)

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.topologyValidated)
        assertTrue(result.metricMeshReady)
        assertTrue(result.printabilityValidationReady)
        assertEquals("metric_mesh_topology_validated", result.status)
        assertEquals(0, result.boundaryEdgeCount)
        assertEquals(0, result.nonManifoldEdgeCount)
        assertEquals(0, result.degenerateTriangleCount)
        assertEquals(0, result.missingVertexReferenceCount)
        assertEquals(0, result.duplicateTriangleCount)
        assertEquals(1, result.disconnectedComponentCount)
        assertEquals(0, result.inconsistentNormalEdgeCount)
        assertTrue(result.errors.contains("printability_report_blocked_until_slicer_validation"))
        assertTrue(result.warnings.contains("stl_3mf_export_remains_blocked"))
        assertTrue(result.warnings.contains("workspace_handoff_remains_blocked"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertTrue(json.getBoolean("topology_validated"))
        assertTrue(json.getBoolean("metric_mesh_ready"))
        assertTrue(json.getBoolean("printability_validation_ready"))
        assertEquals(1, json.getInt("disconnected_component_count"))
        assertEquals(0, json.getInt("inconsistent_normal_edge_count"))
    }

    @Test
    fun validationBlocksVisualOnlyProvenance() {
        val workspaceDir = tempDir("scanner-mesh-validation-visual")
        writeMeshCandidate(workspaceDir, closed = true, visualOnlyTriangle = true)

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("mesh_visual_or_rejected_provenance_present"))
    }

    @Test
    fun validationBlocksMissingVertexReferencesAndDuplicateTriangleVertices() {
        val workspaceDir = tempDir("scanner-mesh-validation-missing-refs")
        writeMeshCandidate(
            workspaceDir = workspaceDir,
            closed = true,
            extraTriangles = listOf(
                triangle("missing_ref", listOf("v0", "v1", "missing")),
                triangle("duplicate_refs", listOf("v0", "v0", "v1"))
            )
        )

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.missingVertexReferenceCount > 0)
        assertTrue(result.duplicateTriangleVertexCount > 0)
        assertTrue(result.errors.contains("mesh_missing_vertex_references_present"))
        assertTrue(result.errors.contains("mesh_duplicate_triangle_vertices_present"))
        assertTrue(result.errors.contains("mesh_degenerate_triangles_present"))
    }

    @Test
    fun validationBlocksDuplicateTriangles() {
        val workspaceDir = tempDir("scanner-mesh-validation-duplicate-tris")
        writeMeshCandidate(
            workspaceDir = workspaceDir,
            closed = true,
            extraTriangles = listOf(triangle("duplicate_t0", listOf("v0", "v2", "v1")))
        )

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.duplicateTriangleCount > 0)
        assertTrue(result.nonManifoldEdgeCount > 0)
        assertTrue(result.errors.contains("mesh_duplicate_triangles_present"))
        assertTrue(result.errors.contains("mesh_non_manifold_edges_present"))
    }

    @Test
    fun validationBlocksDisconnectedComponents() {
        val workspaceDir = tempDir("scanner-mesh-validation-components")
        writeMeshCandidate(workspaceDir, closed = true, includeSecondClosedTetra = true)

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 8, minTriangleCount = 8)
        )

        assertFalse(result.allowed)
        assertEquals(2, result.disconnectedComponentCount)
        assertTrue(result.errors.contains("mesh_disconnected_components_present"))
    }

    @Test
    fun validationBlocksInconsistentNormals() {
        val workspaceDir = tempDir("scanner-mesh-validation-normals")
        writeMeshCandidate(workspaceDir, closed = true, flipFirstTriangle = true)

        val result = validateScannerMeshCandidate(
            workspaceDir,
            ScannerMeshValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.inconsistentNormalEdgeCount > 0)
        assertTrue(result.errors.contains("mesh_inconsistent_normals_present"))
    }

    private fun writeMeshCandidate(
        workspaceDir: File,
        closed: Boolean,
        visualOnlyTriangle: Boolean = false,
        extraTriangles: List<JSONObject> = emptyList(),
        includeSecondClosedTetra: Boolean = false,
        flipFirstTriangle: Boolean = false
    ) {
        workspaceDir.resolve("mesh").mkdirs()
        val vertices = JSONArray()
            .put(vertex("v0", 0.0, 0.0, 0.0))
            .put(vertex("v1", 10.0, 0.0, 0.0))
            .put(vertex("v2", 0.0, 10.0, 0.0))
            .put(vertex("v3", 0.0, 0.0, 10.0))
        if (includeSecondClosedTetra) {
            vertices
                .put(vertex("v4", 40.0, 0.0, 0.0))
                .put(vertex("v5", 50.0, 0.0, 0.0))
                .put(vertex("v6", 40.0, 10.0, 0.0))
                .put(vertex("v7", 40.0, 0.0, 10.0))
        }
        val triangles = if (closed) {
            JSONArray()
                .put(
                    triangle(
                        "t0",
                        if (flipFirstTriangle) listOf("v0", "v1", "v2") else listOf("v0", "v2", "v1"),
                        if (visualOnlyTriangle) "visual_only" else "measured_high"
                    )
                )
                .put(triangle("t1", listOf("v0", "v1", "v3")))
                .put(triangle("t2", listOf("v1", "v2", "v3")))
                .put(triangle("t3", listOf("v0", "v3", "v2")))
        } else {
            JSONArray()
                .put(triangle("t0", listOf("v0", "v1", "v2")))
                .put(triangle("t1", listOf("v0", "v1", "v3")))
        }
        if (includeSecondClosedTetra) {
            triangles
                .put(triangle("t4", listOf("v4", "v6", "v5")))
                .put(triangle("t5", listOf("v4", "v5", "v7")))
                .put(triangle("t6", listOf("v5", "v6", "v7")))
                .put(triangle("t7", listOf("v4", "v7", "v6")))
        }
        extraTriangles.forEach { triangles.put(it) }
        workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("triangle_mesh_candidate_ready", true)
                .put("metric_mesh_ready", false)
                .put("printability_validation_ready", false)
                .put("repaired_surface_ratio", 0.0)
                .put("vertices", vertices)
                .put("triangles", triangles)
                .toString(2)
        )
    }

    private fun vertex(id: String, x: Double, y: Double, z: Double): JSONObject =
        JSONObject()
            .put("vertex_id", id)
            .put("source_point_id", "point_$id")
            .put("source_track_id", "track_$id")
            .put("xyz_mm", JSONObject().put("x", x).put("y", y).put("z", z))
            .put("provenance_class", "measured_high")

    private fun triangle(
        id: String,
        vertices: List<String>,
        provenanceClass: String = "measured_high"
    ): JSONObject =
        JSONObject()
            .put("triangle_id", id)
            .put("vertex_ids", JSONArray(vertices))
            .put("provenance_class", provenanceClass)
            .put("max_edge_mm", 14.2)
            .put("area_mm2", 50.0)

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
