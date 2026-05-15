package com.mobileslicer.modelsearch

import com.mobileslicer.modelsearch.importflow.FindImportState
import com.mobileslicer.modelsearch.importflow.FindImportStateMachine
import com.mobileslicer.modelsearch.importflow.ModelImportIntentResolver
import com.mobileslicer.modelsearch.sources.SourceIntegrationMode
import com.mobileslicer.modelsearch.sources.SourceRegistry
import com.mobileslicer.modelsearch.sources.SourceUrlNormalizer
import com.mobileslicer.modelsearch.thingiverse.ThingiverseFileResult
import com.mobileslicer.modelsearch.thingiverse.ThingiverseSearchResult
import com.mobileslicer.modelsearch.thingiverse.ThingiverseSearchUiState
import com.mobileslicer.modelsearch.thingiverse.isThingiverseOAuthRedirectParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSearchContractsTest {
    @Test
    fun defaultWebSourcesAreExternalLinkOnlyAndDoNotAllowDownloadsOrThumbnails() {
        val sources = SourceRegistry.defaultSources

        assertEquals(2, sources.size)
        assertFalse(sources.any { it.id == SourceRegistry.THINGIVERSE })
        sources.forEach { source ->
            assertEquals(SourceIntegrationMode.EXTERNAL_LINK_ONLY, source.mode)
            assertTrue(source.canOpenExternally)
            assertFalse(source.directDownloadAllowed)
            assertFalse(source.thumbnailCachingAllowed)
            assertTrue(source.plainTextUiOnly)
        }
    }

    @Test
    fun sourceUrlNormalizerRecognizesKnownSourcesWithoutScraping() {
        val thingiverse = SourceUrlNormalizer.normalize("thingiverse.com/thing:123")
        val printables = SourceUrlNormalizer.normalize("https://www.printables.com/model/123")
        val makerWorld = SourceUrlNormalizer.normalize("https://makerworld.com/en/models/123")

        assertTrue(thingiverse.valid)
        assertEquals(SourceRegistry.THINGIVERSE, thingiverse.sourceId)
        assertEquals(SourceRegistry.PRINTABLES, printables.sourceId)
        assertEquals(SourceRegistry.MAKERWORLD, makerWorld.sourceId)
    }

    @Test
    fun intentResolverAcceptsModelFilesAndGenericAndroidModelMimeTypes() {
        assertTrue(
            ModelImportIntentResolver.acceptsModelUri(
                type = "application/octet-stream",
                lastPathSegment = "model.stl"
            )
        )
        assertTrue(
            ModelImportIntentResolver.acceptsModelUri(
                type = "application/vnd.ms-3mfdocument",
                lastPathSegment = "model.3mf"
            )
        )
        assertTrue(
            ModelImportIntentResolver.acceptsModelUri(
                type = "application/iso-10303-21",
                lastPathSegment = "bracket.step"
            )
        )
        assertTrue(
            ModelImportIntentResolver.acceptsModelUri(
                type = "application/octet-stream",
                lastPathSegment = "bracket.stp"
            )
        )
        assertFalse(
            ModelImportIntentResolver.acceptsModelUri(
                type = "image/png",
                lastPathSegment = "preview.png"
            )
        )
    }

    @Test
    fun sourceFlowWaitsForUserSelectedFileAndDoesNotAssumeAutoDownload() {
        val opened = FindImportStateMachine.openExternalSource(
            sourceId = SourceRegistry.PRINTABLES,
            openedAtEpochMs = 100L
        )
        val resumed = FindImportStateMachine.onAppResumed(opened)

        assertTrue(resumed is FindImportState.AwaitingUserFile)
        resumed as FindImportState.AwaitingUserFile
        assertEquals(SourceRegistry.PRINTABLES, resumed.sourceId)
        assertEquals(100L, resumed.openedAtEpochMs)
    }

    @Test
    fun thingiverseDirectImportOnlyEnablesSupportedModelFiles() {
        val stl = thingiverseFile("part.stl")
        val threeMf = thingiverseFile("plate.3mf")
        val step = thingiverseFile("cad-source.step")
        val stp = thingiverseFile("cad-source.stp")
        val pdf = thingiverseFile("instructions.pdf")

        assertTrue(stl.isSupportedModelFile)
        assertTrue(threeMf.isSupportedModelFile)
        assertTrue(step.isSupportedModelFile)
        assertTrue(stp.isSupportedModelFile)
        assertFalse(pdf.isSupportedModelFile)
    }

    @Test
    fun thingiversePrintableFilesExposeStableDisplayMetadataAndSortPriority() {
        val threeMf = thingiverseFile("benchy_plate.3mf", sizeBytes = 2_621_440, formattedSize = null)
        val stl = thingiverseFile("benchy_-_hull.stl", formattedSize = "42 KB")
        val step = thingiverseFile("benchy-source.step")
        val readme = thingiverseFile("readme.txt")

        assertEquals("3MF", threeMf.extensionLabel)
        assertEquals("benchy plate", threeMf.displayName)
        assertEquals("2.5 MB", threeMf.displaySize)
        assertEquals("benchy hull", stl.displayName)
        assertEquals("42 KB", stl.displaySize)
        assertTrue(threeMf.printableFileSortKey < stl.printableFileSortKey)
        assertTrue(stl.printableFileSortKey < step.printableFileSortKey)
        assertTrue(step.printableFileSortKey < readme.printableFileSortKey)
    }

    @Test
    fun thingiverseMultiImportStateTracksCurrentFileAndCounts() {
        val files = listOf(
            thingiverseFile("leaf.stl"),
            thingiverseFile("petal.stl"),
            thingiverseFile("pot.stl")
        )
        val state = ThingiverseSearchUiState.ImportingFiles(
            thing = ThingiverseSearchResult(
                thingId = 42,
                name = "Flower",
                creatorName = "maker",
                publicUrl = "https://www.thingiverse.com/thing:42",
                thumbnailUrl = null,
                license = "CC BY",
                fileCount = files.size,
                likeCount = null,
                downloadCount = null
            ),
            files = files,
            currentIndex = 1
        )

        assertEquals("petal.stl", state.currentFile?.name)
        assertEquals(1, state.completedCount)
        assertEquals(3, state.totalCount)
    }

    @Test
    fun thingiverseSearchResultsTrackPaginationState() {
        val results = listOf(
            ThingiverseSearchResult(
                thingId = 42,
                name = "Flower",
                creatorName = "maker",
                publicUrl = "https://www.thingiverse.com/thing:42",
                thumbnailUrl = null,
                license = "CC BY",
                fileCount = 3,
                likeCount = null,
                downloadCount = null
            )
        )
        val state = ThingiverseSearchUiState.SearchResults(
            query = "flower",
            results = results,
            page = 2,
            canLoadMore = true,
            isLoadingMore = true
        )

        assertEquals(2, state.page)
        assertTrue(state.canLoadMore)
        assertTrue(state.isLoadingMore)
        assertEquals(results, state.results)
    }

    @Test
    fun thingiverseOAuthRedirectOnlyAcceptsRegisteredAppCallback() {
        assertTrue(isThingiverseOAuthRedirectParts("mobileslicer", "thingiverse-auth", "mobileslicer", "thingiverse-auth"))
        assertFalse(isThingiverseOAuthRedirectParts("mobileslicer", "other-auth", "mobileslicer", "thingiverse-auth"))
        assertFalse(isThingiverseOAuthRedirectParts("https", "thingiverse-auth", "mobileslicer", "thingiverse-auth"))
    }

    private fun thingiverseFile(
        name: String,
        sizeBytes: Long? = 100,
        formattedSize: String? = "100 B"
    ): ThingiverseFileResult =
        ThingiverseFileResult(
            fileId = 10,
            name = name,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            thumbnailUrl = "https://cdn.thingiverse.com/renders/example.png",
            downloadUrl = "https://api.thingiverse.com/files/10/download",
            publicUrl = "https://www.thingiverse.com/download:10",
            directUrl = null
        )
}
