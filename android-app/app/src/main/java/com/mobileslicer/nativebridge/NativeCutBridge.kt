package com.mobileslicer.nativebridge

import org.json.JSONArray
import org.json.JSONObject

internal enum class NativeCutMode(val jsonName: String) {
    Plane("plane"),
    Groove("groove"),
    Contour("contour")
}

internal data class NativeCutAttributes(
    val keepUpper: Boolean = true,
    val keepLower: Boolean = true,
    val keepAsParts: Boolean = false,
    val flipUpper: Boolean = false,
    val flipLower: Boolean = false,
    val placeOnCutUpper: Boolean = false,
    val placeOnCutLower: Boolean = false
)

internal data class NativeCutRequest(
    val mobileObjectId: Long,
    val instanceIndex: Int = 0,
    val cutMatrixRowMajor: DoubleArray,
    val mode: NativeCutMode = NativeCutMode.Plane,
    val attributes: NativeCutAttributes = NativeCutAttributes(),
    val outputDirectory: String,
    val connectors: JSONArray = JSONArray(),
    val parts: JSONArray = JSONArray(),
    val groove: JSONObject? = null
) {
    fun toJsonString(): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("mobileObjectId", mobileObjectId)
            .put("instanceIndex", instanceIndex)
            .put("mode", mode.jsonName)
            .put("cutMatrix", JSONArray().apply { cutMatrixRowMajor.forEach { put(it) } })
            .put("attributes", attributes.toJson())
            .put("outputDirectory", outputDirectory)
            .put("connectors", connectors)
            .put("parts", parts)
            .also { json -> groove?.let { json.put("groove", it) } }
            .toString()
}

internal data class NativeCutResultObject(
    val mobileObjectId: Long,
    val label: String,
    val role: String,
    val filePath: String?,
    val volumeCount: Int,
    val cutMetadata: NativeCutMetadata? = null
)

internal data class NativeCutMetadata(
    val id: Long,
    val checkSum: Long,
    val connectorsCount: Long
)

internal data class NativeCutResult(
    val sourceMobileObjectId: Long,
    val cutGroupId: String,
    val objects: List<NativeCutResultObject>,
    val rawJson: String
)

internal sealed class NativeCutCallResult {
    data class Success(val result: NativeCutResult) : NativeCutCallResult()
    data object Unavailable : NativeCutCallResult()
    data class Failure(val message: String) : NativeCutCallResult()

    val succeeded: Boolean get() = this is Success
}

internal object NativeCutCalls {
    fun cutObject(handle: NativeEngineHandle, request: NativeCutRequest): NativeCutCallResult =
        try {
            if (!NativeEngineBridge.nativeCutObject(handle.raw, request.toJsonString())) {
                NativeCutCallResult.Failure(
                    NativeEngineBridge.nativeGetLastError(handle.raw)?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "native cut failed"
                )
            } else {
                val resultJson = NativeEngineBridge.nativeGetLastCutResultJson(handle.raw)
                    ?.takeIf { it.isNotBlank() }
                    ?: return NativeCutCallResult.Failure("native cut did not return a result")
                NativeCutCallResult.Success(parseNativeCutResult(resultJson))
            }
        } catch (_: UnsatisfiedLinkError) {
            NativeCutCallResult.Unavailable
        } catch (error: Exception) {
            NativeCutCallResult.Failure(error.message ?: "native cut failed")
        }
}

private fun NativeCutAttributes.toJson(): JSONObject =
    JSONObject()
        .put("keepUpper", keepUpper)
        .put("keepLower", keepLower)
        .put("keepAsParts", keepAsParts)
        .put("flipUpper", flipUpper)
        .put("flipLower", flipLower)
        .put("placeOnCutUpper", placeOnCutUpper)
        .put("placeOnCutLower", placeOnCutLower)

internal fun parseNativeCutResult(json: String): NativeCutResult {
    val root = JSONObject(json)
    val objects = root.optJSONArray("objects") ?: JSONArray()
    return NativeCutResult(
        sourceMobileObjectId = root.optLong("sourceMobileObjectId", 0L),
        cutGroupId = root.optString("cutGroupId"),
        objects = List(objects.length()) { index ->
            val item = objects.getJSONObject(index)
            NativeCutResultObject(
                mobileObjectId = item.optLong("mobileObjectId", 0L),
                label = item.optString("label", "Cut result"),
                role = item.optString("role", "part"),
                filePath = item.optString("filePath", "").takeIf { it.isNotBlank() },
                volumeCount = item.optInt("volumeCount", 0),
                cutMetadata = item.optJSONObject("cutMetadata")?.let { metadata ->
                    NativeCutMetadata(
                        id = metadata.optLong("id", 0L),
                        checkSum = metadata.optLong("checkSum", 0L),
                        connectorsCount = metadata.optLong("connectorsCount", 0L)
                    )
                }
            )
        },
        rawJson = json
    )
}
