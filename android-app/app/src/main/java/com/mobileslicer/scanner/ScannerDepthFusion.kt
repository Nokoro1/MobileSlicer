package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_DEPTH_FUSION_PATH = "depth/depth_fusion.json"

internal data class ScannerDepthFusionLimits(
    val minDepthFrameCount: Int = 3,
    val minAverageDepthCoverage: Float = 0.30f,
    val requireConfidenceImages: Boolean = false,
    val depthSampleStride: Int = 4,
    val maxBackProjectedPoints: Int = 4_000
)

internal data class ScannerDepthFusionFrame(
    val frameId: String,
    val stagedDepth16Path: String,
    val stagedDepth16Sha256: String?,
    val stagedDepthConfidencePath: String?,
    val stagedDepthConfidenceSha256: String?,
    val poseWorldFromCamera: List<Double>?,
    val intrinsics: CameraIntrinsicsData?,
    val depthCoverage: Float?
)

internal data class ScannerDepthFusionResult(
    val allowed: Boolean,
    val metric: Boolean,
    val depthFusionReady: Boolean,
    val denseReconstructionAssistReady: Boolean,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>,
    val depthFrameCount: Int,
    val averageDepthCoverage: Float,
    val frames: List<ScannerDepthFusionFrame>,
    val backProjectedPoints: List<ScannerBackProjectedDepthPoint>
)

internal fun buildScannerDepthFusion(
    workspaceDir: File,
    limits: ScannerDepthFusionLimits = ScannerDepthFusionLimits()
): ScannerDepthFusionResult {
    val job = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (job == null) {
        val result = blockedScannerDepthFusion(listOf("reconstruction_workspace_missing"))
        writeScannerDepthFusion(workspaceDir, result, limits)
        return result
    }

    val frames = parseDepthFusionFrames(job.optJSONArray("frames") ?: JSONArray())
        .filter { workspaceDir.resolve(it.stagedDepth16Path).isFile }
    val averageCoverage = frames.mapNotNull { it.depthCoverage }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
    val errors = buildList {
        if (frames.size < limits.minDepthFrameCount) add("depth_frame_count_low")
        if (averageCoverage < limits.minAverageDepthCoverage) add("depth_coverage_low")
        if (limits.requireConfidenceImages && frames.any { it.stagedDepthConfidencePath == null }) {
            add("depth_confidence_missing")
        }
        if (frames.any { it.stagedDepth16Sha256.isNullOrBlank() }) add("depth_hash_missing")
    }.distinct()
    val accepted = errors.isEmpty()
    val backProjectedPoints = if (accepted) {
        frames.flatMap { frame ->
            backProjectDepthFusionFrame(
                workspaceDir = workspaceDir,
                frame = frame,
                limits = limits
            )
        }.distinctBy { it.quantizedKey(voxelSizeMm = 1.0f) }
            .take(limits.maxBackProjectedPoints)
    } else {
        emptyList()
    }
    val result = ScannerDepthFusionResult(
        allowed = accepted,
        metric = accepted,
        depthFusionReady = accepted,
        denseReconstructionAssistReady = accepted,
        status = if (accepted) "depth_fusion_ready" else "depth_fusion_blocked",
        errors = if (accepted) emptyList() else errors + "dense_depth_assist_blocked_until_depth_fusion",
        warnings = buildList {
            if (accepted) add("depth_is_measured_assist_not_standalone_geometry")
            if (!accepted) add("photogrammetry_pipeline_may_continue_without_depth_assist")
        },
        depthFrameCount = if (accepted) frames.size else 0,
        averageDepthCoverage = averageCoverage,
        frames = if (accepted) frames else emptyList(),
        backProjectedPoints = backProjectedPoints
    )
    writeScannerDepthFusion(workspaceDir, result, limits)
    return result
}

private fun parseDepthFusionFrames(frames: JSONArray): List<ScannerDepthFusionFrame> =
    List(frames.length()) { index -> frames.getJSONObject(index) }
        .mapNotNull { frame ->
            val depthPath = frame.optString("staged_depth16").takeIf { it.isNotBlank() && it != "null" }
                ?: return@mapNotNull null
            val quality = frame.optJSONObject("quality") ?: JSONObject()
            ScannerDepthFusionFrame(
                frameId = frame.optString("frame_id"),
                stagedDepth16Path = depthPath,
                stagedDepth16Sha256 = frame.optString("staged_depth16_sha256").takeIf { it.isNotBlank() && it != "null" },
                stagedDepthConfidencePath = frame.optString("staged_depth_confidence").takeIf { it.isNotBlank() && it != "null" },
                stagedDepthConfidenceSha256 = frame.optString("staged_depth_confidence_sha256").takeIf {
                    it.isNotBlank() && it != "null"
                },
                poseWorldFromCamera = frame.optJSONArray("pose_world_from_camera")?.let { pose ->
                    List(pose.length()) { index -> pose.getDouble(index) }
                },
                intrinsics = frame.optJSONObject("intrinsics")?.let {
                    CameraIntrinsicsData(
                        fx = it.optDouble("fx", 0.0).toFloat(),
                        fy = it.optDouble("fy", 0.0).toFloat(),
                        cx = it.optDouble("cx", 0.0).toFloat(),
                        cy = it.optDouble("cy", 0.0).toFloat(),
                        imageWidth = it.optInt("width", 0),
                        imageHeight = it.optInt("height", 0)
                    )
                },
                depthCoverage = quality.takeUnless { it.isNull("depth_coverage") }?.optDouble("depth_coverage")?.toFloat()
            )
        }

private fun backProjectDepthFusionFrame(
    workspaceDir: File,
    frame: ScannerDepthFusionFrame,
    limits: ScannerDepthFusionLimits
): List<ScannerBackProjectedDepthPoint> {
    val intrinsics = frame.intrinsics ?: return emptyList()
    val encoded = scannerEncodedDepthPngSamples(
        depthPng = workspaceDir.resolve(frame.stagedDepth16Path),
        confidencePng = frame.stagedDepthConfidencePath?.let { workspaceDir.resolve(it) },
        stride = limits.depthSampleStride
    )
    val depthIntrinsics = scaledScannerIntrinsicsForDepth(
        intrinsics = intrinsics,
        depthWidth = encoded.width,
        depthHeight = encoded.height
    )
    return backProjectScannerDepthSamples(
        frameId = frame.frameId,
        samples = encoded.samples,
        intrinsics = depthIntrinsics,
        poseWorldFromCamera = frame.poseWorldFromCamera?.map { it.toFloat() }?.toFloatArray(),
        limits = ScannerDepthBackProjectionLimits(maxSamples = limits.maxBackProjectedPoints)
    )
}

private fun blockedScannerDepthFusion(errors: List<String>): ScannerDepthFusionResult =
    ScannerDepthFusionResult(
        allowed = false,
        metric = false,
        depthFusionReady = false,
        denseReconstructionAssistReady = false,
        status = "blocked",
        errors = errors.distinct(),
        warnings = emptyList(),
        depthFrameCount = 0,
        averageDepthCoverage = 0f,
        frames = emptyList(),
        backProjectedPoints = emptyList()
    )

private fun writeScannerDepthFusion(
    workspaceDir: File,
    result: ScannerDepthFusionResult,
    limits: ScannerDepthFusionLimits
) {
    workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerDepthFusionJson(result, limits).toString(2))
    }
}

internal fun scannerDepthFusionJson(
    result: ScannerDepthFusionResult,
    limits: ScannerDepthFusionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("depth_fusion_ready", result.depthFusionReady)
        .put("dense_reconstruction_assist_ready", result.denseReconstructionAssistReady)
        .put("status", result.status)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("depth_frame_count", result.depthFrameCount)
        .put("average_depth_coverage", result.averageDepthCoverage.toDouble())
        .put("back_projected_point_count", result.backProjectedPoints.size)
        .put(
            "limits",
            JSONObject()
                .put("min_depth_frame_count", limits.minDepthFrameCount)
                .put("min_average_depth_coverage", limits.minAverageDepthCoverage.toDouble())
                .put("require_confidence_images", limits.requireConfidenceImages)
                .put("depth_sample_stride", limits.depthSampleStride)
                .put("max_back_projected_points", limits.maxBackProjectedPoints)
        )
        .put(
            "fusion_contract",
            JSONObject()
                .put("source_workspace", SCANNER_RECONSTRUCTION_MANIFEST_PATH)
                .put("depth_role", "Measured depth assist for dense reconstruction")
                .put("standalone_mesh_allowed", false)
        )
        .put(
            "frames",
            JSONArray().apply {
                result.frames.forEach { frame ->
                    put(
                        JSONObject()
                            .put("frame_id", frame.frameId)
                            .put("staged_depth16", frame.stagedDepth16Path)
                            .put("staged_depth16_sha256", frame.stagedDepth16Sha256 ?: JSONObject.NULL)
                            .put("staged_depth_confidence", frame.stagedDepthConfidencePath ?: JSONObject.NULL)
                            .put("staged_depth_confidence_sha256", frame.stagedDepthConfidenceSha256 ?: JSONObject.NULL)
                            .put(
                                "pose_world_from_camera",
                                frame.poseWorldFromCamera?.let { JSONArray(it) } ?: JSONObject.NULL
                            )
                            .put(
                                "intrinsics",
                                frame.intrinsics?.let {
                                    JSONObject()
                                        .put("fx", it.fx.toDouble())
                                        .put("fy", it.fy.toDouble())
                                        .put("cx", it.cx.toDouble())
                                        .put("cy", it.cy.toDouble())
                                        .put("width", it.imageWidth)
                                        .put("height", it.imageHeight)
                                } ?: JSONObject.NULL
                            )
                            .put("depth_coverage", frame.depthCoverage ?: JSONObject.NULL)
                    )
                }
            }
        )
        .put(
            "back_projected_points",
            JSONArray().apply {
                result.backProjectedPoints.forEach { point ->
                    put(
                        JSONObject()
                            .put("point_id", point.pointId)
                            .put("frame_id", point.frameId)
                            .put(
                                "xyz_mm",
                                JSONObject()
                                    .put("x", point.xMm)
                                    .put("y", point.yMm)
                                    .put("z", point.zMm)
                            )
                            .put("source_x_px", point.sourceXPx)
                            .put("source_y_px", point.sourceYPx)
                            .put("depth_mm", point.depthMm)
                            .put("confidence", point.confidence)
                            .put("provenance_class", point.provenanceClass)
                    )
                }
            }
        )
