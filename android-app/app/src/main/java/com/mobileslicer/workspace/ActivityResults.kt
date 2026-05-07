package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh

internal data class ModelLoadResult(
    val message: String,
    val loaded: Boolean,
    val stagedFilePath: String? = null,
    val format: ImportedModelFormat? = null,
    val loadTiming: ModelImportTiming? = null,
    val bounds: MeshBounds? = null,
    val geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile
)

internal data class WorkspacePreparationResult(
    val preparedMesh: StlMesh? = null,
    val viewerPreparationError: String? = null,
    val timing: WorkspacePreparationTiming? = null
)

internal data class SliceResult(
    val message: String,
    val sliced: Boolean,
    val gcodeFilePath: String? = null,
    val fileName: String? = null,
    val summary: SliceResultSummary? = null,
    val timing: SlicePipelineTiming? = null
)

internal fun modelLoadStatusMessage(
    loaded: Boolean,
    fileName: String,
    timing: ModelImportTiming? = null,
    extraDetail: String? = null
): String = buildString {
    append(if (loaded) "Model loaded successfully" else "Failed to load model")
    append('\n')
    append(fileName)
    timing?.let {
        append('\n')
        append("File staging: ")
        append(formatDurationMs(it.stagingMs))
        append(" • Native load: ")
        append(formatDurationMs(it.nativeLoadMs))
    }
    if (!extraDetail.isNullOrBlank()) {
        append('\n')
        append(extraDetail)
    }
}
