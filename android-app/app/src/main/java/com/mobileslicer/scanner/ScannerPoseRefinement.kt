package com.mobileslicer.scanner

import java.io.File
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_POSE_REFINEMENT_PATH = "poses/pose_refinement.json"

internal data class ScannerPoseRefinementLimits(
    val minAverageSupportEdges: Float = 2.0f,
    val minAverageMatchCount: Float = 16.0f,
    val minRefinementConfidence: Float = 0.70f,
    val requireMetricInitialization: Boolean = true
)

internal data class ScannerPoseRefinementNode(
    val frameId: String,
    val capturePass: ScannerCapturePass,
    val supportEdges: Int,
    val confidence: Float
)

internal data class ScannerPoseRefinementResult(
    val allowed: Boolean,
    val denseReconstructionAllowed: Boolean,
    val metric: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val refinementConfidence: Float,
    val averageSupportEdges: Float,
    val averageMatchCount: Float,
    val nodes: List<ScannerPoseRefinementNode>
)

internal fun refineScannerPoseGraph(
    workspaceDir: File,
    limits: ScannerPoseRefinementLimits = ScannerPoseRefinementLimits()
): ScannerPoseRefinementResult {
    val initializationFile = workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH)
    if (!initializationFile.isFile) {
        val result = blockedScannerPoseRefinement(listOf("pose_initialization_missing"))
        writeScannerPoseRefinement(workspaceDir, result, limits)
        return result
    }

    val initialization = JSONObject(initializationFile.readText())
    val initializationAllowed = initialization.optBoolean("allowed", false)
    val initializationMetric = initialization.optBoolean("metric", false)
    val initializationErrors = initialization.optJSONArray("errors").scannerPoseRefinementStringList()
    if (!initializationAllowed) {
        val result = blockedScannerPoseRefinement(listOf("pose_initialization_blocked") + initializationErrors)
        writeScannerPoseRefinement(workspaceDir, result, limits)
        return result
    }

    val nodes = parsePoseRefinementNodes(initialization)
    val edges = parsePoseRefinementEdges(initialization)
    val supportByFrame = nodes.associate { node ->
        node.frameId to edges.count { it.frameA == node.frameId || it.frameB == node.frameId }
    }
    val averageSupportEdges = if (nodes.isEmpty()) {
        0f
    } else {
        supportByFrame.values.average().toFloat()
    }
    val averageMatchCount = if (edges.isEmpty()) {
        0f
    } else {
        edges.map { it.matchCount }.average().toFloat()
    }
    val confidence = min(
        a = 1f,
        b = (
            (averageSupportEdges / limits.minAverageSupportEdges).coerceIn(0f, 1f) * 0.45f +
                (averageMatchCount / limits.minAverageMatchCount).coerceIn(0f, 1f) * 0.45f +
                (if (initializationMetric) 1f else 0f) * 0.10f
            )
    )
    val refinedNodes = nodes.map { node ->
        val supportEdges = supportByFrame[node.frameId] ?: 0
        ScannerPoseRefinementNode(
            frameId = node.frameId,
            capturePass = node.capturePass,
            supportEdges = supportEdges,
            confidence = (
                (supportEdges.toFloat() / limits.minAverageSupportEdges).coerceIn(0f, 1f) * 0.55f +
                    (confidence * 0.45f)
                ).coerceIn(0f, 1f)
        )
    }
    val errors = buildList {
        if (nodes.isEmpty()) add("pose_nodes_missing")
        if (edges.isEmpty()) add("pose_edges_missing")
        if (averageSupportEdges < limits.minAverageSupportEdges) add("pose_support_low")
        if (averageMatchCount < limits.minAverageMatchCount) add("pose_match_count_low")
        if (confidence < limits.minRefinementConfidence) add("pose_refinement_confidence_low")
        if (limits.requireMetricInitialization && !initializationMetric) add("pose_refinement_not_metric")
        add("dense_reconstruction_blocked_until_metric_pose_solve")
    }.distinct()
    val result = ScannerPoseRefinementResult(
        allowed = errors.none {
            it != "pose_refinement_not_metric" && it != "dense_reconstruction_blocked_until_metric_pose_solve"
        },
        denseReconstructionAllowed = false,
        metric = false,
        errors = errors,
        warnings = listOf("relative_pose_refinement_scaffold_only", "bundle_adjustment_required"),
        refinementConfidence = confidence,
        averageSupportEdges = averageSupportEdges,
        averageMatchCount = averageMatchCount,
        nodes = refinedNodes
    )
    writeScannerPoseRefinement(workspaceDir, result, limits)
    return result
}

private fun blockedScannerPoseRefinement(errors: List<String>): ScannerPoseRefinementResult =
    ScannerPoseRefinementResult(
        allowed = false,
        denseReconstructionAllowed = false,
        metric = false,
        errors = errors.distinct(),
        warnings = emptyList(),
        refinementConfidence = 0f,
        averageSupportEdges = 0f,
        averageMatchCount = 0f,
        nodes = emptyList()
    )

private data class PoseRefinementInputNode(
    val frameId: String,
    val capturePass: ScannerCapturePass
)

private fun parsePoseRefinementNodes(initialization: JSONObject): List<PoseRefinementInputNode> {
    val nodes = initialization.optJSONArray("nodes") ?: JSONArray()
    return List(nodes.length()) { index -> nodes.getJSONObject(index) }
        .map {
            PoseRefinementInputNode(
                frameId = it.getString("frame_id"),
                capturePass = scannerPoseRefinementCapturePass(it.getString("capture_pass"))
            )
        }
}

private fun parsePoseRefinementEdges(initialization: JSONObject): List<ScannerPoseEdge> {
    val edges = initialization.optJSONArray("edges") ?: JSONArray()
    return List(edges.length()) { index -> edges.getJSONObject(index) }
        .map {
            ScannerPoseEdge(
                frameA = it.getString("frame_a"),
                frameB = it.getString("frame_b"),
                matchCount = it.getInt("match_count")
            )
        }
}

private fun scannerPoseRefinementCapturePass(value: String): ScannerCapturePass =
    ScannerCapturePass.entries.single { it.manifestValue == value }

private fun writeScannerPoseRefinement(
    workspaceDir: File,
    result: ScannerPoseRefinementResult,
    limits: ScannerPoseRefinementLimits
) {
    workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerPoseRefinementJson(result, limits).toString(2))
    }
}

internal fun scannerPoseRefinementJson(
    result: ScannerPoseRefinementResult,
    limits: ScannerPoseRefinementLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("dense_reconstruction_allowed", result.denseReconstructionAllowed)
        .put("metric", result.metric)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put("refinement_confidence", result.refinementConfidence.toDouble())
        .put("average_support_edges", result.averageSupportEdges.toDouble())
        .put("average_match_count", result.averageMatchCount.toDouble())
        .put(
            "limits",
            JSONObject()
                .put("min_average_support_edges", limits.minAverageSupportEdges.toDouble())
                .put("min_average_match_count", limits.minAverageMatchCount.toDouble())
                .put("min_refinement_confidence", limits.minRefinementConfidence.toDouble())
                .put("require_metric_initialization", limits.requireMetricInitialization)
        )
        .put(
            "nodes",
            JSONArray().apply {
                result.nodes.forEach { node ->
                    put(
                        JSONObject()
                            .put("frame_id", node.frameId)
                            .put("capture_pass", node.capturePass.manifestValue)
                            .put("support_edges", node.supportEdges)
                            .put("confidence", node.confidence.toDouble())
                    )
                }
            }
        )

private fun JSONArray?.scannerPoseRefinementStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
