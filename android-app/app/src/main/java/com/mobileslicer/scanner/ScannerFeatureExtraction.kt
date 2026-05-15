package com.mobileslicer.scanner

import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_FEATURES_PATH = "features/features.json"
internal const val SCANNER_MATCH_GRAPH_PATH = "matches/match_graph.json"

internal data class ScannerFeatureExtractionLimits(
    val minFeaturesPerFrame: Int = 24,
    val maxFeaturesPerFrame: Int = 300,
    val sampleStridePx: Int = 6,
    val descriptorRadiusPx: Int = 4,
    val borderRejectRatio: Float = 0.035f,
    val maskHardRejectThreshold: Int = 4,
    val maskSupportThreshold: Int = 32,
    val requireMaskSupportWhenAvailable: Boolean = true,
    val maxDescriptorHammingDistance: Int = 10,
    val minPairMatches: Int = 12
)

internal data class ScannerLumaImage(
    val width: Int,
    val height: Int,
    val luma: IntArray
) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(luma.size == width * height) { "Luma buffer size does not match dimensions" }
    }
}

internal data class ScannerImageFeature(
    val x: Int,
    val y: Int,
    val score: Int,
    val descriptor: Long,
    val maskSupport: Int = 255
)

internal data class ScannerFrameFeatureReport(
    val frameId: String,
    val stagedImagePath: String,
    val stagedMaskPath: String?,
    val capturePass: ScannerCapturePass,
    val imageWidth: Int,
    val imageHeight: Int,
    val features: List<ScannerImageFeature>,
    val warnings: List<String>
)

internal data class ScannerPairMatchReport(
    val frameA: String,
    val frameB: String,
    val matchCount: Int,
    val averageDescriptorDistance: Float?,
    val spatialCellCount: Int,
    val averageMaskSupport: Float?,
    val accepted: Boolean,
    val matches: List<ScannerFeatureCorrespondence>
)

internal data class ScannerFeatureCorrespondence(
    val ax: Int,
    val ay: Int,
    val bx: Int,
    val by: Int,
    val descriptorDistance: Int
)

internal data class ScannerFeatureMatchGraphResult(
    val allowed: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val frameReports: List<ScannerFrameFeatureReport>,
    val pairReports: List<ScannerPairMatchReport>,
    val connectedComponentCount: Int
)

internal fun buildScannerFeatureMatchGraph(
    workspaceDir: File,
    limits: ScannerFeatureExtractionLimits = ScannerFeatureExtractionLimits()
): ScannerFeatureMatchGraphResult {
    val jobFile = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
    if (!jobFile.isFile) {
        return ScannerFeatureMatchGraphResult(
            allowed = false,
            errors = listOf("reconstruction_job_missing"),
            warnings = emptyList(),
            frameReports = emptyList(),
            pairReports = emptyList(),
            connectedComponentCount = 0
        )
    }
    val job = JSONObject(jobFile.readText())
    val frameInputs: List<ScannerFeatureFrameInput> = parseScannerFeatureFrameInputs(job)
    val errors = mutableListOf<String>()
    val frameReports: List<ScannerFrameFeatureReport> = frameInputs.mapNotNull { input: ScannerFeatureFrameInput ->
        val imageFile = workspaceDir.resolve(input.stagedImagePath)
        val image = decodeScannerLumaImage(imageFile)
        if (image == null) {
            errors += "feature_image_decode_failed:${input.frameId}"
            null
        } else {
            val maskWarnings = mutableListOf<String>()
            val mask = input.stagedMaskPath?.let { maskPath ->
                decodeScannerLumaImage(workspaceDir.resolve(maskPath)).also { decoded ->
                    if (decoded == null) maskWarnings += "mask_decode_failed"
                }
            } ?: run {
                maskWarnings += "mask_missing_for_feature_filter"
                null
            }
            val features = extractScannerImageFeatures(image, limits, mask)
            ScannerFrameFeatureReport(
                frameId = input.frameId,
                stagedImagePath = input.stagedImagePath,
                stagedMaskPath = input.stagedMaskPath,
                capturePass = input.capturePass,
                imageWidth = image.width,
                imageHeight = image.height,
                features = features,
                warnings = buildList {
                    addAll(maskWarnings)
                    if (features.size < limits.minFeaturesPerFrame) add("feature_count_low_after_mask_or_border_filter")
                }
            )
        }
    }
    val pairReports = buildScannerPairMatchReports(frameReports, limits)
    val connectedComponents = scannerAcceptedMatchComponents(frameReports, pairReports)
    val graphErrors = buildList {
        addAll(errors)
        frameReports
            .filter { it.features.size < limits.minFeaturesPerFrame }
            .forEach { add("not_enough_features:${it.frameId}") }
        if (frameReports.size != frameInputs.size) add("feature_reports_missing")
        if (frameReports.size >= 2 && connectedComponents != 1) add("match_graph_disconnected")
        if (frameReports.size < 2) add("not_enough_frames_for_matching")
    }
    val result = ScannerFeatureMatchGraphResult(
        allowed = graphErrors.isEmpty(),
        errors = graphErrors.distinct(),
        warnings = frameReports.flatMap { report -> report.warnings.map { "${report.frameId}:$it" } }.distinct(),
        frameReports = frameReports,
        pairReports = pairReports,
        connectedComponentCount = connectedComponents
    )
    writeScannerFeatureDiagnostics(workspaceDir, result, limits)
    return result
}

internal fun extractScannerImageFeatures(
    image: ScannerLumaImage,
    limits: ScannerFeatureExtractionLimits = ScannerFeatureExtractionLimits(),
    mask: ScannerLumaImage? = null
): List<ScannerImageFeature> {
    val radius = limits.descriptorRadiusPx.coerceAtLeast(2)
    val stride = limits.sampleStridePx.coerceAtLeast(2)
    val borderMargin = maxOf(
        radius + 1,
        (minOf(image.width, image.height) * limits.borderRejectRatio)
            .roundToInt()
            .coerceAtLeast(0)
    )
    if (image.width <= (borderMargin + radius + 1) * 2 || image.height <= (borderMargin + radius + 1) * 2) {
        return emptyList()
    }
    val candidates = mutableListOf<ScannerImageFeature>()
    for (y in borderMargin until image.height - borderMargin step stride) {
        for (x in borderMargin until image.width - borderMargin step stride) {
            val maskSupport = scannerFeatureMaskSupport(image, mask, x, y)
            if (!scannerFeatureMaskAllows(maskSupport, mask, limits)) continue
            val center = image.luma[y * image.width + x]
            val dx = image.luma[y * image.width + x + 1] - image.luma[y * image.width + x - 1]
            val dy = image.luma[(y + 1) * image.width + x] - image.luma[(y - 1) * image.width + x]
            val diagonal = image.luma[(y + 1) * image.width + x + 1] - image.luma[(y - 1) * image.width + x - 1]
            val laplacian = abs((4 * center) -
                image.luma[y * image.width + x - 1] -
                image.luma[y * image.width + x + 1] -
                image.luma[(y - 1) * image.width + x] -
                image.luma[(y + 1) * image.width + x])
            val score = abs(dx) + abs(dy) + abs(diagonal) + laplacian
            val weightedScore = scannerMaskWeightedFeatureScore(score, maskSupport, mask, limits)
            if (weightedScore > 40) {
                candidates += ScannerImageFeature(
                    x = x,
                    y = y,
                    score = weightedScore,
                    descriptor = scannerPatchDescriptor(image, x, y, radius),
                    maskSupport = maskSupport
                )
            }
        }
    }
    return candidates
        .sortedWith(compareByDescending<ScannerImageFeature> { it.score }.thenBy { it.y }.thenBy { it.x })
        .distinctBy { "${it.x / stride}:${it.y / stride}:${it.descriptor}" }
        .take(limits.maxFeaturesPerFrame)
}

internal fun buildScannerPairMatchReports(
    frameReports: List<ScannerFrameFeatureReport>,
    limits: ScannerFeatureExtractionLimits = ScannerFeatureExtractionLimits()
): List<ScannerPairMatchReport> {
    val reports = mutableListOf<ScannerPairMatchReport>()
    for (a in frameReports.indices) {
        for (b in a + 1 until frameReports.size) {
            val matchCount = scannerDescriptorMatchCount(
                frameReports[a].features,
                frameReports[b].features,
                limits.maxDescriptorHammingDistance
            )
            val matches = scannerDescriptorMatches(
                frameReports[a].features,
                frameReports[b].features,
                limits.maxDescriptorHammingDistance
            )
            reports += ScannerPairMatchReport(
                frameA = frameReports[a].frameId,
                frameB = frameReports[b].frameId,
                matchCount = matches.size.coerceAtLeast(matchCount),
                averageDescriptorDistance = matches
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.descriptorDistance }
                    ?.average()
                    ?.toFloat(),
                spatialCellCount = scannerMatchSpatialCells(
                    matches = matches,
                    width = minOf(frameReports[a].imageWidth, frameReports[b].imageWidth),
                    height = minOf(frameReports[a].imageHeight, frameReports[b].imageHeight)
                ),
                averageMaskSupport = scannerAverageMatchMaskSupport(
                    matches = matches,
                    featuresA = frameReports[a].features,
                    featuresB = frameReports[b].features
                ),
                accepted = matches.size >= limits.minPairMatches,
                matches = matches
            )
        }
    }
    return reports
}

private data class ScannerFeatureFrameInput(
    val frameId: String,
    val stagedImagePath: String,
    val stagedMaskPath: String?,
    val capturePass: ScannerCapturePass
)

private fun parseScannerFeatureFrameInputs(job: JSONObject): List<ScannerFeatureFrameInput> {
    val frames = job.getJSONArray("frames")
    return List(frames.length()) { index ->
        val frame = frames.getJSONObject(index)
        ScannerFeatureFrameInput(
            frameId = frame.getString("frame_id"),
            stagedImagePath = frame.getString("staged_image"),
            stagedMaskPath = frame.optString("staged_mask").takeIf { it.isNotBlank() && it != "null" },
            capturePass = scannerFeatureCapturePass(frame.getString("capture_pass"))
        )
    }
}

private fun scannerFeatureCapturePass(value: String): ScannerCapturePass =
    ScannerCapturePass.entries.single { it.manifestValue == value }

private fun scannerFeatureMaskSupport(
    image: ScannerLumaImage,
    mask: ScannerLumaImage?,
    x: Int,
    y: Int
): Int {
    if (mask == null) return 255
    val maskX = ((x.toFloat() / image.width.toFloat()) * mask.width.toFloat())
        .toInt()
        .coerceIn(0, mask.width - 1)
    val maskY = ((y.toFloat() / image.height.toFloat()) * mask.height.toFloat())
        .toInt()
        .coerceIn(0, mask.height - 1)
    return mask.luma[maskY * mask.width + maskX].coerceIn(0, 255)
}

private fun scannerFeatureMaskAllows(
    maskSupport: Int,
    mask: ScannerLumaImage?,
    limits: ScannerFeatureExtractionLimits
): Boolean {
    if (mask == null || !limits.requireMaskSupportWhenAvailable) return true
    return maskSupport >= limits.maskHardRejectThreshold
}

private fun scannerMaskWeightedFeatureScore(
    score: Int,
    maskSupport: Int,
    mask: ScannerLumaImage?,
    limits: ScannerFeatureExtractionLimits
): Int {
    if (mask == null || !limits.requireMaskSupportWhenAvailable) return score
    val support = maskSupport.coerceIn(0, 255)
    val softWeight = 0.35f + 0.65f * (support.toFloat() / 255f)
    val thresholdBonus = if (support >= limits.maskSupportThreshold) 1.12f else 0.82f
    return (score.toFloat() * softWeight * thresholdBonus).roundToInt()
}

private fun decodeScannerLumaImage(file: File): ScannerLumaImage? {
    decodeScannerPlainGrayMap(file)?.let { return it }
    val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    if (bitmap != null) {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            ScannerLumaImage(
                width = width,
                height = height,
                luma = IntArray(pixels.size) { scannerFeatureArgbToLuma(pixels[it]) }
            )
        } finally {
            bitmap.recycle()
        }
    }
    return null
}

private fun decodeScannerPlainGrayMap(file: File): ScannerLumaImage? {
    val tokens = runCatching {
        file.readText()
            .lineSequence()
            .map { it.substringBefore("#") }
            .flatMap { it.trim().split(Regex("\\s+")).asSequence() }
            .filter { it.isNotBlank() }
            .toList()
    }.getOrNull() ?: return null
    if (tokens.size < 4 || tokens[0] != "P2") return null
    val width = tokens[1].toIntOrNull() ?: return null
    val height = tokens[2].toIntOrNull() ?: return null
    val maxValue = tokens[3].toIntOrNull()?.coerceAtLeast(1) ?: return null
    val values = tokens.drop(4).mapNotNull { it.toIntOrNull() }
    if (width <= 0 || height <= 0 || values.size < width * height) return null
    return ScannerLumaImage(
        width = width,
        height = height,
        luma = IntArray(width * height) { index ->
            ((values[index].coerceIn(0, maxValue).toFloat() / maxValue.toFloat()) * 255f).toInt().coerceIn(0, 255)
        }
    )
}

private fun scannerPatchDescriptor(image: ScannerLumaImage, centerX: Int, centerY: Int, radius: Int): Long {
    var sum = 0
    var count = 0
    for (dy in -radius until radius) {
        for (dx in -radius until radius) {
            sum += image.luma[(centerY + dy) * image.width + centerX + dx]
            count += 1
        }
    }
    val mean = if (count == 0) 0 else sum / count
    var descriptor = 0L
    var bit = 0
    for (dy in -radius until radius) {
        for (dx in -radius until radius) {
            if (bit >= Long.SIZE_BITS) return descriptor
            val value = image.luma[(centerY + dy) * image.width + centerX + dx]
            if (value >= mean) descriptor = descriptor or (1L shl bit)
            bit += 1
        }
    }
    return descriptor
}

private fun scannerDescriptorMatchCount(
    featuresA: List<ScannerImageFeature>,
    featuresB: List<ScannerImageFeature>,
    maxDistance: Int
): Int = scannerDescriptorMatches(featuresA, featuresB, maxDistance).size

private fun scannerDescriptorMatches(
    featuresA: List<ScannerImageFeature>,
    featuresB: List<ScannerImageFeature>,
    maxDistance: Int
): List<ScannerFeatureCorrespondence> {
    val usedB = mutableSetOf<Int>()
    val matches = mutableListOf<ScannerFeatureCorrespondence>()
    featuresA.forEach { featureA ->
        var bestIndex = -1
        var bestDistance = Int.MAX_VALUE
        featuresB.forEachIndexed { index, featureB ->
            if (index !in usedB) {
                val distance = java.lang.Long.bitCount(featureA.descriptor xor featureB.descriptor)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
        }
        if (bestIndex >= 0 && bestDistance <= maxDistance) {
            usedB += bestIndex
            val featureB = featuresB[bestIndex]
            matches += ScannerFeatureCorrespondence(
                ax = featureA.x,
                ay = featureA.y,
                bx = featureB.x,
                by = featureB.y,
                descriptorDistance = bestDistance
            )
        }
    }
    return matches
}

private fun scannerAcceptedMatchComponents(
    frames: List<ScannerFrameFeatureReport>,
    pairs: List<ScannerPairMatchReport>
): Int {
    if (frames.isEmpty()) return 0
    val parent = frames.associate { it.frameId to it.frameId }.toMutableMap()
    fun find(id: String): String {
        val current = parent.getValue(id)
        if (current == id) return id
        val root = find(current)
        parent[id] = root
        return root
    }
    fun union(a: String, b: String) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) parent[rootB] = rootA
    }
    pairs.filter { it.accepted }.forEach { union(it.frameA, it.frameB) }
    return frames.map { find(it.frameId) }.toSet().size
}

private fun writeScannerFeatureDiagnostics(
    workspaceDir: File,
    result: ScannerFeatureMatchGraphResult,
    limits: ScannerFeatureExtractionLimits
) {
    workspaceDir.resolve(SCANNER_FEATURES_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerFeaturesJson(result, limits).toString(2))
    }
    workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerMatchGraphJson(result, limits).toString(2))
    }
}

internal fun scannerFeaturesJson(
    result: ScannerFeatureMatchGraphResult,
    limits: ScannerFeatureExtractionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("min_features_per_frame", limits.minFeaturesPerFrame)
        .put("border_reject_ratio", limits.borderRejectRatio.toDouble())
        .put("mask_support_threshold", limits.maskSupportThreshold)
        .put("require_mask_support_when_available", limits.requireMaskSupportWhenAvailable)
        .put(
            "frames",
            JSONArray().apply {
                result.frameReports.forEach { report ->
                    put(
                        JSONObject()
                            .put("frame_id", report.frameId)
                            .put("staged_image", report.stagedImagePath)
                            .put("staged_mask", report.stagedMaskPath ?: JSONObject.NULL)
                            .put("capture_pass", report.capturePass.manifestValue)
                            .put("width", report.imageWidth)
                            .put("height", report.imageHeight)
                            .put("feature_count", report.features.size)
                            .put("warnings", JSONArray(report.warnings))
                            .put(
                                "features",
                                JSONArray().apply {
                                    report.features.forEach { feature ->
                                        put(
                                            JSONObject()
                                                .put("x", feature.x)
                                                .put("y", feature.y)
                                                .put("score", feature.score)
                                                .put("mask_support", feature.maskSupport)
                                                .put("descriptor_hex", java.lang.Long.toUnsignedString(feature.descriptor, 16))
                                        )
                                    }
                                }
                            )
                    )
                }
            }
        )

internal fun scannerMatchGraphJson(
    result: ScannerFeatureMatchGraphResult,
    limits: ScannerFeatureExtractionLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("min_pair_matches", limits.minPairMatches)
        .put("connected_component_count", result.connectedComponentCount)
        .put(
            "pairs",
            JSONArray().apply {
                result.pairReports.forEach { pair ->
                    put(
                        JSONObject()
                            .put("frame_a", pair.frameA)
                            .put("frame_b", pair.frameB)
                            .put("match_count", pair.matchCount)
                            .put("average_descriptor_distance", pair.averageDescriptorDistance ?: JSONObject.NULL)
                            .put("spatial_cell_count", pair.spatialCellCount)
                            .put("average_mask_support", pair.averageMaskSupport ?: JSONObject.NULL)
                            .put("accepted", pair.accepted)
                            .put(
                                "matches",
                                JSONArray().apply {
                                    pair.matches.forEach { match ->
                                        put(
                                            JSONObject()
                                                .put("ax", match.ax)
                                                .put("ay", match.ay)
                                                .put("bx", match.bx)
                                                .put("by", match.by)
                                                .put("descriptor_distance", match.descriptorDistance)
                                        )
                                    }
                                }
                            )
                    )
                }
            }
        )

private fun scannerFeatureArgbToLuma(argb: Int): Int {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return ((red * 299 + green * 587 + blue * 114) / 1000).coerceIn(0, 255)
}

private fun scannerMatchSpatialCells(
    matches: List<ScannerFeatureCorrespondence>,
    width: Int,
    height: Int
): Int {
    if (matches.isEmpty() || width <= 0 || height <= 0) return 0
    return matches.map {
        val cellX = (((it.ax + it.bx) * 0.5f / width.toFloat()) * 4f).toInt().coerceIn(0, 3)
        val cellY = (((it.ay + it.by) * 0.5f / height.toFloat()) * 4f).toInt().coerceIn(0, 3)
        cellX to cellY
    }.toSet().size
}

private fun scannerAverageMatchMaskSupport(
    matches: List<ScannerFeatureCorrespondence>,
    featuresA: List<ScannerImageFeature>,
    featuresB: List<ScannerImageFeature>
): Float? {
    if (matches.isEmpty()) return null
    val supportByA = featuresA.associateBy { it.x to it.y }
    val supportByB = featuresB.associateBy { it.x to it.y }
    val supports = matches.mapNotNull { match ->
        val a = supportByA[match.ax to match.ay]?.maskSupport
        val b = supportByB[match.bx to match.by]?.maskSupport
        if (a == null || b == null) null else (a + b).toFloat() * 0.5f
    }
    return supports.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}
