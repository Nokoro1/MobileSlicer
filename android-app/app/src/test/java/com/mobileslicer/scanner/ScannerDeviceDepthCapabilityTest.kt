package com.mobileslicer.scanner

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDeviceDepthCapabilityTest {
    @Test
    fun depthOutputSupportIsFalseWhenCapabilitiesAreMissing() {
        assertFalse(scannerDepthOutputSupported(null))
    }

    @Test
    fun depthOutputSupportIsFalseWhenCapabilityIsAbsent() {
        assertFalse(scannerDepthOutputSupported(intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)))
    }

    @Test
    fun depthOutputSupportIsTrueWhenDepthCapabilityIsPresent() {
        assertTrue(scannerDepthOutputSupported(intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)))
    }
}
