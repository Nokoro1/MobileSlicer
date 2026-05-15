package com.mobileslicer.workspace

import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.ProcessProfile
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import java.util.Locale

internal data class PlateObject(
    val id: Long,
    val label: String,
    val filePath: String,
    val nativeSourceKey: String = filePath,
    val filamentSlotIndex: Int = 1,
    val format: ImportedModelFormat,
    val importTiming: ModelImportTiming?,
    val bounds: MeshBounds? = null,
    val mesh: StlMesh? = null,
    val viewerPreparationError: String? = null,
    val workspacePreparationTiming: WorkspacePreparationTiming? = null,
    val transform: ViewerModelTransform,
    val paint: PlateObjectPaint = PlateObjectPaint(),
    val geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile,
    val processOverride: PlateObjectProcessOverride? = null,
    val modifiers: List<PlateObjectModifierMesh> = emptyList(),
    val attribution: WorkspaceModelAttribution? = null
)

internal data class PlateObjectModifierMesh(
    val id: Long,
    val label: String,
    val filePath: String,
    val bounds: MeshBounds? = null,
    val mesh: StlMesh? = null,
    val transform: ViewerModelTransform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
    val processOverride: PlateObjectProcessOverride = PlateObjectProcessOverride(),
    val enabled: Boolean = true
) {
    val hasProcessOverrides: Boolean
        get() = enabled && processOverride.hasProcessOverrides
}

internal fun modifierViewerObjectId(parentObjectId: Long, modifierId: Long): Long =
    -((parentObjectId.coerceAtLeast(1L) * 1_000_000L) + modifierId.coerceAtLeast(1L))

internal data class PlateObjectProcessOverride(
    val selectedProcessId: String? = null,
    val editedProcessProfile: ProcessProfile? = null
) {
    val hasProcessOverrides: Boolean
        get() = editedProcessProfile != null

    fun selectedProcessIdOr(defaultProcessId: String): String =
        selectedProcessId?.takeIf { it.isNotBlank() }
            ?: editedProcessProfile?.id?.takeIf { it.isNotBlank() }
            ?: defaultProcessId

    fun effectiveProcess(defaultProcess: ProcessProfile): ProcessProfile =
        editedProcessProfile?.takeIf { it.id == selectedProcessIdOr(defaultProcess.id) }
            ?: editedProcessProfile
            ?: defaultProcess

    fun withSelectedProcess(process: ProcessProfile): PlateObjectProcessOverride =
        PlateObjectProcessOverride(
            selectedProcessId = process.id,
            editedProcessProfile = null
        )

    fun withEditedProcess(process: ProcessProfile): PlateObjectProcessOverride =
        copy(
            selectedProcessId = process.id,
            editedProcessProfile = process
        )

    val nativeOverridesJson: String
        get() = editedProcessProfile?.orcaProcessOverridesJson
            ?.takeIf { it.isNotBlank() }
            ?: "{}"
}

internal data class WorkspaceModelAttribution(
    val title: String?,
    val author: String?,
    val sourceUrl: String?,
    val licenseName: String?,
    val licenseUrl: String?,
    val changes: String?,
    val rightsBasis: String,
    val policyRevision: String
) {
    fun compactLabel(): String = listOfNotNull(
        licenseName?.takeIf { it.isNotBlank() },
        author?.takeIf { it.isNotBlank() }?.let { "by $it" }
    ).joinToString(" ").ifBlank { "Source metadata stored" }
}

internal data class WorkspacePlate(
    val id: Long,
    val label: String,
    val objects: List<PlateObject> = emptyList(),
    val selectedObjectId: Long? = objects.firstOrNull()?.id,
    val profileState: PlateProfileState = PlateProfileState(),
    val gcodeFilePath: String? = null,
    val sliceSummary: SliceResultSummary? = null,
    val sliceTiming: SlicePipelineTiming? = null,
    val gcodeFileName: String = "mobile_slicer_output.gcode",
    val slicePreviewKey: Long = 0L
) {
    val objectCount: Int get() = objects.size
}

internal data class PlateProfileState(
    val selectedProcessId: String? = null,
    val editedProcessProfile: ProcessProfile? = null
) {
    val hasProcessOverrides: Boolean
        get() = editedProcessProfile != null

    fun selectedProcessIdOr(defaultProcessId: String): String =
        selectedProcessId?.takeIf { it.isNotBlank() } ?: defaultProcessId

    fun effectiveProcess(defaultProcess: ProcessProfile): ProcessProfile =
        editedProcessProfile?.takeIf { it.id == selectedProcessIdOr(defaultProcess.id) }
            ?: defaultProcess

    fun withSelectedProcess(process: ProcessProfile): PlateProfileState =
        PlateProfileState(
            selectedProcessId = process.id,
            editedProcessProfile = null
        )

    fun withEditedProcess(process: ProcessProfile): PlateProfileState =
        copy(
            selectedProcessId = process.id,
            editedProcessProfile = process
        )

    fun resetProcessOverrides(): PlateProfileState =
        copy(editedProcessProfile = null)

    fun transferProcessOverridesTo(process: ProcessProfile): PlateProfileState {
        val edited = editedProcessProfile ?: return withSelectedProcess(process)
        return copy(
            selectedProcessId = process.id,
            editedProcessProfile = edited.withValues(
                "id" to process.id,
                "name" to process.name,
                "subtitle" to process.subtitle,
                "builtIn" to false,
                "profileSource" to process.profileSource,
                "printerProfileId" to process.printerProfileId,
                "printerVariantKey" to process.printerVariantKey,
                "orcaFamily" to process.orcaFamily,
                "orcaProcessPath" to process.orcaProcessPath,
                "orcaRawProcessJson" to process.orcaRawProcessJson,
                "orcaResolvedProcessJson" to process.orcaResolvedProcessJson,
                "orcaResolvedSourceChain" to process.orcaResolvedSourceChain
            )
        )
    }
}

internal fun defaultWorkspacePlateLabel(index: Int): String = "Plate ${index.coerceAtLeast(1)}"

internal sealed class PlateObjectGeometrySource {
    data object StagedFile : PlateObjectGeometrySource()
    data class ThreeMfMeshExtract(
        val originalPath: String,
        val extractedStlPath: String,
        val projectMetadata: ThreeMfProjectMetadata? = null
    ) : PlateObjectGeometrySource()
    data class StepMeshConvert(
        val originalPath: String,
        val convertedStlPath: String,
        val linearDeflection: Double,
        val angleDeflection: Double
    ) : PlateObjectGeometrySource()
    data class NativeCutResult(
        val cutGroupId: String,
        val sourceMobileObjectId: Long,
        val role: String,
        val resultJson: String
    ) : PlateObjectGeometrySource()
}

internal data class ThreeMfProjectMetadata(
    val sourcePath: String,
    val nativeProjectFilePath: String? = null,
    val plateCount: Int? = null,
    val objectCount: Int? = null,
    val plateNames: List<String> = emptyList(),
    val objectNames: List<String> = emptyList(),
    val objectFilamentAssignments: List<ThreeMfObjectFilamentAssignment> = emptyList(),
    val filamentCount: Int? = null,
    val thumbnailEntries: List<String> = emptyList(),
    val configEntries: List<String> = emptyList(),
    val preservedFeatures: List<String> = emptyList(),
    val unsupportedFeatures: List<String> = emptyList()
)

internal data class ThreeMfObjectFilamentAssignment(
    val objectName: String,
    val filamentIndex: Int
)

internal data class PlateFilamentSlot(
    val index: Int,
    val filamentProfileId: String,
    val label: String,
    val materialType: String,
    val colorHex: String,
    val physicalNozzleIndex: Int? = null
)

internal data class PlateFlushVolumes(
    val slotCount: Int,
    val multipliers: List<Double>,
    val matrix: List<Double>
) {
    fun normalized(): PlateFlushVolumes {
        val normalizedSlotCount = slotCount.coerceAtLeast(1)
        val normalizedMultipliers = multipliers.ifEmpty { listOf(0.3) }
        val expectedMatrixSize = normalizedSlotCount * normalizedSlotCount * normalizedMultipliers.size
        val normalizedMatrix = if (matrix.size == expectedMatrixSize) {
            matrix
        } else {
            List(expectedMatrixSize) { index ->
                val slotMatrixSize = normalizedSlotCount * normalizedSlotCount
                val localIndex = index % slotMatrixSize
                val fromIndex = localIndex / normalizedSlotCount
                val toIndex = localIndex % normalizedSlotCount
                if (fromIndex == toIndex) 0.0 else 280.0
            }
        }
        return copy(
            slotCount = normalizedSlotCount,
            multipliers = normalizedMultipliers,
            matrix = normalizedMatrix
        )
    }
}

internal data class ModelImportTiming(
    val stagingMs: Long,
    val nativeLoadMs: Long
)

internal data class WorkspacePreparationTiming(
    val viewerMeshPrepMs: Long,
    val cacheHit: Boolean = false,
    val sourceTriangleCount: Int? = null,
    val displayTriangleCount: Int? = null,
    val renderArrayBytes: Long? = null,
    val cacheRetainedBytes: Long? = null,
    val exactPreviewBudgetBytes: Long? = null
)

internal data class SlicePipelineTiming(
    val modelReloadMs: Long,
    val configMs: Long,
    val nativeSliceMs: Long,
    val writeGcodeMs: Long,
    val summaryMs: Long,
    val totalMs: Long,
    val thumbnailMs: Long = 0L
) {
    fun compactLine(): String = buildString {
        if (modelReloadMs > 0) {
            append("Reload ")
            append(formatDurationMs(modelReloadMs))
            append(" • ")
        }
        append("Slice ")
        append(formatDurationMs(nativeSliceMs))
        append(" • G-code file ")
        append(formatDurationMs(writeGcodeMs))
        append(" • Summary ")
        append(formatDurationMs(summaryMs))
        if (thumbnailMs > 0) {
            append(" • Thumbnail ")
            append(formatDurationMs(thumbnailMs))
        }
        append(" • Total ")
        append(formatDurationMs(totalMs))
    }
}

internal data class SliceResultSummary(
    val byteCount: Int,
    val lineCount: Int,
    val layerChangeCount: Int,
    val observedTypes: List<String>,
    val wallShellTypes: List<String>,
    val estimatedPrintTimeText: String? = null,
    val filamentUsedGrams: Double? = null,
    val previewInfo: PreviewInfoSummary = PreviewInfoSummary(),
    val regressionMetrics: GcodeRegressionMetrics = GcodeRegressionMetrics()
) {
    fun compactMetricsLine(): String = buildString {
        append(formatCount(byteCount))
        append(" bytes")
        append(" • ")
        append(formatCount(lineCount))
        append(" lines")
        append(" • ")
        append(formatCount(layerChangeCount))
        append(" layer changes")
    }

    fun prepareMetricsLine(): String = buildString {
        append("Layers: ")
        append(formatCount(layerChangeCount))
        append(" • Time: ")
        append(estimatedPrintTimeLabel())
        append(" • Filament: ")
        append(filamentUsedLabel())
    }

    fun observedTypesLine(): String =
        if (observedTypes.isEmpty()) {
            "Observed ;TYPE markers: none found in emitted G-code."
        } else {
            "Observed ;TYPE markers: ${observedTypes.joinToString(", ")}"
        }

    fun wallShellTypesLine(): String =
        if (wallShellTypes.isEmpty()) {
            "Wall/shell trust aid: emitted G-code did not expose wall or surface ;TYPE markers on this slice."
        } else {
            "Wall/shell trust aid: emitted G-code includes ${wallShellTypes.joinToString(", ")}."
        }

    fun estimatedPrintTimeLabel(): String = estimatedPrintTimeText ?: "--"

    fun filamentUsedLabel(): String =
        filamentUsedGrams?.let { String.format(Locale.US, "%.2f g", it) } ?: "--"

    fun dialogBody(activeConfiguration: ActiveSlicerConfiguration): String = buildString {
        append("Result details from emitted G-code:\n")
        append(compactMetricsLine())
        append('\n')
        append("Requested process: ")
        append(activeConfiguration.nativeSliceBody())
        append('\n')
        append(observedTypesLine())
        append('\n')
        append(wallShellTypesLine())
        append('\n')
        append("Current screen still shows the imported STL mesh, not these emitted toolpaths.")
    }
}

internal data class GcodeRegressionMetrics(
    val firstLayerExtrusionBounds: GcodeMotionBounds? = null,
    val nozzleTemperaturesC: List<Int> = emptyList(),
    val bedTemperaturesC: List<Int> = emptyList(),
    val fanSpeeds: List<Int> = emptyList(),
    val extrusionFeedratesMmPerMin: List<Double> = emptyList(),
    val accelerationsMmPerSec2: List<Double> = emptyList()
)

internal data class GcodeMotionBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
) {
    val widthMm: Double get() = maxX - minX
    val depthMm: Double get() = maxY - minY
}

internal data class PreviewInfoSummary(
    val lineTypes: List<PreviewLineTypeRow> = emptyList(),
    val filaments: List<PreviewFilamentUsageRow> = emptyList(),
    val totalSeconds: Double? = null,
    val prepareSeconds: Double? = null,
    val modelSeconds: Double? = null,
    val totalCost: Double? = null,
    val filamentChanges: Int = 0,
    val extruderChanges: Int = 0
) {
    val hasRichData: Boolean
        get() = lineTypes.isNotEmpty() || filaments.isNotEmpty()
}

internal data class PreviewLineTypeRow(
    val kind: PreviewPathKind,
    val nativeId: Int,
    val label: String,
    val colorHex: String,
    val timeSeconds: Double,
    val percent: Double,
    val usageMeters: Double,
    val usageGrams: Double,
    val defaultVisible: Boolean
)

internal enum class PreviewPathKind(val nativeKind: Int) {
    Role(0),
    Option(1)
}

internal data class PreviewFilamentUsageRow(
    val slotIndex: Int,
    val label: String,
    val colorHex: String,
    val modelMeters: Double,
    val modelGrams: Double,
    val supportMeters: Double,
    val supportGrams: Double,
    val flushedMeters: Double,
    val flushedGrams: Double,
    val towerMeters: Double,
    val towerGrams: Double,
    val totalMeters: Double,
    val totalGrams: Double,
    val cost: Double
)

internal enum class AppScreen {
    Home,
    ModelSearch,
    Workspace,
    Scanner,
    Profiles,
    Calibrations,
    PrinterBrowser,
    Settings
}

internal enum class WorkspaceMode {
    Prepare,
    Preview,
    Paint
}

internal enum class ImportedModelFormat {
    Stl,
    ThreeMf
}

internal fun formatDurationMs(value: Long): String = "${value} ms"

internal fun formatDurationSecondsTenths(value: Long): String =
    String.format(Locale.US, "%.1f seconds", value / 1000.0)

internal fun formatCount(value: Int): String = String.format(Locale.US, "%,d", value)
