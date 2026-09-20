"""Authenticated read-back proving Modrinth actually holds the published jar.

"The publish step exited 0" is not sufficient: this looks the version up by
the jar's sha512 (freshly published versions can lag the project listing, so
this uses the same /v2/version_file endpoint check-modrinth-version.py uses)
and checks project id, version_number, and both the sha1 and sha512 of the
file against the jar actually built in this run. It retries with a short
bounded backoff, since a freshly published version can take a moment to
appear, and raises (failing the job) if it never matches.
"""
import hashlib
import json
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

USER_AGENT = "brooswit-minecraft/dynamic-atmosphere-release"


def lookup_version_file(sha512, token, fetch):
    request = Request(
        f"https://api.modrinth.com/v2/version_file/{sha512}?algorithm=sha512",
        headers={"Authorization": token, "User-Agent": USER_AGENT},
    )
    try:
        with fetch(request, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        if error.code == 404:
            return None
        raise


def _matching_file(record, sha512):
    for file_entry in record.get("files", []):
        if file_entry.get("hashes", {}).get("sha512") == sha512:
            return file_entry
    return None


def verify(project_id, version, artifact, token, *, attempts=5, delay_seconds=5,
           fetch=urlopen, sleep=time.sleep):
    """Return the read-back fields, or raise SystemExit after exhausting retries."""
    sha1 = hashlib.sha1(artifact).hexdigest()
    sha512 = hashlib.sha512(artifact).hexdigest()
    last_error = "Modrinth read-back never ran."

    for attempt in range(1, attempts + 1):
        try:
            record = lookup_version_file(sha512, token, fetch)
        except HTTPError as error:
            if error.code in (401, 403):
                raise SystemExit(
                    f"::error::Modrinth read-back failed: HTTP {error.code} from "
                    f"Modrinth ({error.reason}); not retrying."
                )
            last_error = f"HTTP {error.code} from Modrinth: {error.reason}"
            if attempt < attempts:
                sleep(delay_seconds)
            continue
        except (URLError, TimeoutError) as error:
            last_error = f"network error contacting Modrinth: {error}"
            if attempt < attempts:
                sleep(delay_seconds)
            continue

        if record is None:
            last_error = f"no version_file match yet for sha512 {sha512[:12]}..."
        elif record.get("project_id") != project_id:
            last_error = (
                f"version_file matched a different project: expected {project_id!r}, "
                f"got {record.get('project_id')!r}."
            )
        elif record.get("version_number") != version:
            last_error = (
                f"version_file matched a different version: expected {version!r}, "
                f"got {record.get('version_number')!r}."
            )
        else:
            file_entry = _matching_file(record, sha512)
            if file_entry is None:
                last_error = "version_file record has no file entry with the expected sha512."
            elif file_entry.get("hashes", {}).get("sha1") != sha1:
                last_error = (
                    f"sha1 mismatch: built jar is {sha1}, Modrinth file entry is "
                    f"{file_entry.get('hashes', {}).get('sha1')}."
                )
            else:
                return {
                    "modrinth_version_id": record["id"],
                    "sha1": sha1,
                    "sha512": sha512,
                    "download_url": file_entry["url"],
                    "file_name": file_entry["filename"],
                }
        if attempt < attempts:
            sleep(delay_seconds)

    raise SystemExit(f"::error::Modrinth read-back failed after {attempts} attempts: {last_error}")


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    project_id = os.environ["PROJECT_ID"]
    version = os.environ["VERSION"]
    token = os.environ["MODRINTH_TOKEN"]
    jar_path = argv[0] if argv else f"forge/build/libs/dynamicatmosphere-{version}.jar"
    artifact = Path(jar_path).read_bytes()

    result = verify(project_id, version, artifact, token)
    for key, value in result.items():
        print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
