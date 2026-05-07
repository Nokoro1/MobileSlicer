package com.mobileslicer.storage

import android.content.SharedPreferences
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.toJsonObject
import com.mobileslicer.profiles.toProfileStoreOrDefault
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObjectPaint
import com.mobileslicer.workspace.PlateObjectGeometrySource
import com.mobileslicer.workspace.defaultWorkspacePlateLabel
import com.mobileslicer.workspace.SerializedPaintLayer
import com.mobileslicer.workspace.SerializedPaintTriangle
import com.mobileslicer.workspace.SerializedPaintVolumeLayer
import com.mobileslicer.workspace.SourceMeshFingerprint
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedProject(
    val id: String,
    val name: String,
    val updatedAtEpochMs: Long,
    val profileStore: ProfileStore,
    val plateObjects: List<SavedProjectPlateObject>,
    val plates: List<SavedProjectPlate> = emptyList(),
    val filamentSlots: List<PlateFilamentSlot> = emptyList(),
    val flushVolumes: PlateFlushVolumes? = null,
    val thumbnailPath: String? = null,
    val nativeProjectFilePath: String? = null,
    val schemaVersion: Int = 1
)

internal data class SavedProjectPlate(
    val label: String,
    val plateObjects: List<SavedProjectPlateObject>
)

internal data class SavedProjectPlateObject(
    val label: String,
    val filePath: String,
    val nativeSourceKey: String = filePath,
    val filamentSlotIndex: Int = 1,
    val format: ImportedModelFormat,
    val bounds: MeshBounds?,
    val transform: ViewerModelTransform,
    val paint: PlateObjectPaint = PlateObjectPaint(),
    val geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile
)

internal object SavedProjectRepository {
    private const val KEY_SAVED_PROJECTS_JSON = "saved_projects_json"
    private const val MAX_SAVED_PROJECTS = 24

    fun load(preferences: SharedPreferences): List<SavedProject> {
        val stored = preferences.getString(KEY_SAVED_PROJECTS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            List(array.length()) { index -> array.getJSONObject(index).toSavedProject() }
                .sortedByDescending { it.updatedAtEpochMs }
        }.getOrDefault(emptyList())
    }

    fun persist(preferences: SharedPreferences, projects: List<SavedProject>) {
        val trimmed = projects
            .sortedByDescending { it.updatedAtEpochMs }
            .take(MAX_SAVED_PROJECTS)
        preferences.edit()
            .putString(
                KEY_SAVED_PROJECTS_JSON,
                JSONArray().apply {
                    trimmed.forEach { put(it.toJson()) }
                }.toString()
            )
            .apply()
    }
}

private fun SavedProject.toJson(): JSONObject = JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("id", id)
    .put("name", name)
    .put("updatedAtEpochMs", updatedAtEpochMs)
    .put("profileStore", profileStore.toJsonObject())
    .put("filamentSlots", JSONArray().apply {
        filamentSlots.forEach { put(it.toJson()) }
    })
    .also { json ->
        flushVolumes?.let { json.put("flushVolumes", it.toJson()) }
    }
    .put("plateObjects", JSONArray().apply {
        plateObjects.forEach { put(it.toJson()) }
    })
    .put("plates", JSONArray().apply {
        savedProjectPlates().forEach { put(it.toJson()) }
    })
    .also { json ->
        thumbnailPath?.let { json.put("thumbnailPath", it) }
        nativeProjectFilePath?.let { json.put("nativeProjectFilePath", it) }
    }

private fun SavedProject.savedProjectPlates(): List<SavedProjectPlate> =
    plates.ifEmpty {
        listOf(SavedProjectPlate(defaultWorkspacePlateLabel(1), plateObjects))
    }

private fun SavedProjectPlate.toJson(): JSONObject = JSONObject()
    .put("label", label)
    .put("plateObjects", JSONArray().apply {
        plateObjects.forEach { put(it.toJson()) }
    })

private fun SavedProjectPlateObject.toJson(): JSONObject = JSONObject()
    .put("label", label)
    .put("filePath", filePath)
    .put("nativeSourceKey", nativeSourceKey)
    .put("filamentSlotIndex", filamentSlotIndex)
    .put("format", format.name)
    .put("transform", transform.toJson())
    .also { json ->
        if (paint.hasAnyPaintPayload) {
            json.put("paint", paint.toJson())
        }
        json.put("geometrySource", geometrySource.toJson())
    }
    .also { json ->
        bounds?.let { json.put("bounds", it.toJson()) }
    }

private fun JSONObject.toSavedProject(): SavedProject = SavedProject(
    id = getString("id"),
    name = optString("name", "Saved plate"),
    updatedAtEpochMs = optLong("updatedAtEpochMs", 0L),
    profileStore = optJSONObject("profileStore")?.toProfileStoreOrDefault()
        ?: JSONObject().toProfileStoreOrDefault(),
    filamentSlots = optJSONArray("filamentSlots")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index).toPlateFilamentSlot() }
    }.orEmpty(),
    flushVolumes = optJSONObject("flushVolumes")?.toPlateFlushVolumes(),
    plateObjects = optJSONArray("plateObjects")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index).toSavedProjectPlateObject() }
    }.orEmpty(),
    plates = optJSONArray("plates")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index).toSavedProjectPlate() }
    }.orEmpty(),
    thumbnailPath = optString("thumbnailPath", "").takeIf { it.isNotBlank() },
    nativeProjectFilePath = optString("nativeProjectFilePath", "").takeIf { it.isNotBlank() },
    schemaVersion = optInt("schemaVersion", 1)
)

private fun JSONObject.toSavedProjectPlate(): SavedProjectPlate = SavedProjectPlate(
    label = optString("label", defaultWorkspacePlateLabel(1)),
    plateObjects = optJSONArray("plateObjects")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index).toSavedProjectPlateObject() }
    }.orEmpty()
)

private fun JSONObject.toSavedProjectPlateObject(): SavedProjectPlateObject = SavedProjectPlateObject(
    label = optString("label", "Model"),
    filePath = getString("filePath"),
    nativeSourceKey = optString("nativeSourceKey", getString("filePath")),
    filamentSlotIndex = optInt("filamentSlotIndex", 1).coerceAtLeast(1),
    format = runCatching {
        ImportedModelFormat.valueOf(optString("format", ImportedModelFormat.Stl.name))
    }.getOrDefault(ImportedModelFormat.Stl),
    bounds = optJSONObject("bounds")?.toMeshBounds(),
    transform = optJSONObject("transform")?.toViewerModelTransform()
        ?: ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
    paint = optJSONObject("paint")?.toPlateObjectPaint() ?: PlateObjectPaint(),
    geometrySource = optJSONObject("geometrySource")?.toPlateObjectGeometrySource()
        ?: PlateObjectGeometrySource.StagedFile
)

private fun PlateObjectGeometrySource.toJson(): JSONObject = when (this) {
    PlateObjectGeometrySource.StagedFile -> JSONObject().put("type", "stagedFile")
    is PlateObjectGeometrySource.ThreeMfMeshExtract -> JSONObject()
        .put("type", "threeMfMeshExtract")
        .put("originalPath", originalPath)
        .put("extractedStlPath", extractedStlPath)
    is PlateObjectGeometrySource.NativeCutResult -> JSONObject()
        .put("type", "nativeCutResult")
        .put("cutGroupId", cutGroupId)
        .put("sourceMobileObjectId", sourceMobileObjectId)
        .put("role", role)
        .put("resultJson", resultJson)
}

private fun JSONObject.toPlateObjectGeometrySource(): PlateObjectGeometrySource =
    when (optString("type")) {
        "threeMfMeshExtract" -> PlateObjectGeometrySource.ThreeMfMeshExtract(
            originalPath = optString("originalPath"),
            extractedStlPath = optString("extractedStlPath")
        )
        "nativeCutResult" -> PlateObjectGeometrySource.NativeCutResult(
            cutGroupId = optString("cutGroupId"),
            sourceMobileObjectId = optLong("sourceMobileObjectId", 0L),
            role = optString("role", "part"),
            resultJson = optString("resultJson")
        )
        else -> PlateObjectGeometrySource.StagedFile
    }

private fun PlateObjectPaint.toJson(): JSONObject = JSONObject()
    .put("schemaVersion", schemaVersion)
    .also { json ->
        support?.let { json.put("support", it.toJson()) }
        seam?.let { json.put("seam", it.toJson()) }
        color?.let { json.put("color", it.toJson()) }
        fuzzySkin?.let { json.put("fuzzySkin", it.toJson()) }
    }

private fun JSONObject.toPlateObjectPaint(): PlateObjectPaint =
    PlateObjectPaint(
        support = optJSONObject("support")?.toSerializedPaintLayer(PaintMode.Support),
        seam = optJSONObject("seam")?.toSerializedPaintLayer(PaintMode.Seam),
        color = optJSONObject("color")?.toSerializedPaintLayer(PaintMode.Color),
        fuzzySkin = optJSONObject("fuzzySkin")?.toSerializedPaintLayer(PaintMode.FuzzySkin),
        schemaVersion = optInt("schemaVersion", 1)
    )

private fun SerializedPaintLayer.toJson(): JSONObject = JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("mode", mode.name)
    .put("objectSourceKey", objectSourceKey)
    .put("volumeLayers", JSONArray().apply {
        volumeLayers.forEach { put(it.toJson()) }
    })
    .also { json ->
        meshFingerprint?.let { json.put("meshFingerprint", it.toJson()) }
        if (referencedSlotIndexes.isNotEmpty()) {
            json.put("referencedSlotIndexes", JSONArray().apply {
                referencedSlotIndexes.sorted().forEach { put(it) }
            })
        }
        staleReason?.takeIf { it.isNotBlank() }?.let { json.put("staleReason", it) }
    }

private fun JSONObject.toSerializedPaintLayer(fallbackMode: PaintMode): SerializedPaintLayer =
    SerializedPaintLayer(
        mode = runCatching {
            PaintMode.valueOf(optString("mode", fallbackMode.name))
        }.getOrDefault(fallbackMode),
        objectSourceKey = optString("objectSourceKey"),
        meshFingerprint = optJSONObject("meshFingerprint")?.toSourceMeshFingerprint(),
        volumeLayers = optJSONArray("volumeLayers")?.let { array ->
            List(array.length()) { index -> array.getJSONObject(index).toSerializedPaintVolumeLayer() }
        }.orEmpty(),
        referencedSlotIndexes = optJSONArray("referencedSlotIndexes")?.let { array ->
            List(array.length()) { index -> array.optInt(index, 0) }
                .filter { it > 0 }
                .toSet()
        }.orEmpty(),
        staleReason = optString("staleReason", "").takeIf { it.isNotBlank() },
        schemaVersion = optInt("schemaVersion", 1)
    )

private fun SerializedPaintVolumeLayer.toJson(): JSONObject = JSONObject()
    .put("volumeIndex", volumeIndex)
    .put("triangleCount", triangleCount)
    .put("triangles", JSONArray().apply {
        serializedTriangles.forEach { put(it.toJson()) }
    })
    .also { json ->
        nativeMeshFingerprint?.takeIf { it.isNotBlank() }?.let {
            json.put("nativeMeshFingerprint", it)
        }
    }

private fun JSONObject.toSerializedPaintVolumeLayer(): SerializedPaintVolumeLayer =
    SerializedPaintVolumeLayer(
        volumeIndex = optInt("volumeIndex", 0).coerceAtLeast(0),
        triangleCount = optInt("triangleCount", 0).coerceAtLeast(0),
        serializedTriangles = optJSONArray("triangles")?.let { array ->
            List(array.length()) { index -> array.getJSONObject(index).toSerializedPaintTriangle() }
        }.orEmpty(),
        nativeMeshFingerprint = optString("nativeMeshFingerprint", "").takeIf { it.isNotBlank() }
    )

private fun SerializedPaintTriangle.toJson(): JSONObject = JSONObject()
    .put("triangleIndex", triangleIndex)
    .put("hexBits", hexBits)

private fun JSONObject.toSerializedPaintTriangle(): SerializedPaintTriangle =
    SerializedPaintTriangle(
        triangleIndex = optInt("triangleIndex", 0).coerceAtLeast(0),
        hexBits = optString("hexBits")
    )

private fun SourceMeshFingerprint.toJson(): JSONObject = JSONObject()
    .put("fileLengthBytes", fileLengthBytes)
    .put("lastModifiedEpochMs", lastModifiedEpochMs)
    .put("sampleSha256", sampleSha256)
    .also { json ->
        triangleCount?.let { json.put("triangleCount", it) }
        bounds?.let { json.put("bounds", it.toJson()) }
    }

private fun JSONObject.toSourceMeshFingerprint(): SourceMeshFingerprint =
    SourceMeshFingerprint(
        fileLengthBytes = optLong("fileLengthBytes", 0L),
        lastModifiedEpochMs = optLong("lastModifiedEpochMs", 0L),
        triangleCount = optInt("triangleCount", -1).takeIf { it >= 0 },
        bounds = optJSONObject("bounds")?.toMeshBounds(),
        sampleSha256 = optString("sampleSha256")
    )

private fun PlateFilamentSlot.toJson(): JSONObject = JSONObject()
    .put("index", index)
    .put("filamentProfileId", filamentProfileId)
    .put("label", label)
    .put("materialType", materialType)
    .put("colorHex", colorHex)
    .also { json ->
        physicalNozzleIndex?.let { json.put("physicalNozzleIndex", it) }
    }

private fun JSONObject.toPlateFilamentSlot(): PlateFilamentSlot = PlateFilamentSlot(
    index = optInt("index", 1).coerceAtLeast(1),
    filamentProfileId = optString("filamentProfileId"),
    label = optString("label", "Filament"),
    materialType = optString("materialType"),
    colorHex = optString("colorHex", "#8FC1FF"),
    physicalNozzleIndex = optInt("physicalNozzleIndex", 0).takeIf { it > 0 }
)

private fun PlateFlushVolumes.toJson(): JSONObject = JSONObject()
    .put("slotCount", slotCount)
    .put("multipliers", JSONArray().apply {
        multipliers.forEach { put(it) }
    })
    .put("matrix", JSONArray().apply {
        matrix.forEach { put(it) }
    })

private fun JSONObject.toPlateFlushVolumes(): PlateFlushVolumes =
    PlateFlushVolumes(
        slotCount = optInt("slotCount", 1).coerceAtLeast(1),
        multipliers = optJSONArray("multipliers")?.let { array ->
            List(array.length()) { index -> array.optDouble(index, 0.3) }
        }.orEmpty(),
        matrix = optJSONArray("matrix")?.let { array ->
            List(array.length()) { index -> array.optDouble(index, 0.0) }
        }.orEmpty()
    ).normalized()

private fun MeshBounds.toJson(): JSONObject = JSONObject()
    .put("minX", minX)
    .put("minY", minY)
    .put("minZ", minZ)
    .put("maxX", maxX)
    .put("maxY", maxY)
    .put("maxZ", maxZ)

private fun JSONObject.toMeshBounds(): MeshBounds = MeshBounds(
    minX = optDouble("minX", 0.0).toFloat(),
    minY = optDouble("minY", 0.0).toFloat(),
    minZ = optDouble("minZ", 0.0).toFloat(),
    maxX = optDouble("maxX", 0.0).toFloat(),
    maxY = optDouble("maxY", 0.0).toFloat(),
    maxZ = optDouble("maxZ", 0.0).toFloat()
)

private fun ViewerModelTransform.toJson(): JSONObject = JSONObject()
    .put("centerXmm", centerXmm)
    .put("centerYmm", centerYmm)
    .put("rotationXDegrees", rotationXDegrees)
    .put("rotationYDegrees", rotationYDegrees)
    .put("rotationZDegrees", rotationZDegrees)
    .put("uniformScale", uniformScale)
    .also { json ->
        orientationMatrix?.takeIf { values -> values.size == 9 && values.all { it.isFinite() } }?.let { matrix ->
            json.put("orientationMatrix", JSONArray().apply {
                matrix.forEach { put(it) }
            })
        }
    }

private fun JSONObject.toViewerModelTransform(): ViewerModelTransform = ViewerModelTransform(
    centerXmm = optDouble("centerXmm", 0.0).toFloat(),
    centerYmm = optDouble("centerYmm", 0.0).toFloat(),
    rotationXDegrees = optDouble("rotationXDegrees", 0.0).toFloat(),
    rotationYDegrees = optDouble("rotationYDegrees", 0.0).toFloat(),
    rotationZDegrees = optDouble("rotationZDegrees", 0.0).toFloat(),
    uniformScale = optDouble("uniformScale", 1.0).toFloat(),
    orientationMatrix = optJSONArray("orientationMatrix")?.let { array ->
        List(array.length()) { index -> array.optDouble(index, Double.NaN).toFloat() }
            .takeIf { values -> values.size == 9 && values.all { it.isFinite() } }
    }
)
