package com.mobileslicer

import com.mobileslicer.profiles.EnsureVerticalShellThicknessMode
import com.mobileslicer.profiles.DefaultBedType
import com.mobileslicer.profiles.ExtraBridgeLayerMode
import com.mobileslicer.profiles.FuzzySkinMode
import com.mobileslicer.profiles.FuzzySkinNoiseType
import com.mobileslicer.profiles.FuzzySkinType
import com.mobileslicer.profiles.GeneratedOrcaSettingMetadata
import com.mobileslicer.profiles.InternalBridgeFilterMode
import com.mobileslicer.profiles.IroningPattern
import com.mobileslicer.profiles.OrcaFilamentPreset
import com.mobileslicer.profiles.OrcaPrinterImportBundle
import com.mobileslicer.profiles.OrcaPrinterPreset
import com.mobileslicer.profiles.OrcaProcessPresetBundle
import com.mobileslicer.profiles.ProfileEditorSetting
import com.mobileslicer.profiles.ProfileEditorOrcaUiOrder
import com.mobileslicer.profiles.ProfileSettingVisibility
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.ProcessIroningType
import com.mobileslicer.profiles.ProcessGcodeOutputDetails
import com.mobileslicer.profiles.ProcessPrimeTowerDetails
import com.mobileslicer.profiles.ProcessQualitySurfaceDetails
import com.mobileslicer.profiles.ProcessSpecialModeDetails
import com.mobileslicer.profiles.ProcessStrengthInfillDetails
import com.mobileslicer.profiles.encodeProfileSelection
import com.mobileslicer.profiles.filamentEditorGroupLabelsForParityTest
import com.mobileslicer.profiles.filamentEditorTabLabelsForParityTest
import com.mobileslicer.profiles.filteredOrcaFilamentPresets
import com.mobileslicer.profiles.findResolvedOrcaProcessFallback
import com.mobileslicer.profiles.findGenericOrcaFilamentPresetForPrinter
import com.mobileslicer.profiles.findGenericOrcaFilamentFallbackPreset
import com.mobileslicer.profiles.findSystemGenericOrcaFilamentPreset
import com.mobileslicer.profiles.isVisible
import com.mobileslicer.profiles.needsSystemGenericOrcaFallback
import com.mobileslicer.profiles.parseProfileSelection
import com.mobileslicer.profiles.printerEditorGroupLabelsForParityTest
import com.mobileslicer.profiles.printerEditorTabLabelsForParityTest
import com.mobileslicer.profiles.processEditorGroupLabelsForParityTest
import com.mobileslicer.profiles.processEditorTabLabelsForParityTest
import com.mobileslicer.profiles.profileEditorOrcaKeysForVisibility
import com.mobileslicer.profiles.repairWithResolvedOrcaProcessFallback
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.profiles.SupportBasePattern
import com.mobileslicer.profiles.SupportInterfacePattern
import com.mobileslicer.profiles.SupportStyle
import com.mobileslicer.profiles.SupportType
import com.mobileslicer.profiles.TimelapseType
import com.mobileslicer.profiles.WallDirection
import com.mobileslicer.profiles.WallInfillOrder
import com.mobileslicer.profiles.WallSequence
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.assetImageCache
import com.mobileslicer.profiles.buildOrcaFilamentPickerRows
import com.mobileslicer.profiles.filamentColor
import com.mobileslicer.profiles.filamentColorCache
import com.mobileslicer.profiles.filteredOrcaFilamentPickerRows
import com.mobileslicer.profiles.isReplaceableOrcaGenericMaterialFor
import com.mobileslicer.profiles.orcaFilamentCompatibleKeysCache
import com.mobileslicer.profiles.resolveOrcaFilamentPresetForImport
import com.mobileslicer.profiles.toFilamentProfile
import com.mobileslicer.profiles.toJson
import com.mobileslicer.profiles.toImportedPrinterProfile
import com.mobileslicer.profiles.toProcessProfile
import com.mobileslicer.profiles.toPrinterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileModelsTest {
    @Test
    fun profileUiCachesAreBounded() {
        synchronized(filamentColorCache) {
            filamentColorCache.clear()
            repeat(160) { index ->
                filamentColor("#${index.toString(16).padStart(6, '0')}")
            }
            assertTrue(filamentColorCache.size <= 128)
        }
        synchronized(assetImageCache) {
            assetImageCache.clear()
            @Suppress("UNCHECKED_CAST")
            val rawAssetImageCache = assetImageCache as MutableMap<String, Any?>
            repeat(80) { index ->
                rawAssetImageCache["asset_$index"] = null
            }
            assertTrue(assetImageCache.size <= 64)
        }
    }

    @Test
    fun filamentCompatibleKeyCacheIsBounded() {
        synchronized(orcaFilamentCompatibleKeysCache) {
            orcaFilamentCompatibleKeysCache.clear()
            repeat(600) { index ->
                orcaFilamentCompatibleKeysCache["preset_$index"] = listOf("printer_$index")
            }
            assertTrue(orcaFilamentCompatibleKeysCache.size <= 512)
        }
    }

    @Test
    fun filamentJsonRoundTripPreservesMinFanSpeed() {
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            minFanSpeedPercent = 42,
            coolingPercent = 87
        )

        val restored = filament.toJson().toFilamentProfile()

        assertEquals(42, restored.minFanSpeedPercent)
        assertEquals(87, restored.coolingPercent)
    }

    @Test
    fun activeConfigurationDoesNotUseStaleProcessFromAnotherPrinter() {
        val printerA = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_a",
            name = "Printer A"
        )
        val printerB = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_b",
            name = "Printer B"
        )
        val staleProcess = processFixture().copy(
            id = "process_stale",
            name = "0.20mm Standard @Printer A"
        ).withValues("printerProfileId" to printerA.id, "layerHeightMm" to 0.2f)
        val fineProcess = processFixture().copy(
            id = "process_fine",
            name = "0.12mm Fine @Printer B"
        ).withValues("printerProfileId" to printerB.id, "layerHeightMm" to 0.12f)
        val standardProcess = processFixture().copy(
            id = "process_standard",
            name = "0.18mm Standard @Printer B"
        ).withValues("printerProfileId" to printerB.id, "layerHeightMm" to 0.18f)
        val store = ProfileStore(
            printers = listOf(printerA, printerB),
            filaments = listOf(
                ProfileStoreRepository.fallbackFilamentProfile().copy(
                    id = "filament_b",
                    printerProfileId = printerB.id
                )
            ),
            processes = listOf(staleProcess, fineProcess, standardProcess),
            selectedPrinterId = printerB.id,
            selectedFilamentId = "filament_b",
            selectedProcessId = staleProcess.id
        )

        assertEquals(standardProcess.id, store.activeConfiguration().process.id)
    }

    @Test
    fun importedProfilesPreserveResolvedOrcaPrinterFilamentAndProcessData() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_04",
            name = "Audit Printer",
            orcaResolvedMachineJson = """{"printer_passthrough_key":"printer-value","shared_profile_key":"printer"}"""
        )
        val filament = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "filament_abs",
            name = "ABS",
            orcaResolvedFilamentJson = """{"filament_passthrough_key":"filament-value","shared_profile_key":"filament"}"""
        )
        val process = processFixture().copy(
            id = "process_standard",
            name = "0.20mm Standard",
            orcaResolvedProcessJson = """{"process_passthrough_key":"process-value","shared_profile_key":"process"}"""
        )
        val store = ProfileStore(
            printers = listOf(printer),
            filaments = listOf(filament),
            processes = listOf(process),
            selectedPrinterId = printer.id,
            selectedFilamentId = filament.id,
            selectedProcessId = process.id
        )

        assertEquals(
            """{"printer_passthrough_key":"printer-value","shared_profile_key":"printer"}""",
            store.printers.single().orcaResolvedMachineJson
        )
        assertEquals(
            """{"filament_passthrough_key":"filament-value","shared_profile_key":"filament"}""",
            store.filaments.single().orcaResolvedFilamentJson
        )
        assertEquals(
            """{"process_passthrough_key":"process-value","shared_profile_key":"process"}""",
            store.processes.single().orcaResolvedProcessJson
        )
    }

    @Test
    fun importedPrinterUsesMachineModelMultiBedSupportWhenResolvedOmitsIt() {
        val preset = OrcaPrinterPreset(
            name = "Fixture Multibed Printer",
            family = "Fixture",
            searchText = "Fixture Multibed Printer",
            nozzleDiameters = "0.4",
            profilePath = "Fixture/machine/Fixture Multibed Printer.json",
            coverAssetPath = "",
            importBundleAssetPath = "",
            bedModelAssetPath = "",
            bedTextureAssetPath = "",
            bedWidthMm = 220f,
            bedDepthMm = 220f,
            maxHeightMm = 250f,
            activeNozzleDiameterMm = 0.4f,
            nozzleMachinePaths = emptyList(),
            resolvedSourceChains = emptyList()
        )
        val bundle = OrcaPrinterImportBundle(
            machineModelJson = """{"support_multi_bed_types":"1","default_bed_type":"Textured PEI Plate"}""",
            resolvedMachineJson = """{"name":"Fixture Multibed Printer 0.4 nozzle","nozzle_diameter":"0.4","bed_mesh_min":["10","10"],"bed_mesh_max":["290","260"],"bed_mesh_probe_distance":["50","50"]}""",
            nozzleMachineJsons = emptyList(),
            resolvedMachineJsons = emptyList(),
            processPresets = emptyList()
        )

        val printer = preset.toImportedPrinterProfile(bundle, selectedNozzleDiameterMm = 0.4f)

        assertTrue(printer.supportMultiBedTypes)
        assertEquals(DefaultBedType.TexturedPeiPlate, printer.defaultBedType)
        assertEquals("10,10", printer.bedMeshMin)
        assertEquals("290,260", printer.bedMeshMax)
        assertEquals("50,50", printer.bedMeshProbeDistance)
    }

    @Test
    fun importedBambuPrinterDefaultsToBambuLanConnection() {
        val preset = OrcaPrinterPreset(
            name = "Bambu Lab P1S",
            family = "BBL",
            searchText = "Bambu Lab P1S",
            nozzleDiameters = "0.4",
            profilePath = "BBL/machine/Bambu Lab P1S.json",
            coverAssetPath = "",
            importBundleAssetPath = "",
            bedModelAssetPath = "",
            bedTextureAssetPath = "",
            bedWidthMm = 256f,
            bedDepthMm = 256f,
            maxHeightMm = 256f,
            activeNozzleDiameterMm = 0.4f,
            nozzleMachinePaths = emptyList(),
            resolvedSourceChains = emptyList()
        )
        val bundle = OrcaPrinterImportBundle(
            machineModelJson = """{"name":"Bambu Lab P1S","default_bed_type":"Textured PEI Plate"}""",
            resolvedMachineJson = """{"name":"Bambu Lab P1S 0.4 nozzle","printer_model":"Bambu Lab P1S","nozzle_diameter":"0.4"}""",
            nozzleMachineJsons = emptyList(),
            resolvedMachineJsons = emptyList(),
            processPresets = emptyList()
        )

        val printer = preset.toImportedPrinterProfile(bundle, selectedNozzleDiameterMm = 0.4f)

        assertEquals(PrintHostType.BambuLan, printer.printHostType)
        assertEquals("Textured PEI Plate", printer.bambuBedType)
    }

    @Test
    fun savedLegacyBambuPrinterWithGenericDefaultMigratesToBambuLanConnection() {
        val stored = ProfileStoreRepository.fallbackPrinterProfile().copy(
            name = "Bambu Lab P2S 0.4 nozzle",
            printerModel = "Bambu Lab P2S",
            orcaFamily = "BBL",
            printHostType = PrintHostType.OctoPrint,
            printHost = "",
            printHostApiKey = "",
            printHostPort = ""
        ).toJson().apply {
            remove("printHostType")
        }

        val printer = stored.toPrinterProfile()

        assertEquals(PrintHostType.BambuLan, printer.printHostType)
    }

    @Test
    fun profileSettingVisibilityMatchesOrcaAdvancedToggleRules() {
        assertTrue(ProfileEditorSetting.FilamentBedTemperature.isVisible(showAdvancedProfileSettings = false))
        assertTrue(ProfileEditorSetting.FilamentCoolingBaseline.isVisible(showAdvancedProfileSettings = false))
        assertTrue(ProfileEditorSetting.ProcessInfillCore.isVisible(showAdvancedProfileSettings = false))

        assertFalse(ProfileEditorSetting.PrinterBedDimensions.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.PrinterNozzleDiameter.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentFlowRatio.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentMaxVolumetricSpeed.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentRetractionOverrides.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentPressureAdvance.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentAdaptiveVolumetricSpeed.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.FilamentGcodeAdvanced.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.ProcessSpeedAndAcceleration.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.ProcessFuzzySkinDetail.isVisible(showAdvancedProfileSettings = false))
        assertFalse(ProfileEditorSetting.ProcessIroningAdvanced.isVisible(showAdvancedProfileSettings = false))

        assertTrue(ProfileEditorSetting.PrinterBedDimensions.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.FilamentMaxVolumetricSpeed.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.FilamentRetractionOverrides.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.FilamentPressureAdvance.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.FilamentAdaptiveVolumetricSpeed.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.FilamentGcodeAdvanced.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.ProcessSpeedAndAcceleration.isVisible(showAdvancedProfileSettings = true))
        assertTrue(ProfileEditorSetting.ProcessIroningAdvanced.isVisible(showAdvancedProfileSettings = true))

        val simpleKeys = profileEditorOrcaKeysForVisibility(ProfileSettingVisibility.Simple)
        val advancedKeys = profileEditorOrcaKeysForVisibility(ProfileSettingVisibility.Advanced)
        assertTrue("hot-plate temperatures should stay visible by default", "hot_plate_temp" in simpleKeys)
        assertTrue("cooling baseline should stay visible by default", "fan_min_speed" in simpleKeys)
        assertTrue("filament flow ratio should be advanced", "filament_flow_ratio" in advancedKeys)
        assertTrue("pressure advance should be advanced", "pressure_advance" in advancedKeys)
        assertTrue("filament G-code should be advanced", "filament_start_gcode" in advancedKeys)
        assertTrue("process ironing should be advanced", "ironing_type" in advancedKeys)
        assertTrue("advanced max volumetric speed should be hidden by default", "filament_max_volumetric_speed" in advancedKeys)
        assertFalse("fan_min_speed must not be classified as advanced", "fan_min_speed" in advancedKeys)
    }

    @Test
    fun profileEditorTabAndProcessGroupOrderMirrorsOrcaReference() {
        assertEquals(ProfileEditorOrcaUiOrder.processTabs, processEditorTabLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.filamentTabs, filamentEditorTabLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.printerTabs, printerEditorTabLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.processGroups, processEditorGroupLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.filamentGroups, filamentEditorGroupLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.printerGroups, printerEditorGroupLabelsForParityTest())
        assertEquals(ProfileEditorOrcaUiOrder.processTabs.toSet(), processEditorGroupLabelsForParityTest().keys)
        assertEquals(ProfileEditorOrcaUiOrder.filamentTabs.toSet(), filamentEditorGroupLabelsForParityTest().keys)
        assertEquals(ProfileEditorOrcaUiOrder.printerTabs.toSet(), printerEditorGroupLabelsForParityTest().keys)
    }

    @Test
    fun filamentDependenciesUseStructuredSelectionEncodingInsteadOfRawUiBlobs() {
        val rawJsonList = """["Bambu Lab X1 Carbon 0.4 nozzle","Qidi Q2 0.4 nozzle"]"""
        val rawSemicolonList = "Bambu Lab X1 Carbon 0.4 nozzle; Qidi Q2 0.4 nozzle"

        assertEquals(setOf("Bambu Lab X1 Carbon 0.4 nozzle", "Qidi Q2 0.4 nozzle"), parseProfileSelection(rawJsonList))
        assertEquals(setOf("Bambu Lab X1 Carbon 0.4 nozzle", "Qidi Q2 0.4 nozzle"), parseProfileSelection(rawSemicolonList))
        assertEquals("", encodeProfileSelection(emptyList()))
        assertEquals(
            "Qidi Q2 0.4 nozzle;Bambu Lab X1 Carbon 0.4 nozzle",
            encodeProfileSelection(listOf("Qidi Q2 0.4 nozzle", "Bambu Lab X1 Carbon 0.4 nozzle", "Qidi Q2 0.4 nozzle"))
        )
    }

    @Test
    fun orcaFilamentPickerDefaultsToAllPresetsAndUsesCompatibilityOnlyForRecommendedMode() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_audit",
            name = "Audit Printer",
            nozzleDiameterMm = 0.4f,
            printerModel = "Audit Printer",
            orcaFamily = "Audit"
        )
        val recommended = testOrcaFilamentPreset(
            path = "audit/recommended.json",
            searchText = "audit pla",
            compatiblePrinters = listOf("Audit Printer 0.4 nozzle")
        )
        val differentPrinter = testOrcaFilamentPreset(
            path = "other/petg.json",
            searchText = "other petg",
            compatiblePrinters = listOf("Other Printer 0.6 nozzle")
        )
        val noCompatibilityMetadata = testOrcaFilamentPreset(
            path = "generic/tpu.json",
            searchText = "generic tpu",
            compatiblePrinters = emptyList()
        )
        val presets = listOf(recommended, differentPrinter, noCompatibilityMetadata)

        assertEquals(
            listOf("audit/recommended.json", "other/petg.json", "generic/tpu.json"),
            filteredOrcaFilamentPresets(
                presets = presets,
                selectedPrinter = printer,
                query = "",
                recommendedOnly = false
            ).map { it.profilePath }
        )
        assertEquals(
            listOf("audit/recommended.json"),
            filteredOrcaFilamentPresets(
                presets = presets,
                selectedPrinter = printer,
                query = "",
                recommendedOnly = true
            ).map { it.profilePath }
        )
        assertEquals(
            listOf("generic/tpu.json"),
            filteredOrcaFilamentPresets(
                presets = presets,
                selectedPrinter = printer,
                query = "TPU",
                recommendedOnly = false
            ).map { it.profilePath }
        )
    }

    @Test
    fun orcaFilamentPickerRowsCollapseVisibleDuplicatesWithoutManifestDuplicateKey() {
        val baseAbs = testOrcaFilamentPreset(
            path = "vendor/generic-abs.json",
            name = "ABS",
            rawName = "Generic ABS",
            materialType = "ABS",
            vendor = "Generic",
            searchText = "generic abs base",
            compatiblePrinters = emptyList(),
            pickerDuplicateKey = ""
        )
        val nozzleAbs = testOrcaFilamentPreset(
            path = "vendor/generic-abs-nozzle.json",
            name = "ABS",
            rawName = "Generic ABS 0.4 nozzle",
            materialType = "ABS",
            vendor = "Generic",
            searchText = "generic abs nozzle specific",
            compatiblePrinters = emptyList(),
            pickerDuplicateKey = ""
        )

        val rows = buildOrcaFilamentPickerRows(listOf(baseAbs, nozzleAbs), selectedPrinter = null)

        assertEquals(listOf("vendor/generic-abs.json"), rows.map { it.preset.profilePath })
        assertEquals(
            listOf("vendor/generic-abs.json"),
            filteredOrcaFilamentPickerRows(
                rows = rows,
                query = "nozzle specific",
                recommendedOnly = false
            ).map { it.preset.profilePath }
        )
    }

    @Test
    fun orcaFilamentPickerRowsCollapseGenericMaterialNamesAcrossVendors() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_audit",
            name = "Audit Printer",
            nozzleDiameterMm = 0.4f,
            printerModel = "Audit Printer",
            orcaFamily = "Audit"
        )
        val genericPla = testOrcaFilamentPreset(
            path = "generic/pla.json",
            name = "PLA",
            rawName = "Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            searchText = "generic pla",
            compatiblePrinters = emptyList()
        )
        val brandPla = testOrcaFilamentPreset(
            path = "brand/pla.json",
            name = "PLA",
            rawName = "Brand PLA",
            materialType = "PLA",
            vendor = "Brand",
            searchText = "brand pla",
            compatiblePrinters = emptyList()
        )
        val recommendedBrandPla = testOrcaFilamentPreset(
            path = "brand/recommended-pla.json",
            name = "PLA",
            rawName = "Brand PLA @ Audit Printer",
            materialType = "PLA",
            vendor = "Brand",
            searchText = "recommended brand pla",
            compatiblePrinters = listOf("Audit Printer 0.4 nozzle")
        )

        assertEquals(
            listOf("generic/pla.json"),
            buildOrcaFilamentPickerRows(listOf(brandPla, genericPla), selectedPrinter = printer)
                .map { it.preset.profilePath }
        )
        assertEquals(
            listOf("brand/recommended-pla.json"),
            buildOrcaFilamentPickerRows(listOf(genericPla, brandPla, recommendedBrandPla), selectedPrinter = printer)
                .map { it.preset.profilePath }
        )
    }

    @Test
    fun orcaFilamentPickerRowsPreferSystemGenericMaterialFallback() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "snapmaker_u1",
            name = "Snapmaker U1",
            nozzleDiameterMm = 0.4f,
            printerModel = "Snapmaker U1",
            orcaFamily = "Snapmaker"
        )
        val crealityGenericPla = testOrcaFilamentPreset(
            path = "Creality/filament/Creality Generic PLA.json",
            name = "PLA",
            rawName = "Creality Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            family = "Creality",
            searchText = "creality generic pla",
            compatiblePrinters = listOf("Creality K1 0.4 nozzle")
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )

        val rows = buildOrcaFilamentPickerRows(
            listOf(crealityGenericPla, systemGenericPla),
            selectedPrinter = printer
        )

        assertEquals(listOf("OrcaFilamentLibrary/filament/Generic PLA @System.json"), rows.map { it.preset.profilePath })
        assertTrue(rows.single().compatibleWithSelectedPrinter == true)
    }

    @Test
    fun systemGenericOrcaFilamentLookupMatchesMaterialWithoutPrinterSpecialCases() {
        val genericAbs = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic ABS @System.json",
            name = "ABS",
            rawName = "Generic ABS @System",
            materialType = "ABS",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic abs system",
            compatiblePrinters = emptyList()
        )
        val genericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )

        assertEquals(
            "OrcaFilamentLibrary/filament/Generic ABS @System.json",
            findSystemGenericOrcaFilamentPreset(listOf(genericPla, genericAbs), "ABS")?.profilePath
        )
    }

    @Test
    fun genericOrcaFilamentLookupPrefersCompatiblePrinterFamilyPreset() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "bambu_p2s",
            name = "Bambu Lab P2S 0.4 nozzle",
            nozzleDiameterMm = 0.4f,
            printerModel = "Bambu Lab P2S",
            orcaFamily = "BBL",
            orcaResolvedMachineJson = """{"name":"Bambu Lab P2S 0.4 nozzle","printer_model":"Bambu Lab P2S"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val p2sGenericPla = testOrcaFilamentPreset(
            path = "BBL/filament/Generic PLA @BBL P2S.json",
            name = "PLA",
            rawName = "Generic PLA @BBL P2S",
            materialType = "PLA",
            vendor = "Generic",
            family = "BBL",
            searchText = "generic pla bbl p2s",
            compatiblePrinters = listOf("Bambu Lab P2S 0.4 nozzle")
        )

        assertEquals(
            "BBL/filament/Generic PLA @BBL P2S.json",
            findGenericOrcaFilamentPresetForPrinter(listOf(systemGenericPla, p2sGenericPla), printer, "PLA")?.profilePath
        )
    }

    @Test
    fun genericOrcaFilamentLookupAcceptsFamilyPrefixedGenericMaterialPreset() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "flashforge_ad5m",
            name = "Flashforge Adventurer 5M",
            nozzleDiameterMm = 0.4f,
            printerModel = "Flashforge Adventurer 5M",
            orcaFamily = "Flashforge",
            orcaResolvedMachineJson = """{"name":"Flashforge Adventurer 5M 0.4 Nozzle","printer_model":"Flashforge Adventurer 5M"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val flashforgeGenericPla = testOrcaFilamentPreset(
            path = "Flashforge/filament/Flashforge Generic PLA.json",
            name = "PLA",
            rawName = "Flashforge Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            family = "Flashforge",
            searchText = "flashforge generic pla",
            compatiblePrinters = listOf("Flashforge Adventurer 5M 0.4 Nozzle")
        )

        assertEquals(
            "Flashforge/filament/Flashforge Generic PLA.json",
            findGenericOrcaFilamentPresetForPrinter(
                listOf(systemGenericPla, flashforgeGenericPla),
                printer,
                "PLA"
            )?.profilePath
        )
    }

    @Test
    fun selectedSystemGenericFilamentFallsForwardToPrinterCompatibleGeneric() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "flashforge_ad5m",
            name = "Flashforge Adventurer 5M",
            nozzleDiameterMm = 0.4f,
            printerModel = "Flashforge Adventurer 5M",
            orcaFamily = "Flashforge",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"name":"Flashforge Adventurer 5M 0.4 Nozzle","printer_model":"Flashforge Adventurer 5M"}"""
        )
        val selectedSystemPla = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "system_plain",
            name = "PLA",
            materialType = "PLA",
            vendor = "Generic",
            profileSource = "orca",
            orcaFamily = "OrcaFilamentLibrary",
            orcaFilamentPath = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Generic PLA @System"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val flashforgeGenericPla = testOrcaFilamentPreset(
            path = "Flashforge/filament/Flashforge Generic PLA.json",
            name = "PLA",
            rawName = "Flashforge Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            family = "Flashforge",
            searchText = "flashforge generic pla",
            compatiblePrinters = listOf("Flashforge Adventurer 5M 0.4 Nozzle")
        )

        assertEquals(
            "Flashforge/filament/Flashforge Generic PLA.json",
            findGenericOrcaFilamentFallbackPreset(
                presets = listOf(systemGenericPla, flashforgeGenericPla),
                printer = printer,
                filament = selectedSystemPla
            )?.profilePath
        )
    }

    @Test
    fun genericOrcaFilamentLookupDoesNotUseSpecialtyMaterialAsGenericFallback() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "prusa_core_one_hf",
            name = "Prusa CORE One HF 0.4 nozzle",
            nozzleDiameterMm = 0.4f,
            printerModel = "Prusa CORE One HF",
            orcaFamily = "Prusa",
            orcaResolvedMachineJson = """{"name":"Prusa CORE One HF 0.4 nozzle","printer_model":"Prusa CORE One HF"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val prusaSilkPla = testOrcaFilamentPreset(
            path = "Prusa/filament/Prusa Generic PLA Silk @CORE One.json",
            name = "PLA",
            rawName = "Prusa Generic PLA Silk @CORE One",
            materialType = "PLA",
            vendor = "Generic",
            family = "Prusa",
            searchText = "prusa generic pla silk core one",
            compatiblePrinters = listOf("Prusa CORE One HF 0.4 nozzle")
        )

        assertEquals(
            "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            findGenericOrcaFilamentPresetForPrinter(listOf(systemGenericPla, prusaSilkPla), printer, "PLA")?.profilePath
        )
    }

    @Test
    fun selectedSpecialtyGenericFilamentFallsBackToPlainSystemGeneric() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "prusa_core_one_hf",
            name = "Prusa CORE One HF 0.4 nozzle",
            printerModel = "Prusa CORE One HF",
            orcaFamily = "Prusa",
            profileSource = "orca"
        )
        val prusaSilkPla = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "prusa_silk",
            name = "PLA",
            materialType = "PLA",
            vendor = "Generic",
            profileSource = "orca",
            orcaFamily = "Prusa",
            orcaFilamentPath = "Prusa/filament/Prusa Generic PLA Silk @CORE One.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Prusa Generic PLA Silk @CORE One"}"""
        )
        val prusaPlainPla = prusaSilkPla.copy(
            id = "prusa_plain",
            name = "PLA",
            orcaFilamentPath = "Prusa/filament/Generic PLA @CORE One.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Generic PLA @CORE One"}"""
        )
        val systemGenericPla = prusaSilkPla.copy(
            id = "system_plain",
            orcaFamily = "OrcaFilamentLibrary",
            orcaFilamentPath = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Generic PLA @System"}"""
        )

        assertTrue(prusaSilkPla.needsSystemGenericOrcaFallback(printer))
        assertFalse(prusaPlainPla.needsSystemGenericOrcaFallback(printer))
        assertFalse(systemGenericPla.needsSystemGenericOrcaFallback(printer))
    }

    @Test
    fun specialtyGenericFallbackPrefersSystemGenericOverPrinterSpecificGeneric() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "prusa_core_one_hf",
            name = "Prusa CORE One HF 0.4 nozzle",
            nozzleDiameterMm = 0.4f,
            printerModel = "Prusa CORE One HF",
            orcaFamily = "Prusa",
            profileSource = "orca"
        )
        val selectedSilkPla = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "prusa_silk",
            name = "PLA",
            materialType = "PLA",
            vendor = "Generic",
            profileSource = "orca",
            orcaFamily = "Prusa",
            orcaFilamentPath = "Prusa/filament/Prusa Generic PLA Silk @CORE One.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Prusa Generic PLA Silk @CORE One"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val prusaGenericPla = testOrcaFilamentPreset(
            path = "Prusa/filament/Prusa Generic PLA @CORE One HF 0.4.json",
            name = "PLA",
            rawName = "Prusa Generic PLA @CORE One HF 0.4",
            materialType = "PLA",
            vendor = "Generic",
            family = "Prusa",
            searchText = "prusa generic pla core one hf",
            compatiblePrinters = listOf("Prusa CORE One HF 0.4 nozzle")
        )

        assertEquals(
            "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            findGenericOrcaFilamentFallbackPreset(
                presets = listOf(prusaGenericPla, systemGenericPla),
                printer = printer,
                filament = selectedSilkPla
            )?.profilePath
        )
    }

    @Test
    fun resolvedOrcaProcessFallbackMatchesSelectedNameAndNozzle() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_voron",
            name = "Voron 2.4 250 0.4 nozzle",
            nozzleDiameterMm = 0.4f
        )
        val selectedProcess = processFixture().copy(
            name = "0.20mm Standard @Voron",
            orcaResolvedProcessJson = ""
        )
        val wrongNozzle = testOrcaProcessPreset(
            name = "0.20mm Standard @Voron",
            nozzleDiameterMm = 0.6f,
            profilePath = "Voron/process/0.20mm Standard @Voron 0.6.json"
        )
        val expected = testOrcaProcessPreset(
            name = "0.20mm Standard @Voron",
            nozzleDiameterMm = 0.4f,
            profilePath = "Voron/process/0.20mm Standard @Voron.json"
        )

        assertEquals(
            expected.profilePath,
            findResolvedOrcaProcessFallback(selectedProcess, printer, listOf(wrongNozzle, expected))?.profilePath
        )
    }

    @Test
    fun unresolvedCustomProcessFallbackHydratesBaselineAndPreservesEditedOverrides() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "printer_voron",
            name = "Voron 2.4 250 0.4 nozzle",
            nozzleDiameterMm = 0.4f
        )
        val selectedProcess = processFixture().copy(
            name = "0.20mm Standard @Voron Copy",
            orcaResolvedProcessJson = ""
        ).withValues(
            "printerProfileId" to printer.id,
            "wallCount" to 4
        )
        val fallback = testOrcaProcessPreset(
            name = "0.20mm Standard @Voron",
            nozzleDiameterMm = 0.4f,
            profilePath = "Voron/process/0.20mm Standard @Voron.json",
            resolvedProcessJson = """{"print_settings_id":"0.20mm Standard @Voron","wall_loops":2,"layer_height":0.2}"""
        )

        val repaired = selectedProcess.repairWithResolvedOrcaProcessFallback(fallback, printer)

        assertEquals("""{"print_settings_id":"0.20mm Standard @Voron","wall_loops":2,"layer_height":0.2}""", repaired.orcaResolvedProcessJson)
        assertTrue(repaired.orcaProcessOverridesJson.contains("wall_loops"))
        assertTrue(repaired.orcaProcessOverridesJson.contains("4"))
    }

    @Test
    fun orcaFilamentPickerRowsPreserveBrandStyleNames() {
        val genericPla = testOrcaFilamentPreset(
            path = "generic/pla.json",
            name = "PLA",
            rawName = "Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            searchText = "generic pla",
            compatiblePrinters = emptyList()
        )
        val flashforgePro = testOrcaFilamentPreset(
            path = "flashforge/pla-pro.json",
            name = "Flashforge PLA Pro",
            rawName = "Flashforge PLA Pro",
            materialType = "PLA",
            vendor = "Flashforge",
            searchText = "flashforge pla pro",
            compatiblePrinters = emptyList()
        )

        assertEquals(
            listOf("flashforge/pla-pro.json", "generic/pla.json"),
            buildOrcaFilamentPickerRows(listOf(genericPla, flashforgePro), selectedPrinter = null)
                .map { it.preset.profilePath }
                .sorted()
        )
    }

    @Test
    fun filamentProfileStoresAdvancedFilamentControls() {
        val filament = ProfileStoreRepository.defaultFilamentProfiles().first().copy(
            flowRatio = 0.97f,
            retractionLengthMm = 0.8f,
            retractionSpeedMmPerSec = 35f,
            deretractionSpeedMmPerSec = 22f,
            pressureAdvanceEnabled = true,
            pressureAdvance = 0.035f,
            adaptiveVolumetricSpeedEnabled = true,
            volumetricSpeedCoefficients = "1.0,2.0,3.0",
            filamentStartGcode = " M117 Filament start\nG4 S1 ",
            filamentEndGcode = "\nM117 Filament end\n"
        )

        assertEquals(0.97f, filament.flowRatio, 0.0001f)
        assertEquals(0.8f, filament.retractionLengthMm ?: -1f, 0.0001f)
        assertEquals(35f, filament.retractionSpeedMmPerSec ?: -1f, 0.0001f)
        assertEquals(22f, filament.deretractionSpeedMmPerSec ?: -1f, 0.0001f)
        assertTrue(filament.pressureAdvanceEnabled)
        assertEquals(0.035f, filament.pressureAdvance, 0.0001f)
        assertTrue(filament.adaptiveVolumetricSpeedEnabled)
        assertEquals("1.0,2.0,3.0", filament.volumetricSpeedCoefficients)
        assertEquals(" M117 Filament start\nG4 S1 ", filament.filamentStartGcode)
        assertEquals("\nM117 Filament end\n", filament.filamentEndGcode)
    }

    @Test
    fun surfacedProfileVisibilityIsBackedByGeneratedOrcaMetadataOrDocumentedOverride() {
        val missingMetadata = mutableListOf<String>()
        val mismatchedVisibility = mutableListOf<String>()

        ProfileEditorSetting.entries.forEach { setting ->
            assertTrue("${setting.name} must cite Orca source or an override", setting.source.isNotBlank())
            setting.orcaKeys.forEach { key ->
                val metadata = GeneratedOrcaSettingMetadata.all[key]
                if (metadata == null || !metadata.isDefinedInPrintConfig) {
                    if (setting.overrideReason.isNullOrBlank()) {
                        missingMetadata += "${setting.name}:$key"
                    }
                    return@forEach
                }

                if (!setting.overrideReason.isNullOrBlank()) {
                    return@forEach
                }

                val expected = when (metadata.mode) {
                    "comAdvanced", "comDevelop" -> ProfileSettingVisibility.Advanced
                    "comSimple" -> ProfileSettingVisibility.Simple
                    "Default" -> if (metadata.appearsInOrcaGui) {
                        ProfileSettingVisibility.Simple
                    } else {
                        null
                    }
                    else -> null
                }
                if (expected == null || expected != setting.visibility) {
                    mismatchedVisibility += "${setting.name}:$key mode=${metadata.mode} visibility=${setting.visibility}"
                }
            }
        }

        assertTrue(
            "Profile settings missing generated Orca metadata need documented overrides: $missingMetadata",
            missingMetadata.isEmpty()
        )
        assertTrue(
            "Profile settings whose visibility disagrees with generated Orca metadata need documented overrides: $mismatchedVisibility",
            mismatchedVisibility.isEmpty()
        )
    }

    @Test
    fun processProfileStoresFuzzySkinSettings() {
        val process = processFixture().copy(
            fuzzySkin = FuzzySkinType.Contour,
            fuzzySkinThicknessMm = 0.18f,
            fuzzySkinPointDistanceMm = 0.42f,
            fuzzySkinFirstLayer = true,
            fuzzySkinMode = FuzzySkinMode.Combined,
            fuzzySkinNoiseType = FuzzySkinNoiseType.Voronoi,
            fuzzySkinScaleMm = 2.5f,
            fuzzySkinOctaves = 6,
            fuzzySkinPersistence = 0.35f
        )

        assertEquals("external", process.fuzzySkin.configValue)
        assertEquals(0.18f, process.fuzzySkinThicknessMm, 0.0001f)
        assertEquals(0.42f, process.fuzzySkinPointDistanceMm, 0.0001f)
        assertEquals(true, process.fuzzySkinFirstLayer)
        assertEquals("combined", process.fuzzySkinMode.configValue)
        assertEquals("voronoi", process.fuzzySkinNoiseType.configValue)
        assertEquals(2.5f, process.fuzzySkinScaleMm, 0.0001f)
        assertEquals(6, process.fuzzySkinOctaves)
        assertEquals(0.35f, process.fuzzySkinPersistence, 0.0001f)
    }

    @Test
    fun processFixtureKeepsFuzzySkinDisabled() {
        val process = processFixture()

        assertEquals(FuzzySkinType.Disabled, process.fuzzySkin)
        assertFalse(process.fuzzySkinFirstLayer)
    }

    @Test
    fun defaultProcessProfilesAreNotBuiltIn() {
        assertTrue(ProfileStoreRepository.defaultProcessProfiles().isEmpty())
    }

    @Test
    fun processProfileStoresProcessIroningSettings() {
        val process = processFixture().copy(
            ironingType = ProcessIroningType.AllSolidLayers,
            ironingPattern = IroningPattern.Concentric,
            ironingFlowPercent = 12.5f,
            ironingSpacingMm = 0.12f,
            ironingInsetMm = 0.25f,
            ironingAngleDegrees = 45f,
            ironingAngleFixed = true,
            ironingSpeedMmPerSec = 18f
        )

        assertEquals("solid", process.ironingType.configValue)
        assertEquals("concentric", process.ironingPattern.configValue)
        assertEquals(12.5f, process.ironingFlowPercent, 0.0001f)
        assertEquals(0.12f, process.ironingSpacingMm, 0.0001f)
        assertEquals(0.25f, process.ironingInsetMm, 0.0001f)
        assertEquals(45f, process.ironingAngleDegrees, 0.0001f)
        assertTrue(process.ironingAngleFixed)
        assertEquals(18f, process.ironingSpeedMmPerSec, 0.0001f)
    }

    @Test
    fun ensureVerticalShellThicknessModeNormalizesUnknownValues() {
        assertEquals(
            EnsureVerticalShellThicknessMode.CriticalOnly,
            EnsureVerticalShellThicknessMode.fromConfigValue("ensure_critical_only")
        )
        assertEquals(
            EnsureVerticalShellThicknessMode.All,
            EnsureVerticalShellThicknessMode.fromConfigValue("unexpected_mode")
        )
    }

    @Test
    fun supportEnumsNormalizeUnknownValues() {
        assertEquals(
            SupportType.TreeManual,
            SupportType.fromConfigValue("tree(manual)")
        )
        assertEquals(
            SupportType.NormalAuto,
            SupportType.fromConfigValue("unexpected_support_type")
        )
        assertEquals(
            SupportStyle.TreeHybrid,
            SupportStyle.fromConfigValue("tree_hybrid")
        )
        assertEquals(
            SupportStyle.Default,
            SupportStyle.fromConfigValue("unexpected_support_style")
        )
        assertEquals(
            SupportInterfacePattern.RectilinearInterlaced,
            SupportInterfacePattern.fromConfigValue("rectilinear_interlaced")
        )
        assertEquals(
            SupportInterfacePattern.Auto,
            SupportInterfacePattern.fromConfigValue("unexpected_support_interface_pattern")
        )
        assertEquals(
            SupportBasePattern.RectilinearGrid,
            SupportBasePattern.fromConfigValue("rectilinear-grid")
        )
        assertEquals(
            SupportBasePattern.Default,
            SupportBasePattern.fromConfigValue("unexpected_support_base_pattern")
        )
    }

    @Test
    fun processProfileStoresNewStage2SpeedSettings() {
        val process = processFixture().copy(
            initialLayerAccelerationMmPerSec2 = 275f,
            initialLayerJerkMmPerSec = 8f,
            firstLayerFlowRatio = 0.95f,
            defaultAccelerationMmPerSec2 = 1250f,
            innerWallJerkMmPerSec = 11f,
            innerWallFlowRatio = 1.05f,
            outerWallJerkMmPerSec = 7f,
            outerWallFlowRatio = 0.98f,
            topSolidInfillFlowRatio = 0.97f,
            bottomSolidInfillFlowRatio = 1.03f,
            overhang1_4Speed = "20",
            overhang2_4Speed = "35%",
            overhang3_4Speed = "45",
            overhang4_4Speed = "60%",
            overhangFlowRatio = 0.9f,
            dontSlowDownOuterWall = true
        )

        assertEquals(275f, process.initialLayerAccelerationMmPerSec2, 0.0001f)
        assertEquals(8f, process.initialLayerJerkMmPerSec, 0.0001f)
        assertEquals(0.95f, process.firstLayerFlowRatio, 0.0001f)
        assertEquals(1250f, process.defaultAccelerationMmPerSec2, 0.0001f)
        assertEquals(11f, process.innerWallJerkMmPerSec, 0.0001f)
        assertEquals(1.05f, process.innerWallFlowRatio, 0.0001f)
        assertEquals(7f, process.outerWallJerkMmPerSec, 0.0001f)
        assertEquals(0.98f, process.outerWallFlowRatio, 0.0001f)
        assertEquals(0.97f, process.topSolidInfillFlowRatio, 0.0001f)
        assertEquals(1.03f, process.bottomSolidInfillFlowRatio, 0.0001f)
        assertEquals("20", process.overhang1_4Speed)
        assertEquals("35%", process.overhang2_4Speed)
        assertEquals("45", process.overhang3_4Speed)
        assertEquals("60%", process.overhang4_4Speed)
        assertEquals(0.9f, process.overhangFlowRatio, 0.0001f)
        assertTrue(process.dontSlowDownOuterWall)
    }

    @Test
    fun processProfileStoresNewStage2MultimaterialSettings() {
        val process = processFixture().copy(
            enablePrimeTower = false,
            primeTowerWidthMm = 42f,
            primeTowerDetails = ProcessPrimeTowerDetails(
                primeTowerSkipPoints = false,
                primeVolumeMm3 = 46f,
                primeTowerBrimWidthMm = 4f,
                wipeTowerExtraFlowPercent = 120
            ),
            standbyTemperatureDeltaC = -12,
            wipeTowerNoSparseLayers = true,
            flushIntoInfill = true,
            flushIntoObjects = true,
            flushIntoSupport = false,
            specialModeDetails = ProcessSpecialModeDetails(
                spiralModeSmooth = true,
                timelapseType = TimelapseType.Smooth
            ),
            gcodeOutputDetails = ProcessGcodeOutputDetails(
                gcodeComments = true,
                gcodeLabelObjects = false,
                excludeObject = true
            ),
            postProcessScripts = "/tmp/filter-a;/tmp/filter-b",
            notes = "Process audit note"
        )

        assertFalse(process.enablePrimeTower)
        assertEquals(42f, process.primeTowerWidthMm, 0.0001f)
        assertFalse(process.primeTowerSkipPoints)
        assertEquals(46f, process.primeVolumeMm3, 0.0001f)
        assertEquals(4f, process.primeTowerBrimWidthMm, 0.0001f)
        assertEquals(120, process.wipeTowerExtraFlowPercent)
        assertEquals(-12, process.standbyTemperatureDeltaC)
        assertTrue(process.wipeTowerNoSparseLayers)
        assertTrue(process.flushIntoInfill)
        assertTrue(process.flushIntoObjects)
        assertFalse(process.flushIntoSupport)
        assertTrue(process.spiralModeSmooth)
        assertEquals(TimelapseType.Smooth, process.timelapseType)
        assertTrue(process.gcodeComments)
        assertFalse(process.gcodeLabelObjects)
        assertTrue(process.excludeObject)
        assertEquals("/tmp/filter-a;/tmp/filter-b", process.postProcessScripts)
        assertEquals("Process audit note", process.notes)
    }

    @Test
    fun processProfileStoresNewStage2SupportSettings() {
        val process = processFixture().copy(
            enableSupport = true,
            supportType = SupportType.TreeManual,
            supportStyle = SupportStyle.TreeHybrid,
            supportThresholdAngleDegrees = 47,
            supportBuildplateOnly = true,
            supportTopZDistanceMm = 0.18f,
            supportBottomZDistanceMm = 0.22f,
            supportInterfaceTopLayers = 3,
            supportInterfaceBottomLayers = 1,
            supportInterfaceSpacingMm = 0.35f,
            supportBottomInterfaceSpacingMm = 0.45f,
            supportInterfaceSpeedMmPerSec = 65f,
            supportInterfaceFlowRatio = 0.92f,
            supportMaterialInterfaceFanSpeed = "70",
            supportInterfacePattern = SupportInterfacePattern.Grid,
            supportInterfaceLoopPattern = true,
            supportLineWidth = "120%",
            supportBasePattern = SupportBasePattern.Honeycomb,
            supportBasePatternSpacingMm = 3.2f,
            supportSpeedMmPerSec = 95f,
            supportFlowRatio = 1.08f,
            supportObjectElevationMm = 6f,
            supportMaxBridgeLengthMm = 12f,
            supportIroning = true,
            supportIroningFlowPercent = 15f,
            supportIroningSpacingMm = 0.2f,
            supportExpansionMm = 0.6f,
            supportObjectXyDistanceMm = 0.4f
        )

        assertTrue(process.enableSupport)
        assertEquals(SupportType.TreeManual, process.supportType)
        assertEquals(SupportStyle.TreeHybrid, process.supportStyle)
        assertEquals(47, process.supportThresholdAngleDegrees)
        assertTrue(process.supportBuildplateOnly)
        assertEquals(0.18f, process.supportTopZDistanceMm, 0.0001f)
        assertEquals(0.22f, process.supportBottomZDistanceMm, 0.0001f)
        assertEquals(3, process.supportInterfaceTopLayers)
        assertEquals(1, process.supportInterfaceBottomLayers)
        assertEquals(0.35f, process.supportInterfaceSpacingMm, 0.0001f)
        assertEquals(0.45f, process.supportBottomInterfaceSpacingMm, 0.0001f)
        assertEquals(65f, process.supportInterfaceSpeedMmPerSec, 0.0001f)
        assertEquals(0.92f, process.supportInterfaceFlowRatio, 0.0001f)
        assertEquals("70", process.supportMaterialInterfaceFanSpeed)
        assertEquals(SupportInterfacePattern.Grid, process.supportInterfacePattern)
        assertTrue(process.supportInterfaceLoopPattern)
        assertEquals("120%", process.supportLineWidth)
        assertEquals(SupportBasePattern.Honeycomb, process.supportBasePattern)
        assertEquals(3.2f, process.supportBasePatternSpacingMm, 0.0001f)
        assertEquals(95f, process.supportSpeedMmPerSec, 0.0001f)
        assertEquals(1.08f, process.supportFlowRatio, 0.0001f)
        assertEquals(6f, process.supportObjectElevationMm, 0.0001f)
        assertEquals(12f, process.supportMaxBridgeLengthMm, 0.0001f)
        assertTrue(process.supportIroning)
        assertEquals(15f, process.supportIroningFlowPercent, 0.0001f)
        assertEquals(0.2f, process.supportIroningSpacingMm, 0.0001f)
        assertEquals(0.6f, process.supportExpansionMm, 0.0001f)
        assertEquals(0.4f, process.supportObjectXyDistanceMm, 0.0001f)
    }

    @Test
    fun processProfileJsonNormalizesLegacyDefaultSeamGap() {
        val process = processFixture()
            .withValues("seamGap" to "10%")
            .toJson()
            .toProcessProfile()

        assertEquals("0%", process.seamGap)
    }

    @Test
    fun processProfileJsonRoundTripPreservesSavedProcessSettings() {
        val process = processFixture().withValues(
            "enableOverhangSpeed" to false,
            "bridgeSpeedMmPerSec" to 33f,
            "adaptiveLayerHeight" to true,
            "dontFilterInternalBridges" to InternalBridgeFilterMode.NoFilter,
            "wallInfillOrder" to WallInfillOrder.InfillOuterInner,
            "wallSequence" to WallSequence.OuterInner,
            "supportThresholdOverlap" to "35%",
            "supportCriticalRegionsOnly" to true,
            "supportInterfacePattern" to SupportInterfacePattern.Grid,
            "treeSupportTipDiameterMm" to 1.1f,
            "treeSupportBranchDistanceMm" to 6.5f,
            "treeSupportBranchDistanceOrganicMm" to 1.8f,
            "treeSupportTopRatePercent" to 44,
            "treeSupportBranchDiameterOrganicMm" to 2.8f,
            "treeSupportBranchDiameterAngleDegrees" to 7f,
            "treeSupportBranchAngleOrganicDegrees" to 38f,
            "treeSupportPreferredBranchAngleDegrees" to 31f,
            "treeSupportAutoBrim" to false,
            "treeSupportBrimWidthMm" to 4.5f,
            "fuzzySkin" to FuzzySkinType.AllWalls,
            "fuzzySkinMode" to FuzzySkinMode.Combined,
            "fuzzySkinNoiseType" to FuzzySkinNoiseType.Perlin,
            "ironingType" to ProcessIroningType.TopSurfaces,
            "ironingPattern" to IroningPattern.Concentric,
            "qualitySurfaceDetails" to ProcessQualitySurfaceDetails(
                thickInternalBridges = false,
                extraBridgeLayer = ExtraBridgeLayerMode.ApplyToAll,
                preciseZHeight = true,
                onlyOneWallFirstLayer = true,
                printInfillFirst = true,
                wallDirection = WallDirection.Clockwise,
                printFlowRatio = 1.12f,
                elefantFootCompensationLayers = 3,
                internalSolidInfillFlowRatio = 0.87f,
                setOtherFlowRatios = true,
                smallAreaInfillFlowCompensation = true,
                makeOverhangPrintable = true,
                makeOverhangPrintableAngleDegrees = 62f,
                makeOverhangPrintableHoleSizeMm2 = 4f,
                overhangReverse = true,
                overhangReverseInternalOnly = true,
                overhangReverseThreshold = "60%"
            ),
            "strengthInfillDetails" to ProcessStrengthInfillDetails(
                skinInfillDensity = 42,
                skeletonInfillDensity = 17,
                infillLockDepthMm = 1.4f,
                skinInfillDepthMm = 2.6f,
                skinInfillLineWidth = "105%",
                skeletonInfillLineWidth = "96%",
                symmetricInfillYAxis = true,
                infillShiftStepMm = 0.8f,
                infillOverhangAngleDegrees = 47f,
                gapFillTarget = "everywhere",
                filterOutGapFillMm = 0.35f
            ),
            "primeTowerDetails" to ProcessPrimeTowerDetails(
                wipeTowerXmm = 18f,
                wipeTowerYmm = 205f,
                primeTowerSkipPoints = false,
                primeVolumeMm3 = 52f,
                primeTowerBrimWidthMm = 4f,
                primeTowerInfillGapPercent = 130,
                wipeTowerRotationAngleDegrees = 12f,
                wipeTowerBridgingMm = 8f,
                wipeTowerExtraSpacingPercent = 115,
                wipeTowerExtraFlowPercent = 96,
                wipeTowerMaxPurgeSpeedMmPerSec = 75f,
                wipeTowerFilletWall = false
            ),
            "specialModeDetails" to ProcessSpecialModeDetails(
                spiralModeSmooth = true,
                spiralModeMaxXySmoothing = "150%",
                spiralStartingFlowRatio = 0.2f,
                spiralFinishingFlowRatio = 0.8f,
                timelapseType = TimelapseType.Smooth,
                enableWrappingDetection = true
            ),
            "gcodeOutputDetails" to ProcessGcodeOutputDetails(
                gcodeAddLineNumber = true,
                gcodeComments = true,
                gcodeLabelObjects = true,
                excludeObject = true
            )
        )
        val savedJson = process.toJson()
        val restoredJson = savedJson.toProcessProfile().toJson()
        val keys = savedJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            assertEquals(
                "Process profile JSON key must round-trip: $key",
                jsonAssertValue(savedJson.opt(key)),
                jsonAssertValue(restoredJson.opt(key))
            )
        }
    }

    private fun processFixture() = newProcessProfileUnchecked(
        0 to "process_fixture",
        1 to "Fixture Process",
        3 to false,
        5 to 0.20f
    )

    private fun jsonAssertValue(value: Any?): String =
        if (value is Number) value.toDouble().toString() else value.toString()

    @Test
    fun importingSystemGenericFilamentResolvesToPrinterCompatibleGenericPreset() {
        val printer = ProfileStoreRepository.fallbackPrinterProfile().copy(
            id = "flashforge_ad5m",
            name = "Flashforge Adventurer 5M",
            nozzleDiameterMm = 0.4f,
            printerModel = "Flashforge Adventurer 5M",
            orcaFamily = "Flashforge",
            profileSource = "orca",
            orcaResolvedMachineJson = """{"name":"Flashforge Adventurer 5M 0.4 Nozzle","printer_model":"Flashforge Adventurer 5M"}"""
        )
        val systemGenericPla = testOrcaFilamentPreset(
            path = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            name = "PLA",
            rawName = "Generic PLA @System",
            materialType = "PLA",
            vendor = "Generic",
            family = "OrcaFilamentLibrary",
            searchText = "generic pla system",
            compatiblePrinters = emptyList()
        )
        val flashforgeGenericPla = testOrcaFilamentPreset(
            path = "Flashforge/filament/Flashforge Generic PLA.json",
            name = "PLA",
            rawName = "Flashforge Generic PLA",
            materialType = "PLA",
            vendor = "Generic",
            family = "Flashforge",
            searchText = "flashforge generic pla",
            compatiblePrinters = listOf("Flashforge Adventurer 5M 0.4 Nozzle")
        )

        val resolved = resolveOrcaFilamentPresetForImport(
            presets = listOf(systemGenericPla, flashforgeGenericPla),
            selectedPreset = systemGenericPla,
            selectedPrinter = printer
        )

        assertEquals("Flashforge/filament/Flashforge Generic PLA.json", resolved.profilePath)
    }

    @Test
    fun importingPrinterGenericFilamentReplacesOlderEquivalentSystemGenericProfile() {
        val printerId = "flashforge_ad5m"
        val existingSystemPla = ProfileStoreRepository.fallbackFilamentProfile().copy(
            id = "system_plain",
            name = "PLA",
            materialType = "PLA",
            vendor = "Generic",
            profileSource = "orca",
            printerProfileId = printerId,
            orcaFamily = "OrcaFilamentLibrary",
            orcaFilamentPath = "OrcaFilamentLibrary/filament/Generic PLA @System.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Generic PLA @System"}"""
        )
        val importedFlashforgePla = existingSystemPla.copy(
            id = "flashforge_plain",
            orcaFamily = "Flashforge",
            orcaFilamentPath = "Flashforge/filament/Flashforge Generic PLA.json",
            orcaResolvedFilamentJson = """{"filament_settings_id":"Flashforge Generic PLA"}"""
        )

        assertTrue(existingSystemPla.isReplaceableOrcaGenericMaterialFor(importedFlashforgePla, printerId))
    }

    private fun testOrcaFilamentPreset(
        path: String,
        name: String = path.substringAfter('/').substringBefore('.').replaceFirstChar { it.uppercase() },
        rawName: String = path.substringAfter('/').substringBefore('.'),
        materialType: String = "PLA",
        vendor: String = "Test",
        family: String = "Audit",
        searchText: String,
        compatiblePrinters: List<String>,
        pickerDuplicateKey: String = listOf(name, materialType, vendor).joinToString("|") {
            it.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()
        }
    ) = OrcaFilamentPreset(
        name = name,
        rawName = rawName,
        family = family,
        materialType = materialType,
        vendor = vendor,
        defaultFilamentColor = "#FFFFFF",
        profilePath = path,
        importBundleAssetPath = "",
        compatiblePrinters = compatiblePrinters,
        compatiblePrinterKeys = compatiblePrinters.map { it.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim() },
        pickerDuplicateKey = pickerDuplicateKey,
        searchText = searchText
    )

    private fun testOrcaProcessPreset(
        name: String,
        nozzleDiameterMm: Float,
        profilePath: String,
        resolvedProcessJson: String = "{}"
    ) = OrcaProcessPresetBundle(
        machineName = "Fixture",
        nozzleDiameterMm = nozzleDiameterMm,
        name = name,
        rawName = name,
        profilePath = profilePath,
        rawProcessJson = "{}",
        resolvedProcessJson = resolvedProcessJson,
        resolvedSourceChain = emptyList()
    )
}
