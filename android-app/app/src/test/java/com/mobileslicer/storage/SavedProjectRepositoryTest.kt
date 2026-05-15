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
import com.mobileslicer.workspace.PlateObjectGeometrySource
import com.mobileslicer.workspace.PlateObjectModifierMesh
import com.mobileslicer.workspace.PlateObjectPaint
import com.mobileslicer.workspace.PlateObjectProcessOverride
import com.mobileslicer.workspace.PlateProfileState
import com.mobileslicer.workspace.SerializedPaintLayer
import com.mobileslicer.workspace.SerializedPaintTriangle
import com.mobileslicer.workspace.SerializedPaintVolumeLayer
import com.mobileslicer.workspace.SourceMeshFingerprint
import com.mobileslicer.workspace.ThreeMfProjectMetadata
import com.mobileslicer.workspace.ThreeMfObjectFilamentAssignment
import com.mobileslicer.workspace.commitNativePaintPayloadToPlateObjects
import com.mobileslicer.workspace.nativePaintPayloadJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedProjectRepositoryTest {
    @Test
    fun persistAndLoadPreservesFullObjectTransform() {
        val preferences = FakeSharedPreferences()
        val transform = ViewerModelTransform(
            centerXmm = 42.5f,
            centerYmm = 84.25f,
            zOffsetMm = 3.5f,
            rotationXDegrees = 12f,
            rotationYDegrees = 24f,
            rotationZDegrees = 36f,
            uniformScale = 1.25f,
            orientationMatrix = listOf(
                0f, -1f, 0f,
                1f, 0f, 0f,
                0f, 0f, 1f
            )
        )
        val project = savedProject(transform = transform)

        SavedProjectRepository.persist(preferences, listOf(project))

        val loaded = SavedProjectRepository.load(preferences).single()
        val loadedTransform = loaded.plateObjects.single().transform
        assertEquals(transform.centerXmm, loadedTransform.centerXmm, 0.0001f)
        assertEquals(transform.centerYmm, loadedTransform.centerYmm, 0.0001f)
        assertEquals(transform.zOffsetMm, loadedTransform.zOffsetMm, 0.0001f)
        assertEquals(transform.rotationXDegrees, loadedTransform.rotationXDegrees, 0.0001f)
        assertEquals(transform.rotationYDegrees, loadedTransform.rotationYDegrees, 0.0001f)
        assertEquals(transform.rotationZDegrees, loadedTransform.rotationZDegrees, 0.0001f)
        assertEquals(transform.uniformScale, loadedTransform.uniformScale, 0.0001f)
        assertEquals(transform.orientationMatrix, loadedTransform.orientationMatrix)
    }

    @Test
    fun persistAndLoadPreservesNativeProjectGraphPath() {
        val preferences = FakeSharedPreferences()
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            nativeProjectFilePath = "/tmp/native-project.3mf"
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loaded = SavedProjectRepository.load(preferences).single()
        assertEquals("/tmp/native-project.3mf", loaded.nativeProjectFilePath)
    }

    @Test
    fun persistAndLoadPreservesStepConversionSource() {
        val preferences = FakeSharedPreferences()
        val geometrySource = PlateObjectGeometrySource.StepMeshConvert(
            originalPath = "/tmp/cad-source.step",
            convertedStlPath = "/tmp/cad-source-step-mesh.stl",
            linearDeflection = 0.003,
            angleDeflection = 0.5
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            geometrySource = geometrySource
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedSource = SavedProjectRepository.load(preferences).single().plateObjects.single().geometrySource
        assertEquals(geometrySource, loadedSource)
    }

    @Test
    fun persistAndLoadPreservesObjectProcessOverrideAndThreeMfMetadata() {
        val preferences = FakeSharedPreferences()
        val store = defaultStore()
        val editedProcess = store.processes.first().withValues(
            "name" to "Object support override",
            "orcaProcessOverridesJson" to """{"enable_support":true,"support_threshold_angle":35}"""
        )
        val geometrySource = PlateObjectGeometrySource.ThreeMfMeshExtract(
            originalPath = "/tmp/project.3mf",
            extractedStlPath = "/tmp/project-object.stl",
            projectMetadata = ThreeMfProjectMetadata(
                sourcePath = "/tmp/project.3mf",
                nativeProjectFilePath = "/tmp/native-project.3mf",
                plateCount = 2,
                objectCount = 4,
                plateNames = listOf("Plate A", "Plate B"),
                objectNames = listOf("left_cube.stl", "right_cube.stl"),
                objectFilamentAssignments = listOf(
                    ThreeMfObjectFilamentAssignment("left_cube.stl", 1),
                    ThreeMfObjectFilamentAssignment("right_cube.stl", 2)
                ),
                filamentCount = 2,
                thumbnailEntries = listOf("Metadata/plate_1.png", "Metadata/top_1.png"),
                configEntries = listOf("Metadata/model_settings.config", "Metadata/project_settings.config"),
                preservedFeatures = listOf("mesh", "materials"),
                unsupportedFeatures = listOf("modifier_meshes")
            )
        )
        val processOverride = PlateObjectProcessOverride(
            selectedProcessId = editedProcess.id,
            editedProcessProfile = editedProcess
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            geometrySource = geometrySource,
            processOverride = processOverride
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedObject = SavedProjectRepository.load(preferences).single().plateObjects.single()
        assertEquals(processOverride.selectedProcessId, loadedObject.processOverride?.selectedProcessId)
        assertEquals("""{"enable_support":true,"support_threshold_angle":35}""", loadedObject.processOverride?.editedProcessProfile?.orcaProcessOverridesJson)
        val loadedSource = loadedObject.geometrySource as PlateObjectGeometrySource.ThreeMfMeshExtract
        assertEquals(2, loadedSource.projectMetadata?.plateCount)
        assertEquals(4, loadedSource.projectMetadata?.objectCount)
        assertEquals(listOf("Plate A", "Plate B"), loadedSource.projectMetadata?.plateNames)
        assertEquals(listOf("left_cube.stl", "right_cube.stl"), loadedSource.projectMetadata?.objectNames)
        assertEquals(
            listOf(
                ThreeMfObjectFilamentAssignment("left_cube.stl", 1),
                ThreeMfObjectFilamentAssignment("right_cube.stl", 2)
            ),
            loadedSource.projectMetadata?.objectFilamentAssignments
        )
        assertEquals(2, loadedSource.projectMetadata?.filamentCount)
        assertEquals(listOf("Metadata/plate_1.png", "Metadata/top_1.png"), loadedSource.projectMetadata?.thumbnailEntries)
        assertEquals(
            listOf("Metadata/model_settings.config", "Metadata/project_settings.config"),
            loadedSource.projectMetadata?.configEntries
        )
        assertEquals(listOf("mesh", "materials"), loadedSource.projectMetadata?.preservedFeatures)
        assertEquals(listOf("modifier_meshes"), loadedSource.projectMetadata?.unsupportedFeatures)
    }

    @Test
    fun persistAndLoadPreservesSelectedObjectProcessBaseWithoutUnsavedEdits() {
        val preferences = FakeSharedPreferences()
        val processOverride = PlateObjectProcessOverride(
            selectedProcessId = "object_process_base",
            editedProcessProfile = null
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            processOverride = processOverride
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedObject = SavedProjectRepository.load(preferences).single().plateObjects.single()
        assertEquals("object_process_base", loadedObject.processOverride?.selectedProcessId)
        assertNull(loadedObject.processOverride?.editedProcessProfile)
    }

    @Test
    fun persistAndLoadPreservesModifierMeshProcessOverride() {
        val preferences = FakeSharedPreferences()
        val store = defaultStore()
        val modifierProcess = store.processes.first().withValues(
            "name" to "Modifier dense walls",
            "orcaProcessOverridesJson" to """{"wall_loops":5,"sparse_infill_density":80}"""
        )
        val modifier = PlateObjectModifierMesh(
            id = 9001L,
            label = "Dense corner modifier",
            filePath = "/tmp/modifier-corner.stl",
            bounds = MeshBounds(0f, 0f, 0f, 5f, 5f, 5f),
            transform = ViewerModelTransform(
                centerXmm = 4f,
                centerYmm = 5f,
                zOffsetMm = 6f,
                rotationXDegrees = 10f,
                rotationYDegrees = 20f,
                rotationZDegrees = 15f,
                uniformScale = 1.4f
            ),
            processOverride = PlateObjectProcessOverride(
                selectedProcessId = modifierProcess.id,
                editedProcessProfile = modifierProcess
            )
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            modifiers = listOf(modifier)
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedModifier = SavedProjectRepository.load(preferences).single().plateObjects.single().modifiers.single()
        assertEquals(9001L, loadedModifier.id)
        assertEquals("Dense corner modifier", loadedModifier.label)
        assertEquals("/tmp/modifier-corner.stl", loadedModifier.filePath)
        assertEquals(4f, loadedModifier.transform.centerXmm, 0.0001f)
        assertEquals(5f, loadedModifier.transform.centerYmm, 0.0001f)
        assertEquals(6f, loadedModifier.transform.zOffsetMm, 0.0001f)
        assertEquals(10f, loadedModifier.transform.rotationXDegrees, 0.0001f)
        assertEquals(20f, loadedModifier.transform.rotationYDegrees, 0.0001f)
        assertEquals(15f, loadedModifier.transform.rotationZDegrees, 0.0001f)
        assertEquals(1.4f, loadedModifier.transform.uniformScale, 0.0001f)
        assertEquals("""{"wall_loops":5,"sparse_infill_density":80}""", loadedModifier.processOverride.editedProcessProfile?.orcaProcessOverridesJson)
    }

    @Test
    fun persistAndLoadPreservesWorkspaceProcessState() {
        val preferences = FakeSharedPreferences()
        val store = defaultStore()
        val editedProcess = store.processes.first().withValues(
            "name" to "0.20mm Standard - workspace",
            "layerHeightMm" to 0.16f,
            "orcaProcessOverridesJson" to """{"layer_height":0.16}"""
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            profileStore = store,
            plates = listOf(
                SavedProjectPlate(
                    label = "ABS plate",
                    plateObjects = listOf(savedProjectObject(ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f))),
                    profileState = PlateProfileState(
                        selectedProcessId = editedProcess.id,
                        editedProcessProfile = editedProcess
                    )
                )
            )
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedPlate = SavedProjectRepository.load(preferences).single().plates.single()
        assertEquals("ABS plate", loadedPlate.label)
        assertEquals(editedProcess.id, loadedPlate.profileState.selectedProcessId)
        assertEquals("0.20mm Standard - workspace", loadedPlate.profileState.editedProcessProfile?.name)
        assertEquals(0.16f, loadedPlate.profileState.editedProcessProfile?.layerHeightMm ?: 0f, 0.0001f)
        assertEquals("""{"layer_height":0.16}""", loadedPlate.profileState.editedProcessProfile?.orcaProcessOverridesJson)
    }

    @Test
    fun persistDropsInvalidOrientationMatrix() {
        val preferences = FakeSharedPreferences()
        val project = savedProject(
            transform = ViewerModelTransform(
                centerXmm = 42.5f,
                centerYmm = 84.25f,
                orientationMatrix = listOf(1f, Float.NaN, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            )
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedTransform = SavedProjectRepository.load(preferences).single().plateObjects.single().transform
        assertNull(loadedTransform.orientationMatrix)
    }

    @Test
    fun persistAndLoadPreservesPaintPayloads() {
        val preferences = FakeSharedPreferences()
        val paint = PlateObjectPaint(
            seam = paintLayer(PaintMode.Seam, "1C0C"),
            color = paintLayer(PaintMode.Color, "2C0C", referencedSlots = setOf(1, 2))
        )
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            paint = paint
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedPaint = SavedProjectRepository.load(preferences).single().plateObjects.single().paint
        assertEquals("1C0C", loadedPaint.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
        assertEquals("2C0C", loadedPaint.color?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
        assertEquals(setOf(1, 2), loadedPaint.color?.referencedSlotIndexes)
        assertEquals(paint.color?.meshFingerprint, loadedPaint.color?.meshFingerprint)
        assertEquals("fnv1a64:test", loadedPaint.color?.volumeLayers?.single()?.nativeMeshFingerprint)
    }

    @Test
    fun persistAndLoadPreservesNativeCommittedPaintPayload() {
        val preferences = FakeSharedPreferences()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 1,
                  "layers": [
                    {
                      "mode": "Seam",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 1,
                          "meshFingerprint": "fnv1a64:native",
                          "triangles": [
                            {"triangleIndex": 0, "hexBits": "1C0C"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val nativeCommittedPaint = commitNativePaintPayloadToPlateObjects(
            objects = listOf(
                com.mobileslicer.workspace.PlateObject(
                    id = 1L,
                    label = "Oriented Model",
                    filePath = "/tmp/oriented-model.stl",
                    nativeSourceKey = "native-source-1",
                    format = ImportedModelFormat.Stl,
                    importTiming = null,
                    bounds = MeshBounds(0f, 0f, 0f, 10f, 20f, 30f),
                    transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f)
                )
            ),
            objectId = 1L,
            mode = PaintMode.Seam,
            payloadJson = payload
        ).committedObject?.paint ?: error("native payload did not commit")
        val project = savedProject(
            transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f),
            paint = nativeCommittedPaint
        )

        SavedProjectRepository.persist(preferences, listOf(project))

        val loadedPaint = SavedProjectRepository.load(preferences).single().plateObjects.single().paint
        assertEquals("1C0C", loadedPaint.seam?.volumeLayers?.single()?.serializedTriangles?.single()?.hexBits)
        assertEquals("fnv1a64:native", loadedPaint.seam?.volumeLayers?.single()?.nativeMeshFingerprint)
    }

    @Test
    fun persistedNativeCommittedPaintEmitsReplayPayloadAfterLoad() {
        val preferences = FakeSharedPreferences()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 1,
                  "layers": [
                    {
                      "mode": "Support",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:native",
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
        val committedPaint = commitNativePaintPayloadToPlateObjects(
            objects = listOf(
                PlateObject(
                    id = 1L,
                    label = "Painted Model",
                    filePath = "/tmp/painted-model.stl",
                    nativeSourceKey = "native-source-1",
                    format = ImportedModelFormat.Stl,
                    importTiming = null,
                    bounds = MeshBounds(0f, 0f, 0f, 10f, 20f, 30f),
                    transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f)
                )
            ),
            objectId = 1L,
            mode = PaintMode.Support,
            payloadJson = payload
        ).committedObject?.paint ?: error("native payload did not commit")
        SavedProjectRepository.persist(
            preferences,
            listOf(savedProject(transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f), paint = committedPaint))
        )

        val loadedObject = SavedProjectRepository.load(preferences).single().plateObjects.single()
        val replayJson = JSONObject(
            nativePaintPayloadJson(
                listOf(
                    PlateObject(
                        id = 1L,
                        label = loadedObject.label,
                        filePath = loadedObject.filePath,
                        nativeSourceKey = loadedObject.nativeSourceKey,
                        filamentSlotIndex = loadedObject.filamentSlotIndex,
                        format = loadedObject.format,
                        importTiming = null,
                        bounds = loadedObject.bounds,
                        transform = loadedObject.transform,
                        paint = loadedObject.paint
                    )
                )
            )
        )
        val replayVolume = replayJson.getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")
            .getJSONObject(0)
            .getJSONArray("volumes")
            .getJSONObject(0)

        assertTrue(replayJson.getJSONArray("objects").length() == 1)
        assertEquals("Support", replayJson.getJSONArray("objects").getJSONObject(0).getJSONArray("layers").getJSONObject(0).getString("mode"))
        assertEquals("fnv1a64:native", replayVolume.getString("meshFingerprint"))
        assertEquals(3, replayVolume.getJSONArray("triangles").getJSONObject(0).getInt("triangleIndex"))
    }

    @Test
    fun persistedNativeCommittedAllModePaintEmitsReplayPayloadAfterLoad() {
        val preferences = FakeSharedPreferences()
        val payload = """
            {
              "schemaVersion": 1,
              "objects": [
                {
                  "mobileObjectId": 1,
                  "layers": [
                    {
                      "mode": "Support",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:support",
                          "triangles": [
                            {"triangleIndex": 3, "hexBits": "2A"}
                          ]
                        }
                      ]
                    },
                    {
                      "mode": "Seam",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:seam",
                          "triangles": [
                            {"triangleIndex": 4, "hexBits": "1C0C"}
                          ]
                        }
                      ]
                    },
                    {
                      "mode": "Color",
                      "colorSlots": [1, 3],
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:color",
                          "triangles": [
                            {"triangleIndex": 5, "hexBits": "2C0C"}
                          ]
                        }
                      ]
                    },
                    {
                      "mode": "FuzzySkin",
                      "volumes": [
                        {
                          "volumeIndex": 0,
                          "triangleCount": 12,
                          "meshFingerprint": "fnv1a64:fuzzy",
                          "triangles": [
                            {"triangleIndex": 6, "hexBits": "1C0C"}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val committedPaint = commitNativePaintPayloadToPlateObjects(
            objects = listOf(
                PlateObject(
                    id = 1L,
                    label = "Painted Model",
                    filePath = "/tmp/painted-model.stl",
                    nativeSourceKey = "native-source-1",
                    format = ImportedModelFormat.Stl,
                    importTiming = null,
                    bounds = MeshBounds(0f, 0f, 0f, 10f, 20f, 30f),
                    transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f)
                )
            ),
            objectId = 1L,
            mode = PaintMode.Support,
            payloadJson = payload
        ).committedObject?.paint ?: error("native payload did not commit")
        SavedProjectRepository.persist(
            preferences,
            listOf(savedProject(transform = ViewerModelTransform(centerXmm = 42.5f, centerYmm = 84.25f), paint = committedPaint))
        )

        val loadedObject = SavedProjectRepository.load(preferences).single().plateObjects.single()
        val replayLayers = JSONObject(
            nativePaintPayloadJson(
                listOf(
                    PlateObject(
                        id = 1L,
                        label = loadedObject.label,
                        filePath = loadedObject.filePath,
                        nativeSourceKey = loadedObject.nativeSourceKey,
                        filamentSlotIndex = loadedObject.filamentSlotIndex,
                        format = loadedObject.format,
                        importTiming = null,
                        bounds = loadedObject.bounds,
                        transform = loadedObject.transform,
                        paint = loadedObject.paint
                    )
                )
            )
        ).getJSONArray("objects")
            .getJSONObject(0)
            .getJSONArray("layers")

        assertEquals(listOf("Support", "Seam", "Color", "FuzzySkin"), (0 until replayLayers.length()).map { index ->
            replayLayers.getJSONObject(index).getString("mode")
        })
        assertFalse(replayLayers.getJSONObject(2).has("referencedSlotIndexes"))
        assertEquals(3, replayLayers.getJSONObject(2).getJSONArray("colorSlots").getInt(1))
        assertEquals("fnv1a64:fuzzy", replayLayers.getJSONObject(3).getJSONArray("volumes").getJSONObject(0).getString("meshFingerprint"))
    }

    private fun savedProject(
        transform: ViewerModelTransform,
        paint: PlateObjectPaint = PlateObjectPaint(),
        geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile,
        processOverride: PlateObjectProcessOverride? = null,
        modifiers: List<PlateObjectModifierMesh> = emptyList(),
        nativeProjectFilePath: String? = null,
        profileStore: ProfileStore = defaultStore(),
        plates: List<SavedProjectPlate> = emptyList()
    ): SavedProject =
        SavedProject(
            id = "project_orientation_roundtrip",
            name = "Orientation Roundtrip",
            updatedAtEpochMs = 123L,
            profileStore = profileStore,
            nativeProjectFilePath = nativeProjectFilePath,
            plateObjects = listOf(savedProjectObject(transform, paint, geometrySource, processOverride, modifiers)),
            plates = plates
        )

    private fun savedProjectObject(
        transform: ViewerModelTransform,
        paint: PlateObjectPaint = PlateObjectPaint(),
        geometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile,
        processOverride: PlateObjectProcessOverride? = null,
        modifiers: List<PlateObjectModifierMesh> = emptyList()
    ): SavedProjectPlateObject =
        SavedProjectPlateObject(
            label = "Oriented Model",
            filePath = "/tmp/oriented-model.stl",
            nativeSourceKey = "native-source-1",
            filamentSlotIndex = 1,
            format = ImportedModelFormat.Stl,
            bounds = MeshBounds(
                minX = 0f,
                maxX = 10f,
                minY = 0f,
                maxY = 20f,
                minZ = 0f,
                maxZ = 30f
            ),
            transform = transform,
            paint = paint,
            geometrySource = geometrySource,
            processOverride = processOverride,
            modifiers = modifiers
        )

    private fun paintLayer(
        mode: PaintMode,
        hexBits: String,
        referencedSlots: Set<Int> = emptySet()
    ): SerializedPaintLayer =
        SerializedPaintLayer(
            mode = mode,
            objectSourceKey = "native-source-1",
            meshFingerprint = SourceMeshFingerprint(
                fileLengthBytes = 128L,
                lastModifiedEpochMs = 456L,
                triangleCount = 1,
                bounds = MeshBounds(0f, 0f, 0f, 10f, 20f, 30f),
                sampleSha256 = "abc123"
            ),
            referencedSlotIndexes = referencedSlots,
            volumeLayers = listOf(
                SerializedPaintVolumeLayer(
                    volumeIndex = 0,
                    triangleCount = 1,
                    serializedTriangles = listOf(
                        SerializedPaintTriangle(
                            triangleIndex = 0,
                            hexBits = hexBits
                        )
                    ),
                    nativeMeshFingerprint = "fnv1a64:test"
                )
            )
        )

    private fun defaultStore(): ProfileStore {
        val printers = listOf(ProfileStoreRepository.fallbackPrinterProfile())
        val filaments = ProfileStoreRepository.defaultFilamentProfiles()
        val processes = listOf(
            newProcessProfileUnchecked(
                0 to "process_saved_project_repository",
                1 to "Saved Project Repository Process",
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
            commit()
        }

        override fun commit(): Boolean {
            if (clear) values.clear()
            values.putAll(pending)
            pending.clear()
            clear = false
            return true
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = null
        }
    }
}
