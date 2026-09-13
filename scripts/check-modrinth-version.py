"""Allow release retries only when Modrinth already holds the exact artifact."""
import hashlib
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen


def matches(version, expected_version, artifact):
    return (
        version["version_number"] == expected_version
        and version["loaders"] == ["neoforge"]
        and version["game_versions"] == ["1.21.1"]
        and version["version_type"] == "alpha"
        and len(version["files"]) == 1
        and version["files"][0]["hashes"]["sha512"]
        == hashlib.sha512(artifact).hexdigest()
    )


if __name__ == "__main__":
    project = os.environ["PROJECT_ID"]
    version = os.environ["VERSION"]
    request = Request(
        f"https://api.modrinth.com/v2/project/{project}/version",
        headers={"Authorization": os.environ["MODRINTH_TOKEN"],
                 "User-Agent": "brooswit-minecraft/dynamic-atmosphere-release"},
    )
    with urlopen(request, timeout=30) as response:
        versions = json.load(response)
    existing = [v for v in versions if v["version_number"] == version]
    if existing:
        artifact = Path(f"forge/build/libs/dynamicatmosphere-{version}.jar").read_bytes()
        if len(existing) != 1 or not matches(existing[0], version, artifact):
            raise SystemExit("Existing Modrinth version differs; bump version.txt. Never overwrite.")
    print(f"exists={'true' if existing else 'false'}")
