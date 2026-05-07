package com.mobileslicer

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLoaderCalibrationPlateTest {
    @Test
    fun calibrationFailureStatusUsesSpecificMessageAndFallback() {
        assertEquals(
            "Calibration could not be created\nDisk full",
            calibrationPlateCreationFailureStatus(IllegalStateException("Disk full"))
        )
        assertEquals(
            "Calibration could not be created\nUnable to create the calibration model.",
            calibrationPlateCreationFailureStatus(Throwable())
        )
    }
}
