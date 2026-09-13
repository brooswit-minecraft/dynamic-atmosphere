# Dynamic Atmosphere

A small atmospheric mod in alpha for Minecraft **1.21.1 / NeoForge**.

**This release retains default-enabled destructive pressure introduced in 0.6.0-alpha.1. It can damage
terrain and player builds, with no claim/protected-area support. Back up your
world before upgrading; restoring that backup is required to undo terrain damage.**

The **atmospheric grid** divides space into 4x4x4-block cells. Water fog,
high-terrain clouds, rain landing on exposed surfaces, and dark exposed ground add
material. There is no natural decay: material spreads by equalizing fullness
across six face-adjacent cells. Retaining the 0.8.1 tuning, simulation/source cadence is
`1000 * cellSize / 16` ticks: 4-block cells use 250 ticks, or 12.5 seconds at
20 TPS. This is 40 times the original 25-base delay instead of the previous
10 times. Size 1 averages 62.5 ticks, size 16 uses 1000, and size 32 uses 2000.
Cache/render/sync intervals and
96-block producer reach are unchanged; no data reset is required.
Simulation work remains budgeted across ticks. Clients render
translucent cells with opacity based on material amount divided by capacity.
This is an incremental alpha, not a complete weather simulation.

## Current Scope

- In 0.10.0-alpha.1, coarse LOD and cached fallback volumes take Minecraft's current fog/horizon color. Nearby 4-block detail uses mean effective air-block light as grayscale: 0 is black, 15 is white, with sky darkening and block light included. Non-air blocks are excluded, not dark air.
- Lighting samples only loaded terrain, at most 32 cells (2,048 blocks) per client tick. Visible cells request refresh after 20 ticks; queues can delay updates. Unsampled cells use 50% gray; the disposable cache is bounded to 8,192 cells and clears on world changes. Cached spatial back-to-front ordering handles different colors with bounded GPU batches. Simulation, opacity, protocol, and persistent data are unchanged.
- New in 0.9.0-alpha.1: distance-based rendering LOD. With Minecraft client view distance `V` in blocks, use 4x4x4-block volumes below `V/2`, 8x8x8 in `[V/2, V)`, 16x16x16 in `[V, 2V)`, and 32x32x32 in `[2V, 4V]`.
- Coarser volumes recursively average eight children, including empty volumes. Coverage is non-overlapping: coarse parents are not rendered over their finer children. Fewer volumes and slices are rendered at distance; actual performance needs user verification, with no measured FPS or runtime-verification claim.
- Boundary-crossing parents can remain finer. Selection is cached in 16-block camera regions, spatially queried and budgeted; rotation does not rebuild it. New views temporarily use aligned 32-block cached coverage while refining, and unloaded near chunks retain a 16-block fallback, never overlaid with detail.
- LOD is client rendering only: retain 4-block simulation cells, 250-tick checks (12.5 seconds at 20 TPS), unchanged condensation probability/consumption, cache/render/sync intervals, and persistent data. No world reset is required.
- New in 0.8.0-alpha.1: each cell's scheduled check can condense material into one real water source. Chance is linear above 50% fullness: 0% at 50%, 5% at 75%, capped at 10% at or above 100%; at or below 50% there is no placement.
- A successful roll targets a random air block in the same cell, never solids. Successful placement consumes 25% of the current material amount, rounded down with a minimum of 1 unit. No air or failed placement consumes nothing; ultrawarm dimensions such as the Nether skip both placement and consumption.
- **Placed water flows normally and can wet builds. Back up worlds.** This is a world-changing feature, not a visual effect or Minecraft rain. Checks now use 250 ticks for 4-block cells, subject to work budgets; probability and consumption per check are unchanged.
- Server-owned material amounts, synchronized to nearby clients.
- Bounded work near players, without forcing chunks to load.
- Producer offsets reach 96 blocks instead of 12, with the same eight positions sampled per pass.
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
- Pressure remains limited to four attempts per sampling interval, now 40 times the original delay in 0.8.1-alpha.1. Negative-hardness/intrinsically unbreakable blocks are exempt; closed unbreakable surroundings leave excess blocked, not deleted.
- Rain buildup respects shelter and biome precipitation, including roof and canopy landing surfaces.
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
**0.10.0-alpha.1 retains protocol 5: update both sides; earlier protocols are
incompatible.** Multi-packet snapshots complete atomically, with world identity,
snapshot scope, and chunk freshness separating live observations from cached
visual history.

Local runtime testing is intentionally skipped at the user's request. CI and
hosted status checks do not establish visual/cache correctness; server/client
validation remains with user testing. Destructive-pressure warnings still apply.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
