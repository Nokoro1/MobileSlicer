package com.mobileslicer

import com.mobileslicer.automation.AutomationConfigInput
import com.mobileslicer.automation.AutomationConfigResolver
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.SparseInfillPattern
import com.mobileslicer.profiles.SupportStyle
import com.mobileslicer.profiles.SupportType
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.newProcessProfileUnchecked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class AutomationConfigResolverTest {
    @Test
    fun returnsExplicitConfigJsonUnchanged() {
        val explicit = """{"layer_height":0.32}"""
        val resolved = resolver().resolve(
            mapInput("automation_config_json" to explicit)
        )

        assertEquals(explicit, resolved)
    }

    @Test
    fun appliesPrinterOverridesToNativeConfig() {
        val store = resolver().resolveProfileStore(
            mapInput(
                "automation_bed_width_mm" to 180f,
                "automation_bed_depth_mm" to 190f,
                "automation_max_height_mm" to 200f,
                "automation_nozzle_diameter_mm" to 0.6f
            )
        )
        val printer = store.activeConfiguration().printer

        assertEquals(180f, printer.bedWidthMm)
        assertEquals(190f, printer.bedDepthMm)
        assertEquals(200f, printer.maxHeightMm)
        assertEquals(0.6f, printer.nozzleDiameterMm)
        assertFalse(printer.builtIn)
    }

    @Test
    fun automationPrinterFilamentDiameterUpdatesActiveFilamentConfig() {
        val resolved = resolver().resolve(
            mapInput(
                "automation_filament_diameter_mm" to 2.85f
            )
        )
        val nativeConfig = JSONObject(resolved)

        assertEquals(2.85, nativeConfig.optDouble("filament_diameter"), 0.0001)
    }

    @Test
    fun appliesProcessAndBridgeOverridesToNativeConfig() {
        val store = resolver().resolveProfileStore(
            mapInput(
                "automation_layer_height_mm" to 0.28f,
                "automation_wall_count" to 4,
                "automation_infill_percent" to 35,
                "automation_sparse_infill_pattern" to "gyroid",
                "automation_bridge_speed_mm_per_sec" to 18f,
                "automation_bridge_no_support" to true
            )
        )
        val process = store.activeConfiguration().process

        assertEquals(0.28f, process.layerHeightMm)
        assertEquals(4, process.wallCount)
        assertEquals(35, process.infillPercent)
        assertEquals(SparseInfillPattern.Gyroid, process.sparseInfillPattern)
        assertEquals(18f, process.bridgeSpeedMmPerSec)
        assertTrue(process.bridgeNoSupport)
        assertFalse(process.builtIn)
    }

    @Test
    fun automationProcessSpeedOverridesWinOverOrcaResolvedProfileValues() {
        val resolved = resolver(loadProfileStore = { orcaStoreWithResolvedProcessSpeeds() }).resolve(
            mapInput(
                "automation_first_layer_print_speed_mm_per_sec" to 31f,
                "automation_first_layer_infill_speed_mm_per_sec" to 17f,
                "automation_first_layer_travel_speed_percent" to 42,
                "automation_outer_wall_speed_mm_per_sec" to 45f,
                "automation_inner_wall_speed_mm_per_sec" to 55f,
                "automation_top_surface_speed_mm_per_sec" to 35f,
                "automation_travel_speed_mm_per_sec" to 180f,
                "automation_sparse_infill_speed_mm_per_sec" to 90f,
                "automation_internal_solid_infill_speed_mm_per_sec" to 80f,
                "automation_gap_infill_speed_mm_per_sec" to 12f
            )
        )
        val nativeConfig = JSONObject(resolved)

        assertEquals(31.0, nativeConfig.optDouble("initial_layer_speed"), 0.0001)
        assertEquals(17.0, nativeConfig.optDouble("initial_layer_infill_speed"), 0.0001)
        assertEquals("42%", nativeConfig.optString("initial_layer_travel_speed"))
        assertEquals(45.0, nativeConfig.optDouble("outer_wall_speed"), 0.0001)
        assertEquals(55.0, nativeConfig.optDouble("inner_wall_speed"), 0.0001)
        assertEquals(35.0, nativeConfig.optDouble("top_surface_speed"), 0.0001)
        assertEquals(180.0, nativeConfig.optDouble("travel_speed"), 0.0001)
        assertEquals(90.0, nativeConfig.optDouble("sparse_infill_speed"), 0.0001)
        assertEquals(80.0, nativeConfig.optDouble("internal_solid_infill_speed"), 0.0001)
        assertEquals(12.0, nativeConfig.optDouble("gap_infill_speed"), 0.0001)
    }

    @Test
    fun appliesSupportOverridesToNativeConfig() {
        val store = resolver().resolveProfileStore(
            mapInput(
                "automation_enable_support" to true,
                "automation_support_type" to "tree(manual)",
                "automation_support_style" to "tree_hybrid",
                "automation_support_threshold_angle" to 42,
                "automation_support_buildplate_only" to true
            )
        )
        val process = store.activeConfiguration().process

        assertTrue(process.enableSupport)
        assertEquals(SupportType.TreeManual, process.supportType)
        assertEquals(SupportStyle.TreeHybrid, process.supportStyle)
        assertEquals(42, process.supportThresholdAngleDegrees)
        assertTrue(process.supportBuildplateOnly)
        assertFalse(process.builtIn)
    }

    @Test
    fun keepsBaseConfigWhenNoOverridesArePresent() {
        val config = resolver().resolveProfileStore(mapInput()).activeConfiguration()

        assertEquals(220f, config.printer.bedWidthMm)
        assertEquals(0.20f, config.process.layerHeightMm)
        assertFalse(config.process.bridgeNoSupport)
    }

    private fun resolver(loadProfileStore: () -> ProfileStore = { defaultStore() }): AutomationConfigResolver =
        AutomationConfigResolver(
            loadProfileStore = loadProfileStore,
            timestampMillis = { 123L }
        )

    private fun mapInput(vararg extras: Pair<String, Any>): AutomationConfigInput =
        MapAutomationConfigInput(extras.toMap())

    private fun defaultStore(): ProfileStore {
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

    private fun orcaStoreWithResolvedProcessSpeeds(): ProfileStore {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "orca_printer_automation",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"printer_settings_id":"Orca Printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "orca_filament_automation",
            printerProfileId = printer.id,
            profileSource = "orca",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Orca PLA"}"""
        )
        val process = newProcessProfileUnchecked(
            0 to "orca_process_automation",
            1 to "Orca Process",
            3 to false,
            5 to 0.20f,
            6 to 100f,
            7 to 100f,
            8 to 100,
            15 to 100f,
            16 to 100f,
            17 to 100f,
            18 to 100f,
            67 to 100f,
            68 to 100f,
            69 to 100f,
            258 to "orca",
            259 to printer.id,
            261 to printer.nozzleDiameterMm,
            265 to """
                {
                  "print_settings_id":"Orca Process",
                  "initial_layer_speed":100,
                  "initial_layer_infill_speed":100,
                  "initial_layer_travel_speed":"100%",
                  "outer_wall_speed":100,
                  "inner_wall_speed":100,
                  "top_surface_speed":100,
                  "travel_speed":100,
                  "sparse_infill_speed":100,
                  "internal_solid_infill_speed":100,
                  "gap_infill_speed":100
                }
            """.trimIndent()
        )
        return ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )
    }

    private class MapAutomationConfigInput(
        private val extras: Map<String, Any>
    ) : AutomationConfigInput {
        override fun getStringExtra(name: String): String? = extras[name] as? String

        override fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean =
            extras[name] as? Boolean ?: defaultValue

        override fun getFloatExtra(name: String, defaultValue: Float): Float =
            extras[name] as? Float ?: defaultValue

        override fun getIntExtra(name: String, defaultValue: Int): Int =
            extras[name] as? Int ?: defaultValue

        override fun hasExtra(name: String): Boolean = extras.containsKey(name)
    }
}
