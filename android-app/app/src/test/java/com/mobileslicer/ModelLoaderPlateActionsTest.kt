package com.mobileslicer

import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderPlateActionsTest {
    @Test
    fun removeFilamentSlotReindexesSlotsAndRemapsObjects() {
        val slots = listOf(slot(1), slot(2), slot(3))
        val objects = listOf(
            plateObject(id = 1L, filamentSlotIndex = 1),
            plateObject(id = 2L, filamentSlotIndex = 2),
            plateObject(id = 3L, filamentSlotIndex = 3)
        )

        val mutation = removePlateFilamentSlot(
            slots = slots,
            objects = objects,
            flushVolumes = null,
            slotIndex = 2
        )

        assertTrue(mutation.changed)
        assertEquals(listOf(1, 2), mutation.slots.map { it.index })
        assertEquals(listOf(1, 1, 2), mutation.objects.map { it.filamentSlotIndex })
    }

    @Test
    fun removePrimaryOrOnlyFilamentSlotIsIgnored() {
        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1)),
            objects = listOf(plateObject(id = 1L, filamentSlotIndex = 1)),
            flushVolumes = null,
            slotIndex = 1
        )

        assertFalse(mutation.changed)
        assertEquals(1, mutation.slots.single().index)
    }

    @Test
    fun updateFilamentProfileKeepsPhysicalNozzleMapping() {
        val replacement = ProfileStoreRepository.defaultFilamentProfiles().first()
        val mutation = updatePlateFilamentSlotProfile(
            slots = listOf(slot(1), slot(2, physicalNozzleIndex = 1)),
            objects = emptyList(),
            flushVolumes = null,
            slotIndex = 2,
            filament = replacement
        )

        assertEquals(replacement.id, mutation.slots[1].filamentProfileId)
        assertEquals(1, mutation.slots[1].physicalNozzleIndex)
    }

    @Test
    fun cloneSelectedObjectOffsetsAndAdvancesId() {
        val (clone, nextId) = cloneSelectedPlateObject(
            objects = listOf(plateObject(id = 5L, centerX = 190f, centerY = 190f)),
            selectedPlateObjectId = 5L,
            nextPlateObjectId = 8L,
            bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 180f)
        )

        checkNotNull(clone)
        assertEquals(8L, clone.id)
        assertEquals(200f, clone.transform.centerXmm)
        assertEquals(200f, clone.transform.centerYmm)
        assertEquals(9L, nextId)
    }

    @Test
    fun deleteSelectedObjectSelectsNextNeighbor() {
        val result = deleteSelectedPlateObject(
            objects = listOf(plateObject(id = 1L), plateObject(id = 2L), plateObject(id = 3L)),
            selectedPlateObjectId = 2L
        )

        assertTrue(result.changed)
        assertEquals(listOf(1L, 3L), result.objects.map { it.id })
        assertEquals(3L, result.nextSelection?.id)
        assertEquals("Object removed\n2 on plate", result.statusMessage)
    }

    @Test
    fun deleteOnlyObjectClearsSelection() {
        val result = deleteSelectedPlateObject(
            objects = listOf(plateObject(id = 1L)),
            selectedPlateObjectId = 1L
        )

        assertTrue(result.changed)
        assertNull(result.nextSelection)
        assertEquals("No model loaded", result.statusMessage)
    }

    @Test
    fun platePlanningStatusMessagesAreStable() {
        assertEquals(
            "Auto-orient unavailable\nNo objects on plate",
            autoOrientPlateObjectsUnavailableStatus()
        )
        assertEquals(
            "Object already oriented\nSnapped to nearest 90 degrees",
            autoOrientPlateObjectsStatus(
                PlateAutoOrientResult(objects = emptyList(), targetCount = 1, changedCount = 0, selectedOnly = true)
            )
        )
        assertEquals(
            "Objects auto-oriented\n3 snapped to nearest 90 degrees",
            autoOrientPlateObjectsStatus(
                PlateAutoOrientResult(objects = emptyList(), targetCount = 5, changedCount = 3, selectedOnly = false)
            )
        )
        assertEquals(
            "Objects do not fit\n4 on 200 x 180 mm plate",
            autoArrangePlateObjectsFailureStatus(
                objectCount = 4,
                bed = PrinterBedSpec(widthMm = 200f, depthMm = 180f, maxHeightMm = 180f)
            )
        )
        assertEquals(
            "Objects arranged\n2 on plate; prime tower space reserved",
            autoArrangePlateObjectsStatus(
                objectCount = 2,
                result = PlateAutoArrangeResult(
                    objects = emptyList(),
                    changedCount = 1,
                    reservedPrimeTowerSpace = true,
                    centersSummary = ""
                )
            )
        )
        assertEquals("Object duplicated\n3 on plate", clonedPlateObjectStatus(3))
    }

    @Test
    fun autoOrientChoosesLowHeightRightAngleOrientationFromBounds() {
        val bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 180f)
        val result = planAutoOrientPlateObjects(
            plateObjects = listOf(
                plateObject(
                    id = 1L,
                    bounds = MeshBounds(0f, 0f, 0f, 10f, 20f, 80f)
                )
            ),
            selectedPlateObjectId = null,
            bed = bed
        )

        checkNotNull(result)
        val orientedBounds = transformedObjectBoundsOnPlate(
            bounds = result.objects.single().bounds!!,
            transform = result.objects.single().transform
        )
        assertTrue(result.changedCount > 0)
        assertEquals(10f, orientedBounds.maxZ - orientedBounds.minZ, 0.001f)
    }

    @Test
    fun autoArrangeMayRotateObjectsToFitPlate() {
        val bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 180f)
        val result = planAutoArrangePlateObjects(
            plateObjects = listOf(
                plateObject(id = 1L, bounds = MeshBounds(0f, 0f, 0f, 120f, 80f, 10f)),
                plateObject(id = 2L, bounds = MeshBounds(0f, 0f, 0f, 120f, 80f, 10f))
            ),
            bed = bed,
            clearance = 0f,
            materialSlotCount = 1,
            singleExtruderMultiMaterial = false,
            primeTowerWidthMm = 35f,
            primeTowerBrimWidthMm = 0f
        )

        checkNotNull(result)
        assertEquals(2, result.objects.size)
        assertTrue(result.objects.any { kotlin.math.abs(it.transform.rotationZDegrees) == 90f })
        val rects = result.objects.map { objectOnPlate ->
            generatedFootprintRect(objectOnPlate.bounds!!, objectOnPlate.transform, clearance = 8f)
        }
        assertFalse(rects[0].minX < 0f || rects[0].maxX > bed.widthMm || rects[0].minY < 0f || rects[0].maxY > bed.depthMm)
        assertFalse(rects[1].minX < 0f || rects[1].maxX > bed.widthMm || rects[1].minY < 0f || rects[1].maxY > bed.depthMm)
        assertFalse(rects[0].minX < rects[1].maxX && rects[0].maxX > rects[1].minX && rects[0].minY < rects[1].maxY && rects[0].maxY > rects[1].minY)
    }

    private fun slot(index: Int, physicalNozzleIndex: Int? = null): PlateFilamentSlot =
        PlateFilamentSlot(
            index = index,
            filamentProfileId = "filament_$index",
            label = "Filament $index",
            materialType = "PLA",
            colorHex = "#8FC1FF",
            physicalNozzleIndex = physicalNozzleIndex
        )

    private fun plateObject(
        id: Long,
        filamentSlotIndex: Int = 1,
        centerX: Float = 100f,
        centerY: Float = 100f,
        bounds: MeshBounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 10f)
    ): PlateObject =
        PlateObject(
            id = id,
            label = "object_$id",
            filePath = "/tmp/object_$id.stl",
            filamentSlotIndex = filamentSlotIndex,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = bounds,
            transform = ViewerModelTransform(centerXmm = centerX, centerYmm = centerY)
        )
}
