package com.mobileslicer.workspace

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import com.mobileslicer.nativebridge.NativeThumbnailRequest
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.TriangleProgram
import com.mobileslicer.viewer.TriangleUpload
import com.mobileslicer.viewer.ViewerTriangleProgram
import com.mobileslicer.viewer.buildModelPlacement
import com.mobileslicer.viewer.deleteTriangleUpload
import com.mobileslicer.viewer.drawTriangleUpload
import com.mobileslicer.viewer.uploadTriangleData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

internal data class OffscreenEglThumbnailTiming(
    val eglCreateMs: Long,
    val uploadMs: Long,
    val drawMs: Long,
    val readPixelsMs: Long,
    val cleanupMs: Long,
    val totalMs: Long,
    val glError: Int
)

internal class OffscreenEglSliceThumbnailRenderer : SliceThumbnailRendererBackend, AutoCloseable {
    override val rendererName: String = OrcaThumbnailRenderPolicy.RendererName

    var lastTiming: OffscreenEglThumbnailTiming? = null
        private set

    private var session: RenderSession? = null

    override fun render(
        request: NativeThumbnailRequest,
        role: ThumbnailRenderRole,
        sources: List<ThumbnailSource>,
        printerBed: PrinterBedSpec
    ): SliceThumbnailRgba {
        val renderSize = OrcaThumbnailRenderPolicy.renderSize(request.width, request.height, role)
        val width = renderSize.outputWidth
        val height = renderSize.outputHeight
        val renderWidth = renderSize.renderWidth
        val renderHeight = renderSize.renderHeight
        val totalStartedAt = SystemClock.elapsedRealtime()
        val (activeSession, eglCreateMs) = ensureSession(renderWidth, renderHeight)

        var uploads = emptyList<Pair<ThumbnailSource, TriangleUpload>>()
        var uploadMs = 0L
        var drawMs = 0L
        var readPixelsMs = 0L
        var cleanupMs = 0L
        var glError = GLES20.GL_NO_ERROR
        var cleanedUp = false
        val renderRgba = ByteArray(renderWidth * renderHeight * 4)
        try {
            GLES20.glViewport(0, 0, renderWidth, renderHeight)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val program = activeSession.program ?: ViewerTriangleProgram.create().also { activeSession.program = it }
            val uploadStartedAt = SystemClock.elapsedRealtime()
            uploads = sources.map { source ->
                val upload = uploadTriangleData(
                    vertices = source.mesh.expandedVertices(),
                    normals = source.mesh.expandedNormals()
                )
                source to upload
            }
            uploadMs = SystemClock.elapsedRealtime() - uploadStartedAt

            val viewProjectionMatrix = buildThumbnailViewProjection(renderWidth, renderHeight, printerBed, sources, role)
            val identityModelMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            val drawStartedAt = SystemClock.elapsedRealtime()
            uploads.forEachIndexed { index, (source, upload) ->
                drawTriangleUpload(
                    programId = program.programId,
                    handles = program.handles,
                    upload = upload,
                    color = thumbnailColorFloatArray(
                        roleColor(
                            baseColor = source.color ?: defaultThumbnailObjectColor(index),
                            role = role,
                            sourceIndex = index
                        )
                    ),
                    viewProjectionMatrix = viewProjectionMatrix,
                    modelMatrix = identityModelMatrix,
                    identityModelMatrix = identityModelMatrix,
                    modelMatrixOverride = buildModelPlacement(source.mesh, source.transform, printerBed).matrix,
                    fullBright = role == ThumbnailRenderRole.NoLight || role == ThumbnailRenderRole.Top
                )
            }
            GLES20.glFinish()
            drawMs = SystemClock.elapsedRealtime() - drawStartedAt

            val readStartedAt = SystemClock.elapsedRealtime()
            val pixelBuffer = ByteBuffer.allocateDirect(renderRgba.size).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, renderWidth, renderHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
            pixelBuffer.position(0)
            pixelBuffer.get(renderRgba)
            readPixelsMs = SystemClock.elapsedRealtime() - readStartedAt
            glError = GLES20.glGetError()
            val outputRgba = if (renderSize.supersampled) {
                downsampleThumbnailRgba(
                    rgba = renderRgba,
                    sourceWidth = renderWidth,
                    sourceHeight = renderHeight,
                    targetWidth = width,
                    targetHeight = height
                )
            } else {
                renderRgba
            }

            val cleanupStartedAt = SystemClock.elapsedRealtime()
            uploads.forEach { (_, upload) -> deleteTriangleUpload(upload) }
            uploads = emptyList()
            cleanupMs = SystemClock.elapsedRealtime() - cleanupStartedAt
            cleanedUp = true

            val totalTriangles = sources.sumOf { it.mesh.triangleCount }
            lastTiming = OffscreenEglThumbnailTiming(
                eglCreateMs = eglCreateMs,
                uploadMs = uploadMs,
                drawMs = drawMs,
                readPixelsMs = readPixelsMs,
                cleanupMs = cleanupMs,
                totalMs = SystemClock.elapsedRealtime() - totalStartedAt,
                glError = glError
            )
            return SliceThumbnailRgba(
                width = width,
                height = height,
                format = request.format,
                role = role.wireName,
                rgba = outputRgba,
                sourceTriangleCount = totalTriangles,
                renderedTriangleCount = totalTriangles,
                renderer = rendererName
            )
        } finally {
            uploads.forEach { (_, upload) -> deleteTriangleUpload(upload) }
            if (!cleanedUp) {
                val cleanupStartedAt = SystemClock.elapsedRealtime()
                close()
                cleanupMs = SystemClock.elapsedRealtime() - cleanupStartedAt
                lastTiming = OffscreenEglThumbnailTiming(
                    eglCreateMs = eglCreateMs,
                    uploadMs = uploadMs,
                    drawMs = drawMs,
                    readPixelsMs = readPixelsMs,
                    cleanupMs = cleanupMs,
                    totalMs = SystemClock.elapsedRealtime() - totalStartedAt,
                    glError = glError
                )
            }
        }
    }

    override fun close() {
        val activeSession = session ?: return
        session = null
        EGL14.eglMakeCurrent(
            activeSession.display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        activeSession.program?.let { program ->
            GLES20.glDeleteProgram(program.programId)
            activeSession.program = null
        }
        if (activeSession.surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(activeSession.display, activeSession.surface)
        }
        if (activeSession.context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(activeSession.display, activeSession.context)
        }
        if (activeSession.display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(activeSession.display)
        }
    }

    private fun buildThumbnailViewProjection(
        width: Int,
        height: Int,
        printerBed: PrinterBedSpec,
        sources: List<ThumbnailSource>,
        role: ThumbnailRenderRole
    ): FloatArray {
        if (OrcaThumbnailRenderPolicy.cameraContract(role).mode == OrcaThumbnailCameraMode.TopPlate) {
            return buildOrcaPlateTopViewProjection(width, height, printerBed)
        }
        if (role == ThumbnailRenderRole.Gcode ||
            role == ThumbnailRenderRole.Plate ||
            role == ThumbnailRenderRole.NoLight
        ) {
            return buildOrcaAngledPackageViewProjection(width, height, printerBed, sources)
        }
        return buildOrcaAngledPackageViewProjection(width, height, printerBed, sources)
    }

    private fun buildOrcaAngledPackageViewProjection(
        width: Int,
        height: Int,
        printerBed: PrinterBedSpec,
        sources: List<ThumbnailSource>
    ): FloatArray {
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewProjectionMatrix = FloatArray(16)
        val span = max(max(printerBed.widthMm, printerBed.depthMm), printerBed.maxHeightMm)
            .coerceAtLeast(100f)
        val distance = span * OrcaThumbnailRenderPolicy.AngledCameraDistanceFactor
        val pitchRadians = Math.toRadians(OrcaThumbnailRenderPolicy.AngledPitchDegrees.toDouble())
        val yawRadians = Math.toRadians(OrcaThumbnailRenderPolicy.AngledYawDegrees.toDouble())
        val volumesBox = orcaThumbnailVolumesBox(printerBed, sources)
        val targetX = (volumesBox.minX + volumesBox.maxX) * 0.5f
        val targetY = (volumesBox.minY + volumesBox.maxY) * 0.5f
        val targetZ = (volumesBox.minZ + volumesBox.maxZ) * 0.5f
        val eyeX = targetX + (kotlin.math.sin(yawRadians) * kotlin.math.cos(pitchRadians) * distance).toFloat()
        val eyeY = targetY - (kotlin.math.cos(yawRadians) * kotlin.math.cos(pitchRadians) * distance).toFloat()
        val eyeZ = targetZ + (kotlin.math.sin(pitchRadians) * distance).toFloat()
        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            0f,
            1f
        )

        val bounds = projectedBoxViewBounds(viewMatrix, volumesBox)
        val objectWidth = (bounds.maxX - bounds.minX).coerceAtLeast(1f)
        val objectHeight = (bounds.maxY - bounds.minY).coerceAtLeast(1f)
        val zoomToBoxMarginFactor = angledZoomToBoxMarginFactor(volumesBox)
        val aspectIndependentZoom = min(
            width.toFloat() / (objectWidth * zoomToBoxMarginFactor),
            height.toFloat() / (objectHeight * zoomToBoxMarginFactor)
        ).coerceAtLeast(0.0001f)
        val halfWidth = width * 0.5f / aspectIndependentZoom
        val halfHeight = height * 0.5f / aspectIndependentZoom
        Matrix.orthoM(
            projectionMatrix,
            0,
            -halfWidth,
            halfWidth,
            -halfHeight,
            halfHeight,
            1f,
            distance + span * 4f
        )
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        return viewProjectionMatrix
    }

    private fun angledZoomToBoxMarginFactor(bounds: WorldBounds): Float {
        val footprint = max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY)
        val height = (bounds.maxZ - bounds.minZ).coerceAtLeast(0f)
        val isBroadLowOrMidHeightFootprint =
            footprint >= OrcaThumbnailRenderPolicy.AngledBroadFootprintMinSizeMm &&
                height >= footprint * OrcaThumbnailRenderPolicy.AngledBroadFootprintMinHeightRatio &&
                height <= footprint * OrcaThumbnailRenderPolicy.AngledBroadFootprintMaxHeightRatio
        return if (isBroadLowOrMidHeightFootprint) {
            OrcaThumbnailRenderPolicy.AngledBroadFootprintZoomToBoxMarginFactor
        } else {
            OrcaThumbnailRenderPolicy.AngledZoomToBoxMarginFactor
        }
    }

    private fun buildOrcaPlateTopViewProjection(
        width: Int,
        height: Int,
        printerBed: PrinterBedSpec
    ): FloatArray {
        val aspectRatio = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        val bedHalfWidth = printerBed.widthMm * 0.5f
        val bedHalfDepth = printerBed.depthMm * 0.5f
        val halfHeight = max(bedHalfDepth, bedHalfWidth / aspectRatio) * OrcaThumbnailRenderPolicy.TopPlateMargin
        val halfWidth = halfHeight * aspectRatio
        val far = max(
            printerBed.maxHeightMm * 3f,
            max(printerBed.widthMm, printerBed.depthMm) * 3f
        )
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        val viewProjectionMatrix = FloatArray(16)
        Matrix.setLookAtM(
            viewMatrix,
            0,
            0f,
            0f,
            far * 0.5f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )
        Matrix.orthoM(
            projectionMatrix,
            0,
            -halfWidth,
            halfWidth,
            -halfHeight,
            halfHeight,
            1f,
            far
        )
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        return viewProjectionMatrix
    }

    private fun orcaThumbnailVolumesBox(
        printerBed: PrinterBedSpec,
        sources: List<ThumbnailSource>
    ): WorldBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = 0f
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = 0f
        sources.forEach { source ->
            val sourceBounds = transformedWorldBounds(source, printerBed)
            minX = min(minX, sourceBounds.minX)
            minY = min(minY, sourceBounds.minY)
            minZ = min(minZ, sourceBounds.minZ)
            maxX = max(maxX, sourceBounds.maxX)
            maxY = max(maxY, sourceBounds.maxY)
            maxZ = max(maxZ, sourceBounds.maxZ)
        }
        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
            minX = -10f
            maxX = 10f
            minY = -10f
            maxY = 10f
            minZ = 0f
            maxZ = 20f
        }
        val width = maxX - minX
        val depth = maxY - minY
        val height = maxZ - minZ
        return WorldBounds(
            minX = minX - width * OrcaThumbnailRenderPolicy.AngledBoxHorizontalMarginFactor,
            maxX = maxX + width * OrcaThumbnailRenderPolicy.AngledBoxHorizontalMarginFactor,
            minY = minY - depth * OrcaThumbnailRenderPolicy.AngledBoxHorizontalMarginFactor,
            maxY = maxY + depth * OrcaThumbnailRenderPolicy.AngledBoxHorizontalMarginFactor,
            minZ = minZ - height * OrcaThumbnailRenderPolicy.AngledBoxVerticalMarginFactor,
            maxZ = maxZ + height * OrcaThumbnailRenderPolicy.AngledBoxVerticalMarginFactor
        )
    }

    private fun transformedWorldBounds(
        source: ThumbnailSource,
        printerBed: PrinterBedSpec
    ): WorldBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        val placement = buildModelPlacement(source.mesh, source.transform, printerBed)
        val modelMatrix = placement.matrix
        val bounds = source.mesh.bounds
        val xs = floatArrayOf(bounds.minX, bounds.maxX)
        val ys = floatArrayOf(bounds.minY, bounds.maxY)
        val zs = floatArrayOf(bounds.minZ, bounds.maxZ)
        val local = FloatArray(4)
        val world = FloatArray(4)
        for (x in xs) {
            for (y in ys) {
                for (z in zs) {
                    local[0] = x
                    local[1] = y
                    local[2] = z
                    local[3] = 1f
                    Matrix.multiplyMV(world, 0, modelMatrix, 0, local, 0)
                    minX = min(minX, world[0])
                    minY = min(minY, world[1])
                    minZ = min(minZ, world[2])
                    maxX = max(maxX, world[0])
                    maxY = max(maxY, world[1])
                    maxZ = max(maxZ, world[2])
                }
            }
        }
        return WorldBounds(minX = minX, minY = minY, minZ = minZ, maxX = maxX, maxY = maxY, maxZ = maxZ)
    }

    private fun projectedBoxViewBounds(
        viewMatrix: FloatArray,
        bounds: WorldBounds
    ): ViewSpaceBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        val world = FloatArray(4)
        val view = FloatArray(4)
        val xs = floatArrayOf(bounds.minX, bounds.maxX)
        val ys = floatArrayOf(bounds.minY, bounds.maxY)
        val zs = floatArrayOf(bounds.minZ, bounds.maxZ)
        for (x in xs) {
            for (y in ys) {
                for (z in zs) {
                    world[0] = x
                    world[1] = y
                    world[2] = z
                    world[3] = 1f
                    Matrix.multiplyMV(view, 0, viewMatrix, 0, world, 0)
                    minX = min(minX, view[0])
                    minY = min(minY, view[1])
                    maxX = max(maxX, view[0])
                    maxY = max(maxY, view[1])
                }
            }
        }
        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
            minX = -10f
            maxX = 10f
            minY = -10f
            maxY = 10f
        }
        return ViewSpaceBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

    private fun choosePbufferConfig(display: EGLDisplay): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        require(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) {
            "Unable to choose EGL pbuffer config."
        }
        require(count[0] > 0 && configs[0] != null) { "No EGL pbuffer config found." }
        return requireNotNull(configs[0])
    }

    private fun createContext(display: EGLDisplay, config: EGLConfig): EGLContext {
        val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attributes, 0)
        require(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context." }
        return context
    }

    private fun createPbufferSurface(display: EGLDisplay, config: EGLConfig, width: Int, height: Int): EGLSurface {
        val attributes = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, attributes, 0)
        require(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer surface." }
        return surface
    }

    private fun ensureSession(width: Int, height: Int): Pair<RenderSession, Long> {
        val startedAt = SystemClock.elapsedRealtime()
        val existing = session
        if (existing != null) {
            if (existing.surfaceWidth != width || existing.surfaceHeight != height) {
                EGL14.eglMakeCurrent(
                    existing.display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (existing.surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(existing.display, existing.surface)
                }
                existing.surface = createPbufferSurface(existing.display, existing.config, width, height)
                existing.surfaceWidth = width
                existing.surfaceHeight = height
            }
            require(EGL14.eglMakeCurrent(existing.display, existing.surface, existing.surface, existing.context)) {
                "Unable to bind existing offscreen thumbnail EGL context."
            }
            return existing to (SystemClock.elapsedRealtime() - startedAt)
        }

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display." }
        val version = IntArray(2)
        require(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL." }
        val config = choosePbufferConfig(display)
        val context = createContext(display, config)
        val surface = createPbufferSurface(display, config, width, height)
        require(EGL14.eglMakeCurrent(display, surface, surface, context)) {
            "Unable to bind offscreen thumbnail EGL context."
        }
        val created = RenderSession(
            display = display,
            config = config,
            context = context,
            surface = surface,
            surfaceWidth = width,
            surfaceHeight = height
        )
        session = created
        return created to (SystemClock.elapsedRealtime() - startedAt)
    }

    private data class RenderSession(
        val display: EGLDisplay,
        val config: EGLConfig,
        val context: EGLContext,
        var surface: EGLSurface,
        var surfaceWidth: Int,
        var surfaceHeight: Int,
        var program: TriangleProgram? = null
    )
}

private data class ViewSpaceBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
)

private data class WorldBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
)

internal fun downsampleThumbnailRgba(
    rgba: ByteArray,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): ByteArray {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive." }
    require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive." }
    require(rgba.size >= sourceWidth * sourceHeight * 4) { "Source RGBA buffer is too small." }
    if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
        return rgba.copyOf(targetWidth * targetHeight * 4)
    }
    val output = ByteArray(targetWidth * targetHeight * 4)
    for (targetY in 0 until targetHeight) {
        val sourceY0 = targetY * sourceHeight / targetHeight
        val sourceY1 = ((targetY + 1) * sourceHeight / targetHeight).coerceAtLeast(sourceY0 + 1)
        for (targetX in 0 until targetWidth) {
            val sourceX0 = targetX * sourceWidth / targetWidth
            val sourceX1 = ((targetX + 1) * sourceWidth / targetWidth).coerceAtLeast(sourceX0 + 1)
            var red = 0
            var green = 0
            var blue = 0
            var alpha = 0
            var count = 0
            for (sourceY in sourceY0 until sourceY1.coerceAtMost(sourceHeight)) {
                for (sourceX in sourceX0 until sourceX1.coerceAtMost(sourceWidth)) {
                    val sourceOffset = (sourceY * sourceWidth + sourceX) * 4
                    red += rgba[sourceOffset].toInt() and 0xff
                    green += rgba[sourceOffset + 1].toInt() and 0xff
                    blue += rgba[sourceOffset + 2].toInt() and 0xff
                    alpha += rgba[sourceOffset + 3].toInt() and 0xff
                    count++
                }
            }
            val targetOffset = (targetY * targetWidth + targetX) * 4
            output[targetOffset] = ((red + count / 2) / count).toByte()
            output[targetOffset + 1] = ((green + count / 2) / count).toByte()
            output[targetOffset + 2] = ((blue + count / 2) / count).toByte()
            output[targetOffset + 3] = ((alpha + count / 2) / count).toByte()
        }
    }
    return output
}

private fun com.mobileslicer.viewer.StlMesh.expandedVertices(): FloatArray {
    if (indices == null) {
        return vertices
    }
    val expanded = FloatArray(triangleCount * 9)
    var targetOffset = 0
    for (triangleIndex in 0 until triangleCount) {
        repeat(3) { corner ->
            val sourceOffset = indices[triangleIndex * 3 + corner] * 3
            expanded[targetOffset++] = vertices[sourceOffset]
            expanded[targetOffset++] = vertices[sourceOffset + 1]
            expanded[targetOffset++] = vertices[sourceOffset + 2]
        }
    }
    return expanded
}

private fun com.mobileslicer.viewer.StlMesh.expandedNormals(): FloatArray {
    if (indices == null) {
        return normals
    }
    val expanded = FloatArray(triangleCount * 9)
    var targetOffset = 0
    for (triangleIndex in 0 until triangleCount) {
        repeat(3) { corner ->
            val sourceOffset = indices[triangleIndex * 3 + corner] * 3
            expanded[targetOffset++] = normals.getOrElse(sourceOffset) { 0f }
            expanded[targetOffset++] = normals.getOrElse(sourceOffset + 1) { 0f }
            expanded[targetOffset++] = normals.getOrElse(sourceOffset + 2) { 1f }
        }
    }
    return expanded
}

private fun thumbnailColorFloatArray(color: Int): FloatArray =
    floatArrayOf(
        ((color shr 16) and 0xff) / 255f,
        ((color shr 8) and 0xff) / 255f,
        (color and 0xff) / 255f,
        1f
    )
