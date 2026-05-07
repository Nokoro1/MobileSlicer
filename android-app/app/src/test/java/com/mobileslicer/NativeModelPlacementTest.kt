package com.mobileslicer

import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.nativeModelTransformFromArray
import com.mobileslicer.workspace.nativeModelTransformToViewerTransform
import com.mobileslicer.workspace.nativePlateTransformInputStride
import com.mobileslicer.workspace.NativeModelTransformInputStride
import com.mobileslicer.workspace.NativeModelTransformOutputStride
import com.mobileslicer.workspace.writeTo
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeModelPlacementTest {
    @Test
    fun defaultTransformCentersRawNegativeCoordinatesOnBed() {
        val transform = defaultNativeModelTransform(
            bounds = MeshBounds(
                minX = -29.176f,
                minY = -15.502f,
                minZ = 0f,
                maxX = 30.825f,
                maxY = 15.502f,
                maxZ = 48f
            ),
            printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f),
            modelTransform = null
        )

        assertEquals(134.1755, transform.xMm, 0.0001)
        assertEquals(135.0, transform.yMm, 0.0001)
        assertEquals(0.0, transform.zMm, 0.0001)
        assertEquals(1.0, transform.uniformScale, 0.0001)
    }

    @Test
    fun defaultTransformMapsUiCenterToNativeCenteredBedOrigin() {
        val transform = defaultNativeModelTransform(
            bounds = MeshBounds(
                minX = -29.176f,
                minY = -15.502f,
                minZ = 0f,
                maxX = 30.825f,
                maxY = 15.502f,
                maxZ = 48f
            ),
            printerBed = PrinterBedSpec(
                widthMm = 220f,
                depthMm = 220f,
                maxHeightMm = 220f,
                originXmm = -110f,
                originYmm = -110f
            ),
            modelTransform = null
        )

        assertEquals(-0.8245, transform.xMm, 0.0001)
        assertEquals(0.0, transform.yMm, 0.0001)
    }

    @Test
    fun nativeTransformRoundTripsViewerCenterAndRotation() {
        val bounds = MeshBounds(
            minX = -10f,
            minY = -5f,
            minZ = 0f,
            maxX = 30f,
            maxY = 25f,
            maxZ = 20f
        )
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val original = ViewerModelTransform(
            centerXmm = 42f,
            centerYmm = 118f,
            rotationXDegrees = 0f,
            rotationYDegrees = 0f,
            rotationZDegrees = 37f,
            uniformScale = 1.4f
        )

        val native = defaultNativeModelTransform(bounds, bed, original)
        val restored = nativeModelTransformToViewerTransform(bounds, bed, native)

        assertEquals(original.centerXmm, restored.centerXmm, 0.0001f)
        assertEquals(original.centerYmm, restored.centerYmm, 0.0001f)
        assertEquals(original.rotationXDegrees, restored.rotationXDegrees, 0.0001f)
        assertEquals(original.rotationYDegrees, restored.rotationYDegrees, 0.0001f)
        assertEquals(original.rotationZDegrees, restored.rotationZDegrees, 0.0001f)
        assertEquals(original.uniformScale, restored.uniformScale, 0.0001f)
    }

    @Test
    fun arbitraryViewerRotationBoundsAreGroundedOnPlate() {
        val bounds = MeshBounds(
            minX = -29.176f,
            minY = -15.502f,
            minZ = 0f,
            maxX = 30.825f,
            maxY = 15.502f,
            maxZ = 48f
        )
        val transform = ViewerModelTransform(
            centerXmm = 135f,
            centerYmm = 135f,
            rotationXDegrees = 0f,
            rotationYDegrees = -55f,
            rotationZDegrees = -73f,
            uniformScale = 1f
        )

        val plateBounds = transformedObjectBoundsOnPlate(bounds, transform)

        assertEquals(0f, plateBounds.minZ, 0.0001f)
        assertEquals(true, plateBounds.maxZ > 0f)
    }

    @Test
    fun plannedNativeOffsetMovesViewerCenterBySameAmount() {
        val bounds = MeshBounds(
            minX = 0f,
            minY = 0f,
            minZ = 0f,
            maxX = 20f,
            maxY = 20f,
            maxZ = 20f
        )
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val original = ViewerModelTransform(
            centerXmm = 135f,
            centerYmm = 135f,
            rotationXDegrees = 0f,
            rotationYDegrees = 0f,
            rotationZDegrees = 0f,
            uniformScale = 1f
        )

        val native = defaultNativeModelTransform(bounds, bed, original)
        val planned = native.copy(xMm = native.xMm + 45.0, yMm = native.yMm - 30.0)
        val moved = nativeModelTransformToViewerTransform(bounds, bed, planned)

        assertEquals(180f, moved.centerXmm, 0.0001f)
        assertEquals(105f, moved.centerYmm, 0.0001f)
    }

    @Test
    fun nativeTransformArrayHelpersUseSevenValueLayout() {
        val values = DoubleArray(14)
        val expected = defaultNativeModelTransform(
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
            printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f),
            modelTransform = null
        )

        expected.writeTo(values, offset = 7)
        val parsed = nativeModelTransformFromArray(values, offset = 7)

        assertEquals(expected.xMm, parsed.xMm, 0.0001)
        assertEquals(expected.yMm, parsed.yMm, 0.0001)
        assertEquals(expected.zMm, parsed.zMm, 0.0001)
        assertEquals(expected.rotationXRadians, parsed.rotationXRadians, 0.0001)
        assertEquals(expected.rotationYRadians, parsed.rotationYRadians, 0.0001)
        assertEquals(expected.rotationZRadians, parsed.rotationZRadians, 0.0001)
        assertEquals(expected.uniformScale, parsed.uniformScale, 0.0001)
    }

    @Test
    fun compactPlateRequestWritesSevenValuesPerObjectForThreeOrMoreObjects() {
        val values = DoubleArray(NativeModelTransformInputStride * 3) { -999.0 }
        val first = defaultNativeModelTransform(
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
            printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f),
            modelTransform = ViewerModelTransform(centerXmm = 40f, centerYmm = 50f, uniformScale = 1f)
        )
        val second = defaultNativeModelTransform(
            bounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 10f),
            printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f),
            modelTransform = ViewerModelTransform(centerXmm = 90f, centerYmm = 100f, uniformScale = 1.5f)
        )
        val third = defaultNativeModelTransform(
            bounds = MeshBounds(0f, 0f, 0f, 30f, 30f, 30f),
            printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f),
            modelTransform = ViewerModelTransform(centerXmm = 140f, centerYmm = 150f, rotationZDegrees = 45f)
        )

        first.writeTo(values, offset = 0, stride = NativeModelTransformInputStride)
        second.writeTo(values, offset = NativeModelTransformInputStride, stride = NativeModelTransformInputStride)
        third.writeTo(values, offset = NativeModelTransformInputStride * 2, stride = NativeModelTransformInputStride)

        val parsedFirst = nativeModelTransformFromArray(values, offset = 0, stride = NativeModelTransformInputStride)
        val parsedSecond = nativeModelTransformFromArray(values, offset = NativeModelTransformInputStride, stride = NativeModelTransformInputStride)
        val parsedThird = nativeModelTransformFromArray(values, offset = NativeModelTransformInputStride * 2, stride = NativeModelTransformInputStride)

        assertEquals(first.uniformScale, parsedFirst.uniformScale, 0.0001)
        assertEquals(second.uniformScale, parsedSecond.uniformScale, 0.0001)
        assertEquals(third.uniformScale, parsedThird.uniformScale, 0.0001)
        assertEquals(second.xMm, parsedSecond.xMm, 0.0001)
        assertEquals(third.rotationZRadians, parsedThird.rotationZRadians, 0.0001)
    }

    @Test
    fun nativeTransformArrayHelpersPreserveMatrixLayoutForPlateRequests() {
        val bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 40f)
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val matrix = listOf(
            0f, 0f, 1f,
            0f, 1f, 0f,
            -1f, 0f, 0f
        )
        val expected = defaultNativeModelTransform(
            bounds = bounds,
            printerBed = bed,
            modelTransform = ViewerModelTransform(
                centerXmm = 135f,
                centerYmm = 135f,
                orientationMatrix = matrix
            )
        )
        val values = DoubleArray(NativeModelTransformOutputStride)

        expected.writeTo(values)
        val parsed = nativeModelTransformFromArray(values)
        val restored = nativeModelTransformToViewerTransform(bounds, bed, parsed)

        assertEquals(matrix, restored.orientationMatrix)
        assertEquals(135f, restored.centerXmm, 0.0001f)
        assertEquals(135f, restored.centerYmm, 0.0001f)
    }

    @Test
    fun nativePlateTransformStrideUsesCompactLayoutUntilMatrixIsPresent() {
        assertEquals(
            NativeModelTransformInputStride,
            nativePlateTransformInputStride(
                listOf(
                    null,
                    ViewerModelTransform(
                        centerXmm = 135f,
                        centerYmm = 135f,
                        rotationZDegrees = 45f
                    )
                )
            )
        )
        assertEquals(
            NativeModelTransformOutputStride,
            nativePlateTransformInputStride(
                listOf(
                    ViewerModelTransform(
                        centerXmm = 135f,
                        centerYmm = 135f,
                        orientationMatrix = listOf(
                            1f, 0f, 0f,
                            0f, 1f, 0f,
                            0f, 0f, 1f
                        )
                    )
                )
            )
        )
    }
}
