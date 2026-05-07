package com.mobileslicer.workspace

import com.mobileslicer.profiles.ActiveSlicerConfiguration
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
    val geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile
)

internal data class WorkspacePlate(
    val id: Long,
    val label: String,
    val objects: List<PlateObject> = emptyList(),
    val selectedObjectId: Long? = objects.firstOrNull()?.id,
    val gcodeFilePath: String? = null,
    val sliceSummary: SliceResultSummary? = null,
    val sliceTiming: SlicePipelineTiming? = null,
    val gcodeFileName: String = "mobile_slicer_output.gcode",
    val slicePreviewKey: Long = 0L
) {
    val objectCount: Int get() = objects.size
}

internal fun defaultWorkspacePlateLabel(index: Int): String = "Plate ${index.coerceAtLeast(1)}"

internal sealed class PlateObjectGeometrySource {
    data object StagedFile : PlateObjectGeometrySource()
    data class ThreeMfMeshExtract(
        val originalPath: String,
        val extractedStlPath: String
    ) : PlateObjectGeometrySource()
    data class NativeCutResult(
        val cutGroupId: String,
        val sourceMobileObjectId: Long,
        val role: String,
        val resultJson: String
    ) : PlateObjectGeometrySource()
}

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
    val reducedForDisplay: Boolean = false
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
    Workspace,
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
