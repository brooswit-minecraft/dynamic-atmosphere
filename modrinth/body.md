# Dynamic Atmosphere

A small atmospheric mod in alpha for Minecraft **1.21.1 / NeoForge**.

**0.6.0-alpha.1 includes default-enabled destructive pressure. It can damage
terrain and player builds, with no claim/protected-area support. Back up your
world before upgrading; restoring that backup is required to undo terrain damage.**

The **atmospheric grid** divides space into 4x4x4-block cells. Water fog,
high-terrain clouds, rain landing on exposed surfaces, and dark exposed ground add
material. There is no natural decay: material spreads by equalizing fullness
across six face-adjacent cells. Sources are sampled every five seconds, with
simulation work budgeted across ticks. Clients render
translucent cells with opacity based on material amount divided by capacity.
This is an incremental alpha, not a complete weather simulation.

## Current Scope

- Server-owned material amounts, synchronized to nearby clients.
- Bounded work near players, without forcing chunks to load.
- Sparse per-Minecraft-chunk storage replaces the global 1,024-cell cap. Work and network updates are budgeted, not total stored cell count.
- Capacity is proportional to vacant air blocks (0..64): fewer air blocks need less material to fill a cell.
- Ordinary equalization transfers material without loss to positive-capacity face neighbors, balancing fullness rather than raw amounts. Zero-air cells block transfer; no diagonal transfer.
- Overfull cells push excess outward toward nearby available capacity through air-capacity neighbors. Unknown unloaded boundaries or exhausted budgets leave work pending: neither authorizes pressure destruction or discards material. Unlimited displacement is not guaranteed.
- Trapped excess triggers default-enabled pressure destruction: break the lowest-hardness eligible source-cell block with drops, then work outward once source blocks are gone. Each broken block adds 1 material unit.
- Pressure work is bounded per pass. Negative-hardness/intrinsically unbreakable blocks are exempt; closed unbreakable surroundings leave excess blocked, not deleted.
- Rain buildup respects shelter and biome precipitation, including roof and canopy landing surfaces.
- Low-light exposed ground builds fog; full daylight stops this source but does not clear existing material, which can continue spreading.
- Amounts save with their owning chunks and restore on reload/restart; capacities are recomputed. Unload releases the simulation mirror, not saved material. No range-based deletion; terrain damage also persists.
- Each cell stores up to 1,000,000 material units, with capacity 0..1000.
- `/dynamicatmosphere status` and `/dynamicatmosphere demo` for operators.
- No world generation changes or world reset required.

It is **not yet** the planned terrain-aware cloud and fog simulation. Gas
transport, pollution, and generating precipitation are future work.
The air-count rule is coarse, not an exact airtight-wall simulation: it does
not inspect every shared-face opening. The grid is a coarse visual representation,
not terrain-clipped volumetric fog.

Install the same version on **both server and client**, or in a NeoForge 1.21.1
single-player instance. No extra graphics dependency is required. This requirement
starts with 0.3.0-alpha.1; older releases were particle-only.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
