package com.mobileslicer.nativebridge

class NativeEngineBridge private constructor() {
    companion object {
        init {
            System.loadLibrary("orca_engine")
        }

        @JvmStatic external fun nativeConfigureRuntimePaths(resourcesDir: String, temporaryDir: String)
        @JvmStatic external fun nativeCreateEngine(): Long
        @JvmStatic external fun nativeDestroyEngine(handle: Long)
        @JvmStatic external fun nativeTrimMemory()
        @JvmStatic external fun nativeLoadModel(handle: Long, path: String): Boolean
        @JvmStatic external fun nativeClearGeneratedGcode(handle: Long)
        @JvmStatic external fun nativeLoadPlateModels(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray): Boolean
        @JvmStatic external fun nativeLoadPlateModelsV2(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray, mobileObjectIds: LongArray, paintPayloadJson: String): Boolean
        @JvmStatic external fun nativeLoadProject3mf(handle: Long, path: String, mobileObjectIds: LongArray): Boolean
        @JvmStatic external fun nativeExtractModelMeshToStl(handle: Long, inputPath: String, outputStlPath: String): Boolean
        @JvmStatic external fun nativeSplitModelMeshToStls(handle: Long, inputPath: String, outputDirectory: String, splitMode: Int): Boolean
        @JvmStatic external fun nativeGetLastSplitResultJson(handle: Long): String?
        @JvmStatic external fun nativeCutObject(handle: Long, requestJson: String): Boolean
        @JvmStatic external fun nativeGetLastCutResultJson(handle: Long): String?
        @JvmStatic external fun nativePaintBeginSession(handle: Long, mobileObjectId: Long, mode: Int): Boolean
        @JvmStatic external fun nativePaintBindingDebugJson(handle: Long): String?
        @JvmStatic external fun nativePaintObjectBoundsJson(handle: Long, mobileObjectId: Long): String?
        @JvmStatic external fun nativePaintEndSession(handle: Long, commit: Boolean): Boolean
        @JvmStatic external fun nativePaintHitTestRay(handle: Long, ray: FloatArray): FloatArray?
        @JvmStatic external fun nativePaintSetTool(handle: Long, brushShape: Int, action: Int, brushRadiusMm: Float, brushHeightMm: Float, colorSlot: Int): Boolean
        @JvmStatic external fun nativePaintSetToolOptions(handle: Long, smartFillAngleDeg: Float, overhangAngleDeg: Float, clippingPlane: FloatArray?): Boolean
        @JvmStatic external fun nativePaintStrokeBeginRay(handle: Long, ray: FloatArray, brushShape: Int, action: Int, brushRadiusMm: Float): Boolean
        @JvmStatic external fun nativePaintStrokeMoveRay(handle: Long, ray: FloatArray, brushShape: Int, action: Int, brushRadiusMm: Float): Boolean
        @JvmStatic external fun nativePaintStrokeEnd(handle: Long, commit: Boolean): Boolean
        @JvmStatic external fun nativePaintUndo(handle: Long): Boolean
        @JvmStatic external fun nativePaintRedo(handle: Long): Boolean
        @JvmStatic external fun nativePaintClear(handle: Long): Boolean
        @JvmStatic external fun nativePaintCanUndo(handle: Long): Boolean
        @JvmStatic external fun nativePaintCanRedo(handle: Long): Boolean
        @JvmStatic external fun nativePaintSerialize(handle: Long): String?
        @JvmStatic external fun nativePaintGetOverlay(handle: Long): String?
        @JvmStatic external fun nativePaintGetOverlayInterleaved(handle: Long): FloatArray?
        @JvmStatic external fun nativePaintGetOverlayInterleavedBuffer(handle: Long): java.nio.ByteBuffer?
        @JvmStatic external fun nativePaintGetOverlayDelta(handle: Long): String?
        @JvmStatic external fun nativePaintGetOverlayDeltaInterleaved(handle: Long): FloatArray?
        @JvmStatic external fun nativePaintGetOverlayDeltaInterleavedBuffer(handle: Long): java.nio.ByteBuffer?
        @JvmStatic external fun nativePaintRemapColorSlots(handle: Long, payloadJson: String, oldSlotToNewSlot: IntArray): String?
        @JvmStatic external fun nativePlanPlateArrangement(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray, configJson: String, allowRotation: Boolean): DoubleArray?
        @JvmStatic external fun nativePlanAutoOrientation(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray, configJson: String): DoubleArray?
        @JvmStatic external fun nativeCancelPlanning(handle: Long): Boolean
        @JvmStatic external fun nativeSetModelPlacement(handle: Long, xMm: Double, yMm: Double, zMm: Double): Boolean
        @JvmStatic external fun nativeSetModelTransform(handle: Long, xMm: Double, yMm: Double, zMm: Double, rotationXRadians: Double, rotationYRadians: Double, rotationZRadians: Double, uniformScale: Double): Boolean
        @JvmStatic external fun nativeSetConfigJson(handle: Long, json: String): Boolean
        @JvmStatic external fun nativeSlice(handle: Long): Boolean
        @JvmStatic external fun nativeGetGcodeSummary(handle: Long): String?
        @JvmStatic external fun nativeGetEnrichedGcodeSummary(handle: Long): String?
        @JvmStatic external fun nativeGetSliceMetrics(handle: Long): String?
        @JvmStatic external fun nativePlanLatestSlicePreviewRanges(handle: Long, minLayer: Long, maxLayer: Long, vertexBudget: Long): String?
        @JvmStatic external fun nativeWriteGcodeToFile(handle: Long, path: String): Boolean
        @JvmStatic external fun nativeWriteProject3mfToFile(handle: Long, path: String): Boolean
        @JvmStatic external fun nativeWriteBambuGcode3mfToFile(handle: Long, path: String): Boolean
        @JvmStatic external fun nativeGetLastError(handle: Long): String?
        internal fun nativeGetLastEngineError(handle: Long): NativeEngineError? =
            parseNativeEngineError(nativeGetLastError(handle))
        @JvmStatic external fun nativeCreateGcodeViewer(): Long
        @JvmStatic external fun nativeDestroyGcodeViewer(handle: Long)
        @JvmStatic external fun nativeShutdownGcodeViewer(handle: Long): Boolean
        @JvmStatic external fun nativeLoadGcodeIntoGcodeViewer(viewerHandle: Long, gcode: String): Boolean
        @JvmStatic external fun nativeSetGcodePreviewGeneration(engineHandle: Long, generation: Long): Boolean
        @JvmStatic external fun nativeLoadLatestSliceIntoGcodeViewer(viewerHandle: Long, engineHandle: Long, minLayer: Long, maxLayer: Long, lodHint: Int, generation: Long): Boolean
        @JvmStatic external fun nativeRenderGcodeViewer(viewerHandle: Long, viewMatrix: FloatArray, projectionMatrix: FloatArray): Boolean
        @JvmStatic external fun nativeGetGcodeViewerLayersCount(viewerHandle: Long): Long
        @JvmStatic external fun nativeSetGcodeViewerLayerRange(viewerHandle: Long, minLayer: Long, maxLayer: Long): Boolean
        @JvmStatic external fun nativeSetGcodeViewerExtrusionWidthScale(viewerHandle: Long, scale: Float): Boolean
        @JvmStatic external fun nativeSetGcodeViewerPathVisibility(viewerHandle: Long, kind: Int, id: Int, visible: Boolean): Boolean
        @JvmStatic external fun nativeSetGcodeViewerViewType(viewerHandle: Long, viewType: Int): Boolean
        @JvmStatic external fun nativeGetGcodeViewerLastError(viewerHandle: Long): String?
        @JvmStatic external fun nativeGetGcodeViewerLastLoadMetrics(viewerHandle: Long): String?
    }
}
