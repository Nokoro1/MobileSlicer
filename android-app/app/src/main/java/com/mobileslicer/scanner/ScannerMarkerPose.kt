package com.mobileslicer.scanner

import kotlin.math.abs
import kotlin.math.sqrt

internal data class MarkerPoseEstimate(
    val markerId: String,
    val frameId: String,
    val reprojectionErrorPx: Float,
    val distanceMm: Float,
    val normalCamera: FloatArray
) {
    override fun equals(other: Any?): Boolean =
        other is MarkerPoseEstimate &&
            markerId == other.markerId &&
            frameId == other.frameId &&
            reprojectionErrorPx == other.reprojectionErrorPx &&
            distanceMm == other.distanceMm &&
            normalCamera.contentEquals(other.normalCamera)

    override fun hashCode(): Int {
        var result = markerId.hashCode()
        result = 31 * result + frameId.hashCode()
        result = 31 * result + reprojectionErrorPx.hashCode()
        result = 31 * result + distanceMm.hashCode()
        result = 31 * result + normalCamera.contentHashCode()
        return result
    }
}

internal fun calibrateMarkerObservations(
    observations: List<MarkerObservation>,
    frames: List<ScanFrame>
): List<MarkerObservation> {
    val intrinsicsByFrame = frames.associate { it.id to it.intrinsics }
    return observations.map { observation ->
        val intrinsics = intrinsicsByFrame[observation.frameId]
        val pose = if (intrinsics != null) {
            estimateMarkerPose(observation, intrinsics)
        } else {
            null
        }
        observation.copy(reprojectionErrorPx = pose?.reprojectionErrorPx)
    }
}

internal fun estimateMarkerPose(
    observation: MarkerObservation,
    intrinsics: CameraIntrinsicsData
): MarkerPoseEstimate? {
    if (observation.cornersPx.size != 4 || observation.markerSizeMm <= 0f) return null
    if (intrinsics.fx <= 0f || intrinsics.fy <= 0f) return null
    val size = observation.markerSizeMm.toDouble()
    val objectCorners = arrayOf(
        doubleArrayOf(0.0, 0.0),
        doubleArrayOf(size, 0.0),
        doubleArrayOf(size, size),
        doubleArrayOf(0.0, size)
    )
    val normalizedImageCorners = observation.cornersPx.map { corner ->
        doubleArrayOf(
            (corner.first.toDouble() - intrinsics.cx.toDouble()) / intrinsics.fx.toDouble(),
            (corner.second.toDouble() - intrinsics.cy.toDouble()) / intrinsics.fy.toDouble()
        )
    }.toTypedArray()
    val homography = solvePlanarHomography(objectCorners, normalizedImageCorners) ?: return null
    val h1 = doubleArrayOf(homography[0][0], homography[1][0], homography[2][0])
    val h2 = doubleArrayOf(homography[0][1], homography[1][1], homography[2][1])
    val h3 = doubleArrayOf(homography[0][2], homography[1][2], homography[2][2])
    val scale = 2.0 / (norm(h1) + norm(h2))
    var r1 = h1.map { it * scale }.toDoubleArray()
    var r2 = h2.map { it * scale }.toDoubleArray()
    val translation = h3.map { it * scale }.toDoubleArray()
    if (translation[2] <= 0.0) return null

    r1 = normalize(r1) ?: return null
    r2 = subtract(r2, multiply(r1, dot(r1, r2)))
    r2 = normalize(r2) ?: return null
    val normal = cross(r1, r2)

    val reprojected = objectCorners.map { objectCorner ->
        val cameraX = r1[0] * objectCorner[0] + r2[0] * objectCorner[1] + translation[0]
        val cameraY = r1[1] * objectCorner[0] + r2[1] * objectCorner[1] + translation[1]
        val cameraZ = r1[2] * objectCorner[0] + r2[2] * objectCorner[1] + translation[2]
        if (cameraZ <= 0.0) return null
        doubleArrayOf(
            intrinsics.fx.toDouble() * (cameraX / cameraZ) + intrinsics.cx.toDouble(),
            intrinsics.fy.toDouble() * (cameraY / cameraZ) + intrinsics.cy.toDouble()
        )
    }
    val rmsError = sqrt(
        reprojected.mapIndexed { index, projected ->
            val observed = observation.cornersPx[index]
            val dx = projected[0] - observed.first.toDouble()
            val dy = projected[1] - observed.second.toDouble()
            dx * dx + dy * dy
        }.average()
    ).toFloat()

    if (!rmsError.isFinite() || rmsError > 100f) return null
    return MarkerPoseEstimate(
        markerId = observation.markerId,
        frameId = observation.frameId,
        reprojectionErrorPx = rmsError,
        distanceMm = norm(translation).toFloat(),
        normalCamera = floatArrayOf(normal[0].toFloat(), normal[1].toFloat(), normal[2].toFloat())
    )
}

private fun solvePlanarHomography(
    objectCorners: Array<DoubleArray>,
    imageCorners: Array<DoubleArray>
): Array<DoubleArray>? {
    val a = Array(8) { DoubleArray(9) }
    for (i in 0 until 4) {
        val x = objectCorners[i][0]
        val y = objectCorners[i][1]
        val u = imageCorners[i][0]
        val v = imageCorners[i][1]
        val row = i * 2
        a[row][0] = x
        a[row][1] = y
        a[row][2] = 1.0
        a[row][6] = -u * x
        a[row][7] = -u * y
        a[row][8] = u
        a[row + 1][3] = x
        a[row + 1][4] = y
        a[row + 1][5] = 1.0
        a[row + 1][6] = -v * x
        a[row + 1][7] = -v * y
        a[row + 1][8] = v
    }
    val solution = solveLinearSystem(a) ?: return null
    return arrayOf(
        doubleArrayOf(solution[0], solution[1], solution[2]),
        doubleArrayOf(solution[3], solution[4], solution[5]),
        doubleArrayOf(solution[6], solution[7], 1.0)
    )
}

private fun solveLinearSystem(augmented: Array<DoubleArray>): DoubleArray? {
    val n = 8
    for (pivot in 0 until n) {
        var bestRow = pivot
        var bestValue = abs(augmented[pivot][pivot])
        for (row in pivot + 1 until n) {
            val candidate = abs(augmented[row][pivot])
            if (candidate > bestValue) {
                bestValue = candidate
                bestRow = row
            }
        }
        if (bestValue < 1e-9) return null
        if (bestRow != pivot) {
            val temp = augmented[pivot]
            augmented[pivot] = augmented[bestRow]
            augmented[bestRow] = temp
        }
        val pivotValue = augmented[pivot][pivot]
        for (col in pivot until n + 1) {
            augmented[pivot][col] /= pivotValue
        }
        for (row in 0 until n) {
            if (row == pivot) continue
            val factor = augmented[row][pivot]
            if (factor == 0.0) continue
            for (col in pivot until n + 1) {
                augmented[row][col] -= factor * augmented[pivot][col]
            }
        }
    }
    return DoubleArray(n) { augmented[it][n] }
}

private fun norm(vector: DoubleArray): Double =
    sqrt(vector.sumOf { it * it })

private fun normalize(vector: DoubleArray): DoubleArray? {
    val length = norm(vector)
    if (length < 1e-9) return null
    return vector.map { it / length }.toDoubleArray()
}

private fun dot(a: DoubleArray, b: DoubleArray): Double =
    a.indices.sumOf { a[it] * b[it] }

private fun multiply(vector: DoubleArray, scalar: Double): DoubleArray =
    vector.map { it * scalar }.toDoubleArray()

private fun subtract(a: DoubleArray, b: DoubleArray): DoubleArray =
    a.indices.map { a[it] - b[it] }.toDoubleArray()

private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray =
    doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )
