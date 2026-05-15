package com.mobileslicer.viewer

private const val DEFAULT_MAX_INDEXABLE_SOURCE_VERTICES = 1_500_000
private const val MIN_INDEXED_VERTEX_SAVINGS_PERCENT = 10

internal data class TriangleUploadData(
    val vertices: FloatArray,
    val normals: FloatArray,
    val indices: IntArray?,
    val sourceVertexCount: Int,
    val flatShaded: Boolean = false
) {
    val indexed: Boolean get() = indices != null
    val vertexCount: Int get() = vertices.size / 3
    val indexCount: Int get() = indices?.size ?: 0
    val uploadBytes: Long
        get() = (vertices.size.toLong() + normals.size.toLong()) * Float.SIZE_BYTES +
            (indices?.size?.toLong() ?: 0L) * Int.SIZE_BYTES
}

internal fun prepareTriangleUploadData(
    vertices: FloatArray,
    normals: FloatArray,
    maxIndexableSourceVertices: Int = DEFAULT_MAX_INDEXABLE_SOURCE_VERTICES
): TriangleUploadData {
    require(vertices.size == normals.size) { "Vertex and normal buffers must have matching sizes." }
    require(vertices.size % 3 == 0) { "Triangle vertex buffer is malformed." }
    val sourceVertexCount = vertices.size / 3
    if (sourceVertexCount == 0 || sourceVertexCount > maxIndexableSourceVertices) {
        return TriangleUploadData(vertices = vertices, normals = normals, indices = null, sourceVertexCount = sourceVertexCount)
    }

    val keyToIndex = HashMap<VertexNormalKey, Int>(sourceVertexCount)
    val uniqueVertices = FloatCollector(vertices.size)
    val uniqueNormals = FloatCollector(normals.size)
    val indices = IntArray(sourceVertexCount)

    var sourceOffset = 0
    repeat(sourceVertexCount) { sourceIndex ->
        val key = VertexNormalKey(
            x = vertices[sourceOffset],
            y = vertices[sourceOffset + 1],
            z = vertices[sourceOffset + 2],
            nx = normals[sourceOffset],
            ny = normals[sourceOffset + 1],
            nz = normals[sourceOffset + 2]
        )
        val existingIndex = keyToIndex[key]
        if (existingIndex != null) {
            indices[sourceIndex] = existingIndex
        } else {
            val nextIndex = uniqueVertices.size / 3
            keyToIndex[key] = nextIndex
            indices[sourceIndex] = nextIndex
            uniqueVertices.append(key.x, key.y, key.z)
            uniqueNormals.append(key.nx, key.ny, key.nz)
        }
        sourceOffset += 3
    }

    val uniqueVertexCount = uniqueVertices.size / 3
    val savedVertices = sourceVertexCount - uniqueVertexCount
    val savedPercent = if (sourceVertexCount == 0) 0 else savedVertices * 100 / sourceVertexCount
    if (savedPercent < MIN_INDEXED_VERTEX_SAVINGS_PERCENT) {
        return TriangleUploadData(vertices = vertices, normals = normals, indices = null, sourceVertexCount = sourceVertexCount)
    }
    return TriangleUploadData(
        vertices = uniqueVertices.toFloatArray(),
        normals = uniqueNormals.toFloatArray(),
        indices = indices,
        sourceVertexCount = sourceVertexCount
    )
}

internal fun prepareTriangleUploadData(mesh: StlMesh): TriangleUploadData =
    mesh.indices?.let { indices ->
        TriangleUploadData(
            vertices = mesh.vertices,
            normals = mesh.normals,
            indices = indices,
            sourceVertexCount = mesh.triangleCount * 3,
            flatShaded = mesh.flatShaded
        )
    } ?: prepareTriangleUploadData(vertices = mesh.vertices, normals = mesh.normals)
