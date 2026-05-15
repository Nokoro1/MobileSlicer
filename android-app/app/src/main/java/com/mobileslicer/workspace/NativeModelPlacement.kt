package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class NativeModelTransform(
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val rotationXRadians: Double,
    val rotationYRadians: Double,
    val rotationZRadians: Double,
    val uniformScale: Double,
    val orientationMatrix: List<Double>? = null
)

internal const val NativeModelTransformInputStride = 7
internal const val NativeModelTransformOutputStride = 16
internal const val NativeModelTransformPlateInputStride = NativeModelTransformOutputStride

internal fun nativePlateTransformInputStride(transforms: Iterable<ViewerModelTransform?>): Int =
    if (transforms.any { it.hasNativeOrientationMatrix() }) {
        NativeModelTransformOutputStride
    } else {
        NativeModelTransformInputStride
    }

private fun ViewerModelTransform?.hasNativeOrientationMatrix(): Boolean {
    val matrix = this?.orientationMatrix ?: return false
    return matrix.size == 9 && matrix.all { value -> !value.isNaN() && !value.isInfinite() }
}

internal fun nativeModelTransformFromArray(
    values: DoubleArray,
    offset: Int = 0,
    stride: Int = inferNativeModelTransformStride(values, offset)
): NativeModelTransform {
    require(offset >= 0 && offset + NativeModelTransformInputStride - 1 < values.size) { "Native transform offset is outside the source array." }
    if (stride >= NativeModelTransformOutputStride && offset + NativeModelTransformOutputStride - 1 < values.size) {
        val matrix = List(9) { index -> values[offset + 3 + index] }
        return NativeModelTransform(
            xMm = values[offset + 0],
            yMm = values[offset + 1],
            zMm = values[offset + 2],
            rotationXRadians = values[offset + 13],
            rotationYRadians = values[offset + 14],
            rotationZRadians = values[offset + 15],
            uniformScale = values[offset + 12],
            orientationMatrix = matrix.takeIf { it.all(Double::isFinite) }
        )
    }
    return NativeModelTransform(
        xMm = values[offset + 0],
        yMm = values[offset + 1],
        zMm = values[offset + 2],
        rotationXRadians = values[offset + 3],
        rotationYRadians = values[offset + 4],
        rotationZRadians = values[offset + 5],
        uniformScale = values[offset + 6]
    )
}

internal fun NativeModelTransform.writeTo(
    values: DoubleArray,
    offset: Int = 0,
    stride: Int = inferNativeModelTransformStride(values, offset)
) {
    require(offset >= 0 && offset + 6 < values.size) { "Native transform offset is outside the target array." }
    values[offset + 0] = xMm
    values[offset + 1] = yMm
    values[offset + 2] = zMm
    if (stride >= NativeModelTransformOutputStride) {
        require(offset + NativeModelTransformOutputStride - 1 < values.size) { "Native matrix transform offset is outside the target array." }
        val matrix = orientationMatrix
            ?.takeIf { it.size == 9 && it.all(Double::isFinite) }
            ?: eulerOrientationMatrix(
                rotationXRadians = rotationXRadians,
                rotationYRadians = rotationYRadians,
                rotationZRadians = rotationZRadians
            )
        for (index in 0 until 9) {
            values[offset + 3 + index] = matrix[index]
        }
        values[offset + 12] = uniformScale
        values[offset + 13] = rotationXRadians
        values[offset + 14] = rotationYRadians
        values[offset + 15] = rotationZRadians
        return
    }
    values[offset + 3] = rotationXRadians
    values[offset + 4] = rotationYRadians
    values[offset + 5] = rotationZRadians
    values[offset + 6] = uniformScale
}

private fun inferNativeModelTransformStride(values: DoubleArray, offset: Int): Int =
    if (values.size == NativeModelTransformOutputStride && offset == 0) {
        NativeModelTransformOutputStride
    } else {
        NativeModelTransformInputStride
    }

internal fun defaultNativeModelTransform(
    bounds: MeshBounds,
    printerBed: PrinterBedSpec,
    modelTransform: ViewerModelTransform?
): NativeModelTransform {
    val transform = modelTransform ?: ViewerModelTransform(
        centerXmm = printerBed.widthMm * 0.5f,
        centerYmm = printerBed.depthMm * 0.5f,
        rotationXDegrees = 0f,
        rotationYDegrees = 0f,
        rotationZDegrees = 0f,
        uniformScale = 1f
    )
    val scale = transform.uniformScale.coerceIn(0.05f, 20f).toDouble()
    val rotationXRadians = Math.toRadians(transform.rotationXDegrees.toDouble())
    val rotationYRadians = Math.toRadians(transform.rotationYDegrees.toDouble())
    val rotationZRadians = Math.toRadians(transform.rotationZDegrees.toDouble())
    val transformedCenter = transformPoint(
        x = bounds.centerX.toDouble(),
        y = bounds.centerY.toDouble(),
        z = bounds.centerZ.toDouble(),
        scale = scale,
        rotationXRadians = rotationXRadians,
        rotationYRadians = rotationYRadians,
        rotationZRadians = rotationZRadians,
        orientationMatrix = transform.orientationMatrix?.map { it.toDouble() }
    )
    val transformedBounds = transformedBounds(
        bounds = bounds,
        scale = scale,
        rotationXRadians = rotationXRadians,
        rotationYRadians = rotationYRadians,
        rotationZRadians = rotationZRadians,
        orientationMatrix = transform.orientationMatrix?.map { it.toDouble() }
    )
    return NativeModelTransform(
        xMm = printerBed.originXmm.toDouble() + transform.centerXmm.toDouble() - transformedCenter.xMm,
        yMm = printerBed.originYmm.toDouble() + transform.centerYmm.toDouble() - transformedCenter.yMm,
        zMm = transform.zOffsetMm.toDouble() - transformedBounds.minZ,
        rotationXRadians = rotationXRadians,
        rotationYRadians = rotationYRadians,
        rotationZRadians = rotationZRadians,
        uniformScale = scale,
        orientationMatrix = transform.orientationMatrix?.map { it.toDouble() }
    )
}

internal fun nativeModelTransformToViewerTransform(
    bounds: MeshBounds,
    printerBed: PrinterBedSpec,
    nativeTransform: NativeModelTransform
): ViewerModelTransform {
    val transformedCenter = transformPoint(
        x = bounds.centerX.toDouble(),
        y = bounds.centerY.toDouble(),
        z = bounds.centerZ.toDouble(),
        scale = nativeTransform.uniformScale,
        rotationXRadians = nativeTransform.rotationXRadians,
        rotationYRadians = nativeTransform.rotationYRadians,
        rotationZRadians = nativeTransform.rotationZRadians,
        orientationMatrix = nativeTransform.orientationMatrix
    )
    return ViewerModelTransform(
        centerXmm = (nativeTransform.xMm - printerBed.originXmm.toDouble() + transformedCenter.xMm).toFloat(),
        centerYmm = (nativeTransform.yMm - printerBed.originYmm.toDouble() + transformedCenter.yMm).toFloat(),
        zOffsetMm = (nativeTransform.zMm + transformedBounds(
            bounds = bounds,
            scale = nativeTransform.uniformScale,
            rotationXRadians = nativeTransform.rotationXRadians,
            rotationYRadians = nativeTransform.rotationYRadians,
            rotationZRadians = nativeTransform.rotationZRadians,
            orientationMatrix = nativeTransform.orientationMatrix
        ).minZ).toFloat(),
        rotationXDegrees = Math.toDegrees(nativeTransform.rotationXRadians).toFloat(),
        rotationYDegrees = Math.toDegrees(nativeTransform.rotationYRadians).toFloat(),
        rotationZDegrees = Math.toDegrees(nativeTransform.rotationZRadians).toFloat(),
        uniformScale = nativeTransform.uniformScale.toFloat(),
        orientationMatrix = nativeTransform.orientationMatrix?.map { it.toFloat() }
    )
}

private data class TransformedPointD(
    val xMm: Double,
    val yMm: Double,
    val zMm: Double
)

private data class TransformedBoundsD(
    val minZ: Double
)

private fun transformedBounds(
    bounds: MeshBounds,
    scale: Double,
    rotationXRadians: Double,
    rotationYRadians: Double,
    rotationZRadians: Double,
    orientationMatrix: List<Double>? = null
): TransformedBoundsD {
    var minZ = Double.POSITIVE_INFINITY
    val xs = doubleArrayOf(bounds.minX.toDouble(), bounds.maxX.toDouble())
    val ys = doubleArrayOf(bounds.minY.toDouble(), bounds.maxY.toDouble())
    val zs = doubleArrayOf(bounds.minZ.toDouble(), bounds.maxZ.toDouble())
    for (x in xs) {
        for (y in ys) {
            for (z in zs) {
                minZ = min(
                    minZ,
                    transformPoint(x, y, z, scale, rotationXRadians, rotationYRadians, rotationZRadians, orientationMatrix).zMm
                )
            }
        }
    }
    return TransformedBoundsD(minZ = minZ)
}

private fun transformPoint(
    x: Double,
    y: Double,
    z: Double,
    scale: Double,
    rotationXRadians: Double,
    rotationYRadians: Double,
    rotationZRadians: Double,
    orientationMatrix: List<Double>? = null
): TransformedPointD {
    var tx = x * scale
    var ty = y * scale
    var tz = z * scale
    if (orientationMatrix != null && orientationMatrix.size == 9) {
        return TransformedPointD(
            xMm = orientationMatrix[0] * tx + orientationMatrix[1] * ty + orientationMatrix[2] * tz,
            yMm = orientationMatrix[3] * tx + orientationMatrix[4] * ty + orientationMatrix[5] * tz,
            zMm = orientationMatrix[6] * tx + orientationMatrix[7] * ty + orientationMatrix[8] * tz
        )
    }

    val cosX = cos(rotationXRadians)
    val sinX = sin(rotationXRadians)
    val yAfterX = ty * cosX - tz * sinX
    val zAfterX = ty * sinX + tz * cosX
    ty = yAfterX
    tz = zAfterX

    val cosY = cos(rotationYRadians)
    val sinY = sin(rotationYRadians)
    val xAfterY = tx * cosY + tz * sinY
    val zAfterY = -tx * sinY + tz * cosY
    tx = xAfterY
    tz = zAfterY

    val cosZ = cos(rotationZRadians)
    val sinZ = sin(rotationZRadians)
    return TransformedPointD(
        xMm = tx * cosZ - ty * sinZ,
        yMm = tx * sinZ + ty * cosZ,
        zMm = tz
    )
}

private fun eulerOrientationMatrix(
    rotationXRadians: Double,
    rotationYRadians: Double,
    rotationZRadians: Double
): List<Double> {
    val cosX = cos(rotationXRadians)
    val sinX = sin(rotationXRadians)
    val cosY = cos(rotationYRadians)
    val sinY = sin(rotationYRadians)
    val cosZ = cos(rotationZRadians)
    val sinZ = sin(rotationZRadians)
    val mx = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, cosX, -sinX,
        0.0, sinX, cosX
    )
    val my = doubleArrayOf(
        cosY, 0.0, sinY,
        0.0, 1.0, 0.0,
        -sinY, 0.0, cosY
    )
    val mz = doubleArrayOf(
        cosZ, -sinZ, 0.0,
        sinZ, cosZ, 0.0,
        0.0, 0.0, 1.0
    )
    return multiplyMatrix3(multiplyMatrix3(mz, my), mx).toList()
}

private fun multiplyMatrix3(left: DoubleArray, right: DoubleArray): DoubleArray {
    val out = DoubleArray(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            out[row * 3 + col] =
                left[row * 3 + 0] * right[0 * 3 + col] +
                    left[row * 3 + 1] * right[1 * 3 + col] +
                    left[row * 3 + 2] * right[2 * 3 + col]
        }
    }
    return out
}
