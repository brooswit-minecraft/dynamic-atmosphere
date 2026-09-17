# T5 — Fill-biased vertical movement

Repo: `brooswit-minecraft/dynamic-atmosphere`. Depends on: T1 (fill fraction
depends on the new per-cell capacity). Land last.

## Goal

A nearly empty cell drifts toward the cloudline; a nearly full cell sinks. The
switch is continuous, not a threshold.

## Rule

Let `f` be the cell's fill fraction, `amount / capacity`, clamped to `[0, 1]`.
The probability that a move goes toward the cloudline is a linear
interpolation from `P_up_max` at `f = 0` to `P_up_min` at `f = 1`:

```
p_cloudline = P_up_max + f * (P_up_min - P_up_max)
p_down      = 1 - p_cloudline
```

Pick the two endpoints, make them configurable, and justify them in the PR.

## Notes from code review

- There is no cloudline movement function today. The nearest existing concepts
  are the `RAIN_CLOUD_HEIGHT` server config value and the rain-cloud code in
  `ForgeAtmospherePrototype`. This is new behaviour, so the first job is
  deciding where it belongs in the simulation step and saying so in the PR.
- Define "toward the cloudline" explicitly for a cell already above it —
  moving toward the cloudline then means downward, and the two directions
  collapse. Say what you chose.
- The bias must compose with, not replace, existing spreading and fan
  transport. Fans run before distribution within a simulation step today.
- Decide and state which materials this applies to. The brief describes it as
  a property of atmosphere cells generally.

## Definition of done

- Movement direction probability is continuous in fill, with no threshold.
- A focused test asserts the interpolation at empty, half, and full, and that
  the two probabilities sum to one.
- A test covers a cell at or above the cloudline.
- Existing movement, spreading, and fan tests still pass.
- README and CHANGELOG updated.
