package com.mobileslicer.nativebridge

@JvmInline
internal value class NativeEngineHandle private constructor(val raw: Long) {
    companion object {
        fun fromRaw(raw: Long): NativeEngineHandle? =
            if (raw != 0L) NativeEngineHandle(raw) else null
    }
}

internal sealed class NativeEngineCallResult {
    abstract val statusMessage: String

    data object Success : NativeEngineCallResult() {
        override val statusMessage: String = "success"
    }

    data class Failure(
        val operation: String,
        val error: NativeEngineError?
    ) : NativeEngineCallResult() {
        override val statusMessage: String =
            error?.let { "$operation failed: ${it.automationStatus}" }
                ?: "$operation failed"
    }
}

internal fun NativeEngineCallResult.isSuccess(): Boolean =
    this is NativeEngineCallResult.Success

internal object NativeEngineCalls {
    fun trimMemory() {
        NativeEngineBridge.nativeTrimMemory()
    }

    fun loadModel(handle: NativeEngineHandle, path: String): NativeEngineCallResult =
        booleanCall(handle, "nativeLoadModel") {
            NativeEngineBridge.nativeLoadModel(handle.raw, path)
        }

    fun clearGeneratedGcode(handle: NativeEngineHandle) {
        NativeEngineBridge.nativeClearGeneratedGcode(handle.raw)
    }

    fun setGcodePreviewGeneration(handle: NativeEngineHandle, generation: Long): NativeEngineCallResult =
        booleanCall(handle, "nativeSetGcodePreviewGeneration") {
            NativeEngineBridge.nativeSetGcodePreviewGeneration(handle.raw, generation)
        }

    fun loadPlateModels(
        handle: NativeEngineHandle,
        paths: Array<String>,
        transforms: DoubleArray,
        extruderIds: IntArray
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeLoadPlateModels") {
            NativeEngineBridge.nativeLoadPlateModels(handle.raw, paths, transforms, extruderIds)
        }

    fun loadPlateModelsV2(
        handle: NativeEngineHandle,
        paths: Array<String>,
        transforms: DoubleArray,
        extruderIds: IntArray,
        mobileObjectIds: LongArray,
        paintPayloadJson: String
    ): NativeEngineCallResult {
        return try {
            booleanCall(handle, "nativeLoadPlateModelsV2") {
                NativeEngineBridge.nativeLoadPlateModelsV2(
                    handle.raw,
                    paths,
                    transforms,
                    extruderIds,
                    mobileObjectIds,
                    paintPayloadJson
                )
            }
        } catch (error: UnsatisfiedLinkError) {
            NativeEngineCallResult.Failure(
                operation = "nativeLoadPlateModelsV2",
                error = null
            )
        }
    }

    fun loadProject3mf(
        handle: NativeEngineHandle,
        path: String,
        mobileObjectIds: LongArray
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeLoadProject3mf") {
            NativeEngineBridge.nativeLoadProject3mf(handle.raw, path, mobileObjectIds)
        }

    fun extractModelMeshToStl(
        handle: NativeEngineHandle,
        inputPath: String,
        outputStlPath: String
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeExtractModelMeshToStl") {
            NativeEngineBridge.nativeExtractModelMeshToStl(handle.raw, inputPath, outputStlPath)
        }

    fun planPlateArrangement(
        handle: NativeEngineHandle,
        paths: Array<String>,
        transforms: DoubleArray,
        extruderIds: IntArray,
        configJson: String,
        allowRotation: Boolean
    ): DoubleArray? =
        NativeEngineBridge.nativePlanPlateArrangement(
            handle.raw,
            paths,
            transforms,
            extruderIds,
            configJson,
            allowRotation
        )

    fun planAutoOrientation(
        handle: NativeEngineHandle,
        paths: Array<String>,
        transforms: DoubleArray,
        extruderIds: IntArray,
        configJson: String
    ): DoubleArray? =
        NativeEngineBridge.nativePlanAutoOrientation(
            handle.raw,
            paths,
            transforms,
            extruderIds,
            configJson
        )

    fun cancelPlanning(handle: NativeEngineHandle): Boolean =
        NativeEngineBridge.nativeCancelPlanning(handle.raw)

    fun setModelTransform(
        handle: NativeEngineHandle,
        xMm: Double,
        yMm: Double,
        zMm: Double,
        rotationXRadians: Double,
        rotationYRadians: Double,
        rotationZRadians: Double,
        uniformScale: Double
    ): NativeEngineCallResult =
        booleanCall(handle, "nativeSetModelTransform") {
            NativeEngineBridge.nativeSetModelTransform(
                handle.raw,
                xMm,
                yMm,
                zMm,
                rotationXRadians,
                rotationYRadians,
                rotationZRadians,
                uniformScale
            )
        }

    fun setConfigJson(handle: NativeEngineHandle, json: String): NativeEngineCallResult =
        booleanCall(handle, "nativeSetConfigJson") {
            NativeEngineBridge.nativeSetConfigJson(handle.raw, json)
        }

    fun slice(handle: NativeEngineHandle): NativeEngineCallResult =
        booleanCall(handle, "nativeSlice") {
            NativeEngineBridge.nativeSlice(handle.raw)
        }

    fun writeGcodeToFile(handle: NativeEngineHandle, path: String): NativeEngineCallResult =
        booleanCall(handle, "nativeWriteGcodeToFile") {
            NativeEngineBridge.nativeWriteGcodeToFile(handle.raw, path)
        }

    fun writeProject3mfToFile(handle: NativeEngineHandle, path: String): NativeEngineCallResult =
        booleanCall(handle, "nativeWriteProject3mfToFile") {
            NativeEngineBridge.nativeWriteProject3mfToFile(handle.raw, path)
        }

    fun writeBambuGcode3mfToFile(handle: NativeEngineHandle, path: String): NativeEngineCallResult =
        booleanCall(handle, "nativeWriteBambuGcode3mfToFile") {
            NativeEngineBridge.nativeWriteBambuGcode3mfToFile(handle.raw, path)
        }

    fun getGcodeSummary(handle: NativeEngineHandle): String? =
        NativeEngineBridge.nativeGetGcodeSummary(handle.raw)

    fun getEnrichedGcodeSummary(handle: NativeEngineHandle): String? =
        NativeEngineBridge.nativeGetEnrichedGcodeSummary(handle.raw)

    fun getSliceMetrics(handle: NativeEngineHandle): String? =
        NativeEngineBridge.nativeGetSliceMetrics(handle.raw)

    fun planLatestSlicePreviewRanges(
        handle: NativeEngineHandle,
        minLayer: Long,
        maxLayer: Long,
        vertexBudget: Long
    ): String? =
        NativeEngineBridge.nativePlanLatestSlicePreviewRanges(handle.raw, minLayer, maxLayer, vertexBudget)

    fun getLastErrorMessage(handle: NativeEngineHandle): String =
        NativeEngineBridge.nativeGetLastError(handle.raw)?.trim().orEmpty()

    fun getLastEngineError(handle: NativeEngineHandle): NativeEngineError? =
        NativeEngineBridge.nativeGetLastEngineError(handle.raw)

    private fun booleanCall(
        handle: NativeEngineHandle,
        operation: String,
        call: () -> Boolean
    ): NativeEngineCallResult =
        if (call()) {
            NativeEngineCallResult.Success
        } else {
            NativeEngineCallResult.Failure(
                operation = operation,
                error = NativeEngineBridge.nativeGetLastEngineError(handle.raw)
            )
        }
}
