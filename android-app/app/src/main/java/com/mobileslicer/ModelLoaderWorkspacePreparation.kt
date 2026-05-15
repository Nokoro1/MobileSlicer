package com.mobileslicer

import com.mobileslicer.workspace.ModelImportTiming
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.formatDurationMs
import com.mobileslicer.workspace.modelLoadStatusMessage
import java.io.File

internal fun workspacePreparationTargetKey(
    selectedObject: PlateObject?,
    currentModelFilePath: String?
): String? =
    selectedObject?.let { "${it.id}:${it.filePath}" } ?: currentModelFilePath

internal fun shouldPrepareWorkspaceMesh(
    selectedObject: PlateObject?,
    currentPreparedMeshPresent: Boolean,
    currentViewerPreparationError: String?,
    inProgressTargetKey: String?,
    targetKey: String
): Boolean {
    if (selectedObject != null) {
        if (selectedObject.mesh != null || !selectedObject.viewerPreparationError.isNullOrBlank()) return false
    } else if (currentPreparedMeshPresent || !currentViewerPreparationError.isNullOrBlank()) {
        return false
    }
    return inProgressTargetKey != targetKey
}

internal fun workspaceMeshPreparingStatus(
    modelFilePath: String,
    importTiming: ModelImportTiming?
): String =
    buildString {
        append(
            modelLoadStatusMessage(
                loaded = true,
                fileName = displayNameForModelFile(File(modelFilePath)),
                timing = importTiming
            )
        )
        append('\n')
        append("Preparing the workspace preview...")
    }

internal fun workspaceMeshPreparedStatus(
    modelFilePath: String,
    importTiming: ModelImportTiming?,
    result: WorkspacePreparationResult
): String =
    buildString {
        append(
            modelLoadStatusMessage(
                loaded = true,
                fileName = displayNameForModelFile(File(modelFilePath)),
                timing = importTiming
            )
        )
        result.timing?.let {
            append('\n')
            append(if (it.cacheHit) "Preview loaded from cache: " else "Preview prepared: ")
            append(formatDurationMs(it.viewerMeshPrepMs))
        }
        if (!result.viewerPreparationError.isNullOrBlank()) {
            append('\n')
            append("The model loaded, but the workspace preview could not be prepared: ")
            append(result.viewerPreparationError)
        } else {
            append('\n')
            append("Workspace preview ready. Waiting for the first frame.")
        }
    }

internal fun firstVisibleWorkspaceFrameStatus(currentStatus: String, firstFrameMs: Long): String =
    buildString {
        append(currentStatus.lineSequence().joinToString("\n"))
        append('\n')
        append("First visible workspace frame: ")
        append(formatDurationMs(firstFrameMs))
    }
