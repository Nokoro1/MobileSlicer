package com.mobileslicer.viewer

import java.util.IdentityHashMap
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceObjectUploadManagerTest {
    @Test
    fun reusableWorkspaceObjectUpload_reusesSameObjectMeshUpload() {
        val mesh = testMesh()
        val upload = testUpload(1)
        val plateObject = testPlateObject(id = 10L, mesh = mesh)
        val previousUploads = mapOf(10L to testModelObjectUpload(id = 10L, mesh = mesh, upload = upload))

        val reuse = reusableWorkspaceObjectUpload(
            plateObject = plateObject,
            previousUploadsById = previousUploads,
            nextUploadsByMesh = IdentityHashMap(),
            reusableUploadsByMesh = IdentityHashMap()
        )

        assertSame(upload, reuse.upload)
        assertEquals(WorkspaceObjectUploadReuseSource.SameObject, reuse.source)
    }

    @Test
    fun reusableWorkspaceObjectUpload_reusesPreviousMeshForCopiedObject() {
        val mesh = testMesh()
        val upload = testUpload(2)
        val plateObject = testPlateObject(id = 20L, mesh = mesh)
        val reusableUploadsByMesh = IdentityHashMap<StlMesh, TriangleUpload>().apply {
            this[mesh] = upload
        }

        val reuse = reusableWorkspaceObjectUpload(
            plateObject = plateObject,
            previousUploadsById = emptyMap(),
            nextUploadsByMesh = IdentityHashMap(),
            reusableUploadsByMesh = reusableUploadsByMesh
        )

        assertSame(upload, reuse.upload)
        assertEquals(WorkspaceObjectUploadReuseSource.PreviousMesh, reuse.source)
    }

    @Test
    fun reusableWorkspaceObjectUpload_reusesCurrentBatchMeshUpload() {
        val mesh = testMesh()
        val upload = testUpload(3)
        val plateObject = testPlateObject(id = 30L, mesh = mesh)
        val nextUploadsByMesh = IdentityHashMap<StlMesh, TriangleUpload>().apply {
            this[mesh] = upload
        }

        val reuse = reusableWorkspaceObjectUpload(
            plateObject = plateObject,
            previousUploadsById = emptyMap(),
            nextUploadsByMesh = nextUploadsByMesh,
            reusableUploadsByMesh = IdentityHashMap()
        )

        assertSame(upload, reuse.upload)
        assertEquals(WorkspaceObjectUploadReuseSource.CurrentMesh, reuse.source)
    }

    @Test
    fun reusableWorkspaceObjectUpload_returnsNoneForNewMesh() {
        val plateObject = testPlateObject(id = 40L, mesh = testMesh())

        val reuse = reusableWorkspaceObjectUpload(
            plateObject = plateObject,
            previousUploadsById = emptyMap(),
            nextUploadsByMesh = IdentityHashMap(),
            reusableUploadsByMesh = IdentityHashMap()
        )

        assertNull(reuse.upload)
        assertEquals(WorkspaceObjectUploadReuseSource.None, reuse.source)
    }

    private fun testPlateObject(id: Long, mesh: StlMesh): ViewerPlateObject =
        ViewerPlateObject(
            id = id,
            label = "Object $id",
            mesh = mesh,
            transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
        )

    private fun testModelObjectUpload(id: Long, mesh: StlMesh, upload: TriangleUpload): ModelObjectUpload =
        ModelObjectUpload(
            id = id,
            mesh = mesh,
            upload = upload,
            modelMatrix = FloatArray(16),
            centerX = 0f,
            centerY = 0f,
            centerZ = 0f,
            radius = 1f,
            sizeX = 1f,
            sizeY = 1f,
            sizeZ = 1f,
            colorInt = null,
            selected = false
        )

    private fun testUpload(id: Int): TriangleUpload =
        TriangleUpload(vertexBufferId = id, normalBufferId = id + 100, vertexCount = 3)

    private fun testMesh(): StlMesh =
        StlMesh(
            vertices = floatArrayOf(
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            ),
            normals = floatArrayOf(
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f
            ),
            triangleCount = 1,
            bounds = MeshBounds(
                minX = 0f,
                minY = 0f,
                minZ = 0f,
                maxX = 1f,
                maxY = 1f,
                maxZ = 0f
            )
        )
}
