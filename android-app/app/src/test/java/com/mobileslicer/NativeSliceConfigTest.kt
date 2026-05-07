package com.mobileslicer

import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.OrcaFilamentImportBundle
import com.mobileslicer.profiles.OrcaFilamentPreset
import com.mobileslicer.profiles.OrcaProcessPresetBundle
import com.mobileslicer.profiles.FilamentProfileEditorDraft
import com.mobileslicer.profiles.NativeSliceConfigCache
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfileEditorDraft
import com.mobileslicer.profiles.ProcessProfileEditorDraft
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.toNativeSliceConfigBuildResult
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.profiles.orcaFilamentIdentityMatchesPreset
import com.mobileslicer.profiles.toImportedFilamentProfile
import com.mobileslicer.profiles.toImportedProcessProfile
import com.mobileslicer.profiles.toFilamentProfile
import com.mobileslicer.profiles.toNativeSliceConfigJson
import com.mobileslicer.profiles.toPrinterProfile
import com.mobileslicer.profiles.toProcessProfile
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectPaint
import com.mobileslicer.workspace.SerializedPaintLayer
import com.mobileslicer.workspace.SerializedPaintTriangle
import com.mobileslicer.workspace.SerializedPaintVolumeLayer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSliceConfigTest {
    @Test
    fun nativeProcessSpeedControlsEmitCanonicalOrcaSpeedKeys() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_speed_keys")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_speed_keys",
            printerProfileId = printer.id
        )
        val process = newProcessProfileUnchecked(
            0 to "process_speed_keys",
            1 to "Speed Keys",
            4 to 0.24f,
            6 to 24f,
            7 to 18f,
            8 to 65,
            15 to 44f,
            16 to 55f,
            17 to 33f,
            18 to 160f,
            48 to 22f,
            49 to 0f,
            67 to 88f,
            68 to 77f,
            69 to 11f,
            259 to printer.id
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertEquals(0.24, nativeConfig.optDouble("initial_layer_print_height"), 0.0001)
        assertEquals(24.0, nativeConfig.optDouble("initial_layer_speed"), 0.0001)
        assertEquals(18.0, nativeConfig.optDouble("initial_layer_infill_speed"), 0.0001)
        assertEquals("65%", nativeConfig.optString("initial_layer_travel_speed"))
        assertEquals(44.0, nativeConfig.optDouble("outer_wall_speed"), 0.0001)
        assertEquals(55.0, nativeConfig.optDouble("inner_wall_speed"), 0.0001)
        assertEquals(33.0, nativeConfig.optDouble("top_surface_speed"), 0.0001)
        assertEquals(160.0, nativeConfig.optDouble("travel_speed"), 0.0001)
        assertEquals(22.0, nativeConfig.optDouble("bridge_speed"), 0.0001)
        assertEquals(0.0, nativeConfig.optDouble("small_perimeter_speed"), 0.0001)
        assertEquals(88.0, nativeConfig.optDouble("sparse_infill_speed"), 0.0001)
        assertEquals(77.0, nativeConfig.optDouble("internal_solid_infill_speed"), 0.0001)
        assertEquals(11.0, nativeConfig.optDouble("gap_infill_speed"), 0.0001)
    }

    @Test
    fun importedOrcaProcessReadsCanonicalInitialLayerSpeedKeys() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_import_speed_keys",
            nozzleDiameterMm = 0.4f
        )
        val imported = OrcaProcessPresetBundle(
            machineName = "Fixture 0.4",
            nozzleDiameterMm = 0.4f,
            name = "0.20mm Speed Fixture",
            rawName = "0.20mm Speed Fixture",
            profilePath = "Fixture/process/0.20mm Speed Fixture.json",
            rawProcessJson = "{}",
            resolvedProcessJson = """
                {
                  "print_settings_id":"0.20mm Speed Fixture",
                  "initial_layer_print_height":["0.28"],
                  "initial_layer_speed":["31"],
                  "initial_layer_infill_speed":["17"],
                  "initial_layer_travel_speed":"42%",
                  "outer_wall_speed":45,
                  "inner_wall_speed":55,
                  "top_surface_speed":35,
                  "travel_speed":180,
                  "sparse_infill_speed":90,
                  "internal_solid_infill_speed":80,
                  "gap_infill_speed":12,
                  "skirt_loops":4
                }
            """.trimIndent(),
            resolvedSourceChain = emptyList()
        ).toImportedProcessProfile(printer)

        assertEquals(0.28f, imported.firstLayerHeightMm, 0.0001f)
        assertEquals(31f, imported.firstLayerPrintSpeedMmPerSec, 0.0001f)
        assertEquals(17f, imported.firstLayerInfillSpeedMmPerSec, 0.0001f)
        assertEquals(42, imported.firstLayerTravelSpeedPercent)
        assertEquals(45f, imported.outerWallSpeedMmPerSec, 0.0001f)
        assertEquals(55f, imported.innerWallSpeedMmPerSec, 0.0001f)
        assertEquals(35f, imported.topSurfaceSpeedMmPerSec, 0.0001f)
        assertEquals(180f, imported.travelSpeedMmPerSec, 0.0001f)
        assertEquals(90f, imported.sparseInfillSpeedMmPerSec, 0.0001f)
        assertEquals(80f, imported.internalSolidInfillSpeedMmPerSec, 0.0001f)
        assertEquals(12f, imported.gapInfillSpeedMmPerSec, 0.0001f)
        assertEquals(4, imported.skirts)
    }

    @Test
    fun nativeSliceConfigBuildCacheHitsButInvalidatesOnChangedProcessOverride() {
        NativeSliceConfigCache.clear()
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_cache",
            name = "Cache Printer",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Cache Printer 0.4 nozzle"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_cache",
            name = "Cache PLA",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Cache PLA","filament_type":"PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_cache",
            1 to "0.20mm Cache",
            259 to printer.id,
            265 to """{"print_settings_id":"0.20mm Cache","wall_loops":3}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val first = store.activeConfiguration().toNativeSliceConfigBuildResult()
        val second = store.activeConfiguration().toNativeSliceConfigBuildResult()
        val changed = store.copy(
            processes = listOf(process.withValues("orcaProcessOverridesJson" to """{"wall_loops":6}"""))
        ).activeConfiguration().toNativeSliceConfigBuildResult()

        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        assertFalse(changed.cacheHit)
        assertEquals(3, JSONObject(first.json).optInt("wall_loops"))
        assertEquals(6, JSONObject(changed.json).optInt("wall_loops"))
    }

    @Test
    fun positiveBrimWidthUsesManualOuterBrimForNativeSliceConfig() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_brim_width")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_brim_width",
            printerProfileId = printer.id
        )
        val process = newProcessProfileUnchecked(
            0 to "process_brim_width",
            1 to "Manual Brim Width",
            200 to 8f,
            259 to printer.id
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertEquals("outer_only", nativeConfig.optString("brim_type"))
        assertEquals(8.0, nativeConfig.optDouble("brim_width"), 0.0001)
    }

    @Test
    fun positiveOrcaBrimWidthOverrideUsesManualOuterBrim() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_orca_brim_width",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Brim Width Printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_orca_brim_width",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Brim Width PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_orca_brim_width",
            1 to "Orca Manual Brim Width",
            259 to printer.id,
            265 to """{"print_settings_id":"Orca Manual Brim Width","brim_type":"auto_brim","brim_width":0}""",
            267 to """{"brim_width":8}"""
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertEquals("outer_only", nativeConfig.optString("brim_type"))
        assertEquals(8.0, nativeConfig.optDouble("brim_width"), 0.0001)
    }

    @Test
    fun coreSlicingRegressionParametersReachNativeConfig() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_core_regression")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_core_regression",
            printerProfileId = printer.id,
            nozzleTemperatureInitialLayerC = 215,
            nozzleTemperatureC = 225,
            bedTemperatureInitialLayerC = 55,
            bedTemperatureC = 60
        )
        val process = newProcessProfileUnchecked(
            0 to "process_core_regression",
            1 to "Core Regression",
            124 to 4,
            125 to 35,
            133 to true,
            199 to 3,
            200 to 6f,
            259 to printer.id
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertEquals(215, nativeConfig.optInt("nozzle_temperature_initial_layer"))
        assertEquals(225, nativeConfig.optInt("nozzle_temperature"))
        assertEquals(55, nativeConfig.optInt("bed_temperature_initial_layer"))
        assertEquals(60, nativeConfig.optInt("bed_temperature"))
        assertEquals(4, nativeConfig.optInt("wall_loops"))
        assertEquals(35, nativeConfig.optInt("sparse_infill_density"))
        assertTrue(nativeConfig.optBoolean("enable_support", false))
        assertEquals(3, nativeConfig.optInt("skirt_loops"))
        assertEquals(3, nativeConfig.optInt("skirts"))
        assertEquals("outer_only", nativeConfig.optString("brim_type"))
        assertEquals(6.0, nativeConfig.optDouble("brim_width"), 0.0001)
    }

    @Test
    fun nativeSliceConfigCacheCanonicalizesEquivalentResolvedJson() {
        NativeSliceConfigCache.clear()
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_canonical",
            name = "Canonical Printer",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Canonical Printer","printable_area":[[0,0],[220,220]]}"""
        )
        val printerWithReformattedJson = printer.copy(
            orcaResolvedMachineJson = """
                {
                  "printable_area": [
                    [0, 0],
                    [220, 220]
                  ],
                  "printer_settings_id": "Canonical Printer"
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_canonical",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Canonical PLA","filament_type":"PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_canonical",
            1 to "0.20mm Canonical",
            259 to printer.id,
            265 to """{"print_settings_id":"0.20mm Canonical","wall_loops":3}"""
        )

        val first = nativeConfigBuildResultFor(printer, filament, process)
        val second = nativeConfigBuildResultFor(printerWithReformattedJson, filament, process)

        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        assertEquals(JSONObject(first.json).toString(), JSONObject(second.json).toString())
        assertEquals(
            criticalNativeConfigSnapshot(JSONObject(first.json)),
            criticalNativeConfigSnapshot(JSONObject(second.json))
        )
    }

    @Test
    fun nativeSliceConfigBuildTimingAndCriticalSnapshotStayBounded() {
        NativeSliceConfigCache.clear()
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_timing",
            name = "Timing Printer",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Timing Printer","extruders_count":1}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_timing",
            name = "Timing PLA",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Timing PLA","filament_id":"TIME01","filament_type":"PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_timing",
            1 to "0.20mm Timing",
            259 to printer.id,
            265 to """{"print_settings_id":"0.20mm Timing","enable_prime_tower":true,"purge_in_prime_tower":true}"""
        )

        val startedAtNs = System.nanoTime()
        val build = nativeConfigBuildResultFor(printer, filament, process)
        val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000.0
        val snapshot = criticalNativeConfigSnapshot(JSONObject(build.json))

        assertFalse(build.cacheHit)
        assertTrue(build.timing.keyMs >= 0L)
        assertTrue(build.timing.totalMs >= 0L)
        assertTrue("native config build exceeded smoke-test budget: $elapsedMs ms", elapsedMs < 5_000.0)
        assertEquals("Timing Printer", snapshot["printer_settings_id"])
        assertEquals("Timing PLA", snapshot["filament_settings_id"])
        assertEquals("0.20mm Timing", snapshot["print_settings_id"])
        assertEquals(false, snapshot["enable_prime_tower"])
    }

    @Test
    fun nativeSliceConfigPreservesResolvedOrcaJsonAndCachesStableResult() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_04",
            name = "Audit Printer",
            orcaResolvedMachineJson = """{"printer_passthrough_key":"printer-value","shared_profile_key":"printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_abs",
            name = "ABS",
            printerProfileId = printer.id,
            orcaResolvedFilamentJson = """{"filament_passthrough_key":"filament-value","shared_profile_key":"filament"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_standard",
            1 to "0.20mm Standard",
            259 to printer.id,
            265 to """{"process_passthrough_key":"process-value","shared_profile_key":"process"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val firstConfig = store.activeConfiguration().toNativeSliceConfigJson()
        val secondConfig = store.activeConfiguration().toNativeSliceConfigJson()
        val nativeConfig = JSONObject(firstConfig)

        assertEquals(firstConfig, secondConfig)
        assertEquals("printer-value", nativeConfig.optString("printer_passthrough_key"))
        assertEquals("filament-value", nativeConfig.optString("filament_passthrough_key"))
        assertEquals("process-value", nativeConfig.optString("process_passthrough_key"))
        assertEquals("process", nativeConfig.optString("shared_profile_key"))
    }

    @Test
    fun importedOrcaFilamentPreservesResolvedJsonAndMappedValues() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_04")
        val preset = OrcaFilamentPreset(
            name = "Test PLA",
            rawName = "Test PLA @ Audit",
            family = "Audit",
            materialType = "PLA",
            vendor = "Fallback Vendor",
            defaultFilamentColor = "#123456",
            profilePath = "filaments/test.json",
            importBundleAssetPath = "",
            compatiblePrinters = listOf("Audit Printer 0.4 nozzle"),
            searchText = "test pla"
        )
        val resolvedJson = """
            {
              "filament_type": "PETG",
              "filament_vendor": "Mapped Vendor",
              "default_filament_colour": "#ABCDEF",
              "nozzle_temperature": 235,
              "filament_max_volumetric_speed": 9.5,
              "filament_passthrough_key": "kept"
            }
        """.trimIndent()
        val bundle = OrcaFilamentImportBundle(
            rawFilamentJson = """{"name":"Test PLA"}""",
            resolvedFilamentJson = resolvedJson,
            resolvedSourceChain = listOf("base", "test")
        )

        val imported = preset.toImportedFilamentProfile(bundle, printer)

        assertEquals("PETG", imported.materialType)
        assertEquals("Mapped Vendor", imported.vendor)
        assertEquals("#ABCDEF", imported.defaultFilamentColor)
        assertEquals(235, imported.nozzleTemperatureC)
        assertEquals(9.5f, imported.maxVolumetricSpeedMm3PerSec)
        assertEquals(printer.id, imported.printerProfileId)
        assertEquals(resolvedJson, imported.orcaResolvedFilamentJson)
        assertEquals("kept", JSONObject(imported.orcaResolvedFilamentJson).optString("filament_passthrough_key"))
    }

    @Test
    fun singleMaterialNativeConfigPreservesResolvedOrcaAmsIdentityButDisablesTower() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_ams",
            name = "AMS Printer",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"single_extruder_multi_material":true,"printer_passthrough_key":"kept"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_pla",
            name = "PLA",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_colour":"#112233","filament_passthrough_key":"kept"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_ams",
            1 to "0.20mm AMS",
            259 to printer.id,
            265 to """{"enable_prime_tower":true,"purge_in_prime_tower":true,"single_extruder_multi_material_priming":true,"allow_multicolor_oneplate":true,"process_passthrough_key":"kept"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("kept", nativeConfig.optString("printer_passthrough_key"))
        assertEquals("kept", nativeConfig.optString("filament_passthrough_key"))
        assertEquals("kept", nativeConfig.optString("process_passthrough_key"))
        assertFalse(nativeConfig.optBoolean("enable_prime_tower", true))
        assertFalse(nativeConfig.optBoolean("purge_in_prime_tower", true))
        assertEquals(true, nativeConfig.optBoolean("single_extruder_multi_material", false))
        assertTrue(nativeConfig.optBoolean("single_extruder_multi_material_priming", false))
        assertTrue(nativeConfig.optBoolean("allow_multicolor_oneplate", false))
    }

    @Test
    fun bambuSingleMaterialGoldenNativeConfigDisablesTowerAndKeepsResolvedIdentity() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_bambu_single",
            name = "Bambu Lab P1S 0.4 nozzle",
            profileSource = "orca",
            printerAgent = "bambu",
            printerModel = "Bambu Lab P1S",
            orcaResolvedMachineJson = """
                {
                  "printer_settings_id":"Bambu Lab P1S 0.4 nozzle",
                  "printer_model":"Bambu Lab P1S",
                  "extruders_count":1,
                  "default_bed_type":"Textured PEI Plate"
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bambu_single",
            name = "Bambu PLA Basic",
            printerProfileId = printer.id,
            profileSource = "orca",
            materialType = "PLA",
            defaultFilamentColor = "#00AA99",
            orcaResolvedFilamentJson = """
                {
                  "filament_settings_id":"Bambu PLA Basic",
                  "filament_id":"GFA00",
                  "filament_type":"PLA",
                  "filament_colour":"#00AA99",
                  "nozzle_temperature":220
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_bambu_single",
            1 to "0.20mm Standard @BBL P1S",
            258 to "orca",
            259 to printer.id,
            265 to """{"print_settings_id":"0.20mm Standard @BBL P1S","enable_prime_tower":true,"purge_in_prime_tower":true}"""
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertFalse(nativeConfig.optBoolean("enable_prime_tower", true))
        assertFalse(nativeConfig.optBoolean("purge_in_prime_tower", true))
        assertEquals("Bambu Lab P1S 0.4 nozzle", nativeConfig.optString("printer_settings_id"))
        assertEquals("Bambu PLA Basic", nativeConfig.optString("filament_settings_id"))
        assertEquals("GFA00", nativeConfig.optString("filament_ids"))
        assertEquals("Textured PEI Plate", nativeConfig.optString("curr_bed_type"))
        assertCriticalNativeConfigContains(
            nativeConfig,
            mapOf(
                "printer_settings_id" to "Bambu Lab P1S 0.4 nozzle",
                "filament_settings_id" to "Bambu PLA Basic",
                "filament_ids" to "GFA00",
                "filament_type" to "PLA",
                "print_settings_id" to "0.20mm Standard @BBL P1S",
                "curr_bed_type" to "Textured PEI Plate",
                "enable_prime_tower" to false,
                "purge_in_prime_tower" to false
            )
        )
    }

    @Test
    fun bambuMultiMaterialPlateGoldenNativeConfigEnablesTowerAndVectorsSlots() {
        val filaments = (1..2).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_bambu_multi_$index",
                name = "Bambu PLA $index",
                profileSource = "orca",
                materialType = "PLA",
                defaultFilamentColor = if (index == 1) "#112233" else "#445566",
                orcaResolvedFilamentJson = """
                    {
                      "filament_settings_id":"Bambu PLA $index",
                      "filament_id":"GFA0$index",
                      "filament_type":"PLA",
                      "filament_colour":"${if (index == 1) "#112233" else "#445566"}"
                    }
                """.trimIndent()
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val plateObjects = listOf(
            testPlateObject(id = 1L, filamentSlotIndex = 1),
            testPlateObject(id = 2L, filamentSlotIndex = 2)
        )

        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = """{"enable_prime_tower":true,"purge_in_prime_tower":false}""",
                slots = slots,
                plateObjects = plateObjects,
                filaments = filaments,
                flushVolumes = null
            )
        )

        assertEquals(2, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertTrue(result.optBoolean("enable_prime_tower", false))
        assertTrue(result.optBoolean("purge_in_prime_tower", false))
        assertJsonArrayEquals(listOf("Bambu PLA 1", "Bambu PLA 2"), result.getJSONArray("filament_settings_id"))
        assertJsonArrayEquals(listOf(1, 2), result.getJSONArray("filament_self_index"))
        assertJsonArrayEquals(listOf(1, 1), result.getJSONArray("filament_map"))
        assertCriticalNativeConfigContains(
            result,
            mapOf(
                "filament_settings_id" to listOf("Bambu PLA 1", "Bambu PLA 2"),
                "filament_ids" to listOf("filament_bambu_multi_1", "filament_bambu_multi_2"),
                "filament_type" to listOf("PLA", "PLA"),
                "enable_prime_tower" to true,
                "purge_in_prime_tower" to true,
                "mobile_slicer_active_filament_slot_count" to 2
            )
        )
    }

    @Test
    fun colorPaintReferencedSlotsAreIncludedInNativePlateConfig() {
        val filaments = (1..2).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_$index",
                name = "Paint Slot $index",
                defaultFilamentColor = if (index == 1) "#111111" else "#EEEEEE"
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val objectPaint = PlateObjectPaint(
            color = paintLayer(
                mode = PaintMode.Color,
                referencedSlots = setOf(2)
            )
        )
        val result = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = "{}",
            slots = slots,
            plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 1, paint = objectPaint)),
            filaments = filaments,
            flushVolumes = null
        )
        val nativeConfig = JSONObject(result.json)

        assertEquals(2, nativeConfig.optInt("mobile_slicer_active_filament_slot_count"))
        assertJsonArrayEquals(listOf("Paint Slot 1", "Paint Slot 2"), nativeConfig.getJSONArray("filament_settings_id"))
        assertFalse(nativeConfig.optBoolean("enable_prime_tower", true))
        assertFalse(nativeConfig.optBoolean("purge_in_prime_tower", true))
    }

    @Test
    fun colorPaintKeepsNativeFilamentSlotsContiguousThroughHighestPaintedSlot() {
        val filaments = (1..4).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_$index",
                name = "Paint Slot $index",
                defaultFilamentColor = when (index) {
                    1 -> "#8FC1FF"
                    2 -> "#FF7043"
                    3 -> "#FFD166"
                    else -> "#AB34D6"
                }
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val objectPaint = PlateObjectPaint(
            color = paintLayer(
                mode = PaintMode.Color,
                referencedSlots = setOf(4)
            )
        )
        val result = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = "{}",
            slots = slots,
            plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 1, paint = objectPaint)),
            filaments = filaments,
            flushVolumes = null
        )
        val nativeConfig = JSONObject(result.json)

        assertEquals(4, nativeConfig.optInt("mobile_slicer_active_filament_slot_count"))
        assertJsonArrayEquals(
            listOf("Paint Slot 1", "Paint Slot 2", "Paint Slot 3", "Paint Slot 4"),
            nativeConfig.getJSONArray("filament_settings_id")
        )
        assertJsonArrayEquals(listOf(1, 2, 3, 4), nativeConfig.getJSONArray("filament_self_index"))
        assertFalse(nativeConfig.optBoolean("enable_prime_tower", true))
        assertFalse(nativeConfig.optBoolean("purge_in_prime_tower", true))
    }

    @Test
    fun paintAwareNativeConfigEnablesSupportAndPreservesFuzzyProfileSettings() {
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(id = "filament_paint_config")
        val slot = filament.toPlateFilamentSlot(index = 1)
        val paint = PlateObjectPaint(
            support = paintLayer(PaintMode.Support),
            fuzzySkin = paintLayer(PaintMode.FuzzySkin)
        )
        val result = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = """
                {
                  "enable_support": false,
                  "support_type": "normal(auto)",
                  "fuzzy_skin": "disabled_fuzzy",
                  "fuzzy_skin_thickness": 0,
                  "fuzzy_skin_point_distance": 0
                }
            """.trimIndent(),
            slots = listOf(slot),
            plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 1, paint = paint)),
            filaments = listOf(filament),
            flushVolumes = null
        )
        val nativeConfig = JSONObject(result.json)

        assertTrue(nativeConfig.optBoolean("enable_support"))
        assertEquals("normal(manual)", nativeConfig.optString("support_type"))
        assertEquals("disabled_fuzzy", nativeConfig.optString("fuzzy_skin"))
        assertEquals(0.0, nativeConfig.optDouble("fuzzy_skin_thickness"), 0.0001)
        assertEquals(0.0, nativeConfig.optDouble("fuzzy_skin_point_distance"), 0.0001)
    }

    @Test
    fun paintAwareNativeConfigPreservesTreeSupportProfileForPaintedSupports() {
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(id = "filament_tree_support_paint")
        val slot = filament.toPlateFilamentSlot(index = 1)
        val paint = PlateObjectPaint(support = paintLayer(PaintMode.Support))
        val result = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = """
                {
                  "enable_support": false,
                  "support_type": "tree(auto)",
                  "support_style": "tree_hybrid",
                  "support_threshold_angle": 37,
                  "support_on_build_plate_only": true
                }
            """.trimIndent(),
            slots = listOf(slot),
            plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 1, paint = paint)),
            filaments = listOf(filament),
            flushVolumes = null
        )
        val nativeConfig = JSONObject(result.json)

        assertTrue(nativeConfig.optBoolean("enable_support"))
        assertEquals("tree(manual)", nativeConfig.optString("support_type"))
        assertEquals("tree_hybrid", nativeConfig.optString("support_style"))
        assertEquals(37, nativeConfig.optInt("support_threshold_angle"))
        assertTrue(nativeConfig.optBoolean("support_on_build_plate_only"))
    }

    @Test
    fun plateConfigCacheInvalidatesOnlyForActivePaintHash() {
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(id = "filament_cache_paint")
        val slot = filament.toPlateFilamentSlot(index = 1)
        val unpainted = listOf(testPlateObject(id = 1L, filamentSlotIndex = 1))
        val painted = listOf(
            testPlateObject(
                id = 1L,
                filamentSlotIndex = 1,
                paint = PlateObjectPaint(support = paintLayer(PaintMode.Support))
            )
        )

        val first = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = """{"enable_support":false,"support_type":"normal(auto)"}""",
            slots = listOf(slot),
            plateObjects = unpainted,
            filaments = listOf(filament),
            flushVolumes = null
        )
        val second = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = """{"enable_support":false,"support_type":"normal(auto)"}""",
            slots = listOf(slot),
            plateObjects = painted,
            filaments = listOf(filament),
            flushVolumes = null
        )
        val third = applyPlateFilamentSlotsToNativeConfigResult(
            configJson = """{"enable_support":false,"support_type":"normal(auto)"}""",
            slots = listOf(slot),
            plateObjects = painted,
            filaments = listOf(filament),
            flushVolumes = null
        )

        assertFalse(first.cacheHit)
        assertFalse(second.cacheHit)
        assertTrue(third.cacheHit)
        assertTrue(JSONObject(second.json).optBoolean("enable_support"))
        assertEquals("normal(manual)", JSONObject(second.json).optString("support_type"))
    }

    @Test
    fun plateFilamentSlotApplicationStaysBoundedForManyObjects() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_many_slots")
        val filaments = (1..8).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_many_$index",
                name = "Many PLA $index",
                printerProfileId = printer.id,
                profileSource = "orca",
                materialType = "PLA",
                defaultFilamentColor = "#11223$index",
                orcaResolvedFilamentJson = """
                    {
                      "filament_settings_id":"Many PLA $index",
                      "filament_id":"MANY0$index",
                      "filament_type":"PLA"
                    }
                """.trimIndent()
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val plateObjects = (1L..96L).map { id ->
            testPlateObject(id = id, filamentSlotIndex = ((id - 1L) % slots.size + 1L).toInt())
        }

        val startedAtNs = System.nanoTime()
        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = "{}",
                slots = slots,
                plateObjects = plateObjects,
                filaments = filaments,
                flushVolumes = null
            )
        )
        val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000.0

        assertTrue("plate filament slot application exceeded smoke-test budget: $elapsedMs ms", elapsedMs < 5_000.0)
        assertEquals(8, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertEquals(8, result.getJSONArray("filament_settings_id").length())
        assertFalse(result.optBoolean("enable_prime_tower", true))
        assertFalse(result.optBoolean("purge_in_prime_tower", true))
    }

    @Test
    fun plateObjectsUsingOnlyFourthFilamentSlotStillSliceAsSingleMaterial() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_slot4")
        val filaments = (1..4).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_$index",
                name = "Filament $index",
                printerProfileId = printer.id,
                defaultFilamentColor = when (index) {
                    4 -> "#445566"
                    else -> "#8FC1FF"
                }
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val plateObjects = listOf(
            testPlateObject(id = 1L, filamentSlotIndex = 4),
            testPlateObject(id = 2L, filamentSlotIndex = 4)
        )

        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = """{"enable_prime_tower":true,"purge_in_prime_tower":false}""",
                slots = slots,
                plateObjects = plateObjects,
                filaments = filaments,
                flushVolumes = null
            )
        )

        assertEquals(1, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertFalse(result.optBoolean("enable_prime_tower", true))
        assertFalse(result.optBoolean("purge_in_prime_tower", true))
        assertEquals("Filament 4", result.optString("filament_settings_id"))
        assertEquals("#445566", result.optString("filament_colour"))
    }

    @Test
    fun multipleObjectsUsingSameFirstFilamentSlotStaySingleMaterial() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_slot1")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_1",
            name = "Only PETG",
            printerProfileId = printer.id,
            defaultFilamentColor = "#334455"
        )
        val slots = listOf(filament.toPlateFilamentSlot(index = 1))
        val plateObjects = listOf(
            testPlateObject(id = 1L, filamentSlotIndex = 1),
            testPlateObject(id = 2L, filamentSlotIndex = 1),
            testPlateObject(id = 3L, filamentSlotIndex = 1)
        )

        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = """{"enable_prime_tower":true,"purge_in_prime_tower":true}""",
                slots = slots,
                plateObjects = plateObjects,
                filaments = listOf(filament),
                flushVolumes = null
            )
        )

        assertEquals(1, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertFalse(result.optBoolean("enable_prime_tower", true))
        assertFalse(result.optBoolean("purge_in_prime_tower", true))
        assertEquals("Only PETG", result.optString("filament_settings_id"))
        assertEquals("#334455", result.optString("filament_colour"))
    }

    @Test
    fun missingPlateObjectFilamentSlotFallsBackToFirstAvailableSlot() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_missing_slot")
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_1",
            name = "Fallback PLA",
            printerProfileId = printer.id,
            defaultFilamentColor = "#223344"
        )
        val slots = listOf(filament.toPlateFilamentSlot(index = 1))
        val plateObjects = listOf(testPlateObject(id = 1L, filamentSlotIndex = 99))

        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = """{"enable_prime_tower":true,"purge_in_prime_tower":true}""",
                slots = slots,
                plateObjects = plateObjects,
                filaments = listOf(filament),
                flushVolumes = null
            )
        )

        assertEquals(1, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertFalse(result.optBoolean("enable_prime_tower", true))
        assertFalse(result.optBoolean("purge_in_prime_tower", true))
        assertEquals("Fallback PLA", result.optString("filament_settings_id"))
        assertEquals("#223344", result.optString("filament_colour"))
    }

    @Test
    fun objectsUsingDifferentFilamentSlotsEnablePrimeTower() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(id = "printer_multi_slot")
        val filaments = (1..2).map { index ->
            ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_$index",
                name = "Filament $index",
                printerProfileId = printer.id,
                defaultFilamentColor = if (index == 1) "#112233" else "#445566"
            )
        }
        val slots = filaments.mapIndexed { index, filament -> filament.toPlateFilamentSlot(index + 1) }
        val plateObjects = listOf(
            testPlateObject(id = 1L, filamentSlotIndex = 1),
            testPlateObject(id = 2L, filamentSlotIndex = 2)
        )

        val result = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = """{"enable_prime_tower":true,"purge_in_prime_tower":false}""",
                slots = slots,
                plateObjects = plateObjects,
                filaments = filaments,
                flushVolumes = null
            )
        )

        assertEquals(2, result.optInt("mobile_slicer_active_filament_slot_count"))
        assertTrue(result.optBoolean("enable_prime_tower", false))
        assertTrue(result.optBoolean("purge_in_prime_tower", false))
        assertEquals(2, result.getJSONArray("filament_settings_id").length())
    }

    @Test
    fun orcaResolvedProfileValuesWinOverStaleMobileFields() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_orca",
            name = "Orca Printer",
            profileSource = "",
            orcaResolvedMachineJson = """
                {
                  "printer_settings_id":"Orca Q2 0.4 nozzle",
                  "extruders_count":1,
                  "auxiliary_fan":true,
                  "bed_exclude_area":"",
                  "printer_custom_passthrough":"printer-kept"
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_stale",
            name = "Stale Mobile PLA",
            printerProfileId = printer.id,
            profileSource = "",
            materialType = "PLA",
            vendor = "Stale Vendor",
            densityGPerCm3 = 1.24f,
            maxVolumetricSpeedMm3PerSec = 12f,
            nozzleTemperatureC = 210,
            orcaResolvedFilamentJson = """
                {
                  "filament_settings_id":"Qidi ABS @Qidi Q2",
                  "filament_type":"ABS",
                  "filament_density":1.04,
                  "filament_max_volumetric_speed":17,
                  "filament_vendor":"Orca Vendor",
                  "nozzle_temperature":260,
                  "filament_custom_passthrough":"filament-kept"
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_orca",
            1 to "0.20mm Orca",
            259 to printer.id,
            265 to """
                {
                  "print_settings_id":"0.20mm Standard @Qidi Q2",
                  "layer_height":0.18,
                  "sparse_infill_density":"13%",
                  "wall_loops":3,
                  "skin_infill_density":"13%",
                  "skeleton_infill_line_width":"0.45",
                  "skirt_loops":0,
                  "combine_brims":false,
                  "process_custom_passthrough":"process-kept"
                }
            """.trimIndent()
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("Orca Q2 0.4 nozzle", nativeConfig.optString("printer_settings_id"))
        assertEquals(true, nativeConfig.optBoolean("auxiliary_fan", false))
        assertEquals("", nativeConfig.optString("bed_exclude_area"))
        assertEquals("printer-kept", nativeConfig.optString("printer_custom_passthrough"))
        assertEquals("ABS", nativeConfig.optString("filament_type"))
        assertEquals("Orca Vendor", nativeConfig.optString("filament_vendor"))
        assertEquals(1.04, nativeConfig.optDouble("filament_density"), 0.0001)
        assertEquals(17.0, nativeConfig.optDouble("filament_max_volumetric_speed"), 0.0001)
        assertEquals(260, nativeConfig.optInt("nozzle_temperature"))
        assertEquals("filament-kept", nativeConfig.optString("filament_custom_passthrough"))
        assertEquals("0.20mm Standard @Qidi Q2", nativeConfig.optString("print_settings_id"))
        assertEquals(0.18, nativeConfig.optDouble("layer_height"), 0.0001)
        assertEquals("13%", nativeConfig.optString("sparse_infill_density"))
        assertEquals(3, nativeConfig.optInt("wall_loops"))
        assertEquals("13%", nativeConfig.optString("skin_infill_density"))
        assertEquals("0.45", nativeConfig.optString("skeleton_infill_line_width"))
        assertEquals(0, nativeConfig.optInt("skirt_loops"))
        assertEquals(false, nativeConfig.optBoolean("combine_brims", true))
        assertEquals("process-kept", nativeConfig.optString("process_custom_passthrough"))
    }

    @Test
    fun processEditorChangesOnlyEditedOrcaProcessKeys() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_orca_edit",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_orca_edit",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_orca_edit",
            1 to "0.20mm Orca",
            259 to printer.id,
            263 to "orca",
            265 to """
                {
                  "print_settings_id":"0.20mm Standard",
                  "layer_height":0.18,
                  "sparse_infill_density":"13%",
                  "wall_loops":3,
                  "process_custom_passthrough":"kept"
                }
            """.trimIndent()
        )
        val editedProcess = ProcessProfileEditorDraft(process).apply {
            layerHeight = "0.30"
        }.toProcessProfile(process, isNew = false)
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(editedProcess),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = editedProcess.id
        )

        val resolvedAfterEdit = JSONObject(editedProcess.orcaResolvedProcessJson)
        val processOverrides = JSONObject(editedProcess.orcaProcessOverridesJson)
        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals(0.18, resolvedAfterEdit.optDouble("layer_height"), 0.0001)
        assertEquals("13%", resolvedAfterEdit.optString("sparse_infill_density"))
        assertEquals(3, resolvedAfterEdit.optInt("wall_loops"))
        assertEquals("kept", resolvedAfterEdit.optString("process_custom_passthrough"))
        assertEquals(0.30, processOverrides.optDouble("layer_height"), 0.0001)
        assertFalse(processOverrides.has("sparse_infill_density"))
        assertFalse(processOverrides.has("wall_loops"))
        assertEquals(0.30, nativeConfig.optDouble("layer_height"), 0.0001)
        assertEquals("13%", nativeConfig.optString("sparse_infill_density"))
        assertEquals(3, nativeConfig.optInt("wall_loops"))
        assertEquals("kept", nativeConfig.optString("process_custom_passthrough"))
    }

    @Test
    fun nativeSliceConfigAppliesProfileOverrideLayersAfterResolvedOrcaBaselines() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_override_layers",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer","bed_width_mm":220,"printer_passthrough_key":"kept"}""",
            orcaMachineOverridesJson = """{"bed_width_mm":256,"thumbnails":"96x96/PNG"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_override_layers",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA","nozzle_temperature":210,"filament_passthrough_key":"kept"}""",
            orcaFilamentOverridesJson = """{"nozzle_temperature":225}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_override_layers",
            1 to "0.20mm Orca",
            259 to printer.id,
            263 to "orca",
            265 to """{"print_settings_id":"0.20mm Standard","layer_height":0.2,"wall_loops":2,"process_passthrough_key":"kept"}""",
            267 to """
                {
                  "wall_loops":4,
                  "inner_wall_line_width":"0.44",
                  "dont_slow_down_outer_wall":true,
                  "internal_bridge_fan_speed":"77",
                  "support_material_interface_fan_speed":"88"
                }
            """.trimIndent()
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals(256.0, nativeConfig.optDouble("bed_width_mm"), 0.0001)
        assertEquals("96x96/PNG", nativeConfig.optString("thumbnails"))
        assertEquals("PNG", nativeConfig.optString("thumbnails_format"))
        assertEquals(225, nativeConfig.optInt("nozzle_temperature"))
        assertEquals(4, nativeConfig.optInt("wall_loops"))
        assertEquals("0.44", nativeConfig.optString("inner_wall_line_width"))
        assertTrue(nativeConfig.optBoolean("dont_slow_down_outer_wall"))
        assertEquals("77", nativeConfig.optString("internal_bridge_fan_speed"))
        assertEquals("88", nativeConfig.optString("support_material_interface_fan_speed"))
        assertEquals("kept", nativeConfig.optString("printer_passthrough_key"))
        assertEquals("kept", nativeConfig.optString("filament_passthrough_key"))
        assertEquals("kept", nativeConfig.optString("process_passthrough_key"))
    }

    @Test
    fun nativeSliceConfigPreservesResolvedOrcaParityDriftKeysAndExplicitOverrides() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_parity_drift",
            profileSource = "orca",
            orcaResolvedMachineJson = """
                {
                  "printer_settings_id":"Creality K2 0.4 nozzle",
                  "host_type":"octoprint",
                  "printhost_authorization_type":"key",
                  "printhost_ssl_ignore_revoke":0,
                  "thumbnails":"300x300/PNG, 96x96/PNG",
                  "thumbnails_format":"PNG",
                  "upward_compatible_machine":""
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_parity_drift",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_parity_drift",
            1 to "0.20mm Orca",
            259 to printer.id,
            263 to "orca",
            265 to """
                {
                  "print_settings_id":"0.20mm Standard",
                  "accel_to_decel_factor":"100%",
                  "combine_brims":0,
                  "default_jerk":12,
                  "infill_jerk":12,
                  "top_surface_jerk":8,
                  "travel_acceleration":12000,
                  "slowdown_for_curled_perimeters":0,
                  "gap_fill_target":"everywhere",
                  "exclude_object":1,
                  "gcode_label_objects":0,
                  "wall_direction":"auto",
                  "wipe_tower_cone_angle":0
                }
            """.trimIndent(),
            267 to """{"gap_fill_target":"nowhere","exclude_object":0}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("octoprint", nativeConfig.optString("host_type"))
        assertEquals("key", nativeConfig.optString("printhost_authorization_type"))
        assertEquals(0, nativeConfig.optInt("printhost_ssl_ignore_revoke"))
        assertEquals("300x300/PNG, 96x96/PNG", nativeConfig.optString("thumbnails"))
        assertEquals("PNG", nativeConfig.optString("thumbnails_format"))
        assertEquals("100%", nativeConfig.optString("accel_to_decel_factor"))
        assertEquals(0, nativeConfig.optInt("combine_brims"))
        assertEquals(12, nativeConfig.optInt("default_jerk"))
        assertEquals(12, nativeConfig.optInt("infill_jerk"))
        assertEquals(8, nativeConfig.optInt("top_surface_jerk"))
        assertEquals(12000, nativeConfig.optInt("travel_acceleration"))
        assertEquals(0, nativeConfig.optInt("slowdown_for_curled_perimeters"))
        assertEquals("nowhere", nativeConfig.optString("gap_fill_target"))
        assertEquals(0, nativeConfig.optInt("exclude_object"))
        assertEquals(0, nativeConfig.optInt("gcode_label_objects"))
        assertEquals("auto", nativeConfig.optString("wall_direction"))
        assertEquals(0, nativeConfig.optInt("wipe_tower_cone_angle"))
    }

    @Test
    fun nativeSliceConfigMapsAppOnlyBambuLanHostTypeToValidOrcaHostType() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_bambu_lan_native_host",
            printHostType = PrintHostType.BambuLan,
            printHost = "192.168.1.50"
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bambu_lan_native_host",
            printerProfileId = printer.id
        )
        val process = newProcessProfileUnchecked(
            0 to "process_bambu_lan_native_host",
            1 to "Bambu LAN Native Host",
            259 to printer.id
        )

        val nativeConfig = JSONObject(nativeConfigFor(printer, filament, process))

        assertEquals("octoprint", nativeConfig.optString("host_type"))
        assertEquals("192.168.1.50", nativeConfig.optString("print_host"))
    }

    @Test
    fun nativeSliceConfigCacheInvalidatesWhenOverrideContentChanges() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_cache_override",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_cache_override",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "process_cache_override",
            1 to "0.20mm Orca",
            259 to printer.id,
            263 to "orca",
            265 to """{"print_settings_id":"0.20mm Standard","wall_loops":2}"""
        )
        fun storeFor(overrideJson: String) = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process.withValues("orcaProcessOverridesJson" to overrideJson)),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val firstConfig = JSONObject(storeFor("""{"wall_loops":3}""").activeConfiguration().toNativeSliceConfigJson())
        val secondConfig = JSONObject(storeFor("""{"wall_loops":5}""").activeConfiguration().toNativeSliceConfigJson())

        assertEquals(3, firstConfig.optInt("wall_loops"))
        assertEquals(5, secondConfig.optInt("wall_loops"))
    }

    @Test
    fun printerEditorChangesOnlyEditedOrcaMachineKeys() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_editor_override",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer","bed_width_mm":220,"bed_depth_mm":220,"printer_passthrough_key":"kept"}"""
        )

        val edited = PrinterProfileEditorDraft(printer).apply {
            bedWidth = "250"
        }.toPrinterProfile(printer, isNew = false)

        val resolved = JSONObject(edited.orcaResolvedMachineJson)
        val overrides = JSONObject(edited.orcaMachineOverridesJson)

        assertEquals(220.0, resolved.optDouble("bed_width_mm"), 0.0001)
        assertEquals(220.0, resolved.optDouble("bed_depth_mm"), 0.0001)
        assertEquals(250.0, overrides.optDouble("bed_width_mm"), 0.0001)
        assertFalse(overrides.has("bed_depth_mm"))
    }

    @Test
    fun filamentEditorChangesOnlyEditedOrcaFilamentKeys() {
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_editor_override",
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA","nozzle_temperature":210,"filament_max_volumetric_speed":12,"filament_passthrough_key":"kept"}"""
        )

        val edited = FilamentProfileEditorDraft(filament).apply {
            nozzleTemp = "225"
        }.toFilamentProfile(filament, isNew = false)

        val resolved = JSONObject(edited.orcaResolvedFilamentJson)
        val overrides = JSONObject(edited.orcaFilamentOverridesJson)

        assertEquals(210, resolved.optInt("nozzle_temperature"))
        assertEquals(12.0, resolved.optDouble("filament_max_volumetric_speed"), 0.0001)
        assertEquals(225, overrides.optInt("nozzle_temperature"))
        assertFalse(overrides.has("filament_max_volumetric_speed"))
    }

    @Test
    fun filamentEditorRetractionEditsOverrideResolvedOrcaValues() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_filament_retraction_override",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_retraction_override",
            printerProfileId = printer.id,
            profileSource = "orca",
            retractionLengthMm = null,
            orcaResolvedFilamentJson = """
                {
                  "filament_settings_id":"Orca PLA",
                  "filament_retraction_length":0.4,
                  "filament_wipe":1,
                  "filament_wipe_distance":1.0
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_filament_retraction_override",
            1 to "0.20mm Orca",
            259 to printer.id,
            258 to "orca",
            265 to """{"print_settings_id":"0.20mm Standard"}"""
        )

        val edited = FilamentProfileEditorDraft(filament).apply {
            retractionLength = "0.8"
        }.toFilamentProfile(filament, isNew = false)
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(edited),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = edited.id,
            selectedProcessId = process.id
        )
        val overrides = JSONObject(edited.orcaFilamentOverridesJson)
        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals(0.8, overrides.optDouble("filament_retraction_length"), 0.0001)
        assertFalse(overrides.has("filament_wipe"))
        assertEquals(0.8, nativeConfig.optDouble("filament_retraction_length"), 0.0001)
        assertEquals(1, nativeConfig.optInt("filament_wipe"))
        assertEquals(1.0, nativeConfig.optDouble("filament_wipe_distance"), 0.0001)
    }

    @Test
    fun filamentEditorBedPlateTemperatureEditsOverrideResolvedOrcaValues() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_textured_plate",
            profileSource = "orca",
            orcaResolvedMachineJson = """
                {
                  "printer_settings_id":"Orca Printer",
                  "curr_bed_type":"Textured PEI Plate",
                  "default_bed_type":"Textured PEI Plate"
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bed_temp_edit",
            name = "ABS",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """
                {
                  "filament_settings_id":"Orca ABS",
                  "filament_type":"ABS",
                  "hot_plate_temp_initial_layer":90,
                  "hot_plate_temp":90,
                  "textured_plate_temp_initial_layer":45,
                  "textured_plate_temp":45,
                  "cool_plate_temp_initial_layer":35,
                  "cool_plate_temp":35,
                  "textured_cool_plate_temp_initial_layer":40,
                  "textured_cool_plate_temp":40,
                  "eng_plate_temp_initial_layer":50,
                  "eng_plate_temp":50,
                  "supertack_plate_temp_initial_layer":35,
                  "supertack_plate_temp":35
                }
            """.trimIndent()
        )
        val edited = FilamentProfileEditorDraft(filament).apply {
            bedTempInitialLayer = "110"
            bedTemp = "110"
            texturedPlateTempInitialLayer = "110"
            texturedPlateTemp = "110"
            coolPlateTempInitialLayer = "55"
            coolPlateTemp = "55"
            texturedCoolPlateTempInitialLayer = "60"
            texturedCoolPlateTemp = "60"
            engineeringPlateTempInitialLayer = "70"
            engineeringPlateTemp = "70"
            supertackPlateTempInitialLayer = "50"
            supertackPlateTemp = "50"
        }.toFilamentProfile(filament, isNew = false)
        val process = newProcessProfileUnchecked(
            0 to "process_bed_temp_edit",
            1 to "0.20mm Standard",
            259 to printer.id,
            263 to "orca",
            265 to """{"print_settings_id":"0.20mm Standard"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(edited),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = edited.id,
            selectedProcessId = process.id
        )

        val overrides = JSONObject(edited.orcaFilamentOverridesJson)
        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())
        val slottedConfig = JSONObject(
            applyPlateFilamentSlotsToNativeConfig(
                configJson = nativeConfig.toString(),
                slots = listOf(edited.toPlateFilamentSlot(1)),
                plateObjects = listOf(testPlateObject(id = 10L, filamentSlotIndex = 1)),
                filaments = listOf(edited),
                flushVolumes = null
            )
        )

        assertEquals(110, overrides.optInt("hot_plate_temp_initial_layer"))
        assertEquals(110, overrides.optInt("hot_plate_temp"))
        assertEquals(110, overrides.optInt("textured_plate_temp_initial_layer"))
        assertEquals(110, overrides.optInt("textured_plate_temp"))
        assertEquals(55, overrides.optInt("cool_plate_temp"))
        assertEquals(60, overrides.optInt("textured_cool_plate_temp"))
        assertEquals(70, overrides.optInt("eng_plate_temp"))
        assertEquals(50, overrides.optInt("supertack_plate_temp"))
        assertEquals("Textured PEI Plate", nativeConfig.optString("curr_bed_type"))
        assertEquals(110, nativeConfig.optInt("textured_plate_temp_initial_layer"))
        assertEquals(110, nativeConfig.optInt("textured_plate_temp"))
        assertEquals(110, slottedConfig.optInt("textured_plate_temp_initial_layer"))
        assertEquals(110, slottedConfig.optInt("textured_plate_temp"))
    }

    @Test
    fun blankResolvedBambuIdentityFieldsDoNotEraseExportedProfileNames() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_bambu",
            name = "Bambu Lab P2S",
            profileSource = "orca",
            orcaFamily = "BBL-3DP",
            orcaResolvedMachineJson = """{"name":"Bambu Lab P2S 0.4 nozzle","printer_settings_id":""}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bambu_pla",
            name = "PLA",
            printerProfileId = printer.id,
            profileSource = "orca",
            materialType = "PLA",
            vendor = "Generic",
            orcaFamily = "BBL",
            orcaFilamentPath = "BBL/filament/Generic PLA @BBL P2S.json",
            orcaResolvedFilamentJson = """
                {
                  "name":"Generic PLA @BBL P2S",
                  "filament_settings_id":[""],
                  "filament_vendor":["Generic"],
                  "filament_density":["1.24"]
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_bambu_020",
            1 to "0.20mm Standard",
            259 to printer.id,
            258 to "orca",
            262 to "BBL-3DP",
            263 to "BBL/process/0.20mm Standard @BBL P2S.json",
            265 to """
                {
                  "name":"0.20mm Standard @BBL P2S",
                  "print_settings_id":"",
                  "initial_layer_speed":["50","50"],
                  "skin_infill_density":"15%",
                  "skirt_loops":"0"
                }
            """.trimIndent()
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("Bambu Lab P2S 0.4 nozzle", nativeConfig.optString("printer_settings_id"))
        assertEquals("Generic PLA @BBL P2S", nativeConfig.optString("filament_settings_id"))
        assertEquals("0.20mm Standard @BBL P2S", nativeConfig.optString("print_settings_id"))
        assertEquals("50", nativeConfig.optJSONArray("initial_layer_speed")?.optString(0))
        assertEquals("15%", nativeConfig.optString("skin_infill_density"))
        assertEquals(0, nativeConfig.optInt("skirt_loops"))
    }

    @Test
    fun resolvedOrcaPrinterModelDefaultBedTypeWinsOverFilamentPlateTemps() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_bambu",
            name = "Bambu Lab P2S",
            profileSource = "orca",
            orcaFamily = "BBL-3DP",
            orcaMachineModelJson = """{"name":"Bambu Lab P2S","default_bed_type":"Textured PEI Plate"}""",
            orcaResolvedMachineJson = """{"name":"Bambu Lab P2S 0.4 nozzle","printer_settings_id":""}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bambu_pla",
            name = "PLA",
            printerProfileId = printer.id,
            profileSource = "orca",
            materialType = "PLA",
            vendor = "Generic",
            orcaFamily = "BBL",
            orcaFilamentPath = "BBL/filament/Generic PLA @BBL P2S.json",
            coolPlateTemperatureInitialLayerC = 55,
            coolPlateTemperatureC = 55,
            orcaResolvedFilamentJson = """
                {
                  "name":"Generic PLA @BBL P2S",
                  "filament_settings_id":[""],
                  "filament_vendor":["Generic"],
                  "filament_density":["1.24"],
                  "cool_plate_temp_initial_layer":["35"],
                  "cool_plate_temp":["35"],
                  "supertack_plate_temp_initial_layer":["45"],
                  "supertack_plate_temp":["45"],
                  "eng_plate_temp_initial_layer":["55"],
                  "eng_plate_temp":["55"],
                  "hot_plate_temp_initial_layer":["55"],
                  "hot_plate_temp":["55"]
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_bambu_020",
            1 to "0.20mm Standard",
            259 to printer.id,
            258 to "orca",
            262 to "BBL-3DP",
            263 to "BBL/process/0.20mm Standard @BBL P2S.json",
            265 to """{"name":"0.20mm Standard @BBL P2S","print_settings_id":""}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("Textured PEI Plate", nativeConfig.optString("default_bed_type"))
        assertEquals("Textured PEI Plate", nativeConfig.optString("curr_bed_type"))
        assertEquals("35", nativeConfig.optJSONArray("cool_plate_temp_initial_layer")?.optString(0))
        assertEquals("35", nativeConfig.optJSONArray("cool_plate_temp")?.optString(0))
        assertEquals("45", nativeConfig.optJSONArray("supertack_plate_temp_initial_layer")?.optString(0))
        assertEquals("45", nativeConfig.optJSONArray("supertack_plate_temp")?.optString(0))
    }

    @Test
    fun resolvedBambuH2sFilamentAndMultiBedValuesWinOverStoredFallbacks() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_bambu_h2s",
            name = "Bambu Lab H2S",
            profileSource = "orca",
            orcaFamily = "BBL-3DP",
            supportMultiBedTypes = false,
            defaultBedType = com.mobileslicer.profiles.DefaultBedType.TexturedPeiPlate,
            orcaResolvedMachineJson = """
                {
                  "name":"Bambu Lab H2S 0.4 nozzle",
                  "printer_settings_id":"Bambu Lab H2S 0.4 nozzle",
                  "support_multi_bed_types":"1",
                  "default_bed_type":"Textured PEI Plate"
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_bambu_h2s_pla",
            name = "Bambu PLA Basic @BBL H2S",
            printerProfileId = printer.id,
            profileSource = "orca",
            materialType = "PLA",
            vendor = "Bambu Lab",
            orcaFamily = "BBL",
            orcaFilamentPath = "BBL/filament/Bambu PLA Basic @BBL H2S.json",
            maxVolumetricSpeedMm3PerSec = 21f,
            minFanSpeedPercent = 100,
            additionalCoolingFanSpeedPercent = 70,
            slowDownMinSpeedMmPerSec = 10f,
            coolPlateTemperatureInitialLayerC = 55,
            coolPlateTemperatureC = 55,
            engineeringPlateTemperatureInitialLayerC = 0,
            engineeringPlateTemperatureC = 0,
            longRetractionsWhenCut = true,
            retractionDistancesWhenCut = "18",
            orcaResolvedFilamentJson = """
                {
                  "name":"Bambu PLA Basic @BBL H2S",
                  "filament_settings_id":["Bambu PLA Basic @BBL H2S"],
                  "filament_id":"GFA00",
                  "filament_type":["PLA"],
                  "filament_vendor":["Bambu Lab"],
                  "filament_max_volumetric_speed":["25","40"],
                  "fan_min_speed":["60"],
                  "fan_max_speed":["100"],
                  "additional_cooling_fan_speed":["75"],
                  "slow_down_min_speed":["20"],
                  "cool_plate_temp_initial_layer":["35"],
                  "cool_plate_temp":["35"],
                  "eng_plate_temp_initial_layer":["55"],
                  "eng_plate_temp":["55"],
                  "filament_retraction_length":["0.4","0.4"],
                  "filament_wipe":["1","1"],
                  "filament_wipe_distance":["1","1"],
                  "filament_z_hop_types":["Spiral Lift","Spiral Lift"],
                  "filament_long_retractions_when_cut":["nil","nil"],
                  "filament_retraction_distances_when_cut":["nil","nil"]
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_bambu_h2s_020",
            1 to "0.20mm Standard @BBL H2S",
            55 to com.mobileslicer.profiles.ProcessQualitySurfaceDetails(
                wallDirection = com.mobileslicer.profiles.WallDirection.CounterClockwise
            ),
            259 to printer.id,
            258 to "orca",
            262 to "BBL-3DP",
            263 to "BBL/process/0.20mm Standard @BBL H2S.json",
            265 to """{"name":"0.20mm Standard @BBL H2S","print_settings_id":"0.20mm Standard @BBL H2S","wall_direction":"auto"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals(1, nativeConfig.optInt("support_multi_bed_types"))
        assertEquals("GFA00", nativeConfig.optString("filament_ids"))
        assertEquals("25", nativeConfig.optJSONArray("filament_max_volumetric_speed")?.optString(0))
        assertEquals("60", nativeConfig.optJSONArray("fan_min_speed")?.optString(0))
        assertEquals("75", nativeConfig.optJSONArray("additional_cooling_fan_speed")?.optString(0))
        assertEquals("20", nativeConfig.optJSONArray("slow_down_min_speed")?.optString(0))
        assertEquals("35", nativeConfig.optJSONArray("cool_plate_temp")?.optString(0))
        assertEquals("55", nativeConfig.optJSONArray("eng_plate_temp")?.optString(0))
        assertEquals("0.4", nativeConfig.optJSONArray("filament_retraction_length")?.optString(0))
        assertEquals("1", nativeConfig.optJSONArray("filament_wipe")?.optString(0))
        assertEquals("1", nativeConfig.optJSONArray("filament_wipe_distance")?.optString(0))
        assertEquals("Spiral Lift", nativeConfig.optJSONArray("filament_z_hop_types")?.optString(0))
        assertFalse(nativeConfig.has("filament_long_retractions_when_cut"))
        assertFalse(nativeConfig.has("filament_retraction_distances_when_cut"))
        assertEquals("auto", nativeConfig.optString("wall_direction"))
        assertCriticalNativeConfigContains(
            nativeConfig,
            mapOf(
                "printer_settings_id" to "Bambu Lab H2S 0.4 nozzle",
                "filament_settings_id" to listOf("Bambu PLA Basic @BBL H2S"),
                "filament_ids" to "GFA00",
                "filament_type" to listOf("PLA"),
                "print_settings_id" to "0.20mm Standard @BBL H2S",
                "curr_bed_type" to "Textured PEI Plate"
            )
        )
    }

    @Test
    fun resolvedOrcaBedMeshPointsEmitCommaPairsForNativeParser() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_wondermaker",
            name = "WonderMaker ZR Ultra S",
            profileSource = "orca",
            bedMeshMin = "-99999,-99999",
            bedMeshMax = "99999,99999",
            bedMeshProbeDistance = "50,50",
            orcaResolvedMachineJson = """
                {
                  "name":"WonderMaker ZR Ultra S 0.4 nozzle",
                  "bed_mesh_min":["10","10"],
                  "bed_mesh_max":["290","260"],
                  "bed_mesh_probe_distance":["50","50"]
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_wondermaker_pla",
            printerProfileId = printer.id
        )
        val process = newProcessProfileUnchecked(
            0 to "process_wondermaker_020",
            1 to "0.20mm Standard",
            259 to printer.id,
            258 to "orca"
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("10,10", nativeConfig.optString("bed_mesh_min"))
        assertEquals("290,260", nativeConfig.optString("bed_mesh_max"))
        assertEquals("50,50", nativeConfig.optString("bed_mesh_probe_distance"))
    }

    @Test
    fun resolvedWonderMakerProcessDefaultsReplaceMobileFallbacks() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_wondermaker",
            name = "WonderMaker ZR Ultra S",
            profileSource = "orca",
            orcaFamily = "WonderMaker",
            defaultBedType = com.mobileslicer.profiles.DefaultBedType.HighTempPlate,
            orcaResolvedMachineJson = """
                {
                  "name":"WonderMaker ZR Ultra S 0.4 nozzle",
                  "default_bed_type":"4",
                  "nozzle_diameter":["0.4","0.4","0.4","0.4"],
                  "extruder_offset":["0x0","0x0","0x0","0x0"],
                  "extruder_colour":["#26A69A","#26A69A","#26A69A","#26A69A"],
                  "extruder_type":["Direct Drive","Direct Drive","Direct Drive","Direct Drive"],
                  "extruder_variant_list":["Direct Drive Standard","Direct Drive Standard","Direct Drive Standard","Direct Drive Standard"]
                }
            """.trimIndent()
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_wondermaker_pla",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """
                {
                  "filament_settings_id":["WonderMaker PLA Basic","Bambu PLA Basic @System","Bambu PLA Basic @System","Bambu PLA Basic @System"],
                  "filament_ids":["GFA00","OGFA00","OGFA00","OGFA00"],
                  "filament_type":["PLA","PLA","PLA","PLA"],
                  "filament_vendor":["WonderMaker","Bambu Lab","Bambu Lab","Bambu Lab"],
                  "filament_density":["1.26","1.26","1.26","1.26"],
                  "filament_max_volumetric_speed":["21","21","21","21"],
                  "cool_plate_temp":["35","35","35","35"]
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "process_wondermaker_020",
            1 to "0.20mm Standard @WonderMaker ZR Ultra",
            55 to com.mobileslicer.profiles.ProcessQualitySurfaceDetails(
                wallDirection = com.mobileslicer.profiles.WallDirection.CounterClockwise
            ),
            188 to false,
            189 to 0f,
            259 to printer.id,
            258 to "orca",
            262 to "WonderMaker",
            263 to "WonderMaker/process/0.20mm Standard @WonderMaker ZR Ultra.json",
            265 to """
                {
                  "print_settings_id":"0.20mm Standard @WonderMaker ZR Ultra",
                  "accel_to_decel_enable":"0",
                  "exclude_object":"1",
                  "ooze_prevention":"1",
                  "prime_volume":"15",
                  "wipe_tower_wall_type":"cone",
                  "wipe_tower_cone_angle":"15",
                  "filament_map":["1","2","3","4"],
                  "filament_self_index":["1","2","3","4"]
                }
            """.trimIndent()
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("High Temp Plate", nativeConfig.optString("curr_bed_type"))
        assertEquals("auto", nativeConfig.optString("wall_direction"))
        assertEquals(0, nativeConfig.optInt("accel_to_decel_enable"))
        assertEquals(1, nativeConfig.optInt("exclude_object"))
        assertEquals(1, nativeConfig.optInt("ooze_prevention"))
        assertEquals(15.0, nativeConfig.optDouble("prime_volume"), 0.0001)
        assertEquals("cone", nativeConfig.optString("wipe_tower_wall_type"))
        assertEquals(15.0, nativeConfig.optDouble("wipe_tower_cone_angle"), 0.0001)
        assertEquals(165.0, nativeConfig.optDouble("wipe_tower_x"), 0.0001)
        assertEquals(250.0, nativeConfig.optDouble("wipe_tower_y"), 0.0001)
        assertEquals(4, nativeConfig.optJSONArray("nozzle_diameter")?.length())
        assertEquals(4, nativeConfig.optJSONArray("filament_settings_id")?.length())
        assertEquals("WonderMaker PLA Basic", nativeConfig.optJSONArray("filament_settings_id")?.optString(0))
        assertEquals("Bambu PLA Basic @System", nativeConfig.optJSONArray("filament_settings_id")?.optString(1))
        assertEquals(4, nativeConfig.optJSONArray("filament_ids")?.length())
        assertEquals("GFA00", nativeConfig.optJSONArray("filament_ids")?.optString(0))
        assertEquals("OGFA00", nativeConfig.optJSONArray("filament_ids")?.optString(1))
        assertEquals(4, nativeConfig.optJSONArray("filament_map")?.length())
        assertEquals("1", nativeConfig.optJSONArray("filament_map")?.optString(0))
        assertEquals("4", nativeConfig.optJSONArray("filament_map")?.optString(3))
    }

    @Test
    fun materialOnlyIdentityDoesNotMatchArbitraryVendorPreset() {
        val aliZPresetIdentities = listOf(
            "OrcaFilamentLibrary/filament/AliZ/AliZ PLA @System.json",
            "AliZ PLA @System",
            "AliZ PLA @base"
        )

        assertFalse(orcaFilamentIdentityMatchesPreset("PLA", aliZPresetIdentities))
        assertFalse(orcaFilamentIdentityMatchesPreset("Generic PLA", aliZPresetIdentities))
        assertEquals(
            true,
            orcaFilamentIdentityMatchesPreset(
                "BBL/filament/Generic PLA @BBL P2S.json",
                listOf("Generic PLA @BBL P2S", "BBL/filament/Generic PLA @BBL P2S.json")
            )
        )
    }

    @Test
    fun importedFlashforgeGenericPlaEmitsPrinterSpecificFilamentConfig() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "flashforge_ad5m",
            name = "Flashforge Adventurer 5M 0.4 mm",
            profileSource = "orca",
            orcaFamily = "Flashforge",
            orcaResolvedMachineJson = """{"name":"Flashforge Adventurer 5M 0.4 Nozzle","printer_settings_id":"Flashforge Adventurer 5M 0.4 Nozzle"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "flashforge_generic_pla",
            name = "PLA",
            materialType = "PLA",
            vendor = "Generic",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaFamily = "Flashforge",
            orcaFilamentPath = "Flashforge/filament/Flashforge Generic PLA.json",
            orcaResolvedFilamentJson = """
                {
                  "name":"Flashforge Generic PLA",
                  "filament_settings_id":"Flashforge Generic PLA",
                  "filament_id":"FFG01",
                  "filament_ids":"FFG01",
                  "filament_type":"PLA",
                  "filament_vendor":"Generic",
                  "filament_max_volumetric_speed":25,
                  "enable_pressure_advance":1,
                  "pressure_advance":0.025,
                  "temperature_vitrification":60,
                  "support_material_interface_fan_speed":100,
                  "filament_start_gcode":"; filament start gcode\n;right_extruder_material: PLA\n"
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "flashforge_process",
            1 to "0.20mm Standard",
            259 to printer.id,
            258 to "orca",
            262 to "Flashforge",
            263 to "Flashforge/process/0.20mm Standard @Flashforge AD5M 0.4 Nozzle.json",
            265 to """{"print_settings_id":"0.20mm Standard @Flashforge AD5M 0.4 Nozzle"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("Flashforge Generic PLA", nativeConfig.optString("filament_settings_id"))
        assertEquals("FFG01", nativeConfig.optString("filament_ids"))
        assertEquals(25.0, nativeConfig.optDouble("filament_max_volumetric_speed"), 0.0001)
        assertEquals(1, nativeConfig.optInt("enable_pressure_advance"))
        assertEquals(0.025, nativeConfig.optDouble("pressure_advance"), 0.0001)
        assertEquals(60, nativeConfig.optInt("temperature_vitrification"))
        assertEquals(100, nativeConfig.optInt("support_material_interface_fan_speed"))
        assertTrue(nativeConfig.optString("filament_start_gcode").contains("right_extruder_material: PLA"))
        assertEquals(0, nativeConfig.optInt("accel_to_decel_enable"))
        assertEquals("auto", nativeConfig.optString("wall_direction"))
        assertEquals(0.5, nativeConfig.optDouble("filter_out_gap_fill"), 0.0001)
        assertEquals(0, nativeConfig.optInt("gcode_label_objects"))
        assertCriticalNativeConfigContains(
            nativeConfig,
            mapOf(
                "printer_settings_id" to "Flashforge Adventurer 5M 0.4 Nozzle",
                "filament_settings_id" to "Flashforge Generic PLA",
                "filament_ids" to "FFG01",
                "filament_type" to "PLA",
                "print_settings_id" to "0.20mm Standard @Flashforge AD5M 0.4 Nozzle"
            )
        )
    }

    @Test
    fun importedCrealityAbsKeepsOrcaProcessAndFilamentDefaults() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "creality_cr10max",
            name = "Creality CR-10 Max 0.4 mm",
            profileSource = "orca",
            orcaFamily = "Creality",
            orcaResolvedMachineJson = """{"name":"Creality CR-10 Max 0.4 nozzle","printer_settings_id":"Creality CR-10 Max 0.4 nozzle"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "creality_generic_abs",
            name = "Creality Generic ABS",
            materialType = "ABS",
            vendor = "Generic",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaFamily = "Creality",
            orcaFilamentPath = "Creality/filament/Creality Generic ABS.json",
            orcaResolvedFilamentJson = """
                {
                  "name":"Creality Generic ABS",
                  "filament_settings_id":"Creality Generic ABS",
                  "filament_id":"GFB99",
                  "filament_ids":"GFB99",
                  "filament_type":"ABS",
                  "fan_max_speed":80
                }
            """.trimIndent()
        )
        val process = newProcessProfileUnchecked(
            0 to "creality_cr10max_standard",
            1 to "0.20mm Standard @Creality CR10Max",
            25 to 10000f,
            55 to com.mobileslicer.profiles.ProcessQualitySurfaceDetails(
                wallDirection = com.mobileslicer.profiles.WallDirection.CounterClockwise
            ),
            259 to printer.id,
            258 to "orca",
            262 to "Creality",
            263 to "Creality/process/0.20mm Standard @Creality CR10Max.json",
            265 to """{"print_settings_id":"0.20mm Standard @Creality CR10Max","travel_acceleration":"700"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        val nativeConfig = JSONObject(store.activeConfiguration().toNativeSliceConfigJson())

        assertEquals("Creality Generic ABS", nativeConfig.optString("filament_settings_id"))
        assertEquals("GFB99", nativeConfig.optString("filament_ids"))
        assertEquals(10, nativeConfig.optInt("fan_min_speed"))
        assertEquals(80, nativeConfig.optInt("fan_max_speed"))
        assertEquals(700.0, nativeConfig.optDouble("travel_acceleration"), 0.0001)
        assertEquals("auto", nativeConfig.optString("wall_direction"))
        assertCriticalNativeConfigContains(
            nativeConfig,
            mapOf(
                "printer_settings_id" to "Creality CR-10 Max 0.4 nozzle",
                "filament_settings_id" to "Creality Generic ABS",
                "filament_ids" to "GFB99",
                "filament_type" to "ABS",
                "print_settings_id" to "0.20mm Standard @Creality CR10Max"
            )
        )
    }

    private fun testPlateObject(
        id: Long,
        filamentSlotIndex: Int,
        paint: PlateObjectPaint = PlateObjectPaint()
    ): PlateObject =
        PlateObject(
            id = id,
            label = "Object $id",
            filePath = "/tmp/object_$id.stl",
            filamentSlotIndex = filamentSlotIndex,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f),
            paint = paint
        )

    private fun paintLayer(
        mode: PaintMode,
        referencedSlots: Set<Int> = emptySet()
    ): SerializedPaintLayer =
        SerializedPaintLayer(
            mode = mode,
            objectSourceKey = "/tmp/object.stl",
            meshFingerprint = null,
            referencedSlotIndexes = referencedSlots,
            volumeLayers = listOf(
                SerializedPaintVolumeLayer(
                    volumeIndex = 0,
                    triangleCount = 12,
                    serializedTriangles = listOf(SerializedPaintTriangle(1, "2A")),
                    nativeMeshFingerprint = "fnv1a64:test"
                )
            )
        )

    private fun nativeConfigFor(
        printer: com.mobileslicer.profiles.PrinterProfile,
        filament: com.mobileslicer.profiles.FilamentProfile,
        process: com.mobileslicer.profiles.ProcessProfile
    ): String =
        nativeConfigBuildResultFor(printer, filament, process).json

    private fun nativeConfigBuildResultFor(
        printer: com.mobileslicer.profiles.PrinterProfile,
        filament: com.mobileslicer.profiles.FilamentProfile,
        process: com.mobileslicer.profiles.ProcessProfile
    ) = ProfileStore(
        printers = listOf(printer),
        filaments = listOf(filament),
        processes = listOf(process),
        selectedPrinterId = printer.id,
        selectedFilamentId = filament.id,
        selectedProcessId = process.id
    ).activeConfiguration().toNativeSliceConfigBuildResult()

    private fun assertJsonArrayEquals(expected: List<Any>, actual: JSONArray) {
        assertEquals(expected.size, actual.length())
        expected.forEachIndexed { index, value ->
            assertEquals(value.toString(), actual.opt(index).toString())
        }
    }

    private fun criticalNativeConfigSnapshot(nativeConfig: JSONObject): Map<String, Any?> =
        listOf(
            "printer_settings_id",
            "filament_settings_id",
            "filament_ids",
            "filament_type",
            "print_settings_id",
            "curr_bed_type",
            "enable_prime_tower",
            "purge_in_prime_tower",
            "single_extruder_multi_material",
            "mobile_slicer_active_filament_slot_count"
        ).associateWith { key ->
            when (val value = nativeConfig.opt(key)) {
                is JSONArray -> List(value.length()) { index -> value.opt(index).toString() }
                else -> value
            }
        }

    private fun assertCriticalNativeConfigContains(nativeConfig: JSONObject, expected: Map<String, Any?>) {
        val snapshot = criticalNativeConfigSnapshot(nativeConfig)
        expected.forEach { (key, value) ->
            assertEquals("critical native config mismatch for $key", value, snapshot[key])
        }
    }
}
