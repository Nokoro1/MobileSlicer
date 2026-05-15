package com.mobileslicer.scanner

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class ScannerAutomationRequest(
    val statusPath: String,
    val summaryPath: String,
    val scanId: String,
    val fixtureKind: ScannerAutomationFixtureKind,
    val includeSyntheticMarkers: Boolean,
    val measuredScaleBarMm: Float?
) {
    companion object {
        const val ACTION_AUTOMATE_SCANNER_SYNTHETIC = "com.mobileslicer.action.AUTOMATE_SCANNER_SYNTHETIC"
        const val EXTRA_STATUS_PATH = "scanner_automation_status_path"
        const val EXTRA_SUMMARY_PATH = "scanner_automation_summary_path"
        const val EXTRA_SCAN_ID = "scanner_automation_scan_id"
        const val EXTRA_FIXTURE_KIND = "scanner_automation_fixture_kind"
        const val EXTRA_INCLUDE_SYNTHETIC_MARKERS = "scanner_automation_include_synthetic_markers"
        const val EXTRA_MEASURED_SCALE_BAR_MM = "scanner_automation_measured_scale_bar_mm"

        fun fromIntent(context: Context, intent: Intent?): ScannerAutomationRequest? {
            if (intent?.action != ACTION_AUTOMATE_SCANNER_SYNTHETIC) {
                return null
            }
            val automationDir = File(context.filesDir, "automation")
            val scanId = intent.getStringExtra(EXTRA_SCAN_ID)
                ?.takeIf { it.isNotBlank() }
                ?: "scan_app_synthetic_${UUID.randomUUID()}"
            val statusPath = intent.getStringExtra(EXTRA_STATUS_PATH)
                ?.let { context.resolveScannerAutomationPath(it) }
                ?: automationDir.resolve("scanner-status.json")
            val summaryPath = intent.getStringExtra(EXTRA_SUMMARY_PATH)
                ?.let { context.resolveScannerAutomationPath(it) }
                ?: automationDir.resolve("scanner-summary.json")
            val measuredScale = if (intent.hasExtra(EXTRA_MEASURED_SCALE_BAR_MM)) {
                intent.getFloatExtra(EXTRA_MEASURED_SCALE_BAR_MM, Float.NaN).takeIf { it.isFinite() && it > 0f }
            } else {
                null
            }
            return ScannerAutomationRequest(
                statusPath = statusPath.absolutePath,
                summaryPath = summaryPath.absolutePath,
                scanId = scanId,
                fixtureKind = ScannerAutomationFixtureKind.fromManifestValue(
                    intent.getStringExtra(EXTRA_FIXTURE_KIND)
                ),
                includeSyntheticMarkers = intent.getBooleanExtra(EXTRA_INCLUDE_SYNTHETIC_MARKERS, false),
                measuredScaleBarMm = measuredScale
            )
        }

        private fun Context.resolveScannerAutomationPath(path: String): File =
            File(path).takeIf { it.isAbsolute } ?: File(dataDir, path)
    }
}

internal enum class ScannerAutomationFixtureKind(val manifestValue: String) {
    CapturePackage("capture_package"),
    MetricReconstruction("metric_reconstruction");

    companion object {
        fun fromManifestValue(value: String?): ScannerAutomationFixtureKind =
            entries.firstOrNull { it.manifestValue == value } ?: CapturePackage
    }
}

internal class ScannerAutomationRunner(
    private val context: Context
) {
    fun run(request: ScannerAutomationRequest): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val statusFile = File(request.statusPath)
        val summaryFile = File(request.summaryPath)

        fun writeStatus(status: String, fields: JSONObject = JSONObject()) {
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(scannerAutomationStatusJson(status, request, fields).toString(2))
        }

        return runCatching {
            writeStatus("running")
            val fields = when (request.fixtureKind) {
                ScannerAutomationFixtureKind.CapturePackage -> runCapturePackageFixture(request, summaryFile, startedAt)
                ScannerAutomationFixtureKind.MetricReconstruction -> runMetricReconstructionFixture(request, summaryFile, startedAt)
            }
            val success = fields.optBoolean("automation_success", false)
            writeStatus(if (success) "passed" else "failed", fields)
            success
        }.getOrElse { exception ->
            Log.e(TAG, "scanner automation failed", exception)
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            writeStatus(
                "failed",
                JSONObject()
                    .put("elapsed_ms", elapsedMs)
                    .put("error_class", exception.javaClass.simpleName)
                    .put("error_message", exception.message.orEmpty())
            )
            false
        }
    }

    private fun runCapturePackageFixture(
        request: ScannerAutomationRequest,
        summaryFile: File,
        startedAt: Long
    ): JSONObject {
        val result = runScannerSyntheticCaptureRegression(
            rootDir = context.cacheDir,
            config = ScannerSyntheticRunConfig(
                scanId = request.scanId,
                measuredScaleBarMm = request.measuredScaleBarMm,
                includeSyntheticMarkers = request.includeSyntheticMarkers
            )
        )
        writeScannerSyntheticRunSummary(result, summaryFile)
        val success = result.export.validation.valid &&
            result.pipeline.completed &&
            result.pipeline.blockingErrors.none { it.contains("package_validation_failed") }
        return scannerAutomationCapturePackageResultFields(
            result = result,
            summaryFile = summaryFile,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            success = success
        )
    }

    private fun runMetricReconstructionFixture(
        request: ScannerAutomationRequest,
        summaryFile: File,
        startedAt: Long
    ): JSONObject {
        val workspaceDir = context.cacheDir.resolve("scanner-workspaces/${request.scanId}")
        val result = runScannerSyntheticMetricPoseFixture(workspaceDir)
        val summary = JSONObject(workspaceDir.resolve(SCANNER_SYNTHETIC_METRIC_FIXTURE_SUMMARY_PATH).readText())
        summaryFile.parentFile?.mkdirs()
        summaryFile.writeText(summary.toString(2))
        val success = result.optimizer.allowed &&
            result.optimizer.metric &&
            result.sparseTriangulation.metric &&
            result.denseReconstruction.denseReconstructionAdmitted &&
            result.densePointCloud.densePointCloudReady &&
            result.densePointCloud.surfaceReconstructionReady
        return scannerAutomationMetricReconstructionResultFields(
            result = result,
            summaryFile = summaryFile,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            success = success
        )
    }

    private companion object {
        const val TAG = "MobileSlicerScanner"
    }
}

internal fun scannerAutomationStatusJson(
    status: String,
    request: ScannerAutomationRequest,
    fields: JSONObject = JSONObject()
): JSONObject =
    JSONObject()
        .put("schema_version", 1)
        .put("created_at", Instant.now().toString())
        .put("status", status)
        .put("scan_id", request.scanId)
        .put("mode", "synthetic_app_scanner_smoke")
        .put("fixture_kind", request.fixtureKind.manifestValue)
        .put("include_synthetic_markers", request.includeSyntheticMarkers)
        .put("measured_scale_bar_mm", request.measuredScaleBarMm ?: JSONObject.NULL)
        .put("summary_path", request.summaryPath)
        .put("details", fields)

internal fun scannerAutomationCapturePackageResultFields(
    result: ScannerSyntheticRunResult,
    summaryFile: File,
    elapsedMs: Long,
    success: Boolean = result.export.validation.valid &&
        result.pipeline.completed &&
        result.pipeline.blockingErrors.none { it.contains("package_validation_failed") }
): JSONObject =
    JSONObject()
        .put("automation_success", success)
        .put("fixture_kind", ScannerAutomationFixtureKind.CapturePackage.manifestValue)
        .put("elapsed_ms", elapsedMs)
        .put("session_dir", result.sessionDir.absolutePath)
        .put("package_dir", result.export.packageDir.absolutePath)
        .put("zip", result.export.zipFile.absolutePath)
        .put("workspace_dir", result.workspaceDir.absolutePath)
        .put("summary_file", summaryFile.absolutePath)
        .put("reconstruction_summary", result.workspaceDir.resolve(result.pipeline.summaryPath).absolutePath)
        .put("reconstruction_audit", result.workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).absolutePath)
        .put("captured_frame_count", result.capturedFrameCount)
        .put("retained_frame_count", result.retainedFrameCount)
        .put("accepted_frame_count", result.acceptedFrameCount)
        .put("retained_rejected_frame_count", result.retainedRejectedFrameCount)
        .put("package_valid", result.export.validation.valid)
        .put("package_errors", JSONArray(result.export.validation.errors))
        .put("pipeline_completed", result.pipeline.completed)
        .put("dense_ready", result.pipeline.denseReconstructionReady)
        .put("blocking_errors", JSONArray(result.pipeline.blockingErrors))

internal fun scannerAutomationMetricReconstructionResultFields(
    result: ScannerSyntheticMetricPoseFixtureResult,
    summaryFile: File,
    elapsedMs: Long,
    success: Boolean = result.optimizer.allowed &&
        result.optimizer.metric &&
        result.sparseTriangulation.metric &&
        result.denseReconstruction.denseReconstructionAdmitted &&
        result.densePointCloud.densePointCloudReady &&
        result.densePointCloud.surfaceReconstructionReady
): JSONObject =
    JSONObject()
        .put("automation_success", success)
        .put("fixture_kind", ScannerAutomationFixtureKind.MetricReconstruction.manifestValue)
        .put("elapsed_ms", elapsedMs)
        .put("workspace_dir", result.workspaceDir.absolutePath)
        .put("summary_file", summaryFile.absolutePath)
        .put(
            "summary",
            result.workspaceDir.resolve(SCANNER_SYNTHETIC_METRIC_FIXTURE_SUMMARY_PATH).absolutePath
        )
        .put("optimizer_allowed", result.optimizer.allowed)
        .put("optimizer_metric", result.optimizer.metric)
        .put("sparse_metric", result.sparseTriangulation.metric)
        .put("sparse_measured_point_count", result.sparseTriangulation.measuredPointCount)
        .put("dense_admitted", result.denseReconstruction.denseReconstructionAdmitted)
        .put("debug_point_cloud_ready", result.densePointCloud.densePointCloudReady)
        .put("surface_ready", result.densePointCloud.surfaceReconstructionReady)
        .put("debug_point_cloud_ply", result.workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).absolutePath)
        .put("surface_candidate", result.workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).absolutePath)
        .put("printable", false)
        .put("slicer_handoff_allowed", false)
        .put(
            "blocking_errors",
            JSONArray(
                result.optimizer.errors +
                    result.sparseTriangulation.errors +
                    result.denseReconstruction.errors +
                    result.densePointCloud.errors
            )
        )
