"""Sync current Modrinth v3 environment metadata after version publication."""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen

project = os.environ["PROJECT_ID"]
settings = json.loads(Path("modrinth/project.json").read_text())
headers = {"Authorization": os.environ["MODRINTH_TOKEN"],
           "User-Agent": "brooswit-minecraft/dynamic-atmosphere-release",
           "Content-Type": "application/json"}


def api(path, patch=None):
    request = Request(f"https://api.modrinth.com/v3/project/{project}{path}",
                      data=None if patch is None else json.dumps(patch).encode(),
                      headers=headers, method="GET" if patch is None else "PATCH")
    with urlopen(request, timeout=30) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


api("", {"environment": settings["environment"],
         "side_types_migration_review_status": "reviewed"})
result = api("")
if result.get("environment") != [settings["environment"]]:
    raise SystemExit("Modrinth environment read-back did not match repository settings")
print("Verified Modrinth environment:", settings["environment"])
if settings.get("submit_for_review") and result["status"] in ("draft", "rejected"):
    api("", {"status": "processing"})
    result = api("")
    if result["status"] not in ("processing", "approved", "unlisted"):
        raise SystemExit("Project submission did not take effect")
print("Modrinth project status:", result["status"])
