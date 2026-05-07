package com.mobileslicer.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import com.mobileslicer.nativebridge.NativeEngineCallResult
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal class TouchModelViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val paintMoveMinScreenDistancePx = 1f
    private val objectDragMinBedDistanceMm = 0.15f

    private var renderThread: WorkspaceRenderThread? = null
    private var onViewerFailure: ((ViewerFailure?) -> Unit)? = null
    private var onRenderReady: ((Boolean) -> Unit)? = null
    private var onPreviewRuntimeMetrics: ((GcodePreviewRuntimeMetrics) -> Unit)? = null
    private var onObjectSelected: ((Long?) -> Unit)? = null
    private var onObjectHitSelected: ((ViewerPickHit?) -> Boolean)? = null
    private var onObjectDrag: ((Long, Float, Float, Boolean) -> Unit)? = null
    private var onCutConnectorPointAdded: ((ViewerCutConnectorPoint) -> Unit)? = null
    private var onPaintStrokeBegin: ((ViewerPaintStrokePoint) -> Unit)? = null
    private var onPaintStrokeMove: ((ViewerPaintStrokePoint) -> Unit)? = null
    private var onPaintStrokeEnd: ((Boolean) -> Unit)? = null
    private var currentMesh: StlMesh? = null
    private var currentPlateObjects: List<ViewerPlateObject> = emptyList()
    private var currentPlateObjectsSignature = ViewerUpdateDecisions.plateObjectsSignature(currentPlateObjects)
    private var currentPaintSession: ViewerPaintSession? = null
    private var currentLivePaintOverlay = ViewerPaintOverlay.Empty
    private var currentGcodePreviewEngineHandle = 0L
    private var currentGcodePreviewKey = 0L
    private var currentGcodePreviewVertexBudget = GcodePreviewPerformanceMode.HARD_VERTEX_CEILING
    private var currentGcodeLayerMin = 0L
    private var currentGcodeLayerMax = Long.MAX_VALUE
    private var currentGcodeLayerReloadToken = 0L
    private var currentGcodePathVisibility: Map<Pair<Int, Int>, Boolean> = emptyMap()
    private var currentGcodeDisplayMode = GcodePreviewDisplayMode.Auto
    private var currentCutPlaneSession: ViewerCutPlaneSession? = null
    private var pendingCameraState: ViewerCameraState? = null
    private var currentBed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
    private var currentModelTransform: ViewerModelTransform? = null
    private var currentAppearance = ViewerAppearance(
        darkTheme = true,
        accentColor = Color.rgb(143, 193, 255)
    )
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var twoFingerGesture = TwoFingerGesture.None
    private var twoFingerStartCentroidX = 0f
    private var twoFingerStartCentroidY = 0f
    private var twoFingerStartDistance = 0f
    private var lastTwoFingerDistance = 0f
    private var dragging = false
    private var suppressTapSelection = false
    private var resumed = true
    private var lastPickX = Float.NaN
    private var lastPickY = Float.NaN
    private var lastPickAtMs = 0L
    private var lastPickCandidates: List<Long> = emptyList()
    private var lastPickIndex = -1
    private var movableObjectIds: Set<Long> = emptySet()
    private var objectDragId: Long? = null
    private var objectDragLastBedPoint: StlModelPlacement? = null
    private var twoFingerTapCandidate = false
    private var twoFingerTapStartedAtMs = 0L
    private var lastTwoFingerTapX = Float.NaN
    private var lastTwoFingerTapY = Float.NaN
    private var lastTwoFingerTapAtMs = 0L
    private var paintTouchState = PaintTouchState.None
    private var activeStylusPaintOnly = false
    private var lastPaintDispatchX = Float.NaN
    private var lastPaintDispatchY = Float.NaN
    private var paintGestureDownUptimeMs = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
        isClickable = true
    }

    internal fun setMesh(mesh: StlMesh?) {
        if (currentMesh === mesh) return
        currentMesh = mesh
        onRenderReady?.invoke(false)
        renderThread?.setMesh(mesh)
    }

    internal fun setGcodePreviewSource(
        engineHandle: Long,
        previewKey: Long,
        vertexBudget: Long = currentGcodePreviewVertexBudget
    ) {
        val source = ViewerUpdateDecisions.normalizeGcodePreviewSource(engineHandle, previewKey)
        val safeEngineHandle = source.engineHandle
        val safePreviewKey = source.previewKey
        val safeVertexBudget = vertexBudget.coerceIn(1L, GcodePreviewPerformanceMode.HARD_VERTEX_CEILING)
        if (
            currentGcodePreviewEngineHandle == safeEngineHandle &&
            currentGcodePreviewKey == safePreviewKey &&
            currentGcodePreviewVertexBudget == safeVertexBudget
        ) return
        currentGcodePreviewEngineHandle = safeEngineHandle
        currentGcodePreviewKey = safePreviewKey
        currentGcodePreviewVertexBudget = safeVertexBudget
        onRenderReady?.invoke(false)
        renderThread?.setGcodePreviewSource(safeEngineHandle, safePreviewKey, safeVertexBudget)
    }

    internal fun setGcodePreviewSourceAndLayerRange(
        engineHandle: Long,
        previewKey: Long,
        vertexBudget: Long,
        minLayer: Long,
        maxLayer: Long,
        reloadToken: Long
    ) {
        val source = ViewerUpdateDecisions.normalizeGcodePreviewSource(engineHandle, previewKey)
        val safeEngineHandle = source.engineHandle
        val safePreviewKey = source.previewKey
        val safeVertexBudget = vertexBudget.coerceIn(1L, GcodePreviewPerformanceMode.HARD_VERTEX_CEILING)
        val changed =
            currentGcodePreviewEngineHandle != safeEngineHandle ||
                currentGcodePreviewKey != safePreviewKey ||
                currentGcodePreviewVertexBudget != safeVertexBudget ||
                currentGcodeLayerMin != minLayer ||
                currentGcodeLayerMax != maxLayer ||
                currentGcodeLayerReloadToken != reloadToken
        if (!changed) return
        val sourceChanged =
            currentGcodePreviewEngineHandle != safeEngineHandle ||
                currentGcodePreviewKey != safePreviewKey ||
                currentGcodePreviewVertexBudget != safeVertexBudget
        val reloadChanged = currentGcodeLayerReloadToken != reloadToken
        currentGcodePreviewEngineHandle = safeEngineHandle
        currentGcodePreviewKey = safePreviewKey
        currentGcodePreviewVertexBudget = safeVertexBudget
        currentGcodeLayerMin = minLayer
        currentGcodeLayerMax = maxLayer
        currentGcodeLayerReloadToken = reloadToken
        if (sourceChanged || reloadChanged) {
            onRenderReady?.invoke(false)
        }
        renderThread?.setGcodePreviewSourceAndLayerRange(
            safeEngineHandle,
            safePreviewKey,
            safeVertexBudget,
            minLayer,
            maxLayer,
            reloadToken
        )
    }

    internal fun setGcodeLayerRange(minLayer: Long, maxLayer: Long, reloadToken: Long = currentGcodeLayerReloadToken) {
        if (
            currentGcodeLayerMin == minLayer &&
            currentGcodeLayerMax == maxLayer &&
            currentGcodeLayerReloadToken == reloadToken
        ) return
        currentGcodeLayerMin = minLayer
        currentGcodeLayerMax = maxLayer
        currentGcodeLayerReloadToken = reloadToken
        renderThread?.setGcodeLayerRange(minLayer, maxLayer, reloadToken)
    }

    internal fun setGcodePathVisibility(kind: Int, id: Int, visible: Boolean) {
        currentGcodePathVisibility = currentGcodePathVisibility.toMutableMap().apply {
            put(kind to id, visible)
        }
        renderThread?.setGcodePathVisibility(kind, id, visible)
    }

    internal fun setGcodeDisplayMode(mode: GcodePreviewDisplayMode) {
        if (currentGcodeDisplayMode == mode) return
        currentGcodeDisplayMode = mode
        renderThread?.setGcodeDisplayMode(mode)
    }

    internal fun captureCameraState(): ViewerCameraState? =
        renderThread?.cameraState() ?: pendingCameraState

    internal fun paintRayForObject(screenX: Float, screenY: Float, objectId: Long): ViewerPaintRay? =
        renderThread?.paintRayForObject(screenX, screenY, objectId)

    internal fun restoreCameraState(state: ViewerCameraState) {
        pendingCameraState = state
        renderThread?.restoreCameraState(state)
    }

    internal fun resetCameraView() {
        pendingCameraState = null
        renderThread?.resetCameraView()
    }

    internal fun setPrinterBed(bed: PrinterBedSpec) {
        if (currentBed == bed) return
        currentBed = bed
        renderThread?.setPrinterBed(bed)
    }

    internal fun setModelTransform(transform: ViewerModelTransform?) {
        if (currentModelTransform == transform) return
        currentModelTransform = transform
        renderThread?.setModelTransform(transform)
    }

    internal fun setPlateObjects(objects: List<ViewerPlateObject>) {
        val nextSignature = ViewerUpdateDecisions.plateObjectsSignature(objects)
        if (currentPlateObjectsSignature == nextSignature) return
        val uploadSetChanged = !ViewerUpdateDecisions.samePlateObjectUploadSet(currentPlateObjects, objects)
        currentPlateObjects = objects.toList()
        currentPlateObjectsSignature = nextSignature
        if (uploadSetChanged) {
            onRenderReady?.invoke(false)
        }
        renderThread?.setPlateObjects(currentPlateObjects)
    }

    internal fun setPaintSession(session: ViewerPaintSession?) {
        if (session == null) {
            if (currentPaintSession == null) return
            currentPaintSession = null
            currentLivePaintOverlay = ViewerPaintOverlay.Empty
            paintTouchState = PaintTouchState.None
            renderThread?.setPaintSession(null)
            return
        }
        val existing = currentPaintSession
        val preservedOverlay = if (
            existing != null &&
            existing.selectedObjectId == session.selectedObjectId &&
            existing.mode == session.mode &&
            session.overlay.layers.isEmpty() &&
            existing.overlay.layers.isNotEmpty()
        ) {
            existing.overlay
        } else {
            session.overlay
        }
        val nextSession = session.copy(overlay = preservedOverlay)
        if (currentPaintSession == nextSession) {
            replayPaintOverlayToRenderThread()
            return
        }
        currentPaintSession = nextSession
        renderThread?.setPaintSession(nextSession)
    }

    internal fun setCutPlaneSession(session: ViewerCutPlaneSession?) {
        if (currentCutPlaneSession == session) return
        currentCutPlaneSession = session
        renderThread?.setCutPlaneSession(session)
    }

    internal fun setActiveStylusPaintOnly(enabled: Boolean) {
        activeStylusPaintOnly = enabled
    }

    internal fun setPaintOverlay(overlay: ViewerPaintOverlay) {
        val session = currentPaintSession ?: return
        currentPaintSession = session.copy(overlay = overlay)
        renderThread?.setPaintOverlay(overlay)
    }

    internal fun appendPaintOverlay(overlay: ViewerPaintOverlay) {
        if (overlay.layers.isEmpty()) return
        currentPaintSession = currentPaintSession?.let { session ->
            session.copy(overlay = session.overlay.plusReplacing(overlay))
        }
        renderThread?.appendPaintOverlay(overlay)
    }

    internal fun replaceLivePaintOverlay(overlay: ViewerPaintOverlay) {
        currentLivePaintOverlay = overlay
        renderThread?.replaceLivePaintOverlay(overlay)
    }

    internal fun clearLivePaintOverlay() {
        currentLivePaintOverlay = ViewerPaintOverlay.Empty
        renderThread?.clearLivePaintOverlay()
    }

    internal fun promoteLivePaintOverlay() {
        currentPaintSession = currentPaintSession?.let { session ->
            session.copy(overlay = session.overlay.plusReplacing(currentLivePaintOverlay))
        }
        currentLivePaintOverlay = ViewerPaintOverlay.Empty
        renderThread?.promoteLivePaintOverlay()
    }

    internal fun setFailureListener(listener: (ViewerFailure?) -> Unit) {
        onViewerFailure = listener
        renderThread?.currentFailure()?.let(listener)
    }

    internal fun setRenderReadyListener(listener: (Boolean) -> Unit) {
        onRenderReady = listener
    }

    internal fun setPreviewRuntimeMetricsListener(listener: (GcodePreviewRuntimeMetrics) -> Unit) {
        onPreviewRuntimeMetrics = listener
    }

    internal fun setObjectSelectionListener(listener: (Long?) -> Unit) {
        onObjectSelected = listener
    }

    internal fun setCutConnectorPointListener(listener: (ViewerCutConnectorPoint) -> Unit) {
        onCutConnectorPointAdded = listener
    }

    internal fun setObjectHitSelectionListener(listener: (ViewerPickHit?) -> Boolean) {
        onObjectHitSelected = listener
    }

    internal fun setObjectDragListener(
        movableIds: Set<Long>,
        listener: (Long, Float, Float, Boolean) -> Unit
    ) {
        movableObjectIds = movableIds
        onObjectDrag = listener
    }

    internal fun setPaintStrokeListener(
        onBegin: (ViewerPaintStrokePoint) -> Unit,
        onMove: (ViewerPaintStrokePoint) -> Unit,
        onEnd: (Boolean) -> Unit
    ) {
        onPaintStrokeBegin = onBegin
        onPaintStrokeMove = onMove
        onPaintStrokeEnd = onEnd
    }

    internal fun setPaintHitTestListener(listener: (Float, Float) -> Boolean?) {
        // Kept for callers that still wire the old API during the native paint transition.
        // Paint acceptance now happens asynchronously in stroke begin instead of input dispatch.
    }

    internal fun setViewerAppearance(darkTheme: Boolean, accentColor: Int, worldColor: Int? = null) {
        if (
            currentAppearance.darkTheme == darkTheme &&
            currentAppearance.accentColor == accentColor &&
            currentAppearance.worldColor == worldColor
        ) return
        currentAppearance = ViewerAppearance(darkTheme = darkTheme, accentColor = accentColor, worldColor = worldColor)
        renderThread?.setViewerAppearance(darkTheme = darkTheme, accentColor = accentColor, worldColor = worldColor)
    }

    internal fun captureCurrentFrame(onResult: (Bitmap?) -> Unit) {
        val surface = holder.surface
        if (!surface.isValid || width <= 0 || height <= 0) {
            onResult(null)
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(this, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                onResult(bitmap)
            } else {
                bitmap.recycle()
                onResult(null)
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureRenderThread()
    }

    override fun onDetachedFromWindow() {
        teardownRenderThread()
        super.onDetachedFromWindow()
    }

    internal fun onResume() {
        resumed = true
        ensureRenderThread()
        renderThread?.setPaused(false)
    }

    internal fun onPause() {
        resumed = false
        pendingCameraState = renderThread?.cameraState() ?: pendingCameraState
        teardownRenderThread()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ensureRenderThread()
        renderThread?.bindSurface(holder.surface, width.coerceAtLeast(1), height.coerceAtLeast(1))
        replayPaintOverlayToRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        ensureRenderThread()
        renderThread?.bindSurface(holder.surface, width.coerceAtLeast(1), height.coerceAtLeast(1))
        replayPaintOverlayToRenderThread()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.unbindSurface()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                paintGestureDownUptimeMs = SystemClock.uptimeMillis()
                lastTouchX = event.x
                lastTouchY = event.y
                resetPaintDispatchPosition()
                dragging = false
                suppressTapSelection = false
                twoFingerGesture = TwoFingerGesture.None
                paintTouchState = PaintTouchState.None
                objectDragId = null
                objectDragLastBedPoint = null
                if (currentPaintSession != null) {
                    val paintOnlyStylusDown = activeStylusPaintOnly && event.isActiveStylusPointer(event.actionIndex)
                    if (activeStylusPaintOnly && !paintOnlyStylusDown) {
                        paintTouchState = PaintTouchState.CameraGesture
                        suppressTapSelection = true
                    } else if (!paintOnlyStylusDown && !canStartPaintGesture(event.x, event.y)) {
                        paintTouchState = if (paintOnlyStylusDown) {
                            PaintTouchState.StylusNoPaint
                        } else {
                            PaintTouchState.CameraGesture
                        }
                        suppressTapSelection = true
                    } else {
                        if (currentPaintSession?.brushShape?.paintsOnTap() == true) {
                            paintTouchState = PaintTouchState.Painting
                            dispatchPaintBegin(event.x, event.y)
                        } else {
                            paintTouchState = PaintTouchState.PotentialPaint
                        }
                        suppressTapSelection = true
                    }
                } else if (movableObjectIds.isNotEmpty()) {
                    val pickedMovable = renderThread
                        ?.pickObjectIdsSync(event.x, event.y)
                        ?.firstOrNull { it in movableObjectIds }
                    if (pickedMovable != null) {
                        objectDragId = pickedMovable
                        objectDragLastBedPoint = renderThread?.bedPointForScreen(event.x, event.y)
                        suppressTapSelection = true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    if (currentPaintSession != null && activeStylusPaintOnly && event.hasActiveStylusPointer()) {
                        if (paintTouchState == PaintTouchState.Painting) {
                            onPaintStrokeEnd?.invoke(false)
                            renderThread?.setPaintCursor(lastTouchX, lastTouchY, false)
                            resetPaintDispatchPosition()
                        }
                        paintTouchState = PaintTouchState.StylusNoPaint
                        dragging = false
                        suppressTapSelection = true
                        return true
                    }
                    objectDragId?.let { draggingObjectId ->
                        onObjectDrag?.invoke(draggingObjectId, 0f, 0f, true)
                    }
                    objectDragId = null
                    objectDragLastBedPoint = null
                    if (paintTouchState == PaintTouchState.Painting) {
                        onPaintStrokeEnd?.invoke(false)
                        renderThread?.setPaintCursor(lastTouchX, lastTouchY, false)
                        resetPaintDispatchPosition()
                        suppressTapSelection = true
                    }
                    beginTwoFingerGesture(event)
                    twoFingerTapCandidate = event.pointerCount == 2
                    twoFingerTapStartedAtMs = SystemClock.uptimeMillis()
                    dragging = true
                    suppressTapSelection = true
                    if (currentPaintSession != null) {
                        paintTouchState = PaintTouchState.MultiTouchCamera
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (paintTouchState == PaintTouchState.StylusNoPaint) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                if (paintTouchState == PaintTouchState.Painting) {
                    if (currentPaintSession?.brushShape?.paintsOnTap() != true) {
                        dispatchHistoricalPaintMoves(event)
                        dispatchPaintMoveIfFarEnough(event.x, event.y, force = true)
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                objectDragId?.let { draggingObjectId ->
                    val nextBedPoint = renderThread?.bedPointForScreen(event.x, event.y)
                    val lastBedPoint = objectDragLastBedPoint
                    if (nextBedPoint != null && lastBedPoint != null) {
                        val deltaX = nextBedPoint.xMm - lastBedPoint.xMm
                        val deltaY = nextBedPoint.yMm - lastBedPoint.yMm
                        if (
                            deltaX.isFinite() &&
                            deltaY.isFinite() &&
                            deltaX * deltaX + deltaY * deltaY >= objectDragMinBedDistanceMm * objectDragMinBedDistanceMm
                        ) {
                            onObjectDrag?.invoke(draggingObjectId, deltaX, deltaY, false)
                            objectDragLastBedPoint = nextBedPoint
                        }
                    }
                    dragging = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                if (event.pointerCount >= 2) {
                    updateTwoFingerGesture(event)
                } else {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    val totalDragX = event.x - downTouchX
                    val totalDragY = event.y - downTouchY
                    if (dragging || abs(totalDragX) > touchSlop || abs(totalDragY) > touchSlop) {
                        dragging = true
                        if (paintTouchState == PaintTouchState.PotentialPaint) {
                            if (activeStylusPaintOnly && !canStartPaintGesture(event.x, event.y)) {
                                renderThread?.setPaintCursor(event.x, event.y, true)
                                lastTouchX = event.x
                                lastTouchY = event.y
                                return true
                            }
                            paintTouchState = PaintTouchState.Painting
                            val beginX = if (activeStylusPaintOnly) event.x else downTouchX
                            val beginY = if (activeStylusPaintOnly) event.y else downTouchY
                            dispatchPaintBegin(beginX, beginY)
                            if (!activeStylusPaintOnly) {
                                dispatchHistoricalPaintMoves(event)
                            }
                            dispatchPaintMoveIfFarEnough(event.x, event.y, force = true)
                        } else {
                            renderThread?.orbitBy(deltaX, deltaY)
                        }
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount - 1 < 2) {
                    if (consumeTwoFingerTapIfNeeded(event)) {
                        resetCameraView()
                    }
                    val survivorIndex = if (event.actionIndex == 0) 1 else 0
                    if (survivorIndex in 0 until event.pointerCount) {
                        downTouchX = event.getX(survivorIndex)
                        downTouchY = event.getY(survivorIndex)
                        lastTouchX = event.getX(survivorIndex)
                        lastTouchY = event.getY(survivorIndex)
                        dragging = false
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (paintTouchState == PaintTouchState.Painting) {
                    dispatchPaintMoveIfFarEnough(event.x, event.y, force = true)
                    onPaintStrokeEnd?.invoke(event.actionMasked == MotionEvent.ACTION_UP)
                    renderThread?.setPaintCursor(event.x, event.y, false)
                } else if (paintTouchState == PaintTouchState.StylusNoPaint) {
                    renderThread?.setPaintCursor(event.x, event.y, false)
                } else if (paintTouchState == PaintTouchState.PotentialPaint && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (currentPaintSession?.brushShape?.paintsOnTap() == true) {
                        dispatchPaintBegin(event.x, event.y)
                        onPaintStrokeEnd?.invoke(true)
                    }
                    renderThread?.setPaintCursor(event.x, event.y, false)
                } else if (
                    event.actionMasked == MotionEvent.ACTION_UP &&
                    !dragging &&
                    !suppressTapSelection &&
                    currentCutPlaneSession?.connectorsEditing == true
                ) {
                    performClick()
                    renderThread?.cutPlanePointForScreen(event.x, event.y)?.let { point ->
                        post { onCutConnectorPointAdded?.invoke(point) }
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP && !dragging && !suppressTapSelection) {
                    performClick()
                    val tapX = event.x
                    val tapY = event.y
                    renderThread?.pickObjectHits(tapX, tapY) { hits ->
                        val candidates = hits.map { it.objectId }.distinct().ifEmpty {
                            renderThread?.pickObjectIdsSync(tapX, tapY).orEmpty()
                        }
                        val objectId = chooseObjectFromCandidates(tapX, tapY, candidates)
                        val hit = objectId?.let { selectedId -> hits.firstOrNull { it.objectId == selectedId } }
                        post {
                            val consumed = onObjectHitSelected?.invoke(hit) == true
                            if (!consumed) {
                                onObjectSelected?.invoke(objectId)
                            }
                        }
                    }
                }
                objectDragId?.let { draggingObjectId ->
                    onObjectDrag?.invoke(draggingObjectId, 0f, 0f, true)
                }
                objectDragId = null
                objectDragLastBedPoint = null
                dragging = false
                suppressTapSelection = false
                twoFingerGesture = TwoFingerGesture.None
                twoFingerTapCandidate = false
                paintTouchState = PaintTouchState.None
                resetPaintDispatchPosition()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun ensureRenderThread() {
        if (!isAttachedToWindow) return
        if (renderThread != null) return
        val thread = WorkspaceRenderThread(
            context = context.applicationContext,
            onFailure = { failure -> post { onViewerFailure?.invoke(failure) } },
            onRenderReady = { ready -> post { onRenderReady?.invoke(ready) } },
            onPreviewRuntimeMetrics = { metrics -> post { onPreviewRuntimeMetrics?.invoke(metrics) } }
        )
        renderThread = thread
        thread.start()
        thread.setPaused(!resumed)
        thread.setPrinterBed(currentBed)
        thread.setViewerAppearance(
            darkTheme = currentAppearance.darkTheme,
            accentColor = currentAppearance.accentColor,
            worldColor = currentAppearance.worldColor
        )
        thread.setMesh(currentMesh)
        thread.setPlateObjects(currentPlateObjects)
        thread.setPaintSession(currentPaintSession)
        thread.setCutPlaneSession(currentCutPlaneSession)
        replayPaintOverlayToRenderThread()
        thread.setModelTransform(currentModelTransform)
        thread.setGcodePreviewSourceAndLayerRange(
            currentGcodePreviewEngineHandle,
            currentGcodePreviewKey,
            currentGcodePreviewVertexBudget,
            currentGcodeLayerMin,
            currentGcodeLayerMax,
            currentGcodeLayerReloadToken
        )
        currentGcodePathVisibility.forEach { (key, visible) ->
            thread.setGcodePathVisibility(kind = key.first, id = key.second, visible = visible)
        }
        thread.setGcodeDisplayMode(currentGcodeDisplayMode)
        pendingCameraState?.let(thread::restoreCameraState)
        if (holder.surface?.isValid == true) {
            thread.bindSurface(holder.surface, width.coerceAtLeast(1), height.coerceAtLeast(1))
            replayPaintOverlayToRenderThread()
        }
    }

    private fun replayPaintOverlayToRenderThread() {
        val session = currentPaintSession ?: return
        val overlay = session.overlay.plusReplacing(currentLivePaintOverlay)
        val replaySession = session.copy(overlay = overlay)
        currentPaintSession = replaySession
        renderThread?.setPaintSession(replaySession)
        if (overlay.layers.isNotEmpty()) {
            renderThread?.setPaintOverlay(overlay)
        }
    }

    private fun teardownRenderThread() {
        renderThread?.requestExitAndWait()
        renderThread = null
    }

    private fun ViewerPaintOverlay.plusReplacing(other: ViewerPaintOverlay): ViewerPaintOverlay {
        if (other.layers.isEmpty()) return this
        if (layers.isEmpty()) return ViewerPaintOverlay(other.layers.filterNot { it.deleteOnly })
        val byId = LinkedHashMap<String, ViewerPaintOverlayLayer>(layers.size + other.layers.size)
        val replacementSourceKeys = other.layers.mapNotNullTo(mutableSetOf()) { it.id.overlaySourceKey() }
        layers.forEach { layer ->
            if (!layer.deleteOnly && layer.id.overlaySourceKey() !in replacementSourceKeys) {
                byId[layer.id] = layer
            }
        }
        other.layers.forEach { layer ->
            if (layer.deleteOnly) {
                byId.remove(layer.id)
                layer.id.overlaySourceKey()?.let { sourceKey ->
                    byId.keys
                        .filter { it.overlaySourceKey() == sourceKey }
                        .forEach { byId.remove(it) }
                }
            } else {
                byId[layer.id] = layer
            }
        }
        return ViewerPaintOverlay(byId.values.toList())
    }

    private fun String.overlaySourceKey(): String? {
        val separator = indexOf(':')
        return if (separator > 0) substring(0, separator) else null
    }

    private fun centroidX(event: MotionEvent): Float {
        var total = 0f
        repeat(event.pointerCount) { total += event.getX(it) }
        return total / event.pointerCount.toFloat()
    }

    private fun centroidY(event: MotionEvent): Float {
        var total = 0f
        repeat(event.pointerCount) { total += event.getY(it) }
        return total / event.pointerCount.toFloat()
    }

    private fun beginTwoFingerGesture(event: MotionEvent) {
        lastCentroidX = centroidX(event)
        lastCentroidY = centroidY(event)
        twoFingerStartCentroidX = lastCentroidX
        twoFingerStartCentroidY = lastCentroidY
        twoFingerStartDistance = pointerDistance(event)
        lastTwoFingerDistance = twoFingerStartDistance
        twoFingerGesture = TwoFingerGesture.Undecided
    }

    private fun updateTwoFingerGesture(event: MotionEvent) {
        if (twoFingerGesture == TwoFingerGesture.None || event.pointerCount < 2) {
            beginTwoFingerGesture(event)
            return
        }
        val nextCentroidX = centroidX(event)
        val nextCentroidY = centroidY(event)
        val nextDistance = pointerDistance(event)
        val panFromStart = hypot(nextCentroidX - twoFingerStartCentroidX, nextCentroidY - twoFingerStartCentroidY)
        val pinchFromStart = abs(nextDistance - twoFingerStartDistance)
        if (twoFingerGesture == TwoFingerGesture.Undecided) {
            twoFingerGesture = decideTwoFingerGesture(panFromStart, pinchFromStart)
        }
        when (twoFingerGesture) {
            TwoFingerGesture.Pan -> {
                renderThread?.panBy(nextCentroidX - lastCentroidX, nextCentroidY - lastCentroidY)
            }
            TwoFingerGesture.Zoom -> {
                val scaleFactor = if (lastTwoFingerDistance > 1f) nextDistance / lastTwoFingerDistance else 1f
                if (scaleFactor.isFinite() && scaleFactor > 0f) {
                    renderThread?.zoomBy(scaleFactor)
                }
            }
            TwoFingerGesture.None,
            TwoFingerGesture.Undecided -> Unit
        }
        lastCentroidX = nextCentroidX
        lastCentroidY = nextCentroidY
        lastTwoFingerDistance = nextDistance
        if (
            twoFingerTapCandidate &&
            (panFromStart > TWO_FINGER_TAP_MOVE_LIMIT_PX || pinchFromStart > TWO_FINGER_TAP_MOVE_LIMIT_PX)
        ) {
            twoFingerTapCandidate = false
        }
    }

    private fun decideTwoFingerGesture(panFromStart: Float, pinchFromStart: Float): TwoFingerGesture {
        val deadZone = max(touchSlop, TWO_FINGER_DEAD_ZONE_PX)
        if (panFromStart < deadZone && pinchFromStart < deadZone) {
            return TwoFingerGesture.Undecided
        }
        return if (pinchFromStart >= panFromStart * ZOOM_DOMINANCE_RATIO) {
            TwoFingerGesture.Zoom
        } else {
            TwoFingerGesture.Pan
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    }

    private fun consumeTwoFingerTapIfNeeded(event: MotionEvent): Boolean {
        if (!twoFingerTapCandidate || event.pointerCount != 2) {
            twoFingerTapCandidate = false
            return false
        }
        twoFingerTapCandidate = false
        val now = SystemClock.uptimeMillis()
        if (now - twoFingerTapStartedAtMs > TWO_FINGER_TAP_TIMEOUT_MS) {
            return false
        }
        val tapX = centroidX(event)
        val tapY = centroidY(event)
        val isDoubleTap =
            now - lastTwoFingerTapAtMs <= TWO_FINGER_DOUBLE_TAP_TIMEOUT_MS &&
                abs(tapX - lastTwoFingerTapX) <= TWO_FINGER_DOUBLE_TAP_RADIUS_PX &&
                abs(tapY - lastTwoFingerTapY) <= TWO_FINGER_DOUBLE_TAP_RADIUS_PX
        lastTwoFingerTapX = tapX
        lastTwoFingerTapY = tapY
        lastTwoFingerTapAtMs = now
        return isDoubleTap
    }

    private fun chooseObjectFromCandidates(screenX: Float, screenY: Float, candidates: List<Long>): Long? {
        if (candidates.isEmpty()) {
            lastPickCandidates = emptyList()
            lastPickIndex = -1
            return null
        }
        val now = SystemClock.uptimeMillis()
        val sameTapArea =
            abs(screenX - lastPickX) <= CYCLE_TAP_RADIUS_PX &&
                abs(screenY - lastPickY) <= CYCLE_TAP_RADIUS_PX
        val sameCandidates = candidates == lastPickCandidates
        val shouldCycle =
            candidates.size > 1 &&
                sameTapArea &&
                sameCandidates &&
                now - lastPickAtMs <= CYCLE_TAP_TIMEOUT_MS
        val nextIndex = if (shouldCycle) {
            (lastPickIndex + 1).floorMod(candidates.size)
        } else {
            0
        }
        lastPickX = screenX
        lastPickY = screenY
        lastPickAtMs = now
        lastPickCandidates = candidates
        lastPickIndex = nextIndex
        return candidates[nextIndex]
    }

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    private fun canStartPaintGesture(screenX: Float, screenY: Float): Boolean {
        val session = currentPaintSession ?: return false
        val hits = renderThread?.pickObjectHitsSync(screenX, screenY).orEmpty()
        return hits.any { it.objectId == session.selectedObjectId }
    }

    private fun dispatchPaintBegin(screenX: Float, screenY: Float) {
        lastPaintDispatchX = screenX
        lastPaintDispatchY = screenY
        onPaintStrokeBegin?.invoke(ViewerPaintStrokePoint(screenX, screenY, null, paintGestureDownUptimeMs))
        renderThread?.setPaintCursor(screenX, screenY, true)
    }

    private fun dispatchPaintMove(screenX: Float, screenY: Float) {
        lastPaintDispatchX = screenX
        lastPaintDispatchY = screenY
        onPaintStrokeMove?.invoke(ViewerPaintStrokePoint(screenX, screenY, null, paintGestureDownUptimeMs))
        renderThread?.setPaintCursor(screenX, screenY, true)
    }

    private fun dispatchPaintMoveIfFarEnough(screenX: Float, screenY: Float, force: Boolean = false) {
        if (!force && lastPaintDispatchX.isFinite() && lastPaintDispatchY.isFinite()) {
            val dx = screenX - lastPaintDispatchX
            val dy = screenY - lastPaintDispatchY
            if (dx * dx + dy * dy < paintMoveMinScreenDistancePx * paintMoveMinScreenDistancePx) {
                renderThread?.setPaintCursor(screenX, screenY, true)
                return
            }
        }
        dispatchPaintMove(screenX, screenY)
    }

    private fun dispatchHistoricalPaintMoves(event: MotionEvent) {
        val historySize = event.historySize
        if (historySize <= 0) return
        for (index in 0 until historySize) {
            dispatchPaintMoveIfFarEnough(event.getHistoricalX(index), event.getHistoricalY(index))
        }
    }

    private fun resetPaintDispatchPosition() {
        lastPaintDispatchX = Float.NaN
        lastPaintDispatchY = Float.NaN
    }

    private enum class TwoFingerGesture {
        None,
        Undecided,
        Pan,
        Zoom
    }

    private enum class PaintTouchState {
        None,
        PotentialPaint,
        Painting,
        CameraGesture,
        MultiTouchCamera,
        StylusNoPaint
    }

    private fun ViewerPaintBrushShape.paintsOnTap(): Boolean =
        this == ViewerPaintBrushShape.Triangle ||
            this == ViewerPaintBrushShape.HeightRange ||
            this == ViewerPaintBrushShape.Fill ||
            this == ViewerPaintBrushShape.GapFill

    private companion object {
        private const val CYCLE_TAP_RADIUS_PX = 34f
        private const val CYCLE_TAP_TIMEOUT_MS = 1_000L
        private const val TWO_FINGER_TAP_MOVE_LIMIT_PX = 10f
        private const val TWO_FINGER_TAP_TIMEOUT_MS = 180L
        private const val TWO_FINGER_DOUBLE_TAP_RADIUS_PX = 54f
        private const val TWO_FINGER_DOUBLE_TAP_TIMEOUT_MS = 320L
        private const val TWO_FINGER_DEAD_ZONE_PX = 7f
        private const val ZOOM_DOMINANCE_RATIO = 1.18f
    }
}

private fun MotionEvent.isActiveStylusPointer(pointerIndex: Int): Boolean {
    if (pointerIndex !in 0 until pointerCount) return false
    return when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS,
        MotionEvent.TOOL_TYPE_ERASER -> true
        else -> false
    }
}

private fun MotionEvent.hasActiveStylusPointer(): Boolean {
    for (index in 0 until pointerCount) {
        if (isActiveStylusPointer(index)) return true
    }
    return false
}
