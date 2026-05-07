package com.mobileslicer.storage

import android.content.SharedPreferences
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.commitNativePaintPayloadToPlateObjects
import com.mobileslicer.workspace.nativePaintPayloadJson
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PaintFullAppPathProofTest {
    @Test
    fun nativePayloadSurvivesSavedProjectAndWritesReplayPayloadForNativeProof() {
        val payloadPath = System.getenv("PAINT_PROOF_PAYLOAD_PATH").orEmpty()
        val replayOutputPath = System.getenv("PAINT_PROOF_REPLAY_OUTPUT_PATH").orEmpty()
        val mode = System.getenv("PAINT_PROOF_MODE").orEmpty().toPaintModeOrNull()
        assumeTrue("PAINT_PROOF_* env vars not set", payloadPath.isNotBlank() && replayOutputPath.isNotBlank() && mode != null)

        val nativePayload = File(payloadPath).readText()
        val objectId = nativePayload.mobileObjectIdOrDefault(42L)
        val plateObject = PlateObject(
            id = objectId,
            label = "Paint proof",
            filePath = System.getenv("PAINT_PROOF_MODEL_PATH").orEmpty().ifBlank { "/tmp/paint-proof.stl" },
            nativeSourceKey = "paint-proof-source",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = MeshBounds(0f, 0f, 0f, 20f, 20f, 20f),
            transform = ViewerModelTransform(centerXmm = 110f, centerYmm = 110f)
        )
        val committed = commitNativePaintPayloadToPlateObjects(
            objects = listOf(plateObject),
            objectId = plateObject.id,
            mode = mode!!,
            payloadJson = nativePayload
        ).committedObject ?: error("native payload did not commit into PlateObject.paint")
        assertTrue(committed.paint.hasActivePaint)

        val preferences = FakeSharedPreferences()
        SavedProjectRepository.persist(
            preferences,
            listOf(
                SavedProject(
                    id = "paint_full_app_path_proof",
                    name = "Paint Full App Path Proof",
                    updatedAtEpochMs = 123L,
                    profileStore = defaultStore(),
                    plateObjects = listOf(
                        SavedProjectPlateObject(
                            label = committed.label,
                            filePath = committed.filePath,
                            nativeSourceKey = committed.nativeSourceKey,
                            filamentSlotIndex = committed.filamentSlotIndex,
                            format = committed.format,
                            bounds = committed.bounds,
                            transform = committed.transform,
                            paint = committed.paint
                        )
                    )
                )
            )
        )

        val loaded = SavedProjectRepository.load(preferences).single().plateObjects.single()
        val replayObject = PlateObject(
            id = objectId,
            label = loaded.label,
            filePath = loaded.filePath,
            nativeSourceKey = loaded.nativeSourceKey,
            filamentSlotIndex = loaded.filamentSlotIndex,
            format = loaded.format,
            importTiming = null,
            bounds = loaded.bounds,
            transform = loaded.transform,
            paint = loaded.paint
        )
        val replayJson = nativePaintPayloadJson(listOf(replayObject))
        val replayRoot = JSONObject(replayJson)
        val replayLayer = replayRoot.getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)
        assertEquals(mode.name, replayLayer.getString("mode"))
        assertTrue(replayLayer.getJSONArray("volumes").getJSONObject(0).getJSONArray("triangles").length() > 0)

        File(replayOutputPath).apply {
            parentFile?.mkdirs()
            writeText(replayJson)
        }
    }

    private fun String.toPaintModeOrNull(): PaintMode? =
        PaintMode.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    private fun String.mobileObjectIdOrDefault(default: Long): Long =
        runCatching {
            val root = JSONObject(this)
            when {
                root.has("mobileObjectId") -> root.getLong("mobileObjectId")
                root.has("objects") && root.getJSONArray("objects").length() > 0 ->
                    root.getJSONArray("objects").getJSONObject(0).getLong("mobileObjectId")
                else -> default
            }
        }.getOrDefault(default)

    private fun defaultStore(): ProfileStore {
        val printers = listOf(ProfileStoreRepository.fallbackPrinterProfile())
        val filaments = ProfileStoreRepository.defaultFilamentProfiles()
        val processes = listOf(
            newProcessProfileUnchecked(
                0 to "process_paint_full_app_path_proof",
                1 to "Paint Full App Path Proof Process",
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
        private val values = mutableMapOf<String, String>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
        }
    }
}
