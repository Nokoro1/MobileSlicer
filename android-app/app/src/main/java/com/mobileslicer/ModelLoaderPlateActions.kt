package com.mobileslicer

import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject

internal data class ModelLoaderPlateMutation(
    val slots: List<PlateFilamentSlot>,
    val objects: List<PlateObject>,
    val flushVolumes: PlateFlushVolumes?,
    val changed: Boolean
)

internal data class ModelLoaderObjectDeleteResult(
    val objects: List<PlateObject>,
    val nextSelection: PlateObject?,
    val statusMessage: String,
    val changed: Boolean
)

internal fun autoOrientPlateObjectsStatus(result: PlateAutoOrientResult): String =
    if (result.changedCount == 0) {
        if (result.selectedOnly) {
            "Object already oriented\nSnapped to nearest 90 degrees"
        } else {
            "Objects already oriented\n${result.targetCount} checked"
        }
    } else {
        if (result.selectedOnly) {
            "Object auto-oriented\nSnapped to nearest 90 degrees"
        } else {
            "Objects auto-oriented\n${result.changedCount} snapped to nearest 90 degrees"
        }
    }

internal fun autoArrangePlateObjectsFailureStatus(
    objectCount: Int,
    bed: PrinterBedSpec
): String =
    "Objects do not fit\n$objectCount on ${bed.widthMm.toInt()} x ${bed.depthMm.toInt()} mm plate"

internal fun autoArrangePlateObjectsStatus(
    objectCount: Int,
    result: PlateAutoArrangeResult
): String =
    if (result.reservedPrimeTowerSpace) {
        "Objects arranged\n$objectCount on plate; prime tower space reserved"
    } else {
        "Objects arranged\n$objectCount on plate"
    }

internal fun clonedPlateObjectStatus(objectCount: Int): String =
    "Object duplicated\n$objectCount on plate"

internal fun addPlateFilamentSlot(
    slots: List<PlateFilamentSlot>,
    objects: List<PlateObject>,
    selectedFilament: FilamentProfile,
    flushVolumes: PlateFlushVolumes?
): ModelLoaderPlateMutation {
    val nextIndex = ((slots.maxOfOrNull { it.index } ?: 0) + 1).coerceAtLeast(1)
    val nextSlots = slots + selectedFilament.toPlateFilamentSlot(index = nextIndex)
    return ModelLoaderPlateMutation(
        slots = nextSlots,
        objects = objects,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = nextSlots,
            existing = flushVolumes,
            regenerateFromColors = true
        ),
        changed = true
    )
}

internal fun updatePlateFilamentSlotColor(
    slots: List<PlateFilamentSlot>,
    objects: List<PlateObject>,
    flushVolumes: PlateFlushVolumes?,
    slotIndex: Int,
    colorHex: String
): ModelLoaderPlateMutation {
    val nextSlots = slots.map { slot ->
        if (slot.index == slotIndex) slot.copy(colorHex = colorHex) else slot
    }
    return ModelLoaderPlateMutation(
        slots = nextSlots,
        objects = objects,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = nextSlots,
            existing = flushVolumes,
            regenerateFromColors = true
        ),
        changed = nextSlots != slots
    )
}

internal fun updatePlateFilamentSlotProfile(
    slots: List<PlateFilamentSlot>,
    objects: List<PlateObject>,
    flushVolumes: PlateFlushVolumes?,
    slotIndex: Int,
    filament: FilamentProfile
): ModelLoaderPlateMutation {
    val nextSlots = slots.map { slot ->
        if (slot.index == slotIndex) {
            filament.toPlateFilamentSlot(index = slotIndex).copy(
                physicalNozzleIndex = slot.physicalNozzleIndex
            )
        } else {
            slot
        }
    }
    return ModelLoaderPlateMutation(
        slots = nextSlots,
        objects = objects,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = nextSlots,
            existing = flushVolumes,
            regenerateFromColors = true
        ),
        changed = nextSlots != slots
    )
}

internal fun updatePlateFilamentSlotNozzle(
    slots: List<PlateFilamentSlot>,
    objects: List<PlateObject>,
    flushVolumes: PlateFlushVolumes?,
    slotIndex: Int,
    physicalNozzleIndex: Int?
): ModelLoaderPlateMutation {
    val nextSlots = slots.map { slot ->
        if (slot.index == slotIndex) slot.copy(physicalNozzleIndex = physicalNozzleIndex) else slot
    }
    return ModelLoaderPlateMutation(
        slots = nextSlots,
        objects = objects,
        flushVolumes = flushVolumes,
        changed = nextSlots != slots
    )
}

internal fun removePlateFilamentSlot(
    slots: List<PlateFilamentSlot>,
    objects: List<PlateObject>,
    flushVolumes: PlateFlushVolumes?,
    slotIndex: Int
): ModelLoaderPlateMutation {
    if (slotIndex <= 1 || slots.size <= 1) {
        return ModelLoaderPlateMutation(slots = slots, objects = objects, flushVolumes = flushVolumes, changed = false)
    }
    val remainingOldIndices = slots
        .map { it.index }
        .filterNot { it == slotIndex }
        .sorted()
    val remap = remainingOldIndices
        .mapIndexed { index, oldIndex -> oldIndex to index + 1 }
        .toMap()
    val nextSlots = slots
        .filterNot { it.index == slotIndex }
        .sortedBy { it.index }
        .mapIndexed { index, slot -> slot.copy(index = index + 1) }
    val nextObjects = objects.map { objectOnPlate ->
        objectOnPlate.copy(filamentSlotIndex = remap[objectOnPlate.filamentSlotIndex] ?: 1)
    }
    return ModelLoaderPlateMutation(
        slots = nextSlots,
        objects = nextObjects,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = nextSlots,
            existing = flushVolumes,
            regenerateFromColors = false
        ),
        changed = true
    )
}

internal fun cloneSelectedPlateObject(
    objects: List<PlateObject>,
    selectedPlateObjectId: Long?,
    nextPlateObjectId: Long,
    bed: PrinterBedSpec
): Pair<PlateObject?, Long> {
    val selected = objects.firstOrNull { it.id == selectedPlateObjectId } ?: return null to nextPlateObjectId
    val offset = 12f
    return selected.copy(
        id = nextPlateObjectId,
        transform = selected.transform.copy(
            centerXmm = (selected.transform.centerXmm + offset).coerceIn(0f, bed.widthMm),
            centerYmm = (selected.transform.centerYmm + offset).coerceIn(0f, bed.depthMm)
        )
    ) to nextPlateObjectId + 1L
}

internal fun deleteSelectedPlateObject(
    objects: List<PlateObject>,
    selectedPlateObjectId: Long?
): ModelLoaderObjectDeleteResult {
    val index = objects.indexOfFirst { it.id == selectedPlateObjectId }
    if (index < 0) {
        return ModelLoaderObjectDeleteResult(
            objects = objects,
            nextSelection = objects.firstOrNull { it.id == selectedPlateObjectId },
            statusMessage = "",
            changed = false
        )
    }
    val nextObjects = objects.toMutableList().apply { removeAt(index) }
    val nextSelection = nextObjects.getOrNull(index) ?: nextObjects.getOrNull(index - 1)
    return ModelLoaderObjectDeleteResult(
        objects = nextObjects,
        nextSelection = nextSelection,
        statusMessage = if (nextSelection == null) {
            "No model loaded"
        } else {
            "Object removed\n${nextObjects.size} on plate"
        },
        changed = true
    )
}
