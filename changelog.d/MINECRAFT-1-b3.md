bump: minor

### Added

- **Nether Fog** (MINECRAFT-1 B3): a ninth atmospheric material, very dark red (0x400000), vapor-like LOD. Harvests and condenses lava, ultraWarm dimensions only: a bounded, palette-gated random check consumes one lava source block per producer pass into fog equal to one full block's capacity share; at high fullness a cell places one lava source back into a random air block in itself, at exactly the same cost. Both directions share one constant, so the cycle neither drains nor floods lava over any number of round trips. No explosions either way — both are direct block-state writes, the same technique the existing magma-regrowth feature uses. The existing Smoke "lava disappeared" emission is deliberately not fired for a Nether Fog harvest. Known physics: gas cannot move down out of a cell containing fluid, so Nether Fog pools on the surface of a Nether lava sea rather than reaching the bedrock beneath it — accepted, documented, not a bug. Home is the dimension's own bottom (structural, not configurable).

Added (server config, `dynamicatmosphere-server.toml`), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `netherFog.maxCondensationChance` | 0.10 | 0-1 |

Added (client config, `dynamicatmosphere-client.toml`), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `materials.netherFogOpticalDensity` | 1 | 0-1000 |

Network protocol bumped `11` -> `12`: a client and server must be on the same release of this mod to connect, as with any new atmospheric material.

This ticket's third fragment (B1: `MINECRAFT-1.md`, B2: `MINECRAFT-1-b2.md`), filed separately since both prior fragments were already consumed by their own releases.

This is a partial MINECRAFT-1 slice covering B3 (Nether Fog) only; End Sand (B4) remains unimplemented.
