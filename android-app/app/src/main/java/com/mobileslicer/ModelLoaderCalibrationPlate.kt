package com.mobileslicer

import android.content.Context
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.writeOrcaCalibrationModels
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import java.io.File

internal data class ModelLoaderCalibrationPlateBuildResult(
    val objects: List<PlateObject>,
    val nextObjectId: Long
)

internal fun calibrationPlateCreationFailureStatus(error: Throwable): String =
    "Calibration could not be created\n${error.localizedMessage ?: "Unable to write Orca calibration model."}"

private data class PendingCalibrationObject(
    val modelFile: File,
    val mesh: StlMesh?,
    val bounds: MeshBounds
)

internal fun buildModelLoaderCalibrationPlate(
    context: Context,
    calibrationDir: File,
    job: CalibrationJob,
    bed: PrinterBedSpec,
    firstObjectId: Long,
    defaultTransform: (Int) -> ViewerModelTransform
): Result<ModelLoaderCalibrationPlateBuildResult> = runCatching {
    val modelFiles = writeOrcaCalibrationModels(context, calibrationDir, job)
    val pendingCalibrationObjects = modelFiles.mapNotNull { modelFile ->
        val mesh = runCatching { StlMeshParser.parseForDisplay(modelFile).mesh }.getOrNull()
        val bounds = mesh?.bounds ?: runCatching { StlMeshParser.parseBounds(modelFile) }.getOrNull()
        bounds?.let { PendingCalibrationObject(modelFile = modelFile, mesh = mesh, bounds = it) }
    }
    check(pendingCalibrationObjects.size == modelFiles.size && pendingCalibrationObjects.isNotEmpty()) {
        "Generated model bounds could not be read."
    }

    val groupMinX = pendingCalibrationObjects.minOf { it.bounds.minX }
    val groupMaxX = pendingCalibrationObjects.maxOf { it.bounds.maxX }
    val groupMinY = pendingCalibrationObjects.minOf { it.bounds.minY }
    val groupMaxY = pendingCalibrationObjects.maxOf { it.bounds.maxY }
    val groupCenterX = (groupMinX + groupMaxX) * 0.5f
    val groupCenterY = (groupMinY + groupMaxY) * 0.5f
    val groupOffsetX = bed.widthMm * 0.5f - groupCenterX
    val groupOffsetY = bed.depthMm * 0.5f - groupCenterY
    var nextObjectId = firstObjectId
    val calibrationObjects = pendingCalibrationObjects.mapIndexed { index, pending ->
        val transform = if (job.preservesAssetPlacement()) {
            ViewerModelTransform(
                centerXmm = pending.bounds.centerX + groupOffsetX,
                centerYmm = pending.bounds.centerY + groupOffsetY
            )
        } else {
            defaultTransform(index)
        }
        PlateObject(
            id = nextObjectId++,
            label = if (modelFiles.size == 1) {
                job.modelLabel
            } else {
                "${job.modelLabel} ${pending.modelFile.nameWithoutExtension.substringAfterLast('-')}"
            },
            filePath = pending.modelFile.absolutePath,
            filamentSlotIndex = 1,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = pending.bounds,
            mesh = pending.mesh,
            transform = transform
        )
    }
    ModelLoaderCalibrationPlateBuildResult(
        objects = calibrationObjects,
        nextObjectId = nextObjectId
    )
}
