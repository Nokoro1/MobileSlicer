package com.mobileslicer.nativebridge

import org.json.JSONArray
import org.json.JSONObject

internal enum class NativeSplitMode(val nativeValue: Int, val label: String) {
    Objects(0, "objects"),
    Parts(1, "parts")
}

internal data class NativeSplitResultObject(
    val label: String,
    val role: String,
    val filePath: String,
    val volumeCount: Int
)

internal data class NativeSplitResult(
    val mode: NativeSplitMode,
    val sourcePath: String,
    val objects: List<NativeSplitResultObject>,
    val rawJson: String
)

internal sealed class NativeSplitCallResult {
    data class Success(val result: NativeSplitResult) : NativeSplitCallResult()
    data object Unavailable : NativeSplitCallResult()
    data class Failure(val message: String) : NativeSplitCallResult()
}

internal object NativeSplitCalls {
    fun splitModelMesh(
        handle: NativeEngineHandle,
        inputPath: String,
        outputDirectory: String,
        mode: NativeSplitMode
    ): NativeSplitCallResult =
        try {
            if (!NativeEngineBridge.nativeSplitModelMeshToStls(handle.raw, inputPath, outputDirectory, mode.nativeValue)) {
                NativeSplitCallResult.Failure(
                    NativeEngineBridge.nativeGetLastError(handle.raw)?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "native split failed"
                )
            } else {
                val resultJson = NativeEngineBridge.nativeGetLastSplitResultJson(handle.raw)
                    ?.takeIf { it.isNotBlank() }
                    ?: return NativeSplitCallResult.Failure("native split did not return a result")
                NativeSplitCallResult.Success(parseNativeSplitResult(resultJson, mode))
            }
        } catch (_: UnsatisfiedLinkError) {
            NativeSplitCallResult.Unavailable
        } catch (error: Exception) {
            NativeSplitCallResult.Failure(error.message ?: "native split failed")
        }
}

private fun parseNativeSplitResult(json: String, fallbackMode: NativeSplitMode): NativeSplitResult {
    val root = JSONObject(json)
    val mode = when (root.optString("mode", fallbackMode.label)) {
        NativeSplitMode.Parts.label -> NativeSplitMode.Parts
        else -> NativeSplitMode.Objects
    }
    val objects = root.optJSONArray("objects") ?: JSONArray()
    return NativeSplitResult(
        mode = mode,
        sourcePath = root.optString("sourcePath"),
        objects = List(objects.length()) { index ->
            val item = objects.getJSONObject(index)
            NativeSplitResultObject(
                label = item.optString("label", "Split result"),
                role = item.optString("role", if (mode == NativeSplitMode.Parts) "part" else "object"),
                filePath = item.optString("filePath"),
                volumeCount = item.optInt("volumeCount", 0)
            )
        }.filter { it.filePath.isNotBlank() },
        rawJson = json
    )
}
