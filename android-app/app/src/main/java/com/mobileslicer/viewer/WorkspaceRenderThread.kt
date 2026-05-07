package com.mobileslicer.viewer

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin

private const val PaintOverlaySurfaceOffsetMm = 0.0f
private const val PaintLiveOverlaySurfaceOffsetMm = 0.0f
private const val PaintOverlayUploadVertexBudgetPerFrame = 45_000
private const val PaintOverlayReplacementUploadVertexBudgetPerFrame = 45_000
private const val PaintOverlayUploadLayerBudgetPerFrame = 32
private const val PaintOverlayUploadPerfLogVertexThreshold = 120_000
private const val PaintOverlayDrawPerfLogMs = 8L
private const val PaintOverlayDrawPerfLogMinIntervalMs = 500L
private const val PaintPreviewTrailMaxPoints = 320
private const val PaintPreviewTrailMinDistancePx = 4f
private const val PaintPreviewCircleSegments = 12

private data class PaintCursorPoint(val x: Float, val y: Float)

internal class WorkspaceRenderThread(
    private val context: Context,
    private val onFailure: (ViewerFailure?) -> Unit,
    private val onRenderReady: (Boolean) -> Unit,
    private val onPreviewRuntimeMetrics: (GcodePreviewRuntimeMetrics) -> Unit = {}
) : Thread("MobileSlicerWorkspaceRenderer") {
    private val stateLock = Object()
    private var shouldExit = false
    private var paused = false
    private var dirty = true

    private var boundSurface: Surface? = null
    private var viewportWidth = 1
    private var viewportHeight = 1

    private var pendingMesh: StlMesh? = null
    private var pendingPlateObjects: List<ViewerPlateObject> = emptyList()
    private var pendingPlateObjectsSignature = ViewerUpdateDecisions.plateObjectsSignature(pendingPlateObjects)
    private var pendingGcodePreviewEngineHandle = 0L
    private var pendingGcodePreviewKey = 0L
    private var pendingGcodePreviewVertexBudget = GcodePreviewPerformanceMode.HARD_VERTEX_CEILING
    private var pendingGcodeLayerMin = 0L
    private var pendingGcodeLayerMax = Long.MAX_VALUE
    private var pendingGcodeLayerReloadToken = 0L
    private var pendingGcodePreviewQueuedAtMs = 0L
    private var pendingGcodePathVisibility: Map<Pair<Int, Int>, Boolean> = emptyMap()
    private var pendingGcodeDisplayMode = GcodePreviewDisplayMode.Auto
    private var pendingCutPlaneSession: ViewerCutPlaneSession? = null
    private var pendingCameraState: ViewerCameraState? = null
    private var pendingPaintSession: ViewerPaintSession? = null
    private var pendingPaintOverlayReplace: ViewerPaintOverlay? = null
    private var pendingPaintOverlayAppend: ViewerPaintOverlay? = null
    private var pendingPaintOverlayClearLive = false
    private var pendingPaintOverlayPromoteLive = false
    private var pendingPaintCursorX = Float.NaN
    private var pendingPaintCursorY = Float.NaN
    private var pendingPaintCursorVisible = false
    private val pendingPaintCursorTrail = ArrayDeque<PaintCursorPoint>()
    private var pendingBed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
    private var pendingModelTransform: ViewerModelTransform? = null
    private var pendingAppearance = ViewerAppearance(
        darkTheme = true,
        accentColor = Color.rgb(143, 193, 255)
    )
    private var meshVersion = 0L
    private var plateObjectsVersion = 0L
    private var plateObjectTransformVersion = 0L
    private var plateObjectSelectionVersion = 0L
    private var gcodePreviewVersion = 0L
    private var gcodeLayerRangeVersion = 0L
    private var gcodePathVisibilityVersion = 0L
    private var gcodeDisplayModeVersion = 0L
    private var cutPlaneVersion = 0L
    private var cameraStateVersion = 0L
    private var bedVersion = 0L
    private var modelTransformVersion = 0L
    private var appearanceVersion = 0L
    private var paintSessionVersion = 0L
    private var paintOverlayReplaceVersion = 0L
    private var paintOverlayAppendVersion = 0L
    private var paintCursorVersion = 0L
    private var renderedReadyMeshVersion = -1L
    private var firstFrameCleared = false

    private val camera = ViewerCamera(pendingBed)
    private val objectUploadManager = WorkspaceObjectUploadManager()
    private val paintOverlayUploadManager = PaintOverlayUploadManager(::deleteTriangleUpload)

    private var failure: ViewerFailure? = null

    private val egl = WorkspaceEglSession()

    private var triangleProgram = 0
    private var triangleHandles: TriangleProgramHandles? = null
    private var textureProgram = 0
    private var textureHandles: TextureProgramHandles? = null
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val gcodeViewMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val identityModelMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var activeBed = pendingBed
    private var activeModelTransform: ViewerModelTransform? = null
    private var activePlateObjects: List<ViewerPlateObject> = emptyList()
    private var activeAppearance = pendingAppearance
    private var activePaintSession: ViewerPaintSession? = null
    private var activePaintCursorX = Float.NaN
    private var activePaintCursorY = Float.NaN
    private var activePaintCursorVisible = false
    private var activePaintCursorTrail: List<PaintCursorPoint> = emptyList()
    private var activeMesh: StlMesh? = null
    private var activeGcodePreviewEngineHandle = 0L
    private var activeGcodePreviewKey = 0L
    private var activeGcodePreviewVertexBudget = GcodePreviewPerformanceMode.HARD_VERTEX_CEILING
    private val gcodePreviewRenderer = GcodePreviewRenderer()
    private var activeGcodeLayerMin = 0L
    private var activeGcodeLayerMax = Long.MAX_VALUE
    private var activeGcodePreviewQueuedAtMs = 0L
    private var activeGcodePathVisibility: Map<Pair<Int, Int>, Boolean> = emptyMap()
    private var activeGcodeDisplayMode = GcodePreviewDisplayMode.Auto
    private var activeCutPlaneSession: ViewerCutPlaneSession? = null
    private var appliedMeshVersion = -1L
    private var appliedPlateObjectsVersion = -1L
    private var appliedPlateObjectTransformVersion = -1L
    private var appliedPlateObjectSelectionVersion = -1L
    private var appliedGcodePreviewVersion = -1L
    private var appliedGcodeLayerRangeVersion = -1L
    private var appliedGcodePathVisibilityVersion = -1L
    private var appliedGcodeDisplayModeVersion = -1L
    private var appliedCutPlaneVersion = -1L
    private var appliedCameraStateVersion = -1L
    private var appliedBedVersion = -1L
    private var appliedModelTransformVersion = -1L
    private var appliedAppearanceVersion = -1L
    private var appliedPaintSessionVersion = -1L
    private var appliedPaintOverlayReplaceVersion = -1L
    private var appliedPaintOverlayAppendVersion = -1L
    private var appliedPaintCursorVersion = -1L
    private var appliedPaintSessionOverlayKey: Pair<Long, ViewerPaintMode>? = null

    private var bedSurfaceUpload: TriangleUpload? = null
    private var bedModelUpload: TriangleUpload? = null
    private var bedModelUndersideUpload: TriangleUpload? = null
    private var bedTextureUpload: TextureUpload? = null
    private var bedGridUpload: TriangleUpload? = null
    private var bedGridBoldUpload: TriangleUpload? = null
    private var bedBorderUpload: TriangleUpload? = null
    private var bedWallUpload: TriangleUpload? = null
    private var modelUpload: TriangleUpload? = null
    private var cutPlaneUpload: TriangleUpload? = null
    private var cutConnectorUpload: TriangleUpload? = null
    private var cutFaceFillUpload: TriangleUpload? = null
    private var cutClippedObjectUpload: TriangleUpload? = null
    private var cutPlaneSessionUploadKey: ViewerCutPlaneSession? = null

    // Paint overlay GL ownership lives in PaintOverlayUploadManager.
    private var modelPlacementX = 0f
    private var modelPlacementY = 0f
    private var modelPlacementZ = 0f
    private var modelRotationXDegrees = 0f
    private var modelRotationYDegrees = 0f
    private var modelRotationZDegrees = 0f
    private var modelUniformScale = 1f
    private var viewerColors = buildViewerColors(activeAppearance)
    private var activePreviewNativeLoadMs = 0L
    private var activePreviewNativeSelectedParseMs = 0L
    private var activePreviewNativeLibvgcodeLoadMs = 0L
    private var activePreviewNativeTotalLoadMs = 0L
    private var activePreviewNativeLoadedVertices = 0L
    private var activePreviewNativeCachedVertices = 0L
    private var activePreviewNativeCachedLayers = 0L
    private var activePreviewNativeCacheHit = 0L
    private var activePreviewNativeCacheBuilt = 0L
    private var activePreviewFirstFrameMs = -1L
    private var activePreviewRenderedFrames = 0L
    private var activePreviewSlowFrames = 0L
    private var activePreviewLastMetricsReportAtMs = 0L
    private var paintOverlayLastDrawPerfLogAtMs = 0L

    fun setMesh(mesh: StlMesh?) {
        synchronized(stateLock) {
            if (pendingMesh === mesh) return
            pendingMesh = mesh
            meshVersion++
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPlateObjects(objects: List<ViewerPlateObject>) {
        synchronized(stateLock) {
            val nextObjects = objects.toList()
            val nextSignature = ViewerUpdateDecisions.plateObjectsSignature(nextObjects)
            if (pendingPlateObjectsSignature == nextSignature) return
            val updateDecision = ViewerUpdateDecisions.plateObjectUpdateDecision(pendingPlateObjects, nextObjects)
            pendingPlateObjects = nextObjects
            pendingPlateObjectsSignature = nextSignature
            if (updateDecision.geometryChanged) {
                plateObjectsVersion++
                firstFrameCleared = false
            }
            if (updateDecision.transformChanged) {
                plateObjectTransformVersion++
            }
            if (updateDecision.selectionChanged) {
                plateObjectSelectionVersion++
            }
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            if (updateDecision.geometryChanged && nextObjects.isEmpty()) {
                objectUploadManager.plateSceneCameraInitialized = false
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setGcodePreviewSource(
        engineHandle: Long,
        previewKey: Long,
        vertexBudget: Long = GcodePreviewPerformanceMode.HARD_VERTEX_CEILING
    ) {
        synchronized(stateLock) {
            val source = ViewerUpdateDecisions.normalizeGcodePreviewSource(engineHandle, previewKey)
            val safeEngineHandle = source.engineHandle
            val safePreviewKey = source.previewKey
            val safeVertexBudget = vertexBudget.coerceIn(1L, GcodePreviewPerformanceMode.HARD_VERTEX_CEILING)
            if (
                pendingGcodePreviewEngineHandle == safeEngineHandle &&
                pendingGcodePreviewKey == safePreviewKey &&
                pendingGcodePreviewVertexBudget == safeVertexBudget
            ) return
            val generationEngineHandle = if (safeEngineHandle != 0L) {
                safeEngineHandle
            } else {
                pendingGcodePreviewEngineHandle
            }
            pendingGcodePreviewEngineHandle = safeEngineHandle
            pendingGcodePreviewKey = safePreviewKey
            pendingGcodePreviewVertexBudget = safeVertexBudget
            gcodePreviewVersion++
            publishGcodePreviewGeneration(generationEngineHandle, gcodePreviewVersion)
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            firstFrameCleared = false
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setGcodePreviewSourceAndLayerRange(
        engineHandle: Long,
        previewKey: Long,
        vertexBudget: Long,
        minLayer: Long,
        maxLayer: Long,
        reloadToken: Long
    ) {
        synchronized(stateLock) {
            val source = ViewerUpdateDecisions.normalizeGcodePreviewSource(engineHandle, previewKey)
            val safeEngineHandle = source.engineHandle
            val safePreviewKey = source.previewKey
            val safeVertexBudget = vertexBudget.coerceIn(1L, GcodePreviewPerformanceMode.HARD_VERTEX_CEILING)
            val previousEngineHandle = pendingGcodePreviewEngineHandle
            val sourceChanged =
                pendingGcodePreviewEngineHandle != safeEngineHandle ||
                    pendingGcodePreviewKey != safePreviewKey ||
                    pendingGcodePreviewVertexBudget != safeVertexBudget
            val rangeDecision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
                previousMinLayer = pendingGcodeLayerMin,
                previousMaxLayer = pendingGcodeLayerMax,
                previousReloadToken = pendingGcodeLayerReloadToken,
                nextMinLayer = minLayer,
                nextMaxLayer = maxLayer,
                nextReloadToken = reloadToken,
                activeEngineHandle = safeEngineHandle,
                activePreviewKey = safePreviewKey
            )
            if (!sourceChanged && !rangeDecision.rangeChanged && !rangeDecision.reloadChanged) return

            pendingGcodePreviewEngineHandle = safeEngineHandle
            pendingGcodePreviewKey = safePreviewKey
            pendingGcodePreviewVertexBudget = safeVertexBudget
            pendingGcodeLayerMin = minLayer
            pendingGcodeLayerMax = maxLayer
            pendingGcodeLayerReloadToken = reloadToken

            if (sourceChanged || rangeDecision.shouldReloadPreview) {
                gcodePreviewVersion++
                publishGcodePreviewGeneration(
                    if (safeEngineHandle != 0L) safeEngineHandle else previousEngineHandle,
                    gcodePreviewVersion
                )
                pendingGcodePreviewQueuedAtMs = SystemClock.elapsedRealtime()
                firstFrameCleared = false
            }
            if (rangeDecision.rangeChanged) {
                gcodeLayerRangeVersion++
            }
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setGcodeLayerRange(minLayer: Long, maxLayer: Long, reloadToken: Long) {
        synchronized(stateLock) {
            val updateDecision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
                previousMinLayer = pendingGcodeLayerMin,
                previousMaxLayer = pendingGcodeLayerMax,
                previousReloadToken = pendingGcodeLayerReloadToken,
                nextMinLayer = minLayer,
                nextMaxLayer = maxLayer,
                nextReloadToken = reloadToken,
                activeEngineHandle = pendingGcodePreviewEngineHandle,
                activePreviewKey = pendingGcodePreviewKey
            )
            if (!updateDecision.rangeChanged && !updateDecision.reloadChanged) return
            pendingGcodeLayerMin = minLayer
            pendingGcodeLayerMax = maxLayer
            pendingGcodeLayerReloadToken = reloadToken
            if (updateDecision.rangeChanged) {
                gcodeLayerRangeVersion++
            }
            if (updateDecision.shouldReloadPreview) {
                gcodePreviewVersion++
                publishGcodePreviewGeneration(pendingGcodePreviewEngineHandle, gcodePreviewVersion)
                pendingGcodePreviewQueuedAtMs = SystemClock.elapsedRealtime()
                firstFrameCleared = false
            }
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setGcodePathVisibility(kind: Int, id: Int, visible: Boolean) {
        synchronized(stateLock) {
            val key = kind to id
            if (pendingGcodePathVisibility[key] == visible) return
            pendingGcodePathVisibility = pendingGcodePathVisibility.toMutableMap().apply {
                put(key, visible)
            }
            gcodePathVisibilityVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setGcodeDisplayMode(mode: GcodePreviewDisplayMode) {
        synchronized(stateLock) {
            if (pendingGcodeDisplayMode == mode) return
            pendingGcodeDisplayMode = mode
            gcodeDisplayModeVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun cameraState(): ViewerCameraState =
        synchronized(stateLock) {
            camera.snapshotState()
        }

    fun restoreCameraState(state: ViewerCameraState) {
        synchronized(stateLock) {
            pendingCameraState = state
            cameraStateVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun resetCameraView() {
        synchronized(stateLock) {
            pendingCameraState = null
            camera.resetView()
            cameraStateVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPrinterBed(bed: PrinterBedSpec) {
        synchronized(stateLock) {
            if (pendingBed == bed) return
            pendingBed = bed
            bedVersion++
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setModelTransform(transform: ViewerModelTransform?) {
        synchronized(stateLock) {
            if (pendingModelTransform == transform) return
            pendingModelTransform = transform
            modelTransformVersion++
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setViewerAppearance(darkTheme: Boolean, accentColor: Int, worldColor: Int? = null) {
        synchronized(stateLock) {
            val appearance = ViewerAppearance(darkTheme = darkTheme, accentColor = accentColor, worldColor = worldColor)
            if (pendingAppearance == appearance) return
            pendingAppearance = appearance
            appearanceVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPaintSession(session: ViewerPaintSession?) {
        synchronized(stateLock) {
            if (pendingPaintSession == session) return
            pendingPaintSession = session
            paintSessionVersion++
            if (session == null) {
                pendingPaintCursorVisible = false
                pendingPaintCursorTrail.clear()
                paintCursorVersion++
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setCutPlaneSession(session: ViewerCutPlaneSession?) {
        synchronized(stateLock) {
            if (pendingCutPlaneSession == session) return
            pendingCutPlaneSession = session
            cutPlaneVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPaintOverlay(overlay: ViewerPaintOverlay) {
        synchronized(stateLock) {
            val session = pendingPaintSession ?: return
            pendingPaintSession = session.copy(overlay = overlay)
            paintSessionVersion++
            pendingPaintOverlayReplace = overlay
            pendingPaintOverlayAppend = null
            paintOverlayReplaceVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun appendPaintOverlay(overlay: ViewerPaintOverlay) {
        if (overlay.layers.isEmpty()) return
        synchronized(stateLock) {
            val session = pendingPaintSession ?: return
            pendingPaintSession = session.copy(overlay = session.overlay.plusReplacing(overlay))
            paintSessionVersion++
            val pending = pendingPaintOverlayAppend
            pendingPaintOverlayAppend = if (pending == null || pending.layers.isEmpty()) {
                overlay
            } else {
                pending.plusReplacing(overlay)
            }
            paintOverlayAppendVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun replaceLivePaintOverlay(overlay: ViewerPaintOverlay) {
        synchronized(stateLock) {
            val session = pendingPaintSession ?: return
            pendingPaintSession = session.copy(overlay = session.overlay)
            pendingPaintOverlayClearLive = true
            pendingPaintOverlayAppend = overlay.coalescedForLiveUpload()
            paintOverlayAppendVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun clearLivePaintOverlay() {
        synchronized(stateLock) {
            pendingPaintOverlayClearLive = true
            pendingPaintOverlayAppend = null
            paintOverlayAppendVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun promoteLivePaintOverlay() {
        synchronized(stateLock) {
            val session = pendingPaintSession
            if (session != null && pendingPaintOverlayAppend != null) {
                pendingPaintSession = session.copy(overlay = session.overlay.plusReplacing(pendingPaintOverlayAppend!!))
                paintSessionVersion++
            }
            pendingPaintOverlayPromoteLive = true
            paintOverlayAppendVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPaintCursor(screenX: Float, screenY: Float, visible: Boolean) {
        synchronized(stateLock) {
            if (
                pendingPaintCursorX == screenX &&
                pendingPaintCursorY == screenY &&
                pendingPaintCursorVisible == visible
            ) return
            pendingPaintCursorX = screenX
            pendingPaintCursorY = screenY
            pendingPaintCursorVisible = visible
            if (visible) {
                val last = pendingPaintCursorTrail.peekLast()
                val shouldAppend = last == null ||
                    (screenX - last.x) * (screenX - last.x) +
                    (screenY - last.y) * (screenY - last.y) >=
                    PaintPreviewTrailMinDistancePx * PaintPreviewTrailMinDistancePx
                if (shouldAppend) {
                    pendingPaintCursorTrail.addLast(PaintCursorPoint(screenX, screenY))
                    while (pendingPaintCursorTrail.size > PaintPreviewTrailMaxPoints) {
                        pendingPaintCursorTrail.removeFirst()
                    }
                }
            } else {
                pendingPaintCursorTrail.clear()
            }
            paintCursorVersion++
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun bindSurface(surface: Surface, width: Int, height: Int) {
        synchronized(stateLock) {
            if (boundSurface === surface && viewportWidth == width && viewportHeight == height) return
            boundSurface = surface
            viewportWidth = width
            viewportHeight = height
            if (failure != null) {
                failure = null
                onFailure(null)
            }
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun unbindSurface() {
        synchronized(stateLock) {
            boundSurface = null
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun setPaused(paused: Boolean) {
        synchronized(stateLock) {
            if (this.paused == paused) return
            this.paused = paused
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun orbitBy(deltaX: Float, deltaY: Float) {
        synchronized(stateLock) {
            if (!camera.orbitBy(deltaX, deltaY)) return
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun panBy(deltaX: Float, deltaY: Float) {
        synchronized(stateLock) {
            if (!camera.panBy(deltaX, deltaY, viewportHeight)) return
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun zoomBy(scaleFactor: Float) {
        synchronized(stateLock) {
            if (!camera.zoomBy(scaleFactor)) return
            dirty = true
            stateLock.notifyAll()
        }
    }

    fun pickObject(screenX: Float, screenY: Float, callback: (Long?) -> Unit) {
        val picked = pickHits(screenX, screenY).firstOrNull()?.objectId
        callback(picked)
    }

    fun pickObjects(screenX: Float, screenY: Float, callback: (List<Long>) -> Unit) {
        callback(pickHits(screenX, screenY).map { it.objectId }.distinct())
    }

    fun pickObjectHits(screenX: Float, screenY: Float, callback: (List<ViewerPickHit>) -> Unit) {
        callback(pickHits(screenX, screenY))
    }

    fun pickObjectHitsSync(screenX: Float, screenY: Float): List<ViewerPickHit> =
        pickHits(screenX, screenY)

    fun pickObjectIdsSync(screenX: Float, screenY: Float): List<Long> =
        synchronized(stateLock) {
            pickViewerObjects(
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix,
                sceneSpan = camera.sceneSpan,
                objects = objectUploadManager.uploads.map { it.toPickableObjectBounds() }
            )
        }

    fun bedPointForScreen(screenX: Float, screenY: Float): StlModelPlacement? =
        synchronized(stateLock) {
            val ray = screenRay(
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix
            ) ?: return@synchronized null
            if (kotlin.math.abs(ray.directionZ) < 0.0001f) return@synchronized null
            val t = -ray.originZ / ray.directionZ
            if (!t.isFinite()) return@synchronized null
            val worldX = ray.originX + ray.directionX * t
            val worldY = ray.originY + ray.directionY * t
            StlModelPlacement(
                xMm = worldX + pendingBed.widthMm * 0.5f,
                yMm = worldY + pendingBed.depthMm * 0.5f,
                zMm = 0f
            )
        }

    fun paintRayForObject(screenX: Float, screenY: Float, objectId: Long): ViewerPaintRay? =
        synchronized(stateLock) {
            val ray = screenRay(
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix
            ) ?: return@synchronized null
            val upload = objectUploadManager.uploads.firstOrNull { it.id == objectId } ?: return@synchronized null
            val inverseModel = FloatArray(16)
            if (!Matrix.invertM(inverseModel, 0, upload.modelMatrix, 0)) {
                return@synchronized null
            }
            paintRayInModelSpace(ray, inverseModel)
        }

    fun cutPlanePointForScreen(screenX: Float, screenY: Float): ViewerCutConnectorPoint? =
        synchronized(stateLock) {
            val session = activeCutPlaneSession?.takeIf { it.connectorsEditing } ?: return@synchronized null
            val upload = objectUploadManager.uploads.firstOrNull { it.id == session.selectedObjectId }
                ?: return@synchronized null
            val ray = screenRay(
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix
            ) ?: return@synchronized null
            val inverseModel = FloatArray(16)
            if (!Matrix.invertM(inverseModel, 0, upload.modelMatrix, 0)) {
                return@synchronized null
            }
            val localRay = paintRayInModelSpace(ray, inverseModel)?.values ?: return@synchronized null
            val plane = session.cutPlaneGeometry(upload.mesh.bounds)
            val denominator =
                localRay[3] * plane.normal[0] +
                    localRay[4] * plane.normal[1] +
                    localRay[5] * plane.normal[2]
            if (kotlin.math.abs(denominator) < 0.0001f) return@synchronized null
            val t = (
                (plane.center[0] - localRay[0]) * plane.normal[0] +
                    (plane.center[1] - localRay[1]) * plane.normal[1] +
                    (plane.center[2] - localRay[2]) * plane.normal[2]
                ) / denominator
            if (!t.isFinite() || t < 0f) return@synchronized null
            val x = localRay[0] + localRay[3] * t
            val y = localRay[1] + localRay[4] * t
            val z = localRay[2] + localRay[5] * t
            val bounds = upload.mesh.bounds
            val fromCenterX = x - plane.center[0]
            val fromCenterY = y - plane.center[1]
            val fromCenterZ = z - plane.center[2]
            val cutU = fromCenterX * plane.u[0] + fromCenterY * plane.u[1] + fromCenterZ * plane.u[2]
            val cutV = fromCenterX * plane.v[0] + fromCenterY * plane.v[1] + fromCenterZ * plane.v[2]
            if (!upload.mesh.containsCutPlanePoint(plane = plane, pointU = cutU, pointV = cutV)) {
                return@synchronized null
            }
            val tolerance = maxOf(2f, maxOf(bounds.sizeX, bounds.sizeY, bounds.sizeZ) * 0.06f)
            if (
                x !in (bounds.minX - tolerance)..(bounds.maxX + tolerance) ||
                y !in (bounds.minY - tolerance)..(bounds.maxY + tolerance) ||
                z !in (bounds.minZ - tolerance)..(bounds.maxZ + tolerance)
            ) {
                return@synchronized null
            }
            ViewerCutConnectorPoint(xMm = x, yMm = y, zMm = z)
        }

    private fun pickHits(screenX: Float, screenY: Float): List<ViewerPickHit> {
        val picked = synchronized(stateLock) {
            pickViewerObjectHits(
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix,
                sceneSpan = camera.sceneSpan,
                objects = objectUploadManager.uploads
            )
        }
        return picked
    }

    fun currentFailure(): ViewerFailure? = failure

    fun requestExitAndWait() {
        synchronized(stateLock) {
            shouldExit = true
            stateLock.notifyAll()
        }
        join()
    }

    override fun run() {
        while (true) {
            val targetSurface: Surface?
            val targetWidth: Int
            val targetHeight: Int
            val shouldPause: Boolean
            val exiting: Boolean
            synchronized(stateLock) {
                while (!dirty && !shouldExit) {
                    stateLock.wait()
                }
                targetSurface = boundSurface
                targetWidth = viewportWidth.coerceAtLeast(1)
                targetHeight = viewportHeight.coerceAtLeast(1)
                shouldPause = paused
                exiting = shouldExit
                dirty = false
            }

            if (exiting) {
                break
            }

            if (shouldPause || targetSurface == null || !targetSurface.isValid) {
                if (egl.hasWindowSurface) {
                    releaseGlResources()
                }
                destroyWindowSurface()
                continue
            }

            try {
                ensureGlReady(targetSurface, targetWidth, targetHeight)
                if (syncSceneState()) {
                    renderFrame(targetWidth, targetHeight)
                }
            } catch (throwable: Throwable) {
                fail(
                    title = "Workspace renderer failed",
                    detail = throwable.message ?: "The STL workspace could not render on this device."
                )
            }
        }

        destroyGl()
    }

    private fun ensureGlReady(surface: Surface, width: Int, height: Int) {
        egl.ensureReady(surface, width, height, onSurfaceCreated = ::buildProgramsIfNeeded)
    }

    private fun syncSceneState(): Boolean {
        val targetMeshVersion: Long
        val targetPlateObjectsVersion: Long
        val targetPlateObjectTransformVersion: Long
        val targetPlateObjectSelectionVersion: Long
        val targetGcodePreviewVersion: Long
        val targetGcodeLayerRangeVersion: Long
        val targetGcodePathVisibilityVersion: Long
        val targetGcodeDisplayModeVersion: Long
        val targetCutPlaneVersion: Long
        val targetCameraStateVersion: Long
        val targetBedVersion: Long
        val targetModelTransformVersion: Long
        val targetAppearanceVersion: Long
        val targetPaintSessionVersion: Long
        val targetPaintOverlayReplaceVersion: Long
        val targetPaintOverlayAppendVersion: Long
        val targetPaintCursorVersion: Long
        val targetPaintOverlayReplace: ViewerPaintOverlay?
        val targetPaintOverlayAppend: ViewerPaintOverlay?
        val targetPaintOverlayClearLive: Boolean
        val targetPaintOverlayPromoteLive: Boolean
        val targetCameraState: ViewerCameraState?
        synchronized(stateLock) {
            activeBed = pendingBed
            activeModelTransform = pendingModelTransform
            activePlateObjects = pendingPlateObjects
            activeAppearance = pendingAppearance
            activePaintSession = pendingPaintSession
            activePaintCursorX = pendingPaintCursorX
            activePaintCursorY = pendingPaintCursorY
            activePaintCursorVisible = pendingPaintCursorVisible
            activePaintCursorTrail = ArrayList(pendingPaintCursorTrail)
            activeMesh = pendingMesh
            activeGcodePreviewEngineHandle = pendingGcodePreviewEngineHandle
            activeGcodePreviewKey = pendingGcodePreviewKey
            activeGcodePreviewVertexBudget = pendingGcodePreviewVertexBudget
            activeGcodeLayerMin = pendingGcodeLayerMin
            activeGcodeLayerMax = pendingGcodeLayerMax
            activeGcodePreviewQueuedAtMs = pendingGcodePreviewQueuedAtMs
            activeGcodePathVisibility = pendingGcodePathVisibility
            activeGcodeDisplayMode = pendingGcodeDisplayMode
            activeCutPlaneSession = pendingCutPlaneSession
            targetMeshVersion = meshVersion
            targetPlateObjectsVersion = plateObjectsVersion
            targetPlateObjectTransformVersion = plateObjectTransformVersion
            targetPlateObjectSelectionVersion = plateObjectSelectionVersion
            targetGcodePreviewVersion = gcodePreviewVersion
            targetGcodeLayerRangeVersion = gcodeLayerRangeVersion
            targetGcodePathVisibilityVersion = gcodePathVisibilityVersion
            targetGcodeDisplayModeVersion = gcodeDisplayModeVersion
            targetCutPlaneVersion = cutPlaneVersion
            targetCameraStateVersion = cameraStateVersion
            targetBedVersion = bedVersion
            targetModelTransformVersion = modelTransformVersion
            targetAppearanceVersion = appearanceVersion
            targetPaintSessionVersion = paintSessionVersion
            targetPaintOverlayReplaceVersion = paintOverlayReplaceVersion
            targetPaintOverlayReplace = pendingPaintOverlayReplace
            pendingPaintOverlayReplace = null
            targetPaintOverlayAppendVersion = paintOverlayAppendVersion
            targetPaintOverlayAppend = pendingPaintOverlayAppend
            pendingPaintOverlayAppend = null
            targetPaintOverlayClearLive = pendingPaintOverlayClearLive
            pendingPaintOverlayClearLive = false
            targetPaintOverlayPromoteLive = pendingPaintOverlayPromoteLive
            pendingPaintOverlayPromoteLive = false
            targetPaintCursorVersion = paintCursorVersion
            targetCameraState = pendingCameraState
        }

        if (appliedBedVersion != targetBedVersion) {
            uploadBedGeometry(activeBed)
            objectUploadManager.plateSceneCameraInitialized = false
            appliedBedVersion = targetBedVersion
        }

        if (appliedMeshVersion != targetMeshVersion) {
            uploadModel(activeMesh)
            appliedMeshVersion = targetMeshVersion
            appliedModelTransformVersion = targetModelTransformVersion
        } else if (appliedModelTransformVersion != targetModelTransformVersion) {
            updateModelPlacement(activeMesh)
            appliedModelTransformVersion = targetModelTransformVersion
        }

        if (appliedPlateObjectsVersion != targetPlateObjectsVersion) {
            uploadPlateObjects(activePlateObjects)
            updateCutPlaneUpload(activeCutPlaneSession)
            appliedPlateObjectsVersion = targetPlateObjectsVersion
            appliedPlateObjectTransformVersion = targetPlateObjectTransformVersion
            appliedPlateObjectSelectionVersion = targetPlateObjectSelectionVersion
        } else if (
            appliedPlateObjectTransformVersion != targetPlateObjectTransformVersion ||
            appliedPlateObjectSelectionVersion != targetPlateObjectSelectionVersion
        ) {
            updatePlateObjectTransforms(activePlateObjects)
            appliedPlateObjectTransformVersion = targetPlateObjectTransformVersion
            appliedPlateObjectSelectionVersion = targetPlateObjectSelectionVersion
        }

        if (appliedGcodePreviewVersion != targetGcodePreviewVersion) {
            val previewEngineHandle = activeGcodePreviewEngineHandle
            val previewKey = activeGcodePreviewKey
            if (previewEngineHandle != 0L && previewKey > 0L) {
                if (!isPreviewStateCurrent(
                        previewVersion = targetGcodePreviewVersion
                    )
                ) {
                    return false
                }
                if (deferFreshPreviewReloadIfNeeded(activeGcodePreviewQueuedAtMs)) {
                    return false
                }
                appliedGcodeLayerRangeVersion = -1L
                val requestedLayerMin = activeGcodeLayerMin
                val requestedLayerMax = activeGcodeLayerMax
                val loadStartedAtMs = SystemClock.elapsedRealtime()
                val loadResult = gcodePreviewRenderer.loadLatestSlice(
                    engineRawHandle = previewEngineHandle,
                    requestedLayerMin,
                    requestedLayerMax,
                    activeGcodePreviewVertexBudget,
                    targetGcodePreviewVersion
                )
                activePreviewNativeLoadMs = SystemClock.elapsedRealtime() - loadStartedAtMs
                val nativeLoadMetrics = gcodePreviewRenderer.lastNativeLoadMetrics
                activePreviewNativeSelectedParseMs = nativeLoadMetrics.selectedParseMs
                activePreviewNativeLibvgcodeLoadMs = nativeLoadMetrics.libvgcodeLoadMs
                activePreviewNativeTotalLoadMs = nativeLoadMetrics.totalMs
                activePreviewNativeLoadedVertices = nativeLoadMetrics.vertices
                activePreviewNativeCachedVertices = nativeLoadMetrics.cachedVertices
                activePreviewNativeCachedLayers = nativeLoadMetrics.cachedLayers
                activePreviewNativeCacheHit = nativeLoadMetrics.cacheHit
                activePreviewNativeCacheBuilt = nativeLoadMetrics.cacheBuilt
                activePreviewFirstFrameMs = -1L
                activePreviewRenderedFrames = 0L
                activePreviewSlowFrames = 0L
                activePreviewLastMetricsReportAtMs = 0L
                if (loadResult !is NativeEngineCallResult.Success) {
                    fail(
                        title = "G-code preview range too large",
                        detail = loadResult.statusMessage
                    )
                    throw IllegalStateException(loadResult.statusMessage)
                }
                if (!isPreviewStateCurrent(
                        previewVersion = targetGcodePreviewVersion
                    )
                ) {
                    return false
                }
                appliedGcodePathVisibilityVersion = -1L
                appliedGcodeDisplayModeVersion = -1L
            } else {
                releaseGcodeViewer()
            }
            appliedGcodePreviewVersion = targetGcodePreviewVersion
        }

        if (appliedGcodeLayerRangeVersion != targetGcodeLayerRangeVersion) {
            if (gcodePreviewRenderer.isActive) {
                gcodePreviewRenderer.setVisibleLayerRange(activeGcodeLayerMin, activeGcodeLayerMax)
            }
            appliedGcodeLayerRangeVersion = targetGcodeLayerRangeVersion
        }

        if (appliedGcodeDisplayModeVersion != targetGcodeDisplayModeVersion) {
            if (gcodePreviewRenderer.isActive) {
                gcodePreviewRenderer.setDisplayMode(activeGcodeDisplayMode)
            }
            appliedGcodeDisplayModeVersion = targetGcodeDisplayModeVersion
        }

        if (appliedGcodePathVisibilityVersion != targetGcodePathVisibilityVersion) {
            if (gcodePreviewRenderer.isActive) {
                activeGcodePathVisibility.forEach { (key, visible) ->
                    gcodePreviewRenderer.setPathVisibility(kind = key.first, id = key.second, visible = visible)
                }
            }
            appliedGcodePathVisibilityVersion = targetGcodePathVisibilityVersion
        }

        if (appliedAppearanceVersion != targetAppearanceVersion) {
            updateAppearanceColors(activeAppearance)
            appliedAppearanceVersion = targetAppearanceVersion
        }

        if (appliedPaintSessionVersion != targetPaintSessionVersion) {
            val overlayKey = activePaintSession?.let { it.selectedObjectId to it.mode }
            if (overlayKey != appliedPaintSessionOverlayKey || activePaintSession?.hasOverlay == true) {
                uploadPaintOverlay(activePaintSession)
                appliedPaintSessionOverlayKey = overlayKey
            }
            appliedPaintSessionVersion = targetPaintSessionVersion
        }

        if (appliedPaintOverlayReplaceVersion != targetPaintOverlayReplaceVersion) {
            val replacementSession = activePaintSession?.copy(
                overlay = targetPaintOverlayReplace ?: ViewerPaintOverlay.Empty
            )
            uploadPaintOverlay(replacementSession)
            appliedPaintOverlayReplaceVersion = targetPaintOverlayReplaceVersion
        }

        if (appliedPaintOverlayAppendVersion != targetPaintOverlayAppendVersion) {
            if (targetPaintOverlayPromoteLive) {
                paintOverlayUploadManager.promoteLive()
            }
            if (targetPaintOverlayClearLive) {
                paintOverlayUploadManager.clearLive()
            }
            targetPaintOverlayAppend?.let { paintOverlayUploadManager.queue(it, live = !targetPaintOverlayPromoteLive) }
            appliedPaintOverlayAppendVersion = targetPaintOverlayAppendVersion
        }
        drainPaintOverlayUploadQueue(activePaintSession)

        if (appliedPaintCursorVersion != targetPaintCursorVersion) {
            appliedPaintCursorVersion = targetPaintCursorVersion
        }

        if (appliedCutPlaneVersion != targetCutPlaneVersion) {
            updateCutPlaneUpload(activeCutPlaneSession)
            appliedCutPlaneVersion = targetCutPlaneVersion
        }

        if (appliedCameraStateVersion != targetCameraStateVersion) {
            targetCameraState?.let(camera::restoreState)
            synchronized(stateLock) {
                if (pendingCameraState === targetCameraState) {
                    pendingCameraState = null
                }
            }
            appliedCameraStateVersion = targetCameraStateVersion
        }
        return true
    }

    private fun isPreviewStateCurrent(previewVersion: Long): Boolean =
        synchronized(stateLock) {
            val isCurrent = ViewerUpdateDecisions.isGcodePreviewLoadCurrent(
                expectedPreviewVersion = previewVersion,
                currentPreviewVersion = gcodePreviewVersion
            )
            if (!isCurrent) {
                dirty = true
                stateLock.notifyAll()
            }
            isCurrent
        }

    private fun deferFreshPreviewReloadIfNeeded(queuedAtMs: Long): Boolean {
        val delayMs = ViewerUpdateDecisions.gcodePreviewReloadCoalesceDelayMs(
            rendererActive = gcodePreviewRenderer.isActive,
            queuedAtMs = queuedAtMs,
            nowMs = SystemClock.elapsedRealtime(),
            coalesceWindowMs = PreviewReloadCoalesceWindowMs
        )
        if (delayMs <= 0L) {
            return false
        }
        Thread.sleep(delayMs)
        synchronized(stateLock) {
            dirty = true
            stateLock.notifyAll()
        }
        return true
    }

    private fun publishGcodePreviewGeneration(engineRawHandle: Long, generation: Long) {
        val engineHandle = NativeEngineHandle.fromRaw(engineRawHandle) ?: return
        NativeEngineCalls.setGcodePreviewGeneration(engineHandle, generation)
    }

    private fun renderFrame(width: Int, height: Int) {
        val frameStartedAtMs = SystemClock.elapsedRealtime()
        GLES20.glClearColor(viewerColors.clearColor[0], viewerColors.clearColor[1], viewerColors.clearColor[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        updateCamera(width = width, height = height)

        val paintSession = activePaintSession
        val paintModeActive = paintSession != null
        if (!paintModeActive) {
            val hasOrcaBedModel = bedModelUpload != null
            val hasOrcaBedTexture = bedTextureUpload != null
            bedModelUpload?.let {
                drawWorkspaceCulledTriangles(upload = it, color = viewerColors.bedWallColor, drawTriangles = ::drawTriangles)
            }
            bedModelUndersideUpload?.let {
                drawWorkspaceTransparentBedUnderside(upload = it, drawTriangles = ::drawTriangles)
            }
            if (!hasOrcaBedModel) {
                bedWallUpload?.let {
                    drawTriangles(upload = it, color = viewerColors.bedWallColor, applyPlacement = false)
                }
                bedSurfaceUpload?.let(::drawTransparentPlateSurface)
            }
            if (!hasOrcaBedTexture) {
                bedGridUpload?.let {
                    drawTriangles(upload = it, color = viewerColors.bedGridColor, applyPlacement = false, stabilizeSurface = true)
                }
                bedGridBoldUpload?.let {
                    drawTriangles(upload = it, color = viewerColors.bedBorderColor, applyPlacement = false, stabilizeSurface = true)
                }
                bedBorderUpload?.let {
                    drawTriangles(upload = it, color = viewerColors.bedBorderColor, applyPlacement = false, stabilizeSurface = true)
                }
            } else if (activeBed.bedTextureIncludesGrid) {
                bedGridUpload?.let {
                    drawWorkspaceBottomOnlyGrid(upload = it, color = ORCA_GRID_THIN_COLOR, drawTriangles = ::drawTriangles)
                }
                bedGridBoldUpload?.let {
                    drawWorkspaceBottomOnlyGrid(upload = it, color = ORCA_GRID_BOLD_COLOR, drawTriangles = ::drawTriangles)
                }
            } else {
                bedGridUpload?.let {
                    drawTriangles(upload = it, color = ORCA_GRID_THIN_COLOR, applyPlacement = false, stabilizeSurface = true)
                }
                bedGridBoldUpload?.let {
                    drawTriangles(upload = it, color = ORCA_GRID_BOLD_COLOR, applyPlacement = false, stabilizeSurface = true)
                }
            }
            bedTextureUpload?.let {
                drawWorkspaceTexturedBed(
                    textureProgram = textureProgram,
                    textureHandles = textureHandles,
                    upload = it,
                    viewProjectionMatrix = viewProjectionMatrix,
                    alpha = 1f
                )
            }
        }
        if (gcodePreviewRenderer.isActive) {
            viewMatrix.copyInto(gcodeViewMatrix)
            Matrix.translateM(
                gcodeViewMatrix,
                0,
                -(activeBed.originXmm + activeBed.widthMm * 0.5f),
                -(activeBed.originYmm + activeBed.depthMm * 0.5f),
                0f
            )
            val renderResult = gcodePreviewRenderer.render(gcodeViewMatrix, projectionMatrix)
            require(renderResult is NativeEngineCallResult.Success) {
                renderResult.statusMessage
            }
            if (!firstFrameCleared) {
                firstFrameCleared = true
                activePreviewFirstFrameMs = if (activeGcodePreviewQueuedAtMs > 0L) {
                    SystemClock.elapsedRealtime() - activeGcodePreviewQueuedAtMs
                } else {
                    activePreviewNativeLoadMs
                }
                onRenderReady(true)
            }
        } else if (objectUploadManager.uploads.isNotEmpty()) {
            for (objectUpload in objectUploadManager.uploads) {
                if (paintSession != null && objectUpload.id != paintSession.selectedObjectId) continue
                val clippedCutUpload = cutClippedObjectUpload
                    ?.takeIf {
                        activeCutPlaneSession?.connectorsEditing == true &&
                            activeCutPlaneSession?.selectedObjectId == objectUpload.id
                    }
                drawTriangles(
                    upload = clippedCutUpload ?: objectUpload.upload,
                    color = objectUpload.colorInt?.let(::viewerObjectColor)
                        ?: if (paintSession != null) {
                            viewerColors.paintBaseModelColor()
                        } else if (objectUpload.selected) {
                            viewerColors.modelColor
                        } else {
                            viewerColors.dimmedModelColor()
                        },
                    modelMatrixOverride = objectUpload.modelMatrix
                )
            }
            if (!paintModeActive) {
                drawCutPlaneOverlay()
            }
            drawPaintOverlay()
            drawPaintPreviewTrail(width = width, height = height)
            if (!paintModeActive) {
                objectUploadManager.selectedFootprintUpload?.let {
                    drawTriangles(upload = it, color = viewerColors.selectedFootprintColor, applyPlacement = false)
                }
            }
            if (!firstFrameCleared || renderedReadyMeshVersion != appliedPlateObjectsVersion) {
                firstFrameCleared = true
                renderedReadyMeshVersion = appliedPlateObjectsVersion
                onRenderReady(true)
            }
        } else if (modelUpload != null) {
            modelUpload?.let {
                drawTriangles(upload = it, color = viewerColors.modelColor)
                if (!firstFrameCleared || renderedReadyMeshVersion != appliedMeshVersion) {
                    firstFrameCleared = true
                    renderedReadyMeshVersion = appliedMeshVersion
                    onRenderReady(true)
                }
            }
        } else {
            if (!firstFrameCleared) {
                firstFrameCleared = true
                onRenderReady(false)
            }
        }

        egl.swapBuffers()
        if (gcodePreviewRenderer.isActive) {
            val frameMs = SystemClock.elapsedRealtime() - frameStartedAtMs
            activePreviewRenderedFrames++
            if (frameMs > PreviewSlowFrameThresholdMs) {
                activePreviewSlowFrames++
            }
            val shouldReportMetrics = activePreviewFirstFrameMs >= 0L &&
                (
                    activePreviewRenderedFrames == 1L ||
                        frameMs > PreviewSlowFrameThresholdMs ||
                        frameStartedAtMs - activePreviewLastMetricsReportAtMs >= PreviewMetricsReportIntervalMs
                    )
            if (shouldReportMetrics) {
                activePreviewLastMetricsReportAtMs = frameStartedAtMs
                onPreviewRuntimeMetrics(
                    GcodePreviewRuntimeMetrics(
                        previewKey = activeGcodePreviewKey,
                        layerStart = activeGcodeLayerMin,
                        layerEnd = activeGcodeLayerMax,
                        vertexBudget = activeGcodePreviewVertexBudget,
                        nativeLoadMs = activePreviewNativeLoadMs,
                        nativeSelectedParseMs = activePreviewNativeSelectedParseMs,
                        nativeLibvgcodeLoadMs = activePreviewNativeLibvgcodeLoadMs,
                        nativeTotalLoadMs = activePreviewNativeTotalLoadMs,
                        nativeLoadedVertices = activePreviewNativeLoadedVertices,
                        nativeCachedVertices = activePreviewNativeCachedVertices,
                        nativeCachedLayers = activePreviewNativeCachedLayers,
                        nativeCacheHit = activePreviewNativeCacheHit,
                        nativeCacheBuilt = activePreviewNativeCacheBuilt,
                        firstFrameMs = activePreviewFirstFrameMs,
                        lastFrameMs = frameMs,
                        slowFrameCount = activePreviewSlowFrames,
                        renderedFrameCount = activePreviewRenderedFrames
                    )
                )
            }
        }
    }

    private fun drawTransparentPlateSurface(upload: TriangleUpload) =
        drawWorkspaceTransparentPlateSurface(upload = upload, drawTriangles = ::drawTriangles, viewerColors = viewerColors)

    private fun updateCutPlaneUpload(session: ViewerCutPlaneSession?) {
        cutPlaneUpload?.let(::deleteTriangleUpload)
        cutPlaneUpload = null
        cutConnectorUpload?.let(::deleteTriangleUpload)
        cutConnectorUpload = null
        cutFaceFillUpload?.let(::deleteTriangleUpload)
        cutFaceFillUpload = null
        cutClippedObjectUpload?.let(::deleteTriangleUpload)
        cutClippedObjectUpload = null
        cutPlaneSessionUploadKey = null
        val activeSession = session ?: return
        val objectUpload = objectUploadManager.uploads.firstOrNull { it.id == activeSession.selectedObjectId } ?: return
        val bounds = objectUpload.mesh.bounds
        val longestSide = maxOf(bounds.sizeX, bounds.sizeY, bounds.sizeZ)
        val pad = if (activeSession.connectorsEditing) {
            maxOf(1.5f, longestSide * 0.08f)
        } else {
            maxOf(8f, longestSide * 0.75f)
        }
        val minX = bounds.minX - pad
        val maxX = bounds.maxX + pad
        val minY = bounds.minY - pad
        val maxY = bounds.maxY + pad
        val minZ = bounds.minZ - pad
        val maxZ = bounds.maxZ + pad
        val offset = when (activeSession.axis) {
            ViewerCutPlaneAxis.X -> activeSession.offsetMm.coerceIn(bounds.minX, bounds.maxX)
            ViewerCutPlaneAxis.Y -> activeSession.offsetMm.coerceIn(bounds.minY, bounds.maxY)
            ViewerCutPlaneAxis.Z,
            ViewerCutPlaneAxis.Custom -> activeSession.offsetMm.coerceIn(bounds.minZ, bounds.maxZ)
        }
        val centerX = (bounds.minX + bounds.maxX) * 0.5f
        val centerY = (bounds.minY + bounds.maxY) * 0.5f
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5f
        val zPlaneGeometry = activeSession.cutPlaneGeometry(bounds)
        val zNormal = zPlaneGeometry.normal
        fun zPlaneSlab(): Pair<FloatArray, FloatArray> {
            val halfU = (bounds.sizeX * 0.5f) + pad
            val halfV = (bounds.sizeY * 0.5f) + pad
            val thickness = maxOf(0.35f, longestSide * 0.008f)
            val planeCenter = zPlaneGeometry.center
            val u = zPlaneGeometry.u
            val v = zPlaneGeometry.v
            fun point(uScale: Float, vScale: Float, nScale: Float): FloatArray =
                floatArrayOf(
                    planeCenter[0] + u[0] * uScale + v[0] * vScale + zNormal[0] * nScale,
                    planeCenter[1] + u[1] * uScale + v[1] * vScale + zNormal[1] * nScale,
                    planeCenter[2] + u[2] * uScale + v[2] * vScale + zNormal[2] * nScale
                )
            val front = arrayOf(
                point(-halfU, -halfV, thickness),
                point(halfU, -halfV, thickness),
                point(halfU, halfV, thickness),
                point(-halfU, halfV, thickness)
            )
            val back = arrayOf(
                point(-halfU, -halfV, -thickness),
                point(halfU, -halfV, -thickness),
                point(halfU, halfV, -thickness),
                point(-halfU, halfV, -thickness)
            )
            val vertexList = ArrayList<Float>(108)
            val normalList = ArrayList<Float>(108)
            fun appendVertex(p: FloatArray, n: FloatArray) {
                vertexList.add(p[0])
                vertexList.add(p[1])
                vertexList.add(p[2])
                normalList.add(n[0])
                normalList.add(n[1])
                normalList.add(n[2])
            }
            fun appendQuad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, n: FloatArray) {
                appendVertex(a, n)
                appendVertex(b, n)
                appendVertex(c, n)
                appendVertex(a, n)
                appendVertex(c, n)
                appendVertex(d, n)
            }
            appendQuad(front[0], front[1], front[2], front[3], zNormal)
            appendQuad(back[1], back[0], back[3], back[2], floatArrayOf(-zNormal[0], -zNormal[1], -zNormal[2]))
            appendQuad(front[0], back[0], back[1], front[1], floatArrayOf(-v[0], -v[1], -v[2]))
            appendQuad(front[1], back[1], back[2], front[2], u)
            appendQuad(front[2], back[2], back[3], front[3], v)
            appendQuad(front[3], back[3], back[0], front[0], floatArrayOf(-u[0], -u[1], -u[2]))
            return vertexList.toFloatArray() to normalList.toFloatArray()
        }
        if (activeSession.connectorPoints.isNotEmpty()) {
            val connectorGeometry = buildCutConnectorMarkerGeometry(
                points = activeSession.connectorPoints,
                plane = zPlaneGeometry,
                session = activeSession
            )
            if (connectorGeometry.first.isNotEmpty()) {
                cutConnectorUpload = uploadTriangleData(connectorGeometry.first, connectorGeometry.second)
            }
        }
        if (activeSession.connectorsEditing) {
            val clippedGeometry = buildCutClippedObjectGeometry(objectUpload.mesh, zPlaneGeometry)
            if (clippedGeometry.first.isNotEmpty()) {
                cutClippedObjectUpload = uploadTriangleData(clippedGeometry.first, clippedGeometry.second)
            }
            val fillGeometry = buildCutFaceFillGeometry(objectUpload.mesh, zPlaneGeometry)
            if (fillGeometry.first.isNotEmpty()) {
                cutFaceFillUpload = uploadTriangleData(fillGeometry.first, fillGeometry.second)
            }
        }
        var normalsOverride: FloatArray? = null
        val vertices = when (activeSession.axis) {
            ViewerCutPlaneAxis.X -> floatArrayOf(
                offset, minY, minZ, offset, maxY, minZ, offset, maxY, maxZ,
                offset, minY, minZ, offset, maxY, maxZ, offset, minY, maxZ
            )
            ViewerCutPlaneAxis.Y -> floatArrayOf(
                minX, offset, minZ, maxX, offset, minZ, maxX, offset, maxZ,
                minX, offset, minZ, maxX, offset, maxZ, minX, offset, maxZ
            )
            ViewerCutPlaneAxis.Z,
            ViewerCutPlaneAxis.Custom -> zPlaneSlab().also { normalsOverride = it.second }.first
        }
        val normal = when (activeSession.axis) {
            ViewerCutPlaneAxis.X -> floatArrayOf(1f, 0f, 0f)
            ViewerCutPlaneAxis.Y -> floatArrayOf(0f, 1f, 0f)
            ViewerCutPlaneAxis.Z,
            ViewerCutPlaneAxis.Custom -> zNormal
        }
        val normals = normalsOverride ?: FloatArray(vertices.size).also { generatedNormals ->
            var i = 0
            while (i < generatedNormals.size) {
                generatedNormals[i] = normal[0]
                generatedNormals[i + 1] = normal[1]
                generatedNormals[i + 2] = normal[2]
                i += 3
            }
        }
        cutPlaneUpload = uploadTriangleData(vertices, normals)
        cutPlaneSessionUploadKey = activeSession
    }

    private fun drawCutPlaneOverlay() {
        val session = activeCutPlaneSession ?: return
        val upload = cutPlaneUpload ?: return
        val objectUpload = objectUploadManager.uploads.firstOrNull { it.id == session.selectedObjectId } ?: return
        val alpha = if (session.connectorsEditing) 0.18f else 0.34f
        val color = when (session.axis) {
            ViewerCutPlaneAxis.X -> floatArrayOf(1.0f, 0.22f, 0.16f, alpha)
            ViewerCutPlaneAxis.Y -> floatArrayOf(0.12f, 0.80f, 0.40f, alpha)
            ViewerCutPlaneAxis.Z -> floatArrayOf(0.0f, 0.88f, 0.95f, alpha)
            ViewerCutPlaneAxis.Custom -> floatArrayOf(1.0f, 0.76f, 0.20f, alpha)
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        try {
            if (session.connectorsEditing) {
                cutFaceFillUpload?.let { fillUpload ->
                    drawTriangles(
                        upload = fillUpload,
                        color = floatArrayOf(0.76f, 0.78f, 0.78f, 0.78f),
                        modelMatrixOverride = objectUpload.modelMatrix
                    )
                }
            } else {
                drawTriangles(upload = upload, color = color, modelMatrixOverride = objectUpload.modelMatrix)
            }
            cutConnectorUpload?.let { connectorUpload ->
                val connectorColor = when (session.connectorKind) {
                    ViewerCutConnectorKind.Dowel -> floatArrayOf(0.72f, 0.74f, 0.72f, 0.92f)
                    ViewerCutConnectorKind.Plug,
                    ViewerCutConnectorKind.Snap,
                    ViewerCutConnectorKind.None -> floatArrayOf(1.0f, 0.80f, 0.05f, 0.94f)
                }
                drawTriangles(
                    upload = connectorUpload,
                    color = connectorColor,
                    modelMatrixOverride = objectUpload.modelMatrix
                )
            }
        } finally {
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun uploadBedGeometry(bed: PrinterBedSpec) {
        bedSurfaceUpload?.let(::deleteTriangleUpload)
        bedModelUpload?.let(::deleteTriangleUpload)
        bedModelUndersideUpload?.let(::deleteTriangleUpload)
        bedTextureUpload?.let(::deleteTextureUpload)
        bedGridUpload?.let(::deleteTriangleUpload)
        bedGridBoldUpload?.let(::deleteTriangleUpload)
        bedBorderUpload?.let(::deleteTriangleUpload)
        bedWallUpload?.let(::deleteTriangleUpload)
        bedSurfaceUpload = null
        bedModelUpload = null
        bedModelUndersideUpload = null
        bedTextureUpload = null
        bedGridUpload = null
        bedGridBoldUpload = null
        bedBorderUpload = null
        bedWallUpload = null

        val hasOrcaAssetPath = bed.bedModelAssetPath.isNotBlank() || bed.bedTextureAssetPath.isNotBlank()
        runCatching { loadSplitOrcaBedModelGeometry(context, bed) }
            .getOrNull()
            ?.let { bedModelGeometry ->
                bedModelUpload = uploadTriangleDataIfNotEmpty(bedModelGeometry.opaque)
                bedModelUndersideUpload = uploadTriangleDataIfNotEmpty(bedModelGeometry.underside)
            }
        runCatching { loadOrcaBedTextureGeometry(context, bed) }
            .getOrNull()
            ?.let { bedTextureGeometry ->
                try {
                    bedTextureUpload = uploadTextureQuad(
                        vertices = bedTextureGeometry.vertices,
                        uvs = bedTextureGeometry.uvs,
                        bitmap = bedTextureGeometry.bitmap
                    )
                } finally {
                    bedTextureGeometry.bitmap.recycle()
                }
            }
        if (bedModelUpload != null || bedTextureUpload != null || hasOrcaAssetPath) {
            val grid = buildOrcaBedGridGeometry(bed)
            bedGridUpload = uploadTriangleDataIfNotEmpty(grid.thin)
            bedGridBoldUpload = uploadTriangleDataIfNotEmpty(grid.bold)
        } else {
            val geometry = buildBedGeometry(bed)
            bedSurfaceUpload = uploadTriangleData(
                vertices = geometry.surface.vertices,
                normals = geometry.surface.normals
            )
            bedGridUpload = uploadTriangleData(
                vertices = geometry.grid.vertices,
                normals = geometry.grid.normals
            )
            bedBorderUpload = uploadTriangleData(
                vertices = geometry.border.vertices,
                normals = geometry.border.normals
            )
            bedWallUpload = uploadTriangleData(
                vertices = geometry.wall.vertices,
                normals = geometry.wall.normals
            )
        }
    }

    private fun uploadModel(mesh: StlMesh?) {
        modelUpload?.let(::deleteTriangleUpload)
        modelUpload = null
        updateModelPlacement(mesh)

        if (mesh == null) {
            return
        }

        require(mesh.triangleCount > 0) { "Mesh has no triangles." }
        modelUpload = uploadTriangleData(
            vertices = mesh.vertices,
            normals = mesh.normals
        )
    }

    private fun updateModelPlacement(mesh: StlMesh?) {
        Matrix.setIdentityM(identityModelMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)
        modelPlacementX = 0f
        modelPlacementY = 0f
        modelPlacementZ = 0f

        if (mesh == null) {
            camera.setEmptyScene(activeBed)
            return
        }

        require(mesh.triangleCount > 0) { "Mesh has no triangles." }
        val transform = activeModelTransform ?: defaultBedCenteredPrinterTransform(activeBed)
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
            mesh.bounds,
            scale,
            transform.rotationXDegrees,
            transform.rotationYDegrees,
            transform.rotationZDegrees,
            transform.orientationMatrix
        )
        modelPlacementX = transform.centerXmm - activeBed.widthMm * 0.5f - rotatedCenter.xMm
        modelPlacementY = transform.centerYmm - activeBed.depthMm * 0.5f - rotatedCenter.yMm
        modelPlacementZ = -rotatedBounds.minZ
        modelRotationXDegrees = transform.rotationXDegrees
        modelRotationYDegrees = transform.rotationYDegrees
        modelRotationZDegrees = transform.rotationZDegrees
        modelUniformScale = scale
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, modelPlacementX, modelPlacementY, modelPlacementZ)
        if (transform.orientationMatrix?.size == 9) {
            Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, transform.orientationMatrix.toAndroidMatrix4(), 0)
        } else {
            Matrix.rotateM(modelMatrix, 0, modelRotationZDegrees, 0f, 0f, 1f)
            Matrix.rotateM(modelMatrix, 0, modelRotationYDegrees, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, modelRotationXDegrees, 1f, 0f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, modelUniformScale, modelUniformScale, modelUniformScale)
        val actualCenterX = modelPlacementX + (rotatedBounds.minX + rotatedBounds.maxX) * 0.5f
        val actualCenterY = modelPlacementY + (rotatedBounds.minY + rotatedBounds.maxY) * 0.5f
        camera.setModelScene(
            bed = activeBed,
            focusX = actualCenterX,
            focusY = actualCenterY,
            sizeX = rotatedBounds.sizeX,
            sizeY = rotatedBounds.sizeY,
            sizeZ = rotatedBounds.sizeZ
        )
    }

    private fun uploadPlateObjects(objects: List<ViewerPlateObject>) =
        objectUploadManager.uploadObjects(objects = objects, bed = activeBed, camera = camera)

    private fun updatePlateObjectTransforms(objects: List<ViewerPlateObject>) =
        objectUploadManager.updateTransforms(objects = objects, bed = activeBed)

    private fun uploadPaintOverlay(session: ViewerPaintSession?) {
        paintOverlayUploadManager.replaceAll(session?.overlay)
    }

    private fun drainPaintOverlayUploadQueue(session: ViewerPaintSession?) {
        if (!paintOverlayUploadManager.hasPending) return
        val startedAt = SystemClock.elapsedRealtime()
        val selectedObject = objectUploadManager.uploads
            .firstOrNull { it.id == session?.selectedObjectId }
        val selectedObjectMatrix = selectedObject
            ?.modelMatrix
            ?.copyOf()
        val appendedBaseUploads = mutableListOf<PaintOverlayUpload>()
        val appendedLiveUploads = mutableListOf<PaintOverlayUpload>()
        val appendedReplacementUploads = mutableListOf<PaintOverlayUpload>()
        var uploadedVertices = 0
        var uploadedLayers = 0
        while (
            paintOverlayUploadManager.hasPending &&
            uploadedLayers < PaintOverlayUploadLayerBudgetPerFrame
        ) {
            val pending = paintOverlayUploadManager.peekPending() ?: break
            val frameVertexBudget = if (pending.replacement) {
                PaintOverlayReplacementUploadVertexBudgetPerFrame
            } else {
                PaintOverlayUploadVertexBudgetPerFrame
            }
            if (uploadedVertices >= frameVertexBudget) break
            val layer = pending.layer
            if (layer.deleteOnly) {
                val deleteLayerIds = mutableSetOf<String>()
                while (
                    paintOverlayUploadManager.hasPending &&
                    uploadedLayers < PaintOverlayUploadLayerBudgetPerFrame
                ) {
                    val deletePending = paintOverlayUploadManager.peekPending() ?: break
                    if (!deletePending.layer.deleteOnly || deletePending.replacement) break
                    deleteLayerIds += deletePending.layer.id
                    paintOverlayUploadManager.removePendingHead()
                    uploadedLayers++
                }
                if (deleteLayerIds.isNotEmpty()) {
                    paintOverlayUploadManager.removeByLayerIds(deleteLayerIds, removeBase = true, removeLive = true)
                }
                continue
            }
            val overlayMatrix = layer.modelMatrix ?: selectedObjectMatrix
            if (overlayMatrix == null) {
                paintOverlayUploadManager.removePendingHead()
                continue
            }
            val totalVertices = minOf(layer.vertices.size, layer.normals.size) / 3
            if (pending.vertexOffset >= totalVertices) {
                paintOverlayUploadManager.removePendingHead()
                continue
            }
            if (!pending.replacement && pending.vertexOffset == 0 && totalVertices <= PaintLiveOverlayBatchVertexLimit) {
                val batch = mutableListOf<PendingPaintOverlayUpload>()
                var batchVertexCount = 0
                val layerSourceKey = layer.id.overlaySourceKey()
                val iterator = paintOverlayUploadManager.pendingIterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    val candidateLayer = candidate.layer
                    if (
                        candidate.layer.deleteOnly ||
                        candidate.live != pending.live ||
                        candidate.replacement ||
                        candidate.vertexOffset != 0 ||
                        candidateLayer.colorInt != layer.colorInt ||
                        candidateLayer.state != layer.state ||
                        candidateLayer.sourceBounds != layer.sourceBounds ||
                        !candidateLayer.modelMatrix.contentEqualsNullable(layer.modelMatrix) ||
                        candidateLayer.id.overlaySourceKey() != layerSourceKey
                    ) break
                    val candidateMatrix = candidateLayer.modelMatrix ?: selectedObjectMatrix ?: break
                    if (!candidateMatrix.contentEquals(overlayMatrix)) break
                    val candidateVertices = minOf(candidateLayer.vertices.size, candidateLayer.normals.size) / 3
                    if (candidateVertices <= 0 || candidateVertices > PaintLiveOverlayBatchVertexLimit) break
                    if (
                        uploadedVertices + batchVertexCount + candidateVertices > frameVertexBudget ||
                        uploadedLayers + batch.size + 1 > PaintOverlayUploadLayerBudgetPerFrame
                    ) break
                    batch += candidate
                    batchVertexCount += candidateVertices
                }
                if (batch.size >= 2 && batchVertexCount > 0) {
                    repeat(batch.size) { paintOverlayUploadManager.removePendingHead() }
                    val surfaceOffsetMm = if (pending.live && !pending.replacement) {
                        PaintLiveOverlaySurfaceOffsetMm
                    } else {
                        PaintOverlaySurfaceOffsetMm
                    }
                    val batchedVertices = FloatArray(batchVertexCount * 3)
                    val batchedNormals = FloatArray(batchVertexCount * 3)
                    var vertexOffset = 0
                    batch.forEach { batchedPending ->
                        val batchedLayer = batchedPending.layer
                        val candidateVertices = minOf(batchedLayer.vertices.size, batchedLayer.normals.size) / 3
                        val sourceVertices = batchedLayer.vertices.copyOfRange(0, candidateVertices * 3)
                        val sourceNormals = batchedLayer.normals.copyOfRange(0, candidateVertices * 3)
                        val alignedVertices = batchedLayer.sourceBounds?.let { sourceBounds ->
                            selectedObject?.let { objectUpload ->
                                alignNativeOverlayToViewerSource(
                                    vertices = sourceVertices,
                                    nativeBounds = sourceBounds,
                                    viewerBounds = objectUpload.mesh.bounds
                                )
                            }
                        } ?: sourceVertices
                        val vertices = offsetOverlayVertices(
                            vertices = alignedVertices,
                            normals = sourceNormals,
                            distanceMm = surfaceOffsetMm
                        )
                        vertices.copyInto(batchedVertices, destinationOffset = vertexOffset * 3)
                        sourceNormals.copyInto(batchedNormals, destinationOffset = vertexOffset * 3)
                        vertexOffset += candidateVertices
                    }
                    val batchLayerIds = batch.mapTo(mutableSetOf()) { it.layer.id }
                    val batchSourceKeys = batchLayerIds.mapNotNullTo(mutableSetOf()) { it.overlaySourceKey() }
                    val upload = PaintOverlayUpload(
                        id = "batch-${batch.first().layer.id}-${batch.size}",
                        upload = uploadTriangleData(vertices = batchedVertices, normals = batchedNormals),
                        color = viewerObjectColor(layer.colorInt).withAlpha(1.0f),
                        modelMatrix = overlayMatrix,
                        layerIds = batchLayerIds,
                        sourceKeys = batchSourceKeys
                    )
                    if (pending.live) {
                        appendedLiveUploads += upload
                    } else {
                        appendedBaseUploads += upload
                    }
                    uploadedVertices += batchVertexCount
                    uploadedLayers += batch.size
                    continue
                }
            }
            val remainingBudget = frameVertexBudget - uploadedVertices
            val remainingVertices = totalVertices - pending.vertexOffset
            val chunkVertices = minOf(remainingBudget, remainingVertices)
                .coerceAtLeast(0)
                .let { it - (it % 3) }
                .takeIf { it > 0 } ?: break
            val isFirstLiveChunk = pending.live && !pending.replacement && pending.vertexOffset == 0
            val start = pending.vertexOffset * 3
            val end = (pending.vertexOffset + chunkVertices) * 3
            val sourceVertices = layer.vertices.copyOfRange(start, end)
            val sourceNormals = layer.normals.copyOfRange(start, end)
            val surfaceOffsetMm = if (pending.live && !pending.replacement) {
                PaintLiveOverlaySurfaceOffsetMm
            } else {
                PaintOverlaySurfaceOffsetMm
            }
            val alignedVertices = layer.sourceBounds?.let { sourceBounds ->
                selectedObject?.let { objectUpload ->
                    alignNativeOverlayToViewerSource(
                        vertices = sourceVertices,
                        nativeBounds = sourceBounds,
                        viewerBounds = objectUpload.mesh.bounds
                    )
                }
            } ?: sourceVertices
            val vertices = offsetOverlayVertices(
                vertices = alignedVertices,
                normals = sourceNormals,
                distanceMm = surfaceOffsetMm
            )
            val layerIds = setOf(layer.id)
            val sourceKeys = setOfNotNull(layer.id.overlaySourceKey())
            val upload = PaintOverlayUpload(
                id = "${layer.id}:${pending.vertexOffset}",
                upload = uploadTriangleData(vertices = vertices, normals = sourceNormals),
                color = viewerObjectColor(layer.colorInt).withAlpha(1.0f),
                modelMatrix = overlayMatrix,
                layerIds = layerIds,
                sourceKeys = sourceKeys
            )
            pending.vertexOffset += chunkVertices
            val layerUploadComplete = pending.vertexOffset >= totalVertices
            if (pending.replacement) {
                appendedReplacementUploads += upload
            } else if (pending.live) {
                if (isFirstLiveChunk) {
                    paintOverlayUploadManager.removeByLayerIds(setOf(layer.id), removeBase = true, removeLive = true)
                }
                appendedLiveUploads += upload
            } else {
                appendedBaseUploads += upload
            }
            uploadedVertices += chunkVertices
            uploadedLayers++
            if (layerUploadComplete) {
                paintOverlayUploadManager.removePendingHead()
            }
        }
        if (appendedBaseUploads.isNotEmpty()) {
            paintOverlayUploadManager.appendBaseReplacing(appendedBaseUploads)
        }
        if (appendedLiveUploads.isNotEmpty()) {
            paintOverlayUploadManager.appendLiveReplacing(appendedLiveUploads)
        }
        if (appendedReplacementUploads.isNotEmpty()) {
            paintOverlayUploadManager.appendReplacement(appendedReplacementUploads)
        }
        paintOverlayUploadManager.swapReplacementIfReady()
        val appendedUploads = appendedBaseUploads + appendedLiveUploads + appendedReplacementUploads
        val uploadMs = SystemClock.elapsedRealtime() - startedAt
        val appendedVertexCount = appendedUploads.sumOf { it.upload.vertexCount }
        if (
            uploadMs >= 24L ||
            appendedVertexCount >= PaintOverlayUploadPerfLogVertexThreshold ||
            (appendedLiveUploads.isNotEmpty() && (uploadMs >= 8L || appendedLiveUploads.sumOf { it.upload.vertexCount } >= PaintLiveOverlayBatchVertexLimit))
        ) {
            Log.i(
                "MobileSlicerPaintPerf",
                    "overlay_upload_append ms=$uploadMs layers=${appendedUploads.size} " +
                    "vertices=$appendedVertexCount " +
                    "liveAppended=${appendedLiveUploads.size} " +
                    "baseLayers=${paintOverlayUploadManager.baseUploads.size} liveLayers=${paintOverlayUploadManager.liveUploads.size} " +
                    "replacement=${paintOverlayUploadManager.replacementCount}"
            )
        }
        if (paintOverlayUploadManager.hasPending) {
            synchronized(stateLock) {
                dirty = true
                stateLock.notifyAll()
            }
        }
    }

    private fun offsetOverlayVertices(
        vertices: FloatArray,
        normals: FloatArray,
        distanceMm: Float
    ): FloatArray {
        if (vertices.isEmpty() || normals.size != vertices.size || distanceMm == 0f) {
            return vertices
        }
        val offset = vertices.copyOf()
        var index = 0
        while (index + 2 < offset.size) {
            offset[index] += normals[index] * distanceMm
            offset[index + 1] += normals[index + 1] * distanceMm
            offset[index + 2] += normals[index + 2] * distanceMm
            index += 3
        }
        return offset
    }

    private fun alignNativeOverlayToViewerSource(
        vertices: FloatArray,
        nativeBounds: MeshBounds,
        viewerBounds: MeshBounds
    ): FloatArray {
        val nativeMaxSize = maxOf(nativeBounds.sizeX, nativeBounds.sizeY, nativeBounds.sizeZ)
        val viewerMaxSize = maxOf(viewerBounds.sizeX, viewerBounds.sizeY, viewerBounds.sizeZ)
        val scale = if (nativeMaxSize > 0.0001f && viewerMaxSize > 0.0001f) {
            (viewerMaxSize / nativeMaxSize).coerceIn(0.01f, 100f)
        } else {
            1f
        }
        val aligned = vertices.copyOf()
        var index = 0
        while (index + 2 < aligned.size) {
            aligned[index] = (aligned[index] - nativeBounds.centerX) * scale + viewerBounds.centerX
            aligned[index + 1] = (aligned[index + 1] - nativeBounds.centerY) * scale + viewerBounds.centerY
            aligned[index + 2] = (aligned[index + 2] - nativeBounds.centerZ) * scale + viewerBounds.centerZ
            index += 3
        }
        return aligned
    }

    private fun drawPaintOverlay() {
        if (activePaintSession == null) return
        if (paintOverlayUploadManager.baseUploads.isEmpty() && paintOverlayUploadManager.liveUploads.isEmpty()) return
        val startedAt = SystemClock.elapsedRealtime()
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-4f, -4f)
        try {
            paintOverlayUploadManager.baseUploads.forEach { overlay ->
                drawTriangles(
                    upload = overlay.upload,
                    color = overlay.color,
                    stabilizeSurface = false,
                    modelMatrixOverride = overlay.modelMatrix
                )
            }
            paintOverlayUploadManager.liveUploads.forEach { overlay ->
                drawTriangles(
                    upload = overlay.upload,
                    color = overlay.color,
                    stabilizeSurface = false,
                    modelMatrixOverride = overlay.modelMatrix
                )
            }
        } finally {
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glDepthFunc(GLES20.GL_LESS)
            GLES20.glDepthMask(true)
        }
        val drawMs = SystemClock.elapsedRealtime() - startedAt
        val now = SystemClock.elapsedRealtime()
        if (
            drawMs >= PaintOverlayDrawPerfLogMs &&
            now - paintOverlayLastDrawPerfLogAtMs >= PaintOverlayDrawPerfLogMinIntervalMs
        ) {
            paintOverlayLastDrawPerfLogAtMs = now
            Log.i(
                "MobileSlicerPaintPerf",
                "overlay_draw ms=$drawMs baseLayers=${paintOverlayUploadManager.baseUploads.size} " +
                    "liveLayers=${paintOverlayUploadManager.liveUploads.size} " +
                    "baseVertices=${paintOverlayUploadManager.baseUploads.sumOf { it.upload.vertexCount }} " +
                    "liveVertices=${paintOverlayUploadManager.liveUploads.sumOf { it.upload.vertexCount }} " +
                    "pending=${paintOverlayUploadManager.pendingCount}"
            )
        }
    }

    private fun drawPaintPreviewTrail(width: Int, height: Int) {
        val session = activePaintSession ?: return
        if (!activePaintCursorVisible || activePaintCursorTrail.isEmpty()) return
        val handles = triangleHandles ?: return
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val radiusPx = paintPreviewRadiusPx(session.brushRadiusMm, safeHeight)
        val pointCount = activePaintCursorTrail.size
        val circleVertexCount = pointCount * PaintPreviewCircleSegments * 3
        val connectorVertexCount = (pointCount - 1).coerceAtLeast(0) * 6
        val vertexCount = circleVertexCount + connectorVertexCount
        fun ndcX(screenX: Float): Float = screenX / safeWidth.toFloat() * 2f - 1f
        fun ndcY(screenY: Float): Float = 1f - screenY / safeHeight.toFloat() * 2f
        fun trailGeometry(radiusScale: Float): Pair<FloatArray, FloatArray> {
            val vertices = FloatArray(vertexCount * 3)
            val normals = FloatArray(vertexCount * 3)
            var out = 0
            fun appendVertex(x: Float, y: Float) {
                vertices[out] = x
                normals[out++] = 0f
                vertices[out] = y
                normals[out++] = 0f
                vertices[out] = 0f
	                normals[out++] = 1f
            }
            fun appendConnector(from: PaintCursorPoint, to: PaintCursorPoint, radiusPx: Float) {
                val dx = to.x - from.x
                val dy = to.y - from.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                if (length <= 0.001f) return
                val offsetX = -dy / length * radiusPx
                val offsetY = dx / length * radiusPx
                val fromLeftX = ndcX(from.x + offsetX)
                val fromLeftY = ndcY(from.y + offsetY)
                val fromRightX = ndcX(from.x - offsetX)
                val fromRightY = ndcY(from.y - offsetY)
                val toLeftX = ndcX(to.x + offsetX)
                val toLeftY = ndcY(to.y + offsetY)
                val toRightX = ndcX(to.x - offsetX)
                val toRightY = ndcY(to.y - offsetY)
                appendVertex(fromLeftX, fromLeftY)
                appendVertex(fromRightX, fromRightY)
                appendVertex(toLeftX, toLeftY)
                appendVertex(toLeftX, toLeftY)
                appendVertex(fromRightX, fromRightY)
                appendVertex(toRightX, toRightY)
            }
            val rx = radiusPx * radiusScale / safeWidth.toFloat() * 2f
            val ry = radiusPx * radiusScale / safeHeight.toFloat() * 2f
            var previousPoint: PaintCursorPoint? = null
            for (point in activePaintCursorTrail) {
                previousPoint?.let { appendConnector(it, point, radiusPx * radiusScale) }
                previousPoint = point
                val cx = ndcX(point.x)
                val cy = ndcY(point.y)
                for (segment in 0 until PaintPreviewCircleSegments) {
                    val a0 = Math.PI * 2.0 * segment.toDouble() / PaintPreviewCircleSegments.toDouble()
                    val a1 = Math.PI * 2.0 * (segment + 1).toDouble() / PaintPreviewCircleSegments.toDouble()
                    appendVertex(cx, cy)
                    appendVertex(cx + cos(a0).toFloat() * rx, cy + sin(a0).toFloat() * ry)
                    appendVertex(cx + cos(a1).toFloat() * rx, cy + sin(a1).toFloat() * ry)
                }
            }
            return vertices to normals
        }
        fun drawTrailPass(radiusScale: Float, color: FloatArray) {
            val (vertices, normals) = trailGeometry(radiusScale)
            GLES20.glVertexAttribPointer(handles.positionHandle, 3, GLES20.GL_FLOAT, false, 0, floatBufferOf(vertices))
            GLES20.glVertexAttribPointer(handles.normalHandle, 3, GLES20.GL_FLOAT, false, 0, floatBufferOf(normals))
            GLES20.glUniform4fv(handles.colorHandle, 1, color, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }
        GLES20.glUseProgram(triangleProgram)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnableVertexAttribArray(handles.positionHandle)
        GLES20.glEnableVertexAttribArray(handles.normalHandle)
        GLES20.glUniformMatrix4fv(handles.matrixHandle, 1, false, identityModelMatrix, 0)
        GLES20.glUniformMatrix4fv(handles.modelMatrixHandle, 1, false, identityModelMatrix, 0)
        GLES20.glUniform3f(handles.lightHandle, 0f, 0f, 1f)
        if (session.action == ViewerPaintAction.Erase) {
            drawTrailPass(radiusScale = 1.10f, color = viewerObjectColor(0xFF111827.toInt()).withAlpha(0.42f))
            drawTrailPass(radiusScale = 0.78f, color = viewerObjectColor(0xFFE5E7EB.toInt()).withAlpha(0.72f))
        } else {
            drawTrailPass(radiusScale = 1.0f, color = paintPreviewTrailColor(session).withAlpha(0.46f))
        }
        GLES20.glDisableVertexAttribArray(handles.positionHandle)
        GLES20.glDisableVertexAttribArray(handles.normalHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun FloatArray.withAlpha(alpha: Float): FloatArray =
        floatArrayOf(getOrElse(0) { 1f }, getOrElse(1) { 1f }, getOrElse(2) { 1f }, alpha)

    private fun paintPreviewTrailColor(session: ViewerPaintSession): FloatArray =
        when (session.mode) {
            ViewerPaintMode.Color -> session.activeColorInt?.let(::viewerObjectColor)
                ?: floatArrayOf(1f, 0.45f, 0.10f, 1f)
            ViewerPaintMode.Support -> viewerObjectColor(0xFF20B455.toInt())
            ViewerPaintMode.Seam -> viewerObjectColor(0xFF2F80ED.toInt())
            ViewerPaintMode.FuzzySkin -> viewerObjectColor(0xFF9C4DCC.toInt())
        }

    private fun paintPreviewRadiusPx(radiusMm: Float, viewportHeight: Int): Float {
        val pixelsPerMm = viewportHeight.toFloat().coerceAtLeast(1f) * projectionMatrix[5] * 0.5f
        return (radiusMm * pixelsPerMm).coerceIn(4f, 96f)
    }

    private fun updateCamera(width: Int, height: Int) {
        synchronized(stateLock) {
            camera.updateMatrices(
                width = width,
                height = height,
                bed = activeBed,
                projectionMatrix = projectionMatrix,
                viewMatrix = viewMatrix,
                viewProjectionMatrix = viewProjectionMatrix
            )
        }
    }

    private fun drawTriangles(
        upload: TriangleUpload,
        color: FloatArray,
        applyPlacement: Boolean = true,
        stabilizeSurface: Boolean = false,
        modelMatrixOverride: FloatArray? = null
    ) {
        val handles = triangleHandles ?: return
        drawTriangleUpload(
            programId = triangleProgram,
            handles = handles,
            upload = upload,
            color = color,
            viewProjectionMatrix = viewProjectionMatrix,
            modelMatrix = modelMatrix,
            identityModelMatrix = identityModelMatrix,
            applyPlacement = applyPlacement,
            stabilizeSurface = stabilizeSurface,
            modelMatrixOverride = modelMatrixOverride
        )
    }

    private fun buildProgramsIfNeeded() {
        if (triangleProgram == 0) {
            val program = ViewerTriangleProgram.create()
            triangleProgram = program.programId
            triangleHandles = program.handles
        }
        if (textureProgram == 0) {
            val program = ViewerTextureProgram.create()
            textureProgram = program.programId
            textureHandles = program.handles
        }
    }

    private fun updateAppearanceColors(appearance: ViewerAppearance) {
        viewerColors = buildViewerColors(appearance)
    }

    private fun fail(
        title: String,
        detail: String,
        previewRangeSuggestions: List<PreviewRangeSuggestion> = emptyList()
    ) {
        if (failure != null) return
        val newFailure = ViewerFailure(
            title = title,
            detail = detail,
            previewRangeSuggestions = previewRangeSuggestions
        )
        failure = newFailure
        onFailure(newFailure)
    }

    private fun destroyWindowSurface() {
        egl.destroyWindowSurface()
    }

    private fun releaseGcodeViewer() {
        gcodePreviewRenderer.release()
        activePreviewNativeLoadMs = 0L
        activePreviewNativeSelectedParseMs = 0L
        activePreviewNativeLibvgcodeLoadMs = 0L
        activePreviewNativeTotalLoadMs = 0L
        activePreviewNativeLoadedVertices = 0L
        activePreviewNativeCachedVertices = 0L
        activePreviewNativeCachedLayers = 0L
        activePreviewNativeCacheHit = 0L
        activePreviewNativeCacheBuilt = 0L
        activePreviewFirstFrameMs = -1L
        activePreviewRenderedFrames = 0L
        activePreviewSlowFrames = 0L
        activePreviewLastMetricsReportAtMs = 0L
        appliedGcodePreviewVersion = -1L
        appliedGcodeLayerRangeVersion = -1L
        appliedGcodePathVisibilityVersion = -1L
        appliedGcodeDisplayModeVersion = -1L
    }

    private fun releaseGlResources() {
        releaseGcodeViewer()
        if (triangleProgram != 0) {
            GLES20.glDeleteProgram(triangleProgram)
            triangleProgram = 0
            triangleHandles = null
        }
        if (textureProgram != 0) {
            GLES20.glDeleteProgram(textureProgram)
            textureProgram = 0
            textureHandles = null
        }
        bedSurfaceUpload?.let(::deleteTriangleUpload)
        bedSurfaceUpload = null
        bedModelUpload?.let(::deleteTriangleUpload)
        bedModelUpload = null
        bedModelUndersideUpload?.let(::deleteTriangleUpload)
        bedModelUndersideUpload = null
        bedTextureUpload?.let(::deleteTextureUpload)
        bedTextureUpload = null
        bedGridUpload?.let(::deleteTriangleUpload)
        bedGridUpload = null
        bedGridBoldUpload?.let(::deleteTriangleUpload)
        bedGridBoldUpload = null
        bedBorderUpload?.let(::deleteTriangleUpload)
        bedBorderUpload = null
        bedWallUpload?.let(::deleteTriangleUpload)
        bedWallUpload = null
        modelUpload?.let(::deleteTriangleUpload)
        modelUpload = null
        cutPlaneUpload?.let(::deleteTriangleUpload)
        cutPlaneUpload = null
        cutConnectorUpload?.let(::deleteTriangleUpload)
        cutConnectorUpload = null
        cutFaceFillUpload?.let(::deleteTriangleUpload)
        cutFaceFillUpload = null
        cutClippedObjectUpload?.let(::deleteTriangleUpload)
        cutClippedObjectUpload = null
        cutPlaneSessionUploadKey = null
        paintOverlayUploadManager.deleteAll()
        objectUploadManager.clear()
        GLES20.glUseProgram(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glFinish()
        appliedMeshVersion = -1L
        appliedPlateObjectsVersion = -1L
        appliedPlateObjectTransformVersion = -1L
        appliedPlateObjectSelectionVersion = -1L
        appliedGcodePreviewVersion = -1L
        appliedGcodeLayerRangeVersion = -1L
        appliedGcodePathVisibilityVersion = -1L
        appliedGcodeDisplayModeVersion = -1L
        appliedCutPlaneVersion = -1L
        appliedBedVersion = -1L
        appliedModelTransformVersion = -1L
        appliedAppearanceVersion = -1L
        appliedPaintSessionVersion = -1L
        appliedPaintOverlayReplaceVersion = -1L
        appliedPaintOverlayAppendVersion = -1L
        appliedPaintCursorVersion = -1L
        appliedPaintSessionOverlayKey = null
        renderedReadyMeshVersion = -1L
        firstFrameCleared = false
    }

    private fun destroyGl() {
        if (egl.hasDisplay) {
            if (egl.hasWindowSurface) {
                releaseGlResources()
                destroyWindowSurface()
            }
            egl.destroyContext()
        }
    }

    private companion object {
        private const val PreviewSlowFrameThresholdMs = 32L
        private const val PreviewMetricsReportIntervalMs = 1_000L
        private const val PreviewReloadCoalesceWindowMs = 40L
    }
}
