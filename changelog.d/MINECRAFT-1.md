bump: minor

### Changed

- Fill-biased vertical movement now uses a **preferred resting height per material** instead of every material sharing Vapor's `rainCloudHeight` as a single global cloudline. Vapor keeps using its own `rainCloudHeight` (key unrenamed, so existing worlds are unaffected). Smoke and each of the five named material grids (Dust, Void Gas, Exhaust, Slime, Obsidian Powder) gain their own `preferredHeight` key in their server config section, each defaulting to the same value as `vapor.rainCloudHeight` (256) so installing this version changes no existing world's behavior until an operator tunes a key.

Added (server config, `dynamicatmosphere-server.toml`), `RestartType.NONE`, range -2048..2048, default 256 (matches `vapor.rainCloudHeight`):

| Key | Default |
| --- | --- |
| `smoke.preferredHeight` | 256 |
| `dust.preferredHeight` | 256 |
| `voidGas.preferredHeight` | 256 |
| `exhaust.preferredHeight` | 256 |
| `slime.preferredHeight` | 256 |
| `obsidianPowder.preferredHeight` | 256 |

This is a partial MINECRAFT-1 slice covering only preferred resting heights; Glow Gas, Nether Fog, and End Sand remain unimplemented pending a scope-boundary confirmation with MINECRAFT-52 (see ticket comments).
