# Dynamic Atmosphere

**0.19.0-alpha.1** for Minecraft **1.21.1 / NeoForge**, with seven server-owned,
chunk-persisted atmospheric materials and translucent client volumes.

**Destructive pressure is enabled by default and can damage terrain and builds,
without claim/protected-area support. Material effects also change blocks,
villager professions/trades, entity health, and spawning. Back up your world;
downgrading does not undo these changes. No world reset is required.**

## Seven Materials

`0.19.0-alpha.1` enables all seven independent materials, each with chunk-persisted
server amounts and separate client state. These are active MVP systems, not
placeholders for future runtime support. Numeric defaults are initial tuning,
not a claim of balance or measured performance.

| Material | Base cell edge | Scheduled simulation interval | Color | Optical density |
| --- | --- | --- | --- | --- |
| Vapor | 4 blocks | 200 ticks | Minecraft fog/horizon | 1x |
| Smoke | 4 blocks | 200 ticks | Very dark brown (0x1F160F) | 2x |
| Dust | 4 blocks | 200 ticks | Brown | 1x |
| Ender Gas | 4 blocks | 200 ticks | Purple | 40x |
| Void Gas | 4 blocks | 200 ticks | Black | 4x |
| Exhaust | 4 blocks | 200 ticks | Yellow | 1x |
| Slime | 4 blocks | 200 ticks | Green | 4x |

All materials share the 200-tick simulation cadence and scheduled producer passes
share a 300-tick cadence. These are scheduled game ticks subject to bounded work
queues, not guaranteed wall-clock completion. Event sources remain event-driven.
All materials have a configurable 75% due-check skip; Vapor retains condensation.
Material amounts do not combine across identities. Only Vapor uses the persistent
client visual disk cache; the other six keep independent session-only visual caches.
Legacy 8-block Smoke cells migrate into aligned 4-block children while preserving
the exact total stored Smoke mass; migrated chunks are saved in the new format.

### Producers and Effects

- **Smoke:** fire emits 40 units and lava 4, lit furnaces and campfires 20, and lit
  torches 2 per producer check. Explosions add an 80-unit burst plus 10 for each
  successfully destroyed block. Fire/lava presence transitions also emit; ordinary
  fire-age/fluid-level changes and scoped fluid transport do not duplicate them.
  Per processed Smoke turn, independent rolls can remove one leaf (100%), turn
  farmland into dirt (10/128), or change an eligible villager to a nitwit (10/256).
  Each successful effect costs 40 units. The profession change invalidates trades;
  farmland conversion can affect crops. An independent 10/64 roll dissipates up to
  40 Smoke. These chances are 10x the earlier defaults and capped at 100%.
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
  Natural Endermen require strictly more than 50% local Ender Gas fullness,
  including underground, without consuming it; other normal spawn restrictions
  still apply. Other natural surface hostiles retain the Vapor fullness gate.
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

Producer hooks and effect scans are bounded and loaded-only; not every block or
entity is sampled each tick. Effects requiring material cannot spend unavailable
amounts. Spawn costs are charged after finding an eligible position, before the
spawn call; an attempted spawn is not a guarantee an entity appears.
The coarse transport model and these numerical defaults remain open to tuning.

**These mechanics can damage builds, remove leaves, change farmland and villager
trades, place gravel/mud/water, damage living entities, and spawn mobs. Back up
worlds. Updating does not clear existing material or undo previous world changes.**

## Vapor and World Behavior

Vapor retains 4-block cells and scheduled 200-tick simulation checks (10 seconds
at 20 TPS). Each due cell has a 75% skip chance and is rescheduled normally;
it can still receive incoming material. Producer passes run every 300 ticks
(15 seconds at 20 TPS), with a 10% loaded-chunk gate and a sampled X/Z column.
Bounded fair queues permit backlog and never force chunks to load.

- Rain splits 320 units between ground (a random integer from 0 through 320)
  and a random airborne height between ground and Y=192, or ground when above
  Y=192. The total is not doubled; this does not change Minecraft's weather.
- High-terrain clouds and dark exposed-ground emissions remain. Effective light
  gives zero ground emission at 15, rising to 40 units at 0. Daylight stops this
  source but does not remove existing Vapor.
- A WORLD_SURFACE snow/ice lookup emits 40 Vapor without consuming the block,
  using ICE-tagged blocks, SNOW, SNOW_BLOCK, and POWDER_SNOW. It reuses the
  existing chunk gate with no additional column scan; capacity scans may occur.
- Scheduled evaporation traces water to its bottom and rolls
  `clamp(biome temperature / 2, 0, 1)` after the outer gate. Bottom water above
  magma bypasses this roll and also targets the original surface, once if equal.
  Plain water is removed; waterlogged hosts are drained without destroying them.
- Successful non-transport water removal emits
  `round(10 + 70 * clamp(biome downfall, 0, 1))` Vapor units.
  Water-level changes, failed mutations, chunk unloads, and fluid-tick transport
  do not emit. Vanilla and Flowing Fluids transport use the scoped exclusion.
- On processed Vapor checks, condensation chance rises linearly from zero at
  50% fullness to 5% at 75% and 10% at or above 100%. Successful placement of
  one source into air costs 25% of current material, rounded down, minimum 1.
  No air or failed placement costs nothing. Ultrawarm dimensions skip both.
  **Water flows normally and can wet builds; evaporation can remove it again.**

## Storage and Pressure

Each material equalizes its own fullness across six face-adjacent cells, not
diagonally. Capacity is proportional to vacant volume for that material's cell size,
scaled to 0..1000; air and liquid blocks are vacant, while waterlogged hosts remain
occupied. Stored amounts can reach 1,000,000 per cell. Zero-capacity cells block
transfer. Any fluid amount and bedrock block downward transfer from the source cell,
without blocking sideways or upward movement. Ordinary transfer conserves amounts; gameplay consumption and
Dust's explicit dissipation are separate. This is coarse air-count transport,
not exact voxel air paths or terrain-clipped fog.

Amounts save with owning Minecraft chunks and restore on reload. Unload evicts
the simulation mirror, not saved data. There is no global 1,024-cell storage
limit or range-based deletion; processing and networking remain bounded.
Overfull cells search outward through air-capacity neighbors. Unknown unloaded
boundaries and exhausted budgets leave pending work, never authorize pressure
destruction or discard material.

Before each destructive attempt, overflow is freshly rechecked. Neighbor-scope
probability is the source cell's air fraction; source-scope probability is its
occupied fraction. This is a choice of search scope, not a separate destruction
chance. Source scope selects its weakest eligible block; neighbor scope excludes
the source and selects the weakest block in the nearest reachable layer, with
deterministic coordinate ties. No candidate means no fallback to the other scope.
Negative-hardness/unbreakable blocks are exempt.

Pressure shares four attempts per sampling interval. A successful break uses
vanilla drops and adds 1 material unit at the broken cell before fresh relief.
Closed unbreakable surroundings leave excess blocked, not deleted. Small-cell
consolidation remains side/down only, into an existing fuller neighbor with
enough room; it never creates pressure overflow.

## Rendering and Visual Cache

Let V be Minecraft's effective client view distance in blocks:
- Vapor and Smoke: base cells through V/2, 2x through V, 4x through 2V; nothing beyond.
- Void Gas and Slime: base cells through V, 2x through 2V; nothing beyond.
- Dust, Exhaust, and Ender Gas: base cells through V/4 only; nothing beyond.

Coarse volumes recursively average eight children, including empty volumes.
Parent/child coverage never overlaps; aligned boundary volumes may remain finer.
Fallback geometry obeys each material's hard cutoff without deleting cache data.
LOD changes rendering only and does not load distant chunks.

Every LOD keeps camera-facing slice spacing at
`baseCellSize / slicesPerBaseCell`. Aggregated distant volumes receive enough
slices to cover their larger depth at that spacing; there is no distant one-slice
shortcut. Aggregation, cutoffs, and thickness-integrated density remain unchanged.

Mixed-color slices share back-to-front ordering and bounded GPU batches.
Vapor uses current Minecraft fog RGB; the other six use their listed colors.
Void Gas and Slime use 4x optical density and Smoke 2x before thickness-integrated
alpha; Ender Gas uses 40x. Density does not change stored fullness, capacity, or
gameplay. Vapor-only frames retain their constant-color unsorted path. No measured
FPS improvement is claimed.

Only Vapor has a persistent client visual disk cache, under
`gameDirectory/dynamicatmosphere-cache`, scoped by hashed server/world/dimension/
layout identity and a stable server world UUID. Approximate, potentially stale
history renders only previously seen areas, up to 2V; older farther data is
retained. Fresh scoped snapshots supersede observations, never import cached
amounts into server simulation. The other six client caches are session-only.

Changed cache chunks are written atomically every 10 seconds and on disconnect,
bounded to 64 MiB and 8,192 files, with a 200,000-cell RAM restore limit.
Live visibility follows tracked chunks, effective view distance, loaded terrain,
and frustum. Delta sync is every 20 ticks and full snapshots every 200.
512-cell packet batches are not a draw cap. Work budgets can delay progress.

## Create Fans and Configuration

Fans affect cells up to 4x4x4, which now covers every material: Vapor, Smoke,
Dust, Exhaust, Ender Gas, Void Gas, and Slime. Void Gas and Slime previously used
larger cells and were unaffected; they are fan-transportable as of `0.20.0-alpha.1`.

With Create installed, positive-RPM Encased Fans first draw evenly from their five
non-facing neighbors, then push toward the facing neighbor. Negative RPM reverses
this: draw from the facing neighbor, then distribute evenly to the other five.
Empty fan cells can draw material. Requested movement per stage is
`floor(abs(RPM) * createFanTransportPerRpm)`, with a default coefficient of 1.0.
Fan movement runs independently every 100 ticks (five seconds), with no skip roll.
`integrations.createFanIntervalTicks` and `integrations.maxFanChunksPerTick`
(default 32) configure cadence and bounded loaded-chunk scanning.
Normal spreading shares limited material and destination capacity proportionally,
without preferring X over Z or whichever source is processed first.
Each stage shares one RPM-sized budget across eligible neighbors, redistributing
shortfalls and rotating integer remainders. Output can use existing material in the
fan cell; blocked output leaves intake there. Any destination with empty space can be
overfilled, causing normal pressure handling and possible block destruction.
Source amount, the numeric storage ceiling, loaded terrain, and liquid/bedrock
downward barriers still bound transfer. At 256 RPM, each pass requests up to 256 units
of intake and 256 units of output per supported material.

Portal sections are palette-filtered and scanned for every portal block, emitting
on both faces. `enderGas.portalBlockEmission` defaults to 100 per block per producer
pass; zero disables it. `runtime.simulationSkipChance` defaults to 0.75 for all
seven materials, replacing the old Vapor-only setting.

Server gameplay, shared cadence, work budgets, and fan settings are in
`config/dynamicatmosphere-server.toml` (legacy installations may use the world's
`serverconfig` directory). Client rendering, reach, optical
density, and allocation settings are in
`<game-directory>/config/dynamicatmosphere-client.toml`. Runtime systems consume
immutable snapshots replaced on NeoForge config reload, so exposed server and
client presentation settings hot-reload. Client `allocation.cellBudget` is the
restart-required exception. Cell sizes, storage formats, and network protocol are
structural rather than configurable and require a compatible mod update on both
client and server.

## Installation

Install **0.19.0-alpha.1 on both server and client**, or in a NeoForge 1.21.1
single-player instance. **Protocol 10 requires both sides to update together;
earlier protocols are incompatible.** No extra graphics dependency is required.
World identity, scoped snapshots, and chunk freshness distinguish live state from
visual history. Existing world/material data and Vapor cache are retained;
there is no world reset or cleanup.

Operators have `/dynamicatmosphere status` and `/dynamicatmosphere demo`.
Exact terrain-aware transport, balance tuning, and performance tuning remain
future work; all seven materials above are implemented MVP runtime systems.
Build/CI status is separate from in-game verification. No runtime-verification
claim is made here; actual appearance, effects, and performance need user testing.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
