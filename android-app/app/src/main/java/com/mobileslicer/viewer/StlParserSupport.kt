package com.mobileslicer.viewer

import java.util.Locale
import kotlin.math.sqrt

internal fun normalizeNormal(x: Float, y: Float, z: Float, out: FloatArray) {
    val normalLength = sqrt(x * x + y * y + z * z)
    if (normalLength > 0.0001f) {
        out[0] = x / normalLength
        out[1] = y / normalLength
        out[2] = z / normalLength
    } else {
        out[0] = 0f
        out[1] = 0f
        out[2] = 1f
    }
}

internal fun exactPreviewMeshByteSize(triangleCount: Int): Long =
    triangleCount.toLong() * 9L * 2L * Float.SIZE_BYTES

internal fun exactIndexedPreviewMeshByteSize(uniqueVertexCount: Int, indexCount: Int): Long =
    uniqueVertexCount.toLong() * 3L * 2L * Float.SIZE_BYTES + indexCount.toLong() * Int.SIZE_BYTES

internal fun exactFlatIndexedPreviewMeshByteSize(uniqueVertexCount: Int, indexCount: Int): Long =
    uniqueVertexCount.toLong() * 3L * Float.SIZE_BYTES + indexCount.toLong() * Int.SIZE_BYTES

internal fun StlMesh.exactPreviewMeshByteSize(): Long =
    indices?.let {
        if (flatShaded) {
            exactFlatIndexedPreviewMeshByteSize(vertices.size / 3, it.size)
        } else {
            exactIndexedPreviewMeshByteSize(vertices.size / 3, it.size)
        }
    }
        ?: exactPreviewMeshByteSize(triangleCount)

internal fun requireExactPreviewMeshWithinBudget(triangleCount: Int, maxPreviewMeshBytes: Long) {
    val requiredBytes = exactPreviewMeshByteSize(triangleCount)
    require(requiredBytes <= maxPreviewMeshBytes) {
        "STL is too large for exact workspace preview (${formatPreviewBytes(requiredBytes)} required, " +
            "${formatPreviewBytes(maxPreviewMeshBytes)} available)."
    }
}

private fun formatPreviewBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 10.0) {
        "${mib.toInt()} MiB"
    } else {
        "${String.format(Locale.US, "%.1f", mib)} MiB"
    }
}

internal data class VertexNormalKey(
    val x: Float,
    val y: Float,
    val z: Float,
    val nx: Float,
    val ny: Float,
    val nz: Float
)

internal data class VertexPositionKey(
    val x: Float,
    val y: Float,
    val z: Float
)
