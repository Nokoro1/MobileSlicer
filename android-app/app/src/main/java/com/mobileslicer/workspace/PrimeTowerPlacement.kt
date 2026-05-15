package com.mobileslicer.workspace

import com.mobileslicer.activePlateFilamentSlots
import com.mobileslicer.actuallyUsedPlateFilamentSlotIndexes
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.NativeConfigKeys
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPlateObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.json.JSONObject

internal const val PrimeTowerVirtualObjectId: Long = Long.MIN_VALUE + 1042L

internal data class PrimeTowerPlacement(
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val depthMm: Float,
    val heightMm: Float,
    val brimWidthMm: Float
) {
    val centerXmm: Float get() = xMm + widthMm * 0.5f
    val centerYmm: Float get() = yMm + depthMm * 0.5f
}

internal data class PrimeTowerPlacementOverride(
    val xMm: Float,
    val yMm: Float
)

internal fun primeTowerPlacementForWorkspace(
    configuration: ActiveSlicerConfiguration,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    printerBed: PrinterBedSpec,
    override: PrimeTowerPlacementOverride?
): PrimeTowerPlacement? {
    if (!configuration.process.enablePrimeTower) return null
    val activeSlots = activePlateFilamentSlots(filamentSlots, plateObjects)
    val actuallyUsedSlots = actuallyUsedPlateFilamentSlotIndexes(activeSlots, plateObjects)
    if (actuallyUsedSlots.size <= 1) return null
    if (plateObjects.isEmpty()) return null

    val process = configuration.process
    val width = process.primeTowerWidthMm.coerceAtLeast(1f)
    val brim = process.primeTowerBrimWidthMm.coerceAtLeast(0f)
    val height = plateObjects
        .mapNotNull { objectOnPlate -> objectOnPlate.bounds?.sizeZ }
        .maxOrNull()
        ?.coerceAtLeast(0.1f)
        ?: 0.1f
    val depth = estimatePrimeTowerDepthMm(
        towerWidthMm = width,
        primeVolumeMm3 = process.primeVolumeMm3,
        layerHeightMm = process.layerHeightMm.coerceAtLeast(0.01f),
        filamentCount = actuallyUsedSlots.size,
        maxHeightMm = height,
        infillGapPercent = process.primeTowerInfillGapPercent
    )
    val margin = 5f + brim
    val maxX = printerBed.widthMm - width - margin
    val maxY = printerBed.depthMm - depth - margin
    if (maxX < margin || maxY < margin) return null
    val requestedX = override?.xMm ?: process.wipeTowerXmm
    val requestedY = override?.yMm ?: process.wipeTowerYmm
    return PrimeTowerPlacement(
        xMm = requestedX.coerceIn(margin, maxX),
        yMm = requestedY.coerceIn(margin, maxY),
        widthMm = width,
        depthMm = depth,
        heightMm = height,
        brimWidthMm = brim
    )
}

internal fun PrimeTowerPlacement.toViewerPlateObject(
    selected: Boolean,
    movable: Boolean,
    colorInt: Int? = null,
    mesh: StlMesh? = null
): ViewerPlateObject =
    ViewerPlateObject(
        id = PrimeTowerVirtualObjectId,
        label = "Prime tower",
        mesh = mesh ?: previewMesh(),
        transform = ViewerModelTransform(centerXmm = centerXmm, centerYmm = centerYmm),
        colorInt = colorInt ?: 0x99D88C3A.toInt(),
        selected = selected,
        movable = movable
    )

internal fun PrimeTowerPlacement.previewMesh(): StlMesh =
    primeTowerPreviewMesh(
        width = widthMm,
        depth = depthMm,
        previewHeightMm = estimatedPrimeTowerPreviewHeightMm(heightMm)
    )

internal fun PrimeTowerPlacement.applyToNativeConfig(configJson: String): String {
    val json = runCatching { JSONObject(configJson) }.getOrElse { return configJson }
    if (!json.optBoolean(NativeConfigKeys.PrimeTower.Enable, false)) return configJson
    json.put(NativeConfigKeys.Process.WipeTowerX, xMm.toDouble())
    json.put(NativeConfigKeys.Process.WipeTowerY, yMm.toDouble())
    return json.toString()
}

private fun estimatePrimeTowerDepthMm(
    towerWidthMm: Float,
    primeVolumeMm3: Float,
    layerHeightMm: Float,
    filamentCount: Int,
    maxHeightMm: Float,
    infillGapPercent: Int
): Float {
    val purgeTransitions = (filamentCount - 1).coerceAtLeast(1)
    val extraSpacing = (infillGapPercent.coerceAtLeast(1) / 100f).coerceAtLeast(0.05f)
    val volumeDepth = primeVolumeMm3.coerceAtLeast(0f) * purgeTransitions / (layerHeightMm * towerWidthMm) * extraSpacing
    return max(minTowerDepthByHeight(maxHeightMm), volumeDepth).coerceAtLeast(1f)
}

private fun minTowerDepthByHeight(heightMm: Float): Float =
    max(10f, sqrt(heightMm.coerceAtLeast(0.1f)) * 2.8f)

private fun estimatedPrimeTowerPreviewHeightMm(heightMm: Float): Float {
    val safeHeight = heightMm.takeIf { it.isFinite() && it > 0f } ?: 4f
    return min(safeHeight, 24f).coerceAtLeast(4f)
}

private fun primeTowerPreviewMesh(width: Float, depth: Float, previewHeightMm: Float): StlMesh {
    val vertices = mutableListOf<Float>()
    val normals = mutableListOf<Float>()
    fun tri(normalX: Float, normalY: Float, normalZ: Float, a: FloatArray, b: FloatArray, c: FloatArray) {
        listOf(a, b, c).forEach { point ->
            vertices.add(point[0])
            vertices.add(point[1])
            vertices.add(point[2])
        }
        repeat(3) {
            normals.add(normalX)
            normals.add(normalY)
            normals.add(normalZ)
        }
    }
    fun quad(normalX: Float, normalY: Float, normalZ: Float, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        tri(normalX, normalY, normalZ, a, b, c)
        tri(normalX, normalY, normalZ, a, c, d)
    }
    val z = previewHeightMm.coerceAtLeast(0.1f)
    val p000 = floatArrayOf(0f, 0f, 0f)
    val p100 = floatArrayOf(width, 0f, 0f)
    val p110 = floatArrayOf(width, depth, 0f)
    val p010 = floatArrayOf(0f, depth, 0f)
    val p001 = floatArrayOf(0f, 0f, z)
    val p101 = floatArrayOf(width, 0f, z)
    val p111 = floatArrayOf(width, depth, z)
    val p011 = floatArrayOf(0f, depth, z)
    quad(0f, 0f, -1f, p000, p010, p110, p100)
    quad(0f, 0f, 1f, p001, p101, p111, p011)
    quad(0f, -1f, 0f, p000, p100, p101, p001)
    quad(1f, 0f, 0f, p100, p110, p111, p101)
    quad(0f, 1f, 0f, p110, p010, p011, p111)
    quad(-1f, 0f, 0f, p010, p000, p001, p011)
    return StlMesh(
        vertices = vertices.toFloatArray(),
        normals = normals.toFloatArray(),
        triangleCount = vertices.size / 9,
        bounds = MeshBounds(0f, 0f, 0f, width, depth, z)
    )
}
