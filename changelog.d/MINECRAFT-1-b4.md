bump: minor

### Added

- **End Sand** (MINECRAFT-1 B4): the mod's first registered block and item, `dynamicatmosphere:end_sand` (ID frozen — renaming it later is breaking for sickos). Rises one block every `endSand.riseIntervalTicks` ticks; when blocked above it stops rising and resumes the moment it's freed, and is deleted outright rather than placed if a rise would cross the dimension's build-height ceiling. Mineable, drops itself. Player-placed End Sand rises exactly the same way as condensed End Sand — both go through the same placement hook.
- Ender Gas (Obsidian Powder) gains a third, End-only production path: near the dimension's build-height ceiling, a chunk gate mirroring Vapor's own producer pattern gates emission per loaded End chunk per pass. At high fullness, a dense Ender Gas cell in the End occasionally condenses into End Sand.
- Ships a flat placeholder purple texture for End Sand; real block art is a follow-up.

Added (server config, `dynamicatmosphere-server.toml`), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `obsidianPowder.endEmission` | 200 | 0-1,000,000 |
| `obsidianPowder.endGateChance` | 0.10 | 0-1 |
| `obsidianPowder.endSandCondensationChance` | 0.10 | 0-1 |
| `endSand.riseIntervalTicks` | 20 | 1-72,000 |

This ticket's fourth and final fragment (B1: `MINECRAFT-1.md`, B2: `MINECRAFT-1-b2.md`, B3: `MINECRAFT-1-b3.md`), filed separately since all three prior fragments were already consumed by their own releases.

This completes MINECRAFT-1's B1-B4 scope (preferred resting heights, Glow Gas, Nether Fog, End Sand).
