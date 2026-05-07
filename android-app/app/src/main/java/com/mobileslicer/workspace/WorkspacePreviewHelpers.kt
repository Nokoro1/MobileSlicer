package com.mobileslicer.workspace

import com.mobileslicer.viewer.PreviewRangeSuggestion
import com.mobileslicer.viewer.ViewerFailure

internal fun PreviewRangeSuggestion.toPreviewLayerSelection(): PreviewLayerSelection =
    PreviewLayerSelection(
        mode = PreviewLayerMode.Range,
        singleLayer = startLayer,
        rangeStartLayer = startLayer,
        rangeEndLayer = endLayer
    )

internal fun ViewerFailure.isGcodePreviewRangeTooLarge(): Boolean =
    title.contains("G-code preview range too large", ignoreCase = true) ||
        detail.contains("G-code preview is too large", ignoreCase = true) ||
        detail.contains("Vertex limit:", ignoreCase = true)
