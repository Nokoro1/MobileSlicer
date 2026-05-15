package com.mobileslicer.viewer

internal data class ViewerFailure(
    val title: String,
    val detail: String,
    val previewRangeSuggestions: List<PreviewRangeSuggestion> = emptyList()
)

internal data class PreviewRangeSuggestion(
    val label: String,
    val startLayer: Int,
    val endLayer: Int
)

internal data class GcodePreviewRuntimeMetrics(
    val previewKey: Long,
    val layerStart: Long,
    val layerEnd: Long,
    val vertexBudget: Long,
    val nativeLoadMs: Long,
    val nativeSelectedParseMs: Long,
    val nativeLibvgcodeLoadMs: Long,
    val nativeTotalLoadMs: Long,
    val nativeLoadedVertices: Long,
    val nativeCachedVertices: Long,
    val nativeCachedLayers: Long,
    val nativeCacheHit: Long,
    val nativeCacheBuilt: Long,
    val firstFrameMs: Long,
    val lastFrameMs: Long,
    val slowFrameCount: Long,
    val renderedFrameCount: Long
)

internal data class StlModelPlacement(
    val xMm: Float,
    val yMm: Float,
    val zMm: Float
)

internal data class ViewerPickHit(
    val objectId: Long,
    val triangleIndex: Int,
    val hitPoint: StlModelPlacement,
    val normal: StlModelPlacement,
    val distance: Float
)

internal enum class ViewerPaintMode {
    Color,
    Seam,
    Support,
    FuzzySkin
}

internal enum class ViewerPaintBrushShape {
    Circle,
    Sphere,
    Triangle,
    HeightRange,
    Fill,
    GapFill
}

internal enum class ViewerPaintAction {
    Paint,
    Erase,
    Enforce,
    Block
}

internal data class ViewerPaintSession(
    val selectedObjectId: Long,
    val mode: ViewerPaintMode,
    val brushShape: ViewerPaintBrushShape = ViewerPaintBrushShape.Circle,
    val brushRadiusMm: Float = 2.0f,
    val brushHeightMm: Float = 0.2f,
    val action: ViewerPaintAction = ViewerPaintAction.Paint,
    val activeColorInt: Int? = null,
    val overlay: ViewerPaintOverlay = ViewerPaintOverlay.Empty
) {
    val hasOverlay: Boolean get() = overlay.layers.isNotEmpty()
}

internal data class ViewerPaintOverlay(
    val layers: List<ViewerPaintOverlayLayer>
) {
    companion object {
        val Empty = ViewerPaintOverlay(emptyList())
    }
}

internal data class ViewerPaintOverlayLayer(
    val id: String,
    val colorInt: Int,
    val state: Int = 0,
    val vertices: FloatArray,
    val normals: FloatArray,
    val sourceBounds: MeshBounds? = null,
    val modelMatrix: FloatArray? = null,
    val deleteOnly: Boolean = false
)

internal data class ViewerPaintStrokePoint(
    val x: Float,
    val y: Float,
    val hit: ViewerPickHit?,
    val gestureDownUptimeMs: Long = 0L
)

internal data class ViewerPaintRay(
    val values: FloatArray
) {
    init {
        require(values.size == 6) { "Paint ray must contain origin xyz and direction xyz." }
    }
}

internal data class ViewerModelTransform(
    val centerXmm: Float,
    val centerYmm: Float,
    val zOffsetMm: Float = 0f,
    val rotationXDegrees: Float = 0f,
    val rotationYDegrees: Float = 0f,
    val rotationZDegrees: Float = 0f,
    val uniformScale: Float = 1f,
    val orientationMatrix: List<Float>? = null
)

internal data class ViewerPlateObject(
    val id: Long,
    val label: String,
    val mesh: StlMesh,
    val transform: ViewerModelTransform,
    val colorInt: Int? = null,
    val selected: Boolean = false,
    val movable: Boolean = false
)

internal enum class ViewerCutPlaneAxis {
    X,
    Y,
    Z,
    Custom
}

internal enum class ViewerCutConnectorKind {
    None,
    Plug,
    Dowel,
    Snap
}

internal enum class ViewerCutConnectorStyle {
    Prism,
    Frustum
}

internal enum class ViewerCutConnectorShape {
    Triangle,
    Square,
    Hexagon,
    Circle
}

internal data class ViewerCutPlaneSession(
    val selectedObjectId: Long,
    val axis: ViewerCutPlaneAxis,
    val offsetMm: Float,
    val rotationXDegrees: Float = 0f,
    val rotationYDegrees: Float = 0f,
    val keepUpper: Boolean,
    val keepLower: Boolean,
    val connectorKind: ViewerCutConnectorKind = ViewerCutConnectorKind.None,
    val connectorStyle: ViewerCutConnectorStyle = ViewerCutConnectorStyle.Prism,
    val connectorShape: ViewerCutConnectorShape = ViewerCutConnectorShape.Circle,
    val connectorDepthMm: Float = 3f,
    val connectorDepthToleranceMm: Float = 0.1f,
    val connectorSizeMm: Float = 2.5f,
    val connectorSizeToleranceMm: Float = 0f,
    val connectorRotationDegrees: Float = 0f,
    val connectorSnapBulgePercent: Float = 15f,
    val connectorSnapSpacePercent: Float = 30f,
    val connectorsEditing: Boolean = false,
    val connectorPoints: List<ViewerCutConnectorPoint> = emptyList()
)

internal data class ViewerCutConnectorPoint(
    val xMm: Float,
    val yMm: Float,
    val zMm: Float
)

internal data class ViewerAppearance(
    val darkTheme: Boolean,
    val accentColor: Int,
    val worldColor: Int? = null
)
