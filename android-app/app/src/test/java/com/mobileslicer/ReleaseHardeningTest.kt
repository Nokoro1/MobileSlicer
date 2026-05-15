package com.mobileslicer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ReleaseHardeningTest {
    @Test
    fun manifestDisablesAppBackupAndDataExtractionUsesRules() {
        val application = androidManifest().documentElement
            .children("application")
            .single()

        assertEquals("false", application.androidAttr("allowBackup"))
        assertEquals("false", application.androidAttr("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttr("dataExtractionRules"))
    }

    @Test
    fun fileProviderIsPrivateAndGrantsOnlySharedCachePath() {
        val provider = androidManifest().documentElement
            .children("application")
            .single()
            .children("provider")
            .single { it.androidAttr("name") == "androidx.core.content.FileProvider" }

        assertEquals("false", provider.androidAttr("exported"))
        assertEquals("true", provider.androidAttr("grantUriPermissions"))
        assertEquals("${'$'}{applicationId}.fileprovider", provider.androidAttr("authorities"))

        val metaData = provider.children("meta-data").single()
        assertEquals("@xml/file_paths", metaData.androidAttr("resource"))

        val paths = resourceXml("xml/file_paths.xml").documentElement
        val cachePaths = paths.children("cache-path")
        assertEquals(1, cachePaths.size)
        assertEquals("shared_gcode", cachePaths.single().androidAttr("name"))
        assertEquals("shared/", cachePaths.single().androidAttr("path"))
        assertFalse(paths.hasChildrenNamed("root-path", "external-path", "external-files-path"))
    }

    @Test
    fun dataExtractionRulesExcludeAllAppStorageDomains() {
        val rules = resourceXml("xml/data_extraction_rules.xml").documentElement
        val expectedDomains = setOf("sharedpref", "database", "file", "root")

        val cloudBackup = rules.children("cloud-backup").single()
        val deviceTransfer = rules.children("device-transfer").single()

        assertTrue(cloudBackup.excludedDomains().containsAll(expectedDomains))
        assertTrue(deviceTransfer.excludedDomains().containsAll(expectedDomains))
    }

    @Test
    fun launcherActivityIsTheOnlyExportedComponent() {
        val application = androidManifest().documentElement.children("application").single()
        val exportedComponents = application.children("activity", "provider", "service", "receiver")
            .filter { it.androidAttr("exported") == "true" }

        assertEquals(1, exportedComponents.size)
        assertEquals(".MainActivity", exportedComponents.single().androidAttr("name"))
        assertNotNull(
            exportedComponents.single()
                .children("intent-filter")
                .singleOrNull { filter ->
                    filter.children("action").any { it.androidAttr("name") == "android.intent.action.MAIN" } &&
                        filter.children("category").any { it.androidAttr("name") == "android.intent.category.LAUNCHER" }
                }
        )
    }

    @Test
    fun manifestCleartextIsDocumentedAndRuntimeGatedForPrinterNetworking() {
        val application = androidManifest().documentElement.children("application").single()
        assertEquals("@xml/network_security_config", application.androidAttr("networkSecurityConfig"))
        assertEquals("", application.androidAttr("usesCleartextTraffic"))

        val networkSecurityConfig = resourceXml("xml/network_security_config.xml").documentElement
        assertEquals("true", networkSecurityConfig.children("base-config").single().attr("cleartextTrafficPermitted"))

        val urlGuards = File("src/main/java/com/mobileslicer/printerconnection/PrinterConnectionUrls.kt").readText()
        assertTrue(urlGuards.contains("requireAllowedPrinterBaseUrl"))
        assertTrue(urlGuards.contains("requireAllowedPrinterNetworkUrl"))
        assertTrue(urlGuards.contains("Cleartext HTTP is only allowed for local printer addresses"))

        val repository = File("src/main/java/com/mobileslicer/printerconnection/PrinterConnectionRepository.kt").readText()
        assertTrue(repository.contains("requireAllowedPrinterNetworkUrl(url)"))
    }

    @Test
    fun skirtOutputSupportIsVisibleInReleaseClaims() {
        val truthRows = File("src/main/java/com/mobileslicer/profiles/ProfileSettingTruthRows.kt").readText()
        val wrapperAdhesion = repoFile("engine-wrapper/orca_wrapper_config_adhesion_helpers.h").readText()
        val releaseGateScript = repoFile("scripts/verify_android.sh").readText()

        assertTrue(truthRows.contains("brim-plus-skirt outputs"))
        assertTrue(wrapperAdhesion.contains("""config.set_deserialize_strict("skirt_loops", std::to_string(loops))"""))
        assertTrue(wrapperAdhesion.contains("""extract_number(json, "skirts")"""))
        assertTrue(releaseGateScript.contains("skirt-parity"))
        assertTrue(releaseGateScript.contains("brim_skirt_config"))
        assertTrue(Regex(""""skirts"\s*:\s*0""").containsMatchIn(releaseGateScript))
    }

    @Test
    fun automationIsExplicitlyDisabledForReleaseBuilds() {
        val buildGradle = File("build.gradle.kts").readText()
        assertTrue(buildGradle.contains("""buildConfigField("boolean", "AUTOMATION_ENABLED", "true")"""))
        assertTrue(buildGradle.contains("""buildConfigField("boolean", "AUTOMATION_ENABLED", "false")"""))

        val automationSource = File("src/main/java/com/mobileslicer/MainActivityAutomation.kt").readText()
        assertTrue(automationSource.contains("BuildConfig.AUTOMATION_ENABLED"))
        assertFalse(automationSource.contains("BuildConfig.DEBUG"))

        val paintAutomationSource = File("src/main/java/com/mobileslicer/PaintInteractionAutomation.kt").readText()
        val previewAutomationSource = File("src/main/java/com/mobileslicer/PreviewInteractionAutomation.kt").readText()
        assertTrue(paintAutomationSource.contains("BuildConfig.AUTOMATION_ENABLED"))
        assertTrue(previewAutomationSource.contains("BuildConfig.AUTOMATION_ENABLED"))
        assertFalse(paintAutomationSource.contains("BuildConfig.DEBUG"))
        assertFalse(previewAutomationSource.contains("BuildConfig.DEBUG"))
    }

    @Test
    fun nativeOrcaAutoArrangeUsesParallelSolver() {
        val planningSource = repoFile("engine-wrapper/orca_wrapper_planning_api.cpp").readText()
        val arrangeFunction = planningSource.substringAfter("extern \"C\" int orca_plan_plate_arrangement")
            .substringBefore("extern \"C\" int orca_plan_auto_orientation")
        assertTrue(arrangeFunction.contains("params.parallel = true;"))
        assertFalse(arrangeFunction.contains("params.parallel = false;"))
        assertTrue(arrangeFunction.contains("Slic3r::arrangement::arrange(items, excludes, bed_points, params);"))
        assertTrue(arrangeFunction.contains("<< \" parallel=\" << (params.parallel ? 1 : 0)"))
    }

    @Test
    fun stepConversionValidatesWrittenBinaryStlBeforeReportingSuccess() {
        val wrapperSource = repoFile("engine-wrapper/orca_wrapper.cpp").readText()
        val stepFunction = wrapperSource.substringAfter("extern \"C\" int orca_convert_step_to_stl")
            .substringBefore("extern \"C\" int orca_split_model_mesh_to_stls")

        assertTrue(wrapperSource.contains("validate_binary_stl_output"))
        assertTrue(stepFunction.contains("validate_binary_stl_output(output_stl_path, facet_count"))
        assertTrue(wrapperSource.contains("converted STEP STL contains zero triangles"))
        assertTrue(wrapperSource.contains("converted STEP STL has invalid binary size"))
    }

    @Test
    fun convertedModelImportFailureCleansOriginalStagedSource() {
        val importSource = File("src/main/java/com/mobileslicer/MainActivityModelLoading.kt").readText()
        val nativeFailureBranch = importSource.substringAfter("\"model_import_failed native_load_failed")
            .substringBefore("ModelLoadResult(")

        assertTrue(nativeFailureBranch.contains("workspaceModelFile.delete()"))
        assertTrue(nativeFailureBranch.contains("SourceModelFileFormat.Step"))
        assertTrue(nativeFailureBranch.contains("SourceModelFileFormat.ThreeMf"))
        assertTrue(nativeFailureBranch.contains("stagedFile.delete()"))
    }

    @Test
    fun workspaceProcessActionsUseExplicitPlateAndPresetLabels() {
        val profilesScreen = File("src/main/java/com/mobileslicer/profiles/ProfilesScreen.kt").readText()
        val selectedTab = File("src/main/java/com/mobileslicer/profiles/ProfilesSelectedTabContent.kt").readText()
        val parityDoc = repoFile("docs/ORCA_PROFILE_PARITY.md").readText()

        assertTrue(profilesScreen.contains("\"Apply to plate\""))
        assertTrue(profilesScreen.contains("\"Save preset\""))
        assertTrue(selectedTab.contains("\"Apply to plate\""))
        assertTrue(selectedTab.contains("\"Applied to plate\""))
        assertTrue(parityDoc.contains("`Apply to plate`"))
        assertTrue(parityDoc.contains("`Save preset`"))
    }

    @Test
    fun androidDependencySkipChecksEveryBoostLibraryUsedByCmake() {
        val depsScript = repoFile("engine-wrapper/orca-android-libslic3r/build-android-deps.sh").readText()
        val boostSkipCheck = depsScript.substringAfter("build_boost() {")
            .substringBefore("if [ -d \"${'$'}src_dir\" ]")

        assertTrue(boostSkipCheck.contains("libboost_filesystem.a"))
        assertTrue(boostSkipCheck.contains("libboost_thread.a"))
        assertTrue(boostSkipCheck.contains("libboost_chrono.a"))
        assertTrue(boostSkipCheck.contains("libboost_atomic.a"))
    }

    @Test
    fun orcaImportSmokeCoversStlThreeMfAndStepAutomation() {
        val verifyScript = repoFile("scripts/verify_android.sh").readText()
        val releaseGate = repoFile("scripts/release_gate_android.sh").readText()
        val stepFixture = repoFile("regression-fixtures/import/occt_screw.step")
        val stepText = stepFixture.readText()
        val stepDoc = repoFile("docs/STEP_IMPORT.md").readText()

        assertTrue(stepFixture.isFile)
        assertTrue(stepText.contains("ISO-10303-21"))
        assertTrue(stepText.contains("MANIFOLD_SOLID_BREP"))
        assertTrue(verifyScript.contains("run_orca_import_smoke"))
        assertTrue(verifyScript.contains("run_orca_3mf_roundtrip_device_gate"))
        assertTrue(verifyScript.contains("orca-import-stl"))
        assertTrue(verifyScript.contains("orca-import-3mf"))
        assertTrue(verifyScript.contains("orca-import-step"))
        assertTrue(verifyScript.contains("orca-3mf-roundtrip-device"))
        assertTrue(releaseGate.contains("orca-3mf-roundtrip-device"))
        assertTrue(verifyScript.contains("regression-fixtures/import/occt_screw.step"))
        assertTrue(releaseGate.contains("orca-import-smoke"))
        assertTrue(stepDoc.contains("orca-import-smoke"))
    }

    @Test
    fun automationSlicePathConvertsOrcaImportFormatsBeforeNativeLoad() {
        val runnerSource = File("src/main/java/com/mobileslicer/automation/AutomationSliceRunner.kt").readText()

        assertTrue(runnerSource.contains("prepareAutomationWorkspaceModel"))
        assertTrue(runnerSource.contains("NativeEngineCalls.extractModelMeshToStl"))
        assertTrue(runnerSource.contains("NativeEngineCalls.convertStepToStl"))
        assertTrue(runnerSource.contains("AUTOMATION_STEP_LINEAR_DEFLECTION"))
        assertTrue(runnerSource.contains("EXTRA_PRESERVE_PROJECT_OBJECTS"))
        assertTrue(runnerSource.contains("EXTRA_EXPORT_PROJECT_3MF"))
        assertTrue(runnerSource.contains("runProject3mfRoundTripExport"))
        assertTrue(runnerSource.contains("NativeEngineCalls.loadProject3mf"))
        assertTrue(runnerSource.contains("NativeEngineCalls.writeProject3mfToFile"))
        assertTrue(runnerSource.contains("sourceFormat == SourceModelFileFormat.ThreeMf"))
        assertTrue(runnerSource.contains("loadPaths = arrayOf(modelToLoad.absolutePath, modelToLoad.absolutePath)"))
        assertTrue(runnerSource.contains("loadPaths = arrayOf(modelToLoad.absolutePath)"))
    }

    @Test
    fun orcaParityDocsSeparateMeshExtractionFromFullProjectAndModifierSupport() {
        val parityDoc = repoFile("docs/ORCA_PROFILE_PARITY.md").readText()

        assertTrue(parityDoc.contains("Object And Modifier Scope"))
        assertTrue(parityDoc.contains("PlateObjectProcessOverride"))
        assertTrue(parityDoc.contains("PlateObjectModifierMesh"))
        assertTrue(parityDoc.contains("PARAMETER_MODIFIER"))
        assertTrue(parityDoc.contains("3MF Project Scope"))
        assertTrue(parityDoc.contains("Current Android 3MF support extracts printable mesh geometry"))
        assertTrue(parityDoc.contains("ThreeMfProjectMetadata"))
        assertTrue(parityDoc.contains("Full Orca/Bambu project parity is broader"))
    }

    @Test
    fun orcaStabilizationPlanDocumentsObjectOverrideAnd3MfGates() {
        val stabilizationDoc = repoFile("docs/ORCA_STABILIZATION_PLAN.md").readText()
        val workspaceModels = File("src/main/java/com/mobileslicer/workspace/WorkspaceModels.kt").readText()
        val modelLoaderSlicing = File("src/main/java/com/mobileslicer/ModelLoaderSlicing.kt").readText()
        val nativeOverrides = repoFile("engine-wrapper/orca_wrapper_model_overrides.cpp").readText()
        val verifyAndroid = repoFile("scripts/verify_android.sh").readText()
        val releaseGate = repoFile("scripts/release_gate_android.sh").readText()
        val profileParityDoc = repoFile("docs/ORCA_PROFILE_PARITY.md").readText()

        assertTrue(stabilizationDoc.contains("PlateObjectProcessOverride"))
        assertTrue(stabilizationDoc.contains("selectedProcessId"))
        assertTrue(stabilizationDoc.contains("editedProcessProfile"))
        assertTrue(stabilizationDoc.contains("ThreeMfProjectMetadata"))
        assertTrue(stabilizationDoc.contains("mobile_slicer_object_process_overrides"))
        assertTrue(stabilizationDoc.contains("gcode_label_objects"))
        assertTrue(stabilizationDoc.contains("exclude_object"))
        assertTrue(workspaceModels.contains("data class PlateObjectProcessOverride"))
        assertTrue(workspaceModels.contains("val editedProcessProfile: ProcessProfile?"))
        assertTrue(workspaceModels.contains("fun withSelectedProcess"))
        assertTrue(workspaceModels.contains("fun withEditedProcess"))
        assertTrue(workspaceModels.contains("data class ThreeMfProjectMetadata"))
        assertTrue(modelLoaderSlicing.contains("mobile_slicer_object_process_overrides"))
        assertTrue(nativeOverrides.contains("mobile_slicer_object_process_overrides"))
        assertTrue(nativeOverrides.contains("plateObjectIndex"))
        assertTrue(nativeOverrides.contains("apply_explicit_process_json_to_object"))
        assertTrue(nativeOverrides.contains("object.config.set_deserialize(key"))
        assertTrue(nativeOverrides.contains("object_process_override"))
        assertTrue(nativeOverrides.contains("acceptedKeys="))
        assertTrue(nativeOverrides.contains("ignoredKeys="))
        assertTrue(nativeOverrides.contains("is_non_object_profile_metadata_key"))
        assertTrue(nativeOverrides.contains("printer_settings_id"))
        assertTrue(nativeOverrides.contains("compatible_printers"))
        assertTrue(verifyAndroid.contains("orca-object-label-parity"))
        assertTrue(verifyAndroid.contains("gcode_label_objects=false"))
        assertTrue(verifyAndroid.contains("exclude_object=true"))
        assertTrue(verifyAndroid.contains("MOBILE_SLICER_MOONRAKER_LABEL_OFF_METADATA_MAX_MS"))
        assertTrue(releaseGate.contains("orca-object-label-parity"))
        assertTrue(profileParityDoc.contains("Object labels and exclude-object markers"))
        assertTrue(profileParityDoc.contains("Moonraker"))
    }

    private fun androidManifest() = parseXml(File("src/main/AndroidManifest.xml"))

    private fun resourceXml(relativePath: String) = parseXml(File("src/main/res", relativePath))

    private fun parseXml(file: File) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun Element.children(vararg names: String): List<Element> {
        val allowed = names.toSet()
        return (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { allowed.isEmpty() || it.tagName in allowed }
            .toList()
    }

    private fun Element.hasChildrenNamed(vararg names: String): Boolean =
        children(*names).isNotEmpty()

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(AndroidNamespace, name)
            .ifBlank { getAttribute("android:$name") }
            .ifBlank { getAttribute(name) }

    private fun Element.excludedDomains(): Set<String> =
        children("exclude")
            .filter { it.attr("path") == "." }
            .map { it.attr("domain") }
            .toSet()

    private fun Element.attr(name: String): String =
        getAttribute(name)

    private companion object {
        const val AndroidNamespace = "http://schemas.android.com/apk/res/android"

        fun repoFile(relativePath: String): File =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, relativePath) }
                .firstOrNull { it.exists() }
                ?: File(relativePath)
    }
}
