package com.mobileslicer.profiles

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import org.json.JSONObject

internal fun parseProfilePayloadZip(context: Context, bytes: ByteArray, currentStore: ProfileStore): ProfileImportPayload? {
    if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) {
        return null
    }
    parseOrcaBundleZip(context, bytes, currentStore)?.let { return it }
    val jsonEntries = mutableListOf<Pair<String, String>>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                val text = zip.readBytes().toString(StandardCharsets.UTF_8)
                jsonEntries += entry.name.substringAfterLast('/') to text
            }
            zip.closeEntry()
        }
    }
    if (jsonEntries.isEmpty()) {
        error("No JSON profile files were found in the selected ZIP.")
    }
    val parsed = parseNonBundleZipJsonEntries(context, jsonEntries, currentStore)
    return when {
        parsed.size == 1 -> parsed.single()
        else -> parsed.toProfileStorePayload(currentStore)
    }
}

private fun parseNonBundleZipJsonEntries(
    context: Context,
    entries: List<Pair<String, String>>,
    currentStore: ProfileStore
): List<ProfileImportPayload> {
    val parsed = mutableListOf<ProfileImportPayload>()
    val printerEntries = entries.filter { (_, text) -> text.toJsonObjectOrNull()?.isOrcaPrinterPresetJson() == true }
    printerEntries.forEach { (displayName, text) ->
        parsed += parseProfilePayloadText(context, text, displayName, currentStore)
    }
    val importedPrinters = parsed.flatMap { payload ->
        when (payload) {
            is ProfileImportPayload.Printer -> listOf(payload.profile)
            is ProfileImportPayload.Store -> payload.store.store.printers
            else -> emptyList()
        }
    }
    val correlatedStore = if (importedPrinters.isEmpty()) {
        currentStore
    } else {
        currentStore.copy(
            printers = (currentStore.printers + importedPrinters).distinctBy { it.id },
            selectedPrinterId = importedPrinters.firstOrNull()?.id ?: currentStore.selectedPrinterId
        )
    }
    entries
        .filterNot { entry -> printerEntries.any { it.first == entry.first && it.second == entry.second } }
        .forEach { (displayName, text) ->
            parsed += parseProfilePayloadText(context, text, displayName, correlatedStore)
        }
    return parsed
}

private fun List<ProfileImportPayload>.toProfileStorePayload(currentStore: ProfileStore): ProfileImportPayload.Store {
    val importedPrinters = mutableListOf<PrinterProfile>()
    val importedFilaments = mutableListOf<FilamentProfile>()
    val importedProcesses = mutableListOf<ProcessProfile>()
    forEach { payload ->
        when (payload) {
            is ProfileImportPayload.Store -> {
                importedPrinters += payload.store.store.printers
                importedFilaments += payload.store.store.filaments
                importedProcesses += payload.store.store.processes
            }
            is ProfileImportPayload.Printer -> {
                importedPrinters += payload.profile
                importedProcesses += payload.processes
            }
            is ProfileImportPayload.Filament -> importedFilaments += payload.profile
            is ProfileImportPayload.Process -> importedProcesses += payload.profile
        }
    }
    val selectedPrinterId = importedPrinters.firstOrNull()?.id
        ?: currentStore.selectedPrinterId.takeIf { id -> importedFilaments.any { it.printerProfileId == id } || importedProcesses.any { it.printerProfileId == id } }
        ?: importedFilaments.firstOrNull()?.printerProfileId
        ?: importedProcesses.firstOrNull()?.printerProfileId
        ?: ""
    val selectedFilamentId = importedFilaments.firstOrNull { it.printerProfileId == selectedPrinterId }?.id
        ?: importedFilaments.firstOrNull()?.id
        ?: ""
    val selectedProcessId = importedProcesses.firstOrNull { it.printerProfileId == selectedPrinterId }?.id
        ?: importedProcesses.firstOrNull()?.id
        ?: ""
    return ProfileImportPayload.Store(
        ProfileStoreDeviceImportResult(
            store = ProfileStore(
                printers = importedPrinters,
                filaments = importedFilaments,
                processes = importedProcesses,
                selectedPrinterId = selectedPrinterId,
                selectedFilamentId = selectedFilamentId,
                selectedProcessId = selectedProcessId,
                selectedProcessIdsByPrinterId = if (selectedPrinterId.isNotBlank() && selectedProcessId.isNotBlank()) {
                    mapOf(selectedPrinterId to selectedProcessId)
                } else {
                    emptyMap()
                }
            ),
            printerCount = importedPrinters.size,
            filamentCount = importedFilaments.size,
            processCount = importedProcesses.size
        )
    )
}

internal fun parseProfilePayloadText(
    context: Context,
    text: String,
    displayName: String,
    currentStore: ProfileStore
): ProfileImportPayload {
    runCatching {
        return ProfileImportPayload.Store(profileStoreFromDeviceTransferJson(text))
    }
    val json = runCatching { JSONObject(text) }.getOrElse {
        error("Profile file is not valid JSON.")
    }
    if (json.has(NativeConfigKeys.Printer.SettingsId) || json.isOrcaPrinterPresetJson()) {
        val profile = json.toImportedDeviceOrcaPrinterProfile(context, displayName)
        val processes = findOrcaPrinterPresetForProfile(context, profile)
            ?.let { preset -> loadOrcaPrinterImportBundle(context, preset).toImportedProcessProfiles(profile) }
            .orEmpty()
        return ProfileImportPayload.Printer(profile, processes)
    }
    if (json.has(NativeConfigKeys.Process.SettingsId) || json.isOrcaProcessPresetJson()) {
        return ProfileImportPayload.Process(json.toImportedDeviceOrcaProcessProfile(context, displayName, currentStore))
    }
    if (json.has(NativeConfigKeys.Filament.SettingsId) || json.isOrcaFilamentPresetJson()) {
        return ProfileImportPayload.Filament(json.toImportedDeviceOrcaFilamentProfile(context, displayName, currentStore))
    }
    error("Only MobileSlicer profile exports and printer, filament, or process preset JSON files are supported here.")
}

internal fun JSONObject.isOrcaPrinterPresetJson(): Boolean =
    has(NativeConfigKeys.Bed.PrintableArea) && has("printable_height") && has(NativeConfigKeys.Printer.NozzleDiameter) &&
        (has(NativeConfigKeys.Printer.Model) || has("machine_start_gcode") || has("gcode_flavor"))

internal fun JSONObject.isOrcaFilamentPresetJson(): Boolean =
    has(NativeConfigKeys.Filament.Type) || has("nozzle_temperature") || has("filament_flow_ratio") ||
        has(NativeConfigKeys.Temperature.HotPlate) || has(NativeConfigKeys.Temperature.Bed)

internal fun JSONObject.isOrcaProcessPresetJson(): Boolean =
    has("layer_height") || has("wall_loops") || has("sparse_infill_density") ||
        has("initial_layer_speed") || has("outer_wall_speed")

private fun String.toJsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()
