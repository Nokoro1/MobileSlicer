package com.mobileslicer.profiles

import android.content.Context

internal fun ProfileStore.withOrcaProfileFallbacks(context: Context): ProfileStore =
    withSystemGenericOrcaFilamentFallback(context)
        .withResolvedOrcaProcessFallback(context)

internal fun ProfileStore.withResolvedOrcaProcessFallback(context: Context): ProfileStore {
    val printer = printers.firstOrNull { it.id == selectedPrinterId } ?: return this
    val process = processes.firstOrNull { it.id == selectedProcessId && it.printerProfileId == selectedPrinterId }
        ?: return this
    if (!process.needsResolvedOrcaProcessFallback(printer)) return this
    val printerPreset = findOrcaPrinterPresetForProfile(context, printer) ?: return this
    val processPreset = runCatching { loadOrcaPrinterImportBundle(context, printerPreset) }
        .getOrNull()
        ?.processPresets
        ?.let { presets -> findResolvedOrcaProcessFallback(process, printer, presets) }
        ?: return this
    val repairedSelected = process.repairWithResolvedOrcaProcessFallback(processPreset, printer)
    return copy(
        processes = processes.upsert(repairedSelected),
        selectedProcessId = repairedSelected.id
    ).withSelectedProcessForCurrentPrinter(repairedSelected.id)
}

internal fun ProcessProfile.repairWithResolvedOrcaProcessFallback(
    processPreset: OrcaProcessPresetBundle,
    printer: PrinterProfile
): ProcessProfile {
    val imported = processPreset.toImportedProcessProfile(printer)
        .withValues("orcaProcessOverridesJson" to orcaProcessOverridesJson)
    return withValues(
        "orcaFamily" to imported.orcaFamily,
        "orcaProcessPath" to imported.orcaProcessPath,
        "orcaRawProcessJson" to imported.orcaRawProcessJson,
        "orcaResolvedProcessJson" to imported.orcaResolvedProcessJson,
        "orcaResolvedSourceChain" to imported.orcaResolvedSourceChain
    ).withChangedNativeProcessOverridesFrom(imported)
}

private fun ProcessProfile.needsResolvedOrcaProcessFallback(printer: PrinterProfile): Boolean =
    printer.profileSource == "orca" &&
        orcaResolvedProcessJson.isBlank() &&
        listOf(name, orcaProcessPath, readPrintSettingsId()).any { it.isNotBlank() }

internal fun findResolvedOrcaProcessFallback(
    process: ProcessProfile,
    printer: PrinterProfile,
    processPresets: List<OrcaProcessPresetBundle>
): OrcaProcessPresetBundle? {
    val selectedKeys = process.matchKeys()
    if (selectedKeys.isEmpty()) return null
    return processPresets
        .filter { preset -> kotlin.math.abs(preset.nozzleDiameterMm - printer.nozzleDiameterMm) < 0.001f }
        .firstOrNull { preset ->
            preset.matchKeys().any { key -> key in selectedKeys }
        }
}

private fun ProcessProfile.matchesOrcaProcessPreset(preset: OrcaProcessPresetBundle): Boolean =
    matchKeys().intersect(preset.matchKeys().toSet()).isNotEmpty()

private fun ProcessProfile.matchKeys(): Set<String> =
    listOf(
        name,
        name.removeProfileCopySuffix(),
        name.substringBefore('@').trim(),
        name.removeProfileCopySuffix().substringBefore('@').trim(),
        orcaProcessPath,
        orcaProcessPath.orcaProfileNameFromPath(),
        readPrintSettingsId()
    ).map { it.cleanProfileMatchKey() }.filter { it.isNotBlank() }.toSet()

private fun OrcaProcessPresetBundle.matchKeys(): Set<String> =
    listOf(
        name,
        name.substringBefore('@').trim(),
        rawName,
        rawName.substringBefore('@').trim(),
        profilePath,
        profilePath.orcaProfileNameFromPath()
    ).map { it.cleanProfileMatchKey() }.filter { it.isNotBlank() }.toSet()

private fun ProcessProfile.readPrintSettingsId(): String =
    jsonObjectOrNull(orcaRawProcessJson)?.optString(NativeConfigKeys.Process.SettingsId).orEmpty()
