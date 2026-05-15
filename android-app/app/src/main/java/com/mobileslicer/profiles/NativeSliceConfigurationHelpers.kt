package com.mobileslicer.profiles

import org.json.JSONObject
import org.json.JSONArray

internal fun JSONObject.restoreResolvedOrcaParityValues(
    printerJson: JSONObject?,
    filamentJson: JSONObject?,
    processJson: JSONObject?
) {
    copyResolvedValues(printerJson, resolvedPrinterParityKeys)
    restoreResolvedThumbnailValues(printerJson)
    copyResolvedValues(filamentJson, resolvedFilamentParityKeys)
    copyResolvedValues(processJson, resolvedProcessParityKeys)
    processJson?.opt(NativeConfigKeys.Compatibility.CompatiblePrinters)?.let { compatiblePrinters ->
        put(NativeConfigKeys.Compatibility.PrintCompatiblePrinters, compatiblePrinters)
    }
    filamentJson?.scalarString(NativeConfigKeys.Filament.Id)
        ?.takeIf { it.isNotBlank() && !hasNonBlankNativeScalar(NativeConfigKeys.Filament.Ids) }
        ?.let { filamentId ->
        put(NativeConfigKeys.Filament.Ids, filamentId)
    }
    if (optString(NativeConfigKeys.Bed.Shape).isBlank()) {
        printerJson?.opt(NativeConfigKeys.Bed.PrintableArea).toNativeConfigListString()?.let {
            put(NativeConfigKeys.Bed.Shape, it)
        }
    }
    val resolvedDefaultBedType = normalizedOrcaBedTypeName(printerJson?.scalarString(NativeConfigKeys.Bed.DefaultType).orEmpty())
    if (resolvedDefaultBedType in knownOrcaBedTypes &&
        printerJson?.hasNonBlankNativeScalar(NativeConfigKeys.Bed.CurrentType) != true &&
        scalarString(NativeConfigKeys.Bed.CurrentType).isBlank()
    ) {
        put(NativeConfigKeys.Bed.CurrentType, resolvedDefaultBedType)
    }
}

internal fun JSONObject.restoreResolvedOrcaTemplateDefaultValues(
    printerJson: JSONObject?,
    filamentJson: JSONObject?,
    processJson: JSONObject?
) {
    copyResolvedValues(printerJson, resolvedPrinterTemplateDefaultRestoreKeys)
    copyResolvedValues(filamentJson, resolvedFilamentParityKeys - resolvedProfileIdentityKeys)
    copyResolvedValues(processJson, resolvedProcessTemplateDefaultRestoreKeys)
}

private fun JSONObject.restoreResolvedThumbnailValues(printerJson: JSONObject?) {
    if (printerJson == null) return
    val legacyThumbnailSize = printerJson.opt("thumbnail_size").toNativeConfigListString().orEmpty()
    if (legacyThumbnailSize.isNotBlank()) {
        val format = printerJson.nativeResolvedString("thumbnails_format")
            .ifBlank { nativeResolvedString("thumbnails_format") }
            .ifBlank { "PNG" }
        val normalized = normalizeThumbnailDimensionsWithFormat(legacyThumbnailSize, format)
        put("thumbnails", normalized)
        put("thumbnails_format", format.uppercase())
        return
    }
    val resolvedThumbnails = printerJson.nativeResolvedString("thumbnails")
    if (resolvedThumbnails.isNotBlank()) {
        val normalized = normalizeThumbnailDimensionsWithFormat(
            rawThumbnails = resolvedThumbnails,
            defaultFormat = nativeResolvedString("thumbnails_format").ifBlank {
                printerJson.nativeResolvedString("thumbnails_format")
            }
        )
        put("thumbnails", normalized)
        return
    }
}

internal fun JSONObject.ensureFluiddCompatibleThumbnails(printer: PrinterProfile) {
    if (printer.printHostType != PrintHostType.OctoPrint || printer.hasBambuContext()) {
        return
    }

    val normalized = normalizeThumbnailDimensionsWithFormat(
        rawThumbnails = nativeResolvedString("thumbnails"),
        defaultFormat = nativeResolvedString("thumbnails_format").ifBlank { "PNG" }
    )
    val entries = normalized
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()

    fun hasPngThumbnail(width: Int, height: Int): Boolean =
        entries.any { entry ->
            val dimensions = entry.substringBefore('/').trim()
            val format = entry.substringAfter('/', missingDelimiterValue = "PNG").trim().uppercase()
            dimensions.equals("${width}x$height", ignoreCase = true) && format == "PNG"
        }

    if (!hasPngThumbnail(48, 48)) {
        entries += "48x48/PNG"
    }
    if (!hasPngThumbnail(300, 300)) {
        entries += "300x300/PNG"
    }

    put("thumbnails", entries.joinToString(", "))
    put("thumbnails_format", "PNG")
}

private val resolvedProcessTemplateDefaultRestoreKeys = resolvedProcessParityKeys - setOf(
    NativeConfigKeys.PrimeTower.Enable,
    NativeConfigKeys.PrimeTower.Purge
)

private val resolvedPrinterTemplateDefaultRestoreKeys = setOf(
    NativeConfigKeys.Printer.ExtruderAmsCount,
    NativeConfigKeys.Process.WipeTowerType,
    NativeConfigKeys.Process.WipeTowerX,
    NativeConfigKeys.Process.WipeTowerY,
    NativeConfigKeys.Printer.MachineMaxJunctionDeviation,
    NativeConfigKeys.PrimeTower.SingleExtruderMultiMaterial
)

private fun normalizeThumbnailDimensionsWithFormat(rawThumbnails: String, defaultFormat: String): String {
    val format = defaultFormat.trim().ifBlank { "PNG" }.uppercase()
    return rawThumbnails
        .split(',')
        .mapNotNull { rawItem ->
            val item = rawItem.trim().trim('"')
            if (item.isBlank()) {
                null
            } else if ('/' in item) {
                item
            } else {
                "$item/$format"
            }
        }
        .joinToString(", ")
}

private fun JSONObject.copyResolvedValues(source: JSONObject?, keys: Set<String>) {
    if (source == null) return
    keys.forEach { key ->
        if (source.has(key) && !source.isNull(key)) {
            if (source.nativeResolvedString(key).isBlank()) {
                if (key in resolvedProfileIdentityKeys) {
                    return@forEach
                }
                remove(key)
                return@forEach
            }
            put(key, source.opt(key))
        }
    }
}

internal fun JSONObject.hasNonBlankNativeScalar(key: String): Boolean =
    when (val value = opt(key)) {
        is JSONArray -> firstScalarString(value)?.isNotBlank() == true
        null -> false
        else -> value.toString().trim().trim('"').isNotBlank()
    }

internal fun JSONObject.putIfBlankOrZero(key: String, value: String) {
    val current = scalarString(key).trim()
    if (current.isBlank() || current == "0" || current == "0%" || current == "0.0") {
        put(key, value)
    }
}

internal fun JSONObject.putIfBlank(key: String, value: String) {
    if (scalarString(key).isBlank()) {
        put(key, value)
    }
}

internal fun JSONObject.putIfBlankOrValue(key: String, value: String, currentValue: String) {
    val current = scalarString(key)
    if (current.isBlank() || current == currentValue) {
        put(key, value)
    }
}

internal fun JSONObject.removeIfUnresolvedDefault(
    source: JSONObject?,
    key: String,
    vararg defaultValues: String
) {
    if (source?.has(key) == true) return
    removeIfDefaultValue(key, *defaultValues)
}

private fun JSONObject.removeIfDefaultValue(
    key: String,
    vararg defaultValues: String
) {
    val current = scalarString(key)
    if (current in defaultValues) {
        remove(key)
    }
}
