package com.mobileslicer.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

internal data class ScannerSoftMaskResult(
    val maskPath: String,
    val maskSha256: String,
    val generatorName: String,
    val generatorStatus: String,
    val objectRoi: ScannerObjectRoi,
    val coverageRatio: Float,
    val edgeUncertainty: Float,
    val centerSupport: Float,
    val warnings: List<String>
)

internal enum class ScannerObjectRoiSource(val manifestValue: String) {
    UserTap("user_tap"),
    DefaultCenter("default_center")
}

internal data class ScannerObjectRoi(
    val xNormalized: Float,
    val yNormalized: Float,
    val source: ScannerObjectRoiSource
) {
    fun clamped(): ScannerObjectRoi =
        copy(
            xNormalized = xNormalized.coerceIn(0f, 1f),
            yNormalized = yNormalized.coerceIn(0f, 1f)
        )
}

internal fun defaultScannerObjectRoi(): ScannerObjectRoi =
    ScannerObjectRoi(
        xNormalized = 0.5f,
        yNormalized = 0.5f,
        source = ScannerObjectRoiSource.DefaultCenter
    )

internal interface ScannerSoftMaskGenerator {
    val generatorName: String
    val generatorStatus: String
    fun generateMask(
        sessionDir: File,
        frame: ScanFrame,
        absoluteFramePath: String,
        objectRoi: ScannerObjectRoi
    ): ScannerSoftMaskResult?
}

internal object HeuristicScannerSoftMaskGenerator : ScannerSoftMaskGenerator {
    override val generatorName: String = "heuristic_center_background_soft_mask_v1"
    override val generatorStatus: String = "ready_non_ai_fallback"

    override fun generateMask(
        sessionDir: File,
        frame: ScanFrame,
        absoluteFramePath: String,
        objectRoi: ScannerObjectRoi
    ): ScannerSoftMaskResult? {
        val bitmap = BitmapFactory.decodeFile(absoluteFramePath) ?: return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val luma = IntArray(pixels.size) { index -> scannerArgbToLuma(pixels[index]) }
            val mask = heuristicSoftMaskAlpha(width, height, luma)
            writeScannerSoftMaskPng(
                sessionDir = sessionDir,
                frame = frame,
                width = width,
                height = height,
                alpha = mask.alpha,
                result = mask,
                generatorName = generatorName,
                generatorStatus = generatorStatus,
                objectRoi = objectRoi,
                warnings = mask.warnings + scannerObjectRoiWarnings(objectRoi)
            )
        } finally {
            bitmap.recycle()
        }
    }
}

internal data class HeuristicSoftMaskAnalysis(
    val alpha: ByteArray,
    val coverageRatio: Float,
    val edgeUncertainty: Float,
    val centerSupport: Float,
    val warnings: List<String>
)

internal fun heuristicSoftMaskAlpha(
    width: Int,
    height: Int,
    luma: IntArray
): HeuristicSoftMaskAnalysis {
    require(width > 0 && height > 0) { "Image dimensions must be positive" }
    require(luma.size == width * height) { "Luma buffer size does not match dimensions" }

    val borderMean = scannerBorderMean(width, height, luma)
    val centerX = (width - 1) * 0.5f
    val centerY = (height - 1) * 0.5f
    val maxDistance = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
    val alpha = ByteArray(luma.size)
    var foregroundCount = 0
    var uncertainCount = 0
    var centerSum = 0f
    var centerSamples = 0

    for (y in 0 until height) {
        val dy = y - centerY
        for (x in 0 until width) {
            val index = y * width + x
            val dx = x - centerX
            val centerPrior = (1f - sqrt(dx * dx + dy * dy) / maxDistance).coerceIn(0f, 1f)
            val contrast = (abs(luma[index] - borderMean) / 80f).coerceIn(0f, 1f)
            val foreground = (contrast * 0.72f + centerPrior * 0.28f).coerceIn(0f, 1f)
            val value = (foreground * 255f).toInt().coerceIn(0, 255)
            alpha[index] = value.toByte()
            if (value >= 128) foregroundCount += 1
            if (value in 80..175) uncertainCount += 1
            if (centerPrior > 0.65f) {
                centerSum += foreground
                centerSamples += 1
            }
        }
    }

    val coverage = foregroundCount.toFloat() / luma.size.toFloat()
    val edgeUncertainty = uncertainCount.toFloat() / luma.size.toFloat()
    val centerSupport = if (centerSamples == 0) 0f else centerSum / centerSamples.toFloat()
    val warnings = buildList {
        add("heuristic_soft_mask_not_ai")
        add("mask_is_soft_evidence_only")
        if (coverage < 0.08f) add("mask_coverage_low")
        if (coverage > 0.85f) add("mask_coverage_high")
        if (edgeUncertainty > 0.55f) add("mask_boundary_uncertain")
        if (centerSupport < 0.35f) add("object_center_support_low")
    }
    return HeuristicSoftMaskAnalysis(
        alpha = alpha,
        coverageRatio = coverage,
        edgeUncertainty = edgeUncertainty,
        centerSupport = centerSupport,
        warnings = warnings
    )
}

internal fun scannerAnalyzeSoftMaskAlpha(
    width: Int,
    height: Int,
    alpha: ByteArray
): HeuristicSoftMaskAnalysis {
    require(width > 0 && height > 0) { "Image dimensions must be positive" }
    require(alpha.size == width * height) { "Alpha buffer size does not match dimensions" }
    var foregroundCount = 0
    var uncertainCount = 0
    var centerSum = 0f
    var centerSamples = 0
    val centerX = (width - 1) * 0.5f
    val centerY = (height - 1) * 0.5f
    val maxDistance = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
    alpha.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        if (value >= 128) foregroundCount += 1
        if (value in 80..175) uncertainCount += 1
        val x = index % width
        val y = index / width
        val centerPrior = (1f - sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)) / maxDistance)
            .coerceIn(0f, 1f)
        if (centerPrior > 0.65f) {
            centerSum += value.toFloat() / 255f
            centerSamples += 1
        }
    }
    val coverage = foregroundCount.toFloat() / alpha.size.toFloat()
    val edgeUncertainty = uncertainCount.toFloat() / alpha.size.toFloat()
    val centerSupport = if (centerSamples == 0) 0f else centerSum / centerSamples.toFloat()
    val warnings = buildList {
        if (coverage < 0.08f) add("mask_coverage_low")
        if (coverage > 0.85f) add("mask_coverage_high")
        if (edgeUncertainty > 0.55f) add("mask_boundary_uncertain")
        if (centerSupport < 0.35f) add("object_center_support_low")
    }
    return HeuristicSoftMaskAnalysis(
        alpha = alpha,
        coverageRatio = coverage,
        edgeUncertainty = edgeUncertainty,
        centerSupport = centerSupport,
        warnings = warnings
    )
}

internal fun writeScannerSoftMaskPng(
    sessionDir: File,
    frame: ScanFrame,
    width: Int,
    height: Int,
    alpha: ByteArray,
    result: HeuristicSoftMaskAnalysis,
    generatorName: String,
    generatorStatus: String,
    objectRoi: ScannerObjectRoi,
    warnings: List<String>
): ScannerSoftMaskResult {
    require(alpha.size == width * height) { "Alpha buffer size does not match dimensions" }
    val maskPixels = IntArray(alpha.size) { index ->
        val value = alpha[index].toInt() and 0xFF
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        maskBitmap.setPixels(maskPixels, 0, width, 0, 0, width, height)
        val frameName = frame.id.ifBlank { File(frame.rgbPath).nameWithoutExtension }
        val relativePath = "frames/${frameName}_mask.png"
        val output = sessionDir.resolve(relativePath)
        output.parentFile?.mkdirs()
        output.outputStream().use { stream ->
            check(maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to write soft mask PNG"
            }
        }
        return ScannerSoftMaskResult(
            maskPath = relativePath,
            maskSha256 = sha256Hex(output),
            generatorName = generatorName,
            generatorStatus = generatorStatus,
            objectRoi = objectRoi.clamped(),
            coverageRatio = result.coverageRatio,
            edgeUncertainty = result.edgeUncertainty,
            centerSupport = result.centerSupport,
            warnings = warnings.distinct()
        )
    } finally {
        maskBitmap.recycle()
    }
}

internal fun scannerObjectRoiWarnings(objectRoi: ScannerObjectRoi): List<String> =
    when (objectRoi.source) {
        ScannerObjectRoiSource.UserTap -> listOf("user_object_tap_recorded")
        ScannerObjectRoiSource.DefaultCenter -> listOf("default_center_roi_weak_evidence")
    }

private fun scannerBorderMean(width: Int, height: Int, luma: IntArray): Int {
    var sum = 0L
    var count = 0
    val border = maxOf(1, minOf(width, height) / 12)
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (x < border || y < border || x >= width - border || y >= height - border) {
                sum += luma[y * width + x].coerceIn(0, 255)
                count += 1
            }
        }
    }
    return if (count == 0) 127 else (sum / count).toInt().coerceIn(0, 255)
}

private fun scannerArgbToLuma(argb: Int): Int {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return ((red * 299 + green * 587 + blue * 114) / 1000).coerceIn(0, 255)
}
