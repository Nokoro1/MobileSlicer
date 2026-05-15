package com.mobileslicer

import android.content.Context
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.CalibrationType
import com.mobileslicer.calibration.writeOrcaCalibrationModels
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.forEachTriangleVertexOffsets
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import java.io.File

internal data class ModelLoaderCalibrationPlateBuildResult(
    val objects: List<PlateObject>,
    val nextObjectId: Long
)

internal fun calibrationPlateCreationFailureStatus(error: Throwable): String =
    "Calibration could not be created\n${error.localizedMessage ?: "Unable to create the calibration model."}"

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
        val mesh = runCatching {
            calibrationPreviewMesh(job, StlMeshParser.parseForDisplay(modelFile).mesh)
        }.getOrNull()
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

private fun calibrationPreviewMesh(job: CalibrationJob, mesh: StlMesh): StlMesh =
    when (job.type) {
        CalibrationType.TemperatureTower -> {
            val start = job.options.startValue.toDoubleOrNull() ?: 230.0
            val end = job.options.endValue.toDoubleOrNull() ?: 190.0
            val minZ = mesh.bounds.minZ + (kotlin.math.round((500.0 - start) / 5.0) * 10.0).toFloat()
            val maxZ = mesh.bounds.minZ + (kotlin.math.round((500.0 - end) / 5.0 + 1.0) * 10.0).toFloat()
            cropPreviewMeshZ(mesh, minZ, maxZ, shiftDownBy = minZ - mesh.bounds.minZ)
        }
        else -> mesh
    }

private fun cropPreviewMeshZ(
    mesh: StlMesh,
    minZ: Float,
    maxZ: Float,
    shiftDownBy: Float = 0f
): StlMesh {
    if (minZ >= maxZ || (minZ <= mesh.bounds.minZ && maxZ >= mesh.bounds.maxZ)) return mesh
    val keptTriangles = ArrayList<Int>(mesh.triangleCount)
    mesh.forEachTriangleVertexOffsets { triangle, a, b, c ->
        val z0 = mesh.vertices[a + 2]
        val z1 = mesh.vertices[b + 2]
        val z2 = mesh.vertices[c + 2]
        if (z0 >= minZ && z0 <= maxZ && z1 >= minZ && z1 <= maxZ && z2 >= minZ && z2 <= maxZ) {
            keptTriangles.add(triangle)
        }
    }
    if (keptTriangles.isEmpty()) return mesh

    val vertices = FloatArray(keptTriangles.size * 9)
    val normals = FloatArray(keptTriangles.size * 9)
    keptTriangles.forEachIndexed { index, sourceTriangle ->
        val targetBase = index * 9
        for (corner in 0..2) {
            val sourceBase = mesh.vertexOffsetForCalibrationCrop(sourceTriangle, corner)
            val targetOffset = targetBase + corner * 3
            mesh.vertices.copyInto(vertices, destinationOffset = targetOffset, startIndex = sourceBase, endIndex = sourceBase + 3)
            normals[targetOffset] = mesh.normals.getOrElse(sourceBase) { 0f }
            normals[targetOffset + 1] = mesh.normals.getOrElse(sourceBase + 1) { 0f }
            normals[targetOffset + 2] = mesh.normals.getOrElse(sourceBase + 2) { 1f }
        }
        if (shiftDownBy != 0f) {
            vertices[targetBase + 2] -= shiftDownBy
            vertices[targetBase + 5] -= shiftDownBy
            vertices[targetBase + 8] -= shiftDownBy
        }
    }
    return StlMesh(
        vertices = vertices,
        normals = normals,
        triangleCount = keptTriangles.size,
        bounds = boundsForVertices(vertices)
    )
}

private fun StlMesh.vertexOffsetForCalibrationCrop(triangleIndex: Int, cornerIndex: Int): Int {
    val index = indices?.get(triangleIndex * 3 + cornerIndex) ?: (triangleIndex * 3 + cornerIndex)
    return index * 3
}

private fun boundsForVertices(vertices: FloatArray): MeshBounds {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    var index = 0
    while (index + 2 < vertices.size) {
        val x = vertices[index]
        val y = vertices[index + 1]
        val z = vertices[index + 2]
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (z < minZ) minZ = z
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
        if (z > maxZ) maxZ = z
        index += 3
    }
    return MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
}
