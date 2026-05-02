package com.mobileslicer

import com.mobileslicer.automation.AutomationSliceTiming
import com.mobileslicer.automation.automationSliceSuccessStatus
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSliceRunnerTest {
    @Test
    fun successStatusIncludesPhaseTimings() {
        val outputFile = createTempFile(prefix = "automation-status", suffix = ".gcode")
        outputFile.writeText("G1 X1 Y1 E1\n")

        val status = automationSliceSuccessStatus(
            modelFile = File("/tmp/model.stl"),
            stagedModel = File("/tmp/staged-model.stl"),
            outputFile = outputFile,
            timing = AutomationSliceTiming(
                stagingMs = 1,
                nativeLoadMs = 2,
                placementMs = 3,
                configMs = 4,
                nativeSliceMs = 5,
                writeGcodeMs = 6,
                totalMs = 21
            ),
            configJson = """{"layer_height":0.2}"""
        )

        assertTrue(status.startsWith("success:"))
        assertTrue(status.contains("bytes=${outputFile.length()}"))
        assertTrue(status.contains("stagingMs=1"))
        assertTrue(status.contains("nativeLoadMs=2"))
        assertTrue(status.contains("placementMs=3"))
        assertTrue(status.contains("configMs=4"))
        assertTrue(status.contains("nativeSliceMs=5"))
        assertTrue(status.contains("writeGcodeMs=6"))
        assertTrue(status.contains("elapsedMs=21"))
        assertTrue(status.contains("""config={"layer_height":0.2}"""))
    }
}
