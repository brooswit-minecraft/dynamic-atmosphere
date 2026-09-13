# 0.5.0-alpha.1

- Reduce atmospheric cells to 4x4x4 blocks with shared server/client grid coordinates.
- Preserve the 64-block subscription radius and existing cell/network limits.
- Correct render bounds and chunk visibility checks for sub-chunk cells, including negative coordinates.
- Keep proportional translucent volume sampling for the smaller cells.
- Exposed dry ground produces more material at lower effective light levels, creating nighttime fog that decays after daylight returns.
- Effective light includes sky darkening and local block light; this source does not emit on water/lava or sheltered ground.

Minor pre-1.0 compatibility bump: update both sides together. The new protocol
rejects older 16-block-grid clients. Grid state is transient; no world reset.

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
