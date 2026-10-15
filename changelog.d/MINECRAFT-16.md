bump: minor

### Changed
- Ender Gas is now Obsidian Powder: the material id `ender_gas` becomes `obsidian_powder` and every `enderGas.*` config key becomes `obsidianPowder.*`. There is no migration — saved Ender Gas atmosphere is discarded and renamed config values reset to their defaults on upgrade. Client and server must be upgraded together; back up worlds before upgrading. Existing sources keep their behavior.

### Added
- `obsidianPowder.obsidianBlockEmission` (default `1`): plain obsidian blocks are now a passive Obsidian Powder source.
- `obsidianPowder.cryingObsidianEmission` (default `1000`): crying obsidian now has its own, much larger passive emission; `obsidianPowder.passiveBlockEmission` remains the general passive source for the other blocks.
