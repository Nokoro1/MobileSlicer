package com.mobileslicer.workspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePaintRayContractTest {
    @Test
    fun workspacePaintPathUsesOnlyRayNativeCalls() {
        val workspacePaintSources = sourceDirectory("app/src/main/java/com/mobileslicer/workspace")
            .walkTopDown()
            .filter { it.isFile && it.name.startsWith("WorkspacePaint") && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(workspacePaintSources.contains("NativePaintCalls.strokeBeginRay"))
        assertTrue(workspacePaintSources.contains("NativePaintCalls.strokeMoveRay"))
        assertFalse(workspacePaintSources.contains("NativePaintCalls.hitTestRay"))
        assertFalse(workspacePaintSources.contains("NativePaintCalls.hitTest("))
        assertFalse(workspacePaintSources.contains("NativePaintCalls.strokeBegin(handle"))
        assertFalse(workspacePaintSources.contains("NativePaintCalls.strokeMove(handle"))
    }

    @Test
    fun kotlinBridgeDoesNotExposeScreenCoordinatePaintWrappers() {
        val bridge = sourceFile("app/src/main/java/com/mobileslicer/nativebridge/NativePaintBridge.kt").readText()
        val engineBridge = sourceFile("app/src/main/java/com/mobileslicer/nativebridge/NativeEngineBridge.kt").readText()

        assertFalse(bridge.contains("fun hitTest(handle: NativeEngineHandle, screenX"))
        assertFalse(bridge.contains("fun strokeBegin(handle: NativeEngineHandle, screenX"))
        assertFalse(bridge.contains("fun strokeMove(handle: NativeEngineHandle, screenX"))
        assertFalse(engineBridge.contains("nativePaintHitTest(handle: Long, screenX"))
        assertFalse(engineBridge.contains("nativePaintStrokeBegin(handle: Long, screenX"))
        assertFalse(engineBridge.contains("nativePaintStrokeMove(handle: Long, screenX"))
    }

    private fun sourceFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            listOf(
                File(dir, path),
                File(dir, "android-app/$path")
            ).firstOrNull { it.exists() }?.let { return it }
            dir = dir.parentFile
        }
        return File(path)
    }

    private fun sourceDirectory(path: String): File {
        val directory = sourceFile(path)
        require(directory.isDirectory) { "$path is not a directory" }
        return directory
    }
}
