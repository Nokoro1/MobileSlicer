package com.mobileslicer.workspace

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform

internal class WorkspaceSessionViewModel : ViewModel() {
    val modelLoaded = mutableStateOf(false)
    val currentModelLabel = mutableStateOf("No model imported")
    val currentModelFilePath = mutableStateOf<String?>(null)
    val currentPreparedMesh = mutableStateOf<StlMesh?>(null)
    val currentModelBounds = mutableStateOf<MeshBounds?>(null)
    val currentViewerPreparationError = mutableStateOf<String?>(null)
    val currentImportTiming = mutableStateOf<ModelImportTiming?>(null)
    val currentWorkspacePreparationTiming = mutableStateOf<WorkspacePreparationTiming?>(null)
    val importStartedAtMs = mutableStateOf<Long?>(null)
    val firstVisibleWorkspaceFrameMs = mutableStateOf<Long?>(null)
    val currentModelFormatName = mutableStateOf<String?>(null)
    val workspaceStatus = mutableStateOf("")
    val currentGcodeFilePath = mutableStateOf<String?>(null)
    val currentSliceSummary = mutableStateOf<SliceResultSummary?>(null)
    val currentSliceTiming = mutableStateOf<SlicePipelineTiming?>(null)
    val currentGcodeFileName = mutableStateOf("mobile_slicer_output.gcode")
    val currentSlicePreviewKey = mutableLongStateOf(0L)
    val currentModelTransform = mutableStateOf<ViewerModelTransform?>(null)
    val workspacePlates = mutableStateListOf(
        WorkspacePlate(
            id = 1L,
            label = defaultWorkspacePlateLabel(1)
        )
    )
    val activePlateId = mutableLongStateOf(1L)
    val nextPlateId = mutableLongStateOf(2L)
    val plateObjects = mutableStateListOf<PlateObject>()
    val plateFilamentSlots = mutableStateListOf<PlateFilamentSlot>()
    val plateFlushVolumes = mutableStateOf<PlateFlushVolumes?>(null)
    val selectedPlateObjectId = mutableStateOf<Long?>(null)
    val nextPlateObjectId = mutableLongStateOf(1L)
    val currentSavedProjectId = mutableStateOf<String?>(null)
    val workspaceMode = mutableStateOf(WorkspaceMode.Prepare)
    val currentScreen = mutableStateOf(AppScreen.Home)
    val profilesReturnScreenName = mutableStateOf(AppScreen.Home.name)

    fun clearGeneratedPreviewState(resetWorkspaceMode: Boolean = true) {
        currentGcodeFilePath.value = null
        currentSliceSummary.value = null
        currentSliceTiming.value = null
        currentGcodeFileName.value = "mobile_slicer_output.gcode"
        currentSlicePreviewKey.longValue = 0L
        if (resetWorkspaceMode) {
            workspaceMode.value = WorkspaceMode.Prepare
        }
    }
}
