package com.mobileslicer.scanner

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSyntheticMetricPoseFixtureTest {
    @Test
    fun syntheticMetricPoseFixturePassesOptimizerSparseAndDebugCloudWithoutMeshHandoff() {
        val workspaceDir = Files.createTempDirectory("scanner-synthetic-metric-pose").toFile()

        val result = runScannerSyntheticMetricPoseFixture(workspaceDir)

        assertTrue(result.optimizer.errors.joinToString(), result.optimizer.allowed)
        assertTrue(result.optimizer.metric)
        assertTrue(result.sparseTriangulation.errors.joinToString(), result.sparseTriangulation.metric)
        assertEquals(48, result.sparseTriangulation.measuredPointCount)
        assertTrue(result.denseReconstruction.denseReconstructionAdmitted)
        assertTrue(result.densePointCloud.densePointCloudReady)
        assertTrue(result.densePointCloud.surfaceReconstructionReady)
        assertTrue(workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).isFile)

        val summary = JSONObject(workspaceDir.resolve(SCANNER_SYNTHETIC_METRIC_FIXTURE_SUMMARY_PATH).readText())
        assertTrue(summary.getBoolean("optimizer_allowed"))
        assertTrue(summary.getBoolean("sparse_metric"))
        assertTrue(summary.getBoolean("debug_point_cloud_ready"))
        assertTrue(summary.getBoolean("surface_ready"))
        assertFalse(summary.getBoolean("printable"))
        assertFalse(summary.getBoolean("slicer_handoff_allowed"))
    }

    @Test
    fun metricReconstructionAutomationStatusRequiresOptimizerSparseDenseAndSurface() {
        val workspaceDir = Files.createTempDirectory("scanner-synthetic-metric-automation").toFile()
        val result = runScannerSyntheticMetricPoseFixture(workspaceDir)
        val summaryFile = workspaceDir.resolve("automation-summary.json")
        val request = ScannerAutomationRequest(
            statusPath = workspaceDir.resolve("automation-status.json").absolutePath,
            summaryPath = summaryFile.absolutePath,
            scanId = "scan_metric_automation",
            fixtureKind = ScannerAutomationFixtureKind.MetricReconstruction,
            includeSyntheticMarkers = true,
            measuredScaleBarMm = 100f
        )

        val json = scannerAutomationStatusJson(
            status = "passed",
            request = request,
            fields = scannerAutomationMetricReconstructionResultFields(
                result = result,
                summaryFile = summaryFile,
                elapsedMs = 50L
            )
        )

        assertEquals("metric_reconstruction", json.getString("fixture_kind"))
        val details = json.getJSONObject("details")
        assertTrue(details.getBoolean("automation_success"))
        assertEquals("metric_reconstruction", details.getString("fixture_kind"))
        assertTrue(details.getBoolean("optimizer_allowed"))
        assertTrue(details.getBoolean("optimizer_metric"))
        assertTrue(details.getBoolean("sparse_metric"))
        assertTrue(details.getBoolean("dense_admitted"))
        assertTrue(details.getBoolean("debug_point_cloud_ready"))
        assertTrue(details.getBoolean("surface_ready"))
        assertEquals(48, details.getInt("sparse_measured_point_count"))
        assertFalse(details.getBoolean("printable"))
        assertFalse(details.getBoolean("slicer_handoff_allowed"))
        assertEquals(0, details.getJSONArray("blocking_errors").length())
    }
}
