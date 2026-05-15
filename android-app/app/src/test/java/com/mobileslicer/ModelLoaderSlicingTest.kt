package com.mobileslicer

import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.newProcessProfileUnchecked
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectModifierMesh
import com.mobileslicer.workspace.PlateObjectProcessOverride
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderSlicingTest {
    @Test
    fun objectProcessOverridesAreInjectedIntoNativeConfigByPlateObjectIndex() {
        val process = newProcessProfileUnchecked(
            0 to "process_object_override",
            1 to "Object Override",
            3 to false,
            5 to 0.20f,
            259 to ProfileStoreRepository.fallbackPrinterProfile().id,
            261 to 0.4f,
            267 to """{"enable_support":true,"support_threshold_angle":35,"wall_loops":6,"only_one_wall_first_layer":true}"""
        )
        val objectWithOverride = plateObject(
            id = 42L,
            processOverride = PlateObjectProcessOverride(
                selectedProcessId = process.id,
                editedProcessProfile = process
            )
        )
        val config = JSONObject(
            applyObjectProcessOverridesToNativeConfig(
                configJson = """{"layer_height":0.2}""",
                plateObjects = listOf(plateObject(id = 41L), objectWithOverride),
                defaultProcess = process.copy(id = "workspace_process"),
                availableProcesses = listOf(process)
            )
        )

        val overrides = config.getJSONArray("mobile_slicer_object_process_overrides")
        val entry = overrides.getJSONObject(0)
        assertEquals(1, overrides.length())
        assertEquals(42L, entry.getLong("mobileObjectId"))
        assertEquals(1, entry.getInt("plateObjectIndex"))
        assertEquals(process.id, entry.getString("selectedProcessId"))
        assertTrue(entry.getJSONObject("config").getBoolean("enable_support"))
        assertEquals(35, entry.getJSONObject("config").getInt("support_threshold_angle"))
        assertEquals(6, entry.getJSONObject("config").getInt("wall_loops"))
        assertTrue(entry.getJSONObject("config").getBoolean("only_one_wall_first_layer"))
    }

    @Test
    fun selectedObjectProcessBaseIsInjectedWhenDifferentFromWorkspaceProcess() {
        val workspaceProcess = newProcessProfileUnchecked(
            0 to "workspace_process",
            1 to "Workspace Process",
            3 to false,
            5 to 0.20f,
            259 to ProfileStoreRepository.fallbackPrinterProfile().id,
            261 to 0.4f,
            267 to """{"wall_loops":2}"""
        )
        val objectProcess = newProcessProfileUnchecked(
            0 to "object_process_base",
            1 to "Object Process Base",
            3 to false,
            5 to 0.20f,
            259 to ProfileStoreRepository.fallbackPrinterProfile().id,
            261 to 0.4f,
            267 to """{"wall_loops":5}"""
        )
        val objectWithBase = plateObject(
            id = 52L,
            processOverride = PlateObjectProcessOverride(
                selectedProcessId = objectProcess.id,
                editedProcessProfile = null
            )
        )

        val config = JSONObject(
            applyObjectProcessOverridesToNativeConfig(
                configJson = """{"layer_height":0.2}""",
                plateObjects = listOf(objectWithBase),
                defaultProcess = workspaceProcess,
                availableProcesses = listOf(workspaceProcess, objectProcess)
            )
        )

        val entry = config.getJSONArray("mobile_slicer_object_process_overrides").getJSONObject(0)
        assertEquals(objectProcess.id, entry.getString("selectedProcessId"))
        assertEquals(5, entry.getJSONObject("config").getInt("wall_loops"))
    }

    @Test
    fun selectedObjectProcessBaseMatchingWorkspaceDoesNotEmitOverride() {
        val workspaceProcess = newProcessProfileUnchecked(
            0 to "workspace_process",
            1 to "Workspace Process",
            3 to false,
            5 to 0.20f,
            259 to ProfileStoreRepository.fallbackPrinterProfile().id,
            261 to 0.4f,
            267 to """{"wall_loops":2}"""
        )
        val objectWithBase = plateObject(
            id = 53L,
            processOverride = PlateObjectProcessOverride(
                selectedProcessId = workspaceProcess.id,
                editedProcessProfile = null
            )
        )

        val config = applyObjectProcessOverridesToNativeConfig(
            configJson = """{"layer_height":0.2}""",
            plateObjects = listOf(objectWithBase),
            defaultProcess = workspaceProcess,
            availableProcesses = listOf(workspaceProcess)
        )

        assertFalse(JSONObject(config).has("mobile_slicer_object_process_overrides"))
    }

    @Test
    fun objectProcessOverridesDoNotMutateNativeConfigWhenNoneExist() {
        val configJson = """{"layer_height":0.2}"""

        val config = applyObjectProcessOverridesToNativeConfig(
            configJson = configJson,
            plateObjects = listOf(plateObject(id = 1L))
        )

        assertEquals(configJson, config)
        assertFalse(JSONObject(config).has("mobile_slicer_object_process_overrides"))
    }

    @Test
    fun modifierProcessOverridesAreInjectedIntoNativeConfigByParentObjectIndex() {
        val process = newProcessProfileUnchecked(
            0 to "modifier_process_override",
            1 to "Modifier Override",
            3 to false,
            5 to 0.20f,
            259 to ProfileStoreRepository.fallbackPrinterProfile().id,
            261 to 0.4f,
            267 to """{"wall_loops":5,"sparse_infill_density":80}"""
        )
        val modifier = PlateObjectModifierMesh(
            id = 9001L,
            label = "Dense corner modifier",
            filePath = "/tmp/modifier.stl",
            bounds = MeshBounds(
                minX = 0f,
                minY = 0f,
                minZ = 0f,
                maxX = 10f,
                maxY = 10f,
                maxZ = 10f
            ),
            transform = ViewerModelTransform(
                centerXmm = 25f,
                centerYmm = 35f,
                zOffsetMm = 4f,
                rotationXDegrees = 10f,
                rotationYDegrees = 20f,
                rotationZDegrees = 90f,
                uniformScale = 2f
            ),
            processOverride = PlateObjectProcessOverride(
                selectedProcessId = process.id,
                editedProcessProfile = process
            )
        )

        val config = JSONObject(
            applyObjectProcessOverridesToNativeConfig(
                configJson = """{"layer_height":0.2}""",
                plateObjects = listOf(plateObject(id = 41L), plateObject(id = 42L, modifiers = listOf(modifier))),
                defaultProcess = process.copy(id = "workspace_process"),
                availableProcesses = listOf(process),
                printerBed = PrinterBedSpec(
                    originXmm = 0f,
                    originYmm = 0f,
                    widthMm = 270f,
                    depthMm = 270f,
                    maxHeightMm = 256f
                )
            )
        )

        assertFalse(config.has("mobile_slicer_object_process_overrides"))
        val overrides = config.getJSONArray("mobile_slicer_modifier_process_overrides")
        val entry = overrides.getJSONObject(0)
        assertEquals(1, overrides.length())
        assertEquals(42L, entry.getLong("mobileObjectId"))
        assertEquals(9001L, entry.getLong("modifierId"))
        assertEquals(1, entry.getInt("plateObjectIndex"))
        assertEquals("/tmp/modifier.stl", entry.getString("path"))
        assertEquals("Dense corner modifier", entry.getString("label"))
        assertEquals(process.id, entry.getString("selectedProcessId"))
        assertEquals(5, entry.getJSONObject("config").getInt("wall_loops"))
        assertEquals(80, entry.getJSONObject("config").getInt("sparse_infill_density"))
        val transform = entry.getJSONObject("transform")
        assertEquals(33.111596, transform.getDouble("xMm"), 0.0001)
        assertEquals(21.640921, transform.getDouble("yMm"), 0.0001)
        assertEquals(10.840403, transform.getDouble("zMm"), 0.0001)
        assertEquals(Math.toRadians(10.0), transform.getDouble("rotationXRadians"), 0.0001)
        assertEquals(Math.toRadians(20.0), transform.getDouble("rotationYRadians"), 0.0001)
        assertEquals(Math.PI / 2.0, transform.getDouble("rotationZRadians"), 0.0001)
        assertEquals(2.0, transform.getDouble("uniformScale"), 0.0001)
    }

    private fun plateObject(
        id: Long,
        processOverride: PlateObjectProcessOverride? = null,
        modifiers: List<PlateObjectModifierMesh> = emptyList()
    ): PlateObject =
        PlateObject(
            id = id,
            label = "Object $id",
            filePath = "/tmp/object-$id.stl",
            nativeSourceKey = "source-$id",
            format = ImportedModelFormat.Stl,
            importTiming = null,
            transform = ViewerModelTransform(centerXmm = 0f, centerYmm = 0f),
            processOverride = processOverride,
            modifiers = modifiers
        )
}
