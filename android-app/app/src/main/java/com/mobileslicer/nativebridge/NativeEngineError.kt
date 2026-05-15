package com.mobileslicer.nativebridge

internal enum class NativeEngineErrorCode {
    PrintableVolumeExceeded,
    ModelLoadFailed,
    PlateLoadFailed,
    ConfigRejected,
    SliceFailed,
    GcodeUnavailable,
    OutputOpenFailed,
    OutputWriteFailed,
    Unknown
}

internal data class NativeEngineError(
    val code: NativeEngineErrorCode,
    val message: String
) {
    val automationStatus: String =
        "${code.name} nativeError=$message"
}

internal fun parseNativeEngineError(message: String?): NativeEngineError? {
    val cleaned = message?.trim().orEmpty()
    if (cleaned.isEmpty()) return null
    val lowercase = cleaned.lowercase()
    val code = when {
        "printable volume exceeded" in lowercase -> NativeEngineErrorCode.PrintableVolumeExceeded
        "stl load failed" in lowercase ||
            "step import" in lowercase ||
            "step file" in lowercase ||
            "model load failed" in lowercase -> NativeEngineErrorCode.ModelLoadFailed
        "plate" in lowercase && "load failed" in lowercase -> NativeEngineErrorCode.PlateLoadFailed
        "config" in lowercase && ("rejected" in lowercase || "failed" in lowercase) -> NativeEngineErrorCode.ConfigRejected
        "no generated g-code" in lowercase -> NativeEngineErrorCode.GcodeUnavailable
        "unable to open g-code output path" in lowercase -> NativeEngineErrorCode.OutputOpenFailed
        "unable to write g-code output file" in lowercase -> NativeEngineErrorCode.OutputWriteFailed
        "slice failed" in lowercase || "slicing" in lowercase -> NativeEngineErrorCode.SliceFailed
        else -> NativeEngineErrorCode.Unknown
    }
    return NativeEngineError(code = code, message = cleaned)
}
