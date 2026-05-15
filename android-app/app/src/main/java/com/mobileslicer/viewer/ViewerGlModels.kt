package com.mobileslicer.viewer

internal data class TriangleUpload(
    val vertexBufferId: Int,
    val normalBufferId: Int,
    val vertexCount: Int,
    val indexBufferId: Int = 0,
    val indexCount: Int = 0,
    val sourceVertexCount: Int = vertexCount,
    val uploadBytes: Long = (vertexCount.toLong() * 3L * 2L * Float.SIZE_BYTES),
    val uploadMs: Long = 0L,
    val glError: Int = 0,
    val flatShaded: Boolean = false
) {
    val indexed: Boolean get() = indexBufferId != 0 && indexCount > 0
}

internal data class TriangleProgramHandles(
    val positionHandle: Int,
    val normalHandle: Int,
    val matrixHandle: Int,
    val modelMatrixHandle: Int,
    val colorHandle: Int,
    val lightHandle: Int,
    val flatShadingHandle: Int,
    val fullBrightHandle: Int
)

internal data class TriangleProgram(
    val programId: Int,
    val handles: TriangleProgramHandles
)

internal data class TextureUpload(
    val vertexBufferId: Int,
    val uvBufferId: Int,
    val textureId: Int,
    val vertexCount: Int
)

internal data class TextureProgramHandles(
    val positionHandle: Int,
    val uvHandle: Int,
    val matrixHandle: Int,
    val textureHandle: Int,
    val alphaHandle: Int
)

internal data class TextureProgram(
    val programId: Int,
    val handles: TextureProgramHandles
)

internal data class ModelObjectUpload(
    val id: Long,
    val mesh: StlMesh,
    val upload: TriangleUpload,
    val modelMatrix: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val radius: Float,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float,
    val colorInt: Int?,
    val selected: Boolean
)

internal data class PaintOverlayUpload(
    val id: String,
    val upload: TriangleUpload,
    val color: FloatArray,
    val modelMatrix: FloatArray?,
    val layerIds: Set<String>,
    val sourceKeys: Set<String>
)

internal data class PendingPaintOverlayUpload(
    val layer: ViewerPaintOverlayLayer,
    var vertexOffset: Int = 0,
    val live: Boolean = false,
    val replacement: Boolean = false
)
