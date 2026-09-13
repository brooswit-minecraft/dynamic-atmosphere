# Dynamic Atmosphere

Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Mod ID: `dynamicatmosphere`.
MIT licensed.

## Atmospheric Grid Alpha

The server maintains a world-aligned atmospheric grid of 4x4x4-block cells.
Water fog, high-terrain clouds, rain landing on exposed surfaces, and dark exposed ground add material to their cells. Material
decays in place; it never travels between cells. Nearby clients receive a snapshot
and batched changes, and render translucent cells with opacity based on material.
This is **not** the full terrain-aware atmospheric simulation. No generated precipitation,
pollution, gas transport, or world generation changes are included yet.

Rain uses Minecraft's local rain/exposure check at the topmost landing surface,
including roofs and canopies. Each sampled rainy position contributes 40 material
units every five seconds, independently of water/cloud sources; each cell loses
10 units per pass. Dry biomes, snow, and sheltered ground do not emit rain material.
The mod does not create rain or change the world's weather.

Exposed non-fluid ground also emits according to effective light: zero at light
15, rising to 40 units per pass at light 0. This uses the day/night-adjusted sky
light combined with local block lighting. Daylight stops this source, allowing
normal decay to clear overnight fog. Water/rain/cloud sources remain independent.

Install this version on **both server and client**, or in a single-player NeoForge
instance. Earlier particle-only releases did not require a client installation;
the new grid renderer and sync protocol do. Update both sides together.
No world reset is needed. Grid state is transient, not saved terrain.

Operators can run `/dynamicatmosphere status` to inspect runtime counters and
`/dynamicatmosphere demo` as a player to add material nearby for visual testing.

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
  classes directly into the shipped jar. The atmospheric grid does not depend on
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
- `modrinth/project.json`: this release's Modrinth environment and submission policy.
  CI applies the environment only to the version in `version.txt`, preserving
  older releases' compatibility metadata.

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
