bump: minor

### Changed

- Slime: removed the consumption-driven spawn that used to fire above 75% fullness on every processed cell (ATMO-22 comment 23227: "Slimes also shouldn't do that [consumption-spawn] behavior, just on dissipation there is an additional chance that a slime spawns. Size of the slime is based on how dense the slime for is there."). A Slime spawn is now an independent, configurable chance rolled each time a cell actually loses material to `heavyGas` dissipation — the same `HeavyGasDissipation` call site MINECRAFT-16/ATMO-24 retunes `heavyGas.dissipationFactor` for. The spawned slime's size (vanilla small/medium/big = 1/2/4) is picked by the cell's amount/capacity density at the moment of dissipation, against configurable thresholds. No material is consumed by the spawn itself — the dissipation loss is the only cost; the underground production path (`undergroundEmission`/`undergroundChanceDenominator`) is unchanged.

Removed (server config, `[slime]` section of `dynamicatmosphere-server.toml`), no replacement — these only drove the removed consumption-driven spawn path:

| Key | Old default | Old range |
| --- | --- | --- |
| `slime.spawnChanceDenominator` | 32 | 1-1,000,000 |
| `slime.spawnAttempts` | 8 | 1-1024 |

Added (same `[slime]` section), `RestartType.NONE`:

| Key | Default | Range | Purpose |
| --- | --- | --- | --- |
| `slime.dissipationSpawnChanceDenominator` | 32 | 1-1,000,000 | Chance, as 1/denominator, a Slime spawns when a cell loses material to `heavyGas` dissipation. |
| `slime.dissipationSpawnAttempts` | 8 | 1-1024 | Loaded-only spawn-position attempts tried per dissipation-triggered spawn roll. |
| `slime.mediumDensityThreshold` | 0.25 | 0-1 | Amount/capacity density, at the point of dissipation, at or above which the spawn is medium (size 2) instead of small (size 1). |
| `slime.bigDensityThreshold` | 0.75 | 0-1 | Amount/capacity density, at the point of dissipation, at or above which the spawn is big (size 4) instead of medium. |
