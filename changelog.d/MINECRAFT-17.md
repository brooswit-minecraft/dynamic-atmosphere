bump: minor

### Changed

- Void Gas: removed the consumption-driven zombie spawn that used to fire above 75% fullness (ATMO-22 comment 23227: "I want to get rid of the hostiles spawning to consume void gas. Void gas is just a hazard for now."). The villager-breeding band at 10-25% fullness is unchanged. No damage path was added — Void Gas remains a hazard with no mechanism yet, per ATMO-22 comment 23231, which reserves that decision for Brooswit.
- Void Gas is now a valid surface-spawn location "like vapor": a Void Gas cell strictly more than half full satisfies `VaporHostileSpawnGate` the same way a more-than-half-full Vapor cell does today, instead of spawning anything itself. This is Overworld-only, matching the existing Vapor gate exactly — confirmed by Brooswit after reviewing an initial draft that offered an all-dimensions config option; there is no such option in the shipped behavior.

Removed (server config, `[voidGas]` section of `dynamicatmosphere-server.toml`), no replacement — these only drove the removed zombie spawn path:

| Key | Old default | Old range |
| --- | --- | --- |
| `voidGas.spawnChanceDenominator` | 32 | 1-1,000,000 |
| `voidGas.spawnAttempts` | 8 | 1-1024 |

No new config keys: the Overworld-only dimension scope needed no flag once confirmed.
