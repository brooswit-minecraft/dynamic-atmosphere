# changelog.d/ — per-PR changelog fragments

Every PR that changes the gated implementation paths must add exactly one new
`changelog.d/<TICKET>.md` fragment. The release workflow collates fragments added
since the latest reachable DA alpha tag, bumps by the highest level, and starts
the new version at `-alpha.1`. Notes are generated in CI and the source commit is
tagged; no version/changelog commit is pushed back to main. Tags record consumed
fragments, which remain in the repository. Add a new fragment instead of editing
a previously released one. Do not edit the shared changelog heading or version
file directly in a feature PR.

## Format

```
bump: patch

### Fixed
- Describe the user-visible correction.
```

Supported bump values are `major`, `minor`, and `patch`; use a `BREAKING`
section only with a `major` bump.
