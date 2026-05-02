package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspaceMode

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

internal data class ModelLoaderSliceRunInputs(
    val configuration: ActiveSlicerConfiguration,
    val calibrationJob: CalibrationJob?,
    val plateObjects: List<PlateObject>,
    val profileFilaments: List<FilamentProfile>,
    val activePlateSlots: List<PlateFilamentSlot>,
    val flushVolumes: PlateFlushVolumes?,
    val printer: PrinterProfile,
    val modelFilePath: String?,
    val preparedMesh: StlMesh?,
    val modelBounds: MeshBounds?,
    val modelTransform: ViewerModelTransform?,
    val gcodeFileName: String
)

internal data class ModelLoaderSliceUiStartPlan(
    val sliceInProgress: Boolean,
    val gcodeFilePath: String?,
    val summary: com.mobileslicer.workspace.SliceResultSummary?,
    val timing: com.mobileslicer.workspace.SlicePipelineTiming?,
    val previewKey: Long,
    val previewStartedAtMs: Long?,
    val firstVisiblePreviewFrameMs: Long?,
    val workspaceMode: WorkspaceMode,
    val statusMessage: String
)

internal fun planModelLoaderSliceUiStart(statusMessage: String): ModelLoaderSliceUiStartPlan =
    ModelLoaderSliceUiStartPlan(
        sliceInProgress = true,
        gcodeFilePath = null,
        summary = null,
        timing = null,
        previewKey = 0L,
        previewStartedAtMs = null,
        firstVisiblePreviewFrameMs = null,
        workspaceMode = WorkspaceMode.Prepare,
        statusMessage = statusMessage
    )

internal fun captureModelLoaderSliceRunInputs(
    configuration: ActiveSlicerConfiguration,
    calibrationJob: CalibrationJob?,
    plateObjects: List<PlateObject>,
    profileFilaments: List<FilamentProfile>,
    plateFilamentSlots: List<PlateFilamentSlot>,
    fallbackFilament: FilamentProfile,
    flushVolumes: PlateFlushVolumes?,
    printer: PrinterProfile,
    modelFilePath: String?,
    preparedMesh: StlMesh?,
    modelBounds: MeshBounds?,
    modelTransform: ViewerModelTransform?,
    gcodeFileName: String
): ModelLoaderSliceRunInputs =
    ModelLoaderSliceRunInputs(
        configuration = configuration,
        calibrationJob = calibrationJob,
        plateObjects = plateObjects.toList(),
        profileFilaments = profileFilaments.toList(),
        activePlateSlots = plateFilamentSlots.toList().ifEmpty {
            listOf(fallbackFilament.toPlateFilamentSlot(index = 1))
        },
        flushVolumes = flushVolumes,
        printer = printer,
        modelFilePath = modelFilePath,
        preparedMesh = preparedMesh,
        modelBounds = modelBounds,
        modelTransform = modelTransform,
        gcodeFileName = gcodeFileName
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
