bump: minor

### Changed
- Vapor's rendered opacity now increases as the live fog-derived colour it's tinted with gets darker (night, dark biomes, weather), up to a bounded, configurable cap (`vaporDarknessOpacityBoost`, default 3x, range 1-6). Vapor's colour, density distribution, volume shape, LOD, clipping, and distance fade are all unchanged; only alpha is affected, and only for Vapor. Fixes Vapor reading as nearly invisible at night.
