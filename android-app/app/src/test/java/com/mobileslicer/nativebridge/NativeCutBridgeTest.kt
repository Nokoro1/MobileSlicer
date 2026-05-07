package com.mobileslicer.nativebridge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeCutBridgeTest {
    @Test
    fun cutRequestJsonPreservesConnectorAndGrooveContract() {
        val connector = JSONObject()
            .put("id", "connector-0-dowel")
            .put("type", "dowel")
            .put("style", "prism")
            .put("shape", "hexagon")
            .put("position", JSONArray(listOf(1.0, 2.0, 3.0)))
            .put("rotationMatrix", JSONArray(List(16) { index -> if (index % 5 == 0) 1.0 else 0.0 }))
            .put("radius", 1.25)
            .put("height", 3.0)
            .put("radiusTolerance", 0.05)
            .put("heightTolerance", 0.1)
            .put("zAngle", 0.25)
        val request = NativeCutRequest(
            mobileObjectId = 7,
            cutMatrixRowMajor = DoubleArray(16) { index -> if (index % 5 == 0) 1.0 else 0.0 },
            mode = NativeCutMode.Groove,
            attributes = NativeCutAttributes(
                keepUpper = true,
                keepLower = true,
                keepAsParts = false,
                flipUpper = true,
                placeOnCutLower = true
            ),
            outputDirectory = "/tmp/cut",
            connectors = JSONArray().put(connector),
            groove = JSONObject()
                .put("depth", 2.0)
                .put("width", 8.0)
                .put("flapsAngleRadians", 1.1)
                .put("angleRadians", 0.2)
                .put("depthTolerance", 0.1)
                .put("widthTolerance", 0.2)
        )

        val json = JSONObject(request.toJsonString())

        assertEquals("groove", json.getString("mode"))
        assertEquals(16, json.getJSONArray("cutMatrix").length())
        assertEquals(true, json.getJSONObject("attributes").getBoolean("flipUpper"))
        assertEquals(true, json.getJSONObject("attributes").getBoolean("placeOnCutLower"))
        assertEquals("dowel", json.getJSONArray("connectors").getJSONObject(0).getString("type"))
        assertEquals("hexagon", json.getJSONArray("connectors").getJSONObject(0).getString("shape"))
        assertEquals(8.0, json.getJSONObject("groove").getDouble("width"), 0.0)
    }

    @Test
    fun parseNativeCutResultPreservesDowelRole() {
        val result = parseNativeCutResult(
            """
            {
              "schemaVersion": 1,
              "sourceMobileObjectId": 7,
              "cutGroupId": "cut-7-3",
              "objects": [
                {
                  "mobileObjectId": 8,
                  "label": "upper",
                  "role": "upper",
                  "filePath": "/tmp/upper.stl",
                  "volumeCount": 2,
                  "cutMetadata": {"id": 42, "checkSum": 3, "connectorsCount": 1}
                },
                {
                  "mobileObjectId": 9,
                  "label": "lower",
                  "role": "lower",
                  "filePath": "/tmp/lower.stl",
                  "volumeCount": 2
                },
                {
                  "mobileObjectId": 10,
                  "label": "dowel",
                  "role": "dowel",
                  "volumeCount": 1
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(7L, result.sourceMobileObjectId)
        assertEquals("cut-7-3", result.cutGroupId)
        assertEquals(listOf("upper", "lower", "dowel"), result.objects.map { it.role })
        assertEquals(10L, result.objects[2].mobileObjectId)
        assertNull(result.objects[2].filePath)
        assertEquals(42L, result.objects[0].cutMetadata?.id)
        assertEquals(3L, result.objects[0].cutMetadata?.checkSum)
        assertEquals(1L, result.objects[0].cutMetadata?.connectorsCount)
    }
}
