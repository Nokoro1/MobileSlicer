package com.mobileslicer.scanner

internal enum class ScannerMarkerSystem(val manifestValue: String) {
    AprilTag("apriltag"),
    ArUco("aruco"),
    ChArUco("charuco")
}

internal data class PrintedScaleVerificationInput(
    val expectedMm: Float,
    val measuredMm: Float,
    val maxScaleErrorRatio: Float = 0.01f
)

internal data class PrintedScaleVerificationResult(
    val valid: Boolean,
    val expectedMm: Float,
    val measuredMm: Float,
    val scaleCorrection: Float,
    val scaleErrorRatio: Float,
    val message: String
)

internal data class PrintableCalibrationGate(
    val minScaleConfidence: Float = 0.80f,
    val maxMarkerReprojectionErrorPx: Float = 2.5f,
    val minMarkerObservations: Int = 4,
    val minMarkerFrames: Int = 2,
    val requireMeasuredScaleBar: Boolean = true
)

internal data class PrintableCalibrationGateResult(
    val allowed: Boolean,
    val reasons: List<String>
)

internal data class MarkerObservation(
    val markerSystem: ScannerMarkerSystem,
    val markerId: String,
    val frameId: String,
    val framePath: String,
    val markerSizeMm: Float,
    val cornersPx: List<Pair<Float, Float>>,
    val hamming: Int? = null,
    val decisionMargin: Float? = null,
    val reprojectionErrorPx: Float? = null
)

internal interface ScannerMarkerDetector {
    val detectorName: String
    val detectorStatus: String
    fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation>
}

internal object UnimplementedScannerMarkerDetector : ScannerMarkerDetector {
    override val detectorName: String = "apriltag_36h11_jni"
    override val detectorStatus: String = ScannerAprilTagNativeBridge.status().status
    override fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> = emptyList()
}

internal data class MarkerEvidenceSummary(
    val detectorName: String,
    val detectorStatus: String,
    val observationCount: Int,
    val calibratedObservationCount: Int,
    val observedFrameCount: Int,
    val uniqueMarkerCount: Int,
    val averageReprojectionErrorPx: Float?,
    val markerVisibility: Float,
    val markerScaleConfidence: Float,
    val reasons: List<String>
)

internal fun verifyPrintedScale(input: PrintedScaleVerificationInput): PrintedScaleVerificationResult {
    require(input.expectedMm > 0f) { "Expected scale length must be positive" }
    require(input.measuredMm > 0f) { "Measured scale length must be positive" }
    val scaleCorrection = input.expectedMm / input.measuredMm
    val scaleErrorRatio = kotlin.math.abs(input.measuredMm - input.expectedMm) / input.expectedMm
    val valid = scaleErrorRatio <= input.maxScaleErrorRatio
    return PrintedScaleVerificationResult(
        valid = valid,
        expectedMm = input.expectedMm,
        measuredMm = input.measuredMm,
        scaleCorrection = scaleCorrection,
        scaleErrorRatio = scaleErrorRatio,
        message = if (valid) {
            "Printed scale is within tolerance."
        } else {
            "Printed scale is outside tolerance; reprint without page scaling or use measured correction only as weak evidence."
        }
    )
}

internal fun evaluatePrintableCalibrationGate(
    scaleSource: ScannerScaleSource,
    calibration: ScannerCalibration?,
    alignment: ScannerTwoSidedAlignment,
    markerEvidence: MarkerEvidenceSummary = emptyMarkerEvidence(),
    gate: PrintableCalibrationGate = PrintableCalibrationGate()
): PrintableCalibrationGateResult {
    val reasons = buildList {
        if (scaleSource == ScannerScaleSource.None || scaleSource == ScannerScaleSource.ArCoreDepthAssist) {
            add("scale_source_not_printable")
        }
        if (calibration == null) {
            add("calibration_missing")
        } else {
            if (gate.requireMeasuredScaleBar && calibration.printedScaleBarMeasuredMm == null) {
                add("printed_scale_unverified")
            }
            if (calibration.scaleConfidence < gate.minScaleConfidence) {
                add("scale_confidence_low")
            }
            val reprojectionError = calibration.markerReprojectionErrorPx
            if (reprojectionError == null) {
                add("marker_reprojection_missing")
            } else if (reprojectionError > gate.maxMarkerReprojectionErrorPx) {
                add("marker_reprojection_high")
            }
        }
        if (markerEvidence.observationCount < gate.minMarkerObservations) {
            add("marker_observations_insufficient")
        }
        if (markerEvidence.observedFrameCount < gate.minMarkerFrames) {
            add("marker_frame_coverage_insufficient")
        }
        markerEvidence.reasons.forEach { add(it) }
        if (alignment.objectMovedDuringSession &&
            alignment.alignmentMethod == ScannerAlignmentMethod.Unknown
        ) {
            add("two_sided_alignment_unknown")
        }
    }
    return PrintableCalibrationGateResult(
        allowed = reasons.isEmpty(),
        reasons = reasons
    )
}

internal fun summarizeMarkerEvidence(
    observations: List<MarkerObservation>,
    acceptedFrameCount: Int,
    detectorName: String,
    detectorStatus: String
): MarkerEvidenceSummary {
    val observedFrameCount = observations.map { it.frameId }.distinct().size
    val uniqueMarkerCount = observations.map { it.markerId }.distinct().size
    val reprojectionErrors = observations.mapNotNull { it.reprojectionErrorPx }
    val calibratedObservationCount = reprojectionErrors.size
    val averageReprojectionError = reprojectionErrors.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val markerVisibility = if (acceptedFrameCount <= 0) {
        0f
    } else {
        (observedFrameCount.toFloat() / acceptedFrameCount.toFloat()).coerceIn(0f, 1f)
    }
    val reprojectionConfidence = averageReprojectionError?.let {
        (1f - (it / 2.5f)).coerceIn(0f, 1f)
    } ?: 0f
    val countConfidence = (observations.size.toFloat() / 8f).coerceIn(0f, 1f)
    val markerScaleConfidence = minOf(markerVisibility, reprojectionConfidence, countConfidence)
    val reasons = buildList {
        if (detectorStatus != "ready") add("marker_detector_not_ready")
        if (observations.isEmpty()) add("markers_not_detected")
        if (observations.isNotEmpty() && calibratedObservationCount < observations.size) {
            add("marker_pose_intrinsics_missing")
        }
        if (averageReprojectionError == null) add("marker_reprojection_missing")
    }
    return MarkerEvidenceSummary(
        detectorName = detectorName,
        detectorStatus = detectorStatus,
        observationCount = observations.size,
        calibratedObservationCount = calibratedObservationCount,
        observedFrameCount = observedFrameCount,
        uniqueMarkerCount = uniqueMarkerCount,
        averageReprojectionErrorPx = averageReprojectionError,
        markerVisibility = markerVisibility,
        markerScaleConfidence = markerScaleConfidence,
        reasons = reasons
    )
}

internal fun emptyMarkerEvidence(): MarkerEvidenceSummary =
    MarkerEvidenceSummary(
        detectorName = UnimplementedScannerMarkerDetector.detectorName,
        detectorStatus = UnimplementedScannerMarkerDetector.detectorStatus,
        observationCount = 0,
        calibratedObservationCount = 0,
        observedFrameCount = 0,
        uniqueMarkerCount = 0,
        averageReprojectionErrorPx = null,
        markerVisibility = 0f,
        markerScaleConfidence = 0f,
        reasons = listOf("marker_detector_not_ready", "markers_not_detected", "marker_reprojection_missing")
    )
