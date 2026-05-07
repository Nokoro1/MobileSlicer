package com.mobileslicer.workspace

import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePaintActionReducerTest {
    @Test
    fun normalizesUnsupportedActionAndShapeForSeamMode() {
        val state = normalizedPaintToolState(
            mode = ViewerPaintMode.Seam,
            currentBrushShape = ViewerPaintBrushShape.Fill,
            currentAction = ViewerPaintAction.Paint
        )

        assertEquals(ViewerPaintBrushShape.Circle, state.brushShape)
        assertEquals(ViewerPaintAction.Enforce, state.action)
    }

    @Test
    fun preservesSupportedColorModeTrianglePaintTool() {
        val state = normalizedPaintToolState(
            mode = ViewerPaintMode.Color,
            currentBrushShape = ViewerPaintBrushShape.Triangle,
            currentAction = ViewerPaintAction.Paint
        )

        assertEquals(ViewerPaintBrushShape.Triangle, state.brushShape)
        assertEquals(ViewerPaintAction.Paint, state.action)
    }

    @Test
    fun defaultModeActionsMatchNativePaintClaim() {
        assertEquals(ViewerPaintAction.Enforce, defaultPaintToolStateForMode(ViewerPaintMode.Support).action)
        assertEquals(ViewerPaintAction.Enforce, defaultPaintToolStateForMode(ViewerPaintMode.Seam).action)
        assertEquals(ViewerPaintAction.Paint, defaultPaintToolStateForMode(ViewerPaintMode.Color).action)
        assertEquals(ViewerPaintAction.Paint, defaultPaintToolStateForMode(ViewerPaintMode.FuzzySkin).action)
    }
}

