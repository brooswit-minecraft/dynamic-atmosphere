bump: minor

### Changed
- Lava touching water no longer makes obsidian, cobblestone, or stone — it makes magma, and the water is consumed too.
- Obsidian isn't removed from the game by this — it has moved sideways: lava against packed ice or blue ice now pays out obsidian there instead.
- Lava beside or above ice, packed ice, or blue ice now degrades the ice one rung (to water, ice, or packed ice) and converts the lava's own position (to magma or obsidian), preserving the existing soul-soil basalt-generator precedence (blue ice still becomes basalt, not obsidian, when lava has soul soil beneath it).
- Five of these conversions emit DA's existing lava-removal smoke (water-on-source, water-on-flowing-lava, and all three ice rungs); lava spreading down into water does not, since no lava fluid is actually removed in that case.

### Added
- Breaking a magma block, by any actor or mechanism (player, explosion, piston, mob, or an automated machine), now independently rolls a configurable `magma.breakLavaChance` chance (default `0.1`) to leave a lava source at the removed position.
