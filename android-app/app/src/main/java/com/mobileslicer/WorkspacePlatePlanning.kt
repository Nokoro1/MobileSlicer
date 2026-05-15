package com.mobileslicer

import com.mobileslicer.profiles.BrimType
import com.mobileslicer.profiles.ProcessProfile
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.PlateObject
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

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
    val centersSummary: String,
    val bedIndices: List<Int> = List(objects.size) { 0 }
) {
    val arrangedPlateCount: Int get() = bedIndices.distinct().size.coerceAtLeast(1)
    val usesMultipleBeds: Boolean get() = bedIndices.any { it != 0 }
}

internal sealed class PlatePlanningOutcome<out T> {
    data class Success<T>(val result: T) : PlatePlanningOutcome<T>()
    data class Failure(val statusMessage: String) : PlatePlanningOutcome<Nothing>()
}

internal fun generatedFootprintClearanceMm(process: ProcessProfile): Float {
    val brimClearance = if (process.brimType == BrimType.NoBrim) 0f else process.brimWidthMm.coerceAtLeast(0f) + 2f
    val skirtClearance = if (process.skirts > 0) process.skirtDistanceMm.coerceAtLeast(0f) + 8f else 0f
    return maxOf(0f, brimClearance, skirtClearance)
}

internal fun generatedFootprintClearanceMm(configJson: String): Float {
    val json = runCatching { JSONObject(configJson) }.getOrNull() ?: return 0f
    val brimType = BrimType.fromConfigValue(json.nativeScalarString("brim_type"))
    val brimWidth = json.nativeScalarFloat("brim_width", fallback = 0f).coerceAtLeast(0f)
    val brimClearance = if (brimType == BrimType.NoBrim || brimWidth <= 0f) {
        0f
    } else {
        brimWidth + 2f
    }
    val skirts = json.nativeScalarInt("skirt_loops", fallback = json.nativeScalarInt("skirts", fallback = 0))
    val skirtDistance = json.nativeScalarFloat("skirt_distance", fallback = 0f).coerceAtLeast(0f)
    val skirtClearance = if (skirts > 0) skirtDistance + 8f else 0f
    return maxOf(0f, brimClearance, skirtClearance)
}

private fun JSONObject.nativeScalarString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val value = opt(key)) {
        is JSONArray -> {
            for (index in 0 until value.length()) {
                val item = value.opt(index)?.toString()?.trim()?.trim('"').orEmpty()
                if (item.isNotBlank()) return item
            }
            ""
        }
        is String -> value.trim().trim('"')
        else -> value?.toString()?.trim()?.trim('"').orEmpty()
    }
}

private fun JSONObject.nativeScalarFloat(key: String, fallback: Float): Float =
    nativeScalarString(key).toFloatOrNull()?.takeIf { it.isFinite() } ?: fallback

private fun JSONObject.nativeScalarInt(key: String, fallback: Int): Int =
    nativeScalarString(key).toIntOrNull()
        ?: nativeScalarString(key).toFloatOrNull()?.toInt()
        ?: fallback

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
