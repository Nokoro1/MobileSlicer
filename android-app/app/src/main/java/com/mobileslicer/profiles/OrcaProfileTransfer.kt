package com.mobileslicer.profiles

import android.content.Context
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal const val ORCA_PROFILE_TRANSFER_MIME_TYPE = "application/zip"

private const val ORCA_EXPORT_VERSION = "02.03.02.60"
private const val ORCA_PRINTER_BUNDLE_TYPE = "printer config bundle"
private const val ORCA_FILAMENT_BUNDLE_TYPE = "filament config bundle"

internal enum class OrcaProfileExportKind(
    val label: String,
    val fileExtension: String
) {
    PrinterBundle("Printer config bundle (recommended)", "orca_printer"),
    FilamentBundle("Filament bundle", "orca_filament"),
    PrinterPresetsZip("Printer preset JSON only (advanced)", "zip"),
    FilamentPresetsZip("Filament preset JSON only (advanced)", "zip"),
    ProcessPresetsZip("Process preset JSON only (advanced)", "zip")
}

internal val OrcaProfileExportKind.createDocumentMimeType: String
    get() = when (this) {
        OrcaProfileExportKind.PrinterBundle,
        OrcaProfileExportKind.FilamentBundle -> "application/octet-stream"
        OrcaProfileExportKind.PrinterPresetsZip,
        OrcaProfileExportKind.FilamentPresetsZip,
        OrcaProfileExportKind.ProcessPresetsZip -> "application/zip"
    }

internal val OrcaProfileExportKind.formatLabel: String
    get() = when (this) {
        OrcaProfileExportKind.PrinterBundle -> ".orca_printer"
        OrcaProfileExportKind.FilamentBundle -> ".orca_filament"
        OrcaProfileExportKind.PrinterPresetsZip,
        OrcaProfileExportKind.FilamentPresetsZip,
        OrcaProfileExportKind.ProcessPresetsZip -> ".zip"
    }

internal val OrcaProfileExportKind.groupLabel: String
    get() = when (this) {
        OrcaProfileExportKind.PrinterBundle,
        OrcaProfileExportKind.PrinterPresetsZip -> "Printer"
        OrcaProfileExportKind.FilamentBundle,
        OrcaProfileExportKind.FilamentPresetsZip -> "Filament"
        OrcaProfileExportKind.ProcessPresetsZip -> "Process"
    }

internal data class OrcaProfileExportOption(
    val id: String,
    val kind: OrcaProfileExportKind,
    val title: String,
    val subtitle: String,
    val printerIds: List<String> = emptyList(),
    val filamentIds: List<String> = emptyList(),
    val processIds: List<String> = emptyList()
) {
    val fileName: String
        get() = "${safeFileStem(title)}.${kind.fileExtension}"
}

internal fun ProfileStore.orcaProfileExportOptions(): List<OrcaProfileExportOption> {
    val options = mutableListOf<OrcaProfileExportOption>()
    printers.forEach { printer ->
        val linkedFilaments = filaments.filter { it.printerProfileId == printer.id }
        val linkedProcesses = processes.filter { it.printerProfileId == printer.id }
        options += OrcaProfileExportOption(
            id = "printer_bundle:${printer.id}",
            kind = OrcaProfileExportKind.PrinterBundle,
            title = printer.name,
            subtitle = "${linkedFilaments.size} filament, ${linkedProcesses.size} process presets",
            printerIds = listOf(printer.id),
            filamentIds = linkedFilaments.map { it.id },
            processIds = linkedProcesses.map { it.id }
        )
        options += OrcaProfileExportOption(
            id = "printer_zip:${printer.id}",
            kind = OrcaProfileExportKind.PrinterPresetsZip,
            title = "${printer.name} printer preset",
            subtitle = "Single preset JSON for advanced Orca workflows",
            printerIds = listOf(printer.id)
        )
        if (linkedProcesses.isNotEmpty()) {
            options += OrcaProfileExportOption(
                id = "process_zip:${printer.id}",
                kind = OrcaProfileExportKind.ProcessPresetsZip,
                title = "${printer.name} process presets",
                subtitle = "${linkedProcesses.size} linked process preset JSON files",
                processIds = linkedProcesses.map { it.id }
            )
        }
    }
    filaments
        .filter { it.printerProfileId.isNotBlank() && printers.any { printer -> printer.id == it.printerProfileId } }
        .groupBy { it.displayProfileName().trim().lowercase(Locale.US) }
        .values
        .forEach { group ->
            val title = group.first().displayProfileName()
            val printerNames = group.mapNotNull { filament ->
                printers.firstOrNull { it.id == filament.printerProfileId }?.name
            }.distinct()
            options += OrcaProfileExportOption(
                id = "filament_bundle:${group.map { it.id }.sorted().joinToString(",")}",
                kind = OrcaProfileExportKind.FilamentBundle,
                title = title,
                subtitle = "${group.size} Orca filament presets for ${printerNames.joinToString(", ")}",
                filamentIds = group.map { it.id }
            )
            options += OrcaProfileExportOption(
                id = "filament_zip:${group.map { it.id }.sorted().joinToString(",")}",
                kind = OrcaProfileExportKind.FilamentPresetsZip,
                title = "$title filament presets",
                subtitle = "${group.size} linked filament preset JSON files",
                filamentIds = group.map { it.id }
            )
        }
    return options
}

internal fun ProfileStore.exportOrcaProfileOption(option: OrcaProfileExportOption): ByteArray {
    val selectedPrinters = option.printerIds.mapNotNull { id -> printers.firstOrNull { it.id == id } }
    val selectedFilaments = option.filamentIds.mapNotNull { id -> filaments.firstOrNull { it.id == id } }
    val selectedProcesses = option.processIds.mapNotNull { id -> processes.firstOrNull { it.id == id } }
    return when (option.kind) {
        OrcaProfileExportKind.PrinterBundle -> exportOrcaPrinterBundle(selectedPrinters, selectedFilaments, selectedProcesses)
        OrcaProfileExportKind.FilamentBundle -> exportOrcaFilamentBundle(selectedFilaments, printers)
        OrcaProfileExportKind.PrinterPresetsZip -> writeZip(selectedPrinters.map { "${safeFileStem(it.name)}.json" to it.toOrcaPrinterPresetJson().toPrettyJsonBytes() })
        OrcaProfileExportKind.FilamentPresetsZip -> writeZip(selectedFilaments.map { "${safeFileStem(it.orcaFilamentPresetName())}.json" to it.toOrcaFilamentPresetJson().toPrettyJsonBytes() })
        OrcaProfileExportKind.ProcessPresetsZip -> writeZip(selectedProcesses.map { process ->
            "${safeFileStem(process.orcaProcessPresetName())}.json" to process.toOrcaProcessPresetJson(printers.firstOrNull { it.id == process.printerProfileId }).toPrettyJsonBytes()
        })
    }
}

internal fun parseOrcaBundleZip(
    context: Context,
    bytes: ByteArray,
    currentStore: ProfileStore
): ProfileImportPayload.Store? {
    val entries = readZipJsonEntries(bytes)
    val structure = entries["bundle_structure.json"]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val bundleType = structure.optString("bundle_type")
    if (bundleType != ORCA_PRINTER_BUNDLE_TYPE && bundleType != ORCA_FILAMENT_BUNDLE_TYPE) return null
    val printerPaths = structure.optJSONArray("printer_config").toStringList().ifEmpty {
        entries.keys.filter { it.startsWith("printer/") && it.endsWith(".json", ignoreCase = true) }
    }
    val filamentPaths = structure.optJSONArray("filament_config").toStringList().ifEmpty {
        structure.optJSONArray("printer_vendor").toFilamentBundlePaths().ifEmpty {
            entries.keys.filter { it.startsWith("filament/") && it.endsWith(".json", ignoreCase = true) }
        }
    }
    val processPaths = structure.optJSONArray("process_config").toStringList().ifEmpty {
        entries.keys.filter { it.startsWith("process/") && it.endsWith(".json", ignoreCase = true) }
    }

    val importedPrinters = printerPaths.mapNotNull { path ->
        entries[path]?.let { JSONObject(it).toImportedDeviceOrcaPrinterProfile(context, path) }
    }
    val selectedPrinter = importedPrinters.firstOrNull()
        ?: currentStore.printers.firstOrNull { it.id == currentStore.selectedPrinterId }
        ?: currentStore.printers.firstOrNull()
    val importBaseStore = currentStore.copy(
        printers = if (selectedPrinter != null && currentStore.printers.none { it.id == selectedPrinter.id }) {
            currentStore.printers + selectedPrinter
        } else {
            currentStore.printers
        },
        selectedPrinterId = selectedPrinter?.id.orEmpty()
    )
    val importedFilaments = filamentPaths.mapNotNull { path ->
        entries[path]?.let { text ->
            runCatching {
                val json = JSONObject(text)
                val correlatedStore = importBaseStore.withSelectedPrinterForOrcaFilament(path, json)
                json.toImportedDeviceOrcaFilamentProfile(context, path, correlatedStore)
            }.getOrNull()
        }
    }
    val importedProcesses = processPaths.mapNotNull { path ->
        entries[path]?.let { text ->
            runCatching {
                val json = JSONObject(text)
                val correlatedStore = importBaseStore.withSelectedPrinterForOrcaProcess(path, json)
                json.toImportedDeviceOrcaProcessProfile(context, path, correlatedStore)
            }.getOrNull()
        }
    }
    if (importedPrinters.isEmpty() && importedFilaments.isEmpty() && importedProcesses.isEmpty()) {
        error("No Orca profile JSON files were found in the selected bundle.")
    }
    val selectedPrinterId = selectedPrinter?.id.orEmpty()
    val selectedFilamentId = importedFilaments.firstOrNull { it.printerProfileId == selectedPrinterId }?.id.orEmpty()
    val selectedProcessId = importedProcesses.firstOrNull { it.printerProfileId == selectedPrinterId }?.id.orEmpty()
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

private fun ProfileStore.exportOrcaPrinterBundle(
    selectedPrinters: List<PrinterProfile>,
    selectedFilaments: List<FilamentProfile>,
    selectedProcesses: List<ProcessProfile>
): ByteArray {
    val printerExportNames = selectedPrinters.associate { printer ->
        printer.id to printer.orcaBundlePrinterExportName()
    }
    val printerEntries = selectedPrinters.map { printer ->
        val exportName = printerExportNames.getValue(printer.id)
        "printer/${safeFileStem(exportName)}.json" to printer.toOrcaPrinterPresetJson(exportName).toPrettyJsonBytes()
    }
    val filamentEntries = selectedFilaments.map { filament ->
        val exportPrinterName = printerExportNames[filament.printerProfileId]
        val exportName = filament.orcaFilamentPresetName().withExportPrinterSuffix(exportPrinterName)
        "filament/${safeFileStem(exportName)}.json" to filament.toOrcaFilamentPresetJson(
            exportName = exportName,
            exportPrinterName = exportPrinterName
        ).toPrettyJsonBytes()
    }
    val processEntries = selectedProcesses.map { process ->
        val printer = selectedPrinters.firstOrNull { it.id == process.printerProfileId }
        val exportPrinterName = printer?.id?.let { printerExportNames[it] }
        val exportName = process.orcaProcessPresetName().withExportPrinterSuffix(exportPrinterName)
        "process/${safeFileStem(exportName)}.json" to process.toOrcaProcessPresetJson(
            printer = printer,
            exportName = exportName,
            exportPrinterName = exportPrinterName
        ).toPrettyJsonBytes()
    }
    val firstPrinterExportName = selectedPrinters.firstOrNull()?.id?.let { printerExportNames[it] }.orEmpty()
    val structure = JSONObject()
        .put("bundle_id", "offline_${safeFileStem(firstPrinterExportName.ifBlank { "profiles" })}_${timestampForBundleId()}")
        .put("bundle_type", ORCA_PRINTER_BUNDLE_TYPE)
        .put("filament_config", JSONArray().apply { filamentEntries.forEach { put(it.first) } })
        .put("printer_config", JSONArray().apply { printerEntries.forEach { put(it.first) } })
        .put("printer_preset_name", firstPrinterExportName)
        .put("process_config", JSONArray().apply { processEntries.forEach { put(it.first) } })
        .put("version", "")
    return writeZip(printerEntries + filamentEntries + processEntries + ("bundle_structure.json" to structure.toPrettyJsonBytes()))
}

private fun exportOrcaFilamentBundle(
    selectedFilaments: List<FilamentProfile>,
    allPrinters: List<PrinterProfile>
): ByteArray {
    val entries = selectedFilaments.map { filament ->
        val vendor = filament.orcaFilamentVendorName(allPrinters)
        "${safeFileStem(vendor)}/${safeFileStem(filament.orcaFilamentPresetName())}.json" to filament.toOrcaFilamentPresetJson().toPrettyJsonBytes()
    }
    val filamentName = selectedFilaments.firstOrNull()?.name.orEmpty()
    val printerVendor = JSONArray().apply {
        selectedFilaments.groupBy { it.orcaFilamentVendorName(allPrinters) }.forEach { (vendor, filaments) ->
            put(JSONObject()
                .put("vendor", vendor)
                .put("filament_path", JSONArray().apply {
                    filaments.forEach { filament ->
                        put("${safeFileStem(vendor)}/${safeFileStem(filament.orcaFilamentPresetName())}.json")
                    }
                })
            )
        }
    }
    val structure = JSONObject()
        .put("bundle_id", "offline_${safeFileStem(filamentName.ifBlank { "filament" })}_${timestampForBundleId()}")
        .put("bundle_type", ORCA_FILAMENT_BUNDLE_TYPE)
        .put("filament_name", filamentName)
        .put("printer_vendor", printerVendor)
        .put("version", "")
    return writeZip(entries + ("bundle_structure.json" to structure.toPrettyJsonBytes()))
}

private fun PrinterProfile.toOrcaPrinterPresetJson(exportName: String = name): JSONObject =
    baseOrcaJson(orcaResolvedMachineJson, orcaMachineModelJson, orcaMachineOverridesJson) {
        putNativePrinterConfiguration(this@toOrcaPrinterPresetJson)
    }.withOrcaPresetIdentity(
        name = exportName,
        settingIdKey = NativeConfigKeys.Printer.SettingsId,
        settingId = exportName
    ).apply {
        put(NativeConfigKeys.Printer.Name, exportName)
        put("printable_height", maxHeightMm.toOrcaString())
        put(NativeConfigKeys.Bed.PrintableArea, defaultPrintableAreaArray(bedWidthMm, bedDepthMm))
        scrubPrinterSecrets()
    }.dropEmptyVectorConfigValues()
        .orcaJsonScalarStrings()

private fun FilamentProfile.toOrcaFilamentPresetJson(
    exportName: String = orcaFilamentPresetName(),
    exportPrinterName: String? = null
): JSONObject =
    baseOrcaJson(orcaResolvedFilamentJson, orcaRawFilamentJson, orcaFilamentOverridesJson) {
        putNativeFilamentConfiguration(this@toOrcaFilamentPresetJson)
    }.withOrcaPresetIdentity(
        name = exportName,
        settingIdKey = NativeConfigKeys.Filament.SettingsId,
        settingId = exportName
    ).apply {
        put(NativeConfigKeys.Filament.Type, scalarString(NativeConfigKeys.Filament.Type).ifBlank { materialType })
        put("filament_vendor", scalarString("filament_vendor").ifBlank { vendor })
        exportPrinterName?.takeIf { it.isNotBlank() }?.let { printerName ->
            put(NativeConfigKeys.Compatibility.CompatiblePrinters, JSONArray().put(printerName))
            put("compatible_printers_condition", "")
        }
        normalizeSelectionArray("compatible_printers")
        normalizeSelectionArray("compatible_prints")
    }.dropEmptyVectorConfigValues()
        .orcaJsonScalarStrings()

private fun ProcessProfile.toOrcaProcessPresetJson(
    printer: PrinterProfile?,
    exportName: String = orcaProcessPresetName(),
    exportPrinterName: String? = printer?.name
): JSONObject =
    baseOrcaJson(orcaResolvedProcessJson, orcaRawProcessJson, orcaProcessOverridesJson) {
        putNativeProcessConfiguration(this@toOrcaProcessPresetJson)
        putProcessCompatibilityForExport(exportPrinterName)
    }.withOrcaPresetIdentity(
        name = exportName,
        settingIdKey = NativeConfigKeys.Process.SettingsId,
        settingId = exportName
    ).dropEmptyVectorConfigValues()
        .orcaJsonScalarStrings()

private fun baseOrcaJson(
    resolvedJson: String,
    rawJson: String,
    overridesJson: String,
    fallback: JSONObject.() -> Unit
): JSONObject {
    val json = jsonObjectOrNull(resolvedJson)
        ?: jsonObjectOrNull(rawJson)
        ?: JSONObject()
    json.apply(fallback)
    json.mergeJsonObject(jsonObjectOrNull(overridesJson))
    return json
}

private fun JSONObject.withOrcaPresetIdentity(name: String, settingIdKey: String, settingId: String): JSONObject = apply {
    put("version", optString("version").takeIf { it.isValidOrcaSemver() } ?: ORCA_EXPORT_VERSION)
    put("name", name)
    put("from", "User")
    put("inherits", "")
    put("is_custom_defined", "1")
    put(settingIdKey, settingId)
}

private fun JSONObject.putProcessCompatibilityForExport(printerName: String?) {
    printerName
        ?.takeIf { it.isNotBlank() }
        ?.let { put(NativeConfigKeys.Compatibility.CompatiblePrinters, JSONArray().put(it)) }
    if (!has("compatible_printers_condition")) {
        put("compatible_printers_condition", "")
    }
}

private fun JSONObject.normalizeSelectionArray(key: String) {
    val value = opt(key)
    if (value is JSONArray) return
    val selections = parseProfileSelection(value?.toString().orEmpty()).ifEmpty {
        value?.toString()?.takeIf { it.isNotBlank() }?.let { setOf(it) }.orEmpty()
    }
    put(key, JSONArray().apply { selections.forEach { put(it) } })
}

private val emptyScalarExportAllowlist = setOf(
    "inherits",
    "compatible_printers_condition",
    "compatible_prints_condition"
)

private fun JSONObject.dropEmptyVectorConfigValues(): JSONObject = apply {
    val keysToRemove = mutableListOf<String>()
    keys().asSequence().forEach { key ->
        if (key in emptyScalarExportAllowlist) return@forEach
        val value = opt(key)
        if (value is JSONArray && value.length() == 0) {
            keysToRemove += key
        } else if (value is String && value.isBlank() && key.orcaConfigType().isVectorOrcaConfigType()) {
            keysToRemove += key
        }
    }
    keysToRemove.forEach { remove(it) }
}

private fun String.orcaConfigType(): String =
    GeneratedOrcaSettingMetadata.all[this]?.configType.orEmpty()

private fun String.isVectorOrcaConfigType(): Boolean =
    this == "coStrings" || this == "coPointsGroups" || (startsWith("co") && endsWith("s"))

private fun JSONObject.orcaJsonScalarStrings(): JSONObject {
    val result = JSONObject()
    keys().asSequence().sorted().forEach { key ->
        result.put(key, orcaJsonValue(opt(key)))
    }
    return result
}

private fun orcaJsonValue(value: Any?): Any? =
    when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> value.orcaJsonScalarStrings()
        is JSONArray -> JSONArray().apply {
            for (index in 0 until value.length()) {
                put(orcaJsonValue(value.opt(index)))
            }
        }
        is Boolean -> if (value) "1" else "0"
        is Float -> value.toDouble().toOrcaDecimalString()
        is Double -> value.toOrcaDecimalString()
        is Number -> value.toString()
        else -> value.toString()
    }

private fun JSONObject.scrubPrinterSecrets() {
    put("printhost_apikey", "")
    put("printhost_user", "")
    put("printhost_password", "")
    put("print_host", scalarString("print_host"))
    put("print_host_webui", scalarString("print_host_webui"))
}

private fun JSONObject.putIfMissing(key: String, value: Any) {
    if (!has(key)) put(key, value)
}

private fun defaultPrintableAreaArray(width: Float, depth: Float): JSONArray =
    JSONArray()
        .put("0x0")
        .put("${width.toOrcaString()}x0")
        .put("${width.toOrcaString()}x${depth.toOrcaString()}")
        .put("0x${depth.toOrcaString()}")

private fun FilamentProfile.orcaFilamentPresetName(): String =
    displayProfileName().ifBlank { orcaFilamentPath.orcaProfileNameFromPath() }

private fun ProcessProfile.orcaProcessPresetName(): String =
    orcaProcessPath.orcaProfileNameFromPath().ifBlank { displayProfileName() }

private fun PrinterProfile.orcaBundlePrinterExportName(): String =
    name.withMobileSlicerExportSuffix()

private fun String.withExportPrinterSuffix(exportPrinterName: String?): String {
    val printerName = exportPrinterName?.takeIf { it.isNotBlank() } ?: return withMobileSlicerExportSuffix()
    val atIndex = lastIndexOf('@')
    return if (atIndex >= 0) {
        "${substring(0, atIndex + 1).trimEnd()} $printerName".replace("@ ", "@")
    } else {
        "$this @$printerName"
    }.withMobileSlicerExportSuffixIfMissing()
}

private fun String.withMobileSlicerExportSuffix(): String =
    withMobileSlicerExportSuffixIfMissing()

private fun String.withMobileSlicerExportSuffixIfMissing(): String {
    val trimmed = trim()
    if (trimmed.contains("MobileSlicer", ignoreCase = true)) return trimmed
    return "$trimmed (MobileSlicer)"
}

private fun FilamentProfile.displayProfileName(): String =
    name.cleanDisplayProfileName()

private fun ProcessProfile.displayProfileName(): String =
    name.cleanDisplayProfileName()

private fun String.cleanDisplayProfileName(): String {
    val trimmed = trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return trimmed
    val parsed = runCatching { JSONArray(trimmed) }.getOrNull() ?: return trimmed
    if (parsed.length() != 1) return trimmed
    return parsed.optString(0).trim().ifBlank { trimmed }
}

private fun FilamentProfile.orcaFilamentVendorName(allPrinters: List<PrinterProfile>): String =
    allPrinters.firstOrNull { it.id == printerProfileId }?.orcaFamily
        ?.takeIf { it.isNotBlank() }
        ?: allPrinters.firstOrNull { it.id == printerProfileId }?.machineFamily?.takeIf { it.isNotBlank() }
        ?: vendor.takeIf { it.isNotBlank() && it != "(Undefined)" }
        ?: "Custom"

private fun readZipJsonEntries(bytes: ByteArray): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                entries[entry.name] = zip.readBytes().toString(StandardCharsets.UTF_8)
            }
            zip.closeEntry()
        }
    }
    return entries
}

private fun ProfileStore.withSelectedPrinterForOrcaFilament(path: String, json: JSONObject): ProfileStore {
    val printer = findCorrelatedPrinterForOrcaProfile(path, json, NativeConfigKeys.Compatibility.CompatiblePrinters)
        ?: printers.firstOrNull { it.id == selectedPrinterId }
        ?: printers.firstOrNull()
        ?: return this
    val selectedFilament = filaments.firstOrNull { filament ->
        filament.printerProfileId == printer.id &&
            filament.materialType.equals(json.profileConfigString(NativeConfigKeys.Filament.Type), ignoreCase = true)
    }
    return copy(
        selectedPrinterId = printer.id,
        selectedFilamentId = selectedFilament?.id.orEmpty()
    )
}

private fun ProfileStore.withSelectedPrinterForOrcaProcess(path: String, json: JSONObject): ProfileStore {
    val printer = findCorrelatedPrinterForOrcaProfile(path, json, NativeConfigKeys.Compatibility.CompatiblePrinters)
        ?: printers.firstOrNull { it.id == selectedPrinterId }
        ?: printers.firstOrNull()
        ?: return this
    return copy(selectedPrinterId = printer.id)
}

private fun ProfileStore.findCorrelatedPrinterForOrcaProfile(
    path: String,
    json: JSONObject,
    compatibilityKey: String
): PrinterProfile? {
    val compatibleKeys = json.opt(compatibilityKey).toProfileMatchKeys()
    val pathKeys = path.toPathProfileMatchKeys()
    return printers
        .map { printer ->
            printer to printer.orcaImportMatchKeys().scoreCorrelatedPrinter(compatibleKeys, pathKeys)
        }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}

private fun Set<String>.scoreCorrelatedPrinter(compatibleKeys: Set<String>, pathKeys: Set<String>): Int {
    var score = 0
    compatibleKeys.forEach { compatible ->
        if (compatible in this) score += 100
        if (any { printerKey -> compatible.contains(printerKey) || printerKey.contains(compatible) }) score += 25
    }
    pathKeys.forEach { pathKey ->
        if (pathKey in this) score += 20
        if (any { printerKey -> pathKey.contains(printerKey) || printerKey.contains(pathKey) }) score += 5
    }
    return score
}

private fun PrinterProfile.orcaImportMatchKeys(): Set<String> =
    buildList {
        add(id)
        add(name)
        add("$name ${nozzleDiameterMm.toOrcaString()} nozzle")
        add(printerModel)
        add(orcaFamily)
        add(machineFamily)
        addAll(orcaResolvedSourceChains)
        jsonObjectOrNull(orcaResolvedMachineJson)?.let { resolved ->
            add(resolved.optString(NativeConfigKeys.Printer.Name))
            add(resolved.optString(NativeConfigKeys.Printer.SettingsId))
            add(resolved.optString(NativeConfigKeys.Printer.Model))
            add(resolved.optString(NativeConfigKeys.Printer.Inherits))
        }
    }.map { it.cleanProfileMatchKey() }
        .filter { it.isNotBlank() }
        .toSet()

private fun Any?.toProfileMatchKeys(): Set<String> =
    when (this) {
        is JSONArray -> buildSet {
            for (index in 0 until length()) {
                addAll(opt(index).toProfileMatchKeys())
            }
        }
        null, JSONObject.NULL -> emptySet()
        else -> parseProfileSelection(toString()).ifEmpty { setOf(toString()) }
            .map { it.cleanProfileMatchKey() }
            .filter { it.isNotBlank() }
            .toSet()
    }

private fun String.toPathProfileMatchKeys(): Set<String> =
    split('/', '\\', '@')
        .flatMap { part ->
            listOf(
                part,
                part.removeSuffix(".json"),
                part.removeSuffix(".JSON").removeNozzleSuffix()
            )
        }
        .map { it.cleanProfileMatchKey() }
        .filter { it.isNotBlank() }
        .toSet()

private fun writeZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    require(entries.isNotEmpty()) { "There are no Orca profiles available for this export." }
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun JSONObject.toPrettyJsonBytes(): ByteArray =
    toString(4).toByteArray(StandardCharsets.UTF_8)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}

private fun JSONArray?.toFilamentBundlePaths(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            addAll(optJSONObject(index)?.optJSONArray("filament_path").toStringList())
        }
    }
}

private fun String.isValidOrcaSemver(): Boolean =
    matches(Regex("""\d+\.\d+\.\d+(?:\.\d+)?(?:[-+][A-Za-z0-9_.-]+)?"""))

private fun safeFileStem(value: String): String =
    value.trim()
        .ifBlank { "Orca Profile" }
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .take(120)

private fun Float.toOrcaString(): String =
    String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')

private fun Double.toOrcaDecimalString(): String =
    if (isFinite()) {
        String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')
    } else {
        toString()
    }

private fun timestampForBundleId(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
