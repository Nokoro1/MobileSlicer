package com.mobileslicer

import com.mobileslicer.profiles.BrimType
import com.mobileslicer.profiles.ProcessProfile
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.PlateObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class GeneratedFootprintRect(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val depth: Float get() = maxY - minY
}

internal data class PlateAutoOrientResult(
    val objects: List<PlateObject>,
    val targetCount: Int,
    val changedCount: Int,
    val selectedOnly: Boolean
)

internal data class PlateAutoArrangeResult(
    val objects: List<PlateObject>,
    val changedCount: Int,
    val reservedPrimeTowerSpace: Boolean,
    val centersSummary: String
)

private data class PlateOrientationCandidate(
    val transform: ViewerModelTransform,
    val width: Float,
    val depth: Float,
    val height: Float,
    val area: Float,
    val maxSide: Float,
    val overflow: Float,
    val bedAlignmentPenalty: Float
)

internal fun generatedFootprintClearanceMm(process: ProcessProfile): Float {
    val brimClearance = if (process.brimType == BrimType.NoBrim) 0f else process.brimWidthMm.coerceAtLeast(0f) + 2f
    val skirtClearance = if (process.skirts > 0) process.skirtDistanceMm.coerceAtLeast(0f) + 8f else 0f
    return maxOf(0f, brimClearance, skirtClearance)
}

internal fun generatedFootprintRect(
    bounds: MeshBounds,
    transform: ViewerModelTransform,
    clearance: Float
): GeneratedFootprintRect {
    val transformed = transformedObjectBoundsOnPlate(bounds, transform)
    return GeneratedFootprintRect(
        minX = transformed.minX - clearance,
        maxX = transformed.maxX + clearance,
        minY = transformed.minY - clearance,
        maxY = transformed.maxY + clearance
    )
}

internal fun unionGeneratedFootprint(rects: List<GeneratedFootprintRect>): GeneratedFootprintRect? {
    if (rects.isEmpty()) return null
    return GeneratedFootprintRect(
        minX = rects.minOf { it.minX },
        maxX = rects.maxOf { it.maxX },
        minY = rects.minOf { it.minY },
        maxY = rects.maxOf { it.maxY }
    )
}

internal fun shiftForGeneratedFootprint(rect: GeneratedFootprintRect, bed: PrinterBedSpec): Pair<Float, Float>? {
    if (!rect.width.isFinite() || !rect.depth.isFinite() || rect.width > bed.widthMm || rect.depth > bed.depthMm) {
        return null
    }
    var dx = 0f
    var dy = 0f
    if (rect.minX < 0f) dx = -rect.minX
    if (rect.maxX + dx > bed.widthMm) dx += bed.widthMm - (rect.maxX + dx)
    if (rect.minY < 0f) dy = -rect.minY
    if (rect.maxY + dy > bed.depthMm) dy += bed.depthMm - (rect.maxY + dy)
    return dx to dy
}

internal fun objectFootprintOnPlate(objectOnPlate: PlateObject): Pair<Float, Float> {
    val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds
    if (bounds != null) {
        val transformed = transformedObjectBoundsOnPlate(bounds, objectOnPlate.transform)
        return (transformed.maxX - transformed.minX).coerceAtLeast(8f) to
            (transformed.maxY - transformed.minY).coerceAtLeast(8f)
    }
    val scale = objectOnPlate.transform.uniformScale.coerceIn(0.05f, 20f)
    return (35f * scale).coerceAtLeast(8f) to (35f * scale).coerceAtLeast(8f)
}

internal fun materiallyDifferentRotation(left: ViewerModelTransform, right: ViewerModelTransform): Boolean =
    abs(left.rotationXDegrees - right.rotationXDegrees) > 0.25f ||
        abs(left.rotationYDegrees - right.rotationYDegrees) > 0.25f ||
        abs(left.rotationZDegrees - right.rotationZDegrees) > 0.25f

internal fun materiallyDifferentPlacement(left: ViewerModelTransform, right: ViewerModelTransform): Boolean =
    abs(left.centerXmm - right.centerXmm) > 0.05f ||
        abs(left.centerYmm - right.centerYmm) > 0.05f ||
        materiallyDifferentRotation(left, right) ||
        abs(left.uniformScale - right.uniformScale) > 0.0005f

internal fun nearestRightAngleDegrees(value: Float): Float {
    var normalized = (Math.round(value / 90f) * 90f) % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized <= -180f) normalized += 360f
    return normalized
}

private fun normalizedRightAngleDegrees(value: Float): Float {
    var normalized = value % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized <= -180f) normalized += 360f
    return nearestRightAngleDegrees(normalized)
}

private fun orientationCandidate(
    bounds: MeshBounds,
    transform: ViewerModelTransform,
    bed: PrinterBedSpec
): PlateOrientationCandidate {
    val transformed = transformedObjectBoundsOnPlate(bounds, transform)
    val width = (transformed.maxX - transformed.minX).coerceAtLeast(0f)
    val depth = (transformed.maxY - transformed.minY).coerceAtLeast(0f)
    val height = (transformed.maxZ - transformed.minZ).coerceAtLeast(0f)
    val overflow = maxOf(0f, width - bed.widthMm) + maxOf(0f, depth - bed.depthMm)
    val bedAlignmentPenalty = if (bed.widthMm >= bed.depthMm) {
        maxOf(0f, depth - width)
    } else {
        maxOf(0f, width - depth)
    }
    return PlateOrientationCandidate(
        transform = transform,
        width = width,
        depth = depth,
        height = height,
        area = width * depth,
        maxSide = maxOf(width, depth),
        overflow = overflow,
        bedAlignmentPenalty = bedAlignmentPenalty
    )
}

private fun bestAutoOrientTransform(
    bounds: MeshBounds,
    transform: ViewerModelTransform,
    bed: PrinterBedSpec
): ViewerModelTransform {
    val snapped = transform.copy(
        rotationXDegrees = nearestRightAngleDegrees(transform.rotationXDegrees),
        rotationYDegrees = nearestRightAngleDegrees(transform.rotationYDegrees),
        rotationZDegrees = nearestRightAngleDegrees(transform.rotationZDegrees)
    )
    val angles = listOf(0f, 90f, -90f, 180f)
    return angles.flatMap { x ->
        angles.flatMap { y ->
            angles.map { z ->
                orientationCandidate(
                    bounds = bounds,
                    transform = snapped.copy(
                        rotationXDegrees = x,
                        rotationYDegrees = y,
                        rotationZDegrees = z
                    ),
                    bed = bed
                )
            }
        }
    }.minWith(
        compareBy<PlateOrientationCandidate> { it.overflow }
            .thenBy { it.height }
            .thenBy { it.bedAlignmentPenalty }
            .thenBy { it.maxSide }
            .thenBy { it.area }
    ).transform.copy(
        centerXmm = transform.centerXmm,
        centerYmm = transform.centerYmm,
        uniformScale = transform.uniformScale
    )
}

private fun bestArrangeTransform(
    bounds: MeshBounds,
    transform: ViewerModelTransform,
    bed: PrinterBedSpec
): PlateOrientationCandidate {
    val snappedZ = nearestRightAngleDegrees(transform.rotationZDegrees)
    val candidates = listOf(
        transform.copy(rotationZDegrees = snappedZ),
        transform.copy(rotationZDegrees = normalizedRightAngleDegrees(snappedZ + 90f))
    ).distinctBy {
        "${it.rotationXDegrees}:${it.rotationYDegrees}:${it.rotationZDegrees}:${it.uniformScale}"
    }
    return candidates.map { candidate ->
        orientationCandidate(bounds, candidate, bed)
    }.minWith(
        compareBy<PlateOrientationCandidate> { it.overflow }
            .thenBy { it.width }
            .thenBy { it.maxSide }
            .thenBy { it.area }
    )
}

internal fun printableVolumePreflightFailure(
    plateObjects: List<PlateObject>,
    fallbackBounds: MeshBounds?,
    fallbackTransform: ViewerModelTransform?,
    fallbackModelPath: String?,
    bed: PrinterBedSpec,
    clearance: Float,
    defaultTransform: ViewerModelTransform
): String? {
    val epsilon = 0.05f

    fun generatedFootprintFailure(rect: GeneratedFootprintRect, label: String): String? {
        val outside = rect.minX < -epsilon ||
            rect.maxX > bed.widthMm + epsilon ||
            rect.minY < -epsilon ||
            rect.maxY > bed.depthMm + epsilon ||
            !rect.minX.isFinite() ||
            !rect.maxX.isFinite() ||
            !rect.minY.isFinite() ||
            !rect.maxY.isFinite()
        if (!outside) return null
        return buildString {
            append("Slice failed\n")
            append("Printable volume exceeded.\n")
            append(label)
            append(" fits as a model, but generated skirt, brim, or purge geometry would extend outside the ")
            append(String.format(Locale.US, "%.0f x %.0f mm", bed.widthMm, bed.depthMm))
            append(" printable area.\n")
            append("Move objects inward, reduce skirt/brim width, or arrange the plate before slicing.")
        }
    }

    fun failureFor(label: String, bounds: MeshBounds, transform: ViewerModelTransform): String? {
        val transformed = transformedObjectBoundsOnPlate(bounds, transform)
        val exceedsMinX = transformed.minX < clearance - epsilon
        val exceedsMaxX = transformed.maxX > bed.widthMm - clearance + epsilon
        val exceedsMinY = transformed.minY < clearance - epsilon
        val exceedsMaxY = transformed.maxY > bed.depthMm - clearance + epsilon
        val exceedsMinZ = transformed.minZ < -epsilon
        val exceedsMaxZ = transformed.maxZ > bed.maxHeightMm + epsilon
        val outside =
            !transformed.minX.isFinite() ||
                !transformed.maxX.isFinite() ||
                !transformed.minY.isFinite() ||
                !transformed.maxY.isFinite() ||
                !transformed.minZ.isFinite() ||
                !transformed.maxZ.isFinite() ||
                exceedsMinX ||
                exceedsMaxX ||
                exceedsMinY ||
                exceedsMaxY ||
                exceedsMinZ ||
                exceedsMaxZ
        if (!outside) return null
        val footprintX = transformed.maxX - transformed.minX
        val footprintY = transformed.maxY - transformed.minY
        val footprintZ = transformed.maxZ - transformed.minZ
        val failedAxes = buildList {
            if (exceedsMinX || exceedsMaxX) add("X")
            if (exceedsMinY || exceedsMaxY) add("Y")
            if (exceedsMinZ || exceedsMaxZ) add("Z")
        }
        return buildString {
            append("Slice failed\n")
            append("Printable volume exceeded.\n")
            append(label)
            append(" is outside the ")
            append(String.format(Locale.US, "%.0f x %.0f x %.0f mm", bed.widthMm, bed.depthMm, bed.maxHeightMm))
            append(" printable volume.")
            if (failedAxes.isNotEmpty()) {
                append(" Failed axis: ")
                append(failedAxes.joinToString("/"))
                append(".")
            }
            append("\nCurrent footprint: ")
            append(String.format(Locale.US, "%.1f x %.1f x %.1f mm.", footprintX, footprintY, footprintZ))
            append("\nMove, scale, or rotate it back onto the build plate before slicing.")
        }
    }

    val plateFootprintUnion = unionGeneratedFootprint(plateObjects.mapNotNull { objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return@mapNotNull null
        generatedFootprintRect(bounds, objectOnPlate.transform, clearance)
    })
    plateFootprintUnion?.let { union ->
        generatedFootprintFailure(union, "Plate objects")?.let { return it }
    }

    plateObjects.firstOrNull { objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return@firstOrNull false
        failureFor(objectOnPlate.label, bounds, objectOnPlate.transform) != null
    }?.let { objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return null
        return failureFor(objectOnPlate.label, bounds, objectOnPlate.transform)
    }

    if (plateObjects.isNotEmpty()) return null

    val singleBounds = fallbackBounds ?: return null
    val singleTransform = fallbackTransform ?: defaultTransform
    val singleLabel = fallbackModelPath?.let(::File)?.name ?: "Model"
    generatedFootprintFailure(generatedFootprintRect(singleBounds, singleTransform, clearance), singleLabel)?.let { return it }
    return failureFor(singleLabel, singleBounds, singleTransform)
}

internal fun planAutoOrientPlateObjects(
    plateObjects: List<PlateObject>,
    selectedPlateObjectId: Long?,
    bed: PrinterBedSpec
): PlateAutoOrientResult? {
    if (plateObjects.isEmpty()) return null
    val selectedObject = selectedPlateObjectId?.let { selectedId ->
        plateObjects.firstOrNull { it.id == selectedId }
    }
    val targetIds = selectedObject?.let { setOf(it.id) } ?: plateObjects.map { it.id }.toSet()
    var changedCount = 0
    val updatedObjects = plateObjects.map { objectOnPlate ->
        if (objectOnPlate.id !in targetIds) return@map objectOnPlate
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds
        val nextTransform = if (bounds != null) {
            bestAutoOrientTransform(bounds, objectOnPlate.transform, bed)
        } else {
            objectOnPlate.transform.copy(
                rotationXDegrees = nearestRightAngleDegrees(objectOnPlate.transform.rotationXDegrees),
                rotationYDegrees = nearestRightAngleDegrees(objectOnPlate.transform.rotationYDegrees),
                rotationZDegrees = nearestRightAngleDegrees(objectOnPlate.transform.rotationZDegrees)
            )
        }
        if (materiallyDifferentRotation(objectOnPlate.transform, nextTransform)) {
            changedCount++
        }
        objectOnPlate.copy(transform = nextTransform)
    }
    return PlateAutoOrientResult(
        objects = updatedObjects,
        targetCount = targetIds.size,
        changedCount = changedCount,
        selectedOnly = selectedObject != null
    )
}

internal fun planAutoArrangePlateObjects(
    plateObjects: List<PlateObject>,
    bed: PrinterBedSpec,
    clearance: Float,
    materialSlotCount: Int,
    singleExtruderMultiMaterial: Boolean,
    primeTowerWidthMm: Float,
    primeTowerBrimWidthMm: Float
): PlateAutoArrangeResult? {
    if (plateObjects.isEmpty()) return null
    val margin = 8f + clearance
    data class ArrangeRect(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)
    data class ArrangeFootprint(
        val objectId: Long,
        val width: Float,
        val depth: Float,
        val area: Float,
        val maxSide: Float,
        val transform: ViewerModelTransform
    )

    fun ArrangeRect.intersects(other: ArrangeRect): Boolean =
        minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

    fun objectRect(center: Pair<Float, Float>, footprint: Pair<Float, Float>): ArrangeRect =
        ArrangeRect(
            minX = center.first - footprint.first * 0.5f - margin,
            maxX = center.first + footprint.first * 0.5f + margin,
            minY = center.second - footprint.second * 0.5f - margin,
            maxY = center.second + footprint.second * 0.5f + margin
        )

    fun ArrangeRect.fitsBed(): Boolean =
        minX >= 0f && maxX <= bed.widthMm && minY >= 0f && maxY <= bed.depthMm

    class ArrangeSpatialIndex(private val cellSize: Float) {
        private val cells = mutableMapOf<Pair<Int, Int>, MutableList<ArrangeRect>>()

        private fun cellRange(min: Float, max: Float): IntRange {
            val start = kotlin.math.floor(min / cellSize).toInt()
            val end = kotlin.math.floor(max / cellSize).toInt()
            return start..end
        }

        fun intersects(rect: ArrangeRect): Boolean {
            for (cellX in cellRange(rect.minX, rect.maxX)) {
                for (cellY in cellRange(rect.minY, rect.maxY)) {
                    val occupants = cells[cellX to cellY] ?: continue
                    if (occupants.any { rect.intersects(it) }) return true
                }
            }
            return false
        }

        fun add(rect: ArrangeRect) {
            for (cellX in cellRange(rect.minX, rect.maxX)) {
                for (cellY in cellRange(rect.minY, rect.maxY)) {
                    cells.getOrPut(cellX to cellY) { mutableListOf() }.add(rect)
                }
            }
        }
    }

    fun estimatedPrimeTowerKeepout(): ArrangeRect? {
        if (materialSlotCount <= 1 || !singleExtruderMultiMaterial) {
            return null
        }
        val towerWidth = primeTowerWidthMm.takeIf { it > 1f } ?: 35f
        val brim = primeTowerBrimWidthMm.coerceAtLeast(0f)
        val towerDepth = (towerWidth * (1.5f + materialSlotCount * 0.35f)).coerceIn(towerWidth, 140f)
        val towerX = (bed.widthMm - towerWidth - brim - margin).coerceAtLeast(margin)
        val towerY = (bed.depthMm - towerDepth - brim - margin).coerceAtLeast(margin)
        return ArrangeRect(
            minX = towerX - brim - margin,
            maxX = towerX + towerWidth + brim + margin,
            minY = towerY - brim - margin,
            maxY = towerY + towerDepth + brim + margin
        )
    }

    val towerKeepout = estimatedPrimeTowerKeepout()
    val footprintList = plateObjects.map { objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds
        val arranged = bounds?.let { bestArrangeTransform(it, objectOnPlate.transform, bed) }
        val width = arranged?.width ?: objectFootprintOnPlate(objectOnPlate).first
        val depth = arranged?.depth ?: objectFootprintOnPlate(objectOnPlate).second
        ArrangeFootprint(
            objectId = objectOnPlate.id,
            width = width,
            depth = depth,
            area = width * depth,
            maxSide = maxOf(width, depth),
            transform = arranged?.transform ?: objectOnPlate.transform
        )
    }
    val footprints = footprintList.associateBy { it.objectId }

    fun footprintPair(objectId: Long): Pair<Float, Float>? =
        footprints[objectId]?.let { it.width to it.depth }

    fun singleObjectPlacement(): LinkedHashMap<Long, Pair<Float, Float>>? {
        if (plateObjects.size != 1) return null
        val objectOnPlate = plateObjects.first()
        val footprint = footprintPair(objectOnPlate.id) ?: return null
        val center = bed.widthMm * 0.5f to bed.depthMm * 0.5f
        val rect = objectRect(center, footprint)
        if (!rect.fitsBed() || (towerKeepout != null && rect.intersects(towerKeepout))) {
            return null
        }
        return linkedMapOf(objectOnPlate.id to center)
    }

    fun centeredClusterPlacement(): LinkedHashMap<Long, Pair<Float, Float>>? {
        val footprintById = footprints
        val orderedObjects = plateObjects.sortedWith(
            compareByDescending<PlateObject> {
                footprintById[it.id]?.area ?: 0f
            }.thenBy { it.id }
        )
        val largestFootprint = footprintList.maxOfOrNull { it.maxSide } ?: 40f
        val radialStep = maxOf(6f, largestFootprint * 0.28f, margin * 0.75f)
        val candidateLimit = maxOf(240, plateObjects.size * 80).coerceAtMost(6_000)
        val maxRadius = maxOf(bed.widthMm, bed.depthMm) * 0.75f
        val centerX = bed.widthMm * 0.5f
        val centerY = bed.depthMm * 0.5f
        val candidates = mutableListOf(centerX to centerY)
        var radius = radialStep
        var ringIndex = 0
        while (radius <= maxRadius + radialStep && candidates.size < candidateLimit) {
            val angleCount = kotlin.math.ceil((2.0 * Math.PI * radius / radialStep)).toInt().coerceAtLeast(8)
            val angleOffset = if (ringIndex % 2 == 0) 0.0 else Math.PI / angleCount.toDouble()
            repeat(angleCount) { angleIndex ->
                if (candidates.size >= candidateLimit) return@repeat
                val angle = angleOffset + 2.0 * Math.PI * angleIndex.toDouble() / angleCount.toDouble()
                candidates += (centerX + cos(angle).toFloat() * radius) to (centerY + sin(angle).toFloat() * radius)
            }
            radius += radialStep
            ringIndex++
        }

        val placements = linkedMapOf<Long, Pair<Float, Float>>()
        val spatialIndex = ArrangeSpatialIndex(
            cellSize = maxOf(16f, largestFootprint * 0.65f, margin * 2f)
        )
        for (objectOnPlate in orderedObjects) {
            val footprint = footprintPair(objectOnPlate.id) ?: return null
            val center = candidates.firstOrNull { candidate ->
                val rect = objectRect(candidate, footprint)
                rect.fitsBed() &&
                    !spatialIndex.intersects(rect) &&
                    (towerKeepout == null || !rect.intersects(towerKeepout))
            } ?: return null
            placements[objectOnPlate.id] = center
            spatialIndex.add(objectRect(center, footprint))
        }
        return linkedMapOf<Long, Pair<Float, Float>>().apply {
            plateObjects.forEach { objectOnPlate ->
                placements[objectOnPlate.id]?.let { this[objectOnPlate.id] = it }
            }
        }
    }

    fun centeredGridPlacement(): LinkedHashMap<Long, Pair<Float, Float>>? {
        for (columns in 1..plateObjects.size) {
            val rows = kotlin.math.ceil(plateObjects.size.toDouble() / columns.toDouble()).toInt().coerceAtLeast(1)
            val cellWidth = bed.widthMm / columns.toFloat()
            val cellDepth = bed.depthMm / rows.toFloat()
            val fits = plateObjects.all { objectOnPlate ->
                val footprint = footprints[objectOnPlate.id] ?: return@all false
                footprint.width + margin * 2f <= cellWidth &&
                    footprint.depth + margin * 2f <= cellDepth
            }
            if (!fits) continue
            return linkedMapOf<Long, Pair<Float, Float>>().apply {
                plateObjects.forEachIndexed { index, fallbackObject ->
                    val column = index % columns
                    val row = index / columns
                    val footprint = footprintPair(fallbackObject.id) ?: return null
                    val center = (cellWidth * (column + 0.5f)) to (cellDepth * (row + 0.5f))
                    val rect = objectRect(center, footprint)
                    if (!rect.fitsBed() || (towerKeepout != null && rect.intersects(towerKeepout))) {
                        return null
                    }
                    this[fallbackObject.id] = center
                }
            }
        }
        return null
    }

    fun shelfPlacement(): LinkedHashMap<Long, Pair<Float, Float>>? {
        var cursorX = margin
        var cursorY = margin
        var rowDepth = 0f
        val placements = linkedMapOf<Long, Pair<Float, Float>>()
        for (objectOnPlate in plateObjects) {
            val (width, depth) = footprintPair(objectOnPlate.id) ?: return null
            if (width + margin * 2f > bed.widthMm || depth + margin * 2f > bed.depthMm) {
                return null
            }
            if (cursorX > margin && cursorX + width + margin > bed.widthMm) {
                cursorX = margin
                cursorY += rowDepth + margin
                rowDepth = 0f
            }
            val currentTowerKeepout = towerKeepout
            if (currentTowerKeepout != null) {
                val candidate = objectRect((cursorX + width * 0.5f) to (cursorY + depth * 0.5f), width to depth)
                if (candidate.intersects(currentTowerKeepout)) {
                    val shiftedX = currentTowerKeepout.maxX + margin
                    if (shiftedX + width + margin <= bed.widthMm) {
                        cursorX = shiftedX
                    } else {
                        cursorX = margin
                        cursorY += rowDepth + margin
                        rowDepth = 0f
                    }
                }
            }
            if (cursorY + depth + margin > bed.depthMm) {
                return null
            }
            placements[objectOnPlate.id] = (cursorX + width * 0.5f) to (cursorY + depth * 0.5f)
            cursorX += width + margin
            rowDepth = kotlin.math.max(rowDepth, depth)
        }
        return placements
    }

    val placements = singleObjectPlacement() ?: centeredClusterPlacement() ?: centeredGridPlacement() ?: shelfPlacement()
        ?: return null
    var changedCount = 0
    val centers = mutableListOf<String>()
    val updatedObjects = plateObjects.map { objectOnPlate ->
        val placement = placements[objectOnPlate.id] ?: return@map objectOnPlate
        val plannedTransform = footprints[objectOnPlate.id]?.transform ?: objectOnPlate.transform
        val nextTransform = objectOnPlate.transform.copy(
            rotationZDegrees = plannedTransform.rotationZDegrees,
            centerXmm = placement.first.coerceIn(0f, bed.widthMm),
            centerYmm = placement.second.coerceIn(0f, bed.depthMm)
        )
        centers += "${objectOnPlate.id}=(${String.format(Locale.US, "%.1f", nextTransform.centerXmm)},${String.format(Locale.US, "%.1f", nextTransform.centerYmm)})"
        if (materiallyDifferentPlacement(objectOnPlate.transform, nextTransform)) {
            changedCount++
        }
        objectOnPlate.copy(transform = nextTransform)
    }
    return PlateAutoArrangeResult(
        objects = updatedObjects,
        changedCount = changedCount,
        reservedPrimeTowerSpace = towerKeepout != null,
        centersSummary = centers.joinToString()
    )
}
