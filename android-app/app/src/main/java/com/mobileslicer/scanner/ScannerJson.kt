package com.mobileslicer.scanner

import org.json.JSONArray
import org.json.JSONObject

internal fun ScanPackageManifest.toJson(): JSONObject =
    JSONObject()
        .put("scan_id", scanId)
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("created_at", createdAtIso8601)
        .put("mode", mode.manifestValue)
        .put("capture_profile", captureProfile.manifestValue)
        .put("units", SCANNER_PACKAGE_UNITS)
        .put("frame_count", frameCount)
        .put("accepted_frame_count", acceptedFrameCount)
        .put("forced_frame_count", forcedFrameCount)
        .put("rejected_frame_count", rejectedFrameCount)
        .put("has_arcore_poses", hasArCorePoses)
        .put("has_depth", hasDepth)
        .put("has_masks", hasMasks)
        .put("scale_source", scaleSource.manifestValue)
        .put("object_moved_during_session", twoSidedAlignment.objectMovedDuringSession)
        .put("two_sided_alignment", twoSidedAlignment.toJson())
        .put("calibration", calibration?.toJson() ?: JSONObject.NULL)
        .put("requested_outputs", JSONArray(requestedOutputs))
        .put("frames", JSONArray().apply { frames.forEach { put(it.toJson()) } })

internal fun parseScanPackageManifest(json: JSONObject): ScanPackageManifest {
    val schemaVersion = json.getInt("schema_version")
    require(schemaVersion == SCANNER_PACKAGE_SCHEMA_VERSION) {
        "Unsupported scanner package schema: $schemaVersion"
    }
    val frames = json.optJSONArray("frames").orEmptyJsonArray().toObjectList()
        .map(::parseScanFrame)
    return ScanPackageManifest(
        scanId = json.getString("scan_id"),
        createdAtIso8601 = json.getString("created_at"),
        mode = scannerModeFromManifestValue(json.getString("mode")),
        captureProfile = scannerCaptureProfileFromManifestValue(json.getString("capture_profile")),
        frameCount = json.getInt("frame_count"),
        acceptedFrameCount = json.getInt("accepted_frame_count"),
        forcedFrameCount = json.getInt("forced_frame_count"),
        rejectedFrameCount = json.getInt("rejected_frame_count"),
        hasArCorePoses = json.getBoolean("has_arcore_poses"),
        hasDepth = json.getBoolean("has_depth"),
        hasMasks = json.getBoolean("has_masks"),
        scaleSource = scannerScaleSourceFromManifestValue(json.getString("scale_source")),
        calibration = json.optJSONObject("calibration")?.let(::parseScannerCalibration),
        twoSidedAlignment = parseScannerTwoSidedAlignment(json.getJSONObject("two_sided_alignment")),
        requestedOutputs = json.getJSONArray("requested_outputs").toStringList(),
        frames = frames
    )
}

internal fun ScanFrame.toJson(): JSONObject =
    JSONObject()
        .put("frame_id", id)
        .put("timestamp_ns", timestampNs)
        .put("image", rgbPath)
        .put("image_sha256", rgbSha256)
        .putNullable("mask", maskPath)
        .putNullable("mask_sha256", maskSha256)
        .putNullable("depth16", depth16Path)
        .putNullable("depth_sha256", depthSha256)
        .putNullable("confidence", depthConfidencePath)
        .put("pose_world_from_camera", poseWorldFromCamera?.toJsonArray() ?: JSONObject.NULL)
        .put("intrinsics", intrinsics?.toJson() ?: JSONObject.NULL)
        .put("distortion", distortion?.toJson() ?: JSONObject.NULL)
        .put(
            "camera",
            JSONObject()
                .put("lens_facing", lensFacing)
                .putNullable("focal_length_mm", focalLengthMm)
                .putNullable("exposure_time_ns", exposureTimeNs)
                .putNullable("iso", iso)
                .putNullable("focus_distance", focusDistance)
                .putNullable("white_balance_mode", whiteBalanceMode)
        )
        .put("width", width)
        .put("height", height)
        .put("capture_pass", capturePass.manifestValue)
        .put("quality", quality.toJson())
        .put("forced_capture", forcedCapture)

internal fun parseScanFrame(json: JSONObject): ScanFrame =
    ScanFrame(
        id = json.getString("frame_id"),
        timestampNs = json.getLong("timestamp_ns"),
        rgbPath = json.getString("image"),
        rgbSha256 = json.getString("image_sha256"),
        maskPath = json.nullableString("mask"),
        maskSha256 = json.nullableString("mask_sha256"),
        depth16Path = json.nullableString("depth16"),
        depthSha256 = json.nullableString("depth_sha256"),
        depthConfidencePath = json.nullableString("confidence"),
        poseWorldFromCamera = json.optJSONArray("pose_world_from_camera")?.toFloatArray(),
        intrinsics = json.optJSONObject("intrinsics")?.let(::parseCameraIntrinsicsData),
        distortion = json.optJSONObject("distortion")?.let(::parseCameraDistortionData),
        lensFacing = json.getJSONObject("camera").getString("lens_facing"),
        focalLengthMm = json.getJSONObject("camera").nullableFloat("focal_length_mm"),
        exposureTimeNs = json.getJSONObject("camera").nullableLong("exposure_time_ns"),
        iso = json.getJSONObject("camera").nullableInt("iso"),
        focusDistance = json.getJSONObject("camera").nullableFloat("focus_distance"),
        whiteBalanceMode = json.getJSONObject("camera").nullableString("white_balance_mode"),
        width = json.getInt("width"),
        height = json.getInt("height"),
        capturePass = scannerCapturePassFromManifestValue(json.optString("capture_pass", ScannerCapturePass.MidRing.manifestValue)),
        quality = parseFrameQuality(json.getJSONObject("quality")),
        forcedCapture = json.getBoolean("forced_capture")
    )

private fun CameraIntrinsicsData.toJson(): JSONObject =
    JSONObject()
        .put("fx", fx.toDouble())
        .put("fy", fy.toDouble())
        .put("cx", cx.toDouble())
        .put("cy", cy.toDouble())
        .put("width", imageWidth)
        .put("height", imageHeight)

private fun parseCameraIntrinsicsData(json: JSONObject): CameraIntrinsicsData =
    CameraIntrinsicsData(
        fx = json.getDouble("fx").toFloat(),
        fy = json.getDouble("fy").toFloat(),
        cx = json.getDouble("cx").toFloat(),
        cy = json.getDouble("cy").toFloat(),
        imageWidth = json.getInt("width"),
        imageHeight = json.getInt("height")
    )

private fun CameraDistortionData.toJson(): JSONObject =
    JSONObject()
        .put("model", model)
        .put("coefficients", coefficients.toJsonArray())

private fun parseCameraDistortionData(json: JSONObject): CameraDistortionData =
    CameraDistortionData(
        model = json.getString("model"),
        coefficients = json.getJSONArray("coefficients").toFloatArray()
    )

private fun FrameQuality.toJson(): JSONObject =
    JSONObject()
        .put("blur_score", blurScore.toDouble())
        .put("exposure_score", exposureScore.toDouble())
        .put("overlap_score", overlapScore.toDouble())
        .putNullable("tracking_good", trackingGood)
        .putNullable("depth_coverage", depthCoverage)
        .put("marker_visibility", markerVisibility.toDouble())
        .put("scale_confidence", scaleConfidence.toDouble())
        .put("material_risk", materialRisk.toDouble())
        .put("accepted", accepted)
        .put("rejection_reasons", JSONArray(rejectionReasons))

private fun parseFrameQuality(json: JSONObject): FrameQuality =
    FrameQuality(
        blurScore = json.getDouble("blur_score").toFloat(),
        exposureScore = json.getDouble("exposure_score").toFloat(),
        overlapScore = json.getDouble("overlap_score").toFloat(),
        trackingGood = json.nullableBoolean("tracking_good"),
        depthCoverage = json.nullableFloat("depth_coverage"),
        markerVisibility = json.getDouble("marker_visibility").toFloat(),
        scaleConfidence = json.getDouble("scale_confidence").toFloat(),
        materialRisk = json.getDouble("material_risk").toFloat(),
        accepted = json.getBoolean("accepted"),
        rejectionReasons = json.optJSONArray("rejection_reasons").orEmptyJsonArray().toStringList()
    )

private fun ScannerCalibration.toJson(): JSONObject =
    JSONObject()
        .put("marker_type", markerType)
        .put("marker_size_mm", markerSizeMm.toDouble())
        .put("printed_scale_bar_expected_mm", printedScaleBarExpectedMm.toDouble())
        .putNullable("printed_scale_bar_measured_mm", printedScaleBarMeasuredMm)
        .putNullable("scale_correction", scaleCorrection)
        .put("scale_confidence", scaleConfidence.toDouble())
        .putNullable("marker_reprojection_error_px", markerReprojectionErrorPx)

private fun parseScannerCalibration(json: JSONObject): ScannerCalibration =
    ScannerCalibration(
        markerType = json.getString("marker_type"),
        markerSizeMm = json.getDouble("marker_size_mm").toFloat(),
        printedScaleBarExpectedMm = json.getDouble("printed_scale_bar_expected_mm").toFloat(),
        printedScaleBarMeasuredMm = json.nullableFloat("printed_scale_bar_measured_mm"),
        scaleCorrection = json.nullableFloat("scale_correction"),
        scaleConfidence = json.getDouble("scale_confidence").toFloat(),
        markerReprojectionErrorPx = json.nullableFloat("marker_reprojection_error_px")
    )

private fun ScannerTwoSidedAlignment.toJson(): JSONObject =
    JSONObject()
        .put("object_moved_during_session", objectMovedDuringSession)
        .put("alignment_method", alignmentMethod.manifestValue)
        .putNullable("alignment_confidence", alignmentConfidence)
        .put("pass_count", passCount)

private fun parseScannerTwoSidedAlignment(json: JSONObject): ScannerTwoSidedAlignment =
    ScannerTwoSidedAlignment(
        objectMovedDuringSession = json.getBoolean("object_moved_during_session"),
        alignmentMethod = scannerAlignmentMethodFromManifestValue(json.getString("alignment_method")),
        alignmentConfidence = json.nullableFloat("alignment_confidence"),
        passCount = json.getInt("pass_count")
    )

private fun scannerModeFromManifestValue(value: String): ScannerMode =
    ScannerMode.entries.single { it.manifestValue == value }

private fun scannerCaptureProfileFromManifestValue(value: String): ScannerCaptureProfile =
    ScannerCaptureProfile.entries.single { it.manifestValue == value }

private fun scannerScaleSourceFromManifestValue(value: String): ScannerScaleSource =
    ScannerScaleSource.entries.single { it.manifestValue == value }

private fun scannerAlignmentMethodFromManifestValue(value: String): ScannerAlignmentMethod =
    ScannerAlignmentMethod.entries.single { it.manifestValue == value }

private fun scannerCapturePassFromManifestValue(value: String): ScannerCapturePass =
    ScannerCapturePass.entries.single { it.manifestValue == value }

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.nullableFloat(name: String): Float? =
    if (!has(name) || isNull(name)) null else getDouble(name).toFloat()

private fun JSONObject.nullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else getLong(name)

private fun JSONObject.nullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else getInt(name)

private fun JSONObject.nullableBoolean(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else getBoolean(name)

private fun JSONArray?.orEmptyJsonArray(): JSONArray = this ?: JSONArray()

private fun JSONArray.toStringList(): List<String> =
    List(length()) { index -> getString(index) }

private fun JSONArray.toObjectList(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }

private fun JSONArray.toFloatArray(): FloatArray =
    FloatArray(length()) { index -> getDouble(index).toFloat() }

private fun FloatArray.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it.toDouble()) } }
