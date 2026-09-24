# Dust and Obsidian Powder Gameplay Defaults

These are the configurable implementation defaults for the combined
`0.16.1-alpha.1` release. Both materials use the shared 200-tick simulation and
300-tick scheduled-producer cadence. Event producers remain event-driven. All
queries are bounded and loaded-only; neither system scans a dimension or forces a
chunk to load.

## Dust

- Walking emits 1 unit every 10 ticks of actual grounded movement.
- Running emits 3 units every 5 ticks of actual grounded movement.
- Walking or running in water, or on ice, snow, snow blocks, or powder snow,
  emits the same amount as Vapor instead of Dust. Routing is mutually exclusive.
- Jump transitions emit 8 Dust. Ordinary landing transitions emit 6 Dust.
- A landing that deals fall damage emits `16 + ceil(2 * damage)` Dust, capped at
  64, and suppresses the ordinary landing burst for that tick.
- Breaking a block emits 16 Dust; placing one emits 12; a successfully placed
  falling block emits 24 at its landing position.
- Each processed 4x4x4 Dust cell examines at most its 64 blocks. Plain water
  can become mud with chance 0% at 50% fullness, rising linearly to 100% at full
  capacity. Success consumes half the current Dust, rounded up. Failed placement
  or debit restores the water and consumes nothing. Waterlogged hosts are ignored.
- Overfull Dust has a 1/16 chance per processed turn to place gravel in air.
  Successful placement consumes 25% of current Dust, rounded down with a minimum
  of one; failure consumes nothing.
- An independent 1/64 processed-turn roll dissipates up to 40 Dust.

## Obsidian Powder

Obsidian Powder uses 4x4x4 server cells and a client optical density of 40. The density
is visual only and does not alter amounts, capacity, fullness, or gameplay gates.
Dust and Obsidian Powder render only their base-cell LOD through V/4. Like all materials,
their geometry uses `baseCellSize / slicesPerBaseCell` slice spacing rather than a
distant one-slice shortcut, while retaining thickness-integrated optical density.

- Endermen, endermites, Ender Dragons, witches, and shulkers emit 1 unit every
  40 ticks. Any entity actively occupying a Nether portal emits 2 every 20 ticks.
- A sampled Nether portal, Ender chest, soul torch or wall torch, soul fire, or soul
  sand emits 1 unit (`obsidianPowder.passiveBlockEmission`).
- A sampled plain Obsidian block emits 1 unit on its own key
  (`obsidianPowder.obsidianEmission`), separate from the shared passive-block key above.
- A sampled Crying Obsidian block emits 32 units on its own key
  (`obsidianPowder.cryingObsidianEmission`) — deliberately far above the shared passive
  default, and no longer folded into it.
- A successfully spawned Ender pearl emits 24 units; its first impact emits 48.
- Obsidian Powder never gates spawning. Endermen still emit it as described above, but
  their natural/chunk-generation spawns follow the same Overworld-only Vapor
  (fog) rule as every other monster; Nether and End spawns follow vanilla rules.

## Configuration

These producer amounts, chances, intervals, and Dust effects are exposed in the
world's `serverconfig/dynamicatmosphere-server.toml`. The shared simulation and
producer intervals are under `runtime`; material-specific settings are under
`dust` and `obsidianPowder`. Server values are read through reloadable snapshots and
apply after NeoForge reloads the server config.

Obsidian Powder reach and optical density are client settings in
`<game-directory>/config/dynamicatmosphere-client.toml`. Client presentation
settings hot-reload. Only client `allocation.cellBudget` requires a game restart.
Cell sizes, persisted formats, and the network protocol are structural and are not
configuration options.
