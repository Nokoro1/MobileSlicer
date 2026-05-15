package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerAccuracyBenchmarkTest {
    @Test
    fun fixtureInputParserAcceptsManualKnownFixtureDimensions() {
        val result = parseScannerBenchmarkFixtureInput(
            fixtureIdInput = "known block 20x40x60",
            dimensionXInput = "20",
            dimensionYInput = "40.0",
            dimensionZInput = "60"
        )

        assertTrue(result.valid)
        assertEquals("known_block_20x40x60", result.fixture!!.fixtureId)
        assertEquals(listOf(20.0, 40.0, 60.0), result.fixture.expectedDimensionsMm)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun fixtureInputParserRejectsMissingOrInvalidDimensions() {
        val result = parseScannerBenchmarkFixtureInput(
            fixtureIdInput = "",
            dimensionXInput = "",
            dimensionYInput = "-40",
            dimensionZInput = "not-a-number"
        )

        assertFalse(result.valid)
        assertEquals(null, result.fixture)
        assertTrue(result.errors.contains("dimension_x_missing"))
        assertTrue(result.errors.contains("dimension_y_invalid"))
        assertTrue(result.errors.contains("dimension_z_invalid"))
    }

    @Test
    fun benchmarkPassesKnownBlockWithinTolerance() {
        val workspaceDir = tempDir("scanner-accuracy-pass")
        writeReconstructionJob(workspaceDir, scaleConfidence = 0.91f, markerReprojectionPx = 1.2f)
        writeDenseReconstruction(
            workspaceDir = workspaceDir,
            admitted = true,
            extentX = 20.8,
            extentY = 40.3,
            extentZ = 59.2
        )

        val result = runScannerAccuracyBenchmark(
            workspaceDir = workspaceDir,
            fixture = ScannerBenchmarkFixture(
                fixtureId = "known_block_20x40x60",
                expectedDimensionsMm = listOf(20.0, 40.0, 60.0)
            )
        )

        assertTrue(result.allowed)
        assertEquals("accuracy_benchmark_passed", result.status)
        assertEquals(3, result.dimensionAccuracy.size)
        assertTrue(result.dimensionAccuracy.all { it.passed })
        assertTrue(result.maxAbsoluteErrorMm!! <= 1.5)

        val json = JSONObject(workspaceDir.resolve(SCANNER_ACCURACY_BENCHMARK_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertEquals("known_block_20x40x60", json.getJSONObject("fixture").getString("fixture_id"))
        assertEquals(3, json.getJSONArray("dimension_accuracy").length())
    }

    @Test
    fun benchmarkFailsDimensionOutsideTolerance() {
        val workspaceDir = tempDir("scanner-accuracy-fail-dimension")
        writeReconstructionJob(workspaceDir, scaleConfidence = 0.91f, markerReprojectionPx = 1.2f)
        writeDenseReconstruction(
            workspaceDir = workspaceDir,
            admitted = true,
            extentX = 20.0,
            extentY = 40.0,
            extentZ = 66.0
        )

        val result = runScannerAccuracyBenchmark(
            workspaceDir = workspaceDir,
            fixture = ScannerBenchmarkFixture(
                fixtureId = "known_block_20x40x60",
                expectedDimensionsMm = listOf(20.0, 40.0, 60.0)
            )
        )

        assertFalse(result.allowed)
        assertEquals("accuracy_benchmark_failed", result.status)
        assertTrue(result.errors.contains("dimension_3_outside_tolerance"))
    }

    @Test
    fun benchmarkFailsWithoutDenseAdmissionOrScaleEvidence() {
        val workspaceDir = tempDir("scanner-accuracy-fail-evidence")
        writeReconstructionJob(workspaceDir, scaleConfidence = 0.60f, markerReprojectionPx = null)
        writeDenseReconstruction(
            workspaceDir = workspaceDir,
            admitted = false,
            extentX = 20.0,
            extentY = 40.0,
            extentZ = 60.0
        )

        val result = runScannerAccuracyBenchmark(
            workspaceDir = workspaceDir,
            fixture = ScannerBenchmarkFixture(
                fixtureId = "known_block_20x40x60",
                expectedDimensionsMm = listOf(20.0, 40.0, 60.0)
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("dense_reconstruction_not_admitted"))
        assertTrue(result.errors.contains("scale_confidence_low"))
        assertTrue(result.errors.contains("marker_reprojection_error_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_ACCURACY_BENCHMARK_PATH).isFile)
    }

    private fun writeReconstructionJob(
        workspaceDir: File,
        scaleConfidence: Float,
        markerReprojectionPx: Float?
    ) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put(
                    "calibration",
                    JSONObject()
                        .put("scale_confidence", scaleConfidence.toDouble())
                        .put("marker_reprojection_error_px", markerReprojectionPx ?: JSONObject.NULL)
                )
                .toString(2)
        )
    }

    private fun writeDenseReconstruction(
        workspaceDir: File,
        admitted: Boolean,
        extentX: Double,
        extentY: Double,
        extentZ: Double
    ) {
        workspaceDir.resolve("dense").mkdirs()
        workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", admitted)
                .put("metric", admitted)
                .put("dense_reconstruction_admitted", admitted)
                .put(
                    "bounding_box_mm",
                    JSONObject()
                        .put("extent", JSONObject().put("x", extentX).put("y", extentY).put("z", extentZ))
                )
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}
