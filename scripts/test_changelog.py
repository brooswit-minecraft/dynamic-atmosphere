import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("changelog", Path(__file__).with_name("changelog.py"))
changelog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(changelog)

ATMO7_FIXTURE = """# 0.20.0-alpha.1

Category: breaking

- Overworld Endermen follow the Vapor spawn rule.
- Other feature bullets.

## Migration

- Saved dust, ender, exhaust, void and slime gas is dropped on load.
- `violence` config resets to defaults under `voidGas`.
- Client and server must run the same version.
- Back up your world first.

# 0.19.0-alpha.1

- Older section, no marker.
"""


class ExtractSectionTest(unittest.TestCase):
    def test_extracts_only_the_named_section(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        self.assertTrue(section.startswith("# 0.20.0-alpha.1"))
        self.assertIn("## Migration", section)
        self.assertNotIn("0.19.0-alpha.1", section)

    def test_missing_version_raises(self):
        with self.assertRaises(ValueError):
            changelog.extract_section(ATMO7_FIXTURE, "9.9.9")


class ParseCategoryTest(unittest.TestCase):
    def test_valid_patch(self):
        self.assertEqual(changelog.parse_category("# 0.1.0\n\nCategory: patch\n"), "patch")

    def test_valid_minor(self):
        self.assertEqual(changelog.parse_category("# 0.1.0\n\nCategory: minor\n"), "minor")

    def test_valid_breaking(self):
        self.assertEqual(changelog.parse_category("# 0.1.0\n\nCategory: breaking\n"), "breaking")

    def test_missing_marker_raises(self):
        with self.assertRaises(ValueError):
            changelog.parse_category("# 0.1.0\n\n- just a bullet\n")

    def test_invalid_value_raises(self):
        with self.assertRaises(ValueError):
            changelog.parse_category("# 0.1.0\n\nCategory: massive\n")

    def test_duplicate_marker_raises(self):
        with self.assertRaises(ValueError):
            changelog.parse_category("# 0.1.0\n\nCategory: patch\nCategory: patch\n")

    def test_atmo7_fixture_shape_passes(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        self.assertEqual(changelog.parse_category(section), "breaking")


class ValidateMigrationTest(unittest.TestCase):
    def test_breaking_without_migration_raises(self):
        with self.assertRaises(ValueError):
            changelog.validate_migration("# 0.1.0\n\nCategory: breaking\n\n- bullet\n", "breaking")

    def test_breaking_with_empty_migration_raises(self):
        section = "# 0.1.0\n\nCategory: breaking\n\n## Migration\n\n"
        with self.assertRaises(ValueError):
            changelog.validate_migration(section, "breaking")

    def test_breaking_with_content_passes(self):
        section = "# 0.1.0\n\nCategory: breaking\n\n## Migration\n\n- do a thing\n"
        changelog.validate_migration(section, "breaking")  # does not raise

    def test_migration_allowed_but_not_required_for_non_breaking(self):
        section = "# 0.1.0\n\nCategory: patch\n\n## Migration\n\n- optional notes\n"
        changelog.validate_migration(section, "patch")  # does not raise
        changelog.validate_migration("# 0.1.0\n\nCategory: patch\n\n- bullet\n", "patch")

    def test_atmo7_fixture_migration_passes(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        changelog.validate_migration(section, "breaking")  # does not raise


class ValidateSectionTest(unittest.TestCase):
    def test_no_section_for_version_raises(self):
        with self.assertRaises(ValueError):
            changelog.validate_section(ATMO7_FIXTURE, "9.9.9")

    def test_full_atmo7_fixture_passes(self):
        category, section_text = changelog.validate_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        self.assertEqual(category, "breaking")
        self.assertTrue(section_text.startswith("# 0.20.0-alpha.1"))


class BuildReleaseNotesTest(unittest.TestCase):
    def test_first_line_is_lowercase_category(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        notes = changelog.build_release_notes(section, "breaking")
        self.assertEqual(notes.splitlines()[0], "category: breaking")

    def test_migration_copied_verbatim_under_new_version_heading(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        notes = changelog.build_release_notes(section, "breaking")
        # The version heading and its Migration subsection travel together,
        # since the sickos extractor copies Migration text from inside it.
        heading_index = notes.index("# 0.20.0-alpha.1")
        migration_index = notes.index("## Migration")
        self.assertLess(heading_index, migration_index)
        self.assertIn("Saved dust, ender, exhaust, void and slime gas is dropped on load.", notes)

    def test_other_versions_excluded(self):
        section = changelog.extract_section(ATMO7_FIXTURE, "0.20.0-alpha.1")
        notes = changelog.build_release_notes(section, "breaking")
        self.assertNotIn("0.19.0-alpha.1", notes)

    def test_notes_are_lf_only_even_if_source_changelog_has_crlf(self):
        # sickos's extraction fails closed on CRLF (ticket comment 22811).
        crlf_fixture = ATMO7_FIXTURE.replace("\n", "\r\n")
        section = changelog.extract_section(crlf_fixture, "0.20.0-alpha.1")
        notes = changelog.build_release_notes(section, "breaking")
        self.assertNotIn("\r", notes)
        self.assertIn("## Migration", notes)


class RealChangelogTest(unittest.TestCase):
    """Catches a missing/invalid marker on a PR before it can fail a release on main."""

    def test_real_changelog_and_version_validate(self):
        repo_root = Path(__file__).resolve().parent.parent
        changelog_text = (repo_root / "CHANGELOG.md").read_text()
        version = (repo_root / "version.txt").read_text().strip()
        category, section_text = changelog.validate_section(changelog_text, version)
        self.assertIn(category, changelog.VALID_CATEGORIES)
        self.assertTrue(section_text.startswith(f"# {version}"))

    def test_real_release_notes_are_lf_only(self):
        repo_root = Path(__file__).resolve().parent.parent
        changelog_text = (repo_root / "CHANGELOG.md").read_text()
        version = (repo_root / "version.txt").read_text().strip()
        category, section_text = changelog.validate_section(changelog_text, version)
        notes = changelog.build_release_notes(section_text, category)
        self.assertNotIn("\r", notes)


if __name__ == "__main__":
    unittest.main()
