package com.mobileslicer.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerUpdateDecisionsTest {
    @Test
    fun plateObjectTransformOnlyChangeDoesNotRequireGeometryUpload() {
        val mesh = testMesh()
        val previous = listOf(
            testPlateObject(
                id = 1L,
                mesh = mesh,
                transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
            )
        )
        val next = listOf(
            previous.single().copy(
                transform = ViewerModelTransform(
                    centerXmm = 110f,
                    centerYmm = 100f,
                    rotationZDegrees = 15f
                )
            )
        )

        val decision = ViewerUpdateDecisions.plateObjectUpdateDecision(previous, next)

        assertFalse(decision.geometryChanged)
        assertTrue(decision.transformChanged)
        assertFalse(decision.selectionChanged)
        assertTrue(ViewerUpdateDecisions.samePlateObjectUploadSet(previous, next))
    }

    @Test
    fun plateObjectSelectionOnlyChangeDoesNotRequireGeometryUpload() {
        val mesh = testMesh()
        val previous = listOf(testPlateObject(id = 1L, mesh = mesh, selected = false))
        val next = listOf(previous.single().copy(selected = true))

        val decision = ViewerUpdateDecisions.plateObjectUpdateDecision(previous, next)

        assertFalse(decision.geometryChanged)
        assertFalse(decision.transformChanged)
        assertTrue(decision.selectionChanged)
        assertTrue(ViewerUpdateDecisions.samePlateObjectUploadSet(previous, next))
    }

    @Test
    fun plateObjectMeshIdentityChangeRequiresGeometryUpload() {
        val previous = listOf(testPlateObject(id = 1L, mesh = testMesh()))
        val next = listOf(testPlateObject(id = 1L, mesh = testMesh()))

        val decision = ViewerUpdateDecisions.plateObjectUpdateDecision(previous, next)

        assertTrue(decision.geometryChanged)
        assertFalse(decision.transformChanged)
        assertFalse(decision.selectionChanged)
        assertFalse(ViewerUpdateDecisions.samePlateObjectUploadSet(previous, next))
    }

    @Test
    fun plateObjectRenderSignatureTracksSelectionColorAndGeometry() {
        val mesh = testMesh()
        val previous = listOf(testPlateObject(id = 1L, mesh = mesh, selected = false))
        val selected = listOf(previous.single().copy(selected = true))
        val recolored = listOf(previous.single().copy(colorInt = 0xFF112233.toInt()))
        val newGeometry = listOf(previous.single().copy(mesh = testMesh()))

        val previousSignature = ViewerUpdateDecisions.plateObjectsSignature(previous)

        assertTrue(previousSignature != ViewerUpdateDecisions.plateObjectsSignature(selected))
        assertTrue(previousSignature != ViewerUpdateDecisions.plateObjectsSignature(recolored))
        assertTrue(previousSignature != ViewerUpdateDecisions.plateObjectsSignature(newGeometry))
    }

    @Test
    fun plateObjectOrderChangeRequiresGeometryUpload() {
        val meshA = testMesh()
        val meshB = testMesh()
        val previous = listOf(
            testPlateObject(id = 1L, mesh = meshA),
            testPlateObject(id = 2L, mesh = meshB)
        )
        val next = listOf(
            testPlateObject(id = 2L, mesh = meshB),
            testPlateObject(id = 1L, mesh = meshA)
        )

        val decision = ViewerUpdateDecisions.plateObjectUpdateDecision(previous, next)

        assertTrue(decision.geometryChanged)
        assertTrue(decision.transformChanged)
        assertTrue(decision.selectionChanged)
        assertFalse(ViewerUpdateDecisions.samePlateObjectUploadSet(previous, next))
    }

    @Test
    fun addingOrRemovingPlateObjectsRequiresGeometryUpload() {
        val mesh = testMesh()
        val first = testPlateObject(id = 1L, mesh = mesh)
        val second = testPlateObject(id = 2L, mesh = mesh)

        val addDecision = ViewerUpdateDecisions.plateObjectUpdateDecision(listOf(first), listOf(first, second))
        val removeDecision = ViewerUpdateDecisions.plateObjectUpdateDecision(listOf(first, second), listOf(second))

        assertTrue(addDecision.geometryChanged)
        assertTrue(addDecision.transformChanged)
        assertTrue(addDecision.selectionChanged)
        assertFalse(ViewerUpdateDecisions.samePlateObjectUploadSet(listOf(first), listOf(first, second)))
        assertTrue(removeDecision.geometryChanged)
        assertTrue(removeDecision.transformChanged)
        assertTrue(removeDecision.selectionChanged)
        assertFalse(ViewerUpdateDecisions.samePlateObjectUploadSet(listOf(first, second), listOf(second)))
    }

    @Test
    fun gcodePreviewSourceNormalizationRequiresValidEngineAndPreviewKey() {
        assertEquals(GcodePreviewSource(engineHandle = 4L, previewKey = 9L), ViewerUpdateDecisions.normalizeGcodePreviewSource(4L, 9L))
        assertEquals(GcodePreviewSource(engineHandle = 0L, previewKey = 0L), ViewerUpdateDecisions.normalizeGcodePreviewSource(0L, 9L))
        assertEquals(GcodePreviewSource(engineHandle = 0L, previewKey = 0L), ViewerUpdateDecisions.normalizeGcodePreviewSource(4L, 0L))
        assertEquals(GcodePreviewSource(engineHandle = 0L, previewKey = 0L), ViewerUpdateDecisions.normalizeGcodePreviewSource(4L, -1L))
    }

    @Test
    fun gcodeLayerRangeChangeStreamsWithoutReloadingPreview() {
        val decision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
            previousMinLayer = 0L,
            previousMaxLayer = 50L,
            previousReloadToken = 1L,
            nextMinLayer = 10L,
            nextMaxLayer = 30L,
            nextReloadToken = 1L,
            activeEngineHandle = 100L,
            activePreviewKey = 200L
        )

        assertTrue(decision.rangeChanged)
        assertFalse(decision.reloadChanged)
        assertFalse(decision.shouldReloadPreview)
    }

    @Test
    fun gcodeLayerRangeCommitWithoutReloadTokenDoesNotReloadPreview() {
        val decision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
            previousMinLayer = 10L,
            previousMaxLayer = 30L,
            previousReloadToken = 1L,
            nextMinLayer = 10L,
            nextMaxLayer = 30L,
            nextReloadToken = 1L,
            activeEngineHandle = 100L,
            activePreviewKey = 200L
        )

        assertFalse(decision.rangeChanged)
        assertFalse(decision.reloadChanged)
        assertFalse(decision.shouldReloadPreview)
    }

    @Test
    fun gcodeReloadTokenChangeReloadsOnlyWhenPreviewSourceIsActive() {
        val activeDecision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
            previousMinLayer = 0L,
            previousMaxLayer = 50L,
            previousReloadToken = 1L,
            nextMinLayer = 0L,
            nextMaxLayer = 50L,
            nextReloadToken = 2L,
            activeEngineHandle = 100L,
            activePreviewKey = 200L
        )
        val inactiveDecision = ViewerUpdateDecisions.gcodeLayerRangeUpdateDecision(
            previousMinLayer = 0L,
            previousMaxLayer = 50L,
            previousReloadToken = 1L,
            nextMinLayer = 0L,
            nextMaxLayer = 50L,
            nextReloadToken = 2L,
            activeEngineHandle = 0L,
            activePreviewKey = 0L
        )

        assertFalse(activeDecision.rangeChanged)
        assertTrue(activeDecision.reloadChanged)
        assertTrue(activeDecision.shouldReloadPreview)
        assertFalse(inactiveDecision.rangeChanged)
        assertTrue(inactiveDecision.reloadChanged)
        assertFalse(inactiveDecision.shouldReloadPreview)
    }

    @Test
    fun gcodePreviewStateIsCurrentOnlyWhenAllPreviewVersionsMatch() {
        val expected = GcodePreviewStateVersions(
            previewVersion = 10L,
            layerRangeVersion = 20L,
            pathVisibilityVersion = 30L,
            displayModeVersion = 40L
        )

        assertTrue(ViewerUpdateDecisions.isGcodePreviewStateCurrent(expected, expected))
        assertFalse(
            ViewerUpdateDecisions.isGcodePreviewStateCurrent(
                expected,
                expected.copy(previewVersion = 11L)
            )
        )
        assertFalse(
            ViewerUpdateDecisions.isGcodePreviewStateCurrent(
                expected,
                expected.copy(layerRangeVersion = 21L)
            )
        )
        assertFalse(
            ViewerUpdateDecisions.isGcodePreviewStateCurrent(
                expected,
                expected.copy(pathVisibilityVersion = 31L)
            )
        )
        assertFalse(
            ViewerUpdateDecisions.isGcodePreviewStateCurrent(
                expected,
                expected.copy(displayModeVersion = 41L)
            )
        )
    }

    private fun testPlateObject(
        id: Long,
        mesh: StlMesh,
        transform: ViewerModelTransform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f),
        selected: Boolean = false,
        colorInt: Int? = null
    ): ViewerPlateObject =
        ViewerPlateObject(
            id = id,
            label = "Object $id",
            mesh = mesh,
            transform = transform,
            colorInt = colorInt,
            selected = selected
        )

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
