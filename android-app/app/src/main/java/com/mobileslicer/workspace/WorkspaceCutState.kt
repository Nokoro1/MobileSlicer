package com.mobileslicer.workspace

import kotlin.math.abs

internal fun sameCutPlacementSurface(
    left: WorkspaceCutRequest,
    right: WorkspaceCutRequest
): Boolean =
    left.mode == right.mode &&
        left.connectorKind == right.connectorKind &&
        abs(left.heightMm - right.heightMm) < 0.01f &&
        abs(left.rotationXDegrees - right.rotationXDegrees) < 0.01f &&
        abs(left.rotationYDegrees - right.rotationYDegrees) < 0.01f

internal fun mergeCutPreviewRequest(
    existing: WorkspaceCutRequest?,
    next: WorkspaceCutRequest?
): WorkspaceCutRequest? {
    if (next == null) return null
    return if (
        existing != null &&
        existing.connectorPositions.isNotEmpty() &&
        next.connectorKind != WorkspaceCutConnectorKind.None &&
        sameCutPlacementSurface(existing, next)
    ) {
        next.copy(connectorPositions = existing.connectorPositions)
    } else {
        next
    }
}

internal fun mergeSubmittedCutRequest(
    existingPreview: WorkspaceCutRequest?,
    request: WorkspaceCutRequest
): WorkspaceCutRequest {
    val connectorPositions = existingPreview
        ?.takeIf { sameCutPlacementSurface(it, request) }
        ?.connectorPositions
        .orEmpty()
    return request.copy(
        keepAsParts = if (request.connectorKind == WorkspaceCutConnectorKind.None) request.keepAsParts else false,
        connectorPositions = connectorPositions
    )
}

internal fun workspaceCutSession(
    workspaceMode: WorkspaceMode,
    showTransformSheet: Boolean,
    activeTransformTab: TransformToolTab,
    selectedPlateObject: PlateObject?,
    cutPreviewBounds: com.mobileslicer.viewer.MeshBounds?,
    cutPreviewRequest: WorkspaceCutRequest?
): WorkspaceCutSession? {
    val objectOnPlate = selectedPlateObject ?: return null
    val request = cutPreviewRequest ?: return null
    val bounds = cutPreviewBounds ?: return null
    if (
        workspaceMode != WorkspaceMode.Prepare ||
        !showTransformSheet ||
        activeTransformTab != TransformToolTab.Cut
    ) {
        return null
    }
    return WorkspaceCutSession(
        selectedObjectId = objectOnPlate.id,
        axis = request.axis,
        offsetMm = request.heightMm,
        rotationXDegrees = request.rotationXDegrees,
        rotationYDegrees = request.rotationYDegrees,
        bounds = bounds,
        keepUpper = request.keepUpper,
        keepLower = request.keepLower,
        keepAsParts = request.keepAsParts,
        mode = request.mode,
        connectorKind = request.connectorKind,
        connectorStyle = request.connectorStyle,
        connectorShape = request.connectorShape,
        connectorDepthMm = request.connectorDepthMm,
        connectorDepthToleranceMm = request.connectorDepthToleranceMm,
        connectorSizeMm = request.connectorSizeMm,
        connectorSizeToleranceMm = request.connectorSizeToleranceMm,
        connectorRotationDegrees = request.connectorRotationDegrees,
        connectorSnapBulgePercent = request.connectorSnapBulgePercent,
        connectorSnapSpacePercent = request.connectorSnapSpacePercent,
        connectorsEditing = request.connectorsEditing,
        connectorPositions = request.connectorPositions
    )
}
