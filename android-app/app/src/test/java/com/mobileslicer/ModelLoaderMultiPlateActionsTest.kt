package com.mobileslicer

import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.WorkspacePlate
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.WorkspaceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderMultiPlateActionsTest {
    @Test
    fun addPlateKeepsCurrentPlateActive() {
        val activeObjects = listOf(plateObject(1L, "Cube"))
        val mutation = addWorkspacePlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = activeObjects, selectedObjectId = 1L)),
            activePlateId = 1L,
            activeObjects = activeObjects,
            selectedObjectId = 1L,
            nextPlateId = 2L
        )

        assertEquals(1L, mutation.activePlateId)
        assertEquals(listOf(1L, 2L), mutation.plates.map { it.id })
        assertEquals(emptyList<PlateObject>(), mutation.plates.last().objects)
        assertEquals(activeObjects, mutation.activeObjects)
        assertFalse(mutation.clearGeneratedPreviewState)
    }

    @Test
    fun duplicatePlateKeepsCurrentPlateActiveAndCopiesObjectIds() {
        val activeObjects = listOf(plateObject(1L, "Cube"), plateObject(2L, "Sphere"))
        val mutation = duplicateActiveWorkspacePlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = activeObjects, selectedObjectId = 1L)),
            activePlateId = 1L,
            activeObjects = activeObjects,
            selectedObjectId = 1L,
            nextPlateId = 2L,
            firstObjectId = 10L
        )

        checkNotNull(mutation)
        assertEquals(1L, mutation.activePlateId)
        assertEquals(listOf(1L, 2L), mutation.plates.map { it.id })
        assertEquals(listOf(10L, 11L), mutation.plates.last().objects.map { it.id })
        assertEquals(activeObjects, mutation.activeObjects)
        assertFalse(mutation.clearGeneratedPreviewState)
    }

    @Test
    fun moveObjectToExistingPlateKeepsCurrentPlateActiveAndClearsGeneratedState() {
        val cube = plateObject(1L, "Cube")
        val sphere = plateObject(2L, "Sphere")
        val target = WorkspacePlate(
            id = 2L,
            label = "Plate 2",
            objects = emptyList(),
            gcodeFilePath = "/tmp/stale.gcode",
            slicePreviewKey = 7L
        )
        val mutation = moveWorkspaceObjectToPlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = listOf(cube, sphere), selectedObjectId = 1L), target),
            activePlateId = 1L,
            activeObjects = listOf(cube, sphere),
            selectedObjectId = 1L,
            objectId = 1L,
            targetPlateId = 2L,
            nextPlateId = 3L
        )

        checkNotNull(mutation)
        assertEquals(1L, mutation.activePlateId)
        assertEquals(listOf(2L), mutation.activeObjects.map { it.id })
        assertEquals(2L, mutation.selectedObjectId)
        assertEquals(listOf(1L), mutation.plates[1].objects.map { it.id })
        assertNull(mutation.plates[1].gcodeFilePath)
        assertEquals(0L, mutation.plates[1].slicePreviewKey)
        assertTrue(mutation.clearGeneratedPreviewState)
    }

    @Test
    fun moveObjectToNewPlateCreatesPlateAndKeepsCurrentPlateActive() {
        val cube = plateObject(1L, "Cube")
        val sphere = plateObject(2L, "Sphere")
        val mutation = moveWorkspaceObjectToNewPlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = listOf(cube, sphere), selectedObjectId = 1L)),
            activePlateId = 1L,
            activeObjects = listOf(cube, sphere),
            selectedObjectId = 1L,
            objectId = 1L,
            nextPlateId = 2L
        )

        checkNotNull(mutation)
        assertEquals(1L, mutation.activePlateId)
        assertEquals(2L, mutation.createdPlateId)
        assertEquals(3L, mutation.nextPlateId)
        assertEquals(listOf(2L), mutation.activeObjects.map { it.id })
        assertEquals(listOf(1L), mutation.plates.last().objects.map { it.id })
        assertTrue(mutation.clearGeneratedPreviewState)
    }

    @Test
    fun clearGeneratedPreviewStateDropsStalePreviewAndExportState() {
        val session = WorkspaceSessionViewModel()
        session.currentGcodeFilePath.value = "/tmp/stale.gcode"
        session.currentGcodeFileName.value = "stale.gcode"
        session.currentSlicePreviewKey.longValue = 4L
        session.workspaceMode.value = WorkspaceMode.Preview

        session.clearGeneratedPreviewState()

        assertNull(session.currentGcodeFilePath.value)
        assertEquals("mobile_slicer_output.gcode", session.currentGcodeFileName.value)
        assertEquals(0L, session.currentSlicePreviewKey.longValue)
        assertEquals(WorkspaceMode.Prepare, session.workspaceMode.value)
    }

    private fun plateObject(id: Long, label: String): PlateObject =
        PlateObject(
            id = id,
            label = label,
            filePath = "/tmp/$label.stl",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = id.toFloat(), centerYmm = id.toFloat())
        )
}
