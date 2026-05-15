package com.mobileslicer.viewer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TriangleUploadDataTest {
    @Test
    fun indexesExactDuplicateVertexNormalPairsWhenSavingsAreMeaningful() {
        val vertices = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 0f,
            0f, 1f, 0f,
            1f, 0f, 0f
        )
        val normals = FloatArray(vertices.size) { index ->
            if (index % 3 == 2) 1f else 0f
        }

        val prepared = prepareTriangleUploadData(vertices, normals)

        assertTrue(prepared.indexed)
        assertEquals(6, prepared.sourceVertexCount)
        assertEquals(3, prepared.vertexCount)
        assertArrayEquals(intArrayOf(0, 1, 2, 0, 2, 1), prepared.indices)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), prepared.vertices, 0f)
    }

    @Test
    fun doesNotIndexWhenNormalsDifferBecauseFlatFacetsMustStayExact() {
        val vertices = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 0f, 0f
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 1f, 0f
        )

        val prepared = prepareTriangleUploadData(vertices, normals)

        assertFalse(prepared.indexed)
        assertSame(vertices, prepared.vertices)
        assertSame(normals, prepared.normals)
    }

    @Test
    fun skipsIndexingAboveSourceVertexLimit() {
        val vertices = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)

        val prepared = prepareTriangleUploadData(vertices, normals, maxIndexableSourceVertices = 1)

        assertFalse(prepared.indexed)
        assertSame(vertices, prepared.vertices)
        assertSame(normals, prepared.normals)
    }

    @Test
    fun usesExistingMeshIndicesWithoutReindexing() {
        val vertices = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )
        val normals = FloatArray(vertices.size) { index ->
            if (index % 3 == 2) 1f else 0f
        }
        val indices = intArrayOf(0, 1, 2, 0, 2, 1)
        val mesh = StlMesh(
            vertices = vertices,
            normals = normals,
            triangleCount = 2,
            bounds = MeshBounds(0f, 0f, 0f, 1f, 1f, 0f),
            indices = indices
        )

        val prepared = prepareTriangleUploadData(mesh)

        assertTrue(prepared.indexed)
        assertSame(vertices, prepared.vertices)
        assertSame(normals, prepared.normals)
        assertSame(indices, prepared.indices)
        assertEquals(6, prepared.sourceVertexCount)
        assertEquals(3, prepared.vertexCount)
    }
}
