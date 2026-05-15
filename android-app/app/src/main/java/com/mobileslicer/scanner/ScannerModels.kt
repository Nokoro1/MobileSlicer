package com.mobileslicer.scanner

internal const val SCANNER_PACKAGE_SCHEMA_VERSION = 1
internal const val SCANNER_PACKAGE_UNITS = "millimeters"

internal enum class ScannerMode(val manifestValue: String) {
    LocalPrintable("local_printable"),
    CloudAdvanced("cloud_advanced"),
    CloudProPrintable("cloud_pro_printable")
}

internal enum class ScannerScaleSource(val manifestValue: String) {
    None("none"),
    VerifiedMarkerMat("verified_marker_mat"),
    TurntableDiameter("turntable_diameter"),
    KnownObjectDimension("known_object_dimension"),
    ManualMeasurement("manual_measurement"),
    ArCoreDepthAssist("arcore_depth_assist")
}

internal enum class ScannerAlignmentMethod(val manifestValue: String) {
    NotMoved("not_moved"),
    MarkerAlignment("marker_alignment"),
    IcpOverlap("icp_overlap"),
    TurntableJig("turntable_jig"),
    Unknown("unknown")
}

internal enum class ScannerCaptureProfile(val manifestValue: String) {
    HighResPhoto("high_res_photo"),
    ArSynchronized("ar_synchronized"),
    Hybrid("hybrid")
}

internal enum class ScannerCapturePass(val manifestValue: String, val displayName: String) {
    MidRing("mid_ring", "Mid ring"),
    LowRing("low_ring", "Low ring"),
    HighRing("high_ring", "High ring"),
    TopDetail("top_detail", "Top/detail"),
    Underside("underside", "Underside")
}

internal data class CameraIntrinsicsData(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val imageWidth: Int,
    val imageHeight: Int
)

internal data class CameraDistortionData(
    val model: String,
    val coefficients: FloatArray
) {
    override fun equals(other: Any?): Boolean =
        other is CameraDistortionData &&
            model == other.model &&
            coefficients.contentEquals(other.coefficients)

    override fun hashCode(): Int = 31 * model.hashCode() + coefficients.contentHashCode()
}

internal data class FrameQuality(
    val blurScore: Float,
    val exposureScore: Float,
    val overlapScore: Float,
    val trackingGood: Boolean?,
    val depthCoverage: Float?,
    val markerVisibility: Float,
    val scaleConfidence: Float,
    val materialRisk: Float,
    val accepted: Boolean,
    val rejectionReasons: List<String> = emptyList()
)

internal data class ScanFrame(
    val id: String,
    val timestampNs: Long,
    val rgbPath: String,
    val rgbSha256: String,
    val maskPath: String?,
    val maskSha256: String?,
    val depth16Path: String?,
    val depthSha256: String?,
    val depthConfidencePath: String?,
    val poseWorldFromCamera: FloatArray?,
    val intrinsics: CameraIntrinsicsData?,
    val distortion: CameraDistortionData?,
    val lensFacing: String,
    val focalLengthMm: Float?,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val focusDistance: Float?,
    val whiteBalanceMode: String?,
    val width: Int,
    val height: Int,
    val capturePass: ScannerCapturePass = ScannerCapturePass.MidRing,
    val quality: FrameQuality,
    val forcedCapture: Boolean
) {
    override fun equals(other: Any?): Boolean =
        other is ScanFrame &&
            id == other.id &&
            timestampNs == other.timestampNs &&
            rgbPath == other.rgbPath &&
            rgbSha256 == other.rgbSha256 &&
            maskPath == other.maskPath &&
            maskSha256 == other.maskSha256 &&
            depth16Path == other.depth16Path &&
            depthSha256 == other.depthSha256 &&
            depthConfidencePath == other.depthConfidencePath &&
            poseWorldFromCamera.contentEqualsNullable(other.poseWorldFromCamera) &&
            intrinsics == other.intrinsics &&
            distortion == other.distortion &&
            lensFacing == other.lensFacing &&
            focalLengthMm == other.focalLengthMm &&
            exposureTimeNs == other.exposureTimeNs &&
            iso == other.iso &&
            focusDistance == other.focusDistance &&
            whiteBalanceMode == other.whiteBalanceMode &&
            width == other.width &&
            height == other.height &&
            capturePass == other.capturePass &&
            quality == other.quality &&
            forcedCapture == other.forcedCapture

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestampNs.hashCode()
        result = 31 * result + rgbPath.hashCode()
        result = 31 * result + rgbSha256.hashCode()
        result = 31 * result + (maskPath?.hashCode() ?: 0)
        result = 31 * result + (maskSha256?.hashCode() ?: 0)
        result = 31 * result + (depth16Path?.hashCode() ?: 0)
        result = 31 * result + (depthSha256?.hashCode() ?: 0)
        result = 31 * result + (depthConfidencePath?.hashCode() ?: 0)
        result = 31 * result + (poseWorldFromCamera?.contentHashCode() ?: 0)
        result = 31 * result + (intrinsics?.hashCode() ?: 0)
        result = 31 * result + (distortion?.hashCode() ?: 0)
        result = 31 * result + lensFacing.hashCode()
        result = 31 * result + (focalLengthMm?.hashCode() ?: 0)
        result = 31 * result + (exposureTimeNs?.hashCode() ?: 0)
        result = 31 * result + (iso ?: 0)
        result = 31 * result + (focusDistance?.hashCode() ?: 0)
        result = 31 * result + (whiteBalanceMode?.hashCode() ?: 0)
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + capturePass.hashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + forcedCapture.hashCode()
        return result
    }
}

internal data class ScannerCalibration(
    val markerType: String,
    val markerSizeMm: Float,
    val printedScaleBarExpectedMm: Float,
    val printedScaleBarMeasuredMm: Float?,
    val scaleCorrection: Float?,
    val scaleConfidence: Float,
    val markerReprojectionErrorPx: Float?
)

internal data class ScannerTwoSidedAlignment(
    val objectMovedDuringSession: Boolean,
    val alignmentMethod: ScannerAlignmentMethod,
    val alignmentConfidence: Float?,
    val passCount: Int
)

internal data class ScanPackageManifest(
    val scanId: String,
    val createdAtIso8601: String,
    val mode: ScannerMode,
    val captureProfile: ScannerCaptureProfile,
    val frameCount: Int,
    val acceptedFrameCount: Int,
    val forcedFrameCount: Int,
    val rejectedFrameCount: Int,
    val hasArCorePoses: Boolean,
    val hasDepth: Boolean,
    val hasMasks: Boolean,
    val scaleSource: ScannerScaleSource,
    val calibration: ScannerCalibration?,
    val twoSidedAlignment: ScannerTwoSidedAlignment,
    val requestedOutputs: List<String>,
    val frames: List<ScanFrame>
)

private fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean =
    when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
