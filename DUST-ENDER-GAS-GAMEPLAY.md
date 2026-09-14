# Dust and Ender Gas Gameplay Defaults

These are conservative implementation defaults for the next release, not new
permanent design constraints. Producers are event-driven or called by the
existing bounded loaded-chunk producer schedule. They never scan a dimension or
load a chunk.

## Dust

- Walking emits 1 unit every 10 ticks of actual grounded movement.
- Running emits 3 units every 5 ticks of actual grounded movement.
- Walking or running in water, or on ice, snow, snow blocks, or powder snow,
  emits the same amount as Vapor instead of Dust. Routing is mutually exclusive.
- Jump transitions emit 8 Dust. Ordinary landing transitions emit 6 Dust.
- A landing that actually deals fall damage emits `16 + ceil(2 * damage)` Dust,
  capped at 64, and suppresses the ordinary landing burst for that tick.
- Breaking a block emits 16 Dust; placing a block emits 12 Dust.
- Each processed 2x2x2 Dust cell examines at most its eight blocks. Pure water
  can become mud with chance 0% at 50% fullness, linear to 100% at 100%.
  Success consumes half the then-current Dust, rounded up. Failed placement or
  debit restores the water and consumes nothing. Waterlogged hosts are ignored.
- Overfull Dust has a 1/16 chance per processed-cell check to place one gravel
  block in an air position. This initial gravel rule does not consume Dust.

## Ender Gas

- Endermen, endermites, Ender Dragons, witches, and shulkers emit 1 unit every
  40 ticks. Any entity actively occupying a Nether portal emits 2 every 20 ticks.
- A sampled Nether portal, Ender chest, soul torch/wall torch, soul fire, or soul
  sand emits 1 unit.
- A successfully spawned Ender pearl emits 24 units; its first impact emits 48.
- During a full-moon night, every bounded loaded-chunk producer check makes its
  own 1/256 roll. Success emits 8,000 units at one sampled surface position.

## Future Enderman Override

The purple spawn gate exists but must not be registered until Ender Gas storage,
production, and rendering are active. At activation, natural Endermen must be
removed from the generic Vapor hostile gate in the same change. They then require
strictly more than 50% Ender Gas at every natural spawn position, including
underground, without consuming gas or overriding any ordinary spawn rule.
