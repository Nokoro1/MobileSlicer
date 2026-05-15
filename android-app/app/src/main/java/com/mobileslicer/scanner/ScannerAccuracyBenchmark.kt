package com.mobileslicer.scanner

import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_ACCURACY_BENCHMARK_PATH = "benchmark/accuracy_report.json"

internal data class ScannerBenchmarkFixture(
    val fixtureId: String,
    val expectedDimensionsMm: List<Double>,
    val maxDimensionErrorMm: Double = 1.5,
    val maxDimensionErrorPercent: Double = 1.5,
    val minScaleConfidence: Float = 0.85f,
    val maxMarkerReprojectionErrorPx: Float = 2.0f
) {
    init {
        require(expectedDimensionsMm.size == 3) { "Benchmark fixture requires exactly three dimensions" }
        require(expectedDimensionsMm.all { it > 0.0 }) { "Benchmark dimensions must be positive" }
    }
}

internal data class ScannerBenchmarkFixtureInputResult(
    val fixture: ScannerBenchmarkFixture?,
    val errors: List<String>
) {
    val valid: Boolean = fixture != null && errors.isEmpty()
}

internal data class ScannerDimensionAccuracy(
    val expectedMm: Double,
    val measuredMm: Double,
    val absoluteErrorMm: Double,
    val percentError: Double,
    val toleranceMm: Double,
    val passed: Boolean
)

internal data class ScannerAccuracyBenchmarkResult(
    val allowed: Boolean,
    val status: String,
    val fixture: ScannerBenchmarkFixture,
    val errors: List<String>,
    val warnings: List<String>,
    val scaleConfidence: Float?,
    val markerReprojectionErrorPx: Float?,
    val measuredDimensionsMm: List<Double>,
    val dimensionAccuracy: List<ScannerDimensionAccuracy>,
    val maxAbsoluteErrorMm: Double?,
    val maxPercentError: Double?
)

internal fun parseScannerBenchmarkFixtureInput(
    fixtureIdInput: String,
    dimensionXInput: String,
    dimensionYInput: String,
    dimensionZInput: String
): ScannerBenchmarkFixtureInputResult {
    val fixtureId = fixtureIdInput.trim()
        .ifBlank { "manual_known_fixture" }
        .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        .take(64)
    val dimensions = listOf(
        parseBenchmarkDimension("x", dimensionXInput),
        parseBenchmarkDimension("y", dimensionYInput),
        parseBenchmarkDimension("z", dimensionZInput)
    )
    val errors = dimensions.mapNotNull { it.error }.distinct()
    val values = dimensions.mapNotNull { it.value }
    return if (errors.isEmpty() && values.size == 3) {
        ScannerBenchmarkFixtureInputResult(
            fixture = ScannerBenchmarkFixture(
                fixtureId = fixtureId,
                expectedDimensionsMm = values
            ),
            errors = emptyList()
        )
    } else {
        ScannerBenchmarkFixtureInputResult(fixture = null, errors = errors)
    }
}

internal fun runScannerAccuracyBenchmark(
    workspaceDir: File,
    fixture: ScannerBenchmarkFixture
): ScannerAccuracyBenchmarkResult {
    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    val dense = workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }

    val calibration = reconstructionJob?.optJSONObject("calibration")
    val scaleConfidence = calibration?.optDouble("scale_confidence", Double.NaN)
        ?.takeIf { it.isFinite() }
        ?.toFloat()
    val markerReprojection = calibration?.takeIf { !it.isNull("marker_reprojection_error_px") }
        ?.optDouble("marker_reprojection_error_px", Double.NaN)
        ?.takeIf { it.isFinite() }
        ?.toFloat()
    val measuredDimensions = dense?.optJSONObject("bounding_box_mm")
        ?.optJSONObject("extent")
        ?.let { extent ->
            listOf(
                extent.optDouble("x", Double.NaN),
                extent.optDouble("y", Double.NaN),
                extent.optDouble("z", Double.NaN)
            ).takeIf { values -> values.all { it.isFinite() && it > 0.0 } }
        }
        .orEmpty()
    val dimensionAccuracy = if (measuredDimensions.size == 3) {
        scannerDimensionAccuracy(
            expectedDimensions = fixture.expectedDimensionsMm,
            measuredDimensions = measuredDimensions,
            fixture = fixture
        )
    } else {
        emptyList()
    }
    val errors = buildList {
        if (reconstructionJob == null) add("reconstruction_job_missing")
        if (dense == null) add("dense_reconstruction_missing")
        if (dense != null && !dense.optBoolean("dense_reconstruction_admitted", false)) {
            add("dense_reconstruction_not_admitted")
        }
        if (scaleConfidence == null) add("scale_confidence_missing")
        if (markerReprojection == null) add("marker_reprojection_error_missing")
        if (scaleConfidence != null && scaleConfidence < fixture.minScaleConfidence) {
            add("scale_confidence_low")
        }
        if (markerReprojection != null && markerReprojection > fixture.maxMarkerReprojectionErrorPx) {
            add("marker_reprojection_error_high")
        }
        if (measuredDimensions.size != 3) add("measured_dimensions_missing")
        dimensionAccuracy
            .forEachIndexed { index, accuracy ->
                if (!accuracy.passed) add("dimension_${index + 1}_outside_tolerance")
            }
    }.distinct()
    val result = ScannerAccuracyBenchmarkResult(
        allowed = errors.isEmpty(),
        status = if (errors.isEmpty()) "accuracy_benchmark_passed" else "accuracy_benchmark_failed",
        fixture = fixture,
        errors = errors,
        warnings = buildList {
            add("benchmark_compares_sorted_extents_not_object_pose")
            add("benchmark_does_not_replace_mesh_topology_or_printability_validation")
        },
        scaleConfidence = scaleConfidence,
        markerReprojectionErrorPx = markerReprojection,
        measuredDimensionsMm = measuredDimensions.sorted(),
        dimensionAccuracy = dimensionAccuracy,
        maxAbsoluteErrorMm = dimensionAccuracy.maxOfOrNull { it.absoluteErrorMm },
        maxPercentError = dimensionAccuracy.maxOfOrNull { it.percentError }
    )
    writeScannerAccuracyBenchmark(workspaceDir, result)
    return result
}

private data class ParsedBenchmarkDimension(
    val value: Double?,
    val error: String?
)

private fun parseBenchmarkDimension(
    axis: String,
    rawValue: String
): ParsedBenchmarkDimension {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank()) {
        return ParsedBenchmarkDimension(value = null, error = "dimension_${axis}_missing")
    }
    val parsed = trimmed.toDoubleOrNull()
    return if (parsed != null && parsed.isFinite() && parsed > 0.0) {
        ParsedBenchmarkDimension(value = parsed, error = null)
    } else {
        ParsedBenchmarkDimension(value = null, error = "dimension_${axis}_invalid")
    }
}

private fun scannerDimensionAccuracy(
    expectedDimensions: List<Double>,
    measuredDimensions: List<Double>,
    fixture: ScannerBenchmarkFixture
): List<ScannerDimensionAccuracy> {
    val expected = expectedDimensions.sorted()
    val measured = measuredDimensions.sorted()
    return expected.indices.map { index ->
        val absoluteError = abs(measured[index] - expected[index])
        val percentError = absoluteError / expected[index] * 100.0
        val tolerance = maxOf(
            fixture.maxDimensionErrorMm,
            expected[index] * fixture.maxDimensionErrorPercent / 100.0
        )
        ScannerDimensionAccuracy(
            expectedMm = expected[index],
            measuredMm = measured[index],
            absoluteErrorMm = absoluteError,
            percentError = percentError,
            toleranceMm = tolerance,
            passed = absoluteError <= tolerance
        )
    }
}

private fun writeScannerAccuracyBenchmark(
    workspaceDir: File,
    result: ScannerAccuracyBenchmarkResult
) {
    workspaceDir.resolve(SCANNER_ACCURACY_BENCHMARK_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerAccuracyBenchmarkJson(result).toString(2))
    }
}

internal fun scannerAccuracyBenchmarkJson(result: ScannerAccuracyBenchmarkResult): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("status", result.status)
        .put(
            "fixture",
            JSONObject()
                .put("fixture_id", result.fixture.fixtureId)
                .put("expected_dimensions_mm", JSONArray(result.fixture.expectedDimensionsMm.sorted()))
                .put("max_dimension_error_mm", result.fixture.maxDimensionErrorMm)
                .put("max_dimension_error_percent", result.fixture.maxDimensionErrorPercent)
                .put("min_scale_confidence", result.fixture.minScaleConfidence.toDouble())
                .put("max_marker_reprojection_error_px", result.fixture.maxMarkerReprojectionErrorPx.toDouble())
        )
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("scale_confidence", result.scaleConfidence ?: JSONObject.NULL)
        .put("marker_reprojection_error_px", result.markerReprojectionErrorPx ?: JSONObject.NULL)
        .put("measured_dimensions_mm", JSONArray(result.measuredDimensionsMm))
        .put("max_absolute_error_mm", result.maxAbsoluteErrorMm ?: JSONObject.NULL)
        .put("max_percent_error", result.maxPercentError ?: JSONObject.NULL)
        .put(
            "dimension_accuracy",
            JSONArray().apply {
                result.dimensionAccuracy.forEach { accuracy ->
                    put(
                        JSONObject()
                            .put("expected_mm", accuracy.expectedMm)
                            .put("measured_mm", accuracy.measuredMm)
                            .put("absolute_error_mm", accuracy.absoluteErrorMm)
                            .put("percent_error", accuracy.percentError)
                            .put("tolerance_mm", accuracy.toleranceMm)
                            .put("passed", accuracy.passed)
                    )
                }
            }
        )
