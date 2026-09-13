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
