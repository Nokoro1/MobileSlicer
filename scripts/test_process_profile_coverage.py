#!/usr/bin/env python3
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/audit_process_profile_coverage.py"
REPORT = ROOT / "docs/ORCA_PROCESS_SETTING_COVERAGE.md"


def load_audit_module():
    spec = importlib.util.spec_from_file_location("audit_process_profile_coverage", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ProcessProfileCoverageAuditTest(unittest.TestCase):
    def test_process_profile_coverage_has_no_gaps(self):
        audit = load_audit_module()
        rows, problems = audit.collect_coverage()
        self.assertEqual([], problems)
        self.assertGreaterEqual(len(rows), 300)
        self.assertTrue(any(row.field == "onlyOneWallFirstLayer" for row in rows))
        self.assertTrue(any("only_one_wall_first_layer" in row.native_keys for row in rows))

    def test_checked_in_report_is_current(self):
        audit = load_audit_module()
        rows, problems = audit.collect_coverage()
        self.assertEqual([], problems)
        self.assertEqual(audit.render_report(rows), REPORT.read_text())


if __name__ == "__main__":
    unittest.main()
