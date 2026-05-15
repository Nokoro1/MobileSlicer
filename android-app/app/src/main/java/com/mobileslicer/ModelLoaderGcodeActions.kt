package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.printerconnection.BambuLanPrintOptions
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.workspace.PlateFilamentSlot
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

internal fun activeExportFilamentMaterial(
    plateObjects: List<PlateObject>,
    plateFilamentSlots: List<PlateFilamentSlot>,
    fallbackFilament: FilamentProfile
): String {
    val activeSlots = activePlateFilamentSlots(plateFilamentSlots, plateObjects).ifEmpty {
        return fallbackFilament.materialType
    }
    val usedIndexes = actuallyUsedPlateFilamentSlotIndexes(activeSlots, plateObjects)
    val usedSlots = if (usedIndexes.isEmpty()) {
        activeSlots.take(1)
    } else {
        activeSlots.filter { it.index in usedIndexes }.ifEmpty { activeSlots.take(1) }
    }
    return usedSlots
        .mapNotNull { slot -> slot.materialType.ifBlank { slot.label }.takeIf { it.isNotBlank() } }
        .distinctBy { it.uppercase() }
        .joinToString("_")
        .ifBlank { fallbackFilament.materialType }
}

internal data class ModelLoaderGcodeFileAction(
    val gcodeFilePath: String,
    val fileName: String
)

internal fun exportCancelledStatus(): String = "Export cancelled"

internal fun uploadCancelledStatus(): String = "Upload cancelled"

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
    uploadAction: PrinterUploadAction,
    bambuOptions: BambuLanPrintOptions? = null
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
        uploadAction = uploadAction,
        bambuOptions = bambuOptions
    )
}

internal fun printerOpenPlan(printer: PrinterProfile): Result<String> =
    printerWebUiUrl(printer)?.let { Result.success(it) }
        ?: Result.failure(IllegalStateException("Printer unavailable\nEnter a printer host or printer web UI URL in the profile."))
