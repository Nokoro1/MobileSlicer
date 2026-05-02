package com.mobileslicer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePerformanceTelemetryTest {
    @Test
    fun summarizesPrepareSliceAndDisplayLod() {
        val snapshot = workspacePerformanceSnapshot(
            importTiming = ModelImportTiming(stagingMs = 100L, nativeLoadMs = 200L),
            workspacePreparationTiming = WorkspacePreparationTiming(
                viewerMeshPrepMs = 300L,
                sourceTriangleCount = 1000,
                displayTriangleCount = 250,
                reducedForDisplay = true
            ),
            firstVisibleWorkspaceFrameMs = null,
            sliceTiming = SlicePipelineTiming(
                modelReloadMs = 0L,
                configMs = 10L,
                nativeSliceMs = 20L,
                writeGcodeMs = 30L,
                summaryMs = 40L,
                totalMs = 100L
            )
        )

        assertEquals(600L, snapshot.prepareDurationMs)
        assertEquals(100L, snapshot.sliceDurationMs)
        assertEquals("Display LOD: 250/1000 tris", snapshot.displayLodSummary)
        assertEquals("Prepare: 0.6 seconds • Display LOD: 250/1000 tris • Slice: 0.1 seconds", snapshot.compactSummary())
    }

    @Test
    fun firstVisibleFrameWinsOverEstimatedPrepareDuration() {
        val snapshot = workspacePerformanceSnapshot(
            importTiming = ModelImportTiming(stagingMs = 1_000L, nativeLoadMs = 1_000L),
            workspacePreparationTiming = WorkspacePreparationTiming(viewerMeshPrepMs = 1_000L),
            firstVisibleWorkspaceFrameMs = 123L,
            sliceTiming = null
        )

        assertEquals(123L, snapshot.prepareDurationMs)
    }

    @Test
    fun flagsSlowPrepareAndSliceWithoutFailingTheFlow() {
        val snapshot = workspacePerformanceSnapshot(
            importTiming = ModelImportTiming(stagingMs = 15_000L, nativeLoadMs = 0L),
            workspacePreparationTiming = null,
            firstVisibleWorkspaceFrameMs = null,
            sliceTiming = SlicePipelineTiming(
                modelReloadMs = 0L,
                configMs = 0L,
                nativeSliceMs = 120_000L,
                writeGcodeMs = 0L,
                summaryMs = 0L,
                totalMs = 120_000L
            )
        )

        assertTrue(snapshot.warnings.contains("Prepare slow"))
        assertTrue(snapshot.warnings.contains("Slice slow"))
        assertEquals("Prepare: 15.0 seconds • Slice: 120.0 seconds • Prepare slow • Slice slow", snapshot.compactSummary())
    }

    @Test
    fun emitsStructuredResponsivenessLogLineWithoutUiText() {
        val line = workspaceResponsivenessLogLine(
            eventName = "workspace first visible frame",
            importTiming = ModelImportTiming(stagingMs = 100L, nativeLoadMs = 200L),
            workspacePreparationTiming = WorkspacePreparationTiming(
                viewerMeshPrepMs = 300L,
                cacheHit = true,
                sourceTriangleCount = 1000,
                displayTriangleCount = 250,
                reducedForDisplay = true
            ),
            firstVisibleWorkspaceFrameMs = 700L,
            firstVisiblePreviewFrameMs = 50L,
            sliceTiming = SlicePipelineTiming(
                modelReloadMs = 1L,
                configMs = 2L,
                nativeSliceMs = 3L,
                writeGcodeMs = 4L,
                summaryMs = 5L,
                totalMs = 15L
            )
        )

        assertTrue(line.startsWith("workspace_responsiveness "))
        assertTrue(line.contains("event=workspace_first_visible_frame"))
        assertTrue(line.contains("importTotalMs=300"))
        assertTrue(line.contains("workspaceMeshCacheHit=true"))
        assertTrue(line.contains("firstWorkspaceFrameMs=700"))
        assertTrue(line.contains("firstPreviewFrameMs=50"))
        assertTrue(line.contains("sliceTotalMs=15"))
    }
}
