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

internal data class ViewerModelTransform(
    val centerXmm: Float,
    val centerYmm: Float,
    val rotationXDegrees: Float = 0f,
    val rotationYDegrees: Float = 0f,
    val rotationZDegrees: Float = 0f,
    val uniformScale: Float = 1f
)

internal data class ViewerPlateObject(
    val id: Long,
    val label: String,
    val mesh: StlMesh,
    val transform: ViewerModelTransform,
    val colorInt: Int? = null,
    val selected: Boolean = false
)

internal data class ViewerAppearance(
    val darkTheme: Boolean,
    val accentColor: Int,
    val worldColor: Int? = null
)
