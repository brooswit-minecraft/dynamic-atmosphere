import hashlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from urllib.error import HTTPError, URLError

spec = importlib.util.spec_from_file_location(
    "verify_modrinth_publish", Path(__file__).with_name("verify-modrinth-publish.py")
)
verify_modrinth_publish = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify_modrinth_publish)

ARTIFACT = b"pretend jar bytes"
SHA1 = hashlib.sha1(ARTIFACT).hexdigest()
SHA512 = hashlib.sha512(ARTIFACT).hexdigest()


class _FakeResponse:
    def __init__(self, payload):
        self._payload = json.dumps(payload).encode()

    def __enter__(self):
        return io.BytesIO(self._payload)

    def __exit__(self, *exc_info):
        return False


def _record(project_id="proj1", version="0.20.0-alpha.1", sha1=SHA1, sha512=SHA512):
    return {
        "id": "modrinth-version-id",
        "project_id": project_id,
        "version_number": version,
        "files": [{"url": "https://cdn.modrinth.com/x.jar", "filename": "x.jar",
                   "hashes": {"sha1": sha1, "sha512": sha512}}],
    }


def _not_found(request):
    raise HTTPError(request.full_url, 404, "not found", hdrs=None, fp=None)


def _server_error(request):
    raise HTTPError(request.full_url, 503, "service unavailable", hdrs=None, fp=None)


class VerifyTest(unittest.TestCase):
    def test_matches_on_first_attempt(self):
        fetch = lambda request, timeout: _FakeResponse(_record())
        result = verify_modrinth_publish.verify(
            "proj1", "0.20.0-alpha.1", ARTIFACT, "token", fetch=fetch, sleep=lambda s: None,
        )
        self.assertEqual(result, {
            "modrinth_version_id": "modrinth-version-id",
            "sha1": SHA1,
            "sha512": SHA512,
            "download_url": "https://cdn.modrinth.com/x.jar",
            "file_name": "x.jar",
        })

    def test_retries_through_404_then_succeeds(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            if calls["n"] < 3:
                return _not_found(request)
            return _FakeResponse(_record())

        sleeps = []
        result = verify_modrinth_publish.verify(
            "proj1", "0.20.0-alpha.1", ARTIFACT, "token", fetch=fetch, sleep=sleeps.append,
        )
        self.assertEqual(result["modrinth_version_id"], "modrinth-version-id")
        self.assertEqual(len(sleeps), 2)

    def test_wrong_project_rejected(self):
        fetch = lambda request, timeout: _FakeResponse(_record(project_id="other-project"))
        with self.assertRaises(SystemExit):
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=1, fetch=fetch, sleep=lambda s: None,
            )

    def test_wrong_version_rejected(self):
        fetch = lambda request, timeout: _FakeResponse(_record(version="0.1.0"))
        with self.assertRaises(SystemExit):
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=1, fetch=fetch, sleep=lambda s: None,
            )

    def test_hash_mismatch_rejected(self):
        fetch = lambda request, timeout: _FakeResponse(_record(sha1="deadbeef"))
        with self.assertRaises(SystemExit):
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=1, fetch=fetch, sleep=lambda s: None,
            )

    def test_never_matching_fails_after_all_attempts(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            return _not_found(request)

        sleeps = []
        with self.assertRaises(SystemExit):
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=3, fetch=fetch, sleep=sleeps.append,
            )
        self.assertEqual(calls["n"], 3)
        self.assertEqual(len(sleeps), 2)

    def test_retries_through_503_then_succeeds(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            if calls["n"] < 3:
                return _server_error(request)
            return _FakeResponse(_record())

        sleeps = []
        result = verify_modrinth_publish.verify(
            "proj1", "0.20.0-alpha.1", ARTIFACT, "token", fetch=fetch, sleep=sleeps.append,
        )
        self.assertEqual(result["modrinth_version_id"], "modrinth-version-id")
        self.assertEqual(calls["n"], 3)
        self.assertEqual(len(sleeps), 2)

    def test_retries_through_network_error_then_succeeds(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            if calls["n"] < 3:
                raise URLError("connection reset")
            return _FakeResponse(_record())

        sleeps = []
        result = verify_modrinth_publish.verify(
            "proj1", "0.20.0-alpha.1", ARTIFACT, "token", fetch=fetch, sleep=sleeps.append,
        )
        self.assertEqual(result["modrinth_version_id"], "modrinth-version-id")
        self.assertEqual(calls["n"], 3)
        self.assertEqual(len(sleeps), 2)

    def test_5xx_exhausted_retries_fails_with_clear_error(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            return _server_error(request)

        sleeps = []
        with self.assertRaises(SystemExit) as context:
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=3, fetch=fetch, sleep=sleeps.append,
            )
        self.assertEqual(calls["n"], 3)
        self.assertEqual(len(sleeps), 2)
        self.assertIn("::error::", str(context.exception))

    def test_401_fails_immediately_without_retry(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            raise HTTPError(request.full_url, 401, "unauthorized", hdrs=None, fp=None)

        sleeps = []
        with self.assertRaises(SystemExit) as context:
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=5, fetch=fetch, sleep=sleeps.append,
            )
        self.assertEqual(calls["n"], 1)
        self.assertEqual(len(sleeps), 0)
        self.assertIn("::error::", str(context.exception))

    def test_403_fails_immediately_without_retry(self):
        calls = {"n": 0}

        def fetch(request, timeout):
            calls["n"] += 1
            raise HTTPError(request.full_url, 403, "forbidden", hdrs=None, fp=None)

        sleeps = []
        with self.assertRaises(SystemExit):
            verify_modrinth_publish.verify(
                "proj1", "0.20.0-alpha.1", ARTIFACT, "token",
                attempts=5, fetch=fetch, sleep=sleeps.append,
            )
        self.assertEqual(calls["n"], 1)
        self.assertEqual(len(sleeps), 0)


if __name__ == "__main__":
    unittest.main()
