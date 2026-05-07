package com.mobileslicer.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.mobileslicer.activePlateFilamentSlots
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerPlateObject

internal data class PrimeTowerViewerState(
    val placement: PrimeTowerPlacement?,
    val viewerObject: ViewerPlateObject?,
    val canDrag: Boolean
)

@Composable
internal fun rememberPrimeTowerViewerState(
    configuration: ActiveSlicerConfiguration,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    printerBed: PrinterBedSpec,
    override: PrimeTowerPlacementOverride?,
    selected: Boolean,
    moveEnabled: Boolean,
    visible: Boolean
): PrimeTowerViewerState {
    val placement = primeTowerPlacementForWorkspace(
        configuration = configuration,
        plateObjects = plateObjects,
        filamentSlots = filamentSlots,
        printerBed = printerBed,
        override = override
    )
    val canDrag = visible && moveEnabled && selected && placement != null
    val baseColorInt = activePlateFilamentSlots(filamentSlots, plateObjects)
        .firstOrNull()
        ?.colorHex
        ?.let { slotColor(it).toArgb() }
    val displayColorInt = primeTowerDisplayColor(
        baseColorInt = baseColorInt,
        selected = selected,
        canDrag = canDrag
    )
    val previewMesh = remember(
        placement?.widthMm,
        placement?.depthMm,
        placement?.heightMm
    ) {
        placement?.previewMesh()
    }
    val viewerObject = remember(
        placement,
        displayColorInt,
        selected,
        canDrag,
        previewMesh,
        visible
    ) {
        if (!visible) {
            null
        } else {
            placement?.toViewerPlateObject(
                selected = selected,
                movable = canDrag,
                colorInt = displayColorInt,
                mesh = previewMesh
            )
        }
    }
    return PrimeTowerViewerState(
        placement = placement,
        viewerObject = viewerObject,
        canDrag = canDrag
    )
}

private fun primeTowerDisplayColor(
    baseColorInt: Int?,
    selected: Boolean,
    canDrag: Boolean
): Int? {
    if (!selected || !canDrag) return baseColorInt
    val base = androidx.compose.ui.graphics.Color(baseColorInt ?: 0x99D88C3A.toInt())
    val moveTint = androidx.compose.ui.graphics.Color(0xFF7DB7FF)
    return lerp(base, moveTint, 0.55f).toArgb()
}
