# Dynamic Atmosphere

Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Mod ID: `dynamicatmosphere`.
MIT licensed.

## Water Fog Alpha

This version adds persistent, bounded fog patches over water near players.
Deeper water produces denser fog. Patches build up and fade rather than existing
only for a single sampling pass. The original high-terrain cloud effect remains.
It is **not** the planned terrain-aware atmospheric simulation. No precipitation,
pollution, gas transport, or world generation changes are included yet.

Effects run on the server using vanilla particles. Install on the server or in
a single-player NeoForge instance; multiplayer clients need no special renderer.
Client particle settings affect visibility. No world reset is needed.

Operators can run `/dynamicatmosphere status` to inspect runtime counters and
`/dynamicatmosphere demo` as a player to emit visible particles immediately.

## Development

Set `JAVA_HOME` to a JDK 21 installation, then run:

```sh
./gradlew build --no-daemon
```

The jar is `forge/build/libs/dynamicatmosphere-<version>.jar`.
`version.txt` is the default version; CI passes the same value as `-Pmod_version`.

- `engine/` is a pure Java library. Its tests and `verifyNoMinecraft` task enforce
  that Minecraft and NeoForge are absent from its classpath.
- `forge/` contains the Minecraft adapter and packages the engine's compiled
  classes directly into the shipped jar. The visual spike does not depend on
  the unfinished material simulation.

## Automated Releases

Change `version.txt` and `CHANGELOG.md` with a release-worthy change. Merging to
`main` automatically builds and tests, creates a GitHub release with the jar,
syncs the Modrinth description, and publishes the native NeoForge version there.
GitHub is the primary artifact source. Workflow dispatch retries the checked-in
version; it is a **live publish**, not a dry run. Existing artifacts are never
overwritten. An existing Modrinth version is reused only if its hash and metadata
match; a mismatch fails rather than replacing a release.

Repository configuration:

- Secret `MODRINTH_TOKEN` with project/version write permission.
- Variable `MODRINTH_PROJECT_ID`: `PZV7RorC`.
- `modrinth/body.md`: formatted listing description.
- `modrinth/short-description.txt`: listing summary.
- `modrinth/project.json`: current Modrinth environment and initial submission policy.

Pre-release versions use the alpha channel. While this project is 0.x, use a
minor bump for added capability or incompatible changes, a patch for compatible
fixes, and increment the prerelease suffix for revisions to an unreleased alpha.
Never reuse a published version. Documentation-only changes need no release.

Modrinth moderation is separate from artifact publication. CI submits a draft
after publication when `submit_for_review` is enabled; moderator approval is
still external. Changes under `modrinth/` also trigger the idempotent sync/release
workflow without changing the artifact version. The existing draft-create
workflow is only for initial provisioning; do not create a second project.

## Runtime Verification

Test the actual built jar in a disposable NeoForge 21.1.250 server before adding
it to Sickos. Require a clean `Done` startup, the mod's startup log, a successful
`dynamicatmosphere status` command, and increasing tick/pass counters. Join with
a client and run the demo to verify rendering. A build alone is not runtime proof.

After adding the published Modrinth version to Sickos, use Sickos' normal version
bump and CI release/deploy path. Verify the hosted jar hash, restarted server, and
runtime command; retain the existing world.
