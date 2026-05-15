package com.mobileslicer

import com.mobileslicer.storage.PreparedViewerMeshCache
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PreparedViewerMesh
import com.mobileslicer.viewer.StlMesh
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedViewerMeshCacheTest {
    @Test
    fun returnsCachedMeshForUnchangedFileIdentity() {
        val file = File.createTempFile("mobileslicer-mesh-cache-", ".stl")
        try {
            file.writeText("solid cache\nendsolid cache\n")
            val mesh = testMesh()
            val cache = PreparedViewerMeshCache(maxEntries = 2)

            cache.put(file, prepared(mesh))

            assertSame(mesh, cache.get(file)?.mesh)
        } finally {
            file.delete()
        }
    }

    @Test
    fun missesWhenFileSizeChanges() {
        val file = File.createTempFile("mobileslicer-mesh-cache-", ".stl")
        try {
            file.writeText("first")
            val mesh = testMesh()
            val cache = PreparedViewerMeshCache(maxEntries = 2)
            cache.put(file, prepared(mesh))

            file.appendText(" changed")

            assertNull(cache.get(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun evictsLeastRecentlyUsedEntry() {
        val first = File.createTempFile("mobileslicer-mesh-cache-first-", ".stl")
        val second = File.createTempFile("mobileslicer-mesh-cache-second-", ".stl")
        val third = File.createTempFile("mobileslicer-mesh-cache-third-", ".stl")
        try {
            first.writeText("first")
            second.writeText("second")
            third.writeText("third")
            val firstMesh = testMesh()
            val secondMesh = testMesh()
            val thirdMesh = testMesh()
            val cache = PreparedViewerMeshCache(maxEntries = 2)

            cache.put(first, prepared(firstMesh))
            cache.put(second, prepared(secondMesh))
            assertSame(firstMesh, cache.get(first)?.mesh)
            cache.put(third, prepared(thirdMesh))

            assertSame(firstMesh, cache.get(first)?.mesh)
            assertNull(cache.get(second))
            assertSame(thirdMesh, cache.get(third)?.mesh)
        } finally {
            first.delete()
            second.delete()
            third.delete()
        }
    }

    @Test
    fun evictsLeastRecentlyUsedEntriesToStayWithinByteBudget() {
        val first = File.createTempFile("mobileslicer-mesh-cache-first-", ".stl")
        val second = File.createTempFile("mobileslicer-mesh-cache-second-", ".stl")
        val third = File.createTempFile("mobileslicer-mesh-cache-third-", ".stl")
        try {
            first.writeText("first")
            second.writeText("second")
            third.writeText("third")
            val firstMesh = testMesh()
            val secondMesh = testMesh()
            val thirdMesh = testMesh()
            val oneMeshBytes = 18L * Float.SIZE_BYTES
            val cache = PreparedViewerMeshCache(maxEntries = 4, maxBytes = oneMeshBytes * 2)

            cache.put(first, prepared(firstMesh))
            cache.put(second, prepared(secondMesh))
            assertSame(firstMesh, cache.get(first)?.mesh)
            cache.put(third, prepared(thirdMesh))

            assertSame(firstMesh, cache.get(first)?.mesh)
            assertNull(cache.get(second))
            assertSame(thirdMesh, cache.get(third)?.mesh)
            assertEquals(oneMeshBytes * 2, cache.retainedBytes)
        } finally {
            first.delete()
            second.delete()
            third.delete()
        }
    }

    @Test
    fun skipsMeshLargerThanByteBudget() {
        val file = File.createTempFile("mobileslicer-mesh-cache-large-", ".stl")
        try {
            file.writeText("large")
            val cache = PreparedViewerMeshCache(maxEntries = 4, maxBytes = 1)

            cache.put(file, prepared(testMesh()))

            assertNull(cache.get(file))
            assertEquals(0L, cache.retainedBytes)
        } finally {
            file.delete()
        }
    }

    private fun testMesh(): StlMesh =
        StlMesh(
            vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            triangleCount = 1,
            bounds = MeshBounds(0f, 0f, 0f, 1f, 1f, 0f)
        )

    private fun prepared(mesh: StlMesh): PreparedViewerMesh =
        PreparedViewerMesh(
            mesh = mesh,
            sourceTriangleCount = mesh.triangleCount,
            displayTriangleCount = mesh.triangleCount,
            sourceBounds = mesh.bounds,
            renderArrayBytes = (mesh.vertices.size.toLong() + mesh.normals.size.toLong()) * Float.SIZE_BYTES
        )
}
