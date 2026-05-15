package com.mobileslicer.scanner

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_METRIC_STL_PATH = "outputs/model_metric.stl"
internal const val SCANNER_METRIC_3MF_PATH = "outputs/model_metric.3mf"
internal const val SCANNER_EXPORT_MANIFEST_PATH = "reports/export_manifest.json"

internal data class ScannerMetricExportLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val requireSlicerLoadValidation: Boolean = true
)

internal data class ScannerMetricExportVertex(
    val id: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val provenanceClass: String
)

internal data class ScannerMetricExportTriangle(
    val id: String,
    val vertexIds: List<String>,
    val provenanceClass: String
)

internal data class ScannerMetricExportResult(
    val allowed: Boolean,
    val metric: Boolean,
    val stlExported: Boolean,
    val threeMfExported: Boolean,
    val workspaceHandoffReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val stlPath: String?,
    val threeMfPath: String?,
    val manifestPath: String,
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

internal fun exportScannerMetricMesh(
    workspaceDir: File,
    limits: ScannerMetricExportLimits = ScannerMetricExportLimits()
): ScannerMetricExportResult {
    val slicerLoad = workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val candidate = workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (slicerLoad == null) {
        val result = blockedScannerMetricExport(listOf("slicer_load_validation_missing"))
        writeScannerMetricExportManifest(workspaceDir, result, limits)
        return result
    }
    if (candidate == null) {
        val result = blockedScannerMetricExport(listOf("metric_mesh_candidate_missing"))
        writeScannerMetricExportManifest(workspaceDir, result, limits)
        return result
    }

    val vertices = parseMetricExportVertices(candidate.optJSONArray("vertices") ?: JSONArray())
    val triangles = parseMetricExportTriangles(candidate.optJSONArray("triangles") ?: JSONArray())
    val vertexIds = vertices.map { it.id }.toSet()
    val slicerVertexCount = slicerLoad.optInt("vertex_count", -1)
    val slicerTriangleCount = slicerLoad.optInt("triangle_count", -1)
    val errors = buildList {
        if (limits.requireSlicerLoadValidation && !slicerLoad.optBoolean("allowed", false)) {
            add("slicer_load_validation_blocked")
        }
        if (!slicerLoad.optBoolean("metric", false)) add("metric_slicer_load_validation_missing")
        if (!slicerLoad.optBoolean("slicer_load_validated", false)) add("slicer_load_not_validated")
        if (!slicerLoad.optBoolean("internal_import_contract_ready", false)) add("slicer_load_contract_not_ready")
        if (slicerLoad.optBoolean("export_ready", true)) add("slicer_load_export_ready_before_writer")
        if (slicerLoad.optBoolean("workspace_handoff_ready", true)) add("slicer_load_handoff_ready_before_writer")
        if (slicerVertexCount >= 0 && slicerVertexCount != vertices.size) add("export_vertex_count_mismatch_slicer_contract")
        if (slicerTriangleCount >= 0 && slicerTriangleCount != triangles.size) add("export_triangle_count_mismatch_slicer_contract")
        if (!candidate.optBoolean("allowed", false)) add("metric_mesh_candidate_blocked")
        if (!candidate.optBoolean("metric", false)) add("metric_mesh_candidate_not_metric")
        if (!candidate.optBoolean("triangle_mesh_candidate_ready", false)) add("triangle_mesh_candidate_not_ready")
        if (vertices.size < limits.minVertexCount) add("export_vertex_count_low")
        if (triangles.size < limits.minTriangleCount) add("export_triangle_count_low")
        if (vertices.any { it.provenanceClass == "visual_only" || it.provenanceClass == "rejected" }) {
            add("export_vertex_provenance_rejected")
        }
        if (triangles.any { it.provenanceClass == "visual_only" || it.provenanceClass == "rejected" }) {
            add("export_triangle_provenance_rejected")
        }
        if (triangles.any { triangle -> triangle.vertexIds.size != 3 || triangle.vertexIds.any { it !in vertexIds } }) {
            add("export_triangle_vertex_reference_invalid")
        }
    }.distinct()
    if (errors.isNotEmpty()) {
        val result = blockedScannerMetricExport(errors + "workspace_handoff_blocked_until_metric_export")
        writeScannerMetricExportManifest(workspaceDir, result, limits)
        return result
    }

    val stlFile = workspaceDir.resolve(SCANNER_METRIC_STL_PATH).also { it.parentFile?.mkdirs() }
    val threeMfFile = workspaceDir.resolve(SCANNER_METRIC_3MF_PATH).also { it.parentFile?.mkdirs() }
    writeMetricAsciiStl(stlFile, vertices.associateBy { it.id }, triangles)
    writeMetricThreeMf(threeMfFile, vertices, triangles)
    val outputVerification = verifyMetricExportOutputs(
        stlFile = stlFile,
        threeMfFile = threeMfFile,
        expectedVertexCount = vertices.size,
        expectedTriangleCount = triangles.size
    )
    if (outputVerification.errors.isNotEmpty()) {
        val result = blockedScannerMetricExport(outputVerification.errors + "workspace_handoff_blocked_until_metric_export")
        writeScannerMetricExportManifest(workspaceDir, result, limits)
        return result
    }
    val result = ScannerMetricExportResult(
        allowed = true,
        metric = true,
        stlExported = true,
        threeMfExported = true,
        workspaceHandoffReady = false,
        status = "metric_exports_ready_workspace_handoff_blocked",
        errors = listOf("workspace_handoff_gate_required_before_import"),
        warnings = listOf(
            "metric_exports_generated_from_validated_candidate",
            "workspace_handoff_remains_blocked"
        ),
        stlPath = SCANNER_METRIC_STL_PATH,
        threeMfPath = SCANNER_METRIC_3MF_PATH,
        manifestPath = SCANNER_EXPORT_MANIFEST_PATH,
        vertexCount = vertices.size,
        triangleCount = triangles.size,
        units = "millimeters",
        stlByteSize = outputVerification.stlByteSize,
        threeMfByteSize = outputVerification.threeMfByteSize,
        stlSha256 = outputVerification.stlSha256,
        threeMfSha256 = outputVerification.threeMfSha256,
        verifiedStlTriangleCount = outputVerification.stlTriangleCount,
        verifiedThreeMfVertexCount = outputVerification.threeMfVertexCount,
        verifiedThreeMfTriangleCount = outputVerification.threeMfTriangleCount
    )
    writeScannerMetricExportManifest(workspaceDir, result, limits)
    return result
}

private fun blockedScannerMetricExport(errors: List<String>): ScannerMetricExportResult =
    ScannerMetricExportResult(
        allowed = false,
        metric = false,
        stlExported = false,
        threeMfExported = false,
        workspaceHandoffReady = false,
        status = "metric_export_blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        stlPath = null,
        threeMfPath = null,
        manifestPath = SCANNER_EXPORT_MANIFEST_PATH,
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

private data class MetricExportOutputVerification(
    val errors: List<String>,
    val stlByteSize: Long = 0,
    val threeMfByteSize: Long = 0,
    val stlSha256: String? = null,
    val threeMfSha256: String? = null,
    val stlTriangleCount: Int = 0,
    val threeMfVertexCount: Int = 0,
    val threeMfTriangleCount: Int = 0
)

private fun verifyMetricExportOutputs(
    stlFile: File,
    threeMfFile: File,
    expectedVertexCount: Int,
    expectedTriangleCount: Int
): MetricExportOutputVerification {
    val errors = mutableListOf<String>()
    if (!stlFile.isFile) errors += "export_stl_missing"
    if (!threeMfFile.isFile) errors += "export_3mf_missing"
    val stlByteSize = stlFile.takeIf { it.isFile }?.length() ?: 0L
    val threeMfByteSize = threeMfFile.takeIf { it.isFile }?.length() ?: 0L
    if (stlByteSize <= 0L) errors += "export_stl_empty"
    if (threeMfByteSize <= 0L) errors += "export_3mf_empty"

    val stlText = stlFile.takeIf { it.isFile }?.readText().orEmpty()
    val stlTriangleCount = Regex("""\bfacet\s+normal\b""").findAll(stlText).count()
    if (stlTriangleCount != expectedTriangleCount) errors += "export_stl_triangle_count_mismatch"

    val threeMfStats = if (threeMfFile.isFile) parseThreeMfStats(threeMfFile) else ThreeMfExportStats()
    if (threeMfStats.hasModelEntry.not()) errors += "export_3mf_model_missing"
    if (threeMfStats.units != "millimeter") errors += "export_3mf_units_not_millimeter"
    if (threeMfStats.vertexCount != expectedVertexCount) errors += "export_3mf_vertex_count_mismatch"
    if (threeMfStats.triangleCount != expectedTriangleCount) errors += "export_3mf_triangle_count_mismatch"

    val stlSha = stlFile.takeIf { it.isFile && stlByteSize > 0L }?.sha256Hex()
    val threeMfSha = threeMfFile.takeIf { it.isFile && threeMfByteSize > 0L }?.sha256Hex()
    if (stlSha.isNullOrBlank()) errors += "export_stl_hash_missing"
    if (threeMfSha.isNullOrBlank()) errors += "export_3mf_hash_missing"

    return MetricExportOutputVerification(
        errors = errors.distinct(),
        stlByteSize = stlByteSize,
        threeMfByteSize = threeMfByteSize,
        stlSha256 = stlSha,
        threeMfSha256 = threeMfSha,
        stlTriangleCount = stlTriangleCount,
        threeMfVertexCount = threeMfStats.vertexCount,
        threeMfTriangleCount = threeMfStats.triangleCount
    )
}

private data class ThreeMfExportStats(
    val hasModelEntry: Boolean = false,
    val units: String? = null,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0
)

private fun parseThreeMfStats(file: File): ThreeMfExportStats =
    runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("3D/3dmodel.model") ?: return@use ThreeMfExportStats()
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            ThreeMfExportStats(
                hasModelEntry = true,
                units = Regex("""<model[^>]*\bunit="([^"]+)"""").find(xml)?.groupValues?.getOrNull(1),
                vertexCount = Regex("""<vertex\s""").findAll(xml).count(),
                triangleCount = Regex("""<triangle\s""").findAll(xml).count()
            )
        }
    }.getOrElse {
        ThreeMfExportStats()
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

private fun parseMetricExportVertices(vertices: JSONArray): List<ScannerMetricExportVertex> =
    List(vertices.length()) { index -> vertices.getJSONObject(index) }
        .mapNotNull { vertex ->
            val xyz = vertex.optJSONObject("xyz_mm") ?: return@mapNotNull null
            ScannerMetricExportVertex(
                id = vertex.optString("vertex_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                provenanceClass = vertex.optString("provenance_class", "rejected")
            )
        }

private fun parseMetricExportTriangles(triangles: JSONArray): List<ScannerMetricExportTriangle> =
    List(triangles.length()) { index -> triangles.getJSONObject(index) }
        .mapNotNull { triangle ->
            val vertexIds = triangle.optJSONArray("vertex_ids") ?: return@mapNotNull null
            ScannerMetricExportTriangle(
                id = triangle.optString("triangle_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                vertexIds = List(vertexIds.length()) { index -> vertexIds.getString(index) },
                provenanceClass = triangle.optString("provenance_class", "rejected")
            )
        }

private fun writeMetricAsciiStl(
    file: File,
    verticesById: Map<String, ScannerMetricExportVertex>,
    triangles: List<ScannerMetricExportTriangle>
) {
    file.bufferedWriter().use { writer ->
        writer.appendLine("solid mobileslicer_metric_scan")
        triangles.forEach { triangle ->
            val a = requireNotNull(verticesById[triangle.vertexIds[0]])
            val b = requireNotNull(verticesById[triangle.vertexIds[1]])
            val c = requireNotNull(verticesById[triangle.vertexIds[2]])
            val normal = triangleNormal(a, b, c)
            writer.appendLine("  facet normal ${normal.x.formatStl()} ${normal.y.formatStl()} ${normal.z.formatStl()}")
            writer.appendLine("    outer loop")
            writer.appendLine("      vertex ${a.xMm.formatStl()} ${a.yMm.formatStl()} ${a.zMm.formatStl()}")
            writer.appendLine("      vertex ${b.xMm.formatStl()} ${b.yMm.formatStl()} ${b.zMm.formatStl()}")
            writer.appendLine("      vertex ${c.xMm.formatStl()} ${c.yMm.formatStl()} ${c.zMm.formatStl()}")
            writer.appendLine("    endloop")
            writer.appendLine("  endfacet")
        }
        writer.appendLine("endsolid mobileslicer_metric_scan")
    }
}

private fun writeMetricThreeMf(
    file: File,
    vertices: List<ScannerMetricExportVertex>,
    triangles: List<ScannerMetricExportTriangle>
) {
    val vertexIndex = vertices.mapIndexed { index, vertex -> vertex.id to index }.toMap()
    ZipOutputStream(file.outputStream()).use { zip ->
        zip.writeTextEntry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>
"""
        )
        zip.writeTextEntry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>
"""
        )
        zip.writeTextEntry("3D/3dmodel.model", metricThreeMfModelXml(vertices, triangles, vertexIndex))
    }
}

private fun metricThreeMfModelXml(
    vertices: List<ScannerMetricExportVertex>,
    triangles: List<ScannerMetricExportTriangle>,
    vertexIndex: Map<String, Int>
): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">""")
    appendLine("  <resources>")
    appendLine("""    <object id="1" type="model">""")
    appendLine("      <mesh>")
    appendLine("        <vertices>")
    vertices.forEach { vertex ->
        appendLine(
            """          <vertex x="${vertex.xMm.formatStl()}" y="${vertex.yMm.formatStl()}" z="${vertex.zMm.formatStl()}"/>"""
        )
    }
    appendLine("        </vertices>")
    appendLine("        <triangles>")
    triangles.forEach { triangle ->
        appendLine(
            """          <triangle v1="${vertexIndex.getValue(triangle.vertexIds[0])}" v2="${vertexIndex.getValue(triangle.vertexIds[1])}" v3="${vertexIndex.getValue(triangle.vertexIds[2])}"/>"""
        )
    }
    appendLine("        </triangles>")
    appendLine("      </mesh>")
    appendLine("    </object>")
    appendLine("  </resources>")
    appendLine("  <build>")
    appendLine("""    <item objectid="1"/>""")
    appendLine("  </build>")
    appendLine("</model>")
}

private data class ExportNormal(val x: Double, val y: Double, val z: Double)

private fun triangleNormal(
    a: ScannerMetricExportVertex,
    b: ScannerMetricExportVertex,
    c: ScannerMetricExportVertex
): ExportNormal {
    val abX = b.xMm - a.xMm
    val abY = b.yMm - a.yMm
    val abZ = b.zMm - a.zMm
    val acX = c.xMm - a.xMm
    val acY = c.yMm - a.yMm
    val acZ = c.zMm - a.zMm
    val crossX = abY * acZ - abZ * acY
    val crossY = abZ * acX - abX * acZ
    val crossZ = abX * acY - abY * acX
    val length = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
    return if (length > 0.000001) {
        ExportNormal(crossX / length, crossY / length, crossZ / length)
    } else {
        ExportNormal(0.0, 0.0, 1.0)
    }
}

private fun ZipOutputStream.writeTextEntry(path: String, text: String) {
    putNextEntry(ZipEntry(path))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun Double.formatStl(): String =
    String.format(Locale.US, "%.6f", this)

private fun writeScannerMetricExportManifest(
    workspaceDir: File,
    result: ScannerMetricExportResult,
    limits: ScannerMetricExportLimits
) {
    workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMetricExportManifestJson(result, limits).toString(2))
    }
}

internal fun scannerMetricExportManifestJson(
    result: ScannerMetricExportResult,
    limits: ScannerMetricExportLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("stl_exported", result.stlExported)
        .put("three_mf_exported", result.threeMfExported)
        .put("workspace_handoff_ready", result.workspaceHandoffReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("stl_path", result.stlPath ?: JSONObject.NULL)
        .put("three_mf_path", result.threeMfPath ?: JSONObject.NULL)
        .put("manifest_path", result.manifestPath)
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
            "source_contract",
            JSONObject()
                .put("slicer_load_validation", SCANNER_SLICER_LOAD_VALIDATION_PATH)
                .put("metric_mesh_candidate", SCANNER_METRIC_MESH_CANDIDATE_PATH)
                .put("slicer_load_validated_required", limits.requireSlicerLoadValidation)
                .put("pre_writer_export_ready_must_be_false", true)
                .put("pre_writer_workspace_handoff_ready_must_be_false", true)
        )
        .put(
            "verification",
            JSONObject()
                .put("stl_file_exists", result.stlExported && result.stlByteSize > 0L)
                .put("three_mf_file_exists", result.threeMfExported && result.threeMfByteSize > 0L)
                .put("stl_triangle_count_matches_candidate", result.verifiedStlTriangleCount == result.triangleCount)
                .put("three_mf_vertex_count_matches_candidate", result.verifiedThreeMfVertexCount == result.vertexCount)
                .put("three_mf_triangle_count_matches_candidate", result.verifiedThreeMfTriangleCount == result.triangleCount)
                .put("hashes_recorded", !result.stlSha256.isNullOrBlank() && !result.threeMfSha256.isNullOrBlank())
                .put("units", result.units)
        )
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("require_slicer_load_validation", limits.requireSlicerLoadValidation)
        )
        .put(
            "handoff_contract",
            JSONObject()
                .put("source_validation", SCANNER_SLICER_LOAD_VALIDATION_PATH)
                .put("source_mesh_candidate", SCANNER_METRIC_MESH_CANDIDATE_PATH)
                .put("workspace_handoff_allowed", false)
                .put("required_next_gate", "workspace_handoff_validation")
        )
