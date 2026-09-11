# Dynamic Atmosphere

Dynamic Atmosphere is a NeoForge mod for Minecraft 1.21.1. It adds a coarse,
data driven 3D atmospheric simulation to the game: weather materials such as
fog, smoke, and cloud that move through the world under a small set of
generic rules, instead of scripted weather events.

**This project is early and not yet playable.** The current build is a shell
mod. It loads, proves that its simulation code is packaged correctly inside
the mod jar, and logs one line at startup. No weather or atmosphere
simulation runs yet.

## What it will do

Weather materials will move through connected terrain, driven by sources,
sinks, preferred altitudes, diffusion, and threshold events. Planned
materials include Cloud, Fog, Smoke, Zombie Fog, Slime Fog, and Nether Gas.
Examples of the intended behavior:

- Clouds forming predictably around mountains and precipitating.
- Fog gathering over deep water, then pouring into valleys and cave
  entrances.
- Smoke accumulating in enclosed lava caves, and venting from factories and
  forest fires.
- Zombie deaths producing Zombie Fog that changes nearby zombie spawning.
- Nether gas escaping through Nether portals into the Overworld.

The goal is for most new atmospheric content to be addable through data
rather than new code, so the simulation engine stays simple while the
possible material behaviors stay varied. Minecraft defines the environment.
Weather materials define how they react to it.

## Planned first playable milestone

The first playable milestone is limited to Cloud and Fog only, with
rendering just good enough to observe and debug the simulation. No release
date is set for this or any later milestone.

## Source

Source code, issue tracker, and licence are linked on this page.
