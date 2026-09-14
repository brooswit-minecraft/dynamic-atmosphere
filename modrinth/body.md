# Dynamic Atmosphere

**0.15.0-alpha.1** for Minecraft **1.21.1 / NeoForge**, with seven server-owned,
chunk-persisted atmospheric materials and translucent client volumes.

**Destructive pressure is enabled by default and can damage terrain and builds,
without claim/protected-area support. Material effects also change blocks,
villager professions/trades, entity health, and spawning. Back up your world;
downgrading does not undo these changes. No world reset is required.**

## Seven Materials

0.15.0-alpha.1 enables all seven independent materials, each with chunk-persisted
server amounts and separate client state. These are active MVP systems, not
placeholders for future runtime support. Numeric defaults are initial tuning,
not a claim of balance or measured performance.

| Material | Base cell edge | Scheduled simulation interval | Color | Optical density |
| --- | --- | --- | --- | --- |
| Vapor | 4 blocks | 200 ticks | Minecraft fog/horizon | 1x |
| Smoke | 8 blocks | 200 ticks | Black | 4x |
| Dust | 2 blocks | 50 ticks | Brown | 1x |
| Ender Gas | 1 block | 25 ticks | Purple | 4x |
| Violence | 8 blocks | 200 ticks | Red | 4x |
| Exhaust | 2 blocks | 50 ticks | Yellow | 1x |
| Slime | 16 blocks | 400 ticks | Green | 4x |

Intervals are scheduled game ticks, subject to bounded work queues, not guaranteed
wall-clock completion. Vapor retains its 50% due-check skip and condensation.
Material amounts do not combine across identities. Only Vapor uses the persistent
client visual disk cache; the other six keep independent session-only visual caches.

### Producers and Effects

- **Smoke:** fire and lava emit 40 units, lit furnaces and campfires 20, and lit
  torches 2 per producer check. Explosions add an 80-unit burst plus 10 for each
  successfully destroyed block. Fire/lava presence transitions also emit; ordinary
  fire-age/fluid-level changes and scoped fluid transport do not duplicate them.
  Per processed Smoke turn, independent rolls can remove one leaf (10%), turn
  farmland into dirt (1/128), or change an eligible villager to a nitwit (1/256).
  Each successful effect costs 40 units. The profession change invalidates trades;
  farmland conversion can affect crops.
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
  chests, portals/portal occupants, soul torches/fire/sand, and ender-pearl use and
  impact are sources. Pearl use adds 24 and impact 48. A full-moon loaded-chunk
  check has a 1/256 chance of an 8,000-unit burst, independent of the Vapor gate.
  Natural Endermen require strictly more than 50% local Ender Gas fullness,
  including underground, without consuming it; other normal spawn restrictions
  still apply. Other natural surface hostiles retain the Vapor fullness gate.
- **Violence:** hostile mob deaths add 40, sampled netherrack adds 2, and a
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
at 20 TPS). Each due Vapor cell has a 50% skip chance and is rescheduled normally;
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
diagonally. Capacity is proportional to air count for that material's cell size,
scaled to 0..1000; stored amounts can reach 1,000,000 per cell. Zero-air cells
block transfer. Ordinary transfer conserves amounts; gameplay consumption and
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
Negative-hardness/unbreakable blocks are exempt. Bedrock in a source cell blocks
downward transfers, not side/up transfers.

Pressure shares four attempts per sampling interval. A successful break uses
vanilla drops and adds 1 material unit at the broken cell before fresh relief.
Closed unbreakable surroundings leave excess blocked, not deleted. Small-cell
consolidation remains side/down only, into an existing fuller neighbor with
enough room; it never creates pressure overflow.

## Rendering and Visual Cache

Let V be Minecraft's effective client view distance in blocks:
- Vapor and Smoke: base cells through V/2, 2x through V, 4x through 2V; nothing beyond.
- Violence and Slime: base cells through V, 2x through 2V; nothing beyond.
- Dust, Exhaust, and Ender Gas: base cells through V/4 only; nothing beyond.

Coarse volumes recursively average eight children, including empty volumes.
Parent/child coverage never overlaps; aligned boundary volumes may remain finer.
Fallback geometry obeys each material's hard cutoff without deleting cache data.
LOD changes rendering only and does not load distant chunks.

Mixed-color slices share back-to-front ordering and bounded GPU batches.
Vapor uses current Minecraft fog RGB; the other six use their listed colors.
The 4x materials scale optical density before thickness-integrated alpha, not
stored fullness or individual triangle alpha. Vapor-only frames retain their
constant-color unsorted path. No measured FPS improvement is claimed.

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

## Installation

Install **0.15.0-alpha.1 on both server and client**, or in a NeoForge 1.21.1
single-player instance. **Protocol 8 requires both sides to update together;
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
