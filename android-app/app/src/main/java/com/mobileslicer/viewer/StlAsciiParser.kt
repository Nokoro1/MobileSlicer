package com.mobileslicer.viewer

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.sqrt

internal fun parseAsciiForDisplay(file: File, maxPreviewMeshBytes: Long): PreparedViewerMesh {
    val sourceSummary = parseAsciiBoundsAndCount(file)
    if (exactPreviewMeshByteSize(sourceSummary.triangleCount) > maxPreviewMeshBytes) {
        return parseAsciiIndexedForDisplay(file, sourceSummary, maxPreviewMeshBytes)
    }

    return parseAscii(file).let { mesh ->
        PreparedViewerMesh(
            mesh = mesh,
            sourceTriangleCount = mesh.triangleCount,
            displayTriangleCount = mesh.triangleCount,
            sourceBounds = mesh.bounds,
            renderArrayBytes = mesh.exactPreviewMeshByteSize()
        )
    }
}

private fun parseAsciiIndexedForDisplay(
    file: File,
    sourceSummary: BoundsAndTriangleCount,
    maxPreviewMeshBytes: Long
): PreparedViewerMesh {
    val vertices = FloatCollector(4096)
    val indices = IntArray(sourceSummary.triangleCount * 3)
    val keyToIndex = VertexPositionIndexMap()
    val parsedVector = FloatArray(3)
    val parsedNormal = FloatArray(3)
    var currentFacetNormalX = 0f
    var currentFacetNormalY = 0f
    var currentFacetNormalZ = 1f
    var vertexCount = 0

    InputStreamReader(file.inputStream(), asciiDecoder()).use { reader ->
        reader.buffered().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("facet normal ")) {
                    if (parseVector(line, prefixLength = 13, out = parsedVector)) {
                        normalizeNormal(parsedVector[0], parsedVector[1], parsedVector[2], parsedNormal)
                        currentFacetNormalX = parsedNormal[0]
                        currentFacetNormalY = parsedNormal[1]
                        currentFacetNormalZ = parsedNormal[2]
                    }
                } else if (line.startsWith("vertex ")) {
                    require(parseVector(line, prefixLength = 7, out = parsedVector)) {
                        "ASCII STL vertex line is malformed."
                    }
                    val x = parsedVector[0]
                    val y = parsedVector[1]
                    val z = parsedVector[2]
                    val vertexIndex = keyToIndex.getOrPut(x, y, z) {
                        val nextIndex = vertices.size / 3
                        vertices.append(x, y, z)
                        val byteSize = exactFlatIndexedPreviewMeshByteSize(nextIndex + 1, indices.size)
                        require(byteSize <= maxPreviewMeshBytes) {
                            "STL is too large for exact workspace preview (${formatExactPreviewBytes(byteSize)} required, " +
                                "${formatExactPreviewBytes(maxPreviewMeshBytes)} available)."
                        }
                        nextIndex
                    }
                    indices[vertexCount] = vertexIndex
                    vertexCount++
                }
            }
        }
    }
    require(vertexCount > 0) { "ASCII STL has no vertices." }
    require(vertexCount % 3 == 0) { "ASCII STL vertex count is incomplete." }
    require(vertexCount == indices.size) { "ASCII STL triangle count changed during indexed parse." }
    val mesh = StlMesh(
        vertices = vertices.toFloatArray(),
        normals = FloatArray(0),
        triangleCount = sourceSummary.triangleCount,
        bounds = sourceSummary.bounds,
        indices = indices,
        flatShaded = true
    )
    val renderBytes = mesh.exactPreviewMeshByteSize()
    require(renderBytes <= maxPreviewMeshBytes) {
        "STL is too large for exact workspace preview (${formatExactPreviewBytes(renderBytes)} required, " +
            "${formatExactPreviewBytes(maxPreviewMeshBytes)} available)."
    }
    return PreparedViewerMesh(
        mesh = mesh,
        sourceTriangleCount = sourceSummary.triangleCount,
        displayTriangleCount = sourceSummary.triangleCount,
        sourceBounds = sourceSummary.bounds,
        renderArrayBytes = renderBytes
    )
}

internal fun parseAscii(file: File): StlMesh {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    val estimatedVertexCount = ((file.length() / 48L).coerceIn(256L, STL_MAX_TRIANGLES.toLong() * 3L)).toInt()
    val vertices = FloatCollector(estimatedVertexCount * 3)
    val normals = FloatCollector(estimatedVertexCount * 3)
    var currentFacetNormalX = 0f
    var currentFacetNormalY = 0f
    var currentFacetNormalZ = 1f
    val parsedVector = FloatArray(3)
    var vertexCount = 0

    InputStreamReader(file.inputStream(), asciiDecoder()).use { reader ->
        reader.buffered().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("facet normal ")) {
                    if (parseVector(line, prefixLength = 13, out = parsedVector)) {
                        val length = sqrt(
                            parsedVector[0] * parsedVector[0] +
                                parsedVector[1] * parsedVector[1] +
                                parsedVector[2] * parsedVector[2]
                        )
                        if (length > 0.0001f) {
                            currentFacetNormalX = parsedVector[0] / length
                            currentFacetNormalY = parsedVector[1] / length
                            currentFacetNormalZ = parsedVector[2] / length
                        } else {
                            currentFacetNormalX = 0f
                            currentFacetNormalY = 0f
                            currentFacetNormalZ = 1f
                        }
                    }
                } else if (line.startsWith("vertex ")) {
                    require(parseVector(line, prefixLength = 7, out = parsedVector)) {
                        "ASCII STL vertex line is malformed."
                    }
                    val x = parsedVector[0]
                    val y = parsedVector[1]
                    val z = parsedVector[2]

                    vertices.append(x, y, z)
                    normals.append(currentFacetNormalX, currentFacetNormalY, currentFacetNormalZ)
                    vertexCount++

                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    minZ = minOf(minZ, z)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    maxZ = maxOf(maxZ, z)
                }
            }
        }
    }

    require(vertexCount > 0) { "ASCII STL has no vertices." }
    require(vertexCount % 3 == 0) { "ASCII STL vertex count is incomplete." }

    return StlMesh(
        vertices = vertices.toFloatArray(),
        normals = normals.toFloatArray(),
        triangleCount = vertexCount / 3,
        bounds = MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
    )
}

internal fun parseAsciiBoundsAndCount(file: File): BoundsAndTriangleCount {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    val parsedVector = FloatArray(3)
    var vertexCount = 0

    InputStreamReader(file.inputStream(), asciiDecoder()).use { reader ->
        reader.buffered().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("vertex ")) {
                    require(parseVector(line, prefixLength = 7, out = parsedVector)) {
                        "ASCII STL vertex line is malformed."
                    }
                    val x = parsedVector[0]
                    val y = parsedVector[1]
                    val z = parsedVector[2]
                    vertexCount++

                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    minZ = minOf(minZ, z)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    maxZ = maxOf(maxZ, z)
                }
            }
        }
    }

    require(vertexCount > 0) { "ASCII STL has no vertices." }
    require(vertexCount % 3 == 0) { "ASCII STL vertex count is incomplete." }
    return BoundsAndTriangleCount(
        bounds = MeshBounds(minX, minY, minZ, maxX, maxY, maxZ),
        triangleCount = vertexCount / 3
    )
}

internal data class BoundsAndTriangleCount(
    val bounds: MeshBounds,
    val triangleCount: Int
)

internal fun asciiDecoder(): CharsetDecoder {
    return StandardCharsets.UTF_8.newDecoder().apply {
        onMalformedInput(CodingErrorAction.REPORT)
        onUnmappableCharacter(CodingErrorAction.REPORT)
    }
}

internal fun parseVector(line: String, prefixLength: Int, out: FloatArray): Boolean {
    var index = prefixLength
    var part = 0
    val length = line.length

    while (part < 3) {
        while (index < length && line[index].isWhitespace()) {
            index++
        }
        if (index >= length) return false

        val start = index
        while (index < length && !line[index].isWhitespace()) {
            index++
        }
        out[part] = line.substring(start, index).toFloat()
        part++
    }
    return true
}

internal class FloatCollector(initialCapacity: Int) {
    private var values = FloatArray(initialCapacity.coerceAtLeast(12))
    var size = 0
        private set

    fun append(x: Float, y: Float, z: Float) {
        ensureCapacity(3)
        values[size++] = x
        values[size++] = y
        values[size++] = z
    }

    fun toFloatArray(): FloatArray = values.copyOf(size)

    private fun ensureCapacity(extra: Int) {
        if (size + extra <= values.size) return
        var nextSize = values.size
        while (size + extra > nextSize) {
            nextSize *= 2
        }
        values = values.copyOf(nextSize)
    }
}
