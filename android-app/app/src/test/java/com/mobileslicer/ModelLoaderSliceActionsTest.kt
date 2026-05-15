package com.mobileslicer

import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.CalibrationOptions
import com.mobileslicer.calibration.CalibrationType
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspaceMode
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
        assertEquals("Slicing\nPrinter / PLA / 0.20mm", start.statusMessage)
    }

    @Test
    fun sliceUiStartPlanResetsGeneratedOutputAndPreviewState() {
        val plan = planModelLoaderSliceUiStart("Slice in progress")

        assertTrue(plan.sliceInProgress)
        assertEquals(null, plan.gcodeFilePath)
        assertEquals(null, plan.summary)
        assertEquals(null, plan.timing)
        assertEquals(0L, plan.previewKey)
        assertEquals(null, plan.previewStartedAtMs)
        assertEquals(null, plan.firstVisiblePreviewFrameMs)
        assertEquals(WorkspaceMode.Prepare, plan.workspaceMode)
        assertEquals("Slice in progress", plan.statusMessage)
    }

    @Test
    fun sliceRunInputsSnapshotPlateStateAndFallbackFilamentSlot() {
        val store = profileStore()
        val objects = mutableListOf(plateObject(id = 1L))
        val filaments = store.filaments.toMutableList()

        val inputs = captureModelLoaderSliceRunInputs(
            configuration = store.activeConfiguration(),
            calibrationJob = null,
            plateObjects = objects,
            processProfiles = store.processes,
            profileFilaments = filaments,
            plateFilamentSlots = emptyList(),
            fallbackFilament = store.activeConfiguration().filament,
            flushVolumes = null,
            primeTowerPlacementOverride = null,
            printer = store.activeConfiguration().printer,
            modelFilePath = "/tmp/model.stl",
            preparedMesh = null,
            modelBounds = objects.first().bounds,
            modelTransform = objects.first().transform,
            gcodeFileName = "model.gcode"
        )

        objects += plateObject(id = 2L)
        filaments.clear()

        assertEquals(1, inputs.plateObjects.size)
        assertEquals(store.filaments.size, inputs.profileFilaments.size)
        assertEquals(1, inputs.activePlateSlots.single().index)
        assertEquals(store.activeConfiguration().filament.id, inputs.activePlateSlots.single().filamentProfileId)
        assertEquals("/tmp/model.stl", inputs.modelFilePath)
        assertEquals("model.gcode", inputs.gcodeFileName)
    }

    @Test
    fun sliceRunInputsPreserveConfiguredPlateSlots() {
        val store = profileStore()
        val slot = PlateFilamentSlot(
            index = 2,
            filamentProfileId = "custom",
            label = "Custom",
            materialType = "PETG",
            colorHex = "#FFAA00"
        )

        val inputs = captureModelLoaderSliceRunInputs(
            configuration = store.activeConfiguration(),
            calibrationJob = calibrationJob(),
            plateObjects = emptyList(),
            processProfiles = store.processes,
            profileFilaments = store.filaments,
            plateFilamentSlots = listOf(slot),
            fallbackFilament = store.activeConfiguration().filament,
            flushVolumes = null,
            primeTowerPlacementOverride = null,
            printer = store.activeConfiguration().printer,
            modelFilePath = null,
            preparedMesh = null,
            modelBounds = null,
            modelTransform = null,
            gcodeFileName = "calibration.gcode"
        )

        assertEquals(listOf(slot), inputs.activePlateSlots)
        assertEquals(calibrationJob().gcodeFileName(), inputs.calibrationJob?.gcodeFileName())
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
            file.writeText("; printing object selected-model-flowrate_20.stl\nG1 X0 Y0\n")

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

    @Test
    fun calibrationSliceCompletionDoesNotMisclassifyPressureAdvanceAsFlowRate() {
        val file = File.createTempFile("mobileslicer-calibration-", ".gcode")
        try {
            file.writeText(
                """
                ; start pressure advance pattern for layer
                ; calib_flowrate_topinfill_special_order = 0
                G1 X0 Y0
                """.trimIndent()
            )

            val plan = planModelLoaderSliceCompletion(
                result = SliceResult(
                    message = "Slice successful",
                    sliced = true,
                    gcodeFilePath = file.absolutePath,
                    fileName = "ignored.gcode"
                ),
                calibrationJob = calibrationJob(CalibrationType.PressureAdvance),
                fallbackFileName = "fallback.gcode",
                previousPreviewKey = 0L
            )

            assertEquals("Pressure Advance Calibration.gcode", plan.gcodeFileName)
        } finally {
            file.delete()
        }
    }

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

    private fun profileStore(): ProfileStore {
        val printers = listOf(ProfileStoreRepository.fallbackPrinterProfile())
        val filaments = ProfileStoreRepository.defaultFilamentProfiles()
        val processes = listOf(
            newProcessProfileUnchecked(
                0 to "process_fixture",
                1 to "Fixture Process",
                3 to false,
                5 to 0.20f,
                259 to printers.first().id,
                261 to printers.first().nozzleDiameterMm
            )
        )
        return ProfileStore(
            printers = printers,
            filaments = filaments,
            processes = processes,
            selectedPrinterId = printers.first().id,
            selectedFilamentId = filaments.first().id,
            selectedProcessId = processes.first().id
        )
    }

    private fun plateObject(id: Long): PlateObject =
        PlateObject(
            id = id,
            label = "object_$id",
            filePath = "/tmp/object_$id.stl",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = MeshBounds(0f, 0f, 0f, 10f, 10f, 10f),
            transform = ViewerModelTransform(centerXmm = 5f, centerYmm = 5f)
        )
}
