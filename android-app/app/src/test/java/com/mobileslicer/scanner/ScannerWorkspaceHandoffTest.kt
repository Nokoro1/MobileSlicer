package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerWorkspaceHandoffTest {
    @Test
    fun handoffBlocksWhenExportManifestIsMissing() {
        val workspaceDir = tempDir("scanner-handoff-missing")

        val result = validateScannerWorkspaceHandoff(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.workspaceHandoffReady)
        assertTrue(result.errors.contains("metric_export_manifest_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH).isFile)
    }

    @Test
    fun handoffBlocksFailedExportManifest() {
        val workspaceDir = tempDir("scanner-handoff-blocked")
        writeExportManifest(workspaceDir, allowed = false, writeFiles = false)

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("metric_export_blocked"))
        assertTrue(result.errors.contains("stl_file_missing"))
        assertTrue(result.errors.contains("three_mf_file_missing"))
    }

    @Test
    fun handoffAcceptsValidatedMetricExports() {
        val workspaceDir = tempDir("scanner-handoff-ready")
        writeExportManifest(workspaceDir, allowed = true, writeFiles = true)

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.workspaceHandoffReady)
        assertTrue(result.stlByteSize > 0L)
        assertTrue(result.threeMfByteSize > 0L)
        assertEquals(4, result.verifiedStlTriangleCount)
        assertEquals(4, result.verifiedThreeMfVertexCount)
        assertEquals(4, result.verifiedThreeMfTriangleCount)
        assertEquals(64, result.stlSha256.orEmpty().length)
        assertEquals(64, result.threeMfSha256.orEmpty().length)
        assertTrue(result.warnings.contains("handoff_manifest_ready_for_workspace_integration"))
        assertTrue(result.warnings.contains("shared_workspace_import_not_invoked_by_scanner_gate"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("workspace_handoff_ready"))
        assertEquals(4, json.getJSONObject("outputs").getJSONObject("stl").getInt("verified_triangle_count"))
        assertEquals(4, json.getJSONObject("outputs").getJSONObject("three_mf").getInt("verified_vertex_count"))
        assertTrue(json.getJSONObject("verification").getBoolean("workspace_import_gate_passed"))
        assertTrue(json.getJSONObject("handoff_contract").getBoolean("validated_for_workspace_import"))
        assertFalse(json.getJSONObject("handoff_contract").getBoolean("shared_workspace_import_invoked"))
    }

    @Test
    fun handoffBlocksTamperedExportFiles() {
        val workspaceDir = tempDir("scanner-handoff-tampered")
        writeExportManifest(workspaceDir, allowed = true, writeFiles = true)
        workspaceDir.resolve(SCANNER_METRIC_STL_PATH).appendText("tampered\n")

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("stl_hash_mismatch"))
        assertTrue(result.errors.contains("stl_byte_size_mismatch"))
    }

    @Test
    fun handoffBlocksMissingManifestHashes() {
        val workspaceDir = tempDir("scanner-handoff-missing-hashes")
        writeExportManifest(workspaceDir, allowed = true, writeFiles = true, includeHashes = false)

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("stl_hash_missing"))
        assertTrue(result.errors.contains("three_mf_hash_missing"))
        assertTrue(result.errors.contains("export_manifest_hash_verification_missing"))
    }

    @Test
    fun handoffBlocksManifestCountMismatch() {
        val workspaceDir = tempDir("scanner-handoff-count-mismatch")
        writeExportManifest(workspaceDir, allowed = true, writeFiles = true, manifestTriangleCount = 5)

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("stl_triangle_count_does_not_match_manifest"))
        assertTrue(result.errors.contains("three_mf_triangle_count_does_not_match_manifest"))
    }

    @Test
    fun handoffBlocksPrematureExportManifestHandoffReady() {
        val workspaceDir = tempDir("scanner-handoff-premature")
        writeExportManifest(workspaceDir, allowed = true, writeFiles = true, workspaceHandoffReady = true)

        val result = validateScannerWorkspaceHandoff(
            workspaceDir,
            ScannerWorkspaceHandoffLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("export_manifest_handoff_ready_before_handoff_gate"))
    }

    private fun writeExportManifest(
        workspaceDir: File,
        allowed: Boolean,
        writeFiles: Boolean,
        includeHashes: Boolean = true,
        manifestTriangleCount: Int = 4,
        workspaceHandoffReady: Boolean = false
    ) {
        workspaceDir.resolve("reports").mkdirs()
        val stlFile = workspaceDir.resolve(SCANNER_METRIC_STL_PATH)
        val threeMfFile = workspaceDir.resolve(SCANNER_METRIC_3MF_PATH)
        if (writeFiles) {
            stlFile.also {
                it.parentFile?.mkdirs()
                it.writeText(testStlText())
            }
            threeMfFile.also {
                it.parentFile?.mkdirs()
                writeTestThreeMf(it)
            }
        }
        val stlHash = if (includeHashes && stlFile.isFile) stlFile.sha256Hex() else null
        val threeMfHash = if (includeHashes && threeMfFile.isFile) threeMfFile.sha256Hex() else null
        workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("stl_exported", allowed)
                .put("three_mf_exported", allowed)
                .put("workspace_handoff_ready", workspaceHandoffReady)
                .put("stl_path", SCANNER_METRIC_STL_PATH)
                .put("three_mf_path", SCANNER_METRIC_3MF_PATH)
                .put("vertex_count", 4)
                .put("triangle_count", manifestTriangleCount)
                .put("units", "millimeters")
                .put(
                    "outputs",
                    JSONObject()
                        .put(
                            "stl",
                            JSONObject()
                                .put("path", SCANNER_METRIC_STL_PATH)
                                .put("byte_size", if (stlFile.isFile) stlFile.length() else 0L)
                                .put("sha256", stlHash ?: JSONObject.NULL)
                                .put("verified_triangle_count", 4)
                        )
                        .put(
                            "three_mf",
                            JSONObject()
                                .put("path", SCANNER_METRIC_3MF_PATH)
                                .put("byte_size", if (threeMfFile.isFile) threeMfFile.length() else 0L)
                                .put("sha256", threeMfHash ?: JSONObject.NULL)
                                .put("verified_vertex_count", 4)
                                .put("verified_triangle_count", 4)
                        )
                )
                .put(
                    "verification",
                    JSONObject()
                        .put("stl_file_exists", writeFiles)
                        .put("three_mf_file_exists", writeFiles)
                        .put("stl_triangle_count_matches_candidate", true)
                        .put("three_mf_vertex_count_matches_candidate", true)
                        .put("three_mf_triangle_count_matches_candidate", true)
                        .put("hashes_recorded", includeHashes)
                )
                .toString(2)
        )
    }

    private fun testStlText(): String =
        buildString {
            appendLine("solid scanner_handoff_test")
            repeat(4) { index ->
                appendLine("  facet normal 0 0 1")
                appendLine("    outer loop")
                appendLine("      vertex 0 0 $index")
                appendLine("      vertex 1 0 $index")
                appendLine("      vertex 0 1 $index")
                appendLine("    endloop")
                appendLine("  endfacet")
            }
            appendLine("endsolid scanner_handoff_test")
        }

    private fun writeTestThreeMf(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeTextEntry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types/>""")
            zip.writeTextEntry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships/>""")
            zip.writeTextEntry(
                "3D/3dmodel.model",
                """<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
  <resources>
    <object id="1" type="model">
      <mesh>
        <vertices>
          <vertex x="0" y="0" z="0"/>
          <vertex x="1" y="0" z="0"/>
          <vertex x="0" y="1" z="0"/>
          <vertex x="0" y="0" z="1"/>
        </vertices>
        <triangles>
          <triangle v1="0" v2="1" v3="2"/>
          <triangle v1="0" v2="1" v3="3"/>
          <triangle v1="1" v2="2" v3="3"/>
          <triangle v1="0" v2="2" v3="3"/>
        </triangles>
      </mesh>
    </object>
  </resources>
  <build><item objectid="1"/></build>
</model>
"""
            )
        }
    }

    private fun ZipOutputStream.writeTextEntry(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
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
