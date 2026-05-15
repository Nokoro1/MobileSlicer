package com.mobileslicer.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs

internal data class ScannerImageQualityAnalysis(
    val width: Int,
    val height: Int,
    val blurScore: Float,
    val exposureScore: Float,
    val clippedHighlightRatio: Float,
    val brightnessMean: Float
)

internal fun analyzeScannerJpegQuality(file: File): ScannerImageQualityAnalysis? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = scannerImageSampleSize(bounds.outWidth, bounds.outHeight)
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = IntArray(pixels.size) { index -> argbToLuma(pixels[index]) }
        val analysis = analyzeScannerLumaQuality(width, height, luma)
        analysis.copy(width = bounds.outWidth, height = bounds.outHeight)
    } finally {
        bitmap.recycle()
    }
}

internal fun analyzeScannerLumaQuality(
    width: Int,
    height: Int,
    luma: IntArray
): ScannerImageQualityAnalysis {
    require(width > 0 && height > 0) { "Image dimensions must be positive" }
    require(luma.size == width * height) { "Luma buffer size does not match dimensions" }

    var sum = 0L
    var clipped = 0
    luma.forEach { value ->
        val clamped = value.coerceIn(0, 255)
        sum += clamped
        if (clamped <= 5 || clamped >= 250) {
            clipped += 1
        }
    }
    val mean = sum.toFloat() / luma.size.toFloat()
    val exposureScore = (1f - (abs(mean - 127.5f) / 127.5f)).coerceIn(0f, 1f)
    val clippedRatio = clipped.toFloat() / luma.size.toFloat()
    val blurScore = laplacianVariance(width, height, luma)
    return ScannerImageQualityAnalysis(
        width = width,
        height = height,
        blurScore = blurScore,
        exposureScore = exposureScore,
        clippedHighlightRatio = clippedRatio,
        brightnessMean = mean
    )
}

internal fun scannerImageSharpnessReadiness(blurScore: Float): Float =
    (blurScore / 180f).coerceIn(0f, 1f)

internal fun scannerLightingReadiness(exposureScore: Float, clippedHighlightRatio: Float): Float =
    (exposureScore * (1f - clippedHighlightRatio.coerceIn(0f, 1f))).coerceIn(0f, 1f)

private fun scannerImageSampleSize(width: Int, height: Int): Int {
    var sample = 1
    var largest = maxOf(width, height)
    while (largest / sample > 1024) {
        sample *= 2
    }
    return sample
}

private fun argbToLuma(argb: Int): Int {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return ((red * 299 + green * 587 + blue * 114) / 1000).coerceIn(0, 255)
}

private fun laplacianVariance(width: Int, height: Int, luma: IntArray): Float {
    if (width < 3 || height < 3) return 0f
    var count = 0
    var sum = 0.0
    var sumSquares = 0.0
    for (y in 1 until height - 1) {
        val row = y * width
        for (x in 1 until width - 1) {
            val center = luma[row + x]
            val laplacian =
                -4 * center +
                    luma[row + x - 1] +
                    luma[row + x + 1] +
                    luma[row - width + x] +
                    luma[row + width + x]
            sum += laplacian.toDouble()
            sumSquares += laplacian.toDouble() * laplacian.toDouble()
            count += 1
        }
    }
    if (count == 0) return 0f
    val mean = sum / count.toDouble()
    return (sumSquares / count.toDouble() - mean * mean).toFloat().coerceAtLeast(0f)
}
