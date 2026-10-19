bump: patch

### Changed
- Create fan processing now scans each chunk's block entities once per pass and reuses the resulting fan requests (position, RPM-derived amount, direction, reverse state) across the Vapor, Smoke and all nine other material grids, instead of independently re-scanning the chunk once per material/fan cell. Transport behavior, coordinate ordering, remainder rotation, pressure overfill and material conservation are unchanged.
