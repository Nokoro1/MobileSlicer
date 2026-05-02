package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.workspace.SliceResult

internal const val ModelLoaderPrintableVolumeExceededMessage =
    "Slice failed\nPrintable volume exceeded.\nThe model fits, but generated skirt, brim, or purge geometry cannot fit inside the current printer bed."

internal sealed class ModelLoaderSliceStartPlan {
    data object Ignore : ModelLoaderSliceStartPlan()
    data class Fail(val result: SliceResult) : ModelLoaderSliceStartPlan()
    data class Start(val statusMessage: String) : ModelLoaderSliceStartPlan()
}

internal fun canRequestModelLoaderSlice(
    modelLoaded: Boolean,
    sliceInProgress: Boolean,
    sendToPrinterInProgress: Boolean
): Boolean =
    modelLoaded && !sliceInProgress && !sendToPrinterInProgress

internal fun planModelLoaderSliceStart(
    modelLoaded: Boolean,
    sliceInProgress: Boolean,
    sendToPrinterInProgress: Boolean,
    generatedFootprintFits: Boolean,
    printableVolumePreflightFailure: String?,
    nativeSliceTitle: String
): ModelLoaderSliceStartPlan {
    if (!canRequestModelLoaderSlice(modelLoaded, sliceInProgress, sendToPrinterInProgress)) {
        return ModelLoaderSliceStartPlan.Ignore
    }
    if (!generatedFootprintFits) {
        return ModelLoaderSliceStartPlan.Fail(
            SliceResult(ModelLoaderPrintableVolumeExceededMessage, sliced = false)
        )
    }
    printableVolumePreflightFailure?.let { failure ->
        return ModelLoaderSliceStartPlan.Fail(SliceResult(failure, sliced = false))
    }
    return ModelLoaderSliceStartPlan.Start(
        statusMessage = "Slice in progress\nNative slice inputs: $nativeSliceTitle"
    )
}

internal data class ModelLoaderSliceCompletionPlan(
    val gcodeFilePath: String?,
    val summary: com.mobileslicer.workspace.SliceResultSummary?,
    val timing: com.mobileslicer.workspace.SlicePipelineTiming?,
    val gcodeFileName: String,
    val previewKey: Long,
    val statusMessage: String,
    val completionResult: SliceResult
)

internal fun planModelLoaderSliceCompletion(
    result: SliceResult,
    calibrationJob: CalibrationJob?,
    fallbackFileName: String,
    previousPreviewKey: Long
): ModelLoaderSliceCompletionPlan =
    ModelLoaderSliceCompletionPlan(
        gcodeFilePath = result.gcodeFilePath,
        summary = result.summary,
        timing = result.timing,
        gcodeFileName = effectiveSliceResultGcodeFileName(
            result = result,
            calibrationJob = calibrationJob,
            fallbackFileName = fallbackFileName
        ),
        previewKey = if (result.sliced && result.gcodeFilePath != null) {
            previousPreviewKey + 1L
        } else {
            0L
        },
        statusMessage = result.message,
        completionResult = result
    )

internal fun effectiveSliceResultGcodeFileName(
    result: SliceResult,
    calibrationJob: CalibrationJob?,
    fallbackFileName: String
): String =
    calibrationJob
        ?.let { job ->
            result.gcodeFilePath?.let { detectCalibrationGcodeFileName(it) }
                ?: job.gcodeFileName()
        }
        ?: result.fileName
        ?: fallbackFileName
