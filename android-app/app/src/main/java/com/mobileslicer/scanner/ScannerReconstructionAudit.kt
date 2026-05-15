package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_RECONSTRUCTION_AUDIT_PATH = "reconstruction_audit.json"

internal data class ScannerReconstructionAudit(
    val scanId: String?,
    val firstFailingStage: ScannerReconstructionPipelineStage?,
    val firstFailingStageIndex: Int?,
    val nextActions: List<String>,
    val rawBlockerCount: Int,
    val artifactCount: Int,
    val missingArtifactCount: Int,
    val path: String
)

internal fun writeScannerReconstructionAudit(
    result: ScannerReconstructionPipelineResult,
    packageDir: File
): ScannerReconstructionAudit {
    val scanId = readScannerAuditScanId(packageDir)
    val firstFailingIndex = result.stages.indexOfFirst { stage ->
        !stage.allowed || stage.errors.isNotEmpty()
    }.takeIf { it >= 0 }
    val firstFailingStage = firstFailingIndex?.let { result.stages[it] }
    val artifactStates = result.stages.map { stage ->
        val artifactFile = stage.artifactPath?.let { result.workspaceDir.resolve(it) }
        ScannerAuditArtifactState(
            stage = stage,
            exists = artifactFile?.isFile ?: false,
            absolutePath = artifactFile?.absolutePath
        )
    }
    val audit = ScannerReconstructionAudit(
        scanId = scanId,
        firstFailingStage = firstFailingStage,
        firstFailingStageIndex = firstFailingIndex,
        nextActions = scannerAuditNextActions(result.blockingErrors, firstFailingStage),
        rawBlockerCount = result.blockingErrors.size,
        artifactCount = artifactStates.count { it.stage.artifactPath != null },
        missingArtifactCount = artifactStates.count { it.stage.artifactPath != null && !it.exists },
        path = SCANNER_RECONSTRUCTION_AUDIT_PATH
    )
    result.workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH)
        .writeText(scannerReconstructionAuditJson(result, audit, artifactStates).toString(2))
    return audit
}

internal fun scannerReconstructionAuditJson(
    result: ScannerReconstructionPipelineResult,
    audit: ScannerReconstructionAudit,
    artifactStates: List<ScannerAuditArtifactState>
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("scan_id", audit.scanId ?: JSONObject.NULL)
        .put("workspace_path", result.workspaceDir.absolutePath)
        .put("summary_path", result.workspaceDir.resolve(result.summaryPath).absolutePath)
        .put("audit_path", result.workspaceDir.resolve(audit.path).absolutePath)
        .put("completed", result.completed)
        .put("metric", result.metric)
        .put("dense_reconstruction_ready", result.denseReconstructionReady)
        .put("raw_blocker_count", audit.rawBlockerCount)
        .put("artifact_count", audit.artifactCount)
        .put("missing_artifact_count", audit.missingArtifactCount)
        .put(
            "first_failing_stage",
            audit.firstFailingStage?.let { stage ->
                JSONObject()
                    .put("index", audit.firstFailingStageIndex)
                    .put("name", stage.name)
                    .put("artifact_path", stage.artifactPath ?: JSONObject.NULL)
                    .put("allowed", stage.allowed)
                    .put("metric", stage.metric)
                    .put("errors", JSONArray(stage.errors))
                    .put("warnings", JSONArray(stage.warnings))
            } ?: JSONObject.NULL
        )
        .put("next_actions", JSONArray(audit.nextActions))
        .put("raw_blockers", JSONArray(result.blockingErrors))
        .put(
            "stages",
            JSONArray().apply {
                artifactStates.forEachIndexed { index, state ->
                    put(
                        JSONObject()
                            .put("index", index)
                            .put("name", state.stage.name)
                            .put("allowed", state.stage.allowed)
                            .put("metric", state.stage.metric)
                            .put("artifact_path", state.stage.artifactPath ?: JSONObject.NULL)
                            .put("artifact_exists", state.exists)
                            .put("artifact_absolute_path", state.absolutePath ?: JSONObject.NULL)
                            .put("errors", JSONArray(state.stage.errors))
                            .put("warnings", JSONArray(state.stage.warnings))
                    )
                }
            }
        )

internal data class ScannerAuditArtifactState(
    val stage: ScannerReconstructionPipelineStage,
    val exists: Boolean,
    val absolutePath: String?
)

private fun scannerAuditNextActions(
    blockers: List<String>,
    firstFailingStage: ScannerReconstructionPipelineStage?
): List<String> {
    val joined = blockers.joinToString("\n")
    return buildList {
        if (firstFailingStage == null) {
            add("No pipeline blockers were reported. Verify metric export and workspace handoff artifacts before treating the scan as printable.")
        } else {
            add("Start with stage `${firstFailingStage.name}`; it is the first stage that failed or emitted blocking errors.")
        }
        if (joined.contains("not_enough_accepted_frames") || joined.contains("missing_capture_pass")) {
            add("Capture more accepted frames across low, mid, high, and detail passes before debugging reconstruction math.")
        }
        if (joined.contains("object_masks_missing") || joined.contains("mask", ignoreCase = true)) {
            add("Verify local mask generation and object tap/ROI support; metric reconstruction treats masks as soft evidence.")
        }
        if (joined.contains("feature", ignoreCase = true) || joined.contains("track", ignoreCase = true)) {
            add("Inspect feature and track artifacts for low texture, blur, border artifacts, or poor overlap between accepted frames.")
        }
        if (joined.contains("scale", ignoreCase = true) || joined.contains("marker", ignoreCase = true) || joined.contains("calibration", ignoreCase = true)) {
            add("Treat the scan as non-printable until verified scale and marker reprojection evidence are present.")
        }
        if (joined.contains("pose", ignoreCase = true) || joined.contains("bundle", ignoreCase = true) || joined.contains("optimizer", ignoreCase = true)) {
            add("Inspect pose solve, bundle diagnostics, and native optimizer artifacts before changing dense reconstruction.")
        }
        if (joined.contains("sparse_triangulation", ignoreCase = true) || joined.contains("sparse_points", ignoreCase = true)) {
            add("Do not work on surface generation until accepted optimized metric sparse points exist.")
        }
        if (joined.contains("dense", ignoreCase = true) || joined.contains("surface", ignoreCase = true) || joined.contains("mesh", ignoreCase = true)) {
            add("Dense/surface work is only meaningful after measured metric sparse/depth evidence passes admission.")
        }
        if (joined.contains("export", ignoreCase = true) || joined.contains("handoff", ignoreCase = true) || joined.contains("slicer", ignoreCase = true)) {
            add("Keep STL/3MF export and workspace handoff blocked until topology, printability, and slicer-load validation pass.")
        }
    }.distinct()
}

private fun readScannerAuditScanId(packageDir: File): String? =
    runCatching {
        JSONObject(packageDir.resolve(SCANNER_MANIFEST_PATH).readText()).optString("scan_id")
            .takeIf { it.isNotBlank() }
    }.getOrNull()
