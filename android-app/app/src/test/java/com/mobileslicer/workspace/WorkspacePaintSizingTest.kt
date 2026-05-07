package com.mobileslicer.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePaintSizingTest {
    @Test
    fun nativeBrushRadiusUsesUiMillimetersAfterClamp() {
        assertEquals(1.0f, nativePaintBrushRadiusMm(1.0f), 0.0001f)
        assertEquals(4.0f, nativePaintBrushRadiusMm(4.0f), 0.0001f)
    }

    @Test
    fun nativeBrushRadiusCompensatesForModelScale() {
        assertEquals(0.5f, nativePaintBrushRadiusMm(1.0f, modelScale = 2.0f), 0.0001f)
        assertEquals(2.5f, nativePaintBrushRadiusMm(1.0f, modelScale = 0.4f), 0.0001f)
    }

    @Test
    fun nativeBrushRadiusStillClampsToOrcaLikeUiRange() {
        assertEquals(PaintBrushMinRadiusMm, nativePaintBrushRadiusMm(-3.0f), 0.0001f)
        assertEquals(PaintBrushMaxRadiusMm, nativePaintBrushRadiusMm(300.0f), 0.0001f)
    }

    @Test
    fun nativeBrushHeightUsesUiMillimetersWithMinimumClamp() {
        assertEquals(1.5f, nativePaintBrushHeightMm(1.5f), 0.0001f)
        assertEquals(0.01f, nativePaintBrushHeightMm(-1.0f), 0.0001f)
    }
}
