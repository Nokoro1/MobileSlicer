package com.mobileslicer.viewer

import android.util.Log
import java.util.ArrayDeque
import java.util.LinkedHashMap

internal class PaintOverlayUploadManager(
    private val deleteUpload: (TriangleUpload) -> Unit
) {
    var baseUploads: List<PaintOverlayUpload> = emptyList()
        private set
    var liveUploads: List<PaintOverlayUpload> = emptyList()
        private set

    private val baseUploadPages = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
    private val liveUploadPages = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
    private val baseReplacementKeys = mutableSetOf<String>()
    private val liveReplacementKeys = mutableSetOf<String>()
    private val stagedLiveUploads = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
    private var replacementUploads: MutableList<PaintOverlayUpload>? = null

    private val pendingUploads: ArrayDeque<PendingPaintOverlayUpload> = ArrayDeque()

    val pendingCount: Int
        get() = pendingUploads.size

    val hasPending: Boolean
        get() = pendingUploads.isNotEmpty()

    val replacementCount: Int
        get() = replacementUploads?.size ?: 0

    fun peekPending(): PendingPaintOverlayUpload? = pendingUploads.peek()

    fun pendingIterator(): Iterator<PendingPaintOverlayUpload> = pendingUploads.iterator()

    fun removePendingHead(): PendingPaintOverlayUpload? =
        if (pendingUploads.isEmpty()) null else pendingUploads.remove()

    fun removePendingHead(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            if (pendingUploads.isNotEmpty()) {
                pendingUploads.remove()
            }
        }
    }

    fun deleteAll() {
        baseUploads.forEach { deleteUpload(it.upload) }
        baseUploads = emptyList()
        baseUploadPages.clear()
        baseReplacementKeys.clear()
        liveUploads.forEach { deleteUpload(it.upload) }
        liveUploads = emptyList()
        liveUploadPages.clear()
        liveReplacementKeys.clear()
        deleteStagedLive()
        replacementUploads?.forEach { deleteUpload(it.upload) }
        replacementUploads = null
        pendingUploads.clear()
    }

    fun clearLive() {
        liveUploads.forEach { deleteUpload(it.upload) }
        liveUploads = emptyList()
        liveUploadPages.clear()
        liveReplacementKeys.clear()
        deleteStagedLive()
        val retained = ArrayDeque<PendingPaintOverlayUpload>()
        while (pendingUploads.isNotEmpty()) {
            val upload = pendingUploads.removeFirst()
            if (!upload.live) {
                retained.add(upload)
            }
        }
        pendingUploads.addAll(retained)
    }

    fun replaceAll(overlay: ViewerPaintOverlay?) {
        replacementUploads?.forEach { deleteUpload(it.upload) }
        replacementUploads = null
        pendingUploads.clear()
        if (overlay == null || overlay.layers.isEmpty()) {
            deleteAll()
            return
        }
        replacementUploads = mutableListOf()
        queue(overlay, live = false, replacement = true)
    }

    fun queue(overlay: ViewerPaintOverlay, live: Boolean = false, replacement: Boolean = false) {
        if (overlay.layers.isEmpty()) return
        if (live && !replacement) {
            abortReplacement()
        }
        if (!replacement) {
            removePendingByLayerIds(overlay.layers.mapTo(mutableSetOf()) { it.id })
        }
        if (live && !replacement) {
            overlay.layers.asReversed().forEach { layer ->
                if (layer.deleteOnly || (layer.vertices.isNotEmpty() && layer.normals.isNotEmpty())) {
                    pendingUploads.addFirst(PendingPaintOverlayUpload(layer, live = true, replacement = false))
                }
            }
        } else {
            overlay.layers.forEach { layer ->
                if (layer.deleteOnly || (layer.vertices.isNotEmpty() && layer.normals.isNotEmpty())) {
                    pendingUploads.add(PendingPaintOverlayUpload(layer, live = live, replacement = replacement))
                }
            }
        }
    }

    fun promoteLive() {
        val stagedUploads =
            if (stagedLiveUploads.isEmpty()) {
                emptyList()
            } else {
                stagedLiveUploads.values.flatten()
            }
        stagedLiveUploads.clear()
        if (liveUploads.isNotEmpty() || stagedUploads.isNotEmpty()) {
            val promotedUploads = liveUploads + stagedUploads
            liveUploads = emptyList()
            liveUploadPages.clear()
            liveReplacementKeys.clear()
            appendBaseReplacing(promotedUploads)
        }
        if (pendingUploads.isEmpty()) return
        val promoted = ArrayDeque<PendingPaintOverlayUpload>()
        while (pendingUploads.isNotEmpty()) {
            val upload = pendingUploads.removeFirst()
            if (upload.live) {
                promoted.add(upload.copy(live = false))
            } else {
                promoted.add(upload)
            }
        }
        pendingUploads.addAll(promoted)
    }

    fun appendBaseReplacing(uploads: List<PaintOverlayUpload>) {
        appendPages(
            pages = baseUploadPages,
            replacementKeys = baseReplacementKeys,
            uploads = uploads,
            replace = true
        )
        removeByLayerIds(replacementLayerIdsForUploads(uploads), removeBase = false, removeLive = true)
        baseUploads = baseUploadPages.values.flatten()
    }

    fun appendLiveReplacing(uploads: List<PaintOverlayUpload>) {
        appendPages(
            pages = liveUploadPages,
            replacementKeys = liveReplacementKeys,
            uploads = uploads,
            replace = true
        )
        removeByLayerIds(replacementLayerIdsForUploads(uploads), removeBase = true, removeLive = false)
        liveUploads = liveUploadPages.values.flatten()
    }

    fun appendReplacement(uploads: List<PaintOverlayUpload>) {
        replacementUploads?.addAll(uploads)
    }

    fun swapReplacementIfReady() {
        if (replacementUploads == null || pendingUploads.any { it.replacement }) return
        val replacement = replacementUploads?.toList() ?: return
        baseUploads.forEach { deleteUpload(it.upload) }
        liveUploads.forEach { deleteUpload(it.upload) }
        baseUploadPages.clear()
        baseReplacementKeys.clear()
        appendPages(
            pages = baseUploadPages,
            replacementKeys = baseReplacementKeys,
            uploads = replacement,
            replace = false
        )
        baseUploads = baseUploadPages.values.flatten()
        liveUploads = emptyList()
        liveUploadPages.clear()
        liveReplacementKeys.clear()
        replacementUploads = null
        Log.i(
            "MobileSlicerPaintPerf",
            "overlay_snapshot_swap baseLayers=${baseUploads.size} " +
                "vertices=${baseUploads.sumOf { it.upload.vertexCount }}"
        )
    }

    fun removeByLayerIds(layerIds: Set<String>, removeBase: Boolean, removeLive: Boolean) {
        if (layerIds.isEmpty()) return
        val sourceKeys = layerIds.mapNotNullTo(mutableSetOf()) { it.overlaySourceKey() }
        val replacementKeys = layerIds + sourceKeys
        if (removeBase && replacementKeys.any { it in baseReplacementKeys }) {
            val retainedPages = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
            baseUploadPages.forEach { (pageKey, uploads) ->
                val retainedUploads = uploads.filterTo(mutableListOf()) { upload ->
                    val remove = upload.matchesOverlayReplacement(layerIds, sourceKeys)
                    if (remove) deleteUpload(upload.upload)
                    !remove
                }
                if (retainedUploads.isNotEmpty()) {
                    retainedPages[pageKey] = retainedUploads
                }
            }
            baseUploadPages.clear()
            baseUploadPages.putAll(retainedPages)
            baseUploads = baseUploadPages.values.flatten()
            baseReplacementKeys.rebuildFromPaintOverlayUploads(baseUploads)
        }
        if (removeLive && replacementKeys.any { it in liveReplacementKeys }) {
            val retainedPages = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
            liveUploadPages.forEach { (pageKey, uploads) ->
                val retainedUploads = uploads.filterTo(mutableListOf()) { upload ->
                    val remove = upload.matchesOverlayReplacement(layerIds, sourceKeys)
                    if (remove) deleteUpload(upload.upload)
                    !remove
                }
                if (retainedUploads.isNotEmpty()) {
                    retainedPages[pageKey] = retainedUploads
                }
            }
            liveUploadPages.clear()
            liveUploadPages.putAll(retainedPages)
            liveUploads = liveUploadPages.values.flatten()
            liveReplacementKeys.rebuildFromPaintOverlayUploads(liveUploads)
        }
    }

    private fun deleteStagedLive() {
        stagedLiveUploads.values
            .flatten()
            .forEach { deleteUpload(it.upload) }
        stagedLiveUploads.clear()
    }

    private fun abortReplacement() {
        replacementUploads?.forEach { deleteUpload(it.upload) }
        replacementUploads = null
        if (pendingUploads.none { it.replacement }) return
        val retained = ArrayDeque<PendingPaintOverlayUpload>()
        while (pendingUploads.isNotEmpty()) {
            val upload = pendingUploads.removeFirst()
            if (!upload.replacement) {
                retained.add(upload)
            }
        }
        pendingUploads.addAll(retained)
    }

    private fun appendPages(
        pages: LinkedHashMap<String, MutableList<PaintOverlayUpload>>,
        replacementKeys: MutableSet<String>,
        uploads: List<PaintOverlayUpload>,
        replace: Boolean
    ) {
        if (uploads.isEmpty()) return
        val grouped = LinkedHashMap<String, MutableList<PaintOverlayUpload>>()
        uploads.forEach { upload ->
            grouped.getOrPut(upload.primaryPageKey()) { mutableListOf() } += upload
        }
        grouped.forEach { (pageKey, pageUploads) ->
            if (replace) {
                pages.remove(pageKey)?.forEach { deleteUpload(it.upload) }
            }
            pages.getOrPut(pageKey) { mutableListOf() }.addAll(pageUploads)
        }
        replacementKeys.clear()
        replacementKeys.addAll(pages.values.flatten().flatMap { replacementKeysForUpload(it) })
    }

    private fun removePendingByLayerIds(ids: Set<String>) {
        if (ids.isEmpty() || pendingUploads.isEmpty()) return
        val sourceKeys = ids.mapNotNullTo(mutableSetOf()) { it.overlaySourceKey() }
        ids.forEach { id ->
            stagedLiveUploads.remove(id)?.forEach { deleteUpload(it.upload) }
        }
        sourceKeys.forEach { sourceKey ->
            stagedLiveUploads.keys
                .filter { it.overlaySourceKey() == sourceKey }
                .forEach { key -> stagedLiveUploads.remove(key)?.forEach { deleteUpload(it.upload) } }
        }
        val retained = ArrayDeque<PendingPaintOverlayUpload>()
        while (pendingUploads.isNotEmpty()) {
            val upload = pendingUploads.removeFirst()
            if (!upload.layer.id.matchesOverlayReplacement(ids, sourceKeys)) {
                retained.add(upload)
            }
        }
        pendingUploads.addAll(retained)
    }
}
