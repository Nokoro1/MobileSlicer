package com.mobileslicer

import android.content.SharedPreferences
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectPlateObject
import com.mobileslicer.storage.SavedProjectRepository
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.commitNativePaintPayloadToPlateObjects
import com.mobileslicer.workspace.nativePaintPayloadJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSmokeWorkflowContractTest {
    @Test
    fun releaseWorkflowPlansImportSliceExportUploadAndPaintRestoreBehavior() {
        val store = defaultStore()
        val configuration = store.activeConfiguration()
        val importApplication = planModelImportApplication(
            result = ModelLoadResult(
                message = "Model loaded\ncube.stl",
                loaded = true,
                stagedFilePath = "/tmp/cube.stl",
                format = ImportedModelFormat.Stl,
                bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f)
            ),
            currentScreen = AppScreen.Home,
            existingPlateObjects = emptyList(),
            appendRequested = false,
            nextPlateObjectId = 1L,
            defaultTransform = { ViewerModelTransform(centerXmm = 110f, centerYmm = 110f) }
        )
        val objectOnPlate = importApplication.importedPlateObject ?: error("STL import did not create a plate object")

        assertTrue(importApplication.replacePlate)
        assertTrue(importApplication.clearSavedProject)
        assertEquals(AppScreen.Workspace, planModelImportCompletionUi(importApplication).screen)

        val sliceStart = planModelLoaderSliceStart(
            modelLoaded = true,
            sliceInProgress = false,
            sendToPrinterInProgress = false,
            generatedFootprintFits = true,
            printableVolumePreflightFailure = null,
            nativeSliceTitle = "Fixture Printer / PLA / 0.20mm"
        ) as ModelLoaderSliceStartPlan.Start
        assertTrue(sliceStart.statusMessage.contains("Fixture Printer / PLA / 0.20mm"))

        val runInputs = captureModelLoaderSliceRunInputs(
            configuration = configuration,
            calibrationJob = null,
            plateObjects = listOf(objectOnPlate),
            profileFilaments = store.filaments,
            plateFilamentSlots = emptyList(),
            fallbackFilament = configuration.filament,
            flushVolumes = null,
            primeTowerPlacementOverride = null,
            printer = configuration.printer,
            modelFilePath = objectOnPlate.filePath,
            preparedMesh = null,
            modelBounds = objectOnPlate.bounds,
            modelTransform = objectOnPlate.transform,
            gcodeFileName = "cube.gcode"
        )
        assertEquals(listOf(objectOnPlate), runInputs.plateObjects)
        assertEquals(1, runInputs.activePlateSlots.single().index)

        val completion = planModelLoaderSliceCompletion(
            result = SliceResult(
                message = "Slice successful",
                sliced = true,
                gcodeFilePath = "/tmp/cube.gcode",
                fileName = "cube_PLA.gcode"
            ),
            calibrationJob = null,
            fallbackFileName = "fallback.gcode",
            previousPreviewKey = 3L
        )
        assertEquals("/tmp/cube.gcode", completion.gcodeFilePath)
        assertEquals(4L, completion.previewKey)

        val exportAction = planGcodeFileAction(
            gcodeFilePath = completion.gcodeFilePath,
            calibrationJob = null,
            plateObjects = listOf(objectOnPlate),
            summary = completion.summary,
            filamentMaterial = "PLA",
            fallbackName = "fallback.gcode"
        )
        assertEquals("cube_PLA.gcode", exportAction?.fileName)

        val uploadRequest = planPrinterUploadRequest(
            gcodeFilePath = completion.gcodeFilePath,
            sendToPrinterInProgress = false,
            calibrationJob = null,
            remoteFileName = exportAction?.fileName ?: "cube.gcode",
            printerProfile = configuration.printer,
            uploadAction = PrinterUploadAction.UploadAndStart
        )
        assertNotNull(uploadRequest)
        assertEquals(PrinterUploadAction.UploadAndStart, uploadRequest?.uploadAction)

        val paintedObject = commitNativePaintPayloadToPlateObjects(
            objects = listOf(objectOnPlate),
            objectId = objectOnPlate.id,
            mode = PaintMode.Support,
            payloadJson = nativePaintPayload(objectOnPlate.id)
        ).committedObject ?: error("native paint payload did not commit")
        val preferences = FakeSharedPreferences()
        SavedProjectRepository.persist(
            preferences,
            listOf(
                SavedProject(
                    id = "release-smoke",
                    name = "Release Smoke",
                    updatedAtEpochMs = 1L,
                    profileStore = store,
                    plateObjects = listOf(
                        SavedProjectPlateObject(
                            label = paintedObject.label,
                            filePath = paintedObject.filePath,
                            nativeSourceKey = paintedObject.nativeSourceKey,
                            filamentSlotIndex = paintedObject.filamentSlotIndex,
                            format = paintedObject.format,
                            bounds = paintedObject.bounds,
                            transform = paintedObject.transform,
                            paint = paintedObject.paint
                        )
                    )
                )
            )
        )

        val restoredObject = SavedProjectRepository.load(preferences).single().plateObjects.single()
        val replayJson = nativePaintPayloadJson(
            listOf(
                PlateObject(
                    id = objectOnPlate.id,
                    label = restoredObject.label,
                    filePath = restoredObject.filePath,
                    nativeSourceKey = restoredObject.nativeSourceKey,
                    filamentSlotIndex = restoredObject.filamentSlotIndex,
                    format = restoredObject.format,
                    importTiming = null,
                    bounds = restoredObject.bounds,
                    transform = restoredObject.transform,
                    paint = restoredObject.paint
                )
            )
        )
        val replayLayer = JSONObject(replayJson)
            .getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)
        assertEquals(PaintMode.Support.name, replayLayer.getString("mode"))
        assertEquals("fnv1a64:release", replayLayer.getJSONArray("volumes").getJSONObject(0).getString("meshFingerprint"))
    }

    private fun nativePaintPayload(objectId: Long): String =
        """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": $objectId,
                  "layers": [
                    {
                      "mode": "Support",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:release",
                          "triangles": [
                            {"triangleIndex": 3, "hexBits": "2A"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

    private fun defaultStore(): ProfileStore {
        val printers = listOf(ProfileStoreRepository.fallbackPrinterProfile())
        val filaments = ProfileStoreRepository.defaultFilamentProfiles()
        val processes = listOf(
            newProcessProfileUnchecked(
                0 to "process_release_smoke",
                1 to "Release Smoke Process",
                3 to false,
                5 to 0.20f,
                259 to printers.first().id,
                261 to printers.first().nozzleDiameterMm
            )
        )
        return ProfileStore(
            printers = printers,
            filaments = filaments,
            processes = processes,
            selectedPrinterId = printers.first().id,
            selectedFilamentId = filaments.first().id,
            selectedProcessId = processes.first().id
        )
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, String?>()

        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = FakeEditor(values)
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class FakeEditor(
        private val values: MutableMap<String, String?>
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = null
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }
}
