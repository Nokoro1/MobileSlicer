package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSlicerLoadValidationTest {
    @Test
    fun slicerLoadBlocksWhenPrintabilityReportIsMissing() {
        val workspaceDir = tempDir("scanner-slicer-missing-report")

        val result = validateScannerSlicerLoad(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.slicerLoadValidated)
        assertFalse(result.exportReady)
        assertFalse(result.workspaceHandoffReady)
        assertTrue(result.errors.contains("printability_report_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH).isFile)
    }

    @Test
    fun slicerLoadBlocksWhenMetricCandidateIsMissing() {
        val workspaceDir = tempDir("scanner-slicer-missing-candidate")
        writePrintabilityReport(workspaceDir, allowed = true)

        val result = validateScannerSlicerLoad(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("metric_mesh_candidate_missing"))
    }

    @Test
    fun slicerLoadBlocksFailedPrintabilityReport() {
        val workspaceDir = tempDir("scanner-slicer-report-blocked")
        writePrintabilityReport(workspaceDir, allowed = false)
        writeMetricMeshCandidate(workspaceDir)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("printability_report_blocked"))
        assertTrue(result.errors.contains("printability_report_not_ready"))
        assertTrue(result.errors.contains("scan_not_recommended_for_printing"))
        assertTrue(result.errors.contains("slicer_boundary_edges_present"))
        assertTrue(result.errors.contains("slicer_non_manifold_edges_present"))
        assertTrue(result.errors.contains("slicer_degenerate_triangles_present"))
        assertTrue(result.errors.contains("slicer_disconnected_components_present"))
        assertTrue(result.errors.contains("slicer_inconsistent_normals_present"))
        assertTrue(result.errors.contains("slicer_repair_applied_without_import_policy"))
        assertTrue(result.errors.contains("export_blocked_until_slicer_load_validation"))
    }

    @Test
    fun slicerLoadBlocksCountMismatch() {
        val workspaceDir = tempDir("scanner-slicer-count-mismatch")
        writePrintabilityReport(workspaceDir, allowed = true, vertexCount = 4, triangleCount = 4)
        writeMetricMeshCandidate(workspaceDir, vertexCount = 5, triangleCount = 4)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("vertex_count_mismatch"))
        assertTrue(result.errors.contains("internal_slicer_import_contract_not_ready"))
    }

    @Test
    fun slicerLoadBlocksVisualOnlyGeometry() {
        val workspaceDir = tempDir("scanner-slicer-visual")
        writePrintabilityReport(workspaceDir, allowed = true)
        writeMetricMeshCandidate(workspaceDir, visualOnly = true)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("slicer_visual_or_rejected_geometry_present"))
    }

    @Test
    fun slicerLoadBlocksInvalidCandidateTriangleReferences() {
        val workspaceDir = tempDir("scanner-slicer-invalid-refs")
        writePrintabilityReport(workspaceDir, allowed = true)
        writeMetricMeshCandidate(workspaceDir, missingReference = true, duplicateTriangleVertices = true)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.missingTriangleReferenceCount > 0)
        assertTrue(result.duplicateTriangleVertexCount > 0)
        assertTrue(result.errors.contains("slicer_missing_triangle_vertex_references_present"))
        assertTrue(result.errors.contains("slicer_duplicate_triangle_vertices_present"))
    }

    @Test
    fun slicerLoadBlocksPrematurePrintabilityExportFlags() {
        val workspaceDir = tempDir("scanner-slicer-premature-flags")
        writePrintabilityReport(workspaceDir, allowed = true, exportReady = true, workspaceHandoffReady = true)
        writeMetricMeshCandidate(workspaceDir)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("printability_report_export_ready_before_slicer_validation"))
        assertTrue(result.errors.contains("printability_report_handoff_ready_before_slicer_validation"))
    }

    @Test
    fun slicerLoadValidatesInternalContractButKeepsExportBlocked() {
        val workspaceDir = tempDir("scanner-slicer-ready")
        writePrintabilityReport(workspaceDir, allowed = true)
        writeMetricMeshCandidate(workspaceDir)

        val result = validateScannerSlicerLoad(
            workspaceDir,
            ScannerSlicerLoadValidationLimits(minVertexCount = 4, minTriangleCount = 4, minScaleConfidence = 0.8f)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.slicerLoadValidated)
        assertTrue(result.internalImportContractReady)
        assertFalse(result.exportReady)
        assertFalse(result.workspaceHandoffReady)
        assertEquals(0, result.boundaryEdgeCount)
        assertEquals(0, result.nonManifoldEdgeCount)
        assertEquals(0, result.degenerateTriangleCount)
        assertEquals(1, result.disconnectedComponentCount)
        assertFalse(result.repairApplied)
        assertEquals(0, result.candidateVisualOrRejectedElementCount)
        assertTrue(result.errors.contains("export_writer_required_before_stl_3mf_handoff"))
        assertTrue(result.warnings.contains("stl_3mf_export_remains_blocked"))
        assertTrue(result.warnings.contains("workspace_handoff_remains_blocked"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("slicer_load_validated"))
        assertFalse(json.getBoolean("export_ready"))
        assertFalse(json.getBoolean("workspace_handoff_ready"))
        assertEquals(0, json.getInt("boundary_edge_count"))
        assertEquals(0, json.getJSONObject("candidate_contract").getInt("missing_triangle_reference_count"))
        assertTrue(json.getJSONObject("printability_contract").getBoolean("topology_checked"))
        assertFalse(json.getJSONObject("validated_contract").getBoolean("stl_export_allowed"))
    }

    private fun writePrintabilityReport(
        workspaceDir: File,
        allowed: Boolean,
        vertexCount: Int = 4,
        triangleCount: Int = 4,
        exportReady: Boolean = false,
        workspaceHandoffReady: Boolean = false
    ) {
        workspaceDir.resolve("reports").mkdirs()
        workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("printability_report_ready", allowed)
                .put("recommended_for_printing", allowed)
                .put("slicer_load_validation_ready", false)
                .put("export_ready", exportReady)
                .put("workspace_handoff_ready", workspaceHandoffReady)
                .put(
                    "topology",
                    JSONObject()
                        .put("watertight", allowed)
                        .put("manifold", allowed)
                        .put("boundary_edge_count", if (allowed) 0 else 4)
                        .put("non_manifold_edge_count", if (allowed) 0 else 2)
                        .put("degenerate_triangle_count", if (allowed) 0 else 1)
                        .put("disconnected_component_count", if (allowed) 1 else 2)
                        .put("inconsistent_normal_edge_count", if (allowed) 0 else 1)
                )
                .put("mesh", JSONObject().put("vertex_count", vertexCount).put("triangle_count", triangleCount))
                .put(
                    "repair",
                    JSONObject()
                        .put("repair_applied", !allowed)
                        .put("repaired_surface_ratio", if (allowed) 0.0 else 0.08)
                )
                .put("scale", JSONObject().put("scale_confidence", 0.91).put("scale_confidence_required_for_current_gate", false))
                .put(
                    "slicer_readiness",
                    JSONObject()
                        .put("slicer_load_validation_ready", false)
                        .put("export_ready", exportReady)
                        .put("workspace_handoff_ready", workspaceHandoffReady)
                )
                .toString(2)
        )
    }

    private fun writeMetricMeshCandidate(
        workspaceDir: File,
        vertexCount: Int = 4,
        triangleCount: Int = 4,
        visualOnly: Boolean = false,
        missingReference: Boolean = false,
        duplicateTriangleVertices: Boolean = false
    ) {
        workspaceDir.resolve("mesh").mkdirs()
        workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("triangle_mesh_candidate_ready", true)
                .put("metric_mesh_ready", false)
                .put("printability_validation_ready", false)
                .put("vertex_count", vertexCount)
                .put("triangle_count", triangleCount)
                .put("vertices", JSONArray().apply {
                    repeat(vertexCount) { index ->
                        put(
                            JSONObject()
                                .put("vertex_id", "v$index")
                                .put("source_point_id", "p$index")
                                .put("source_track_id", "t$index")
                                .put("xyz_mm", JSONObject().put("x", index).put("y", index).put("z", index))
                                .put("provenance_class", if (visualOnly && index == vertexCount - 1) "visual_only" else "measured_high")
                        )
                    }
                })
	                .put("triangles", JSONArray().apply {
	                    repeat(triangleCount) { index ->
                            val refs = when {
                                missingReference && index == 0 -> listOf("v0", "v1", "missing")
                                duplicateTriangleVertices && index == 1 -> listOf("v0", "v0", "v1")
                                else -> listOf("v0", "v1", "v2")
                            }
	                        put(
	                            JSONObject()
	                                .put("triangle_id", "tri$index")
	                                .put("vertex_ids", JSONArray(refs))
	                                .put("provenance_class", "measured_high")
	                        )
	                    }
                })
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
