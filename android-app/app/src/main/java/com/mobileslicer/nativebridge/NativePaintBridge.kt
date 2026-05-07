package com.mobileslicer.nativebridge

import java.nio.ByteBuffer

internal enum class NativePaintMode(val nativeId: Int) {
    Support(0),
    Seam(1),
    Color(2),
    FuzzySkin(3)
}

internal enum class NativePaintBrushShape(val nativeId: Int) {
    Circle(0),
    Sphere(1),
    Fill(2),
    SmartFill(3),
    Triangle(4),
    HeightRange(5),
    GapFill(6)
}

internal enum class NativePaintAction(val nativeId: Int) {
    Paint(0),
    Erase(1),
    Enforce(2),
    Block(3)
}

internal sealed class NativePaintCallResult {
    data object Success : NativePaintCallResult()
    data object Unavailable : NativePaintCallResult()
    data class Failure(val message: String) : NativePaintCallResult()

    val succeeded: Boolean get() = this is Success
}

internal data class NativePaintHit(
    val volumeIndex: Int,
    val facetIndex: Int,
    val distance: Float,
    val pointX: Float,
    val pointY: Float,
    val pointZ: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float
)

internal object NativePaintCalls {
    fun beginSession(
        handle: NativeEngineHandle,
        mobileObjectId: Long,
        mode: NativePaintMode
    ): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintBeginSession") {
            NativeEngineBridge.nativePaintBeginSession(handle.raw, mobileObjectId, mode.nativeId)
        }

    fun bindingDebugJson(handle: NativeEngineHandle): String? =
        NativeEngineBridge.nativePaintBindingDebugJson(handle.raw)

    fun objectBoundsJson(handle: NativeEngineHandle, mobileObjectId: Long): String? =
        NativeEngineBridge.nativePaintObjectBoundsJson(handle.raw, mobileObjectId)

    fun endSession(handle: NativeEngineHandle, commit: Boolean): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintEndSession") {
            NativeEngineBridge.nativePaintEndSession(handle.raw, commit)
        }

    fun hitTestRay(handle: NativeEngineHandle, ray: FloatArray): NativePaintHit? =
        try {
            val hit = NativeEngineBridge.nativePaintHitTestRay(handle.raw, ray) ?: return null
            if (hit.size < 9) {
                null
            } else {
                NativePaintHit(
                    volumeIndex = hit[0].toInt(),
                    facetIndex = hit[1].toInt(),
                    distance = hit[2],
                    pointX = hit[3],
                    pointY = hit[4],
                    pointZ = hit[5],
                    normalX = hit[6],
                    normalY = hit[7],
                    normalZ = hit[8]
                )
            }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun setTool(
        handle: NativeEngineHandle,
        brushShape: NativePaintBrushShape,
        action: NativePaintAction,
        brushRadiusMm: Float,
        brushHeightMm: Float,
        colorSlot: Int
    ): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintSetTool") {
            NativeEngineBridge.nativePaintSetTool(
                handle.raw,
                brushShape.nativeId,
                action.nativeId,
                brushRadiusMm,
                brushHeightMm,
                colorSlot
            )
        }

    fun setToolOptions(
        handle: NativeEngineHandle,
        smartFillAngleDeg: Float = 30f,
        overhangAngleDeg: Float = 0f,
        clippingPlane: FloatArray? = null
    ): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintSetToolOptions") {
            NativeEngineBridge.nativePaintSetToolOptions(
                handle.raw,
                smartFillAngleDeg,
                overhangAngleDeg,
                clippingPlane
            )
        }

    fun strokeBeginRay(
        handle: NativeEngineHandle,
        ray: FloatArray,
        brushShape: NativePaintBrushShape,
        action: NativePaintAction,
        brushRadiusMm: Float
    ): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintStrokeBeginRay") {
            NativeEngineBridge.nativePaintStrokeBeginRay(
                handle.raw,
                ray,
                brushShape.nativeId,
                action.nativeId,
                brushRadiusMm
            )
        }

    fun strokeMoveRay(
        handle: NativeEngineHandle,
        ray: FloatArray,
        brushShape: NativePaintBrushShape,
        action: NativePaintAction,
        brushRadiusMm: Float
    ): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintStrokeMoveRay") {
            NativeEngineBridge.nativePaintStrokeMoveRay(
                handle.raw,
                ray,
                brushShape.nativeId,
                action.nativeId,
                brushRadiusMm
            )
        }

    fun strokeEnd(handle: NativeEngineHandle, commit: Boolean): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintStrokeEnd") {
            NativeEngineBridge.nativePaintStrokeEnd(handle.raw, commit)
        }

    fun undo(handle: NativeEngineHandle): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintUndo") {
            NativeEngineBridge.nativePaintUndo(handle.raw)
        }

    fun redo(handle: NativeEngineHandle): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintRedo") {
            NativeEngineBridge.nativePaintRedo(handle.raw)
        }

    fun clear(handle: NativeEngineHandle): NativePaintCallResult =
        booleanPaintCall(handle, "nativePaintClear") {
            NativeEngineBridge.nativePaintClear(handle.raw)
        }

    fun canUndo(handle: NativeEngineHandle): Boolean =
        try {
            NativeEngineBridge.nativePaintCanUndo(handle.raw)
        } catch (_: UnsatisfiedLinkError) {
            false
        }

    fun canRedo(handle: NativeEngineHandle): Boolean =
        try {
            NativeEngineBridge.nativePaintCanRedo(handle.raw)
        } catch (_: UnsatisfiedLinkError) {
            false
        }

    fun serialize(handle: NativeEngineHandle): String? =
        try {
            NativeEngineBridge.nativePaintSerialize(handle.raw)?.takeIf { it.isNotBlank() }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlay(handle: NativeEngineHandle): String? =
        try {
            NativeEngineBridge.nativePaintGetOverlay(handle.raw)?.takeIf { it.isNotBlank() }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlayInterleaved(handle: NativeEngineHandle): FloatArray? =
        try {
            NativeEngineBridge.nativePaintGetOverlayInterleaved(handle.raw)
                ?.takeIf { it.size > 1 }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlayInterleavedBuffer(handle: NativeEngineHandle): ByteBuffer? =
        try {
            NativeEngineBridge.nativePaintGetOverlayInterleavedBuffer(handle.raw)
                ?.takeIf { it.capacity() > Float.SIZE_BYTES }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlayDelta(handle: NativeEngineHandle): String? =
        try {
            NativeEngineBridge.nativePaintGetOverlayDelta(handle.raw)?.takeIf { it.isNotBlank() }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlayDeltaInterleaved(handle: NativeEngineHandle): FloatArray? =
        try {
            NativeEngineBridge.nativePaintGetOverlayDeltaInterleaved(handle.raw)
                ?.takeIf { it.size > 1 }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun overlayDeltaInterleavedBuffer(handle: NativeEngineHandle): ByteBuffer? =
        try {
            NativeEngineBridge.nativePaintGetOverlayDeltaInterleavedBuffer(handle.raw)
                ?.takeIf { it.capacity() > Float.SIZE_BYTES }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    fun remapColorSlots(
        handle: NativeEngineHandle,
        payloadJson: String,
        oldSlotToNewSlot: IntArray
    ): String? =
        try {
            NativeEngineBridge.nativePaintRemapColorSlots(
                handle.raw,
                payloadJson,
                oldSlotToNewSlot
            )?.takeIf { it.isNotBlank() }
        } catch (_: UnsatisfiedLinkError) {
            null
        }

    private fun booleanPaintCall(
        handle: NativeEngineHandle,
        operation: String,
        call: () -> Boolean
    ): NativePaintCallResult =
        try {
            if (call()) {
                NativePaintCallResult.Success
            } else {
                NativePaintCallResult.Failure(
                    NativeEngineBridge.nativeGetLastError(handle.raw)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "$operation failed"
                )
            }
        } catch (_: UnsatisfiedLinkError) {
            NativePaintCallResult.Unavailable
        }
}
