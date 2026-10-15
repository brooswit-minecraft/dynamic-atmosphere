"""Guard the mod-only workflow boundary without any live publication."""
from pathlib import Path
import re
import unittest


class ReleaseBoundaryTest(unittest.TestCase):
    def test_project_mutations_and_downstream_steps_require_integrated_mode(self):
        workflow = (Path(__file__).parents[1] / ".github/workflows/release.yml").read_text()
        steps = re.split(r"^      - ", workflow, flags=re.MULTILINE)[1:]
        guarded = (
            "Sync Modrinth description", "Sync current Modrinth environment and review state",
            "Determine sickos dispatch credential", "Mint sickos dispatch app token",
            "Build dynamic-atmosphere-released payload and dispatch to sickos",
        )
        for name in guarded:
            with self.subTest(step=name):
                step = next(s for s in steps if s.startswith("name: " + name + "\n"))
                condition = next(line for line in step.splitlines() if line.strip().startswith("if:"))
                self.assertIn("env.MOD_ONLY != 'true'", condition)

    def test_automatic_and_default_manual_runs_are_mod_only(self):
        workflow = (Path(__file__).parents[1] / ".github/workflows/release.yml").read_text()
        self.assertRegex(workflow, r"(?s)release_mode:.*?type: choice.*?default: mod-only")
        self.assertIn("github.event_name != 'workflow_dispatch' || inputs.release_mode != 'integrated'", workflow)

    def test_mod_only_keeps_artifact_verification(self):
        workflow = (Path(__file__).parents[1] / ".github/workflows/release.yml").read_text()
        for name in ("Build and test", "Verify production server boots with and without Flowing Fluids",
                     "Publish immutable GitHub release", "Verify Modrinth publish (authenticated read-back)"):
            step = workflow.split("- name: " + name + "\n", 1)[1].split("\n      - ", 1)[0]
            self.assertNotIn("MOD_ONLY", step)


if __name__ == "__main__":
    unittest.main()
