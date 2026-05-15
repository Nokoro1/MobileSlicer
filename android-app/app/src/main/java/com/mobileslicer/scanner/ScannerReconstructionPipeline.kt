package com.mobileslicer.scanner

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val SCANNER_RECONSTRUCTION_SUMMARY_PATH = "reconstruction_summary.json"

internal data class ScannerReconstructionPipelineLimits(
    val workspace: ScannerReconstructionWorkspaceLimits = ScannerReconstructionWorkspaceLimits(
        preflightLimits = ScannerLocalReconstructionLimits(
            requireVerifiedScale = false,
            minimumScaleConfidence = 0f
        )
    ),
    val depthFusion: ScannerDepthFusionLimits = ScannerDepthFusionLimits(),
    val features: ScannerFeatureExtractionLimits = ScannerFeatureExtractionLimits(),
    val tracks: ScannerFeatureTrackLimits = ScannerFeatureTrackLimits(),
    val poseInitialization: ScannerPoseInitializationLimits = ScannerPoseInitializationLimits(),
    val poseRefinement: ScannerPoseRefinementLimits = ScannerPoseRefinementLimits(),
    val sparseReconstruction: ScannerSparseReconstructionLimits = ScannerSparseReconstructionLimits(),
    val metricPoseGraph: ScannerMetricPoseGraphLimits = ScannerMetricPoseGraphLimits(),
    val bundleAdjustment: ScannerBundleAdjustmentLimits = ScannerBundleAdjustmentLimits(),
    val metricPoseSolve: ScannerMetricPoseSolveLimits = ScannerMetricPoseSolveLimits(),
    val poseOptimizer: ScannerPoseOptimizerLimits = ScannerPoseOptimizerLimits(),
    val sparseTriangulation: ScannerSparseTriangulationLimits = ScannerSparseTriangulationLimits(),
    val experimentalSparsePreview: ScannerExperimentalSparsePreviewLimits = ScannerExperimentalSparsePreviewLimits(),
    val denseReconstruction: ScannerDenseReconstructionLimits = ScannerDenseReconstructionLimits(),
    val densePointCloud: ScannerDensePointCloudLimits = ScannerDensePointCloudLimits(),
    val surfaceReconstruction: ScannerSurfaceReconstructionLimits = ScannerSurfaceReconstructionLimits(),
    val meshTopology: ScannerMeshTopologyLimits = ScannerMeshTopologyLimits(),
    val metricMeshCandidate: ScannerMetricMeshCandidateLimits = ScannerMetricMeshCandidateLimits(),
    val meshValidation: ScannerMeshValidationLimits = ScannerMeshValidationLimits(),
    val repairReport: ScannerRepairReportLimits = ScannerRepairReportLimits(),
    val printabilityReport: ScannerPrintabilityReportLimits = ScannerPrintabilityReportLimits(),
    val slicerLoadValidation: ScannerSlicerLoadValidationLimits = ScannerSlicerLoadValidationLimits(),
    val metricExport: ScannerMetricExportLimits = ScannerMetricExportLimits(),
    val workspaceHandoff: ScannerWorkspaceHandoffLimits = ScannerWorkspaceHandoffLimits()
)

internal data class ScannerReconstructionPipelineStage(
    val name: String,
    val artifactPath: String?,
    val allowed: Boolean,
    val metric: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

internal data class ScannerReconstructionPipelineResult(
    val completed: Boolean,
    val metric: Boolean,
    val denseReconstructionReady: Boolean,
    val workspaceDir: File,
    val stages: List<ScannerReconstructionPipelineStage>,
    val blockingErrors: List<String>,
    val summaryPath: String
)

internal fun runScannerLocalReconstructionPipeline(
    packageDir: File,
    workspaceDir: File,
    limits: ScannerReconstructionPipelineLimits = ScannerReconstructionPipelineLimits()
): ScannerReconstructionPipelineResult {
    val stages = mutableListOf<ScannerReconstructionPipelineStage>()

    val workspace = buildLocalReconstructionWorkspace(
        packageDir = packageDir,
        workspaceDir = workspaceDir,
        limits = limits.workspace
    )
    stages += ScannerReconstructionPipelineStage(
        name = "workspace",
        artifactPath = workspace.manifestPath,
        allowed = workspace.created,
        metric = false,
        errors = workspace.errors,
        warnings = workspace.warnings
    )

    if (workspace.created) {
        val depthFusion = buildScannerDepthFusion(workspaceDir, limits.depthFusion)
        stages += ScannerReconstructionPipelineStage(
            name = "depth_fusion",
            artifactPath = SCANNER_DEPTH_FUSION_PATH,
            allowed = depthFusion.allowed,
            metric = depthFusion.metric,
            errors = depthFusion.errors,
            warnings = depthFusion.warnings
        )

        val features = buildScannerFeatureMatchGraph(workspaceDir, limits.features)
        stages += ScannerReconstructionPipelineStage(
            name = "feature_match_graph",
            artifactPath = SCANNER_MATCH_GRAPH_PATH,
            allowed = features.allowed,
            metric = false,
            errors = features.errors,
            warnings = features.warnings
        )

        val tracks = buildScannerFeatureTracks(workspaceDir, limits.tracks)
        stages += ScannerReconstructionPipelineStage(
            name = "feature_tracks",
            artifactPath = SCANNER_FEATURE_TRACKS_PATH,
            allowed = tracks.allowed,
            metric = false,
            errors = tracks.errors,
            warnings = tracks.warnings
        )

        val poseInitialization = initializeScannerPoseGraph(workspaceDir, limits.poseInitialization)
        stages += ScannerReconstructionPipelineStage(
            name = "pose_initialization",
            artifactPath = SCANNER_POSE_INITIALIZATION_PATH,
            allowed = poseInitialization.allowed,
            metric = poseInitialization.metric,
            errors = poseInitialization.errors,
            warnings = poseInitialization.warnings
        )

        val poseRefinement = refineScannerPoseGraph(workspaceDir, limits.poseRefinement)
        stages += ScannerReconstructionPipelineStage(
            name = "pose_refinement",
            artifactPath = SCANNER_POSE_REFINEMENT_PATH,
            allowed = poseRefinement.allowed,
            metric = poseRefinement.metric,
            errors = poseRefinement.errors,
            warnings = poseRefinement.warnings
        )

        val sparseReconstruction = prepareScannerSparseReconstruction(workspaceDir, limits.sparseReconstruction)
        stages += ScannerReconstructionPipelineStage(
            name = "sparse_reconstruction_inputs",
            artifactPath = SCANNER_SPARSE_RECONSTRUCTION_PATH,
            allowed = sparseReconstruction.allowed,
            metric = sparseReconstruction.metric,
            errors = sparseReconstruction.errors,
            warnings = sparseReconstruction.warnings
        )

        val metricPoseGraph = buildScannerMetricPoseGraph(workspaceDir, limits.metricPoseGraph)
        stages += ScannerReconstructionPipelineStage(
            name = "metric_pose_graph",
            artifactPath = SCANNER_METRIC_POSE_GRAPH_PATH,
            allowed = metricPoseGraph.allowed,
            metric = metricPoseGraph.metric,
            errors = metricPoseGraph.errors,
            warnings = metricPoseGraph.warnings
        )

        val bundleAdjustment = analyzeScannerBundleAdjustment(workspaceDir, limits.bundleAdjustment)
        stages += ScannerReconstructionPipelineStage(
            name = "bundle_adjustment_diagnostics",
            artifactPath = SCANNER_BUNDLE_ADJUSTMENT_PATH,
            allowed = bundleAdjustment.allowed,
            metric = bundleAdjustment.metric,
            errors = bundleAdjustment.errors,
            warnings = bundleAdjustment.warnings
        )

        val metricPoseSolve = solveScannerMetricPoses(workspaceDir, limits.metricPoseSolve)
        stages += ScannerReconstructionPipelineStage(
            name = "metric_pose_solve",
            artifactPath = SCANNER_METRIC_POSE_SOLVE_PATH,
            allowed = metricPoseSolve.allowed,
            metric = metricPoseSolve.metric,
            errors = metricPoseSolve.errors,
            warnings = metricPoseSolve.warnings
        )

        val poseOptimizer = optimizeScannerMetricPoses(workspaceDir, limits.poseOptimizer)
        stages += ScannerReconstructionPipelineStage(
            name = "metric_pose_optimizer",
            artifactPath = SCANNER_OPTIMIZED_METRIC_POSES_PATH,
            allowed = poseOptimizer.allowed,
            metric = poseOptimizer.metric,
            errors = poseOptimizer.errors,
            warnings = poseOptimizer.warnings
        )

        val sparseTriangulation = prepareScannerSparseTriangulation(workspaceDir, limits.sparseTriangulation)
        stages += ScannerReconstructionPipelineStage(
            name = "sparse_triangulation",
            artifactPath = SCANNER_SPARSE_TRIANGULATION_PATH,
            allowed = sparseTriangulation.allowed,
            metric = sparseTriangulation.metric,
            errors = sparseTriangulation.errors,
            warnings = sparseTriangulation.warnings
        )

        val experimentalSparsePreview = buildScannerExperimentalSparsePreview(workspaceDir, limits.experimentalSparsePreview)
        stages += ScannerReconstructionPipelineStage(
            name = "experimental_sparse_preview",
            artifactPath = SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH,
            allowed = experimentalSparsePreview.allowed,
            metric = experimentalSparsePreview.metric,
            errors = experimentalSparsePreview.errors,
            warnings = experimentalSparsePreview.warnings
        )

        val denseReconstruction = admitScannerDenseReconstruction(workspaceDir, limits.denseReconstruction)
        stages += ScannerReconstructionPipelineStage(
            name = "dense_reconstruction",
            artifactPath = SCANNER_DENSE_RECONSTRUCTION_PATH,
            allowed = denseReconstruction.allowed,
            metric = denseReconstruction.metric,
            errors = denseReconstruction.errors,
            warnings = denseReconstruction.warnings
        )

        val densePointCloud = buildScannerDensePointCloud(workspaceDir, limits.densePointCloud)
        stages += ScannerReconstructionPipelineStage(
            name = "dense_point_cloud",
            artifactPath = SCANNER_DENSE_POINT_CLOUD_PATH,
            allowed = densePointCloud.allowed,
            metric = densePointCloud.metric,
            errors = densePointCloud.errors,
            warnings = densePointCloud.warnings
        )

        val surfaceReconstruction = reconstructScannerSurface(workspaceDir, limits.surfaceReconstruction)
        stages += ScannerReconstructionPipelineStage(
            name = "surface_reconstruction",
            artifactPath = SCANNER_SURFACE_RECONSTRUCTION_PATH,
            allowed = surfaceReconstruction.allowed,
            metric = surfaceReconstruction.metric,
            errors = surfaceReconstruction.errors,
            warnings = surfaceReconstruction.warnings
        )

        val meshTopology = admitScannerMeshTopology(workspaceDir, limits.meshTopology)
        stages += ScannerReconstructionPipelineStage(
            name = "mesh_topology",
            artifactPath = SCANNER_MESH_TOPOLOGY_PATH,
            allowed = meshTopology.allowed,
            metric = meshTopology.metric,
            errors = meshTopology.errors,
            warnings = meshTopology.warnings
        )

        val metricMeshCandidate = buildScannerMetricMeshCandidate(workspaceDir, limits.metricMeshCandidate)
        stages += ScannerReconstructionPipelineStage(
            name = "metric_mesh_candidate",
            artifactPath = SCANNER_METRIC_MESH_CANDIDATE_PATH,
            allowed = metricMeshCandidate.allowed,
            metric = metricMeshCandidate.metric,
            errors = metricMeshCandidate.errors,
            warnings = metricMeshCandidate.warnings
        )

        val meshValidation = validateScannerMeshCandidate(workspaceDir, limits.meshValidation)
        stages += ScannerReconstructionPipelineStage(
            name = "mesh_validation",
            artifactPath = SCANNER_MESH_VALIDATION_PATH,
            allowed = meshValidation.allowed,
            metric = meshValidation.metric,
            errors = meshValidation.errors,
            warnings = meshValidation.warnings
        )

        val repairReport = buildScannerRepairReport(workspaceDir, limits.repairReport)
        stages += ScannerReconstructionPipelineStage(
            name = "repair_report",
            artifactPath = SCANNER_REPAIR_REPORT_PATH,
            allowed = repairReport.allowed,
            metric = repairReport.metric,
            errors = repairReport.errors,
            warnings = repairReport.warnings
        )

        val printabilityReport = buildScannerPrintabilityReport(workspaceDir, limits.printabilityReport)
        stages += ScannerReconstructionPipelineStage(
            name = "printability_report",
            artifactPath = SCANNER_PRINTABILITY_REPORT_PATH,
            allowed = printabilityReport.allowed,
            metric = printabilityReport.metric,
            errors = printabilityReport.errors,
            warnings = printabilityReport.warnings
        )

        val slicerLoadValidation = validateScannerSlicerLoad(workspaceDir, limits.slicerLoadValidation)
        stages += ScannerReconstructionPipelineStage(
            name = "slicer_load_validation",
            artifactPath = SCANNER_SLICER_LOAD_VALIDATION_PATH,
            allowed = slicerLoadValidation.allowed,
            metric = slicerLoadValidation.metric,
            errors = slicerLoadValidation.errors,
            warnings = slicerLoadValidation.warnings
        )

        val metricExport = exportScannerMetricMesh(workspaceDir, limits.metricExport)
        stages += ScannerReconstructionPipelineStage(
            name = "metric_export",
            artifactPath = SCANNER_EXPORT_MANIFEST_PATH,
            allowed = metricExport.allowed,
            metric = metricExport.metric,
            errors = metricExport.errors,
            warnings = metricExport.warnings
        )

        val workspaceHandoff = validateScannerWorkspaceHandoff(workspaceDir, limits.workspaceHandoff)
        stages += ScannerReconstructionPipelineStage(
            name = "workspace_handoff",
            artifactPath = SCANNER_WORKSPACE_HANDOFF_PATH,
            allowed = workspaceHandoff.allowed,
            metric = workspaceHandoff.metric,
            errors = workspaceHandoff.errors,
            warnings = workspaceHandoff.warnings
        )
    }

    val blockingErrors = stages.flatMap { stage ->
        stage.errors.map { "${stage.name}:$it" }
    }.distinct()
    val metric = stages.isNotEmpty() && stages.all { it.metric }
    val denseReady = workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH)
        .takeIf { it.isFile }
        ?.let { JSONObject(it.readText()).optBoolean("dense_reconstruction_admitted", false) }
        ?: false
    val result = ScannerReconstructionPipelineResult(
        completed = stages.isNotEmpty(),
        metric = metric,
        denseReconstructionReady = denseReady,
        workspaceDir = workspaceDir,
        stages = stages,
        blockingErrors = blockingErrors,
        summaryPath = SCANNER_RECONSTRUCTION_SUMMARY_PATH
    )
    workspaceDir.mkdirs()
    workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH)
        .writeText(scannerReconstructionPipelineSummaryJson(result, limits).toString(2))
    writeScannerReconstructionAudit(result = result, packageDir = packageDir)
    return result
}

internal fun scannerReconstructionPipelineSummaryJson(
    result: ScannerReconstructionPipelineResult,
    limits: ScannerReconstructionPipelineLimits
): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("completed", result.completed)
        .put("metric", result.metric)
        .put("dense_reconstruction_ready", result.denseReconstructionReady)
        .put("summary_path", result.summaryPath)
        .put("workspace_path", result.workspaceDir.absolutePath)
        .put("blocking_errors", JSONArray(result.blockingErrors))
        .put(
            "stage_order",
            JSONArray(
                listOf(
                    "workspace",
                    "depth_fusion",
                    "feature_match_graph",
                    "feature_tracks",
                    "pose_initialization",
                    "pose_refinement",
                    "sparse_reconstruction_inputs",
                    "metric_pose_graph",
                    "bundle_adjustment_diagnostics",
                    "metric_pose_solve",
                    "metric_pose_optimizer",
                    "sparse_triangulation",
                    "experimental_sparse_preview",
                    "dense_reconstruction",
                    "dense_point_cloud",
                    "surface_reconstruction",
                    "mesh_topology",
                    "metric_mesh_candidate",
                    "mesh_validation",
                    "repair_report",
                    "printability_report",
                    "slicer_load_validation",
                    "metric_export",
                    "workspace_handoff"
                )
            )
        )
        .put(
            "stages",
            JSONArray().apply {
                result.stages.forEach { stage ->
                    put(
                        JSONObject()
                            .put("name", stage.name)
                            .put("artifact_path", stage.artifactPath ?: JSONObject.NULL)
                            .put("artifact_exists", stage.artifactPath?.let { result.workspaceDir.resolve(it).isFile } ?: false)
                            .put("allowed", stage.allowed)
                            .put("metric", stage.metric)
                            .put("errors", JSONArray(stage.errors))
                            .put("warnings", JSONArray(stage.warnings))
                    )
                }
            }
        )
        .put(
            "limits",
            JSONObject()
                .put("max_staged_frames", limits.workspace.maxStagedFrames)
                .put("min_depth_fusion_frames", limits.depthFusion.minDepthFrameCount)
                .put("min_features_per_frame", limits.features.minFeaturesPerFrame)
                .put("min_track_count", limits.tracks.minTrackCount)
                .put("min_sparse_tracks", limits.sparseReconstruction.minPreparedTracks)
                .put("min_optimizer_tracks", limits.poseOptimizer.minPreparedTrackCount)
                .put("max_experimental_sparse_preview_points", limits.experimentalSparsePreview.maxPreviewPoints)
                .put("min_dense_sparse_points", limits.denseReconstruction.minMetricSparsePoints)
                .put("min_dense_point_cloud_surface_points", limits.densePointCloud.minPointCountForSurface)
                .put("min_surface_point_count", limits.surfaceReconstruction.minSurfacePointCount)
                .put("min_mesh_topology_source_point_count", limits.meshTopology.minSourcePointCount)
                .put("min_metric_mesh_candidate_vertices", limits.metricMeshCandidate.minVertexCount)
                .put("min_metric_mesh_candidate_triangles", limits.metricMeshCandidate.minTriangleCount)
                .put("min_mesh_validation_vertices", limits.meshValidation.minVertexCount)
                .put("min_mesh_validation_triangles", limits.meshValidation.minTriangleCount)
                .put("max_repair_report_repaired_surface_ratio", limits.repairReport.maxRepairedSurfaceRatioForMetricMesh.toDouble())
                .put("min_printability_vertices", limits.printabilityReport.minVertexCount)
                .put("min_printability_triangles", limits.printabilityReport.minTriangleCount)
                .put("min_slicer_load_vertices", limits.slicerLoadValidation.minVertexCount)
                .put("min_slicer_load_triangles", limits.slicerLoadValidation.minTriangleCount)
                .put("min_metric_export_vertices", limits.metricExport.minVertexCount)
                .put("min_metric_export_triangles", limits.metricExport.minTriangleCount)
                .put("min_workspace_handoff_vertices", limits.workspaceHandoff.minVertexCount)
                .put("min_workspace_handoff_triangles", limits.workspaceHandoff.minTriangleCount)
        )
