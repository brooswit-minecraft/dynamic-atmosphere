import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("settings", Path(__file__).with_name("sync-modrinth-settings.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SettingsTest(unittest.TestCase):
    def test_updates_only_target_version_and_accepts_mixed_project_environments(self):
        calls = []

        def api(path, patch=None):
            calls.append((path, patch))
            if patch is not None:
                return None
            if path.startswith("/version_file/"):
                return {"id": "new", "project_id": "project", "version_number": "0.3.0-alpha.1"}
            if path == "/version/new":
                return {"environment": "client_and_server"}
            return {"status": "processing", "environment": ["server_only", "client_and_server"]}

        self.assertEqual("processing", module.sync(api, "project", "0.3.0-alpha.1",
                         {"environment": "client_and_server", "submit_for_review": True}, "hash"))
        self.assertIn(("/version/new", {"environment": "client_and_server"}), calls)
        self.assertFalse(any(path == "/version/old" for path, _ in calls))
        self.assertFalse(any(path == "/project/project" and patch and "environment" in patch
                             for path, patch in calls))

    def test_wrong_project_or_version_stops_before_writes(self):
        for published in ({"project_id": "other", "version_number": "v"},
                          {"project_id": "p", "version_number": "other"}):
            calls = []
            def api(path, patch=None):
                calls.append(patch)
                return published
            with self.assertRaises(ValueError):
                module.sync(api, "p", "v", {"environment": "client_and_server"}, "hash")
            self.assertEqual([None], calls)


if __name__ == "__main__":
    unittest.main()
