bump: minor

### Changed
- Lava touching water no longer makes obsidian, cobblestone, or stone — it makes magma, and the water is consumed too.
- Lava beside or above ice, packed ice, or blue ice now degrades the ice one rung (to water, ice, or packed ice) and converts the lava's own position (to magma or obsidian), preserving the existing soul-soil basalt-generator precedence.

### Added
- Breaking a magma block, by any actor or mechanism (player, explosion, piston, mob, or an automated machine), now independently rolls a configurable `magma.breakLavaChance` chance (default `0.1`) to leave a lava source at the removed position.
