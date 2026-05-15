package com.mobileslicer.automation

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.workspace.GcodeSummaryParser
import com.mobileslicer.workspace.ModelImportTiming
import com.mobileslicer.workspace.NativeModelTransform
import com.mobileslicer.workspace.NativeModelTransformInputStride
import com.mobileslicer.workspace.OffscreenEglSliceThumbnailRenderer
import com.mobileslicer.workspace.SlicePipelineTiming
import com.mobileslicer.workspace.SourceModelFileFormat
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.detectSourceModelFileFormat
import com.mobileslicer.workspace.nativeModelTransformToViewerTransform
import com.mobileslicer.workspace.renderSliceThumbnails
import com.mobileslicer.workspace.writeTo
import com.mobileslicer.workspace.workspaceResponsivenessLogLine
import com.mobileslicer.viewer.DefaultPreviewVertexBudget
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.parsePreviewRangePlan
import com.mobileslicer.viewer.StlMeshParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val AUTOMATION_STEP_LINEAR_DEFLECTION = 0.003
private const val AUTOMATION_STEP_ANGLE_DEFLECTION = 0.5

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
        const val EXTRA_PRESERVE_PROJECT_OBJECTS = "automation_preserve_project_objects"
        const val EXTRA_TWO_FILAMENT_OBJECTS = "automation_two_filament_objects"
        const val EXTRA_MULTI_PLATE_PACKAGE = "automation_multi_plate_package"
        const val EXTRA_EXPORT_PROJECT_3MF = "automation_export_project_3mf"

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
    val thumbnailMs: Long,
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
    val exactPreviewCacheEligible: Boolean = false,
    val processorMovesReleasedDuringExport: Boolean = false,
    val processorMoveBytesRetained: Long = 0L,
    val processorLineEndBytesRetained: Long = 0L,
    val processorReleaseMs: Long = 0L,
    val nativeExportStartRssKb: Long = 0L,
    val nativeAfterSetupRssKb: Long = 0L,
    val nativeAfterLayersRssKb: Long = 0L,
    val nativeAfterFooterRssKb: Long = 0L,
    val nativeAfterGenerationRssKb: Long = 0L,
    val nativeAfterFinalizeRssKb: Long = 0L,
    val nativeAfterReleaseRssKb: Long = 0L,
    val nativeAfterStatsRssKb: Long = 0L,
    val nativeBeforeReturnRssKb: Long = 0L
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
    val requestedLayerCount: Int = 0,
    val loadedStartLayer: Int = 0,
    val loadedEndLayer: Int = 0,
    val loadedLayerCount: Int = 0,
    val plannedCoveredLayers: Int = 0,
    val vertexBudget: Long = 0L,
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
    configJson: String,
    thumbnailAudit: String = ""
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
        "thumbnailMs=${timing.thumbnailMs} " +
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
        "processorMovesReleasedDuringExport=${if (nativeMetrics.processorMovesReleasedDuringExport) 1 else 0} " +
        "processorMoveBytesRetained=${nativeMetrics.processorMoveBytesRetained} " +
        "processorLineEndBytesRetained=${nativeMetrics.processorLineEndBytesRetained} " +
        "processorReleaseMs=${nativeMetrics.processorReleaseMs} " +
        "nativeExportStartRssKb=${nativeMetrics.nativeExportStartRssKb} " +
        "nativeAfterSetupRssKb=${nativeMetrics.nativeAfterSetupRssKb} " +
        "nativeAfterLayersRssKb=${nativeMetrics.nativeAfterLayersRssKb} " +
        "nativeAfterFooterRssKb=${nativeMetrics.nativeAfterFooterRssKb} " +
        "nativeAfterGenerationRssKb=${nativeMetrics.nativeAfterGenerationRssKb} " +
        "nativeAfterFinalizeRssKb=${nativeMetrics.nativeAfterFinalizeRssKb} " +
        "nativeAfterReleaseRssKb=${nativeMetrics.nativeAfterReleaseRssKb} " +
        "nativeAfterStatsRssKb=${nativeMetrics.nativeAfterStatsRssKb} " +
        "nativeBeforeReturnRssKb=${nativeMetrics.nativeBeforeReturnRssKb} " +
        "previewInfoRich=${if (previewInfoMetrics.summaryHasRichPreviewInfo) 1 else 0} " +
        "previewInfoEnrichedRich=${if (previewInfoMetrics.enrichedHasRichPreviewInfo) 1 else 0} " +
        "previewInfoLineTypes=${previewInfoMetrics.lineTypeCount} " +
        "previewInfoFilaments=${previewInfoMetrics.filamentCount} " +
        "previewInfoLayers=${previewInfoMetrics.layerCount} " +
        "previewPlanMs=${previewLoadMetrics.rangePlanMs} " +
        "previewLoadMs=${previewLoadMetrics.viewerLoadMs} " +
        "previewRanges=${previewLoadMetrics.plannedRangeCount} " +
        "previewRequestedLayers=${previewLoadMetrics.requestedLayerCount} " +
        "previewLoadedStart=${previewLoadMetrics.loadedStartLayer} " +
        "previewLoadedEnd=${previewLoadMetrics.loadedEndLayer} " +
        "previewLoadedLayers=${previewLoadMetrics.loadedLayerCount} " +
        "previewPlannedCoveredLayers=${previewLoadMetrics.plannedCoveredLayers} " +
        "previewVertexBudget=${previewLoadMetrics.vertexBudget} " +
        "previewLoadSuccess=${if (previewLoadMetrics.success) 1 else 0} " +
        "previewLoadGlUnavailable=${if (previewLoadMetrics.unavailableWithoutGlContext) 1 else 0} " +
        previewLoadMetrics.failure.takeIf { it.isNotBlank() }?.let { "previewLoadFailure=${it.sanitizeStatusToken()} " }.orEmpty() +
        thumbnailAudit.takeIf { it.isNotBlank() }?.let { "thumbnailAudit=${it.sanitizeStatusToken()} " }.orEmpty() +
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
        exactPreviewCacheEligible = fields["exactPreviewCacheEligible"] == "1",
        processorMovesReleasedDuringExport = fields["processorMovesReleasedDuringExport"] == "1",
        processorMoveBytesRetained = fields["processorMoveBytesRetained"]?.toLongOrNull() ?: 0L,
        processorLineEndBytesRetained = fields["processorLineEndBytesRetained"]?.toLongOrNull() ?: 0L,
        processorReleaseMs = fields["processorReleaseMs"]?.toLongOrNull() ?: 0L,
        nativeExportStartRssKb = fields["nativeExportStartRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterSetupRssKb = fields["nativeAfterSetupRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterLayersRssKb = fields["nativeAfterLayersRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterFooterRssKb = fields["nativeAfterFooterRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterGenerationRssKb = fields["nativeAfterGenerationRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterFinalizeRssKb = fields["nativeAfterFinalizeRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterReleaseRssKb = fields["nativeAfterReleaseRssKb"]?.toLongOrNull() ?: 0L,
        nativeAfterStatsRssKb = fields["nativeAfterStatsRssKb"]?.toLongOrNull() ?: 0L,
        nativeBeforeReturnRssKb = fields["nativeBeforeReturnRssKb"]?.toLongOrNull() ?: 0L
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
        return AutomationSlicePreviewLoadMetrics(failure = "missing-layers", vertexBudget = vertexBudget)
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
            requestedLayerCount = layerCount,
            vertexBudget = vertexBudget,
            failure = NativeEngineCalls.getLastErrorMessage(engineHandle).ifBlank { "no-preview-ranges" }
        )
    val loadedLayerCount = (firstRange.endLayer - firstRange.startLayer + 1).coerceAtLeast(0)
    return AutomationSlicePreviewLoadMetrics(
        rangePlanMs = planMs,
        plannedRangeCount = ranges.size,
        requestedLayerCount = layerCount,
        loadedStartLayer = firstRange.startLayer,
        loadedEndLayer = firstRange.endLayer,
        loadedLayerCount = loadedLayerCount,
        plannedCoveredLayers = ranges.sumOf { range ->
            (range.endLayer - range.startLayer + 1).coerceAtLeast(0)
        },
        vertexBudget = vertexBudget,
        unavailableWithoutGlContext = true,
        failure = "non-ui-opengl-context-unavailable"
    )
}

private fun String.sanitizeStatusToken(): String =
    trim().replace(Regex("""\s+"""), "_").take(120)

internal class AutomationSliceRunner(
    private val ensureEngine: () -> Long,
    private val resetEngineForRecovery: () -> Unit,
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

        var engineHandle = NativeEngineHandle.fromRaw(ensureEngine())
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
        val exportProject3mf = request.intent.getBooleanExtra(
            AutomationSliceRequest.EXTRA_EXPORT_PROJECT_3MF,
            false
        )
        if (exportProject3mf) {
            return runProject3mfRoundTripExport(
                request = request,
                modelFile = modelFile,
                stagedModel = stagedModel,
                outputFile = outputFile,
                startedAt = startedAt,
                stagingMs = stagingMs,
                engineHandle = engineHandle,
                writeStatus = ::writeStatus
            )
        }
        val preparedModel = prepareAutomationWorkspaceModel(engineHandle, stagedModel, ::writeStatus) ?: run {
            onModelLoadRejected()
            return false
        }
        val preserveProjectObjects = request.intent.getBooleanExtra(
            AutomationSliceRequest.EXTRA_PRESERVE_PROJECT_OBJECTS,
            false
        )
        val sourceFormat = detectSourceModelFileFormat(stagedModel.name)
        val modelToLoad = if (preserveProjectObjects && sourceFormat == SourceModelFileFormat.ThreeMf) {
            stagedModel
        } else {
            preparedModel
        }
        val configJson = runCatching { resolveConfigJson(request.intent) }.getOrElse { exception ->
            Log.e(TAG, "automation:failed config resolution", exception)
            writeStatus("failed: config resolution ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return false
        }
        val placementStartedAt = SystemClock.elapsedRealtime()
        val placement = automationNativeTransform(preparedModel, configJson) ?: run {
            onModelLoadRejected()
            writeStatus("failed: model load failed: native plate transform could not be computed staged=${preparedModel.absolutePath}")
            return false
        }
        val placementSummary = placement.let {
            String.format(
                Locale.US,
                " placement=(%.6f,%.6f,%.6f;rx=%.6f,ry=%.6f,rz=%.6f;s=%.6f)",
                it.xMm,
                it.yMm,
                it.zMm,
                it.rotationXRadians,
                it.rotationYRadians,
                it.rotationZRadians,
                it.uniformScale
            )
        }.orEmpty()
        val transforms = DoubleArray(NativeModelTransformInputStride)
        placement.writeTo(transforms, stride = NativeModelTransformInputStride)
        val placementMs = SystemClock.elapsedRealtime() - placementStartedAt

        val twoFilamentObjects = request.intent.getBooleanExtra(
            AutomationSliceRequest.EXTRA_TWO_FILAMENT_OBJECTS,
            false
        )
        val loadPaths: Array<String>
        val loadTransforms: DoubleArray
        val extruderIds: IntArray
        val mobileObjectIds: LongArray
        if (twoFilamentObjects) {
            val spacingMm = 24.0
            loadPaths = arrayOf(modelToLoad.absolutePath, modelToLoad.absolutePath)
            loadTransforms = DoubleArray(NativeModelTransformInputStride * 2)
            placement.copy(xMm = placement.xMm - spacingMm / 2.0)
                .writeTo(loadTransforms, offset = 0, stride = NativeModelTransformInputStride)
            placement.copy(xMm = placement.xMm + spacingMm / 2.0)
                .writeTo(loadTransforms, offset = NativeModelTransformInputStride, stride = NativeModelTransformInputStride)
            extruderIds = intArrayOf(1, 2)
            mobileObjectIds = longArrayOf(1L, 2L)
        } else {
            loadPaths = arrayOf(modelToLoad.absolutePath)
            loadTransforms = transforms
            extruderIds = intArrayOf(1)
            mobileObjectIds = longArrayOf(1L)
        }
        val nativeLoadStartedAt = SystemClock.elapsedRealtime()
        val loadResult = NativeEngineCalls.loadPlateModelsV2(
            handle = engineHandle,
            paths = loadPaths,
            transforms = loadTransforms,
            extruderIds = extruderIds,
            mobileObjectIds = mobileObjectIds,
            paintPayloadJson = ""
        )
        if (loadResult !is NativeEngineCallResult.Success) {
            NativeEngineCalls.clearGeneratedGcode(engineHandle)
            writeStatus("failed: ${loadResult.statusMessage} path=${request.modelPath} staged=${preparedModel.absolutePath} loaded=${modelToLoad.absolutePath}")
            onModelLoadRejected()
            return false
        }
        val nativeLoadMs = SystemClock.elapsedRealtime() - nativeLoadStartedAt
        onModelLoaded(preparedModel)

        val configStartedAt = SystemClock.elapsedRealtime()
        val configResult = NativeEngineCalls.setConfigJson(engineHandle, configJson)
        if (configResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${configResult.statusMessage} config=$configJson")
            return false
        }
        val configMs = SystemClock.elapsedRealtime() - configStartedAt

        val writesSliced3mf = outputFile.name.lowercase(Locale.US).endsWith(".gcode.3mf")
        val writesMultiPlatePackage = writesSliced3mf && request.intent.getBooleanExtra(
            AutomationSliceRequest.EXTRA_MULTI_PLATE_PACKAGE,
            false
        )
        val thumbnailRequests = NativeEngineCalls.getThumbnailRequests(engineHandle)
        NativeEngineCalls.clearSliceThumbnails(engineHandle)
        if (thumbnailRequests == null) {
            Log.w(
                TAG,
                "automation:slice_thumbnail_requests unavailable error=${NativeEngineCalls.getLastErrorMessage(engineHandle)}"
            )
        } else {
            Log.i(
                TAG,
                "automation:slice_thumbnail_requests count=${thumbnailRequests.requests.size} " +
                    "errors=${thumbnailRequests.hasErrors} thumbnails=${thumbnailRequests.thumbnails} " +
                    "format=${thumbnailRequests.thumbnailsFormat} errorText=${thumbnailRequests.errorText.replace('\n', ' ').trim()}"
            )
        }
        val thumbnailStartedAt = SystemClock.elapsedRealtime()
        var thumbnailMs = 0L
        var uploadedThumbnails = 0
        val thumbnailPrinterBed = automationPrinterBed(configJson)
        val thumbnailMesh = runCatching { StlMeshParser.parseForDisplay(preparedModel).mesh }.getOrNull()
        val thumbnailViewerTransform = thumbnailMesh?.let { mesh ->
            nativeModelTransformToViewerTransform(
                bounds = mesh.bounds,
                printerBed = thumbnailPrinterBed,
                nativeTransform = placement
            )
        }
        if (thumbnailRequests?.requests?.isNotEmpty() == true) {
            val thumbnailRenderResult = renderSliceThumbnails(
                requestSummary = thumbnailRequests,
                plateObjects = emptyList(),
                fallbackMesh = thumbnailMesh,
                fallbackTransform = thumbnailViewerTransform,
                printerBed = thumbnailPrinterBed,
                includePackageThumbnails = writesSliced3mf,
                renderer = OffscreenEglSliceThumbnailRenderer()
            )
            thumbnailRenderResult.thumbnails.forEach { thumbnail ->
                val uploadResult = NativeEngineCalls.addSliceThumbnailRgba(
                    handle = engineHandle,
                    width = thumbnail.width,
                    height = thumbnail.height,
                    format = thumbnail.format,
                    role = thumbnail.role,
                    rgba = thumbnail.rgba
                )
                if (uploadResult is NativeEngineCallResult.Success) {
                    uploadedThumbnails++
                } else {
                    Log.w(TAG, "automation:slice_thumbnail_upload failed ${uploadResult.statusMessage}")
                }
            }
            thumbnailMs = SystemClock.elapsedRealtime() - thumbnailStartedAt
            val thumbnailBytes = thumbnailRenderResult.thumbnails.sumOf { it.rgba.size.toLong() }
            val renderedTriangles = thumbnailRenderResult.thumbnails.sumOf { it.renderedTriangleCount.toLong() }
            val sourceTriangles = thumbnailRenderResult.thumbnails.maxOfOrNull { it.sourceTriangleCount } ?: 0
            val renderers = thumbnailRenderResult.thumbnails
                .map { it.renderer }
                .distinct()
                .joinToString(",")
            Log.i(
                TAG,
                "automation:slice_thumbnail_render requested=${thumbnailRenderResult.requests.size} " +
                    "rendered=${thumbnailRenderResult.thumbnails.size} uploaded=$uploadedThumbnails thumbnailMs=$thumbnailMs " +
                    "packageThumbnails=${thumbnailRenderResult.includePackageThumbnails} " +
                    "renderers=$renderers " +
                    "bytes=$thumbnailBytes sourceTriangles=$sourceTriangles renderedTriangles=$renderedTriangles " +
                    "skip=${thumbnailRenderResult.skippedReason.orEmpty()}"
            )
        }

        val nativeSliceStartedAt = SystemClock.elapsedRealtime()
        val sliceResult = NativeEngineCalls.slice(engineHandle)
        if (sliceResult !is NativeEngineCallResult.Success) {
            Log.w(TAG, "automation:native slice failed; not retrying")
        }
        val nativeSliceMs = SystemClock.elapsedRealtime() - nativeSliceStartedAt
        if (sliceResult !is NativeEngineCallResult.Success) {
            writeStatus(
                "failed: ${sliceResult.statusMessage}$placementSummary fallback=${nativeSliceFailureStatus()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt} config=$configJson"
            )
            return false
        }

        outputFile.parentFile?.mkdirs()
        val writeGcodeStartedAt = SystemClock.elapsedRealtime()
        val temporaryPackageSourceGcodes = mutableListOf<File>()
        val writeResult = when {
            writesMultiPlatePackage -> {
                val tempGcodeParent = outputFile.parentFile ?: outputFile.absoluteFile.parentFile ?: File(".")
                val plateOneGcode = File(tempGcodeParent, "${outputFile.name}.plate-1-source.gcode")
                val plateTwoGcode = File(tempGcodeParent, "${outputFile.name}.plate-2-source.gcode")
                temporaryPackageSourceGcodes += plateOneGcode
                temporaryPackageSourceGcodes += plateTwoGcode
                val firstWriteResult = NativeEngineCalls.writeGcodeToFile(engineHandle, plateOneGcode.absolutePath)
                if (firstWriteResult !is NativeEngineCallResult.Success) {
                    firstWriteResult
                } else {
                    val plateOneBboxJson = NativeEngineCalls.getSlicedPlateBboxJson(engineHandle).orEmpty()
                    val secondPlacement = placement.copy(xMm = placement.xMm + 32.0)
                    val secondTransforms = DoubleArray(NativeModelTransformInputStride)
                    secondPlacement.writeTo(secondTransforms, stride = NativeModelTransformInputStride)
                    val secondLoadResult = NativeEngineCalls.loadPlateModelsV2(
                        handle = engineHandle,
                        paths = arrayOf(modelToLoad.absolutePath),
                        transforms = secondTransforms,
                        extruderIds = intArrayOf(1),
                        mobileObjectIds = longArrayOf(2L),
                        paintPayloadJson = ""
                    )
                    if (secondLoadResult !is NativeEngineCallResult.Success) {
                        secondLoadResult
                    } else {
                        val secondConfig = runCatching {
                            JSONObject(configJson)
                                .put("layer_height", 0.28)
                                .put("first_layer_height", 0.28)
                                .toString()
                        }.getOrDefault(configJson)
                        val secondConfigResult = NativeEngineCalls.setConfigJson(engineHandle, secondConfig)
                        if (secondConfigResult !is NativeEngineCallResult.Success) {
                            secondConfigResult
                        } else {
                            val secondSliceResult = NativeEngineCalls.slice(engineHandle)
                            if (secondSliceResult !is NativeEngineCallResult.Success) {
                                secondSliceResult
                            } else {
                                val plateTwoBboxJson = NativeEngineCalls.getSlicedPlateBboxJson(engineHandle).orEmpty()
                                val secondWriteResult = NativeEngineCalls.writeGcodeToFile(engineHandle, plateTwoGcode.absolutePath)
                                if (secondWriteResult !is NativeEngineCallResult.Success) {
                                    secondWriteResult
                                } else {
                                    if (thumbnailRequests?.requests?.isNotEmpty() == true && thumbnailMesh != null) {
                                        val secondViewerTransform = nativeModelTransformToViewerTransform(
                                            bounds = thumbnailMesh.bounds,
                                            printerBed = thumbnailPrinterBed,
                                            nativeTransform = secondPlacement
                                        )
                                        val secondThumbnailRenderStartedAt = SystemClock.elapsedRealtime()
                                        val secondThumbnailRenderResult = renderSliceThumbnails(
                                            requestSummary = thumbnailRequests,
                                            plateObjects = emptyList(),
                                            fallbackMesh = thumbnailMesh,
                                            fallbackTransform = secondViewerTransform,
                                            printerBed = thumbnailPrinterBed,
                                            includePackageThumbnails = true,
                                            renderer = OffscreenEglSliceThumbnailRenderer()
                                        )
                                        secondThumbnailRenderResult.thumbnails
                                            .filter { it.role in setOf("plate", "no_light", "top", "pick") }
                                            .forEach { thumbnail ->
                                                val uploadResult = NativeEngineCalls.addSliceThumbnailRgba(
                                                    handle = engineHandle,
                                                    width = thumbnail.width,
                                                    height = thumbnail.height,
                                                    format = thumbnail.format,
                                                    role = "${thumbnail.role}:2",
                                                    rgba = thumbnail.rgba
                                                )
                                                if (uploadResult is NativeEngineCallResult.Success) {
                                                    uploadedThumbnails++
                                                }
                                            }
                                        thumbnailMs += SystemClock.elapsedRealtime() - secondThumbnailRenderStartedAt
                                    }

                                    val finalTransforms = DoubleArray(NativeModelTransformInputStride * 2)
                                    placement.writeTo(finalTransforms, offset = 0, stride = NativeModelTransformInputStride)
                                    secondPlacement.writeTo(
                                        finalTransforms,
                                        offset = NativeModelTransformInputStride,
                                        stride = NativeModelTransformInputStride
                                    )
                                    val combinedLoadResult = NativeEngineCalls.loadPlateModelsV2(
                                        handle = engineHandle,
                                        paths = arrayOf(modelToLoad.absolutePath, modelToLoad.absolutePath),
                                        transforms = finalTransforms,
                                        extruderIds = intArrayOf(1, 1),
                                        mobileObjectIds = longArrayOf(1L, 2L),
                                        paintPayloadJson = ""
                                    )
                                    if (combinedLoadResult !is NativeEngineCallResult.Success) {
                                        combinedLoadResult
                                    } else {
                                        val finalConfigResult = NativeEngineCalls.setConfigJson(engineHandle, configJson)
                                        if (finalConfigResult !is NativeEngineCallResult.Success) {
                                            finalConfigResult
                                        } else {
                                            val manifest = JSONObject()
                                                .put(
                                                    "plates",
                                                    JSONArray()
                                                        .put(
                                                            JSONObject()
                                                                .put("plate_index", 0)
                                                                .put("plate_name", "Plate 1")
                                                                .put("gcode_file", plateOneGcode.absolutePath)
                                                                .put("bbox_json", plateOneBboxJson)
                                                                .put("mobile_object_ids", JSONArray().put(1L))
                                                        )
                                                        .put(
                                                            JSONObject()
                                                                .put("plate_index", 1)
                                                                .put("plate_name", "Plate 2")
                                                                .put("gcode_file", plateTwoGcode.absolutePath)
                                                                .put("bbox_json", plateTwoBboxJson)
                                                                .put("mobile_object_ids", JSONArray().put(2L))
                                                        )
                                                )
                                                .toString()
                                            NativeEngineCalls.writeMultiPlateBambuGcode3mfToFile(
                                                engineHandle,
                                                outputFile.absolutePath,
                                                manifest
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            writesSliced3mf -> NativeEngineCalls.writeBambuGcode3mfToFile(engineHandle, outputFile.absolutePath)
            else -> NativeEngineCalls.writeGcodeToFile(engineHandle, outputFile.absolutePath)
        }
        val writeGcodeMs = SystemClock.elapsedRealtime() - writeGcodeStartedAt
        if (writeResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${writeResult.statusMessage} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            val writer = when {
                writesMultiPlatePackage -> "nativeWriteMultiPlateBambuGcode3mfToFile"
                writesSliced3mf -> "nativeWriteBambuGcode3mfToFile"
                else -> "nativeWriteGcodeToFile"
            }
            writeStatus("failed: $writer produced empty output elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        val outputThumbnailAudit = if (writesSliced3mf) {
            if (writesMultiPlatePackage) "package=3mf,multiPlate=1" else "package=3mf"
        } else {
            auditGcodeThumbnailMarkers(outputFile)
        }
        Log.i(TAG, "automation:slice_thumbnail_output $outputThumbnailAudit")
        val nativeMetrics = parseAutomationSliceNativeMetrics(NativeEngineCalls.getSliceMetrics(engineHandle))
        val previewInfoMetrics = automationSlicePreviewInfoMetrics(
            summaryText = NativeEngineCalls.getGcodeSummary(engineHandle),
            enrichedSummaryText = NativeEngineCalls.getEnrichedGcodeSummary(engineHandle)
        )
        val previewLoadMetrics = automationSlicePreviewLoadMetrics(
            engineHandle = engineHandle,
            layerCount = previewInfoMetrics.layerCount
        )
        temporaryPackageSourceGcodes.forEach { it.delete() }
        val totalMs = SystemClock.elapsedRealtime() - startedAt
        val timing = AutomationSliceTiming(
            stagingMs = stagingMs,
            nativeLoadMs = nativeLoadMs,
            placementMs = placementMs,
            configMs = configMs,
            nativeSliceMs = nativeSliceMs,
            thumbnailMs = thumbnailMs,
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
                    totalMs = totalMs,
                    thumbnailMs = thumbnailMs
                )
            )
        )
        writeStatus(
            automationSliceSuccessStatus(
                modelFile = modelFile,
                stagedModel = preparedModel,
                outputFile = outputFile,
                timing = timing,
                nativeMetrics = nativeMetrics,
                previewInfoMetrics = previewInfoMetrics,
                previewLoadMetrics = previewLoadMetrics,
                configJson = configJson,
                thumbnailAudit = outputThumbnailAudit
            )
        )
        return true
    }

    private fun runProject3mfRoundTripExport(
        request: AutomationSliceRequest,
        modelFile: File,
        stagedModel: File,
        outputFile: File,
        startedAt: Long,
        stagingMs: Long,
        engineHandle: NativeEngineHandle,
        writeStatus: (String) -> Unit
    ): Boolean {
        if (detectSourceModelFileFormat(stagedModel.name) != SourceModelFileFormat.ThreeMf) {
            writeStatus("failed: project 3MF export requires a .3mf source path=${request.modelPath} staged=${stagedModel.absolutePath}")
            return false
        }
        val nativeLoadStartedAt = SystemClock.elapsedRealtime()
        val mobileObjectIds = LongArray(256) { index -> index.toLong() + 1L }
        val loadResult = NativeEngineCalls.loadProject3mf(
            handle = engineHandle,
            path = stagedModel.absolutePath,
            mobileObjectIds = mobileObjectIds
        )
        val nativeLoadMs = SystemClock.elapsedRealtime() - nativeLoadStartedAt
        if (loadResult !is NativeEngineCallResult.Success) {
            NativeEngineCalls.clearGeneratedGcode(engineHandle)
            writeStatus("failed: ${loadResult.statusMessage} path=${request.modelPath} staged=${stagedModel.absolutePath}")
            onModelLoadRejected()
            return false
        }
        onModelLoaded(stagedModel)

        outputFile.parentFile?.mkdirs()
        val writeStartedAt = SystemClock.elapsedRealtime()
        val writeResult = NativeEngineCalls.writeProject3mfToFile(engineHandle, outputFile.absolutePath)
        val writeMs = SystemClock.elapsedRealtime() - writeStartedAt
        if (writeResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${writeResult.statusMessage} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            writeStatus("failed: nativeWriteProject3mfToFile produced empty output elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }

        val totalMs = SystemClock.elapsedRealtime() - startedAt
        writeStatus(
            "success: model=${modelFile.absolutePath} " +
                "staged=${stagedModel.absolutePath} " +
                "output=${outputFile.absolutePath} " +
                "bytes=${outputFile.length()} " +
                "stagingMs=$stagingMs " +
                "nativeLoadMs=$nativeLoadMs " +
                "placementMs=0 " +
                "configMs=0 " +
                "nativeSliceMs=0 " +
                "thumbnailMs=0 " +
                "writeGcodeMs=$writeMs " +
                "projectRoundTrip=1 " +
                "elapsedMs=$totalMs"
        )
        return true
    }

    private fun prepareAutomationWorkspaceModel(
        handle: NativeEngineHandle,
        stagedModel: File,
        writeStatus: (String) -> Unit
    ): File? {
        val sourceFormat = detectSourceModelFileFormat(stagedModel.name)
            ?: return stagedModel
        if (sourceFormat == SourceModelFileFormat.Stl) {
            return stagedModel
        }

        val convertedName = stagedModel.nameWithoutExtension + when (sourceFormat) {
            SourceModelFileFormat.ThreeMf -> "-3mf-mesh.stl"
            SourceModelFileFormat.Step -> "-step-mesh.stl"
            SourceModelFileFormat.Stl -> ".stl"
        }
        val converted = File(stagedModel.parentFile ?: stagedModel.absoluteFile.parentFile, convertedName)
        val result = when (sourceFormat) {
            SourceModelFileFormat.ThreeMf -> NativeEngineCalls.extractModelMeshToStl(
                handle = handle,
                inputPath = stagedModel.absolutePath,
                outputStlPath = converted.absolutePath
            )
            SourceModelFileFormat.Step -> NativeEngineCalls.convertStepToStl(
                handle = handle,
                inputPath = stagedModel.absolutePath,
                outputStlPath = converted.absolutePath,
                linearDeflection = AUTOMATION_STEP_LINEAR_DEFLECTION,
                angleDeflection = AUTOMATION_STEP_ANGLE_DEFLECTION
            )
            SourceModelFileFormat.Stl -> NativeEngineCallResult.Success
        }

        if (result !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${sourceFormat.name} mesh conversion failed: ${result.statusMessage} staged=${stagedModel.absolutePath}")
            converted.delete()
            return null
        }
        if (!converted.isFile || converted.length() <= 84L) {
            writeStatus("failed: ${sourceFormat.name} mesh conversion produced invalid STL bytes=${converted.length()} staged=${stagedModel.absolutePath}")
            converted.delete()
            return null
        }
        return converted
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

private fun auditGcodeThumbnailMarkers(outputFile: File): String {
    var pngBlocks = 0
    var qoiBlocks = 0
    var jpgBlocks = 0
    var gimageBlocks = 0
    var simageBlocks = 0
    var thumbnailsConfig = ""
    var thumbnailsFormatConfig = ""
    outputFile.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
        lines.forEach { line ->
            when {
                line.startsWith("; thumbnail begin ") -> pngBlocks++
                line.startsWith("; thumbnail_QOI begin ") -> qoiBlocks++
                line.startsWith("; thumbnail_JPG begin ") -> jpgBlocks++
                line.startsWith(";gimage:") -> gimageBlocks++
                line.startsWith(";simage:") -> simageBlocks++
                line.startsWith("; thumbnails = ") -> thumbnailsConfig = line.substringAfter("= ").trim()
                line.startsWith("; thumbnails_format = ") -> thumbnailsFormatConfig = line.substringAfter("= ").trim()
            }
        }
    }
    return "png=$pngBlocks qoi=$qoiBlocks jpg=$jpgBlocks gimage=$gimageBlocks simage=$simageBlocks " +
        "thumbnails=$thumbnailsConfig thumbnailsFormat=$thumbnailsFormatConfig"
}
