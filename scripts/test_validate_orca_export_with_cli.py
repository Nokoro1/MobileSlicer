#!/usr/bin/env python3
import json
import tempfile
import textwrap
import unittest
import zipfile
from pathlib import Path

from validate_orca_export_with_cli import build_orca_cli_command, validate_with_orca_cli


def write_minimal_orca_printer_bundle(path: Path) -> None:
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr(
            "bundle_structure.json",
            json.dumps(
                {
                    "bundle_type": "printer config bundle",
                    "printer_config": ["printer/P.json"],
                    "filament_config": ["filament/F.json"],
                    "process_config": ["process/Pr.json"],
                    "printer_preset_name": "P",
                    "version": "",
                }
            ),
        )
        zf.writestr(
            "printer/P.json",
            json.dumps(
                {
                    "name": "P",
                    "from": "User",
                    "inherits": "",
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
            "filament/F.json",
            json.dumps(
                {
                    "name": "F",
                    "from": "User",
                    "inherits": "",
                    "filament_settings_id": "F",
                    "filament_type": ["PLA"],
                }
            ),
        )
        zf.writestr(
            "process/Pr.json",
            json.dumps(
                {
                    "name": "Pr",
                    "from": "User",
                    "inherits": "",
                    "print_settings_id": "Pr",
                    "layer_height": "0.2",
                    "wall_loops": "2",
                    "compatible_printers": ["P"],
                }
            ),
        )


class ValidateOrcaExportWithCliTest(unittest.TestCase):
    def test_builds_orca_load_settings_and_filament_command(self):
        command = build_orca_cli_command(
            Path("/tmp/orca"),
            printer_paths=[Path("/tmp/printer/P.json")],
            filament_paths=[Path("/tmp/filament/F.json")],
            process_paths=[Path("/tmp/process/Pr.json")],
            extra_args=["--info"],
        )

        self.assertEqual("/tmp/orca", command[0])
        self.assertEqual("--load_settings", command[1])
        self.assertEqual("/tmp/printer/P.json;/tmp/process/Pr.json", command[2])
        self.assertEqual("--load_filaments", command[3])
        self.assertEqual("/tmp/filament/F.json", command[4])
        self.assertEqual("--info", command[5])

    def test_skips_when_orca_cli_path_is_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "P.orca_printer"
            write_minimal_orca_printer_bundle(bundle)

            message = validate_with_orca_cli(bundle, orca_cli=None)

        self.assertIn("SKIP", message)

    def test_runs_fake_orca_cli_against_extracted_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            bundle = root / "P.orca_printer"
            write_minimal_orca_printer_bundle(bundle)
            log = root / "argv.txt"
            fake_cli = root / "fake_orca.py"
            fake_cli.write_text(
                textwrap.dedent(
                    f"""\
                    #!/usr/bin/env python3
                    import pathlib
                    import sys
                    pathlib.Path({str(log)!r}).write_text("\\n".join(sys.argv[1:]), encoding="utf-8")
                    sys.exit(0)
                    """
                ),
                encoding="utf-8",
            )
            fake_cli.chmod(0o755)

            message = validate_with_orca_cli(bundle, orca_cli=fake_cli, extra_args=["--info"])

            self.assertIn("PASS", message)
            argv = log.read_text(encoding="utf-8")
            self.assertIn("--load_settings", argv)
            self.assertIn("--load_filaments", argv)
            self.assertIn("--info", argv)


if __name__ == "__main__":
    unittest.main()
