package com.mobileslicer.workspace

import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.ProcessProfile
import com.mobileslicer.profiles.ProcessPrimeTowerDetails
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimeTowerPlacementTest {
    @Test
    fun enabledPrimeTowerPlacementWritesWipeTowerCoordinates() {
        val placement = PrimeTowerPlacement(
            xMm = 42f,
            yMm = 73f,
            widthMm = 35f,
            depthMm = 18f,
            heightMm = 20f,
            brimWidthMm = 3f
        )

        val json = JSONObject(placement.applyToNativeConfig("""{"enable_prime_tower":true}"""))

        assertEquals(42.0, json.getDouble("wipe_tower_x"), 0.0001)
        assertEquals(73.0, json.getDouble("wipe_tower_y"), 0.0001)
    }

    @Test
    fun disabledPrimeTowerPlacementDoesNotSmuggleTowerCoordinatesIntoConfig() {
        val placement = PrimeTowerPlacement(
            xMm = 42f,
            yMm = 73f,
            widthMm = 35f,
            depthMm = 18f,
            heightMm = 20f,
            brimWidthMm = 3f
        )

        val json = JSONObject(placement.applyToNativeConfig("""{"enable_prime_tower":false}"""))

        assertFalse(json.has("wipe_tower_x"))
        assertFalse(json.has("wipe_tower_y"))
    }

    @Test
    fun virtualPrimeTowerObjectIsMovableAndUsesExpectedObjectId() {
        val placement = PrimeTowerPlacement(
            xMm = 10f,
            yMm = 20f,
            widthMm = 35f,
            depthMm = 18f,
            heightMm = 20f,
            brimWidthMm = 3f
        )

        val objectForViewer = placement.toViewerPlateObject(selected = true, movable = true)

        assertEquals(PrimeTowerVirtualObjectId, objectForViewer.id)
        assertEquals("Prime tower", objectForViewer.label)
        assertTrue(objectForViewer.movable)
        assertTrue(objectForViewer.selected)
        assertEquals(12, objectForViewer.mesh.triangleCount)
        assertEquals(27.5f, objectForViewer.transform.centerXmm, 0.0001f)
        assertEquals(29f, objectForViewer.transform.centerYmm, 0.0001f)
    }

    @Test
    fun slotTwoOnlyDoesNotShowPrimeTowerPreview() {
        val placement = primeTowerPlacementForWorkspace(
            configuration = testConfiguration(enablePrimeTower = true),
            plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 2)),
            filamentSlots = testFilamentSlots(count = 2),
            printerBed = testPrinterBed(),
            override = null
        )

        assertNull(placement)
    }

    @Test
    fun multipleActuallyUsedSlotsShowPrimeTowerPreview() {
        val placement = primeTowerPlacementForWorkspace(
            configuration = testConfiguration(enablePrimeTower = true),
            plateObjects = listOf(
                testPlateObject(id = 1L, filamentSlotIndex = 1),
                testPlateObject(id = 2L, filamentSlotIndex = 2)
            ),
            filamentSlots = testFilamentSlots(count = 2),
            printerBed = testPrinterBed(),
            override = null
        )

        assertNotNull(placement)
    }

    private fun testConfiguration(enablePrimeTower: Boolean): ActiveSlicerConfiguration {
        val printer = ProfileStoreRepository.fallbackPrinterProfile()
        val filament = ProfileStoreRepository.fallbackFilamentProfile()
        val process = ProcessProfile(
            0 to "process_prime_tower_test",
            1 to "Prime Tower Test",
            188 to enablePrimeTower,
            189 to 35f,
            207 to ProcessPrimeTowerDetails(
                wipeTowerXmm = 10f,
                wipeTowerYmm = 10f,
                primeVolumeMm3 = 45f
            ),
            259 to printer.id
        )
        return ActiveSlicerConfiguration(
            printer = printer,
            filament = filament,
            process = process
        )
    }

    private fun testFilamentSlots(count: Int): List<PlateFilamentSlot> =
        (1..count).map { index ->
            PlateFilamentSlot(
                index = index,
                filamentProfileId = "filament_$index",
                label = "Filament $index",
                materialType = "PLA",
                colorHex = if (index == 1) "#8FC1FF" else "#445566"
            )
        }

    private fun testPlateObject(id: Long, filamentSlotIndex: Int): PlateObject =
        PlateObject(
            id = id,
            label = "Object $id",
            filePath = "/tmp/object_$id.stl",
            filamentSlotIndex = filamentSlotIndex,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 10f),
            transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        )

    private fun testPrinterBed(): PrinterBedSpec =
        PrinterBedSpec(
            widthMm = 220f,
            depthMm = 220f,
            maxHeightMm = 250f
        )
}
