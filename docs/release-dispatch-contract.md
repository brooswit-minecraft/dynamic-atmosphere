# Release / sickos dispatch contract

This is the durable record of the cross-repo interface between this repo's
`release.yml` and the `brooswit-minecraft/sickos` modpack, agreed between the
two repo leads (ATMO-14 / SICKOS-70). Confluence doc creation is currently
broken for the ATMO Jira project (ATMO-16), so this file — not a Confluence
page — is the source of truth. Treat the names, keys and values below as
fixed; a change to any of them needs cross-repo agreement first.

## 1. CHANGELOG `Category` marker

Every `CHANGELOG.md` version section (a top-level `# <version>` heading, e.g.
`# 0.19.0-alpha.1`) that gets released must declare **exactly one** line:

```
Category: patch
```

with value `patch`, `minor`, or `breaking`. `release.yml` validates this
*before* building or publishing anything, and fails the release loudly
(`::error::`, non-zero exit) if:

- there is no section for the version in `version.txt`,
- the marker is missing,
- the marker's value is not one of the three above,
- more than one marker appears in the section, or
- the category is `breaking` and the section has no non-empty `## Migration`
  subsection.

`## Migration` is allowed (but not required) in `patch`/`minor` sections.
Only the section being released is validated — historical sections are never
backfilled with a marker.

A `breaking` section looks like this (this exact shape is a test fixture in
`scripts/test_changelog.py`, since it is close to what the ATMO-7 feature
release will write):

```markdown
# 0.20.0-alpha.1

Category: breaking

- ...feature bullets...

## Migration

- Saved dust, ender, exhaust, void and slime gas is dropped on load.
- `violence` config resets to defaults under `voidGas`.
- Client and server must run the same version.
- Back up your world first.
```

Validation and release-notes logic live in `scripts/changelog.py`
(`scripts/test_changelog.py` covers it, including a test that runs the
validator against this repo's own real `CHANGELOG.md` + `version.txt`, so a
missing marker fails CI on a PR rather than a release on `main`).

## 2. GitHub release body

`gh release create` no longer uses the whole `CHANGELOG.md` as release notes.
Instead, `release.yml` generates a notes file (`scripts/changelog.py notes`)
containing **only** the released version's section, with the lowercase
`category: <value>` line prepended:

```
category: breaking

# 0.20.0-alpha.1

Category: breaking

- ...feature bullets...

## Migration

- ...
```

The `# <version>` heading and the section's own `Category:` line travel with
the body unmodified — the `## Migration` heading stays *inside* the new
version's section, because sickos's extractor copies that section's Migration
text verbatim from the release body.

The Modrinth `--changelog-file CHANGELOG.md` argument, and the existing
already-exists/immutable-release retry branch, are unchanged.

## 3. Modrinth read-back

A Modrinth publish step exiting `0` is not sufficient proof the version is
live — `release.yml` also runs `scripts/verify-modrinth-publish.py`, which:

- **Authenticates** with the existing `MODRINTH_TOKEN`. The DA Modrinth
  project is not currently publicly visible (anonymous API calls 404); this
  is a known, separate problem (see main ticket history) and the read-back
  must not depend on anonymous visibility.
- Looks the version up by the built jar's sha512 via
  `/v2/version_file/{sha512}?algorithm=sha512` (the same endpoint
  `scripts/check-modrinth-version.py` uses, because the project version
  listing can lag a fresh publish).
- Verifies the response's project id, `version_number`, and the file's sha1
  **and** sha512 all match the jar actually built in this run.
- Retries with a short bounded backoff (freshly published versions can take a
  moment to appear), and fails the job if it never matches.

This step runs unconditionally, including on a re-run that found the version
already on Modrinth (it re-confirms rather than trusting a prior run).

## 4. Dispatch to sickos

After a successful read-back, `release.yml` runs
`scripts/dispatch-sickos.py`, which:

1. **Always** writes the exact payload to `dynamic-atmosphere-released.json`
   and uploads it as a workflow artifact — before and independently of
   sending — so any run's payload can be inspected even when nothing was
   dispatched.
2. Sends `POST https://api.github.com/repos/brooswit-minecraft/sickos/dispatches`
   with:
   - `event_type`: `dynamic-atmosphere-released`
   - `client_payload`: **exactly** these 8 string keys (GitHub's hard limit
     is 10): `version`, `modrinth_version_id`, `sha1`, `sha512`,
     `download_url` (the Modrinth CDN file URL from the read-back),
     `file_name`, `category`, `github_release_url`.

     A 9th key (`listing_changes`, to carry non-breaking release notes) was
     proposed and declined for now — `## Migration` already covers the rare
     case for humans; it can be added later since 9 keys is still under the
     limit of 10.

     The shared contract fixture lives at
     `scripts/fixtures/dynamic-atmosphere-released.example.json`, committed
     verbatim from sickos's own
     `tests/fixtures/dynamic-atmosphere-released.example.json` (SICKOS-75,
     now on sickos `main`) — see `scripts/fixtures/README.md` for the source
     commit and pinned sha256. From here on DA's copy is the source of truth
     and sickos checks for drift against it. `scripts/test_dispatch_sickos.py`
     pins the fixture's sha256 and asserts the payload builder's output
     matches its key set, key order, and types.

### When it fires

- **Once per newly published version**, and **never** on a re-run that found
  the version already on Modrinth. This idempotency decision lives in
  `scripts/dispatch-sickos.py::should_dispatch` (fed the pre-publish `exists`
  output from the "Check existing Modrinth version" step), not only in a
  workflow `if:`, so it is unit-tested.
- Only after the authenticated read-back above has confirmed the artifact.

### Credential paths (the asymmetry is deliberate)

1. If secret `SICKOS_DISPATCH_TOKEN` (a fine-grained PAT) is non-empty, use
   it.
2. Otherwise, if secret `SICKOS_DISPATCH_APP_PRIVATE_KEY` and Actions
   variable `SICKOS_DISPATCH_APP_ID` are both set, mint a token with
   `actions/create-github-app-token` (owner `brooswit-minecraft`,
   repositories `sickos`).
3. If **neither** is configured: skip the dispatch with a loud `::warning::`
   (also written to `$GITHUB_STEP_SUMMARY`) and **exit 0** — a missing
   dispatch credential never fails the release. As of this writing none of
   the three secrets/variables are configured in this repo, so this is the
   path that runs by default.

`secrets.*` cannot be tested directly in a step `if:`, so credential presence
is routed through a dedicated step's outputs (`has_pat` / `has_app`) in
`release.yml`, not inlined into a conditional.

### Manual re-send recovery

A dispatch HTTP failure **with** a credential present fails the release job
loudly — the Modrinth publish has already happened, so this is not silent.
The failure message points at the uploaded
`dynamic-atmosphere-released.json` workflow artifact from that run: re-send
it by hand with

```
POST https://api.github.com/repos/brooswit-minecraft/sickos/dispatches
Authorization: Bearer <a token authorized to dispatch to brooswit-minecraft/sickos>
Accept: application/vnd.github+json
Content-Type: application/json

{"event_type": "dynamic-atmosphere-released", "client_payload": <the artifact's contents>}
```

A re-run of the workflow will **not** re-send it automatically (idempotency
above is by design), so the manual POST above is the only recovery path.
