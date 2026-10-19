bump: patch

### Changed
- Nether Gas and Exhaust no longer run a living-entity spatial query (`getEntitiesOfClass`) for a cell whose amount/capacity make its effect ineligible; the query now runs only once eligibility is confirmed from the grid's own cached cell state, with no change to which entities are affected or how.
- Void Gas's villager-breeding check now bounds the underlying entity scan itself (`Level#getEntities` with a limit) instead of collecting every eligible villager in the cell before capping a loop that only ever consumes the first candidate.
- `AtmosphereGrid#spreadOneHop`'s per-tick scratch collections (snapshots, proposals, per-source neighborhood/capacity buffers) are now cleared-and-reused per-grid buffers instead of freshly allocated every call, and each source's canTransfer-filtered neighbor list is computed once per call instead of twice. No change to spread amounts, ordering, or any other simulation behavior.
