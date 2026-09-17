# Atmosphere Cells and Void Gas — project plan

Source brief: `idea.md` at the repository root (decisions section is settled).
Verified against `main` at `348ec52`.

## Outcome

One uniform `4x4x4` atmosphere cell for every material; smoke re-tuned to a
slightly brown `2x` vapor opacity; `violence` renamed to Void Gas keeping
smoke's old black darkness; Ender Gas, Slime, and Void Gas dissipating at half
smoke's rate; and vertical movement biased continuously by cell fill.

## Sequencing

```
T1 uniform 4x4x4 cells  ──┬─> T3 opacity        ──> T5 fill-biased movement
                          └─> T4 dissipation
T2 void gas rename ───────────> T3 (void gas appearance)
```

- **T1** lands first: cell size feeds capacity, which feeds both the opacity
  normalisation and the fill fraction the movement bias reads.
- **T2** can run in parallel with T1; they touch the same two material
  catalogs, so whichever lands second rebases.
- **T3** needs T1 (capacity) and T2 (the Void Gas identity to paint).
- **T4** needs T1 only.
- **T5** lands last; it is the only genuinely new behaviour.

## Tasks

| # | File | Title | Depends on |
|---|------|-------|-----------|
| T1 | `task-1-uniform-cells.md` | Uniform 4x4x4 atmosphere cells | — |
| T2 | `task-2-void-gas-rename.md` | Rename violence to Void Gas | — |
| T3 | `task-3-opacity.md` | Smoke 2x brown, Void Gas smoke-dark black | T1, T2 |
| T4 | `task-4-dissipation.md` | Half-rate dissipation for heavy gases | T1 |
| T5 | `task-5-fill-biased-movement.md` | Fill-biased vertical movement | T1 |

## Shared constraints for every task

- Saved atmosphere data and old `violence` config may be discarded. Do not
  write migration code.
- Client and server ship together; the material `StreamCodec` encodes ordinal,
  so wire compatibility across versions is not a goal.
- Both material catalogs must stay in step: `AtmosphereMaterial` (forge) and
  `AtmosphericMaterials` (engine).
- Each task updates `CHANGELOG.md` and `README.md` where they state the values
  it changes; the README material table lists cell sizes, colors, and density
  multipliers.
- Focused unit tests, in the existing style, are part of each task's
  definition of done. `./gradlew test` must pass.
