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
Retaining the 0.8.1 tuning, simulation/source cadence is `1000 * cellSize / 16` ticks:
40 times the original 25-base delay, rather than the previous 10 times. Current
4-block cells use **250 ticks**, or **12.5 seconds** at 20 TPS (previously 62.5
ticks). Size 1 averages 62.5 ticks, size 16 uses 1000, and size 32 uses 2000.
Actual progress remains work-budgeted. Cache, render, and
sync intervals are unchanged; no data reset is required.
Producer offsets now reach 96 blocks instead of 12, using the same eight sampled
positions per pass and loaded-only checks. Live cell visibility follows Minecraft's actual
tracked chunks and the client's effective render distance, loaded chunks, and
frustum, with no fixed atmospheric radius or nearest-cell cutoff. Delta sync stays every 20 ticks, full snapshots
every 200 ticks, and simulation processes at most 128 source cells per tick.
Nearby clients receive a snapshot
and batched changes, and render translucent cells with opacity based on fullness.
This is **not** the full terrain-aware atmospheric simulation. Condensation can
place real water sources (below), but does not generate Minecraft rain. Pollution,
gas transport, and world generation changes are not included yet.

Rain uses Minecraft's local rain/exposure check at the topmost landing surface,
including roofs and canopies. Each sampled rainy position contributes 40 material
units per sampling pass, independently of water/cloud sources. Dry biomes, snow,
and sheltered ground do not emit rain material.
The mod does not create rain or change the world's weather; condensation places
water blocks independently of Minecraft's rain.

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
the new grid renderer and sync protocol do. **Protocol 5 requires updating both
sides together; earlier-protocol clients cannot connect.** Large snapshots use
512-cell packets and a completion marker. Snapshot scope and chunk freshness
distinguish current observations from retained visual history. There is no
512-cell draw cap; GPU batches bound buffer size.
No world reset is needed. Both atmospheric amounts and broken terrain are saved.

## Water Condensation

The 0.8.0-alpha.1 feature adds a water-placement roll on each cell's scheduled
check. With fullness `f = current material / capacity`, the probability is
`clamp(0.2 * (f - 0.5), 0, 0.1)`: zero at or below 50% fullness, 5% at 75%,
and capped at 10% at or above 100%. A cell with no air cannot place water.

On a successful roll, one water source is placed at a random air block in the
same cell, without replacing solids. Only successful placement removes
`max(1, floor(current material * 0.25))` units. No air or a failed placement
consumes nothing. Ultrawarm dimensions, including the Nether, skip both water
placement and consumption. Checks now use the 250-tick cadence for 4-block cells,
about 12.5 seconds at 20 TPS, subject to work budgets. Probability and consumption
per check are unchanged.

**This places real water that flows normally and can wet builds. Back up worlds
before upgrading.** It changes the world, not just the visual cache. Existing
destructive-pressure warnings remain unchanged, and no data or world reset is
required.

## Distance-Based Rendering

The 0.9.0-alpha.1 client rendering feature uses four levels of detail (LOD).
Let `V` be Minecraft's client view distance expressed in blocks and `d` the
render distance from the viewer:

| Distance band | Rendered volume size |
| --- | --- |
| `d < V/2` | 4x4x4 blocks |
| `V/2 <= d < V` | 8x8x8 blocks |
| `V <= d < 2V` | 16x16x16 blocks |
| `2V <= d <= 4V` | 32x32x32 blocks |

Each coarser volume recursively averages eight children, counting empty volumes
in that average rather than averaging only occupied children. Coverage does not
overlap: a coarse parent is not drawn over its finer children. The coarser bands
reduce the number of rendered volumes and slices at distance; actual performance
requires user verification, and no measured FPS improvement is claimed here.

Bands use aligned parent decisions, so boundary-crossing volumes can remain finer.
Selection is cached in 16-block camera regions and queries only nearby cached
roots, with at most 4,096 selection work units per client tick. Rotation does not
rebuild it. While a new view is being refined, aligned 32-block cached volumes
provide temporary coverage; unloaded near chunks use 16-block cached fallback.
Neither fallback overlaps its detailed descendants.

This changes rendering only. Server simulation still uses 4x4x4-block cells and
250-tick checks (12.5 seconds at 20 TPS), with unchanged condensation chance and
consumption per check. Cache/render/sync intervals and persistent data are
unchanged. Four-times-view cache reach remains, with distant visuals only from
previously seen areas; LOD does not load distant chunks. No world reset is needed.

## Persistent Visual Cache

The 0.7.0-alpha.1 feature release retains previously seen atmospheric visuals
across client sessions. Coarse far fog extends to **four times the client view
distance**, but only in previously seen areas. It can be stale: this is an
approximate visual cache, not simulation, current terrain knowledge, or a way to
load distant chunks. Fresh server observations supersede cached visuals for
their snapshot/chunk scope; cached visuals never add material to the server.

A stable world UUID stored in server SavedData scopes protocol-5 snapshots and
chunk freshness. Client files live under
`gameDirectory/dynamicatmosphere-cache`, keyed by hashed server/world/dimension/
layout identity. Worlds are separate; a newly reset world receives a fresh UUID
instead of reusing the old world's visuals. This update does not require a reset.

Changed chunks are written atomically every 10 seconds and on disconnect.
The disk cache is bounded to **64 MiB and 8,192 files**, and restoring cached
visuals into RAM is limited to **200,000 cells**. These are client visual-cache
limits, not limits on authoritative chunk-persisted simulation. Disconnect and
dimension changes clear live session state without discarding persisted visual
history belonging to another scope.

Operators can run `/dynamicatmosphere status` to inspect runtime counters and
`/dynamicatmosphere demo` as a player to add material nearby for visual testing.

## Destructive Pressure

Pressure relief is included in 0.6.0-alpha.1 and enabled by default. Trapped
excess breaks the eligible block with the lowest hardness in the source cell,
dropping items. Once source-cell blocks are gone, relief tries neighboring cells
and proceeds outward toward room. Each broken block adds **1 material unit**.
Negative-hardness and intrinsically unbreakable blocks are exempt; there is no
claim/protected-area support. Pressure is limited to four attempts per sampling
interval, now 40 times the original delay in 0.8.1-alpha.1. Work is
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

Local server/client testing is intentionally skipped for this release at the
user's request. CI verifies the build; hosted checks verify startup and status
after release. User-run server/client checks cover visuals, pressure, and
save/reload behavior. Neither a build nor a healthy server proves client visuals
or cache correctness; those checks remain pending user verification.

After adding the published Modrinth version to Sickos, use Sickos' normal version
bump and CI release/deploy path. Verify the hosted jar hash, restarted server, and
runtime command; retain the existing world.
