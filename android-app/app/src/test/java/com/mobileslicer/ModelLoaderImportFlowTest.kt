package com.mobileslicer

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelImportTiming
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspacePreparationTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderImportFlowTest {
    @Test
    fun freshStlImportReplacesPlateAndClearsSavedProject() {
        val application = planModelImportApplication(
            result = modelLoadResult(path = "/tmp/new.stl"),
            currentScreen = AppScreen.Home,
            existingPlateObjects = listOf(plateObject(id = 7L, filePath = "/tmp/old.stl")),
            appendRequested = false,
            nextPlateObjectId = 12L,
            defaultTransform = { ViewerModelTransform(centerXmm = it.toFloat(), centerYmm = 1f) }
        )

        assertTrue(application.replacePlate)
        assertTrue(application.clearSavedProject)
        assertEquals(13L, application.nextPlateObjectId)
        assertEquals(12L, application.importedPlateObject?.id)
        assertEquals(0f, application.importedPlateObject?.transform?.centerXmm)
        assertTrue(application.shouldOpenWorkspace)
    }

    @Test
    fun workspaceStlImportAppendsWhenPlateAlreadyHasObjects() {
        val application = planModelImportApplication(
            result = modelLoadResult(path = "/tmp/append.stl"),
            currentScreen = AppScreen.Workspace,
            existingPlateObjects = listOf(plateObject(id = 1L, filePath = "/tmp/existing.stl")),
            appendRequested = false,
            nextPlateObjectId = 2L,
            defaultTransform = { ViewerModelTransform(centerXmm = it.toFloat(), centerYmm = 1f) }
        )

        assertFalse(application.replacePlate)
        assertFalse(application.clearSavedProject)
        assertEquals(2L, application.importedPlateObject?.id)
        assertEquals(1f, application.importedPlateObject?.transform?.centerXmm)
    }

    @Test
    fun importCompletionUiPlanResetsImportStateAndOpensWorkspaceOnlyWhenNeeded() {
        val openApplication = planModelImportApplication(
            result = modelLoadResult(path = "/tmp/new.stl"),
            currentScreen = AppScreen.Home,
            existingPlateObjects = emptyList(),
            appendRequested = false,
            nextPlateObjectId = 1L,
            defaultTransform = { ViewerModelTransform(centerXmm = 0f, centerYmm = 0f) }
        )
        assertEquals(
            ModelLoaderImportCompletionUiPlan(
                importInProgress = false,
                appendNextImportToPlate = false,
                clearGeneratedPreviewState = true,
                screen = AppScreen.Workspace
            ),
            planModelImportCompletionUi(openApplication)
        )

        val stayApplication = planModelImportApplication(
            result = ModelLoadResult(
                message = "Import failed",
                loaded = false,
                stagedFilePath = null,
                format = null
            ),
            currentScreen = AppScreen.Home,
            existingPlateObjects = emptyList(),
            appendRequested = true,
            nextPlateObjectId = 1L,
            defaultTransform = { ViewerModelTransform(centerXmm = 0f, centerYmm = 0f) }
        )
        assertEquals(
            ModelLoaderImportCompletionUiPlan(
                importInProgress = false,
                appendNextImportToPlate = false,
                clearGeneratedPreviewState = false,
                screen = null
            ),
            planModelImportCompletionUi(stayApplication)
        )
    }

    @Test
    fun appendImportReusesPreparedMeshForSameStagedFile() {
        val mesh = mesh(bounds = bounds(maxX = 2f))
        val existing = plateObject(
            id = 1L,
            filePath = "/tmp/reused.stl",
            mesh = mesh,
            viewerPreparationError = "cached warning",
            workspacePreparationTiming = WorkspacePreparationTiming(viewerMeshPrepMs = 42L)
        )

        val application = planModelImportApplication(
            result = modelLoadResult(path = "/tmp/reused.stl"),
            currentScreen = AppScreen.Workspace,
            existingPlateObjects = listOf(existing),
            appendRequested = true,
            nextPlateObjectId = 2L,
            defaultTransform = { ViewerModelTransform(centerXmm = it.toFloat(), centerYmm = 1f) }
        )

        assertSame(mesh, application.importedPlateObject?.mesh)
        assertEquals("cached warning", application.importedPlateObject?.viewerPreparationError)
        assertEquals(42L, application.importedPlateObject?.workspacePreparationTiming?.viewerMeshPrepMs)
    }

    @Test
    fun threeMfImportUsesLegacyStateButStillOpensWorkspace() {
        val application = planModelImportApplication(
            result = ModelLoadResult(
                message = "Model loaded\nplate.3mf",
                loaded = false,
                stagedFilePath = "/tmp/plate.3mf",
                format = ImportedModelFormat.ThreeMf
            ),
            currentScreen = AppScreen.Home,
            existingPlateObjects = emptyList(),
            appendRequested = false,
            nextPlateObjectId = 4L,
            defaultTransform = { ViewerModelTransform(centerXmm = 0f, centerYmm = 0f) }
        )

        assertNull(application.importedPlateObject)
        assertNotNull(application.legacyState)
        assertEquals("plate.3mf", application.loadedLabel)
        assertEquals(4L, application.nextPlateObjectId)
        assertTrue(application.shouldOpenWorkspace)
    }

    @Test
    fun importStartPlanClearsStalePreparationAndPreviewState() {
        val start = planModelImportStart()

        assertTrue(start.importInProgress)
        assertTrue(start.currentCalibrationJobCleared)
        assertEquals("Loading model", start.statusMessage)
        assertTrue(start.clearGeneratedPreviewState)
        assertTrue(start.clearPreparedMeshState)
        assertTrue(start.clearFirstFrameTimings)
        assertTrue(start.clearWorkspacePreparationTarget)
    }

    @Test
    fun legacyStateForPlateObjectMirrorsSelectedObjectAndClearsWhenNull() {
        val objectOnPlate = plateObject(id = 9L, filePath = "/tmp/selected.stl")
        val selectedState = legacyStateForPlateObject(objectOnPlate)

        assertTrue(selectedState.modelLoaded)
        assertEquals("selected.stl", selectedState.modelLabel)
        assertEquals("/tmp/selected.stl", selectedState.modelFilePath)
        assertEquals(ImportedModelFormat.Stl.name, selectedState.modelFormatName)
        assertEquals(objectOnPlate.bounds, selectedState.modelBounds)

        val emptyState = legacyStateForPlateObject(null)
        assertFalse(emptyState.modelLoaded)
        assertEquals("No model imported", emptyState.modelLabel)
        assertNull(emptyState.modelFilePath)
        assertNull(emptyState.modelBounds)
        assertNull(emptyState.modelFormatName)
    }

    @Test
    fun selectedObjectSyncPlanCarriesLegacyAndViewerState() {
        val objectOnPlate = plateObject(id = 9L, filePath = "/tmp/selected.stl")
        val syncPlan = planSelectedObjectSync(objectOnPlate)

        assertEquals(9L, syncPlan.selectedPlateObjectId)
        assertTrue(syncPlan.legacyState.modelLoaded)
        assertEquals("selected.stl", syncPlan.legacyState.modelLabel)
        assertEquals(objectOnPlate.mesh, syncPlan.preparedMesh)
        assertEquals(objectOnPlate.viewerPreparationError, syncPlan.viewerPreparationError)
        assertEquals(objectOnPlate.workspacePreparationTiming, syncPlan.workspacePreparationTiming)
        assertEquals(objectOnPlate.transform, syncPlan.modelTransform)

        val emptyPlan = planSelectedObjectSync(null)
        assertNull(emptyPlan.selectedPlateObjectId)
        assertFalse(emptyPlan.legacyState.modelLoaded)
        assertNull(emptyPlan.preparedMesh)
        assertNull(emptyPlan.modelTransform)
    }

    @Test
    fun workspacePreparationRequestUsesStableTargetKeyAndSkipsInProgressTarget() {
        val selected = plateObject(id = 3L, filePath = "/tmp/current.stl")
        val request = resolveWorkspacePreparationRequest(
            currentScreen = AppScreen.Workspace,
            modelLoaded = true,
            currentModelFilePath = "/tmp/current.stl",
            currentModelFormatName = ImportedModelFormat.Stl.name,
            currentImportTiming = ModelImportTiming(stagingMs = 1L, nativeLoadMs = 2L),
            selectedObject = selected,
            currentPreparedMeshPresent = false,
            currentViewerPreparationError = null,
            inProgressTargetKey = null
        )

        assertEquals("3:/tmp/current.stl", request?.targetKey)
        assertEquals(1L, request?.importTiming?.stagingMs)
        assertNull(
            resolveWorkspacePreparationRequest(
                currentScreen = AppScreen.Workspace,
                modelLoaded = true,
                currentModelFilePath = "/tmp/current.stl",
                currentModelFormatName = ImportedModelFormat.Stl.name,
                currentImportTiming = null,
                selectedObject = selected,
                currentPreparedMeshPresent = false,
                currentViewerPreparationError = null,
                inProgressTargetKey = request?.targetKey
            )
        )
    }

    @Test
    fun workspacePreparationResultOnlyAppliesToMatchingCurrentTarget() {
        val selected = plateObject(id = 5L, filePath = "/tmp/selected.stl")
        val other = plateObject(id = 6L, filePath = "/tmp/other.stl")
        val request = ModelLoaderWorkspacePreparationRequest(
            selectedObject = selected,
            modelFilePath = selected.filePath,
            targetKey = "5:/tmp/selected.stl",
            importTiming = selected.importTiming
        )
        val result = WorkspacePreparationResult(
            preparedMesh = mesh(bounds = bounds(maxX = 9f)),
            viewerPreparationError = null,
            timing = WorkspacePreparationTiming(viewerMeshPrepMs = 30L)
        )

        assertTrue(
            workspacePreparationTargetStillCurrent(
                targetObjectId = selected.id,
                selectedPlateObjectId = selected.id,
                currentModelFilePath = selected.filePath,
                modelFilePath = selected.filePath
            )
        )
        assertFalse(
            workspacePreparationTargetStillCurrent(
                targetObjectId = selected.id,
                selectedPlateObjectId = other.id,
                currentModelFilePath = selected.filePath,
                modelFilePath = selected.filePath
            )
        )

        val currentApplication = planWorkspacePreparationApplication(
            request = request,
            result = result,
            selectedPlateObjectId = selected.id,
            currentModelFilePath = selected.filePath,
            currentModelBounds = selected.bounds
        )
        assertTrue(currentApplication.targetStillCurrent)
        assertEquals(9f, currentApplication.legacyState?.modelBounds?.maxX)
        assertTrue(currentApplication.statusMessage?.contains("Workspace mesh prepared") == true)

        val staleApplication = planWorkspacePreparationApplication(
            request = request,
            result = result,
            selectedPlateObjectId = other.id,
            currentModelFilePath = selected.filePath,
            currentModelBounds = selected.bounds
        )
        assertFalse(staleApplication.targetStillCurrent)
        assertNull(staleApplication.legacyState)
        assertNull(staleApplication.statusMessage)
        assertNull(clearedWorkspacePreparationTarget(request.targetKey, request.targetKey))
        assertEquals("other", clearedWorkspacePreparationTarget("other", request.targetKey))

        val updated = applyWorkspacePreparationToPlateObject(selected, selected.filePath, result)
        val unchanged = applyWorkspacePreparationToPlateObject(other, selected.filePath, result)

        assertEquals(9f, updated.bounds?.maxX)
        assertEquals(30L, updated.workspacePreparationTiming?.viewerMeshPrepMs)
        assertNotEquals(9f, unchanged.bounds?.maxX)
        assertNull(unchanged.workspacePreparationTiming)
    }

    private fun modelLoadResult(path: String): ModelLoadResult =
        ModelLoadResult(
            message = "Model loaded\n${path.substringAfterLast('/')}",
            loaded = true,
            stagedFilePath = path,
            format = ImportedModelFormat.Stl,
            loadTiming = ModelImportTiming(stagingMs = 10L, nativeLoadMs = 20L),
            bounds = bounds()
        )

    private fun plateObject(
        id: Long,
        filePath: String,
        mesh: StlMesh? = null,
        viewerPreparationError: String? = null,
        workspacePreparationTiming: WorkspacePreparationTiming? = null
    ): PlateObject =
        PlateObject(
            id = id,
            label = filePath.substringAfterLast('/'),
            filePath = filePath,
            format = ImportedModelFormat.Stl,
            importTiming = ModelImportTiming(stagingMs = 1L, nativeLoadMs = 2L),
            bounds = mesh?.bounds ?: bounds(maxX = id.toFloat()),
            mesh = mesh,
            viewerPreparationError = viewerPreparationError,
            workspacePreparationTiming = workspacePreparationTiming,
            transform = ViewerModelTransform(centerXmm = 1f, centerYmm = 1f)
        )

    private fun mesh(bounds: MeshBounds): StlMesh =
        StlMesh(
            vertices = floatArrayOf(0f, 0f, 0f),
            normals = floatArrayOf(0f, 0f, 1f),
            triangleCount = 1,
            bounds = bounds
        )

    private fun bounds(maxX: Float = 1f): MeshBounds =
        MeshBounds(
            minX = 0f,
            minY = 0f,
            minZ = 0f,
            maxX = maxX,
            maxY = 1f,
            maxZ = 1f
        )
}
