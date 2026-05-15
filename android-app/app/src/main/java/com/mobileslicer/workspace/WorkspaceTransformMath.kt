package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlModelPlacement
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.forEachTriangleVertexOffsets
import com.mobileslicer.viewer.transformPoint
import com.mobileslicer.viewer.transformedBounds
import com.mobileslicer.viewer.vertexOffset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val LayFacePlanarNormalDotThreshold = 0.9994f
private const val LayFacePlanarDistanceToleranceMm = 0.35f
private const val LayFaceMinimumClusterAreaMm2 = 0.05f

internal fun layFaceOnBedTransform(
    transform: ViewerModelTransform,
    worldNormalX: Float,
    worldNormalY: Float,
    worldNormalZ: Float,
    bounds: MeshBounds? = null
): ViewerModelTransform? {
    val normal = normalize3(worldNormalX, worldNormalY, worldNormalZ) ?: return null
    val delta = rotationFromVectorToVector(
        from = normal,
        to = FloatArray(3).also {
            it[0] = 0f
            it[1] = 0f
            it[2] = -1f
        }
    ) ?: return null
    val current = orientationMatrixFromTransform(transform)
    val next = multiplyMatrix3(delta, current)
    val laid = transform.copy(
        rotationXDegrees = 0f,
        rotationYDegrees = 0f,
        rotationZDegrees = 0f,
        orientationMatrix = next.toList()
    )
    return bounds?.let { preserveVisibleFootprintCenter(transform, laid, it) } ?: laid
}

internal fun clusteredLayFaceNormal(
    mesh: StlMesh,
    triangleIndex: Int,
    transform: ViewerModelTransform,
    fallbackWorldNormal: StlModelPlacement
): StlModelPlacement {
    val fallback = normalize3(
        fallbackWorldNormal.xMm,
        fallbackWorldNormal.yMm,
        fallbackWorldNormal.zMm
    ) ?: return fallbackWorldNormal
    val vertices = mesh.vertices
    if (triangleIndex !in 0 until mesh.triangleCount) {
        return fallbackWorldNormal
    }

    val tappedA = mesh.vertexOffset(triangleIndex, 0)
    val tappedB = mesh.vertexOffset(triangleIndex, 1)
    val tappedC = mesh.vertexOffset(triangleIndex, 2)
    val tappedNormal = triangleNormalLocal(vertices, tappedA, tappedB, tappedC) ?: return fallbackWorldNormal
    val planePointX = vertices[tappedA]
    val planePointY = vertices[tappedA + 1]
    val planePointZ = vertices[tappedA + 2]
    val planeD = tappedNormal[0] * planePointX + tappedNormal[1] * planePointY + tappedNormal[2] * planePointZ
    var sumX = 0f
    var sumY = 0f
    var sumZ = 0f
    var areaSum = 0f
    mesh.forEachTriangleVertexOffsets { _, a, b, c ->
        val normal = triangleNormalLocal(vertices, a, b, c)
        if (normal != null) {
            val dot = normal[0] * tappedNormal[0] + normal[1] * tappedNormal[1] + normal[2] * tappedNormal[2]
            if (dot >= LayFacePlanarNormalDotThreshold) {
                val centroidX = (vertices[a] + vertices[b] + vertices[c]) / 3f
                val centroidY = (vertices[a + 1] + vertices[b + 1] + vertices[c + 1]) / 3f
                val centroidZ = (vertices[a + 2] + vertices[b + 2] + vertices[c + 2]) / 3f
                val distance = abs(tappedNormal[0] * centroidX + tappedNormal[1] * centroidY + tappedNormal[2] * centroidZ - planeD)
                if (distance <= LayFacePlanarDistanceToleranceMm) {
                    val area = triangleAreaLocal(vertices, a, b, c)
                    sumX += normal[0] * area
                    sumY += normal[1] * area
                    sumZ += normal[2] * area
                    areaSum += area
                }
            }
        }
    }
    if (areaSum < LayFaceMinimumClusterAreaMm2) {
        return fallbackWorldNormal
    }
    val localClusterNormal = normalize3(sumX, sumY, sumZ) ?: return fallbackWorldNormal
    val worldClusterNormal = transformNormalToWorld(localClusterNormal, transform) ?: return fallbackWorldNormal
    if (worldClusterNormal[0] * fallback[0] + worldClusterNormal[1] * fallback[1] + worldClusterNormal[2] * fallback[2] < 0f) {
        worldClusterNormal[0] = -worldClusterNormal[0]
        worldClusterNormal[1] = -worldClusterNormal[1]
        worldClusterNormal[2] = -worldClusterNormal[2]
    }
    return StlModelPlacement(
        xMm = worldClusterNormal[0],
        yMm = worldClusterNormal[1],
        zMm = worldClusterNormal[2]
    )
}

internal fun orientationMatrixFromTransform(transform: ViewerModelTransform): FloatArray =
    transform.orientationMatrix
        ?.takeIf { it.size == 9 && it.all(Float::isFinite) }
        ?.toFloatArray()
        ?: eulerOrientationMatrix(
            rotationXDegrees = transform.rotationXDegrees,
            rotationYDegrees = transform.rotationYDegrees,
            rotationZDegrees = transform.rotationZDegrees
        )

private fun eulerOrientationMatrix(
    rotationXDegrees: Float,
    rotationYDegrees: Float,
    rotationZDegrees: Float
): FloatArray {
    val rx = Math.toRadians(rotationXDegrees.toDouble())
    val ry = Math.toRadians(rotationYDegrees.toDouble())
    val rz = Math.toRadians(rotationZDegrees.toDouble())
    val cx = cos(rx).toFloat()
    val sx = sin(rx).toFloat()
    val cy = cos(ry).toFloat()
    val sy = sin(ry).toFloat()
    val cz = cos(rz).toFloat()
    val sz = sin(rz).toFloat()

    val mx = floatArrayOf(
        1f, 0f, 0f,
        0f, cx, -sx,
        0f, sx, cx
    )
    val my = floatArrayOf(
        cy, 0f, sy,
        0f, 1f, 0f,
        -sy, 0f, cy
    )
    val mz = floatArrayOf(
        cz, -sz, 0f,
        sz, cz, 0f,
        0f, 0f, 1f
    )
    return multiplyMatrix3(multiplyMatrix3(mz, my), mx)
}

private fun rotationFromVectorToVector(from: FloatArray, to: FloatArray): FloatArray? {
    val dot = (from[0] * to[0] + from[1] * to[1] + from[2] * to[2]).coerceIn(-1f, 1f)
    if (dot > 0.9999f) return identityMatrix3()
    if (dot < -0.9999f) {
        val axis = normalize3(-from[1], from[0], 0f) ?: normalize3(1f, 0f, 0f) ?: return null
        return axisAngleMatrix3(axis, Math.PI.toFloat())
    }

    val axis = normalize3(
        from[1] * to[2] - from[2] * to[1],
        from[2] * to[0] - from[0] * to[2],
        from[0] * to[1] - from[1] * to[0]
    ) ?: return null
    val angle = kotlin.math.acos(dot)
    return axisAngleMatrix3(axis, angle)
}

private fun axisAngleMatrix3(axis: FloatArray, angle: Float): FloatArray {
    val c = cos(angle)
    val s = sin(angle)
    val t = 1f - c
    val x = axis[0]
    val y = axis[1]
    val z = axis[2]
    return floatArrayOf(
        t * x * x + c, t * x * y - s * z, t * x * z + s * y,
        t * x * y + s * z, t * y * y + c, t * y * z - s * x,
        t * x * z - s * y, t * y * z + s * x, t * z * z + c
    )
}

private fun multiplyMatrix3(left: FloatArray, right: FloatArray): FloatArray {
    val out = FloatArray(9)
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

private fun identityMatrix3(): FloatArray =
    floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

private fun normalize3(x: Float, y: Float, z: Float): FloatArray? {
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
    val length = sqrt(x * x + y * y + z * z)
    if (length <= 0.0001f || abs(length).isNaN()) return null
    return floatArrayOf(x / length, y / length, z / length)
}

private fun triangleNormalLocal(vertices: FloatArray, a: Int, b: Int, c: Int): FloatArray? {
    val abX = vertices[b] - vertices[a]
    val abY = vertices[b + 1] - vertices[a + 1]
    val abZ = vertices[b + 2] - vertices[a + 2]
    val acX = vertices[c] - vertices[a]
    val acY = vertices[c + 1] - vertices[a + 1]
    val acZ = vertices[c + 2] - vertices[a + 2]
    return normalize3(
        abY * acZ - abZ * acY,
        abZ * acX - abX * acZ,
        abX * acY - abY * acX
    )
}

private fun triangleAreaLocal(vertices: FloatArray, a: Int, b: Int, c: Int): Float {
    val abX = vertices[b] - vertices[a]
    val abY = vertices[b + 1] - vertices[a + 1]
    val abZ = vertices[b + 2] - vertices[a + 2]
    val acX = vertices[c] - vertices[a]
    val acY = vertices[c + 1] - vertices[a + 1]
    val acZ = vertices[c + 2] - vertices[a + 2]
    val crossX = abY * acZ - abZ * acY
    val crossY = abZ * acX - abX * acZ
    val crossZ = abX * acY - abY * acX
    return sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * 0.5f
}

private fun transformNormalToWorld(normal: FloatArray, transform: ViewerModelTransform): FloatArray? {
    val matrix = orientationMatrixFromTransform(transform)
    return normalize3(
        matrix[0] * normal[0] + matrix[1] * normal[1] + matrix[2] * normal[2],
        matrix[3] * normal[0] + matrix[4] * normal[1] + matrix[5] * normal[2],
        matrix[6] * normal[0] + matrix[7] * normal[1] + matrix[8] * normal[2]
    )
}

private fun preserveVisibleFootprintCenter(
    before: ViewerModelTransform,
    after: ViewerModelTransform,
    bounds: MeshBounds
): ViewerModelTransform {
    val beforeOffset = visibleFootprintCenterOffset(before, bounds)
    val afterOffset = visibleFootprintCenterOffset(after, bounds)
    return after.copy(
        centerXmm = before.centerXmm + beforeOffset.first - afterOffset.first,
        centerYmm = before.centerYmm + beforeOffset.second - afterOffset.second
    )
}

private fun visibleFootprintCenterOffset(
    transform: ViewerModelTransform,
    bounds: MeshBounds
): Pair<Float, Float> {
    val scale = transform.uniformScale.coerceIn(0.05f, 20f)
    val rotatedCenter = transformPoint(
        x = bounds.centerX,
        y = bounds.centerY,
        z = bounds.centerZ,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    val rotatedBounds = transformedBounds(
        bounds = bounds,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    val visualCenterX = (rotatedBounds.minX + rotatedBounds.maxX) * 0.5f
    val visualCenterY = (rotatedBounds.minY + rotatedBounds.maxY) * 0.5f
    return Pair(visualCenterX - rotatedCenter.xMm, visualCenterY - rotatedCenter.yMm)
}
