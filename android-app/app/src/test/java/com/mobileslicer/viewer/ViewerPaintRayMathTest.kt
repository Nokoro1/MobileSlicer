package com.mobileslicer.viewer

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.sqrt

class ViewerPaintRayMathTest {
    @Test
    fun identityMatrixKeepsRayInModelSpace() {
        val ray = PickRay(
            originX = 1f,
            originY = 2f,
            originZ = 3f,
            directionX = 0f,
            directionY = 0f,
            directionZ = -1f
        )

        val converted: ViewerPaintRay = paintRayInModelSpace(ray, identityMatrix()) ?: error("ray conversion failed")

        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 0f, 0f, -1f), converted.values, 0.0001f)
    }

    @Test
    fun inverseTranslationMovesRayOriginIntoSourceMeshSpace() {
        val ray = PickRay(
            originX = 11f,
            originY = 22f,
            originZ = 33f,
            directionX = 0f,
            directionY = 0f,
            directionZ = -1f
        )

        val converted: ViewerPaintRay = paintRayInModelSpace(ray, translationMatrix(-10f, -20f, -30f))
            ?: error("ray conversion failed")

        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 0f, 0f, -1f), converted.values, 0.0001f)
    }

    @Test
    fun inverseScaleRenormalizesDirection() {
        val ray = PickRay(
            originX = 4f,
            originY = 6f,
            originZ = 8f,
            directionX = 0f,
            directionY = 2f / sqrt(5f),
            directionZ = -1f / sqrt(5f)
        )

        val converted: ViewerPaintRay = paintRayInModelSpace(ray, scaleMatrix(0.5f, 0.5f, 0.5f))
            ?: error("ray conversion failed")

        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 0f, 2f / sqrt(5f), -1f / sqrt(5f)), converted.values, 0.0001f)
    }

    @Test
    fun inverseUniformScaleMapsScaledWorldHitBackToUnscaledSourcePoint() {
        val ray = PickRay(
            originX = 30f,
            originY = 45f,
            originZ = 90f,
            directionX = 0f,
            directionY = 0f,
            directionZ = -1f
        )

        val converted: ViewerPaintRay = paintRayInModelSpace(ray, scaleMatrix(1f / 3f, 1f / 3f, 1f / 3f))
            ?: error("ray conversion failed")

        assertArrayEquals(floatArrayOf(10f, 15f, 30f, 0f, 0f, -1f), converted.values, 0.0001f)
    }

    @Test
    fun inverseRotationMapsDirectionIntoObjectSpace() {
        val ray = PickRay(
            originX = 0f,
            originY = 0f,
            originZ = 1f,
            directionX = 1f,
            directionY = 0f,
            directionZ = 0f
        )

        val converted: ViewerPaintRay = paintRayInModelSpace(ray, inverseRotateZ90Matrix())
            ?: error("ray conversion failed")

        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, -1f, 0f), converted.values, 0.0001f)
    }

    private fun identityMatrix(): FloatArray =
        floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

    private fun translationMatrix(x: Float, y: Float, z: Float): FloatArray =
        floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            x, y, z, 1f
        )

    private fun scaleMatrix(x: Float, y: Float, z: Float): FloatArray =
        floatArrayOf(
            x, 0f, 0f, 0f,
            0f, y, 0f, 0f,
            0f, 0f, z, 0f,
            0f, 0f, 0f, 1f
        )

    private fun inverseRotateZ90Matrix(): FloatArray =
        floatArrayOf(
            0f, -1f, 0f, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
}
