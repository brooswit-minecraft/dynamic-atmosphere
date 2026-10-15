"""Offline release-planning cases for the existing Python CI suite."""
import unittest
import os
from pathlib import Path
import tempfile
from unittest.mock import patch
import next_version
from next_version import render_release


class FragmentReleaseTest(unittest.TestCase):
    def test_minor_after_current_release(self):
        version, section, notes = render_release("0.21.0-alpha.1", [
            ("MINECRAFT-19.md", "bump: minor\n\n### Added\n- Feature.")])
        self.assertEqual(version, "0.22.0-alpha.1")
        self.assertIn("Category: minor", section)
        self.assertTrue(notes.startswith("category: minor\n\n# 0.22.0-alpha.1"))

    def test_highest_bump_resets_alpha_counter(self):
        version, _, _ = render_release("0.21.3-alpha.9", [
            ("fix.md", "bump: patch\n\n### Fixed\n- Fix."),
            ("feature.md", "bump: minor\n\n### Added\n- Feature.")])
        self.assertEqual(version, "0.22.0-alpha.1")

    def test_patch(self):
        self.assertEqual(render_release("0.21.0-alpha.1", [
            ("fix.md", "bump: patch\n\n### Fixed\n- Fix.")])[0], "0.21.1-alpha.1")

    def test_major_requires_migration(self):
        with self.assertRaises(ValueError):
            render_release("0.21.0-alpha.1", [("break.md", "bump: major\n\n### BREAKING\n- Change.")])
        version, _, notes = render_release("0.21.0-alpha.1", [
            ("break.md", "bump: major\n\n### BREAKING\n- Change.\n\n## Migration\nBack up and migrate.")])
        self.assertEqual(version, "1.0.0-alpha.1")
        self.assertIn("category: breaking", notes)

    def test_invalid_fragments_fail_closed(self):
        for text in ("bump: patch", "bump: typo\n- No.", "bump: patch\nCategory: patch\n- No."):
            with self.subTest(text=text), self.assertRaises(ValueError):
                render_release("0.21.0-alpha.1", [("bad.md", text)])
        with self.assertRaises(ValueError):
            render_release("0.21.0-alpha.1", [])


class ReleasePlanTest(unittest.TestCase):
    def run_plan(self, *, fragments=False, manual=False, tagged=False, collision=False):
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            os.chdir(directory)
            self.addCleanup(os.chdir, previous)
            try:
                Path("changelog.d").mkdir()
                Path("changelog.d/new.md").write_text("bump: minor\n\n### Added\n- Feature.")
                def fake_git(*args):
                    if args[:2] == ("tag", "--merged"):
                        return "v0.21.0-alpha.1"
                    if args[:2] == ("tag", "--list"):
                        return "v0.21.0-alpha.1\nv0.22.0-alpha.1" if collision else "v0.21.0-alpha.1"
                    if args[0] == "diff":
                        return "changelog.d/new.md\nchangelog.d/README.md" if fragments else ""
                    if args[0] == "rev-list":
                        return "head" if tagged else "baseline"
                    return "head"
                with patch.object(next_version, "git", fake_git), \
                     patch.dict(os.environ, {"GITHUB_OUTPUT": "output"}), \
                     patch("sys.argv", ["next_version.py"] + (["--manual"] if manual else [])), \
                     patch.object(next_version.subprocess, "check_output", return_value=
                                  "category: minor\n\n# 0.21.0-alpha.1\n\nCategory: minor\n\n- Published.\n") as release:
                    next_version.main()
                    return Path("output").read_text() if Path("output").exists() else "", release.call_count
            finally:
                os.chdir(previous)

    def test_push_without_new_fragments_is_noop(self):
        self.assertEqual(self.run_plan(), ("", 0))

    def test_added_fragment_selects_next_version(self):
        output, calls = self.run_plan(fragments=True)
        self.assertEqual(output, "value=0.22.0-alpha.1\nnew=true\n")
        self.assertEqual(calls, 0)

    def test_manual_and_tagged_retry_reuse_published_version(self):
        for mode in ({"manual": True}, {"tagged": True}):
            output, calls = self.run_plan(**mode)
            self.assertEqual(output, "value=0.21.0-alpha.1\nnew=false\n")
            self.assertEqual(calls, 1)

    def test_existing_unrelated_tag_is_not_overwritten(self):
        with self.assertRaises(ValueError):
            self.run_plan(fragments=True, collision=True)


if __name__ == "__main__":
    unittest.main()
