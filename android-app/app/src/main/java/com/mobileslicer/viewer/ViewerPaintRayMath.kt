package com.mobileslicer.viewer

import kotlin.math.sqrt

internal fun paintRayInModelSpace(ray: PickRay, inverseModelMatrix: FloatArray): ViewerPaintRay? {
    if (inverseModelMatrix.size != 16) return null
    val localOrigin = multiplyPoint(inverseModelMatrix, ray.originX, ray.originY, ray.originZ)
        ?: return null
    val localFar = multiplyPoint(
        inverseModelMatrix,
        ray.originX + ray.directionX,
        ray.originY + ray.directionY,
        ray.originZ + ray.directionZ
    ) ?: return null
    val dx = localFar[0] - localOrigin[0]
    val dy = localFar[1] - localOrigin[1]
    val dz = localFar[2] - localOrigin[2]
    val length = sqrt(dx * dx + dy * dy + dz * dz)
    if (length <= 0.0001f) return null
    return ViewerPaintRay(
        floatArrayOf(
            localOrigin[0],
            localOrigin[1],
            localOrigin[2],
            dx / length,
            dy / length,
            dz / length
        )
    )
}

private fun multiplyPoint(matrix: FloatArray, x: Float, y: Float, z: Float): FloatArray? {
    val w = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15]
    if (w == 0f) return null
    return floatArrayOf(
        (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) / w,
        (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) / w,
        (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]) / w
    )
}
