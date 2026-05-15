package com.mobileslicer.scanner

import java.io.File
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_FEATURE_TRACKS_PATH = "features/tracks.json"

internal data class ScannerFeatureTrackLimits(
    val minTrackCount: Int = 40,
    val minLongTrackCount: Int = 12,
    val minTrackLength: Int = 2,
    val minLongTrackLength: Int = 3,
    val minSpatialCells: Int = 6,
    val enableDescriptorSeededTracks: Boolean = true,
    val maxDescriptorSeededTracks: Int = 96,
    val maxDescriptorSeededFrameCount: Int = 14,
    val minDescriptorSeededAverageScore: Int = 70,
    val spatialGridColumns: Int = 4,
    val spatialGridRows: Int = 4,
    val requiredPasses: Set<ScannerCapturePass> = setOf(
        ScannerCapturePass.LowRing,
        ScannerCapturePass.MidRing,
        ScannerCapturePass.HighRing
    )
)

internal data class ScannerFeatureTrackObservation(
    val frameId: String,
    val x: Int,
    val y: Int,
    val capturePass: ScannerCapturePass,
    val imageWidth: Int,
    val imageHeight: Int
)

internal data class ScannerFeatureTrack(
    val id: String,
    val observations: List<ScannerFeatureTrackObservation>
) {
    val frameCount: Int = observations.map { it.frameId }.distinct().size
    val passCount: Int = observations.map { it.capturePass }.distinct().size
}

internal data class ScannerFeatureTrackResult(
    val allowed: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val tracks: List<ScannerFeatureTrack>,
    val trackCount: Int,
    val longTrackCount: Int,
    val maxTrackLength: Int,
    val descriptorSeededTrackCount: Int,
    val observedPasses: Set<ScannerCapturePass>,
    val spatialCellCount: Int
)

internal fun buildScannerFeatureTracks(
    workspaceDir: File,
    limits: ScannerFeatureTrackLimits = ScannerFeatureTrackLimits()
): ScannerFeatureTrackResult {
    val featuresFile = workspaceDir.resolve(SCANNER_FEATURES_PATH)
    val matchGraphFile = workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH)
    if (!featuresFile.isFile || !matchGraphFile.isFile) {
        val result = blockedScannerFeatureTracks(listOf("feature_or_match_graph_missing"))
        writeScannerFeatureTracks(workspaceDir, result, limits)
        return result
    }

    val featureFrames = parseFeatureTrackFrames(JSONObject(featuresFile.readText()))
    val matchGraph = JSONObject(matchGraphFile.readText())
    if (!matchGraph.optBoolean("allowed", false)) {
        val errors = listOf("match_graph_blocked") + matchGraph.optJSONArray("errors").scannerTrackStringList()
        val result = blockedScannerFeatureTracks(errors)
        writeScannerFeatureTracks(workspaceDir, result, limits)
        return result
    }

    val builder = FeatureTrackUnionBuilder(featureFrames)
    val pairs = matchGraph.optJSONArray("pairs") ?: JSONArray()
    for (index in 0 until pairs.length()) {
        val pair = pairs.getJSONObject(index)
        if (!pair.optBoolean("accepted", false)) continue
        val frameA = pair.getString("frame_a")
        val frameB = pair.getString("frame_b")
        val matches = pair.optJSONArray("matches") ?: continue
        for (matchIndex in 0 until matches.length()) {
            val match = matches.getJSONObject(matchIndex)
            builder.union(
                FeatureKey(frameA, match.getInt("ax"), match.getInt("ay")),
                FeatureKey(frameB, match.getInt("bx"), match.getInt("by"))
            )
        }
    }

    val tracks = builder.tracks()
        .plus(
            if (limits.enableDescriptorSeededTracks) {
                descriptorSeededScannerFeatureTracks(
                    features = JSONObject(featuresFile.readText()),
                    existingTracks = builder.tracks(),
                    limits = limits
                )
            } else {
                emptyList()
            }
        )
        .distinctBy { track ->
            track.observations.joinToString("|") { "${it.frameId}:${it.x}:${it.y}" }
        }
        .filter { it.frameCount >= limits.minTrackLength }
        .sortedWith(compareByDescending<ScannerFeatureTrack> { it.frameCount }.thenBy { it.id })
    val longTrackCount = tracks.count { it.frameCount >= limits.minLongTrackLength }
    val descriptorSeededTrackCount = tracks.count { it.id.startsWith("descriptor_track_") }
    val maxTrackLength = tracks.maxOfOrNull { it.frameCount } ?: 0
    val observedPasses = tracks
        .flatMap { it.observations.map { observation -> observation.capturePass } }
        .toSet()
    val spatialCellCount = scannerFeatureTrackSpatialCells(tracks, limits).size
    val errors = buildList {
        if (tracks.size < limits.minTrackCount) add("not_enough_feature_tracks")
        if (longTrackCount < limits.minLongTrackCount) add("not_enough_long_feature_tracks")
        if (spatialCellCount < limits.minSpatialCells) add("track_spatial_distribution_low")
        limits.requiredPasses
            .filterNot { observedPasses.contains(it) }
            .forEach { add("track_pass_missing:${it.manifestValue}") }
    }.distinct()
    val warnings = buildList {
        if (maxTrackLength < limits.minLongTrackLength) add("tracks_are_pairwise_only")
        if (tracks.any { it.observations.size != it.frameCount }) add("duplicate_frame_observations_removed")
        if (descriptorSeededTrackCount > 0) {
            add("descriptor_seeded_tracks_require_metric_pose_validation")
        }
    }
    val result = ScannerFeatureTrackResult(
        allowed = errors.isEmpty(),
        errors = errors,
        warnings = warnings,
        tracks = tracks,
        trackCount = tracks.size,
        longTrackCount = longTrackCount,
        maxTrackLength = maxTrackLength,
        descriptorSeededTrackCount = descriptorSeededTrackCount,
        observedPasses = observedPasses,
        spatialCellCount = spatialCellCount
    )
    writeScannerFeatureTracks(workspaceDir, result, limits)
    return result
}

private fun blockedScannerFeatureTracks(errors: List<String>): ScannerFeatureTrackResult =
    ScannerFeatureTrackResult(
        allowed = false,
        errors = errors.distinct(),
        warnings = emptyList(),
        tracks = emptyList(),
        trackCount = 0,
        longTrackCount = 0,
        maxTrackLength = 0,
        descriptorSeededTrackCount = 0,
        observedPasses = emptySet(),
        spatialCellCount = 0
    )

private data class FeatureTrackFrame(
    val frameId: String,
    val capturePass: ScannerCapturePass,
    val width: Int,
    val height: Int
)

private data class DescriptorSeedObservation(
    val descriptorHex: String,
    val frameId: String,
    val x: Int,
    val y: Int,
    val score: Int,
    val capturePass: ScannerCapturePass,
    val width: Int,
    val height: Int
)

private data class FeatureKey(
    val frameId: String,
    val x: Int,
    val y: Int
)

private class FeatureTrackUnionBuilder(
    private val frames: Map<String, FeatureTrackFrame>
) {
    private val parent = mutableMapOf<FeatureKey, FeatureKey>()

    fun union(a: FeatureKey, b: FeatureKey) {
        parent.putIfAbsent(a, a)
        parent.putIfAbsent(b, b)
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) {
            parent[rootB] = rootA
        }
    }

    fun tracks(): List<ScannerFeatureTrack> =
        parent.keys
            .groupBy { find(it) }
            .values
            .mapIndexedNotNull { index, keys ->
                val observations = keys
                    .groupBy { it.frameId }
                    .mapNotNull { (frameId, candidates) ->
                        val frame = frames[frameId] ?: return@mapNotNull null
                        val representative = candidates.sortedWith(compareBy<FeatureKey> { it.y }.thenBy { it.x }).first()
                        ScannerFeatureTrackObservation(
                            frameId = frameId,
                            x = representative.x,
                            y = representative.y,
                            capturePass = frame.capturePass,
                            imageWidth = frame.width,
                            imageHeight = frame.height
                        )
                    }
                    .sortedWith(compareBy<ScannerFeatureTrackObservation> { it.frameId }.thenBy { it.y }.thenBy { it.x })
                if (observations.size < 2) {
                    null
                } else {
                    ScannerFeatureTrack(
                        id = "track_${(index + 1).toString().padStart(5, '0')}",
                        observations = observations
                    )
                }
            }

    private fun find(key: FeatureKey): FeatureKey {
        val current = parent[key] ?: key.also { parent[key] = it }
        if (current == key) return key
        val root = find(current)
        parent[key] = root
        return root
    }
}

private fun parseFeatureTrackFrames(features: JSONObject): Map<String, FeatureTrackFrame> {
    val frames = features.optJSONArray("frames") ?: JSONArray()
    return List(frames.length()) { index -> frames.getJSONObject(index) }
        .associate {
            val frameId = it.getString("frame_id")
            frameId to FeatureTrackFrame(
                frameId = frameId,
                capturePass = ScannerCapturePass.entries.single { pass ->
                    pass.manifestValue == it.getString("capture_pass")
                },
                width = it.getInt("width"),
                height = it.getInt("height")
            )
        }
}

private fun descriptorSeededScannerFeatureTracks(
    features: JSONObject,
    existingTracks: List<ScannerFeatureTrack>,
    limits: ScannerFeatureTrackLimits
): List<ScannerFeatureTrack> {
    val existingObservationKeys = existingTracks
        .flatMap { it.observations }
        .map { "${it.frameId}:${it.x}:${it.y}" }
        .toSet()
    val observationsByDescriptor = parseDescriptorSeedObservations(features)
        .groupBy { it.descriptorHex }
    return observationsByDescriptor
        .values
        .mapNotNull { observations ->
            descriptorSeedTrackCandidate(observations, existingObservationKeys, limits)
        }
        .sortedWith(
            compareByDescending<ScannerFeatureTrack> { it.frameCount }
                .thenByDescending { it.passCount }
                .thenByDescending { track -> track.observations.map { it.x + it.y }.average() }
                .thenBy { it.id }
        )
        .take(limits.maxDescriptorSeededTracks)
        .mapIndexed { index, track ->
            track.copy(id = "descriptor_track_${(index + 1).toString().padStart(5, '0')}")
        }
}

private fun descriptorSeedTrackCandidate(
    observations: List<DescriptorSeedObservation>,
    existingObservationKeys: Set<String>,
    limits: ScannerFeatureTrackLimits
): ScannerFeatureTrack? {
    val byFrame = observations.groupBy { it.frameId }
    if (byFrame.size < limits.minLongTrackLength) return null
    if (byFrame.size > limits.maxDescriptorSeededFrameCount) return null
    val representatives = byFrame.values.map { candidates ->
        candidates.maxWith(
            compareBy<DescriptorSeedObservation> { if ("${it.frameId}:${it.x}:${it.y}" in existingObservationKeys) 1 else 0 }
                .thenBy { it.score }
        )
    }
    if (representatives.map { it.score }.average() < limits.minDescriptorSeededAverageScore.toDouble()) return null
    val passCount = representatives.map { it.capturePass }.distinct().size
    val minRequiredPassCount = minOf(2, limits.requiredPasses.size)
    if (passCount < minRequiredPassCount) return null
    val track = ScannerFeatureTrack(
        id = "descriptor_track_candidate",
        observations = representatives
            .map {
                ScannerFeatureTrackObservation(
                    frameId = it.frameId,
                    x = it.x,
                    y = it.y,
                    capturePass = it.capturePass,
                    imageWidth = it.width,
                    imageHeight = it.height
                )
            }
            .sortedWith(compareBy<ScannerFeatureTrackObservation> { it.frameId }.thenBy { it.y }.thenBy { it.x })
    )
    return track.takeIf { scannerFeatureTrackSpatialCells(listOf(it), limits).size >= 2 }
}

private fun parseDescriptorSeedObservations(features: JSONObject): List<DescriptorSeedObservation> {
    val frames = features.optJSONArray("frames") ?: JSONArray()
    val observations = mutableListOf<DescriptorSeedObservation>()
    for (frameIndex in 0 until frames.length()) {
        val frame = frames.getJSONObject(frameIndex)
        val frameId = frame.getString("frame_id")
        val capturePass = ScannerCapturePass.entries.single { pass ->
            pass.manifestValue == frame.getString("capture_pass")
        }
        val width = frame.getInt("width")
        val height = frame.getInt("height")
        val featureArray = frame.optJSONArray("features") ?: continue
        for (featureIndex in 0 until featureArray.length()) {
            val feature = featureArray.getJSONObject(featureIndex)
            val descriptorHex = feature.optString("descriptor_hex")
            if (descriptorHex.isBlank()) continue
            observations += DescriptorSeedObservation(
                descriptorHex = descriptorHex,
                frameId = frameId,
                x = feature.getInt("x"),
                y = feature.getInt("y"),
                score = feature.optInt("score", 0),
                capturePass = capturePass,
                width = width,
                height = height
            )
        }
    }
    return observations
}

private fun scannerFeatureTrackSpatialCells(
    tracks: List<ScannerFeatureTrack>,
    limits: ScannerFeatureTrackLimits
): Set<Pair<Int, Int>> =
    tracks
        .flatMap { it.observations }
        .map { observation ->
            val col = ((observation.x.toFloat() / observation.imageWidth.toFloat()) * limits.spatialGridColumns)
                .roundToInt()
                .coerceIn(0, limits.spatialGridColumns - 1)
            val row = ((observation.y.toFloat() / observation.imageHeight.toFloat()) * limits.spatialGridRows)
                .roundToInt()
                .coerceIn(0, limits.spatialGridRows - 1)
            col to row
        }
        .toSet()

private fun writeScannerFeatureTracks(
    workspaceDir: File,
    result: ScannerFeatureTrackResult,
    limits: ScannerFeatureTrackLimits
) {
    workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerFeatureTracksJson(result, limits).toString(2))
    }
}

internal fun scannerFeatureTracksJson(
    result: ScannerFeatureTrackResult,
    limits: ScannerFeatureTrackLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("track_count", result.trackCount)
        .put("long_track_count", result.longTrackCount)
        .put("max_track_length", result.maxTrackLength)
        .put("descriptor_seeded_track_count", result.descriptorSeededTrackCount)
        .put("spatial_cell_count", result.spatialCellCount)
        .put(
            "observed_passes",
            JSONArray().apply {
                result.observedPasses.sortedBy { it.ordinal }.forEach { put(it.manifestValue) }
            }
        )
        .put(
            "limits",
            JSONObject()
                .put("min_track_count", limits.minTrackCount)
                .put("min_long_track_count", limits.minLongTrackCount)
                .put("min_track_length", limits.minTrackLength)
                .put("min_long_track_length", limits.minLongTrackLength)
                .put("min_spatial_cells", limits.minSpatialCells)
                .put("enable_descriptor_seeded_tracks", limits.enableDescriptorSeededTracks)
                .put("max_descriptor_seeded_tracks", limits.maxDescriptorSeededTracks)
                .put("max_descriptor_seeded_frame_count", limits.maxDescriptorSeededFrameCount)
                .put("min_descriptor_seeded_average_score", limits.minDescriptorSeededAverageScore)
                .put("spatial_grid_columns", limits.spatialGridColumns)
                .put("spatial_grid_rows", limits.spatialGridRows)
                .put(
                    "required_passes",
                    JSONArray().apply {
                        limits.requiredPasses.sortedBy { it.ordinal }.forEach { put(it.manifestValue) }
                    }
                )
        )
        .put(
            "tracks",
            JSONArray().apply {
                result.tracks.forEach { track ->
                    put(
                        JSONObject()
                            .put("track_id", track.id)
                            .put("frame_count", track.frameCount)
                            .put("pass_count", track.passCount)
                            .put(
                                "observations",
                                JSONArray().apply {
                                    track.observations.forEach { observation ->
                                        put(
                                            JSONObject()
                                                .put("frame_id", observation.frameId)
                                                .put("x", observation.x)
                                                .put("y", observation.y)
                                                .put("capture_pass", observation.capturePass.manifestValue)
                                                .put("image_width", observation.imageWidth)
                                                .put("image_height", observation.imageHeight)
                                        )
                                    }
                                }
                            )
                    )
                }
            }
        )

private fun JSONArray?.scannerTrackStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
