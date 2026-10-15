"""Plan fragment-driven DA alpha releases; never edit or commit tracked files."""
import argparse
import os
from pathlib import Path
import re
import subprocess

from changelog import build_release_notes, validate_section

TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)-alpha\.(\d+)$")
LEVELS = {"patch": 0, "minor": 1, "major": 2}


def render_release(last, fragments):
    match = TAG.fullmatch("v" + last)
    if not match:
        raise ValueError("Expected a DA x.y.z-alpha.N release baseline")
    sections, levels = [], []
    for name, text in fragments:
        lines = text.splitlines()
        if not lines or not re.fullmatch(r"bump: (patch|minor|major)", lines[0]):
            raise ValueError(f"{name}: missing or invalid bump header")
        levels.append(lines[0].split(": ", 1)[1])
        body = "\n".join(lines[1:]).strip()
        if not body:
            raise ValueError(f"{name}: empty fragment")
        sections.append(body)
    if not levels:
        raise ValueError("No release fragments")
    level = max(levels, key=LEVELS.get)
    major, minor, patch, _ = map(int, match.groups())
    if level == "major":
        major, minor, patch = major + 1, 0, 0
    elif level == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    version = f"{major}.{minor}.{patch}-alpha.1"
    category = "breaking" if level == "major" else level
    section = f"# {version}\n\nCategory: {category}\n\n" + "\n\n".join(sections) + "\n"
    # Reuse the existing fence-aware Category/Migration contract. A major
    # fragment must supply real ## Migration instructions before publication.
    category, section = validate_section(section, version)
    return version, section + "\n", build_release_notes(section, category)


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manual", action="store_true")
    args = parser.parse_args()
    tags = [t for t in git("tag", "--merged", "HEAD").splitlines() if TAG.fullmatch(t)]
    if not tags:
        raise ValueError("No reachable DA alpha release tag; establish a reviewed baseline first")
    tag = max(tags, key=lambda t: tuple(map(int, TAG.fullmatch(t).groups())))
    names = git("diff", "--name-only", "--diff-filter=A", tag, "HEAD", "--", "changelog.d").splitlines()
    names = [p for p in names if p.endswith(".md") and p != "changelog.d/README.md"]
    if names:
        version, section, notes = render_release(tag[1:], [(p, Path(p).read_text()) for p in names])
        if f"v{version}" in git("tag", "--list").splitlines():
            raise ValueError(f"Tag v{version} already exists outside the release baseline; refuse collision")
        new = "true"
    elif args.manual or git("rev-list", "-n", "1", tag) == git("rev-parse", "HEAD"):
        version, new = tag[1:], "false"
        notes = subprocess.check_output(["gh", "release", "view", tag, "--json", "body", "--jq", ".body"], text=True)
        _, section = validate_section(notes, version)
    else:
        print("No new fragments; nothing to publish.")
        return
    Path("release-version.txt").write_text(version + "\n")
    Path("release-changelog.md").write_text(section)
    Path("release-notes.md").write_text(notes)
    with open(os.environ["GITHUB_OUTPUT"], "a") as out:
        out.write(f"value={version}\nnew={new}\n")


if __name__ == "__main__":
    main()
