package com.mobileslicer.scanner

import java.io.File
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_DENSE_POINT_CLOUD_PATH = "dense/dense_point_cloud.json"
internal const val SCANNER_DEBUG_POINT_CLOUD_PLY_PATH = "dense/debug_point_cloud.ply"

internal data class ScannerDensePointCloudLimits(
    val minPointCountForSurface: Int = 250,
    val minContributingFrameCount: Int = 3,
    val minMeasuredHighRatio: Float = 0.50f,
    val maxMeanNearestNeighborMm: Float = 8.0f,
    val minObjectExtentMm: Float = 10.0f
)

internal data class ScannerDensePoint(
    val pointId: String,
    val sourcePointId: String,
    val sourceTrackId: String,
    val contributingFrames: List<String>,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val reprojectionResidualPx: Float,
    val provenanceClass: String
)

internal data class ScannerDensePointCloudResult(
    val allowed: Boolean,
    val metric: Boolean,
    val densePointCloudReady: Boolean,
    val surfaceReconstructionReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val pointCount: Int,
    val contributingFrameCount: Int,
    val measuredHighRatio: Float,
    val meanNearestNeighborMm: Double?,
    val boundingBox: ScannerDenseBoundingBox?,
    val points: List<ScannerDensePoint>
)

internal fun buildScannerDensePointCloud(
    workspaceDir: File,
    limits: ScannerDensePointCloudLimits = ScannerDensePointCloudLimits()
): ScannerDensePointCloudResult {
    val denseAdmission = workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (denseAdmission == null) {
        val result = blockedScannerDensePointCloud(listOf("dense_reconstruction_missing"))
        writeScannerDensePointCloud(workspaceDir, result, limits)
        return result
    }

    val seedPoints = parseDensePointCloudSeeds(denseAdmission.optJSONArray("seed_points") ?: JSONArray()) +
        parseDepthFusionDensePoints(
            workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH)
                .takeIf { it.isFile }
                ?.let { JSONObject(it.readText()) }
        )
    val boundingBox = densePointBoundingBox(seedPoints)
    val contributingFrameCount = seedPoints.flatMap { it.contributingFrames }.distinct().size
    val measuredHighRatio = if (seedPoints.isEmpty()) {
        0f
    } else {
        seedPoints.count { it.provenanceClass == "measured_high" }.toFloat() / seedPoints.size.toFloat()
    }
    val meanNearestNeighbor = meanNearestNeighborDistance(seedPoints)
    val errors = buildList {
        if (!denseAdmission.optBoolean("allowed", false)) add("dense_reconstruction_blocked")
        if (!denseAdmission.optBoolean("metric", false)) add("metric_dense_seed_points_missing")
        if (!denseAdmission.optBoolean("dense_reconstruction_admitted", false)) {
            add("dense_reconstruction_not_admitted")
        }
        if (seedPoints.isEmpty()) add("dense_seed_points_missing")
        if (contributingFrameCount < limits.minContributingFrameCount) add("contributing_frame_count_low")
        if (seedPoints.any { it.provenanceClass == "rejected" || it.provenanceClass == "visual_only" }) {
            add("dense_point_provenance_rejected")
        }
        if (measuredHighRatio < limits.minMeasuredHighRatio) add("measured_high_ratio_low")
        if (boundingBox == null) {
            add("dense_point_bounds_missing")
        } else if (boundingBox.maxExtent < limits.minObjectExtentMm) {
            add("dense_point_extent_low")
        }
    }.distinct()
    val admitted = errors.isEmpty()
    val surfaceErrors = buildList {
        if (!admitted) add("dense_point_cloud_not_ready")
        if (seedPoints.size < limits.minPointCountForSurface) add("dense_point_count_low_for_surface")
        if (meanNearestNeighbor != null && meanNearestNeighbor > limits.maxMeanNearestNeighborMm.toDouble()) {
            add("dense_point_spacing_too_sparse_for_surface")
        }
    }.distinct()
    val surfaceReady = admitted && surfaceErrors.isEmpty()
    val result = ScannerDensePointCloudResult(
        allowed = admitted,
        metric = admitted,
        densePointCloudReady = admitted,
        surfaceReconstructionReady = surfaceReady,
        status = when {
            surfaceReady -> "dense_point_cloud_surface_ready"
            admitted -> "dense_point_cloud_ready_surface_blocked"
            else -> "dense_point_cloud_blocked"
        },
        errors = if (admitted) surfaceErrors else errors + "surface_reconstruction_blocked_until_dense_point_cloud",
        warnings = buildList {
            if (admitted) {
                add("point_cloud_contains_measured_seed_points_only")
                add("no_interpolated_or_ai_generated_points")
            }
            if (!surfaceReady) add("mesh_generation_remains_blocked_until_surface_reconstruction")
        },
        pointCount = if (admitted) seedPoints.size else 0,
        contributingFrameCount = contributingFrameCount,
        measuredHighRatio = measuredHighRatio,
        meanNearestNeighborMm = meanNearestNeighbor,
        boundingBox = boundingBox,
        points = if (admitted) seedPoints else emptyList()
    )
    writeScannerDensePointCloud(workspaceDir, result, limits)
    return result
}

private fun blockedScannerDensePointCloud(errors: List<String>): ScannerDensePointCloudResult =
    ScannerDensePointCloudResult(
        allowed = false,
        metric = false,
        densePointCloudReady = false,
        surfaceReconstructionReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        pointCount = 0,
        contributingFrameCount = 0,
        measuredHighRatio = 0f,
        meanNearestNeighborMm = null,
        boundingBox = null,
        points = emptyList()
    )

private fun parseDensePointCloudSeeds(points: JSONArray): List<ScannerDensePoint> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            val contributingFrames = point.optJSONArray("contributing_frames") ?: return@mapNotNull null
            if (point.isNull("reprojection_residual_px")) return@mapNotNull null
            val pointId = point.optString("point_id")
            ScannerDensePoint(
                pointId = "dense_$pointId",
                sourcePointId = pointId,
                sourceTrackId = point.optString("source_track_id"),
                contributingFrames = List(contributingFrames.length()) { index -> contributingFrames.getString(index) },
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                reprojectionResidualPx = point.optDouble("reprojection_residual_px").toFloat(),
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }

private fun parseDepthFusionDensePoints(depthFusion: JSONObject?): List<ScannerDensePoint> {
    if (depthFusion == null || !depthFusion.optBoolean("allowed", false)) return emptyList()
    val points = depthFusion.optJSONArray("back_projected_points") ?: return emptyList()
    return List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            ScannerDensePoint(
                pointId = "dense_depth_${point.optString("point_id")}",
                sourcePointId = point.optString("point_id"),
                sourceTrackId = "arcore_raw_depth:${point.optString("frame_id")}",
                contributingFrames = listOf(point.optString("frame_id")),
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                reprojectionResidualPx = 0f,
                provenanceClass = point.optString("provenance_class", "measured_low")
            )
        }
}

private fun densePointBoundingBox(points: List<ScannerDensePoint>): ScannerDenseBoundingBox? {
    if (points.isEmpty()) return null
    return ScannerDenseBoundingBox(
        minX = points.minOf { it.xMm },
        minY = points.minOf { it.yMm },
        minZ = points.minOf { it.zMm },
        maxX = points.maxOf { it.xMm },
        maxY = points.maxOf { it.yMm },
        maxZ = points.maxOf { it.zMm }
    )
}

private fun meanNearestNeighborDistance(points: List<ScannerDensePoint>): Double? {
    if (points.size < 2) return null
    val distances = points.map { point ->
        points
            .asSequence()
            .filter { it.pointId != point.pointId }
            .map { other ->
                val dx = point.xMm - other.xMm
                val dy = point.yMm - other.yMm
                val dz = point.zMm - other.zMm
                sqrt(dx * dx + dy * dy + dz * dz)
            }
            .minOrNull() ?: 0.0
    }
    return distances.average()
}

private fun writeScannerDensePointCloud(
    workspaceDir: File,
    result: ScannerDensePointCloudResult,
    limits: ScannerDensePointCloudLimits
) {
    workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerDensePointCloudJson(result, limits).toString(2))
    }
    if (result.points.isNotEmpty()) {
        workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).also {
            it.parentFile?.mkdirs()
            it.writeText(scannerDebugPointCloudPly(result.points))
        }
    }
}

internal fun scannerDebugPointCloudPly(points: List<ScannerDensePoint>): String =
    buildString {
        appendLine("ply")
        appendLine("format ascii 1.0")
        appendLine("comment MobileSlicer debug point cloud; not printable mesh; no slicer handoff")
        appendLine("comment provenance colors: measured_high green, measured_low yellow")
        appendLine("element vertex ${points.size}")
        appendLine("property double x")
        appendLine("property double y")
        appendLine("property double z")
        appendLine("property uchar red")
        appendLine("property uchar green")
        appendLine("property uchar blue")
        appendLine("end_header")
        points.forEach { point ->
            val color = when (point.provenanceClass) {
                "measured_high" -> Triple(40, 220, 110)
                "measured_low" -> Triple(245, 190, 60)
                else -> Triple(220, 70, 70)
            }
            append(point.xMm)
            append(' ')
            append(point.yMm)
            append(' ')
            append(point.zMm)
            append(' ')
            append(color.first)
            append(' ')
            append(color.second)
            append(' ')
            append(color.third)
            append('\n')
        }
    }

internal fun scannerDensePointCloudJson(
    result: ScannerDensePointCloudResult,
    limits: ScannerDensePointCloudLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("dense_point_cloud_ready", result.densePointCloudReady)
        .put("surface_reconstruction_ready", result.surfaceReconstructionReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("point_count", result.pointCount)
        .put("contributing_frame_count", result.contributingFrameCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("mean_nearest_neighbor_mm", result.meanNearestNeighborMm ?: JSONObject.NULL)
        .put("bounding_box_mm", result.boundingBox?.toJson() ?: JSONObject.NULL)
        .put("debug_point_cloud_ply", if (result.points.isNotEmpty()) SCANNER_DEBUG_POINT_CLOUD_PLY_PATH else JSONObject.NULL)
        .put("debug_point_cloud_printable", false)
        .put("debug_point_cloud_slicer_handoff_allowed", false)
        .put(
            "limits",
            JSONObject()
                .put("min_point_count_for_surface", limits.minPointCountForSurface)
                .put("min_contributing_frame_count", limits.minContributingFrameCount)
                .put("min_measured_high_ratio", limits.minMeasuredHighRatio.toDouble())
                .put("max_mean_nearest_neighbor_mm", limits.maxMeanNearestNeighborMm.toDouble())
                .put("min_object_extent_mm", limits.minObjectExtentMm.toDouble())
        )
        .put(
            "point_schema",
            JSONObject()
                .put("point_id", "Stable dense point id")
                .put("source_point_id", "Dense admission seed point id")
                .put("source_track_id", "Original feature track id")
                .put("contributing_frames", "Frames that measured the source point")
                .put("xyz_mm", "Metric XYZ coordinate in millimeters")
                .put("reprojection_residual_px", "Residual carried from measured evidence")
                .put("provenance_class", "measured_high | measured_low")
        )
        .put(
            "points",
            JSONArray().apply {
                result.points.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_point_id", point.sourcePointId)
                            .put("source_track_id", point.sourceTrackId)
                            .put("contributing_frames", JSONArray(point.contributingFrames))
                            .put(
                                "xyz_mm",
                                JSONObject()
                                    .put("x", point.xMm)
                                    .put("y", point.yMm)
                                    .put("z", point.zMm)
                            )
                            .put("reprojection_residual_px", point.reprojectionResidualPx.toDouble())
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
