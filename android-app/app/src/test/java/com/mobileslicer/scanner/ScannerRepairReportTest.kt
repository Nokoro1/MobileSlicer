package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRepairReportTest {
    @Test
    fun repairReportBlocksWhenMeshValidationIsMissing() {
        val workspaceDir = tempDir("scanner-repair-missing")

        val result = buildScannerRepairReport(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.repairReportReady)
        assertTrue(result.errors.contains("mesh_validation_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_REPAIR_REPORT_PATH).isFile)
    }

    @Test
    fun repairReportAcceptsValidatedMeshWithNoRepair() {
        val workspaceDir = tempDir("scanner-repair-ready")
        writeMeshValidation(workspaceDir, allowed = true, repairedSurfaceRatio = 0.0)

        val result = buildScannerRepairReport(workspaceDir)

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.repairReportReady)
        assertFalse(result.repairApplied)
        assertTrue(result.warnings.contains("no_repair_or_hole_fill_applied"))
    }

    @Test
    fun repairReportBlocksUnreportedRepairs() {
        val workspaceDir = tempDir("scanner-repair-blocked")
        writeMeshValidation(workspaceDir, allowed = true, repairedSurfaceRatio = 0.2)

        val result = buildScannerRepairReport(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("repaired_surface_ratio_requires_explicit_repair_pipeline"))
        assertTrue(result.errors.contains("printability_blocked_until_repair_report"))
    }

    private fun writeMeshValidation(
        workspaceDir: File,
        allowed: Boolean,
        repairedSurfaceRatio: Double
    ) {
        workspaceDir.resolve("mesh").mkdirs()
        workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("metric_mesh_ready", allowed)
                .put("visual_only_element_count", 0)
                .put("repaired_surface_ratio", repairedSurfaceRatio)
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
