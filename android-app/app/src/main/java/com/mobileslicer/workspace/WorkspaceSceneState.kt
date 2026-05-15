package com.mobileslicer.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintSession
import com.mobileslicer.viewer.ViewerPlateObject

internal data class WorkspaceSceneState(
    val selectedPlateObject: PlateObject?,
    val activePlateLabel: String,
    val filamentSlotsByIndex: Map<Int, PlateFilamentSlot>,
    val paintModeActive: Boolean,
    val paintSession: ViewerPaintSession?,
    val visiblePlateObjects: List<PlateObject>,
    val canDragSelectedPlateObject: Boolean,
    val primeTowerViewerState: PrimeTowerViewerState,
    val viewerPlateObjects: List<ViewerPlateObject>,
    val viewerState: WorkspaceViewerState,
    val loadedMesh: StlMesh?,
    val effectiveModelTransform: ViewerModelTransform,
    val meshSummary: String?,
    val sliceReady: Boolean,
    val transformReady: Boolean,
    val cutPreviewBounds: MeshBounds?
)

@Composable
internal fun rememberWorkspaceSceneState(
    modelFilePath: String?,
    modelFormat: ImportedModelFormat?,
    modelLoaded: Boolean,
    preparedMesh: StlMesh?,
    modelBounds: MeshBounds?,
    viewerPreparationError: String?,
    selectedPrinter: PrinterProfile,
    activeConfiguration: ActiveSlicerConfiguration,
    workspaceMode: WorkspaceMode,
    sliceInProgress: Boolean,
    modelTransform: ViewerModelTransform?,
    workspacePlates: List<WorkspacePlate>,
    activePlateId: Long,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    selectedPlateObjectId: Long?,
    primeTowerPlacementOverride: PrimeTowerPlacementOverride?,
    showTransformSheet: Boolean,
    activeTransformTab: TransformToolTab,
    directMoveUnlocked: Boolean,
    primeTowerSelected: Boolean,
    activePaintMode: ViewerPaintMode?,
    paintBrushShape: ViewerPaintBrushShape,
    paintBrushRadiusMm: Float,
    paintAction: ViewerPaintAction,
    activePaintColorSlotIndex: Int?,
    nativePaintOverlay: ViewerPaintOverlay,
    livePaintOverlay: ViewerPaintOverlay
): WorkspaceSceneState {
    val selectedPlateObject = selectedPlateObjectId?.let { selectedId ->
        plateObjects.firstOrNull { it.id == selectedId }
    } ?: plateObjects.singleOrNull()
    val activePlateIndex = workspacePlates.indexOfFirst { it.id == activePlateId }.takeIf { it >= 0 } ?: 0
    val activePlateLabel = if (workspacePlates.size > 1) {
        "Plate ${activePlateIndex + 1} of ${workspacePlates.size}"
    } else {
        workspacePlates.firstOrNull { it.id == activePlateId }?.label ?: "Plate 1"
    }
    val filamentSlotsByIndex = filamentSlots.associateBy { it.index }
    val paintModeActive = workspaceMode == WorkspaceMode.Paint && activePaintMode != null && selectedPlateObject != null
    val paintSession = if (paintModeActive) {
        ViewerPaintSession(
            selectedObjectId = selectedPlateObject.id,
            mode = activePaintMode ?: ViewerPaintMode.Support,
            brushShape = paintBrushShape,
            brushRadiusMm = effectivePaintBrushRadiusMm(paintBrushRadiusMm),
            action = paintAction,
            activeColorInt = activePaintColorSlotIndex
                ?.let { slotIndex -> filamentSlotsByIndex[slotIndex]?.colorHex }
                ?.let { colorHex -> slotColor(colorHex).toArgb() },
            overlay = nativePaintOverlay.plusReplacing(livePaintOverlay)
        )
    } else {
        null
    }
    val visiblePlateObjects = if (paintModeActive) {
        plateObjects.filter { it.id == selectedPlateObject.id }
    } else {
        plateObjects
    }
    val canDragSelectedPlateObject =
        workspaceMode == WorkspaceMode.Prepare &&
            (directMoveUnlocked || (showTransformSheet && activeTransformTab == TransformToolTab.Move)) &&
            !sliceInProgress &&
            !paintModeActive &&
            selectedPlateObject != null
    val primeTowerVisible = workspaceMode == WorkspaceMode.Prepare && !sliceInProgress && !paintModeActive
    val primeTowerMoveEnabled = directMoveUnlocked || (showTransformSheet && activeTransformTab == TransformToolTab.Move)
    val primeTowerViewerState = rememberPrimeTowerViewerState(
        configuration = activeConfiguration,
        plateObjects = plateObjects,
        filamentSlots = filamentSlots,
        printerBed = selectedPrinter.toBedSpec(),
        override = primeTowerPlacementOverride,
        selected = primeTowerSelected,
        moveEnabled = primeTowerMoveEnabled,
        visible = primeTowerVisible
    )
    fun plateObjectFilamentColor(objectOnPlate: PlateObject): Int? =
        filamentSlotsByIndex[objectOnPlate.filamentSlotIndex]?.colorHex
            ?.let { slotColor(it).toArgb() }
    val modelViewerObjects = visiblePlateObjects.mapNotNull { objectOnPlate ->
        objectOnPlate.mesh?.let { mesh ->
            ViewerPlateObject(
                id = objectOnPlate.id,
                label = objectOnPlate.label,
                mesh = mesh,
                transform = objectOnPlate.transform,
                colorInt = if (paintModeActive && activePaintMode != ViewerPaintMode.Color) {
                    null
                } else {
                    plateObjectFilamentColor(objectOnPlate)
                },
                selected = !primeTowerSelected && objectOnPlate.id == selectedPlateObject?.id,
                movable = canDragSelectedPlateObject && objectOnPlate.id == selectedPlateObject?.id
            )
        }
    }
    val modifierViewerObjects = if (paintModeActive) {
        emptyList()
    } else {
        visiblePlateObjects.flatMap { objectOnPlate ->
            objectOnPlate.modifiers.mapNotNull { modifier ->
                val mesh = modifier.mesh ?: return@mapNotNull null
                if (!modifier.enabled) return@mapNotNull null
                ViewerPlateObject(
                    id = modifierViewerObjectId(objectOnPlate.id, modifier.id),
                    label = modifier.label,
                    mesh = mesh,
                    transform = modifier.transform,
                    colorInt = 0xFF7DB7FF.toInt(),
                    selected = !primeTowerSelected && objectOnPlate.id == selectedPlateObject?.id,
                    movable = canDragSelectedPlateObject && objectOnPlate.id == selectedPlateObject?.id
                )
            }
        }
    }
    val viewerPlateObjects = modelViewerObjects + modifierViewerObjects + listOfNotNull(primeTowerViewerState.viewerObject)
    val viewerState = when {
        modelFormat == ImportedModelFormat.ThreeMf -> WorkspaceViewerState.Unsupported
        !modelLoaded || modelFilePath == null -> WorkspaceViewerState.Empty
        modelFormat != ImportedModelFormat.Stl -> WorkspaceViewerState.Error(
            title = "Unsupported viewer format",
            message = "Viewer format not supported in this build."
        )
        selectedPlateObject?.mesh != null -> WorkspaceViewerState.Loaded(selectedPlateObject.mesh)
        preparedMesh != null -> WorkspaceViewerState.Loaded(preparedMesh)
        !viewerPreparationError.isNullOrBlank() -> WorkspaceViewerState.Error(
            title = if (viewerPreparationError.contains("exact workspace preview", ignoreCase = true)) {
                "Exact STL preview skipped"
            } else {
                "STL parse failed"
            },
            message = viewerPreparationError
        )
        else -> WorkspaceViewerState.Preparing
    }
    val loadedMesh = (viewerState as? WorkspaceViewerState.Loaded)?.mesh
    val effectiveModelTransform = selectedPlateObject?.transform
        ?: modelTransform
        ?: defaultViewerModelTransform(selectedPrinter.toBedSpec())
    return WorkspaceSceneState(
        selectedPlateObject = selectedPlateObject,
        activePlateLabel = activePlateLabel,
        filamentSlotsByIndex = filamentSlotsByIndex,
        paintModeActive = paintModeActive,
        paintSession = paintSession,
        visiblePlateObjects = visiblePlateObjects,
        canDragSelectedPlateObject = canDragSelectedPlateObject,
        primeTowerViewerState = primeTowerViewerState,
        viewerPlateObjects = viewerPlateObjects,
        viewerState = viewerState,
        loadedMesh = loadedMesh,
        effectiveModelTransform = effectiveModelTransform,
        meshSummary = loadedMesh?.let { formatMeshSummary(it, modelFormat) },
        sliceReady = loadedMesh != null || modelBounds != null || selectedPlateObject?.bounds != null,
        transformReady = selectedPlateObject != null || loadedMesh != null || preparedMesh != null || modelBounds != null,
        cutPreviewBounds = selectedPlateObject?.mesh?.bounds ?: selectedPlateObject?.bounds
    )
}
