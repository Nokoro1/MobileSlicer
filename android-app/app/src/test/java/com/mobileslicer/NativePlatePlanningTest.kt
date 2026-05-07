package com.mobileslicer

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.NativeModelTransformOutputStride
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.writeTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlatePlanningTest {
    @Test
    fun nativeArrangeOutputIsConvertedFromNativeOffsetToViewerCenter() {
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val objectOnPlate = plateObject(
            id = 1L,
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
            transform = ViewerModelTransform(centerXmm = 135f, centerYmm = 135f)
        )
        val plannedNative = defaultNativeModelTransform(
            bounds = objectOnPlate.bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 180f, centerYmm = 105f)
        )
        val values = DoubleArray(7).also { plannedNative.writeTo(it) }

        val plannedObjects = applyNativePlatePlan(listOf(objectOnPlate), bed, values)

        checkNotNull(plannedObjects)
        assertEquals(180f, plannedObjects.single().transform.centerXmm, 0.0001f)
        assertEquals(105f, plannedObjects.single().transform.centerYmm, 0.0001f)
    }

    @Test
    fun nativeOrientOutputPreservesRadiansAndScaleAfterConversion() {
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val objectOnPlate = plateObject(
            id = 1L,
            bounds = MeshBounds(-10f, -5f, 0f, 30f, 25f, 20f),
            transform = ViewerModelTransform(centerXmm = 135f, centerYmm = 135f)
        )
        val plannedViewer = ViewerModelTransform(
            centerXmm = 135f,
            centerYmm = 135f,
            rotationXDegrees = 30f,
            rotationYDegrees = -20f,
            rotationZDegrees = 45f,
            uniformScale = 1.2f
        )
        val values = DoubleArray(7).also {
            defaultNativeModelTransform(objectOnPlate.bounds!!, bed, plannedViewer).writeTo(it)
        }

        val plannedObjects = applyNativePlatePlan(listOf(objectOnPlate), bed, values)

        checkNotNull(plannedObjects)
        val plannedTransform = plannedObjects.single().transform
        assertEquals(30f, plannedTransform.rotationXDegrees, 0.0001f)
        assertEquals(-20f, plannedTransform.rotationYDegrees, 0.0001f)
        assertEquals(45f, plannedTransform.rotationZDegrees, 0.0001f)
        assertEquals(1.2f, plannedTransform.uniformScale, 0.0001f)
    }

    @Test
    fun nativeMatrixOutputPreservesExactOrientationMatrix() {
        val bed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
        val objectOnPlate = plateObject(
            id = 1L,
            bounds = MeshBounds(-10f, -10f, 0f, 10f, 10f, 20f),
            transform = ViewerModelTransform(centerXmm = 135f, centerYmm = 135f)
        )
        val values = DoubleArray(NativeModelTransformOutputStride)
        val legacy = defaultNativeModelTransform(
            bounds = objectOnPlate.bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 135f, centerYmm = 135f)
        )
        values[0] = legacy.xMm
        values[1] = legacy.yMm
        values[2] = legacy.zMm
        values[3] = 0.0
        values[4] = -1.0
        values[5] = 0.0
        values[6] = 1.0
        values[7] = 0.0
        values[8] = 0.0
        values[9] = 0.0
        values[10] = 0.0
        values[11] = 1.0
        values[12] = 1.0
        values[13] = 0.0
        values[14] = 0.0
        values[15] = Math.toRadians(90.0)

        val plannedObjects = applyNativePlatePlan(listOf(objectOnPlate), bed, values)

        checkNotNull(plannedObjects)
        assertEquals(
            listOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            plannedObjects.single().transform.orientationMatrix
        )
    }

    @Test
    fun invalidNativePlanIsRejectedBeforeMutatingPlateState() {
        val bed = PrinterBedSpec(widthMm = 100f, depthMm = 100f, maxHeightMm = 100f)
        val objectOnPlate = plateObject(
            id = 1L,
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
            transform = ViewerModelTransform(centerXmm = 50f, centerYmm = 50f)
        )
        val outsideBed = defaultNativeModelTransform(
            bounds = objectOnPlate.bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 200f, centerYmm = 50f)
        )
        val values = DoubleArray(7).also { outsideBed.writeTo(it) }

        assertNull(applyNativePlatePlan(listOf(objectOnPlate), bed, values))
    }

    @Test
    fun overlappingNativeArrangePlanIsRejected() {
        val bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 200f)
        val objects = listOf(
            plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 50f, centerYmm = 50f)),
            plateObject(id = 2L, transform = ViewerModelTransform(centerXmm = 150f, centerYmm = 150f))
        )
        val values = DoubleArray(14)
        defaultNativeModelTransform(
            bounds = objects[0].bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        ).writeTo(values, offset = 0)
        defaultNativeModelTransform(
            bounds = objects[1].bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        ).writeTo(values, offset = 7)

        assertNull(
            applyNativePlatePlan(
                plateObjects = objects,
                bed = bed,
                nativeTransforms = values,
                requireNoOverlap = true
            )
        )
    }

    @Test
    fun nativeArrangeCallerCanTrustNativePolygonOverlapValidation() {
        val bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 200f)
        val objects = listOf(
            plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 50f, centerYmm = 50f)),
            plateObject(id = 2L, transform = ViewerModelTransform(centerXmm = 150f, centerYmm = 150f))
        )
        val values = DoubleArray(14)
        defaultNativeModelTransform(
            bounds = objects[0].bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        ).writeTo(values, offset = 0)
        defaultNativeModelTransform(
            bounds = objects[1].bounds!!,
            printerBed = bed,
            modelTransform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        ).writeTo(values, offset = 7)

        val plannedObjects = applyNativePlatePlan(
            plateObjects = objects,
            bed = bed,
            nativeTransforms = values,
            requireNoOverlap = false
        )

        checkNotNull(plannedObjects)
        assertEquals(2, plannedObjects.size)
    }

    @Test
    fun nativeChangedCountUsesPlacementAndRotationTolerance() {
        val before = listOf(plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 50f, centerYmm = 50f)))
        val unchanged = listOf(plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 50.01f, centerYmm = 50f)))
        val changed = listOf(plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 51f, centerYmm = 50f)))

        assertEquals(0, nativePlanChangedCount(before, unchanged))
        assertEquals(1, nativePlanChangedCount(before, changed))
        assertTrue(nativePlanChangedCount(before, changed) > nativePlanChangedCount(before, unchanged))
    }

    @Test
    fun nativeChangedCountIncludesMatrixOnlyOrientationChanges() {
        val before = listOf(plateObject(id = 1L, transform = ViewerModelTransform(centerXmm = 50f, centerYmm = 50f)))
        val changed = listOf(
            plateObject(
                id = 1L,
                transform = ViewerModelTransform(
                    centerXmm = 50f,
                    centerYmm = 50f,
                    orientationMatrix = listOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
                )
            )
        )

        assertEquals(1, nativePlanChangedCount(before, changed))
    }

    private fun plateObject(
        id: Long,
        bounds: MeshBounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
        transform: ViewerModelTransform
    ): PlateObject =
        PlateObject(
            id = id,
            label = "object_$id",
            filePath = "/tmp/object_$id.stl",
            filamentSlotIndex = 1,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = bounds,
            transform = transform
        )
}
