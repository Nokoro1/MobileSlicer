package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativeThumbnailRequest
import com.mobileslicer.nativebridge.NativeThumbnailRequestSummary
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.transformPoint
import com.mobileslicer.viewer.transformedBounds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal const val MaxSoftwareThumbnailTriangles = 180_000

internal data class SliceThumbnailRgba(
    val width: Int,
    val height: Int,
    val format: String,
    val role: String,
    val rgba: ByteArray,
    val sourceTriangleCount: Int,
    val renderedTriangleCount: Int,
    val renderer: String = SoftwareSliceThumbnailRenderer.RendererName
)

internal data class SliceThumbnailVisualMetrics(
    val width: Int,
    val height: Int,
    val nontransparentPixels: Int,
    val transparentPixels: Int,
    val bboxMinX: Int,
    val bboxMinY: Int,
    val bboxMaxX: Int,
    val bboxMaxY: Int,
    val averageLuma: Int
) {
    val visible: Boolean get() = nontransparentPixels > 0
}

internal data class SliceThumbnailRenderResult(
    val requests: List<NativeThumbnailRequest>,
    val thumbnails: List<SliceThumbnailRgba>,
    val includePackageThumbnails: Boolean = false,
    val skippedReason: String? = null
) {
    val rendered: Boolean get() = thumbnails.isNotEmpty()
}

internal fun renderSliceThumbnails(
    requestSummary: NativeThumbnailRequestSummary?,
    plateObjects: List<PlateObject>,
    fallbackMesh: StlMesh?,
    fallbackTransform: ViewerModelTransform?,
    printerBed: PrinterBedSpec,
    includePackageThumbnails: Boolean = false,
    renderer: SliceThumbnailRendererBackend = SoftwareSliceThumbnailRenderer
): SliceThumbnailRenderResult {
    val requests = requestSummary?.requests.orEmpty()
    if (requests.isEmpty()) {
        return SliceThumbnailRenderResult(
            requests = emptyList(),
            thumbnails = emptyList(),
            includePackageThumbnails = includePackageThumbnails,
            skippedReason = "no thumbnail requests"
        )
    }
    if (requestSummary?.hasErrors == true) {
        return SliceThumbnailRenderResult(
            requests = requests,
            thumbnails = emptyList(),
            includePackageThumbnails = includePackageThumbnails,
            skippedReason = "thumbnail request validation failed"
        )
    }

    val sources = thumbnailSources(
        plateObjects = plateObjects,
        fallbackMesh = fallbackMesh,
        fallbackTransform = fallbackTransform ?: defaultViewerModelTransform(printerBed)
    )
    if (sources.isEmpty()) {
        return SliceThumbnailRenderResult(
            requests = requests,
            thumbnails = emptyList(),
            includePackageThumbnails = includePackageThumbnails,
            skippedReason = "no mesh available"
        )
    }

    try {
        val renderRequests = requests.distinctBy { it.width to it.height }
        val gcodeThumbnails = renderGcodeThumbnails(
            requests = renderRequests,
            renderer = renderer,
            sources = sources,
            printerBed = printerBed
        )
        val packageRequest = if (includePackageThumbnails) {
            renderRequests.maxByOrNull { it.width * it.height }
        } else {
            null
        }
        val packageThumbnails = packageRequest?.let { request ->
            listOf(
                ThumbnailRenderRole.Plate,
                ThumbnailRenderRole.NoLight,
                ThumbnailRenderRole.Top,
                ThumbnailRenderRole.Pick
            ).map { role ->
                renderer.render(
                    request = request,
                    role = role,
                    sources = sources,
                    printerBed = printerBed
                )
            }
        }.orEmpty()
        val rendered = gcodeThumbnails + packageThumbnails
        return SliceThumbnailRenderResult(
            requests = requests,
            thumbnails = rendered,
            includePackageThumbnails = includePackageThumbnails,
            skippedReason = if (rendered.isEmpty()) "all thumbnail requests were invalid" else null
        )
    } finally {
        (renderer as? AutoCloseable)?.close()
    }
}

private fun renderGcodeThumbnails(
    requests: List<NativeThumbnailRequest>,
    renderer: SliceThumbnailRendererBackend,
    sources: List<ThumbnailSource>,
    printerBed: PrinterBedSpec
): List<SliceThumbnailRgba> {
    val validRequests = requests.filter { it.width > 0 && it.height > 0 }
    if (validRequests.isEmpty()) {
        return emptyList()
    }
    val largestRequest = validRequests.maxBy { it.width * it.height }
    val canDownsampleFromLargest = validRequests.all { request ->
        request.width <= largestRequest.width &&
            request.height <= largestRequest.height &&
            request.width * largestRequest.height == largestRequest.width * request.height
    }
    if (!canDownsampleFromLargest) {
        return validRequests.map { request ->
            renderer.render(
                request = request,
                role = ThumbnailRenderRole.Gcode,
                sources = sources,
                printerBed = printerBed
            )
        }
    }

    val largestThumbnail = renderer.render(
        request = largestRequest,
        role = ThumbnailRenderRole.Gcode,
        sources = sources,
        printerBed = printerBed
    )
    return validRequests.map { request ->
        if (request.width == largestThumbnail.width && request.height == largestThumbnail.height) {
            largestThumbnail.copy(format = request.format)
        } else {
            largestThumbnail.copy(
                width = request.width,
                height = request.height,
                format = request.format,
                rgba = downsampleThumbnailRgba(
                    rgba = largestThumbnail.rgba,
                    sourceWidth = largestThumbnail.width,
                    sourceHeight = largestThumbnail.height,
                    targetWidth = request.width,
                    targetHeight = request.height
                )
            )
        }
    }
}

internal interface SliceThumbnailRendererBackend {
    val rendererName: String

    fun render(
        request: NativeThumbnailRequest,
        role: ThumbnailRenderRole,
        sources: List<ThumbnailSource>,
        printerBed: PrinterBedSpec
    ): SliceThumbnailRgba
}

internal data class ThumbnailSource(
    val mesh: StlMesh,
    val transform: ViewerModelTransform,
    val color: Int?
)

internal enum class ThumbnailRenderRole(val wireName: String) {
    Gcode("gcode"),
    Plate("plate"),
    NoLight("no_light"),
    Top("top"),
    Pick("pick")
}

internal fun thumbnailSources(
    plateObjects: List<PlateObject>,
    fallbackMesh: StlMesh?,
    fallbackTransform: ViewerModelTransform
): List<ThumbnailSource> {
    val objectSources = plateObjects.mapNotNull { plateObject ->
        val mesh = plateObject.mesh ?: return@mapNotNull null
        ThumbnailSource(
            mesh = mesh,
            transform = plateObject.transform,
            color = null
        )
    }
    if (objectSources.isNotEmpty()) {
        return objectSources
    }
    return fallbackMesh?.let {
        listOf(ThumbnailSource(mesh = it, transform = fallbackTransform, color = null))
    }.orEmpty()
}

internal object SoftwareSliceThumbnailRenderer : SliceThumbnailRendererBackend {
    const val RendererName = "software"
    override val rendererName: String = RendererName

    override fun render(
        request: NativeThumbnailRequest,
        role: ThumbnailRenderRole,
        sources: List<ThumbnailSource>,
        printerBed: PrinterBedSpec
    ): SliceThumbnailRgba {
        val width = request.width.coerceIn(1, 999)
        val height = request.height.coerceIn(1, 999)
        val rgba = ByteArray(width * height * 4)
        val zBuffer = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        val totalTriangles = sources.sumOf { it.mesh.triangleCount }
        val stride = max(1, ceil(totalTriangles.toDouble() / MaxSoftwareThumbnailTriangles.toDouble()).toInt())
        var renderedTriangles = 0

        sources.forEachIndexed { sourceIndex, source ->
            val placement = buildThumbnailPlacement(source.mesh, source.transform, printerBed)
            val color = source.color ?: defaultThumbnailObjectColor(sourceIndex)
            var triangleIndex = 0
            while (triangleIndex < source.mesh.triangleCount) {
                if (renderTriangle(
                        mesh = source.mesh,
                        triangleIndex = triangleIndex,
                        placement = placement,
                        bed = printerBed,
                        width = width,
                        height = height,
                        rgba = rgba,
                        zBuffer = zBuffer,
                        color = roleColor(color, role, sourceIndex),
                        role = role
                    )
                ) {
                    renderedTriangles++
                }
                triangleIndex += stride
            }
        }

        return SliceThumbnailRgba(
            width = width,
            height = height,
            format = request.format,
            role = role.wireName,
            rgba = rgba,
            sourceTriangleCount = totalTriangles,
            renderedTriangleCount = renderedTriangles,
            renderer = rendererName
        )
    }
}

internal fun inspectSliceThumbnailRgba(thumbnail: SliceThumbnailRgba): SliceThumbnailVisualMetrics =
    inspectThumbnailRgba(thumbnail.width, thumbnail.height, thumbnail.rgba)

internal fun inspectThumbnailRgba(width: Int, height: Int, rgba: ByteArray): SliceThumbnailVisualMetrics {
    require(width >= 0 && height >= 0) { "Thumbnail dimensions must be non-negative." }
    require(rgba.size >= width * height * 4) { "Thumbnail RGBA buffer is shorter than its dimensions." }
    var nontransparent = 0
    var lumaTotal = 0L
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            val offset = (y * width + x) * 4
            val alpha = rgba[offset + 3].toInt() and 0xff
            if (alpha == 0) {
                continue
            }
            val red = rgba[offset].toInt() and 0xff
            val green = rgba[offset + 1].toInt() and 0xff
            val blue = rgba[offset + 2].toInt() and 0xff
            nontransparent++
            lumaTotal += (red * 299 + green * 587 + blue * 114) / 1000
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
    }
    return SliceThumbnailVisualMetrics(
        width = width,
        height = height,
        nontransparentPixels = nontransparent,
        transparentPixels = width * height - nontransparent,
        bboxMinX = if (nontransparent == 0) -1 else minX,
        bboxMinY = if (nontransparent == 0) -1 else minY,
        bboxMaxX = maxX,
        bboxMaxY = maxY,
        averageLuma = if (nontransparent == 0) 0 else (lumaTotal / nontransparent).toInt()
    )
}

internal fun roleColor(baseColor: Int, role: ThumbnailRenderRole, sourceIndex: Int): Int =
    when (role) {
        ThumbnailRenderRole.Gcode,
        ThumbnailRenderRole.Top -> baseColor
        ThumbnailRenderRole.Plate -> scaleRgb(baseColor, 1.08f)
        ThumbnailRenderRole.NoLight -> scaleRgb(baseColor, 1.08f)
        ThumbnailRenderRole.Pick -> {
            val pickPalette = intArrayOf(
                thumbnailRgb(255, 32, 32),
                thumbnailRgb(32, 255, 64),
                thumbnailRgb(32, 96, 255),
                thumbnailRgb(255, 224, 32),
                thumbnailRgb(224, 32, 255)
            )
            pickPalette[sourceIndex % pickPalette.size]
        }
    }

internal fun scaleRgb(color: Int, factor: Float): Int {
    val red = (((color shr 16) and 0xff) * factor).toInt().coerceIn(0, 255)
    val green = (((color shr 8) and 0xff) * factor).toInt().coerceIn(0, 255)
    val blue = ((color and 0xff) * factor).toInt().coerceIn(0, 255)
    return thumbnailRgb(red, green, blue)
}

internal fun defaultThumbnailObjectColor(index: Int): Int {
    val palette = intArrayOf(
        thumbnailRgb(143, 193, 255),
        thumbnailRgb(255, 183, 102),
        thumbnailRgb(129, 212, 154),
        thumbnailRgb(239, 132, 132),
        thumbnailRgb(187, 155, 255)
    )
    return palette[index % palette.size]
}

internal fun thumbnailRgb(red: Int, green: Int, blue: Int): Int =
    ((red and 0xff) shl 16) or ((green and 0xff) shl 8) or (blue and 0xff)

private data class ThumbnailPlacement(
    val transform: ViewerModelTransform,
    val scale: Float,
    val xOffsetMm: Float,
    val yOffsetMm: Float,
    val zOffsetMm: Float
)

private fun buildThumbnailPlacement(
    mesh: StlMesh,
    transform: ViewerModelTransform,
    bed: PrinterBedSpec
): ThumbnailPlacement {
    val scale = transform.uniformScale.coerceIn(0.05f, 20f)
    val rotatedCenter = transformPoint(
        x = mesh.bounds.centerX,
        y = mesh.bounds.centerY,
        z = mesh.bounds.centerZ,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    val rotatedBounds = transformedBounds(
        bounds = mesh.bounds,
        scale = scale,
        rotationXDegrees = transform.rotationXDegrees,
        rotationYDegrees = transform.rotationYDegrees,
        rotationZDegrees = transform.rotationZDegrees,
        orientationMatrix = transform.orientationMatrix
    )
    return ThumbnailPlacement(
        transform = transform,
        scale = scale,
        xOffsetMm = transform.centerXmm - bed.widthMm * 0.5f - rotatedCenter.xMm,
        yOffsetMm = transform.centerYmm - bed.depthMm * 0.5f - rotatedCenter.yMm,
        zOffsetMm = transform.zOffsetMm - rotatedBounds.minZ
    )
}

private fun renderTriangle(
    mesh: StlMesh,
    triangleIndex: Int,
    placement: ThumbnailPlacement,
    bed: PrinterBedSpec,
    width: Int,
    height: Int,
    rgba: ByteArray,
    zBuffer: FloatArray,
    color: Int,
    role: ThumbnailRenderRole
): Boolean {
    val ax = FloatArray(4)
    val bx = FloatArray(4)
    val cx = FloatArray(4)
    transformTriangleVertex(mesh, triangleIndex, 0, placement, ax)
    transformTriangleVertex(mesh, triangleIndex, 1, placement, bx)
    transformTriangleVertex(mesh, triangleIndex, 2, placement, cx)

    val projectedA = projectThumbnailVertex(ax, role)
    val projectedB = projectThumbnailVertex(bx, role)
    val projectedC = projectThumbnailVertex(cx, role)
    val x0 = worldXToPixel(projectedA[0], bed, width)
    val y0 = worldYToPixel(projectedA[1], bed, height)
    val x1 = worldXToPixel(projectedB[0], bed, width)
    val y1 = worldYToPixel(projectedB[1], bed, height)
    val x2 = worldXToPixel(projectedC[0], bed, width)
    val y2 = worldYToPixel(projectedC[1], bed, height)
    val minX = max(0, floor(min(x0, min(x1, x2))).toInt())
    val maxX = min(width - 1, ceil(max(x0, max(x1, x2))).toInt())
    val minY = max(0, floor(min(y0, min(y1, y2))).toInt())
    val maxY = min(height - 1, ceil(max(y0, max(y1, y2))).toInt())
    if (minX > maxX || minY > maxY) {
        return false
    }

    val area = edge(x0, y0, x1, y1, x2, y2)
    if (area == 0f) {
        return false
    }

    val red = ((color shr 16) and 0xff).toByte()
    val green = ((color shr 8) and 0xff).toByte()
    val blue = (color and 0xff).toByte()
    var touched = false
    for (py in minY..maxY) {
        val sampleY = py + 0.5f
        for (px in minX..maxX) {
            val sampleX = px + 0.5f
            val w0 = edge(x1, y1, x2, y2, sampleX, sampleY) / area
            val w1 = edge(x2, y2, x0, y0, sampleX, sampleY) / area
            val w2 = 1f - w0 - w1
            if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                val z = projectedA[2] * w0 + projectedB[2] * w1 + projectedC[2] * w2
                val index = py * width + px
                if (z >= zBuffer[index]) {
                    zBuffer[index] = z
                    val rgbaOffset = index * 4
                    rgba[rgbaOffset] = red
                    rgba[rgbaOffset + 1] = green
                    rgba[rgbaOffset + 2] = blue
                    rgba[rgbaOffset + 3] = 0xff.toByte()
                    touched = true
                }
            }
        }
    }
    return touched
}

private fun projectThumbnailVertex(vertex: FloatArray, role: ThumbnailRenderRole): FloatArray =
    when (role) {
        ThumbnailRenderRole.Gcode,
        ThumbnailRenderRole.Top,
        ThumbnailRenderRole.Pick -> floatArrayOf(vertex[0], vertex[1], vertex[2])
        ThumbnailRenderRole.Plate,
        ThumbnailRenderRole.NoLight -> floatArrayOf(
            vertex[0] + vertex[1] * 0.32f,
            vertex[2] * 2f - vertex[1] * 0.46f,
            vertex[2] - vertex[1] * 0.18f
        )
    }

private fun transformTriangleVertex(
    mesh: StlMesh,
    triangleIndex: Int,
    corner: Int,
    placement: ThumbnailPlacement,
    out: FloatArray
) {
    val index = mesh.indices?.get(triangleIndex * 3 + corner) ?: (triangleIndex * 3 + corner)
    val offset = index * 3
    val transformed = transformPoint(
        x = mesh.vertices[offset],
        y = mesh.vertices[offset + 1],
        z = mesh.vertices[offset + 2],
        scale = placement.scale,
        rotationXDegrees = placement.transform.rotationXDegrees,
        rotationYDegrees = placement.transform.rotationYDegrees,
        rotationZDegrees = placement.transform.rotationZDegrees,
        orientationMatrix = placement.transform.orientationMatrix
    )
    out[0] = transformed.xMm + placement.xOffsetMm
    out[1] = transformed.yMm + placement.yOffsetMm
    out[2] = transformed.zMm + placement.zOffsetMm
    out[3] = 1f
}

private fun worldXToPixel(x: Float, bed: PrinterBedSpec, width: Int): Float =
    ((x + bed.widthMm * 0.5f) / bed.widthMm) * (width - 1).coerceAtLeast(1)

private fun worldYToPixel(y: Float, bed: PrinterBedSpec, height: Int): Float =
    (1f - ((y + bed.depthMm * 0.5f) / bed.depthMm)) * (height - 1).coerceAtLeast(1)

private fun edge(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float =
    (cx - ax) * (by - ay) - (cy - ay) * (bx - ax)
