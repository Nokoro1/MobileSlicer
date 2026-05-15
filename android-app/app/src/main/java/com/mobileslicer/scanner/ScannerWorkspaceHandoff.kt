package com.mobileslicer.scanner

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_WORKSPACE_HANDOFF_PATH = "reports/workspace_handoff.json"

internal data class ScannerWorkspaceHandoffLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val requireStl: Boolean = true,
    val requireThreeMf: Boolean = true
)

internal data class ScannerWorkspaceHandoffResult(
    val allowed: Boolean,
    val metric: Boolean,
    val workspaceHandoffReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val stlPath: String?,
    val threeMfPath: String?,
    val vertexCount: Int,
    val triangleCount: Int,
    val units: String,
    val stlByteSize: Long,
    val threeMfByteSize: Long,
    val stlSha256: String?,
    val threeMfSha256: String?,
    val verifiedStlTriangleCount: Int,
    val verifiedThreeMfVertexCount: Int,
    val verifiedThreeMfTriangleCount: Int
)

internal fun validateScannerWorkspaceHandoff(
    workspaceDir: File,
    limits: ScannerWorkspaceHandoffLimits = ScannerWorkspaceHandoffLimits()
): ScannerWorkspaceHandoffResult {
    val exportManifest = workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (exportManifest == null) {
        val result = blockedScannerWorkspaceHandoff(listOf("metric_export_manifest_missing"))
        writeScannerWorkspaceHandoff(workspaceDir, result, limits)
        return result
    }

    val outputs = exportManifest.optJSONObject("outputs") ?: JSONObject()
    val stlOutput = outputs.optJSONObject("stl") ?: JSONObject()
    val threeMfOutput = outputs.optJSONObject("three_mf") ?: JSONObject()
    val verification = exportManifest.optJSONObject("verification") ?: JSONObject()
    val stlPath = stlOutput.optString("path")
        .takeIf { it.isNotBlank() && it != "null" }
        ?: exportManifest.optString("stl_path").takeIf { it.isNotBlank() && it != "null" }
    val threeMfPath = threeMfOutput.optString("path")
        .takeIf { it.isNotBlank() && it != "null" }
        ?: exportManifest.optString("three_mf_path").takeIf { it.isNotBlank() && it != "null" }
    val stlFile = stlPath?.let { workspaceDir.resolve(it) }
    val threeMfFile = threeMfPath?.let { workspaceDir.resolve(it) }
    val vertexCount = exportManifest.optInt("vertex_count", 0)
    val triangleCount = exportManifest.optInt("triangle_count", 0)
    val units = exportManifest.optString("units", "unknown")
    val manifestStlSha = stlOutput.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
    val manifestThreeMfSha = threeMfOutput.optString("sha256").takeIf { it.isNotBlank() && it != "null" }
    val manifestStlByteSize = stlOutput.optLong("byte_size", 0L)
    val manifestThreeMfByteSize = threeMfOutput.optLong("byte_size", 0L)
    val manifestStlTriangleCount = stlOutput.optInt("verified_triangle_count", -1)
    val manifestThreeMfVertexCount = threeMfOutput.optInt("verified_vertex_count", -1)
    val manifestThreeMfTriangleCount = threeMfOutput.optInt("verified_triangle_count", -1)
    val actualStlByteSize = stlFile?.takeIf { it.isFile }?.length() ?: 0L
    val actualThreeMfByteSize = threeMfFile?.takeIf { it.isFile }?.length() ?: 0L
    val actualStlSha = stlFile?.takeIf { it.isFile && actualStlByteSize > 0L }?.sha256Hex()
    val actualThreeMfSha = threeMfFile?.takeIf { it.isFile && actualThreeMfByteSize > 0L }?.sha256Hex()
    val actualStlTriangleCount = stlFile?.takeIf { it.isFile }?.countAsciiStlTriangles() ?: 0
    val actualThreeMfStats = threeMfFile?.takeIf { it.isFile }?.readThreeMfHandoffStats() ?: ThreeMfHandoffStats()
    val errors = buildList {
        if (!exportManifest.optBoolean("allowed", false)) add("metric_export_blocked")
        if (!exportManifest.optBoolean("metric", false)) add("metric_export_not_metric")
        if (exportManifest.optBoolean("workspace_handoff_ready", true)) add("export_manifest_handoff_ready_before_handoff_gate")
        if (limits.requireStl && !exportManifest.optBoolean("stl_exported", false)) add("stl_export_missing")
        if (limits.requireThreeMf && !exportManifest.optBoolean("three_mf_exported", false)) add("three_mf_export_missing")
        if (limits.requireStl && stlFile?.isFile != true) add("stl_file_missing")
        if (limits.requireThreeMf && threeMfFile?.isFile != true) add("three_mf_file_missing")
        if (actualStlByteSize <= 0L && limits.requireStl) add("stl_file_empty")
        if (actualThreeMfByteSize <= 0L && limits.requireThreeMf) add("three_mf_file_empty")
        if (limits.requireStl && manifestStlSha.isNullOrBlank()) add("stl_hash_missing")
        if (limits.requireThreeMf && manifestThreeMfSha.isNullOrBlank()) add("three_mf_hash_missing")
        if (limits.requireStl && !manifestStlSha.isNullOrBlank() && manifestStlSha != actualStlSha) add("stl_hash_mismatch")
        if (limits.requireThreeMf && !manifestThreeMfSha.isNullOrBlank() && manifestThreeMfSha != actualThreeMfSha) {
            add("three_mf_hash_mismatch")
        }
        if (limits.requireStl && manifestStlByteSize > 0L && manifestStlByteSize != actualStlByteSize) add("stl_byte_size_mismatch")
        if (limits.requireThreeMf && manifestThreeMfByteSize > 0L && manifestThreeMfByteSize != actualThreeMfByteSize) {
            add("three_mf_byte_size_mismatch")
        }
        if (limits.requireStl && manifestStlTriangleCount >= 0 && manifestStlTriangleCount != actualStlTriangleCount) {
            add("stl_triangle_count_mismatch")
        }
        if (limits.requireStl && actualStlTriangleCount != triangleCount) add("stl_triangle_count_does_not_match_manifest")
        if (limits.requireThreeMf && !actualThreeMfStats.hasModelEntry) add("three_mf_model_missing")
        if (limits.requireThreeMf && actualThreeMfStats.units != "millimeter") add("three_mf_units_not_millimeter")
        if (limits.requireThreeMf && manifestThreeMfVertexCount >= 0 && manifestThreeMfVertexCount != actualThreeMfStats.vertexCount) {
            add("three_mf_vertex_count_mismatch")
        }
        if (limits.requireThreeMf && manifestThreeMfTriangleCount >= 0 && manifestThreeMfTriangleCount != actualThreeMfStats.triangleCount) {
            add("three_mf_triangle_count_mismatch")
        }
        if (limits.requireThreeMf && actualThreeMfStats.vertexCount != vertexCount) add("three_mf_vertex_count_does_not_match_manifest")
        if (limits.requireThreeMf && actualThreeMfStats.triangleCount != triangleCount) add("three_mf_triangle_count_does_not_match_manifest")
        if (!verification.optBoolean("stl_file_exists", false) && limits.requireStl) add("export_manifest_stl_verification_missing")
        if (!verification.optBoolean("three_mf_file_exists", false) && limits.requireThreeMf) add("export_manifest_3mf_verification_missing")
        if (!verification.optBoolean("stl_triangle_count_matches_candidate", false) && limits.requireStl) {
            add("export_manifest_stl_triangle_verification_missing")
        }
        if (!verification.optBoolean("three_mf_vertex_count_matches_candidate", false) && limits.requireThreeMf) {
            add("export_manifest_3mf_vertex_verification_missing")
        }
        if (!verification.optBoolean("three_mf_triangle_count_matches_candidate", false) && limits.requireThreeMf) {
            add("export_manifest_3mf_triangle_verification_missing")
        }
        if (!verification.optBoolean("hashes_recorded", false)) add("export_manifest_hash_verification_missing")
        if (vertexCount < limits.minVertexCount) add("handoff_vertex_count_low")
        if (triangleCount < limits.minTriangleCount) add("handoff_triangle_count_low")
        if (units != "millimeters") add("handoff_units_not_millimeters")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerWorkspaceHandoffResult(
        allowed = accepted,
        metric = accepted,
        workspaceHandoffReady = accepted,
        status = if (accepted) "workspace_handoff_ready" else "workspace_handoff_blocked",
        errors = if (accepted) emptyList() else errors,
        warnings = buildList {
            if (accepted) add("handoff_manifest_ready_for_workspace_integration")
            if (accepted) add("shared_workspace_import_not_invoked_by_scanner_gate")
        },
        stlPath = if (accepted) stlPath else null,
        threeMfPath = if (accepted) threeMfPath else null,
        vertexCount = if (accepted) vertexCount else 0,
        triangleCount = if (accepted) triangleCount else 0,
        units = units,
        stlByteSize = actualStlByteSize,
        threeMfByteSize = actualThreeMfByteSize,
        stlSha256 = actualStlSha,
        threeMfSha256 = actualThreeMfSha,
        verifiedStlTriangleCount = actualStlTriangleCount,
        verifiedThreeMfVertexCount = actualThreeMfStats.vertexCount,
        verifiedThreeMfTriangleCount = actualThreeMfStats.triangleCount
    )
    writeScannerWorkspaceHandoff(workspaceDir, result, limits)
    return result
}

private fun blockedScannerWorkspaceHandoff(errors: List<String>): ScannerWorkspaceHandoffResult =
    ScannerWorkspaceHandoffResult(
        allowed = false,
        metric = false,
        workspaceHandoffReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        stlPath = null,
        threeMfPath = null,
        vertexCount = 0,
        triangleCount = 0,
        units = "millimeters",
        stlByteSize = 0,
        threeMfByteSize = 0,
        stlSha256 = null,
        threeMfSha256 = null,
        verifiedStlTriangleCount = 0,
        verifiedThreeMfVertexCount = 0,
        verifiedThreeMfTriangleCount = 0
    )

private data class ThreeMfHandoffStats(
    val hasModelEntry: Boolean = false,
    val units: String? = null,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0
)

private fun File.countAsciiStlTriangles(): Int =
    readText().let { text -> Regex("""\bfacet\s+normal\b""").findAll(text).count() }

private fun File.readThreeMfHandoffStats(): ThreeMfHandoffStats =
    runCatching {
        ZipFile(this).use { zip ->
            val entry = zip.getEntry("3D/3dmodel.model") ?: return@use ThreeMfHandoffStats()
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            ThreeMfHandoffStats(
                hasModelEntry = true,
                units = Regex("""<model[^>]*\bunit="([^"]+)"""").find(xml)?.groupValues?.getOrNull(1),
                vertexCount = Regex("""<vertex\s""").findAll(xml).count(),
                triangleCount = Regex("""<triangle\s""").findAll(xml).count()
            )
        }
    }.getOrElse {
        ThreeMfHandoffStats()
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

private fun writeScannerWorkspaceHandoff(
    workspaceDir: File,
    result: ScannerWorkspaceHandoffResult,
    limits: ScannerWorkspaceHandoffLimits
) {
    workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerWorkspaceHandoffJson(result, limits).toString(2))
    }
}

internal fun scannerWorkspaceHandoffJson(
    result: ScannerWorkspaceHandoffResult,
    limits: ScannerWorkspaceHandoffLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("workspace_handoff_ready", result.workspaceHandoffReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("stl_path", result.stlPath ?: JSONObject.NULL)
        .put("three_mf_path", result.threeMfPath ?: JSONObject.NULL)
        .put("vertex_count", result.vertexCount)
        .put("triangle_count", result.triangleCount)
        .put("units", result.units)
        .put(
            "outputs",
            JSONObject()
                .put(
                    "stl",
                    JSONObject()
                        .put("path", result.stlPath ?: JSONObject.NULL)
                        .put("byte_size", result.stlByteSize)
                        .put("sha256", result.stlSha256 ?: JSONObject.NULL)
                        .put("verified_triangle_count", result.verifiedStlTriangleCount)
                )
                .put(
                    "three_mf",
                    JSONObject()
                        .put("path", result.threeMfPath ?: JSONObject.NULL)
                        .put("byte_size", result.threeMfByteSize)
                        .put("sha256", result.threeMfSha256 ?: JSONObject.NULL)
                        .put("verified_vertex_count", result.verifiedThreeMfVertexCount)
                        .put("verified_triangle_count", result.verifiedThreeMfTriangleCount)
                )
        )
        .put(
            "verification",
            JSONObject()
                .put("hashes_match_export_manifest", !result.stlSha256.isNullOrBlank() && !result.threeMfSha256.isNullOrBlank())
                .put("stl_triangle_count_matches_manifest", result.verifiedStlTriangleCount == result.triangleCount)
                .put("three_mf_vertex_count_matches_manifest", result.verifiedThreeMfVertexCount == result.vertexCount)
                .put("three_mf_triangle_count_matches_manifest", result.verifiedThreeMfTriangleCount == result.triangleCount)
                .put("workspace_import_gate_passed", result.workspaceHandoffReady)
        )
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("require_stl", limits.requireStl)
                .put("require_three_mf", limits.requireThreeMf)
        )
        .put(
            "handoff_contract",
            JSONObject()
                .put("source_export_manifest", SCANNER_EXPORT_MANIFEST_PATH)
                .put("metric_model", true)
                .put("validated_for_workspace_import", result.workspaceHandoffReady)
                .put("shared_workspace_import_invoked", false)
        )
