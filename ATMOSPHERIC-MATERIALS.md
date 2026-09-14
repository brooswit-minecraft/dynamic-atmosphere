# Multiple Atmospheric Materials

Status: approved for parallel development with performance work. Performance
fixes remain independently shippable; this document does not indicate deployment.

Each material has an independent cell grid, cell size, rendering LOD distances,
simulation schedule, producer schedule, and rules. Speed multipliers are relative
to the base schedule. LOD multipliers aggregate that material's base cells;
distances below are multiples of the player's view distance. The last band's
outer distance is a hard rendering cutoff, including cached/fallback visuals.
Keeping cached data does not authorize rendering it beyond that cutoff. These
rendering limits do not delete server material or change simulation coverage.

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
When the spawn position can see the sky, allow the ordinary spawn checks to
continue only if Vapor exceeds 50% of that cell's capacity (strictly greater;
exactly 50% does not qualify). Underground/covered spawning is unchanged. This
restriction never overrides other spawn checks to force a spawn. Smoke and other
materials do not satisfy it. Unknown or zero capacity cannot qualify.
This is a read-only density check: spawning never consumes or reduces Vapor.
Initial implementation scope is natural/chunk-generation hostile spawning;
commands, spawn eggs, spawners, and scripted creation are not implicitly changed.
Remove Peaceful Nights from the pack only alongside the working replacement.

### Material Production

- Vapor: existing rules, including water creation at high material levels and
  sampled snow/ice surfaces emitting 40 vapor units without consuming the block.
  Snow/ice uses the existing 15-second / 10% chunk gate and landing-source sample,
  with no extra scan: the ICE tag, SNOW, SNOW_BLOCK, and POWDER_SNOW qualify.
  The catalog explicitly names this existing runtime source as SNOW_ICE_SURFACE
  (runtime SourceKind SNOW_ICE); it must not be registered a second time when
  adapting EXISTING_VAPOR_RULES.
- Dust: small amounts from player and mob walking; block breaking and placement;
  player and mob fall damage. High material creates gravel blocks.
  If a Dust cell contains water, it can turn one water block into mud. Chance
  scales linearly from 0% at 50% fullness to 100% at 100% fullness; below 50%
  is 0%, above 100% stays 100%. A successful conversion consumes 50% of the
  cell's current Dust material; a failed conversion consumes none.
  Exact check cadence, selection among multiple water blocks, and waterlogged-block handling are
  not yet specified. Do not silently overwrite waterlogged host blocks.
- Smoke: lava, fire, explosions and blocks destroyed by explosions; furnaces,
  torches, and campfires.
  Explosions explicitly create a Smoke burst at the blast location, even when
  no blocks are destroyed. Emit once per explosion, separately from the
  per-destroyed-block source; burst amount remains to be specified.
- Violence: hostile mob deaths and Netherrack. High material creates a zombie.
  At 10% to 25% cell capacity, if a villager is present, consume 5% of the cell's
  current material to put a villager into the breeding state. This is a separate
  low-density effect, not a replacement for high-density zombie creation.
  The exact Minecraft breeding/willingness integration, eligibility, effect
  cadence, and selection when multiple villagers are present remain to be settled;
  do not promise a successful birth or silently bypass normal breeding conditions.
- Exhaust: all living mobs randomly, creepers frequently, and player/mob damage.
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
