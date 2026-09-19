"""Parse and validate the CHANGELOG.md Category marker, and build release notes.

Contract (docs/release-dispatch-contract.md has the durable copy):
- Each `# <version>` section declares exactly one `Category: patch|minor|breaking` line.
- `breaking` sections require a non-empty `## Migration` subsection.
- The generated GitHub release notes start with `category: <value>` (lowercase
  key) followed by the released section verbatim, so sickos can copy its
  `## Migration` text unmodified.
"""
import argparse
import re
import sys
from pathlib import Path

VALID_CATEGORIES = ("patch", "minor", "breaking")

_TOP_HEADING = re.compile(r"^# (.+?)\s*$", re.MULTILINE)
_CATEGORY_LINE = re.compile(r"^Category:[ \t]*(\S*)[ \t]*$", re.MULTILINE)
_MIGRATION_HEADING = re.compile(r"^## Migration[ \t]*$", re.MULTILINE)
_ANY_HEADING = re.compile(r"^#{1,2} ", re.MULTILINE)


def extract_section(changelog_text, version):
    """Return the full `# <version>` section, heading included, verbatim."""
    match = None
    for candidate in _TOP_HEADING.finditer(changelog_text):
        if candidate.group(1) == version:
            match = candidate
            break
    if match is None:
        raise ValueError(f"No CHANGELOG.md section found for version {version!r}.")
    start = match.start()
    next_heading = _TOP_HEADING.search(changelog_text, match.end())
    end = next_heading.start() if next_heading else len(changelog_text)
    return changelog_text[start:end].rstrip("\n")


def parse_category(section_text):
    """Return the section's Category value, enforcing exactly one valid marker."""
    matches = _CATEGORY_LINE.findall(section_text)
    if not matches:
        raise ValueError("Missing `Category:` marker in the released version's section.")
    if len(matches) > 1:
        raise ValueError(
            f"Found {len(matches)} `Category:` markers in the released version's "
            "section; exactly one is required."
        )
    value = matches[0]
    if value not in VALID_CATEGORIES:
        raise ValueError(
            f"Invalid Category value {value!r}; must be one of {', '.join(VALID_CATEGORIES)}."
        )
    return value


def _migration_body(section_text):
    heading = _MIGRATION_HEADING.search(section_text)
    if heading is None:
        return None
    next_heading = _ANY_HEADING.search(section_text, heading.end())
    end = next_heading.start() if next_heading else len(section_text)
    return section_text[heading.end():end]


def validate_migration(section_text, category):
    """`breaking` sections must carry a non-empty `## Migration` subsection."""
    if category != "breaking":
        return
    body = _migration_body(section_text)
    if body is None:
        raise ValueError(
            "Category: breaking requires a `## Migration` subsection; none was found."
        )
    if not body.strip():
        raise ValueError(
            "Category: breaking requires a non-empty `## Migration` subsection; "
            "the one found has no content."
        )


def validate_section(changelog_text, version):
    """Extract, then fully validate, the released version's section.

    Returns (category, section_text). Raises ValueError with a human-readable
    message on any contract violation.
    """
    section_text = extract_section(changelog_text, version)
    category = parse_category(section_text)
    validate_migration(section_text, category)
    return category, section_text


def build_release_notes(section_text, category):
    """Generated GitHub release notes: `category: <value>` then the section verbatim."""
    return f"category: {category}\n\n{section_text}\n"


def _read_version(version_file):
    return Path(version_file).read_text().strip()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["validate", "notes"])
    parser.add_argument("--changelog", default="CHANGELOG.md")
    parser.add_argument("--version-file", default="version.txt")
    parser.add_argument("--out")
    args = parser.parse_args(argv)

    changelog_text = Path(args.changelog).read_text()
    version = _read_version(args.version_file)

    try:
        category, section_text = validate_section(changelog_text, version)
    except ValueError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    if args.command == "validate":
        print(f"category={category}")
        return 0

    notes = build_release_notes(section_text, category)
    if args.out:
        Path(args.out).write_text(notes)
    else:
        print(notes, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
