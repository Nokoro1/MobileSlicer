package com.mobileslicer.workspace

import java.util.Locale

internal enum class SourceModelFileFormat {
    Stl,
    ThreeMf,
    Step
}

internal fun detectSourceModelFileFormat(fileNameOrPath: String?): SourceModelFileFormat? {
    val lower = fileNameOrPath?.lowercase(Locale.US).orEmpty()
    return when {
        lower.endsWith(".stl") -> SourceModelFileFormat.Stl
        lower.endsWith(".3mf") -> SourceModelFileFormat.ThreeMf
        lower.endsWith(".step") || lower.endsWith(".stp") -> SourceModelFileFormat.Step
        else -> null
    }
}

internal fun detectSourceModelMimeType(type: String?): SourceModelFileFormat? {
    val normalized = type?.lowercase(Locale.US).orEmpty()
    return when {
        "3mf" in normalized || normalized == "application/vnd.ms-3mfdocument" -> SourceModelFileFormat.ThreeMf
        "stl" in normalized || normalized == "application/sla" -> SourceModelFileFormat.Stl
        "step" in normalized || "stp" in normalized || "iso-10303" in normalized -> SourceModelFileFormat.Step
        else -> null
    }
}

internal fun isSupportedModelMimeType(type: String?): Boolean {
    val normalized = type?.lowercase(Locale.US).orEmpty()
    return detectSourceModelMimeType(type) != null || normalized == "application/octet-stream"
}
