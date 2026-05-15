package com.mobileslicer

import com.mobileslicer.automation.AutomationSliceTiming
import com.mobileslicer.automation.AutomationSliceNativeMetrics
import com.mobileslicer.automation.AutomationSlicePreviewLoadMetrics
import com.mobileslicer.automation.AutomationSlicePreviewInfoMetrics
import com.mobileslicer.automation.automationSlicePreviewInfoMetrics
import com.mobileslicer.automation.automationSliceSuccessStatus
import com.mobileslicer.automation.parseAutomationSliceNativeMetrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSliceRunnerTest {
    @Test
    fun successStatusIncludesPhaseTimings() {
        val outputFile = createTempFile(prefix = "automation-status", suffix = ".gcode")
        outputFile.writeText("G1 X1 Y1 E1\n")

        val status = automationSliceSuccessStatus(
            modelFile = File("/tmp/model.stl"),
            stagedModel = File("/tmp/staged-model.stl"),
            outputFile = outputFile,
            timing = AutomationSliceTiming(
                stagingMs = 1,
                nativeLoadMs = 2,
                placementMs = 3,
                configMs = 4,
                nativeSliceMs = 5,
                thumbnailMs = 6,
                writeGcodeMs = 7,
                totalMs = 21
            ),
            nativeMetrics = AutomationSliceNativeMetrics(
                previewMoves = 7,
                previewCacheBuilt = true,
                previewCacheComplete = true,
                previewCachedVertices = 8,
                previewCacheBuildMs = 9,
                gcodeBytes = 10,
                processorMoveBytes = 11,
                processorLineEndBytes = 12,
                previewLayerCountBytes = 13,
                exactPreviewCacheEligible = true,
                processorMovesReleasedDuringExport = true,
                processorMoveBytesRetained = 0,
                processorLineEndBytesRetained = 0,
                processorReleaseMs = 14,
                nativeExportStartRssKb = 101,
                nativeAfterSetupRssKb = 151,
                nativeAfterLayersRssKb = 202,
                nativeAfterFooterRssKb = 212,
                nativeAfterGenerationRssKb = 222,
                nativeAfterFinalizeRssKb = 303,
                nativeAfterReleaseRssKb = 204,
                nativeAfterStatsRssKb = 205,
                nativeBeforeReturnRssKb = 206
            ),
            previewInfoMetrics = AutomationSlicePreviewInfoMetrics(
                summaryHasRichPreviewInfo = true,
                enrichedHasRichPreviewInfo = true,
                lineTypeCount = 2,
                filamentCount = 1,
                layerCount = 42
            ),
            previewLoadMetrics = AutomationSlicePreviewLoadMetrics(
                rangePlanMs = 10,
                viewerLoadMs = 11,
                plannedRangeCount = 3,
                requestedLayerCount = 42,
                loadedStartLayer = 1,
                loadedEndLayer = 14,
                loadedLayerCount = 14,
                plannedCoveredLayers = 42,
                vertexBudget = 2_000_000,
                success = true
            ),
            configJson = """{"layer_height":0.2}"""
        )

        assertTrue(status.startsWith("success:"))
        assertTrue(status.contains("bytes=${outputFile.length()}"))
        assertTrue(status.contains("stagingMs=1"))
        assertTrue(status.contains("nativeLoadMs=2"))
        assertTrue(status.contains("placementMs=3"))
        assertTrue(status.contains("configMs=4"))
        assertTrue(status.contains("nativeSliceMs=5"))
        assertTrue(status.contains("thumbnailMs=6"))
        assertTrue(status.contains("writeGcodeMs=7"))
        assertTrue(status.contains("previewMoves=7"))
        assertTrue(status.contains("previewCacheBuilt=1"))
        assertTrue(status.contains("previewCacheComplete=1"))
        assertTrue(status.contains("previewCachedVertices=8"))
        assertTrue(status.contains("previewCacheBuildMs=9"))
        assertTrue(status.contains("nativeGcodeBytes=10"))
        assertTrue(status.contains("processorMoveBytes=11"))
        assertTrue(status.contains("processorLineEndBytes=12"))
        assertTrue(status.contains("previewLayerCountBytes=13"))
        assertTrue(status.contains("exactPreviewCacheEligible=1"))
        assertTrue(status.contains("processorMovesReleasedDuringExport=1"))
        assertTrue(status.contains("processorMoveBytesRetained=0"))
        assertTrue(status.contains("processorLineEndBytesRetained=0"))
        assertTrue(status.contains("processorReleaseMs=14"))
        assertTrue(status.contains("nativeExportStartRssKb=101"))
        assertTrue(status.contains("nativeAfterSetupRssKb=151"))
        assertTrue(status.contains("nativeAfterLayersRssKb=202"))
        assertTrue(status.contains("nativeAfterFooterRssKb=212"))
        assertTrue(status.contains("nativeAfterGenerationRssKb=222"))
        assertTrue(status.contains("nativeAfterFinalizeRssKb=303"))
        assertTrue(status.contains("nativeAfterReleaseRssKb=204"))
        assertTrue(status.contains("nativeAfterStatsRssKb=205"))
        assertTrue(status.contains("nativeBeforeReturnRssKb=206"))
        assertTrue(status.contains("previewInfoRich=1"))
        assertTrue(status.contains("previewInfoEnrichedRich=1"))
        assertTrue(status.contains("previewInfoLineTypes=2"))
        assertTrue(status.contains("previewInfoFilaments=1"))
        assertTrue(status.contains("previewInfoLayers=42"))
        assertTrue(status.contains("previewPlanMs=10"))
        assertTrue(status.contains("previewLoadMs=11"))
        assertTrue(status.contains("previewRanges=3"))
        assertTrue(status.contains("previewRequestedLayers=42"))
        assertTrue(status.contains("previewLoadedStart=1"))
        assertTrue(status.contains("previewLoadedEnd=14"))
        assertTrue(status.contains("previewLoadedLayers=14"))
        assertTrue(status.contains("previewPlannedCoveredLayers=42"))
        assertTrue(status.contains("previewVertexBudget=2000000"))
        assertTrue(status.contains("previewLoadSuccess=1"))
        assertTrue(status.contains("previewLoadGlUnavailable=0"))
        assertTrue(status.contains("elapsedMs=21"))
        assertTrue(status.contains("""config={"layer_height":0.2}"""))
    }

    @Test
    fun parsesNativeSliceMetrics() {
        val metrics = parseAutomationSliceNativeMetrics(
            "previewMoves=123|previewCacheBuilt=1|previewCacheComplete=0|previewCachedVertices=456|previewCacheBuildMs=7" +
                "|gcodeBytes=89|processorMoveBytes=100|processorLineEndBytes=11|previewLayerCountBytes=12|exactPreviewCacheEligible=1" +
                "|processorMovesReleasedDuringExport=1|processorMoveBytesRetained=0|processorLineEndBytesRetained=0|processorReleaseMs=3" +
                "|nativeExportStartRssKb=101|nativeAfterSetupRssKb=151|nativeAfterLayersRssKb=202|nativeAfterFooterRssKb=212" +
                "|nativeAfterGenerationRssKb=222|nativeAfterFinalizeRssKb=303|nativeAfterReleaseRssKb=204" +
                "|nativeAfterStatsRssKb=205|nativeBeforeReturnRssKb=206"
        )

        assertEquals(123L, metrics.previewMoves)
        assertTrue(metrics.previewCacheBuilt)
        assertEquals(false, metrics.previewCacheComplete)
        assertEquals(456L, metrics.previewCachedVertices)
        assertEquals(7L, metrics.previewCacheBuildMs)
        assertEquals(89L, metrics.gcodeBytes)
        assertEquals(100L, metrics.processorMoveBytes)
        assertEquals(11L, metrics.processorLineEndBytes)
        assertEquals(12L, metrics.previewLayerCountBytes)
        assertTrue(metrics.exactPreviewCacheEligible)
        assertTrue(metrics.processorMovesReleasedDuringExport)
        assertEquals(0L, metrics.processorMoveBytesRetained)
        assertEquals(0L, metrics.processorLineEndBytesRetained)
        assertEquals(3L, metrics.processorReleaseMs)
        assertEquals(101L, metrics.nativeExportStartRssKb)
        assertEquals(151L, metrics.nativeAfterSetupRssKb)
        assertEquals(202L, metrics.nativeAfterLayersRssKb)
        assertEquals(212L, metrics.nativeAfterFooterRssKb)
        assertEquals(222L, metrics.nativeAfterGenerationRssKb)
        assertEquals(303L, metrics.nativeAfterFinalizeRssKb)
        assertEquals(204L, metrics.nativeAfterReleaseRssKb)
        assertEquals(205L, metrics.nativeAfterStatsRssKb)
        assertEquals(206L, metrics.nativeBeforeReturnRssKb)
    }

    @Test
    fun previewInfoMetricsPreferEnrichedSummaryCounts() {
        val metrics = automationSlicePreviewInfoMetrics(
            summaryText = "bytes=123|lines=10|layers=2|types=Wall",
            enrichedSummaryText = "bytes=123|lines=7|layers=2|time=1h|grams=3.25" +
                "|previewLineTypes=role,2,Outer wall,#FF7D38,12.5,20.0,1.0,2.96;option,0,Travel,#38489B,4.0,6.4,0.0,0.0" +
                "|previewFilaments=1,Bambu PLA Basic,#00AA99,1.0,2.0,0.0,0.0,0.5,1.0,0.25,0.5,1.75,3.5,0.09" +
                "|previewTotals=totalSeconds=60.0,prepareSeconds=5.0,modelSeconds=55.0,cost=0.09,filamentChanges=2,extruderChanges=1"
        )

        assertEquals(false, metrics.summaryHasRichPreviewInfo)
        assertTrue(metrics.enrichedHasRichPreviewInfo)
        assertEquals(2, metrics.lineTypeCount)
        assertEquals(1, metrics.filamentCount)
        assertEquals(2, metrics.layerCount)
    }
}
