package com.mobileslicer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintAction
import com.mobileslicer.nativebridge.NativePaintBrushShape
import com.mobileslicer.nativebridge.NativePaintCallResult
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.nativebridge.NativePaintMode
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintSession
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.workspace.NativeModelTransformInputStride
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.parseNativePaintOverlay
import com.mobileslicer.workspace.parseNativePaintOverlayDeltaInterleaved
import com.mobileslicer.workspace.writeTo
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PaintInteractionAutomationRequest(
    val modelPath: String,
    val statusPath: String
) {
    companion object {
        const val ACTION_PAINT_INTERACTION = "com.mobileslicer.action.PAINT_INTERACTION_PROOF"
        const val EXTRA_MODEL_PATH = "paint_proof_model_path"
        const val EXTRA_STATUS_PATH = "paint_proof_status_path"

        fun fromIntent(intent: Intent): PaintInteractionAutomationRequest? {
            if (intent.action != ACTION_PAINT_INTERACTION) return null
            val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)?.takeIf { it.isNotBlank() } ?: return null
            val statusPath = intent.getStringExtra(EXTRA_STATUS_PATH)
                ?.takeIf { it.isNotBlank() }
                ?: "$modelPath.paint-proof.status.txt"
            return PaintInteractionAutomationRequest(modelPath, statusPath)
        }
    }
}

internal fun MainActivity.maybeRunPaintInteractionAutomation(intent: Intent?): Boolean {
    if (!BuildConfig.AUTOMATION_ENABLED) {
        return false
    }
    val request = intent?.let(PaintInteractionAutomationRequest::fromIntent) ?: return false
    Log.i(MainActivity.TAG, "paint_proof:start model=${request.modelPath} status=${request.statusPath}")
    lifecycleScope.launch {
        val success = try {
            runPaintInteractionAutomation(request)
        } catch (throwable: Throwable) {
            Log.e(MainActivity.TAG, "paint_proof:failed unexpected", throwable)
            runCatching {
                File(request.statusPath).apply { parentFile?.mkdirs() }
                    .writeText(
                        "failed: unexpected ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}",
                        StandardCharsets.UTF_8
                    )
            }
            false
        }
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }
    return true
}

private suspend fun MainActivity.runPaintInteractionAutomation(
    request: PaintInteractionAutomationRequest
): Boolean {
    val statusFile = File(request.statusPath)
    fun writeStatus(message: String) {
        Log.i(MainActivity.TAG, "paint_proof:$message")
        runCatching {
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(message, StandardCharsets.UTF_8)
        }.onFailure { throwable ->
            Log.e(MainActivity.TAG, "paint_proof:status write failed path=${statusFile.absolutePath}", throwable)
        }
    }

    val startedAtMs = SystemClock.elapsedRealtime()
    val sourceModel = File(request.modelPath)
    if (!sourceModel.exists()) {
        writeStatus("failed: model not found path=${request.modelPath}")
        return false
    }
    val stagedModel = stageAutomationModelFile(sourceModel)
    if (stagedModel == null) {
        writeStatus("failed: unable to stage model path=${request.modelPath}")
        return false
    }
    val engine = NativeEngineHandle.fromRaw(ensureEngine())
    if (engine == null) {
        writeStatus("failed: native engine unavailable")
        return false
    }

    val mesh = withContext(Dispatchers.Default) { StlMeshParser.parseForDisplay(stagedModel).mesh }
    val artifactDir = statusFile.parentFile ?: filesDir
    runCatching {
        artifactDir.mkdirs()
        artifactDir.listFiles { file -> file.name.startsWith("paint-payload-") || file.name.startsWith("paint-overlay-") }
            ?.forEach { it.delete() }
    }
    val bed = PrinterBedSpec(widthMm = 220f, depthMm = 220f, maxHeightMm = 220f)
    val view = TouchModelViewerView(this)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        addDeprecatedLockScreenWakeFlags()
    }
    var lastRenderReady = false
    view.setRenderReadyListener { ready -> lastRenderReady = ready }
    view.setPrinterBed(bed)
    view.setViewerAppearance(darkTheme = true, accentColor = 0xFF8FC1FF.toInt(), worldColor = 0xFF101820.toInt())
    setContentView(view)

    val objectId = 7001L
    val scenarios = listOf(
        "translated" to ViewerModelTransform(centerXmm = 128f, centerYmm = 104f),
        "rotated" to ViewerModelTransform(centerXmm = 110f, centerYmm = 110f, rotationZDegrees = 35f),
        "scaled" to ViewerModelTransform(centerXmm = 110f, centerYmm = 110f, uniformScale = 1.35f),
        "oriented" to ViewerModelTransform(
            centerXmm = 110f,
            centerYmm = 110f,
            orientationMatrix = listOf(
                0f, -1f, 0f,
                1f, 0f, 0f,
                0f, 0f, 1f
            )
        )
    )
    val scenarioProofs = mutableListOf<String>()

    if (!waitForViewerSurfaceReady(view)) {
        writeStatus("failed: initial viewer surface not ready ${viewerDiagnostics(view, lastRenderReady)}")
        return false
    }

    for ((name, transform) in scenarios) {
        val transforms = DoubleArray(NativeModelTransformInputStride)
        defaultNativeModelTransform(mesh.bounds, bed, transform).writeTo(transforms)
        val loadResult = withContext(Dispatchers.Default) {
            NativeEngineCalls.loadPlateModelsV2(
                handle = engine,
                paths = arrayOf(stagedModel.absolutePath),
                transforms = transforms,
                extruderIds = intArrayOf(1),
                mobileObjectIds = longArrayOf(objectId),
                paintPayloadJson = ""
            )
        }
        if (loadResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: $name native load ${loadResult.statusMessage}")
            return false
        }
        view.setPlateObjects(
            listOf(
                ViewerPlateObject(
                    id = objectId,
                    label = name,
                    mesh = mesh,
                    transform = transform,
                    selected = true
                )
            )
        )
        view.setPaintSession(ViewerPaintSession(selectedObjectId = objectId, mode = ViewerPaintMode.Support))
        if (!waitForViewerSurfaceReady(view)) {
            writeStatus("failed: $name viewer surface not ready ${viewerDiagnostics(view, lastRenderReady)}")
            return false
        }
        delay(PaintAutomationSettleMs)

        val begin = NativePaintCalls.beginSession(engine, objectId, NativePaintMode.Support)
        if (!begin.succeeded) {
            writeStatus("failed: $name begin session ${paintResultMessage(begin)}")
            return false
        }
        val setToolOptions = NativePaintCalls.setToolOptions(engine)
        if (!setToolOptions.succeeded) {
            writeStatus("failed: $name set tool options ${paintResultMessage(setToolOptions)}")
            return false
        }
        val setTool = NativePaintCalls.setTool(
            handle = engine,
            brushShape = NativePaintBrushShape.Circle,
            action = NativePaintAction.Enforce,
            brushRadiusMm = 4f,
            brushHeightMm = 0.2f,
            colorSlot = 1
        )
        if (!setTool.succeeded) {
            writeStatus("failed: $name set tool ${paintResultMessage(setTool)}")
            return false
        }

        val hitPoint = waitForNativePaintHitPoint(view, engine, objectId)
        if (hitPoint == null) {
            writeStatus("failed: $name no native ray hit ${viewerDiagnostics(view, lastRenderReady)}")
            return false
        }
        val missPoint = 8f to 8f
        var strokeBegins = 0
        var strokeMoves = 0
        var strokeEnds = 0
        var publishedPayloadBytes = 0
        var overlayLayers = 0
        var overlayVertices = 0
        var overlayFootprint = "empty"
        view.setPaintHitTestListener { screenX, screenY ->
            val ray = view.paintRayForObject(screenX, screenY, objectId)?.values ?: return@setPaintHitTestListener false
            NativePaintCalls.hitTestRay(engine, ray) != null
        }
        view.setPaintStrokeListener(
            onBegin = { point ->
                val ray = view.paintRayForObject(point.x, point.y, objectId)?.values
                if (ray != null) {
                    val result = NativePaintCalls.strokeBeginRay(
                        handle = engine,
                        ray = ray,
                        brushShape = NativePaintBrushShape.Circle,
                        action = NativePaintAction.Enforce,
                        brushRadiusMm = 4f
                    )
                    if (result.succeeded) strokeBegins++
                }
            },
            onMove = { point ->
                val ray = view.paintRayForObject(point.x, point.y, objectId)?.values
                if (ray != null) {
                    val result = NativePaintCalls.strokeMoveRay(
                        handle = engine,
                        ray = ray,
                        brushShape = NativePaintBrushShape.Circle,
                        action = NativePaintAction.Enforce,
                        brushRadiusMm = 4f
                    )
                    if (result.succeeded) strokeMoves++
                }
            },
            onEnd = { committed ->
                if (NativePaintCalls.strokeEnd(engine, committed).succeeded) strokeEnds++
                val payload = NativePaintCalls.serialize(engine).orEmpty()
                publishedPayloadBytes = payload.length
                if (payload.isNotBlank()) {
                    writeProofArtifact(artifactDir, "paint-payload-scenario-$name-support.json", payload)
                }
                val overlay = parseNativePaintOverlayDeltaInterleaved(
                    NativePaintCalls.overlayInterleaved(engine),
                    ViewerPaintMode.Support,
                    emptyMap()
                ).let { snapshot ->
                    if (snapshot.layers.isNotEmpty()) {
                        snapshot
                    } else {
                        parseNativePaintOverlay(NativePaintCalls.overlay(engine))
                    }
                }
                overlayLayers = overlay.layers.size
                overlayVertices = overlay.layers.sumOf { it.vertices.size / 3 }
                overlayFootprint = overlayFootprintSummary(overlay)
                view.setPaintSession(
                    ViewerPaintSession(
                        selectedObjectId = objectId,
                        mode = ViewerPaintMode.Support,
                        overlay = overlay
                    )
                )
            }
        )

        dispatchDrag(view, hitPoint.first, hitPoint.second, hitPoint.first + 36f, hitPoint.second + 12f)
        delay(PaintAutomationSettleMs)
        val payloadAfterStroke = NativePaintCalls.serialize(engine).orEmpty()
        if (strokeBegins <= 0 || strokeEnds <= 0 || payloadAfterStroke.isBlank() || overlayLayers <= 0 || overlayVertices <= 0) {
            writeStatus(
                "failed: $name touch stroke incomplete " +
                    "begins=$strokeBegins moves=$strokeMoves ends=$strokeEnds " +
                    "payload=${payloadAfterStroke.length} overlayLayers=$overlayLayers overlayVertices=$overlayVertices"
            )
            return false
        }

        val beforeMissBegins = strokeBegins
        dispatchDrag(view, missPoint.first, missPoint.second, missPoint.first + 80f, missPoint.second + 20f)
        delay(PaintAutomationSettleMs)
        if (strokeBegins != beforeMissBegins) {
            writeStatus("failed: $name miss drag started paint beginBefore=$beforeMissBegins beginAfter=$strokeBegins")
            return false
        }

        val undo = NativePaintCalls.undo(engine)
        val afterUndoCanUndo = NativePaintCalls.canUndo(engine)
        val afterUndoCanRedo = NativePaintCalls.canRedo(engine)
        val redo = NativePaintCalls.redo(engine)
        val afterRedoCanUndo = NativePaintCalls.canUndo(engine)
        val afterRedoCanRedo = NativePaintCalls.canRedo(engine)
        val clear = NativePaintCalls.clear(engine)
        val afterClearCanUndo = NativePaintCalls.canUndo(engine)
        val afterClearCanRedo = NativePaintCalls.canRedo(engine)
        val clearPayloadBytes = NativePaintCalls.serialize(engine).orEmpty().length
        NativePaintCalls.endSession(engine, commit = true)
        if (!undo.succeeded || !redo.succeeded || !clear.succeeded) {
            writeStatus("failed: $name history command undo=${paintResultMessage(undo)} redo=${paintResultMessage(redo)} clear=${paintResultMessage(clear)}")
            return false
        }
        if (afterUndoCanUndo || !afterUndoCanRedo || !afterRedoCanUndo || afterRedoCanRedo || !afterClearCanUndo || afterClearCanRedo) {
            writeStatus(
                "failed: $name native history state " +
                    "afterUndo=$afterUndoCanUndo/$afterUndoCanRedo " +
                    "afterRedo=$afterRedoCanUndo/$afterRedoCanRedo " +
                    "afterClear=$afterClearCanUndo/$afterClearCanRedo"
            )
            return false
        }

        scenarioProofs +=
            "$name(hit=${hitPoint.first.toInt()},${hitPoint.second.toInt()} " +
                "begins=$strokeBegins moves=$strokeMoves ends=$strokeEnds " +
                "payload=$publishedPayloadBytes overlayLayers=$overlayLayers overlayVertices=$overlayVertices " +
                "overlayFootprint=$overlayFootprint " +
                "history=undo:$afterUndoCanUndo/$afterUndoCanRedo,redo:$afterRedoCanUndo/$afterRedoCanRedo,clear:$afterClearCanUndo/$afterClearCanRedo " +
                "clearPayload=$clearPayloadBytes)"
    }

    val modeProofs = mutableListOf<String>()
    val modeTransform = ViewerModelTransform(centerXmm = 110f, centerYmm = 110f)
    val modeTransforms = DoubleArray(NativeModelTransformInputStride)
    defaultNativeModelTransform(mesh.bounds, bed, modeTransform).writeTo(modeTransforms)
    val modeLoad = withContext(Dispatchers.Default) {
        NativeEngineCalls.loadPlateModelsV2(
            handle = engine,
            paths = arrayOf(stagedModel.absolutePath),
            transforms = modeTransforms,
            extruderIds = intArrayOf(1),
            mobileObjectIds = longArrayOf(objectId),
            paintPayloadJson = ""
        )
    }
    if (modeLoad !is NativeEngineCallResult.Success) {
        writeStatus("failed: mode overlay load ${modeLoad.statusMessage}")
        return false
    }
    view.setPlateObjects(
        listOf(
            ViewerPlateObject(
                id = objectId,
                label = "mode-proof",
                mesh = mesh,
                transform = modeTransform,
                selected = true
            )
        )
    )
    delay(PaintAutomationSettleMs)
    val modeProbeBegin = NativePaintCalls.beginSession(engine, objectId, NativePaintMode.Support)
    if (!modeProbeBegin.succeeded) {
        writeStatus("failed: mode overlay probe begin ${paintResultMessage(modeProbeBegin)}")
        return false
    }
    val modeHit = waitForNativePaintHitPoint(view, engine, objectId)
    NativePaintCalls.endSession(engine, commit = false)
    if (modeHit == null) {
        writeStatus("failed: mode overlay no native ray hit")
        return false
    }
    for (case in listOf(
        ModeCase("support-circle", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.Circle),
        ModeCase("support-sphere", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.Sphere),
        ModeCase("support-fill", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.SmartFill, smartFillAngleDeg = 35f),
        ModeCase("support-gap-fill", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.GapFill, smartFillAngleDeg = 35f),
        ModeCase("support-overhang", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.Circle, overhangAngleDeg = 45f),
        ModeCase("support-clip", NativePaintMode.Support, ViewerPaintMode.Support, NativePaintAction.Enforce, NativePaintBrushShape.Circle, clippingPlane = floatArrayOf(0f, 0f, 1f, mesh.bounds.maxZ + 0.001f)),
        ModeCase("seam-circle", NativePaintMode.Seam, ViewerPaintMode.Seam, NativePaintAction.Enforce, NativePaintBrushShape.Circle),
        ModeCase("seam-sphere", NativePaintMode.Seam, ViewerPaintMode.Seam, NativePaintAction.Enforce, NativePaintBrushShape.Sphere),
        ModeCase("seam-clip", NativePaintMode.Seam, ViewerPaintMode.Seam, NativePaintAction.Enforce, NativePaintBrushShape.Circle, clippingPlane = floatArrayOf(0f, 0f, 1f, mesh.bounds.maxZ + 0.001f)),
        ModeCase("color-circle", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.Circle),
        ModeCase("color-circle-slot4", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.Circle, colorSlot = 4),
        ModeCase("color-sphere", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.Sphere),
        ModeCase("color-triangle", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.Triangle),
        ModeCase("color-height-range", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.HeightRange),
        ModeCase("color-fill", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.Fill),
        ModeCase("color-gap-fill", NativePaintMode.Color, ViewerPaintMode.Color, NativePaintAction.Paint, NativePaintBrushShape.GapFill, smartFillAngleDeg = 35f),
        ModeCase("fuzzy-circle", NativePaintMode.FuzzySkin, ViewerPaintMode.FuzzySkin, NativePaintAction.Paint, NativePaintBrushShape.Circle),
        ModeCase("fuzzy-sphere", NativePaintMode.FuzzySkin, ViewerPaintMode.FuzzySkin, NativePaintAction.Paint, NativePaintBrushShape.Sphere),
        ModeCase("fuzzy-triangle", NativePaintMode.FuzzySkin, ViewerPaintMode.FuzzySkin, NativePaintAction.Paint, NativePaintBrushShape.Triangle),
        ModeCase("fuzzy-fill", NativePaintMode.FuzzySkin, ViewerPaintMode.FuzzySkin, NativePaintAction.Paint, NativePaintBrushShape.SmartFill, smartFillAngleDeg = 35f)
    )) {
        val (modeName, nativeMode, viewerMode, action, brushShape) = case
        val begin = NativePaintCalls.beginSession(engine, objectId, nativeMode)
        if (!begin.succeeded) {
            writeStatus("failed: $modeName begin ${paintResultMessage(begin)}")
            return false
        }
        val ray = view.paintRayForObject(modeHit.first, modeHit.second, objectId)?.values
        if (ray == null) {
            writeStatus("failed: $modeName ray unavailable")
            return false
        }
        val setToolOptions = NativePaintCalls.setToolOptions(
            handle = engine,
            smartFillAngleDeg = case.smartFillAngleDeg,
            overhangAngleDeg = case.overhangAngleDeg,
            clippingPlane = case.clippingPlane
        )
        if (!setToolOptions.succeeded) {
            writeStatus("failed: $modeName set tool options ${paintResultMessage(setToolOptions)}")
            return false
        }
        val setTool = NativePaintCalls.setTool(
            handle = engine,
            brushShape = brushShape,
            action = action,
            brushRadiusMm = 4f,
            brushHeightMm = 0.2f,
            colorSlot = if (nativeMode == NativePaintMode.Color) case.colorSlot else 1
        )
        if (!setTool.succeeded) {
            writeStatus("failed: $modeName set tool ${paintResultMessage(setTool)}")
            return false
        }
        val stroke = NativePaintCalls.strokeBeginRay(engine, ray, brushShape, action, 4f)
        val end = NativePaintCalls.strokeEnd(engine, commit = true)
        val overlay = parseNativePaintOverlayDeltaInterleaved(
            NativePaintCalls.overlayInterleaved(engine),
            viewerMode,
            automationFilamentSlotColors(),
            baseColorSlotIndex = 1
        ).let { snapshot ->
            if (snapshot.layers.isNotEmpty()) {
                snapshot
            } else {
                parseNativePaintOverlay(
                    NativePaintCalls.overlay(engine),
                    slotColors = automationFilamentSlotColors(),
                    baseColorSlotIndex = 1
                )
            }
        }
        val payload = NativePaintCalls.serialize(engine).orEmpty()
        if (payload.isNotBlank()) {
            writeProofArtifact(artifactDir, "paint-payload-mode-$modeName.json", payload)
        }
        if (nativeMode == NativePaintMode.Color && !payload.includesColorSlot(case.colorSlot)) {
            writeStatus("failed: $modeName payload did not preserve color slot ${case.colorSlot}")
            return false
        }
        if (nativeMode == NativePaintMode.Color && overlay.layers.none { it.state == case.colorSlot }) {
            writeStatus("failed: $modeName overlay did not include painted color slot ${case.colorSlot}")
            return false
        }
        view.setPaintSession(ViewerPaintSession(selectedObjectId = objectId, mode = viewerMode, overlay = overlay))
        NativePaintCalls.endSession(engine, commit = true)
        if (!stroke.succeeded || !end.succeeded || overlay.layers.isEmpty() || payload.isBlank()) {
            writeStatus(
                "failed: $modeName mode proof stroke=${paintResultMessage(stroke)} end=${paintResultMessage(end)} " +
                    "overlayLayers=${overlay.layers.size} payload=${payload.length}"
            )
            return false
        }
        modeProofs += "$modeName(payload=${payload.length} overlayLayers=${overlay.layers.size} overlayVertices=${overlay.layers.sumOf { it.vertices.size / 3 }})"
    }

    writeStatus(
        "success: model=${sourceModel.absolutePath} staged=${stagedModel.absolutePath} " +
            "payloadDir=${artifactDir.absolutePath} " +
            "scenarios=${scenarioProofs.joinToString(";")} " +
            "modes=${modeProofs.joinToString(";")} " +
            "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}"
    )
    return true
}

private data class ModeCase(
    val name: String,
    val nativeMode: NativePaintMode,
    val viewerMode: ViewerPaintMode,
    val action: NativePaintAction,
    val brushShape: NativePaintBrushShape,
    val smartFillAngleDeg: Float = 30f,
    val overhangAngleDeg: Float = 0f,
    val clippingPlane: FloatArray? = null,
    val colorSlot: Int = 2
)

private fun automationFilamentSlotColors(): Map<Int, Int> =
    mapOf(
        1 to 0xFF8FC1FF.toInt(),
        2 to 0xFFFF7043.toInt(),
        3 to 0xFFFFD166.toInt(),
        4 to 0xFFAB34D6.toInt()
    )

private fun String.includesColorSlot(slot: Int): Boolean =
    Regex(""""colorSlots"\s*:\s*\[([^\]]*)]""")
        .findAll(this)
        .any { match ->
            match.groupValues[1]
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .contains(slot)
        }

private fun overlayFootprintSummary(overlay: ViewerPaintOverlay): String {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    var vertexCount = 0
    overlay.layers.forEach { layer ->
        val vertices = layer.vertices
        var index = 0
        while (index + 2 < vertices.size) {
            val x = vertices[index]
            val y = vertices[index + 1]
            val z = vertices[index + 2]
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
            vertexCount++
            index += 3
        }
    }
    if (vertexCount == 0) return "empty"
    return "w=${String.format(Locale.US, "%.3f", maxX - minX)}," +
        "d=${String.format(Locale.US, "%.3f", maxY - minY)}," +
        "h=${String.format(Locale.US, "%.3f", maxZ - minZ)}"
}

private suspend fun waitForViewerSurfaceReady(view: TouchModelViewerView): Boolean {
    val deadline = SystemClock.elapsedRealtime() + PaintAutomationReadyTimeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (
            view.width > 0 &&
            view.height > 0 &&
            view.isAttachedToWindow &&
            view.holder.surface?.isValid == true
        ) {
            return true
        }
        delay(50)
    }
    return false
}

private suspend fun waitForNativePaintHitPoint(
    view: TouchModelViewerView,
    engine: NativeEngineHandle,
    objectId: Long
): Pair<Float, Float>? {
    val deadline = SystemClock.elapsedRealtime() + PaintAutomationReadyTimeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        findNativePaintHitPoint(view, engine, objectId)?.let { return it }
        delay(80)
    }
    return null
}

private fun viewerDiagnostics(view: TouchModelViewerView, renderReady: Boolean): String =
    "width=${view.width} height=${view.height} attached=${view.isAttachedToWindow} " +
        "shown=${view.isShown} surfaceValid=${view.holder.surface?.isValid == true} renderReady=$renderReady"

private fun findNativePaintHitPoint(
    view: TouchModelViewerView,
    engine: NativeEngineHandle,
    objectId: Long
): Pair<Float, Float>? {
    val width = view.width.takeIf { it > 0 } ?: 1440
    val height = view.height.takeIf { it > 0 } ?: 2560
    val divisions = 28
    for (yIndex in 3 until divisions - 2) {
        val yFraction = yIndex.toFloat() / divisions.toFloat()
        for (xIndex in 2 until divisions - 1) {
            val xFraction = xIndex.toFloat() / divisions.toFloat()
            val x = width * xFraction
            val y = height * yFraction
            val ray = view.paintRayForObject(x, y, objectId)?.values ?: continue
            if (NativePaintCalls.hitTestRay(engine, ray) != null) {
                return x to y
            }
        }
    }
    return null
}

private fun dispatchDrag(view: TouchModelViewerView, startX: Float, startY: Float, endX: Float, endY: Float) {
    val downAt = SystemClock.uptimeMillis()
    view.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, startX, startY, 0))
    val firstMoveAt = downAt + 16L
    view.dispatchTouchEvent(MotionEvent.obtain(downAt, firstMoveAt, MotionEvent.ACTION_MOVE, startX + 20f, startY + 8f, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(downAt, firstMoveAt + 16L, MotionEvent.ACTION_MOVE, endX, endY, 0))
    view.dispatchTouchEvent(MotionEvent.obtain(downAt, firstMoveAt + 32L, MotionEvent.ACTION_UP, endX, endY, 0))
}

private fun paintResultMessage(result: NativePaintCallResult): String =
    when (result) {
        NativePaintCallResult.Success -> "success"
        NativePaintCallResult.Unavailable -> "unavailable"
        is NativePaintCallResult.Failure -> result.message
    }

private fun writeProofArtifact(directory: File, name: String, contents: String) {
    runCatching {
        directory.mkdirs()
        File(directory, name).writeText(contents, StandardCharsets.UTF_8)
    }.onFailure { throwable ->
        Log.e(MainActivity.TAG, "paint_proof:artifact write failed name=$name", throwable)
    }
}

@Suppress("DEPRECATION")
private fun Activity.addDeprecatedLockScreenWakeFlags() {
    window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    )
}

private const val PaintAutomationReadyTimeoutMs = 5_000L
private const val PaintAutomationSettleMs = 180L
