# T2 — Rename violence to Void Gas

Repo: `brooswit-minecraft/dynamic-atmosphere`. Depends on: nothing. Shares
files with T1; whichever lands second rebases.

## Goal

The gas currently called `violence` is called Void Gas everywhere: code
identifiers, saved data id, config section, display strings, and docs.

## Scope

- `AtmosphereMaterial.VIOLENCE("violence", …)` becomes `VOID_GAS("void_gas", …)`.
  Keep the enum ordinal position: the `StreamCodec` encodes ordinal, so moving
  it silently remaps materials.
- `AtmosphericMaterials.VIOLENCE` (engine) likewise, including its
  `MaterialDefinition` name string and the `ALL` list ordering.
- `ViolenceGameplay` and `ViolenceGameplayTest` rename to `VoidGasGameplay` /
  `VoidGasGameplayTest`, with their constants.
- `DynamicAtmosphereServerConfig`: the `violence` config section and its
  `VIOLENCE_HOSTILE_DEATH`, `VIOLENCE_NETHERRACK`, `VIOLENCE_BOTTOM`,
  `VIOLENCE_BOTTOM_DENOMINATOR`, `VIOLENCE_SPAWN_DENOMINATOR` keys become a
  `voidGas` section. `DynamicAtmosphereClientConfig` `VIOLENCE_*` keys
  likewise. Old config values may be discarded — no migration, and users get
  defaults back.
- Saved gas under the old `violence` id may be discarded; `ForgeMaterialStorage`
  keys by id, so old data simply stops loading. Confirm it does not crash.
- Docs: `README.md` (material table and prose), `ATMOSPHERIC-MATERIALS.md`,
  `DUST-ENDER-GAS-GAMEPLAY.md` if it mentions the gas, and `CHANGELOG.md`.
  Note the discarded config in the changelog as a breaking change.

## Out of scope

Appearance — the black, smoke-dark rendering is T3. Sources and gameplay
effects keep their current behaviour; this is a rename, not a redesign.

## Definition of done

- No `violence`/`VIOLENCE` identifier remains outside changelog history.
- Enum ordinals unchanged; client and server agree.
- A world saved before the rename loads without crashing.
- Tests updated and passing.
