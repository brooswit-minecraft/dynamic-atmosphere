# Multiple Atmospheric Materials

Status: approved for parallel development with performance work. Performance
fixes remain independently shippable; this document does not indicate deployment.

## Next Release Scope

The user has expanded active development to all seven materials: Vapor, Dust,
Smoke, Violence, Exhaust, Slime, and Ender Gas. Complete their independent grids,
colors, LOD limits, production, and previously specified effects below. Unknown
numeric defaults need explicit implementation choices. This supersedes earlier
notes that defer Violence, Exhaust, or Slime outside the release scope. Keep
the current deployed server stable while implementation and verification proceed;
the scope expansion itself does not indicate that these features are deployed.

Each material has an independent cell grid, cell size, rendering LOD distances,
simulation schedule, producer schedule, and rules. Speed multipliers are relative
to the base schedule. LOD multipliers aggregate that material's base cells;
distances below are multiples of the player's view distance. The last band's
outer distance is a hard rendering cutoff, including cached/fallback visuals.
Keeping cached data does not authorize rendering it beyond that cutoff. These
rendering limits do not delete server material or change simulation coverage.

Smoke, Violence, Slime, and Ender Gas use 4x the normal rendering opacity.
Vapor, Dust, and Exhaust retain 1x. This is a visual-only material setting:
do not multiply stored amounts, capacity/fullness, production, damage, or
transformation thresholds. Preserve consistent opacity across LOD and slice
counts using the existing thickness-integrated rendering model.

| Material | Color | Cell size | LOD | Simulation / producer speed |
| --- | --- | --- | --- | --- |
| Vapor | Current fog color | 4x4x4 | 1x to 0.5 distance; 2x to 1; 4x to 2; nothing beyond | 1x / 1x |
| Dust | Brown | 2x2x2 | 1x to 0.25 distance; nothing beyond | 4x / unspecified |
| Smoke | Black | 8x8x8 | Same as Vapor | 1x / 1x |
| Violence | Red | 8x8x8 | 1x to 1 distance; 2x to 2; nothing beyond | 1x / unspecified |
| Exhaust | Yellow | 2x2x2 | Same as Dust | 4x / 4x |
| Slime | Green | 16x16x16 | Same as Violence | 0.5x / 0.5x |
| Ender Gas | Purple | 1x1x1 | Same as Dust | 8x / 8x |

## Producers and Transformations

### Vapor Plant Growth

Growing crops and saplings in a Vapor cell each have an independent 10% chance
to consume Vapor and receive a bonemeal effect. Proposed MVP defaults: check once
per processed Vapor simulation turn, cost 40 material units per successful
bonemeal application, and require the full cost before applying. Cadence and
cost are implementation defaults, not numeric requirements supplied by the user.
Use normal bonemeal eligibility/success rules; do not consume material for mature
or otherwise ineligible plants or failed applications. Do not affect every
bonemealable block (grass, moss, etc.) merely because it accepts bonemeal.
Keep checks bounded to processed cells; no extra world-wide scan. Mark consumed
material dirty for persistence and synchronization. This feature is not deployed.

### Material Sources

### Vapor Surface Spawn Gate

Replace Sickos' Peaceful Nights mod with a Vapor-aware hostile spawn restriction.
Classify a spawn as underground when its vertical column up to the sky contains
a solid stone, dirt, or grass block above the spawn position. This replaces the
previous canSeeSky test: other cover alone (leaves, glass, wooden roofs, etc.)
does not count as underground. When no qualifying terrain block is overhead,
allow ordinary spawn checks to continue only if Vapor exceeds 50% of that
cell's capacity (strictly greater; exactly 50% does not qualify). Underground
spawning is unchanged. This
restriction never overrides other spawn checks to force a spawn. Smoke and other
materials do not satisfy it. Unknown or zero capacity cannot qualify.
This is a read-only density check: spawning never consumes or reduces Vapor.
Initial implementation scope is natural/chunk-generation hostile spawning;
commands, spawn eggs, spawners, and scripted creation are not implicitly changed.
Stone is defined by Minecraft's `base_stone_overworld` block tag. Dirt terrain is
the explicit set dirt, grass block, coarse dirt, podzol, mycelium, and rooted dirt;
the broader `dirt` tag is intentionally not used because it also includes moss,
mud, and muddy mangrove roots. Qualifying states must have a full collision shape.
The loaded chunk's highest qualifying block is cached per x/z column and relevant
block mutations invalidate only that column; no spawn check loads a chunk.
Remove Peaceful Nights from the pack only alongside the working replacement.

### Material Production

- Vapor: existing rules, including water creation at high material levels and
  sampled snow/ice surfaces emitting 40 vapor units without consuming the block.
  Snow/ice uses the existing 15-second / 10% chunk gate and landing-source sample,
  with no extra scan: the ICE tag, SNOW, SNOW_BLOCK, and POWDER_SNOW qualify.
  The catalog explicitly names this existing runtime source as SNOW_ICE_SURFACE
  (runtime SourceKind SNOW_ICE); it must not be registered a second time when
  adapting EXISTING_VAPOR_RULES.
  Snow or ice removal also emits Vapor at the removed block's position. This is
  additive to ongoing snow-surface production, not a replacement for it.
  Cover snow layers, snow blocks, powder snow, and ICE-tagged blocks; quantities and partial
  layer-removal scaling are implementation defaults to choose. Ignore failed
  mutations and chunk unloading; do not duplicate a successful removal event.
  Player and mob walking/running on snow or ice, or in water, produces Vapor
  instead of Dust. Route each movement emission to one material, not both.
  Running produces more than walking. This explicitly changes movement output;
  jump/landing material selection on these surfaces is not yet specified.
- Dust: players and mobs produce small amounts while walking, more while
  running, and bursts when jumping and landing. Landings that cause fall damage
  produce a larger burst than ordinary landings. Detect actual movement and
  jump/landing transitions, not repeated bursts throughout an airborne interval.
  Exact quantities and the damage-to-burst scaling are implementation defaults
  still to be chosen; combine landing and fall-damage handling deliberately to
  avoid accidental duplicate bursts. Block breaking and placement also produce
  Dust. High material creates gravel blocks.
  Falling blocks landing create a Dust burst at the landing position, once per
  landing event (including sand/gravel and other falling-block entities).
  Avoid accidental duplicate emission from the associated block placement;
  the conservative implementation default is 24 Dust units. Failed placement,
  entity unload, and item-drop removal do not emit a landing burst.
  If a Dust cell contains water, it can turn one water block into mud. Chance
  scales linearly from 0% at 50% fullness to 100% at 100% fullness; below 50%
  is 0%, above 100% stays 100%. A successful conversion consumes 50% of the
  cell's current Dust material; a failed conversion consumes none.
  Exact check cadence, selection among multiple water blocks, and waterlogged-block handling are
  not yet specified. Do not silently overwrite waterlogged host blocks.
  Dust also has an independent 1/64 chance per processed Dust simulation turn
  to dissipate up to 40 units, capped at the remaining amount, with no other
  effect. Persist/sync the reduction and remove empty cells normally.
- Smoke: lava, fire, explosions and blocks destroyed by explosions; furnaces,
  torches, and campfires.
  Next-version request: emit Smoke when fire or lava is added or removed,
  in addition to ongoing production. Do not include this in the currently
  deploying fire-only release. Transition quantities and whether fluid-level
  changes or transport count remain to be specified; avoid repeating the
  water-transport emission amplification bug.
  Explosions explicitly create a Smoke burst at the blast location, even when
  no blocks are destroyed. Emit once per explosion, separately from the
  per-destroyed-block source; burst amount remains to be specified.
  Smoke has a 1/10 chance to check its own cell for leaf blocks. If present,
  remove one leaf block and consume Smoke. Proposed implementation defaults:
  roll once per processed Smoke simulation turn, cost 40 units, require the
  full cost, consume only after successful removal. Roll before scanning and
  never scan outside the cell or force chunk loads. Use the leaves tag; remove
  at most one block per successful check. Leaf-drop behavior is unspecified.
  Separately, Smoke has a 1/128 chance per processed simulation turn to check
  its cell for farmland and turn one farmland block into dirt. Proposed cost:
  40 Smoke units, requiring the full amount and consuming only after successful
  conversion. Roll before scanning; no farmland or failed conversion consumes
  nothing. This chance is independent of the leaf-removal roll.
  Separately, Smoke has a 1/256 chance to check for a villager in its cell and
  turn one into a nitwit, consuming Smoke. Proposed defaults: once per processed
  Smoke simulation turn, 40 units on successful conversion, full cost required.
  Already-nitwit villagers do not qualify and consume nothing. Do not spawn a
  replacement entity; preserve identity and unrelated entity state. Exact age,
  profession/trading eligibility, and trade-state consequences need deliberate
  handling; this is a potentially destructive change to an existing villager.
  Smoke also dissipates: independent 1/64 chance per processed Smoke simulation
  turn to consume material without any other effect. Proposed amount is up to
  40 units, capped at the remaining amount so small residues can fully disappear.
  Dust has the same explicitly requested rule; do not reintroduce passive
  dissipation for Vapor or other materials. Persist and synchronize the reduction,
  including empty-cell removal.
- Violence: hostile mob deaths and Netherrack. High material creates a zombie.
  Each loaded-chunk producer check has an independent 1/8 chance to create a
  small amount of Violence at the bottom of that dimension's world. Use the
  dimension's minimum build height rather than a hardcoded Overworld Y value;
  exact placement within the bottom cell and the small production amount remain
  to be chosen. Do not add Vapor's 10% gate or force chunks to load. Respect
  the bedrock downward-transfer restriction. This belongs to future Violence
  implementation, not the current Smoke/Dust/Ender Gas release.
  At 10% to 25% cell capacity, if a villager is present, consume 5% of the cell's
  current material to put a villager into the breeding state. This is a separate
  low-density effect, not a replacement for high-density zombie creation.
  The exact Minecraft breeding/willingness integration, eligibility, effect
  cadence, and selection when multiple villagers are present remain to be settled;
  do not promise a successful birth or silently bypass normal breeding conditions.
- Exhaust: all living mobs randomly, creepers frequently, and player/mob damage.
  Also supports the same crop/sapling bonemeal rule as Vapor: independent 10%
  chance per eligible plant, consuming Exhaust from its own cell on success,
  not Vapor. Inherit the proposed 40-unit cost and processed-material-turn
  cadence, with normal bonemeal eligibility and no consumption on failure.
  This remains part of future Exhaust implementation, outside the current
  Smoke/Dust/Ender Gas release scope.
  High density deals suffocation-type damage to exposed living entities. Between
  50% and 100% cell capacity, scale damage from 1 to 4 damage points (not hearts)
  and consume 25% to 50% of the cell's current material. Proposed interpolation
  is linear, clamped at the upper endpoint above 100%; below 50% has no damage.
  Apply the damage effect once per Exhaust simulation turn (confirmed).
  Exposure position, handling multiple entities, and whether
  failed/immune damage consumes material remain undecided. Exhaust-induced damage
  interacting with the damage producer must be handled without recursive events.
- Slime: randomly underground in slime chunks. High material creates a slime.
- Ender Gas: slowly from nether portal blocks; Endermen, endermites, Ender Dragon, witches,
  shulkers, and ender chests;
  ender pearl use; standing in nether portals; soul torches, soul fires, soul sand.
  Ender pearl landing/impact also creates an Ender Gas burst at the impact
  location, once per pearl impact. Burst amount is not yet specified. This is
  recorded separately from the previously requested pearl-use emission.
  During full moons, each loaded-chunk producer check has an independent 1/256
  chance to create a large burst in one cell. This roll is per chunk check, not
  additionally gated by Vapor's 10% producer chance. The burst should overfill
  its initial cell enough to push outward into several cells under normal
  propagation rules. Exact amount and cell placement are not yet specified.
  Keep normal work budgets, loaded-chunk boundaries, and bedrock restrictions.

### Enderman Spawn Override

Natural Enderman spawning requires Ender Gas instead of Vapor. Endermen must
be excluded from the generic hostile Vapor gate, not required to satisfy both.
Interpret the matching fog rule as strictly greater than 50% Ender Gas capacity;
this numeric threshold is an implementation interpretation of "much like" the
Vapor rule. Unlike the surface-only generic gate, the requested "only spawn in"
purple rule applies to natural Enderman spawning underground too. No gas is
consumed; other normal spawn restrictions still apply. Commands, spawn eggs,
and scripted creation retain the existing scope exclusions. Deploy this gate
only with working Ender Gas production, not before purple atmosphere exists.

## Implementation Boundaries

Pressure destruction chooses between the source cell and a neighboring cell
probabilistically: chance of choosing a neighbor equals the source cell's empty
block fraction (empty blocks / total blocks). Fully solid means 0%, half empty
means 50%, and fully empty means 100%. This supersedes waiting until every
source-cell block is gone before targeting neighbors. Roll once per destruction
attempt; retain weakest-breakable-block selection within the selected search
scope. Behavior when that scope has no eligible block must be explicitly handled,
not repeatedly rerolled until a preferred outcome occurs. Preserve loaded-only
search, work limits, bedrock restrictions, and the requirement to exhaust valid
overflow propagation before destruction. Use each material's actual cell size.

Bedrock in a source cell blocks all downward material transfers from that cell.
Apply this to ordinary spreading, overflow propagation, and tiny-cell cleanup;
sideways and upward movement are unchanged. Check bedrock presence, not merely
air capacity, using each material's own cell bounds. Retain blocked material.

Preserve existing worlds and vapor data. Avoid allocating seven grids everywhere:
independent systems must remain sparse. Keep performance fixes separate from
feature changes, and do not turn on new producers in production prematurely.

Except for the Exhaust ranges above, exact production amounts, probabilities, transformation thresholds and costs,
spawn constraints, overlapping event behavior, and cross-material interactions
are not yet specified. Dust and Violence producer speed is also unspecified.
Do not silently treat those details as settled design decisions.
