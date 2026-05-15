package com.mobileslicer.scanner

import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH = "sparse/experimental_sparse_preview.json"

internal data class ScannerExperimentalSparsePreviewLimits(
    val maxPreviewPoints: Int = 1_000,
    val minDepthPreviewPoints: Int = 30,
    val minFeatureTracks: Int = 40
)

internal data class ScannerExperimentalSparsePreviewPoint(
    val pointId: String,
    val sourceFrameId: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val provenanceClass: String
)

internal data class ScannerExperimentalSparsePreviewResult(
    val allowed: Boolean,
    val metric: Boolean,
    val printable: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val featureTrackCount: Int,
    val depthPointCount: Int,
    val previewPointCount: Int,
    val boundingBox: ScannerExperimentalSparsePreviewBounds?,
    val previewPoints: List<ScannerExperimentalSparsePreviewPoint>
)

internal data class ScannerExperimentalSparsePreviewBounds(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double
)

internal fun buildScannerExperimentalSparsePreview(
    workspaceDir: File,
    limits: ScannerExperimentalSparsePreviewLimits = ScannerExperimentalSparsePreviewLimits()
): ScannerExperimentalSparsePreviewResult {
    val depthFusion = workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val featureTracks = workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }

    val tracks = featureTracks?.optJSONArray("tracks") ?: JSONArray()
    val featureTrackCount = featureTracks?.optInt("track_count", tracks.length()) ?: 0
    val depthPoints = parseExperimentalDepthPreviewPoints(
        points = depthFusion?.optJSONArray("back_projected_points") ?: JSONArray(),
        maxPoints = limits.maxPreviewPoints
    )
    val errors = buildList {
        if (depthFusion == null) add("depth_fusion_missing")
        if (featureTracks == null) add("feature_tracks_missing")
        if (featureTrackCount < limits.minFeatureTracks) add("feature_track_count_low")
        if (depthPoints.size < limits.minDepthPreviewPoints) add("depth_preview_point_count_low")
        add("calibration_deferred_preview_is_not_metric")
        add("printable_handoff_blocked_for_experimental_preview")
    }.distinct()
    val hasInspectiblePreview = depthPoints.size >= limits.minDepthPreviewPoints || featureTrackCount >= limits.minFeatureTracks
    val result = ScannerExperimentalSparsePreviewResult(
        allowed = hasInspectiblePreview,
        metric = false,
        printable = false,
        status = if (hasInspectiblePreview) {
            "experimental_sparse_preview_ready_not_metric"
        } else {
            "experimental_sparse_preview_blocked"
        },
        errors = errors,
        warnings = buildList {
            add("preview_is_for_reconstruction_debug_only")
            add("no_slicer_handoff_from_experimental_preview")
            if (depthPoints.isNotEmpty()) add("ar_depth_points_are_measured_assist_not_final_geometry")
            if (featureTrackCount > 0) add("feature_tracks_available_for_pose_reconstruction")
        }.distinct(),
        featureTrackCount = featureTrackCount,
        depthPointCount = depthPoints.size,
        previewPointCount = depthPoints.size,
        boundingBox = experimentalSparseBounds(depthPoints),
        previewPoints = depthPoints
    )
    writeScannerExperimentalSparsePreview(workspaceDir, result, limits)
    return result
}

private fun parseExperimentalDepthPreviewPoints(
    points: JSONArray,
    maxPoints: Int
): List<ScannerExperimentalSparsePreviewPoint> =
    List(points.length()) { index -> points.getJSONObject(index) }
        .asSequence()
        .mapNotNull { point ->
            val xyz = point.optJSONObject("xyz_mm") ?: return@mapNotNull null
            ScannerExperimentalSparsePreviewPoint(
                pointId = point.optString("point_id"),
                sourceFrameId = point.optString("frame_id"),
                xMm = xyz.optDouble("x", Double.NaN),
                yMm = xyz.optDouble("y", Double.NaN),
                zMm = xyz.optDouble("z", Double.NaN),
                provenanceClass = point.optString("provenance_class", "arcore_raw_depth_assist")
            )
        }
        .filter { it.xMm.isFinite() && it.yMm.isFinite() && it.zMm.isFinite() }
        .take(maxPoints.coerceAtLeast(0))
        .toList()

private fun experimentalSparseBounds(
    points: List<ScannerExperimentalSparsePreviewPoint>
): ScannerExperimentalSparsePreviewBounds? {
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
    return ScannerExperimentalSparsePreviewBounds(minX, minY, minZ, maxX, maxY, maxZ)
}

private fun writeScannerExperimentalSparsePreview(
    workspaceDir: File,
    result: ScannerExperimentalSparsePreviewResult,
    limits: ScannerExperimentalSparsePreviewLimits
) {
    workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerExperimentalSparsePreviewJson(result, limits).toString(2))
    }
}

internal fun scannerExperimentalSparsePreviewJson(
    result: ScannerExperimentalSparsePreviewResult,
    limits: ScannerExperimentalSparsePreviewLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("printable", result.printable)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("feature_track_count", result.featureTrackCount)
        .put("depth_point_count", result.depthPointCount)
        .put("preview_point_count", result.previewPointCount)
        .put("bounding_box_mm", result.boundingBox?.toJson() ?: JSONObject.NULL)
        .put(
            "limits",
            JSONObject()
                .put("max_preview_points", limits.maxPreviewPoints)
                .put("min_depth_preview_points", limits.minDepthPreviewPoints)
                .put("min_feature_tracks", limits.minFeatureTracks)
        )
        .put(
            "contract",
            JSONObject()
                .put("purpose", "Inspect local reconstruction progress while calibration is deferred")
                .put("metric_scale_claim_allowed", false)
                .put("slicer_handoff_allowed", false)
                .put("stl_3mf_export_allowed", false)
        )
        .put(
            "preview_points",
            JSONArray().apply {
                result.previewPoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("source_frame_id", point.sourceFrameId)
                            .put(
                                "xyz_mm",
                                JSONObject()
                                    .put("x", point.xMm)
                                    .put("y", point.yMm)
                                    .put("z", point.zMm)
                            )
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )

private fun ScannerExperimentalSparsePreviewBounds.toJson(): JSONObject =
    JSONObject()
        .put("min", JSONObject().put("x", minX).put("y", minY).put("z", minZ))
        .put("max", JSONObject().put("x", maxX).put("y", maxY).put("z", maxZ))
        .put(
            "extent",
            JSONObject()
                .put("x", maxX - minX)
                .put("y", maxY - minY)
                .put("z", maxZ - minZ)
        )
