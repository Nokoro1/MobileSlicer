package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_PRINTABILITY_REPORT_PATH = "reports/printability_report.json"

internal data class ScannerPrintabilityReportLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val maxBoundaryEdgeCount: Int = 0,
    val maxNonManifoldEdgeCount: Int = 0,
    val maxDegenerateTriangleCount: Int = 0,
    val maxRepairedSurfaceRatio: Float = 0.0f,
    val maxMissingVertexReferenceCount: Int = 0,
    val maxDuplicateVertexIdCount: Int = 0,
    val maxDuplicateTriangleCount: Int = 0,
    val maxDuplicateTriangleVertexCount: Int = 0,
    val maxDisconnectedComponentCount: Int = 1,
    val maxUnreferencedVertexCount: Int = 0,
    val maxInconsistentNormalEdgeCount: Int = 0,
    val maxVisualOnlyElementCount: Int = 0,
    val minScaleConfidence: Float = 0.80f,
    val requireScaleConfidenceForRecommendation: Boolean = false
)

internal data class ScannerPrintabilityReportResult(
    val allowed: Boolean,
    val metric: Boolean,
    val printabilityReportReady: Boolean,
    val recommendedForPrinting: Boolean,
    val slicerLoadValidationReady: Boolean,
    val exportReady: Boolean,
    val workspaceHandoffReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val vertexCount: Int,
    val triangleCount: Int,
    val watertight: Boolean,
    val manifold: Boolean,
    val boundaryEdgeCount: Int,
    val nonManifoldEdgeCount: Int,
    val degenerateTriangleCount: Int,
    val missingVertexReferenceCount: Int,
    val duplicateVertexIdCount: Int,
    val duplicateTriangleCount: Int,
    val duplicateTriangleVertexCount: Int,
    val disconnectedComponentCount: Int,
    val unreferencedVertexCount: Int,
    val inconsistentNormalEdgeCount: Int,
    val visualOnlyElementCount: Int,
    val repairApplied: Boolean,
    val repairedHoleCount: Int,
    val largestRepairedHoleMm: Float?,
    val repairedSurfaceRatio: Float,
    val scaleConfidence: Float?
)

internal fun buildScannerPrintabilityReport(
    workspaceDir: File,
    limits: ScannerPrintabilityReportLimits = ScannerPrintabilityReportLimits()
): ScannerPrintabilityReportResult {
    val validation = workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val repairReport = workspaceDir.resolve(SCANNER_REPAIR_REPORT_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (validation == null) {
        val result = blockedScannerPrintabilityReport(listOf("mesh_validation_missing"))
        writeScannerPrintabilityReport(workspaceDir, result, limits)
        return result
    }
    if (repairReport == null) {
        val result = blockedScannerPrintabilityReport(listOf("repair_report_missing"))
        writeScannerPrintabilityReport(workspaceDir, result, limits)
        return result
    }

    val vertexCount = validation.optInt("vertex_count", 0)
    val triangleCount = validation.optInt("triangle_count", 0)
    val boundaryEdges = validation.optInt("boundary_edge_count", Int.MAX_VALUE)
    val nonManifoldEdges = validation.optInt("non_manifold_edge_count", Int.MAX_VALUE)
    val degenerateTriangles = validation.optInt("degenerate_triangle_count", Int.MAX_VALUE)
    val missingVertexReferences = validation.optInt("missing_vertex_reference_count", Int.MAX_VALUE)
    val duplicateVertexIds = validation.optInt("duplicate_vertex_id_count", Int.MAX_VALUE)
    val duplicateTriangles = validation.optInt("duplicate_triangle_count", Int.MAX_VALUE)
    val duplicateTriangleVertices = validation.optInt("duplicate_triangle_vertex_count", Int.MAX_VALUE)
    val disconnectedComponents = validation.optInt("disconnected_component_count", Int.MAX_VALUE)
    val unreferencedVertices = validation.optInt("unreferenced_vertex_count", Int.MAX_VALUE)
    val inconsistentNormalEdges = validation.optInt("inconsistent_normal_edge_count", Int.MAX_VALUE)
    val visualOnlyElements = validation.optInt("visual_only_element_count", Int.MAX_VALUE)
    val repairApplied = repairReport.optBoolean("repair_applied", false)
    val repairedHoleCount = repairReport.optInt("repaired_hole_count", 0)
    val largestRepairedHoleMm = repairReport
        .takeUnless { it.isNull("largest_repaired_hole_mm") }
        ?.optDouble("largest_repaired_hole_mm")
        ?.toFloat()
    val repairedSurfaceRatio = repairReport.optDouble("repaired_surface_ratio", 1.0).toFloat()
    val scaleConfidence = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
        ?.optJSONObject("calibration")
        ?.takeUnless { it.isNull("scale_confidence") }
        ?.optDouble("scale_confidence")
        ?.toFloat()
    val watertight = boundaryEdges == 0
    val manifold = nonManifoldEdges == 0
    val errors = buildList {
        if (!validation.optBoolean("allowed", false)) add("mesh_validation_blocked")
        if (!validation.optBoolean("metric", false)) add("metric_mesh_validation_missing")
        if (!validation.optBoolean("metric_mesh_ready", false)) add("metric_mesh_not_ready")
        if (!validation.optBoolean("printability_validation_ready", false)) add("printability_validation_not_ready")
        if (!repairReport.optBoolean("allowed", false)) add("repair_report_blocked")
        if (!repairReport.optBoolean("metric", false)) add("metric_repair_report_missing")
        if (!repairReport.optBoolean("repair_report_ready", false)) add("repair_report_not_ready")
        if (repairApplied) add("repair_applied_without_printable_repair_policy")
        if (vertexCount < limits.minVertexCount) add("printability_vertex_count_low")
        if (triangleCount < limits.minTriangleCount) add("printability_triangle_count_low")
        if (boundaryEdges > limits.maxBoundaryEdgeCount) add("printability_boundary_edges_present")
        if (nonManifoldEdges > limits.maxNonManifoldEdgeCount) add("printability_non_manifold_edges_present")
        if (degenerateTriangles > limits.maxDegenerateTriangleCount) add("printability_degenerate_triangles_present")
        if (missingVertexReferences > limits.maxMissingVertexReferenceCount) add("printability_missing_vertex_references_present")
        if (duplicateVertexIds > limits.maxDuplicateVertexIdCount) add("printability_duplicate_vertex_ids_present")
        if (duplicateTriangles > limits.maxDuplicateTriangleCount) add("printability_duplicate_triangles_present")
        if (duplicateTriangleVertices > limits.maxDuplicateTriangleVertexCount) {
            add("printability_duplicate_triangle_vertices_present")
        }
        if (disconnectedComponents > limits.maxDisconnectedComponentCount) add("printability_disconnected_components_present")
        if (unreferencedVertices > limits.maxUnreferencedVertexCount) add("printability_unreferenced_vertices_present")
        if (inconsistentNormalEdges > limits.maxInconsistentNormalEdgeCount) add("printability_inconsistent_normals_present")
        if (visualOnlyElements > limits.maxVisualOnlyElementCount) add("printability_visual_or_rejected_geometry_present")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatio) add("printability_repaired_surface_ratio_high")
        if (
            limits.requireScaleConfidenceForRecommendation &&
            (scaleConfidence == null || scaleConfidence < limits.minScaleConfidence)
        ) {
            add("printability_scale_confidence_low")
        }
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerPrintabilityReportResult(
        allowed = accepted,
        metric = accepted,
        printabilityReportReady = accepted,
        recommendedForPrinting = accepted,
        slicerLoadValidationReady = false,
        exportReady = false,
        workspaceHandoffReady = false,
        status = if (accepted) "printability_report_ready_slicer_load_blocked" else "printability_report_blocked",
        errors = if (accepted) {
            listOf("slicer_load_validation_required_before_export")
        } else {
            errors + "export_blocked_until_printability_report"
        },
        warnings = buildList {
            if (scaleConfidence == null) add("scale_confidence_not_available_in_workspace_manifest")
            if (scaleConfidence != null && scaleConfidence < limits.minScaleConfidence) add("scale_confidence_below_printable_target")
            if (!limits.requireScaleConfidenceForRecommendation) add("scale_confidence_reported_but_not_blocking_current_dev_build")
            if (accepted) add("printability_report_does_not_replace_slicer_load_validation")
            add("stl_3mf_export_remains_blocked")
            add("workspace_handoff_remains_blocked")
        },
        vertexCount = if (accepted) vertexCount else 0,
        triangleCount = if (accepted) triangleCount else 0,
        watertight = watertight,
        manifold = manifold,
        boundaryEdgeCount = boundaryEdges,
        nonManifoldEdgeCount = nonManifoldEdges,
        degenerateTriangleCount = degenerateTriangles,
        missingVertexReferenceCount = missingVertexReferences,
        duplicateVertexIdCount = duplicateVertexIds,
        duplicateTriangleCount = duplicateTriangles,
        duplicateTriangleVertexCount = duplicateTriangleVertices,
        disconnectedComponentCount = disconnectedComponents,
        unreferencedVertexCount = unreferencedVertices,
        inconsistentNormalEdgeCount = inconsistentNormalEdges,
        visualOnlyElementCount = visualOnlyElements,
        repairApplied = repairApplied,
        repairedHoleCount = repairedHoleCount,
        largestRepairedHoleMm = largestRepairedHoleMm,
        repairedSurfaceRatio = repairedSurfaceRatio,
        scaleConfidence = scaleConfidence
    )
    writeScannerPrintabilityReport(workspaceDir, result, limits)
    return result
}

private fun blockedScannerPrintabilityReport(errors: List<String>): ScannerPrintabilityReportResult =
    ScannerPrintabilityReportResult(
        allowed = false,
        metric = false,
        printabilityReportReady = false,
        recommendedForPrinting = false,
        slicerLoadValidationReady = false,
        exportReady = false,
        workspaceHandoffReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        vertexCount = 0,
        triangleCount = 0,
        watertight = false,
        manifold = false,
        boundaryEdgeCount = 0,
        nonManifoldEdgeCount = 0,
        degenerateTriangleCount = 0,
        missingVertexReferenceCount = 0,
        duplicateVertexIdCount = 0,
        duplicateTriangleCount = 0,
        duplicateTriangleVertexCount = 0,
        disconnectedComponentCount = 0,
        unreferencedVertexCount = 0,
        inconsistentNormalEdgeCount = 0,
        visualOnlyElementCount = 0,
        repairApplied = false,
        repairedHoleCount = 0,
        largestRepairedHoleMm = null,
        repairedSurfaceRatio = 0f,
        scaleConfidence = null
    )

private fun writeScannerPrintabilityReport(
    workspaceDir: File,
    result: ScannerPrintabilityReportResult,
    limits: ScannerPrintabilityReportLimits
) {
    workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerPrintabilityReportJson(result, limits).toString(2))
    }
}

internal fun scannerPrintabilityReportJson(
    result: ScannerPrintabilityReportResult,
    limits: ScannerPrintabilityReportLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("printability_report_ready", result.printabilityReportReady)
        .put("recommended_for_printing", result.recommendedForPrinting)
        .put("slicer_load_validation_ready", result.slicerLoadValidationReady)
        .put("export_ready", result.exportReady)
        .put("workspace_handoff_ready", result.workspaceHandoffReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put(
            "topology",
            JSONObject()
                .put("watertight", result.watertight)
                .put("manifold", result.manifold)
                .put("boundary_edge_count", result.boundaryEdgeCount)
                .put("non_manifold_edge_count", result.nonManifoldEdgeCount)
                .put("degenerate_triangle_count", result.degenerateTriangleCount)
                .put("duplicate_triangle_count", result.duplicateTriangleCount)
                .put("disconnected_component_count", result.disconnectedComponentCount)
                .put("inconsistent_normal_edge_count", result.inconsistentNormalEdgeCount)
        )
        .put(
            "mesh",
            JSONObject()
                .put("vertex_count", result.vertexCount)
                .put("triangle_count", result.triangleCount)
                .put("degenerate_triangle_count", result.degenerateTriangleCount)
                .put("missing_vertex_reference_count", result.missingVertexReferenceCount)
                .put("duplicate_vertex_id_count", result.duplicateVertexIdCount)
                .put("duplicate_triangle_vertex_count", result.duplicateTriangleVertexCount)
                .put("unreferenced_vertex_count", result.unreferencedVertexCount)
                .put("visual_only_element_count", result.visualOnlyElementCount)
        )
        .put(
            "repair",
            JSONObject()
                .put("repair_applied", result.repairApplied)
                .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
                .put("repaired_hole_count", result.repairedHoleCount)
                .put("largest_repaired_hole_mm", result.largestRepairedHoleMm ?: JSONObject.NULL)
                .put("metric_repairs_allowed_without_policy", false)
        )
        .put(
            "evidence",
            JSONObject()
                .put("source_validation", SCANNER_MESH_VALIDATION_PATH)
                .put("source_repair_report", SCANNER_REPAIR_REPORT_PATH)
                .put("measured_metric_mesh_required", true)
                .put("visual_or_rejected_geometry_allowed", false)
                .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        )
        .put(
            "scale",
            JSONObject()
                .put("scale_confidence", result.scaleConfidence ?: JSONObject.NULL)
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("scale_confidence_required_for_current_gate", limits.requireScaleConfidenceForRecommendation)
                .put("scale_validation_required_for_export", true)
        )
        .put(
            "slicer_readiness",
            JSONObject()
                .put("slicer_load_validation_ready", result.slicerLoadValidationReady)
                .put("export_ready", result.exportReady)
                .put("workspace_handoff_ready", result.workspaceHandoffReady)
                .put("required_next_gate", "slicer_load_validation")
        )
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("max_boundary_edge_count", limits.maxBoundaryEdgeCount)
                .put("max_non_manifold_edge_count", limits.maxNonManifoldEdgeCount)
                .put("max_degenerate_triangle_count", limits.maxDegenerateTriangleCount)
                .put("max_repaired_surface_ratio", limits.maxRepairedSurfaceRatio.toDouble())
                .put("max_missing_vertex_reference_count", limits.maxMissingVertexReferenceCount)
                .put("max_duplicate_vertex_id_count", limits.maxDuplicateVertexIdCount)
                .put("max_duplicate_triangle_count", limits.maxDuplicateTriangleCount)
                .put("max_duplicate_triangle_vertex_count", limits.maxDuplicateTriangleVertexCount)
                .put("max_disconnected_component_count", limits.maxDisconnectedComponentCount)
                .put("max_unreferenced_vertex_count", limits.maxUnreferencedVertexCount)
                .put("max_inconsistent_normal_edge_count", limits.maxInconsistentNormalEdgeCount)
                .put("max_visual_only_element_count", limits.maxVisualOnlyElementCount)
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("require_scale_confidence_for_recommendation", limits.requireScaleConfidenceForRecommendation)
        )
        .put(
            "handoff_contract",
            JSONObject()
                .put("stl_export_allowed", false)
                .put("three_mf_export_allowed", false)
                .put("workspace_handoff_allowed", false)
                .put("required_next_gate", "slicer_load_validation")
        )
