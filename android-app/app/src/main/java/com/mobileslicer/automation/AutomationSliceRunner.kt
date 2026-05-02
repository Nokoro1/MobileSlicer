package com.mobileslicer.automation

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.isSuccess
import com.mobileslicer.workspace.GcodeSummaryParser
import com.mobileslicer.workspace.ModelImportTiming
import com.mobileslicer.workspace.NativeModelTransform
import com.mobileslicer.workspace.SlicePipelineTiming
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.workspaceResponsivenessLogLine
import com.mobileslicer.viewer.DefaultPreviewVertexBudget
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.parsePreviewRangePlan
import com.mobileslicer.viewer.StlMeshParser
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class AutomationSlicePaths(
    val modelPath: String,
    val outputPath: String,
    val statusPath: String
)

internal data class AutomationSliceRequest(
    val intent: Intent,
    val modelPath: String,
    val outputPath: String,
    val statusPath: String
) {
    companion object {
        const val ACTION_AUTOMATE_SLICE = "com.mobileslicer.action.AUTOMATE_SLICE"
        const val EXTRA_MODEL_PATH = "automation_model_path"
        const val EXTRA_OUTPUT_PATH = "automation_output_path"
        const val EXTRA_STATUS_PATH = "automation_status_path"

        fun fromIntent(intent: Intent): AutomationSliceRequest? {
            val paths = pathsFromValues(
                action = intent.action,
                modelPath = intent.getStringExtra(EXTRA_MODEL_PATH),
                outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH),
                statusPath = intent.getStringExtra(EXTRA_STATUS_PATH)
            ) ?: return null
            return AutomationSliceRequest(
                intent = intent,
                modelPath = paths.modelPath,
                outputPath = paths.outputPath,
                statusPath = paths.statusPath
            )
        }

        fun pathsFromValues(
            action: String?,
            modelPath: String?,
            outputPath: String?,
            statusPath: String?
        ): AutomationSlicePaths? {
            if (action != ACTION_AUTOMATE_SLICE || modelPath.isNullOrBlank() || outputPath.isNullOrBlank()) {
                return null
            }
            return AutomationSlicePaths(
                modelPath = modelPath,
                outputPath = outputPath,
                statusPath = statusPath ?: "$outputPath.status.txt"
            )
        }
    }
}

internal data class AutomationSliceTiming(
    val stagingMs: Long,
    val nativeLoadMs: Long,
    val placementMs: Long,
    val configMs: Long,
    val nativeSliceMs: Long,
    val writeGcodeMs: Long,
    val totalMs: Long
)

internal data class AutomationSliceNativeMetrics(
    val previewMoves: Long = 0L,
    val previewCacheBuilt: Boolean = false,
    val previewCacheComplete: Boolean = false,
    val previewCachedVertices: Long = 0L,
    val previewCacheBuildMs: Long = 0L,
    val gcodeBytes: Long = 0L,
    val processorMoveBytes: Long = 0L,
    val processorLineEndBytes: Long = 0L,
    val previewLayerCountBytes: Long = 0L,
    val exactPreviewCacheEligible: Boolean = false
)

internal data class AutomationSlicePreviewInfoMetrics(
    val summaryHasRichPreviewInfo: Boolean = false,
    val enrichedHasRichPreviewInfo: Boolean = false,
    val lineTypeCount: Int = 0,
    val filamentCount: Int = 0,
    val layerCount: Int = 0
)

internal data class AutomationSlicePreviewLoadMetrics(
    val rangePlanMs: Long = 0L,
    val viewerLoadMs: Long = 0L,
    val plannedRangeCount: Int = 0,
    val loadedStartLayer: Int = 0,
    val loadedEndLayer: Int = 0,
    val loadedLayerCount: Int = 0,
    val success: Boolean = false,
    val unavailableWithoutGlContext: Boolean = false,
    val failure: String = ""
)

internal fun automationSliceSuccessStatus(
    modelFile: File,
    stagedModel: File,
    outputFile: File,
    timing: AutomationSliceTiming,
    nativeMetrics: AutomationSliceNativeMetrics = AutomationSliceNativeMetrics(),
    previewInfoMetrics: AutomationSlicePreviewInfoMetrics = AutomationSlicePreviewInfoMetrics(),
    previewLoadMetrics: AutomationSlicePreviewLoadMetrics = AutomationSlicePreviewLoadMetrics(),
    configJson: String
): String =
    "success: model=${modelFile.absolutePath} " +
        "staged=${stagedModel.absolutePath} " +
        "output=${outputFile.absolutePath} " +
        "bytes=${outputFile.length()} " +
        "stagingMs=${timing.stagingMs} " +
        "nativeLoadMs=${timing.nativeLoadMs} " +
        "placementMs=${timing.placementMs} " +
        "configMs=${timing.configMs} " +
        "nativeSliceMs=${timing.nativeSliceMs} " +
        "writeGcodeMs=${timing.writeGcodeMs} " +
        "previewMoves=${nativeMetrics.previewMoves} " +
        "previewCacheBuilt=${if (nativeMetrics.previewCacheBuilt) 1 else 0} " +
        "previewCacheComplete=${if (nativeMetrics.previewCacheComplete) 1 else 0} " +
        "previewCachedVertices=${nativeMetrics.previewCachedVertices} " +
        "previewCacheBuildMs=${nativeMetrics.previewCacheBuildMs} " +
        "nativeGcodeBytes=${nativeMetrics.gcodeBytes} " +
        "processorMoveBytes=${nativeMetrics.processorMoveBytes} " +
        "processorLineEndBytes=${nativeMetrics.processorLineEndBytes} " +
        "previewLayerCountBytes=${nativeMetrics.previewLayerCountBytes} " +
        "exactPreviewCacheEligible=${if (nativeMetrics.exactPreviewCacheEligible) 1 else 0} " +
        "previewInfoRich=${if (previewInfoMetrics.summaryHasRichPreviewInfo) 1 else 0} " +
        "previewInfoEnrichedRich=${if (previewInfoMetrics.enrichedHasRichPreviewInfo) 1 else 0} " +
        "previewInfoLineTypes=${previewInfoMetrics.lineTypeCount} " +
        "previewInfoFilaments=${previewInfoMetrics.filamentCount} " +
        "previewInfoLayers=${previewInfoMetrics.layerCount} " +
        "previewPlanMs=${previewLoadMetrics.rangePlanMs} " +
        "previewLoadMs=${previewLoadMetrics.viewerLoadMs} " +
        "previewRanges=${previewLoadMetrics.plannedRangeCount} " +
        "previewLoadedStart=${previewLoadMetrics.loadedStartLayer} " +
        "previewLoadedEnd=${previewLoadMetrics.loadedEndLayer} " +
        "previewLoadedLayers=${previewLoadMetrics.loadedLayerCount} " +
        "previewLoadSuccess=${if (previewLoadMetrics.success) 1 else 0} " +
        "previewLoadGlUnavailable=${if (previewLoadMetrics.unavailableWithoutGlContext) 1 else 0} " +
        previewLoadMetrics.failure.takeIf { it.isNotBlank() }?.let { "previewLoadFailure=${it.sanitizeStatusToken()} " }.orEmpty() +
        "elapsedMs=${timing.totalMs} " +
        "config=$configJson"

internal fun parseAutomationSliceNativeMetrics(metricsText: String?): AutomationSliceNativeMetrics {
    if (metricsText.isNullOrBlank()) {
        return AutomationSliceNativeMetrics()
    }
    val fields = metricsText
        .split('|')
        .mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
        }
        .toMap()
    return AutomationSliceNativeMetrics(
        previewMoves = fields["previewMoves"]?.toLongOrNull() ?: 0L,
        previewCacheBuilt = fields["previewCacheBuilt"] == "1",
        previewCacheComplete = fields["previewCacheComplete"] == "1",
        previewCachedVertices = fields["previewCachedVertices"]?.toLongOrNull() ?: 0L,
        previewCacheBuildMs = fields["previewCacheBuildMs"]?.toLongOrNull() ?: 0L,
        gcodeBytes = fields["gcodeBytes"]?.toLongOrNull() ?: 0L,
        processorMoveBytes = fields["processorMoveBytes"]?.toLongOrNull() ?: 0L,
        processorLineEndBytes = fields["processorLineEndBytes"]?.toLongOrNull() ?: 0L,
        previewLayerCountBytes = fields["previewLayerCountBytes"]?.toLongOrNull() ?: 0L,
        exactPreviewCacheEligible = fields["exactPreviewCacheEligible"] == "1"
    )
}

internal fun automationSlicePreviewInfoMetrics(summaryText: String?, enrichedSummaryText: String?): AutomationSlicePreviewInfoMetrics {
    val summary = GcodeSummaryParser.fromNativeSummary(summaryText)
    val enrichedSummary = GcodeSummaryParser.fromNativeSummary(enrichedSummaryText)
    val richestSummary = listOfNotNull(enrichedSummary, summary)
        .maxByOrNull { it.previewInfo.lineTypes.size + it.previewInfo.filaments.size }
    return AutomationSlicePreviewInfoMetrics(
        summaryHasRichPreviewInfo = summary?.previewInfo?.hasRichData == true,
        enrichedHasRichPreviewInfo = enrichedSummary?.previewInfo?.hasRichData == true,
        lineTypeCount = richestSummary?.previewInfo?.lineTypes?.size ?: 0,
        filamentCount = richestSummary?.previewInfo?.filaments?.size ?: 0,
        layerCount = richestSummary?.layerChangeCount ?: 0
    )
}

internal fun automationSlicePreviewLoadMetrics(
    engineHandle: NativeEngineHandle,
    layerCount: Int,
    vertexBudget: Long = DefaultPreviewVertexBudget
): AutomationSlicePreviewLoadMetrics {
    if (layerCount <= 0) {
        return AutomationSlicePreviewLoadMetrics(failure = "missing-layers")
    }
    val planStartedAt = SystemClock.elapsedRealtime()
    val rawPlan = NativeEngineCalls.planLatestSlicePreviewRanges(
        handle = engineHandle,
        minLayer = 0L,
        maxLayer = (layerCount - 1).toLong(),
        vertexBudget = vertexBudget
    )
    val planMs = SystemClock.elapsedRealtime() - planStartedAt
    val ranges = parsePreviewRangePlan(rawPlan)
    val firstRange = ranges.firstOrNull()
        ?: return AutomationSlicePreviewLoadMetrics(
            rangePlanMs = planMs,
            failure = NativeEngineCalls.getLastErrorMessage(engineHandle).ifBlank { "no-preview-ranges" }
        )
    val loadedLayerCount = (firstRange.endLayer - firstRange.startLayer + 1).coerceAtLeast(0)
    return AutomationSlicePreviewLoadMetrics(
        rangePlanMs = planMs,
        plannedRangeCount = ranges.size,
        loadedStartLayer = firstRange.startLayer,
        loadedEndLayer = firstRange.endLayer,
        loadedLayerCount = loadedLayerCount,
        unavailableWithoutGlContext = true,
        failure = "non-ui-opengl-context-unavailable"
    )
}

private fun String.sanitizeStatusToken(): String =
    trim().replace(Regex("""\s+"""), "_").take(120)

internal class AutomationSliceRunner(
    private val ensureEngine: () -> Long,
    private val stageModelFile: (File) -> File?,
    private val resolveConfigJson: (Intent) -> String,
    private val onModelLoaded: (File) -> Unit,
    private val onModelLoadRejected: () -> Unit,
    private val nativeSliceFailureStatus: () -> String
) {
    fun run(request: AutomationSliceRequest): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val modelFile = File(request.modelPath)
        val outputFile = File(request.outputPath)
        val statusFile = File(request.statusPath)

        fun writeStatus(message: String) {
            Log.i(TAG, "automation:$message")
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(message, StandardCharsets.UTF_8)
        }

        val engineHandle = NativeEngineHandle.fromRaw(ensureEngine())
        if (engineHandle == null) {
            writeStatus("failed: engine unavailable")
            return false
        }
        if (!modelFile.exists()) {
            writeStatus("failed: model not found path=${request.modelPath}")
            return false
        }
        val stagingStartedAt = SystemClock.elapsedRealtime()
        val stagedModel = stageModelFile(modelFile)
        if (stagedModel == null) {
            writeStatus("failed: unable to stage model path=${request.modelPath}")
            return false
        }
        val stagingMs = SystemClock.elapsedRealtime() - stagingStartedAt
        val nativeLoadStartedAt = SystemClock.elapsedRealtime()
        val loadResult = NativeEngineCalls.loadModel(engineHandle, stagedModel.absolutePath)
        if (loadResult !is NativeEngineCallResult.Success) {
            NativeEngineCalls.clearGeneratedGcode(engineHandle)
            writeStatus("failed: ${loadResult.statusMessage} path=${request.modelPath} staged=${stagedModel.absolutePath}")
            onModelLoadRejected()
            return false
        }
        val nativeLoadMs = SystemClock.elapsedRealtime() - nativeLoadStartedAt
        onModelLoaded(stagedModel)

        val configJson = runCatching { resolveConfigJson(request.intent) }.getOrElse { exception ->
            Log.e(TAG, "automation:failed config resolution", exception)
            writeStatus("failed: config resolution ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return false
        }
        val placementStartedAt = SystemClock.elapsedRealtime()
        val placement = automationNativeTransform(stagedModel, configJson)
        if (
            placement != null &&
            !NativeEngineCalls.setModelTransform(
                engineHandle,
                placement.xMm,
                placement.yMm,
                placement.zMm,
                placement.rotationXRadians,
                placement.rotationYRadians,
                placement.rotationZRadians,
                placement.uniformScale
            ).isSuccess()
        ) {
            writeStatus("failed: nativeSetModelTransform rejected staged=${stagedModel.absolutePath}")
            return false
        }
        val placementMs = SystemClock.elapsedRealtime() - placementStartedAt

        val configStartedAt = SystemClock.elapsedRealtime()
        val configResult = NativeEngineCalls.setConfigJson(engineHandle, configJson)
        if (configResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${configResult.statusMessage} config=$configJson")
            return false
        }
        val configMs = SystemClock.elapsedRealtime() - configStartedAt

        val nativeSliceStartedAt = SystemClock.elapsedRealtime()
        var sliceResult = NativeEngineCalls.slice(engineHandle)
        if (
            sliceResult !is NativeEngineCallResult.Success &&
            (sliceResult as? NativeEngineCallResult.Failure)?.error?.message.equals("vector", ignoreCase = true)
        ) {
            Log.w(TAG, "automation:native slice failed with vector; retrying full native slice once on same engine")
            sliceResult = NativeEngineCalls.slice(engineHandle)
        }
        val nativeSliceMs = SystemClock.elapsedRealtime() - nativeSliceStartedAt
        if (sliceResult !is NativeEngineCallResult.Success) {
            writeStatus(
                "failed: ${sliceResult.statusMessage} fallback=${nativeSliceFailureStatus()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt} config=$configJson"
            )
            return false
        }

        outputFile.parentFile?.mkdirs()
        val writeGcodeStartedAt = SystemClock.elapsedRealtime()
        val writeResult = NativeEngineCalls.writeGcodeToFile(engineHandle, outputFile.absolutePath)
        val writeGcodeMs = SystemClock.elapsedRealtime() - writeGcodeStartedAt
        if (writeResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${writeResult.statusMessage} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            writeStatus("failed: nativeWriteGcodeToFile produced empty output elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        val nativeMetrics = parseAutomationSliceNativeMetrics(NativeEngineCalls.getSliceMetrics(engineHandle))
        val previewInfoMetrics = automationSlicePreviewInfoMetrics(
            summaryText = NativeEngineCalls.getGcodeSummary(engineHandle),
            enrichedSummaryText = NativeEngineCalls.getEnrichedGcodeSummary(engineHandle)
        )
        val previewLoadMetrics = automationSlicePreviewLoadMetrics(
            engineHandle = engineHandle,
            layerCount = previewInfoMetrics.layerCount
        )
        val totalMs = SystemClock.elapsedRealtime() - startedAt
        val timing = AutomationSliceTiming(
            stagingMs = stagingMs,
            nativeLoadMs = nativeLoadMs,
            placementMs = placementMs,
            configMs = configMs,
            nativeSliceMs = nativeSliceMs,
            writeGcodeMs = writeGcodeMs,
            totalMs = totalMs
        )
        Log.i(
            PERF_TAG,
            workspaceResponsivenessLogLine(
                eventName = "automation_slice_completed",
                importTiming = ModelImportTiming(stagingMs = stagingMs, nativeLoadMs = nativeLoadMs),
                workspacePreparationTiming = null,
                firstVisibleWorkspaceFrameMs = null,
                firstVisiblePreviewFrameMs = null,
                sliceTiming = SlicePipelineTiming(
                    modelReloadMs = 0L,
                    configMs = configMs,
                    nativeSliceMs = nativeSliceMs,
                    writeGcodeMs = writeGcodeMs,
                    summaryMs = 0L,
                    totalMs = totalMs
                )
            )
        )
        writeStatus(
            automationSliceSuccessStatus(
                modelFile = modelFile,
                stagedModel = stagedModel,
                outputFile = outputFile,
                timing = timing,
                nativeMetrics = nativeMetrics,
                previewInfoMetrics = previewInfoMetrics,
                previewLoadMetrics = previewLoadMetrics,
                configJson = configJson
            )
        )
        return true
    }

    private fun automationNativeTransform(stagedModel: File, configJson: String): NativeModelTransform? {
        if (!stagedModel.name.lowercase(Locale.US).endsWith(".stl")) {
            return null
        }
        val bounds = runCatching { StlMeshParser.parseBounds(stagedModel) }.getOrNull() ?: return null
        return defaultNativeModelTransform(
            bounds = bounds,
            printerBed = automationPrinterBed(configJson),
            modelTransform = null
        )
    }

    private fun automationPrinterBed(configJson: String): PrinterBedSpec =
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

    private companion object {
        private const val TAG = "MobileSlicer"
        private const val PERF_TAG = "MobileSlicerPerf"
    }
}
