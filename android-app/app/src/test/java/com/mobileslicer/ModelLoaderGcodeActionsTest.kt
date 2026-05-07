package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.CalibrationOptions
import com.mobileslicer.calibration.CalibrationType
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderGcodeActionsTest {
    @Test
    fun gcodeFileActionReturnsNullWithoutGeneratedPath() {
        assertEquals("Export cancelled", exportCancelledStatus())
        assertEquals("Upload cancelled", uploadCancelledStatus())
        assertNull(
            planGcodeFileAction(
                gcodeFilePath = null,
                calibrationJob = null,
                plateObjects = emptyList(),
                summary = null,
                filamentMaterial = "PLA",
                fallbackName = "fallback.gcode"
            )
        )
    }

    @Test
    fun gcodeFileActionUsesFallbackNameForNormalSlice() {
        val action = planGcodeFileAction(
            gcodeFilePath = "/tmp/output.gcode",
            calibrationJob = null,
            plateObjects = listOf(plateObject("cube")),
            summary = null,
            filamentMaterial = "PLA",
            fallbackName = "fallback.gcode"
        )

        assertEquals("/tmp/output.gcode", action?.gcodeFilePath)
        assertEquals("cube_PLA.gcode", action?.fileName)
    }

    @Test
    fun calibrationGcodeFileActionUsesDetectedCalibrationFilename() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText("; printing object selected-model-flowrate_20.stl\nG1 X0 Y0\n")

            val action = planGcodeFileAction(
                gcodeFilePath = file.absolutePath,
                calibrationJob = calibrationJob(),
                plateObjects = emptyList(),
                summary = null,
                filamentMaterial = "PLA",
                fallbackName = "fallback.gcode"
            )

            assertEquals("Filament Flow Rate Calibration.gcode", action?.fileName)
        } finally {
            file.delete()
        }
    }

    @Test
    fun calibrationGcodeFileActionFallsBackToJobFilenameWhenDetectionFails() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText("G1 X0 Y0\n")

            val action = planGcodeFileAction(
                gcodeFilePath = file.absolutePath,
                calibrationJob = calibrationJob(),
                plateObjects = emptyList(),
                summary = null,
                filamentMaterial = "PLA",
                fallbackName = "fallback.gcode"
            )

            assertEquals(calibrationJob().gcodeFileName(), action?.fileName)
        } finally {
            file.delete()
        }
    }

    @Test
    fun uploadRequestUsesRemoteNameAndBlocksConcurrentUpload() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile()
        val request = planPrinterUploadRequest(
            gcodeFilePath = "/tmp/output.gcode",
            sendToPrinterInProgress = false,
            calibrationJob = null,
            remoteFileName = "remote.gcode",
            printerProfile = printer,
            uploadAction = PrinterUploadAction.UploadOnly
        )

        assertNotNull(request)
        assertEquals("remote.gcode", request?.remoteFileName)
        checkNotNull(request)
        assertEquals(
            "Uploading to printer\n${printer.name}",
            printerUploadStartStatus(request)
        )
        assertEquals("Uploading remote.gcode\n42%", printerUploadProgressStatus(request.remoteFileName, 42))
        assertTrue(canRetryPrinterUploadDialog(dialogCanRetry = true, lastRequest = request))
        assertFalse(canRetryPrinterUploadDialog(dialogCanRetry = false, lastRequest = request))
        assertFalse(canRetryPrinterUploadDialog(dialogCanRetry = true, lastRequest = null))
        assertNull(
            planPrinterUploadRequest(
                gcodeFilePath = "/tmp/output.gcode",
                sendToPrinterInProgress = true,
                calibrationJob = null,
                remoteFileName = "remote.gcode",
                printerProfile = printer,
                uploadAction = PrinterUploadAction.UploadOnly
            )
        )
    }

    @Test
    fun bambuUploadRequestKeepsGcodeSourceForOnDemandPackageExport() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostType = PrintHostType.BambuLan
        )
        val request = planPrinterUploadRequest(
            gcodeFilePath = "/tmp/output.gcode",
            sendToPrinterInProgress = false,
            calibrationJob = null,
            remoteFileName = "remote.gcode",
            printerProfile = printer,
            uploadAction = PrinterUploadAction.UploadAndStart
        )

        assertEquals("/tmp/output.gcode", request?.gcodeFilePath)
        assertEquals("remote.gcode", request?.remoteFileName)
    }

    @Test
    fun uploadRequestUsesCalibrationFilenameWhenAvailable() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText("; printing object selected-model-flowrate_20.stl\nG1 X0 Y0\n")
            val request = planPrinterUploadRequest(
                gcodeFilePath = file.absolutePath,
                sendToPrinterInProgress = false,
                calibrationJob = calibrationJob(),
                remoteFileName = "remote.gcode",
                printerProfile = ProfileStoreRepository.fallbackPrinterProfile(),
                uploadAction = PrinterUploadAction.UploadAndStart
            )

            assertEquals("Filament Flow Rate Calibration.gcode", request?.remoteFileName)
            assertEquals(PrinterUploadAction.UploadAndStart, request?.uploadAction)
        } finally {
            file.delete()
        }
    }

    @Test
    fun calibrationGcodeFileActionDoesNotLetFlowConfigOverrideCalibrationJobType() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText("; calib_flowrate_topinfill_special_order = 0\nM104 S230\n")

            val action = planGcodeFileAction(
                gcodeFilePath = file.absolutePath,
                calibrationJob = calibrationJob(CalibrationType.TemperatureTower),
                plateObjects = emptyList(),
                summary = null,
                filamentMaterial = "PLA",
                fallbackName = "fallback.gcode"
            )

            assertEquals("Temperature Tower Calibration.gcode", action?.fileName)
        } finally {
            file.delete()
        }
    }

    @Test
    fun uploadStartStatusMatchesUploadAction() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile()

        assertEquals(
            "Uploading and starting print\n${printer.name}",
            printerUploadStartStatus(
                PrinterUploadRequest(
                    gcodeFilePath = "/tmp/output.gcode",
                    remoteFileName = "remote.gcode",
                    printerProfile = printer,
                    uploadAction = PrinterUploadAction.UploadAndStart
                )
            )
        )
        assertEquals(
            "Uploading to queue\n${printer.name}",
            printerUploadStartStatus(
                PrinterUploadRequest(
                    gcodeFilePath = "/tmp/output.gcode",
                    remoteFileName = "remote.gcode",
                    printerProfile = printer,
                    uploadAction = PrinterUploadAction.Queue
                )
            )
        )
    }

    @Test
    fun printerOpenPlanFailsWithoutHost() {
        val result = printerOpenPlan(ProfileStoreRepository.fallbackPrinterProfile())

        assertTrue(result.isFailure)
        assertEquals(
            "Printer unavailable\nEnter a printer host or printer web UI URL in the profile.",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun printerOpenPlanUsesConfiguredWebUi() {
        val result = printerOpenPlan(
            ProfileStoreRepository.fallbackPrinterProfile().copy(
                printHostWebUi = "http://printer.local"
            )
        )

        assertEquals("http://printer.local/", result.getOrNull())
    }

    private fun plateObject(label: String): PlateObject =
        PlateObject(
            id = 1L,
            label = label,
            filePath = "/tmp/$label.stl",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 10f),
            transform = ViewerModelTransform(centerXmm = 5f, centerYmm = 5f)
        )

    private fun calibrationJob(type: CalibrationType = CalibrationType.FlowRate): CalibrationJob =
        CalibrationJob(
            type = type,
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
