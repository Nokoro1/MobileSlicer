package com.mobileslicer.workspace

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativePaintOverlayParserTest {
    @Test
    fun parsesSeparateVerticesAndNormals() {
        val overlay = parseNativePaintOverlay(
            """
            {
              "layers": [
                {
                  "id": "support",
                  "colorInt": 1711276032,
                  "vertices": [1, 2, 3, 4, 5, 6, 7, 8, 9],
                  "normals": [0, 0, 1, 0, 1, 0, 1, 0, 0]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, overlay.layers.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), overlay.layers.single().vertices, 0.0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f), overlay.layers.single().normals, 0.0f)
    }

    @Test
    fun splitsNativeInterleavedVerticesAndNormals() {
        val overlay = parseNativePaintOverlay(
            """
            {
              "layers": [
                {
                  "id": "v0:2",
                  "state": 2,
                  "vertices": [
                    1, 2, 3, 0, 0, 1,
                    4, 5, 6, 0, 1, 0,
                    7, 8, 9, 1, 0, 0
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, overlay.layers.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), overlay.layers.single().vertices, 0.0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f), overlay.layers.single().normals, 0.0f)
    }

    @Test
    fun mapsNonColorOverlayPaletteByPaintMode() {
        val seam = parseNativePaintOverlayDeltaInterleaved(
            values = overlayDeltaForState(1),
            mode = com.mobileslicer.viewer.ViewerPaintMode.Seam,
            slotColors = emptyMap()
        )
        val support = parseNativePaintOverlayDeltaInterleaved(
            values = overlayDeltaForState(1),
            mode = com.mobileslicer.viewer.ViewerPaintMode.Support,
            slotColors = emptyMap()
        )
        val fuzzy = parseNativePaintOverlayDeltaInterleaved(
            values = overlayDeltaForState(5),
            mode = com.mobileslicer.viewer.ViewerPaintMode.FuzzySkin,
            slotColors = emptyMap()
        )

        assertEquals(0xFF2F80ED.toInt(), seam.layers.single().colorInt)
        assertEquals(0xFF20B455.toInt(), support.layers.single().colorInt)
        assertEquals(0xFF9C4DCC.toInt(), fuzzy.layers.single().colorInt)
    }

    @Test
    fun mapsColorOverlayToConfiguredFilamentSlotColors() {
        val slotFourPurple = 0xFFAB34D6.toInt()
        val overlay = parseNativePaintOverlayDeltaInterleaved(
            values = overlayDeltaForState(4),
            mode = com.mobileslicer.viewer.ViewerPaintMode.Color,
            slotColors = mapOf(4 to slotFourPurple),
            baseColorSlotIndex = 1
        )

        assertEquals(1, overlay.layers.size)
        assertEquals(4, overlay.layers.single().state)
        assertEquals(slotFourPurple, overlay.layers.single().colorInt)
    }

    @Test
    fun parsesDirectByteBufferOverlayDelta() {
        val overlay = parseNativePaintOverlayDeltaInterleaved(
            values = directBufferOf(overlayDeltaForState(2)),
            mode = com.mobileslicer.viewer.ViewerPaintMode.Support,
            slotColors = emptyMap()
        )

        assertEquals(1, overlay.layers.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), overlay.layers.single().vertices, 0.0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), overlay.layers.single().normals, 0.0f)
    }

    @Test
    fun hidesColorOverlayForObjectBaseFilamentSlot() {
        val overlay = parseNativePaintOverlayDeltaInterleaved(
            values = overlayDeltaForState(1),
            mode = com.mobileslicer.viewer.ViewerPaintMode.Color,
            slotColors = mapOf(1 to 0xFF8FC1FF.toInt()),
            baseColorSlotIndex = 1
        )

        assertEquals(0, overlay.layers.size)
    }

    private fun overlayDeltaForState(state: Int): FloatArray =
        floatArrayOf(
            1f,
            state.toFloat(), 0f,
            0f, 0f, 0f, 10f, 10f, 10f,
            3f,
            1f, 2f, 3f, 0f, 0f, 1f,
            4f, 5f, 6f, 0f, 0f, 1f,
            7f, 8f, 9f, 0f, 0f, 1f
        )

    private fun directBufferOf(values: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                values.forEach(::putFloat)
                position(0)
            }
}
