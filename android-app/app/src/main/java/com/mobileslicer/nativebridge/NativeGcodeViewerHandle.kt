package com.mobileslicer.nativebridge

@JvmInline
internal value class NativeGcodeViewerHandle private constructor(val raw: Long) {
    companion object {
        fun fromRaw(raw: Long): NativeGcodeViewerHandle? =
            if (raw != 0L) NativeGcodeViewerHandle(raw) else null
    }
}

internal object NativeGcodeViewerCalls {
    fun create(): NativeGcodeViewerHandle? =
        NativeGcodeViewerHandle.fromRaw(NativeEngineBridge.nativeCreateGcodeViewer())

    fun shutdown(handle: NativeGcodeViewerHandle): NativeEngineCallResult =
        booleanCall(handle, "nativeShutdownGcodeViewer") {
            NativeEngineBridge.nativeShutdownGcodeViewer(handle.raw)
        }

    fun destroy(handle: NativeGcodeViewerHandle) {
        NativeEngineBridge.nativeDestroyGcodeViewer(handle.raw)
    }

    fun loadLatestSlice(
        handle: NativeGcodeViewerHandle,
        engineHandle: NativeEngineHandle,
        minLayer: Long,
        maxLayer: Long,
        lodHint: Int,
        generation: Long
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeLoadLatestSliceIntoGcodeViewer") {
            NativeEngineBridge.nativeLoadLatestSliceIntoGcodeViewer(
                handle.raw,
                engineHandle.raw,
                minLayer,
                maxLayer,
                lodHint,
                generation
            )
        }

    fun loadGcode(handle: NativeGcodeViewerHandle, gcode: String): NativeEngineCallResult =
        booleanCall(handle, "nativeLoadGcodeIntoGcodeViewer") {
            NativeEngineBridge.nativeLoadGcodeIntoGcodeViewer(handle.raw, gcode)
        }

    fun render(
        handle: NativeGcodeViewerHandle,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeRenderGcodeViewer") {
            NativeEngineBridge.nativeRenderGcodeViewer(handle.raw, viewMatrix, projectionMatrix)
        }

    fun setLayerRange(
        handle: NativeGcodeViewerHandle,
        minLayer: Long,
        maxLayer: Long
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeSetGcodeViewerLayerRange") {
            NativeEngineBridge.nativeSetGcodeViewerLayerRange(handle.raw, minLayer, maxLayer)
        }

    fun setExtrusionWidthScale(handle: NativeGcodeViewerHandle, scale: Float): NativeEngineCallResult =
        booleanCall(handle, "nativeSetGcodeViewerExtrusionWidthScale") {
            NativeEngineBridge.nativeSetGcodeViewerExtrusionWidthScale(handle.raw, scale)
        }

    fun setPathVisibility(
        handle: NativeGcodeViewerHandle,
        kind: Int,
        id: Int,
        visible: Boolean
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeSetGcodeViewerPathVisibility") {
            NativeEngineBridge.nativeSetGcodeViewerPathVisibility(handle.raw, kind, id, visible)
        }

    fun setViewType(handle: NativeGcodeViewerHandle, viewType: Int): NativeEngineCallResult =
        booleanCall(handle, "nativeSetGcodeViewerViewType") {
            NativeEngineBridge.nativeSetGcodeViewerViewType(handle.raw, viewType)
        }

    fun getLayersCount(handle: NativeGcodeViewerHandle): Long =
        NativeEngineBridge.nativeGetGcodeViewerLayersCount(handle.raw)

    fun getLastErrorMessage(handle: NativeGcodeViewerHandle): String =
        NativeEngineBridge.nativeGetGcodeViewerLastError(handle.raw)?.trim().orEmpty()

    fun getLastLoadMetrics(handle: NativeGcodeViewerHandle): String =
        NativeEngineBridge.nativeGetGcodeViewerLastLoadMetrics(handle.raw)?.trim().orEmpty()

    private fun booleanCall(
        handle: NativeGcodeViewerHandle,
        operation: String,
        call: () -> Boolean
    ): NativeEngineCallResult =
        if (call()) {
            NativeEngineCallResult.Success
        } else {
            NativeEngineCallResult.Failure(
                operation = operation,
                error = NativeEngineError(
                    code = NativeEngineErrorCode.Unknown,
                    message = getLastErrorMessage(handle).ifBlank { "$operation returned false" }
                )
            )
        }
}
