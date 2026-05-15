package com.mobileslicer.scanner

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

internal data class ScannerDeviceDepthCapability(
    val checked: Boolean,
    val depthOutputSupported: Boolean,
    val backCameraChecked: Boolean,
    val reason: String
)

internal fun scannerDeviceDepthCapability(context: Context): ScannerDeviceDepthCapability =
    runCatching {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backCameraId = cameraManager.cameraIdList.firstOrNull { cameraId ->
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return ScannerDeviceDepthCapability(
            checked = true,
            depthOutputSupported = false,
            backCameraChecked = false,
            reason = "back_camera_missing"
        )
        val characteristics = cameraManager.getCameraCharacteristics(backCameraId)
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val depthSupported = scannerDepthOutputSupported(capabilities)
        ScannerDeviceDepthCapability(
            checked = true,
            depthOutputSupported = depthSupported,
            backCameraChecked = true,
            reason = if (depthSupported) "depth_output_supported" else "depth_output_not_advertised"
        )
    }.getOrElse {
        ScannerDeviceDepthCapability(
            checked = false,
            depthOutputSupported = false,
            backCameraChecked = false,
            reason = "camera_characteristics_unavailable:${it.javaClass.simpleName}"
        )
    }

internal fun scannerDepthOutputSupported(capabilities: IntArray?): Boolean =
    capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == true
