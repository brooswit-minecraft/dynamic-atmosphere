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
