package com.mobileslicer.workspace

import androidx.compose.ui.graphics.Color
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import kotlin.math.round

internal enum class PreviewLayerMode {
    Single,
    Range
}

internal enum class TransformToolTab {
    Move,
    Rotate,
    Scale,
    Split,
    Cut,
    Paint,
    AutoOrient,
    AutoArrange
}

internal enum class WorkspaceCutMode {
    Plane,
    Line,
    Contour,
    Groove
}

internal enum class WorkspaceCutAxis {
    X,
    Y,
    Z,
    Custom
}

internal enum class WorkspaceCutConnectorKind {
    None,
    Plug,
    Dowel,
    Snap
}

internal enum class WorkspaceCutConnectorStyle {
    Prism,
    Frustum
}

internal enum class WorkspaceCutConnectorShape {
    Triangle,
    Square,
    Hexagon,
    Circle
}

internal data class WorkspaceCutRequest(
    val axis: WorkspaceCutAxis = WorkspaceCutAxis.Z,
    val heightMm: Float,
    val rotationXDegrees: Float = 0f,
    val rotationYDegrees: Float = 0f,
    val mode: WorkspaceCutMode = WorkspaceCutMode.Plane,
    val keepUpper: Boolean = true,
    val keepLower: Boolean = true,
    val keepAsParts: Boolean = false,
    val flipUpper: Boolean = false,
    val flipLower: Boolean = false,
    val placeOnCutUpper: Boolean = true,
    val placeOnCutLower: Boolean = false,
    val connectorKind: WorkspaceCutConnectorKind = WorkspaceCutConnectorKind.None,
    val connectorStyle: WorkspaceCutConnectorStyle = WorkspaceCutConnectorStyle.Prism,
    val connectorShape: WorkspaceCutConnectorShape = WorkspaceCutConnectorShape.Circle,
    val connectorDepthMm: Float = 3f,
    val connectorDepthToleranceMm: Float = 0.1f,
    val connectorSizeMm: Float = 2.5f,
    val connectorSizeToleranceMm: Float = 0f,
    val connectorRotationDegrees: Float = 0f,
    val connectorSnapBulgePercent: Float = 15f,
    val connectorSnapSpacePercent: Float = 30f,
    val connectorsEditing: Boolean = false,
    val connectorPositions: List<WorkspaceCutConnectorPoint> = emptyList(),
    val grooveDepthMm: Float = 2f,
    val grooveWidthMm: Float = 8f,
    val grooveFlapAngleDegrees: Float = 60f,
    val grooveAngleDegrees: Float = 0f,
    val grooveDepthToleranceMm: Float = 0.1f,
    val grooveWidthToleranceMm: Float = 0.1f
)

internal data class WorkspaceCutConnectorPoint(
    val xMm: Float,
    val yMm: Float,
    val zMm: Float
)

internal data class WorkspaceCutSession(
    val selectedObjectId: Long,
    val axis: WorkspaceCutAxis,
    val offsetMm: Float,
    val rotationXDegrees: Float,
    val rotationYDegrees: Float,
    val bounds: MeshBounds,
    val keepUpper: Boolean,
    val keepLower: Boolean,
    val keepAsParts: Boolean,
    val mode: WorkspaceCutMode,
    val connectorKind: WorkspaceCutConnectorKind,
    val connectorStyle: WorkspaceCutConnectorStyle = WorkspaceCutConnectorStyle.Prism,
    val connectorShape: WorkspaceCutConnectorShape = WorkspaceCutConnectorShape.Circle,
    val connectorDepthMm: Float = 3f,
    val connectorDepthToleranceMm: Float = 0.1f,
    val connectorSizeMm: Float = 2.5f,
    val connectorSizeToleranceMm: Float = 0f,
    val connectorRotationDegrees: Float = 0f,
    val connectorSnapBulgePercent: Float = 15f,
    val connectorSnapSpacePercent: Float = 30f,
    val connectorsEditing: Boolean = false,
    val connectorPositions: List<WorkspaceCutConnectorPoint> = emptyList()
) {
    val range: ClosedFloatingPointRange<Float>
        get() = when (axis) {
            WorkspaceCutAxis.X -> bounds.minX..bounds.maxX
            WorkspaceCutAxis.Y -> bounds.minY..bounds.maxY
            WorkspaceCutAxis.Z,
            WorkspaceCutAxis.Custom -> bounds.minZ..bounds.maxZ
        }
}

internal enum class TransformNumericField {
    MoveX,
    MoveY,
    RotateX,
    RotateY,
    RotateZ,
    Scale
}

internal data class PreviewLayerSelection(
    val mode: PreviewLayerMode,
    val singleLayer: Int,
    val rangeStartLayer: Int,
    val rangeEndLayer: Int
)

internal sealed interface WorkspaceViewerState {
    data object Empty : WorkspaceViewerState
    data object Preparing : WorkspaceViewerState
    data object Unsupported : WorkspaceViewerState
    data class Loaded(val mesh: StlMesh) : WorkspaceViewerState
    data class Error(val title: String, val message: String) : WorkspaceViewerState
}

internal fun defaultViewerModelTransform(printerBed: PrinterBedSpec): ViewerModelTransform =
    ViewerModelTransform(
        centerXmm = printerBed.widthMm * 0.5f,
        centerYmm = printerBed.depthMm * 0.5f,
        rotationXDegrees = 0f,
        rotationYDegrees = 0f,
        rotationZDegrees = 0f,
        uniformScale = 1f
    )

internal fun normalizeDegrees(value: Float): Float {
    var normalized = value
    while (normalized > 180f) normalized -= 360f
    while (normalized < -180f) normalized += 360f
    return normalized
}

internal fun nearestRightAngle(value: Float): Float =
    normalizeDegrees(round(value / 90f) * 90f)

internal fun selectedWorkspaceWorldColor(worldViewColor: WorldViewColorOption): Color =
    when (worldViewColor) {
        WorldViewColorOption.White -> Color(0xFFF3F7FC)
        WorldViewColorOption.Mist -> Color(0xFFDCE5EE)
        WorldViewColorOption.Slate -> Color(0xFF8E9AA6)
        WorldViewColorOption.Graphite -> Color(0xFF3F4852)
        WorldViewColorOption.Deep -> Color(0xFF071426)
        WorldViewColorOption.Navy -> Color(0xFF10233A)
        WorldViewColorOption.Charcoal -> Color(0xFF171B20)
        WorldViewColorOption.Black -> Color(0xFF020407)
    }
