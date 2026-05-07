package com.mobileslicer

import com.mobileslicer.calibration.CALIBRATION_TEST_MODEL_FAST
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.CalibrationOptions
import com.mobileslicer.calibration.CalibrationType
import com.mobileslicer.calibration.applyCalibrationResult
import com.mobileslicer.calibration.defaultCalibrationOptions
import com.mobileslicer.calibration.resultApplySpec
import com.mobileslicer.calibration.unsupportedFirmwareMessage
import com.mobileslicer.profiles.GcodeFlavor
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.newProcessProfileUnchecked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationResultApplyTest {
    @Test
    fun pressureAdvanceResultUpdatesSelectedFilament() {
        val job = job(CalibrationType.PressureAdvance)
        val outcome = profileStore().applyCalibrationResult(job, "0.037")

        assertTrue(outcome.applied)
        val filament = outcome.store.filaments.single()
        assertTrue(filament.pressureAdvanceEnabled)
        assertEquals(0.037f, filament.pressureAdvance, 0.0001f)
    }

    @Test
    fun filamentCalibrationResultsMapToOrcaFilamentFields() {
        var store = profileStore()
        store = store.applyCalibrationResult(
            job(CalibrationType.FlowRate),
            "0.985"
        ).store
        store = store.applyCalibrationResult(
            job(CalibrationType.TemperatureTower),
            "223"
        ).store
        store = store.applyCalibrationResult(
            job(CalibrationType.MaxVolumetricSpeed),
            "17.5"
        ).store
        store = store.applyCalibrationResult(
            job(CalibrationType.Retraction),
            "0.72"
        ).store

        val filament = store.filaments.single()
        assertEquals(0.985f, filament.flowRatio, 0.0001f)
        assertEquals(223, filament.nozzleTemperatureInitialLayerC)
        assertEquals(223, filament.nozzleTemperatureC)
        assertEquals(17.5f, filament.maxVolumetricSpeedMm3PerSec, 0.0001f)
        assertEquals(0.72f, filament.retractionLengthMm ?: -1f, 0.0001f)
    }

    @Test
    fun corneringResultUpdatesJunctionDeviationWhenProcessUsesIt() {
        val process = processFixture().withValues("defaultJunctionDeviationMm" to 0.02f)
        val store = profileStore(process = process)
        val outcome = store.applyCalibrationResult(
            job(CalibrationType.Cornering),
            "0.045"
        )

        assertTrue(outcome.applied)
        assertEquals(0.045f, outcome.store.processes.single().defaultJunctionDeviationMm, 0.0001f)
    }

    @Test
    fun corneringResultFallsBackToProcessJerkFields() {
        val store = profileStore(process = processFixture().withValues("defaultJunctionDeviationMm" to 0f))
        val outcome = store.applyCalibrationResult(
            job(CalibrationType.Cornering),
            "8.5"
        )

        val process = outcome.store.processes.single()
        assertTrue(outcome.applied)
        assertEquals(8.5f, process.defaultJerkMmPerSec, 0.0001f)
        assertEquals(8.5f, process.outerWallJerkMmPerSec, 0.0001f)
        assertEquals(8.5f, process.innerWallJerkMmPerSec, 0.0001f)
        assertEquals(8.5f, process.travelJerkMmPerSec, 0.0001f)
    }

    @Test
    fun unsupportedCalibrationResultsDoNotExposeSaveSpec() {
        assertNull(job(CalibrationType.Tolerance).resultApplySpec())
        assertNull(job(CalibrationType.Vfa).resultApplySpec())
        assertNull(job(CalibrationType.InputShapingFrequency).resultApplySpec())
        assertNotNull(job(CalibrationType.FlowRate).resultApplySpec())
    }

    @Test
    fun toleranceCalibrationUsesOrcaToleranceAsset() {
        val job = job(CalibrationType.Tolerance)
        val json = org.json.JSONObject(job.applyTemporaryOverrides("{}"))

        assertEquals(listOf("calib_stl/tolerance/OrcaToleranceTest.stl"), job.orcaCalibrationAssetPaths())
        assertTrue(json.getBoolean("mobile_slicer_calibration_tolerance_model"))
    }

    @Test
    fun temperatureDefaultsMatchOrcaFilamentPresets() {
        assertTemperatureDefaults("ABS", "ABS/ASA", "270", "230")
        assertTemperatureDefaults("ASA", "ABS/ASA", "270", "230")
        assertTemperatureDefaults("PETG", "PETG", "250", "230")
        assertTemperatureDefaults("PCTG", "PCTG", "280", "240")
        assertTemperatureDefaults("TPU", "TPU", "240", "210")
        assertTemperatureDefaults("PA-CF", "PA-CF", "320", "280")
        assertTemperatureDefaults("PET-CF", "PET-CF", "320", "280")
        assertTemperatureDefaults("PLA", "PLA", "230", "190")
    }

    @Test
    fun inputShapingOptionsCarryOrcaTestModelAndShaperMetadata() {
        val job = job(
            CalibrationType.InputShapingDamping,
            CalibrationOptions(
                testModel = CALIBRATION_TEST_MODEL_FAST,
                shaperType = "mzv",
                startValue = "0.1",
                endValue = "0.3",
                stepValue = "0.05"
            )
        )
        val json = org.json.JSONObject(job.applyTemporaryOverrides("{}"))

        assertEquals(1, json.getInt("calibration_test_model"))
        assertEquals("mzv", json.getString("calibration_shaper_type"))
    }

    @Test
    fun inputShapingCalibrationRequiresSupportedFirmwareFlavor() {
        val job = job(CalibrationType.InputShapingFrequency)

        assertNotNull(job.unsupportedFirmwareMessage(GcodeFlavor.MarlinLegacy))
        assertNull(job.unsupportedFirmwareMessage(GcodeFlavor.Marlin2))
        assertNull(job.unsupportedFirmwareMessage(GcodeFlavor.Klipper))
        assertNull(job.unsupportedFirmwareMessage(GcodeFlavor.RepRapFirmware))
        assertNull(job(CalibrationType.PressureAdvance).unsupportedFirmwareMessage(GcodeFlavor.MarlinLegacy))
    }

    @Test
    fun invalidResultDoesNotChangeStore() {
        val store = profileStore()
        val outcome = store.applyCalibrationResult(
            job(CalibrationType.FlowRate),
            "not-a-number"
        )

        assertFalse(outcome.applied)
        assertEquals(store, outcome.store)
    }

    private fun profileStore(
        process: com.mobileslicer.profiles.ProcessProfile = processFixture()
    ): ProfileStore {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(id = "filament", name = "PLA")
        return ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )
    }

    private fun processFixture() = newProcessProfileUnchecked(
        0 to "process",
        1 to "Process",
        3 to false,
        5 to 0.20f
    )

    private fun job(
        type: CalibrationType,
        options: CalibrationOptions = CalibrationOptions(
            startValue = "0",
            endValue = "1",
            stepValue = "0.1"
        )
    ) = CalibrationJob(
        type = type,
        printerName = "Printer",
        filamentName = "PLA",
        processName = "Process",
        nozzleDiameterMm = 0.4f,
        options = options
    )

    private fun assertTemperatureDefaults(
        filamentName: String,
        expectedType: String,
        expectedStart: String,
        expectedEnd: String
    ) {
        val options = defaultCalibrationOptions(CalibrationType.TemperatureTower, filamentName)
        assertEquals(expectedType, options.filamentType)
        assertEquals(expectedStart, options.startValue)
        assertEquals(expectedEnd, options.endValue)
        assertEquals("5", options.stepValue)
    }
}
