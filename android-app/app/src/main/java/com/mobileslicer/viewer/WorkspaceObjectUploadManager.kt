package com.mobileslicer.viewer

import android.os.SystemClock
import android.util.Log
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max

internal class WorkspaceObjectUploadManager {
    var uploads: List<ModelObjectUpload> = emptyList()
        private set

    var selectedFootprintUpload: TriangleUpload? = null
        private set

    var plateSceneCameraInitialized = false

    fun uploadObjects(
        objects: List<ViewerPlateObject>,
        bed: PrinterBedSpec,
        camera: ViewerCamera
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        if (objects.isEmpty()) {
            clearObjectUploads()
            clearSelectedFootprint()
            plateSceneCameraInitialized = false
            return
        }

        val previousUploadsById = uploads.associateBy { it.id }
        val reusableUploadsByMesh = IdentityHashMap<StlMesh, TriangleUpload>()
        uploads.forEach { upload ->
            reusableUploadsByMesh.putIfAbsent(upload.mesh, upload.upload)
        }
        val nextUploadsByMesh = IdentityHashMap<StlMesh, TriangleUpload>()
        val retainedUploads = Collections.newSetFromMap(IdentityHashMap<TriangleUpload, Boolean>())
        val nextUploads = mutableListOf<ModelObjectUpload>()
        var maxSize = 40f
        var reusedUploadCount = 0
        var createdUploadCount = 0
        for (plateObject in objects) {
            val mesh = plateObject.mesh
            if (mesh.triangleCount <= 0) continue
            val reuse = reusableWorkspaceObjectUpload(
                plateObject = plateObject,
                previousUploadsById = previousUploadsById,
                nextUploadsByMesh = nextUploadsByMesh,
                reusableUploadsByMesh = reusableUploadsByMesh
            )
            val reusableUpload = reuse.upload
                ?: uploadTriangleMesh(mesh).also { createdUploadCount++ }
            if (reuse.upload != null) {
                reusedUploadCount++
            }
            retainedUploads.add(reusableUpload)
            nextUploadsByMesh[mesh] = reusableUpload
            val placement = buildModelPlacement(mesh, plateObject.transform, bed)
            maxSize = max(maxSize, max(max(placement.sizeX, placement.sizeY), placement.sizeZ))
            nextUploads.add(
                ModelObjectUpload(
                    id = plateObject.id,
                    mesh = mesh,
                    upload = reusableUpload,
                    modelMatrix = placement.matrix,
                    centerX = placement.centerX,
                    centerY = placement.centerY,
                    centerZ = placement.centerZ,
                    radius = modelRadius(placement),
                    sizeX = placement.sizeX,
                    sizeY = placement.sizeY,
                    sizeZ = placement.sizeZ,
                    colorInt = plateObject.colorInt,
                    selected = plateObject.selected
                )
            )
        }
        val deletedUploadCount = deleteUnusedObjectUploads(retainedUploads)
        uploads = nextUploads
        uploadSelectedFootprint(nextUploads.firstOrNull { it.selected })
        val totalMs = SystemClock.elapsedRealtime() - startedAt
        if (totalMs >= 16L || createdUploadCount > 0 || deletedUploadCount > 0) {
            Log.i(
                "MobileSlicerPerf",
                "workspace_object_upload objects=${nextUploads.size} reused=$reusedUploadCount " +
                    "created=$createdUploadCount deleted=$deletedUploadCount ms=$totalMs"
            )
        }

        if (plateSceneCameraInitialized) {
            camera.updatePlateObjectsSceneKeepingView(bed, maxSize)
        } else {
            camera.setPlateObjectsScene(bed, maxSize)
            plateSceneCameraInitialized = true
        }
    }

    fun updateTransforms(objects: List<ViewerPlateObject>, bed: PrinterBedSpec) {
        val objectsById = objects.associateBy { it.id }
        val updatedUploads = uploads.map { upload ->
            val plateObject = objectsById[upload.id] ?: return@map upload
            val placement = buildModelPlacement(plateObject.mesh, plateObject.transform, bed)
            upload.copy(
                mesh = plateObject.mesh,
                modelMatrix = placement.matrix,
                centerX = placement.centerX,
                centerY = placement.centerY,
                centerZ = placement.centerZ,
                radius = modelRadius(placement),
                sizeX = placement.sizeX,
                sizeY = placement.sizeY,
                sizeZ = placement.sizeZ,
                colorInt = plateObject.colorInt,
                selected = plateObject.selected
            )
        }
        uploads = updatedUploads
        uploadSelectedFootprint(updatedUploads.firstOrNull { it.selected })
    }

    fun clear() {
        clearObjectUploads()
        clearSelectedFootprint()
        plateSceneCameraInitialized = false
    }

    private fun clearObjectUploads() {
        deleteUniqueUploads(uploads.map { it.upload })
        uploads = emptyList()
    }

    private fun deleteUnusedObjectUploads(retainedUploads: Set<TriangleUpload>): Int {
        val unusedUploads = uploads
            .map { it.upload }
            .filterNot { retainedUploads.contains(it) }
        return deleteUniqueUploads(unusedUploads)
    }

    private fun deleteUniqueUploads(uploadList: List<TriangleUpload>): Int {
        val deletedUploads = Collections.newSetFromMap(IdentityHashMap<TriangleUpload, Boolean>())
        uploadList.forEach { upload ->
            if (deletedUploads.add(upload)) {
                deleteTriangleUpload(upload)
            }
        }
        return deletedUploads.size
    }

    private fun uploadSelectedFootprint(selected: ModelObjectUpload?) {
        clearSelectedFootprint()
        val geometry = selected?.let(::buildSelectedFootprintGeometry) ?: return
        selectedFootprintUpload = uploadTriangleData(vertices = geometry.vertices, normals = geometry.normals)
    }

    private fun clearSelectedFootprint() {
        selectedFootprintUpload?.let(::deleteTriangleUpload)
        selectedFootprintUpload = null
    }
}

internal data class WorkspaceObjectUploadReuse(
    val upload: TriangleUpload?,
    val source: WorkspaceObjectUploadReuseSource
)

internal enum class WorkspaceObjectUploadReuseSource {
    None,
    SameObject,
    CurrentMesh,
    PreviousMesh
}

internal fun reusableWorkspaceObjectUpload(
    plateObject: ViewerPlateObject,
    previousUploadsById: Map<Long, ModelObjectUpload>,
    nextUploadsByMesh: IdentityHashMap<StlMesh, TriangleUpload>,
    reusableUploadsByMesh: IdentityHashMap<StlMesh, TriangleUpload>
): WorkspaceObjectUploadReuse {
    val mesh = plateObject.mesh
    previousUploadsById[plateObject.id]
        ?.takeIf { it.mesh === mesh }
        ?.upload
        ?.let { upload ->
            return WorkspaceObjectUploadReuse(upload, WorkspaceObjectUploadReuseSource.SameObject)
        }
    nextUploadsByMesh[mesh]?.let { upload ->
        return WorkspaceObjectUploadReuse(upload, WorkspaceObjectUploadReuseSource.CurrentMesh)
    }
    reusableUploadsByMesh[mesh]?.let { upload ->
        return WorkspaceObjectUploadReuse(upload, WorkspaceObjectUploadReuseSource.PreviousMesh)
    }
    return WorkspaceObjectUploadReuse(null, WorkspaceObjectUploadReuseSource.None)
}
