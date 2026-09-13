# Dynamic Atmosphere

A small atmospheric mod in alpha for Minecraft **1.21.1 / NeoForge**.

The **atmospheric grid** divides space into 4x4x4-block cells. Water fog,
high-terrain clouds, rain landing on exposed surfaces, and dark exposed ground add material; it gradually decays in
place. Clients render translucent cells with opacity based on their material.
This is an incremental visual prototype, not a complete weather simulation.

## Current Scope

- Server-owned material amounts, synchronized to nearby clients.
- Bounded work near players, without forcing chunks to load.
- Bounded cell state and network updates; no movement between cells.
- Rain buildup respects shelter and biome precipitation, including roof and canopy landing surfaces.
- Low-light exposed ground builds overnight fog; daylight and nearby lighting reduce this source, and material decays locally.
- `/dynamicatmosphere status` and `/dynamicatmosphere demo` for operators.
- No world generation changes or world reset required.

It is **not yet** the planned terrain-aware cloud and fog simulation. Gas
transport, pollution, generating precipitation, and gameplay effects are future work.
The grid is a coarse visual representation, not terrain-clipped volumetric fog.

Install the same version on **both server and client**, or in a NeoForge 1.21.1
single-player instance. No extra graphics dependency is required. This requirement
starts with 0.3.0-alpha.1; older releases were particle-only.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
