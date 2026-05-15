package com.mobileslicer.storage

import com.mobileslicer.viewer.PreparedViewerMesh
import java.io.File

internal class PreparedViewerMeshCache(
    private val maxEntries: Int = 4,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    private val entries = LinkedHashMap<Key, Entry>(maxEntries, 0.75f, true)
    internal var retainedBytes: Long = 0L
        private set

    @Synchronized
    fun get(file: File): PreparedViewerMesh? = entries[keyFor(file)]?.preparedMesh

    @Synchronized
    fun put(file: File, preparedMesh: PreparedViewerMesh) {
        val key = keyFor(file)
        val byteSize = byteSizeOf(preparedMesh)
        entries.remove(key)?.let { retainedBytes -= it.byteSize }
        if (byteSize > maxBytes.coerceAtLeast(0L)) {
            return
        }
        entries[key] = Entry(preparedMesh = preparedMesh, byteSize = byteSize)
        retainedBytes += byteSize
        trimToBudget()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        retainedBytes = 0L
    }

    private fun keyFor(file: File): Key =
        Key(
            path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath),
            sizeBytes = file.length(),
            lastModifiedMs = file.lastModified()
        )

    private data class Key(
        val path: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long
    )

    private data class Entry(
        val preparedMesh: PreparedViewerMesh,
        val byteSize: Long
    )

    private fun trimToBudget() {
        val entryLimit = maxEntries.coerceAtLeast(1)
        val byteLimit = maxBytes.coerceAtLeast(0L)
        val iterator = entries.entries.iterator()
        while ((entries.size > entryLimit || retainedBytes > byteLimit) && iterator.hasNext()) {
            val eldest = iterator.next()
            retainedBytes -= eldest.value.byteSize
            iterator.remove()
        }
    }

    private fun byteSizeOf(preparedMesh: PreparedViewerMesh): Long =
        preparedMesh.renderArrayBytes

    private companion object {
        private const val DEFAULT_MAX_BYTES = 96L * 1024L * 1024L
    }
}
