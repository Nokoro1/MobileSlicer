package com.mobileslicer.scanner

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.SizeF

internal fun scannerBackCameraIntrinsics(
    context: Context,
    imageWidth: Int,
    imageHeight: Int
): CameraIntrinsicsData? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    val cameraManager = context.getSystemService(CameraManager::class.java) ?: return null
    val characteristics = cameraManager.cameraIdList
        .asSequence()
        .mapNotNull { cameraId -> runCatching { cameraManager.getCameraCharacteristics(cameraId) }.getOrNull() }
        .firstOrNull { characteristics ->
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return null
    return cameraIntrinsicsFromCharacteristics(characteristics, imageWidth, imageHeight)
}

internal fun cameraIntrinsicsFromCharacteristics(
    characteristics: CameraCharacteristics,
    imageWidth: Int,
    imageHeight: Int
): CameraIntrinsicsData? {
    val calibration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
    } else {
        null
    }
    if (calibration != null && calibration.size >= 5) {
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val sensorWidthPx = activeArray?.width()?.takeIf { it > 0 } ?: imageWidth
        val sensorHeightPx = activeArray?.height()?.takeIf { it > 0 } ?: imageHeight
        val scaleX = imageWidth.toFloat() / sensorWidthPx.toFloat()
        val scaleY = imageHeight.toFloat() / sensorHeightPx.toFloat()
        return CameraIntrinsicsData(
            fx = calibration[0] * scaleX,
            fy = calibration[1] * scaleY,
            cx = calibration[2] * scaleX,
            cy = calibration[3] * scaleY,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
    val sensorSize: SizeF = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    val focalLengthMm = focalLengths?.firstOrNull() ?: return null
    if (sensorSize.width <= 0f || sensorSize.height <= 0f || focalLengthMm <= 0f) return null
    return CameraIntrinsicsData(
        fx = focalLengthMm / sensorSize.width * imageWidth,
        fy = focalLengthMm / sensorSize.height * imageHeight,
        cx = imageWidth * 0.5f,
        cy = imageHeight * 0.5f,
        imageWidth = imageWidth,
        imageHeight = imageHeight
    )
}
