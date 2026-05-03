package com.mobileslicer

import com.mobileslicer.workspace.SliceResultSummary
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.viewer.ViewerModelTransform
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHelpersTest {
    @Test
    fun exportFilamentMaterialKeepsTypeOnly() {
        assertEquals("ABS", normalizedExportFilamentMaterial("Bambu ABS"))
        assertEquals("PETG-CF", normalizedExportFilamentMaterial("Generic PETG-CF @System"))
    }

    @Test
    fun exportFilenameUsesFilamentTypeInsteadOfUsage() {
        val summary = SliceResultSummary(
            byteCount = 100,
            lineCount = 10,
            layerChangeCount = 2,
            observedTypes = emptyList(),
            wallShellTypes = emptyList(),
            estimatedPrintTimeText = "1h 02m",
            filamentUsedGrams = 12.34
        )

        assertEquals(
            "mobile_slicer_output_1h02m_ABS.gcode",
            suggestExportGcodeFileName(
                plateObjects = emptyList(),
                summary = summary,
                filamentMaterial = "Bambu ABS"
            )
        )
    }

    @Test
    fun exportFilenameUsesSingleStlNameAndCapitalizedFilamentType() {
        assertEquals(
            "benchy_37m_PLA.gcode",
            suggestExportGcodeFileName(
                plateObjects = listOf(testPlateObject(label = "benchy.stl")),
                summary = testSummary(time = "37m"),
                filamentMaterial = "Bambu PLA Basic"
            )
        )
    }

    @Test
    fun exportFilenameUsesPlateNameForMultipleObjects() {
        assertEquals(
            "plate_3_objects_2h10m_PETG.gcode",
            suggestExportGcodeFileName(
                plateObjects = listOf(
                    testPlateObject(id = 1, label = "part_a.stl"),
                    testPlateObject(id = 2, label = "part_b.stl"),
                    testPlateObject(id = 3, label = "part_c.stl")
                ),
                summary = testSummary(time = "2h 10m"),
                filamentMaterial = "Generic PETG @System"
            )
        )
    }

    @Test
    fun exportFilenameFallbackKeepsSharedNameConsistent() {
        assertEquals(
            "custom_shared_name_ASA.gcode",
            suggestExportGcodeFileName(
                plateObjects = emptyList(),
                summary = null,
                filamentMaterial = "Polymaker ASA",
                fallbackName = "custom_shared_name.gcode"
            )
        )
    }

    @Test
    fun cleanupOrcaTempCacheDeletesExpiredFiles() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val tempDir = File(cacheDir, "orca-temp").apply { mkdirs() }
            val expired = File(tempDir, "old.tmp").apply {
                writeBytes(ByteArray(8))
                setLastModified(1_000L)
            }
            val fresh = File(tempDir, "fresh.tmp").apply {
                writeBytes(ByteArray(8))
                setLastModified(10_000L)
            }

            cleanupOrcaTempCache(
                cacheDir = cacheDir,
                retainedPaths = emptySet(),
                maxBytes = 1024L,
                maxAgeMs = 5_000L,
                nowMs = 10_000L
            )

            assertFalse(expired.exists())
            assertTrue(fresh.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupOrcaTempCacheTrimsOldestFilesToByteBudget() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val tempDir = File(cacheDir, "orca-temp").apply { mkdirs() }
            val oldest = File(tempDir, "oldest.tmp").apply {
                writeBytes(ByteArray(100))
                setLastModified(1_000L)
            }
            val newest = File(tempDir, "newest.tmp").apply {
                writeBytes(ByteArray(100))
                setLastModified(2_000L)
            }

            cleanupOrcaTempCache(
                cacheDir = cacheDir,
                retainedPaths = emptySet(),
                maxBytes = 100L,
                maxAgeMs = Long.MAX_VALUE,
                nowMs = 3_000L
            )

            assertFalse(oldest.exists())
            assertTrue(newest.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupOrcaTempCacheRetainsCurrentFilesWhenTrimming() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val tempDir = File(cacheDir, "orca-temp").apply { mkdirs() }
            val retained = File(tempDir, "retained.tmp").apply {
                writeBytes(ByteArray(100))
                setLastModified(1_000L)
            }
            val removable = File(tempDir, "removable.tmp").apply {
                writeBytes(ByteArray(100))
                setLastModified(2_000L)
            }

            cleanupOrcaTempCache(
                cacheDir = cacheDir,
                retainedPaths = setOf(retained.absolutePath),
                maxBytes = 50L,
                maxAgeMs = 1L,
                nowMs = 3_000L
            )

            assertTrue(retained.exists())
            assertFalse(removable.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    private fun testSummary(time: String): SliceResultSummary = SliceResultSummary(
        byteCount = 100,
        lineCount = 10,
        layerChangeCount = 2,
        observedTypes = emptyList(),
        wallShellTypes = emptyList(),
        estimatedPrintTimeText = time,
        filamentUsedGrams = 12.34
    )

    private fun testPlateObject(
        id: Long = 1,
        label: String
    ): PlateObject = PlateObject(
        id = id,
        label = label,
        filePath = "/tmp/$label",
        format = ImportedModelFormat.Stl,
        importTiming = null,
        transform = ViewerModelTransform(centerXmm = 100f, centerYmm = 100f)
    )
}
