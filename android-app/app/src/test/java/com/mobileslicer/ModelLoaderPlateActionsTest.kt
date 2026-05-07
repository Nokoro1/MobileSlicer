package com.mobileslicer

import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateObjectPaint
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SerializedPaintLayer
import com.mobileslicer.workspace.SerializedPaintTriangle
import com.mobileslicer.workspace.SerializedPaintVolumeLayer
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
    fun removeFilamentSlotMarksShiftedColorPaintReferencesStale() {
        val objects = listOf(
            plateObject(
                id = 1L,
                filamentSlotIndex = 3,
                paint = PlateObjectPaint(
                    color = SerializedPaintLayer(
                        mode = PaintMode.Color,
                        objectSourceKey = "/tmp/object_1.stl",
                        meshFingerprint = null,
                        referencedSlotIndexes = setOf(3),
                        volumeLayers = listOf(
                            SerializedPaintVolumeLayer(
                                volumeIndex = 0,
                                triangleCount = 1,
                                serializedTriangles = listOf(SerializedPaintTriangle(0, "2C0C"))
                            )
                        )
                    )
                )
            )
        )

        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1), slot(2), slot(3)),
            objects = objects,
            flushVolumes = null,
            slotIndex = 2
        )

        assertTrue(mutation.changed)
        assertEquals(2, mutation.objects.single().filamentSlotIndex)
        assertTrue(mutation.objects.single().paint.color?.isStale == true)
        assertEquals(setOf(3), mutation.objects.single().paint.color?.referencedSlotIndexes)
        assertEquals(
            "Color paint references shifted filament slots and needs native remapping.",
            mutation.objects.single().paint.color?.staleReason
        )
    }

    @Test
    fun removeFilamentSlotUsesNativeRemapperForShiftedColorPaintWhenAvailable() {
        var capturedPayload = ""
        var capturedRemap = IntArray(0)
        val objects = listOf(
            plateObject(
                id = 1L,
                filamentSlotIndex = 3,
                paint = colorPaint(hexBits = "2C0C", referencedSlots = setOf(3))
            )
        )

        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1), slot(2), slot(3)),
            objects = objects,
            flushVolumes = null,
            slotIndex = 2,
            colorSlotPayloadRemapper = { payloadJson, oldSlotToNewSlot ->
                capturedPayload = payloadJson
                capturedRemap = oldSlotToNewSlot.copyOf()
                """
                    {
                      "schemaVersion": 1,
                      "objects": [
                        {
                          "mobileObjectId": 1,
                          "layers": [
                            {
                              "mode": "Color",
                              "colorSlots": [2],
                              "volumes": [
                                {
                                  "volumeIndex": 0,
                                  "triangleCount": 1,
                                  "meshFingerprint": "fnv1a64:remapped",
                                  "triangles": [
                                    {"triangleIndex": 0, "hexBits": "AA"}
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent()
            }
        )

        val color = mutation.objects.single().paint.color
        assertTrue(mutation.changed)
        assertEquals(listOf(1, 0, 2), capturedRemap.toList())
        assertTrue(capturedPayload.contains("\"colorSlots\":[3]"))
        assertFalse(color?.isStale == true)
        assertEquals(setOf(2), color?.referencedSlotIndexes)
        assertEquals("AA", color?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
        assertEquals("fnv1a64:remapped", color?.volumeLayers?.single()?.nativeMeshFingerprint)
    }

    @Test
    fun removeFilamentSlotMarksShiftedColorPaintStaleWhenNativeRemapperFails() {
        val objects = listOf(
            plateObject(
                id = 1L,
                filamentSlotIndex = 3,
                paint = colorPaint(hexBits = "2C0C", referencedSlots = setOf(3))
            )
        )

        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1), slot(2), slot(3)),
            objects = objects,
            flushVolumes = null,
            slotIndex = 2,
            colorSlotPayloadRemapper = { _, _ -> null }
        )

        assertTrue(mutation.changed)
        assertTrue(mutation.objects.single().paint.color?.isStale == true)
        assertEquals("Color paint slot remap failed in native code.", mutation.objects.single().paint.color?.staleReason)
    }

    @Test
    fun removeFilamentSlotMarksPaintReferencingRemovedSlotStale() {
        val objects = listOf(
            plateObject(
                id = 1L,
                filamentSlotIndex = 3,
                paint = PlateObjectPaint(
                    color = SerializedPaintLayer(
                        mode = PaintMode.Color,
                        objectSourceKey = "/tmp/object_1.stl",
                        meshFingerprint = null,
                        referencedSlotIndexes = setOf(2),
                        volumeLayers = listOf(
                            SerializedPaintVolumeLayer(
                                volumeIndex = 0,
                                triangleCount = 1,
                                serializedTriangles = listOf(SerializedPaintTriangle(0, "2C0C"))
                            )
                        )
                    )
                )
            )
        )

        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1), slot(2), slot(3)),
            objects = objects,
            flushVolumes = null,
            slotIndex = 2
        )

        assertTrue(mutation.changed)
        assertEquals(2, mutation.objects.single().filamentSlotIndex)
        assertTrue(mutation.objects.single().paint.color?.isStale == true)
        assertEquals("Color paint references a removed filament slot.", mutation.objects.single().paint.color?.staleReason)
    }

    @Test
    fun removeLaterFilamentSlotLeavesEarlierColorPaintReferencesActive() {
        val objects = listOf(
            plateObject(
                id = 1L,
                filamentSlotIndex = 1,
                paint = PlateObjectPaint(
                    color = SerializedPaintLayer(
                        mode = PaintMode.Color,
                        objectSourceKey = "/tmp/object_1.stl",
                        meshFingerprint = null,
                        referencedSlotIndexes = setOf(1, 2),
                        volumeLayers = listOf(
                            SerializedPaintVolumeLayer(
                                volumeIndex = 0,
                                triangleCount = 1,
                                serializedTriangles = listOf(SerializedPaintTriangle(0, "2C0C"))
                            )
                        )
                    )
                )
            )
        )

        val mutation = removePlateFilamentSlot(
            slots = listOf(slot(1), slot(2), slot(3)),
            objects = objects,
            flushVolumes = null,
            slotIndex = 3
        )

        assertTrue(mutation.changed)
        assertFalse(mutation.objects.single().paint.color?.isStale == true)
        assertEquals(setOf(1, 2), mutation.objects.single().paint.color?.referencedSlotIndexes)
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
    fun nativePaintCommitPreservesCurrentSavedProjectIdentity() {
        val mutation = applyNativePaintPayloadCommit(
            objects = listOf(plateObject(id = 7L)),
            currentSavedProjectId = "saved_project_7",
            objectId = 7L,
            mode = PaintMode.Seam,
            payloadJson = nativePaintPayload(
                objectId = 7L,
                mode = "Seam",
                hexBits = "1C0C"
            )
        )

        assertTrue(mutation.changed)
        assertEquals("saved_project_7", mutation.currentSavedProjectId)
        assertEquals("1C0C", mutation.committedObject?.paint?.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
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
    fun cloneSelectedObjectCopiesPaintPayloadToIndependentObject() {
        val sourcePaint = seamPaint(hexBits = "1C0C")
        val (clone, nextId) = cloneSelectedPlateObject(
            objects = listOf(plateObject(id = 5L, paint = sourcePaint)),
            selectedPlateObjectId = 5L,
            nextPlateObjectId = 8L,
            bed = PrinterBedSpec(widthMm = 200f, depthMm = 200f, maxHeightMm = 180f)
        )

        checkNotNull(clone)
        assertEquals(8L, clone.id)
        assertEquals(9L, nextId)
        assertEquals("1C0C", clone.paint.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
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
    fun deleteSelectedObjectRemovesPaintWithObject() {
        val result = deleteSelectedPlateObject(
            objects = listOf(
                plateObject(id = 1L, paint = seamPaint(hexBits = "1C0C")),
                plateObject(id = 2L)
            ),
            selectedPlateObjectId = 1L
        )

        assertTrue(result.changed)
        assertEquals(listOf(2L), result.objects.map { it.id })
        assertFalse(result.objects.any { it.paint.hasAnyPaintPayload })
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
            "Object already oriented\n1 checked",
            autoOrientPlateObjectsStatus(
                PlateAutoOrientResult(objects = emptyList(), targetCount = 1, changedCount = 0, selectedOnly = true)
            )
        )
        assertEquals(
            "Objects auto-oriented\n3 changed",
            autoOrientPlateObjectsStatus(
                PlateAutoOrientResult(objects = emptyList(), targetCount = 5, changedCount = 3, selectedOnly = false)
            )
        )
        assertEquals(
            "Auto-orient failed\nMobileSlicer could not find a better orientation.",
            nativeAutoOrientPlateObjectsFailureStatus(null)
        )
        assertEquals(
            "Auto-orient failed\nNative plan failed plate validation",
            nativeAutoOrientPlateObjectsFailureStatus("Native plan failed plate validation")
        )
        assertEquals(
            "Objects don't fit on current plate\nDelete some or add objects to other plates",
            autoArrangePlateObjectsFailureStatus(
                objectCount = 4,
                bed = PrinterBedSpec(widthMm = 200f, depthMm = 180f, maxHeightMm = 180f)
            )
        )
        assertEquals(
            "Auto-arrange failed\nMobileSlicer could not find a valid plate layout.",
            nativeAutoArrangePlateObjectsFailureStatus(null)
        )
        assertEquals(
            "Auto-arrange failed\nObjects do not fit on the selected build plate.",
            nativeAutoArrangePlateObjectsFailureStatus("Objects do not fit on the selected build plate.")
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
        paint: PlateObjectPaint = PlateObjectPaint()
    ): PlateObject =
        PlateObject(
            id = id,
            label = "object_$id",
            filePath = "/tmp/object_$id.stl",
            filamentSlotIndex = filamentSlotIndex,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 10f),
            transform = ViewerModelTransform(centerXmm = centerX, centerYmm = centerY),
            paint = paint
        )

    private fun seamPaint(hexBits: String): PlateObjectPaint =
        PlateObjectPaint(
            seam = SerializedPaintLayer(
                mode = PaintMode.Seam,
                objectSourceKey = "/tmp/object.stl",
                meshFingerprint = null,
                volumeLayers = listOf(
                    SerializedPaintVolumeLayer(
                        volumeIndex = 0,
                        triangleCount = 1,
                        serializedTriangles = listOf(SerializedPaintTriangle(0, hexBits))
                    )
                )
            )
        )

    private fun colorPaint(hexBits: String, referencedSlots: Set<Int>): PlateObjectPaint =
        PlateObjectPaint(
            color = SerializedPaintLayer(
                mode = PaintMode.Color,
                objectSourceKey = "/tmp/object.stl",
                meshFingerprint = null,
                referencedSlotIndexes = referencedSlots,
                volumeLayers = listOf(
                    SerializedPaintVolumeLayer(
                        volumeIndex = 0,
                        triangleCount = 1,
                        serializedTriangles = listOf(SerializedPaintTriangle(0, hexBits)),
                        nativeMeshFingerprint = "fnv1a64:original"
                    )
                )
            )
        )

    private fun nativePaintPayload(objectId: Long, mode: String, hexBits: String): String =
        """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": $objectId,
                  "layers": [
                    {
                      "mode": "$mode",
                      "schemaVersion": 1,
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 1,
                          "meshFingerprint": "fnv1a64:test",
                          "triangles": [
                            {"triangleIndex": 0, "hexBits": "$hexBits"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
}
