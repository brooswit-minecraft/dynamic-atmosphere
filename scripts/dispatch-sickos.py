"""Build the dynamic-atmosphere-released payload and dispatch it to sickos.

Contract (docs/release-dispatch-contract.md has the durable copy, agreed with
the sickos side building against it — treat names, keys and values as fixed):
- event_type: dynamic-atmosphere-released
- client_payload: exactly these 8 string keys: version, modrinth_version_id,
  sha1, sha512, download_url, file_name, category, github_release_url.
- Fires only when THIS run newly published the version (never on a re-run
  that found it already on Modrinth) - idempotency lives here, in code, not
  only in a YAML `if:`, so it is unit-tested.
- A missing dispatch credential skips with a loud warning and never fails
  the release. A dispatch HTTP failure with a credential present fails the
  job loudly, since the Modrinth publish already happened.
- The exact payload is written to a workflow artifact on every release run,
  before and independently of sending, so any run's payload can be inspected
  even when nothing was actually dispatched.
"""
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

DISPATCH_URL = "https://api.github.com/repos/brooswit-minecraft/sickos/dispatches"
EVENT_TYPE = "dynamic-atmosphere-released"
PAYLOAD_KEYS = (
    "version", "modrinth_version_id", "sha1", "sha512",
    "download_url", "file_name", "category", "github_release_url",
)
DEFAULT_ARTIFACT_PATH = "dynamic-atmosphere-released.json"


def build_payload(*, version, modrinth_version_id, sha1, sha512,
                   download_url, file_name, category, github_release_url):
    payload = {
        "version": version,
        "modrinth_version_id": modrinth_version_id,
        "sha1": sha1,
        "sha512": sha512,
        "download_url": download_url,
        "file_name": file_name,
        "category": category,
        "github_release_url": github_release_url,
    }
    missing = [key for key in PAYLOAD_KEYS if not payload.get(key)]
    if missing:
        raise ValueError(f"Missing required dispatch payload field(s): {', '.join(missing)}")
    if set(payload) != set(PAYLOAD_KEYS):
        raise ValueError("Dispatch payload must have exactly the 8 contract keys, no more.")
    non_strings = [key for key, value in payload.items() if not isinstance(value, str)]
    if non_strings:
        raise ValueError(f"Dispatch payload value(s) must be strings: {', '.join(non_strings)}")
    return payload


def should_dispatch(pre_publish_exists):
    """Dispatch only for a run that newly published; never for a found-existing re-run."""
    return pre_publish_exists != "true"


def resolve_token(env):
    pat = env.get("SICKOS_DISPATCH_TOKEN", "")
    if pat:
        return pat
    app_token = env.get("SICKOS_DISPATCH_APP_TOKEN", "")
    if app_token:
        return app_token
    return None


def send_dispatch(payload, token, *, post=None):
    post = post or _http_post
    body = json.dumps({"event_type": EVENT_TYPE, "client_payload": payload}).encode()
    request = urllib.request.Request(
        DISPATCH_URL,
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
            "User-Agent": "brooswit-minecraft/dynamic-atmosphere-release",
        },
    )
    return post(request)


def _http_post(request):
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status


def _write_summary(message, env):
    summary_path = env.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as handle:
            handle.write(message + "\n")


def main(argv=None, env=None, post=None):
    argv = sys.argv[1:] if argv is None else argv
    env = os.environ if env is None else env

    payload = build_payload(
        version=env["VERSION"],
        modrinth_version_id=env["MODRINTH_VERSION_ID"],
        sha1=env["SHA1"],
        sha512=env["SHA512"],
        download_url=env["DOWNLOAD_URL"],
        file_name=env["FILE_NAME"],
        category=env["CATEGORY"],
        github_release_url=env["GITHUB_RELEASE_URL"],
    )

    artifact_path = Path(argv[0] if argv else env.get("DISPATCH_PAYLOAD_PATH", DEFAULT_ARTIFACT_PATH))
    # No sort_keys: payload must keep PAYLOAD_KEYS' contract order, matching
    # the shared fixture byte-for-byte (see FixtureRoundTripTest).
    artifact_path.write_text(json.dumps(payload, indent=2) + "\n")
    print(f"Wrote dispatch payload to {artifact_path}")

    if not should_dispatch(env.get("MODRINTH_PRE_PUBLISH_EXISTS", "")):
        print("Skipping sickos dispatch: this run did not newly publish a Modrinth version "
              "(re-run of an already-published version).")
        return 0

    token = resolve_token(env)
    if token is None:
        message = ("::warning::No SICKOS_DISPATCH_TOKEN or SICKOS_DISPATCH_APP_* credential "
                   "is configured; skipping the dynamic-atmosphere-released dispatch to sickos. "
                   "The release itself is unaffected.")
        print(message)
        _write_summary(message, env)
        return 0

    try:
        send_dispatch(payload, token, post=post)
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace") if hasattr(error, "read") else ""
        raise SystemExit(
            f"::error::Failed to dispatch {EVENT_TYPE} to sickos (HTTP {error.code}): {body}\n"
            "The Modrinth publish already succeeded; this workflow will NOT retry the dispatch "
            "automatically (re-running would just skip it again as already-published). "
            f"To resend by hand: POST the contents of {artifact_path} as client_payload to "
            f"{DISPATCH_URL} with event_type={EVENT_TYPE}, authenticated with a token authorized "
            "to dispatch to brooswit-minecraft/sickos."
        )

    print(f"Dispatched {EVENT_TYPE} to sickos.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
