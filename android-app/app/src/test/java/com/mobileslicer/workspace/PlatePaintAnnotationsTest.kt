package com.mobileslicer.workspace

import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatePaintAnnotationsTest {
    @Test
    fun paintHashChangesWhenSerializedTriangleChanges() {
        val first = PlateObjectPaint(seam = layer(PaintMode.Seam, hexBits = "1C0C"))
        val second = PlateObjectPaint(seam = layer(PaintMode.Seam, hexBits = "1C0D"))

        assertTrue(first.hasActivePaint)
        assertNotEquals(first.paintHash(), second.paintHash())
    }

    @Test
    fun emptyOrStalePaintDoesNotProduceActiveHash() {
        val empty = PlateObjectPaint()
        val stale = PlateObjectPaint(
            support = layer(PaintMode.Support, hexBits = "1C0C").markStale("changed")
        )

        assertFalse(empty.hasActivePaint)
        assertEquals("", empty.paintHash())
        assertFalse(stale.hasActivePaint)
        assertEquals("", stale.paintHash())
    }

    @Test
    fun fingerprintMismatchMarksLayerStale() {
        val paint = PlateObjectPaint(seam = layer(PaintMode.Seam, hexBits = "1C0C"))
        val mismatched = fingerprint(sample = "different")

        val validated = paint.validatedAgainst(mismatched)

        assertTrue(validated.seam?.isStale == true)
        assertEquals("Paint unavailable because the source mesh changed.", validated.seam?.staleReason)
    }

    @Test
    fun removingReferencedColorSlotMarksColorPaintStale() {
        val paint = PlateObjectPaint(
            color = layer(PaintMode.Color, hexBits = "2C0C", referencedSlots = setOf(1, 3))
        )

        val updated = paint.invalidatingColorForRemovedSlot(3)

        assertTrue(updated.color?.isStale == true)
        assertEquals("Color paint references a removed filament slot.", updated.color?.staleReason)
    }

    @Test
    fun removingEarlierColorSlotMarksShiftedColorReferencesStale() {
        val paint = PlateObjectPaint(
            color = layer(PaintMode.Color, hexBits = "2C0C", referencedSlots = setOf(1, 3))
        )

        val updated = paint.invalidatingColorForRemovedSlot(2)

        assertTrue(updated.color?.isStale == true)
        assertEquals("Color paint references shifted filament slots and needs native remapping.", updated.color?.staleReason)
        assertEquals(setOf(1, 3), updated.color?.referencedSlotIndexes)
    }

    @Test
    fun removingLaterColorSlotLeavesEarlierReferencesActive() {
        val paint = PlateObjectPaint(
            color = layer(PaintMode.Color, hexBits = "2C0C", referencedSlots = setOf(1, 2))
        )

        val updated = paint.invalidatingColorForRemovedSlot(3)

        assertFalse(updated.color?.isStale == true)
        assertEquals(setOf(1, 2), updated.color?.referencedSlotIndexes)
    }

    @Test
    fun removingSlotMarksColorPaintWithoutSlotMetadataStale() {
        val paint = PlateObjectPaint(
            color = layer(PaintMode.Color, hexBits = "2C0C", referencedSlots = emptySet())
        )

        val updated = paint.invalidatingColorForRemovedSlot(2)

        assertTrue(updated.color?.isStale == true)
        assertEquals("Color paint slot references are unavailable after filament slot deletion.", updated.color?.staleReason)
    }

    @Test
    fun nativePayloadCarriesPerVolumeNativeFingerprint() {
        val paint = PlateObjectPaint(
            seam = layer(
                mode = PaintMode.Seam,
                hexBits = "1C0C",
                nativeMeshFingerprint = "fnv1a64:abcd"
            )
        )
        val objectOnPlate = PlateObject(
            id = 42L,
            label = "Cube",
            filePath = "/tmp/cube.stl",
            nativeSourceKey = "cube-source",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            paint = paint
        )

        val json = JSONObject(nativePaintPayloadJson(listOf(objectOnPlate)))
        val volume = json.getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)
            .getJSONArray("volumes")
            .getJSONObject(0)

        assertEquals("fnv1a64:abcd", volume.getString("meshFingerprint"))
    }

    @Test
    fun stalePaintIsSkippedFromNativeReplayPayload() {
        val objectOnPlate = plateObject(
            paint = PlateObjectPaint(
                seam = layer(PaintMode.Seam, hexBits = "1C0C").markStale("source changed")
            )
        )

        assertEquals("", objectOnPlate.paint.paintHash())
        assertEquals("", nativePaintPayloadJson(listOf(objectOnPlate)))
    }

    @Test
    fun nativeSessionPayloadConvertsToPlateObjectPaintLayer() {
        val objectOnPlate = plateObject()
        val payload = """
            {
              "schemaVersion": 1,
              "mode": "Seam",
              "mobileObjectId": 42,
              "volumes": [
                {
                  "volumeIndex": 0,
                  "meshFingerprint": "fnv1a64:abcd",
                  "triangles": [
                    {"triangleIndex": 2, "hexBits": "1c0c"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val commit = checkNotNull(
            parseNativePaintPayloadCommit(
                payloadJson = payload,
                fallbackObject = objectOnPlate,
                fallbackMode = PaintMode.Support
            )
        )
        val paint = PlateObjectPaint().withCommittedNativeLayers(commit.layers)

        assertEquals(42L, commit.mobileObjectId)
        assertEquals("fnv1a64:abcd", paint.seam?.volumeLayers?.single()?.nativeMeshFingerprint)
        assertEquals("1C0C", paint.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
    }

    @Test
    fun nativePayloadCommitUpdatesOnlyMatchingPlateObject() {
        val first = plateObject(id = 41L)
        val second = plateObject(id = 42L)
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 42,
                  "layers": [
                    {
                      "mode": "Seam",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 4,
                          "meshFingerprint": "fnv1a64:abcd",
                          "triangles": [
                            {"triangleIndex": 2, "hexBits": "1C0C"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = commitNativePaintPayloadToPlateObjects(
            objects = listOf(first, second),
            objectId = 42L,
            mode = PaintMode.Support,
            payloadJson = payload
        )

        assertTrue(result.changed)
        assertFalse(result.objects.first { it.id == 41L }.paint.hasAnyPaintPayload)
        val committedPaint = result.objects.first { it.id == 42L }.paint
        assertEquals("1C0C", committedPaint.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
        assertEquals("fnv1a64:abcd", committedPaint.seam?.volumeLayers?.single()?.nativeMeshFingerprint)
    }

    @Test
    fun emptyNativeLayerCommitClearsExistingModePaint() {
        val objectOnPlate = plateObject(
            paint = PlateObjectPaint(seam = layer(PaintMode.Seam, hexBits = "1C0C"))
        )
        val payload = """
            {
              "schemaVersion": 1,
              "mode": "Seam",
              "mobileObjectId": 42,
              "volumes": []
            }
        """.trimIndent()

        val result = commitNativePaintPayloadToPlateObjects(
            objects = listOf(objectOnPlate),
            objectId = 42L,
            mode = PaintMode.Seam,
            payloadJson = payload
        )

        assertTrue(result.changed)
        assertFalse(result.objects.single().paint.hasAnyPaintPayload)
        assertEquals("", nativePaintPayloadJson(result.objects))
    }

    @Test
    fun committedNativePayloadEmitsReplayCompatibleJson() {
        val objectOnPlate = plateObject()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 42,
                  "layers": [
                    {
                      "mode": "Support",
                      "volumes": [
                        {
                          "volumeIndex": 1,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:ef01",
                          "triangles": [
                            {"triangleIndex": 7, "hexBits": "2A"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = commitNativePaintPayloadToPlateObjects(
            objects = listOf(objectOnPlate),
            objectId = 42L,
            mode = PaintMode.Seam,
            payloadJson = payload
        )
        val replayJson = JSONObject(nativePaintPayloadJson(result.objects))
        val replayVolume = replayJson.getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)
            .getJSONArray("volumes")
            .getJSONObject(0)

        assertEquals(42L, replayJson.getJSONArray("objects").getJSONObject(0).getLong("mobileObjectId"))
        assertEquals("Support", replayJson.getJSONArray("objects").getJSONObject(0).getJSONArray("layers").getJSONObject(0).getString("mode"))
        assertEquals(12, replayVolume.getInt("triangleCount"))
        assertEquals("fnv1a64:ef01", replayVolume.getString("meshFingerprint"))
        assertEquals(7, replayVolume.getJSONArray("triangles").getJSONObject(0).getInt("triangleIndex"))
    }

    @Test
    fun replayPayloadConvertsToPlateObjectPaintLayer() {
        val objectOnPlate = plateObject()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 42,
                  "layers": [
                    {
                      "mode": "Support",
                      "volumes": [
                        {
                          "volumeIndex": 1,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:ef01",
                          "triangles": [
                            {"triangleIndex": 7, "hexBits": "2A"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val commit = checkNotNull(
            parseNativePaintPayloadCommit(
                payloadJson = payload,
                fallbackObject = objectOnPlate,
                fallbackMode = PaintMode.Seam
            )
        )
        val paint = PlateObjectPaint().withCommittedNativeLayers(commit.layers)

        assertEquals(12, paint.support?.volumeLayers?.single()?.triangleCount)
        assertEquals("fnv1a64:ef01", paint.support?.volumeLayers?.single()?.nativeMeshFingerprint)
        assertEquals(7, paint.support?.volumeLayers?.single()?.serializedTriangles?.single()?.triangleIndex)
    }

    @Test
    fun nativeColorSlotsAreStoredAndReEmittedCanonically() {
        val objectOnPlate = plateObject()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 42,
                  "layers": [
                    {
                      "mode": "Color",
                      "colorSlots": [2, 4],
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:ef01",
                          "triangles": [
                            {"triangleIndex": 7, "hexBits": "2A"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val commit = checkNotNull(
            parseNativePaintPayloadCommit(
                payloadJson = payload,
                fallbackObject = objectOnPlate,
                fallbackMode = PaintMode.Support
            )
        )
        val paint = PlateObjectPaint().withCommittedNativeLayers(commit.layers)

        assertEquals(setOf(2, 4), paint.color?.referencedSlotIndexes)
        val replayLayer = JSONObject(nativePaintPayloadJson(listOf(objectOnPlate.copy(paint = paint))))
            .getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)

        assertFalse(replayLayer.has("referencedSlotIndexes"))
        assertEquals(2, replayLayer.getJSONArray("colorSlots").getInt(0))
        assertEquals(4, replayLayer.getJSONArray("colorSlots").getInt(1))
    }

    @Test
    fun legacyReferencedSlotIndexesPayloadEmitsCanonicalColorSlots() {
        val objectOnPlate = plateObject()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 42,
                  "layers": [
                    {
                      "mode": "Color",
                      "referencedSlotIndexes": [3],
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:ef01",
                          "triangles": [
                            {"triangleIndex": 7, "hexBits": "2A"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val commit = checkNotNull(
            parseNativePaintPayloadCommit(
                payloadJson = payload,
                fallbackObject = objectOnPlate,
                fallbackMode = PaintMode.Support
            )
        )
        val paint = PlateObjectPaint().withCommittedNativeLayers(commit.layers)
        val replayLayer = JSONObject(nativePaintPayloadJson(listOf(objectOnPlate.copy(paint = paint))))
            .getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)

        assertEquals(setOf(3), paint.color?.referencedSlotIndexes)
        assertFalse(replayLayer.has("referencedSlotIndexes"))
        assertEquals(3, replayLayer.getJSONArray("colorSlots").getInt(0))
    }

    private fun layer(
        mode: PaintMode,
        hexBits: String,
        referencedSlots: Set<Int> = emptySet(),
        nativeMeshFingerprint: String? = null
    ): SerializedPaintLayer =
        SerializedPaintLayer(
            mode = mode,
            objectSourceKey = "object.stl",
            meshFingerprint = fingerprint(),
            referencedSlotIndexes = referencedSlots,
            volumeLayers = listOf(
                SerializedPaintVolumeLayer(
                    volumeIndex = 0,
                    triangleCount = 4,
                    serializedTriangles = listOf(
                        SerializedPaintTriangle(triangleIndex = 2, hexBits = hexBits)
                    ),
                    nativeMeshFingerprint = nativeMeshFingerprint
                )
            )
        )

    private fun plateObject(
        id: Long = 42L,
        paint: PlateObjectPaint = PlateObjectPaint()
    ): PlateObject =
        PlateObject(
            id = id,
            label = "Cube",
            filePath = "/tmp/cube.stl",
            nativeSourceKey = "cube-source",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            paint = paint
        )

    private fun fingerprint(sample: String = "abc"): SourceMeshFingerprint =
        SourceMeshFingerprint(
            fileLengthBytes = 100L,
            lastModifiedEpochMs = 200L,
            triangleCount = 4,
            bounds = MeshBounds(0f, 0f, 0f, 1f, 1f, 1f),
            sampleSha256 = sample
        )
}
