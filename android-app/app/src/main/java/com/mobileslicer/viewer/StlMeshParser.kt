package com.mobileslicer.viewer

import java.io.File
import kotlin.math.max

internal data class MeshBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
    val radius: Float
        get() = max(
            max(sizeX, sizeY),
            sizeZ
        ) * 0.5f
}

internal data class StlMesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val triangleCount: Int,
    val bounds: MeshBounds,
    val indices: IntArray? = null,
    val flatShaded: Boolean = false
)

internal data class PreparedViewerMesh(
    val mesh: StlMesh,
    val sourceTriangleCount: Int,
    val displayTriangleCount: Int,
    val sourceBounds: MeshBounds,
    val renderArrayBytes: Long
)

internal const val STL_MAX_TRIANGLES: Int = Int.MAX_VALUE / 9
internal const val DEFAULT_MAX_EXACT_PREVIEW_MESH_BYTES: Long = 96L * 1024L * 1024L

internal object StlMeshParser {
    fun parse(file: File): StlMesh {
        require(file.exists() && file.length() > 0L) { "STL file is empty." }
        return if (looksLikeBinaryStl(file)) {
            parseBinary(file)
        } else {
            parseAscii(file)
        }
    }

    fun parseForDisplay(
        file: File,
        maxPreviewMeshBytes: Long = DEFAULT_MAX_EXACT_PREVIEW_MESH_BYTES
    ): PreparedViewerMesh {
        // Workspace preview must be geometrically honest: exact STL triangles or a clear error.
        require(maxPreviewMeshBytes > 0L) { "Exact preview mesh byte budget must be positive." }
        require(file.exists() && file.length() > 0L) { "STL file is empty." }
        return if (looksLikeBinaryStl(file)) {
            parseBinaryForDisplay(file, maxPreviewMeshBytes)
        } else {
            parseAsciiForDisplay(file, maxPreviewMeshBytes)
        }
    }

    fun parseBounds(file: File): MeshBounds {
        require(file.exists() && file.length() > 0L) { "STL file is empty." }
        return if (looksLikeBinaryStl(file)) {
            parseBinaryBounds(file)
        } else {
            parseAsciiBoundsAndCount(file).bounds
        }
    }
}
