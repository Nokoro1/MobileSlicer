package com.mobileslicer.viewer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StlMeshParserBoundsTest {
    @Test
    fun parsesBinaryBoundsWithoutFullMeshRequirement() {
        val file = File.createTempFile("mobileslicer-bounds-", ".stl")
        try {
            file.writeBytes(binaryStl())

            val bounds = StlMeshParser.parseBounds(file)

            assertEquals(-2f, bounds.minX)
            assertEquals(-3f, bounds.minY)
            assertEquals(0f, bounds.minZ)
            assertEquals(4f, bounds.maxX)
            assertEquals(5f, bounds.maxY)
            assertEquals(6f, bounds.maxZ)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parsesAsciiBoundsWithoutBuildingFullMesh() {
        val file = File.createTempFile("mobileslicer-ascii-bounds-", ".stl")
        try {
            file.writeText(
                """
                solid bounds
                  facet normal 0 0 1
                    outer loop
                      vertex -4 -1 0
                      vertex 2 3 1
                      vertex 0 9 2
                    endloop
                  endfacet
                endsolid bounds
                """.trimIndent()
            )

            val bounds = StlMeshParser.parseBounds(file)

            assertEquals(-4f, bounds.minX)
            assertEquals(-1f, bounds.minY)
            assertEquals(0f, bounds.minZ)
            assertEquals(2f, bounds.maxX)
            assertEquals(9f, bounds.maxY)
            assertEquals(2f, bounds.maxZ)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseForDisplayKeepsCompleteBinaryMeshWhenWithinExactBudget() {
        val file = File.createTempFile("mobileslicer-exact-preview-", ".stl")
        try {
            file.writeBytes(binaryStl(triangleCount = 5))

            val prepared = StlMeshParser.parseForDisplay(file)

            assertEquals(5, prepared.sourceTriangleCount)
            assertEquals(5, prepared.displayTriangleCount)
            assertEquals(5, prepared.mesh.triangleCount)
            assertEquals(5L * 9L * 2L * Float.SIZE_BYTES, prepared.renderArrayBytes)
            assertEquals(-10f, prepared.mesh.bounds.minX)
            assertEquals(44f, prepared.mesh.bounds.maxX)
            assertEquals(prepared.sourceBounds, prepared.mesh.bounds)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseForDisplayRejectsBinaryMeshWhenExactPreviewWouldExceedBudget() {
        val file = File.createTempFile("mobileslicer-exact-preview-budget-", ".stl")
        try {
            file.writeBytes(binaryStl(triangleCount = 5))

            try {
                StlMeshParser.parseForDisplay(file, maxPreviewMeshBytes = 2L * 9L * 2L * Float.SIZE_BYTES)
                fail("Expected exact preview budget rejection")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("too large for exact workspace preview"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseForDisplayUsesIndexedBinaryMeshWhenOnlyIndexedExactPreviewFitsBudget() {
        val file = File.createTempFile("mobileslicer-indexed-preview-", ".stl")
        try {
            file.writeBytes(repeatedBinaryStl())

            val prepared = StlMeshParser.parseForDisplay(file, maxPreviewMeshBytes = 100L)

            assertEquals(2, prepared.sourceTriangleCount)
            assertEquals(2, prepared.displayTriangleCount)
            assertEquals(2, prepared.mesh.triangleCount)
            assertEquals(60L, prepared.renderArrayBytes)
            assertNotNull(prepared.mesh.indices)
            assertTrue(prepared.mesh.flatShaded)
            assertArrayEquals(intArrayOf(0, 1, 2, 0, 2, 1), prepared.mesh.indices)
            assertEquals(9, prepared.mesh.vertices.size)
            assertEquals(0, prepared.mesh.normals.size)
        } finally {
            file.delete()
        }
    }

    private fun binaryStl(): ByteArray {
        val buffer = ByteBuffer.allocate(84 + 2 * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(2)
        putTriangle(
            buffer,
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(-2f, -3f, 0f),
            floatArrayOf(1f, 0f, 2f),
            floatArrayOf(0f, 5f, 1f)
        )
        putTriangle(
            buffer,
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(4f, 1f, 6f),
            floatArrayOf(2f, 3f, 1f),
            floatArrayOf(0f, 2f, 4f)
        )
        return buffer.array()
    }

    private fun binaryStl(triangleCount: Int): ByteArray {
        val buffer = ByteBuffer.allocate(84 + triangleCount * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(triangleCount)
        repeat(triangleCount) { index ->
            putTriangle(
                buffer,
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(index * 10f - 10f, 0f, 0f),
                floatArrayOf(index * 10f + 1f, 2f, 3f),
                floatArrayOf(index * 10f + 4f, 5f, 6f)
            )
        }
        return buffer.array()
    }

    private fun repeatedBinaryStl(): ByteArray {
        val buffer = ByteBuffer.allocate(84 + 2 * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(80))
        buffer.putInt(2)
        putTriangle(
            buffer,
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f)
        )
        putTriangle(
            buffer,
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(1f, 0f, 0f)
        )
        return buffer.array()
    }

    private fun putTriangle(
        buffer: ByteBuffer,
        normal: FloatArray,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray
    ) {
        normal.forEach(buffer::putFloat)
        a.forEach(buffer::putFloat)
        b.forEach(buffer::putFloat)
        c.forEach(buffer::putFloat)
        buffer.putShort(0)
    }
}
