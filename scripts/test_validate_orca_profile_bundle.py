#!/usr/bin/env python3
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from validate_orca_profile_bundle import BundleValidationError, validate_bundle


ROOT_DIR = Path(__file__).resolve().parents[1]


class ValidateOrcaProfileBundleTest(unittest.TestCase):
    def test_vendored_orca_printer_fixture_matches_bundle_contract(self):
        result = validate_bundle(
            ROOT_DIR / "vendor" / "orcaslicer" / "resources" / "profiles" / "Anet" / "machine" / "Anet A8 Plus 0.4 nozzle.orca_printer"
        )

        self.assertEqual("printer config bundle", result["bundle_type"])
        self.assertEqual(1, result["printers"])
        self.assertEqual(10, result["filaments"])
        self.assertEqual(3, result["processes"])

    def test_rejects_process_without_compatible_printers(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "bad.orca_printer"
            with zipfile.ZipFile(bundle, "w") as zf:
                zf.writestr(
                    "bundle_structure.json",
                    json.dumps(
                        {
                            "bundle_type": "printer config bundle",
                            "printer_config": ["printer/P.json"],
                            "filament_config": [],
                            "process_config": ["process/P.json"],
                        }
                    ),
                )
                zf.writestr(
                    "printer/P.json",
                    json.dumps(
                        {
                            "name": "P",
                            "printer_settings_id": "P",
                            "nozzle_diameter": "0.4",
                            "printable_height": "220",
                            "printable_area": ["0x0", "220x0", "220x220", "0x220"],
                            "printhost_apikey": "",
                            "printhost_user": "",
                            "printhost_password": "",
                        }
                    ),
                )
                zf.writestr(
                    "process/P.json",
                    json.dumps(
                        {
                            "name": "0.20mm",
                            "print_settings_id": "0.20mm",
                            "layer_height": "0.2",
                            "wall_loops": "2",
                        }
                    ),
                )

            with self.assertRaises(BundleValidationError):
                validate_bundle(bundle)

    def test_rejects_mobileslicer_bundle_exported_as_system_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "bad.orca_printer"
            with zipfile.ZipFile(bundle, "w") as zf:
                zf.writestr(
                    "bundle_structure.json",
                    json.dumps(
                        {
                            "bundle_type": "printer config bundle",
                            "printer_preset_name": "Printer A (MobileSlicer)",
                            "printer_config": ["printer/P.json"],
                            "filament_config": [],
                            "process_config": [],
                        }
                    ),
                )
                zf.writestr(
                    "printer/P.json",
                    json.dumps(
                        {
                            "name": "Printer A (MobileSlicer)",
                            "printer_settings_id": "Printer A (MobileSlicer)",
                            "from": "system",
                            "inherits": "Printer A",
                            "is_custom_defined": "0",
                            "nozzle_diameter": "0.4",
                            "printable_height": "220",
                            "printable_area": ["0x0", "220x0", "220x220", "0x220"],
                            "printhost_apikey": "",
                            "printhost_user": "",
                            "printhost_password": "",
                        }
                    ),
                )

            with self.assertRaises(BundleValidationError):
                validate_bundle(bundle)

    def test_rejects_empty_vector_config_values_that_crash_desktop_orca(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "bad.orca_printer"
            with zipfile.ZipFile(bundle, "w") as zf:
                zf.writestr(
                    "bundle_structure.json",
                    json.dumps(
                        {
                            "bundle_type": "printer config bundle",
                            "printer_preset_name": "Printer A (MobileSlicer)",
                            "printer_config": ["printer/P.json"],
                            "filament_config": [],
                            "process_config": [],
                        }
                    ),
                )
                zf.writestr(
                    "printer/P.json",
                    json.dumps(
                        {
                            "name": "Printer A (MobileSlicer)",
                            "printer_settings_id": "Printer A (MobileSlicer)",
                            "from": "User",
                            "inherits": "",
                            "is_custom_defined": "1",
                            "nozzle_diameter": "0.4",
                            "printable_height": "220",
                            "printable_area": ["0x0", "220x0", "220x220", "0x220"],
                            "extruder_colour": "",
                            "printhost_apikey": "",
                            "printhost_user": "",
                            "printhost_password": "",
                        }
                    ),
                )

            with self.assertRaises(BundleValidationError):
                validate_bundle(bundle)

    def test_rejects_mobileslicer_process_values_that_desktop_orca_auto_corrects(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "bad.orca_printer"
            with zipfile.ZipFile(bundle, "w") as zf:
                zf.writestr(
                    "bundle_structure.json",
                    json.dumps(
                        {
                            "bundle_type": "printer config bundle",
                            "printer_preset_name": "Printer A (MobileSlicer)",
                            "printer_config": ["printer/P.json"],
                            "filament_config": [],
                            "process_config": ["process/P.json"],
                        }
                    ),
                )
                zf.writestr(
                    "printer/P.json",
                    json.dumps(
                        {
                            "name": "Printer A (MobileSlicer)",
                            "printer_settings_id": "Printer A (MobileSlicer)",
                            "from": "User",
                            "inherits": "",
                            "is_custom_defined": "1",
                            "nozzle_diameter": "0.4",
                            "printable_height": "220",
                            "printable_area": ["0x0", "220x0", "220x220", "0x220"],
                            "printhost_apikey": "",
                            "printhost_user": "",
                            "printhost_password": "",
                        }
                    ),
                )
                zf.writestr(
                    "process/P.json",
                    json.dumps(
                        {
                            "name": "0.20mm @Printer A (MobileSlicer)",
                            "print_settings_id": "0.20mm @Printer A (MobileSlicer)",
                            "from": "User",
                            "inherits": "",
                            "is_custom_defined": "1",
                            "layer_height": "0.2",
                            "wall_loops": "2",
                            "compatible_printers": ["Printer A (MobileSlicer)"],
                            "support_ironing_spacing": "0",
                        }
                    ),
                )

            with self.assertRaises(BundleValidationError):
                validate_bundle(bundle)


if __name__ == "__main__":
    unittest.main()
