package com.mobileslicer

import android.graphics.Bitmap
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectPlate
import com.mobileslicer.storage.SavedProjectPlateObject
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectGeometrySource
import com.mobileslicer.workspace.WorkspacePlate
import com.mobileslicer.workspace.computeSourceMeshFingerprint
import com.mobileslicer.workspace.defaultWorkspacePlateLabel
import com.mobileslicer.workspace.defaultViewerModelTransform
import com.mobileslicer.workspace.rebasedForSource
import com.mobileslicer.workspace.validatedAgainst
import java.io.File
import java.util.Locale
import java.util.UUID

private const val MaxSavedProjects = 24

internal data class ModelLoaderSavedProjectOpenResult(
    val plateObjects: List<PlateObject>,
    val plates: List<WorkspacePlate>,
    val filamentSlots: List<PlateFilamentSlot>,
    val flushVolumes: PlateFlushVolumes?,
    val nextPlateObjectId: Long,
    val printerBed: PrinterBedSpec
)

internal data class ModelLoaderSavedProjectsUpdate(
    val projects: List<SavedProject>,
    val currentSavedProjectId: String?
)

internal data class ModelLoaderSavedProjectOpenStartPlan(
    val importInProgress: Boolean,
    val firstVisibleWorkspaceFrameMs: Long?,
    val firstVisiblePreviewFrameMs: Long?,
    val statusMessage: String
)

internal data class ModelLoaderSavedProjectOpenMissingFilesPlan(
    val importInProgress: Boolean,
    val statusMessage: String
)

internal sealed class ModelLoaderSavePlatePromptPlan {
    data class Prompt(val suggestedName: String) : ModelLoaderSavePlatePromptPlan()
    data class Fail(val statusMessage: String) : ModelLoaderSavePlatePromptPlan()
}

internal fun normalizedSavedProjects(projects: List<SavedProject>): List<SavedProject> =
    projects.sortedByDescending { it.updatedAtEpochMs }.take(MaxSavedProjects)

internal fun pruneInactiveSavedProjectDirectories(
    savedProjectRootDir: File,
    activeProjects: List<SavedProject>
) {
    val activeIds = activeProjects.map { it.id }.toSet()
    savedProjectRootDir.listFiles()
        ?.filter { it.isDirectory && it.name !in activeIds }
        ?.forEach { it.deleteRecursively() }
}

internal fun deleteSavedProjectDirectory(
    savedProjectRootDir: File,
    project: SavedProject
) {
    File(savedProjectRootDir, project.id).deleteRecursively()
}

internal fun savedProjectsAfterSave(
    project: SavedProject,
    existingProjects: List<SavedProject>
): ModelLoaderSavedProjectsUpdate =
    ModelLoaderSavedProjectsUpdate(
        projects = normalizedSavedProjects(listOf(project) + existingProjects.filterNot { it.id == project.id }),
        currentSavedProjectId = project.id
    )

internal fun savedProjectsAfterDelete(
    project: SavedProject,
    existingProjects: List<SavedProject>,
    currentSavedProjectId: String?
): ModelLoaderSavedProjectsUpdate =
    ModelLoaderSavedProjectsUpdate(
        projects = normalizedSavedProjects(existingProjects.filterNot { it.id == project.id }),
        currentSavedProjectId = currentSavedProjectId.takeUnless { it == project.id }
    )

internal fun suggestedSavedProjectName(
    plateObjects: List<PlateObject>,
    currentModelLabel: String
): String {
    val firstLabel = plateObjects.firstOrNull()?.label ?: currentModelLabel
    return if (plateObjects.size <= 1) {
        firstLabel
    } else {
        "$firstLabel + ${plateObjects.size - 1}"
    }
}

internal fun planSavePlatePrompt(
    plateObjects: List<PlateObject>,
    currentModelLabel: String
): ModelLoaderSavePlatePromptPlan =
    if (plateObjects.isEmpty()) {
        ModelLoaderSavePlatePromptPlan.Fail(noPlateToSaveStatus())
    } else {
        ModelLoaderSavePlatePromptPlan.Prompt(
            suggestedName = suggestedSavedProjectName(
                plateObjects = plateObjects,
                currentModelLabel = currentModelLabel
            )
        )
    }

internal fun noPlateToSaveStatus(): String = "No plate to save"

internal fun plateSavedStatus(project: SavedProject): String = "Plate saved\n${project.name}"

internal fun savedProjectOpeningStatus(project: SavedProject): String = "Opening saved project\n${project.name}"

internal fun savedProjectOpenMissingFilesStatus(): String =
    "Saved project could not be opened\nModel files are missing."

internal fun planSavedProjectOpenStart(project: SavedProject): ModelLoaderSavedProjectOpenStartPlan =
    ModelLoaderSavedProjectOpenStartPlan(
        importInProgress = true,
        firstVisibleWorkspaceFrameMs = null,
        firstVisiblePreviewFrameMs = null,
        statusMessage = savedProjectOpeningStatus(project)
    )

internal fun planSavedProjectOpenMissingFiles(): ModelLoaderSavedProjectOpenMissingFilesPlan =
    ModelLoaderSavedProjectOpenMissingFilesPlan(
        importInProgress = false,
        statusMessage = savedProjectOpenMissingFilesStatus()
    )

internal fun savedProjectLoadedStatus(project: SavedProject, nativeWarmLoadSucceeded: Boolean): String =
    buildString {
        append("Project loaded\n")
        append(project.name)
        if (!nativeWarmLoadSucceeded) {
            append("\nNative model will reload on first slice.")
        }
    }

internal fun buildSavedProject(
    currentSavedProjectId: String?,
    projectName: String,
    projectNameFallback: String,
    savedProjectRootDir: File,
    profileStore: ProfileStore,
    plateObjects: List<PlateObject>,
    workspacePlates: List<WorkspacePlate> = listOf(
        WorkspacePlate(
            id = 1L,
            label = defaultWorkspacePlateLabel(1),
            objects = plateObjects
        )
    ),
    plateFilamentSlots: List<PlateFilamentSlot>,
    plateFlushVolumes: PlateFlushVolumes?,
    thumbnailBitmap: Bitmap?,
    nativeProjectFilePath: String? = null,
    projectIdOverride: String? = null
): SavedProject {
    val projectId = currentSavedProjectId ?: projectIdOverride ?: "project_${UUID.randomUUID()}"
    val projectDir = File(savedProjectRootDir, projectId)
    val modelDir = File(projectDir, "models")
    modelDir.mkdirs()
    val savedSourceFiles = mutableMapOf<String, File>()
    fun savePlateObjects(objects: List<PlateObject>): List<SavedProjectPlateObject> = objects.map { objectOnPlate ->
        val sourceFile = File(objectOnPlate.filePath)
        val extension = sourceFile.extension.ifBlank { objectOnPlate.format.name.lowercase(Locale.US) }
        val sourceKey = objectOnPlate.nativeSourceKey.ifBlank { objectOnPlate.filePath }
        val targetFile = savedSourceFiles.getOrPut(sourceKey) {
            File(modelDir, "${savedSourceFiles.size + 1}_${safeProjectFileName(objectOnPlate.label)}.$extension")
        }
        if (sourceFile.exists() && sourceFile.absolutePath != targetFile.absolutePath) {
            runCatching { sourceFile.copyTo(targetFile, overwrite = true) }
        }
        val savedFilePath = if (targetFile.exists()) targetFile.absolutePath else objectOnPlate.filePath
        val savedGeometrySource = objectOnPlate.geometrySource.rebasedForSavedProject(
            modelDir = modelDir,
            objectLabel = objectOnPlate.label,
            savedFilePath = savedFilePath
        )
        val savedFingerprint = computeSourceMeshFingerprint(
            file = File(savedFilePath),
            bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds,
            triangleCount = objectOnPlate.workspacePreparationTiming?.sourceTriangleCount
                ?: objectOnPlate.mesh?.triangleCount
        )
        SavedProjectPlateObject(
            label = objectOnPlate.label,
            filePath = savedFilePath,
            nativeSourceKey = sourceKey,
            filamentSlotIndex = objectOnPlate.filamentSlotIndex,
            format = objectOnPlate.format,
            bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds,
            transform = objectOnPlate.transform,
            paint = objectOnPlate.paint.rebasedForSource(sourceKey, savedFingerprint),
            geometrySource = savedGeometrySource
        )
    }
    val normalizedWorkspacePlates = workspacePlates.ifEmpty {
        listOf(WorkspacePlate(id = 1L, label = defaultWorkspacePlateLabel(1), objects = plateObjects))
    }
    val savedPlates = normalizedWorkspacePlates.mapIndexed { index, plate ->
        SavedProjectPlate(
            label = plate.label.ifBlank { defaultWorkspacePlateLabel(index + 1) },
            plateObjects = savePlateObjects(plate.objects)
        )
    }
    val savedObjects = savedPlates.flatMap { it.plateObjects }.ifEmpty { savePlateObjects(plateObjects) }
    return SavedProject(
        id = projectId,
        name = projectName.trim().ifBlank { projectNameFallback },
        updatedAtEpochMs = System.currentTimeMillis(),
        profileStore = profileStore,
        filamentSlots = plateFilamentSlots,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = plateFilamentSlots,
            existing = plateFlushVolumes,
            regenerateFromColors = false
        ),
        plateObjects = savedObjects,
        plates = savedPlates,
        nativeProjectFilePath = nativeProjectFilePath?.takeIf { File(it).exists() },
        thumbnailPath = writeCapturedProjectThumbnail(
            projectDir = projectDir,
            bitmap = thumbnailBitmap
        )
    )
}

private fun PlateObjectGeometrySource.rebasedForSavedProject(
    modelDir: File,
    objectLabel: String,
    savedFilePath: String
): PlateObjectGeometrySource = when (this) {
    PlateObjectGeometrySource.StagedFile,
    is PlateObjectGeometrySource.NativeCutResult -> this
    is PlateObjectGeometrySource.ThreeMfMeshExtract -> {
        val original = File(originalPath)
        val savedOriginal = if (original.exists()) {
            val target = File(modelDir, "${safeProjectFileName(objectLabel)}-source.3mf")
            runCatching { original.copyTo(target, overwrite = true) }
            if (target.exists()) target.absolutePath else originalPath
        } else {
            originalPath
        }
        PlateObjectGeometrySource.ThreeMfMeshExtract(
            originalPath = savedOriginal,
            extractedStlPath = savedFilePath
        )
    }
}

private data class LoadedSavedPlateObjects(
    val objects: List<PlateObject>,
    val nextObjectId: Long
)

private fun loadSavedProjectPlateObjects(
    savedObjects: List<SavedProjectPlateObject>,
    firstObjectId: Long
): LoadedSavedPlateObjects {
    var nextObjectId = firstObjectId
    val objects = savedObjects.mapNotNull { savedObject ->
        val sourceFile = File(savedObject.filePath)
        if (!sourceFile.exists()) {
            null
        } else {
            val objectId = nextObjectId++
            val preparedMesh = runCatching { StlMeshParser.parseForDisplay(sourceFile) }.getOrNull()
            val mesh = preparedMesh?.mesh
            val fingerprint = computeSourceMeshFingerprint(
                file = sourceFile,
                bounds = mesh?.bounds ?: savedObject.bounds,
                triangleCount = preparedMesh?.sourceTriangleCount
            )
            PlateObject(
                id = objectId,
                label = savedObject.label,
                filePath = sourceFile.absolutePath,
                nativeSourceKey = savedObject.nativeSourceKey,
                filamentSlotIndex = savedObject.filamentSlotIndex,
                format = savedObject.format,
                importTiming = null,
                bounds = mesh?.bounds ?: savedObject.bounds,
                mesh = mesh,
                viewerPreparationError = if (mesh == null) "Could not reload saved STL mesh." else null,
                workspacePreparationTiming = null,
                transform = savedObject.transform,
                paint = savedObject.paint.validatedAgainst(fingerprint),
                geometrySource = savedObject.geometrySource
            )
        }
    }
    return LoadedSavedPlateObjects(objects = objects, nextObjectId = nextObjectId)
}

internal fun loadSavedProjectPlateObjects(project: SavedProject): List<PlateObject> =
    loadSavedProjectPlateObjects(project.plateObjects, firstObjectId = 1L).objects

internal fun openSavedProjectState(project: SavedProject): ModelLoaderSavedProjectOpenResult? {
    val savedPlates = project.plates.ifEmpty {
        listOf(SavedProjectPlate(defaultWorkspacePlateLabel(1), project.plateObjects))
    }
    if (savedPlates.all { it.plateObjects.isEmpty() }) return null
    if (savedPlates.any { plate -> plate.plateObjects.any { !File(it.filePath).exists() } }) return null
    var nextObjectId = 1L
    val loadedPlates = savedPlates.mapIndexed { index, savedPlate ->
        val loaded = loadSavedProjectPlateObjects(savedPlate.plateObjects, nextObjectId)
        nextObjectId = loaded.nextObjectId
        WorkspacePlate(
            id = index + 1L,
            label = savedPlate.label.ifBlank { defaultWorkspacePlateLabel(index + 1) },
            objects = loaded.objects,
            selectedObjectId = loaded.objects.firstOrNull()?.id
        )
    }
    if (loadedPlates.sumOf { it.objects.size } != savedPlates.sumOf { it.plateObjects.size }) return null
    val activeObjects = loadedPlates.firstOrNull()?.objects.orEmpty()
    val activeConfiguration = project.profileStore.activeConfiguration()
    val filamentSlots = project.filamentSlots.ifEmpty {
        listOf(activeConfiguration.filament.toPlateFilamentSlot(index = 1))
    }
    return ModelLoaderSavedProjectOpenResult(
        plateObjects = activeObjects,
        plates = loadedPlates,
        filamentSlots = filamentSlots,
        flushVolumes = ensureFlushVolumesForSlots(
            slots = filamentSlots,
            existing = project.flushVolumes,
            regenerateFromColors = project.flushVolumes == null
        ),
        nextPlateObjectId = nextObjectId,
        printerBed = activeConfiguration.printer.toBedSpec()
    )
}

internal fun defaultImportedPlateObjectTransform(
    bed: PrinterBedSpec,
    existingObjectCount: Int
): ViewerModelTransform {
    val slotFractions = listOf(
        0.5f to 0.5f,
        0.36f to 0.5f,
        0.64f to 0.5f,
        0.5f to 0.36f,
        0.5f to 0.64f,
        0.36f to 0.36f,
        0.64f to 0.36f,
        0.36f to 0.64f,
        0.64f to 0.64f
    )
    val slot = slotFractions[existingObjectCount % slotFractions.size]
    return defaultViewerModelTransform(bed).copy(
        centerXmm = bed.widthMm * slot.first,
        centerYmm = bed.depthMm * slot.second
    )
}
