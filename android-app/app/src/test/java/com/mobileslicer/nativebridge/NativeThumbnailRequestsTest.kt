package com.mobileslicer.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeThumbnailRequestsTest {
    @Test
    fun parseNativeThumbnailRequestsJsonParsesValidRequests() {
        val summary = parseNativeThumbnailRequestsJson(
            """
            {
              "source": "orca",
              "thumbnails": "48x48/PNG,300x300/QOI,512x512/COLPIC",
              "thumbnailsFormat": "PNG",
              "hasErrors": false,
              "errors": {
                "invalidValue": false,
                "outOfRange": false,
                "invalidExtension": false
              },
              "errorText": "",
              "requests": [
                {"width": 48, "height": 48, "format": "PNG"},
                {"width": 300, "height": 300, "format": "QOI"},
                {"width": 512, "height": 512, "format": "COLPIC"}
              ]
            }
            """.trimIndent()
        )

        requireNotNull(summary)
        assertEquals("orca", summary.source)
        assertEquals("48x48/PNG,300x300/QOI,512x512/COLPIC", summary.thumbnails)
        assertEquals("PNG", summary.thumbnailsFormat)
        assertFalse(summary.hasErrors)
        assertEquals(
            listOf(
                NativeThumbnailRequest(48, 48, "PNG"),
                NativeThumbnailRequest(300, 300, "QOI"),
                NativeThumbnailRequest(512, 512, "COLPIC")
            ),
            summary.requests
        )
    }

    @Test
    fun parseNativeThumbnailRequestsJsonPreservesOrcaErrors() {
        val summary = parseNativeThumbnailRequestsJson(
            """
            {
              "source": "orca",
              "thumbnails": "1200x64/NOPE",
              "thumbnailsFormat": "",
              "hasErrors": true,
              "errors": {
                "invalidValue": false,
                "outOfRange": true,
                "invalidExtension": true
              },
              "errorText": "Invalid thumbnails value",
              "requests": []
            }
            """.trimIndent()
        )

        requireNotNull(summary)
        assertTrue(summary.hasErrors)
        assertFalse(summary.errors.invalidValue)
        assertTrue(summary.errors.outOfRange)
        assertTrue(summary.errors.invalidExtension)
        assertEquals("Invalid thumbnails value", summary.errorText)
        assertTrue(summary.requests.isEmpty())
    }

    @Test
    fun parseNativeThumbnailRequestsJsonDropsInvalidRequestRows() {
        val summary = parseNativeThumbnailRequestsJson(
            """
            {
              "requests": [
                {"width": 0, "height": 64, "format": "PNG"},
                {"width": 64, "height": 0, "format": "PNG"},
                {"width": 64, "height": 64, "format": ""},
                {"width": 96, "height": 96, "format": "JPG"}
              ]
            }
            """.trimIndent()
        )

        requireNotNull(summary)
        assertEquals(listOf(NativeThumbnailRequest(96, 96, "JPG")), summary.requests)
    }

    @Test
    fun parseNativeThumbnailRequestsJsonReturnsNullForBlankOrMalformedJson() {
        assertNull(parseNativeThumbnailRequestsJson(null))
        assertNull(parseNativeThumbnailRequestsJson(""))
        assertNull(parseNativeThumbnailRequestsJson("not-json"))
    }
}
