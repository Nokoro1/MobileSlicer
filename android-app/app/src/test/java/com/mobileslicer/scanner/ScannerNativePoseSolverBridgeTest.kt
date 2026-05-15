package com.mobileslicer.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerNativePoseSolverBridgeTest {
    @Test
    fun nativePoseSolverStatusFailsClosedOnJvmWhenLibraryIsUnavailable() {
        val status = ScannerNativePoseSolverBridge.status()

        assertFalse(status.available)
        assertFalse(status.opencvLinked)
        assertTrue(status.solverName.isNotBlank())
        assertTrue(status.status.isNotBlank())
        assertTrue(status.detail.isNotBlank())
    }
}
