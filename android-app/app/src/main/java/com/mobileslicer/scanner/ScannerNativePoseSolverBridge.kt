package com.mobileslicer.scanner

import org.json.JSONObject

internal object ScannerNativePoseSolverBridge {
    const val SolverName = "native_opencv_metric_pose_solver"

    fun status(): ScannerNativePoseSolverStatus =
        runCatching {
            if (!libraryLoaded) {
                return@runCatching ScannerNativePoseSolverStatus(
                    available = false,
                    status = "native_library_unavailable",
                    detail = loadError ?: "Native scanner_pose_solver bridge is unavailable",
                    solverName = SolverName,
                    opencvLinked = false
                )
            }
            parseNativePoseSolverStatus(nativeStatusJson())
        }.getOrElse {
            ScannerNativePoseSolverStatus(
                available = false,
                status = "native_bridge_unavailable",
                detail = it.message ?: "Native metric pose solver bridge is unavailable",
                solverName = SolverName,
                opencvLinked = false
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
            if (!libraryLoaded) {
                return@runCatching ScannerNativeRelativePairSolveResult(
                    success = false,
                    status = "native_library_unavailable",
                    detail = loadError ?: "Native scanner_pose_solver bridge is unavailable",
                    inlierCount = 0,
                    inlierRatio = 0.0,
                    rotationRowMajor = emptyList(),
                    translationUnit = emptyList()
                )
            }
            parseRelativePairSolveResult(
                nativeSolveRelativePairJson(
                    pointsA,
                    pointsB,
                    intrinsicsA.fx.toDouble(),
                    intrinsicsA.fy.toDouble(),
                    intrinsicsA.cx.toDouble(),
                    intrinsicsA.cy.toDouble(),
                    intrinsicsB.fx.toDouble(),
                    intrinsicsB.fy.toDouble(),
                    intrinsicsB.cx.toDouble(),
                    intrinsicsB.cy.toDouble(),
                    minInliers,
                    maxReprojectionErrorPx
                )
            )
        }.getOrElse {
            ScannerNativeRelativePairSolveResult(
                success = false,
                status = "native_bridge_unavailable",
                detail = it.message ?: "Native relative pair solve failed",
                inlierCount = 0,
                inlierRatio = 0.0,
                rotationRowMajor = emptyList(),
                translationUnit = emptyList()
            )
        }

    private external fun nativeStatusJson(): String
    private external fun nativeSolveRelativePairJson(
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        fxA: Double,
        fyA: Double,
        cxA: Double,
        cyA: Double,
        fxB: Double,
        fyB: Double,
        cxB: Double,
        cyB: Double,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ): String

    private val loadAttempt = runCatching { System.loadLibrary("scanner_pose_solver") }
    private val libraryLoaded = loadAttempt.isSuccess
    private val loadError = loadAttempt.exceptionOrNull()?.message
}

internal data class ScannerNativePoseSolverStatus(
    val available: Boolean,
    val status: String,
    val detail: String,
    val solverName: String,
    val opencvLinked: Boolean
)

internal data class ScannerNativeRelativePairSolveResult(
    val success: Boolean,
    val status: String,
    val detail: String,
    val inlierCount: Int,
    val inlierRatio: Double,
    val rotationRowMajor: List<Double>,
    val translationUnit: List<Double>
)

private fun parseNativePoseSolverStatus(jsonText: String): ScannerNativePoseSolverStatus {
    val json = JSONObject(jsonText)
    return ScannerNativePoseSolverStatus(
        available = json.optBoolean("available", false),
        status = json.optString("status", "native_bridge_unavailable"),
        detail = json.optString("detail", ""),
        solverName = json.optString("solver_name", ScannerNativePoseSolverBridge.SolverName),
        opencvLinked = json.optBoolean("opencv_linked", false)
    )
}

private fun parseRelativePairSolveResult(jsonText: String): ScannerNativeRelativePairSolveResult {
    val json = JSONObject(jsonText)
    return ScannerNativeRelativePairSolveResult(
        success = json.optBoolean("success", false),
        status = json.optString("status", "native_pair_solve_failed"),
        detail = json.optString("detail", ""),
        inlierCount = json.optInt("inlier_count", 0),
        inlierRatio = json.optDouble("inlier_ratio", 0.0),
        rotationRowMajor = json.optJSONArray("rotation_row_major").toDoubleList(),
        translationUnit = json.optJSONArray("translation_unit").toDoubleList()
    )
}

private fun org.json.JSONArray?.toDoubleList(): List<Double> =
    if (this == null) emptyList() else List(length()) { getDouble(it) }
