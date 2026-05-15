import unittest

from release_worktree_audit import group_for_path, parse_status_line, summarize


class ReleaseWorktreeAuditTest(unittest.TestCase):
    def test_parses_renames_to_destination_path(self):
        status, path = parse_status_line("R  docs/OLD.md -> docs/plans/OLD.md")

        self.assertEqual("R ", status)
        self.assertEqual("docs/plans/OLD.md", path)

    def test_groups_release_candidate_paths(self):
        groups = summarize(
            [
                " M scripts/verify_android.sh",
                "?? engine-wrapper/orca_wrapper_internal.cpp",
                " M android-app/app/src/main/java/com/mobileslicer/workspace/WorkspaceScreen.kt",
                " M android-app/app/src/main/java/com/mobileslicer/profiles/OrcaProfileTransfer.kt",
                " M docs/LEGAL.md",
                " M Website/lib/thingiverse-oauth.mjs",
                " M README/plans/MODEL_SEARCH_IMPORT_PLAN.md",
                " M android-app/app/src/main/AndroidManifest.xml",
                " M android-app/gradle/libs.versions.toml",
                " M vendor/orcaslicer/src/libslic3r/TriangleSelector.cpp",
                "?? docs/RELEASE_STATUS.md",
            ]
        )

        self.assertIn((" M", "scripts/verify_android.sh"), groups["release-gates-and-scripts"])
        self.assertIn(("??", "engine-wrapper/orca_wrapper_internal.cpp"), groups["native-orca-wrapper"])
        self.assertIn(
            (" M", "android-app/app/src/main/java/com/mobileslicer/workspace/WorkspaceScreen.kt"),
            groups["android-workspace-and-render"],
        )
        self.assertIn(
            (" M", "android-app/app/src/main/java/com/mobileslicer/profiles/OrcaProfileTransfer.kt"),
            groups["profiles-storage-printer"],
        )
        self.assertIn((" M", "docs/LEGAL.md"), groups["docs"])
        self.assertIn((" M", "Website/lib/thingiverse-oauth.mjs"), groups["website-and-oauth-backend"])
        self.assertIn((" M", "README/plans/MODEL_SEARCH_IMPORT_PLAN.md"), groups["project-plans"])
        self.assertIn((" M", "android-app/app/src/main/AndroidManifest.xml"), groups["android-build-and-manifest"])
        self.assertIn((" M", "android-app/gradle/libs.versions.toml"), groups["android-build-and-manifest"])
        self.assertIn(
            (" M", "vendor/orcaslicer/src/libslic3r/TriangleSelector.cpp"),
            groups["vendor-patches"],
        )
        self.assertIn(("??", "docs/RELEASE_STATUS.md"), groups["docs"])

    def test_uncategorized_paths_are_explicit(self):
        self.assertEqual("uncategorized", group_for_path("unknown/file.txt"))


if __name__ == "__main__":
    unittest.main()
