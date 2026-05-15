#!/usr/bin/env python3

import tempfile
import unittest
import zipfile
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import orca_3mf_project_preservation_audit as audit


class Orca3mfProjectPreservationAuditTest(unittest.TestCase):
    def test_inspects_object_names_filament_assignments_and_thumbnails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "project.3mf"
            write_fixture(fixture)

            metadata = audit.inspect_3mf(fixture)
            failures = audit.validate(
                metadata,
                min_plate_count=1,
                min_object_count=2,
                require_object_names=True,
                require_filament_assignments=True,
                require_object_settings=True,
                require_modifier_volumes=True,
                require_modifier_settings=True,
                require_layer_ranges=True,
                require_layer_range_settings=True,
                require_project_thumbnails=True,
                require_plate_json_metadata=True,
                require_sliced_plate_gcode=True,
                require_project_settings=True,
            )

            self.assertEqual([], failures)
            self.assertEqual(["left_cube.stl", "right_cube.stl"], metadata["object_names"])
            self.assertEqual(2, len(metadata["object_filament_assignments"]))
            self.assertEqual(
                [
                    {
                        "object_name": "right_cube.stl",
                        "key": "sparse_infill_density",
                        "value": "35%",
                    }
                ],
                metadata["object_setting_evidence"],
            )
            self.assertEqual(1, len(metadata["modifier_evidence"]))
            self.assertEqual("dense_modifier.stl", metadata["modifier_evidence"][0]["modifier_name"])
            self.assertIn("modifier_settings", metadata["preserved_features"])
            self.assertEqual(
                [
                    {
                        "object_name": "right_cube.stl",
                        "object_index": 2,
                        "min_z": "0",
                        "max_z": "6",
                        "settings": [
                            {
                                "object_name": "right_cube.stl",
                                "key": "sparse_infill_density",
                                "value": "75%",
                            }
                        ],
                    }
                ],
                metadata["layer_range_evidence"],
            )
            self.assertIn("layer_config_ranges", metadata["preserved_features"])
            self.assertIn("layer_range_settings", metadata["preserved_features"])
            self.assertEqual(2, metadata["filament_count"])
            self.assertIn("project_thumbnails", metadata["preserved_features"])
            self.assertIn("plate_json_metadata", metadata["preserved_features"])
            self.assertEqual([1], metadata["thumbnail_plate_indices"])
            self.assertEqual([1], metadata["plate_json_indices"])
            self.assertEqual([1], metadata["sliced_plate_gcode_indices"])

    def test_fails_missing_required_project_context(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "flat.3mf"
            with zipfile.ZipFile(fixture, "w") as zf:
                zf.writestr("3D/3dmodel.model", ROOT_MODEL_XML)

            metadata = audit.inspect_3mf(fixture)
            failures = audit.validate(
                metadata,
                min_plate_count=1,
                min_object_count=2,
                require_object_names=True,
                require_filament_assignments=True,
                require_object_settings=True,
                require_modifier_volumes=True,
                require_modifier_settings=True,
                require_layer_ranges=True,
                require_layer_range_settings=True,
                require_project_thumbnails=True,
                require_plate_json_metadata=True,
                require_sliced_plate_gcode=True,
                require_project_settings=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("object-names", checks)
            self.assertIn("filament-assignments", checks)
            self.assertIn("object-settings", checks)
            self.assertIn("modifier-volumes", checks)
            self.assertIn("modifier-settings", checks)
            self.assertIn("layer-ranges", checks)
            self.assertIn("layer-range-settings", checks)
            self.assertIn("project-thumbnails", checks)
            self.assertIn("plate-json-metadata", checks)
            self.assertIn("sliced-plate-gcode", checks)
            self.assertIn("project-settings", checks)

    def test_roundtrip_compare_preserves_required_source_context(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.3mf"
            roundtrip = Path(tmp) / "roundtrip.3mf"
            write_fixture(source)
            write_fixture(roundtrip)

            failures = audit.compare_roundtrip(
                audit.inspect_3mf(source),
                audit.inspect_3mf(roundtrip),
                require_object_names=True,
                require_filament_assignments=True,
                require_object_settings=True,
                require_modifier_volumes=True,
                require_modifier_settings=True,
                require_layer_ranges=True,
                require_layer_range_settings=True,
                require_project_thumbnails=True,
                require_plate_json_metadata=True,
                require_sliced_plate_gcode=True,
                require_project_settings=True,
                require_plate_names=True,
            )

            self.assertEqual([], failures)

    def test_roundtrip_compare_fails_when_object_context_is_flattened(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.3mf"
            roundtrip = Path(tmp) / "flat-roundtrip.3mf"
            write_fixture(source, include_second_plate=True, include_second_plate_thumbnails=True)
            with zipfile.ZipFile(roundtrip, "w") as zf:
                zf.writestr("3D/3dmodel.model", ROOT_MODEL_XML)
                zf.writestr("Metadata/model_settings.config", FLATTENED_MODEL_SETTINGS_XML)
                zf.writestr("Metadata/project_settings.config", "{}")

            failures = audit.compare_roundtrip(
                audit.inspect_3mf(source),
                audit.inspect_3mf(roundtrip),
                require_object_names=True,
                require_filament_assignments=True,
                require_object_settings=True,
                require_modifier_volumes=True,
                require_modifier_settings=True,
                require_layer_ranges=True,
                require_layer_range_settings=True,
                require_project_thumbnails=True,
                require_plate_json_metadata=True,
                require_sliced_plate_gcode=True,
                require_project_settings=True,
                require_plate_names=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("roundtrip-object-names", checks)
            self.assertIn("roundtrip-filament-count", checks)
            self.assertIn("roundtrip-filament-assignments", checks)
            self.assertIn("roundtrip-object-settings", checks)
            self.assertIn("roundtrip-modifier-volumes", checks)
            self.assertIn("roundtrip-modifier-settings", checks)
            self.assertIn("roundtrip-layer-ranges", checks)
            self.assertIn("roundtrip-layer-range-settings", checks)
            self.assertIn("roundtrip-project-thumbnails", checks)
            self.assertIn("roundtrip-plate-json-metadata", checks)
            self.assertIn("roundtrip-sliced-plate-gcode", checks)

    def test_project_thumbnails_must_cover_each_source_plate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.3mf"
            roundtrip = Path(tmp) / "roundtrip.3mf"
            write_fixture(source, include_second_plate=True, include_second_plate_thumbnails=True)
            write_fixture(roundtrip, include_second_plate=True, include_second_plate_thumbnails=False)

            failures = audit.validate(
                audit.inspect_3mf(roundtrip),
                min_plate_count=2,
                min_object_count=2,
                require_project_thumbnails=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("project-thumbnails", checks)

            failures = audit.compare_roundtrip(
                audit.inspect_3mf(source),
                audit.inspect_3mf(roundtrip),
                require_project_thumbnails=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("roundtrip-project-thumbnails", checks)

    def test_step_source_evidence_is_reported_and_required(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "step-project.3mf"
            write_fixture(fixture, model_settings_xml=STEP_MODEL_SETTINGS_XML)

            metadata = audit.inspect_3mf(fixture)

            self.assertEqual(["occt_screw.step"], metadata["source_file_evidence"])
            self.assertIn("source_file_evidence", metadata["preserved_features"])
            self.assertIn("step_source_file_evidence", metadata["preserved_features"])
            self.assertEqual(
                [],
                audit.validate(
                    metadata,
                    min_plate_count=1,
                    min_object_count=1,
                    require_step_source=True,
                    require_project_settings=True,
                ),
            )

    def test_step_source_requirement_fails_when_flattened_to_stl(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "stl-project.3mf"
            write_fixture(fixture)

            failures = audit.validate(
                audit.inspect_3mf(fixture),
                min_plate_count=1,
                min_object_count=1,
                require_step_source=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("step-source", checks)

    def test_roundtrip_compare_requires_step_source_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.3mf"
            roundtrip = Path(tmp) / "roundtrip.3mf"
            write_fixture(source, model_settings_xml=STEP_MODEL_SETTINGS_XML)
            write_fixture(roundtrip)

            failures = audit.compare_roundtrip(
                audit.inspect_3mf(source),
                audit.inspect_3mf(roundtrip),
                require_step_source=True,
            )

            checks = {failure.check for failure in failures}
            self.assertIn("roundtrip-step-source", checks)


def write_fixture(
    path: Path,
    *,
    include_second_plate: bool = False,
    include_second_plate_thumbnails: bool = True,
    model_settings_xml: str | None = None,
) -> None:
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("3D/3dmodel.model", ROOT_MODEL_XML)
        zf.writestr(
            "Metadata/model_settings.config",
            model_settings_xml
            or (MODEL_SETTINGS_XML if not include_second_plate else MODEL_SETTINGS_TWO_PLATES_XML),
        )
        zf.writestr("Metadata/layer_config_ranges.xml", LAYER_CONFIG_RANGES_XML)
        zf.writestr("Metadata/slice_info.config", SLICE_INFO_XML)
        zf.writestr("Metadata/project_settings.config", "{}")
        zf.writestr("Metadata/plate_1.png", b"\x89PNG")
        zf.writestr("Metadata/top_1.png", b"\x89PNG")
        zf.writestr("Metadata/plate_1.json", b"{}")
        zf.writestr("Metadata/plate_1.gcode", b"G1 X0 Y0\n")
        if include_second_plate and include_second_plate_thumbnails:
            zf.writestr("Metadata/plate_2.png", b"\x89PNG")
            zf.writestr("Metadata/top_2.png", b"\x89PNG")
            zf.writestr("Metadata/plate_2.json", b"{}")
            zf.writestr("Metadata/plate_2.gcode", b"G1 X1 Y1\n")


ROOT_MODEL_XML = """<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
  <resources>
    <object id="2" type="model"/>
    <object id="4" type="model"/>
  </resources>
  <build>
    <item objectid="2"/>
    <item objectid="4"/>
  </build>
</model>
"""

MODEL_SETTINGS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<config>
  <object id="2">
    <metadata key="name" value="left_cube.stl"/>
    <metadata key="extruder" value="1"/>
  </object>
  <object id="4">
    <metadata key="name" value="right_cube.stl"/>
    <metadata key="extruder" value="2"/>
    <metadata key="sparse_infill_density" value="35%"/>
    <part id="3" subtype="normal_part">
      <metadata key="name" value="right_cube.stl"/>
      <metadata key="matrix" value="1 0 0 0 0 1 0 0 0 0 1 5 0 0 0 1"/>
      <metadata key="source_file" value="right_cube.stl"/>
      <metadata key="source_object_id" value="0"/>
      <metadata key="source_volume_id" value="0"/>
      <metadata key="source_offset_x" value="0"/>
      <metadata key="source_offset_y" value="0"/>
      <metadata key="source_offset_z" value="5"/>
      <mesh_stat edges_fixed="0" degenerate_facets="0" facets_removed="0" facets_reversed="0" backwards_edges="0"/>
    </part>
    <part id="5" subtype="parameter_modifier">
      <metadata key="name" value="dense_modifier.stl"/>
      <metadata key="matrix" value="1 0 0 0 0 1 0 0 0 0 1 10 10 0 1"/>
      <metadata key="source_file" value="dense_modifier.stl"/>
      <metadata key="sparse_infill_density" value="80%"/>
      <mesh_stat edges_fixed="0" degenerate_facets="0" facets_removed="0" facets_reversed="0" backwards_edges="0"/>
    </part>
  </object>
  <plate>
    <metadata key="plater_id" value="1"/>
    <metadata key="plater_name" value="Plate 1"/>
  </plate>
</config>
"""

FLATTENED_MODEL_SETTINGS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<config>
  <object id="2">
    <metadata key="name" value="flattened_mesh.stl"/>
  </object>
  <plate>
    <metadata key="plater_id" value="1"/>
    <metadata key="plater_name" value="Plate 1"/>
  </plate>
</config>
"""

MODEL_SETTINGS_TWO_PLATES_XML = MODEL_SETTINGS_XML.replace(
    "</config>",
    """  <plate>
    <metadata key="plater_id" value="2"/>
    <metadata key="plater_name" value="Plate 2"/>
  </plate>
</config>""",
)

STEP_MODEL_SETTINGS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<config>
  <object id="2">
    <metadata key="name" value="occt_screw"/>
    <metadata key="extruder" value="1"/>
    <metadata key="source_file" value="occt_screw.step"/>
    <metadata key="wall_loops" value="5"/>
  </object>
  <plate>
    <metadata key="plater_id" value="1"/>
    <metadata key="plater_name" value="STEP source plate"/>
  </plate>
</config>
"""

LAYER_CONFIG_RANGES_XML = """<?xml version="1.0" encoding="UTF-8"?>
<objects>
  <object id="2">
    <range min_z="0" max_z="6">
      <option opt_key="sparse_infill_density">75%</option>
    </range>
  </object>
</objects>
"""

SLICE_INFO_XML = """<?xml version="1.0" encoding="UTF-8"?>
<config>
  <plate>
    <metadata key="index" value="1"/>
    <filament id="1" type="PLA" color="#ff0000"/>
    <filament id="2" type="PETG" color="#00ff00"/>
  </plate>
</config>
"""


if __name__ == "__main__":
    unittest.main()
