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
    fun modelDisplayNameStripsInternalStagingPrefixOnly() {
        assertEquals(
            "flower_v1_pot.stl",
            displayNameForModelFileName("selected-model-123456-flower_v1_pot.stl")
        )
        assertEquals(
            "thingiverse_part.stl",
            displayNameForModelFileName("thingiverse_part.stl")
        )
    }

    @Test
    fun modelDisplayStemKeepsThingiverseImportNameForExport() {
        assertEquals(
            "flower_v1_pot",
            displayStemForModelFile(File("/tmp/selected-model-123456-flower_v1_pot.stl"))
        )
    }

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
    fun cleanupGeneratedGcodeCacheDeletesAllGeneratedSliceArtifacts() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val latestSlice = File(cacheDir, "latest-slice-benchy.gcode").apply {
                writeText("stale slice")
            }
            val latestSend = File(cacheDir, "latest-send-benchy.gcode.3mf").apply {
                writeText("stale send package")
            }
            val wrapperOutput = File(cacheDir, "orca_wrapper_123.gcode").apply {
                writeText("stale wrapper")
            }
            val userFile = File(cacheDir, "user-export.gcode").apply {
                writeText("keep")
            }

            cleanupGeneratedGcodeCache(cacheDir = cacheDir, retainedPaths = emptySet())

            assertFalse(latestSlice.exists())
            assertFalse(latestSend.exists())
            assertFalse(wrapperOutput.exists())
            assertTrue(userFile.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupGeneratedGcodeCacheRetainsCurrentGeneratedOutput() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val retained = File(cacheDir, "latest-slice-current.gcode").apply {
                writeText("current")
            }
            val stale = File(cacheDir, "latest-slice-stale.gcode").apply {
                writeText("stale")
            }

            cleanupGeneratedGcodeCache(
                cacheDir = cacheDir,
                retainedPaths = setOf(retained.absolutePath)
            )

            assertTrue(retained.exists())
            assertFalse(stale.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupStagedModelCacheDeletesOnlyInternalStagedModels() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val staged = File(cacheDir, "selected-model-123-benchy.stl").apply {
                writeText("stale staged model")
            }
            val normal = File(cacheDir, "benchy.stl").apply {
                writeText("user model")
            }

            cleanupStagedModelCache(cacheDir = cacheDir, retainedPaths = emptySet())

            assertFalse(staged.exists())
            assertTrue(normal.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupThingiverseImportCacheDeletesTemporaryDownloads() {
        val cacheDir = Files.createTempDirectory("mobileslicer-cache-").toFile()
        try {
            val importDir = File(cacheDir, "thingiverse-imports").apply { mkdirs() }
            val downloaded = File(importDir, "3dbenchy-1.stl").apply {
                writeText("stale thingiverse cache")
            }
            val unrelated = File(cacheDir, "selected-model-123-benchy.stl").apply {
                writeText("staged model")
            }

            cleanupThingiverseImportCache(cacheDir)

            assertFalse(downloaded.exists())
            assertFalse(importDir.exists())
            assertTrue(unrelated.exists())
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
