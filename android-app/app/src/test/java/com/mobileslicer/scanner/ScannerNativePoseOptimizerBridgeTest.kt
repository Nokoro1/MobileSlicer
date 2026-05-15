package com.mobileslicer.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerNativePoseOptimizerBridgeTest {
    @Test
    fun nativePoseOptimizerStatusFailsClosedOnJvmWhenLibraryIsUnavailable() {
        val status = ScannerNativePoseOptimizerBridge.status()

        assertFalse(status.available)
        assertFalse(status.ceresLinked)
        assertFalse(status.optimizerLinked)
        assertTrue(status.solverName.isNotBlank())
        assertTrue(status.status.isNotBlank())
        assertTrue(status.detail.isNotBlank())
    }

    @Test
    fun nativePoseOptimizerSolveFailsClosedOnJvmWhenLibraryIsUnavailable() {
        val result = ScannerNativePoseOptimizerBridge.optimize("""{"schema_version":1}""")

        assertFalse(result.success)
        assertFalse(result.ceresLinked)
        assertFalse(result.optimizerLinked)
        assertTrue(result.solverName.isNotBlank())
        assertTrue(result.status.isNotBlank())
        assertTrue(result.detail.isNotBlank())
        assertTrue(result.optimizedCameraPoses.isEmpty())
        assertTrue(result.optimizedSparsePoints.isEmpty())
    }
}
