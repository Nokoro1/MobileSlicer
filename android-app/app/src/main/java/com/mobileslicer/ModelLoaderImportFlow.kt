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

internal data class ModelLoaderLegacyModelState(
    val modelLoaded: Boolean,
    val modelLabel: String,
    val modelFilePath: String?,
    val importTiming: ModelImportTiming?,
    val modelBounds: MeshBounds?,
    val modelFormatName: String?
)

internal data class ModelLoaderImportApplication(
    val loadedLabel: String,
    val replacePlate: Boolean,
    val clearSavedProject: Boolean,
    val importedPlateObject: PlateObject?,
    val nextPlateObjectId: Long,
    val legacyState: ModelLoaderLegacyModelState?,
    val shouldOpenWorkspace: Boolean,
    val statusMessage: String
)

internal data class ModelLoaderImportCompletionUiPlan(
    val importInProgress: Boolean,
    val appendNextImportToPlate: Boolean,
    val clearGeneratedPreviewState: Boolean,
    val screen: AppScreen?
)

internal data class ModelLoaderWorkspacePreparationRequest(
    val selectedObject: PlateObject?,
    val modelFilePath: String,
    val targetKey: String,
    val importTiming: ModelImportTiming?
) {
    val selectedObjectId: Long? = selectedObject?.id
}

internal data class ModelLoaderPreparedLegacyState(
    val preparedMesh: StlMesh?,
    val modelBounds: MeshBounds?,
    val viewerPreparationError: String?,
    val workspacePreparationTiming: WorkspacePreparationTiming?
)

internal data class ModelLoaderWorkspacePreparationApplication(
    val targetStillCurrent: Boolean,
    val legacyState: ModelLoaderPreparedLegacyState?,
    val statusMessage: String?
)

internal data class ModelLoaderSelectedObjectSyncPlan(
    val selectedPlateObjectId: Long?,
    val legacyState: ModelLoaderLegacyModelState,
    val preparedMesh: StlMesh?,
    val viewerPreparationError: String?,
    val workspacePreparationTiming: WorkspacePreparationTiming?,
    val modelTransform: ViewerModelTransform?
)

internal data class ModelLoaderImportStartState(
    val importInProgress: Boolean = true,
    val currentCalibrationJobCleared: Boolean = true,
    val statusMessage: String = "Loading model",
    val clearGeneratedPreviewState: Boolean = true,
    val clearPreparedMeshState: Boolean = true,
    val clearFirstFrameTimings: Boolean = true,
    val clearWorkspacePreparationTarget: Boolean = true
)

internal fun modelLoadResultLabel(result: ModelLoadResult): String =
    result.message.lineSequence().drop(1).firstOrNull()?.ifBlank { "No model imported" }
        ?: if (result.loaded) "Model ready" else "No model imported"

internal fun planModelImportStart(): ModelLoaderImportStartState =
    ModelLoaderImportStartState()

internal fun planModelImportApplication(
    result: ModelLoadResult,
    currentScreen: AppScreen,
    existingPlateObjects: List<PlateObject>,
    appendRequested: Boolean,
    nextPlateObjectId: Long,
    defaultTransform: (Int) -> ViewerModelTransform
): ModelLoaderImportApplication {
    val loadedLabel = modelLoadResultLabel(result)
    val shouldOpenWorkspace = result.loaded || result.format == ImportedModelFormat.ThreeMf
    if (result.loaded && result.stagedFilePath != null && result.format == ImportedModelFormat.Stl) {
        val shouldAppendToPlate = appendRequested || (currentScreen == AppScreen.Workspace && existingPlateObjects.isNotEmpty())
        val retainedPlateObjects = if (shouldAppendToPlate) existingPlateObjects else emptyList()
        val objectId = nextPlateObjectId
        val reusableObject = retainedPlateObjects.firstOrNull { it.filePath == result.stagedFilePath }
        val objectOnPlate = PlateObject(
            id = objectId,
            label = loadedLabel,
            filePath = result.stagedFilePath,
            nativeSourceKey = result.stagedFilePath,
            filamentSlotIndex = 1,
            format = result.format,
            importTiming = result.loadTiming,
            bounds = result.bounds,
            mesh = reusableObject?.mesh,
            viewerPreparationError = reusableObject?.viewerPreparationError,
            workspacePreparationTiming = reusableObject?.workspacePreparationTiming,
            transform = defaultTransform(retainedPlateObjects.size),
            geometrySource = result.geometrySource
        )
        return ModelLoaderImportApplication(
            loadedLabel = loadedLabel,
            replacePlate = !shouldAppendToPlate,
            clearSavedProject = !shouldAppendToPlate,
            importedPlateObject = objectOnPlate,
            nextPlateObjectId = objectId + 1L,
            legacyState = null,
            shouldOpenWorkspace = shouldOpenWorkspace,
            statusMessage = result.message
        )
    }

    return ModelLoaderImportApplication(
        loadedLabel = loadedLabel,
        replacePlate = false,
        clearSavedProject = false,
        importedPlateObject = null,
        nextPlateObjectId = nextPlateObjectId,
        legacyState = ModelLoaderLegacyModelState(
            modelLoaded = result.loaded,
            modelLabel = loadedLabel,
            modelFilePath = result.stagedFilePath,
            importTiming = result.loadTiming,
            modelBounds = result.bounds,
            modelFormatName = result.format?.name
        ),
        shouldOpenWorkspace = shouldOpenWorkspace,
        statusMessage = result.message
    )
}

internal fun planModelImportCompletionUi(application: ModelLoaderImportApplication): ModelLoaderImportCompletionUiPlan =
    ModelLoaderImportCompletionUiPlan(
        importInProgress = false,
        appendNextImportToPlate = false,
        clearGeneratedPreviewState = application.shouldOpenWorkspace,
        screen = if (application.shouldOpenWorkspace) AppScreen.Workspace else null
    )

internal fun resolveWorkspacePreparationRequest(
    currentScreen: AppScreen,
    modelLoaded: Boolean,
    currentModelFilePath: String?,
    currentModelFormatName: String?,
    currentImportTiming: ModelImportTiming?,
    selectedObject: PlateObject?,
    currentPreparedMeshPresent: Boolean,
    currentViewerPreparationError: String?,
    inProgressTargetKey: String?
): ModelLoaderWorkspacePreparationRequest? {
    val modelFilePath = selectedObject?.filePath ?: currentModelFilePath ?: return null
    val targetKey = workspacePreparationTargetKey(selectedObject, modelFilePath) ?: return null
    if (currentScreen != AppScreen.Workspace) return null
    if (!modelLoaded) return null
    if (currentModelFormatName != ImportedModelFormat.Stl.name) return null
    if (!shouldPrepareWorkspaceMesh(
            selectedObject = selectedObject,
            currentPreparedMeshPresent = currentPreparedMeshPresent,
            currentViewerPreparationError = currentViewerPreparationError,
            inProgressTargetKey = inProgressTargetKey,
            targetKey = targetKey
        )
    ) {
        return null
    }
    return ModelLoaderWorkspacePreparationRequest(
        selectedObject = selectedObject,
        modelFilePath = modelFilePath,
        targetKey = targetKey,
        importTiming = selectedObject?.importTiming ?: currentImportTiming
    )
}

internal fun workspacePreparationTargetStillCurrent(
    targetObjectId: Long?,
    selectedPlateObjectId: Long?,
    currentModelFilePath: String?,
    modelFilePath: String
): Boolean =
    if (targetObjectId == null) {
        currentModelFilePath == modelFilePath
    } else {
        selectedPlateObjectId == targetObjectId
    }

internal fun preparedLegacyState(
    result: WorkspacePreparationResult,
    currentModelBounds: MeshBounds?
): ModelLoaderPreparedLegacyState =
    ModelLoaderPreparedLegacyState(
        preparedMesh = result.preparedMesh,
        modelBounds = result.preparedMesh?.bounds ?: currentModelBounds,
        viewerPreparationError = result.viewerPreparationError,
        workspacePreparationTiming = result.timing
    )

internal fun planWorkspacePreparationApplication(
    request: ModelLoaderWorkspacePreparationRequest,
    result: WorkspacePreparationResult,
    selectedPlateObjectId: Long?,
    currentModelFilePath: String?,
    currentModelBounds: MeshBounds?
): ModelLoaderWorkspacePreparationApplication {
    val targetStillCurrent = workspacePreparationTargetStillCurrent(
        targetObjectId = request.selectedObjectId,
        selectedPlateObjectId = selectedPlateObjectId,
        currentModelFilePath = currentModelFilePath,
        modelFilePath = request.modelFilePath
    )
    return ModelLoaderWorkspacePreparationApplication(
        targetStillCurrent = targetStillCurrent,
        legacyState = if (targetStillCurrent && currentModelFilePath == request.modelFilePath) {
            preparedLegacyState(result, currentModelBounds)
        } else {
            null
        },
        statusMessage = if (targetStillCurrent) {
            workspaceMeshPreparedStatus(request.modelFilePath, request.importTiming, result)
        } else {
            null
        }
    )
}

internal fun clearedWorkspacePreparationTarget(
    currentTargetKey: String?,
    completedTargetKey: String
): String? =
    currentTargetKey.takeUnless { it == completedTargetKey }

internal fun legacyStateForPlateObject(objectOnPlate: PlateObject?): ModelLoaderLegacyModelState =
    ModelLoaderLegacyModelState(
        modelLoaded = objectOnPlate != null,
        modelLabel = objectOnPlate?.label ?: "No model imported",
        modelFilePath = objectOnPlate?.filePath,
        importTiming = objectOnPlate?.importTiming,
        modelBounds = objectOnPlate?.mesh?.bounds ?: objectOnPlate?.bounds,
        modelFormatName = objectOnPlate?.format?.name
    )

internal fun planSelectedObjectSync(objectOnPlate: PlateObject?): ModelLoaderSelectedObjectSyncPlan =
    ModelLoaderSelectedObjectSyncPlan(
        selectedPlateObjectId = objectOnPlate?.id,
        legacyState = legacyStateForPlateObject(objectOnPlate),
        preparedMesh = objectOnPlate?.mesh,
        viewerPreparationError = objectOnPlate?.viewerPreparationError,
        workspacePreparationTiming = objectOnPlate?.workspacePreparationTiming,
        modelTransform = objectOnPlate?.transform
    )

internal fun applyWorkspacePreparationToPlateObject(
    objectOnPlate: PlateObject,
    modelFilePath: String,
    result: WorkspacePreparationResult
): PlateObject =
    if (objectOnPlate.filePath == modelFilePath) {
        objectOnPlate.copy(
            bounds = result.preparedMesh?.bounds ?: objectOnPlate.bounds,
            mesh = result.preparedMesh,
            viewerPreparationError = result.viewerPreparationError,
            workspacePreparationTiming = result.timing
        )
    } else {
        objectOnPlate
    }
