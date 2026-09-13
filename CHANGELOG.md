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
