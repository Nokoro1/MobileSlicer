package com.mobileslicer.automation

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.isSuccess
import com.mobileslicer.workspace.NativeModelTransform
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.StlMeshParser
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class AutomationSlicePaths(
    val modelPath: String,
    val outputPath: String,
    val statusPath: String
)

internal data class AutomationSliceRequest(
    val intent: Intent,
    val modelPath: String,
    val outputPath: String,
    val statusPath: String
) {
    companion object {
        const val ACTION_AUTOMATE_SLICE = "com.mobileslicer.action.AUTOMATE_SLICE"
        const val EXTRA_MODEL_PATH = "automation_model_path"
        const val EXTRA_OUTPUT_PATH = "automation_output_path"
        const val EXTRA_STATUS_PATH = "automation_status_path"

        fun fromIntent(intent: Intent): AutomationSliceRequest? {
            val paths = pathsFromValues(
                action = intent.action,
                modelPath = intent.getStringExtra(EXTRA_MODEL_PATH),
                outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH),
                statusPath = intent.getStringExtra(EXTRA_STATUS_PATH)
            ) ?: return null
            return AutomationSliceRequest(
                intent = intent,
                modelPath = paths.modelPath,
                outputPath = paths.outputPath,
                statusPath = paths.statusPath
            )
        }

        fun pathsFromValues(
            action: String?,
            modelPath: String?,
            outputPath: String?,
            statusPath: String?
        ): AutomationSlicePaths? {
            if (action != ACTION_AUTOMATE_SLICE || modelPath.isNullOrBlank() || outputPath.isNullOrBlank()) {
                return null
            }
            return AutomationSlicePaths(
                modelPath = modelPath,
                outputPath = outputPath,
                statusPath = statusPath ?: "$outputPath.status.txt"
            )
        }
    }
}

internal data class AutomationSliceTiming(
    val stagingMs: Long,
    val nativeLoadMs: Long,
    val placementMs: Long,
    val configMs: Long,
    val nativeSliceMs: Long,
    val writeGcodeMs: Long,
    val totalMs: Long
)

internal fun automationSliceSuccessStatus(
    modelFile: File,
    stagedModel: File,
    outputFile: File,
    timing: AutomationSliceTiming,
    configJson: String
): String =
    "success: model=${modelFile.absolutePath} " +
        "staged=${stagedModel.absolutePath} " +
        "output=${outputFile.absolutePath} " +
        "bytes=${outputFile.length()} " +
        "stagingMs=${timing.stagingMs} " +
        "nativeLoadMs=${timing.nativeLoadMs} " +
        "placementMs=${timing.placementMs} " +
        "configMs=${timing.configMs} " +
        "nativeSliceMs=${timing.nativeSliceMs} " +
        "writeGcodeMs=${timing.writeGcodeMs} " +
        "elapsedMs=${timing.totalMs} " +
        "config=$configJson"

internal class AutomationSliceRunner(
    private val ensureEngine: () -> Long,
    private val stageModelFile: (File) -> File?,
    private val resolveConfigJson: (Intent) -> String,
    private val onModelLoaded: (File) -> Unit,
    private val onModelLoadRejected: () -> Unit,
    private val nativeSliceFailureStatus: () -> String
) {
    fun run(request: AutomationSliceRequest): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val modelFile = File(request.modelPath)
        val outputFile = File(request.outputPath)
        val statusFile = File(request.statusPath)

        fun writeStatus(message: String) {
            Log.i(TAG, "automation:$message")
            statusFile.parentFile?.mkdirs()
            statusFile.writeText(message, StandardCharsets.UTF_8)
        }

        val engineHandle = NativeEngineHandle.fromRaw(ensureEngine())
        if (engineHandle == null) {
            writeStatus("failed: engine unavailable")
            return false
        }
        if (!modelFile.exists()) {
            writeStatus("failed: model not found path=${request.modelPath}")
            return false
        }
        val stagingStartedAt = SystemClock.elapsedRealtime()
        val stagedModel = stageModelFile(modelFile)
        if (stagedModel == null) {
            writeStatus("failed: unable to stage model path=${request.modelPath}")
            return false
        }
        val stagingMs = SystemClock.elapsedRealtime() - stagingStartedAt
        val nativeLoadStartedAt = SystemClock.elapsedRealtime()
        val loadResult = NativeEngineCalls.loadModel(engineHandle, stagedModel.absolutePath)
        if (loadResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${loadResult.statusMessage} path=${request.modelPath} staged=${stagedModel.absolutePath}")
            onModelLoadRejected()
            return false
        }
        val nativeLoadMs = SystemClock.elapsedRealtime() - nativeLoadStartedAt
        onModelLoaded(stagedModel)

        val configJson = runCatching { resolveConfigJson(request.intent) }.getOrElse { exception ->
            Log.e(TAG, "automation:failed config resolution", exception)
            writeStatus("failed: config resolution ${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
            return false
        }
        val placementStartedAt = SystemClock.elapsedRealtime()
        val placement = automationNativeTransform(stagedModel, configJson)
        if (
            placement != null &&
            !NativeEngineCalls.setModelTransform(
                engineHandle,
                placement.xMm,
                placement.yMm,
                placement.zMm,
                placement.rotationXRadians,
                placement.rotationYRadians,
                placement.rotationZRadians,
                placement.uniformScale
            ).isSuccess()
        ) {
            writeStatus("failed: nativeSetModelTransform rejected staged=${stagedModel.absolutePath}")
            return false
        }
        val placementMs = SystemClock.elapsedRealtime() - placementStartedAt

        val configStartedAt = SystemClock.elapsedRealtime()
        val configResult = NativeEngineCalls.setConfigJson(engineHandle, configJson)
        if (configResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${configResult.statusMessage} config=$configJson")
            return false
        }
        val configMs = SystemClock.elapsedRealtime() - configStartedAt

        val nativeSliceStartedAt = SystemClock.elapsedRealtime()
        var sliceResult = NativeEngineCalls.slice(engineHandle)
        if (
            sliceResult !is NativeEngineCallResult.Success &&
            (sliceResult as? NativeEngineCallResult.Failure)?.error?.message.equals("vector", ignoreCase = true)
        ) {
            Log.w(TAG, "automation:native slice failed with vector; retrying full native slice once on same engine")
            sliceResult = NativeEngineCalls.slice(engineHandle)
        }
        val nativeSliceMs = SystemClock.elapsedRealtime() - nativeSliceStartedAt
        if (sliceResult !is NativeEngineCallResult.Success) {
            writeStatus(
                "failed: ${sliceResult.statusMessage} fallback=${nativeSliceFailureStatus()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt} config=$configJson"
            )
            return false
        }

        outputFile.parentFile?.mkdirs()
        val writeGcodeStartedAt = SystemClock.elapsedRealtime()
        val writeResult = NativeEngineCalls.writeGcodeToFile(engineHandle, outputFile.absolutePath)
        val writeGcodeMs = SystemClock.elapsedRealtime() - writeGcodeStartedAt
        if (writeResult !is NativeEngineCallResult.Success) {
            writeStatus("failed: ${writeResult.statusMessage} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            writeStatus("failed: nativeWriteGcodeToFile produced empty output elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return false
        }
        writeStatus(
            automationSliceSuccessStatus(
                modelFile = modelFile,
                stagedModel = stagedModel,
                outputFile = outputFile,
                timing = AutomationSliceTiming(
                    stagingMs = stagingMs,
                    nativeLoadMs = nativeLoadMs,
                    placementMs = placementMs,
                    configMs = configMs,
                    nativeSliceMs = nativeSliceMs,
                    writeGcodeMs = writeGcodeMs,
                    totalMs = SystemClock.elapsedRealtime() - startedAt
                ),
                configJson = configJson
            )
        )
        return true
    }

    private fun automationNativeTransform(stagedModel: File, configJson: String): NativeModelTransform? {
        if (!stagedModel.name.lowercase(Locale.US).endsWith(".stl")) {
            return null
        }
        val bounds = runCatching { StlMeshParser.parseBounds(stagedModel) }.getOrNull() ?: return null
        return defaultNativeModelTransform(
            bounds = bounds,
            printerBed = automationPrinterBed(configJson),
            modelTransform = null
        )
    }

    private fun automationPrinterBed(configJson: String): PrinterBedSpec =
        PrinterBedSpec(
            widthMm = jsonFloat(configJson, "bed_width_mm") ?: 270f,
            depthMm = jsonFloat(configJson, "bed_depth_mm") ?: 270f,
            maxHeightMm = jsonFloat(configJson, "max_height_mm") ?: 256f
        )

    private fun jsonFloat(json: String, key: String): Float? =
        runCatching {
            val value = JSONObject(json).optDouble(key, Double.NaN)
            value.takeIf { it.isFinite() }?.toFloat()
        }.getOrNull()

    private companion object {
        private const val TAG = "MobileSlicer"
    }
}
