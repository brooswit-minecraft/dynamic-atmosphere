bump: patch

### Added
- MINECRAFT-384: regression tests proving atmosphere physics (grid transport, cadence scheduling, decay, and condensation/reaction triggers) advance strictly by simulated tick count and each grid's own configured tick-rate, never by wall-clock time, server lag, or pause/resume gaps. Coverage drives the real `AtmosphereGrid#spread` before/after-spread hooks (the same seam `ForgeAtmospherePrototype.advanceSimulation` uses), compares full cell state (amount, capacity, lastEmissionTick, lastUpdateTick), and exercises pre-due vs. due ticks, equal-tick-count runs under multiple configured cadences, and resume-after-pause behavior. Audit found no production timing defect; no behavior change.
