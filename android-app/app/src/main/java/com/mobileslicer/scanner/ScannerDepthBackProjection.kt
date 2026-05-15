package com.mobileslicer.scanner

import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.roundToInt

internal data class ScannerDepthBackProjectionLimits(
    val minDepthMm: Int = 80,
    val maxDepthMm: Int = 65_535,
    val minConfidence: Int = 64,
    val maxSamples: Int = 2_000
)

internal data class ScannerDepthPixelSample(
    val xPx: Int,
    val yPx: Int,
    val depthMm: Int,
    val confidence: Int
)

internal data class ScannerBackProjectedDepthPoint(
    val pointId: String,
    val frameId: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val sourceXPx: Int,
    val sourceYPx: Int,
    val depthMm: Int,
    val confidence: Int,
    val provenanceClass: String
)

internal data class ScannerEncodedDepthSamples(
    val width: Int,
    val height: Int,
    val samples: List<ScannerDepthPixelSample>
)

internal fun backProjectScannerDepthSamples(
    frameId: String,
    samples: List<ScannerDepthPixelSample>,
    intrinsics: CameraIntrinsicsData,
    poseWorldFromCamera: FloatArray?,
    limits: ScannerDepthBackProjectionLimits = ScannerDepthBackProjectionLimits()
): List<ScannerBackProjectedDepthPoint> {
    val pose = poseWorldFromCamera?.takeIf { it.size == 16 }
    return samples
        .asSequence()
        .filter { it.depthMm in limits.minDepthMm..limits.maxDepthMm }
        .filter { it.confidence >= limits.minConfidence }
        .take(limits.maxSamples)
        .mapIndexed { index, sample ->
            val cameraX = ((sample.xPx.toDouble() - intrinsics.cx.toDouble()) / intrinsics.fx.toDouble()) *
                sample.depthMm.toDouble()
            val cameraY = ((sample.yPx.toDouble() - intrinsics.cy.toDouble()) / intrinsics.fy.toDouble()) *
                sample.depthMm.toDouble()
            val cameraZ = sample.depthMm.toDouble()
            val world = if (pose == null) {
                doubleArrayOf(cameraX, cameraY, cameraZ)
            } else {
                transformScannerCameraPointToWorld(cameraX, cameraY, cameraZ, pose)
            }
            ScannerBackProjectedDepthPoint(
                pointId = "depth_${frameId}_${index.toString().padStart(6, '0')}",
                frameId = frameId,
                xMm = world[0],
                yMm = world[1],
                zMm = world[2],
                sourceXPx = sample.xPx,
                sourceYPx = sample.yPx,
                depthMm = sample.depthMm,
                confidence = sample.confidence,
                provenanceClass = if (sample.confidence >= 192) "measured_high" else "measured_low"
            )
        }
        .toList()
}

internal fun scannerDepthCoverage(samples: List<ScannerDepthPixelSample>, maskPixelCount: Int): Float {
    if (maskPixelCount <= 0) return 0f
    val valid = samples.count { it.depthMm > 0 && it.confidence > 0 }
    return (valid.toFloat() / maskPixelCount.toFloat()).coerceIn(0f, 1f)
}

internal fun scannerUniformDepthSamples(
    depthMm: IntArray,
    confidence: ByteArray,
    width: Int,
    height: Int,
    stride: Int = maxOf(1, width / 80)
): List<ScannerDepthPixelSample> {
    if (width <= 0 || height <= 0 || depthMm.size < width * height || confidence.size < width * height) {
        return emptyList()
    }
    val step = stride.coerceAtLeast(1)
    val samples = ArrayList<ScannerDepthPixelSample>()
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val index = y * width + x
            samples += ScannerDepthPixelSample(
                xPx = x,
                yPx = y,
                depthMm = depthMm[index],
                confidence = confidence[index].toInt() and 0xFF
            )
            x += step
        }
        y += step
    }
    return samples
}

internal fun scannerEncodedDepthPngSamples(
    depthPng: File,
    confidencePng: File?,
    stride: Int = 4
): ScannerEncodedDepthSamples {
    val depthBitmap = BitmapFactory.decodeFile(depthPng.absolutePath)
        ?: return ScannerEncodedDepthSamples(width = 0, height = 0, samples = emptyList())
    val confidenceBitmap = confidencePng?.takeIf { it.isFile }?.let {
        BitmapFactory.decodeFile(it.absolutePath)
    }
    val width = depthBitmap.width
    val height = depthBitmap.height
    val step = stride.coerceAtLeast(1)
    val samples = ArrayList<ScannerDepthPixelSample>()
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val depthPixel = depthBitmap.getPixel(x, y)
            val low = Color.red(depthPixel)
            val high = Color.green(depthPixel)
            val depth = low or (high shl 8)
            val confidence = confidenceBitmap?.let { bitmap ->
                if (x < bitmap.width && y < bitmap.height) {
                    Color.red(bitmap.getPixel(x, y))
                } else {
                    0
                }
            } ?: 255
            samples += ScannerDepthPixelSample(
                xPx = x,
                yPx = y,
                depthMm = depth,
                confidence = confidence
            )
            x += step
        }
        y += step
    }
    return ScannerEncodedDepthSamples(width = width, height = height, samples = samples)
}

internal fun scaledScannerIntrinsicsForDepth(
    intrinsics: CameraIntrinsicsData,
    depthWidth: Int,
    depthHeight: Int
): CameraIntrinsicsData {
    if (intrinsics.imageWidth <= 0 || intrinsics.imageHeight <= 0 || depthWidth <= 0 || depthHeight <= 0) {
        return intrinsics
    }
    val scaleX = depthWidth.toFloat() / intrinsics.imageWidth.toFloat()
    val scaleY = depthHeight.toFloat() / intrinsics.imageHeight.toFloat()
    return CameraIntrinsicsData(
        fx = intrinsics.fx * scaleX,
        fy = intrinsics.fy * scaleY,
        cx = intrinsics.cx * scaleX,
        cy = intrinsics.cy * scaleY,
        imageWidth = depthWidth,
        imageHeight = depthHeight
    )
}

private fun transformScannerCameraPointToWorld(
    xMm: Double,
    yMm: Double,
    zMm: Double,
    pose: FloatArray
): DoubleArray =
    doubleArrayOf(
        pose[0].toDouble() * xMm + pose[1].toDouble() * yMm + pose[2].toDouble() * zMm + pose[3].toDouble(),
        pose[4].toDouble() * xMm + pose[5].toDouble() * yMm + pose[6].toDouble() * zMm + pose[7].toDouble(),
        pose[8].toDouble() * xMm + pose[9].toDouble() * yMm + pose[10].toDouble() * zMm + pose[11].toDouble()
    )

internal fun ScannerBackProjectedDepthPoint.quantizedKey(voxelSizeMm: Float): String {
    val voxel = voxelSizeMm.coerceAtLeast(0.01f).toDouble()
    return listOf(xMm, yMm, zMm)
        .joinToString(":") { (it / voxel).roundToInt().toString() }
}
