# changelog.d/ — per-PR changelog fragments

Every PR that changes a release-gated path adds exactly one fragment named
`changelog.d/<TICKET>.md`. Do not edit a shared unreleased changelog section.

Each fragment starts with `bump: major`, `bump: minor`, or `bump: patch`, then
uses `### Added`, `### Changed`, `### Fixed`, or `### Removed` headings with
bullet points. A major bump also requires a `### BREAKING` heading.
