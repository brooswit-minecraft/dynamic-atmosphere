# Dynamic Atmosphere

Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Mod ID: `dynamicatmosphere`.
MIT licensed.

This documentation describes the combined `0.19.0-alpha.1` release behavior.

## Atmospheric Grid Alpha

**BREAKING behavior in 0.6.0-alpha.1: pressure destruction is enabled by default
and can damage terrain and builds. Back up the world before upgrading. There is
no claim/protected-area integration.**

The server maintains seven independent atmospheric grids. In `0.19.0-alpha.1`,
all seven use a shared 200-tick simulation cadence and 300-tick scheduled producer
cadence. Event producers remain event-driven. Vapor uses world-aligned 4x4x4-block
cells; the following overview describes Vapor unless stated otherwise.
Water fog, high-terrain clouds, rain-driven cloud-height emissions, and dark exposed
ground add material to their cells. There is no natural decay: material spreads
by equalizing fullness across the six face-adjacent cells, not diagonally.
Capacity is proportional to vacant volume. Air and liquid blocks count as vacant;
waterlogged hosts remain occupied.
Fullness is material amount divided by capacity: fewer vacant blocks need less
material to fill. Transfer uses positive-capacity neighbors; zero-capacity cells
block it. Any fluid, including flowing fluid or waterlogging, blocks downward
transfer from its source cell, as does bedrock; sideways and upward transfer remain.
If terrain reduces capacity below the stored amount, excess is pushed farther
outward toward the nearest available capacity through neighboring air-capacity
cells. This search is bounded to the loaded active region. Confirmed blockage
can trigger bounded destructive pressure relief (below). Unknown unloaded
boundaries and exhausted work/search budgets leave work pending, never authorize
pressure destruction, and do not discard material.
Excess that still cannot escape remains blocked and reported, not discarded;
displacement is not unlimited.
In `0.19.0-alpha.1`, all materials use a fixed **200-tick** simulation interval,
or **10 seconds** at 20 TPS. Scheduled producers are independent:
passes are scheduled every **300 ticks** (15 seconds at 20 TPS) across all loaded
chunks, not just player-offset samples. Each chunk has a random **10% default
gate**, with one random X/Z column per pass. A bounded fair queue permits backlog,
so scheduling is not a guarantee every chunk completes within 15 seconds. Checks
never force chunks to load. Actual simulation progress remains work-budgeted.
Each due cell has a **75% skip chance**. Skipped cells are rescheduled at the
normal 200-tick interval rather than retried next tick. They can still receive
incoming material from neighbors: skipping does not freeze a cell or establish
a fixed 400-tick schedule. Condensation rules are unchanged on processed checks.
Cache/render/sync intervals are unchanged; no data reset is required.
Live cell visibility follows Minecraft's actual
tracked chunks and the client's effective render distance, loaded chunks, and
frustum, with no fixed atmospheric radius or nearest-cell cutoff. Delta sync stays every 20 ticks, full snapshots
every 200 ticks, and simulation processes at most 128 source cells per tick.
Nearby clients receive a snapshot
and batched changes, and render translucent cells with opacity based on fullness.
This is **not** the full terrain-aware atmospheric simulation. Condensation can
place real water sources (below), but does not generate Minecraft rain or change
world generation. All seven materials and their MVP gameplay systems are enabled
(see below); exact terrain-aware transport and further balance tuning remain future work.

### Rain and Evaporation in 0.14.0

Each passed rain check splits 320 units between ground and an airborne position:
an integer ground allocation from 0 through 320, with the remainder at a random
height between ground and Y=192 (ground itself if already above Y=192).
The existing rain gate is retained; the total is not doubled.
Evaporation traces the sampled contiguous water column to its bottom. Bottom water
directly above magma bypasses the temperature roll and also targets the original
surface, once if both positions coincide. The outer 300-tick/10% chunk gate remains.
Waterlogged hosts are drained, not destroyed; only successful mutations emit through
the existing humidity-scaled removal hook. No existing atmosphere or world is cleared.
High-terrain clouds and dark exposed-ground sources remain independent.
Sampled surface water now evaporates: plain water fluid blocks become air, while
waterlogged blocks retain their host with WATERLOGGED cleared. Non-water solids
and unsupported water-containing hosts are preserved. Depth-based direct emissions
are removed. This removes real water, including previously condensed water;
natural fluid updates may refill it. No world reset or data migration is required.
The mod does not create rain or change the world's weather.

After the outer 10% chunk gate, scheduled water evaporation gets a second chance
of `clamp(biome temperature / 2, 0, 1)`: temperature 0.8 gives 40%, 2 gives 100%,
and 0 or below gives zero chance through this roll. The magma-bottom exception
above bypasses it. Only the scheduled producer uses this roll.
Every successful non-transport water-to-nonwater mutation, including manual removals, emits
`round(10 + 70 * clamp(biome downfall, 0, 1))` material units (10 dry to 80 wet).
Downfall is a biome humidity proxy, not instantaneous rain or weather; climate
comes from the loaded chunk's biome. Humidity is captured at removal and queued
amounts are added without a second producer emission. Ordinary water-level
changes, failed mutations, and chunk unloads do not trigger this source.

Fluid transport during vanilla/Flowing Fluids fluid ticks does not emit removal
material. The fluid-tick scope includes Flowing Fluids 1.0.6's injected movement
and always clears on return or exception. Direct bucket/removal, block replacement,
and scheduled atmospheric evaporation outside transport still emit by humidity.
This fix does not delete accumulated atmosphere or reset worlds; existing material
and simulation backlog remain.

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
the new grid renderer and sync protocol do. **Protocol 10 requires updating both
sides together; earlier-protocol clients cannot connect.** Large snapshots use
512-cell packets and a completion marker. Snapshot scope and chunk freshness
distinguish current observations from retained visual history. There is no
512-cell draw cap; GPU batches bound buffer size.
No world reset is needed. Both atmospheric amounts and broken terrain are saved.

After bounded spreading, a selected due cell with at most 10 units can move its
entire amount into an existing, loaded cell on one of the four horizontal faces or directly below (never above), with strictly more
material and enough free capacity for the whole amount. Equal amounts never merge.
Prefer the largest eligible destination with deterministic ties; no new cell,
chunk load, or pressure overflow is created. The empty source is removed and both
changes are persisted and synchronized. Solitary or blocked cells retain material.

Sampled snow/ice surfaces also emit **40 vapor units without consuming the
block**. ICE-tagged blocks, SNOW, SNOW_BLOCK, and POWDER_SNOW qualify. This uses
a `WORLD_SURFACE` lookup and the existing 15-second / 10% loaded-chunk gate,
with no additional column scan or forced chunk load. A capacity scan may still occur.

## Water Condensation

The 0.8.0-alpha.1 feature adds a water-placement roll on each cell's scheduled
check. With fullness `f = current material / capacity`, the probability is
`clamp(0.2 * (f - 0.5), 0, 0.1)`: zero at or below 50% fullness, 5% at 75%,
and capped at 10% at or above 100%. A cell with no air cannot place water.

On a successful roll, one water source is placed at a random air block in the
same cell, without replacing solids. Only successful placement removes
`max(1, floor(current material * 0.25))` units. No air or a failed placement
consumes nothing. Ultrawarm dimensions, including the Nether, skip both water
placement and consumption. Checks now use the fixed 200-tick simulation cadence,
about 10 seconds at 20 TPS, subject to work budgets. Probability and consumption
per check are unchanged.

**This places real water that flows normally and can wet builds. Back up worlds
before upgrading.** It changes the world, not just the visual cache. Existing
destructive-pressure warnings remain unchanged, and no data or world reset is
required.

## Seven Materials

`0.19.0-alpha.1` enables all seven independent materials, each with chunk-persisted
server amounts and separate client state. These are active MVP systems, not
placeholders for future runtime support. Numeric defaults are initial tuning,
not a claim of balance or measured performance. `0.20.0-alpha.1` moved every
material onto the same `4x4x4`-block cell; Dust, Ender Gas, Exhaust, Void Gas,
and Slime previously used their own smaller or larger cell sizes. `0.20.0-alpha.1`
also renamed Void Gas's id, config keys, and identifiers (see CHANGELOG); its
emission sources, thresholds, and costs did not change.

| Material | Base cell edge | Scheduled simulation interval | Color | Optical density |
| --- | --- | --- | --- | --- |
| Vapor | 4 blocks | 200 ticks | Minecraft fog/horizon | 1x |
| Smoke | 4 blocks | 200 ticks | Very dark brown (0x1F160F) | 2x |
| Dust | 4 blocks | 200 ticks | Brown | 1x |
| Ender Gas | 4 blocks | 200 ticks | Purple | 40x |
| Void Gas | 4 blocks | 200 ticks | Black | 4x |
| Exhaust | 4 blocks | 200 ticks | Yellow | 1x |
| Slime | 4 blocks | 200 ticks | Green | 4x |

Intervals are scheduled game ticks, subject to bounded work queues, not guaranteed
wall-clock completion. Scheduled producer passes share a 300-tick cadence. All
materials have a configurable 75% due-check skip; Vapor retains condensation.
Material amounts do not combine across identities. `0.20.0-alpha.1`'s move to a
uniform cell size bumped the Dust/Ender Gas/Exhaust/Void Gas/Slime storage
version; a chunk whose stored tag still has the old version or cell size loads
as empty for that material and accumulates new material normally, with the
stale tag replaced on the next save (`0.20.1-alpha.1`; from `0.20.0-alpha.1`
until then, those chunks came back permanently without the material instead).
Genuinely invalid or corrupt data — a missing `cells` array, a malformed tag,
a decode failure — is handled differently: the original tag is retained and
that chunk is skipped rather than silently truncated.
Only Vapor uses the persistent
client visual disk cache; the other six keep independent session-only visual caches.
Legacy 8-block Smoke cells are split into aligned 4-block children on load. Integer
remainders are distributed among children so total stored Smoke mass is preserved,
and migrated chunks are saved in the new format.

### Producers and Effects

- **Smoke:** fire emits 40 units and lava 4, lit furnaces and campfires 20, and lit
  torches 2 per producer check. Explosions add an 80-unit burst plus 10 for each
  successfully destroyed block. Fire/lava presence transitions also emit; ordinary
  fire-age/fluid-level changes and scoped fluid transport do not duplicate them.
  Per processed Smoke turn, independent rolls can remove one leaf (100%), turn
  farmland into dirt (10/128), or change an eligible villager to a nitwit (10/256).
  Each successful effect costs 40 units. The profession change invalidates trades;
  farmland conversion can affect crops. An independent 10/64 roll dissipates up to
  40 Smoke. These chances are 10x their earlier defaults and capped at 100%.
- **Dust:** movement, running, jumping, landing, fall damage, block breaking,
  placement, and falling-block landing produce Dust. Initial amounts include
  walking 1, running 3, jumping 8, landing 6, breaking 16, and placement 12.
  A processed cell can turn plain water into mud: chance rises linearly from zero
  at 50% fullness to 100% at full capacity, costing half the current amount rounded
  up on success. Waterlogged hosts are not replaced. Above capacity, a 1/16 roll
  can place gravel in air, consuming 25% of current Dust on successful placement,
  rounded down with a minimum of 1 unit.
  Independently, a 1/64 processed-turn roll dissipates up to 40 Dust units.
- **Ender Gas:** Endermen, endermites, the Ender Dragon, witches, shulkers, ender
  chests, portals/portal occupants, soul torches/fire/sand, Crying Obsidian, and ender-pearl use and
  impact are sources. Pearl use adds 24 and impact 48. Random full-moon bursts
  have been removed; only source-driven emissions remain.
  Ender Gas never gates spawning. Overworld monsters, including Endermen,
  follow the Vapor (fog) spawn rule: no qualifying terrain above and Vapor
  not strictly more than half full denies natural/chunk-generation monster
  spawns. Nether and End monster spawns follow vanilla rules.
- **Void Gas:** hostile mob deaths add 40, sampled netherrack adds 2, and a
  world-bottom producer has a 1/8 chance to add 8. At 10% through 25% fullness,
  eligible villagers can receive three bread for vanilla breeding readiness,
  costing 5% of the current amount rounded up; this does not force a birth.
  At 75% fullness or higher, a 1/32 processed-turn roll attempts a zombie spawn,
  with a quarter-capacity material cost and normal spawn-position/rule checks.
- **Exhaust:** passive living-mob emissions use a 1/128 chance, or 1/16 for
  creepers, adding 1 unit. Damage emits four units per damage point, rounded up
  and capped at 64; Exhaust's own damage does not recursively emit.
  Eye-position exposure on processed checks deals 1 damage point at 50% fullness,
  rising to 4 at 100%, consuming 25% through 50% of the current amount when damage
  succeeds. Vapor and Exhaust can also grow eligible crops/saplings through
  normal bonemeal behavior: a 10% roll and 40-unit cost on successful growth.
- **Slime:** vanilla-seeded slime chunks can produce 8 units below Y=40 on a
  1/8 producer roll. At 75% fullness or higher, a 1/32 processed-turn roll
  attempts a slime spawn with a quarter-capacity cost and spawn checks.
- **Void Gas, Ender Gas and Slime dissipation:** like Smoke, each dissipates
  gradually via an independent processed-turn roll, reusing Smoke's own
  `dissipationAmount` (up to 40 units) as the cap. The chance is Smoke's own
  `dissipationChance` divided by `heavyGas.dissipationFactor` (default 3), so
  by default these three fade at a third of Smoke's rate. The factor is
  configurable and shared by all three; a factor of 1 means "as fast as
  Smoke."

Producer hooks and effect scans are bounded and loaded-only; not every block or
entity is sampled each tick. Effects requiring material cannot spend unavailable
amounts. Spawn costs are charged after finding an eligible position, before the
spawn call; an attempted spawn is not a guarantee an entity appears.
The coarse transport model and these numerical defaults remain open to tuning.

**These mechanics can damage builds, remove leaves, change farmland and villager
trades, place gravel/mud/water, damage living entities, and spawn mobs. Back up
worlds. Updating does not clear existing material or undo previous world changes.**

## Distance-Based Rendering

Vapor and Smoke use three levels of detail (LOD), with no rendering beyond 2V.
Let `V` be Minecraft's client view distance expressed in blocks and `d` the
render distance from the viewer:

| Distance band | Vapor volume | Smoke volume |
| --- | --- | --- |
| `d <= V/2` | 4x4x4 blocks | 4x4x4 blocks |
| `V/2 < d <= V` | 8x8x8 blocks | 8x8x8 blocks |
| `V < d <= 2V` | 16x16x16 blocks | 16x16x16 blocks |
| `d > 2V` | Not rendered | Not rendered |

Void Gas and Slime use base cells through V and 2x cells through 2V, with nothing
beyond. Dust, Exhaust, and Ender Gas use base cells only through V/4, with nothing
beyond. These cutoffs include cached fallback geometry and preserve stored cache data.
These distance cutoffs (V/4, V, 2V) are unaffected by `0.20.0-alpha.1`'s uniform
cell size: reach is `viewBlocks * reachMultiplier`, a distance that does not
depend on cell size, and no material's reach multiplier changed. What changed is
the volume size of each LOD tier inside those same cutoffs: Void Gas and Slime's
tiers are now 4 then 8 blocks, down from 8 then 16 (Void Gas) and 16 then 32
(Slime); Dust, Exhaust, and Ender Gas's base tier is now 4 blocks, up from 2 —
finer for Void Gas/Slime, coarser for Dust/Exhaust/Ender Gas. Re-tuning these
tiers for the new base size is a follow-up, not required for correctness.

Each coarser volume recursively averages eight children, counting empty volumes
in that average rather than averaging only occupied children. Coverage does not
overlap: a coarse parent is not drawn over its finer children. The coarser bands
reduce the number of independently selected volumes at distance; actual performance
requires user verification, and no measured FPS improvement is claimed here.

Every rendered LOD uses camera-facing slices spaced at
`baseCellSize / slicesPerBaseCell`, including aggregated distant volumes. A 2x or
4x volume therefore receives proportionally more slices across its larger depth;
there is no distant one-slice optimization. Aggregation, hard distance cutoffs,
non-overlapping parent selection, and thickness-integrated optical density remain
unchanged, preserving visual detail and total density through the far bands.

Bands use aligned parent decisions, so boundary-crossing volumes can remain finer.
Selection is cached in 16-block camera regions and queries only nearby cached
roots, with at most 4,096 selection work units per client tick. Rotation does not
rebuild it. While a new view is being refined, aligned 16-block Vapor or 32-block
Smoke volumes provide temporary coverage; Vapor's unloaded near chunks use 16-block
fallbacks. Fallbacks never overlap their detailed descendants, and boundary
geometry is clipped at each material's reach. Shorter render reach does not delete cached data.

Vapor near, far, and fallback volumes use Minecraft's current
fog/horizon color, sampled once per render frame. There is no local light-based
grayscale, distance color blend, or client terrain-light sampling cache.
The other six materials use the colors in the table above. Vapor-only frames retain
the constant-color unsorted path; mixed-material slices are merged back-to-front
through shared bounded GPU batches. Mixed colors are not order-independent.
Void Gas and Slime multiply optical density by four and Smoke by two before
thickness-integrated alpha; Ender Gas uses 40x. This changes rendering only, not
material amounts, capacity, or simulation fullness.

With the default four slices per base cell, 4-block Vapor and Smoke cells use
1-block slice spacing at every LOD. Alpha remains integrated over each slice's
thickness, preserving total optical density. Zero-density volumes exit before
geometry allocation.
Actual performance and in-game appearance remain for user verification.

LOD changes rendering only. Vapor simulation uses 4x4x4-block cells and
200-tick checks (10 seconds at 20 TPS), with unchanged condensation chance and
consumption per check. Cache/render/sync intervals and persistent data are
unchanged. Each material stops at its own cutoff, with cached visuals only from
previously seen areas; LOD does not load distant chunks. No world reset is needed.

## Persistent Visual Cache

The 0.7.0-alpha.1 feature release retains previously seen atmospheric visuals
across client sessions. Cached Vapor fog renders up to **twice the client view
distance**, only in previously seen areas. Older farther-away cache data is retained,
not displayed past the current cutoff. It can be stale: this is an
approximate visual cache, not simulation, current terrain knowledge, or a way to
load distant chunks. Fresh server observations supersede cached visuals for
their snapshot/chunk scope; cached visuals never add material to the server.

A stable world UUID stored in server SavedData scopes protocol-8 snapshots and
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

Pressure relief remains enabled by default for all materials. Only confirmed
blocked overflow can authorize it, with a fresh overflow search before destruction.
Each authorized attempt chooses a scope: neighbor probability equals the source
cell's air-block fraction, and source-cell probability equals its occupied fraction.
This chooses where to search, not a separate probability of breaking a block.
The source scope selects its weakest eligible block; the neighbor scope excludes
the source and selects the weakest block in the nearest reachable face-neighbor
layer. Hardness ties use deterministic coordinates. A scope without a candidate
does not fall back to the other scope. Unknown boundaries or exhausted search
budgets cannot authorize destruction. Bedrock in a source cell blocks downward
transfer, not side/up transfer. Successful breaks use vanilla item drops and add
**1 material unit** at the broken cell before another overflow-relief check.
Negative-hardness and intrinsically unbreakable blocks are exempt; there is no
claim/protected-area support. Pressure shares a limit of four attempts per sampling
interval. Work is
bounded per pass, not an unlimited search
or guarantee of immediate relief. Closed unbreakable surroundings leave excess
blocked and reported rather than deleting it.

Back up existing worlds before upgrading. Downgrading the mod does not restore
broken blocks; restore the backup to roll back terrain damage. This alpha's
pressure implementation still requires build and server/client verification.

## Fans and Configuration

Fans affect cells up to 4x4x4, which now covers every material: Vapor, Smoke,
Dust, Exhaust, Ender Gas, Void Gas, and Slime. Void Gas and Slime previously used
larger cells and were unaffected; they are fan-transportable as of `0.20.0-alpha.1`.

When Create is installed, a rotating Encased Fan performs intake followed by output
on a separate five-second pass. Positive RPM draws evenly from the five neighbors
other than the facing neighbor, then pushes toward that facing neighbor. Negative
RPM draws from the facing neighbor, then distributes evenly to the other five.
The fan can start with an empty cell. Each stage has one shared RPM-sized budget,
not a full budget per neighbor; shortages redistribute to other eligible neighbors.
Output can also use material already in the fan cell, and blocked output retains intake there.
Requested movement is `floor(abs(RPM) * createFanTransportPerRpm)`, defaulting to
1.0 units per RPM. Fans never roll the ordinary simulation's 75% skip.
`integrations.createFanIntervalTicks` defaults to 100 ticks, and
`integrations.maxFanChunksPerTick` defaults to 32 to spread scanning work.
Destinations with any empty space may
be overfilled, leaving normal pressure simulation to spread material or break
blocks. Source material, the numeric storage ceiling, loaded/readable terrain,
and liquid/bedrock downward barriers still bound transfer. At 256 RPM, a fan
requests up to 256 units of intake and 256 units of output per pass per supported
material. Integer remainders rotate between neighbors to avoid fixed-axis bias.

Portal-containing loaded sections are palette-filtered, then scanned for each
portal block. `enderGas.portalBlockEmission` controls the total emitted on its
two faces per producer pass (default 100, zero disables). Other passive Ender Gas
sources retain their random sampling.

`runtime.simulationSkipChance` applies to all seven materials (default 0.75,
zero processes every due cell, one skips every due cell). It replaces the old
Vapor-only `vapor.skipChance` setting; producer cadence is independent.

Server gameplay, cadence, work-budget, and fan settings live in
`config/dynamicatmosphere-server.toml` on the current dedicated server; older
installations may use `<world>/serverconfig/dynamicatmosphere-server.toml`. Client rendering,
reach, optical density, and allocation settings live in
`config/dynamicatmosphere-client.toml` under the game directory. Runtime code
reads immutable configuration snapshots; NeoForge config reloads replace those
snapshots, so exposed server settings and client presentation settings apply
without restarting. The client `allocation.cellBudget` setting is the exception
and requires a game restart. Structural cell sizes, storage formats, and network
protocol are intentionally not configurable and change only with a matching mod
update on both sides.

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
  classes directly into the shipped jar. Runtime material grids use the Forge
  adapter; the additive pure-Java definition foundation remains separate.

## Automated Releases

Change `version.txt` and `CHANGELOG.md` with a release-worthy change. Merging to
`main` automatically builds and tests, creates a GitHub release with the jar,
syncs the Modrinth description, and publishes the native NeoForge version there.
GitHub is the primary artifact source. Workflow dispatch retries the checked-in
version; it is a **live publish**, not a dry run. Existing artifacts are never
overwritten. An existing Modrinth version is reused only if its hash and metadata
match; a mismatch fails rather than replacing a release.

The version section in `CHANGELOG.md` being released must declare exactly one
`Category: patch|minor|breaking` marker; the release fails before anything is
built or published if it is missing, invalid, or duplicated, and a `breaking`
category additionally requires a non-empty `## Migration` subsection. After an
authenticated Modrinth read-back confirms the published version, CI notifies
the Sickos modpack with a `repository_dispatch`. The full cross-repo contract
(payload keys, credential paths, idempotency, manual re-send recovery) is in
[`docs/release-dispatch-contract.md`](docs/release-dispatch-contract.md).

Repository configuration:

- Secret `MODRINTH_TOKEN` with project/version write permission.
- Variable `MODRINTH_PROJECT_ID`: `PZV7RorC`.
- `modrinth/body.md`: formatted listing description.
- `modrinth/short-description.txt`: listing summary.
- `modrinth/project.json`: this release's Modrinth environment and submission policy.
  CI applies the environment only to the version in `version.txt`, preserving
  older releases' compatibility metadata.
- Optional secret `SICKOS_DISPATCH_TOKEN`, or optional secret
  `SICKOS_DISPATCH_APP_PRIVATE_KEY` + variable `SICKOS_DISPATCH_APP_ID`, for
  the sickos dispatch above. Neither is required: with no credential
  configured, the dispatch is skipped with a warning and the release still
  ships. See `docs/release-dispatch-contract.md`.

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
