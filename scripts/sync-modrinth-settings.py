"""Sync this release's environment without relabeling older artifacts."""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

def sync(api, project, version, settings):
    versions = api(f"/project/{project}/version")
    matches = [item for item in versions if item["version_number"] == version]
    if len(matches) != 1:
        raise ValueError("Expected exactly one published release version")
    target = f'/version/{matches[0]["id"]}'
    api(target, {"environment": settings["environment"]})
    if api(target).get("environment") != settings["environment"]:
        raise ValueError("Version environment read-back did not match repository settings")
    # The project's environment list is derived from all its versions.
    api(f"/project/{project}", {"side_types_migration_review_status": "reviewed"})
    result = api(f"/project/{project}")
    if settings["environment"] not in result.get("environment", []):
        raise ValueError("Project environment does not include this release")
    if settings.get("submit_for_review") and result["status"] in ("draft", "rejected"):
        api(f"/project/{project}", {"status": "processing"})
        result = api(f"/project/{project}")
        if result["status"] not in ("processing", "approved", "unlisted"):
            raise ValueError("Project submission did not take effect")
    return result["status"]


def main():
    headers = {"Authorization": os.environ["MODRINTH_TOKEN"],
               "User-Agent": "brooswit-minecraft/dynamic-atmosphere-release",
               "Content-Type": "application/json"}

    def api(path, patch=None):
        request = Request(f"https://api.modrinth.com/v3{path}",
                          data=None if patch is None else json.dumps(patch).encode(),
                          headers=headers, method="GET" if patch is None else "PATCH")
        with urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None

    settings = json.loads(Path("modrinth/project.json").read_text())
    version = os.environ.get("VERSION") or Path("version.txt").read_text().strip()
    status = sync(api, os.environ["PROJECT_ID"], version, settings)
    print("Verified release environment:", settings["environment"])
    print("Modrinth project status:", status)


if __name__ == "__main__":
    main()
