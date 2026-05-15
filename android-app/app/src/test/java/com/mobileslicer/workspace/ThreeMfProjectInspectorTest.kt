package com.mobileslicer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThreeMfProjectInspectorTest {
    @Test
    fun inspectCapturesOrcaProjectEvidenceWithoutReadingGcodePayloads() {
        val fixture = File.createTempFile("mobileslicer-orca-project-", ".3mf")
        try {
            writeFixture(fixture)

            val metadata = ThreeMfProjectInspector.inspect(fixture)

            assertNotNull(metadata)
            checkNotNull(metadata)
            assertEquals(fixture.absolutePath, metadata.sourcePath)
            assertEquals(1, metadata.plateCount)
            assertEquals(2, metadata.objectCount)
            assertEquals(listOf("Plate 1"), metadata.plateNames)
            assertEquals(listOf("left_cube.stl", "right_cube.stl"), metadata.objectNames)
            assertEquals(
                listOf(
                    ThreeMfObjectFilamentAssignment("left_cube.stl", 1),
                    ThreeMfObjectFilamentAssignment("right_cube.stl", 2)
                ),
                metadata.objectFilamentAssignments
            )
            assertEquals(2, metadata.filamentCount)
            assertEquals(
                listOf("Metadata/plate_1.png", "Metadata/plate_1_small.png", "Metadata/top_1.png"),
                metadata.thumbnailEntries
            )
            assertTrue(metadata.configEntries.contains("Metadata/model_settings.config"))
            assertTrue(metadata.configEntries.contains("Metadata/project_settings.config"))
            assertTrue(metadata.configEntries.contains("Metadata/slice_info.config"))
            assertTrue(metadata.preservedFeatures.contains("object_names"))
            assertTrue(metadata.preservedFeatures.contains("object_filament_assignments"))
            assertTrue(metadata.preservedFeatures.contains("project_thumbnails"))
            assertTrue(metadata.preservedFeatures.contains("sliced_plate_gcode_entries"))
        } finally {
            fixture.delete()
        }
    }

    private fun writeFixture(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry("3D/3dmodel.model", rootModelXml)
            zip.writeEntry("Metadata/model_settings.config", modelSettingsXml)
            zip.writeEntry("Metadata/slice_info.config", sliceInfoXml)
            zip.writeEntry("Metadata/project_settings.config", "{}")
            zip.writeEntry("Metadata/plate_1.png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            zip.writeEntry("Metadata/plate_1_small.png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            zip.writeEntry("Metadata/top_1.png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            zip.writeEntry("Metadata/plate_1.gcode", ByteArray(1024) { 'G'.code.toByte() })
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeEntry(name: String, text: String) =
        writeEntry(name, text.toByteArray(Charsets.UTF_8))

    private val rootModelXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
          <resources>
            <object id="2" type="model"/>
            <object id="4" type="model"/>
          </resources>
          <build>
            <item objectid="2"/>
            <item objectid="4"/>
          </build>
        </model>
    """.trimIndent()

    private val modelSettingsXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <config>
          <object id="2">
            <metadata key="name" value="left_cube.stl"/>
            <metadata key="extruder" value="1"/>
          </object>
          <object id="4">
            <metadata key="name" value="right_cube.stl"/>
            <metadata key="extruder" value="2"/>
          </object>
          <plate>
            <metadata key="plater_id" value="1"/>
            <metadata key="plater_name" value="Plate 1"/>
            <metadata key="thumbnail_file" value="Metadata/plate_1.png"/>
          </plate>
        </config>
    """.trimIndent()

    private val sliceInfoXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <config>
          <plate>
            <metadata key="index" value="1"/>
            <filament id="1" type="PLA" color="#ff0000"/>
            <filament id="2" type="PETG" color="#00ff00"/>
          </plate>
        </config>
    """.trimIndent()
}
