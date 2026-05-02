package com.mobileslicer

import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectPlateObject
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateFilamentSlot
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
    fun openSavedProjectStateReturnsNullWhenModelFilesAreMissing() {
        val project = savedProject(
            plateObjects = listOf(
                savedPlateObject(filePath = "/missing/project-model.stl")
            )
        )

        assertNull(openSavedProjectState(project))
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

    private fun savedProject(
        id: String = "project",
        updatedAtEpochMs: Long = 1L,
        profileStore: ProfileStore = defaultStore(),
        plateObjects: List<SavedProjectPlateObject> = emptyList(),
        filamentSlots: List<PlateFilamentSlot> = emptyList()
    ): SavedProject = SavedProject(
        id = id,
        name = id,
        updatedAtEpochMs = updatedAtEpochMs,
        profileStore = profileStore,
        plateObjects = plateObjects,
        filamentSlots = filamentSlots
    )

    private fun savedPlateObject(filePath: String): SavedProjectPlateObject =
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
            transform = ViewerModelTransform(centerXmm = 5f, centerYmm = 5f)
        )

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
