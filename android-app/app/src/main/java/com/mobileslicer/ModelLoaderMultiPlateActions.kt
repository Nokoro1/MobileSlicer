package com.mobileslicer

import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateProfileState
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

internal data class PendingOrcaMultiPlateArrangement(
    val objectCount: Int,
    val plateCount: Int,
    val mutation: WorkspacePlateMutation
)

private val DefaultWorkspacePlateLabelPattern = Regex("""Plate\s+\d+""")

internal fun normalizeDefaultWorkspacePlateLabels(plates: List<WorkspacePlate>): List<WorkspacePlate> =
    plates.mapIndexed { index, plate ->
        val trimmedLabel = plate.label.trim()
        if (trimmedLabel.isBlank() || DefaultWorkspacePlateLabelPattern.matches(trimmedLabel)) {
            plate.copy(label = defaultWorkspacePlateLabel(index + 1))
        } else {
            plate
        }
    }

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
    nextPlateId: Long,
    profileState: PlateProfileState = PlateProfileState()
): WorkspacePlateMutation {
    val nextLabel = defaultWorkspacePlateLabel(plates.size + 1)
    val nextPlate = WorkspacePlate(id = nextPlateId, label = nextLabel, profileState = profileState)
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
    nextPlateId: Long,
    profileState: PlateProfileState = PlateProfileState()
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
            selectedObjectId = objectToMove.id,
            profileState = profileState
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

internal fun orcaMultiPlateWorkspaceMutation(
    result: PlateAutoArrangeResult,
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    selectedObjectId: Long?,
    nextPlateId: Long,
    profileState: PlateProfileState = PlateProfileState()
): WorkspacePlateMutation? {
    if (!result.usesMultipleBeds || result.objects.isEmpty()) return null
    if (result.bedIndices.size != result.objects.size) return null

    val groupedObjects = result.objects
        .zip(result.bedIndices)
        .groupBy({ (_, bedIndex) -> bedIndex.coerceAtLeast(0) }, { (objectOnPlate, _) -> objectOnPlate })
        .toSortedMap()
        .values
        .filter { it.isNotEmpty() }
    if (groupedObjects.size <= 1) return null

    val basePlates = normalizeDefaultWorkspacePlateLabels(
        plates.ifEmpty {
            listOf(WorkspacePlate(id = activePlateId, label = defaultWorkspacePlateLabel(1)))
        }
    )
    val activeIndex = basePlates.indexOfFirst { it.id == activePlateId }.takeIf { it >= 0 } ?: 0
    val arrangedObjectIds = result.objects.map { it.id }.toSet()
    val nextPlates = basePlates
        .map { plate ->
            plateWithObjectsOnly(
                plate = plate,
                objects = plate.objects.filterNot { it.id in arrangedObjectIds },
                selectedObjectId = plate.selectedObjectId?.takeIf { selectedId ->
                    plate.objects.any { it.id == selectedId && it.id !in arrangedObjectIds }
                }
            )
        }
        .toMutableList()

    var allocatedNextPlateId = nextPlateId
    groupedObjects.forEachIndexed { groupIndex, objectsForBed ->
        val targetIndex = activeIndex + groupIndex
        val targetPlate = if (groupIndex == 0) {
            nextPlates[targetIndex]
        } else {
            val inserted = WorkspacePlate(
                id = allocatedNextPlateId,
                label = defaultWorkspacePlateLabel(targetIndex + 1),
                profileState = profileState
            )
            allocatedNextPlateId += 1L
            nextPlates.add(targetIndex.coerceAtMost(nextPlates.size), inserted)
            inserted
        }
        val targetSelectedId = when {
            groupIndex == 0 && selectedObjectId != null && objectsForBed.any { it.id == selectedObjectId } -> selectedObjectId
            else -> objectsForBed.firstOrNull()?.id
        }
        nextPlates[targetIndex] = plateWithObjectsOnly(
            plate = targetPlate,
            objects = objectsForBed,
            selectedObjectId = targetSelectedId
        )
    }

    val activeObjects = groupedObjects.first()
    val nextSelectedId = selectedObjectId
        ?.takeIf { selectedId -> activeObjects.any { it.id == selectedId } }
        ?: activeObjects.firstOrNull()?.id

    return WorkspacePlateMutation(
        plates = nextPlates,
        activePlateId = nextPlates[activeIndex].id,
        activeObjects = activeObjects,
        selectedObjectId = nextSelectedId,
        nextPlateId = allocatedNextPlateId,
        statusMessage = "Objects arranged across ${groupedObjects.size} plates",
        clearGeneratedPreviewState = true
    )
}
