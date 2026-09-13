# Dynamic Atmosphere

A small atmospheric mod in alpha for Minecraft **1.21.1 / NeoForge**.

Fog patches build up over water near players, with deeper water producing denser
fog. Lightweight high-terrain cloud effects and operator demonstration commands
are also included. This is an incremental visual prototype, not a complete
weather simulation.

## Current Scope

- Server-driven visual effects using Minecraft's existing particles.
- Bounded work near players, without forcing chunks to load.
- Persistent water-fog patches with bounded state and particle output.
- `/dynamicatmosphere status` and `/dynamicatmosphere demo` for operators.
- No world generation changes or world reset required.

It is **not yet** the planned terrain-aware cloud and fog simulation. Gas
transport, pollution, precipitation, and gameplay effects are future work.
Particle visibility depends on the client's particle settings.

Install on a NeoForge 1.21.1 server, or in a NeoForge single-player instance.
The client does not need a separate renderer or graphics dependency.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
