package com.mobileslicer.workspace

import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode

internal data class WorkspacePaintToolState(
    val brushShape: ViewerPaintBrushShape,
    val action: ViewerPaintAction
)

internal fun normalizedPaintToolState(
    mode: ViewerPaintMode,
    currentBrushShape: ViewerPaintBrushShape,
    currentAction: ViewerPaintAction
): WorkspacePaintToolState {
    val brushShapes = mode.availableBrushShapes()
    val actions = mode.availableActions()
    return WorkspacePaintToolState(
        brushShape = currentBrushShape.takeIf { it in brushShapes } ?: brushShapes.first(),
        action = currentAction.takeIf { it in actions } ?: actions.first()
    )
}

internal fun defaultPaintToolStateForMode(mode: ViewerPaintMode): WorkspacePaintToolState =
    WorkspacePaintToolState(
        brushShape = ViewerPaintBrushShape.Circle,
        action = when (mode) {
            ViewerPaintMode.Support,
            ViewerPaintMode.Seam -> ViewerPaintAction.Enforce
            ViewerPaintMode.Color,
            ViewerPaintMode.FuzzySkin -> ViewerPaintAction.Paint
        }
    )

