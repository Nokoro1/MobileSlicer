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
        @JvmStatic external fun nativePlanPlateArrangement(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray, configJson: String, allowRotation: Boolean): DoubleArray?
        @JvmStatic external fun nativePlanAutoOrientation(handle: Long, paths: Array<String>, transforms: DoubleArray, extruderIds: IntArray, configJson: String): DoubleArray?
        @JvmStatic external fun nativeSetModelPlacement(handle: Long, xMm: Double, yMm: Double, zMm: Double): Boolean
        @JvmStatic external fun nativeSetModelTransform(handle: Long, xMm: Double, yMm: Double, zMm: Double, rotationXRadians: Double, rotationYRadians: Double, rotationZRadians: Double, uniformScale: Double): Boolean
        @JvmStatic external fun nativeSetConfigJson(handle: Long, json: String): Boolean
        @JvmStatic external fun nativeSlice(handle: Long): Boolean
        @JvmStatic external fun nativeGetGcodeSummary(handle: Long): String?
        @JvmStatic external fun nativeGetEnrichedGcodeSummary(handle: Long): String?
        @JvmStatic external fun nativeGetSliceMetrics(handle: Long): String?
        @JvmStatic external fun nativePlanLatestSlicePreviewRanges(handle: Long, minLayer: Long, maxLayer: Long, vertexBudget: Long): String?
        @JvmStatic external fun nativeWriteGcodeToFile(handle: Long, path: String): Boolean
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
