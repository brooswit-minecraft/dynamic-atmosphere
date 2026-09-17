# T3 — Smoke at 2x brown, Void Gas at smoke's old black

Repo: `brooswit-minecraft/dynamic-atmosphere`. Depends on: T1 (capacity), T2
(the Void Gas identity).

## Goal

Vapor stays the opacity baseline. Smoke reads as slightly brown and exactly
`2x` vapor opacity. Void Gas reads black at the darkness smoke has *today*.

## Current state

The README states that smoke, violence, and slime multiply optical density by
four before rendering. So smoke's opacity drops from `4x` to `2x`, and Void
Gas inherits the `4x` black that smoke is giving up.

## Scope

- Find the density multiplier and color handling on the client side —
  `AtmosphereRenderMaterial` and `AtmosphereVolumeGeometry` under
  `forge/.../client`, plus the `MaterialDefinition.Color` values in the engine
  catalog (smoke is `BLACK`, violence is `RED`).
- Smoke: multiplier `2x` relative to vapor, with a slight brown tint. Pick the
  tint to read as brown-tinged smoke rather than dirt; state the chosen value
  in the PR.
- Void Gas: black, `4x` — visually indistinguishable from pre-change smoke.
- Leave slime's multiplier alone unless T1 forced it to change.
- Check that `sliceOpacityNormalizesToIndependent…` in `SmokeClientTest` and
  `opacityIsMonotonicAndC…` in `AtmosphereVolumeGeometryTest` still express the
  intended invariants at the new values.

## Definition of done

- Smoke is `2x` vapor and brown-tinted; Void Gas is `4x` and black.
- Opacity stays monotonic in amount and correct across LOD slices.
- Tests updated and passing; README density table and CHANGELOG updated.
- PR includes a screenshot or a short description of how it reads in game.
