package com.mobileslicer

import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectPaint
import com.mobileslicer.workspace.commitNativePaintPayloadToPlateObjects
import com.mobileslicer.workspace.invalidatingColorForRemovedSlot
import com.mobileslicer.workspace.nativePaintPayloadJson

internal typealias ColorSlotPayloadRemapper = (payloadJson: String, oldSlotToNewSlot: IntArray) -> String?

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

internal data class ModelLoaderNativePaintCommitMutation(
    val objects: List<PlateObject>,
    val committedObject: PlateObject?,
    val currentSavedProjectId: String?,
    val changed: Boolean
)

internal fun applyNativePaintPayloadCommit(
    objects: List<PlateObject>,
    currentSavedProjectId: String?,
    objectId: Long,
    mode: PaintMode,
    payloadJson: String
): ModelLoaderNativePaintCommitMutation {
    val result = commitNativePaintPayloadToPlateObjects(
        objects = objects,
        objectId = objectId,
        mode = mode,
        payloadJson = payloadJson
    )
    return ModelLoaderNativePaintCommitMutation(
        objects = result.objects,
        committedObject = result.committedObject,
        currentSavedProjectId = currentSavedProjectId,
        changed = result.changed
    )
}

internal fun autoOrientPlateObjectsUnavailableStatus(): String = "Auto-orient unavailable\nNo objects on plate"

internal fun nativeAutoOrientPlateObjectsFailureStatus(details: String?): String {
    val reason = details?.trim().orEmpty()
    return if (reason.isEmpty()) {
        "Auto-orient failed\nMobileSlicer could not find a better orientation."
    } else {
        "Auto-orient failed\n$reason"
    }
}

internal fun autoOrientPlateObjectsStatus(result: PlateAutoOrientResult): String =
    if (result.changedCount == 0) {
        if (result.selectedOnly) {
            "Object already oriented\n1 checked"
        } else {
            "Objects already oriented\n${result.targetCount} checked"
        }
    } else {
        if (result.selectedOnly) {
            "Object auto-oriented\n1 changed"
        } else {
            "Objects auto-oriented\n${result.changedCount} changed"
        }
    }

internal fun autoArrangePlateObjectsFailureStatus(
    objectCount: Int,
    bed: PrinterBedSpec
): String =
    "Objects don't fit on current plate\nDelete some or add objects to other plates"

internal fun nativeAutoArrangePlateObjectsFailureStatus(details: String?): String {
    val reason = details?.trim().orEmpty()
    return if (reason.isEmpty()) {
        "Auto-arrange failed\nMobileSlicer could not find a valid plate layout."
    } else {
        "Auto-arrange failed\n$reason"
    }
}

internal fun autoArrangePlateObjectsStatus(
    objectCount: Int,
    result: PlateAutoArrangeResult
): String =
    if (result.usesMultipleBeds) {
        "Objects arranged\n$objectCount across ${result.arrangedPlateCount} plates"
    } else if (result.reservedPrimeTowerSpace) {
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
    slotIndex: Int,
    colorSlotPayloadRemapper: ColorSlotPayloadRemapper? = null
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
    val oldSlotToNewSlot = slots
        .map { it.index }
        .maxOrNull()
        ?.let { maxSlot ->
            IntArray(maxSlot) { oldSlotZeroBased ->
                remap[oldSlotZeroBased + 1] ?: 0
            }
        }
        ?: IntArray(0)
    val nextSlots = slots
        .filterNot { it.index == slotIndex }
        .sortedBy { it.index }
        .mapIndexed { index, slot -> slot.copy(index = index + 1) }
    val nextObjects = objects.map { objectOnPlate ->
        objectOnPlate.copy(
            filamentSlotIndex = remap[objectOnPlate.filamentSlotIndex] ?: 1,
            paint = objectOnPlate.remappedPaintForRemovedFilamentSlot(
                slotIndex = slotIndex,
                oldSlotToNewSlot = oldSlotToNewSlot,
                colorSlotPayloadRemapper = colorSlotPayloadRemapper
            )
        )
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

private fun PlateObject.remappedPaintForRemovedFilamentSlot(
    slotIndex: Int,
    oldSlotToNewSlot: IntArray,
    colorSlotPayloadRemapper: ColorSlotPayloadRemapper?
): PlateObjectPaint {
    val color = paint.color ?: return paint
    if (color.isEmpty || color.isStale) return paint
    if (slotIndex in color.referencedSlotIndexes || color.referencedSlotIndexes.isEmpty()) {
        return paint.invalidatingColorForRemovedSlot(slotIndex)
    }
    val referencesShift = color.referencedSlotIndexes.any { oldSlot ->
        oldSlot > 0 && oldSlot <= oldSlotToNewSlot.size && oldSlotToNewSlot[oldSlot - 1] != oldSlot
    }
    if (!referencesShift) {
        return paint
    }
    val remapper = colorSlotPayloadRemapper ?: return paint.invalidatingColorForRemovedSlot(slotIndex)
    val payloadJson = nativePaintPayloadJson(listOf(this))
    if (payloadJson.isBlank()) {
        return paint.markColorStale("Color paint slot remap failed because no active replay payload was available.")
    }
    val remappedPayloadJson = remapper(payloadJson, oldSlotToNewSlot)
        ?.takeIf { it.isNotBlank() }
        ?: return paint.markColorStale("Color paint slot remap failed in native code.")
    val remapped = commitNativePaintPayloadToPlateObjects(
        objects = listOf(this),
        objectId = id,
        mode = PaintMode.Color,
        payloadJson = remappedPayloadJson
    ).committedObject?.paint?.color
        ?: return paint.markColorStale("Color paint slot remap returned no color payload.")
    return paint.copy(
        color = remapped.copy(
            objectSourceKey = color.objectSourceKey,
            meshFingerprint = color.meshFingerprint
        )
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
