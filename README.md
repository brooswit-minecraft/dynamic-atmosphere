# Dynamic Atmosphere

Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Mod ID: `dynamicatmosphere`.
MIT licensed.

## Atmospheric Grid Alpha

**BREAKING behavior in 0.6.0-alpha.1: pressure destruction is enabled by default
and can damage terrain and builds. Back up the world before upgrading. There is
no claim/protected-area integration.**

The server maintains a world-aligned atmospheric grid of 4x4x4-block cells.
Water fog, high-terrain clouds, rain landing on exposed surfaces, and dark exposed
ground add material to their cells. There is no natural decay: material spreads
by equalizing fullness across the six face-adjacent cells, not diagonally.
Capacity is proportional to the number of air blocks in each cell (0..64).
Fullness is material amount divided by capacity: fewer vacant blocks need less
material to fill. Transfer uses positive-capacity neighbors; zero-air cells block it.
If terrain reduces capacity below the stored amount, excess is pushed farther
outward toward the nearest available capacity through neighboring air-capacity
cells. This search is bounded to the loaded active region. Confirmed blockage
can trigger bounded destructive pressure relief (below). Unknown unloaded
boundaries and exhausted work/search budgets leave work pending, never authorize
pressure destruction, and do not discard material.
Excess that still cannot escape remains blocked and reported, not discarded;
displacement is not unlimited.
In 0.6.1-alpha.1, simulation/source cadence is `25 * cellSize / 16` ticks:
the old 100-tick base is quartered, then scaled by cell size. Current 4-block
cells average **6.25 ticks** using 6/7-tick intervals. Size 16 would use 25 ticks,
size 1 averages 1.5625, and size 32 uses 50. Actual progress remains work-budgeted.
Producer offsets now reach 96 blocks instead of 12, using the same eight sampled
positions per pass and loaded-only checks. Visibility follows Minecraft's actual
tracked chunks and the client's effective render distance, loaded chunks, and
frustum, with no fixed atmospheric radius or nearest-cell cutoff. Delta sync stays every 20 ticks, full snapshots
every 200 ticks, and simulation processes at most 128 source cells per tick.
Nearby clients receive a snapshot
and batched changes, and render translucent cells with opacity based on fullness.
This is **not** the full terrain-aware atmospheric simulation. No generated precipitation,
pollution, gas transport, or world generation changes are included yet.

Rain uses Minecraft's local rain/exposure check at the topmost landing surface,
including roofs and canopies. Each sampled rainy position contributes 40 material
units per sampling pass, independently of water/cloud sources. Dry biomes, snow,
and sheltered ground do not emit rain material.
The mod does not create rain or change the world's weather.

Exposed non-fluid ground also emits according to effective light: zero at light
15, rising to 40 units per pass at light 0. This uses the day/night-adjusted sky
light combined with local block lighting. Full daylight stops this source, but
does not clear existing fog: material continues to spread without natural decay.
Water/rain/cloud sources remain independent.

The air-count rule is coarse, not an exact airtight-wall simulation. It does
not inspect every shared-face opening, and rendered cells are not clipped to
individual terrain blocks. Ordinary equalization conserves transferred material.
Sparse atmospheric amounts are saved with each Minecraft chunk, without a
global 1,024-cell cap or range-based deletion. Changes mark their owning chunks
dirty for saving; unloading releases the in-memory simulation mirror, and loading
restores amounts with capacity recomputed from terrain. Amounts survive normal
save/unload/reload and server restarts. Work and network budgets limit processing,
not the total number of stored cells. Each cell stores at most 1,000,000 material
units; capacity remains 0..1000.

Install this version on **both server and client**, or in a single-player NeoForge
instance. Earlier particle-only releases did not require a client installation;
the new grid renderer and sync protocol do. **Protocol 4 requires updating both
sides together; older protocol-3 clients cannot connect.** Large snapshots use
512-cell packets and a completion marker: the client replaces the view only
when complete, preserving retained opacity. Client memory follows the subscribed
view plus one incoming snapshot; removals, replacement snapshots, dimension
changes, and disconnect release old state. There is no 4,096-cell cache cap or
512-cell draw cap. GPU batches bound buffer size without dropping distant cells.
No world reset is needed. Both atmospheric amounts and broken terrain are saved.

Operators can run `/dynamicatmosphere status` to inspect runtime counters and
`/dynamicatmosphere demo` as a player to add material nearby for visual testing.

## Destructive Pressure

Pressure relief is included in 0.6.0-alpha.1 and enabled by default. Trapped
excess breaks the eligible block with the lowest hardness in the source cell,
dropping items. Once source-cell blocks are gone, relief tries neighboring cells
and proceeds outward toward room. Each broken block adds **1 material unit**.
Negative-hardness and intrinsically unbreakable blocks are exempt; there is no
claim/protected-area support. Pressure is limited to four attempts per sampling
interval, so the faster cadence also increases pressure opportunities. Work is
bounded per pass, not an unlimited search
or guarantee of immediate relief. Closed unbreakable surroundings leave excess
blocked and reported rather than deleting it.

Back up existing worlds before upgrading. Downgrading the mod does not restore
broken blocks; restore the backup to roll back terrain damage. This alpha's
pressure implementation still requires build and server/client verification.

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
