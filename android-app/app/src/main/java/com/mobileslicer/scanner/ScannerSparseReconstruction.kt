package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_SPARSE_RECONSTRUCTION_PATH = "sparse/sparse_reconstruction.json"

internal data class ScannerSparseReconstructionLimits(
    val minPreparedTracks: Int = 40,
    val minScaleConfidence: Float = 0.85f,
    val maxMarkerReprojectionErrorPx: Float = 3.0f,
    val requireMetricPoses: Boolean = true
)

internal data class ScannerSparseTrackInput(
    val trackId: String,
    val observationCount: Int,
    val normalizedObservations: List<ScannerSparseNormalizedObservation>
)

internal data class ScannerSparseNormalizedObservation(
    val frameId: String,
    val xNormalizedCamera: Double,
    val yNormalizedCamera: Double
)

internal data class ScannerSparseReconstructionResult(
    val allowed: Boolean,
    val metric: Boolean,
    val triangulationReady: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val preparedTrackCount: Int,
    val scaleConfidence: Float,
    val markerReprojectionErrorPx: Float?,
    val tracks: List<ScannerSparseTrackInput>
)

internal fun prepareScannerSparseReconstruction(
    workspaceDir: File,
    limits: ScannerSparseReconstructionLimits = ScannerSparseReconstructionLimits()
): ScannerSparseReconstructionResult {
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val tracksJson = workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (reconstructionJob == null || tracksJson == null) {
        val result = blockedScannerSparseReconstruction(
            errors = buildList {
                if (reconstructionJob == null) add("reconstruction_job_missing")
                if (tracksJson == null) add("feature_tracks_missing")
            }
        )
        writeScannerSparseReconstruction(workspaceDir, result, limits)
        return result
    }

    val frameIntrinsics = parseSparseFrameIntrinsics(reconstructionJob)
    val calibration = reconstructionJob.optJSONObject("calibration")
    val scaleSource = reconstructionJob.optString("scale_source", ScannerScaleSource.None.manifestValue)
    val scaleConfidence = calibration?.optDouble("scale_confidence", 0.0)?.toFloat() ?: 0f
    val markerReprojectionError = calibration
        ?.takeIf { !it.isNull("marker_reprojection_error_px") }
        ?.optDouble("marker_reprojection_error_px")
        ?.toFloat()
    val tracksAllowed = tracksJson.optBoolean("allowed", false)
    val tracks = parseSparseTrackInputs(tracksJson, frameIntrinsics)
    val inputErrors = buildList {
        if (!tracksAllowed) add("feature_tracks_blocked")
        addAll(tracksJson.optJSONArray("errors").scannerSparseStringList().map { "feature_tracks:$it" })
        if (tracks.size < limits.minPreparedTracks) add("not_enough_prepared_tracks")
        if (scaleSource != ScannerScaleSource.VerifiedMarkerMat.manifestValue) add("verified_marker_mat_required")
        if (calibration == null) add("calibration_missing")
        if (scaleConfidence < limits.minScaleConfidence) add("scale_confidence_low")
        if (markerReprojectionError == null) add("marker_reprojection_missing")
        if (markerReprojectionError != null && markerReprojectionError > limits.maxMarkerReprojectionErrorPx) {
            add("marker_reprojection_error_high")
        }
    }.distinct()
    val metricTriangulationBlockers = buildList {
        if (limits.requireMetricPoses) add("metric_camera_poses_missing")
        add("triangulation_blocked_until_metric_poses")
        add("bundle_adjustment_required")
    }.distinct()
    val inputsReady = inputErrors.isEmpty()
    val result = ScannerSparseReconstructionResult(
        allowed = inputsReady,
        metric = false,
        triangulationReady = false,
        errors = if (inputsReady) emptyList() else inputErrors + metricTriangulationBlockers,
        warnings = buildList {
            if (inputsReady) add("sparse_track_inputs_ready_for_metric_optimizer")
            if (inputsReady) addAll(metricTriangulationBlockers)
            add("sparse_inputs_only")
            add("no_3d_points_generated")
        },
        preparedTrackCount = if (inputsReady) tracks.size else tracks.size,
        scaleConfidence = scaleConfidence,
        markerReprojectionErrorPx = markerReprojectionError,
        tracks = if (inputsReady) tracks else tracks
    )
    writeScannerSparseReconstruction(workspaceDir, result, limits)
    return result
}

private fun blockedScannerSparseReconstruction(errors: List<String>): ScannerSparseReconstructionResult =
    ScannerSparseReconstructionResult(
        allowed = false,
        metric = false,
        triangulationReady = false,
        errors = errors.distinct(),
        warnings = emptyList(),
        preparedTrackCount = 0,
        scaleConfidence = 0f,
        markerReprojectionErrorPx = null,
        tracks = emptyList()
    )

private fun parseSparseFrameIntrinsics(reconstructionJob: JSONObject): Map<String, CameraIntrinsicsData> {
    val frames = reconstructionJob.optJSONArray("frames") ?: JSONArray()
    return List(frames.length()) { index -> frames.getJSONObject(index) }
        .mapNotNull { frame ->
            val intrinsics = frame.optJSONObject("intrinsics") ?: return@mapNotNull null
            frame.getString("frame_id") to CameraIntrinsicsData(
                fx = intrinsics.getDouble("fx").toFloat(),
                fy = intrinsics.getDouble("fy").toFloat(),
                cx = intrinsics.getDouble("cx").toFloat(),
                cy = intrinsics.getDouble("cy").toFloat(),
                imageWidth = intrinsics.getInt("width"),
                imageHeight = intrinsics.getInt("height")
            )
        }
        .toMap()
}

private fun parseSparseTrackInputs(
    tracksJson: JSONObject,
    frameIntrinsics: Map<String, CameraIntrinsicsData>
): List<ScannerSparseTrackInput> {
    val tracks = tracksJson.optJSONArray("tracks") ?: JSONArray()
    return List(tracks.length()) { index -> tracks.getJSONObject(index) }
        .mapNotNull { track ->
            val observations = track.optJSONArray("observations") ?: return@mapNotNull null
            val normalized = List(observations.length()) { index -> observations.getJSONObject(index) }
                .mapNotNull { observation ->
                    val frameId = observation.getString("frame_id")
                    val intrinsics = frameIntrinsics[frameId] ?: return@mapNotNull null
                    ScannerSparseNormalizedObservation(
                        frameId = frameId,
                        xNormalizedCamera = (observation.getDouble("x") - intrinsics.cx.toDouble()) / intrinsics.fx.toDouble(),
                        yNormalizedCamera = (observation.getDouble("y") - intrinsics.cy.toDouble()) / intrinsics.fy.toDouble()
                    )
                }
            if (normalized.size < 2) {
                null
            } else {
                ScannerSparseTrackInput(
                    trackId = track.getString("track_id"),
                    observationCount = normalized.size,
                    normalizedObservations = normalized
                )
            }
        }
}

private fun writeScannerSparseReconstruction(
    workspaceDir: File,
    result: ScannerSparseReconstructionResult,
    limits: ScannerSparseReconstructionLimits
) {
    workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerSparseReconstructionJson(result, limits).toString(2))
    }
}

internal fun scannerSparseReconstructionJson(
    result: ScannerSparseReconstructionResult,
    limits: ScannerSparseReconstructionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("triangulation_ready", result.triangulationReady)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("prepared_track_count", result.preparedTrackCount)
        .put("scale_confidence", result.scaleConfidence.toDouble())
        .put("marker_reprojection_error_px", result.markerReprojectionErrorPx ?: JSONObject.NULL)
        .put(
            "limits",
            JSONObject()
                .put("min_prepared_tracks", limits.minPreparedTracks)
                .put("min_scale_confidence", limits.minScaleConfidence.toDouble())
                .put("max_marker_reprojection_error_px", limits.maxMarkerReprojectionErrorPx.toDouble())
                .put("require_metric_poses", limits.requireMetricPoses)
        )
        .put(
            "tracks",
            JSONArray().apply {
                result.tracks.forEach { track ->
                    put(
                        JSONObject()
                            .put("track_id", track.trackId)
                            .put("observation_count", track.observationCount)
                            .put(
                                "normalized_observations",
                                JSONArray().apply {
                                    track.normalizedObservations.forEach { observation ->
                                        put(
                                            JSONObject()
                                                .put("frame_id", observation.frameId)
                                                .put("x_normalized_camera", observation.xNormalizedCamera)
                                                .put("y_normalized_camera", observation.yNormalizedCamera)
                                        )
                                    }
                                }
                            )
                    )
                }
            }
        )

private fun JSONArray?.scannerSparseStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
