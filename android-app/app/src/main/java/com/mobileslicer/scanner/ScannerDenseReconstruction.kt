package com.mobileslicer.scanner

import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_DENSE_RECONSTRUCTION_PATH = "dense/dense_reconstruction.json"

internal data class ScannerDenseReconstructionLimits(
    val minMetricSparsePoints: Int = 40,
    val minContributingFrameCount: Int = 3,
    val maxSparsePointResidualPx: Float = 2.5f,
    val minMeasuredHighRatio: Float = 0.50f,
    val minObjectExtentMm: Float = 10.0f
)

internal data class ScannerDenseSeedPoint(
    val pointId: String,
    val sourceTrackId: String,
    val contributingFrames: List<String>,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val reprojectionResidualPx: Float,
    val provenanceClass: String
)

internal data class ScannerDenseReconstructionResult(
    val allowed: Boolean,
    val metric: Boolean,
    val denseReconstructionAdmitted: Boolean,
    val meshGenerationReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val sparsePointCount: Int,
    val contributingFrameCount: Int,
    val measuredHighRatio: Float,
    val boundingBox: ScannerDenseBoundingBox?,
    val seedPoints: List<ScannerDenseSeedPoint>
)

internal data class ScannerDenseBoundingBox(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double
) {
    val extentX: Double get() = maxX - minX
    val extentY: Double get() = maxY - minY
    val extentZ: Double get() = maxZ - minZ
    val maxExtent: Double get() = max(extentX, max(extentY, extentZ))
}

internal fun admitScannerDenseReconstruction(
    workspaceDir: File,
    limits: ScannerDenseReconstructionLimits = ScannerDenseReconstructionLimits()
): ScannerDenseReconstructionResult {
    val sparseTriangulation = workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }

    if (sparseTriangulation == null) {
        val result = blockedScannerDenseReconstruction(listOf("sparse_triangulation_missing"))
        writeScannerDenseReconstruction(workspaceDir, result, limits)
        return result
    }

    val seedPoints = parseDenseSeedPoints(sparseTriangulation.optJSONArray("measured_points") ?: JSONArray())
    val boundingBox = denseBoundingBox(seedPoints)
    val contributingFrameCount = seedPoints.flatMap { it.contributingFrames }.distinct().size
    val measuredHighCount = seedPoints.count { it.provenanceClass == "measured_high" }
    val measuredHighRatio = if (seedPoints.isEmpty()) {
        0f
    } else {
        measuredHighCount.toFloat() / seedPoints.size.toFloat()
    }
    val errors = buildList {
        if (!sparseTriangulation.optBoolean("allowed", false)) add("sparse_triangulation_blocked")
        if (!sparseTriangulation.optBoolean("metric", false)) add("metric_sparse_points_missing")
        if (!sparseTriangulation.optBoolean("dense_reconstruction_ready", false)) {
            add("dense_reconstruction_not_admitted_by_sparse_stage")
        }
        if (seedPoints.size < limits.minMetricSparsePoints) add("metric_sparse_point_count_low")
        if (contributingFrameCount < limits.minContributingFrameCount) add("contributing_frame_count_low")
        if (seedPoints.any { it.reprojectionResidualPx > limits.maxSparsePointResidualPx }) {
            add("metric_sparse_point_residual_high")
        }
        if (seedPoints.any { it.provenanceClass == "rejected" }) add("metric_sparse_point_provenance_rejected")
        if (measuredHighRatio < limits.minMeasuredHighRatio) add("measured_high_ratio_low")
        if (boundingBox == null) {
            add("metric_sparse_bounds_missing")
        } else if (boundingBox.maxExtent < limits.minObjectExtentMm) {
            add("metric_sparse_extent_low")
        }
    }.distinct()
    val accepted = errors.isEmpty()
    val result = ScannerDenseReconstructionResult(
        allowed = accepted,
        metric = accepted,
        denseReconstructionAdmitted = accepted,
        meshGenerationReady = false,
        status = if (accepted) "dense_reconstruction_admitted" else "dense_reconstruction_blocked",
        errors = if (accepted) {
            emptyList()
        } else {
            errors + "mesh_generation_blocked_until_dense_reconstruction"
        },
        warnings = if (accepted) {
            listOf("mesh_generation_remains_blocked_until_dense_surface_exists")
        } else {
            listOf("accepted_metric_sparse_points_required_before_dense_reconstruction")
        },
        sparsePointCount = seedPoints.size,
        contributingFrameCount = contributingFrameCount,
        measuredHighRatio = measuredHighRatio,
        boundingBox = boundingBox,
        seedPoints = if (accepted) seedPoints else emptyList()
    )
    writeScannerDenseReconstruction(workspaceDir, result, limits)
    return result
}

private fun blockedScannerDenseReconstruction(errors: List<String>): ScannerDenseReconstructionResult =
    ScannerDenseReconstructionResult(
        allowed = false,
        metric = false,
        denseReconstructionAdmitted = false,
        meshGenerationReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        sparsePointCount = 0,
        contributingFrameCount = 0,
        measuredHighRatio = 0f,
        boundingBox = null,
        seedPoints = emptyList()
    )

private fun parseDenseSeedPoints(points: JSONArray): List<ScannerDenseSeedPoint> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            val contributingFrames = point.optJSONArray("contributing_frames") ?: return@mapNotNull null
            if (point.isNull("reprojection_residual_px")) return@mapNotNull null
            ScannerDenseSeedPoint(
                pointId = point.optString("point_id"),
                sourceTrackId = point.optString("source_track_id"),
                contributingFrames = List(contributingFrames.length()) { index -> contributingFrames.getString(index) },
                xMm = xyz.getDouble("x"),
                yMm = xyz.getDouble("y"),
                zMm = xyz.getDouble("z"),
                reprojectionResidualPx = point.optDouble("reprojection_residual_px").toFloat(),
                provenanceClass = point.optString("provenance_class", "rejected")
            )
        }

private fun denseBoundingBox(points: List<ScannerDenseSeedPoint>): ScannerDenseBoundingBox? {
    if (points.isEmpty()) return null
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var maxZ = Double.NEGATIVE_INFINITY
    points.forEach { point ->
        minX = min(minX, point.xMm)
        minY = min(minY, point.yMm)
        minZ = min(minZ, point.zMm)
        maxX = max(maxX, point.xMm)
        maxY = max(maxY, point.yMm)
        maxZ = max(maxZ, point.zMm)
    }
    return ScannerDenseBoundingBox(
        minX = minX,
        minY = minY,
        minZ = minZ,
        maxX = maxX,
        maxY = maxY,
        maxZ = maxZ
    )
}

private fun writeScannerDenseReconstruction(
    workspaceDir: File,
    result: ScannerDenseReconstructionResult,
    limits: ScannerDenseReconstructionLimits
) {
    workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerDenseReconstructionJson(result, limits).toString(2))
    }
}

internal fun scannerDenseReconstructionJson(
    result: ScannerDenseReconstructionResult,
    limits: ScannerDenseReconstructionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("dense_reconstruction_admitted", result.denseReconstructionAdmitted)
        .put("mesh_generation_ready", result.meshGenerationReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("sparse_point_count", result.sparsePointCount)
        .put("contributing_frame_count", result.contributingFrameCount)
        .put("measured_high_ratio", result.measuredHighRatio.toDouble())
        .put("bounding_box_mm", result.boundingBox?.toJson() ?: JSONObject.NULL)
        .put(
            "limits",
            JSONObject()
                .put("min_metric_sparse_points", limits.minMetricSparsePoints)
                .put("min_contributing_frame_count", limits.minContributingFrameCount)
                .put("max_sparse_point_residual_px", limits.maxSparsePointResidualPx.toDouble())
                .put("min_measured_high_ratio", limits.minMeasuredHighRatio.toDouble())
                .put("min_object_extent_mm", limits.minObjectExtentMm.toDouble())
        )
        .put(
            "seed_point_schema",
            JSONObject()
                .put("point_id", "Stable metric sparse seed point id")
                .put("source_track_id", "Feature track used as source evidence")
                .put("contributing_frames", "Frames that observed this seed point")
                .put("xyz_mm", "Metric XYZ seed coordinate in millimeters")
                .put("reprojection_residual_px", "Sparse point residual carried into dense admission")
                .put("provenance_class", "measured_high | measured_low")
        )
        .put(
            "seed_points",
            JSONArray().apply {
                result.seedPoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
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
