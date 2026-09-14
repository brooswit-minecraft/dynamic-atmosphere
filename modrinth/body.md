# Dynamic Atmosphere

A small atmospheric mod in alpha for Minecraft **1.21.1 / NeoForge**.

**This release retains default-enabled destructive pressure introduced in 0.6.0-alpha.1. It can damage
terrain and player builds, with no claim/protected-area support. Back up your
world before upgrading; restoring that backup is required to undo terrain damage.**

The **atmospheric grid** divides space into 4x4x4-block cells. Water fog,
high-terrain clouds, rain-driven cloud-height emissions, and dark exposed ground add
material. There is no natural decay: material spreads by equalizing fullness
across six face-adjacent cells. In 0.13.0-alpha.1, simulation checks retain a fixed
200 ticks (10 seconds at 20 TPS), independent of producer passes scheduled every
300 ticks (15 seconds). Producers cover all loaded chunks with a random 10%
default gate and one random X/Z column per chunk per pass. A bounded fair queue
can accumulate backlog; it never forces chunks to load. This replaces player-offset
sampling and the size-based simulation cadence. Cache/render/sync intervals are
unchanged; no data reset is required.
Simulation work remains budgeted across ticks. Clients render
translucent cells with opacity based on material amount divided by capacity.
This is an incremental alpha, not a complete weather simulation.

**Upcoming, unreleased:** Rain will split a total of 320 units per passed check
between ground (a random integer from 0 through 320) and Y=192 (the remainder),
using the existing rain gate. Evaporation will trace contiguous water to its bottom;
bottom water directly above magma bypasses the temperature roll and also targets
the original surface, once when both coincide. The outer 300-tick/10% chunk gate
remains, waterlogged hosts are preserved, and only successful mutations emit through
the humidity-scaled removal hook. These gameplay changes await verification for a
fresh minor release, not 0.13.2-alpha.1. Existing atmosphere and worlds are retained.

## Current Scope

- New snow/ice surface vapor: a WORLD_SURFACE lookup selects ICE-tagged blocks, SNOW, SNOW_BLOCK, or POWDER_SNOW to emit 40 units without consuming the block. Reuse the existing 15-second / 10% loaded-chunk gate; no additional column scan or forced chunk load. A capacity scan may still occur.
- Each due simulation cell has a 50% skip chance. A skipped cell is rescheduled at the normal 200-tick interval, not retried next tick; neighboring cells can still send it material. This is not a fixed 400-tick schedule. Condensation rules remain unchanged on processed checks.
- In 0.13.0-alpha.1, all near, far, and fallback volumes use Minecraft's current fog/horizon color from one snapshot per frame. Local light-based grayscale, distance color blending, and client terrain-light sampling are removed.
- Constant RGB and no depth writes make atmospheric alpha order-independent. Rendering keeps bounded GPU batches without volume sorting; opacity, protocol, and persistent data are unchanged.
- In 0.13.1-alpha.1, detailed 4-block volumes use four 1-block camera-facing slices instead of eight 0.5-block slices. Thickness-integrated alpha preserves total optical density while reducing translucent geometry and blend layers; zero-density volumes skip geometry allocation. Fog RGB, LOD distances, coarse rendering, caches, protocol 5, and server simulation are unchanged. Actual FPS improvement remains for user testing.
- New in 0.9.0-alpha.1: distance-based rendering LOD. With Minecraft client view distance `V` in blocks, use 4x4x4-block volumes below `V/2`, 8x8x8 in `[V/2, V)`, 16x16x16 in `[V, 2V)`, and 32x32x32 in `[2V, 4V]`.
- Coarser volumes recursively average eight children, including empty volumes. Coverage is non-overlapping: coarse parents are not rendered over their finer children. Fewer volumes and slices are rendered at distance; actual performance needs user verification, with no measured FPS or runtime-verification claim.
- Boundary-crossing parents can remain finer. Selection is cached in 16-block camera regions, spatially queried and budgeted; rotation does not rebuild it. New views temporarily use aligned 32-block cached coverage while refining, and unloaded near chunks retain a 16-block fallback, never overlaid with detail.
- LOD is client rendering only: retain 4-block simulation cells, now with fixed 200-tick checks (10 seconds at 20 TPS), unchanged condensation probability/consumption per check, cache/render/sync intervals, and persistent data. No world reset is required.
- New in 0.8.0-alpha.1: each cell's scheduled check can condense material into one real water source. Chance is linear above 50% fullness: 0% at 50%, 5% at 75%, capped at 10% at or above 100%; at or below 50% there is no placement.
- A successful roll targets a random air block in the same cell, never solids. Successful placement consumes 25% of the current material amount, rounded down with a minimum of 1 unit. No air or failed placement consumes nothing; ultrawarm dimensions such as the Nether skip both placement and consumption.
- **Placed water flows normally and can wet builds. Back up worlds.** This is a world-changing feature, not a visual effect or Minecraft rain. Checks now use fixed 200 ticks, subject to work budgets; probability and consumption per due check are unchanged.
- Server-owned material amounts, synchronized to nearby clients.
- Bounded fair producer work across all loaded chunks, without forcing chunks to load; backlogs can delay scheduled checks.
- Live cell visibility follows Minecraft's tracked chunks and the effective client render distance, loaded chunks, and frustum, without a fixed atmospheric radius or nearest-cell cutoff.
- Persistent client visuals add coarse far fog out to four times the client view distance, only in previously seen areas. Cached fog can be stale; it is approximate visual history, not simulation or distant chunk loading.
- Client disk files live under `gameDirectory/dynamicatmosphere-cache`, keyed by hashed server/world/dimension/layout identity. Changed chunks are written atomically every 10 seconds and on disconnect, bounded to 64 MiB and 8,192 files; RAM restore is limited to 200,000 cells.
- A stable world UUID in server SavedData keeps worlds separate. A newly reset world gets a fresh UUID; this update does not require a world reset. Fresh server observations supersede cached visuals for their snapshot/chunk scope and never import cached material into simulation.
- No 512-cell draw cap; bounded GPU batches render the live view independently of the persistent visual-cache limits.
- Delta sync remains every 20 ticks, full snapshots every 200, and work at most 128 source cells per tick.
- Sparse per-Minecraft-chunk storage replaces the global 1,024-cell cap. Work and network updates are budgeted, not total stored cell count.
- Capacity is proportional to vacant air blocks (0..64): fewer air blocks need less material to fill a cell.
- Ordinary equalization transfers material without loss to positive-capacity face neighbors, balancing fullness rather than raw amounts. Zero-air cells block transfer; no diagonal transfer.
- Overfull cells push excess outward toward nearby available capacity through air-capacity neighbors. Unknown unloaded boundaries or exhausted budgets leave work pending: neither authorizes pressure destruction or discards material. Unlimited displacement is not guaranteed.
- Trapped excess triggers default-enabled pressure destruction: break the lowest-hardness eligible source-cell block with drops, then work outward once source blocks are gone. Each broken block adds 1 material unit.
- Pressure remains limited to four attempts per sampling interval. Negative-hardness/intrinsically unbreakable blocks are exempt; closed unbreakable surroundings leave excess blocked, not deleted.
- Rain checks emit 320 units at the previous cloud altitude Y=192 per passed check, replacing ground-level rain fog. High-terrain clouds and dark exposed-ground sources remain.
- Sampled surface water evaporates: plain water fluid blocks become air; waterlogged hosts retain their block with WATERLOGGED cleared. Non-water solids and unsupported hosts are preserved. This removes real water, including condensed water; natural fluid updates may refill it. No reset or migration is required.
- After the outer 10% chunk gate, evaporation chance is `clamp(biome temperature / 2, 0, 1)`: temperature 0.8 gives 40%, 2 gives 100%, and 0 or below gives 0%. This roll applies only to the scheduled water producer.
- Every successful non-transport water-to-nonwater mutation, including manual removal, emits `round(10 + 70 * clamp(biome downfall, 0, 1))` units: 10 dry to 80 wet. Downfall is a biome humidity proxy, not current weather. Loaded-chunk biome humidity is captured at removal; the deferred hook alone emits, with no depth-based/direct duplicate. Water-level changes, failed mutations, and unloads emit nothing.
- Fluid-tick transport (vanilla and Flowing Fluids 1.0.6) emits no removal material. The scoped guard clears on return/exception; direct bucket/removal, replacement, and atmospheric evaporation outside transport still emit. Existing atmosphere and simulation backlog are retained; no world reset or cleanup is performed.
- After bounded spreading, due cells with at most 10 units can transfer their entire amount to an existing loaded neighbor on one of the four horizontal faces or directly below (never above), with strictly more material and room for the whole amount. Equal amounts never merge; prefer the largest destination with deterministic ties. Empty sources are removed from memory, storage, and client state. No new cell, chunk load, or pressure overflow; solitary or blocked cells retain material.
- Low-light exposed ground builds fog; full daylight stops this source but does not clear existing material, which can continue spreading.
- Amounts save with their owning chunks and restore on reload/restart; capacities are recomputed. Unload releases the simulation mirror, not saved material. No range-based deletion; terrain damage also persists.
- Each cell stores up to 1,000,000 material units, with capacity 0..1000.
- `/dynamicatmosphere status` and `/dynamicatmosphere demo` for operators.
- No world generation changes or world reset required.

It is **not yet** the planned terrain-aware cloud and fog simulation. Gas
transport, pollution, and weather-generated precipitation are future work;
water-source condensation does not change Minecraft's weather.
The air-count rule is coarse, not an exact airtight-wall simulation: it does
not inspect every shared-face opening. The grid is a coarse visual representation,
not terrain-clipped volumetric fog.

Install the same version on **both server and client**, or in a NeoForge 1.21.1
single-player instance. No extra graphics dependency is required. This requirement
starts with 0.3.0-alpha.1; older releases were particle-only.
**0.13.0-alpha.1 retains protocol 5: update both sides; earlier protocols are
incompatible.** Multi-packet snapshots complete atomically, with world identity,
snapshot scope, and chunk freshness separating live observations from cached
visual history.

Local runtime testing is intentionally skipped at the user's request. CI and
hosted status checks do not establish visual/cache correctness; server/client
validation remains with user testing. Destructive-pressure warnings still apply.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
