# T1 — Uniform 4x4x4 atmosphere cells

Repo: `brooswit-minecraft/dynamic-atmosphere`. Depends on: nothing.

## Goal

Every atmosphere material simulates on `4x4x4` block cells. Today sizes differ
per material: dust `2`, ender gas `2`, exhaust `2`, vapor `4`, smoke `4`,
violence `8`, slime `16`.

## Scope

- `AtmosphereMaterial` (forge): every entry's `cellSize` becomes `4`. Decide
  whether the per-entry field is still worth keeping or should collapse to the
  shared `AtmosphereGridLayout.CELL_SIZE`; keeping one source of truth is
  preferred.
- `AtmosphericMaterials` (engine): the approved spec catalog must match —
  `VAPOR`, `SMOKE`, `VIOLENCE`, `SLIME`, `ENDER_GAS`, `DUST`, `EXHAUST` all at
  size `4`.
- Capacity: `capacityForAirBlocks` cubes the cell size, so per-cell capacity
  changes for every material except vapor and smoke. Check
  `MaterialCapacityCache` and `AtmosphereCapacityCache`, which derive
  `16 / cellSize` cells per chunk.
- Storage: saved atmosphere may be discarded. Bump the chunk data version so
  old chunks are rejected cleanly rather than silently reinterpreted;
  `ForgeAtmosphereStorage` already stores and checks `cell_size`, and
  `ForgeMaterialStorage` keys by material id.
- Fans: `AtmosphereFanTransport` currently only moves materials with cells of
  `4` blocks or smaller. After this change Void Gas/violence and Slime become
  fan-transportable for the first time. That is intended; cover it with a test
  rather than re-adding a size gate.
- LOD: `MaterialSettings.LodBand` entries were tuned per material at the old
  sizes (`VIOLENCE_LOD`, `DUST_LOD`, `VAPOR_LOD`). Re-check that the bands
  still make sense at a uniform size and say in the PR what you concluded.

## Out of scope

Opacity values (T3), dissipation rates (T4), the rename (T2), movement (T5).

## Definition of done

- All materials report cell size `4`; capacity and chunk-coordinate maths
  follow.
- Old saved atmosphere data loads without crashing and without being
  misinterpreted.
- Tests updated and passing, including `AtmosphereMaterialTest`,
  `AtmosphericMaterialsTest`, `MaterialCapacityCacheTest`,
  `AtmosphereFanTransportTest`, and the grid layout and payload tests.
- README material table and CHANGELOG updated.
