package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_SLICER_LOAD_VALIDATION_PATH = "reports/slicer_load_validation.json"

internal data class ScannerSlicerLoadValidationLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val minScaleConfidence: Float = 0.0f,
    val requireMillimeterUnits: Boolean = true,
    val maxBoundaryEdgeCount: Int = 0,
    val maxNonManifoldEdgeCount: Int = 0,
    val maxDegenerateTriangleCount: Int = 0,
    val maxDisconnectedComponentCount: Int = 1,
    val maxInconsistentNormalEdgeCount: Int = 0,
    val allowRepairApplied: Boolean = false,
    val requireScaleConfidenceWhenPrintabilityRequiresIt: Boolean = true
)

internal data class ScannerSlicerLoadValidationResult(
    val allowed: Boolean,
    val metric: Boolean,
    val slicerLoadValidated: Boolean,
    val exportReady: Boolean,
    val workspaceHandoffReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val vertexCount: Int,
    val triangleCount: Int,
    val units: String,
    val scaleConfidence: Float?,
    val boundaryEdgeCount: Int,
    val nonManifoldEdgeCount: Int,
    val degenerateTriangleCount: Int,
    val disconnectedComponentCount: Int,
    val inconsistentNormalEdgeCount: Int,
    val repairApplied: Boolean,
    val missingTriangleReferenceCount: Int,
    val duplicateTriangleVertexCount: Int,
    val candidateVisualOrRejectedElementCount: Int,
    val internalImportContractReady: Boolean
)

private data class SlicerCandidateIntegrity(
    val visualOrRejectedElementCount: Int,
    val missingTriangleReferenceCount: Int,
    val duplicateTriangleVertexCount: Int
)

internal fun validateScannerSlicerLoad(
    workspaceDir: File,
    limits: ScannerSlicerLoadValidationLimits = ScannerSlicerLoadValidationLimits()
): ScannerSlicerLoadValidationResult {
    val report = workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val candidate = workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (report == null) {
        val result = blockedScannerSlicerLoadValidation(listOf("printability_report_missing"))
        writeScannerSlicerLoadValidation(workspaceDir, result, limits)
        return result
    }
    if (candidate == null) {
        val result = blockedScannerSlicerLoadValidation(listOf("metric_mesh_candidate_missing"))
        writeScannerSlicerLoadValidation(workspaceDir, result, limits)
        return result
    }

    val reportMesh = report.optJSONObject("mesh") ?: JSONObject()
    val reportTopology = report.optJSONObject("topology") ?: JSONObject()
    val reportRepair = report.optJSONObject("repair") ?: JSONObject()
    val reportScale = report.optJSONObject("scale") ?: JSONObject()
    val reportSlicerReadiness = report.optJSONObject("slicer_readiness") ?: JSONObject()
    val reportVertexCount = reportMesh.optInt("vertex_count", 0)
    val reportTriangleCount = reportMesh.optInt("triangle_count", 0)
    val boundaryEdges = reportTopology.optInt("boundary_edge_count", Int.MAX_VALUE)
    val nonManifoldEdges = reportTopology.optInt("non_manifold_edge_count", Int.MAX_VALUE)
    val degenerateTriangles = reportTopology.optInt(
        "degenerate_triangle_count",
        reportMesh.optInt("degenerate_triangle_count", Int.MAX_VALUE)
    )
    val disconnectedComponents = reportTopology.optInt("disconnected_component_count", Int.MAX_VALUE)
    val inconsistentNormalEdges = reportTopology.optInt("inconsistent_normal_edge_count", Int.MAX_VALUE)
    val repairApplied = reportRepair.optBoolean("repair_applied", false)
    val printabilityRequiresScale = reportScale.optBoolean("scale_confidence_required_for_current_gate", false)
    val reportExportReady = reportSlicerReadiness.optBoolean("export_ready", report.optBoolean("export_ready", false))
    val reportHandoffReady = reportSlicerReadiness.optBoolean(
        "workspace_handoff_ready",
        report.optBoolean("workspace_handoff_ready", false)
    )
    val candidateVertexCount = candidate.optInt("vertex_count", candidate.optJSONArray("vertices")?.length() ?: 0)
    val candidateTriangleCount = candidate.optInt("triangle_count", candidate.optJSONArray("triangles")?.length() ?: 0)
    val scaleConfidence = reportScale.takeUnless { it.isNull("scale_confidence") }
        ?.optDouble("scale_confidence")
        ?.toFloat()
    val units = "millimeters"
    val candidateIntegrity = inspectSlicerCandidateIntegrity(candidate)
    val importContractReady = candidateVertexCount == reportVertexCount &&
        candidateTriangleCount == reportTriangleCount &&
        candidateVertexCount >= limits.minVertexCount &&
        candidateTriangleCount >= limits.minTriangleCount &&
        candidateIntegrity.visualOrRejectedElementCount == 0 &&
        candidateIntegrity.missingTriangleReferenceCount == 0 &&
        candidateIntegrity.duplicateTriangleVertexCount == 0 &&
        boundaryEdges <= limits.maxBoundaryEdgeCount &&
        nonManifoldEdges <= limits.maxNonManifoldEdgeCount &&
        degenerateTriangles <= limits.maxDegenerateTriangleCount &&
        disconnectedComponents <= limits.maxDisconnectedComponentCount &&
        inconsistentNormalEdges <= limits.maxInconsistentNormalEdgeCount &&
        (!repairApplied || limits.allowRepairApplied)
    val errors = buildList {
        if (!report.optBoolean("allowed", false)) add("printability_report_blocked")
        if (!report.optBoolean("metric", false)) add("metric_printability_report_missing")
        if (!report.optBoolean("printability_report_ready", false)) add("printability_report_not_ready")
        if (!report.optBoolean("recommended_for_printing", false)) add("scan_not_recommended_for_printing")
        if (reportExportReady) add("printability_report_export_ready_before_slicer_validation")
        if (reportHandoffReady) add("printability_report_handoff_ready_before_slicer_validation")
        if (!candidate.optBoolean("allowed", false)) add("metric_mesh_candidate_blocked")
        if (!candidate.optBoolean("metric", false)) add("metric_mesh_candidate_not_metric")
        if (!candidate.optBoolean("triangle_mesh_candidate_ready", false)) add("triangle_mesh_candidate_not_ready")
        if (candidateVertexCount != reportVertexCount) add("vertex_count_mismatch")
        if (candidateTriangleCount != reportTriangleCount) add("triangle_count_mismatch")
        if (candidateVertexCount < limits.minVertexCount) add("slicer_vertex_count_low")
        if (candidateTriangleCount < limits.minTriangleCount) add("slicer_triangle_count_low")
        if (candidateIntegrity.visualOrRejectedElementCount > 0) add("slicer_visual_or_rejected_geometry_present")
        if (candidateIntegrity.missingTriangleReferenceCount > 0) add("slicer_missing_triangle_vertex_references_present")
        if (candidateIntegrity.duplicateTriangleVertexCount > 0) add("slicer_duplicate_triangle_vertices_present")
        if (boundaryEdges > limits.maxBoundaryEdgeCount) add("slicer_boundary_edges_present")
        if (nonManifoldEdges > limits.maxNonManifoldEdgeCount) add("slicer_non_manifold_edges_present")
        if (degenerateTriangles > limits.maxDegenerateTriangleCount) add("slicer_degenerate_triangles_present")
        if (disconnectedComponents > limits.maxDisconnectedComponentCount) add("slicer_disconnected_components_present")
        if (inconsistentNormalEdges > limits.maxInconsistentNormalEdgeCount) add("slicer_inconsistent_normals_present")
        if (repairApplied && !limits.allowRepairApplied) add("slicer_repair_applied_without_import_policy")
        if (limits.requireMillimeterUnits && units != "millimeters") add("slicer_units_not_millimeters")
        if (scaleConfidence != null && scaleConfidence < limits.minScaleConfidence) {
            add("slicer_scale_confidence_low")
        }
        if (
            limits.requireScaleConfidenceWhenPrintabilityRequiresIt &&
            printabilityRequiresScale &&
            scaleConfidence == null
        ) {
            add("slicer_scale_confidence_missing")
        }
        if (!importContractReady) add("internal_slicer_import_contract_not_ready")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerSlicerLoadValidationResult(
        allowed = accepted,
        metric = accepted,
        slicerLoadValidated = accepted,
        exportReady = false,
        workspaceHandoffReady = false,
        status = if (accepted) "slicer_load_validated_export_blocked" else "slicer_load_validation_blocked",
        errors = if (accepted) {
            listOf("export_writer_required_before_stl_3mf_handoff")
        } else {
            errors + "export_blocked_until_slicer_load_validation"
        },
        warnings = buildList {
            if (scaleConfidence == null) add("scale_confidence_not_available")
            if (accepted) add("slicer_load_validation_is_internal_contract_not_file_export")
            add("stl_3mf_export_remains_blocked")
            add("workspace_handoff_remains_blocked")
        },
        vertexCount = if (accepted) candidateVertexCount else 0,
        triangleCount = if (accepted) candidateTriangleCount else 0,
        units = units,
        scaleConfidence = scaleConfidence,
        boundaryEdgeCount = boundaryEdges,
        nonManifoldEdgeCount = nonManifoldEdges,
        degenerateTriangleCount = degenerateTriangles,
        disconnectedComponentCount = disconnectedComponents,
        inconsistentNormalEdgeCount = inconsistentNormalEdges,
        repairApplied = repairApplied,
        missingTriangleReferenceCount = candidateIntegrity.missingTriangleReferenceCount,
        duplicateTriangleVertexCount = candidateIntegrity.duplicateTriangleVertexCount,
        candidateVisualOrRejectedElementCount = candidateIntegrity.visualOrRejectedElementCount,
        internalImportContractReady = accepted
    )
    writeScannerSlicerLoadValidation(workspaceDir, result, limits)
    return result
}

private fun inspectSlicerCandidateIntegrity(candidate: JSONObject): SlicerCandidateIntegrity {
    val vertices = candidate.optJSONArray("vertices") ?: JSONArray()
    val triangles = candidate.optJSONArray("triangles") ?: JSONArray()
    val vertexIds = List(vertices.length()) { index -> vertices.getJSONObject(index).optString("vertex_id") }
        .filter { it.isNotBlank() }
        .toSet()
    val visualVertices = List(vertices.length()) { index -> vertices.getJSONObject(index) }
        .count { it.optString("provenance_class", "rejected") == "visual_only" || it.optString("provenance_class") == "rejected" }
    var visualTriangles = 0
    var missingReferences = 0
    var duplicateTriangleVertices = 0
    List(triangles.length()) { index -> triangles.getJSONObject(index) }.forEach { triangle ->
        if (triangle.optString("provenance_class", "rejected") == "visual_only" || triangle.optString("provenance_class") == "rejected") {
            visualTriangles += 1
        }
        val idsJson = triangle.optJSONArray("vertex_ids") ?: JSONArray()
        val ids = List(idsJson.length()) { index -> idsJson.optString(index) }
        if (ids.size != 3 || ids.toSet().size != 3) duplicateTriangleVertices += 1
        missingReferences += ids.count { it !in vertexIds }
    }
    return SlicerCandidateIntegrity(
        visualOrRejectedElementCount = visualVertices + visualTriangles,
        missingTriangleReferenceCount = missingReferences,
        duplicateTriangleVertexCount = duplicateTriangleVertices
    )
}

private fun blockedScannerSlicerLoadValidation(errors: List<String>): ScannerSlicerLoadValidationResult =
    ScannerSlicerLoadValidationResult(
        allowed = false,
        metric = false,
        slicerLoadValidated = false,
        exportReady = false,
        workspaceHandoffReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        vertexCount = 0,
        triangleCount = 0,
        units = "millimeters",
        scaleConfidence = null,
        boundaryEdgeCount = 0,
        nonManifoldEdgeCount = 0,
        degenerateTriangleCount = 0,
        disconnectedComponentCount = 0,
        inconsistentNormalEdgeCount = 0,
        repairApplied = false,
        missingTriangleReferenceCount = 0,
        duplicateTriangleVertexCount = 0,
        candidateVisualOrRejectedElementCount = 0,
        internalImportContractReady = false
    )

private fun writeScannerSlicerLoadValidation(
    workspaceDir: File,
    result: ScannerSlicerLoadValidationResult,
    limits: ScannerSlicerLoadValidationLimits
) {
    workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerSlicerLoadValidationJson(result, limits).toString(2))
    }
}

internal fun scannerSlicerLoadValidationJson(
    result: ScannerSlicerLoadValidationResult,
    limits: ScannerSlicerLoadValidationLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("slicer_load_validated", result.slicerLoadValidated)
        .put("export_ready", result.exportReady)
        .put("workspace_handoff_ready", result.workspaceHandoffReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("vertex_count", result.vertexCount)
        .put("triangle_count", result.triangleCount)
        .put("units", result.units)
        .put("scale_confidence", result.scaleConfidence ?: JSONObject.NULL)
        .put("boundary_edge_count", result.boundaryEdgeCount)
        .put("non_manifold_edge_count", result.nonManifoldEdgeCount)
        .put("degenerate_triangle_count", result.degenerateTriangleCount)
        .put("disconnected_component_count", result.disconnectedComponentCount)
        .put("inconsistent_normal_edge_count", result.inconsistentNormalEdgeCount)
        .put("repair_applied", result.repairApplied)
        .put("missing_triangle_reference_count", result.missingTriangleReferenceCount)
        .put("duplicate_triangle_vertex_count", result.duplicateTriangleVertexCount)
        .put("candidate_visual_or_rejected_element_count", result.candidateVisualOrRejectedElementCount)
        .put("internal_import_contract_ready", result.internalImportContractReady)
        .put(
            "printability_contract",
            JSONObject()
                .put("source_report", SCANNER_PRINTABILITY_REPORT_PATH)
                .put("topology_checked", true)
                .put("repair_policy_checked", true)
                .put("scale_checked", true)
                .put("export_must_be_false_before_writer", true)
                .put("workspace_handoff_must_be_false_before_export", true)
        )
        .put(
            "candidate_contract",
            JSONObject()
                .put("source_mesh_candidate", SCANNER_METRIC_MESH_CANDIDATE_PATH)
                .put("internal_import_contract_ready", result.internalImportContractReady)
                .put("missing_triangle_reference_count", result.missingTriangleReferenceCount)
                .put("duplicate_triangle_vertex_count", result.duplicateTriangleVertexCount)
                .put("visual_or_rejected_element_count", result.candidateVisualOrRejectedElementCount)
        )
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("require_millimeter_units", limits.requireMillimeterUnits)
                .put("max_boundary_edge_count", limits.maxBoundaryEdgeCount)
                .put("max_non_manifold_edge_count", limits.maxNonManifoldEdgeCount)
                .put("max_degenerate_triangle_count", limits.maxDegenerateTriangleCount)
                .put("max_disconnected_component_count", limits.maxDisconnectedComponentCount)
                .put("max_inconsistent_normal_edge_count", limits.maxInconsistentNormalEdgeCount)
                .put("allow_repair_applied", limits.allowRepairApplied)
                .put("require_scale_confidence_when_printability_requires_it", limits.requireScaleConfidenceWhenPrintabilityRequiresIt)
        )
        .put(
            "validated_contract",
            JSONObject()
                .put("source_report", SCANNER_PRINTABILITY_REPORT_PATH)
                .put("source_mesh_candidate", SCANNER_METRIC_MESH_CANDIDATE_PATH)
                .put("mesh_units", "millimeters")
                .put("stl_export_allowed", false)
                .put("three_mf_export_allowed", false)
                .put("workspace_handoff_allowed", false)
                .put("required_next_gate", "metric_export_writer")
        )
