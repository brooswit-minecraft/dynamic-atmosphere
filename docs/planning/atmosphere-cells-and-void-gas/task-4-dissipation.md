# T4 — Half-rate dissipation for Ender Gas, Slime, and Void Gas

Repo: `brooswit-minecraft/dynamic-atmosphere`. Depends on: T1.

## Goal

Ender Gas, Slime, and Void Gas dissipate gradually the way smoke does, at half
smoke's rate, so they persist as lingering clouds.

("Nether gas" in the original brief means Ender Gas. There is no separate
nether gas material.)

## Current state

`SmokeDissipation.amount(remaining, chance, amount, roll)` drives smoke's fade
from a configured chance and amount; `DustDissipation` is the parallel
implementation for dust. Nothing currently fades Ender Gas, Slime, or
violence/Void Gas.

## Scope

- Give the three gases smoke-shaped dissipation at half smoke's rate. Prefer
  generalising the existing dissipation function over a third copy of it —
  `SmokeDissipation` and `DustDissipation` are already near-duplicates, so a
  shared implementation parameterised by material is the cleaner landing.
- "Half smoke's rate" means half the expected amount removed per dissipation
  tick. State in the PR whether you halved the chance or the amount and why;
  the halved quantity must be the one that actually halves the expected fade.
- Expose the rates through config alongside smoke's existing entries, with the
  three gases defaulting to half.
- Leave dust alone.

## Definition of done

- The three gases fade on their own and reach zero, at half smoke's expected
  rate, verified numerically in a test rather than by eye.
- No duplicated third dissipation implementation.
- Tests updated and passing; README and CHANGELOG updated.
