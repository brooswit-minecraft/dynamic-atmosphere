# MINECRAFT-484 frozen atmosphere batch

Scope: sections 1–7 of MINECRAFT-484 comment 49460. Section 8 lives in dynamic-vehicles.
No parked stories, caster/roll-center changes, version bump, release, deployment or live-world work.

## Implementation defaults (reviewable tuning, not user-specified numbers)

- Vapor: multiply the live fog RGB triplet uniformly by min(1.08, 1/maxChannel).
  Black remains black; white stays bounded. Hue ratios are preserved. The renderer samples
  `RenderSystem.getShaderFogColor()` once per frame, before its own render-state changes:
  this is Minecraft FogRenderer's current biome/day/weather/dimension fog, not a white constant.
  Existing raw-fog darkness opacity, celestial tint, optical density and distance fade remain.
- Simulation: remove `runtime.simulationSkipChance` from schema and runtime snapshot. Old
  `runtime.simulationSkipChance`/`vapor.skipChance` values cannot affect execution. Every eligible
  due source reaches the deterministic schedule; the existing 128-source spread and 512-visit
  overflow limits remain. Removing .75 skips increases useful work; no wall-clock speedup is claimed.
- Flora: within the existing bounded Vapor producer queue (default 32 loaded chunks/tick,
  300-tick cycle), `flora.samplesPerChunkCycle=32` random positions per chunk across its entire
  build height. Duplicate positions are not retried or emitted twice. No surface-only gate,
  chunk loads, world scans, or extra material producer queue. Sampling occurs independently of
  the old surface Vapor probability. A sampled cave-flora block emits 2 Vapor (configurable).
  Eligible blocks: moss block/carpet, azalea/flowering azalea, spore blossom, glow lichen,
  hanging roots, cave vines/plant, small dripleaf, big dripleaf/stem. No custom tags required.
- Mold: append a new material ordinal; keep all old IDs/ordinals. Shared grid, persistence,
  payload, sync, cache and rendering loops include Mold. Atmosphere protocol 15 rejects old peers.
  Default RGB `0x706061` approximates Mycelium, optical density 1, Void-Gas-style LOD, bottom home.
  Sampled mycelium, red/brown mushrooms, red/brown mushroom blocks and mushroom stems emit 2 Mold.
  Living witches emit 2 per 100 ticks, staggered by entity ID, through existing entity tick events.
  Emission still requires readable loaded material cells and available capacity.
- Mold transformations: one random block candidate per processed cell; 2% chance. A valid small
  red/brown mushroom costs 16, with air and vanilla survival checks. Small placement first
  requires the target and the surrounding 3x3 chunk footprint to be loaded, matching
  condensation; missing neighbors defer before survival/notifications, with zero debit. At >=75% concentration,
  ordinary red/brown mushrooms use vanilla corresponding giant feature placement, costing 128
  only if placement succeeds. Require a conservative ±16-block loaded footprint first.
  Ordinary cows check at the same staggered 100-tick cadence and 2% probability, require >=75%
  concentration and 256 Mold, and consume only after a mooshroom exists in the server level.
  Costs, probability, high concentration, emission and entity cadence are server config keys.
  Insufficient funds never invoke the action; failure never debits; each attempt charges once.
- Desert Dust: one surface candidate with 1% chance per scheduled loaded chunk cycle, configurable
  as `flora.desertDustChance`; emit 1 only in desert biome on exposed sand/dirt with air above.
  Walking retains the existing broad dust behavior. A just-below-feet probe resolves partial
  blocks; zero-collision single-layer snow is checked at the feet first. Snow/ice walking emits
  Vapor. Existing movement threshold/cadence and jump, landing, fall, break/place emissions remain.
- Existing Nether Fog: TOP = maxBuildHeight - 1, RGB `0x804040` in both live config and renderer.
  Nether Gas is unchanged. Lava harvesting, production, smoke behavior and condensation untouched.
  Existing explicit client color overrides remain overrides; remove/reset `netherFogColor` to
  adopt the new default in an existing config.

## Acceptance evidence and gaps

Tests cover RGB hue/bounds, config skip-key removal, flora selection/classification, bounded
sampling/no duplicates, TOP versus bottom homes, mushroom payment success/failure/insufficient funds, material catalog
parity, and shared persistence/scheduling. Existing deterministic grid, overflow-budget,
loaded-chunk and movement tests remain in the full suite. In particular,
`AtmosphereGridTest.beforeSpreadCallbackSharesTheOneHundredTwentyEightSourceBudget`
proves 130 due sources run as 128 then 2, exactly once, without random skips.

Local Gradle cannot start: `services.gradle.org` DNS resolution fails before compilation.
CI and exact-head results are recorded in the PR; this document does not assert a green build.
No live world was changed. Isolated CI runtime coverage is described below; interactive
manual acceptance remains outstanding.

Manual checks still required on a disposable authorized test instance:
1. Compare Vapor to the horizon at day, night, rain, across biome/dimension transitions; check
   near-black/white scenes and unchanged opacity/distance fade.
2. Observe due-cell work and backlog under load after removing skips; compare tick timing.
3. Observe underground flora emission with no sky access and chunk unload/reload safety.
4. Verify Mold's color, client sync, save/reload and source emissions including witches.
5. Test valid/invalid mushroom placement, red/brown giant success/failure, insufficient Mold,
   cow conversion and exact successful cost; no negative balance or repeat charge.
6. Compare rare desert surface dust with cave absence; walk/run/stand on full and layered
   sand/dirt/snow/ice, then check unrelated jump/landing/break emissions.
7. Observe red Nether Fog rising toward TOP and unchanged lava/condensation/Smoke/Nether Gas.

## Review correction: unloaded small-mushroom edges

`MoldPlacementIntegrationTest` calls the production `processCell` turn with deterministic RNG,
real block states and vanilla mushroom survival, using a mocked ServerLevel I/O boundary. It
covers positive/negative X and Z chunk edges: an unavailable neighbor permits no survival read,
placement or returned debit; marking the footprint loaded permits exactly one placement and
16-unit debit, followed by zero for the same existing mushroom. It asserts no chunk retrieval.
This is a production-path regression with mocked world I/O, not an actual ticking-world test.

Mold now has explicit attachment write -> NBT -> new ProtoChunk -> read coverage alongside Dust,
and an actual byte-buffer payload encode/decode -> Mold client session -> amount/palette/reset
round trip. Actual region-file disk I/O, live client networking and in-world conversion still
require manual/runtime acceptance. No new production rate, material or feature scope is added.


## Disposable ticking-world acceptance

`python3 scripts/world_acceptance.py` refuses an existing run directory and starts
an opt-in test-only server on localhost, with a new flat world. Test classes are
excluded from normal builds. The fixture explicitly loads its setup chunks; the
production actions are then checked for no additional loaded chunks.

The fixture invokes the same production Mold placement/debit and cow-conversion
boundaries used by simulation/entity ticks, with deterministic random draws in a
real ticking ServerLevel. It checks successful and invalid small placement,
insufficient funds, blocked and successful vanilla red/brown giants, real cow to
mooshroom conversion, repeat-charge prevention, and unloaded-edge deferral followed
by loaded-edge success. It does not substitute mocked world I/O. Deterministic
invocation establishes action/debit behavior, not the statistical natural cadence.

An actual underground moss source and exposed desert sand source exercise the
production bounded flora sampler: 32 candidate attempts (98 total integer draws
including surface coordinates), duplicate suppression, Vapor emission without sky,
surface Dust without cave Dust, and unchanged loaded-chunk count. Its recorded
single-call nanoseconds are diagnostic only, not a tick-time performance benchmark.
The existing engine source-budget tests remain the scheduling bound evidence.
`world-acceptance-result.json` and CI logs are the pass/failure receipts; the PR
records which exact head passed. Region-file restart persistence, client rendering,
probability distribution and sustained server workload measurements remain gaps.
