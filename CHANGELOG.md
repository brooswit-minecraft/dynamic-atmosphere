# 0.20.0-alpha.1

**BREAKING:** saved Dust, Ender Gas, Exhaust, Violence, and Slime atmosphere is discarded on upgrade (see the chunk storage version bump below). Client and server must be upgraded together: a client running the old per-material cell sizes will file and render those five materials at the wrong size and position against this server.

- Move every atmosphere material onto a uniform 4x4x4-block cell. Dust, Ender Gas, and Exhaust grow from 2x2x2; Violence shrinks from 8x8x8; Slime shrinks from 16x16x16.
- Collapse `AtmosphereMaterial`'s per-material cell size onto the shared `AtmosphereGridLayout.CELL_SIZE`; the engine's approved specification catalog matches.
- Collapse the client's `AtmosphereRenderMaterial` per-material cell size onto the same `AtmosphereGridLayout.CELL_SIZE`, so the client files and renders cells at the size the server actually sends. A test now asserts the client, server, and engine catalogs agree by material name.
- Bump the Dust/Ender Gas/Exhaust/Violence/Slime chunk storage version. Pre-upgrade chunk data for those materials is retained on disk but treated as unreadable rather than reinterpreted at the new cell size; affected chunks come back with no stored material until new material accumulates. Vapor and Smoke were already 4x4x4 and are unaffected.
- Remove the now-unreachable legacy one-block Ender Gas merge path, superseded by the storage version bump above.
- Violence and Slime are fan-transportable for the first time, a direct consequence of sharing Vapor/Smoke/Dust/Exhaust/Ender Gas's existing 4-block fan size limit; no fan code changed.
- LOD bands (`VIOLENCE_LOD`, `DUST_LOD`, `VAPOR_LOD`) are multipliers of each material's own cell size and were left as-is; see README for the resulting reach change per material.

# 0.19.0-alpha.1

- Positive-RPM fans draw evenly from the five non-facing neighbors before pushing forward. Negative RPM draws from the facing neighbor before distributing evenly to the other five.
- Each stage shares one RPM-sized budget, redistributes shortages, and rotates integer remainders. Intake works when the fan cell starts empty; blocked output retains material in the center. Preserve material at storage ceilings and respect loaded terrain and downward barriers.
- Keep the separate five-second unskipped cadence, destination overpressure, and 4x4x4 maximum affected cell size.

# 0.18.1-alpha.1

- Restrict Create fan transport to grids with cell edges of four blocks or less. Vapor, Smoke, Dust, Exhaust, and Ender Gas can move; Violence and Slime are unaffected.

# 0.18.0-alpha.1

- Give Create fans an independent five-second loaded-chunk pass with no random skip. `integrations.createFanIntervalTicks` defaults to 100 and `maxFanChunksPerTick` to 32; queued work is bounded and never force-loads chunks.
- Fans transfer into any destination with empty space, including already-full cells, allowing overpressure and the existing pressure response. Fully solid/unavailable cells and downward barriers still block transfer. Preserve total material at the internal storage ceiling.
- Count even one vacant block as positive capacity in large material cells.
- Ordinary atmosphere simulation remains on its existing cadence and 75% skip; it no longer invokes fans.

# 0.17.1-alpha.1

- Apply Create fan transfers before normal distribution for every selected cell. Preserve the shared simulation step and 75% skip decision; no separate timer or skip bypass.
- Add fan-specific detection, blocked-transfer, successful-transfer, and moved-material counters to `/dynamicatmosphere status`.

# 0.17.0-alpha.1

- Increase Ender Gas cells from 1x1x1 to 2x2x2 on the server and client. No other material used one-block cells.
- Merge existing one-block Ender Gas cells into aligned two-block cells when chunks load, preserving amounts. Corrupt or unrepresentably large merges retain their original data instead of truncating it.
- Remove random full-moon Ender Gas bursts and their configuration fields. Portal, mob, block-source, and pearl emissions remain.
- Protocol 10 requires matching updated client and server; no world reset is needed.

# 0.16.2-alpha.1

- Find portal blocks in loaded sections using palette-gated scans, emitting Ender Gas on both faces instead of into occupied portal cells. `enderGas.portalBlockEmission` defaults to 100 per portal block per producer pass; zero disables this source.
- Apply configurable `runtime.simulationSkipChance` to all seven materials, defaulting to 0.75. This replaces Vapor-only `vapor.skipChance`; production intervals remain unchanged.
- Allow magma-created water bubble columns to evaporate, and count them as fluid capacity while retaining their downward-transfer barrier.

# 0.16.1-alpha.1

- Increase default Create fan transfer tenfold, to 1 material unit per RPM per processed turn, and apply it after normal spreading.
- Remove fixed-face preference when a source cannot satisfy all neighbors: distribute its outgoing material proportionally to neighbor deficits, retaining integer remainders at the source.
- Correct the configurable Vapor skip probability: zero skips no cells, one skips all cells; the default 50% behavior is unchanged.

# 0.16.0-alpha.1

- Use detailed volumetric slice spacing at every rendering LOD, retaining cell aggregation and distance cutoffs. Integrated optical density stays unchanged.
- Share configurable simulation (200 ticks) and scheduled production (300 ticks) intervals across all materials. Event-driven emissions remain event-driven.
- Add server gameplay and client rendering configuration. Structural cell sizes remain release-defined.
- Powered Create fans move material into the neighboring cell in their facing direction, proportional to absolute RPM and limited by available capacity and downward barriers.
- Liquids provide capacity but prevent downward transfer; waterlogged solid hosts remain occupied.
- Smoke now uses 4-block cells. Existing 8-block Smoke storage migrates with total material preserved. Protocol 9 requires matching client and server versions.
- Ender Gas optical density increases from 4 to 40; Crying Obsidian produces Ender Gas.
- Smoke interaction probabilities increase tenfold, capped at 100%, without changing costs. Lava remains at one tenth of its original emission.

# 0.15.1-alpha.1

- Reduce lava Smoke production from 40 to 4 units, including lava presence transitions. Fire and all other producers are unchanged; existing Smoke is preserved.

# 0.15.0-alpha.1

- Enable all seven independent, chunk-persisted runtime materials: Vapor (4-block cells), Smoke (8), Dust (2), Ender Gas (1), Violence (8), Exhaust (2), and Slime (16). Initial numeric behavior is MVP tuning, not a balance or performance guarantee.
- Extend Smoke beyond fire to lava, lit furnaces/campfires/torches, presence transitions, and explosions. Defaults: fire/lava 40, furnaces/campfires 20, torches 2, explosion burst 80 plus 10 per successfully destroyed block. Independent processed-turn effects remove leaves (10%), turn farmland to dirt (1/128), or make eligible villagers nitwits (1/256), each costing 40 on success. Profession changes invalidate trades.
- Add Dust from movement, jumps/landings, fall damage, block breaking/placement, and falling-block landing. Water-to-mud chance rises from zero at 50% fullness to 100% at capacity, costing half the current amount rounded up on success. Above capacity, a 1/16 roll can place gravel in air, consuming 25% of current Dust on successful placement, rounded down with a minimum of 1 unit. A separate 1/64 roll dissipates up to 40 units.
- Add Ender Gas from End-related mobs/blocks, witches, soul sources, portals, and pearl use/impact. Full-moon chunk checks have a 1/256 chance of an 8,000-unit burst. Natural Endermen require strictly more than 50% local Ender Gas, including underground, without consuming it; other spawn restrictions remain.
- Add Violence from hostile deaths, netherrack, and world-bottom producers. At 10% through 25% fullness it can supply eligible villagers with breeding food for a 5%-current-amount cost; it does not force births. At 75% or higher, a 1/32 roll attempts a zombie spawn with a quarter-capacity cost.
- Add Exhaust from living mobs and damage. Processed eye-position exposure deals 1 through 4 damage points across 50% through 100% fullness and consumes 25% through 50% of current material when damage succeeds. Its own damage does not recursively emit. Vapor and Exhaust grow eligible crops/saplings via normal bonemeal behavior, a 10% roll and 40-unit successful-growth cost.
- Add Slime production below Y=40 in vanilla-seeded slime chunks. At 75% fullness or higher, a 1/32 roll attempts a slime spawn with a quarter-capacity cost. Material scans, producers, and spawn attempts remain bounded and loaded-only.
- Render all seven colors with shared mixed-material depth ordering. Smoke, Ender Gas, Violence, and Slime have 4x optical density before thickness-integrated alpha; Vapor, Dust, and Exhaust remain 1x. No simulation amounts change for opacity.
- Vapor/Smoke render base detail through half view distance, 2x through view distance, and 4x through twice view distance. Violence/Slime render base through view distance and 2x through twice view distance. Dust/Exhaust/Ender Gas render base only through quarter view distance. Nothing, including fallback geometry, renders beyond each material's cutoff.
- Pressure chooses neighbor scope with probability equal to source-cell air fraction, otherwise source scope. Select the weakest eligible block within the chosen scope (nearest reachable layer for neighbors), with no other-scope fallback. Fresh overflow revalidation, unknown-boundary/search-budget safeguards, vanilla drops, 1-unit break emission, and the shared four-attempt limit remain.
- Use protocol 8: update client and server together. Preserve existing worlds, chunk-persisted amounts, and the Vapor visual disk cache; the other six client caches are independent session-only state. No world reset or clearing. Existing destructive-pressure/water warnings remain; new effects can also alter crops, villagers, health, and mob populations.

# 0.14.0-alpha.1

- Add independent, chunk-persisted 8-block Smoke cells produced by fire, synchronized to clients and rendered black with mixed-material depth ordering. Other planned Smoke sources and other materials are not enabled yet.
- Vapor and Smoke stop rendering beyond twice view distance, including fallback visuals. Bedrock in a source cell prevents downward transfers.
- Natural surface hostile spawns require more than 50% Vapor capacity without consuming material; other normal spawning restrictions remain.

- Split each passed rain check's 320-unit emission between ground and a random height between ground and Y=192: choose an integer ground allocation from 0 through 320, with the remainder allocated to the airborne position. Keep the existing rain gate and separate source identities; this is not two 320-unit emissions.
- Trace the sampled contiguous water column to its bottom. Evaporation targets bottom water; when it sits directly above magma, bypass the temperature roll and also target the original surface, once if both positions coincide. Keep the 300-tick/10% outer chunk gate. Drain waterlogged hosts without destroying them; successful water mutations alone generate humidity-scaled atmospheric material through the existing hook.
- These are gameplay changes for the next minor release, pending verification. Preserve existing atmosphere and worlds; no reset or clearing.

# 0.13.2-alpha.1

- Restore rain emission altitude from Y=300 to Y=192, retaining 320 material per passed rain check. This does not move or clear existing atmosphere.
- Retain the 0.13.1 detailed-volume geometry optimization and all other simulation, producer, cache, and protocol behavior. No world reset or migration is required.

# 0.13.1-alpha.1

- Reduce each detailed 4-block atmospheric volume from eight 0.5-block translucent slices to four 1-block slices. Opacity remains integrated over slice thickness, preserving the same total optical density while halving detailed slice submissions and blend layers.
- Skip zero-density volumes before allocating camera-facing geometry. Preserve Minecraft fog RGB, LOD distance bands, cache and protocol behavior, coarse geometry, and server simulation.
- This is a client rendering performance patch. No measured FPS improvement is claimed; automated CI verifies geometry and the production artifact before publication.

# 0.13.0-alpha.1

- Cache cell air capacity per loaded chunk, invalidating on air-occupancy changes including fluid transport. Drop caches on unload; no world data is removed. Avoid redundant unchanged-amount persistence writes and an unnecessary full-view distance sort during synchronization.

- Add a snow/ice surface vapor producer: a WORLD_SURFACE lookup selects ICE-tagged blocks, SNOW, SNOW_BLOCK, or POWDER_SNOW to emit 40 material units without consuming the block. Reuse the existing 300-tick (15-second at 20 TPS), 10% loaded-chunk gate; no additional column scan or forced chunk loading. A capacity scan may still occur.
- Move rain emissions from Y=192 to the fixed altitude Y=300, retaining 320 units per passed rain check.
- Give each due simulation cell a 50% skip chance. A skipped cell is rescheduled at the normal 200-tick interval, not retried next tick; it can still receive incoming material from neighboring cells. This is not a fixed doubling of the interval or a freeze of skipped cells.
- Preserve world and cache data, protocol 5, condensation rules on processed checks, and existing water-flow/destructive-pressure warnings. No world reset or backlog cleanup.

# 0.12.2-alpha.1

- Fix phantom atmospheric emissions from moving water: wrap the vanilla fluid tick, including Flowing Fluids 1.0.6's injected transport, in an exception-safe nested scope. No stack inspection or per-mutation allocation. No Flowing Fluids dependency is required.
- Suppress removal emissions only inside that transport scope. Direct buckets/removals, block replacements, and scheduled evaporation outside transport retain humidity-scaled emissions. Preserve 300-tick/10% producers, 200-tick simulation, uniform fog color, and side/down-only tiny-cell cleanup.
- Existing accumulated atmosphere and work backlog are retained, not cleared; no world reset or migration is performed. Add focused guard regressions and pre-publication CI production-jar boots without and with pinned Flowing Fluids.

# 0.12.1-alpha.1

- Restrict tiny-cell cleanup to the four horizontal neighbors and the neighbor below; never consolidate upward. Ordinary six-face spreading is unchanged. Keep the <=10 threshold, strictly larger occupied destination, full free-capacity requirement, and conservative persisted/synchronized transfer.
- Preserve biome-driven water evaporation, humidity-scaled removal emissions, 300-tick/10% producers, 200-tick simulation, and uniform Minecraft fog color from 0.12.0-alpha.1. No world reset or migration is required.

# 0.12.0-alpha.1

- New world-changing mechanic: sampled surface water evaporates. Plain water fluid blocks become air; waterlogged blocks keep their host with WATERLOGGED cleared. Non-water solids and unsupported hosts are preserved. Only a successful water-removal hook emits humidity-scaled material next tick, with no direct/depth-based emission or double counting. This includes condensed water and permits natural refilling. No world reset or migration is required; existing terrain-damage backup guidance still applies.

- Loaded-chunk producer passes now run every 300 ticks (15 seconds at 20 TPS), with a 10% per-chunk gate instead of 50 ticks and 25%. Bounded queue processing may delay checks; no chunks are force-loaded.
- Use Minecraft's current fog/horizon color for every near, far, and fallback volume. Remove local light-based grayscale, distance color blending, the unused lighting cache, and unnecessary volume sorting. Preserve LOD coverage, opacity, depth testing, and bounded GPU batches.
- Preserve 200-tick simulation checks, rain emissions of 320 units at Y=192, condensation, storage, and protocol behavior. No world reset is required.

- Scheduled evaporation additionally rolls `clamp(biome temperature / 2, 0, 1)` after the chunk gate: hotter means more evaporation. All successful water removals, including manual changes, produce `round(10 + 70 * clamp(biome downfall, 0, 1))` units (10 dry to 80 wet). Climate uses loaded biome settings, not current weather; queued amounts snapshot humidity at removal.
- After bounded spreading, a due cell with at most 10 units can merge wholly into an existing loaded face neighbor with strictly more material and sufficient free capacity. Prefer the largest destination with deterministic ties; equal amounts never merge. Preserve totals, persist/sync both changes, and remove the empty source. No new cell, chunk load, or pressure overflow; solitary/blocked cells retain material.

# 0.11.0-alpha.1

- Smoothstep from light-based grayscale through half the Minecraft view distance to full current fog/horizon color at the view distance. Coarse volumes in the blend region use air-count-weighted base-cell light averages, with at most 32 base cells sampled per client tick. Preserve LOD coverage and persistent visual caches.
- Schedule producer passes over all loaded chunks every 50 ticks (2.5 seconds at 20 TPS), replacing player-offset sampling. Each chunk gets a random 25% default gate and one random X/Z column per pass. A bounded fair queue can delay checks; no chunks are force-loaded.
- Decouple simulation from producers: use fixed 200-tick (10-second at 20 TPS) simulation checks, superseding the size-scaled 250-tick cadence. Condensation chance and consumption per due check are unchanged.
- Replace ground-level rain emissions with 320 material units at cloud height Y=192 per passed rain check, eight times the previous 40. Preserve water-depth fog, high-terrain clouds, and dark exposed-ground sources.
- Emit 40 material units at a block position when water transitions to nonwater. Ordinary water-level changes and chunk unloads do not trigger this source.
- Preserve protocol 5, cache/render/sync intervals, world data, and personal visual caches. No world reset is required. Existing destructive-pressure and flowing-water warnings remain in effect.

Minor feature release. Automated verification runs in CI; hosted startup checks
the required water-transition mixin. No local tests or performance measurements
are performed; appearance remains for user testing.

# 0.10.0-alpha.1

- Tint coarse LOD volumes with Minecraft's actual current fog/horizon color, including cached fallbacks.
- Shade nearby 4-block detail by mean effective light of air blocks, normalized from black at 0 to white at 15. Include sky darkening and block light; exclude non-air blocks. Sample only loaded terrain, at most 32 cells per client tick, with 20-tick refresh requests and neutral gray before sampling. Bound the disposable lighting cache to 8,192 cells.
- Replace the uniform-color composition assumption with cached spatial back-to-front volume ordering, preserving bounded GPU batches and avoiding per-slice sorting. Near unloaded fallbacks are ordered with gray detail, not blindly drawn behind it.
- Preserve opacity, 250-tick simulation checks, condensation, protocol 5, world data, and personal visual caches. No world reset is required.
- Add focused color and spatial composition regression tests for CI; local tests and performance measurement are intentionally skipped.

Minor client-lighting feature release, superseding the unpublished 0.9.1 attempt;
actual appearance remains for user testing.

# 0.9.0-alpha.1

- Add distance-based client rendering LOD. With Minecraft view distance `V` expressed in blocks, render 4x4x4-block volumes below `V/2`, 8x8x8 in `[V/2, V)`, 16x16x16 in `[V, 2V)`, and 32x32x32 in `[2V, 4V]`.
- Build each coarser volume by recursively averaging eight children, including empty volumes in the average. Render non-overlapping coverage, not coarse parents on top of their finer children.
- Use fewer rendered volumes and slices at distance; no measured FPS improvement or runtime verification is claimed. Actual performance remains for user verification.
- Retain the persistent client visual cache and four-times-view reach. Far fog still requires previously seen areas and can be stale; LOD does not load chunks or change server simulation resolution.
- Preserve 4-block simulation cells, the 250-tick cadence (12.5 seconds at 20 TPS), condensation chance and consumption per check, and cache/render/sync intervals. No data or world reset is required.
- Existing water-flow and destructive-pressure warnings remain unchanged: back up worlds before upgrading.

- Bound spatial selection work, discard old-view builds after teleport, and provide non-overlapping coarse cached coverage while refining a new view. Preserve same-tick opacity interpolation across packet updates.

Minor pre-1.0 client-rendering feature release. Automated verification runs in CI;
local tests and performance measurements are intentionally skipped.

# 0.8.1-alpha.1

- Increase the cadence base from 250 to 1000: `1000 * cellSize / 16` ticks, 40 times the original 25-base delay instead of 10 times.
- Current 4-block cells use 250 ticks (12.5 seconds at 20 TPS); size 1 averages 62.5 ticks, size 16 uses 1000, and size 32 uses 2000.
- Preserve condensation probability/consumption per check, pressure limits, persistent data, cached/far rendering, producer reach, sync intervals, and protocol 5. Only scheduled simulation/source delays change.
- Update cadence and due-work regression tests, including callback coverage, for CI verification.

Compatible tuning patch; no world/data reset. Local Gradle and Minecraft tests
are intentionally skipped; automated verification runs in CI.

# 0.8.0-alpha.1

**World-changing feature: atmospheric condensation places real water sources.
Water flows normally and can wet builds. Back up worlds before upgrading;
the existing destructive-pressure warnings also remain in effect.**

- On each cell's scheduled check, fullness above 50% gives a linear water-placement chance: 0% at 50%, 5% at 75%, and 10% at or above 100%. At or below 50%, no placement occurs.
- On a successful roll, try placing one water source at a random air block in that same cell. Never replace solids. Only successful placement consumes material: 25% of the current amount, rounded down with a minimum of 1 unit.
- No available air or failed placement means no consumption. Ultrawarm dimensions, including the Nether, allow neither placement nor consumption.
- Retain the slower `250 * cellSize / 16` cadence: current 4-block cells average 62.5 ticks (62/63 intervals), approximately 3.125 seconds at 20 TPS. Checks remain scheduled and work-budgeted, not guaranteed wall-clock events.
- Preserve persistent server material, the client visual cache, coarse far fog, 96-block producer reach, and cache/render/sync intervals. No data or world reset is required.

Minor pre-1.0 feature release. Gameplay and performance should be evaluated in
the hosted world; this change does not promise a performance improvement.

# 0.7.1-alpha.1

- Increase simulation/source check delays tenfold to reduce check frequency in response to lag: `250 * cellSize / 16` ticks. Current 4-block cells average 62.5 ticks using 62/63-tick intervals, approximately 3.125 seconds at 20 TPS (previously 6.25 ticks).
- Size 1 averages 15.625 ticks; size 16 uses 250 ticks; size 32 uses 500 ticks. Simulation remains work-budgeted.
- Preserve 96-block producer reach, cache/render/sync intervals, protocol 5, and persistent data formats. No data or world reset is required.
- Keep the four-pressure-attempt limit per sampling interval and all destructive-pressure/backup warnings; the interval is now longer.

Compatible cadence-tuning patch. Runtime performance remains subject to user
verification; reduced check frequency is not a measured lag-resolution claim.

# 0.7.0-alpha.1

- Add a persistent client visual cache and coarse far fog extending to four times the client view distance, only in previously seen areas. Cached fog is approximate and can be stale; it is not server simulation and does not load or simulate distant chunks.
- Protocol 5 scopes snapshots and chunk freshness to a stable world UUID stored in server SavedData. Update client and server together; earlier protocols are incompatible. Separate worlds have separate identities, and a newly reset world receives a fresh UUID.
- Store client visuals under `gameDirectory/dynamicatmosphere-cache`, keyed by hashed server/world/dimension/layout identity. Write changed chunks atomically every 10 seconds and on disconnect.
- Bound disk caching to 64 MiB and 8,192 files, with a 200,000-cell RAM restore limit. Fresh server snapshots supersede cached observations for their scope; cached data never restores server material.
- Include the 0.6.1 cadence tuning: current 4-block cells average 6.25 ticks, producer offsets reach 96 blocks, and simulation work remains budgeted.
- Retain default-enabled destructive pressure and all backup warnings below. No world reset is required.

Minor pre-1.0 feature release. Implementation and release preparation are in
progress; no local runtime testing is requested. CI verification and hosted
status checks are separate from user-run server/client validation.

# 0.6.1-alpha.1

- Tune simulation/source cadence to `25 * cellSize / 16` ticks: quarter the old 100-tick base, then scale by cell size. Current 4-block cells average 6.25 ticks using 6/7-tick intervals; 16-block cells would use 25 ticks, 1-block cells 1.5625, and 32-block cells 50.
- Expand producer offsets from 12 to 96 blocks (8x), keeping the same eight sampled positions per pass and loaded-only checks.
- Match visibility to Minecraft's tracked terrain and effective client render distance, loaded chunks, and frustum. Remove fixed atmospheric radii, nearest-cell truncation, the 4,096-cell cache cap, and the 512-cell draw cap.
- Protocol 4 adds snapshot completion across 512-cell packets, preserving retained opacity and replacing membership only after the full view arrives. Update both client and server together; protocol-3 clients are incompatible.
- Keep delta sync every 20 ticks, full snapshots every 200 ticks, and the 128-source-per-tick work budget.
- Pressure remains limited to four attempts per sampling interval, so its opportunities also occur more often. Default-enabled terrain damage and backup warnings still apply.

Routine compatible tuning patch; no new storage format or world reset. Runtime
and client verification for this tuning release remain pending user testing.

# 0.6.0-alpha.1

**BREAKING behavior: destructive pressure is enabled by default and can damage
terrain and player builds, without claim/protected-area support. Back up worlds
before upgrading; downgrading does not undo damage. Restore a backup to roll back.**

- Relieve trapped excess by breaking the lowest-hardness eligible source-cell block with item drops, then progressing outward when source blocks are gone. Each broken block adds 1 material unit.
- Bound pressure work per pass; exempt negative-hardness/intrinsically unbreakable blocks. Excess that cannot escape remains blocked and reported, not deleted.
- Remove natural material decay; equalize fullness across six face-adjacent grid cells, with no diagonal transfer.
- Scale capacity with vacant air blocks (0..64); equalize amount/capacity rather than raw amounts. Zero-air cells block transfer. This is a coarse rule, not an exact airtight-wall simulation.
- Synchronize capacity changes and render opacity relative to fullness; protocol version 3 requires matching clients and servers.
- Push overfull excess farther outward toward nearest available capacity through air-capacity neighbors using bounded searches. Unknown unloaded boundaries and exhausted budgets remain pending and never authorize pressure destruction or discard material.
- Keep 4x4x4-block cells and five-second source sampling; budget simulation work across ticks.
- Replace the global 1,024-cell limit with sparse per-Minecraft-chunk storage. No range-based deletion; amounts save with chunks, release their simulation mirror on unload, and restore on reload/restart with capacity recomputed.
- Store up to 1,000,000 material units per cell while capacity remains 0..1000; network/work budgets do not cap total stored cell count.
- Daylight stops the dark-ground source but does not clear existing fog; material can continue spreading without decay.
- No world reset required; atmospheric amounts and pressure damage persist.

Minor pre-1.0 bump for spreading and breaking pressure behavior. Install the same
version on both server and client. Automated build/tests pass; in-game pressure
verification remains pending player testing.

# 0.5.0-alpha.1

- Reduce atmospheric cells to 4x4x4 blocks with shared server/client grid coordinates.
- Preserve the 64-block subscription radius and existing cell/network limits.
- Correct render bounds and chunk visibility checks for sub-chunk cells, including negative coordinates.
- Keep proportional translucent volume sampling for the smaller cells.
- Exposed dry ground produces more material at lower effective light levels, creating nighttime fog that decays after daylight returns.
- Effective light includes sky darkening and local block light; this source does not emit on water/lava or sheltered ground.

Minor pre-1.0 compatibility bump: update both sides together. The new protocol
rejects older 16-block-grid clients. In 0.5, grid state was not saved (persistence
arrives in 0.6); no world reset.

# 0.4.0-alpha.1

- Rain adds material where it lands on exposed terrain, roofs, canopies, or water.
- Uses Minecraft's local rain check; sheltered locations, dry biomes, and snow do not emit rain material.
- Rain contributions stack with existing water/cloud sources, with shared-player deduplication.
- Local decay, grid synchronization, and world data remain unchanged.
- Runtime status reports rain emission counts.

Minor pre-1.0 bump for a new atmospheric source. No world reset required.

# 0.3.0-alpha.1

- World-aligned atmospheric grid with 16x16x16-block cells.
- Existing fog/cloud sources add material; each cell decays independently.
- Nearby-client snapshots and batched updates, including removed cells.
- Translucent client rendering with material-dependent opacity.
- No inter-cell flow, terrain changes, or world reset.

**Compatibility change:** install this release on both server and client.
Minor pre-1.0 bump for the new grid and network/rendering capability.

# 0.2.0-alpha.1

- Persistent fog patches over water, with density influenced by water depth.
- Bounded loaded-area sampling, particle output, and near-player state.
- Existing operator demo and runtime status remain available.
- No world reset, world generation changes, or custom client renderer required.

Minor pre-1.0 bump: new visual capability, not just a delivery-spike bug fix.
The full terrain-aware weather simulation remains future work.

# 0.1.0-alpha.1

First delivery spike for Minecraft 1.21.1 on NeoForge.

- Small, bounded cloud/fog particle effects near players.
- Operator-only status and demo commands for runtime verification.
- GitHub release and Modrinth publication driven by the checked-in version.

This is a visual/runtime prototype, not the planned terrain-aware atmospheric
simulation. No real precipitation, gas transport, pollution, or world generation
changes are implemented in this version.
