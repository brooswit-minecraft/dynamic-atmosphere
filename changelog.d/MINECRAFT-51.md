bump: minor

### Added

- Generic atmospheric event/action system (spec "Dynamic Atmosphere / Weather System v0.2" sections 23, 26, 29, 30, 40): a Minecraft-free `engine.event` package (`Condition`, `AtmosphericAction`, `AtmosphericEvent`, `PrecipitationRule`, `SpawnModifier`, `AtmosphericEventEngine`) plus a Forge-side action executor (`AtmosphericActionExecutor`) interpreting the seven generic action types (`spawn_entity`, `spawn_item`, `place_block`, `replace_block`, `apply_effect`, `emit_material`, `transfer_material`). This single engine evaluates every event below — there is no per-material special-cased Java logic for "is it lightning" or "is it a Ghast".
- Lightning (spec section 25/40/41): a data-defined event on the Vapor/Cloud grid — concentration >= `lightning.minConcentration` and precipitating fires `minecraft:lightning_bolt` at a `precipitation_surface` target and consumes `lightning.materialCost` of the cell. No electrical charge simulation, no thunderstorm material or subsystem; a severe storm dissipates itself purely because repeated lightning consumes Vapor.
- Dense-Smoke → Ghast spawn (spec sections 30/46): the second worked proof that the event/action layer is genuinely generic — the identical `AtmosphericEvent`/`AtmosphericEventEngine` code path as Lightning, different data, on the Smoke grid (`ghastSpawn.*` config).
- Zombie Fog (new `AtmosphereMaterial`/`MaterialDefinition`, id `zombie_fog`): zombie-death entity source (spec section 17, via new generic `EntityDeathMaterialSource`), a zombie spawn-attempt multiplier (spec section 29/45's own worked example, via new generic `AtmosphericSpawnModifierGate`), and a biome-temperature heat sink (spec section 47) expressed as a threshold-based event with no actions — a sink is just an event that only consumes material.
- Slime Fog precipitation (spec section 44): rain mode occasionally drops a slimeball, snow mode occasionally places a slime block, chosen by biome temperature like vanilla rain/snow. Independent of, and does not change, MINECRAFT-18's existing dissipation-triggered additive spawn.
- `PrecipitationTargeting`: the generic, direction-aware "precipitation_surface" target search (spec section 24) shared by Lightning and Slime Fog's precipitation rules — a randomly selected, loaded, exposed surface beneath (or above, for `UP`-directed materials) a cell.

Added config sections (`dynamicatmosphere-server.toml`), `RestartType.NONE`:

| Section | Key | Default | Range |
| --- | --- | --- | --- |
| `[lightning]` | `minConcentration` | 0.85 | 0-1 |
| `[lightning]` | `chance` | 0.002 | 0-1 |
| `[lightning]` | `materialCost` | 0.03 | 0-1 |
| `[lightning]` | `surfaceSearchAttempts` | 8 | 1-64 |
| `[ghastSpawn]` | `minConcentration` | 0.90 | 0-1 |
| `[ghastSpawn]` | `chance` | 0.0001 | 0-1 |
| `[ghastSpawn]` | `materialCost` | 0.20 | 0-1 |
| `[zombieFog]` | `deathEmission` | 24 | 0-1,000,000 |
| `[zombieFog]` | `spawnConcentrationThreshold` | 0.30 | 0-1 |
| `[zombieFog]` | `spawnMultiplier` | 2.0 | 0-100 |
| `[zombieFog]` | `heatMinBiomeTemperature` | 0.5 | -10-10 |
| `[zombieFog]` | `heatChance` | 0.05 | 0-1 |
| `[zombieFog]` | `heatMaterialCost` | 0.08 | 0-1 |
| `[slimePrecipitation]` | `rainMinConcentration` | 0.65 | 0-1 |
| `[slimePrecipitation]` | `rainMinBiomeTemperature` | 0.15 | -10-10 |
| `[slimePrecipitation]` | `rainChance` | 0.02 | 0-1 |
| `[slimePrecipitation]` | `rainMaterialCost` | 0.01 | 0-1 |
| `[slimePrecipitation]` | `snowMinConcentration` | 0.65 | 0-1 |
| `[slimePrecipitation]` | `snowMaxBiomeTemperature` | 0.15 | -10-10 |
| `[slimePrecipitation]` | `snowChance` | 0.002 | 0-1 |
| `[slimePrecipitation]` | `snowMaterialCost` | 0.04 | 0-1 |
| `[slimePrecipitation]` | `surfaceSearchAttempts` | 8 | 1-64 |
