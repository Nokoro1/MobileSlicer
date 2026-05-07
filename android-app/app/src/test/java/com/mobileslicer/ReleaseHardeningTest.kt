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
        assertNotNull(exportedComponents.single().children("intent-filter").singleOrNull())
    }

    @Test
    fun manifestCleartextIsDocumentedAndRuntimeGatedForPrinterNetworking() {
        val application = androidManifest().documentElement.children("application").single()
        assertEquals("true", application.androidAttr("usesCleartextTraffic"))

        val securityDoc = repoFile("README/SECURITY.md").readText()
        assertTrue(securityDoc.contains("Runtime printer networking refuses cleartext `http://` for non-local hosts"))

        val urlGuards = File("src/main/java/com/mobileslicer/printerconnection/PrinterConnectionUrls.kt").readText()
        assertTrue(urlGuards.contains("requireAllowedPrinterBaseUrl"))
        assertTrue(urlGuards.contains("requireAllowedPrinterNetworkUrl"))
        assertTrue(urlGuards.contains("Cleartext HTTP is only allowed for local printer addresses"))

        val repository = File("src/main/java/com/mobileslicer/printerconnection/PrinterConnectionRepository.kt").readText()
        assertTrue(repository.contains("requireAllowedPrinterNetworkUrl(url)"))
    }

    @Test
    fun skirtOutputSupportIsVisibleInReleaseClaims() {
        val releaseStatus = repoFile("README/RELEASE_STATUS.md").readText()
        val truthRows = File("src/main/java/com/mobileslicer/profiles/ProfileSettingTruthRows.kt").readText()
        val wrapperAdhesion = repoFile("engine-wrapper/orca_wrapper_config_adhesion_helpers.h").readText()
        val releaseGateScript = repoFile("scripts/verify_android.sh").readText()

        assertTrue(releaseStatus.contains("Skirt output is device-tested through the skirt parity matrix"))
        assertTrue(truthRows.contains("Current skirt parity proof"))
        assertTrue(wrapperAdhesion.contains("""config.set_deserialize_strict("skirt_loops", std::to_string(loops))"""))
        assertTrue(wrapperAdhesion.contains("""extract_number(json, "skirts")"""))
        assertTrue(releaseGateScript.contains("skirt-parity"))
        assertTrue(releaseGateScript.contains("brim_skirt_config"))
        assertTrue(releaseGateScript.contains(""""skirts":0"""))
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
