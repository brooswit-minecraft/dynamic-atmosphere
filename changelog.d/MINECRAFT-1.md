bump: minor

### Changed

- Vertical movement now moves atmosphere directly (a conserved transfer) before equalization, instead of biasing the equalization step itself, and each material now drifts toward its own preferred resting height ("home") instead of every material sharing Vapor's `rainCloudHeight` as a single global cloudline. See the README's "Preferred resting heights and fill-biased vertical movement" section for the exact formula and per-material homes.
- This changes Void Gas's and Slime's resting behavior, and therefore where their mob spawns occur: both now settle toward the dimension floor instead of drifting toward the old shared cloudline.
- Fixed an engine catalog drift noted in the epic's spec: Obsidian Powder's (Ender Gas's) producer list was missing witch, shulker, ender_chest and crying_obsidian; added as new `MaterialDefinition.Producer` entries.

Removed (server config, `dynamicatmosphere-server.toml`), no replacement:

| Key | Old default |
| --- | --- |
| `verticalMovement.emptyTowardCloudlineChance` | 0.75 |
| `verticalMovement.fullTowardCloudlineChance` | 0.25 |

Added (server config), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `verticalMovement.deadBand` | 2 | 0-64 |
| `verticalMovement.w` | 0.40 | 0-0.5 |
| `verticalMovement.driftFraction` | 0.10 | 0-1 |
| `vapor.netherCloudlineHeight` | 120 | -2048-2048 |
| `vapor.endCloudlineBlocksBelowTop` | 16 | 0-2048 |
| `smoke.preferredHeight` | 144 | -2048-2048 |
| `slime.preferredHeight` | 64 | -2048-2048 |

Dust, Void Gas, Obsidian Powder and Exhaust rest on the dimension's own bottom (its minimum build height), which is structural and not a config key. `vapor.rainCloudHeight` is unchanged and still Vapor's own home in the Overworld.

This is a partial MINECRAFT-1 slice covering B1 (preferred resting heights) only; Glow Gas (B2), Nether Fog (B3), and End Sand (B4) remain unimplemented.
