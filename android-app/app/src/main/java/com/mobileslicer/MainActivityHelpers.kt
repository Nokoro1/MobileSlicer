package com.mobileslicer

import java.io.File
import java.util.Locale
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResultSummary

internal const val DEFAULT_ORCA_TEMP_CACHE_MAX_BYTES: Long = 256L * 1024L * 1024L
internal const val DEFAULT_ORCA_TEMP_CACHE_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

internal data class NativeSliceFailurePresentation(
    val userMessage: String,
    val automationStatus: String
)

internal fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes.toDouble() / (1024.0 * 1024.0))

internal fun sanitizeFileName(name: String): String {
    val trimmed = name.trim().ifEmpty { "selected_model.stl" }
    return buildString(trimmed.length) {
        for (ch in trimmed) {
            append(
                when {
                    ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_' -> ch
                    else -> '_'
                }
            )
        }
    }
}

internal fun suggestGcodeFileName(modelName: String?, configJson: String? = null): String {
    calibrationGcodeFileName(configJson)?.let { return it }
    val baseName = sanitizeFileName(modelName ?: "mobile_slicer_output").removeSuffix(".stl")
    return if (baseName.endsWith(".gcode", ignoreCase = true)) baseName else "$baseName.gcode"
}

internal fun suggestExportGcodeFileName(
    plateObjects: List<PlateObject>,
    summary: SliceResultSummary?,
    filamentMaterial: String?,
    fallbackName: String = "mobile_slicer_output.gcode"
): String {
    val base = when (plateObjects.size) {
        0 -> fallbackName.removeSuffix(".gcode")
        1 -> plateObjects.first().label
        else -> "plate_${plateObjects.size}_objects"
    }
        .removeSuffix(".stl")
        .removeSuffix(".STL")
        .removeSuffix(".gcode")
        .removeSuffix(".GCODE")
        .ifBlank { fallbackName.removeSuffix(".gcode") }

    val details = listOfNotNull(
        summary?.estimatedPrintTimeText
            ?.replace(Regex("""\s+"""), "")
            ?.takeIf { it.isNotBlank() },
        normalizedExportFilamentMaterial(filamentMaterial)
    )
    val withDetails = buildString {
        append(base)
        details.forEach { detail ->
            append('_')
            append(detail)
        }
    }
    return suggestGcodeFileName(withDetails)
}

internal fun normalizedExportFilamentMaterial(material: String?): String? {
    val cleaned = material
        ?.substringBefore('@')
        ?.replace('_', ' ')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val knownMaterials = listOf(
        "PLA-CF", "PETG-CF", "PA-CF", "ABS-GF", "ASA-GF",
        "PLA", "PETG", "ABS", "ASA", "TPU", "TPE", "PA", "PC", "PVA", "HIPS"
    )
    val upper = cleaned.uppercase(Locale.US)
    val token = knownMaterials.firstOrNull { known ->
        Regex("""(^|[^A-Z0-9])${Regex.escape(known)}([^A-Z0-9]|$)""").containsMatchIn(upper)
    }
    return (token ?: cleaned.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
    })
        .replace(Regex("""\s+"""), "")
        .takeIf { it.isNotBlank() }
}

internal fun calibrationGcodeFileName(configJson: String?): String? {
    if (configJson.isNullOrBlank() || "mobile_slicer_calibration_active" !in configJson) {
        return null
    }
    return when {
        "\"mobile_slicer_calibration_type\":\"FlowRate\"" in configJson || "FlowRate" in configJson ->
            "Filament Flow Rate Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"PressureAdvance\"" in configJson || "PressureAdvance" in configJson ->
            "Pressure Advance Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"TemperatureTower\"" in configJson || "TemperatureTower" in configJson ->
            "Temperature Tower Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"MaxVolumetricSpeed\"" in configJson || "MaxVolumetricSpeed" in configJson ->
            "Max Volumetric Speed Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"Vfa\"" in configJson || "\"type\":\"Vfa\"" in configJson ->
            "VFA Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"Retraction\"" in configJson || "\"type\":\"Retraction\"" in configJson ->
            "Retraction Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"InputShapingFrequency\"" in configJson || "InputShapingFrequency" in configJson ->
            "Input Shaping Frequency Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"InputShapingDamping\"" in configJson || "InputShapingDamping" in configJson ->
            "Input Shaping Damping Calibration.gcode"
        "\"mobile_slicer_calibration_type\":\"Cornering\"" in configJson || "\"type\":\"Cornering\"" in configJson ->
            "Cornering Calibration.gcode"
        else -> null
    }
}

internal fun cleanupGeneratedGcodeCache(cacheDir: File, retainedPaths: Set<String>) {
    cacheDir.listFiles()?.forEach { file ->
        if (!file.isFile) return@forEach
        val shouldDelete = when {
            file.name.startsWith("latest-slice-") -> true
            file.name.startsWith("latest-send-") && file.name.endsWith(".gcode.3mf") -> true
            file.name.startsWith("orca_wrapper_") && file.name.endsWith(".gcode") -> true
            else -> false
        }
        if (shouldDelete && file.absolutePath !in retainedPaths) {
            file.delete()
        }
    }
}

internal fun cleanupStagedModelCache(cacheDir: File, retainedPaths: Set<String>) {
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile &&
            file.name.startsWith("selected-model-") &&
            file.absolutePath !in retainedPaths
        ) {
            file.delete()
        }
    }
}

internal fun cleanupShareCache(cacheDir: File) {
    File(cacheDir, "shared").deleteRecursively()
}

internal fun cleanupOrcaTempCache(
    cacheDir: File,
    retainedPaths: Set<String>,
    maxBytes: Long = DEFAULT_ORCA_TEMP_CACHE_MAX_BYTES,
    maxAgeMs: Long = DEFAULT_ORCA_TEMP_CACHE_MAX_AGE_MS,
    nowMs: Long = System.currentTimeMillis()
) {
    val tempDir = File(cacheDir, "orca-temp")
    if (!tempDir.exists()) {
        return
    }

    val retainedCanonicalPaths = retainedPaths.mapTo(LinkedHashSet()) { retainedPath ->
        canonicalPathFor(File(retainedPath))
    }
    val byteLimit = maxBytes.coerceAtLeast(0L)
    val ageLimit = maxAgeMs.coerceAtLeast(0L)

    tempDir.walkBottomUp()
        .filter { it.isFile }
        .forEach { file ->
            if (canonicalPathFor(file) !in retainedCanonicalPaths &&
                nowMs - file.lastModified() > ageLimit
            ) {
                file.delete()
            }
        }

    val files = tempDir.walkTopDown()
        .filter { it.isFile }
        .map { CacheFile(file = it, sizeBytes = it.length(), lastModifiedMs = it.lastModified()) }
        .toList()
        .sortedWith(compareBy<CacheFile> { it.lastModifiedMs }.thenBy { it.file.absolutePath })

    var retainedBytes = files.sumOf { it.sizeBytes }
    for (entry in files) {
        if (retainedBytes <= byteLimit) {
            break
        }
        if (canonicalPathFor(entry.file) in retainedCanonicalPaths) {
            continue
        }
        if (entry.file.delete()) {
            retainedBytes -= entry.sizeBytes
        }
    }

    tempDir.walkBottomUp()
        .filter { it.isDirectory && it != tempDir && (it.list()?.isEmpty() == true) }
        .forEach { it.delete() }
}

private data class CacheFile(
    val file: File,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

private fun canonicalPathFor(file: File): String =
    runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
