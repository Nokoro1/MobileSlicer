package com.mobileslicer

import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.workspace.NativeModelTransformInputStride
import com.mobileslicer.workspace.NativeModelTransformOutputStride
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.nativeModelTransformFromArray
import com.mobileslicer.workspace.nativeModelTransformToViewerTransform
import kotlin.math.abs

private const val NativePlanEpsilonMm = 0.05f
private const val NativePlanEpsilonDegrees = 0.05f
private const val NativeArrangementOutputStrideWithBed = NativeModelTransformOutputStride + 1

internal fun applyNativePlatePlan(
    plateObjects: List<PlateObject>,
    bed: PrinterBedSpec,
    nativeTransforms: DoubleArray,
    requireAllOnBed: Boolean = true,
    requireNoOverlap: Boolean = false,
    preservePlateCenters: Boolean = false
): List<PlateObject>? {
    if (plateObjects.isEmpty()) return null
    val stride = when (nativeTransforms.size) {
        plateObjects.size * NativeArrangementOutputStrideWithBed -> NativeArrangementOutputStrideWithBed
        plateObjects.size * NativeModelTransformOutputStride -> NativeModelTransformOutputStride
        plateObjects.size * NativeModelTransformInputStride -> NativeModelTransformInputStride
        else -> return null
    }

    val updatedObjects = plateObjects.mapIndexed { index, objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return null
        val nativeTransform = runCatching {
            nativeModelTransformFromArray(nativeTransforms, offset = index * stride, stride = stride)
        }.getOrElse {
            return null
        }
        if (
            !nativeTransform.xMm.isFinite() ||
            !nativeTransform.yMm.isFinite() ||
            !nativeTransform.zMm.isFinite() ||
            !nativeTransform.rotationXRadians.isFinite() ||
            !nativeTransform.rotationYRadians.isFinite() ||
            !nativeTransform.rotationZRadians.isFinite() ||
            !nativeTransform.uniformScale.isFinite() ||
            nativeTransform.uniformScale !in 0.05..20.0
        ) {
            return null
        }

        val nativeViewerTransform = nativeModelTransformToViewerTransform(bounds, bed, nativeTransform)
        val preservedCenterTransform = nativeViewerTransform.copy(
            centerXmm = objectOnPlate.transform.centerXmm,
            centerYmm = objectOnPlate.transform.centerYmm
        )
        val viewerTransform = if (preservePlateCenters && (!requireAllOnBed || transformedObjectFitsOnBed(bounds, preservedCenterTransform, bed))) {
            preservedCenterTransform
        } else {
            nativeViewerTransform
        }
        if (requireAllOnBed) {
            if (!transformedObjectFitsOnBed(bounds, viewerTransform, bed)) return null
        }
        objectOnPlate.copy(transform = viewerTransform)
    }
    if (requireNoOverlap && updatedObjectsOverlap(updatedObjects)) {
        return null
    }
    return updatedObjects
}

private fun transformedObjectFitsOnBed(
    bounds: com.mobileslicer.viewer.MeshBounds,
    transform: com.mobileslicer.viewer.ViewerModelTransform,
    bed: PrinterBedSpec
): Boolean {
    val plateBounds = transformedObjectBoundsOnPlate(bounds, transform)
    return plateBounds.minX >= -NativePlanEpsilonMm &&
        plateBounds.maxX <= bed.widthMm + NativePlanEpsilonMm &&
        plateBounds.minY >= -NativePlanEpsilonMm &&
        plateBounds.maxY <= bed.depthMm + NativePlanEpsilonMm &&
        plateBounds.minZ >= -NativePlanEpsilonMm &&
        plateBounds.maxZ <= bed.maxHeightMm + NativePlanEpsilonMm
}

internal fun nativeArrangementBedIndices(
    nativeTransforms: DoubleArray,
    objectCount: Int
): List<Int> {
    if (objectCount <= 0) return emptyList()
    return if (nativeTransforms.size == objectCount * NativeArrangementOutputStrideWithBed) {
        List(objectCount) { index ->
            nativeTransforms[index * NativeArrangementOutputStrideWithBed + NativeModelTransformOutputStride]
                .toInt()
                .coerceAtLeast(0)
        }
    } else {
        List(objectCount) { 0 }
    }
}

internal fun nativePlanChangedCount(
    before: List<PlateObject>,
    after: List<PlateObject>
): Int =
    before.zip(after).count { (old, new) ->
        abs(old.transform.centerXmm - new.transform.centerXmm) > NativePlanEpsilonMm ||
            abs(old.transform.centerYmm - new.transform.centerYmm) > NativePlanEpsilonMm ||
            abs(old.transform.zOffsetMm - new.transform.zOffsetMm) > NativePlanEpsilonMm ||
            abs(old.transform.rotationXDegrees - new.transform.rotationXDegrees) > NativePlanEpsilonDegrees ||
            abs(old.transform.rotationYDegrees - new.transform.rotationYDegrees) > NativePlanEpsilonDegrees ||
            abs(old.transform.rotationZDegrees - new.transform.rotationZDegrees) > NativePlanEpsilonDegrees ||
            abs(old.transform.uniformScale - new.transform.uniformScale) > 0.001f ||
            !sameOrientationMatrix(old.transform.orientationMatrix, new.transform.orientationMatrix)
    }

internal fun singleObjectAutoArrangeFastPath(
    plateObjects: List<PlateObject>,
    bed: PrinterBedSpec
): List<PlateObject>? {
    val objectOnPlate = plateObjects.singleOrNull() ?: return null
    val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return null
    val centeredTransform = objectOnPlate.transform.copy(
        centerXmm = bed.widthMm * 0.5f,
        centerYmm = bed.depthMm * 0.5f
    )
    val centeredBounds = transformedObjectBoundsOnPlate(bounds, centeredTransform)
    val fits =
        centeredBounds.minX >= -NativePlanEpsilonMm &&
            centeredBounds.maxX <= bed.widthMm + NativePlanEpsilonMm &&
            centeredBounds.minY >= -NativePlanEpsilonMm &&
            centeredBounds.maxY <= bed.depthMm + NativePlanEpsilonMm &&
            centeredBounds.minZ >= -NativePlanEpsilonMm &&
            centeredBounds.maxZ <= bed.maxHeightMm + NativePlanEpsilonMm
    if (!fits) return null
    return listOf(objectOnPlate.copy(transform = centeredTransform))
}

private fun sameOrientationMatrix(left: List<Float>?, right: List<Float>?): Boolean {
    if (left == null && right == null) return true
    if (left == null || right == null || left.size != right.size) return false
    return left.zip(right).all { (old, new) -> abs(old - new) <= 0.0001f }
}

private fun updatedObjectsOverlap(objects: List<PlateObject>): Boolean {
    val boundsByObject = objects.map { objectOnPlate ->
        val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return true
        objectOnPlate.id to transformedObjectBoundsOnPlate(bounds, objectOnPlate.transform)
    }
    for (leftIndex in boundsByObject.indices) {
        val left = boundsByObject[leftIndex].second
        for (rightIndex in leftIndex + 1 until boundsByObject.size) {
            val right = boundsByObject[rightIndex].second
            if (
                left.minX < right.maxX - NativePlanEpsilonMm &&
                left.maxX > right.minX + NativePlanEpsilonMm &&
                left.minY < right.maxY - NativePlanEpsilonMm &&
                left.maxY > right.minY + NativePlanEpsilonMm
            ) {
                return true
            }
        }
    }
    return false
}
