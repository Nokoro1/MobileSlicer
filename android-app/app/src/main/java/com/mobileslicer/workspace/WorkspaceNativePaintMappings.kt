package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativePaintAction
import com.mobileslicer.nativebridge.NativePaintBrushShape
import com.mobileslicer.nativebridge.NativePaintMode
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode

internal fun ViewerPaintMode.toNativePaintMode(): NativePaintMode = when (this) {
    ViewerPaintMode.Support -> NativePaintMode.Support
    ViewerPaintMode.Seam -> NativePaintMode.Seam
    ViewerPaintMode.Color -> NativePaintMode.Color
    ViewerPaintMode.FuzzySkin -> NativePaintMode.FuzzySkin
}

internal fun ViewerPaintBrushShape.toNativePaintBrushShape(): NativePaintBrushShape = when (this) {
    ViewerPaintBrushShape.Circle -> NativePaintBrushShape.Circle
    ViewerPaintBrushShape.Sphere -> NativePaintBrushShape.Sphere
    ViewerPaintBrushShape.Fill -> NativePaintBrushShape.Fill
    ViewerPaintBrushShape.Triangle -> NativePaintBrushShape.Triangle
    ViewerPaintBrushShape.HeightRange -> NativePaintBrushShape.HeightRange
    ViewerPaintBrushShape.GapFill -> NativePaintBrushShape.GapFill
}

internal fun ViewerPaintBrushShape.toNativePaintBrushShape(mode: ViewerPaintMode): NativePaintBrushShape = when (this) {
    ViewerPaintBrushShape.Circle -> NativePaintBrushShape.Circle
    ViewerPaintBrushShape.Sphere -> NativePaintBrushShape.Sphere
    ViewerPaintBrushShape.Fill -> when (mode) {
        ViewerPaintMode.Support,
        ViewerPaintMode.FuzzySkin -> NativePaintBrushShape.SmartFill
        ViewerPaintMode.Color,
        ViewerPaintMode.Seam -> NativePaintBrushShape.Fill
    }
    ViewerPaintBrushShape.Triangle -> NativePaintBrushShape.Triangle
    ViewerPaintBrushShape.HeightRange -> NativePaintBrushShape.HeightRange
    ViewerPaintBrushShape.GapFill -> NativePaintBrushShape.GapFill
}

internal fun ViewerPaintAction.toNativePaintAction(): NativePaintAction = when (this) {
    ViewerPaintAction.Paint -> NativePaintAction.Paint
    ViewerPaintAction.Erase -> NativePaintAction.Erase
    ViewerPaintAction.Enforce -> NativePaintAction.Enforce
    ViewerPaintAction.Block -> NativePaintAction.Block
}
