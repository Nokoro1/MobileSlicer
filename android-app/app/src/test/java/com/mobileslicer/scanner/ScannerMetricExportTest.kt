package com.mobileslicer.scanner

import com.mobileslicer.viewer.StlMeshParser
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMetricExportTest {
    @Test
    fun exportBlocksWhenSlicerLoadValidationIsMissing() {
        val workspaceDir = tempDir("scanner-export-missing-validation")

        val result = exportScannerMetricMesh(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.stlExported)
        assertFalse(result.threeMfExported)
        assertFalse(result.workspaceHandoffReady)
        assertTrue(result.errors.contains("slicer_load_validation_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH).isFile)
    }

    @Test
    fun exportBlocksWhenMetricCandidateIsMissing() {
        val workspaceDir = tempDir("scanner-export-missing-candidate")
        writeSlicerLoadValidation(workspaceDir, allowed = true)

        val result = exportScannerMetricMesh(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("metric_mesh_candidate_missing"))
    }

    @Test
    fun exportBlocksFailedSlicerLoadValidation() {
        val workspaceDir = tempDir("scanner-export-validation-blocked")
        writeSlicerLoadValidation(workspaceDir, allowed = false)
        writeMetricMeshCandidate(workspaceDir)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("slicer_load_validation_blocked"))
        assertTrue(result.errors.contains("slicer_load_not_validated"))
        assertTrue(result.errors.contains("workspace_handoff_blocked_until_metric_export"))
    }

    @Test
    fun exportWritesMetricStlAndThreeMfButKeepsWorkspaceBlocked() {
        val workspaceDir = tempDir("scanner-export-ready")
        writeSlicerLoadValidation(workspaceDir, allowed = true)
        writeMetricMeshCandidate(workspaceDir)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.stlExported)
        assertTrue(result.threeMfExported)
        assertFalse(result.workspaceHandoffReady)
        assertTrue(result.stlByteSize > 0L)
        assertTrue(result.threeMfByteSize > 0L)
        assertTrue(result.stlSha256.orEmpty().length == 64)
        assertTrue(result.threeMfSha256.orEmpty().length == 64)
        assertEquals(4, result.verifiedStlTriangleCount)
        assertEquals(4, result.verifiedThreeMfVertexCount)
        assertEquals(4, result.verifiedThreeMfTriangleCount)
        assertTrue(result.errors.contains("workspace_handoff_gate_required_before_import"))

        val stlFile = workspaceDir.resolve(SCANNER_METRIC_STL_PATH)
        val threeMfFile = workspaceDir.resolve(SCANNER_METRIC_3MF_PATH)
        assertTrue(stlFile.isFile)
        assertTrue(threeMfFile.isFile)
        assertEquals(4, StlMeshParser.parse(stlFile).triangleCount)
        ZipFile(threeMfFile).use { zip ->
            assertTrue(zip.getEntry("[Content_Types].xml") != null)
            assertTrue(zip.getEntry("_rels/.rels") != null)
            assertTrue(zip.getEntry("3D/3dmodel.model") != null)
        }

        val manifest = JSONObject(workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH).readText())
        assertTrue(manifest.getBoolean("allowed"))
        assertTrue(manifest.getBoolean("stl_exported"))
        assertTrue(manifest.getBoolean("three_mf_exported"))
        assertFalse(manifest.getBoolean("workspace_handoff_ready"))
        assertEquals(4, manifest.getJSONObject("outputs").getJSONObject("stl").getInt("verified_triangle_count"))
        assertEquals(4, manifest.getJSONObject("outputs").getJSONObject("three_mf").getInt("verified_vertex_count"))
        assertEquals(4, manifest.getJSONObject("outputs").getJSONObject("three_mf").getInt("verified_triangle_count"))
        assertTrue(manifest.getJSONObject("outputs").getJSONObject("stl").getLong("byte_size") > 0L)
        assertTrue(manifest.getJSONObject("outputs").getJSONObject("three_mf").getLong("byte_size") > 0L)
        assertEquals(64, manifest.getJSONObject("outputs").getJSONObject("stl").getString("sha256").length)
        assertEquals(64, manifest.getJSONObject("outputs").getJSONObject("three_mf").getString("sha256").length)
        assertTrue(manifest.getJSONObject("verification").getBoolean("hashes_recorded"))
        assertTrue(manifest.getJSONObject("verification").getBoolean("stl_triangle_count_matches_candidate"))
        assertTrue(manifest.getJSONObject("verification").getBoolean("three_mf_triangle_count_matches_candidate"))
        assertEquals(SCANNER_SLICER_LOAD_VALIDATION_PATH, manifest.getJSONObject("source_contract").getString("slicer_load_validation"))
        assertFalse(manifest.getJSONObject("handoff_contract").getBoolean("workspace_handoff_allowed"))
    }

    @Test
    fun exportBlocksWhenSlicerLoadContractIsNotReady() {
        val workspaceDir = tempDir("scanner-export-contract-not-ready")
        writeSlicerLoadValidation(workspaceDir, allowed = true, internalImportReady = false)
        writeMetricMeshCandidate(workspaceDir)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("slicer_load_contract_not_ready"))
    }

    @Test
    fun exportBlocksPrematureSlicerLoadExportFlags() {
        val workspaceDir = tempDir("scanner-export-premature-flags")
        writeSlicerLoadValidation(workspaceDir, allowed = true, exportReady = true, workspaceHandoffReady = true)
        writeMetricMeshCandidate(workspaceDir)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("slicer_load_export_ready_before_writer"))
        assertTrue(result.errors.contains("slicer_load_handoff_ready_before_writer"))
    }

    @Test
    fun exportBlocksSlicerLoadCountMismatch() {
        val workspaceDir = tempDir("scanner-export-slicer-count-mismatch")
        writeSlicerLoadValidation(workspaceDir, allowed = true, vertexCount = 5, triangleCount = 4)
        writeMetricMeshCandidate(workspaceDir)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("export_vertex_count_mismatch_slicer_contract"))
    }

    @Test
    fun exportRejectsVisualOnlyCandidateGeometry() {
        val workspaceDir = tempDir("scanner-export-visual")
        writeSlicerLoadValidation(workspaceDir, allowed = true)
        writeMetricMeshCandidate(workspaceDir, visualOnly = true)

        val result = exportScannerMetricMesh(
            workspaceDir,
            ScannerMetricExportLimits(minVertexCount = 4, minTriangleCount = 4)
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("export_vertex_provenance_rejected"))
    }

    private fun writeSlicerLoadValidation(
        workspaceDir: File,
        allowed: Boolean,
        internalImportReady: Boolean = allowed,
        exportReady: Boolean = false,
        workspaceHandoffReady: Boolean = false,
        vertexCount: Int = 4,
        triangleCount: Int = 4
    ) {
        workspaceDir.resolve("reports").mkdirs()
        workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", allowed)
                .put("slicer_load_validated", allowed)
                .put("export_ready", exportReady)
                .put("workspace_handoff_ready", workspaceHandoffReady)
                .put("vertex_count", vertexCount)
                .put("triangle_count", triangleCount)
                .put("units", "millimeters")
                .put("internal_import_contract_ready", internalImportReady)
                .toString(2)
        )
    }

    private fun writeMetricMeshCandidate(workspaceDir: File, visualOnly: Boolean = false) {
        workspaceDir.resolve("mesh").mkdirs()
        workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("triangle_mesh_candidate_ready", true)
                .put("vertex_count", 4)
                .put("triangle_count", 4)
                .put(
                    "vertices",
                    JSONArray()
                        .put(vertex("v0", 0.0, 0.0, 0.0))
                        .put(vertex("v1", 10.0, 0.0, 0.0))
                        .put(vertex("v2", 0.0, 10.0, 0.0))
                        .put(vertex("v3", 0.0, 0.0, 10.0, visualOnly))
                )
                .put(
                    "triangles",
                    JSONArray()
                        .put(triangle("t0", listOf("v0", "v1", "v2")))
                        .put(triangle("t1", listOf("v0", "v1", "v3")))
                        .put(triangle("t2", listOf("v1", "v2", "v3")))
                        .put(triangle("t3", listOf("v0", "v2", "v3")))
                )
                .toString(2)
        )
    }

    private fun vertex(id: String, x: Double, y: Double, z: Double, visualOnly: Boolean = false): JSONObject =
        JSONObject()
            .put("vertex_id", id)
            .put("source_point_id", "point_$id")
            .put("source_track_id", "track_$id")
            .put("xyz_mm", JSONObject().put("x", x).put("y", y).put("z", z))
            .put("provenance_class", if (visualOnly) "visual_only" else "measured_high")

    private fun triangle(id: String, vertexIds: List<String>): JSONObject =
        JSONObject()
            .put("triangle_id", id)
            .put("vertex_ids", JSONArray(vertexIds))
            .put("provenance_class", "measured_high")

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
