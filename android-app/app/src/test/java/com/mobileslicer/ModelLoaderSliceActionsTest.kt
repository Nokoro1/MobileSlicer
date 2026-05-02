package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.CalibrationOptions
import com.mobileslicer.calibration.CalibrationType
import com.mobileslicer.workspace.SliceResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderSliceActionsTest {
    @Test
    fun sliceStartIgnoresUnavailableOrBusyRequests() {
        assertFalse(canRequestModelLoaderSlice(modelLoaded = false, sliceInProgress = false, sendToPrinterInProgress = false))
        assertFalse(canRequestModelLoaderSlice(modelLoaded = true, sliceInProgress = true, sendToPrinterInProgress = false))
        assertFalse(canRequestModelLoaderSlice(modelLoaded = true, sliceInProgress = false, sendToPrinterInProgress = true))

        val plan = planModelLoaderSliceStart(
            modelLoaded = false,
            sliceInProgress = false,
            sendToPrinterInProgress = false,
            generatedFootprintFits = true,
            printableVolumePreflightFailure = null,
            nativeSliceTitle = "Printer / PLA / 0.20mm"
        )

        assertEquals(ModelLoaderSliceStartPlan.Ignore, plan)
    }

    @Test
    fun sliceStartReturnsPrintableVolumeFailureBeforeStarting() {
        val plan = planModelLoaderSliceStart(
            modelLoaded = true,
            sliceInProgress = false,
            sendToPrinterInProgress = false,
            generatedFootprintFits = false,
            printableVolumePreflightFailure = null,
            nativeSliceTitle = "Printer / PLA / 0.20mm"
        )

        val failure = plan as ModelLoaderSliceStartPlan.Fail
        assertEquals(ModelLoaderPrintableVolumeExceededMessage, failure.result.message)
        assertFalse(failure.result.sliced)
    }

    @Test
    fun sliceStartReturnsPreflightFailureBeforeStarting() {
        val plan = planModelLoaderSliceStart(
            modelLoaded = true,
            sliceInProgress = false,
            sendToPrinterInProgress = false,
            generatedFootprintFits = true,
            printableVolumePreflightFailure = "Slice failed\nToo tall",
            nativeSliceTitle = "Printer / PLA / 0.20mm"
        )

        val failure = plan as ModelLoaderSliceStartPlan.Fail
        assertEquals("Slice failed\nToo tall", failure.result.message)
        assertFalse(failure.result.sliced)
    }

    @Test
    fun sliceStartBuildsNativeInputStatus() {
        val plan = planModelLoaderSliceStart(
            modelLoaded = true,
            sliceInProgress = false,
            sendToPrinterInProgress = false,
            generatedFootprintFits = true,
            printableVolumePreflightFailure = null,
            nativeSliceTitle = "Printer / PLA / 0.20mm"
        )

        val start = plan as ModelLoaderSliceStartPlan.Start
        assertEquals("Slice in progress\nNative slice inputs: Printer / PLA / 0.20mm", start.statusMessage)
    }

    @Test
    fun sliceCompletionUsesResultFileNameAndAdvancesPreviewKeyOnlyForGeneratedGcode() {
        val plan = planModelLoaderSliceCompletion(
            result = SliceResult(
                message = "Slice successful",
                sliced = true,
                gcodeFilePath = "/tmp/model.gcode",
                fileName = "model_PLA_20m.gcode"
            ),
            calibrationJob = null,
            fallbackFileName = "fallback.gcode",
            previousPreviewKey = 4L
        )

        assertEquals("/tmp/model.gcode", plan.gcodeFilePath)
        assertEquals("model_PLA_20m.gcode", plan.gcodeFileName)
        assertEquals(5L, plan.previewKey)
        assertEquals("Slice successful", plan.statusMessage)
        assertTrue(plan.completionResult.sliced)
    }

    @Test
    fun sliceCompletionResetsPreviewKeyForFailedSlice() {
        val plan = planModelLoaderSliceCompletion(
            result = SliceResult(message = "Slice failed", sliced = false, fileName = "model.gcode"),
            calibrationJob = null,
            fallbackFileName = "fallback.gcode",
            previousPreviewKey = 4L
        )

        assertEquals("model.gcode", plan.gcodeFileName)
        assertEquals(0L, plan.previewKey)
    }

    @Test
    fun calibrationSliceCompletionUsesDetectedCalibrationName() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText("; calib_flowrate_topinfill_special_order\nG1 X0 Y0\n")

            val plan = planModelLoaderSliceCompletion(
                result = SliceResult(
                    message = "Slice successful",
                    sliced = true,
                    gcodeFilePath = file.absolutePath,
                    fileName = "ignored.gcode"
                ),
                calibrationJob = calibrationJob(),
                fallbackFileName = "fallback.gcode",
                previousPreviewKey = 0L
            )

            assertEquals("Filament Flow Rate Calibration.gcode", plan.gcodeFileName)
            assertEquals(1L, plan.previewKey)
        } finally {
            file.delete()
        }
    }

    private fun calibrationJob(): CalibrationJob =
        CalibrationJob(
            type = CalibrationType.FlowRate,
            printerName = "Printer",
            filamentName = "PLA",
            processName = "Process",
            nozzleDiameterMm = 0.4f,
            options = CalibrationOptions(
                startValue = "0",
                endValue = "0",
                stepValue = "0"
            )
        )
}
