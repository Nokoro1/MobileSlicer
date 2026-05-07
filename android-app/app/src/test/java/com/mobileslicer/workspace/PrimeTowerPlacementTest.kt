package com.mobileslicer.workspace

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
