# Dynamic Atmosphere

A small atmospheric **delivery spike** for Minecraft **1.21.1 / NeoForge**.

This first alpha adds lightweight cloud/fog particle effects near players and
operator commands to demonstrate that the mod is executing. It is deliberately
small so we can test the complete build, release, modpack, and server lifecycle.

## Current Scope

- Server-driven visual effects using Minecraft's existing particles.
- Bounded work near players, without forcing chunks to load.
- `/dynamicatmosphere status` and `/dynamicatmosphere demo` for operators.
- No world generation changes or world reset required.

It is **not yet** the planned terrain-aware cloud and fog simulation. Gas
transport, pollution, precipitation, and gameplay effects are future work.
Particle visibility depends on the client's particle settings.

Install on a NeoForge 1.21.1 server, or in a NeoForge single-player instance.
The client does not need a separate renderer or graphics dependency.

Source, issues, and primary release artifacts are hosted on
[GitHub](https://github.com/brooswit-minecraft/dynamic-atmosphere).
