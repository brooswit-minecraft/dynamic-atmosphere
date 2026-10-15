bump: minor

### Changed

- Uniform LOD max distance + horizontal-only fade, cylindrical cull (ATMO-28 C3): every material's LOD/render distance is now controlled by ONE shared client config value instead of each material's own multiplier, the old flat spherical cutoff (which included the Y axis) is replaced by a continuous fade, and the cull itself is now cylindrical (bounded horizontally, unbounded vertically within the world build range) rather than spherical — a cell far above or below the camera but at ~0 horizontal distance is no longer culled or coarsened by height. **The cylindrical-cull approach is an inferred resolution** (epic ATMO-22's research inferred it from the stated "see stuff far above and below" goal; Brooswit has not confirmed it and may override it).

  Removed (client config, `[materials]` section of `dynamicatmosphere-client.toml`):

  | Key | Old default | Old range |
  | --- | --- | --- |
  | `vaporReachMultiplier` | 2 | 0.25-4 |
  | `smokeReachMultiplier` | 2 | 0.25-4 |
  | `dustReachMultiplier` | 0.25 | 0.25-4 |
  | `voidGasReachMultiplier` | 2 | 0.25-4 |
  | `exhaustReachMultiplier` | 0.25 | 0.25-4 |
  | `slimeReachMultiplier` | 2 | 0.25-4 |
  | `enderGasReachMultiplier` | 0.25 | 0.25-4 |

  Added (client config, `[rendering]` section):

  | Key | Default | Range |
  | --- | --- | --- |
  | `maxDistanceMultiplier` | 2.0 | 0.25-4 |
  | `fadeStartFraction` | 0.75 | 0-1 |

  `maxDistanceMultiplier` defaults to 2.0 because four of the seven removed keys (Vapor, Smoke, Void Gas, Slime) already defaulted to 2 — this keeps their render distance unchanged. Dust, Exhaust and Ender Gas move from 0.25 to 2.0 and now render 8x farther by default; all three keep `rootLevel = 0` (base-cell-only, never coarsened), so this is a real cell-budget increase for those three materials at range, not just a visual change — worth watching if it shows up in `/dynamicatmosphere status` cell counts. LOD detail-level thresholds (which distance band gets 4/8/16-block volumes) also became horizontal-only, using the same distance metric as the cull, rather than staying 3D — chosen for a single consistent distance metric throughout the selection algorithm rather than two.

  Conflicts with the shipped `0.20.x` behavior: `0.20.0-alpha.1` documented reach as `viewBlocks * reachMultiplier`, a per-material 3D-cull value; that entire model is gone.

- Void Gas + Slime amplification (ATMO-24 C1): the `voidGas.bottomChanceDenominator` and `slime.undergroundChanceDenominator` production gates now fire on every scheduled check instead of 1-in-8, Void Gas and Slime "amount produced" emissions are ×8, and `heavyGas.dissipationFactor` rises from 3 (introduced by commit 83eb877, shipped in `0.20.0-alpha.1`) to 12 — so Void Gas, Ender Gas and Slime now dissipate at a twelfth of Smoke's rate instead of a third. The factor is shared by all three materials, so Ender Gas dissipation slowing is an intended side effect of this change, not a separate one. This is pure numeric tuning: no gameplay/spawn-path code changed. Saturation is an intended design feature (ATMO-22) — Void Gas and Slime cells in loaded caves are expected to trend toward permanently full and billow upward, not be capped or damped.

  | Key | Old | New |
  | --- | --- | --- |
  | `voidGas.bottomChanceDenominator` | 8 | 1 |
  | `voidGas.bottomEmission` | 8 | 64 |
  | `voidGas.hostileDeathEmission` | 40 | 320 |
  | `voidGas.netherrackEmission` | 2 | 16 |
  | `slime.undergroundChanceDenominator` | 8 | 1 |
  | `slime.undergroundEmission` | 8 | 64 |
  | `heavyGas.dissipationFactor` | 3 | 12 |

  All seven keys stay in the same file and section they already lived in (server config, `[voidGas]`, `[slime]`, and `[heavyGas]` respectively) — only the defaults changed, not their location.
