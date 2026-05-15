package com.mobileslicer.scanner

import java.io.File
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_METRIC_MESH_CANDIDATE_PATH = "mesh/metric_mesh_candidate.json"

internal data class ScannerMetricMeshCandidateLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val maxTriangleEdgeMm: Float = 12.0f,
    val minTriangleAreaMm2: Float = 0.05f,
    val minMeasuredHighRatio: Float = 0.50f,
    val maxRepairedSurfaceRatio: Float = 0.0f,
    val minCoverageCellCount: Int = 4
)

internal data class ScannerMeshCandidateVertex(
    val vertexId: String,
    val sourcePointId: String,
    val sourceTrackId: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val provenanceClass: String
)

internal data class ScannerMeshCandidateTriangle(
    val triangleId: String,
    val vertexIds: List<String>,
    val provenanceClass: String,
    val maxEdgeMm: Double,
    val areaMm2: Double
)

internal data class ScannerMetricMeshCandidateResult(
    val allowed: Boolean,
    val metric: Boolean,
    val triangleMeshCandidateReady: Boolean,
    val metricMeshReady: Boolean,
    val printabilityValidationReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val vertexCount: Int,
    val triangleCount: Int,
    val measuredHighRatio: Float,
    val repairedSurfaceRatio: Float,
    val maxTriangleEdgeMm: Double?,
    val minTriangleAreaMm2: Double?,
    val coverageCellCount: Int,
    val boundingBox: ScannerDenseBoundingBox?,
    val vertices: List<ScannerMeshCandidateVertex>,
    val triangles: List<ScannerMeshCandidateTriangle>
)

internal fun buildScannerMetricMeshCandidate(
    workspaceDir: File,
    limits: ScannerMetricMeshCandidateLimits = ScannerMetricMeshCandidateLimits()
): ScannerMetricMeshCandidateResult {
    val topology = workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val densePointCloud = workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val surfaceCandidate = workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (topology == null) {
        val result = blockedScannerMetricMeshCandidate(listOf("mesh_topology_missing"))
        writeScannerMetricMeshCandidate(workspaceDir, result, limits)
        return result
    }
    if (densePointCloud == null) {
        val result = blockedScannerMetricMeshCandidate(listOf("dense_point_cloud_missing"))
        writeScannerMetricMeshCandidate(workspaceDir, result, limits)
        return result
    }

    val vertices = surfaceCandidate
        ?.optJSONArray("candidate_points")
        ?.let(::parseMetricMeshVerticesFromSurfaceCandidate)
        ?.takeIf { it.isNotEmpty() }
        ?: parseMetricMeshVertices(densePointCloud.optJSONArray("points") ?: JSONArray())
    val measuredHighRatio = if (vertices.isEmpty()) {
        0f
    } else {
        vertices.count { it.provenanceClass == "measured_high" }.toFloat() / vertices.size.toFloat()
    }
    val repairedSurfaceRatio = topology.optDouble("repaired_surface_ratio", 1.0).toFloat()
    val coverageCellCount = surfaceCandidate?.optJSONArray("coverage_cells")?.length()
        ?: topology.optInt("coverage_cell_count", 0)
    val boundingBox = densePointCloud.optJSONObject("bounding_box_mm")?.let(::parseMetricMeshBoundingBox)
    val triangles = buildMeasuredCandidateTriangles(vertices, limits)
    val maxEdge = triangles.maxOfOrNull { it.maxEdgeMm }
    val minArea = triangles.minOfOrNull { it.areaMm2 }
    val errors = buildList {
        if (!topology.optBoolean("allowed", false)) add("mesh_topology_blocked")
        if (!topology.optBoolean("metric", false)) add("metric_topology_missing")
        if (!topology.optBoolean("topology_reconstruction_ready", false)) {
            add("topology_reconstruction_not_ready")
        }
        if (!densePointCloud.optBoolean("allowed", false)) add("dense_point_cloud_blocked")
        if (!densePointCloud.optBoolean("metric", false)) add("metric_dense_point_cloud_missing")
        if (vertices.size < limits.minVertexCount) add("mesh_vertex_count_low")
        if (coverageCellCount < limits.minCoverageCellCount) add("mesh_coverage_cell_count_low")
        if (triangles.size < limits.minTriangleCount) add("mesh_triangle_count_low")
        if (measuredHighRatio < limits.minMeasuredHighRatio) add("measured_high_ratio_low")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatio) add("repaired_surface_ratio_high")
        if (vertices.any { it.provenanceClass == "visual_only" || it.provenanceClass == "rejected" }) {
            add("mesh_vertex_provenance_rejected")
        }
        if (triangles.any { it.provenanceClass != "measured_high" && it.provenanceClass != "measured_low" }) {
            add("mesh_triangle_provenance_rejected")
        }
        if (triangles.any { it.maxEdgeMm > limits.maxTriangleEdgeMm.toDouble() }) add("mesh_triangle_edge_high")
        if (triangles.any { it.areaMm2 < limits.minTriangleAreaMm2.toDouble() }) add("mesh_triangle_area_low")
        if (boundingBox == null) add("mesh_bounds_missing")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerMetricMeshCandidateResult(
        allowed = accepted,
        metric = accepted,
        triangleMeshCandidateReady = accepted,
        metricMeshReady = false,
        printabilityValidationReady = false,
        status = if (accepted) "triangle_mesh_candidate_ready_validation_blocked" else "triangle_mesh_candidate_blocked",
        errors = if (accepted) {
            listOf("metric_mesh_blocked_until_topology_validation")
        } else {
            errors + "metric_mesh_blocked_until_triangle_candidate"
        },
        warnings = buildList {
            if (accepted) {
                add("candidate_uses_measured_vertices_only")
                add("candidate_triangles_are_not_printability_validation")
                add("no_repair_or_hole_fill_applied")
            }
            add("stl_3mf_export_remains_blocked")
            add("workspace_handoff_remains_blocked")
        },
        vertexCount = if (accepted) vertices.size else 0,
        triangleCount = if (accepted) triangles.size else 0,
        measuredHighRatio = measuredHighRatio,
        repairedSurfaceRatio = repairedSurfaceRatio,
        maxTriangleEdgeMm = maxEdge,
        minTriangleAreaMm2 = minArea,
        coverageCellCount = coverageCellCount,
        boundingBox = boundingBox,
        vertices = if (accepted) vertices else emptyList(),
        triangles = if (accepted) triangles else emptyList()
    )
    writeScannerMetricMeshCandidate(workspaceDir, result, limits)
    return result
}

private fun blockedScannerMetricMeshCandidate(errors: List<String>): ScannerMetricMeshCandidateResult =
    ScannerMetricMeshCandidateResult(
        allowed = false,
        metric = false,
        triangleMeshCandidateReady = false,
        metricMeshReady = false,
        printabilityValidationReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        vertexCount = 0,
        triangleCount = 0,
        measuredHighRatio = 0f,
        repairedSurfaceRatio = 0f,
        maxTriangleEdgeMm = null,
        minTriangleAreaMm2 = null,
        coverageCellCount = 0,
        boundingBox = null,
        vertices = emptyList(),
        triangles = emptyList()
    )

private fun parseMetricMeshVertices(points: JSONArray): List<ScannerMeshCandidateVertex> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            ScannerMeshCandidateVertex(
                vertexId = "vertex_${point.optString("point_id")}",
                sourcePointId = point.optString("source_point_id", point.optString("point_id")),
                sourceTrackId = point.optString("source_track_id"),
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }

private fun parseMetricMeshVerticesFromSurfaceCandidate(points: JSONArray): List<ScannerMeshCandidateVertex> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            ScannerMeshCandidateVertex(
                vertexId = "vertex_${point.optString("point_id")}",
                sourcePointId = point.optString("source_point_id", point.optString("point_id")),
                sourceTrackId = point.optString("source_track_id"),
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }

private fun buildMeasuredCandidateTriangles(
    vertices: List<ScannerMeshCandidateVertex>,
    limits: ScannerMetricMeshCandidateLimits
): List<ScannerMeshCandidateTriangle> {
    val measuredVertices = vertices
        .filter { it.provenanceClass == "measured_high" || it.provenanceClass == "measured_low" }
    if (measuredVertices.size < 3) return emptyList()
    val rows = measuredVertices
        .groupBy { (it.yMm * 1000.0).toLong() }
        .values
        .map { row -> row.sortedBy { it.xMm } }
        .sortedBy { row -> row.firstOrNull()?.yMm ?: 0.0 }
    val triangles = mutableListOf<ScannerMeshCandidateTriangle>()
    for (rowIndex in 0 until rows.size - 1) {
        val upper = rows[rowIndex]
        val lower = rows[rowIndex + 1]
        val columns = minOf(upper.size, lower.size)
        for (column in 0 until columns - 1) {
            addMeasuredTriangle(
                triangles = triangles,
                a = upper[column],
                b = upper[column + 1],
                c = lower[column],
                limits = limits
            )
            addMeasuredTriangle(
                triangles = triangles,
                a = upper[column + 1],
                b = lower[column + 1],
                c = lower[column],
                limits = limits
            )
        }
    }
    if (triangles.isNotEmpty()) return triangles
    val sortedVertices = measuredVertices.sortedWith(compareBy<ScannerMeshCandidateVertex> { it.zMm }.thenBy { it.yMm }.thenBy { it.xMm })
    for (index in 0 until sortedVertices.size - 2) {
        addMeasuredTriangle(triangles, sortedVertices[index], sortedVertices[index + 1], sortedVertices[index + 2], limits)
    }
    return triangles
}

private fun addMeasuredTriangle(
    triangles: MutableList<ScannerMeshCandidateTriangle>,
    a: ScannerMeshCandidateVertex,
    b: ScannerMeshCandidateVertex,
    c: ScannerMeshCandidateVertex,
    limits: ScannerMetricMeshCandidateLimits
) {
    val ab = distanceMm(a, b)
    val bc = distanceMm(b, c)
    val ca = distanceMm(c, a)
    val maxEdge = maxOf(ab, bc, ca)
    val area = triangleAreaMm2(a, b, c)
    if (maxEdge > limits.maxTriangleEdgeMm.toDouble() || area < limits.minTriangleAreaMm2.toDouble()) return
    triangles += ScannerMeshCandidateTriangle(
        triangleId = "tri_${triangles.size.toString().padStart(6, '0')}",
        vertexIds = listOf(a.vertexId, b.vertexId, c.vertexId),
        provenanceClass = if (
            a.provenanceClass == "measured_high" &&
            b.provenanceClass == "measured_high" &&
            c.provenanceClass == "measured_high"
        ) {
            "measured_high"
        } else {
            "measured_low"
        },
        maxEdgeMm = maxEdge,
        areaMm2 = area
    )
}

private fun distanceMm(a: ScannerMeshCandidateVertex, b: ScannerMeshCandidateVertex): Double {
    val dx = a.xMm - b.xMm
    val dy = a.yMm - b.yMm
    val dz = a.zMm - b.zMm
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun triangleAreaMm2(
    a: ScannerMeshCandidateVertex,
    b: ScannerMeshCandidateVertex,
    c: ScannerMeshCandidateVertex
): Double {
    val abX = b.xMm - a.xMm
    val abY = b.yMm - a.yMm
    val abZ = b.zMm - a.zMm
    val acX = c.xMm - a.xMm
    val acY = c.yMm - a.yMm
    val acZ = c.zMm - a.zMm
    val crossX = abY * acZ - abZ * acY
    val crossY = abZ * acX - abX * acZ
    val crossZ = abX * acY - abY * acX
    return 0.5 * sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
}

private fun parseMetricMeshBoundingBox(json: JSONObject): ScannerDenseBoundingBox? {
    val min = json.optJSONObject("min") ?: return null
    val max = json.optJSONObject("max") ?: return null
    return ScannerDenseBoundingBox(
        minX = min.optDouble("x", Double.NaN),
        minY = min.optDouble("y", Double.NaN),
        minZ = min.optDouble("z", Double.NaN),
        maxX = max.optDouble("x", Double.NaN),
        maxY = max.optDouble("y", Double.NaN),
        maxZ = max.optDouble("z", Double.NaN)
    ).takeIf {
        listOf(it.minX, it.minY, it.minZ, it.maxX, it.maxY, it.maxZ).all { value -> value.isFinite() }
    }
}

private fun writeScannerMetricMeshCandidate(
    workspaceDir: File,
    result: ScannerMetricMeshCandidateResult,
    limits: ScannerMetricMeshCandidateLimits
) {
    workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMetricMeshCandidateJson(result, limits).toString(2))
    }
}

internal fun scannerMetricMeshCandidateJson(
    result: ScannerMetricMeshCandidateResult,
    limits: ScannerMetricMeshCandidateLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("triangle_mesh_candidate_ready", result.triangleMeshCandidateReady)
        .put("metric_mesh_ready", result.metricMeshReady)
        .put("printability_validation_ready", result.printabilityValidationReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("vertex_count", result.vertexCount)
        .put("triangle_count", result.triangleCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put("max_triangle_edge_mm", result.maxTriangleEdgeMm ?: JSONObject.NULL)
        .put("min_triangle_area_mm2", result.minTriangleAreaMm2 ?: JSONObject.NULL)
        .put("coverage_cell_count", result.coverageCellCount)
        .put("surface_candidate", if (result.coverageCellCount > 0) SCANNER_SURFACE_CANDIDATE_PATH else JSONObject.NULL)
        .put("bounding_box_mm", result.boundingBox?.toMetricMeshJson() ?: JSONObject.NULL)
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("max_triangle_edge_mm", limits.maxTriangleEdgeMm.toDouble())
                .put("min_triangle_area_mm2", limits.minTriangleAreaMm2.toDouble())
                .put("min_measured_high_ratio", limits.minMeasuredHighRatio.toDouble())
                .put("max_repaired_surface_ratio", limits.maxRepairedSurfaceRatio.toDouble())
                .put("min_coverage_cell_count", limits.minCoverageCellCount)
        )
        .put(
            "mesh_contract",
            JSONObject()
                .put("vertices", "Measured dense point vertices only")
                .put("triangles", "Candidate measured triangles only; not STL/3MF export")
                .put("repairs", "No repair or hole fill in this stage")
                .put("handoff", "Workspace handoff remains blocked until validation passes")
        )
        .put(
            "vertices",
            JSONArray().apply {
                result.vertices.forEach { vertex ->
                    put(
                        JSONObject()
                            .put("vertex_id", vertex.vertexId)
                            .put("source_point_id", vertex.sourcePointId)
                            .put("source_track_id", vertex.sourceTrackId)
                            .put("xyz_mm", JSONObject().put("x", vertex.xMm).put("y", vertex.yMm).put("z", vertex.zMm))
                            .put("provenance_class", vertex.provenanceClass)
                    )
                }
            }
        )
        .put(
            "triangles",
            JSONArray().apply {
                result.triangles.forEach { triangle ->
                    put(
                        JSONObject()
                            .put("triangle_id", triangle.triangleId)
                            .put("vertex_ids", JSONArray(triangle.vertexIds))
                            .put("provenance_class", triangle.provenanceClass)
                            .put("max_edge_mm", triangle.maxEdgeMm)
                            .put("area_mm2", triangle.areaMm2)
                    )
                }
            }
        )

private fun ScannerDenseBoundingBox.toMetricMeshJson(): JSONObject =
    JSONObject()
        .put("min", JSONObject().put("x", minX).put("y", minY).put("z", minZ))
        .put("max", JSONObject().put("x", maxX).put("y", maxY).put("z", maxZ))
        .put("extent", JSONObject().put("x", extentX).put("y", extentY).put("z", extentZ))
