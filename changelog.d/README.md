# changelog.d/ — per-PR changelog fragments

Every PR that changes the gated implementation paths must add exactly one new
`changelog.d/<TICKET>.md` fragment. The release gate collates fragments at
merge time, assigns the release version, updates `CHANGELOG.md`, and removes
the consumed fragments. Do not edit the shared changelog heading or version
file directly in a feature PR.

## Format

```
bump: patch

### Fixed
- Describe the user-visible correction.
```

Supported bump values are `major`, `minor`, and `patch`; use a `BREAKING`
section only with a `major` bump.
