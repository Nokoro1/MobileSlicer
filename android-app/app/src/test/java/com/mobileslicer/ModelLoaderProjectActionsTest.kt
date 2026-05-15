package com.mobileslicer

import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectPlate
import com.mobileslicer.storage.SavedProjectPlateObject
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateProfileState
import com.mobileslicer.workspace.WorkspacePlate
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderProjectActionsTest {
    @Test
    fun normalizedSavedProjectsKeepsMostRecentTwentyFour() {
        val projects = (1..30).map { index ->
            savedProject(
                id = "project_$index",
                updatedAtEpochMs = index.toLong()
            )
        }

        val normalized = normalizedSavedProjects(projects)

        assertEquals(24, normalized.size)
        assertEquals("project_30", normalized.first().id)
        assertEquals("project_7", normalized.last().id)
    }

    @Test
    fun pruneInactiveSavedProjectDirectoriesDeletesOnlyInactiveProjectDirs() {
        val root = Files.createTempDirectory("mobileslicer-project-prune-").toFile()
        try {
            val active = File(root, "active").apply { mkdirs() }
            val inactive = File(root, "inactive").apply { mkdirs() }
            val looseFile = File(root, "loose.txt").apply { writeText("keep") }

            pruneInactiveSavedProjectDirectories(
                savedProjectRootDir = root,
                activeProjects = listOf(savedProject(id = "active"))
            )

            assertTrue(active.exists())
            assertFalse(inactive.exists())
            assertTrue(looseFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun savedProjectsAfterSaveNormalizesListAndSelectsSavedProject() {
        val existing = listOf(
            savedProject(id = "old", updatedAtEpochMs = 1L),
            savedProject(id = "replace", updatedAtEpochMs = 2L)
        )
        val saved = savedProject(id = "replace", updatedAtEpochMs = 99L)

        val update = savedProjectsAfterSave(saved, existing)

        assertEquals("replace", update.currentSavedProjectId)
        assertEquals(listOf("replace", "old"), update.projects.map { it.id })
        assertEquals(99L, update.projects.first().updatedAtEpochMs)
    }

    @Test
    fun savedProjectsAfterDeleteClearsCurrentProjectOnlyWhenDeleted() {
        val existing = listOf(
            savedProject(id = "keep", updatedAtEpochMs = 1L),
            savedProject(id = "delete", updatedAtEpochMs = 2L)
        )

        val activeDeleted = savedProjectsAfterDelete(
            project = existing[1],
            existingProjects = existing,
            currentSavedProjectId = "delete"
        )
        assertNull(activeDeleted.currentSavedProjectId)
        assertEquals(listOf("keep"), activeDeleted.projects.map { it.id })

        val inactiveDeleted = savedProjectsAfterDelete(
            project = existing[1],
            existingProjects = existing,
            currentSavedProjectId = "keep"
        )
        assertEquals("keep", inactiveDeleted.currentSavedProjectId)
    }

    @Test
    fun openSavedProjectStateReturnsNullWhenModelFilesAreMissing() {
        val project = savedProject(
            plateObjects = listOf(
                savedPlateObject(filePath = "/missing/project-model.stl")
            )
        )

        assertNull(openSavedProjectState(project))
    }

    @Test
    fun openSavedProjectStateReturnsNullWhenAnyModelFileIsMissing() {
        val existingFile = File.createTempFile("mobileslicer-project-open-existing-", ".stl")
        try {
            existingFile.writeText(minimalStl())
            val project = savedProject(
                plateObjects = listOf(
                    savedPlateObject(filePath = existingFile.absolutePath),
                    savedPlateObject(filePath = "/missing/project-model.stl")
                )
            )

            assertNull(openSavedProjectState(project))
        } finally {
            existingFile.delete()
        }
    }

    @Test
    fun savePlatePromptFailsForEmptyPlateAndSuggestsProjectNameForObjects() {
        val emptyPlan = planSavePlatePrompt(emptyList(), currentModelLabel = "Fallback Model")
        assertEquals(ModelLoaderSavePlatePromptPlan.Fail("No plate to save"), emptyPlan)

        val singlePlan = planSavePlatePrompt(
            plateObjects = listOf(plateObjectForPrompt("Cube")),
            currentModelLabel = "Fallback Model"
        )
        assertEquals(ModelLoaderSavePlatePromptPlan.Prompt("Cube"), singlePlan)

        val multiPlan = planSavePlatePrompt(
            plateObjects = listOf(plateObjectForPrompt("Cube"), plateObjectForPrompt("Sphere")),
            currentModelLabel = "Fallback Model"
        )
        assertEquals(ModelLoaderSavePlatePromptPlan.Prompt("Cube + 1"), multiPlan)
    }

    @Test
    fun savedProjectLoadedStatusIncludesNativeReloadWarningOnlyWhenNeeded() {
        val project = savedProject(id = "project_status").copy(name = "Fixture Plate")

        assertEquals("No plate to save", noPlateToSaveStatus())
        assertEquals("Plate saved\nFixture Plate", plateSavedStatus(project))
        assertEquals("Opening saved project\nFixture Plate", savedProjectOpeningStatus(project))
        assertEquals(
            "Saved project could not be opened\nModel files are missing.",
            savedProjectOpenMissingFilesStatus()
        )
        assertEquals(
            ModelLoaderSavedProjectOpenStartPlan(
                importInProgress = true,
                firstVisibleWorkspaceFrameMs = null,
                firstVisiblePreviewFrameMs = null,
                statusMessage = "Opening saved project\nFixture Plate"
            ),
            planSavedProjectOpenStart(project)
        )
        assertEquals(
            ModelLoaderSavedProjectOpenMissingFilesPlan(
                importInProgress = false,
                statusMessage = "Saved project could not be opened\nModel files are missing."
            ),
            planSavedProjectOpenMissingFiles()
        )
        assertEquals("Project loaded\nFixture Plate", savedProjectLoadedStatus(project, nativeWarmLoadSucceeded = true))
        assertEquals(
            "Project loaded\nFixture Plate\nNative model will reload on first slice.",
            savedProjectLoadedStatus(project, nativeWarmLoadSucceeded = false)
        )
    }

    @Test
    fun openSavedProjectStateLoadsObjectsAndFallsBackToActiveFilamentSlot() {
        val file = File.createTempFile("mobileslicer-project-open-", ".stl")
        try {
            file.writeText(
                minimalStl()
            )
            val project = savedProject(
                plateObjects = listOf(savedPlateObject(filePath = file.absolutePath)),
                filamentSlots = emptyList()
            )

            val opened = openSavedProjectState(project)

            assertNotNull(opened)
            checkNotNull(opened)
            assertEquals(1, opened.plateObjects.size)
            assertEquals(file.absolutePath, opened.plateObjects.single().filePath)
            assertEquals(2L, opened.nextPlateObjectId)
            assertEquals(1, opened.filamentSlots.single().index)
            assertEquals(project.profileStore.activeConfiguration().filament.id, opened.filamentSlots.single().filamentProfileId)
        } finally {
            file.delete()
        }
    }

    @Test
    fun openSavedProjectStatePreservesMultiPlateObjectTransforms() {
        val firstFile = File.createTempFile("mobileslicer-project-open-first-", ".stl")
        val secondFile = File.createTempFile("mobileslicer-project-open-second-", ".stl")
        try {
            firstFile.writeText(minimalStl())
            secondFile.writeText(minimalStl())
            val firstTransform = ViewerModelTransform(centerXmm = 12f, centerYmm = 34f, rotationZDegrees = 15f)
            val secondTransform = ViewerModelTransform(centerXmm = 56f, centerYmm = 78f, rotationZDegrees = 30f)
            val project = savedProject(
                plates = listOf(
                    SavedProjectPlate(
                        label = "Plate A",
                        plateObjects = listOf(savedPlateObject(filePath = firstFile.absolutePath, transform = firstTransform))
                    ),
                    SavedProjectPlate(
                        label = "Plate B",
                        plateObjects = listOf(savedPlateObject(filePath = secondFile.absolutePath, transform = secondTransform))
                    )
                )
            )

            val opened = openSavedProjectState(project)

            assertNotNull(opened)
            checkNotNull(opened)
            assertEquals(listOf("Plate A", "Plate B"), opened.plates.map { it.label })
            assertEquals(firstTransform, opened.plates[0].objects.single().transform)
            assertEquals(secondTransform, opened.plates[1].objects.single().transform)
            assertEquals(firstTransform, opened.plateObjects.single().transform)
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun openSavedProjectStateLoadsStoredWorkspaceProcessState() {
        val firstFile = File.createTempFile("mobileslicer-project-profile-first-", ".stl")
        val secondFile = File.createTempFile("mobileslicer-project-profile-second-", ".stl")
        try {
            firstFile.writeText(minimalStl())
            secondFile.writeText(minimalStl())
            val store = defaultStore()
            val editedProcess = store.processes.first().withValues(
                "name" to "Fixture Process - workspace edit",
                "layerHeightMm" to 0.12f,
                "orcaProcessOverridesJson" to """{"layer_height":0.12}"""
            )
            val firstState = PlateProfileState(
                selectedProcessId = editedProcess.id,
                editedProcessProfile = editedProcess
            )
            val secondState = PlateProfileState(selectedProcessId = store.processes.first().id)
            val project = savedProject(
                profileStore = store,
                plates = listOf(
                    SavedProjectPlate(
                        label = "Fine plate",
                        plateObjects = listOf(savedPlateObject(filePath = firstFile.absolutePath)),
                        profileState = firstState
                    ),
                    SavedProjectPlate(
                        label = "Standard plate",
                        plateObjects = listOf(savedPlateObject(filePath = secondFile.absolutePath)),
                        profileState = secondState
                    )
                )
            )

            val opened = openSavedProjectState(project)

            assertNotNull(opened)
            checkNotNull(opened)
            assertEquals(firstState, opened.plates[0].profileState)
            assertEquals(secondState, opened.plates[1].profileState)
            assertEquals(0.12f, opened.plates[0].profileState.editedProcessProfile?.layerHeightMm ?: 0f, 0.0001f)
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun synchronizeWorkspaceProcessStateAppliesRestoredStateToEveryPlate() {
        val store = defaultStore()
        val restoredState = PlateProfileState(
            selectedProcessId = store.processes.first().id,
            editedProcessProfile = store.processes.first().withValues(
                "name" to "Fixture Process - workspace edit",
                "layerHeightMm" to 0.12f
            )
        )
        val staleState = PlateProfileState(selectedProcessId = "stale_process")
        val plates = listOf(
            WorkspacePlate(id = 1L, label = "Plate 1", profileState = restoredState),
            WorkspacePlate(id = 2L, label = "Plate 2", profileState = staleState)
        )

        val synchronized = synchronizeWorkspaceProcessState(plates, restoredState)

        assertEquals(listOf(restoredState, restoredState), synchronized.map { it.profileState })
    }

    private fun savedProject(
        id: String = "project",
        updatedAtEpochMs: Long = 1L,
        profileStore: ProfileStore = defaultStore(),
        plateObjects: List<SavedProjectPlateObject> = emptyList(),
        plates: List<SavedProjectPlate> = emptyList(),
        filamentSlots: List<PlateFilamentSlot> = emptyList()
    ): SavedProject = SavedProject(
        id = id,
        name = id,
        updatedAtEpochMs = updatedAtEpochMs,
        profileStore = profileStore,
        plateObjects = plateObjects,
        plates = plates,
        filamentSlots = filamentSlots
    )

    private fun savedPlateObject(
        filePath: String,
        transform: ViewerModelTransform = ViewerModelTransform(centerXmm = 5f, centerYmm = 5f)
    ): SavedProjectPlateObject =
        SavedProjectPlateObject(
            label = "Test Model",
            filePath = filePath,
            format = ImportedModelFormat.Stl,
            bounds = MeshBounds(
                minX = 0f,
                maxX = 10f,
                minY = 0f,
                maxY = 10f,
                minZ = 0f,
                maxZ = 1f
            ),
            transform = transform
        )

    private fun minimalStl(): String =
        """
        solid saved
          facet normal 0 0 1
            outer loop
              vertex 0 0 0
              vertex 10 0 0
              vertex 0 10 1
            endloop
          endfacet
        endsolid saved
        """.trimIndent()

    private fun plateObjectForPrompt(label: String): com.mobileslicer.workspace.PlateObject =
        com.mobileslicer.workspace.PlateObject(
            id = label.hashCode().toLong(),
            label = label,
            filePath = "/tmp/$label.stl",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = 5f, centerYmm = 5f)
        )

    private fun defaultStore(): ProfileStore {
        val printers = listOf(ProfileStoreRepository.fallbackPrinterProfile())
        val filaments = ProfileStoreRepository.defaultFilamentProfiles()
        val processes = listOf(
            newProcessProfileUnchecked(
                0 to "process_fixture",
                1 to "Fixture Process",
                3 to false,
                5 to 0.20f,
                259 to printers.first().id,
                261 to printers.first().nozzleDiameterMm
            )
        )
        return ProfileStore(
            printers = printers,
            filaments = filaments,
            processes = processes,
            selectedPrinterId = printers.first().id,
            selectedFilamentId = filaments.first().id,
            selectedProcessId = processes.first().id
        )
    }
}
