package com.mobileslicer.scanner

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_WORKSPACE_IMPORT_RECEIPT_PATH = "reports/workspace_import_receipt.json"

internal data class ScannerWorkspaceImportPlan(
    val allowed: Boolean,
    val metric: Boolean,
    val modelPath: String?,
    val modelLabel: String?,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>
)

internal fun planScannerWorkspaceImport(
    workspaceDir: File,
    preferThreeMf: Boolean = false
): ScannerWorkspaceImportPlan {
    val handoff = workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (handoff == null) {
        val plan = blockedScannerWorkspaceImport(listOf("workspace_handoff_missing"))
        writeScannerWorkspaceImportReceipt(workspaceDir, plan, imported = false)
        return plan
    }

    val outputs = handoff.optJSONObject("outputs") ?: JSONObject()
    val stlOutput = outputs.optJSONObject("stl") ?: JSONObject()
    val threeMfOutput = outputs.optJSONObject("three_mf") ?: JSONObject()
    val selectedOutput = if (preferThreeMf) threeMfOutput else stlOutput
    val selectedPath = selectedOutput.optString("path").takeIf { it.isNotBlank() && it != "null" }
    val selectedFile = selectedPath?.let { workspaceDir.resolve(it) }
    val selectedHash = selectedOutput.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
    val selectedByteSize = selectedOutput.optLong("byte_size", -1L)
    val actualByteSize = selectedFile?.takeIf { it.isFile }?.length()
    val actualHash = selectedFile?.takeIf { it.isFile && it.length() > 0L }?.sha256Hex()
    val errors = buildList {
        if (!handoff.optBoolean("allowed", false)) add("workspace_handoff_blocked")
        if (!handoff.optBoolean("metric", false)) add("workspace_handoff_not_metric")
        if (!handoff.optBoolean("workspace_handoff_ready", false)) add("workspace_handoff_not_ready")
        if (selectedPath == null) add("workspace_import_model_path_missing")
        if (selectedFile?.isFile != true) add("workspace_import_model_file_missing")
        if ((selectedFile?.length() ?: 0L) <= 0L) add("workspace_import_model_file_empty")
        if (selectedByteSize <= 0L) add("workspace_import_model_byte_size_missing")
        if (selectedByteSize > 0L && actualByteSize != null && selectedByteSize != actualByteSize) {
            add("workspace_import_model_byte_size_mismatch")
        }
        if (selectedHash.isNullOrBlank()) add("workspace_import_model_hash_missing")
        if (!selectedHash.isNullOrBlank() && selectedHash != actualHash) add("workspace_import_model_hash_mismatch")
    }.distinct()
    val accepted = errors.isEmpty()
    val plan = ScannerWorkspaceImportPlan(
        allowed = accepted,
        metric = accepted,
        modelPath = if (accepted) selectedFile?.absolutePath else null,
        modelLabel = if (accepted) "MobileSlicer scan" else null,
        status = if (accepted) "workspace_import_ready" else "workspace_import_blocked",
        errors = errors,
        warnings = if (accepted) listOf("validated_scanner_handoff_required_before_import") else emptyList()
    )
    writeScannerWorkspaceImportReceipt(workspaceDir, plan, imported = false)
    return plan
}

internal fun writeScannerWorkspaceImportReceipt(
    workspaceDir: File,
    plan: ScannerWorkspaceImportPlan,
    imported: Boolean,
    loadMessage: String? = null
) {
    workspaceDir.resolve(SCANNER_WORKSPACE_IMPORT_RECEIPT_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerWorkspaceImportReceiptJson(plan, imported, loadMessage).toString(2))
    }
}

internal fun scannerWorkspaceImportReceiptJson(
    plan: ScannerWorkspaceImportPlan,
    imported: Boolean,
    loadMessage: String?
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", plan.allowed)
        .put("metric", plan.metric)
        .put("workspace_import_ready", plan.allowed)
        .put("shared_workspace_import_invoked", imported)
        .put("status", if (imported) "workspace_import_invoked" else plan.status)
        .put("errors", JSONArray(plan.errors))
        .put("warnings", JSONArray(plan.warnings))
        .put("model_path", plan.modelPath ?: JSONObject.NULL)
        .put("model_label", plan.modelLabel ?: JSONObject.NULL)
        .put("load_message", loadMessage ?: JSONObject.NULL)
        .put(
            "import_contract",
            JSONObject()
                .put("source_handoff", SCANNER_WORKSPACE_HANDOFF_PATH)
                .put("requires_workspace_handoff_ready", true)
                .put("requires_metric_model", true)
                .put("requires_hash_match", true)
        )

private fun blockedScannerWorkspaceImport(errors: List<String>): ScannerWorkspaceImportPlan =
    ScannerWorkspaceImportPlan(
        allowed = false,
        metric = false,
        modelPath = null,
        modelLabel = null,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList()
    )

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
