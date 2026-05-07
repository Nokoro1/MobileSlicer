package com.mobileslicer

import com.mobileslicer.profiles.NativeConfigKeys
import com.mobileslicer.profiles.OrcaFilamentImportBundle
import com.mobileslicer.profiles.OrcaFilamentPreset
import com.mobileslicer.profiles.OrcaProfileExportKind
import com.mobileslicer.profiles.ProfileImportPayload
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.exportOrcaProfileOption
import com.mobileslicer.profiles.findGenericOrcaFilamentPresetForPrinter
import com.mobileslicer.profiles.findMobileSlicerFilamentBaselineForPrinter
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.profiles.orcaProfileExportOptions
import com.mobileslicer.profiles.parseProfilePayloadZip
import com.mobileslicer.profiles.resolveDeviceOrcaFilamentJson
import com.mobileslicer.profiles.toImportedFilamentProfile
import android.content.ContextWrapper
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaProfileTransferTest {
    @Test
    fun orcaExportOptionsAreScopedToLinkedPrinterProfiles() {
        val store = transferStore()

        val printerAOption = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        assertEquals(listOf("filament_a"), printerAOption.filamentIds)
        assertEquals(listOf("process_a"), printerAOption.processIds)
        assertFalse(printerAOption.filamentIds.contains("filament_b"))
        assertFalse(printerAOption.processIds.contains("process_b"))
    }

    @Test
    fun printerBundleMatchesOrcaBundleStructure() {
        val store = transferStore()
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val entries = store.exportOrcaProfileOption(option).zipJsonEntries()
        val structure = JSONObject(entries.getValue("bundle_structure.json"))

        assertEquals("printer config bundle", structure.getString("bundle_type"))
        assertEquals("Printer A (MobileSlicer)", structure.getString("printer_preset_name"))
        assertEquals("printer/Printer A (MobileSlicer).json", structure.getJSONArray("printer_config").getString(0))
        assertEquals("filament/PLA @Printer A (MobileSlicer).json", structure.getJSONArray("filament_config").getString(0))
        assertEquals("process/0.20mm Standard @Printer A (MobileSlicer).json", structure.getJSONArray("process_config").getString(0))
        assertTrue(entries.containsKey("printer/Printer A (MobileSlicer).json"))
        assertTrue(entries.containsKey("filament/PLA @Printer A (MobileSlicer).json"))
        assertTrue(entries.containsKey("process/0.20mm Standard @Printer A (MobileSlicer).json"))
    }

    @Test
    fun exportedOrcaJsonUsesPresetIdentityAndStringScalars() {
        val store = transferStore()
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val printerJson = JSONObject(store.exportOrcaProfileOption(option).zipJsonEntries().getValue("printer/Printer A (MobileSlicer).json"))

        assertEquals("02.03.02.60", printerJson.getString("version"))
        assertEquals("Printer A (MobileSlicer)", printerJson.getString("name"))
        assertEquals("User", printerJson.getString("from"))
        assertEquals("", printerJson.getString("inherits"))
        assertEquals("1", printerJson.getString("is_custom_defined"))
        assertEquals("Printer A (MobileSlicer)", printerJson.getString(NativeConfigKeys.Printer.SettingsId))
        assertEquals("0.4", printerJson.getString(NativeConfigKeys.Printer.NozzleDiameter))
        assertEquals("", printerJson.getString("printhost_apikey"))
    }

    @Test
    fun exportedImportedOrcaProfilesApplyEditedNativeValuesBeforeWritingJson() {
        val editedStore = transferStore().let { store ->
            store.copy(
                printers = store.printers.map { printer ->
                    if (printer.id == "printer_a") {
                        printer.copy(
                            nozzleDiameterMm = 0.6f,
                            maxHeightMm = 255f,
                            orcaResolvedMachineJson = """
                                {"version":"02.03.02.60","name":"Old Printer A","from":"system","inherits":"Base Printer A","nozzle_diameter":"0.4","printable_height":"220"}
                            """.trimIndent()
                        )
                    } else {
                        printer
                    }
                },
                filaments = store.filaments.map { filament ->
                    if (filament.id == "filament_a") {
                        filament.copy(
                            flowRatio = 1.11f,
                            nozzleTemperatureC = 217,
                            orcaResolvedFilamentJson = """
                                {"filament_settings_id":"PLA @Printer A","filament_type":["PLA"],"filament_flow_ratio":["0.98"],"nozzle_temperature":["205"],"inherits":"Base PLA"}
                            """.trimIndent()
                        )
                    } else {
                        filament
                    }
                },
                processes = store.processes.map { process ->
                    if (process.id == "process_a") {
                        process.withValues(
                            "layerHeightMm" to 0.28f,
                            "wallCount" to 4,
                            "brimWidthMm" to 6f,
                            "orcaResolvedProcessJson" to """
                                {"print_settings_id":"0.20mm Standard @Printer A","layer_height":"0.2","wall_loops":"2","brim_width":"0","inherits":"Base Process","compatible_printers":["Old Printer"]}
                            """.trimIndent()
                        )
                    } else {
                        process
                    }
                }
            )
        }
        val option = editedStore.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val entries = editedStore.exportOrcaProfileOption(option).zipJsonEntries()
        val printerJson = JSONObject(entries.getValue("printer/Printer A (MobileSlicer).json"))
        val filamentJson = JSONObject(entries.getValue("filament/PLA @Printer A (MobileSlicer).json"))
        val processJson = JSONObject(entries.getValue("process/0.20mm Standard @Printer A (MobileSlicer).json"))

        assertEquals("0.6", printerJson.getString(NativeConfigKeys.Printer.NozzleDiameter))
        assertEquals("255", printerJson.getString("printable_height"))
        assertEquals("User", printerJson.getString("from"))
        assertEquals("", printerJson.getString("inherits"))
        assertEquals("1.11", filamentJson.getString("filament_flow_ratio"))
        assertEquals("217", filamentJson.getString("nozzle_temperature"))
        assertEquals("User", filamentJson.getString("from"))
        assertEquals("", filamentJson.getString("inherits"))
        assertEquals("0.28", processJson.getString("layer_height"))
        assertEquals("4", processJson.getString("wall_loops"))
        assertEquals("6", processJson.getString("brim_width"))
        assertEquals("User", processJson.getString("from"))
        assertEquals("", processJson.getString("inherits"))
        assertEquals("Printer A (MobileSlicer)", filamentJson.getJSONArray("compatible_printers").getString(0))
        assertEquals("Printer A (MobileSlicer)", processJson.getJSONArray("compatible_printers").getString(0))
    }

    @Test
    fun exportedDeviceImportedProfileNamesStripJsonExtensionAndSourceHash() {
        val store = transferStore().let { base ->
            base.copy(
                filaments = base.filaments.map { filament ->
                    if (filament.id == "filament_a") {
                        filament.copy(
                            name = "ABS",
                            materialType = "ABS",
                            orcaFilamentPath = "device://ABS.json#81420242",
                            orcaResolvedFilamentJson = """{"filament_settings_id":"ABS","filament_type":["ABS"]}"""
                        )
                    } else {
                        filament
                    }
                },
                processes = base.processes.map { process ->
                    if (process.id == "process_a") {
                        process.withValues(
                            "name" to "Standard Quality",
                            "orcaProcessPath" to "device://Standard Quality.json#29f10244",
                            "orcaResolvedProcessJson" to """{"print_settings_id":"Standard Quality","layer_height":"0.2","wall_loops":"2"}"""
                        )
                    } else {
                        process
                    }
                }
            )
        }
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val entries = store.exportOrcaProfileOption(option).zipJsonEntries()
        val structure = JSONObject(entries.getValue("bundle_structure.json"))
        val filamentPath = structure.getJSONArray("filament_config").getString(0)
        val processPath = structure.getJSONArray("process_config").getString(0)
        val filamentJson = JSONObject(entries.getValue(filamentPath))
        val processJson = JSONObject(entries.getValue(processPath))

        assertEquals("filament/ABS @Printer A (MobileSlicer).json", filamentPath)
        assertEquals("process/Standard Quality @Printer A (MobileSlicer).json", processPath)
        assertEquals("ABS @Printer A (MobileSlicer)", filamentJson.getString("name"))
        assertEquals("Standard Quality @Printer A (MobileSlicer)", processJson.getString("name"))
        assertFalse(filamentJson.getString("name").contains(".json#"))
        assertFalse(processJson.getString("name").contains(".json#"))
    }

    @Test
    fun exportedEditedFilamentNameWinsOverOriginalOrcaSourcePath() {
        val store = transferStore().let { base ->
            base.copy(
                filaments = base.filaments.map { filament ->
                    if (filament.id == "filament_a") {
                        filament.copy(
                            name = "ABS",
                            materialType = "ABS",
                            nozzleTemperatureC = 250,
                            orcaFilamentPath = "filament/Generic ABS @Qidi Q2.json",
                            orcaResolvedFilamentJson = """
                                {
                                  "filament_settings_id":"Generic ABS @Qidi Q2",
                                  "filament_type":["ABS"],
                                  "nozzle_temperature":["250"]
                                }
                            """.trimIndent()
                        )
                    } else {
                        filament
                    }
                }
            )
        }
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val entries = store.exportOrcaProfileOption(option).zipJsonEntries()
        val structure = JSONObject(entries.getValue("bundle_structure.json"))
        val filamentPath = structure.getJSONArray("filament_config").getString(0)
        val filamentJson = JSONObject(entries.getValue(filamentPath))

        assertEquals("filament/ABS @Printer A (MobileSlicer).json", filamentPath)
        assertEquals("ABS @Printer A (MobileSlicer)", filamentJson.getString("name"))
        assertEquals("ABS @Printer A (MobileSlicer)", filamentJson.getString("filament_settings_id"))
        assertEquals("ABS", filamentJson.getString("filament_type"))
        assertEquals("250", filamentJson.getString("nozzle_temperature"))
        assertEquals("Printer A (MobileSlicer)", filamentJson.getJSONArray("compatible_printers").getString(0))
    }

    @Test
    fun exportedOrcaJsonOmitsEmptyVectorSettingsThatCrashDesktopImport() {
        val store = transferStore().let { base ->
            base.copy(
                printers = base.printers.map { printer ->
                    if (printer.id == "printer_a") {
                        printer.copy(
                            orcaResolvedMachineJson = """
                                {
                                  "version":"02.03.02.60",
                                  "name":"Old Printer A",
                                  "extruder_colour":"",
                                  "extruder_printable_area":"",
                                  "bed_exclude_area":"",
                                  "printhost_apikey":"secret"
                                }
                            """.trimIndent()
                        )
                    } else {
                        printer
                    }
                },
                filaments = base.filaments.map { filament ->
                    if (filament.id == "filament_a") {
                        filament.copy(
                            orcaResolvedFilamentJson = """
                                {
                                  "filament_settings_id":"PLA @Printer A",
                                  "filament_type":["PLA"],
                                  "filament_notes":"",
                                  "volumetric_speed_coefficients":""
                                }
                            """.trimIndent()
                        )
                    } else {
                        filament
                    }
                },
                processes = base.processes.map { process ->
                    if (process.id == "process_a") {
                        process.withValues(
                            "orcaResolvedProcessJson" to """
                                {
                                  "print_settings_id":"0.20mm Standard @Printer A",
                                  "layer_height":"0.2",
                                  "post_process":""
                                }
                            """.trimIndent()
                        )
                    } else {
                        process
                    }
                }
            )
        }
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val entries = store.exportOrcaProfileOption(option).zipJsonEntries()
        val printerJson = JSONObject(entries.getValue("printer/Printer A (MobileSlicer).json"))
        val filamentJson = JSONObject(entries.getValue("filament/PLA @Printer A (MobileSlicer).json"))
        val processJson = JSONObject(entries.getValue("process/0.20mm Standard @Printer A (MobileSlicer).json"))

        assertFalse(printerJson.has("extruder_colour"))
        assertFalse(printerJson.has("extruder_printable_area"))
        assertFalse(printerJson.has("bed_exclude_area"))
        assertFalse(filamentJson.has("filament_notes"))
        assertFalse(filamentJson.has("volumetric_speed_coefficients"))
        assertFalse(processJson.has("post_process"))
        assertEquals("", printerJson.getString("inherits"))
        assertEquals("", filamentJson.getString("compatible_printers_condition"))
        assertEquals("", processJson.getString("compatible_printers_condition"))
    }

    @Test
    fun exportedProcessJsonUsesOrcaSafeIroningSpacingDefaults() {
        val store = transferStore().let { base ->
            base.copy(
                processes = base.processes.map { process ->
                    if (process.id == "process_a") {
                        process.withValues(
                            "supportIroning" to false,
                            "supportIroningFlowPercent" to 0f,
                            "supportIroningSpacingMm" to 0f,
                            "ironingSpacingMm" to 0f,
                            "orcaResolvedProcessJson" to """
                                {
                                  "print_settings_id":"0.20mm Standard @Printer A",
                                  "support_ironing":"0",
                                  "support_ironing_flow":"0%",
                                  "support_ironing_spacing":"0",
                                  "ironing_spacing":"0"
                                }
                            """.trimIndent()
                        )
                    } else {
                        process
                    }
                }
            )
        }
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle && it.printerIds == listOf("printer_a") }

        val processJson = JSONObject(
            store.exportOrcaProfileOption(option)
                .zipJsonEntries()
                .getValue("process/0.20mm Standard @Printer A (MobileSlicer).json")
        )

        assertEquals("10", processJson.getString("support_ironing_flow"))
        assertEquals("0.1", processJson.getString("support_ironing_spacing"))
        assertEquals("0.1", processJson.getString("ironing_spacing"))
    }

    @Test
    fun filamentBundleUsesOrcaFilamentBundleManifest() {
        val store = transferStore()
        val option = store.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.FilamentBundle && it.filamentIds == listOf("filament_a") }

        val entries = store.exportOrcaProfileOption(option).zipJsonEntries()
        val structure = JSONObject(entries.getValue("bundle_structure.json"))

        assertEquals("filament config bundle", structure.getString("bundle_type"))
        assertEquals("PLA", structure.getString("filament_name"))
        assertEquals("VendorA/PLA.json", structure.getJSONArray("printer_vendor").getJSONObject(0).getJSONArray("filament_path").getString(0))
        assertTrue(entries.containsKey("VendorA/PLA.json"))
    }

    @Test
    fun regularPresetZipUsesOrcaRootJsonEntries() {
        val store = transferStore()

        val printerZip = store.exportOrcaProfileOption(
            store.orcaProfileExportOptions().single { it.kind == OrcaProfileExportKind.PrinterPresetsZip && it.printerIds == listOf("printer_a") }
        ).zipJsonEntries()
        val filamentZip = store.exportOrcaProfileOption(
            store.orcaProfileExportOptions().single { it.kind == OrcaProfileExportKind.FilamentPresetsZip && it.filamentIds == listOf("filament_a") }
        ).zipJsonEntries()
        val processZip = store.exportOrcaProfileOption(
            store.orcaProfileExportOptions().single { it.kind == OrcaProfileExportKind.ProcessPresetsZip && it.processIds == listOf("process_a") }
        ).zipJsonEntries()

        assertTrue(printerZip.containsKey("Printer A.json"))
        assertFalse(printerZip.keys.any { it.startsWith("printer/") })
        assertTrue(filamentZip.containsKey("PLA.json"))
        assertFalse(filamentZip.keys.any { it.startsWith("filament/") })
        assertTrue(processZip.containsKey("0.20mm Standard @Printer A.json"))
        assertFalse(processZip.keys.any { it.startsWith("process/") })
    }

    @Test
    fun multiJsonPresetZipImportsEveryJsonLikeOrca() {
        val zip = zipBytes(
            "0.20mm Standard @Printer A.json" to """{"print_settings_id":"0.20mm Standard @Printer A","layer_height":"0.2","wall_loops":"2"}""",
            "0.28mm Draft @Printer A.json" to """{"print_settings_id":"0.28mm Draft @Printer A","layer_height":"0.28","wall_loops":"2"}"""
        )

        val payload = parseProfilePayloadZip(ContextWrapper(null), zip, transferStore())

        assertTrue(payload is ProfileImportPayload.Store)
        val result = (payload as ProfileImportPayload.Store).store
        assertEquals(0, result.printerCount)
        assertEquals(0, result.filamentCount)
        assertEquals(2, result.processCount)
        assertEquals(listOf("0.20mm Standard @Printer A", "0.28mm Draft @Printer A"), result.store.processes.map { it.name })
    }

    @Test
    fun nonBundleZipCorrelatesFilamentsAndProcessesAgainstPrintersImportedFromSameZip() {
        val zip = zipBytes(
            "printer/Test Printer X.json" to """
                {
                  "printer_settings_id":"Test Printer X",
                  "name":"Test Printer X",
                  "printable_area":["0x0","220x0","220x220","0x220"],
                  "printable_height":"240",
                  "nozzle_diameter":"0.4",
                  "printer_model":"Test Printer X"
                }
            """.trimIndent(),
            "filament/PLA @Test Printer X.json" to """
                {
                  "filament_settings_id":"PLA @Test Printer X",
                  "filament_type":["PLA"],
                  "compatible_printers":["Test Printer X"],
                  "nozzle_temperature":["215"]
                }
            """.trimIndent(),
            "process/0.20mm Standard @Test Printer X.json" to """
                {
                  "print_settings_id":"0.20mm Standard @Test Printer X",
                  "layer_height":"0.2",
                  "wall_loops":"2",
                  "compatible_printers":["Test Printer X"]
                }
            """.trimIndent()
        )

        val payload = parseProfilePayloadZip(
            ContextWrapper(null),
            zip,
            ProfileStore(
                printers = emptyList(),
                filaments = emptyList(),
                processes = emptyList(),
                selectedPrinterId = "",
                selectedFilamentId = "",
                selectedProcessId = ""
            )
        )

        assertTrue(payload is ProfileImportPayload.Store)
        val result = (payload as ProfileImportPayload.Store).store
        val printer = result.store.printers.single()
        assertEquals("Test Printer X", printer.name)
        assertEquals(printer.id, result.store.filaments.single().printerProfileId)
        assertEquals(printer.id, result.store.processes.single().printerProfileId)
    }

    @Test
    fun explicitOrcaPresetIdentityWinsOverBroadHeuristics() {
        val zip = zipBytes(
            "process/Ambiguous Process.json" to """
                {
                  "print_settings_id":"Ambiguous Process",
                  "layer_height":"0.2",
                  "wall_loops":"2",
                  "nozzle_temperature":"215"
                }
            """.trimIndent()
        )

        val payload = parseProfilePayloadZip(ContextWrapper(null), zip, transferStore())

        assertTrue(payload is ProfileImportPayload.Process)
        assertEquals("Ambiguous Process", (payload as ProfileImportPayload.Process).profile.name)
    }

    @Test
    fun vendoredOrcaPrinterFixtureImportsAndExportsBackToOrcaBundleShape() {
        val fixture = repoRoot()
            .resolve("vendor/orcaslicer/resources/profiles/Anet/machine/Anet A8 Plus 0.4 nozzle.orca_printer")
        val payload = parseProfilePayloadZip(
            ContextWrapper(null),
            fixture.readBytes(),
            emptyProfileStore()
        )

        assertTrue(payload is ProfileImportPayload.Store)
        val imported = (payload as ProfileImportPayload.Store).store.store
        assertEquals(1, imported.printers.size)
        assertEquals(10, imported.filaments.size)
        assertEquals(3, imported.processes.size)

        val option = imported.orcaProfileExportOptions()
            .single { it.kind == OrcaProfileExportKind.PrinterBundle }
        val entries = imported.exportOrcaProfileOption(option).zipJsonEntries()
        val structure = JSONObject(entries.getValue("bundle_structure.json"))

        assertEquals("printer config bundle", structure.getString("bundle_type"))
        assertEquals("Anet A8 Plus 0.4 nozzle (MobileSlicer)", structure.getString("printer_preset_name"))
        assertEquals(1, structure.getJSONArray("printer_config").length())
        assertEquals(10, structure.getJSONArray("filament_config").length())
        assertEquals(3, structure.getJSONArray("process_config").length())
        val exportedProcessPath = structure.getJSONArray("process_config").toStringList()
            .single { it.contains("0.20mm Standard") }
        val exportedFilamentPath = structure.getJSONArray("filament_config").toStringList()
            .single { it.contains("Anycubic Generic PLA @") }
        val exportedProcess = JSONObject(entries.getValue(exportedProcessPath))
        val exportedFilament = JSONObject(entries.getValue(exportedFilamentPath))
        assertEquals("Anet A8 Plus 0.4 nozzle (MobileSlicer)", exportedProcess.getJSONArray("compatible_printers").getString(0))
        assertTrue(exportedFilament.getJSONArray("compatible_printers").toString().contains("Anet A8 Plus 0.4 nozzle (MobileSlicer)"))
        assertTrue(exportedProcess.has(NativeConfigKeys.Process.SettingsId))
        assertTrue(JSONObject(entries.getValue("printer/Anet A8 Plus 0.4 nozzle (MobileSlicer).json")).has(NativeConfigKeys.Printer.SettingsId))
    }

    @Test
    fun filamentGapBaselineIsScopedToCorrelatedPrinter() {
        val store = transferStore().copy(
            filaments = transferStore().filaments + ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "filament_b_pla",
                name = "PLA",
                materialType = "PLA",
                printerProfileId = "printer_b",
                flowRatio = 1.04f,
                nozzleTemperatureC = 222,
                bedTemperatureC = 63
            )
        )
        val partialFilamentJson = JSONObject("""{"filament_settings_id":"Generic PLA @Printer B","filament_type":["PLA"]}""")

        val printerBBaseline = findMobileSlicerFilamentBaselineForPrinter(
            currentStore = store,
            json = partialFilamentJson,
            displayName = "Generic PLA @Printer B.json",
            printerId = "printer_b"
        )
        val printerABaseline = findMobileSlicerFilamentBaselineForPrinter(
            currentStore = store,
            json = partialFilamentJson,
            displayName = "Generic PLA @Printer B.json",
            printerId = "printer_a"
        )

        assertEquals("1.04", printerBBaseline?.get("filament_flow_ratio").toString())
        assertEquals("222", printerBBaseline?.get("nozzle_temperature").toString())
        assertEquals("0.98", printerABaseline?.get("filament_flow_ratio").toString())
    }

    @Test
    fun partialFilamentImportPreservesExplicitTemperatureAndVolumetricValues() {
        val partialFilamentJson = JSONObject(
            """
            {
              "filament_settings_id":"Fast PETG @Printer B",
              "filament_type":["PETG"],
              "nozzle_temperature_initial_layer":["251"],
              "nozzle_temperature":["247"],
              "hot_plate_temp_initial_layer":["82"],
              "hot_plate_temp":["78"],
              "filament_max_volumetric_speed":["18.5"],
              "filament_flow_ratio":["0.96"]
            }
            """.trimIndent()
        )

        val resolved = resolveDeviceOrcaFilamentJson(
            currentStore = transferStore(),
            json = partialFilamentJson,
            displayName = "Fast PETG @Printer B.json",
            printerId = "printer_b",
            parentJson = null
        )

        assertEquals("251", resolved.getJSONArray("nozzle_temperature_initial_layer").getString(0))
        assertEquals("247", resolved.getJSONArray("nozzle_temperature").getString(0))
        assertEquals("82", resolved.getJSONArray("hot_plate_temp_initial_layer").getString(0))
        assertEquals("78", resolved.getJSONArray("hot_plate_temp").getString(0))
        assertEquals("18.5", resolved.getJSONArray("filament_max_volumetric_speed").getString(0))
        assertEquals("0.96", resolved.getJSONArray("filament_flow_ratio").getString(0))
        assertEquals("1.75", resolved.get("filament_diameter").toString())

        val imported = OrcaFilamentPreset(
            name = "Fast PETG",
            rawName = "Fast PETG @Printer B",
            family = "VendorB",
            materialType = "PETG",
            vendor = "Generic",
            defaultFilamentColor = "",
            profilePath = "filament/Fast PETG @Printer B.json",
            importBundleAssetPath = "",
            compatiblePrinters = listOf("Printer B"),
            compatiblePrinterKeys = listOf("Printer B"),
            pickerDuplicateKey = "",
            searchText = "fast petg printer b"
        ).toImportedFilamentProfile(
            OrcaFilamentImportBundle(
                rawFilamentJson = partialFilamentJson.toString(),
                resolvedFilamentJson = resolved.toString(),
                resolvedSourceChain = emptyList()
            ),
            printer = transferStore().printers.first { it.id == "printer_b" }
        )

        assertEquals(251, imported.nozzleTemperatureInitialLayerC)
        assertEquals(247, imported.nozzleTemperatureC)
        assertEquals(82, imported.bedTemperatureInitialLayerC)
        assertEquals(78, imported.bedTemperatureC)
        assertEquals(18.5f, imported.maxVolumetricSpeedMm3PerSec)
        assertEquals(0.96f, imported.flowRatio)
    }

    @Test
    fun printerOrcaDefaultBaselineWinsForMissingFilamentTemperatures() {
        val store = transferStore().copy(
            filaments = transferStore().filaments + ProfileStoreRepository.fallbackFilamentProfile().copy(
                id = "wrong_local_abs",
                name = "ABS",
                materialType = "ABS",
                printerProfileId = "printer_b",
                nozzleTemperatureInitialLayerC = 270,
                nozzleTemperatureC = 270
            )
        )
        val partialFilamentJson = JSONObject("""{"filament_settings_id":"Generic ABS @Printer B","filament_type":["ABS"]}""")
        val printerDefault = JSONObject()
            .put("filament_type", "ABS")
            .put("filament_diameter", "1.75")
            .put("filament_flow_ratio", "0.94")
            .put("nozzle_temperature_initial_layer", "250")
            .put("nozzle_temperature", "250")
        val broaderParent = JSONObject()
            .put("filament_type", "ABS")
            .put("filament_flow_ratio", "1.01")
            .put("nozzle_temperature_initial_layer", "270")
            .put("nozzle_temperature", "270")

        val resolved = resolveDeviceOrcaFilamentJson(
            currentStore = store,
            json = partialFilamentJson,
            displayName = "Generic ABS @Printer B.json",
            printerId = "printer_b",
            parentJson = broaderParent,
            printerDefaultJson = printerDefault
        )

        assertEquals("250", resolved.get("nozzle_temperature_initial_layer").toString())
        assertEquals("250", resolved.get("nozzle_temperature").toString())
        assertEquals("0.94", resolved.get("filament_flow_ratio").toString())
    }

    @Test
    fun genericFilamentPresetSelectionUsesExactCompatibleQidiPrinter() {
        val qidiQ2 = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "qidi_q2",
            name = "Qidi Q2",
            orcaFamily = "Qidi",
            nozzleDiameterMm = 0.4f,
            orcaResolvedMachineJson = """{"name":"Qidi Q2 0.4 nozzle","inherits":"Qidi Q2 0.4 nozzle"}"""
        )
        val q2Preset = genericPreset(
            rawName = "Generic ABS @Qidi Q2 0.4 nozzle",
            compatiblePrinter = "Qidi Q2 0.4 nozzle",
            path = "Qidi/filament/Q2/Generic ABS @Qidi Q2 0.4 nozzle.json"
        )
        val q2cPreset = genericPreset(
            rawName = "Generic ABS @Qidi Q2C 0.4 nozzle",
            compatiblePrinter = "Qidi Q2C 0.4 nozzle",
            path = "Qidi/filament/Q2/Generic ABS @Qidi Q2C 0.4 nozzle.json"
        )

        val selected = findGenericOrcaFilamentPresetForPrinter(
            presets = listOf(q2cPreset, q2Preset),
            printer = qidiQ2,
            materialType = "ABS"
        )

        assertEquals("Generic ABS @Qidi Q2 0.4 nozzle", selected?.rawName)
    }

    private fun transferStore(): ProfileStore {
        val printerA = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_a",
            name = "Printer A",
            machineFamily = "VendorA",
            orcaFamily = "VendorA",
            nozzleDiameterMm = 0.4f,
            orcaResolvedMachineJson = """{"version":"bad","name":"Old Printer A","nozzle_diameter":"0.4","printable_height":"220","printhost_apikey":"secret"}"""
        )
        val printerB = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_b",
            name = "Printer B",
            machineFamily = "VendorB",
            orcaFamily = "VendorB",
            nozzleDiameterMm = 0.6f
        )
        val filamentA = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_a",
            name = "PLA",
            vendor = "Generic",
            flowRatio = 0.98f,
            printerProfileId = printerA.id,
            orcaFilamentPath = "filament/PLA @Printer A.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"PLA @Printer A","filament_type":["PLA"],"filament_flow_ratio":["0.98"],"filament_diameter":["1.75"]}"""
        )
        val filamentB = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_b",
            name = "PETG",
            materialType = "PETG",
            vendor = "Generic",
            printerProfileId = printerB.id
        )
        val processA = newProcessProfileUnchecked(
            0 to "process_a",
            1 to "0.20mm Standard @Printer A",
            3 to false,
            5 to 0.20f,
            259 to printerA.id,
            261 to 0.4f,
            263 to "process/0.20mm Standard @Printer A.json",
            265 to """{"print_settings_id":"0.20mm Standard @Printer A","layer_height":"0.2","wall_loops":"2"}"""
        )
        val processB = newProcessProfileUnchecked(
            0 to "process_b",
            1 to "0.24mm Draft @Printer B",
            3 to false,
            5 to 0.24f,
            259 to printerB.id
        )
        return ProfileStore(
            printers = listOf(printerA, printerB),
            filaments = listOf(filamentA, filamentB),
            processes = listOf(processA, processB),
            selectedPrinterId = printerA.id,
            selectedFilamentId = filamentA.id,
            selectedProcessId = processA.id
        )
    }

    private fun ByteArray.zipJsonEntries(): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json")) {
                    entries[entry.name] = zip.readBytes().toString(StandardCharsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> getString(index) }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray =
        java.io.ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun genericPreset(rawName: String, compatiblePrinter: String, path: String): OrcaFilamentPreset =
        OrcaFilamentPreset(
            name = "ABS",
            rawName = rawName,
            family = "Qidi",
            materialType = "ABS",
            vendor = "Generic",
            defaultFilamentColor = "",
            profilePath = path,
            importBundleAssetPath = "",
            compatiblePrinters = listOf(compatiblePrinter),
            compatiblePrinterKeys = listOf(compatiblePrinter.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()),
            pickerDuplicateKey = "",
            searchText = rawName.lowercase()
        )

    private fun emptyProfileStore(): ProfileStore =
        ProfileStore(
            printers = emptyList(),
            filaments = emptyList(),
            processes = emptyList(),
            selectedPrinterId = "",
            selectedFilamentId = "",
            selectedProcessId = ""
        )

    private fun repoRoot(): File {
        var current = File(System.getProperty("user.dir").orEmpty()).canonicalFile
        while (current.parentFile != null && !File(current, "vendor/orcaslicer").exists()) {
            current = current.parentFile!!.canonicalFile
        }
        return current
    }
}
