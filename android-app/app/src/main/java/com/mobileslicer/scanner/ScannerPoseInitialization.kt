package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_POSE_INITIALIZATION_PATH = "poses/pose_initialization.json"

internal data class ScannerPoseInitializationLimits(
    val requiredAnchorPasses: Set<ScannerCapturePass> = setOf(
        ScannerCapturePass.LowRing,
        ScannerCapturePass.MidRing,
        ScannerCapturePass.HighRing
    ),
    val minAcceptedPairSupport: Int = 2,
    val minAnchorPairSupport: Int = 1
)

internal enum class ScannerPoseEstimateStatus(val manifestValue: String) {
    InitialUnmetric("initial_unmetric"),
    Blocked("blocked")
}

internal data class ScannerPoseNode(
    val frameId: String,
    val capturePass: ScannerCapturePass,
    val anchor: Boolean,
    val supportEdges: Int,
    val status: ScannerPoseEstimateStatus
)

internal data class ScannerPoseEdge(
    val frameA: String,
    val frameB: String,
    val matchCount: Int
)

internal data class ScannerPoseInitializationResult(
    val allowed: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val anchors: List<ScannerPoseNode>,
    val nodes: List<ScannerPoseNode>,
    val edges: List<ScannerPoseEdge>,
    val metric: Boolean
)

internal fun initializeScannerPoseGraph(
    workspaceDir: File,
    limits: ScannerPoseInitializationLimits = ScannerPoseInitializationLimits()
): ScannerPoseInitializationResult {
    val matchGraphFile = workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH)
    if (!matchGraphFile.isFile) {
        val result = blockedScannerPoseInitialization(listOf("match_graph_missing"))
        writeScannerPoseInitialization(workspaceDir, result, limits)
        return result
    }

    val matchGraph = JSONObject(matchGraphFile.readText())
    val graphAllowed = matchGraph.optBoolean("allowed", false)
    val graphErrors = matchGraph.optJSONArray("errors").toScannerStringList()
    if (!graphAllowed) {
        val result = blockedScannerPoseInitialization(listOf("match_graph_blocked") + graphErrors)
        writeScannerPoseInitialization(workspaceDir, result, limits)
        return result
    }

    val reconstructionJob = workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()) }
    if (reconstructionJob == null) {
        val result = blockedScannerPoseInitialization(listOf("reconstruction_job_missing"))
        writeScannerPoseInitialization(workspaceDir, result, limits)
        return result
    }

    val framePasses = parsePoseFramePasses(reconstructionJob)
    val edges = parsePoseEdges(matchGraph)
    val supportByFrame = framePasses.keys.associateWith { frameId ->
        edges.count { it.frameA == frameId || it.frameB == frameId }
    }
    val anchors = chooseScannerPoseAnchors(framePasses, supportByFrame, limits)
    val errors = buildList {
        limits.requiredAnchorPasses
            .filterNot { pass -> anchors.any { it.capturePass == pass } }
            .forEach { add("not_enough_anchor_passes:${it.manifestValue}") }
        if (edges.isEmpty()) add("insufficient_pair_support")
        if (anchors.count { it.supportEdges >= limits.minAnchorPairSupport } < limits.requiredAnchorPasses.size) {
            add("insufficient_anchor_pair_support")
        }
        framePasses.keys
            .filter { supportByFrame.getValue(it) < limits.minAcceptedPairSupport }
            .forEach { add("insufficient_pair_support:$it") }
        add("pose_initialization_not_metric")
    }
    val nodes = framePasses.map { (frameId, pass) ->
        val anchor = anchors.any { it.frameId == frameId }
        ScannerPoseNode(
            frameId = frameId,
            capturePass = pass,
            anchor = anchor,
            supportEdges = supportByFrame.getValue(frameId),
            status = ScannerPoseEstimateStatus.InitialUnmetric
        )
    }.sortedWith(compareByDescending<ScannerPoseNode> { it.anchor }.thenBy { it.capturePass.ordinal }.thenBy { it.frameId })
    val result = ScannerPoseInitializationResult(
        allowed = errors.none { it != "pose_initialization_not_metric" },
        errors = errors.distinct(),
        warnings = listOf("pose_initialization_is_not_metric", "bundle_adjustment_required"),
        anchors = anchors,
        nodes = nodes,
        edges = edges,
        metric = false
    )
    writeScannerPoseInitialization(workspaceDir, result, limits)
    return result
}

private fun blockedScannerPoseInitialization(errors: List<String>): ScannerPoseInitializationResult =
    ScannerPoseInitializationResult(
        allowed = false,
        errors = errors.distinct(),
        warnings = emptyList(),
        anchors = emptyList(),
        nodes = emptyList(),
        edges = emptyList(),
        metric = false
    )

private fun chooseScannerPoseAnchors(
    framePasses: Map<String, ScannerCapturePass>,
    supportByFrame: Map<String, Int>,
    limits: ScannerPoseInitializationLimits
): List<ScannerPoseNode> =
    limits.requiredAnchorPasses.mapNotNull { pass ->
        framePasses
            .filterValues { it == pass }
            .keys
            .sortedWith(compareByDescending<String> { supportByFrame[it] ?: 0 }.thenBy { it })
            .firstOrNull()
            ?.let { frameId ->
                ScannerPoseNode(
                    frameId = frameId,
                    capturePass = pass,
                    anchor = true,
                    supportEdges = supportByFrame[frameId] ?: 0,
                    status = ScannerPoseEstimateStatus.InitialUnmetric
                )
            }
    }

private fun parsePoseFramePasses(reconstructionJob: JSONObject): Map<String, ScannerCapturePass> {
    val frames = reconstructionJob.getJSONArray("frames")
    return List(frames.length()) { index ->
        val frame = frames.getJSONObject(index)
        frame.getString("frame_id") to scannerPoseCapturePass(frame.getString("capture_pass"))
    }.toMap()
}

private fun parsePoseEdges(matchGraph: JSONObject): List<ScannerPoseEdge> {
    val pairs = matchGraph.optJSONArray("pairs") ?: JSONArray()
    return List(pairs.length()) { index -> pairs.getJSONObject(index) }
        .filter { it.optBoolean("accepted", false) }
        .map {
            ScannerPoseEdge(
                frameA = it.getString("frame_a"),
                frameB = it.getString("frame_b"),
                matchCount = it.getInt("match_count")
            )
        }
}

private fun scannerPoseCapturePass(value: String): ScannerCapturePass =
    ScannerCapturePass.entries.single { it.manifestValue == value }

private fun writeScannerPoseInitialization(
    workspaceDir: File,
    result: ScannerPoseInitializationResult,
    limits: ScannerPoseInitializationLimits
) {
    workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH).also {
        it.parentFile?.mkdirs()
        it.writeText(scannerPoseInitializationJson(result, limits).toString(2))
    }
}

internal fun scannerPoseInitializationJson(
    result: ScannerPoseInitializationResult,
    limits: ScannerPoseInitializationLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("allowed", result.allowed)
        .put("metric", result.metric)
        .put("errors", JSONArray(result.errors))
        .put("warnings", JSONArray(result.warnings))
        .put(
            "required_anchor_passes",
            JSONArray().apply {
                limits.requiredAnchorPasses
                    .sortedBy { it.ordinal }
                    .forEach { put(it.manifestValue) }
            }
        )
        .put("min_accepted_pair_support", limits.minAcceptedPairSupport)
        .put("min_anchor_pair_support", limits.minAnchorPairSupport)
        .put("anchors", JSONArray().apply { result.anchors.forEach { put(it.toJson()) } })
        .put("nodes", JSONArray().apply { result.nodes.forEach { put(it.toJson()) } })
        .put(
            "edges",
            JSONArray().apply {
                result.edges.forEach {
                    put(
                        JSONObject()
                            .put("frame_a", it.frameA)
                            .put("frame_b", it.frameB)
                            .put("match_count", it.matchCount)
                    )
                }
            }
        )

private fun ScannerPoseNode.toJson(): JSONObject =
    JSONObject()
        .put("frame_id", frameId)
        .put("capture_pass", capturePass.manifestValue)
        .put("anchor", anchor)
        .put("support_edges", supportEdges)
        .put("status", status.manifestValue)

private fun JSONArray?.toScannerStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
