package com.mobileslicer.viewer

internal fun StlMesh.vertexOffset(triangleIndex: Int, cornerIndex: Int): Int {
    require(cornerIndex in 0..2) { "Triangle corner index must be 0, 1, or 2." }
    val vertexIndex = indices?.get(triangleIndex * 3 + cornerIndex) ?: (triangleIndex * 3 + cornerIndex)
    return vertexIndex * 3
}

internal inline fun StlMesh.forEachTriangleVertexOffsets(
    block: (triangleIndex: Int, a: Int, b: Int, c: Int) -> Unit
) {
    for (triangleIndex in 0 until triangleCount) {
        block(
            triangleIndex,
            vertexOffset(triangleIndex, 0),
            vertexOffset(triangleIndex, 1),
            vertexOffset(triangleIndex, 2)
        )
    }
}

internal fun StlMesh.trianglePosition(triangleIndex: Int, cornerIndex: Int, out: FloatArray) {
    val offset = vertexOffset(triangleIndex, cornerIndex)
    out[0] = vertices[offset]
    out[1] = vertices[offset + 1]
    out[2] = vertices[offset + 2]
}

internal fun StlMesh.triangleNormal(triangleIndex: Int, cornerIndex: Int, out: FloatArray) {
    val offset = vertexOffset(triangleIndex, cornerIndex)
    out[0] = normals.getOrElse(offset) { 0f }
    out[1] = normals.getOrElse(offset + 1) { 0f }
    out[2] = normals.getOrElse(offset + 2) { 1f }
}
