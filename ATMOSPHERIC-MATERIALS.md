# Multiple Atmospheric Materials

This document describes the combined `0.16.1-alpha.1` atmospheric-material
runtime. All seven materials are active, independently stored, independently
rendered, sparse, chunk-persisted server systems. Numeric values are configurable
defaults, not permanent balance guarantees.

## Shared Runtime

All seven materials share a 200-tick simulation cadence and a 300-tick scheduled
producer cadence. Work queues remain bounded, so those values schedule work but
do not guarantee wall-clock completion under backlog. Entity and block event
producers remain event-driven. Producer and effect queries use loaded data only
and never force chunks to load.

| Material | Color | Base cell | Render reach and LOD | Optical density |
| --- | --- | --- | --- | --- |
| Vapor | Current fog color | 4x4x4 | 1x to V/2, 2x to V, 4x to 2V | 1x |
| Smoke | Black | 4x4x4 | 1x to V/2, 2x to V, 4x to 2V | 4x |
| Dust | Brown | 2x2x2 | Base cells to V/4 | 1x |
| Violence | Red | 8x8x8 | 1x to V, 2x to 2V | 4x |
| Exhaust | Yellow | 2x2x2 | Base cells to V/4 | 1x |
| Slime | Green | 16x16x16 | 1x to V, 2x to 2V | 4x |
| Ender Gas | Purple | 1x1x1 | Base cells to V/4 | 40x |

Optical density affects rendering only. It never multiplies stored amounts,
capacity, production, damage, or effect thresholds. Material identities never
combine with one another.

All rendered LOD levels retain slice spacing of
`baseCellSize / slicesPerBaseCell`. Aggregated 2x and 4x volumes use enough slices
to span their larger depth at that same spacing; distant volumes are not collapsed
to one slice. Recursive aggregation, material cutoffs, non-overlapping parent and
child coverage, and thickness-integrated density are otherwise unchanged.

Smoke previously used 8-block cells. On load, each legacy cell is split into its
eight aligned 4-block children. Integer remainders are distributed among those
children, preserving the exact total stored Smoke mass, and the chunk is marked
for saving in the new format. No other material or world state is reset.

## Terrain and Transport

Capacity is based on vacant cell volume and scaled to 0..1000. Air and liquid
blocks count as vacant space; a waterlogged host remains occupied. This lets
atmosphere occupy a liquid block's volume without treating a solid host as empty.

Any fluid amount, including flowing fluid or waterlogging, is a downward-transfer
barrier for its source cell. Bedrock is also a downward barrier. These barriers do
not block sideways or upward transfer. Ordinary equalization, overflow searches,
small-cell consolidation, and fan transport retain material when a destination is
unavailable. Unknown unloaded boundaries never authorize destruction or loss.

When Create is installed, each rotating Encased Fan found inside a processed
source cell requests movement one cell in its facing direction. The requested
amount is `floor(abs(RPM) * createFanTransportPerRpm)`, capped at the grid's
maximum amount; the default coefficient is 1.0. Transfers run after normal spreading.
Reverse RPM changes neither the
target direction nor the quantity formula. The actual transfer is limited by
remaining source material, destination spare capacity, loaded/readable terrain,
and the same liquid/bedrock edge rules.

## Producers and Effects

### Vapor

Vapor retains rain/cloud, high-terrain, dark-ground, water-removal, snow/ice, and
movement sources. Its scheduled producer gate defaults to 10%, and due simulation
checks retain the 50% skip chance. High fullness can condense into real water.
Eligible crops and saplings can receive normal bonemeal growth for a 10% roll and
40-unit successful-growth cost. Natural surface hostile spawning requires strictly
more than 50% Vapor, while qualifying underground terrain remains exempt.

### Smoke

Fire emits 40, lava 4, lit furnaces and campfires 20, and lit torches 2 per producer
check. Explosions emit 80 plus 10 per successfully destroyed block. Fire and lava
presence transitions emit without duplicating ordinary age, level, or fluid-flow
changes.

Each processed Smoke turn makes independent effect rolls. `0.16.1-alpha.1`
increases all four chances to 10x their earlier defaults, capped at 1:

- Leaf removal: 1.0, removing at most one leaf for 40 Smoke.
- Farmland conversion: 10/128, converting at most one farmland block for 40 Smoke.
- Villager conversion: 10/256, converting one eligible villager to a nitwit for 40 Smoke.
- Dissipation: 10/64, removing up to 40 Smoke.

Effects remain bounded to the processed loaded cell. Failed mutations consume no
material, and villager conversion preserves the existing entity.

### Dust

Walking, running, jumping, landing, fall damage, block breaking/placement, and
falling-block landings produce Dust. Water or snow/ice movement routes the movement
emission to Vapor instead. Processed Dust can turn plain water into mud, create
gravel when overfull, and independently dissipate. Waterlogged hosts are preserved.

### Violence

Hostile deaths, sampled Netherrack, and bounded world-bottom checks produce
Violence. The medium-density villager effect supplies an eligible existing
villager with breeding food while preserving the entity and vanilla breeding
requirements. High density can attempt one vanilla-checked zombie spawn with a
finite material cost.

### Exhaust

Living mobs, creepers, and non-recursive successful damage produce Exhaust.
Processed high-density cells can damage exposed living entities and consume a
bounded fraction of material. Exhaust also shares Vapor's bounded crop/sapling
growth policy while spending its own material.

### Slime

Loaded vanilla slime chunks can produce Slime below Y=40. High-density processed
cells can attempt one vanilla-checked slime spawn after bounded position checks and
a finite material debit.

### Ender Gas

Endermen, endermites, the Ender Dragon, witches, shulkers, Nether portals and
occupants, Ender chests, soul torches, soul fire, soul sand, Crying Obsidian, and
Ender pearl use/impact produce Ender Gas. Full-moon loaded-chunk checks retain an
independent 1/256 chance of an 8,000-unit burst. Natural Endermen require strictly
more than 50% local Ender Gas, including underground, without bypassing other
vanilla spawn rules or consuming gas.

## Configuration

Server gameplay, shared cadence, work budgets, and Create fan integration are in
`<world>/serverconfig/dynamicatmosphere-server.toml` (the world's `serverconfig`
directory in single-player or on a dedicated server). Client rendering and
allocation settings are in `<game-directory>/config/dynamicatmosphere-client.toml`.

The runtime consumes immutable server and client snapshots. NeoForge load/reload
events replace those snapshots, so exposed server values and client presentation
values hot-reload. Client `allocation.cellBudget` is marked restart-required and
takes effect after restarting the game. Structural cell sizes, persisted format,
and network protocol are deliberately absent from configuration; changing those
requires a compatible mod update, with both client and server updated together.

## Safety Boundaries

All scans, entity queries, spawn attempts, and overflow searches are bounded and
loaded-only. Gameplay effects cannot spend unavailable material. Pressure relief
requires confirmed blocked overflow and a fresh search; unknown boundaries and
exhausted budgets do not authorize terrain destruction. Back up worlds: this alpha
can place or remove blocks, alter villagers, damage entities, spawn mobs, and break
eligible terrain under pressure. Updating preserves existing world and material
state; downgrading does not undo world mutations.
