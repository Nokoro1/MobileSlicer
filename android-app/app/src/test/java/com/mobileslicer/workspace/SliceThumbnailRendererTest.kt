package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativeThumbnailRequest
import com.mobileslicer.nativebridge.NativeThumbnailRequestErrors
import com.mobileslicer.nativebridge.NativeThumbnailRequestSummary
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliceThumbnailRendererTest {
    @Test
    fun renderSliceThumbnailsSkipsWhenNoRequestsExist() {
        val result = renderSliceThumbnails(
            requestSummary = null,
            plateObjects = emptyList(),
            fallbackMesh = null,
            fallbackTransform = null,
            printerBed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
        )

        assertFalse(result.rendered)
        assertEquals("no thumbnail requests", result.skippedReason)
        assertTrue(result.requests.isEmpty())
    }

    @Test
    fun renderSliceThumbnailsPreservesRequestsButSkipsValidationErrors() {
        val request = NativeThumbnailRequest(width = 96, height = 96, format = "PNG")
        val result = renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "96x96/PNG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(invalidExtension = true),
                errorText = "Invalid thumbnails value",
                requests = listOf(request)
            ),
            plateObjects = emptyList(),
            fallbackMesh = null,
            fallbackTransform = null,
            printerBed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
        )

        assertFalse(result.rendered)
        assertEquals("thumbnail request validation failed", result.skippedReason)
        assertEquals(listOf(request), result.requests)
    }

    @Test
    fun renderSliceThumbnailsSkipsWhenMeshIsUnavailable() {
        val request = NativeThumbnailRequest(width = 96, height = 96, format = "PNG")
        val result = renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "96x96/PNG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(),
                errorText = "",
                requests = listOf(request)
            ),
            plateObjects = emptyList(),
            fallbackMesh = null,
            fallbackTransform = null,
            printerBed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
        )

        assertFalse(result.rendered)
        assertEquals("no mesh available", result.skippedReason)
        assertEquals(listOf(request), result.requests)
    }

    @Test
    fun renderSliceThumbnailsRendersOnlyGcodeBuffersByDefault() {
        val requests = listOf(
            NativeThumbnailRequest(width = 32, height = 32, format = "PNG"),
            NativeThumbnailRequest(width = 32, height = 32, format = "QOI"),
            NativeThumbnailRequest(width = 16, height = 16, format = "JPG")
        )
        val result = renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "32x32/PNG,32x32/QOI,16x16/JPG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(),
                errorText = "",
                requests = requests
            ),
            plateObjects = emptyList(),
            fallbackMesh = singleTriangleMesh(),
            fallbackTransform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            printerBed = PrinterBedSpec(widthMm = 100f, depthMm = 100f, maxHeightMm = 100f)
        )

        assertTrue(result.rendered)
        assertEquals(requests, result.requests)
        assertEquals(
            listOf("gcode", "gcode"),
            result.thumbnails.map { it.role }
        )
        assertEquals(listOf(32 to 32, 16 to 16), result.thumbnails.map { it.width to it.height })
        assertFalse(result.includePackageThumbnails)
        assertEquals(listOf("software", "software"), result.thumbnails.map { it.renderer })
        assertTrue(result.thumbnails.first { it.role == "gcode" }.rgba.any { it != 0.toByte() })
    }

    @Test
    fun renderSliceThumbnailsDownsamplesSameAspectGcodeRequestsFromLargestRender() {
        val backend = CountingThumbnailBackend()
        val result = renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "48x48/PNG,150x150/PNG,300x300/PNG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(),
                errorText = "",
                requests = listOf(
                    NativeThumbnailRequest(width = 48, height = 48, format = "PNG"),
                    NativeThumbnailRequest(width = 150, height = 150, format = "PNG"),
                    NativeThumbnailRequest(width = 300, height = 300, format = "PNG")
                )
            ),
            plateObjects = emptyList(),
            fallbackMesh = singleTriangleMesh(),
            fallbackTransform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            printerBed = PrinterBedSpec(widthMm = 100f, depthMm = 100f, maxHeightMm = 100f),
            renderer = backend
        )

        assertTrue(result.rendered)
        assertEquals(1, backend.calls)
        assertEquals(listOf(48 to 48, 150 to 150, 300 to 300), result.thumbnails.map { it.width to it.height })
        assertEquals(listOf(300 to 300), backend.renderedSizes)
    }

    @Test
    fun renderSliceThumbnailsDoesNotDownsampleDifferentAspectGcodeRequests() {
        val backend = CountingThumbnailBackend()
        renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "96x48/PNG,300x300/PNG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(),
                errorText = "",
                requests = listOf(
                    NativeThumbnailRequest(width = 96, height = 48, format = "PNG"),
                    NativeThumbnailRequest(width = 300, height = 300, format = "PNG")
                )
            ),
            plateObjects = emptyList(),
            fallbackMesh = singleTriangleMesh(),
            fallbackTransform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            printerBed = PrinterBedSpec(widthMm = 100f, depthMm = 100f, maxHeightMm = 100f),
            renderer = backend
        )

        assertEquals(2, backend.calls)
        assertEquals(listOf(96 to 48, 300 to 300), backend.renderedSizes)
    }

    @Test
    fun renderSliceThumbnailsProducesDistinctPackageRoles() {
        val result = renderSliceThumbnails(
            requestSummary = NativeThumbnailRequestSummary(
                source = "orca",
                thumbnails = "48x48/PNG",
                thumbnailsFormat = "PNG",
                errors = NativeThumbnailRequestErrors(),
                errorText = "",
                requests = listOf(NativeThumbnailRequest(width = 48, height = 48, format = "PNG"))
            ),
            plateObjects = emptyList(),
            fallbackMesh = singleTriangleMesh(),
            fallbackTransform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            printerBed = PrinterBedSpec(widthMm = 100f, depthMm = 100f, maxHeightMm = 100f),
            includePackageThumbnails = true
        )

        assertTrue(result.includePackageThumbnails)
        assertEquals(
            listOf("gcode", "plate", "no_light", "top", "pick"),
            result.thumbnails.map { it.role }
        )
        val packageHashes = result.thumbnails
            .filter { it.role != "gcode" }
            .associate { it.role to it.rgba.contentHashCode() }
        assertEquals(setOf("plate", "no_light", "top", "pick"), packageHashes.keys)
        assertTrue(packageHashes.values.toSet().size > 1)
    }

    @Test
    fun inspectSliceThumbnailRgbaReportsVisibleBoundsAndLuma() {
        val rgba = ByteArray(4 * 4 * 4)
        val pixelOffset = (1 * 4 + 2) * 4
        rgba[pixelOffset] = 100
        rgba[pixelOffset + 1] = 120
        rgba[pixelOffset + 2] = 140.toByte()
        rgba[pixelOffset + 3] = 0xff.toByte()
        val metrics = inspectThumbnailRgba(width = 4, height = 4, rgba = rgba)

        assertTrue(metrics.visible)
        assertEquals(1, metrics.nontransparentPixels)
        assertEquals(15, metrics.transparentPixels)
        assertEquals(2, metrics.bboxMinX)
        assertEquals(1, metrics.bboxMinY)
        assertEquals(2, metrics.bboxMaxX)
        assertEquals(1, metrics.bboxMaxY)
        assertTrue(metrics.averageLuma > 0)
    }

    private fun singleTriangleMesh(): StlMesh =
        StlMesh(
            vertices = floatArrayOf(
                -10f, -10f, 0f,
                10f, -10f, 0f,
                0f, 10f, 10f
            ),
            normals = floatArrayOf(
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f
            ),
            triangleCount = 1,
            bounds = MeshBounds(
                minX = -10f,
                minY = -10f,
                minZ = 0f,
                maxX = 10f,
                maxY = 10f,
                maxZ = 10f
            ),
            indices = null
        )

    private class CountingThumbnailBackend : SliceThumbnailRendererBackend {
        override val rendererName: String = "counting"
        var calls: Int = 0
            private set
        val renderedSizes = mutableListOf<Pair<Int, Int>>()

        override fun render(
            request: NativeThumbnailRequest,
            role: ThumbnailRenderRole,
            sources: List<ThumbnailSource>,
            printerBed: PrinterBedSpec
        ): SliceThumbnailRgba {
            calls++
            renderedSizes += request.width to request.height
            return SliceThumbnailRgba(
                width = request.width,
                height = request.height,
                format = request.format,
                role = role.wireName,
                rgba = ByteArray(request.width * request.height * 4) { index ->
                    if (index % 4 == 3) 0xff.toByte() else (index % 251).toByte()
                },
                sourceTriangleCount = sources.sumOf { it.mesh.triangleCount },
                renderedTriangleCount = sources.sumOf { it.mesh.triangleCount },
                renderer = rendererName
            )
        }
    }
}
