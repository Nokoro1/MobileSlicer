package com.mobileslicer.scanner

import android.content.Context
import android.util.AttributeSet
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.getSystemService
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class ScannerArCoreCaptureView(
    context: Context,
    private val sessionDir: File,
    private val onStatus: (String) -> Unit
) : GLSurfaceView(context) {
    constructor(context: Context) : this(
        context = context,
        sessionDir = File(context.cacheDir, "scanner-arcore-preview"),
        onStatus = {}
    )

    constructor(context: Context, attrs: AttributeSet?) : this(
        context = context,
        sessionDir = File(context.cacheDir, "scanner-arcore-preview"),
        onStatus = {}
    )

    private val renderer = ScannerArCoreRenderer(
        context = context.applicationContext,
        sessionDir = sessionDir,
        onStatus = onStatus
    )

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun captureKeyframe(
        index: Int,
        capturePass: ScannerCapturePass,
        callback: (ScannerArCoreRawDepthCaptureResult) -> Unit
    ) {
        queueEvent {
            renderer.captureKeyframe(index = index, capturePass = capturePass, callback = callback)
        }
    }

    fun closeSession() {
        queueEvent {
            renderer.closeSession()
        }
    }
}

private data class ScannerPendingArCoreCapture(
    val index: Int,
    val capturePass: ScannerCapturePass,
    val callback: (ScannerArCoreRawDepthCaptureResult) -> Unit
)

private class ScannerArCoreRenderer(
    private val context: Context,
    private val sessionDir: File,
    private val onStatus: (String) -> Unit
) : GLSurfaceView.Renderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCapture = AtomicReference<ScannerPendingArCoreCapture?>()
    private var session: Session? = null
    private var captureManager: ScannerArCoreRawDepthCaptureManager? = null
    private var textureId: Int = 0
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var displayGeometryDirty = true
    private var configured = false
    private val vertexBuffer = directFloatBuffer(
        floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
    )
    private val textureCoordinateBuffer = directFloatBuffer(
        floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureId = createExternalTexture()
        program = createProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        ensureSession()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        displayGeometryDirty = true
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val activeSession = session ?: return
        if (!configured) {
            configureSession(activeSession)
        }
        val frame = runCatching {
            if (displayGeometryDirty) {
                activeSession.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                displayGeometryDirty = false
            }
            activeSession.setCameraTextureName(textureId)
            activeSession.update()
        }.getOrElse {
            postStatus("ARCore frame unavailable: ${it.javaClass.simpleName}")
            deliverPendingFailure("arcore_update_failed:${it.javaClass.simpleName}")
            return
        }
        updateTextureCoordinates(frame)
        drawCameraTexture()
        pendingCapture.getAndSet(null)?.let { request ->
            val result = captureManager?.captureFrame(
                index = request.index,
                capturePass = request.capturePass,
                frame = frame
            ) ?: ScannerArCoreRawDepthCaptureResult(
                frame = null,
                status = "arcore_capture_manager_unavailable",
                errors = listOf("arcore_capture_manager_unavailable"),
                warnings = emptyList()
            )
            mainHandler.post { request.callback(result) }
        }
    }

    fun captureKeyframe(
        index: Int,
        capturePass: ScannerCapturePass,
        callback: (ScannerArCoreRawDepthCaptureResult) -> Unit
    ) {
        if (session == null) {
            callback(
                ScannerArCoreRawDepthCaptureResult(
                    frame = null,
                    status = "arcore_session_unavailable",
                    errors = listOf("arcore_session_unavailable"),
                    warnings = emptyList()
                )
            )
            return
        }
        pendingCapture.set(ScannerPendingArCoreCapture(index, capturePass, callback))
    }

    fun closeSession() {
        runCatching { session?.pause() }
        runCatching { session?.close() }
        session = null
        captureManager = null
        configured = false
    }

    private fun ensureSession() {
        if (session != null) return
        val createdSession = runCatching { Session(context) }
            .getOrElse {
                postStatus("ARCore unavailable: ${it.javaClass.simpleName}")
                return
            }
        session = createdSession
        createdSession.setCameraTextureName(textureId)
        configureSession(createdSession)
        runCatching { createdSession.resume() }
            .onFailure { postStatus("ARCore resume failed: ${it.javaClass.simpleName}") }
        captureManager = ScannerArCoreRawDepthCaptureManager(
            session = createdSession,
            sessionDir = sessionDir
        )
    }

    private fun configureSession(activeSession: Session) {
        val depthConfig = configureScannerArCoreRawDepthSession(activeSession)
        configured = depthConfig.configured
        postStatus(
            if (depthConfig.configured) {
                "AR depth ready: ${depthConfig.depthMode}"
            } else {
                "AR depth unavailable: ${depthConfig.reason}"
            }
        )
    }

    private fun deliverPendingFailure(reason: String) {
        pendingCapture.getAndSet(null)?.let { request ->
            mainHandler.post {
                request.callback(
                    ScannerArCoreRawDepthCaptureResult(
                        frame = null,
                        status = "arcore_frame_unavailable",
                        errors = listOf(reason),
                        warnings = emptyList()
                    )
                )
            }
        }
    }

    private fun postStatus(status: String) {
        mainHandler.post { onStatus(status) }
    }

    private fun drawCameraTexture() {
        if (program == 0 || textureId == 0) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        vertexBuffer.position(0)
        textureCoordinateBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureCoordinateBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun updateTextureCoordinates(frame: Frame) {
        val ndcCoordinates = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
        val transformed = FloatArray(ndcCoordinates.size)
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            ndcCoordinates,
            Coordinates2d.TEXTURE_NORMALIZED,
            transformed
        )
        textureCoordinateBuffer.position(0)
        textureCoordinateBuffer.put(transformed)
        textureCoordinateBuffer.position(0)
    }

    private fun displayRotation(): Int {
        val display = context.getSystemService<WindowManager>()?.defaultDisplay
        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(): Int {
        val vertexShader = compileShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
            """.trimIndent()
        )
        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
            """.trimIndent()
        )
        return GLES20.glCreateProgram().also { programId ->
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
}

private fun directFloatBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * java.lang.Float.BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
