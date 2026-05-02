package com.mobileslicer

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.automation.AutomationConfigResolver
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.viewer.GcodePreviewRuntimeMetrics
import com.mobileslicer.viewer.PreviewRangeSuggestion
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.parsePreviewRangePlan
import com.mobileslicer.workspace.GcodeSummaryParser
import com.mobileslicer.workspace.defaultNativeModelTransform
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal data class PreviewInteractionAutomationRequest(
    val intent: Intent,
    val modelPath: String,
    val statusPath: String
) {
    companion object {
        const val ACTION_PROFILE_PREVIEW = "com.mobileslicer.action.PROFILE_PREVIEW_INTERACTION"
        const val EXTRA_MODEL_PATH = "preview_profile_model_path"
        const val EXTRA_STATUS_PATH = "preview_profile_status_path"

        fun fromIntent(intent: Intent): PreviewInteractionAutomationRequest? {
            val paths = pathsFromValues(
                action = intent.action,
                modelPath = intent.getStringExtra(EXTRA_MODEL_PATH),
                statusPath = intent.getStringExtra(EXTRA_STATUS_PATH)
            ) ?: return null
            return PreviewInteractionAutomationRequest(
                intent = intent,
                modelPath = paths.first,
                statusPath = paths.second
            )
        }

        fun pathsFromValues(
            action: String?,
            modelPath: String?,
            statusPath: String?
        ): Pair<String, String>? {
            if (action != ACTION_PROFILE_PREVIEW || modelPath.isNullOrBlank()) {
                return null
            }
            return modelPath to (statusPath ?: "$modelPath.preview-profile.status.txt")
        }
    }
}

internal fun MainActivity.maybeRunPreviewInteractionAutomation(intent: Intent?): Boolean {
    if (intent?.action != PreviewInteractionAutomationRequest.ACTION_PROFILE_PREVIEW) {
        return false
    }
    val request = PreviewInteractionAutomationRequest.fromIntent(intent)
    if (request == null) {
        Log.e(MainActivity.TAG, "preview_profile:start missing modelPath")
        setResult(Activity.RESULT_CANCELED)
        finish()
        return true
    }

    lifecycleScope.launch {
        val success = runPreviewInteractionAutomation(request)
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }
    return true
}

private suspend fun MainActivity.runPreviewInteractionAutomation(
    request: PreviewInteractionAutomationRequest
): Boolean {
    val statusFile = File(request.statusPath)
    fun writeStatus(message: String) {
        Log.i(MainActivity.TAG, "preview_profile:$message")
        statusFile.parentFile?.mkdirs()
        statusFile.writeText(message, StandardCharsets.UTF_8)
    }

    val startedAtMs = SystemClock.elapsedRealtime()
    val engineHandle = NativeEngineHandle.fromRaw(ensureEngine())
    if (engineHandle == null) {
        writeStatus("failed: engine unavailable")
        return false
    }
    val modelFile = File(request.modelPath)
    if (!modelFile.exists()) {
        writeStatus("failed: model not found path=${request.modelPath}")
        return false
    }
    val stagedModel = stageAutomationModelFile(modelFile)
    if (stagedModel == null) {
        writeStatus("failed: unable to stage model path=${request.modelPath}")
        return false
    }

    val configJson = runCatching {
        AutomationConfigResolver(loadProfileStore = { loadProfileStore() }).resolve(request.intent)
    }.getOrElse { exception ->
        writeStatus("failed: config resolution ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
        return false
    }

    val slicePrepared = withContext(Dispatchers.Default) {
        preparePreviewProfileSlice(engineHandle, stagedModel, configJson)
    }
    if (slicePrepared.failure != null) {
        writeStatus("failed: ${slicePrepared.failure}")
        return false
    }

    val ranges = slicePrepared.previewRanges
    val firstRange = ranges.firstOrNull()
    if (firstRange == null) {
        writeStatus("failed: no preview ranges elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
        return false
    }

    val metrics = mutableListOf<GcodePreviewRuntimeMetrics>()
    val view = TouchModelViewerView(this)
    var nextReady = CompletableDeferred<Boolean>()
    view.setRenderReadyListener { ready ->
        if (ready && !nextReady.isCompleted) {
            nextReady.complete(true)
        }
    }
    view.setPreviewRuntimeMetricsListener { metric ->
        metrics += metric
        Log.i(
            "MobileSlicerPerf",
            "workspace_preview_runtime " +
                "previewKey=${metric.previewKey} " +
                "layerStart=${metric.layerStart} " +
                "layerEnd=${metric.layerEnd} " +
                "vertexBudget=${metric.vertexBudget} " +
                "nativeLoadMs=${metric.nativeLoadMs} " +
                "firstFrameMs=${metric.firstFrameMs} " +
                "lastFrameMs=${metric.lastFrameMs} " +
                "slowFrames=${metric.slowFrameCount} " +
                "frames=${metric.renderedFrameCount}"
        )
    }
    view.setPrinterBed(previewProfilePrinterBed(configJson))
    view.setViewerAppearance(darkTheme = true, accentColor = 0xFF8FC1FF.toInt(), worldColor = 0xFF101820.toInt())
    setContentView(view)

    fun queuePreviewRange(range: PreviewRangeSuggestion, token: Long) {
        nextReady = CompletableDeferred()
        view.setGcodePreviewSourceAndLayerRange(
            engineHandle = engineHandle.raw,
            previewKey = slicePrepared.previewKey,
            vertexBudget = GcodePreviewPerformanceMode.Default.vertexBudget,
            minLayer = (range.startLayer - 1).coerceAtLeast(0).toLong(),
            maxLayer = (range.endLayer - 1).coerceAtLeast(0).toLong(),
            reloadToken = token
        )
    }

    var reloadToken = 1L
    queuePreviewRange(firstRange, reloadToken)
    val firstReady = waitForPreviewReady(nextReady)
    delay(ProfileInteractionSettleMs)

    val firstMidLayer = ((firstRange.startLayer + firstRange.endLayer) / 2 - firstRange.startLayer).coerceAtLeast(0)
    view.setGcodeLayerRange(firstMidLayer.toLong(), firstMidLayer.toLong(), reloadToken)
    delay(ProfileInteractionSettleMs)
    view.setGcodeDisplayMode(GcodePreviewDisplayMode.FeatureType)
    delay(ProfileInteractionSettleMs)
    view.setGcodeDisplayMode(GcodePreviewDisplayMode.Speed)
    delay(ProfileInteractionSettleMs)

    var secondReady = true
    val secondRange = ranges.getOrNull(1)
    if (secondRange != null) {
        reloadToken++
        queuePreviewRange(secondRange, reloadToken)
        secondReady = waitForPreviewReady(nextReady)
        delay(ProfileInteractionSettleMs)
        val secondMidLayer = ((secondRange.startLayer + secondRange.endLayer) / 2 - secondRange.startLayer).coerceAtLeast(0)
        view.setGcodeLayerRange(secondMidLayer.toLong(), secondMidLayer.toLong(), reloadToken)
        delay(ProfileInteractionSettleMs)
    }

    val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
    val maxFirstFrameMs = metrics.maxOfOrNull { it.firstFrameMs } ?: 0L
    val maxNativeLoadMs = metrics.maxOfOrNull { it.nativeLoadMs } ?: 0L
    val maxFrameMs = metrics.maxOfOrNull { it.lastFrameMs } ?: 0L
    val maxSlowFrames = metrics.maxOfOrNull { it.slowFrameCount } ?: 0L
    val maxRenderedFrames = metrics.maxOfOrNull { it.renderedFrameCount } ?: 0L
    writeStatus(
        "success: model=${modelFile.absolutePath} " +
            "staged=${stagedModel.absolutePath} " +
            "previewKey=${slicePrepared.previewKey} " +
            "previewRanges=${ranges.size} " +
            "firstRangeLayers=${firstRange.layerCount()} " +
            "secondRangeLayers=${secondRange?.layerCount() ?: 0} " +
            "firstReady=${if (firstReady) 1 else 0} " +
            "secondReady=${if (secondReady) 1 else 0} " +
            "metrics=${metrics.size} " +
            "maxNativeLoadMs=$maxNativeLoadMs " +
            "maxFirstFrameMs=$maxFirstFrameMs " +
            "maxFrameMs=$maxFrameMs " +
            "slowFrames=$maxSlowFrames " +
            "renderedFrames=$maxRenderedFrames " +
            "elapsedMs=$elapsedMs"
    )
    return firstReady && secondReady && metrics.isNotEmpty()
}

private data class PreviewProfileSlicePreparation(
    val previewKey: Long = 0L,
    val previewRanges: List<PreviewRangeSuggestion> = emptyList(),
    val failure: String? = null
)

private fun MainActivity.preparePreviewProfileSlice(
    engineHandle: NativeEngineHandle,
    stagedModel: File,
    configJson: String
): PreviewProfileSlicePreparation {
    val loadResult = NativeEngineCalls.loadModel(engineHandle, stagedModel.absolutePath)
    if (loadResult !is NativeEngineCallResult.Success) {
        return PreviewProfileSlicePreparation(failure = loadResult.statusMessage)
    }

    automationNativeTransform(stagedModel, configJson)?.let { placement ->
        val transformResult = NativeEngineCalls.setModelTransform(
            engineHandle,
            placement.xMm,
            placement.yMm,
            placement.zMm,
            placement.rotationXRadians,
            placement.rotationYRadians,
            placement.rotationZRadians,
            placement.uniformScale
        )
        if (transformResult !is NativeEngineCallResult.Success) {
            return PreviewProfileSlicePreparation(failure = transformResult.statusMessage)
        }
    }
    val configResult = NativeEngineCalls.setConfigJson(engineHandle, configJson)
    if (configResult !is NativeEngineCallResult.Success) {
        return PreviewProfileSlicePreparation(failure = configResult.statusMessage)
    }
    val sliceResult = NativeEngineCalls.slice(engineHandle)
    if (sliceResult !is NativeEngineCallResult.Success) {
        return PreviewProfileSlicePreparation(failure = sliceResult.statusMessage)
    }

    val summary = GcodeSummaryParser.fromNativeSummary(NativeEngineCalls.getEnrichedGcodeSummary(engineHandle))
        ?: GcodeSummaryParser.fromNativeSummary(NativeEngineCalls.getGcodeSummary(engineHandle))
    val layerCount = summary?.layerChangeCount ?: 0
    if (layerCount <= 0) {
        return PreviewProfileSlicePreparation(failure = "missing preview layer count")
    }
    val ranges = parsePreviewRangePlan(
        NativeEngineCalls.planLatestSlicePreviewRanges(
            handle = engineHandle,
            minLayer = 0L,
            maxLayer = (layerCount - 1).toLong(),
            vertexBudget = GcodePreviewPerformanceMode.Default.vertexBudget
        )
    )
    if (ranges.isEmpty()) {
        return PreviewProfileSlicePreparation(
            failure = NativeEngineCalls.getLastErrorMessage(engineHandle).ifBlank { "no preview ranges" }
        )
    }
    return PreviewProfileSlicePreparation(
        previewKey = SystemClock.elapsedRealtime(),
        previewRanges = ranges
    )
}

private suspend fun waitForPreviewReady(ready: CompletableDeferred<Boolean>): Boolean =
    withTimeoutOrNull(ProfilePreviewReadyTimeoutMs) {
        ready.await()
    } == true

private fun automationNativeTransform(stagedModel: File, configJson: String): com.mobileslicer.workspace.NativeModelTransform? {
    if (!stagedModel.name.lowercase(Locale.US).endsWith(".stl")) {
        return null
    }
    val bounds = runCatching { StlMeshParser.parseBounds(stagedModel) }.getOrNull() ?: return null
    return defaultNativeModelTransform(
        bounds = bounds,
        printerBed = previewProfilePrinterBed(configJson),
        modelTransform = null
    )
}

private fun previewProfilePrinterBed(configJson: String): PrinterBedSpec =
    PrinterBedSpec(
        widthMm = jsonFloat(configJson, "bed_width_mm") ?: 270f,
        depthMm = jsonFloat(configJson, "bed_depth_mm") ?: 270f,
        maxHeightMm = jsonFloat(configJson, "max_height_mm") ?: 256f
    )

private fun jsonFloat(json: String, key: String): Float? =
    runCatching {
        val value = JSONObject(json).optDouble(key, Double.NaN)
        value.takeIf { it.isFinite() }?.toFloat()
    }.getOrNull()

private fun PreviewRangeSuggestion.layerCount(): Int =
    (endLayer - startLayer + 1).coerceAtLeast(0)

private const val ProfileInteractionSettleMs = 450L
private const val ProfilePreviewReadyTimeoutMs = 15_000L
