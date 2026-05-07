package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativeCutMode
import com.mobileslicer.nativebridge.NativePaintBrushShape
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintCutReleaseAuditTest {
    @Test
    fun visiblePaintSurfaceMapsToNativeSupportedIds() {
        ViewerPaintMode.values().forEach { mode ->
            assertTrue("${mode.name} exposes at least one brush", mode.availableBrushShapes().isNotEmpty())
            assertTrue("${mode.name} exposes at least one action", mode.availableActions().isNotEmpty())

            mode.availableBrushShapes().forEach { shape ->
                val nativeShape = shape.toNativePaintBrushShape(mode)
                assertTrue(
                    "${mode.name}/${shape.name} maps outside native brush range",
                    nativeShape.nativeId in NativeBrushIdRange
                )
            }
            mode.availableActions().forEach { action ->
                val nativeAction = action.toNativePaintAction()
                assertTrue(
                    "${mode.name}/${action.name} maps outside native action range",
                    nativeAction.nativeId in NativeActionIdRange
                )
            }
        }
    }

    @Test
    fun smartFillClaimMatchesVisibleFillModes() {
        assertEquals(NativePaintBrushShape.SmartFill, ViewerPaintBrushShape.Fill.toNativePaintBrushShape(ViewerPaintMode.Support))
        assertEquals(NativePaintBrushShape.SmartFill, ViewerPaintBrushShape.Fill.toNativePaintBrushShape(ViewerPaintMode.FuzzySkin))
        assertEquals(NativePaintBrushShape.Fill, ViewerPaintBrushShape.Fill.toNativePaintBrushShape(ViewerPaintMode.Color))

        assertTrue(ViewerPaintMode.Seam.availableBrushShapes().none { it == ViewerPaintBrushShape.Fill })
    }

    @Test
    fun visibleCutModesMapToNativeCutModes() {
        assertEquals(NativeCutMode.Plane, WorkspaceCutMode.Plane.toNativeCutMode())
        assertEquals(NativeCutMode.Plane, WorkspaceCutMode.Line.toNativeCutMode())
        assertEquals(NativeCutMode.Contour, WorkspaceCutMode.Contour.toNativeCutMode())
        assertEquals(NativeCutMode.Groove, WorkspaceCutMode.Groove.toNativeCutMode())
    }

    @Test
    fun paintCutNativeUnsupportedGuardsStayPresent() {
        val native = repoFile("engine-wrapper/orca_wrapper_paint_cut_api.cpp").readText()
        assertTrue(native.contains("shape < 0 || shape > 6"))
        assertTrue(native.contains("action < 0 || action > 3"))
        assertTrue(native.contains("mode == \"contour\""))
        assertTrue(native.contains("mode == \"groove\""))
        assertTrue(native.contains("Connector-\" + std::to_string(++connector_id)"))
        assertTrue(native.contains("increase_connectors_cnt(connector_id - connector_id_start)"))
    }

    private companion object {
        val NativeBrushIdRange = 0..6
        val NativeActionIdRange = 0..3

        fun repoFile(relativePath: String): File =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, relativePath) }
                .firstOrNull { it.exists() }
                ?: File(relativePath)
    }
}
