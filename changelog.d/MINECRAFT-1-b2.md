bump: minor

### Added

- **Glow Gas** (MINECRAFT-1 B2): an eighth atmospheric material, emitted by glowstone (100 units per glowstone block found by a bounded, palette-gated section scan of each loaded-chunk producer check). Visual only: warm gold (0xFFC83C), 2x Vapor's optical density, rendered unlit — it carries no real block light, grants no spawn eligibility, and has no entity effects. Its preferred resting height is a literal Y=0, not configurable.

Added (server config, `dynamicatmosphere-server.toml`), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `glowGas.glowstoneEmission` | 100 | 0-1,000,000 |

Added (client config, `dynamicatmosphere-client.toml`), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `materials.glowGasOpticalDensity` | 2 | 0-1000 |

Network protocol bumped `10` -> `11`: a client and server must be on the same release of this mod to connect, as with any new atmospheric material. This ticket's second fragment, filed separately from `MINECRAFT-1.md` (already consumed by the B1 release) rather than editing it.

This is a partial MINECRAFT-1 slice covering B2 (Glow Gas) only; Nether Fog (B3) and End Sand (B4) remain unimplemented.
