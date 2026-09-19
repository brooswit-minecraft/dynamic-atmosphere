import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("dispatch_sickos", Path(__file__).with_name("dispatch-sickos.py"))
dispatch_sickos = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dispatch_sickos)

VALID_FIELDS = dict(
    version="0.19.1-alpha.1",
    modrinth_version_id="mrv123",
    sha1="a" * 40,
    sha512="b" * 128,
    download_url="https://cdn.modrinth.com/data/PZV7RorC/versions/mrv123/x.jar",
    file_name="dynamicatmosphere-0.19.1-alpha.1.jar",
    category="patch",
    github_release_url="https://github.com/brooswit-minecraft/dynamic-atmosphere/releases/tag/v0.19.1-alpha.1",
)


class BuildPayloadTest(unittest.TestCase):
    def test_exactly_eight_keys_all_strings(self):
        payload = dispatch_sickos.build_payload(**VALID_FIELDS)
        self.assertEqual(set(payload), set(dispatch_sickos.PAYLOAD_KEYS))
        self.assertEqual(len(payload), 8)
        self.assertTrue(all(isinstance(v, str) for v in payload.values()))
        self.assertEqual(payload, VALID_FIELDS)

    def test_missing_field_rejected(self):
        fields = dict(VALID_FIELDS)
        fields["sha1"] = ""
        with self.assertRaises(ValueError):
            dispatch_sickos.build_payload(**fields)


class ShouldDispatchTest(unittest.TestCase):
    def test_dispatches_when_not_pre_existing(self):
        self.assertTrue(dispatch_sickos.should_dispatch("false"))
        self.assertTrue(dispatch_sickos.should_dispatch(""))

    def test_skips_when_pre_existing(self):
        self.assertFalse(dispatch_sickos.should_dispatch("true"))


class ResolveTokenTest(unittest.TestCase):
    def test_prefers_pat(self):
        env = {"SICKOS_DISPATCH_TOKEN": "pat-token", "SICKOS_DISPATCH_APP_TOKEN": "app-token"}
        self.assertEqual(dispatch_sickos.resolve_token(env), "pat-token")

    def test_falls_back_to_app_token(self):
        env = {"SICKOS_DISPATCH_TOKEN": "", "SICKOS_DISPATCH_APP_TOKEN": "app-token"}
        self.assertEqual(dispatch_sickos.resolve_token(env), "app-token")

    def test_none_when_neither_set(self):
        self.assertIsNone(dispatch_sickos.resolve_token({}))


class SendDispatchTest(unittest.TestCase):
    def test_request_shape(self):
        captured = {}

        def fake_post(request):
            captured["url"] = request.full_url
            captured["method"] = request.get_method()
            captured["headers"] = {k.lower(): v for k, v in request.header_items()}
            captured["body"] = json.loads(request.data)
            return 204

        payload = dispatch_sickos.build_payload(**VALID_FIELDS)
        dispatch_sickos.send_dispatch(payload, "secret-token", post=fake_post)

        self.assertEqual(captured["url"], dispatch_sickos.DISPATCH_URL)
        self.assertEqual(captured["method"], "POST")
        self.assertEqual(captured["headers"]["authorization"], "Bearer secret-token")
        self.assertEqual(captured["headers"]["accept"], "application/vnd.github+json")
        self.assertEqual(captured["body"]["event_type"], "dynamic-atmosphere-released")
        self.assertEqual(captured["body"]["client_payload"], payload)


class MainTest(unittest.TestCase):
    def _env(self, **overrides):
        env = {
            "VERSION": VALID_FIELDS["version"],
            "MODRINTH_VERSION_ID": VALID_FIELDS["modrinth_version_id"],
            "SHA1": VALID_FIELDS["sha1"],
            "SHA512": VALID_FIELDS["sha512"],
            "DOWNLOAD_URL": VALID_FIELDS["download_url"],
            "FILE_NAME": VALID_FIELDS["file_name"],
            "CATEGORY": VALID_FIELDS["category"],
            "GITHUB_RELEASE_URL": VALID_FIELDS["github_release_url"],
        }
        env.update(overrides)
        return env

    def test_skip_when_already_published_makes_no_http_call(self):
        calls = []
        with tempfile.TemporaryDirectory() as tmp:
            artifact = str(Path(tmp) / "payload.json")
            env = self._env(MODRINTH_PRE_PUBLISH_EXISTS="true", SICKOS_DISPATCH_TOKEN="tok")
            result = dispatch_sickos.main([artifact], env=env, post=lambda r: calls.append(r))
            self.assertEqual(result, 0)
            self.assertEqual(calls, [])
            # The payload artifact is still written, independent of dispatch.
            self.assertTrue(Path(artifact).exists())

    def test_skip_when_no_credential_writes_warning_and_makes_no_http_call(self):
        calls = []
        with tempfile.TemporaryDirectory() as tmp:
            artifact = str(Path(tmp) / "payload.json")
            summary = str(Path(tmp) / "summary.md")
            env = self._env(MODRINTH_PRE_PUBLISH_EXISTS="false", GITHUB_STEP_SUMMARY=summary)
            result = dispatch_sickos.main([artifact], env=env, post=lambda r: calls.append(r))
            self.assertEqual(result, 0)
            self.assertEqual(calls, [])
            self.assertIn("::warning::", Path(summary).read_text())

    def test_dispatches_when_newly_published_with_credential(self):
        calls = []
        with tempfile.TemporaryDirectory() as tmp:
            artifact = str(Path(tmp) / "payload.json")
            env = self._env(MODRINTH_PRE_PUBLISH_EXISTS="false", SICKOS_DISPATCH_TOKEN="tok")
            result = dispatch_sickos.main([artifact], env=env, post=lambda r: calls.append(r) or 204)
            self.assertEqual(result, 0)
            self.assertEqual(len(calls), 1)
            written = json.loads(Path(artifact).read_text())
            self.assertEqual(set(written), set(dispatch_sickos.PAYLOAD_KEYS))

    def test_http_failure_with_credential_raises_loudly(self):
        def failing_post(request):
            raise HTTPError(request.full_url, 500, "boom", hdrs=None, fp=None)

        with tempfile.TemporaryDirectory() as tmp:
            artifact = str(Path(tmp) / "payload.json")
            env = self._env(MODRINTH_PRE_PUBLISH_EXISTS="false", SICKOS_DISPATCH_TOKEN="tok")
            with self.assertRaises(SystemExit) as ctx:
                dispatch_sickos.main([artifact], env=env, post=failing_post)
            message = str(ctx.exception).lower()
            self.assertIn("resend by hand", message)
            self.assertIn("500", message)


class FixtureTest(unittest.TestCase):
    """The shared producer/consumer contract fixture (SICKOS-72 tests against the same bytes).

    Per ticket comment 22701: this fixture is DA-authored only until sickos posts the
    commit SHA of their own tests/fixtures/dynamic-atmosphere-released.example.json;
    at that point this file must be replaced with those exact bytes, and the assertion
    below extends to key ORDER (not just the key set) to catch drift.
    """

    def test_fixture_has_exactly_the_payload_builder_key_set_and_types(self):
        fixture_path = Path(__file__).with_name("fixtures") / "dynamic-atmosphere-released.example.json"
        fixture = json.loads(fixture_path.read_text())
        self.assertEqual(set(fixture), set(dispatch_sickos.PAYLOAD_KEYS))
        self.assertTrue(all(isinstance(v, str) for v in fixture.values()))
        # A payload built from realistic values must validate the same way.
        dispatch_sickos.build_payload(**fixture)


if __name__ == "__main__":
    unittest.main()
