bump: patch

### Changed
- Reduce server tick cost: atmospheric sync now resolves each player's tracked chunks once for all nine grids and builds the snapshot-only chunk scope lazily, dirty chunks are no longer flushed twice on non-sync ticks, and Exhaust cells query nearby entities only when they are full enough to suffocate. Behavior is unchanged.
