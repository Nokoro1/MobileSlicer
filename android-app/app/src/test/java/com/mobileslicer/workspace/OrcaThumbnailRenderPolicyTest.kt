package com.mobileslicer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaThumbnailRenderPolicyTest {
    @Test
    fun cameraContractsMatchOrcaThumbnailRoles() {
        val gcode = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Gcode)
        val plate = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Plate)
        val noLight = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.NoLight)
        val top = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Top)
        val pick = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Pick)

        assertEquals(OrcaThumbnailCameraMode.AngledIso, gcode.mode)
        assertEquals(OrcaThumbnailCameraMode.AngledIso, plate.mode)
        assertEquals(OrcaThumbnailCameraMode.AngledIso, noLight.mode)
        assertEquals(OrcaThumbnailCameraMode.TopPlate, top.mode)
        assertEquals(OrcaThumbnailCameraMode.TopPlate, pick.mode)
        assertFalse(plate.banLight)
        assertTrue(noLight.banLight)
        assertFalse(top.picking)
        assertTrue(pick.picking)
        assertTrue(pick.banLight)
        assertTrue(top.source.contains("Top_Plate"))
        assertTrue(plate.source.contains("render_thumbnail_internal"))
    }

    @Test
    fun angledContractCarriesOrcaIsoFramingConstants() {
        val plate = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Plate)

        assertEquals(45f, plate.pitchDegrees!!, 0.0001f)
        assertEquals(-45f, plate.yawDegrees!!, 0.0001f)
        assertEquals(4f, plate.cameraDistanceFactor!!, 0.0001f)
        assertEquals(1.025f, plate.zoomToBoxMarginFactor!!, 0.0001f)
        assertEquals(1.38f, OrcaThumbnailRenderPolicy.AngledBroadFootprintZoomToBoxMarginFactor, 0.0001f)
        assertEquals(0.01f, plate.boxHorizontalMarginFactor!!, 0.0001f)
        assertEquals(0.02f, plate.boxVerticalMarginFactor!!, 0.0001f)
        assertTrue(plate.source.contains("zoom_to_box"))
    }

    @Test
    fun topPlateContractCarriesWholePlateMargin() {
        val top = OrcaThumbnailRenderPolicy.cameraContract(ThumbnailRenderRole.Top)

        assertEquals(OrcaThumbnailRenderPolicy.TopPlateMargin, top.topPlateMargin!!, 0.0001f)
        assertTrue(top.source.contains("Top_Plate"))
    }

    @Test
    fun renderSizeSupersamplesOnlySmallThumbnails() {
        val gcode128 = OrcaThumbnailRenderPolicy.renderSize(128, 128, ThumbnailRenderRole.Gcode)
        assertTrue(gcode128.supersampled)
        assertEquals(256, gcode128.renderWidth)
        assertEquals(256, gcode128.renderHeight)

        val package128 = OrcaThumbnailRenderPolicy.renderSize(128, 128, ThumbnailRenderRole.Plate)
        assertTrue(package128.supersampled)
        assertEquals(256, package128.renderWidth)
        assertEquals(256, package128.renderHeight)

        val gcode150 = OrcaThumbnailRenderPolicy.renderSize(150, 150, ThumbnailRenderRole.Gcode)
        assertTrue(gcode150.supersampled)
        assertEquals(300, gcode150.renderWidth)
        assertEquals(300, gcode150.renderHeight)

        val package192 = OrcaThumbnailRenderPolicy.renderSize(192, 192, ThumbnailRenderRole.Top)
        assertFalse(package192.supersampled)
        assertEquals(192, package192.renderWidth)
        assertEquals(192, package192.renderHeight)

        val large = OrcaThumbnailRenderPolicy.renderSize(300, 300, ThumbnailRenderRole.Gcode)
        assertTrue(large.supersampled)
        assertEquals(600, large.renderWidth)
        assertEquals(600, large.renderHeight)
    }

    @Test
    fun downsampleThumbnailRgbaAveragesSmallSupersampledInputs() {
        val source = byteArrayOf(
            0, 0, 0, 0,
            100, 0, 0, 100,
            0, 100, 0, 100,
            100, 100, 100, 100
        )
        val output = downsampleThumbnailRgba(
            rgba = source,
            sourceWidth = 2,
            sourceHeight = 2,
            targetWidth = 1,
            targetHeight = 1
        )

        assertEquals(4, output.size)
        assertEquals(50, output[0].toInt() and 0xff)
        assertEquals(50, output[1].toInt() and 0xff)
        assertEquals(25, output[2].toInt() and 0xff)
        assertEquals(75, output[3].toInt() and 0xff)
    }
}
