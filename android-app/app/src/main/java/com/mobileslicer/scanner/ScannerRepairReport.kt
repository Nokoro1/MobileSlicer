package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_REPAIR_REPORT_PATH = "reports/repair_report.json"

internal data class ScannerRepairReportLimits(
    val maxRepairedSurfaceRatioForMetricMesh: Float = 0.0f,
    val maxVisualOnlyElementCount: Int = 0
)

internal data class ScannerRepairReportResult(
    val allowed: Boolean,
    val metric: Boolean,
    val repairReportReady: Boolean,
    val repairApplied: Boolean,
    val repairedSurfaceRatio: Float,
    val repairedHoleCount: Int,
    val largestRepairedHoleMm: Float?,
    val visualOnlyElementCount: Int,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>
)

internal fun buildScannerRepairReport(
    workspaceDir: File,
    limits: ScannerRepairReportLimits = ScannerRepairReportLimits()
): ScannerRepairReportResult {
    val validation = workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (validation == null) {
        val result = blockedScannerRepairReport(listOf("mesh_validation_missing"))
        writeScannerRepairReport(workspaceDir, result, limits)
        return result
    }

    val repairedSurfaceRatio = validation.optDouble("repaired_surface_ratio", 1.0).toFloat()
    val visualOnlyElementCount = validation.optInt("visual_only_element_count", Int.MAX_VALUE)
    val repairApplied = repairedSurfaceRatio > 0f
    val errors = buildList {
        if (!validation.optBoolean("allowed", false)) add("mesh_validation_blocked")
        if (!validation.optBoolean("metric", false)) add("metric_mesh_validation_missing")
        if (!validation.optBoolean("metric_mesh_ready", false)) add("metric_mesh_not_ready")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatioForMetricMesh) {
            add("repaired_surface_ratio_requires_explicit_repair_pipeline")
        }
        if (visualOnlyElementCount > limits.maxVisualOnlyElementCount) add("visual_only_geometry_blocks_metric_repair_report")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerRepairReportResult(
        allowed = accepted,
        metric = accepted,
        repairReportReady = accepted,
        repairApplied = repairApplied,
        repairedSurfaceRatio = repairedSurfaceRatio,
        repairedHoleCount = if (repairApplied) 1 else 0,
        largestRepairedHoleMm = if (repairApplied) 0f else null,
        visualOnlyElementCount = visualOnlyElementCount.coerceAtLeast(0),
        status = if (accepted) "repair_report_ready" else "repair_report_blocked",
        errors = if (accepted) emptyList() else errors + "printability_blocked_until_repair_report",
        warnings = buildList {
            if (accepted && !repairApplied) add("no_repair_or_hole_fill_applied")
            if (accepted) add("repair_report_required_before_printability")
        }
    )
    writeScannerRepairReport(workspaceDir, result, limits)
    return result
}

private fun blockedScannerRepairReport(errors: List<String>): ScannerRepairReportResult =
    ScannerRepairReportResult(
        allowed = false,
        metric = false,
        repairReportReady = false,
        repairApplied = false,
        repairedSurfaceRatio = 0f,
        repairedHoleCount = 0,
        largestRepairedHoleMm = null,
        visualOnlyElementCount = 0,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList()
    )

private fun writeScannerRepairReport(
    workspaceDir: File,
    result: ScannerRepairReportResult,
    limits: ScannerRepairReportLimits
) {
    workspaceDir.resolve(SCANNER_REPAIR_REPORT_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerRepairReportJson(result, limits).toString(2))
    }
}

internal fun scannerRepairReportJson(
    result: ScannerRepairReportResult,
    limits: ScannerRepairReportLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("repair_report_ready", result.repairReportReady)
        .put("repair_applied", result.repairApplied)
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put("repaired_hole_count", result.repairedHoleCount)
        .put("largest_repaired_hole_mm", result.largestRepairedHoleMm ?: JSONObject.NULL)
        .put("visual_only_element_count", result.visualOnlyElementCount)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put(
            "limits",
            JSONObject()
                .put("max_repaired_surface_ratio_for_metric_mesh", limits.maxRepairedSurfaceRatioForMetricMesh.toDouble())
                .put("max_visual_only_element_count", limits.maxVisualOnlyElementCount)
        )
        .put(
            "repair_contract",
            JSONObject()
                .put("source_validation", SCANNER_MESH_VALIDATION_PATH)
                .put("metric_repairs_allowed_without_explicit_pipeline", false)
                .put("visual_only_geometry_allowed_in_metric_mesh", false)
                .put("required_next_gate", "printability_report")
        )
