package com.mobileslicer.workspace

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

internal object ThreeMfProjectInspector {
    fun inspect(file: File): ThreeMfProjectMetadata? {
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                val configEntries = entries
                    .filter { it.startsWith("Metadata/") && it.endsWith(".config", ignoreCase = true) }
                    .sorted()
                val thumbnailEntries = entries
                    .filter(::isProjectThumbnailEntry)
                    .sorted()
                val modelSettings = zip.readTextEntry("Metadata/model_settings.config")
                val sliceInfo = zip.readTextEntry("Metadata/slice_info.config")
                val rootModel = zip.readTextEntry("3D/3dmodel.model")

                val objectSummaries = parseObjectSummaries(modelSettings)
                val objectNames = objectSummaries.mapNotNull { it.name }.distinct()
                val objectFilamentAssignments = objectSummaries.mapNotNull { summary ->
                    val name = summary.name ?: return@mapNotNull null
                    val extruder = summary.extruder ?: return@mapNotNull null
                    ThreeMfObjectFilamentAssignment(objectName = name, filamentIndex = extruder)
                }
                val plateNames = parsePlateNames(modelSettings).ifEmpty {
                    parsePlateNames(sliceInfo)
                }
                val filamentCount = parseFilamentCount(sliceInfo)
                val buildItemCount = parseBuildItemCount(rootModel)
                val objectCount = objectNames.size.takeIf { it > 0 } ?: buildItemCount
                val plateCount = plateNames.size.takeIf { it > 0 }
                    ?: entries.count { it.matches(Regex("""Metadata/plate_\d+\.json""")) }.takeIf { it > 0 }
                val preservedFeatures = buildPreservedFeatures(
                    entries = entries,
                    objectNames = objectNames,
                    objectFilamentAssignments = objectFilamentAssignments,
                    plateNames = plateNames,
                    thumbnailEntries = thumbnailEntries,
                    configEntries = configEntries,
                    filamentCount = filamentCount
                )
                val unsupportedFeatures = detectUnsupportedFeatureWarnings(modelSettings)

                ThreeMfProjectMetadata(
                    sourcePath = file.absolutePath,
                    plateCount = plateCount,
                    objectCount = objectCount.takeIf { it > 0 },
                    plateNames = plateNames,
                    objectNames = objectNames,
                    objectFilamentAssignments = objectFilamentAssignments,
                    filamentCount = filamentCount,
                    thumbnailEntries = thumbnailEntries,
                    configEntries = configEntries,
                    preservedFeatures = preservedFeatures,
                    unsupportedFeatures = unsupportedFeatures
                )
            }
        }.getOrNull()
    }

    private fun isProjectThumbnailEntry(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".png") &&
            (
                lower.contains("thumbnail") ||
                    lower.contains("/.thumbnails/") ||
                    lower.matches(Regex("""metadata/(plate|plate_small|plate_no_light|top|pick)_?\d*\.png""")) ||
                    lower.matches(Regex("""metadata/plate_\d+(_small)?\.png""")) ||
                    lower.matches(Regex("""metadata/(plate_no_light|top|pick)_\d+\.png"""))
                )
    }

    private fun ZipFile.readTextEntry(name: String): String? =
        getEntry(name)?.let { entry ->
            getInputStream(entry).use { input -> input.readBytes().toString(Charsets.UTF_8) }
        }

    private data class ObjectSummary(
        val name: String?,
        val extruder: Int?
    )

    private fun parseObjectSummaries(xml: String?): List<ObjectSummary> {
        val document = parseXml(xml) ?: return emptyList()
        val objects = document.getElementsByTagName("object")
        return List(objects.length) { index -> objects.item(index) as? Element }
            .mapNotNull { element ->
                element ?: return@mapNotNull null
                val metadata = directChildMetadata(element)
                val name = metadata["name"]?.takeIf { it.isNotBlank() }
                val extruder = metadata["extruder"]?.toIntOrNull()
                if (name == null && extruder == null) null else ObjectSummary(name, extruder)
            }
    }

    private fun parsePlateNames(xml: String?): List<String> {
        val document = parseXml(xml) ?: return emptyList()
        val plates = document.getElementsByTagName("plate")
        return List(plates.length) { index -> plates.item(index) as? Element }
            .mapIndexedNotNull { index, element ->
                element ?: return@mapIndexedNotNull null
                val metadata = directChildMetadata(element)
                metadata["plater_name"]
                    ?.takeIf { it.isNotBlank() }
                    ?: metadata["index"]?.takeIf { it.isNotBlank() }?.let { "Plate $it" }
                    ?: "Plate ${index + 1}"
            }
            .distinct()
    }

    private fun parseFilamentCount(xml: String?): Int? {
        val document = parseXml(xml) ?: return null
        val filaments = document.getElementsByTagName("filament")
        return filaments.length.takeIf { it > 0 }
    }

    private fun parseBuildItemCount(xml: String?): Int {
        val document = parseXml(xml) ?: return 0
        return document.getElementsByTagName("item").length
    }

    private fun directChildMetadata(element: Element): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName != "metadata") continue
            val key = child.getAttribute("key").ifBlank { child.getAttribute("name") }
            val value = child.getAttribute("value").ifBlank { child.textContent.orEmpty() }
            if (key.isNotBlank()) result[key] = value
        }
        return result
    }

    private fun parseXml(xml: String?): org.w3c.dom.Document? {
        if (xml.isNullOrBlank()) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))))
        }.getOrNull()
    }

    private fun buildPreservedFeatures(
        entries: List<String>,
        objectNames: List<String>,
        objectFilamentAssignments: List<ThreeMfObjectFilamentAssignment>,
        plateNames: List<String>,
        thumbnailEntries: List<String>,
        configEntries: List<String>,
        filamentCount: Int?
    ): List<String> = buildList {
        if ("3D/3dmodel.model" in entries) add("mesh_geometry")
        if (objectNames.isNotEmpty()) add("object_names")
        if (objectFilamentAssignments.isNotEmpty()) add("object_filament_assignments")
        if (plateNames.isNotEmpty()) add("plate_metadata")
        if (filamentCount != null && filamentCount > 0) add("filament_metadata")
        if (thumbnailEntries.isNotEmpty()) add("project_thumbnails")
        if ("Metadata/project_settings.config" in configEntries) add("project_settings")
        if ("Metadata/model_settings.config" in configEntries) add("model_settings")
        if ("Metadata/slice_info.config" in configEntries) add("slice_info")
        if (entries.any { it.matches(Regex("""Metadata/plate_\d+\.gcode""")) }) add("sliced_plate_gcode_entries")
    }

    private fun detectUnsupportedFeatureWarnings(modelSettings: String?): List<String> {
        if (modelSettings.isNullOrBlank()) return emptyList()
        val lower = modelSettings.lowercase(Locale.US)
        return buildList {
            if ("modifier" in lower || "parameter_modifier" in lower) {
                add("modifier_round_trip_requires_validation")
            }
            if ("paint" in lower || "color" in lower) {
                add("paint_round_trip_requires_validation")
            }
        }
    }
}
