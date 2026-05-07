package com.mobileslicer.workspace

internal const val PaintBrushMinRadiusMm = 0.4f
internal const val PaintBrushMaxRadiusMm = 8.0f
internal const val PaintBrushDefaultRadiusMm = 1.0f

internal fun effectivePaintBrushRadiusMm(radiusMm: Float): Float =
    radiusMm.coerceIn(PaintBrushMinRadiusMm, PaintBrushMaxRadiusMm)

internal fun paintBrushSliderValue(radiusMm: Float): Float {
    val clampedRadius = effectivePaintBrushRadiusMm(radiusMm)
    return (clampedRadius - PaintBrushMinRadiusMm) / (PaintBrushMaxRadiusMm - PaintBrushMinRadiusMm)
}

internal fun paintBrushRadiusFromSliderValue(value: Float): Float {
    val clampedValue = value.coerceIn(0f, 1f)
    return PaintBrushMinRadiusMm + clampedValue * (PaintBrushMaxRadiusMm - PaintBrushMinRadiusMm)
}

internal fun nativePaintBrushRadiusMm(radiusMm: Float, modelScale: Float = 1f): Float {
    val safeScale = modelScale.takeIf { it.isFinite() && it > 0.0001f } ?: 1f
    return effectivePaintBrushRadiusMm(radiusMm) / safeScale
}

internal fun nativePaintBrushHeightMm(heightMm: Float): Float =
    heightMm.coerceAtLeast(0.01f)
