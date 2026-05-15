package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPrintabilityReportTest {
    @Test
    fun reportBlocksWhenMeshValidationIsMissing() {
        val workspaceDir = tempDir("scanner-printability-missing")

        val result = buildScannerPrintabilityReport(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.printabilityReportReady)
        assertFalse(result.recommendedForPrinting)
        assertFalse(result.exportReady)
        assertTrue(result.errors.contains("mesh_validation_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH).isFile)
    }

    @Test
    fun reportBlocksFailedMeshValidation() {
        val workspaceDir = tempDir("scanner-printability-blocked")
        writeMeshValidation(workspaceDir, allowed = false, metricReady = false, boundaryEdges = 4)
        writeRepairReport(workspaceDir, allowed = false)

        val result = buildScannerPrintabilityReport(
            workspaceDir,
            ScannerPrintabilityReportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertFalse(result.printabilityReportReady)
        assertTrue(result.errors.contains("mesh_validation_blocked"))
        assertTrue(result.errors.contains("metric_mesh_not_ready"))
        assertTrue(result.errors.contains("printability_boundary_edges_present"))
        assertTrue(result.errors.contains("printability_missing_vertex_references_present"))
        assertTrue(result.errors.contains("printability_duplicate_vertex_ids_present"))
        assertTrue(result.errors.contains("printability_duplicate_triangles_present"))
        assertTrue(result.errors.contains("printability_duplicate_triangle_vertices_present"))
        assertTrue(result.errors.contains("printability_disconnected_components_present"))
        assertTrue(result.errors.contains("printability_unreferenced_vertices_present"))
        assertTrue(result.errors.contains("printability_inconsistent_normals_present"))
        assertTrue(result.errors.contains("printability_visual_or_rejected_geometry_present"))
        assertTrue(result.errors.contains("export_blocked_until_printability_report"))
    }

    @Test
    fun reportAcceptsValidatedMeshButKeepsExportBlocked() {
        val workspaceDir = tempDir("scanner-printability-ready")
        writeWorkspaceManifest(workspaceDir)
        writeMeshValidation(workspaceDir, allowed = true, metricReady = true, boundaryEdges = 0)
        writeRepairReport(workspaceDir, allowed = true)

        val result = buildScannerPrintabilityReport(
            workspaceDir,
            ScannerPrintabilityReportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.printabilityReportReady)
        assertTrue(result.recommendedForPrinting)
        assertFalse(result.slicerLoadValidationReady)
        assertFalse(result.exportReady)
        assertFalse(result.workspaceHandoffReady)
        assertTrue(result.watertight)
        assertTrue(result.manifold)
        assertEquals(0, result.boundaryEdgeCount)
        assertEquals(0, result.nonManifoldEdgeCount)
        assertEquals(1, result.disconnectedComponentCount)
        assertEquals(0, result.visualOnlyElementCount)
        assertFalse(result.repairApplied)
        assertTrue(result.errors.contains("slicer_load_validation_required_before_export"))
        assertTrue(result.warnings.contains("scale_confidence_reported_but_not_blocking_current_dev_build"))
        assertTrue(result.warnings.contains("stl_3mf_export_remains_blocked"))
        assertTrue(result.warnings.contains("workspace_handoff_remains_blocked"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("printability_report_ready"))
        assertFalse(json.getBoolean("export_ready"))
        assertFalse(json.getBoolean("workspace_handoff_ready"))
        assertEquals(0, json.getJSONObject("topology").getInt("boundary_edge_count"))
        assertEquals(1, json.getJSONObject("topology").getInt("disconnected_component_count"))
        assertEquals(0, json.getJSONObject("mesh").getInt("visual_only_element_count"))
        assertFalse(json.getJSONObject("repair").getBoolean("repair_applied"))
        assertEquals(SCANNER_MESH_VALIDATION_PATH, json.getJSONObject("evidence").getString("source_validation"))
        assertFalse(json.getJSONObject("slicer_readiness").getBoolean("export_ready"))
        assertFalse(json.getJSONObject("handoff_contract").getBoolean("stl_export_allowed"))
    }

    @Test
    fun reportCanRequireScaleConfidenceWhenGateIsEnabled() {
        val workspaceDir = tempDir("scanner-printability-scale-required")
        writeWorkspaceManifest(workspaceDir, scaleConfidence = 0.42)
        writeMeshValidation(workspaceDir, allowed = true, metricReady = true, boundaryEdges = 0)
        writeRepairReport(workspaceDir, allowed = true)

        val result = buildScannerPrintabilityReport(
            workspaceDir,
            ScannerPrintabilityReportLimits(
                minVertexCount = 4,
                minTriangleCount = 4,
                requireScaleConfidenceForRecommendation = true,
                minScaleConfidence = 0.80f
            )
        )

        assertFalse(result.allowed)
        assertFalse(result.recommendedForPrinting)
        assertTrue(result.errors.contains("printability_scale_confidence_low"))
        assertTrue(result.warnings.contains("scale_confidence_below_printable_target"))
    }

    @Test
    fun reportBlocksRepairWithoutPrintableRepairPolicy() {
        val workspaceDir = tempDir("scanner-printability-repair-policy")
        writeWorkspaceManifest(workspaceDir)
        writeMeshValidation(workspaceDir, allowed = true, metricReady = true, boundaryEdges = 0)
        writeRepairReport(workspaceDir, allowed = true, repairApplied = true, repairedSurfaceRatio = 0.03)

        val result = buildScannerPrintabilityReport(
            workspaceDir,
            ScannerPrintabilityReportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.repairApplied)
        assertEquals(1, result.repairedHoleCount)
        assertTrue(result.errors.contains("repair_applied_without_printable_repair_policy"))
        assertTrue(result.errors.contains("printability_repaired_surface_ratio_high"))
    }

    private fun writeMeshValidation(
        workspaceDir: File,
        allowed: Boolean,
        metricReady: Boolean,
        boundaryEdges: Int,
        nonManifoldEdges: Int = 0,
        degenerateTriangles: Int = 0,
        missingVertexReferences: Int = if (allowed) 0 else 1,
        duplicateVertexIds: Int = if (allowed) 0 else 1,
        duplicateTriangles: Int = if (allowed) 0 else 1,
        duplicateTriangleVertices: Int = if (allowed) 0 else 1,
        disconnectedComponents: Int = if (allowed) 1 else 2,
        unreferencedVertices: Int = if (allowed) 0 else 1,
        inconsistentNormalEdges: Int = if (allowed) 0 else 1,
        visualOnlyElements: Int = if (allowed) 0 else 1
    ) {
        workspaceDir.resolve("mesh").mkdirs()
        workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("topology_validated", allowed)
                .put("metric_mesh_ready", metricReady)
                .put("printability_validation_ready", metricReady)
                .put("vertex_count", 4)
                .put("triangle_count", 4)
                .put("unique_edge_count", 6)
                .put("boundary_edge_count", boundaryEdges)
                .put("non_manifold_edge_count", nonManifoldEdges)
                .put("degenerate_triangle_count", degenerateTriangles)
                .put("missing_vertex_reference_count", missingVertexReferences)
                .put("duplicate_vertex_id_count", duplicateVertexIds)
                .put("duplicate_triangle_count", duplicateTriangles)
                .put("duplicate_triangle_vertex_count", duplicateTriangleVertices)
                .put("disconnected_component_count", disconnectedComponents)
                .put("unreferenced_vertex_count", unreferencedVertices)
                .put("inconsistent_normal_edge_count", inconsistentNormalEdges)
                .put("visual_only_element_count", visualOnlyElements)
                .put("repaired_surface_ratio", 0.0)
                .toString(2)
        )
    }

    private fun writeRepairReport(
        workspaceDir: File,
        allowed: Boolean,
        repairApplied: Boolean = false,
        repairedSurfaceRatio: Double = 0.0
    ) {
        workspaceDir.resolve("reports").mkdirs()
        workspaceDir.resolve(SCANNER_REPAIR_REPORT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("repair_report_ready", allowed)
                .put("repair_applied", repairApplied)
                .put("repaired_surface_ratio", repairedSurfaceRatio)
                .put("repaired_hole_count", if (repairApplied) 1 else 0)
                .put("largest_repaired_hole_mm", if (repairApplied) 4.2 else JSONObject.NULL)
                .toString(2)
        )
    }

    private fun writeWorkspaceManifest(workspaceDir: File, scaleConfidence: Double = 0.91) {
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).also {
            it.parentFile?.mkdirs()
            it.writeText(
                JSONObject()
                    .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                    .put("calibration", JSONObject().put("scale_confidence", scaleConfidence))
                    .put("frames", JSONArray())
                    .toString(2)
            )
        }
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
