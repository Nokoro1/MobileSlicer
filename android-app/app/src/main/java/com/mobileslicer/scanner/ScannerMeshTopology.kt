package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_MESH_TOPOLOGY_PATH = "mesh/mesh_topology.json"

internal data class ScannerMeshTopologyLimits(
    val minSurfacePatchCount: Int = 1,
    val minMeasuredSurfaceRatio: Float = 0.50f,
    val minMeasuredHighRatio: Float = 0.50f,
    val maxRepairedSurfaceRatioForMetricMesh: Float = 0.0f,
    val minSourcePointCount: Int = 250,
    val maxMeanPointSpacingMm: Float = 8.0f,
    val minSurfaceCoverageCellCount: Int = 4
)

internal data class ScannerMeshTopologyPatch(
    val patchId: String,
    val sourcePointCount: Int,
    val contributingFrameCount: Int,
    val provenanceClass: String,
    val measuredSurfaceRatio: Float,
    val repairedSurfaceRatio: Float,
    val topologyClass: String
)

internal data class ScannerMeshTopologyResult(
    val allowed: Boolean,
    val metric: Boolean,
    val topologyReconstructionReady: Boolean,
    val metricMeshReady: Boolean,
    val printabilityValidationReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val surfacePatchCount: Int,
    val sourcePointCount: Int,
    val contributingFrameCount: Int,
    val measuredHighRatio: Float,
    val measuredSurfaceRatio: Float,
    val repairedSurfaceRatio: Float,
    val meanPointSpacingMm: Double?,
    val boundingBox: ScannerDenseBoundingBox?,
    val candidatePointCount: Int,
    val coverageCellCount: Int,
    val patches: List<ScannerMeshTopologyPatch>
)

internal fun admitScannerMeshTopology(
    workspaceDir: File,
    limits: ScannerMeshTopologyLimits = ScannerMeshTopologyLimits()
): ScannerMeshTopologyResult {
    val surface = workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (surface == null) {
        val result = blockedScannerMeshTopology(listOf("surface_reconstruction_missing"))
        writeScannerMeshTopology(workspaceDir, result, limits)
        return result
    }

    val patchesJson = surface.optJSONArray("patches") ?: JSONArray()
    val patches = parseMeshTopologyPatches(patchesJson)
    val surfaceCandidate = workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val candidatePointCount = surfaceCandidate
        ?.optJSONArray("candidate_points")
        ?.length()
        ?: 0
    val coverageCellCount = surfaceCandidate
        ?.optJSONArray("coverage_cells")
        ?.length()
        ?: surface.optInt("coverage_cell_count", 0)
    val sourcePointCount = surface.optInt("source_point_count", patches.sumOf { it.sourcePointCount })
    val contributingFrameCount = surface.optInt(
        "contributing_frame_count",
        patches.maxOfOrNull { it.contributingFrameCount } ?: 0
    )
    val measuredHighRatio = surface.optDouble("measured_high_ratio", 0.0).toFloat()
    val measuredSurfaceRatio = surface.optDouble("measured_surface_ratio", 0.0).toFloat()
    val repairedSurfaceRatio = surface.optDouble("repaired_surface_ratio", 0.0).toFloat()
    val meanSpacing = surface.takeUnless { it.isNull("mean_point_spacing_mm") }
        ?.optDouble("mean_point_spacing_mm")
        ?.takeIf { it.isFinite() }
    val boundingBox = surface.optJSONObject("bounding_box_mm")?.let(::parseMeshTopologyBoundingBox)
    val errors = buildList {
        if (!surface.optBoolean("allowed", false)) add("surface_reconstruction_blocked")
        if (!surface.optBoolean("metric", false)) add("metric_surface_reconstruction_missing")
        if (!surface.optBoolean("surface_reconstruction_ready", false)) {
            add("surface_reconstruction_not_ready_for_topology")
        }
        if (patches.size < limits.minSurfacePatchCount) add("surface_patch_count_low")
        if (sourcePointCount < limits.minSourcePointCount) add("source_point_count_low_for_topology")
        if (candidatePointCount > 0 && candidatePointCount < limits.minSourcePointCount) {
            add("surface_candidate_point_count_low_for_topology")
        }
        if (coverageCellCount < limits.minSurfaceCoverageCellCount) add("surface_coverage_cell_count_low")
        if (measuredHighRatio < limits.minMeasuredHighRatio) add("measured_high_ratio_low")
        if (measuredSurfaceRatio < limits.minMeasuredSurfaceRatio) add("measured_surface_ratio_low")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatioForMetricMesh) {
            add("repaired_surface_ratio_high_for_metric_mesh")
        }
        if (patches.any { it.provenanceClass == "visual_only" || it.provenanceClass == "rejected" }) {
            add("surface_patch_provenance_rejected")
        }
        if (meanSpacing == null) {
            add("mean_point_spacing_missing")
        } else if (meanSpacing > limits.maxMeanPointSpacingMm.toDouble()) {
            add("mean_point_spacing_high_for_topology")
        }
        if (boundingBox == null) add("mesh_bounds_missing")
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerMeshTopologyResult(
        allowed = accepted,
        metric = accepted,
        topologyReconstructionReady = accepted,
        metricMeshReady = false,
        printabilityValidationReady = false,
        status = if (accepted) "topology_reconstruction_ready_mesh_blocked" else "topology_reconstruction_blocked",
        errors = if (accepted) {
            listOf("metric_mesh_blocked_until_triangle_generation")
        } else {
            errors + "topology_generation_blocked_until_surface_reconstruction"
        },
        warnings = buildList {
            if (accepted) {
                add("topology_artifact_is_admission_contract_not_triangle_mesh")
                add("no_faces_edges_or_holes_generated_yet")
            }
            add("stl_3mf_export_remains_blocked")
            add("workspace_handoff_remains_blocked")
        },
        surfacePatchCount = if (accepted) patches.size else 0,
        sourcePointCount = if (accepted) sourcePointCount else 0,
        contributingFrameCount = contributingFrameCount,
        measuredHighRatio = measuredHighRatio,
        measuredSurfaceRatio = measuredSurfaceRatio,
        repairedSurfaceRatio = repairedSurfaceRatio,
        meanPointSpacingMm = meanSpacing,
        boundingBox = boundingBox,
        candidatePointCount = if (accepted) candidatePointCount else 0,
        coverageCellCount = if (accepted) coverageCellCount else 0,
        patches = if (accepted) patches else emptyList()
    )
    writeScannerMeshTopology(workspaceDir, result, limits)
    return result
}

private fun blockedScannerMeshTopology(errors: List<String>): ScannerMeshTopologyResult =
    ScannerMeshTopologyResult(
        allowed = false,
        metric = false,
        topologyReconstructionReady = false,
        metricMeshReady = false,
        printabilityValidationReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        surfacePatchCount = 0,
        sourcePointCount = 0,
        contributingFrameCount = 0,
        measuredHighRatio = 0f,
        measuredSurfaceRatio = 0f,
        repairedSurfaceRatio = 0f,
        meanPointSpacingMm = null,
        boundingBox = null,
        candidatePointCount = 0,
        coverageCellCount = 0,
        patches = emptyList()
    )

private fun parseMeshTopologyPatches(patches: JSONArray): List<ScannerMeshTopologyPatch> =
    List(patches.length()) { index -> patches.getJSONObject(index) }
        .mapNotNull { patch ->
            val provenanceClass = patch.optString("provenance_class", "rejected")
            val repairedSurfaceRatio = patch.optDouble("repaired_surface_ratio", 1.0).toFloat()
            ScannerMeshTopologyPatch(
                patchId = patch.optString("patch_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                sourcePointCount = patch.optInt("source_point_count", 0),
                contributingFrameCount = patch.optInt("contributing_frame_count", 0),
                provenanceClass = provenanceClass,
                measuredSurfaceRatio = patch.optDouble("measured_surface_ratio", 0.0).toFloat(),
                repairedSurfaceRatio = repairedSurfaceRatio,
                topologyClass = when {
                    provenanceClass == "measured_high" && repairedSurfaceRatio == 0f -> "measured_metric_surface"
                    provenanceClass == "measured_low" && repairedSurfaceRatio == 0f -> "weak_metric_surface"
                    repairedSurfaceRatio > 0f -> "repair_required_before_metric_mesh"
                    else -> "rejected"
                }
            )
        }

private fun parseMeshTopologyBoundingBox(json: JSONObject): ScannerDenseBoundingBox? {
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

private fun writeScannerMeshTopology(
    workspaceDir: File,
    result: ScannerMeshTopologyResult,
    limits: ScannerMeshTopologyLimits
) {
    workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMeshTopologyJson(result, limits).toString(2))
    }
}

internal fun scannerMeshTopologyJson(
    result: ScannerMeshTopologyResult,
    limits: ScannerMeshTopologyLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("topology_reconstruction_ready", result.topologyReconstructionReady)
        .put("metric_mesh_ready", result.metricMeshReady)
        .put("printability_validation_ready", result.printabilityValidationReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("surface_patch_count", result.surfacePatchCount)
        .put("source_point_count", result.sourcePointCount)
        .put("contributing_frame_count", result.contributingFrameCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("measured_surface_ratio", result.measuredSurfaceRatio.toDouble())
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put("mean_point_spacing_mm", result.meanPointSpacingMm ?: JSONObject.NULL)
        .put("bounding_box_mm", result.boundingBox?.toMeshTopologyJson() ?: JSONObject.NULL)
        .put("surface_candidate", if (result.candidatePointCount > 0) SCANNER_SURFACE_CANDIDATE_PATH else JSONObject.NULL)
        .put("candidate_point_count", result.candidatePointCount)
        .put("coverage_cell_count", result.coverageCellCount)
        .put(
            "limits",
            JSONObject()
                .put("min_surface_patch_count", limits.minSurfacePatchCount)
                .put("min_measured_surface_ratio", limits.minMeasuredSurfaceRatio.toDouble())
                .put("min_measured_high_ratio", limits.minMeasuredHighRatio.toDouble())
                .put("max_repaired_surface_ratio_for_metric_mesh", limits.maxRepairedSurfaceRatioForMetricMesh.toDouble())
                .put("min_source_point_count", limits.minSourcePointCount)
                .put("max_mean_point_spacing_mm", limits.maxMeanPointSpacingMm.toDouble())
                .put("min_surface_coverage_cell_count", limits.minSurfaceCoverageCellCount)
        )
        .put(
            "provenance_schema",
            JSONObject()
                .put("measured_high", "Measured surface with strong multi-frame support")
                .put("measured_low", "Measured surface with weak support; can block printability later")
                .put("repaired_small", "Reserved for later conservative repair stage")
                .put("repaired_large", "Reserved for later repair stage; blocks metric handoff above threshold")
                .put("visual_only", "Never allowed in metric mesh")
        )
        .put(
            "patch_schema",
            JSONObject()
                .put("patch_id", "Source surface patch id")
                .put("source_point_count", "Dense measured point count supporting this patch")
                .put("contributing_frame_count", "Distinct measured source frames")
                .put("provenance_class", "measured_high | measured_low")
                .put("measured_surface_ratio", "Measured support ratio carried from surface reconstruction")
                .put("repaired_surface_ratio", "Repair ratio; must be zero before repair exists")
                .put("topology_class", "measured_metric_surface | weak_metric_surface | repair_required_before_metric_mesh")
        )
        .put(
            "patches",
            JSONArray().apply {
                result.patches.forEach { patch ->
                    put(
                        JSONObject()
                            .put("patch_id", patch.patchId)
                            .put("source_point_count", patch.sourcePointCount)
                            .put("contributing_frame_count", patch.contributingFrameCount)
                            .put("provenance_class", patch.provenanceClass)
                            .put("measured_surface_ratio", patch.measuredSurfaceRatio.toDouble())
                            .put("repaired_surface_ratio", patch.repairedSurfaceRatio.toDouble())
                            .put("topology_class", patch.topologyClass)
                    )
                }
            }
        )

private fun ScannerDenseBoundingBox.toMeshTopologyJson(): JSONObject =
    JSONObject()
        .put("min", JSONObject().put("x", minX).put("y", minY).put("z", minZ))
        .put("max", JSONObject().put("x", maxX).put("y", maxY).put("z", maxZ))
        .put("extent", JSONObject().put("x", extentX).put("y", extentY).put("z", extentZ))
