package com.mobileslicer.automation

import android.content.Intent
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.SystemClock
import android.util.Log
import com.mobileslicer.nativebridge.NativeThumbnailRequest
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.OffscreenEglSliceThumbnailRenderer
import com.mobileslicer.workspace.SliceThumbnailVisualMetrics
import com.mobileslicer.workspace.SoftwareSliceThumbnailRenderer
import com.mobileslicer.workspace.ThumbnailRenderRole
import com.mobileslicer.workspace.ThumbnailSource
import com.mobileslicer.workspace.inspectSliceThumbnailRgba
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

internal data class OffscreenEglSmokeRequest(
    val statusPath: String,
    val width: Int,
    val height: Int,
    val repeats: Int,
    val action: String = ACTION_EGL_THUMBNAIL_SMOKE,
    val modelPath: String? = null,
    val artifactDirPath: String? = null,
    val bedWidthMm: Float = 270f,
    val bedDepthMm: Float = 270f,
    val bedHeightMm: Float = 256f,
    val sourceLayout: String = SOURCE_LAYOUT_SINGLE,
    val sourceColors: List<Int> = emptyList()
) {
    companion object {
        const val ACTION_EGL_THUMBNAIL_SMOKE = "com.mobileslicer.action.EGL_THUMBNAIL_SMOKE"
        const val ACTION_EGL_SLICE_THUMBNAIL_SMOKE = "com.mobileslicer.action.EGL_SLICE_THUMBNAIL_SMOKE"
        const val ACTION_EGL_THUMBNAIL_COMPARE = "com.mobileslicer.action.EGL_THUMBNAIL_COMPARE"
        const val SOURCE_LAYOUT_SINGLE = "single"
        const val SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS = "two_filament_objects"
        const val EXTRA_STATUS_PATH = "automation_status_path"
        const val EXTRA_WIDTH = "automation_width"
        const val EXTRA_HEIGHT = "automation_height"
        const val EXTRA_REPEATS = "automation_repeats"
        const val EXTRA_MODEL_PATH = "automation_model_path"
        const val EXTRA_ARTIFACT_DIR = "automation_artifact_dir"
        const val EXTRA_BED_WIDTH_MM = "automation_bed_width_mm"
        const val EXTRA_BED_DEPTH_MM = "automation_bed_depth_mm"
        const val EXTRA_BED_HEIGHT_MM = "automation_bed_height_mm"
        const val EXTRA_SOURCE_LAYOUT = "automation_thumbnail_source_layout"
        const val EXTRA_SOURCE_COLORS = "automation_thumbnail_source_colors"

        fun fromIntent(intent: Intent?): OffscreenEglSmokeRequest? {
            val action = intent?.action ?: return null
            if (action !in SupportedActions) {
                return null
            }
            return fromValues(
                action = action,
                statusPath = intent.getStringExtra(EXTRA_STATUS_PATH),
                width = intent.getIntExtra(EXTRA_WIDTH, 128),
                height = intent.getIntExtra(EXTRA_HEIGHT, 128),
                repeats = intent.getIntExtra(EXTRA_REPEATS, 3),
                modelPath = intent.getStringExtra(EXTRA_MODEL_PATH),
                artifactDirPath = intent.getStringExtra(EXTRA_ARTIFACT_DIR),
                bedWidthMm = intent.getFloatExtra(EXTRA_BED_WIDTH_MM, 270f),
                bedDepthMm = intent.getFloatExtra(EXTRA_BED_DEPTH_MM, 270f),
                bedHeightMm = intent.getFloatExtra(EXTRA_BED_HEIGHT_MM, 256f),
                sourceLayout = intent.getStringExtra(EXTRA_SOURCE_LAYOUT),
                sourceColors = parseSourceColors(intent.getStringExtra(EXTRA_SOURCE_COLORS))
            )
        }

        fun fromValues(
            action: String?,
            statusPath: String?,
            width: Int = 128,
            height: Int = 128,
            repeats: Int = 3,
            modelPath: String? = null,
            artifactDirPath: String? = null,
            bedWidthMm: Float = 270f,
            bedDepthMm: Float = 270f,
            bedHeightMm: Float = 256f,
            sourceLayout: String? = SOURCE_LAYOUT_SINGLE,
            sourceColors: List<Int> = emptyList()
        ): OffscreenEglSmokeRequest? {
            if (action !in SupportedActions || statusPath.isNullOrBlank()) {
                return null
            }
            val normalizedSourceLayout = when (sourceLayout?.trim().orEmpty()) {
                "", SOURCE_LAYOUT_SINGLE -> SOURCE_LAYOUT_SINGLE
                SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS -> SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS
                else -> SOURCE_LAYOUT_SINGLE
            }
            return OffscreenEglSmokeRequest(
                statusPath = statusPath,
                width = width.coerceIn(16, 1024),
                height = height.coerceIn(16, 1024),
                repeats = repeats.coerceIn(1, 20),
                action = requireNotNull(action),
                modelPath = modelPath,
                artifactDirPath = artifactDirPath,
                bedWidthMm = bedWidthMm.coerceIn(50f, 1000f),
                bedDepthMm = bedDepthMm.coerceIn(50f, 1000f),
                bedHeightMm = bedHeightMm.coerceIn(50f, 1000f),
                sourceLayout = normalizedSourceLayout,
                sourceColors = sourceColors.take(16)
            )
        }

        private fun parseSourceColors(rawColors: String?): List<Int> =
            rawColors
                ?.split(',', ';', '|')
                ?.mapNotNull { token -> parseHexColor(token.trim()) }
                .orEmpty()

        private fun parseHexColor(rawColor: String): Int? {
            val normalized = rawColor.removePrefix("#")
            if (normalized.length != 6) {
                return null
            }
            return normalized.toIntOrNull(radix = 16)
        }

        private val SupportedActions = setOf(
            ACTION_EGL_THUMBNAIL_SMOKE,
            ACTION_EGL_SLICE_THUMBNAIL_SMOKE,
            ACTION_EGL_THUMBNAIL_COMPARE
        )
    }
}

internal data class OffscreenEglSmokeMetrics(
    val width: Int,
    val height: Int,
    val repeats: Int,
    val eglCreateMs: Long,
    val renderMs: Long,
    val readPixelsMs: Long,
    val cleanupMs: Long,
    val totalMs: Long,
    val nontransparentPixels: Int,
    val transparentPixels: Int,
    val bboxMinX: Int,
    val bboxMinY: Int,
    val bboxMaxX: Int,
    val bboxMaxY: Int,
    val averageLuma: Int,
    val glError: Int
) {
    fun status(): String =
        "success: width=$width height=$height repeats=$repeats " +
            "eglCreateMs=$eglCreateMs renderMs=$renderMs readPixelsMs=$readPixelsMs cleanupMs=$cleanupMs totalMs=$totalMs " +
            "nontransparentPixels=$nontransparentPixels transparentPixels=$transparentPixels " +
            "bboxMinX=$bboxMinX bboxMinY=$bboxMinY bboxMaxX=$bboxMaxX bboxMaxY=$bboxMaxY " +
            "averageLuma=$averageLuma glError=$glError"
}

internal class OffscreenEglSmokeRunner {
    fun run(request: OffscreenEglSmokeRequest): Boolean {
        val statusFile = File(request.statusPath)
        fun writeStatus(message: String) {
            Log.i(TAG, "egl_thumbnail_smoke:$message")
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(message, StandardCharsets.UTF_8)
        }

        return runCatching {
            if (request.action == OffscreenEglSmokeRequest.ACTION_EGL_SLICE_THUMBNAIL_SMOKE) {
                return runSliceThumbnailSmoke(request, ::writeStatus)
            }
            if (request.action == OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_COMPARE) {
                return runThumbnailCompare(request, ::writeStatus)
            }
            val metrics = renderSmoke(request)
            if (metrics.nontransparentPixels <= 0) {
                writeStatus("failed: blank render ${metrics.status()}")
                return false
            }
            if (metrics.glError != GLES20.GL_NO_ERROR) {
                writeStatus("failed: glError=${metrics.glError} ${metrics.status()}")
                return false
            }
            writeStatus(metrics.status())
            true
        }.getOrElse { exception ->
            Log.e(TAG, "egl_thumbnail_smoke:failed", exception)
            writeStatus("failed: ${exception.javaClass.simpleName} ${exception.message.orEmpty().sanitizeStatusToken()}")
            false
        }
    }

    private fun runSliceThumbnailSmoke(request: OffscreenEglSmokeRequest, writeStatus: (String) -> Unit): Boolean {
        val renderer = OffscreenEglSliceThumbnailRenderer()
        try {
            val thumbnail = renderer.render(
                request = NativeThumbnailRequest(width = request.width, height = request.height, format = "PNG"),
                role = ThumbnailRenderRole.Plate,
                sources = listOf(
                    ThumbnailSource(
                        mesh = smokeCubeMesh(),
                        transform = ViewerModelTransform(centerXmm = 135f, centerYmm = 135f),
                        color = null
                    )
                ),
                printerBed = PrinterBedSpec(widthMm = 270f, depthMm = 270f, maxHeightMm = 256f)
            )
            val visualMetrics = inspectSliceThumbnailRgba(thumbnail)
            val timing = renderer.lastTiming
            if (!visualMetrics.visible) {
                writeStatus("failed: blank slice thumbnail ${visualMetrics.statusFields()} ${timing.statusFields()}")
                return false
            }
            if (timing?.glError != GLES20.GL_NO_ERROR) {
                writeStatus("failed: glError=${timing?.glError} ${visualMetrics.statusFields()} ${timing.statusFields()}")
                return false
            }
            writeStatus(
                "success: renderer=${thumbnail.renderer} role=${thumbnail.role} " +
                    "sourceTriangles=${thumbnail.sourceTriangleCount} renderedTriangles=${thumbnail.renderedTriangleCount} " +
                    "${visualMetrics.statusFields()} ${timing.statusFields()}"
            )
            return true
        } finally {
            renderer.close()
        }
    }

    private fun runThumbnailCompare(request: OffscreenEglSmokeRequest, writeStatus: (String) -> Unit): Boolean {
        val modelPath = request.modelPath ?: run {
            writeStatus("failed: missing modelPath")
            return false
        }
        val artifactDir = File(request.artifactDirPath ?: "${File(request.statusPath).parent}/egl-thumbnail-compare")
        artifactDir.mkdirs()
        val modelFile = File(modelPath)
        val mesh = StlMeshParser.parseForDisplay(modelFile).mesh
        val printerBed = PrinterBedSpec(
            widthMm = request.bedWidthMm,
            depthMm = request.bedDepthMm,
            maxHeightMm = request.bedHeightMm
        )
        val sources = request.thumbnailSources(mesh, printerBed)
        val thumbnailRequest = NativeThumbnailRequest(width = request.width, height = request.height, format = "PNG")
        val roles = listOf(
            ThumbnailRenderRole.Plate,
            ThumbnailRenderRole.NoLight,
            ThumbnailRenderRole.Top,
            ThumbnailRenderRole.Pick
        )
        val softwareRenderer = SoftwareSliceThumbnailRenderer
        val eglRenderer = OffscreenEglSliceThumbnailRenderer()
        val results = JSONArray()
        try {
            roles.forEach { role ->
                val software = softwareRenderer.render(thumbnailRequest, role, sources, printerBed)
                val egl = eglRenderer.render(thumbnailRequest, role, sources, printerBed)
                val softwareMetrics = inspectSliceThumbnailRgba(software)
                val eglMetrics = inspectSliceThumbnailRgba(egl)
                writePng(File(artifactDir, "software-${role.wireName}.png"), software)
                writePng(File(artifactDir, "egl-${role.wireName}.png"), egl)
                results.put(
                    JSONObject()
                        .put("role", role.wireName)
                        .put("software", softwareMetrics.toJson())
                        .put("egl", eglMetrics.toJson())
                        .put("eglTiming", eglRenderer.lastTiming?.toJson() ?: JSONObject())
                        .put("softwareHash", software.rgba.contentHashCode())
                        .put("eglHash", egl.rgba.contentHashCode())
                )
            }
        } finally {
            eglRenderer.close()
        }
        val summary = JSONObject()
            .put("model", modelFile.name)
            .put("artifactDir", artifactDir.absolutePath)
            .put("width", request.width)
            .put("height", request.height)
            .put("sourceLayout", request.sourceLayout)
            .put("sourceCount", sources.size)
            .put("sourceColors", JSONArray(sources.map { source -> source.color ?: JSONObject.NULL }))
            .put(
                "printerBed",
                JSONObject()
                    .put("widthMm", printerBed.widthMm.toDouble())
                    .put("depthMm", printerBed.depthMm.toDouble())
                    .put("heightMm", printerBed.maxHeightMm.toDouble())
            )
            .put("roles", results)
        val metricsFile = File(artifactDir, "metrics.json")
        metricsFile.writeText(summary.toString(2), StandardCharsets.UTF_8)

        val roleHashes = (0 until results.length()).map { results.getJSONObject(it).getInt("eglHash") }
        val minVisible = (0 until results.length()).minOf {
            results.getJSONObject(it).getJSONObject("egl").getInt("nontransparentPixels")
        }
        if (minVisible <= 0) {
            writeStatus("failed: blank egl role artifactDir=${artifactDir.absolutePath} metrics=${metricsFile.absolutePath}")
            return false
        }
        if (roleHashes.toSet().size < 3) {
            writeStatus("failed: collapsed egl role outputs distinctRoles=${roleHashes.toSet().size} artifactDir=${artifactDir.absolutePath} metrics=${metricsFile.absolutePath}")
            return false
        }
        writeStatus(
            "success: artifactDir=${artifactDir.absolutePath} metrics=${metricsFile.absolutePath} " +
                "roles=${roles.size} sourceLayout=${request.sourceLayout} sourceCount=${sources.size} " +
                "distinctEglRoles=${roleHashes.toSet().size} minVisible=$minVisible"
        )
        return true
    }

    private fun OffscreenEglSmokeRequest.thumbnailSources(mesh: StlMesh, printerBed: PrinterBedSpec): List<ThumbnailSource> {
        val centerX = printerBed.widthMm * 0.5f
        val centerY = printerBed.depthMm * 0.5f
        if (sourceLayout != OffscreenEglSmokeRequest.SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS) {
            return listOf(
                ThumbnailSource(
                    mesh = mesh,
                    transform = ViewerModelTransform(centerXmm = centerX, centerYmm = centerY),
                    color = sourceColors.getOrNull(0)
                )
            )
        }
        val spacingMm = (mesh.bounds.sizeY + 2f).coerceAtLeast(12f)
        return listOf(
            ThumbnailSource(
                mesh = mesh,
                transform = ViewerModelTransform(centerXmm = centerX, centerYmm = centerY - spacingMm * 0.5f),
                color = sourceColors.getOrNull(0) ?: thumbnailColor(242, 117, 78)
            ),
            ThumbnailSource(
                mesh = mesh,
                transform = ViewerModelTransform(centerXmm = centerX, centerYmm = centerY + spacingMm * 0.5f),
                color = sourceColors.getOrNull(1) ?: thumbnailColor(78, 163, 242)
            )
        )
    }

    private fun thumbnailColor(red: Int, green: Int, blue: Int): Int =
        ((red and 0xff) shl 16) or ((green and 0xff) shl 8) or (blue and 0xff)

    private fun renderSmoke(request: OffscreenEglSmokeRequest): OffscreenEglSmokeMetrics {
        val totalStartedAt = SystemClock.elapsedRealtime()
        val eglStartedAt = SystemClock.elapsedRealtime()
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display." }
        val version = IntArray(2)
        require(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL." }
        val config = choosePbufferConfig(display)
        val context = createContext(display, config)
        val surface = createPbufferSurface(display, config, request.width, request.height)
        require(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Unable to bind offscreen EGL context." }
        val eglCreateMs = SystemClock.elapsedRealtime() - eglStartedAt

        var program = 0
        var renderMs = 0L
        var readPixelsMs = 0L
        var glError = GLES20.GL_NO_ERROR
        var cleanedUp = false
        val pixelBuffer = ByteBuffer.allocateDirect(request.width * request.height * 4).order(ByteOrder.nativeOrder())
        try {
            program = buildProgram()
            repeat(request.repeats) {
                val renderStartedAt = SystemClock.elapsedRealtime()
                drawTriangle(program, request.width, request.height)
                GLES20.glFinish()
                renderMs += SystemClock.elapsedRealtime() - renderStartedAt

                val readStartedAt = SystemClock.elapsedRealtime()
                pixelBuffer.position(0)
                GLES20.glReadPixels(
                    0,
                    0,
                    request.width,
                    request.height,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    pixelBuffer
                )
                readPixelsMs += SystemClock.elapsedRealtime() - readStartedAt
                glError = GLES20.glGetError()
            }
            val visualMetrics = inspectPixels(pixelBuffer, request.width, request.height)
            val cleanupStartedAt = SystemClock.elapsedRealtime()
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            cleanup(display, surface, context)
            cleanedUp = true
            val cleanupMs = SystemClock.elapsedRealtime() - cleanupStartedAt
            return OffscreenEglSmokeMetrics(
                width = request.width,
                height = request.height,
                repeats = request.repeats,
                eglCreateMs = eglCreateMs,
                renderMs = renderMs,
                readPixelsMs = readPixelsMs,
                cleanupMs = cleanupMs,
                totalMs = SystemClock.elapsedRealtime() - totalStartedAt,
                nontransparentPixels = visualMetrics.nontransparentPixels,
                transparentPixels = visualMetrics.transparentPixels,
                bboxMinX = visualMetrics.bboxMinX,
                bboxMinY = visualMetrics.bboxMinY,
                bboxMaxX = visualMetrics.bboxMaxX,
                bboxMaxY = visualMetrics.bboxMaxY,
                averageLuma = visualMetrics.averageLuma.roundToInt(),
                glError = glError
            )
        } finally {
            if (program != 0) {
                GLES20.glDeleteProgram(program)
            }
            if (!cleanedUp) {
                cleanup(display, surface, context)
            }
        }
    }

    private fun choosePbufferConfig(display: EGLDisplay): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
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
        val attributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attributes, 0)
        require(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context." }
        return context
    }

    private fun createPbufferSurface(display: EGLDisplay, config: EGLConfig, width: Int, height: Int): EGLSurface {
        val attributes = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val surface = EGL14.eglCreatePbufferSurface(display, config, attributes, 0)
        require(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer surface." }
        return surface
    }

    private fun cleanup(display: EGLDisplay, surface: EGLSurface, context: EGLContext) {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface)
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context)
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(display)
        }
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        require(linkStatus[0] != 0) { "EGL smoke program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        require(compileStatus[0] != 0) { "EGL smoke shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun drawTriangle(program: Int, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glUniform4f(colorHandle, 0.48f, 0.73f, 1.0f, 1.0f)
        val vertices = ByteBuffer.allocateDirect(TRIANGLE_VERTICES.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(TRIANGLE_VERTICES)
                position(0)
            }
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glUseProgram(0)
    }

    private fun inspectPixels(buffer: ByteBuffer, width: Int, height: Int): VisualMetrics {
        var nontransparent = 0
        var luma = 0.0
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        buffer.position(0)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val red = buffer.get().toInt() and 0xff
                val green = buffer.get().toInt() and 0xff
                val blue = buffer.get().toInt() and 0xff
                val alpha = buffer.get().toInt() and 0xff
                if (alpha == 0) {
                    continue
                }
                nontransparent++
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                luma += 0.2126 * red + 0.7152 * green + 0.0722 * blue
            }
        }
        return VisualMetrics(
            nontransparentPixels = nontransparent,
            transparentPixels = width * height - nontransparent,
            bboxMinX = if (nontransparent > 0) minX else -1,
            bboxMinY = if (nontransparent > 0) minY else -1,
            bboxMaxX = if (nontransparent > 0) maxX else -1,
            bboxMaxY = if (nontransparent > 0) maxY else -1,
            averageLuma = if (nontransparent > 0) luma / nontransparent else 0.0
        )
    }

    private data class VisualMetrics(
        val nontransparentPixels: Int,
        val transparentPixels: Int,
        val bboxMinX: Int,
        val bboxMinY: Int,
        val bboxMaxX: Int,
        val bboxMaxY: Int,
        val averageLuma: Double
    )

    private fun String.sanitizeStatusToken(): String =
        trim().replace(Regex("""\s+"""), "_").take(120)

    private fun SliceThumbnailVisualMetrics.statusFields(): String =
        "width=$width height=$height nontransparentPixels=$nontransparentPixels transparentPixels=$transparentPixels " +
            "bboxMinX=$bboxMinX bboxMinY=$bboxMinY bboxMaxX=$bboxMaxX bboxMaxY=$bboxMaxY averageLuma=$averageLuma"

    private fun SliceThumbnailVisualMetrics.toJson(): JSONObject =
        JSONObject()
            .put("width", width)
            .put("height", height)
            .put("nontransparentPixels", nontransparentPixels)
            .put("transparentPixels", transparentPixels)
            .put("bboxMinX", bboxMinX)
            .put("bboxMinY", bboxMinY)
            .put("bboxMaxX", bboxMaxX)
            .put("bboxMaxY", bboxMaxY)
            .put("averageLuma", averageLuma)

    private fun com.mobileslicer.workspace.OffscreenEglThumbnailTiming?.statusFields(): String =
        this?.let {
            "eglCreateMs=${it.eglCreateMs} uploadMs=${it.uploadMs} drawMs=${it.drawMs} readPixelsMs=${it.readPixelsMs} " +
                "cleanupMs=${it.cleanupMs} totalMs=${it.totalMs} glError=${it.glError}"
        } ?: "eglCreateMs=0 uploadMs=0 drawMs=0 readPixelsMs=0 cleanupMs=0 totalMs=0 glError=-1"

    private fun com.mobileslicer.workspace.OffscreenEglThumbnailTiming.toJson(): JSONObject =
        JSONObject()
            .put("eglCreateMs", eglCreateMs)
            .put("uploadMs", uploadMs)
            .put("drawMs", drawMs)
            .put("readPixelsMs", readPixelsMs)
            .put("cleanupMs", cleanupMs)
            .put("totalMs", totalMs)
            .put("glError", glError)

    private fun writePng(file: File, thumbnail: com.mobileslicer.workspace.SliceThumbnailRgba) {
        val pixels = IntArray(thumbnail.width * thumbnail.height)
        var offset = 0
        for (index in pixels.indices) {
            val red = thumbnail.rgba[offset].toInt() and 0xff
            val green = thumbnail.rgba[offset + 1].toInt() and 0xff
            val blue = thumbnail.rgba[offset + 2].toInt() and 0xff
            val alpha = thumbnail.rgba[offset + 3].toInt() and 0xff
            pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            offset += 4
        }
        val bitmap = Bitmap.createBitmap(thumbnail.width, thumbnail.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, thumbnail.width, 0, 0, thumbnail.width, thumbnail.height)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    private fun smokeCubeMesh(): StlMesh =
        StlMesh(
            vertices = SMOKE_CUBE_VERTICES,
            normals = SMOKE_CUBE_NORMALS,
            triangleCount = SMOKE_CUBE_VERTICES.size / 9,
            bounds = MeshBounds(
                minX = -10f,
                minY = -10f,
                minZ = 0f,
                maxX = 10f,
                maxY = 10f,
                maxZ = 20f
            ),
            indices = null
        )

    private companion object {
        private const val TAG = "MobileSlicer"
        private val TRIANGLE_VERTICES = floatArrayOf(
            -0.65f, -0.60f,
            0.65f, -0.60f,
            0.00f, 0.70f
        )
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
        private val SMOKE_CUBE_VERTICES = floatArrayOf(
            -10f, -10f, 0f, 10f, -10f, 0f, 10f, 10f, 0f,
            -10f, -10f, 0f, 10f, 10f, 0f, -10f, 10f, 0f,
            -10f, -10f, 20f, 10f, 10f, 20f, 10f, -10f, 20f,
            -10f, -10f, 20f, -10f, 10f, 20f, 10f, 10f, 20f,
            -10f, -10f, 0f, 10f, -10f, 20f, 10f, -10f, 0f,
            -10f, -10f, 0f, -10f, -10f, 20f, 10f, -10f, 20f,
            10f, -10f, 0f, 10f, -10f, 20f, 10f, 10f, 20f,
            10f, -10f, 0f, 10f, 10f, 20f, 10f, 10f, 0f,
            10f, 10f, 0f, 10f, 10f, 20f, -10f, 10f, 20f,
            10f, 10f, 0f, -10f, 10f, 20f, -10f, 10f, 0f,
            -10f, 10f, 0f, -10f, 10f, 20f, -10f, -10f, 20f,
            -10f, 10f, 0f, -10f, -10f, 20f, -10f, -10f, 0f
        )
        private val SMOKE_CUBE_NORMALS = FloatArray(SMOKE_CUBE_VERTICES.size) { index ->
            when (index % 3) {
                2 -> 1f
                else -> 0f
            }
        }
    }
}
