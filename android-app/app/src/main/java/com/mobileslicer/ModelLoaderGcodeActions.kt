package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResultSummary

internal fun effectiveGcodeFileName(
    calibrationJob: CalibrationJob?,
    plateObjects: List<PlateObject>,
    summary: SliceResultSummary?,
    filamentMaterial: String,
    fallbackName: String
): String {
    calibrationJob?.let { return it.gcodeFileName() }
    return suggestExportGcodeFileName(
        plateObjects = plateObjects,
        summary = summary,
        filamentMaterial = filamentMaterial,
        fallbackName = fallbackName
    )
}

internal fun effectiveGcodeFileNameForFile(
    gcodeFilePath: String,
    calibrationJob: CalibrationJob?,
    plateObjects: List<PlateObject>,
    summary: SliceResultSummary?,
    filamentMaterial: String,
    fallbackName: String
): String {
    calibrationJob?.let {
        detectCalibrationGcodeFileName(gcodeFilePath)?.let { detected -> return detected }
        return it.gcodeFileName()
    }
    return effectiveGcodeFileName(
        calibrationJob = null,
        plateObjects = plateObjects,
        summary = summary,
        filamentMaterial = filamentMaterial,
        fallbackName = fallbackName
    )
}

internal data class ModelLoaderGcodeFileAction(
    val gcodeFilePath: String,
    val fileName: String
)

internal fun planGcodeFileAction(
    gcodeFilePath: String?,
    calibrationJob: CalibrationJob?,
    plateObjects: List<PlateObject>,
    summary: SliceResultSummary?,
    filamentMaterial: String,
    fallbackName: String
): ModelLoaderGcodeFileAction? {
    val path = gcodeFilePath ?: return null
    return ModelLoaderGcodeFileAction(
        gcodeFilePath = path,
        fileName = effectiveGcodeFileNameForFile(
            gcodeFilePath = path,
            calibrationJob = calibrationJob,
            plateObjects = plateObjects,
            summary = summary,
            filamentMaterial = filamentMaterial,
            fallbackName = fallbackName
        )
    )
}

internal fun planPrinterUploadRequest(
    gcodeFilePath: String?,
    sendToPrinterInProgress: Boolean,
    calibrationJob: CalibrationJob?,
    remoteFileName: String,
    printerProfile: PrinterProfile,
    uploadAction: PrinterUploadAction
): PrinterUploadRequest? {
    val path = gcodeFilePath ?: return null
    if (sendToPrinterInProgress) return null
    val effectiveRemoteFileName = calibrationJob
        ?.let { detectCalibrationGcodeFileName(path) ?: it.gcodeFileName() }
        ?: remoteFileName
    return PrinterUploadRequest(
        gcodeFilePath = path,
        remoteFileName = effectiveRemoteFileName,
        printerProfile = printerProfile,
        uploadAction = uploadAction
    )
}

internal fun printerOpenPlan(printer: PrinterProfile): Result<String> =
    printerWebUiUrl(printer)?.let { Result.success(it) }
        ?: Result.failure(IllegalStateException("Printer unavailable\nEnter a printer host or printer web UI URL in the profile."))
