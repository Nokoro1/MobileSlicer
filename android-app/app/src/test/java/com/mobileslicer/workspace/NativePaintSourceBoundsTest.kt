package com.mobileslicer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativePaintSourceBoundsTest {
    @Test
    fun parsesFirstVolumeBoundsForRequestedObject() {
        val bounds = parseNativePaintSourceBoundsJson(
            boundsJson = """
                {
                  "schemaVersion": 1,
                  "mobileObjectId": 42,
                  "volumeBounds": [
                    {"minX": 1.5, "minY": 2.5, "minZ": 3.5, "maxX": 11.0, "maxY": 22.0, "maxZ": 33.0},
                    {"minX": 100.0, "minY": 200.0, "minZ": 300.0, "maxX": 400.0, "maxY": 500.0, "maxZ": 600.0}
                  ]
                }
            """.trimIndent(),
            mobileObjectId = 42L
        )

        requireNotNull(bounds)
        assertEquals(1.5f, bounds.minX, 0.0001f)
        assertEquals(2.5f, bounds.minY, 0.0001f)
        assertEquals(3.5f, bounds.minZ, 0.0001f)
        assertEquals(11.0f, bounds.maxX, 0.0001f)
        assertEquals(22.0f, bounds.maxY, 0.0001f)
        assertEquals(33.0f, bounds.maxZ, 0.0001f)
    }

    @Test
    fun rejectsMismatchedObjectId() {
        assertNull(
            parseNativePaintSourceBoundsJson(
                boundsJson = """{"schemaVersion":1,"mobileObjectId":7,"volumeBounds":[{"minX":0,"minY":0,"minZ":0,"maxX":1,"maxY":1,"maxZ":1}]}""",
                mobileObjectId = 42L
            )
        )
    }

    @Test
    fun returnsNullForMissingBounds() {
        assertNull(parseNativePaintSourceBoundsJson("""{"schemaVersion":1,"mobileObjectId":42}""", 42L))
        assertNull(parseNativePaintSourceBoundsJson("""{"schemaVersion":1,"mobileObjectId":42,"volumeBounds":[]}""", 42L))
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertNull(parseNativePaintSourceBoundsJson("not-json", 42L))
        assertNull(parseNativePaintSourceBoundsJson(null, 42L))
    }
}
