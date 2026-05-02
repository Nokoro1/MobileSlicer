package com.mobileslicer.viewer

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import android.view.Surface
import com.mobileslicer.nativebridge.NativeEngineCallResult

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
    private var pendingCameraState: ViewerCameraState? = null
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
    private var cameraStateVersion = 0L
    private var bedVersion = 0L
    private var modelTransformVersion = 0L
    private var appearanceVersion = 0L
    private var renderedReadyMeshVersion = -1L
    private var firstFrameCleared = false

    private val camera = ViewerCamera(pendingBed)
    private val objectUploadManager = WorkspaceObjectUploadManager()

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
    private var appliedMeshVersion = -1L
    private var appliedPlateObjectsVersion = -1L
    private var appliedPlateObjectTransformVersion = -1L
    private var appliedPlateObjectSelectionVersion = -1L
    private var appliedGcodePreviewVersion = -1L
    private var appliedGcodeLayerRangeVersion = -1L
    private var appliedGcodePathVisibilityVersion = -1L
    private var appliedGcodeDisplayModeVersion = -1L
    private var appliedCameraStateVersion = -1L
    private var appliedBedVersion = -1L
    private var appliedModelTransformVersion = -1L
    private var appliedAppearanceVersion = -1L

    private var bedSurfaceUpload: TriangleUpload? = null
    private var bedModelUpload: TriangleUpload? = null
    private var bedModelUndersideUpload: TriangleUpload? = null
    private var bedTextureUpload: TextureUpload? = null
    private var bedGridUpload: TriangleUpload? = null
    private var bedGridBoldUpload: TriangleUpload? = null
    private var bedBorderUpload: TriangleUpload? = null
    private var bedWallUpload: TriangleUpload? = null
    private var modelUpload: TriangleUpload? = null
    private var modelPlacementX = 0f
    private var modelPlacementY = 0f
    private var modelPlacementZ = 0f
    private var modelRotationXDegrees = 0f
    private var modelRotationYDegrees = 0f
    private var modelRotationZDegrees = 0f
    private var modelUniformScale = 1f
    private var viewerColors = buildViewerColors(activeAppearance)
    private var activePreviewNativeLoadMs = 0L
    private var activePreviewFirstFrameMs = -1L
    private var activePreviewRenderedFrames = 0L
    private var activePreviewSlowFrames = 0L
    private var activePreviewLastMetricsReportAtMs = 0L

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
            pendingGcodePreviewEngineHandle = safeEngineHandle
            pendingGcodePreviewKey = safePreviewKey
            pendingGcodePreviewVertexBudget = safeVertexBudget
            gcodePreviewVersion++
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
                    releaseGcodeViewer()
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
        val targetCameraStateVersion: Long
        val targetBedVersion: Long
        val targetModelTransformVersion: Long
        val targetAppearanceVersion: Long
        val targetCameraState: ViewerCameraState?
        synchronized(stateLock) {
            activeBed = pendingBed
            activeModelTransform = pendingModelTransform
            activePlateObjects = pendingPlateObjects
            activeAppearance = pendingAppearance
            activeMesh = pendingMesh
            activeGcodePreviewEngineHandle = pendingGcodePreviewEngineHandle
            activeGcodePreviewKey = pendingGcodePreviewKey
            activeGcodePreviewVertexBudget = pendingGcodePreviewVertexBudget
            activeGcodeLayerMin = pendingGcodeLayerMin
            activeGcodeLayerMax = pendingGcodeLayerMax
            activeGcodePreviewQueuedAtMs = pendingGcodePreviewQueuedAtMs
            activeGcodePathVisibility = pendingGcodePathVisibility
            activeGcodeDisplayMode = pendingGcodeDisplayMode
            targetMeshVersion = meshVersion
            targetPlateObjectsVersion = plateObjectsVersion
            targetPlateObjectTransformVersion = plateObjectTransformVersion
            targetPlateObjectSelectionVersion = plateObjectSelectionVersion
            targetGcodePreviewVersion = gcodePreviewVersion
            targetGcodeLayerRangeVersion = gcodeLayerRangeVersion
            targetGcodePathVisibilityVersion = gcodePathVisibilityVersion
            targetGcodeDisplayModeVersion = gcodeDisplayModeVersion
            targetCameraStateVersion = cameraStateVersion
            targetBedVersion = bedVersion
            targetModelTransformVersion = modelTransformVersion
            targetAppearanceVersion = appearanceVersion
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
                        previewVersion = targetGcodePreviewVersion,
                        layerRangeVersion = targetGcodeLayerRangeVersion,
                        pathVisibilityVersion = targetGcodePathVisibilityVersion,
                        displayModeVersion = targetGcodeDisplayModeVersion
                    )
                ) {
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
                    activeGcodePreviewVertexBudget
                )
                activePreviewNativeLoadMs = SystemClock.elapsedRealtime() - loadStartedAtMs
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
                        previewVersion = targetGcodePreviewVersion,
                        layerRangeVersion = targetGcodeLayerRangeVersion,
                        pathVisibilityVersion = targetGcodePathVisibilityVersion,
                        displayModeVersion = targetGcodeDisplayModeVersion
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

        if (appliedGcodePathVisibilityVersion != targetGcodePathVisibilityVersion) {
            if (gcodePreviewRenderer.isActive) {
                activeGcodePathVisibility.forEach { (key, visible) ->
                    gcodePreviewRenderer.setPathVisibility(kind = key.first, id = key.second, visible = visible)
                }
            }
            appliedGcodePathVisibilityVersion = targetGcodePathVisibilityVersion
        }

        if (appliedGcodeDisplayModeVersion != targetGcodeDisplayModeVersion) {
            if (gcodePreviewRenderer.isActive) {
                gcodePreviewRenderer.setDisplayMode(activeGcodeDisplayMode)
            }
            appliedGcodeDisplayModeVersion = targetGcodeDisplayModeVersion
        }

        if (appliedAppearanceVersion != targetAppearanceVersion) {
            updateAppearanceColors(activeAppearance)
            appliedAppearanceVersion = targetAppearanceVersion
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

    private fun isPreviewStateCurrent(
        previewVersion: Long,
        layerRangeVersion: Long,
        pathVisibilityVersion: Long,
        displayModeVersion: Long
    ): Boolean =
        synchronized(stateLock) {
            val isCurrent = ViewerUpdateDecisions.isGcodePreviewStateCurrent(
                expected = GcodePreviewStateVersions(
                    previewVersion = previewVersion,
                    layerRangeVersion = layerRangeVersion,
                    pathVisibilityVersion = pathVisibilityVersion,
                    displayModeVersion = displayModeVersion
                ),
                current = GcodePreviewStateVersions(
                    previewVersion = gcodePreviewVersion,
                    layerRangeVersion = gcodeLayerRangeVersion,
                    pathVisibilityVersion = gcodePathVisibilityVersion,
                    displayModeVersion = gcodeDisplayModeVersion
                )
            )
            if (!isCurrent) {
                dirty = true
                stateLock.notifyAll()
            }
            isCurrent
        }

    private fun renderFrame(width: Int, height: Int) {
        val frameStartedAtMs = SystemClock.elapsedRealtime()
        GLES20.glClearColor(viewerColors.clearColor[0], viewerColors.clearColor[1], viewerColors.clearColor[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        updateCamera(width = width, height = height)

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
                drawTriangles(
                    upload = objectUpload.upload,
                    color = objectUpload.colorInt?.let(::viewerObjectColor)
                        ?: if (objectUpload.selected) viewerColors.modelColor else viewerColors.dimmedModelColor(),
                    modelMatrixOverride = objectUpload.modelMatrix
                )
            }
            objectUploadManager.selectedFootprintUpload?.let {
                drawTriangles(upload = it, color = viewerColors.selectedFootprintColor, applyPlacement = false)
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
            rotationZDegrees = transform.rotationZDegrees
        )
        val rotatedBounds = transformedBounds(mesh.bounds, scale, transform.rotationXDegrees, transform.rotationYDegrees, transform.rotationZDegrees)
        modelPlacementX = transform.centerXmm - activeBed.widthMm * 0.5f - rotatedCenter.xMm
        modelPlacementY = transform.centerYmm - activeBed.depthMm * 0.5f - rotatedCenter.yMm
        modelPlacementZ = -rotatedBounds.minZ
        modelRotationXDegrees = transform.rotationXDegrees
        modelRotationYDegrees = transform.rotationYDegrees
        modelRotationZDegrees = transform.rotationZDegrees
        modelUniformScale = scale
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, modelPlacementX, modelPlacementY, modelPlacementZ)
        Matrix.rotateM(modelMatrix, 0, modelRotationXDegrees, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, modelRotationYDegrees, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, modelRotationZDegrees, 0f, 0f, 1f)
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
        activePreviewFirstFrameMs = -1L
        activePreviewRenderedFrames = 0L
        activePreviewSlowFrames = 0L
        activePreviewLastMetricsReportAtMs = 0L
        appliedGcodePreviewVersion = -1L
        appliedGcodeLayerRangeVersion = -1L
        appliedGcodePathVisibilityVersion = -1L
        appliedGcodeDisplayModeVersion = -1L
    }

    private fun destroyGl() {
        if (egl.hasDisplay) {
            if (egl.hasWindowSurface) {
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
                objectUploadManager.clear()
                destroyWindowSurface()
            }
            egl.destroyContext()
        }
    }

    private companion object {
        private const val PreviewSlowFrameThresholdMs = 32L
        private const val PreviewMetricsReportIntervalMs = 1_000L
    }
}
