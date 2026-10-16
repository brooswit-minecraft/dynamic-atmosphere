bump: minor

### Added

- Surface Creeper spawns now require BOTH local Exhaust and nighttime (comment 34257 on MINECRAFT-2): a Creeper spawn without qualifying overhead terrain above it is denied unless the local Exhaust cell is more than half full and it is currently night. This is an eligibility gate layered on top of ordinary surface spawning, modeled on the existing `VaporHostileSpawnGate` -- it only denies spawns vanilla/NeoForge already attempted and never creates one. Below-ground (and any other sheltered) spawning is unaffected, since it already has qualifying overhead terrain. An eligible spawn consumes some of the local Exhaust; a spawn that passed through purely because of qualifying terrain is never charged. The Exhaust source (vehicles) and the car-hits-creeper explosion are out of scope here (MINECRAFT-218).

Added (server config, `[exhaust]` section of `dynamicatmosphere-server.toml`), `RestartType.NONE`:

| Key | Default | Range | Purpose |
| --- | --- | --- | --- |
| `exhaust.creeperSpawnConsumption` | 50 | 0-1,000,000 | Exhaust consumed from the local cell when the night-plus-local-Exhaust surface Creeper spawn eligibility gate allows a spawn through. |
