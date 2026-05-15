package com.mobileslicer.viewer

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class ModelPlacement(
    val matrix: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float
)

internal data class TransformedBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float
)

internal fun buildModelPlacement(
    mesh: StlMesh,
    transform: ViewerModelTransform,
    bed: PrinterBedSpec
): ModelPlacement {
    val scale = transform.uniformScale.coerceIn(0.05f, 20f)
    val rotatedCenter = transformPoint(
        x = mesh.bounds.centerX,
        y = mesh.bounds.centerY,
        z = mesh.bounds.centerZ,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    val rotatedBounds = transformedBounds(
        bounds = mesh.bounds,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    val placementX = transform.centerXmm - bed.widthMm * 0.5f - rotatedCenter.xMm
    val placementY = transform.centerYmm - bed.depthMm * 0.5f - rotatedCenter.yMm
    val placementZ = transform.zOffsetMm - rotatedBounds.minZ
    val matrix = FloatArray(16)
    Matrix.setIdentityM(matrix, 0)
    Matrix.translateM(matrix, 0, placementX, placementY, placementZ)
    if (transform.orientationMatrix?.size == 9) {
        Matrix.multiplyMM(matrix, 0, matrix, 0, transform.orientationMatrix.toAndroidMatrix4(), 0)
    } else {
        Matrix.rotateM(matrix, 0, transform.rotationZDegrees, 0f, 0f, 1f)
        Matrix.rotateM(matrix, 0, transform.rotationYDegrees, 0f, 1f, 0f)
        Matrix.rotateM(matrix, 0, transform.rotationXDegrees, 1f, 0f, 0f)
    }
    Matrix.scaleM(matrix, 0, scale, scale, scale)
    return ModelPlacement(
        matrix = matrix,
        centerX = placementX + (rotatedBounds.minX + rotatedBounds.maxX) * 0.5f,
        centerY = placementY + (rotatedBounds.minY + rotatedBounds.maxY) * 0.5f,
        centerZ = placementZ + (rotatedBounds.minZ + rotatedBounds.maxZ) * 0.5f,
        sizeX = rotatedBounds.sizeX,
        sizeY = rotatedBounds.sizeY,
        sizeZ = rotatedBounds.sizeZ
    )
}

internal fun List<Float>.toAndroidMatrix4(): FloatArray {
    require(size == 9) { "Orientation matrix must contain 9 values." }
    val matrix = FloatArray(16)
    Matrix.setIdentityM(matrix, 0)
    matrix[0] = this[0]
    matrix[4] = this[1]
    matrix[8] = this[2]
    matrix[1] = this[3]
    matrix[5] = this[4]
    matrix[9] = this[5]
    matrix[2] = this[6]
    matrix[6] = this[7]
    matrix[10] = this[8]
    return matrix
}

internal fun defaultBedCenteredPrinterTransform(bed: PrinterBedSpec): ViewerModelTransform =
    ViewerModelTransform(
        centerXmm = bed.widthMm * 0.5f,
        centerYmm = bed.depthMm * 0.5f,
        rotationXDegrees = 0f,
        rotationYDegrees = 0f,
        rotationZDegrees = 0f,
        uniformScale = 1f
    )

internal fun transformedBounds(
    bounds: MeshBounds,
    scale: Float,
    rotationXDegrees: Float,
    rotationYDegrees: Float,
    rotationZDegrees: Float,
    orientationMatrix: List<Float>? = null
): TransformedBounds {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    val xs = floatArrayOf(bounds.minX, bounds.maxX)
    val ys = floatArrayOf(bounds.minY, bounds.maxY)
    val zs = floatArrayOf(bounds.minZ, bounds.maxZ)
    for (x in xs) {
        for (y in ys) {
            for (z in zs) {
                val point = transformPoint(x, y, z, scale, rotationXDegrees, rotationYDegrees, rotationZDegrees, orientationMatrix)
                minX = min(minX, point.xMm)
                minY = min(minY, point.yMm)
                minZ = min(minZ, point.zMm)
                maxX = max(maxX, point.xMm)
                maxY = max(maxY, point.yMm)
                maxZ = max(maxZ, point.zMm)
            }
        }
    }
    return TransformedBounds(
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        minZ = minZ,
        maxZ = maxZ,
        sizeX = maxX - minX,
        sizeY = maxY - minY,
        sizeZ = maxZ - minZ
    )
}

internal fun transformPoint(
    x: Float,
    y: Float,
    z: Float,
    scale: Float,
    rotationXDegrees: Float,
    rotationYDegrees: Float,
    rotationZDegrees: Float,
    orientationMatrix: List<Float>? = null
): StlModelPlacement {
    var tx = x * scale
    var ty = y * scale
    var tz = z * scale
    if (orientationMatrix != null && orientationMatrix.size == 9) {
        return StlModelPlacement(
            xMm = orientationMatrix[0] * tx + orientationMatrix[1] * ty + orientationMatrix[2] * tz,
            yMm = orientationMatrix[3] * tx + orientationMatrix[4] * ty + orientationMatrix[5] * tz,
            zMm = orientationMatrix[6] * tx + orientationMatrix[7] * ty + orientationMatrix[8] * tz
        )
    }

    val rx = Math.toRadians(rotationXDegrees.toDouble())
    val cosX = cos(rx).toFloat()
    val sinX = sin(rx).toFloat()
    val yAfterX = ty * cosX - tz * sinX
    val zAfterX = ty * sinX + tz * cosX
    ty = yAfterX
    tz = zAfterX

    val ry = Math.toRadians(rotationYDegrees.toDouble())
    val cosY = cos(ry).toFloat()
    val sinY = sin(ry).toFloat()
    val xAfterY = tx * cosY + tz * sinY
    val zAfterY = -tx * sinY + tz * cosY
    tx = xAfterY
    tz = zAfterY

    val rz = Math.toRadians(rotationZDegrees.toDouble())
    val cosZ = cos(rz).toFloat()
    val sinZ = sin(rz).toFloat()
    return StlModelPlacement(
        xMm = tx * cosZ - ty * sinZ,
        yMm = tx * sinZ + ty * cosZ,
        zMm = tz
    )
}

internal fun modelRadius(placement: ModelPlacement): Float =
    max(max(placement.sizeX, placement.sizeY), placement.sizeZ) * 0.5f
