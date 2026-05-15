package com.mobileslicer

import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateProfileState
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
        val profileState = PlateProfileState(selectedProcessId = "process_workspace")
        val mutation = addWorkspacePlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = activeObjects, selectedObjectId = 1L)),
            activePlateId = 1L,
            activeObjects = activeObjects,
            selectedObjectId = 1L,
            nextPlateId = 2L,
            profileState = profileState
        )

        assertEquals(1L, mutation.activePlateId)
        assertEquals(listOf(1L, 2L), mutation.plates.map { it.id })
        assertEquals(emptyList<PlateObject>(), mutation.plates.last().objects)
        assertEquals(profileState, mutation.plates.last().profileState)
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
        val profileState = PlateProfileState(selectedProcessId = "process_workspace")
        val mutation = moveWorkspaceObjectToNewPlateMutation(
            plates = listOf(WorkspacePlate(id = 1L, label = "Plate 1", objects = listOf(cube, sphere), selectedObjectId = 1L)),
            activePlateId = 1L,
            activeObjects = listOf(cube, sphere),
            selectedObjectId = 1L,
            objectId = 1L,
            nextPlateId = 2L,
            profileState = profileState
        )

        checkNotNull(mutation)
        assertEquals(1L, mutation.activePlateId)
        assertEquals(2L, mutation.createdPlateId)
        assertEquals(3L, mutation.nextPlateId)
        assertEquals(listOf(2L), mutation.activeObjects.map { it.id })
        assertEquals(listOf(1L), mutation.plates.last().objects.map { it.id })
        assertEquals(profileState, mutation.plates.last().profileState)
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

    @Test
    fun clearForFreshWorkspaceDropsStaleModelPlatesAndPreviewState() {
        val session = WorkspaceSessionViewModel()
        val staleProfileState = PlateProfileState(selectedProcessId = "stale_process")
        session.modelLoaded.value = true
        session.currentModelLabel.value = "benchy-8.stl"
        session.currentModelFilePath.value = "/tmp/selected-model-123-benchy-8.stl"
        session.currentGcodeFilePath.value = "/tmp/latest-slice-benchy-8.gcode"
        session.currentGcodeFileName.value = "benchy-8.gcode"
        session.currentSlicePreviewKey.longValue = 9L
        session.workspaceMode.value = WorkspaceMode.Preview
        session.workspacePlates.clear()
        session.workspacePlates.add(WorkspacePlate(id = 4L, label = "Plate 4"))
        session.activePlateProfileState.value = staleProfileState
        session.activePlateId.longValue = 4L
        session.nextPlateId.longValue = 7L
        session.plateObjects.add(plateObject(12L, "stale.stl"))
        session.selectedPlateObjectId.value = 12L
        session.nextPlateObjectId.longValue = 13L
        session.currentSavedProjectId.value = "old-project"

        session.clearForFreshWorkspace()

        assertFalse(session.modelLoaded.value)
        assertEquals("No model imported", session.currentModelLabel.value)
        assertNull(session.currentModelFilePath.value)
        assertNull(session.currentGcodeFilePath.value)
        assertEquals("mobile_slicer_output.gcode", session.currentGcodeFileName.value)
        assertEquals(0L, session.currentSlicePreviewKey.longValue)
        assertEquals(WorkspaceMode.Prepare, session.workspaceMode.value)
        assertEquals(listOf(1L), session.workspacePlates.map { it.id })
        assertEquals(PlateProfileState(), session.activePlateProfileState.value)
        assertEquals(1L, session.activePlateId.longValue)
        assertEquals(2L, session.nextPlateId.longValue)
        assertTrue(session.plateObjects.isEmpty())
        assertNull(session.selectedPlateObjectId.value)
        assertEquals(1L, session.nextPlateObjectId.longValue)
        assertNull(session.currentSavedProjectId.value)
    }

    @Test
    fun transferProcessOverridesMovesDirtyWorkspaceChangesToNewSelectedPreset() {
        val firstProcess = newProcessProfileUnchecked(
            0 to "process_standard",
            1 to "0.20mm Standard",
            5 to 0.20f,
            259 to "printer_fixture",
            261 to 0.4f
        ).withValues(
            "orcaResolvedProcessJson" to """{"layer_height":0.20}"""
        )
        val secondProcess = newProcessProfileUnchecked(
            0 to "process_fine",
            1 to "0.12mm Fine",
            5 to 0.12f,
            259 to "printer_fixture",
            261 to 0.4f
        ).withValues(
            "orcaResolvedProcessJson" to """{"layer_height":0.12}"""
        )
        val dirtyState = PlateProfileState(
            selectedProcessId = firstProcess.id,
            editedProcessProfile = firstProcess.withValues(
                "layerHeightMm" to 0.16f,
                "enableSupport" to true
            )
        )

        val transferred = dirtyState.transferProcessOverridesTo(secondProcess)

        assertEquals(secondProcess.id, transferred.selectedProcessId)
        assertTrue(transferred.hasProcessOverrides)
        assertEquals(secondProcess.id, transferred.editedProcessProfile?.id)
        assertEquals(secondProcess.name, transferred.editedProcessProfile?.name)
        assertEquals(0.16f, transferred.editedProcessProfile?.layerHeightMm ?: 0f, 0.0001f)
        assertTrue(transferred.editedProcessProfile?.enableSupport == true)
        assertEquals("""{"layer_height":0.12}""", transferred.editedProcessProfile?.orcaResolvedProcessJson)
    }

    @Test
    fun defaultPlateLabelsNormalizeToVisibleOrder() {
        val plates = normalizeDefaultWorkspacePlateLabels(
            listOf(
                WorkspacePlate(id = 8L, label = "Plate 4"),
                WorkspacePlate(id = 2L, label = "Plate 2"),
                WorkspacePlate(id = 9L, label = "Custom setup")
            )
        )

        assertEquals(listOf("Plate 1", "Plate 2", "Custom setup"), plates.map { it.label })
    }

    @Test
    fun orcaMultiPlateMutationUsesNativeBedIndicesWithoutRepacking() {
        val first = plateObject(1L, "First")
        val second = plateObject(2L, "Second")
        val third = plateObject(3L, "Third")
        val existingSecondPlateObject = plateObject(9L, "Existing")
        val result = PlateAutoArrangeResult(
            objects = listOf(first, second, third),
            changedCount = 3,
            reservedPrimeTowerSpace = false,
            centersSummary = "native",
            bedIndices = listOf(0, 1, 1)
        )

        val mutation = orcaMultiPlateWorkspaceMutation(
            result = result,
            plates = listOf(
                WorkspacePlate(id = 1L, label = "Plate 1", objects = listOf(first, second, third), selectedObjectId = 2L),
                WorkspacePlate(id = 2L, label = "Plate 2", objects = listOf(existingSecondPlateObject), selectedObjectId = 9L)
            ),
            activePlateId = 1L,
            selectedObjectId = 2L,
            nextPlateId = 3L
        )

        checkNotNull(mutation)
        assertEquals(listOf(1L, 3L, 2L), mutation.plates.map { it.id })
        assertEquals(listOf(1L), mutation.plates[0].objects.map { it.id })
        assertEquals(listOf(2L, 3L), mutation.plates[1].objects.map { it.id })
        assertEquals(listOf(9L), mutation.plates[2].objects.map { it.id })
        assertEquals(listOf(1L), mutation.activeObjects.map { it.id })
        assertEquals(1L, mutation.selectedObjectId)
        assertEquals(4L, mutation.nextPlateId)
        assertTrue(mutation.clearGeneratedPreviewState)
    }

    private fun plateObject(
        id: Long,
        label: String
    ): PlateObject =
        PlateObject(
            id = id,
            label = label,
            filePath = "/tmp/$label.stl",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = id.toFloat(), centerYmm = id.toFloat())
        )
}
