package com.mobileslicer.viewer

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

private const val BINARY_BOUNDS_IN_MEMORY_LIMIT_BYTES = 32L * 1024L * 1024L

internal fun looksLikeBinaryStl(file: File): Boolean {
if (file.length() < 84L) return false
return RandomAccessFile(file, "r").use { raf ->
    raf.seek(80L)
    val triangleCount = readLittleEndianInt(raf)
    if (triangleCount !in 0..STL_MAX_TRIANGLES) {
    return false
    }
    val expectedSize = 84L + triangleCount.toLong() * 50L
    expectedSize == file.length()
}
}
internal fun parseBinary(file: File): StlMesh {
require(file.length() >= 84L) { "Binary STL too small." }

return BufferedInputStream(file.inputStream(), 1 shl 20).use { input ->
    skipExactly(input, 80)
    val triangleCount = readLittleEndianInt(input)
    require(triangleCount in 1..STL_MAX_TRIANGLES) { "Binary STL triangle count is invalid." }
    val expectedSize = 84L + triangleCount.toLong() * 50L
    require(expectedSize == file.length()) { "Binary STL size does not match triangle count." }

    val vertices = FloatArray(triangleCount * 9)
    val normals = FloatArray(triangleCount * 9)
    val triangleBytes = ByteArray(50)
    var vertexIndex = 0

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    repeat(triangleCount) {
    readFully(input, triangleBytes, 0, triangleBytes.size)
    val rawNormalX = readLittleEndianFloat(triangleBytes, 0)
    val rawNormalY = readLittleEndianFloat(triangleBytes, 4)
    val rawNormalZ = readLittleEndianFloat(triangleBytes, 8)
    val normalLength = sqrt(
        rawNormalX * rawNormalX +
        rawNormalY * rawNormalY +
        rawNormalZ * rawNormalZ
    )
    val normalX: Float
    val normalY: Float
    val normalZ: Float
    if (normalLength > 0.0001f) {
        normalX = rawNormalX / normalLength
        normalY = rawNormalY / normalLength
        normalZ = rawNormalZ / normalLength
    } else {
        normalX = 0f
        normalY = 0f
        normalZ = 1f
    }

    repeat(3) {
        val base = 12 + it * 12
        val x = readLittleEndianFloat(triangleBytes, base)
        val y = readLittleEndianFloat(triangleBytes, base + 4)
        val z = readLittleEndianFloat(triangleBytes, base + 8)

        vertices[vertexIndex] = x
        normals[vertexIndex] = normalX
        vertexIndex++
        vertices[vertexIndex] = y
        normals[vertexIndex] = normalY
        vertexIndex++
        vertices[vertexIndex] = z
        normals[vertexIndex] = normalZ
        vertexIndex++

        minX = minOf(minX, x)
        minY = minOf(minY, y)
        minZ = minOf(minZ, z)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
        maxZ = maxOf(maxZ, z)
    }
    }

    StlMesh(
    vertices = vertices,
    normals = normals,
    triangleCount = triangleCount,
    bounds = MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
    )
}
}

internal fun parseBinaryForDisplay(file: File, maxPreviewMeshBytes: Long): PreparedViewerMesh {
    require(file.length() >= 84L) { "Binary STL too small." }

    val triangleCount = RandomAccessFile(file, "r").use { raf ->
        raf.seek(80L)
        val triangleCount = readLittleEndianInt(raf)
        require(triangleCount in 1..STL_MAX_TRIANGLES) { "Binary STL triangle count is invalid." }
        val expectedSize = 84L + triangleCount.toLong() * 50L
        require(expectedSize == file.length()) { "Binary STL size does not match triangle count." }
        triangleCount
    }
    if (exactPreviewMeshByteSize(triangleCount) <= maxPreviewMeshBytes) {
        return parseBinary(file).let { mesh ->
            PreparedViewerMesh(
                mesh = mesh,
                sourceTriangleCount = triangleCount,
                displayTriangleCount = triangleCount,
                sourceBounds = mesh.bounds,
                renderArrayBytes = mesh.exactPreviewMeshByteSize()
            )
        }
    }
    return parseBinaryIndexedForDisplay(file, maxPreviewMeshBytes)
}

private fun parseBinaryIndexedForDisplay(file: File, maxPreviewMeshBytes: Long): PreparedViewerMesh {
    return BufferedInputStream(file.inputStream(), 1 shl 20).use { input ->
        skipExactly(input, 80)
        val triangleCount = readLittleEndianInt(input)
        require(triangleCount in 1..STL_MAX_TRIANGLES) { "Binary STL triangle count is invalid." }
        val expectedSize = 84L + triangleCount.toLong() * 50L
        require(expectedSize == file.length()) { "Binary STL size does not match triangle count." }

        val vertices = FloatCollector(4096)
        val indices = IntArray(triangleCount * 3)
        val keyToIndex = VertexPositionIndexMap()
        val triangleBytes = ByteArray(50)
        val normal = FloatArray(3)
        var indexOffset = 0
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        repeat(triangleCount) {
            readFully(input, triangleBytes, 0, triangleBytes.size)
            normalizeNormal(
                readLittleEndianFloat(triangleBytes, 0),
                readLittleEndianFloat(triangleBytes, 4),
                readLittleEndianFloat(triangleBytes, 8),
                normal
            )
            repeat(3) { vertexInTriangle ->
                val base = 12 + vertexInTriangle * 12
                val x = readLittleEndianFloat(triangleBytes, base)
                val y = readLittleEndianFloat(triangleBytes, base + 4)
                val z = readLittleEndianFloat(triangleBytes, base + 8)
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                minZ = minOf(minZ, z)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                maxZ = maxOf(maxZ, z)

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
                indices[indexOffset++] = vertexIndex
            }
        }
        val mesh = StlMesh(
            vertices = vertices.toFloatArray(),
            normals = FloatArray(0),
            triangleCount = triangleCount,
            bounds = MeshBounds(minX, minY, minZ, maxX, maxY, maxZ),
            indices = indices,
            flatShaded = true
        )
        val renderBytes = mesh.exactPreviewMeshByteSize()
        require(renderBytes <= maxPreviewMeshBytes) {
            "STL is too large for exact workspace preview (${formatExactPreviewBytes(renderBytes)} required, " +
                "${formatExactPreviewBytes(maxPreviewMeshBytes)} available)."
        }
        PreparedViewerMesh(
            mesh = mesh,
            sourceTriangleCount = triangleCount,
            displayTriangleCount = triangleCount,
            sourceBounds = mesh.bounds,
            renderArrayBytes = renderBytes
        )
    }
}

internal fun formatExactPreviewBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 10.0) {
        "${mib.toInt()} MiB"
    } else {
        "${java.lang.String.format(java.util.Locale.US, "%.1f", mib)} MiB"
    }
}

internal fun parseBinaryBounds(file: File): MeshBounds {
require(file.length() >= 84L) { "Binary STL too small." }

if (file.length() <= BINARY_BOUNDS_IN_MEMORY_LIMIT_BYTES) {
    return parseBinaryBoundsInMemory(file)
}

return BufferedInputStream(file.inputStream(), 1 shl 20).use { input ->
    skipExactly(input, 80)
    val triangleCount = readLittleEndianInt(input)
    require(triangleCount in 1..STL_MAX_TRIANGLES) { "Binary STL triangle count is invalid." }
    val expectedSize = 84L + triangleCount.toLong() * 50L
    require(expectedSize == file.length()) { "Binary STL size does not match triangle count." }

    val triangleBytes = ByteArray(50)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    repeat(triangleCount) {
    readFully(input, triangleBytes, 0, triangleBytes.size)
    repeat(3) {
        val base = 12 + it * 12
        val x = readLittleEndianFloat(triangleBytes, base)
        val y = readLittleEndianFloat(triangleBytes, base + 4)
        val z = readLittleEndianFloat(triangleBytes, base + 8)

        minX = minOf(minX, x)
        minY = minOf(minY, y)
        minZ = minOf(minZ, z)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
        maxZ = maxOf(maxZ, z)
    }
    }

    MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
}
}

private fun parseBinaryBoundsInMemory(file: File): MeshBounds {
    val bytes = file.readBytes()
    val triangleCount = readLittleEndianInt(bytes, 80)
    require(triangleCount in 1..STL_MAX_TRIANGLES) { "Binary STL triangle count is invalid." }
    val expectedSize = 84L + triangleCount.toLong() * 50L
    require(expectedSize == bytes.size.toLong()) { "Binary STL size does not match triangle count." }

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY

    var triangleOffset = 84
    repeat(triangleCount) {
        repeat(3) { vertexInTriangle ->
            val base = triangleOffset + 12 + vertexInTriangle * 12
            val x = readLittleEndianFloat(bytes, base)
            val y = readLittleEndianFloat(bytes, base + 4)
            val z = readLittleEndianFloat(bytes, base + 8)

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
        }
        triangleOffset += 50
    }

    return MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
}

internal fun readLittleEndianInt(raf: RandomAccessFile): Int {
    val b0 = raf.read()
    val b1 = raf.read()
    val b2 = raf.read()
    val b3 = raf.read()
    require((b0 or b1 or b2 or b3) >= 0) { "Unexpected end of STL file." }
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

internal fun readLittleEndianFloat(raf: RandomAccessFile): Float {
    return Float.fromBits(readLittleEndianInt(raf))
}

internal fun readLittleEndianInt(input: BufferedInputStream): Int {
    val b0 = input.read()
    val b1 = input.read()
    val b2 = input.read()
    val b3 = input.read()
    require((b0 or b1 or b2 or b3) >= 0) { "Unexpected end of STL file." }
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

internal fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

internal fun readLittleEndianFloat(bytes: ByteArray, offset: Int): Float {
    return Float.fromBits(readLittleEndianInt(bytes, offset))
}

internal fun skipExactly(input: BufferedInputStream, byteCount: Int) {
    var remaining = byteCount.toLong()
    while (remaining > 0L) {
        val skipped = input.skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
            continue
        }
        if (input.read() == -1) {
            throw EOFException("Unexpected end of STL file.")
        }
        remaining--
    }
}

internal fun readFully(input: BufferedInputStream, target: ByteArray, offset: Int, length: Int) {
    var totalRead = 0
    while (totalRead < length) {
        val read = input.read(target, offset + totalRead, length - totalRead)
        if (read < 0) {
            throw EOFException("Unexpected end of STL file.")
        }
        totalRead += read
    }
}
