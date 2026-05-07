package com.mobileslicer.profiles

import org.json.JSONObject

internal fun JSONObject.applyOrcaProfileIdentityDefaults(
    printer: PrinterProfile,
    filament: FilamentProfile,
    process: ProcessProfile
) {
    val hasOrcaPrinterContext = printer.hasOrcaContext()
    val hasOrcaFilamentContext = filament.hasOrcaContext() || hasOrcaFilamentIdentity()
    val hasOrcaProcessContext = process.hasOrcaContext()
    if (hasOrcaPrinterContext && shouldUseExportedPrinterSettingsId(printer)) {
        put(NativeConfigKeys.Printer.SettingsId, resolvedPrinterSettingsIdForExport(printer))
    }
    if (hasOrcaFilamentContext && scalarString(NativeConfigKeys.Filament.SettingsId).isBlank()) {
        put(NativeConfigKeys.Filament.SettingsId, filament.orcaFilamentPath.orcaProfileNameFromPath().ifBlank { filament.name })
    }
    if (hasOrcaFilamentContext && !hasNonBlankNativeScalar(NativeConfigKeys.Filament.Ids)) {
        scalarString(NativeConfigKeys.Filament.Id).takeIf { it.isNotBlank() }?.let { put(NativeConfigKeys.Filament.Ids, it) }
    }
    if (hasOrcaFilamentContext) {
        applyOrcaFilamentIdentityDefaults(filament)
        val currentColor = scalarString(NativeConfigKeys.Filament.Color)
            .ifBlank { scalarString(NativeConfigKeys.Filament.DefaultColor) }
        val fallbackColor = filament.orcaDefaultColorFallback()
        val color = if (currentColor.isBlank() || currentColor.equals("#8FC1FF", ignoreCase = true)) {
            fallbackColor.ifBlank { filament.defaultFilamentColor }.ifBlank { currentColor }
        } else {
            currentColor
        }
        if (color.isNotBlank()) {
            put(NativeConfigKeys.Filament.Color, color)
            put(NativeConfigKeys.Filament.MultiColor, color)
            if (scalarString(NativeConfigKeys.Filament.ColorType).isBlank()) {
                put(NativeConfigKeys.Filament.ColorType, 1)
            }
        }
    }
    if (hasOrcaProcessContext && optString(NativeConfigKeys.Process.SettingsId).isBlank()) {
        put(NativeConfigKeys.Process.SettingsId, process.orcaProcessPath.orcaProfileNameFromPath().ifBlank { process.name })
    }
    if (hasOrcaFilamentContext) {
        putIfBlankOrZero(NativeConfigKeys.Filament.PressureAdvance, "0.02")
        applyOrcaFilamentTemplateDefaults(filament)
        applyOrcaCurrentBedTypeDefault(printer)
    }
    if (hasOrcaProcessContext || hasOrcaPrinterContext) {
        applyOrcaProcessTemplateDefaults(printer, process)
    }
    if (hasOrcaPrinterContext) {
        applyOrcaPrinterHardwareDefaults(printer)
        expandOrcaMachineLimitVectors()
    }
}

internal fun JSONObject.applyFinalOrcaParityNormalization(
    printer: PrinterProfile,
    process: ProcessProfile
) {
    if (printer.hasOrcaContext() || process.hasOrcaContext() || hasBambuContext()) {
        applyOrcaProcessTemplateDefaults(printer, process)
    }
    if (printer.hasOrcaContext()) {
        applyOrcaPrinterHardwareDefaults(printer)
        expandOrcaMachineLimitVectors()
        applyOrcaCurrentBedTypeDefault(printer)
    }
}

internal fun PrinterProfile.hasOrcaContext(): Boolean =
    profileSource == "orca" || orcaMachineModelPath.isNotBlank() || orcaFamily.isNotBlank() ||
        orcaResolvedMachineJson.isNotBlank() || orcaMachineOverridesJson.isNotBlank()

internal fun FilamentProfile.hasOrcaContext(): Boolean =
    profileSource == "orca" || orcaFilamentPath.isNotBlank() || orcaFamily.isNotBlank() ||
        orcaResolvedFilamentJson.isNotBlank() || orcaFilamentOverridesJson.isNotBlank()

internal fun ProcessProfile.hasOrcaContext(): Boolean =
    profileSource == "orca" || orcaProcessPath.isNotBlank() || orcaFamily.isNotBlank() ||
        orcaResolvedProcessJson.isNotBlank() || orcaProcessOverridesJson.isNotBlank()

private fun JSONObject.shouldUseExportedPrinterSettingsId(printer: PrinterProfile): Boolean {
    val current = scalarString(NativeConfigKeys.Printer.SettingsId)
    return current.isBlank() ||
        current == "Qidi" ||
        current == printer.orcaFamily ||
        current == printer.name ||
        current == printer.printerModel ||
        (current.contains("MyToolChanger", ignoreCase = true) && !printer.orcaFamily.equals("Custom", ignoreCase = true))
}

private fun JSONObject.resolvedPrinterSettingsIdForExport(printer: PrinterProfile): String =
    printer.resolvedOrcaMachineName().ifBlank { printerSettingsIdForExport(printer) }

private fun PrinterProfile.resolvedOrcaMachineName(): String =
    runCatching { JSONObject(orcaResolvedMachineJson).optString(NativeConfigKeys.Printer.Name) }
        .getOrNull()
        .orEmpty()
        .trim()

private fun JSONObject.hasOrcaFilamentIdentity(): Boolean =
    scalarString(NativeConfigKeys.Filament.SettingsId).isNotBlank() ||
        scalarString(NativeConfigKeys.Filament.Ids).isNotBlank() ||
        scalarString(NativeConfigKeys.Filament.Id).isNotBlank()

internal fun String.orcaProfileNameFromPath(): String =
    substringAfterLast('/')
        .substringBefore('#')
        .substringBefore('?')
        .removeSuffix(".json")
        .removeSuffix(".JSON")
        .trim()

private fun FilamentProfile.orcaDefaultColorFallback(): String =
    if (hasOrcaContext()) {
        "#26A69A"
    } else {
        ""
    }
