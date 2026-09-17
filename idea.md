# Atmosphere Cell and Gas Behavior

## Intent

Make the atmosphere simulation feel spatially coherent and make the heavy gases
read as dangerous, persistent clouds rather than short-lived effects.

## Proposed changes

- Make every atmosphere cell `4x4x4` blocks.
- Keep vapor as the visual opacity baseline.
- Make smoke exactly `2x` as opaque as vapor, and tint it slightly brown.
- Rename the current `violence` gas to `void gas`.
- Render void gas black, at the pre-change smoke darkness (the current `4x`
  optical density), so it stays as dark as smoke is today after smoke drops
  to `2x`.
- Make `ender gas`, `slime gas`, and `void gas` dissipate gradually like smoke,
  at half smoke's rate.

## Density-directed movement

When a cell contains only a small percentage of its capacity, its movement
should be biased toward the cloudline rather than downward. As the cell becomes
fuller, the bias should reverse: movement should favor downward travel over
travel toward the cloudline.

The movement rule is continuous, not a hard threshold: the probability of
moving toward the cloudline is a linear interpolation on fill fraction, from
fully cloudline-biased at empty to fully downward-biased at full, with the
downward probability as its complement.

## Settled decisions

These were open questions in the first draft of this brief. They are now
decided, and implementation should not reopen them.

- **"Nether gas" means Ender Gas.** There is no separate nether gas material.
  The three gases that get slower dissipation are Ender Gas, Slime, and Void
  Gas.
- **Cells are `4x4x4` cubes for every material**, replacing the current
  per-material sizes (dust `2`, ender gas `2`, exhaust `2`, vapor `4`,
  smoke `4`, violence `8`, slime `16`).
- **Existing saved atmosphere data may be discarded.** No chunk migration is
  required; worlds may come back with empty atmosphere.
- **Smoke is slightly brown and `2x` vapor opacity**, down from the current
  `4x` optical density multiplier.
- **Void Gas keeps the old pre-change smoke appearance**: black, at smoke's
  current darkness.
- **Old `violence` config may be discarded** and replaced with a `void gas`
  config section. No config migration is required.
- **Dissipation for Ender Gas, Slime, and Void Gas runs at half smoke's rate.**
- **Movement bias is linear interpolation on fill fraction**, cloudline-biased
  at low fill, downward-biased at high fill.

## Implementation notes from code review

Verified against `main` at `348ec52`; verify these again before relying on them.

- `AtmosphereMaterial` carries the per-material `cellSize` and derives cell and
  chunk coordinates and `capacityForAirBlocks` (which uses `cellSize` cubed)
  from it. `AtmosphereGridLayout.CELL_SIZE` and `SmokeGridLayout.CELL_SIZE` are
  already `4`.
- `ForgeAtmosphereStorage` stores `cell_size` in chunk NBT and treats a
  mismatch as unreadable data, which it preserves rather than simulating. With
  saved data discardable, the simplest route is a storage version bump.
- `ForgeMaterialStorage` keys saved material data by `AtmosphereMaterial.id()`,
  so the `violence` -> `void_gas` id change drops old saved gas, which is
  acceptable here.
- The network `StreamCodec` encodes material ordinal, not id, so the rename does
  not change the wire format, but client and server must ship together.
- `DynamicAtmosphereServerConfig` has a `violence` config section
  (`VIOLENCE_HOSTILE_DEATH`, `VIOLENCE_NETHERRACK`, `VIOLENCE_BOTTOM`,
  `VIOLENCE_BOTTOM_DENOMINATOR`, `VIOLENCE_SPAWN_DENOMINATOR`) and
  `DynamicAtmosphereClientConfig` has `VIOLENCE_*` and `SLIME_*` entries.
- `AtmosphereFanTransport` only moves materials whose cells are `4` blocks or
  smaller. Making every cell `4` makes Void Gas and Slime fan-transportable for
  the first time; that is a consequence of the resolution change and should be
  covered by tests.
- There is no cloudline movement function today. The nearest existing concepts
  are the `RAIN_CLOUD_HEIGHT` server config value and the rain-cloud code in
  `ForgeAtmospherePrototype`. The fill-biased vertical movement is new
  behaviour, not an edit to an existing rule.
- `AtmosphericMaterials` in the `engine` module holds the parallel approved
  specification catalog (`violence`, cell size `8`, `RED`) and must be updated
  alongside the forge enum.
- Tests that will need to change include `AtmosphereMaterialTest`,
  `AtmosphericMaterialsTest`, `MaterialClientTest`, `ViolenceGameplayTest`,
  `SmokeDissipationTest`, `AtmosphereFanTransportTest`,
  `MaterialCapacityCacheTest`, and the grid layout and payload tests.

## Acceptance shape

The implementation should demonstrate the new cell size, the relative opacity
values, the renamed void gas appearance, half-rate dissipation for the three
heavy gases, and a smooth low-fill/high-fill movement bias with focused tests.
