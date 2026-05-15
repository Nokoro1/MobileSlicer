package com.mobileslicer.scanner

import java.io.File
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_SURFACE_RECONSTRUCTION_PATH = "surface/surface_reconstruction.json"
internal const val SCANNER_SURFACE_CANDIDATE_PATH = "surface/measured_surface_candidate.json"

internal data class ScannerSurfaceReconstructionLimits(
    val minSurfacePointCount: Int = 250,
    val maxMeanPointSpacingMm: Float = 8.0f,
    val minMeasuredHighRatio: Float = 0.50f,
    val minSurfaceCoverageRatio: Float = 0.35f,
    val maxRepairedSurfaceRatioForMesh: Float = 0.0f
)

internal data class ScannerSurfacePatch(
    val patchId: String,
    val sourcePointCount: Int,
    val contributingFrameCount: Int,
    val provenanceClass: String,
    val measuredSurfaceRatio: Float,
    val repairedSurfaceRatio: Float,
    val meanPointSpacingMm: Double?
)

internal data class ScannerSurfaceCandidatePoint(
    val pointId: String,
    val sourcePointId: String,
    val sourceTrackId: String,
    val contributingFrames: List<String>,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val provenanceClass: String
)

internal data class ScannerSurfaceCoverageCell(
    val cellId: String,
    val column: Int,
    val row: Int,
    val pointCount: Int,
    val measuredHighCount: Int,
    val provenanceClass: String
)

internal data class ScannerSurfaceReconstructionResult(
    val allowed: Boolean,
    val metric: Boolean,
    val surfaceReconstructionReady: Boolean,
    val meshGenerationReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val sourcePointCount: Int,
    val contributingFrameCount: Int,
    val measuredHighRatio: Float,
    val measuredSurfaceRatio: Float,
    val repairedSurfaceRatio: Float,
    val meanPointSpacingMm: Double?,
    val boundingBox: ScannerDenseBoundingBox?,
    val patches: List<ScannerSurfacePatch>,
    val candidatePoints: List<ScannerSurfaceCandidatePoint>,
    val coverageCells: List<ScannerSurfaceCoverageCell>
)

internal fun reconstructScannerSurface(
    workspaceDir: File,
    limits: ScannerSurfaceReconstructionLimits = ScannerSurfaceReconstructionLimits()
): ScannerSurfaceReconstructionResult {
    val densePointCloud = workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (densePointCloud == null) {
        val result = blockedScannerSurfaceReconstruction(listOf("dense_point_cloud_missing"))
        writeScannerSurfaceReconstruction(workspaceDir, result, limits)
        return result
    }

    val points = densePointCloud.optJSONArray("points") ?: JSONArray()
    val candidatePoints = parseSurfaceCandidatePoints(points)
    val pointCount = densePointCloud.optInt("point_count", points.length())
    val contributingFrameCount = densePointCloud.optInt("contributing_frame_count", 0)
    val measuredHighRatio = densePointCloud.optDouble("measured_high_ratio", 0.0).toFloat()
    val meanSpacing = densePointCloud.takeUnless { it.isNull("mean_nearest_neighbor_mm") }
        ?.optDouble("mean_nearest_neighbor_mm")
        ?.takeIf { it.isFinite() }
    val boundingBox = densePointCloud.optJSONObject("bounding_box_mm")?.let(::parseSurfaceBoundingBox)
    val coverageCells = buildSurfaceCoverageCells(candidatePoints, boundingBox)
    val measuredSurfaceRatio = estimateMeasuredSurfaceRatio(pointCount, meanSpacing, boundingBox, coverageCells)
    val repairedSurfaceRatio = 0f
    val errors = buildList {
        if (!densePointCloud.optBoolean("allowed", false)) add("dense_point_cloud_blocked")
        if (!densePointCloud.optBoolean("metric", false)) add("metric_dense_point_cloud_missing")
        if (!densePointCloud.optBoolean("surface_reconstruction_ready", false)) {
            add("surface_reconstruction_not_admitted_by_point_cloud")
        }
        if (pointCount < limits.minSurfacePointCount) add("surface_point_count_low")
        if (measuredHighRatio < limits.minMeasuredHighRatio) add("measured_high_ratio_low")
        if (meanSpacing == null) {
            add("mean_point_spacing_missing")
        } else if (meanSpacing > limits.maxMeanPointSpacingMm.toDouble()) {
            add("mean_point_spacing_high")
        }
        if (boundingBox == null) add("surface_bounds_missing")
        if (coverageCells.isEmpty()) add("surface_coverage_cells_missing")
        if (measuredSurfaceRatio < limits.minSurfaceCoverageRatio) add("measured_surface_ratio_low")
        if (repairedSurfaceRatio > limits.maxRepairedSurfaceRatioForMesh) add("repaired_surface_ratio_high")
    }.distinct()
    val accepted = errors.isEmpty()
    val patches = if (accepted) {
        listOf(
            ScannerSurfacePatch(
                patchId = "measured_surface_patch_0",
                sourcePointCount = pointCount,
                contributingFrameCount = contributingFrameCount,
                provenanceClass = if (measuredHighRatio >= 0.80f) "measured_high" else "measured_low",
                measuredSurfaceRatio = measuredSurfaceRatio,
                repairedSurfaceRatio = repairedSurfaceRatio,
                meanPointSpacingMm = meanSpacing
            )
        )
    } else {
        emptyList()
    }
    val result = ScannerSurfaceReconstructionResult(
        allowed = accepted,
        metric = accepted,
        surfaceReconstructionReady = accepted,
        meshGenerationReady = false,
        status = if (accepted) "surface_reconstruction_ready_mesh_blocked" else "surface_reconstruction_blocked",
        errors = if (accepted) {
            listOf("mesh_generation_blocked_until_topology_reconstruction")
        } else {
            errors + "mesh_generation_blocked_until_surface_reconstruction"
        },
        warnings = buildList {
            if (accepted) {
                add("surface_artifact_is_reconstruction_boundary_not_printable_mesh")
                add("no_hole_fill_or_topology_repair_applied")
            }
            add("stl_3mf_export_remains_blocked")
        },
        sourcePointCount = if (accepted) pointCount else 0,
        contributingFrameCount = contributingFrameCount,
        measuredHighRatio = measuredHighRatio,
        measuredSurfaceRatio = measuredSurfaceRatio,
        repairedSurfaceRatio = repairedSurfaceRatio,
        meanPointSpacingMm = meanSpacing,
        boundingBox = boundingBox,
        patches = patches,
        candidatePoints = if (accepted) candidatePoints else emptyList(),
        coverageCells = if (accepted) coverageCells else emptyList()
    )
    writeScannerSurfaceReconstruction(workspaceDir, result, limits)
    return result
}

private fun blockedScannerSurfaceReconstruction(errors: List<String>): ScannerSurfaceReconstructionResult =
    ScannerSurfaceReconstructionResult(
        allowed = false,
        metric = false,
        surfaceReconstructionReady = false,
        meshGenerationReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        sourcePointCount = 0,
        contributingFrameCount = 0,
        measuredHighRatio = 0f,
        measuredSurfaceRatio = 0f,
        repairedSurfaceRatio = 0f,
        meanPointSpacingMm = null,
        boundingBox = null,
        patches = emptyList(),
        candidatePoints = emptyList(),
        coverageCells = emptyList()
    )

private fun estimateMeasuredSurfaceRatio(
    pointCount: Int,
    meanSpacing: Double?,
    boundingBox: ScannerDenseBoundingBox?,
    coverageCells: List<ScannerSurfaceCoverageCell>
): Float {
    if (pointCount <= 0 || meanSpacing == null || boundingBox == null || boundingBox.maxExtent <= 0.0) {
        return 0f
    }
    val spacingScore = (1.0 - (meanSpacing / max(boundingBox.maxExtent, 1.0))).coerceIn(0.0, 1.0)
    val countScore = (pointCount.toDouble() / 1000.0).coerceIn(0.0, 1.0)
    val coverageScore = (coverageCells.size.toDouble() / SURFACE_COVERAGE_CELL_COUNT.toDouble()).coerceIn(0.0, 1.0)
    return (0.45 * spacingScore + 0.25 * countScore + 0.30 * coverageScore).toFloat().coerceIn(0f, 1f)
}

private fun parseSurfaceCandidatePoints(points: JSONArray): List<ScannerSurfaceCandidatePoint> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            val provenance = point.optString("provenance_class", "rejected")
            if (provenance != "measured_high" && provenance != "measured_low") return@mapNotNull null
            val contributing = point.optJSONArray("contributing_frames") ?: JSONArray()
            ScannerSurfaceCandidatePoint(
                pointId = point.optString("point_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                sourcePointId = point.optString("source_point_id"),
                sourceTrackId = point.optString("source_track_id"),
                contributingFrames = List(contributing.length()) { index -> contributing.getString(index) },
                xMm = xyz.optDouble("x", Double.NaN),
                yMm = xyz.optDouble("y", Double.NaN),
                zMm = xyz.optDouble("z", Double.NaN),
                provenanceClass = provenance
            )
        }
        .filter { listOf(it.xMm, it.yMm, it.zMm).all(Double::isFinite) }

private fun buildSurfaceCoverageCells(
    points: List<ScannerSurfaceCandidatePoint>,
    boundingBox: ScannerDenseBoundingBox?
): List<ScannerSurfaceCoverageCell> {
    if (points.isEmpty() || boundingBox == null || boundingBox.extentX <= 0.0 || boundingBox.extentY <= 0.0) return emptyList()
    return points
        .groupBy { point ->
            val column = (((point.xMm - boundingBox.minX) / boundingBox.extentX) * SURFACE_COVERAGE_GRID_COLUMNS)
                .toInt()
                .coerceIn(0, SURFACE_COVERAGE_GRID_COLUMNS - 1)
            val row = (((point.yMm - boundingBox.minY) / boundingBox.extentY) * SURFACE_COVERAGE_GRID_ROWS)
                .toInt()
                .coerceIn(0, SURFACE_COVERAGE_GRID_ROWS - 1)
            column to row
        }
        .map { (cell, cellPoints) ->
            val measuredHigh = cellPoints.count { it.provenanceClass == "measured_high" }
            ScannerSurfaceCoverageCell(
                cellId = "cell_${cell.first}_${cell.second}",
                column = cell.first,
                row = cell.second,
                pointCount = cellPoints.size,
                measuredHighCount = measuredHigh,
                provenanceClass = if (measuredHigh.toFloat() / cellPoints.size.toFloat() >= 0.5f) {
                    "measured_high"
                } else {
                    "measured_low"
                }
            )
        }
        .sortedWith(compareBy<ScannerSurfaceCoverageCell> { it.row }.thenBy { it.column })
}

private fun parseSurfaceBoundingBox(json: JSONObject): ScannerDenseBoundingBox? {
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

private fun writeScannerSurfaceReconstruction(
    workspaceDir: File,
    result: ScannerSurfaceReconstructionResult,
    limits: ScannerSurfaceReconstructionLimits
) {
    workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerSurfaceReconstructionJson(result, limits).toString(2))
    }
    if (result.candidatePoints.isNotEmpty()) {
        workspaceDir.resolve(SCANNER_SURFACE_CANDIDATE_PATH).also {
            it.parentFile?.mkdirs()
            it.writeText(scannerSurfaceCandidateJson(result).toString(2))
        }
    }
}

internal fun scannerSurfaceReconstructionJson(
    result: ScannerSurfaceReconstructionResult,
    limits: ScannerSurfaceReconstructionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("surface_reconstruction_ready", result.surfaceReconstructionReady)
        .put("mesh_generation_ready", result.meshGenerationReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("source_point_count", result.sourcePointCount)
        .put("contributing_frame_count", result.contributingFrameCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("measured_surface_ratio", result.measuredSurfaceRatio.toDouble())
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put("mean_point_spacing_mm", result.meanPointSpacingMm ?: JSONObject.NULL)
        .put("bounding_box_mm", result.boundingBox?.toJson() ?: JSONObject.NULL)
        .put("surface_candidate", if (result.candidatePoints.isNotEmpty()) SCANNER_SURFACE_CANDIDATE_PATH else JSONObject.NULL)
        .put("surface_candidate_printable", false)
        .put("surface_candidate_slicer_handoff_allowed", false)
        .put("coverage_cell_count", result.coverageCells.size)
        .put("coverage_grid_columns", SURFACE_COVERAGE_GRID_COLUMNS)
        .put("coverage_grid_rows", SURFACE_COVERAGE_GRID_ROWS)
        .put(
            "limits",
            JSONObject()
                .put("min_surface_point_count", limits.minSurfacePointCount)
                .put("max_mean_point_spacing_mm", limits.maxMeanPointSpacingMm.toDouble())
                .put("min_measured_high_ratio", limits.minMeasuredHighRatio.toDouble())
                .put("min_surface_coverage_ratio", limits.minSurfaceCoverageRatio.toDouble())
                .put("max_repaired_surface_ratio_for_mesh", limits.maxRepairedSurfaceRatioForMesh.toDouble())
        )
        .put(
            "patch_schema",
            JSONObject()
                .put("patch_id", "Stable measured surface patch id")
                .put("source_point_count", "Dense point count supporting this patch")
                .put("contributing_frame_count", "Distinct source frames for this patch")
                .put("provenance_class", "measured_high | measured_low")
                .put("measured_surface_ratio", "Estimated measured support ratio")
                .put("repaired_surface_ratio", "Repair ratio; must remain zero before repair stage")
                .put("mean_point_spacing_mm", "Mean dense point spacing for this patch")
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
                            .put("mean_point_spacing_mm", patch.meanPointSpacingMm ?: JSONObject.NULL)
                    )
                }
            }
        )

internal fun scannerSurfaceCandidateJson(result: ScannerSurfaceReconstructionResult): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("metric", result.metric)
        .put("printable", false)
        .put("slicer_handoff_allowed", false)
        .put("source_point_count", result.sourcePointCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("measured_surface_ratio", result.measuredSurfaceRatio.toDouble())
        .put("repaired_surface_ratio", result.repairedSurfaceRatio.toDouble())
        .put("bounding_box_mm", result.boundingBox?.toJson() ?: JSONObject.NULL)
        .put(
            "contract",
            JSONObject()
                .put("purpose", "Measured surface candidate for reconstruction inspection")
                .put("triangles", "No triangle mesh is generated in this artifact")
                .put("repairs", "No hole fill or inferred geometry is included")
                .put("export", "STL/3MF export remains blocked")
        )
        .put(
            "coverage_cells",
            JSONArray().apply {
                result.coverageCells.forEach { cell ->
                    put(
                        JSONObject()
                            .put("cell_id", cell.cellId)
                            .put("column", cell.column)
                            .put("row", cell.row)
                            .put("point_count", cell.pointCount)
                            .put("measured_high_count", cell.measuredHighCount)
                            .put("provenance_class", cell.provenanceClass)
                    )
                }
            }
        )
        .put(
            "candidate_points",
            JSONArray().apply {
                result.candidatePoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_point_id", point.sourcePointId)
                            .put("source_track_id", point.sourceTrackId)
                            .put("contributing_frames", JSONArray(point.contributingFrames))
                            .put("xyz_mm", JSONObject().put("x", point.xMm).put("y", point.yMm).put("z", point.zMm))
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )

private fun ScannerDenseBoundingBox.toJson(): JSONObject =
    JSONObject()
        .put("min", JSONObject().put("x", minX).put("y", minY).put("z", minZ))
        .put("max", JSONObject().put("x", maxX).put("y", maxY).put("z", maxZ))
        .put("extent", JSONObject().put("x", extentX).put("y", extentY).put("z", extentZ))

private const val SURFACE_COVERAGE_GRID_COLUMNS = 4
private const val SURFACE_COVERAGE_GRID_ROWS = 4
private const val SURFACE_COVERAGE_CELL_COUNT = SURFACE_COVERAGE_GRID_COLUMNS * SURFACE_COVERAGE_GRID_ROWS
