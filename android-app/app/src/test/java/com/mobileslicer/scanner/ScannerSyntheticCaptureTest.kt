package com.mobileslicer.scanner

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSyntheticCaptureTest {
    @Test
    fun syntheticCaptureRunsExportAndPipelineWithoutCamera() {
        val root = Files.createTempDirectory("scanner-synthetic-root").toFile()

        val result = runScannerSyntheticCaptureRegression(
            rootDir = root,
            config = ScannerSyntheticRunConfig(
                scanId = "scan_synthetic_unit",
                rejectedWarmupFrames = 6,
                rejectedDuplicateFramesPerPass = 3,
                measuredScaleBarMm = null,
                includeSyntheticMarkers = false
            )
        )

        assertEquals(62, result.capturedFrameCount)
        assertEquals(44, result.acceptedFrameCount)
        assertEquals(17, result.retainedRejectedFrameCount)
        assertEquals(61, result.retainedFrameCount)
        assertTrue(result.export.validation.errors.joinToString(), result.export.validation.valid)
        assertEquals(44, result.export.validation.manifest?.frameCount)
        assertEquals(0, result.export.validation.manifest?.rejectedFrameCount)
        assertTrue(result.export.validation.manifest?.hasMasks == true)
        assertTrue(result.export.zipFile.name.contains("_44_"))
        assertTrue(result.pipeline.completed)
        assertFalse(result.pipeline.blockingErrors.any { it.contains("package_validation_failed") })
        assertTrue(result.workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH).isFile)

        val compactRejections = JSONObject(
            result.export.packageDir.resolve("diagnostics/capture_rejections_compact.json").readText()
        )
        assertEquals(61, compactRejections.getInt("captured_frame_count"))
        assertEquals(17, compactRejections.getInt("rejected_frame_count"))
        assertFalse(compactRejections.getBoolean("rejected_image_files_exported"))

        val localAi = JSONObject(result.export.packageDir.resolve("diagnostics/local_ai_status.json").readText())
        assertTrue(localAi.getJSONObject("mask_quality").getInt("mask_count") > 0)
        assertFalse(localAi.getJSONObject("stack").getBoolean("metric_truth"))
    }

    @Test
    fun syntheticCaptureCapsRetainedRejectedFramesPerPass() {
        val root = Files.createTempDirectory("scanner-synthetic-cap-root").toFile()

        val result = runScannerSyntheticCaptureRegression(
            rootDir = root,
            config = ScannerSyntheticRunConfig(
                scanId = "scan_synthetic_cap",
                rejectedWarmupFrames = 12,
                rejectedDuplicateFramesPerPass = 12
            )
        )

        assertEquals(100, result.capturedFrameCount)
        assertEquals(44, result.acceptedFrameCount)
        assertEquals(32, result.retainedRejectedFrameCount)
        assertEquals(76, result.retainedFrameCount)
        assertTrue(result.export.validation.valid)
        assertEquals(44, result.export.validation.manifest?.frameCount)
        assertFalse(result.export.packageDir.walkTopDown().any { file ->
            file.isFile && file.name.contains("duplicate_009")
        })
    }

    @Test
    fun syntheticRunSummaryIsMachineReadable() {
        val root = Files.createTempDirectory("scanner-synthetic-summary-root").toFile()
        val result = runScannerSyntheticCaptureRegression(root)
        val output = root.resolve("synthetic_summary.json")

        writeScannerSyntheticRunSummary(result, output)

        val json = JSONObject(output.readText())
        assertEquals(result.scanId, json.getString("scan_id"))
        assertEquals(result.acceptedFrameCount, json.getInt("accepted_frame_count"))
        assertTrue(json.getBoolean("package_valid"))
        assertTrue(json.getBoolean("pipeline_completed"))
        assertTrue(json.getJSONObject("artifact_paths").getString("summary").endsWith(SCANNER_RECONSTRUCTION_SUMMARY_PATH))
    }

    @Test
    fun scannerAutomationStatusIsMachineReadableAndKeepsPipelineBlockersVisible() {
        val root = Files.createTempDirectory("scanner-automation-status-root").toFile()
        val result = runScannerSyntheticCaptureRegression(root)
        val request = ScannerAutomationRequest(
            statusPath = root.resolve("status.json").absolutePath,
            summaryPath = root.resolve("summary.json").absolutePath,
            scanId = result.scanId,
            fixtureKind = ScannerAutomationFixtureKind.CapturePackage,
            includeSyntheticMarkers = false,
            measuredScaleBarMm = null
        )

        val json = scannerAutomationStatusJson(
            status = "passed",
            request = request,
            fields = scannerAutomationCapturePackageResultFields(
                result = result,
                summaryFile = root.resolve("summary.json"),
                elapsedMs = 1234L
            )
        )

        assertEquals("passed", json.getString("status"))
        assertEquals("synthetic_app_scanner_smoke", json.getString("mode"))
        assertEquals("capture_package", json.getString("fixture_kind"))
        assertEquals(result.scanId, json.getString("scan_id"))
        val details = json.getJSONObject("details")
        assertTrue(details.getBoolean("automation_success"))
        assertEquals("capture_package", details.getString("fixture_kind"))
        assertEquals(44, details.getInt("accepted_frame_count"))
        assertTrue(details.getBoolean("package_valid"))
        assertTrue(details.getBoolean("pipeline_completed"))
        assertTrue(details.getJSONArray("blocking_errors").length() > 0)
        assertTrue(details.getString("reconstruction_summary").endsWith(SCANNER_RECONSTRUCTION_SUMMARY_PATH))
        assertTrue(details.getString("reconstruction_audit").endsWith(SCANNER_RECONSTRUCTION_AUDIT_PATH))
    }
}
