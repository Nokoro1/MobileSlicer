package com.mobileslicer.scanner

import java.io.File
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_MESH_VALIDATION_PATH = "mesh/metric_mesh_validation.json"

internal data class ScannerMeshValidationLimits(
    val minVertexCount: Int = 250,
    val minTriangleCount: Int = 80,
    val maxBoundaryEdgeCountForMetricMesh: Int = 0,
    val maxNonManifoldEdgeCount: Int = 0,
    val maxDegenerateTriangleCount: Int = 0,
    val maxRepairedSurfaceRatio: Float = 0.0f,
    val maxMissingVertexReferenceCount: Int = 0,
    val maxDuplicateVertexIdCount: Int = 0,
    val maxDuplicateTriangleCount: Int = 0,
    val maxDuplicateTriangleVertexCount: Int = 0,
    val maxDisconnectedComponentCount: Int = 1,
    val maxUnreferencedVertexCount: Int = 0,
    val maxInconsistentNormalEdgeCount: Int = 0,
    val minTriangleAreaMm2: Double = 0.0001
)

internal data class ScannerMeshValidationResult(
    val allowed: Boolean,
    val metric: Boolean,
    val topologyValidated: Boolean,
    val metricMeshReady: Boolean,
    val printabilityValidationReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val vertexCount: Int,
    val triangleCount: Int,
    val uniqueEdgeCount: Int,
    val boundaryEdgeCount: Int,
    val nonManifoldEdgeCount: Int,
    val degenerateTriangleCount: Int,
    val missingVertexReferenceCount: Int,
    val duplicateVertexIdCount: Int,
    val duplicateTriangleCount: Int,
    val duplicateTriangleVertexCount: Int,
    val disconnectedComponentCount: Int,
    val unreferencedVertexCount: Int,
    val inconsistentNormalEdgeCount: Int,
    val visualOnlyElementCount: Int,
    val repairedSurfaceRatio: Float
)

private data class MeshValidationVertex(
    val id: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val provenanceClass: String
)

private data class MeshValidationTriangle(
    val id: String,
    val vertexIds: List<String>,
    val provenanceClass: String
)

internal fun validateScannerMeshCandidate(
    workspaceDir: File,
    limits: ScannerMeshValidationLimits = ScannerMeshValidationLimits()
): ScannerMeshValidationResult {
    val candidate = workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (candidate == null) {
        val result = blockedScannerMeshValidation(listOf("metric_mesh_candidate_missing"))
        writeScannerMeshValidation(workspaceDir, result, limits)
        return result
    }

    val verticesJson = candidate.optJSONArray("vertices") ?: JSONArray()
    val trianglesJson = candidate.optJSONArray("triangles") ?: JSONArray()
    val vertices = parseMeshValidationVertices(verticesJson)
    val triangles = parseMeshValidationTriangles(trianglesJson)
    val vertexIdCounts = vertices.groupingBy { it.id }.eachCount()
    val duplicateVertexIdCount = vertexIdCounts.values.sumOf { (it - 1).coerceAtLeast(0) }
    val verticesById = vertices.associateBy { it.id }
    val edgeUse = mutableMapOf<Pair<String, String>, Int>()
    val directedEdgeUse = mutableMapOf<Pair<String, String>, Int>()
    val referencedVertices = mutableSetOf<String>()
    val triangleKeys = mutableSetOf<String>()
    var duplicateTriangleCount = 0
    var duplicateTriangleVertexCount = 0
    var degenerateCount = 0
    var missingVertexReferenceCount = 0
    var visualOnlyCount = vertices.count { it.provenanceClass == "visual_only" || it.provenanceClass == "rejected" }
    triangles.forEach { triangle ->
        if (triangle.provenanceClass == "visual_only" || triangle.provenanceClass == "rejected") visualOnlyCount += 1
        val triangleVertexIds = triangle.vertexIds
        if (triangleVertexIds.size != 3) {
            degenerateCount += 1
            return@forEach
        }
        if (triangleVertexIds.toSet().size != 3) {
            duplicateTriangleVertexCount += 1
            degenerateCount += 1
            return@forEach
        }
        val missingReferences = triangleVertexIds.count { it !in verticesById }
        if (missingReferences > 0) {
            missingVertexReferenceCount += missingReferences
            degenerateCount += 1
            return@forEach
        }
        val triangleKey = triangleVertexIds.sorted().joinToString("|")
        if (!triangleKeys.add(triangleKey)) duplicateTriangleCount += 1
        val a = verticesById.getValue(triangleVertexIds[0])
        val b = verticesById.getValue(triangleVertexIds[1])
        val c = verticesById.getValue(triangleVertexIds[2])
        if (triangleAreaMm2(a, b, c) < limits.minTriangleAreaMm2) {
            degenerateCount += 1
            return@forEach
        }
        triangleVertexIds.forEach(referencedVertices::add)
        listOf(
            triangleVertexIds[0] to triangleVertexIds[1],
            triangleVertexIds[1] to triangleVertexIds[2],
            triangleVertexIds[2] to triangleVertexIds[0]
        ).forEach { edge ->
            val normalized = if (edge.first <= edge.second) edge else edge.second to edge.first
            edgeUse[normalized] = (edgeUse[normalized] ?: 0) + 1
            directedEdgeUse[edge] = (directedEdgeUse[edge] ?: 0) + 1
        }
    }
    val boundaryEdges = edgeUse.count { it.value == 1 }
    val nonManifoldEdges = edgeUse.count { it.value > 2 }
    val inconsistentNormalEdges = edgeUse.count { (edge, count) ->
        count == 2 && (directedEdgeUse[edge] ?: 0) != 1
    }
    val disconnectedComponents = countMeshComponents(referencedVertices, edgeUse.keys)
    val unreferencedVertexCount = vertices.count { it.id !in referencedVertices }
    val repairedSurfaceRatio = candidate.optDouble("repaired_surface_ratio", 1.0).toFloat()
    val errors = buildList {
        if (!candidate.optBoolean("allowed", false)) add("metric_mesh_candidate_blocked")
        if (!candidate.optBoolean("metric", false)) add("metric_mesh_candidate_not_metric")
        if (!candidate.optBoolean("triangle_mesh_candidate_ready", false)) add("triangle_mesh_candidate_not_ready")
        if (vertices.size < limits.minVertexCount) add("mesh_vertex_count_low")
        if (triangles.size < limits.minTriangleCount) add("mesh_triangle_count_low")
        if (boundaryEdges > limits.maxBoundaryEdgeCountForMetricMesh) add("mesh_boundary_edges_present")
        if (nonManifoldEdges > limits.maxNonManifoldEdgeCount) add("mesh_non_manifold_edges_present")
        if (degenerateCount > limits.maxDegenerateTriangleCount) add("mesh_degenerate_triangles_present")
        if (missingVertexReferenceCount > limits.maxMissingVertexReferenceCount) add("mesh_missing_vertex_references_present")
        if (duplicateVertexIdCount > limits.maxDuplicateVertexIdCount) add("mesh_duplicate_vertex_ids_present")
        if (duplicateTriangleCount > limits.maxDuplicateTriangleCount) add("mesh_duplicate_triangles_present")
        if (duplicateTriangleVertexCount > limits.maxDuplicateTriangleVertexCount) add("mesh_duplicate_triangle_vertices_present")
        if (disconnectedComponents > limits.maxDisconnectedComponentCount) add("mesh_disconnected_components_present")
        if (unreferencedVertexCount > limits.maxUnreferencedVertexCount) add("mesh_unreferenced_vertices_present")
        if (inconsistentNormalEdges > limits.maxInconsistentNormalEdgeCount) add("mesh_inconsistent_normals_present")
        if (visualOnlyCount > 0) add("mesh_visual_or_rejected_provenance_present")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatio) add("mesh_repaired_surface_ratio_high")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerMeshValidationResult(
        allowed = accepted,
        metric = accepted,
        topologyValidated = accepted,
        metricMeshReady = accepted,
        printabilityValidationReady = accepted,
        status = if (accepted) "metric_mesh_topology_validated" else "metric_mesh_validation_blocked",
        errors = if (accepted) {
            listOf("printability_report_blocked_until_slicer_validation")
        } else {
            errors + "printability_blocked_until_mesh_validation"
        },
        warnings = buildList {
            if (accepted) add("mesh_validation_does_not_replace_slicer_load_test")
            add("stl_3mf_export_remains_blocked")
            add("workspace_handoff_remains_blocked")
        },
        vertexCount = if (accepted) vertices.size else 0,
        triangleCount = if (accepted) triangles.size else 0,
        uniqueEdgeCount = edgeUse.size,
        boundaryEdgeCount = boundaryEdges,
        nonManifoldEdgeCount = nonManifoldEdges,
        degenerateTriangleCount = degenerateCount,
        missingVertexReferenceCount = missingVertexReferenceCount,
        duplicateVertexIdCount = duplicateVertexIdCount,
        duplicateTriangleCount = duplicateTriangleCount,
        duplicateTriangleVertexCount = duplicateTriangleVertexCount,
        disconnectedComponentCount = disconnectedComponents,
        unreferencedVertexCount = unreferencedVertexCount,
        inconsistentNormalEdgeCount = inconsistentNormalEdges,
        visualOnlyElementCount = visualOnlyCount,
        repairedSurfaceRatio = repairedSurfaceRatio
    )
    writeScannerMeshValidation(workspaceDir, result, limits)
    return result
}

private fun blockedScannerMeshValidation(errors: List<String>): ScannerMeshValidationResult =
    ScannerMeshValidationResult(
        allowed = false,
        metric = false,
        topologyValidated = false,
        metricMeshReady = false,
        printabilityValidationReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        vertexCount = 0,
        triangleCount = 0,
        uniqueEdgeCount = 0,
        boundaryEdgeCount = 0,
        nonManifoldEdgeCount = 0,
        degenerateTriangleCount = 0,
        missingVertexReferenceCount = 0,
        duplicateVertexIdCount = 0,
        duplicateTriangleCount = 0,
        duplicateTriangleVertexCount = 0,
        disconnectedComponentCount = 0,
        unreferencedVertexCount = 0,
        inconsistentNormalEdgeCount = 0,
        visualOnlyElementCount = 0,
        repairedSurfaceRatio = 0f
    )

private fun parseMeshValidationVertices(vertices: JSONArray): List<MeshValidationVertex> =
    List(vertices.length()) { index -> vertices.getJSONObject(index) }
        .mapNotNull { vertex ->
            val id = vertex.optString("vertex_id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val xyz = vertex.optJSONObject("xyz_mm") ?: return@mapNotNull null
            MeshValidationVertex(
                id = id,
                xMm = xyz.optDouble("x", Double.NaN),
                yMm = xyz.optDouble("y", Double.NaN),
                zMm = xyz.optDouble("z", Double.NaN),
                provenanceClass = vertex.optString("provenance_class", "rejected")
            ).takeIf { listOf(it.xMm, it.yMm, it.zMm).all(Double::isFinite) }
        }

private fun parseMeshValidationTriangles(triangles: JSONArray): List<MeshValidationTriangle> =
    List(triangles.length()) { index -> index to triangles.getJSONObject(index) }
        .map { (index, triangle) ->
            val ids = triangle.optJSONArray("vertex_ids") ?: JSONArray()
            MeshValidationTriangle(
                id = triangle.optString("triangle_id", "triangle_$index"),
                vertexIds = List(ids.length()) { idIndex -> ids.optString(idIndex) },
                provenanceClass = triangle.optString("provenance_class", "rejected")
            )
        }

private fun countMeshComponents(
    referencedVertices: Set<String>,
    edges: Set<Pair<String, String>>
): Int {
    if (referencedVertices.isEmpty()) return 0
    val adjacency = referencedVertices.associateWith { mutableSetOf<String>() }.toMutableMap()
    edges.forEach { edge ->
        if (edge.first in referencedVertices && edge.second in referencedVertices) {
            adjacency.getValue(edge.first).add(edge.second)
            adjacency.getValue(edge.second).add(edge.first)
        }
    }
    val unseen = referencedVertices.toMutableSet()
    var components = 0
    while (unseen.isNotEmpty()) {
        components += 1
        val stack = mutableListOf(unseen.first())
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.lastIndex)
            if (!unseen.remove(current)) continue
            adjacency[current].orEmpty().forEach { neighbor ->
                if (neighbor in unseen) stack += neighbor
            }
        }
    }
    return components
}

private fun triangleAreaMm2(
    a: MeshValidationVertex,
    b: MeshValidationVertex,
    c: MeshValidationVertex
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

private fun writeScannerMeshValidation(
    workspaceDir: File,
    result: ScannerMeshValidationResult,
    limits: ScannerMeshValidationLimits
) {
    workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMeshValidationJson(result, limits).toString(2))
    }
}

internal fun scannerMeshValidationJson(
    result: ScannerMeshValidationResult,
    limits: ScannerMeshValidationLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("topology_validated", result.topologyValidated)
        .put("metric_mesh_ready", result.metricMeshReady)
        .put("printability_validation_ready", result.printabilityValidationReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("vertex_count", result.vertexCount)
        .put("triangle_count", result.triangleCount)
        .put("unique_edge_count", result.uniqueEdgeCount)
        .put("boundary_edge_count", result.boundaryEdgeCount)
        .put("non_manifold_edge_count", result.nonManifoldEdgeCount)
        .put("degenerate_triangle_count", result.degenerateTriangleCount)
        .put("missing_vertex_reference_count", result.missingVertexReferenceCount)
        .put("duplicate_vertex_id_count", result.duplicateVertexIdCount)
        .put("duplicate_triangle_count", result.duplicateTriangleCount)
        .put("duplicate_triangle_vertex_count", result.duplicateTriangleVertexCount)
        .put("disconnected_component_count", result.disconnectedComponentCount)
        .put("unreferenced_vertex_count", result.unreferencedVertexCount)
        .put("inconsistent_normal_edge_count", result.inconsistentNormalEdgeCount)
        .put("visual_only_element_count", result.visualOnlyElementCount)
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put(
            "limits",
            JSONObject()
                .put("min_vertex_count", limits.minVertexCount)
                .put("min_triangle_count", limits.minTriangleCount)
                .put("max_boundary_edge_count_for_metric_mesh", limits.maxBoundaryEdgeCountForMetricMesh)
                .put("max_non_manifold_edge_count", limits.maxNonManifoldEdgeCount)
                .put("max_degenerate_triangle_count", limits.maxDegenerateTriangleCount)
                .put("max_repaired_surface_ratio", limits.maxRepairedSurfaceRatio.toDouble())
                .put("max_missing_vertex_reference_count", limits.maxMissingVertexReferenceCount)
                .put("max_duplicate_vertex_id_count", limits.maxDuplicateVertexIdCount)
                .put("max_duplicate_triangle_count", limits.maxDuplicateTriangleCount)
                .put("max_duplicate_triangle_vertex_count", limits.maxDuplicateTriangleVertexCount)
                .put("max_disconnected_component_count", limits.maxDisconnectedComponentCount)
                .put("max_unreferenced_vertex_count", limits.maxUnreferencedVertexCount)
                .put("max_inconsistent_normal_edge_count", limits.maxInconsistentNormalEdgeCount)
                .put("min_triangle_area_mm2", limits.minTriangleAreaMm2)
        )
        .put(
            "validation_contract",
            JSONObject()
                .put("artifact", SCANNER_MESH_VALIDATION_PATH)
                .put("topology", "Boundary, non-manifold, duplicate, disconnected, normal consistency, and degenerate triangle checks")
                .put("references", "Every triangle must reference three unique measured vertices")
                .put("provenance", "Visual-only or rejected vertices/triangles block metric mesh")
                .put("repair", "Repaired surface must be reported before it can be allowed")
                .put("handoff", "Slicer handoff remains blocked until printability and slicer-load validation")
        )
