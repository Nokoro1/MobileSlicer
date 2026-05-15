package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerWorkspaceImportTest {
    @Test
    fun importBlocksWhenHandoffIsMissing() {
        val workspaceDir = tempDir("scanner-import-missing-handoff")

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertFalse(plan.allowed)
        assertFalse(plan.metric)
        assertNull(plan.modelPath)
        assertTrue(plan.errors.contains("workspace_handoff_missing"))
        assertReceipt(workspaceDir, allowed = false, invoked = false)
    }

    @Test
    fun importBlocksWhenHandoffIsNotReady() {
        val workspaceDir = tempDir("scanner-import-blocked-handoff")
        writeHandoff(workspaceDir, allowed = false, metric = false, ready = false, writeModel = false)

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertFalse(plan.allowed)
        assertTrue(plan.errors.contains("workspace_handoff_blocked"))
        assertTrue(plan.errors.contains("workspace_handoff_not_metric"))
        assertTrue(plan.errors.contains("workspace_handoff_not_ready"))
        assertTrue(plan.errors.contains("workspace_import_model_file_missing"))
        assertReceipt(workspaceDir, allowed = false, invoked = false)
    }

    @Test
    fun importAllowsValidatedMetricHandoff() {
        val workspaceDir = tempDir("scanner-import-ready")
        val modelFile = writeHandoff(workspaceDir, allowed = true, metric = true, ready = true, writeModel = true)

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertTrue(plan.allowed)
        assertTrue(plan.metric)
        assertEquals(modelFile.absolutePath, plan.modelPath)
        assertEquals("workspace_import_ready", plan.status)
        assertTrue(plan.errors.isEmpty())
        assertReceipt(workspaceDir, allowed = true, invoked = false)

        writeScannerWorkspaceImportReceipt(workspaceDir, plan, imported = true, loadMessage = "loaded")
        assertReceipt(workspaceDir, allowed = true, invoked = true)
    }

    @Test
    fun importBlocksMissingOutputHash() {
        val workspaceDir = tempDir("scanner-import-missing-hash")
        writeHandoff(
            workspaceDir = workspaceDir,
            allowed = true,
            metric = true,
            ready = true,
            writeModel = true,
            includeHash = false
        )

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertFalse(plan.allowed)
        assertTrue(plan.errors.contains("workspace_import_model_hash_missing"))
    }

    @Test
    fun importBlocksTamperedOutput() {
        val workspaceDir = tempDir("scanner-import-tampered")
        val modelFile = writeHandoff(workspaceDir, allowed = true, metric = true, ready = true, writeModel = true)
        modelFile.appendText("tampered\n")

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertFalse(plan.allowed)
        assertTrue(plan.errors.contains("workspace_import_model_hash_mismatch"))
        assertNull(plan.modelPath)
    }

    @Test
    fun importBlocksOutputByteSizeMismatch() {
        val workspaceDir = tempDir("scanner-import-size-mismatch")
        writeHandoff(
            workspaceDir = workspaceDir,
            allowed = true,
            metric = true,
            ready = true,
            writeModel = true,
            declaredByteSizeOffset = 10L
        )

        val plan = planScannerWorkspaceImport(workspaceDir)

        assertFalse(plan.allowed)
        assertTrue(plan.errors.contains("workspace_import_model_byte_size_mismatch"))
    }

    private fun writeHandoff(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        ready: Boolean,
        writeModel: Boolean,
        includeHash: Boolean = true,
        declaredByteSizeOffset: Long = 0L
    ): File {
        workspaceDir.resolve("reports").mkdirs()
        val modelFile = workspaceDir.resolve(SCANNER_METRIC_STL_PATH)
        if (writeModel) {
            modelFile.parentFile?.mkdirs()
            modelFile.writeText(testStlText())
        }
        workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("workspace_handoff_ready", ready)
                .put(
                    "outputs",
                    JSONObject()
                        .put(
                            "stl",
                            JSONObject()
                                .put("path", SCANNER_METRIC_STL_PATH)
                                .put("byte_size", if (modelFile.isFile) modelFile.length() + declaredByteSizeOffset else 0L)
                                .put("sha256", if (includeHash && modelFile.isFile) modelFile.sha256Hex() else JSONObject.NULL)
                        )
                        .put("three_mf", JSONObject())
                )
                .toString(2)
        )
        return modelFile
    }

    private fun assertReceipt(workspaceDir: File, allowed: Boolean, invoked: Boolean) {
        val receipt = JSONObject(workspaceDir.resolve(SCANNER_WORKSPACE_IMPORT_RECEIPT_PATH).readText())
        assertEquals(allowed, receipt.getBoolean("allowed"))
        assertEquals(allowed, receipt.getBoolean("workspace_import_ready"))
        assertEquals(invoked, receipt.getBoolean("shared_workspace_import_invoked"))
        assertTrue(receipt.getJSONObject("import_contract").getBoolean("requires_workspace_handoff_ready"))
        assertTrue(receipt.getJSONObject("import_contract").getBoolean("requires_metric_model"))
        assertTrue(receipt.getJSONObject("import_contract").getBoolean("requires_hash_match"))
    }

    private fun testStlText(): String =
        buildString {
            appendLine("solid scanner_workspace_import_test")
            appendLine("  facet normal 0 0 1")
            appendLine("    outer loop")
            appendLine("      vertex 0 0 0")
            appendLine("      vertex 1 0 0")
            appendLine("      vertex 0 1 0")
            appendLine("    endloop")
            appendLine("  endfacet")
            appendLine("endsolid scanner_workspace_import_test")
        }

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

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
