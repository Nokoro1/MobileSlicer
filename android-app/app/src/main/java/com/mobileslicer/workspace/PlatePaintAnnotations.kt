package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

internal enum class PaintMode {
    Support,
    Seam,
    Color,
    FuzzySkin
}

internal data class SourceMeshFingerprint(
    val fileLengthBytes: Long,
    val lastModifiedEpochMs: Long,
    val triangleCount: Int?,
    val bounds: MeshBounds?,
    val sampleSha256: String
) {
    fun stableSignature(): String = buildString {
        append(fileLengthBytes)
        append(':')
        append(lastModifiedEpochMs)
        append(':')
        append(triangleCount ?: -1)
        append(':')
        bounds?.let {
            append(formatFloat(it.minX))
            append(',')
            append(formatFloat(it.minY))
            append(',')
            append(formatFloat(it.minZ))
            append(',')
            append(formatFloat(it.maxX))
            append(',')
            append(formatFloat(it.maxY))
            append(',')
            append(formatFloat(it.maxZ))
        } ?: append("no-bounds")
        append(':')
        append(sampleSha256)
    }
}

internal data class SerializedPaintTriangle(
    val triangleIndex: Int,
    val hexBits: String
)

internal data class SerializedPaintVolumeLayer(
    val volumeIndex: Int,
    val triangleCount: Int,
    val serializedTriangles: List<SerializedPaintTriangle>,
    val nativeMeshFingerprint: String? = null
) {
    val isEmpty: Boolean get() = serializedTriangles.isEmpty()
}

internal data class SerializedPaintLayer(
    val mode: PaintMode,
    val objectSourceKey: String,
    val meshFingerprint: SourceMeshFingerprint?,
    val volumeLayers: List<SerializedPaintVolumeLayer>,
    val referencedSlotIndexes: Set<Int> = emptySet(),
    val staleReason: String? = null,
    val schemaVersion: Int = 1
) {
    val isEmpty: Boolean get() = volumeLayers.all { it.isEmpty }
    val isStale: Boolean get() = !staleReason.isNullOrBlank()

    fun markStale(reason: String): SerializedPaintLayer =
        copy(staleReason = reason.trim().ifBlank { "Paint is stale." })
}

internal data class PlateObjectPaint(
    val support: SerializedPaintLayer? = null,
    val seam: SerializedPaintLayer? = null,
    val color: SerializedPaintLayer? = null,
    val fuzzySkin: SerializedPaintLayer? = null,
    val schemaVersion: Int = 1
) {
    val hasActivePaint: Boolean
        get() = layers().any { layer -> !layer.isEmpty && !layer.isStale }

    val hasAnyPaintPayload: Boolean
        get() = layers().any { layer -> !layer.isEmpty }

    fun layers(): List<SerializedPaintLayer> =
        listOfNotNull(support, seam, color, fuzzySkin)

    fun markColorStale(reason: String): PlateObjectPaint =
        copy(color = color?.markStale(reason))
}

internal fun PlateObjectPaint.paintHash(): String {
    if (!hasActivePaint) return ""
    val digest = MessageDigest.getInstance("SHA-256")
    fun put(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    put(schemaVersion.toString())
    layers()
        .filter { !it.isEmpty && !it.isStale }
        .sortedBy { it.mode.name }
        .forEach { layer ->
            put(layer.mode.name)
            put(layer.schemaVersion.toString())
            put(layer.objectSourceKey)
            put(layer.meshFingerprint?.stableSignature().orEmpty())
            layer.referencedSlotIndexes.sorted().forEach { put("slot:$it") }
            layer.volumeLayers.sortedBy { it.volumeIndex }.forEach { volume ->
                put("volume:${volume.volumeIndex}:${volume.triangleCount}")
                put("nativeFingerprint:${volume.nativeMeshFingerprint.orEmpty()}")
                volume.serializedTriangles.sortedBy { it.triangleIndex }.forEach { triangle ->
                    put("${triangle.triangleIndex}:${triangle.hexBits}")
                }
            }
        }
    return digest.digest().toHexString()
}

internal fun computeSourceMeshFingerprint(
    file: File,
    bounds: MeshBounds?,
    triangleCount: Int?
): SourceMeshFingerprint? {
    if (!file.exists() || !file.isFile || file.length() <= 0L) return null
    return SourceMeshFingerprint(
        fileLengthBytes = file.length(),
        lastModifiedEpochMs = file.lastModified(),
        triangleCount = triangleCount,
        bounds = bounds,
        sampleSha256 = sampledFileSha256(file)
    )
}

internal fun SerializedPaintLayer.validatedAgainst(
    fingerprint: SourceMeshFingerprint?
): SerializedPaintLayer {
    val expected = meshFingerprint ?: return this
    val actual = fingerprint ?: return markStale("Paint unavailable because the source mesh could not be fingerprinted.")
    return if (expected == actual) {
        this
    } else {
        markStale("Paint unavailable because the source mesh changed.")
    }
}

internal fun PlateObjectPaint.validatedAgainst(
    fingerprint: SourceMeshFingerprint?
): PlateObjectPaint =
    copy(
        support = support?.validatedAgainst(fingerprint),
        seam = seam?.validatedAgainst(fingerprint),
        color = color?.validatedAgainst(fingerprint),
        fuzzySkin = fuzzySkin?.validatedAgainst(fingerprint)
    )

internal fun PlateObjectPaint.rebasedForSource(
    objectSourceKey: String,
    fingerprint: SourceMeshFingerprint?
): PlateObjectPaint {
    fun SerializedPaintLayer.rebased(): SerializedPaintLayer =
        copy(
            objectSourceKey = objectSourceKey,
            meshFingerprint = fingerprint ?: meshFingerprint
        )
    return copy(
        support = support?.rebased(),
        seam = seam?.rebased(),
        color = color?.rebased(),
        fuzzySkin = fuzzySkin?.rebased()
    )
}

internal fun PlateObjectPaint.invalidatingColorForRemovedSlot(slotIndex: Int): PlateObjectPaint {
    val layer = color ?: return this
    if (layer.isEmpty || layer.isStale) return this
    if (layer.referencedSlotIndexes.isEmpty()) {
        return markColorStale("Color paint slot references are unavailable after filament slot deletion.")
    }
    if (slotIndex in layer.referencedSlotIndexes) {
        return markColorStale("Color paint references a removed filament slot.")
    }
    if (layer.referencedSlotIndexes.any { it > slotIndex }) {
        return markColorStale("Color paint references shifted filament slots and needs native remapping.")
    }
    return this
}

private fun sampledFileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val sampleSize = 64 * 1024
        val offsets = listOf(
            0L,
            ((length - sampleSize) / 2L).coerceAtLeast(0L),
            (length - sampleSize).coerceAtLeast(0L)
        ).distinct()
        val buffer = ByteArray(sampleSize)
        offsets.forEach { offset ->
            input.seek(offset)
            val bytesToRead = minOf(sampleSize.toLong(), length - offset).toInt()
            val read = input.read(buffer, 0, bytesToRead)
            if (read > 0) {
                digest.update(offset.toString().toByteArray(Charsets.UTF_8))
                digest.update(0)
                digest.update(buffer, 0, read)
            }
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte -> "%02x".format(byte) }

private fun formatFloat(value: Float): String =
    String.format(Locale.US, "%.5f", value)
