package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlModelPlacement
import com.mobileslicer.viewer.transformedBounds
import com.mobileslicer.viewer.transformPoint
import com.mobileslicer.viewer.ViewerModelTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceTransformMathTest {
    @Test
    fun layFaceOnBedRotatesPickedNormalToNegativeZ() {
        val transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)

        val laid = layFaceOnBedTransform(transform, 1f, 0f, 0f)

        assertNotNull(laid)
        val matrix = laid!!.orientationMatrix!!
        val transformedNormalX = matrix[0]
        val transformedNormalY = matrix[3]
        val transformedNormalZ = matrix[6]
        assertEquals(0f, transformedNormalX, 0.0001f)
        assertEquals(0f, transformedNormalY, 0.0001f)
        assertEquals(-1f, transformedNormalZ, 0.0001f)
        assertEquals(100f, laid.centerXmm, 0.0001f)
        assertEquals(100f, laid.centerYmm, 0.0001f)
    }

    @Test
    fun layFaceOnBedKeepsAlreadyDownFaceStable() {
        val transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)

        val laid = layFaceOnBedTransform(transform, 0f, 0f, -1f)

        assertNotNull(laid)
        assertEquals(
            listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            laid!!.orientationMatrix
        )
    }

    @Test
    fun layFaceOnBedPreservesVisibleFootprintCenterForAsymmetricBounds() {
        val bounds = MeshBounds(
            minX = -5f,
            minY = -2f,
            minZ = -1f,
            maxX = 35f,
            maxY = 18f,
            maxZ = 12f
        )
        val transform = ViewerModelTransform(centerXmm = 140f, centerYmm = 92f)
        val beforeCenter = visibleFootprintCenter(transform, bounds)

        val laid = layFaceOnBedTransform(
            transform = transform,
            worldNormalX = 1f,
            worldNormalY = 0f,
            worldNormalZ = 0f,
            bounds = bounds
        )

        assertNotNull(laid)
        val afterCenter = visibleFootprintCenter(laid!!, bounds)
        assertEquals(beforeCenter.first, afterCenter.first, 0.0001f)
        assertEquals(beforeCenter.second, afterCenter.second, 0.0001f)
    }

    @Test
    fun clusteredLayFaceNormalUsesPlanarNeighborsInsteadOfTappedTriangleOnly() {
        val mesh = StlMesh(
            vertices = floatArrayOf(
                0f, 0f, 0f,
                10f, 0f, 0f,
                0f, 10f, 0f,
                10f, 0f, 0f,
                10f, 10f, 0f,
                0f, 10f, 0f,
                0f, 0f, 3f,
                0f, 5f, 3f,
                0f, 0f, 8f
            ),
            normals = FloatArray(27),
            triangleCount = 3,
            bounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 8f)
        )

        val clustered = clusteredLayFaceNormal(
            mesh = mesh,
            triangleIndex = 0,
            transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f),
            fallbackWorldNormal = StlModelPlacement(0f, 0f, 1f)
        )

        assertEquals(0f, clustered.xMm, 0.0001f)
        assertEquals(0f, clustered.yMm, 0.0001f)
        assertEquals(1f, clustered.zMm, 0.0001f)
    }
}

private fun visibleFootprintCenter(
    transform: ViewerModelTransform,
    bounds: MeshBounds
): Pair<Float, Float> {
    val scale = transform.uniformScale
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
    return Pair(
        transform.centerXmm + (rotatedBounds.minX + rotatedBounds.maxX) * 0.5f - rotatedCenter.xMm,
        transform.centerYmm + (rotatedBounds.minY + rotatedBounds.maxY) * 0.5f - rotatedCenter.yMm
    )
}
