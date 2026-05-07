package com.mobileslicer

import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.WorkspacePlate
import com.mobileslicer.workspace.defaultWorkspacePlateLabel

internal data class WorkspacePlateMutation(
    val plates: List<WorkspacePlate>,
    val activePlateId: Long,
    val activeObjects: List<PlateObject>,
    val selectedObjectId: Long?,
    val nextPlateId: Long,
    val statusMessage: String,
    val clearGeneratedPreviewState: Boolean = false,
    val createdPlateId: Long? = null
)

internal fun plateWithObjectsOnly(
    plate: WorkspacePlate,
    objects: List<PlateObject>,
    selectedObjectId: Long?
): WorkspacePlate =
    plate.copy(
        objects = objects,
        selectedObjectId = selectedObjectId,
        gcodeFilePath = null,
        sliceSummary = null,
        sliceTiming = null,
        gcodeFileName = "mobile_slicer_output.gcode",
        slicePreviewKey = 0L
    )

internal fun addWorkspacePlateMutation(
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    activeObjects: List<PlateObject>,
    selectedObjectId: Long?,
    nextPlateId: Long
): WorkspacePlateMutation {
    val nextLabel = defaultWorkspacePlateLabel(plates.size + 1)
    val nextPlate = WorkspacePlate(id = nextPlateId, label = nextLabel)
    return WorkspacePlateMutation(
        plates = plates + nextPlate,
        activePlateId = activePlateId,
        activeObjects = activeObjects,
        selectedObjectId = selectedObjectId,
        nextPlateId = nextPlateId + 1L,
        statusMessage = "$nextLabel added",
        createdPlateId = nextPlateId
    )
}

internal fun duplicateActiveWorkspacePlateMutation(
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    activeObjects: List<PlateObject>,
    selectedObjectId: Long?,
    nextPlateId: Long,
    firstObjectId: Long
): WorkspacePlateMutation? {
    val active = plates.firstOrNull { it.id == activePlateId } ?: return null
    val copiedObjects = active.objects.mapIndexed { index, objectOnPlate ->
        objectOnPlate.copy(id = firstObjectId + index)
    }
    val nextLabel = defaultWorkspacePlateLabel(plates.size + 1)
    val duplicate = WorkspacePlate(
        id = nextPlateId,
        label = nextLabel,
        objects = copiedObjects,
        selectedObjectId = copiedObjects.firstOrNull()?.id
    )
    return WorkspacePlateMutation(
        plates = plates + duplicate,
        activePlateId = activePlateId,
        activeObjects = activeObjects,
        selectedObjectId = selectedObjectId,
        nextPlateId = nextPlateId + 1L,
        statusMessage = "$nextLabel duplicated",
        createdPlateId = nextPlateId
    )
}

internal fun moveWorkspaceObjectToPlateMutation(
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    activeObjects: List<PlateObject>,
    selectedObjectId: Long?,
    objectId: Long,
    targetPlateId: Long,
    nextPlateId: Long
): WorkspacePlateMutation? {
    if (targetPlateId == activePlateId) return null
    val objectToMove = activeObjects.firstOrNull { it.id == objectId } ?: return null
    val activeIndex = plates.indexOfFirst { it.id == activePlateId }
    val targetIndex = plates.indexOfFirst { it.id == targetPlateId }
    if (activeIndex < 0 || targetIndex < 0) return null

    val nextActiveObjects = activeObjects.filterNot { it.id == objectId }
    val nextSelectedId = if (selectedObjectId == objectId) nextActiveObjects.firstOrNull()?.id else selectedObjectId
    val targetPlate = plates[targetIndex]
    val nextPlates = plates.toMutableList()
    nextPlates[activeIndex] = plateWithObjectsOnly(
        plate = plates[activeIndex],
        objects = nextActiveObjects,
        selectedObjectId = nextSelectedId
    )
    nextPlates[targetIndex] = plateWithObjectsOnly(
        plate = targetPlate,
        objects = targetPlate.objects + objectToMove,
        selectedObjectId = targetPlate.selectedObjectId ?: objectToMove.id
    )

    return WorkspacePlateMutation(
        plates = nextPlates,
        activePlateId = activePlateId,
        activeObjects = nextActiveObjects,
        selectedObjectId = nextSelectedId,
        nextPlateId = nextPlateId,
        statusMessage = "${objectToMove.label} moved to ${targetPlate.label}",
        clearGeneratedPreviewState = true
    )
}

internal fun moveWorkspaceObjectToNewPlateMutation(
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    activeObjects: List<PlateObject>,
    selectedObjectId: Long?,
    objectId: Long,
    nextPlateId: Long
): WorkspacePlateMutation? {
    val objectToMove = activeObjects.firstOrNull { it.id == objectId } ?: return null
    val activeIndex = plates.indexOfFirst { it.id == activePlateId }
    if (activeIndex < 0) return null

    val nextActiveObjects = activeObjects.filterNot { it.id == objectId }
    val nextSelectedId = if (selectedObjectId == objectId) nextActiveObjects.firstOrNull()?.id else selectedObjectId
    val nextLabel = defaultWorkspacePlateLabel(plates.size + 1)
    val nextPlates = plates.toMutableList()
    nextPlates[activeIndex] = plateWithObjectsOnly(
        plate = plates[activeIndex],
        objects = nextActiveObjects,
        selectedObjectId = nextSelectedId
    )
    nextPlates.add(
        WorkspacePlate(
            id = nextPlateId,
            label = nextLabel,
            objects = listOf(objectToMove),
            selectedObjectId = objectToMove.id
        )
    )

    return WorkspacePlateMutation(
        plates = nextPlates,
        activePlateId = activePlateId,
        activeObjects = nextActiveObjects,
        selectedObjectId = nextSelectedId,
        nextPlateId = nextPlateId + 1L,
        statusMessage = "${objectToMove.label} moved to $nextLabel",
        clearGeneratedPreviewState = true,
        createdPlateId = nextPlateId
    )
}
