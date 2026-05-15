package com.mobileslicer.scanner

import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point

internal object ScannerAndroidOpenCvPoseSolver {
    const val SolverName = "android_opencv_relative_pose_solver"

    fun status(): ScannerOpenCvPoseSolverStatus =
        runCatching {
            val initialized = OpenCVLoader.initLocal()
            ScannerOpenCvPoseSolverStatus(
                available = initialized,
                status = if (initialized) "ready" else "opencv_init_failed",
                detail = if (initialized) {
                    "OpenCV Android Java API is initialized for calibrated relative pose solving."
                } else {
                    "OpenCV Android Java API did not initialize."
                },
                solverName = SolverName
            )
        }.getOrElse {
            ScannerOpenCvPoseSolverStatus(
                available = false,
                status = "opencv_unavailable",
                detail = it.message ?: "OpenCV Android Java API is unavailable",
                solverName = SolverName
            )
        }

    fun solveRelativePair(
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        intrinsicsA: CameraIntrinsicsData,
        intrinsicsB: CameraIntrinsicsData,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ): ScannerNativeRelativePairSolveResult =
        runCatching {
            if (pointsA.size != pointsB.size || pointsA.size < 10 || pointsA.size % 2 != 0) {
                return@runCatching ScannerNativeRelativePairSolveResult(
                    success = false,
                    status = "invalid_correspondence_input",
                    detail = "Relative pose solving requires equal point arrays with at least five 2D correspondences.",
                    inlierCount = 0,
                    inlierRatio = 0.0,
                    rotationRowMajor = emptyList(),
                    translationUnit = emptyList()
                )
            }
            if (!status().available) {
                return@runCatching ScannerNativeRelativePairSolveResult(
                    success = false,
                    status = "opencv_unavailable",
                    detail = "OpenCV Android Java API is not initialized.",
                    inlierCount = 0,
                    inlierRatio = 0.0,
                    rotationRowMajor = emptyList(),
                    translationUnit = emptyList()
                )
            }

            val imagePointsA = MatOfPoint2f(*pointsA.toPointArray())
            val imagePointsB = MatOfPoint2f(*pointsB.toPointArray())
            val cameraMatrix = averageCameraMatrix(intrinsicsA, intrinsicsB)
            val essential = Calib3d.findEssentialMat(
                imagePointsA,
                imagePointsB,
                cameraMatrix,
                Calib3d.RANSAC,
                0.999,
                maxReprojectionErrorPx,
                1000
            )
            if (essential.rows() <= 0 || essential.cols() <= 0) {
                return@runCatching ScannerNativeRelativePairSolveResult(
                    success = false,
                    status = "essential_matrix_failed",
                    detail = "OpenCV could not estimate an essential matrix from the selected pair.",
                    inlierCount = 0,
                    inlierRatio = 0.0,
                    rotationRowMajor = emptyList(),
                    translationUnit = emptyList()
                )
            }

            val rotation = Mat()
            val translation = Mat()
            val recoverMask = Mat()
            val inliers = Calib3d.recoverPose(
                essential,
                imagePointsA,
                imagePointsB,
                cameraMatrix,
                rotation,
                translation,
                recoverMask
            )
            val total = pointsA.size / 2
            if (inliers < minInliers) {
                return@runCatching ScannerNativeRelativePairSolveResult(
                    success = false,
                    status = "relative_pose_inliers_low",
                    detail = "OpenCV recovered a relative pose, but inlier support is below the configured threshold.",
                    inlierCount = inliers,
                    inlierRatio = if (total > 0) inliers.toDouble() / total.toDouble() else 0.0,
                    rotationRowMajor = emptyList(),
                    translationUnit = emptyList()
                )
            }

            ScannerNativeRelativePairSolveResult(
                success = true,
                status = "relative_pose_recovered",
                detail = "OpenCV recovered a calibrated relative pose for the selected frame pair. Translation is unit length and not yet metric scale.",
                inlierCount = inliers,
                inlierRatio = if (total > 0) inliers.toDouble() / total.toDouble() else 0.0,
                rotationRowMajor = rotation.toRowMajorList(),
                translationUnit = translation.toColumnList()
            )
        }.getOrElse {
            ScannerNativeRelativePairSolveResult(
                success = false,
                status = "opencv_relative_pair_solve_failed",
                detail = it.message ?: "OpenCV relative pair solve failed",
                inlierCount = 0,
                inlierRatio = 0.0,
                rotationRowMajor = emptyList(),
                translationUnit = emptyList()
            )
        }
}

internal data class ScannerOpenCvPoseSolverStatus(
    val available: Boolean,
    val status: String,
    val detail: String,
    val solverName: String
)

private fun DoubleArray.toPointArray(): Array<Point> =
    Array(size / 2) { index ->
        Point(this[index * 2], this[index * 2 + 1])
    }

private fun averageCameraMatrix(
    a: CameraIntrinsicsData,
    b: CameraIntrinsicsData
): Mat =
    Mat.eye(3, 3, CvType.CV_64F).also { matrix ->
        matrix.put(0, 0, (a.fx + b.fx).toDouble() * 0.5)
        matrix.put(1, 1, (a.fy + b.fy).toDouble() * 0.5)
        matrix.put(0, 2, (a.cx + b.cx).toDouble() * 0.5)
        matrix.put(1, 2, (a.cy + b.cy).toDouble() * 0.5)
    }

private fun Mat.toRowMajorList(): List<Double> =
    buildList {
        for (row in 0 until rows()) {
            for (col in 0 until cols()) {
                add(get(row, col)?.firstOrNull() ?: 0.0)
            }
        }
    }

private fun Mat.toColumnList(): List<Double> =
    buildList {
        for (row in 0 until rows()) {
            add(get(row, 0)?.firstOrNull() ?: 0.0)
        }
    }
