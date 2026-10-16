bump: minor

### Added
- Nether Gas: an eighth atmospheric material, sourced from loaded Netherrack samples. Mild negative potion effects (Confusion/Nausea) apply at low concentration, with Poison added at high concentration; movement and dissipation reuse the existing generic heavy-gas rules shared with Void Gas, Obsidian Powder and Slime.
- Portal Transfer Sinks: a Nether portal block consumes Nether Gas locally and re-emits the same amount at the linked destination, using the vanilla 8:1 Nether/Overworld coordinate scale. An End portal block drains every generic-grid material near it into The End instead. A transfer never forces a chunk or dimension to load — an unreachable destination simply skips that pass, and the source material is only debited after a successful emit, so it is never destroyed by a skipped transfer.
- Dimension configuration: a small, dimension-keyed override (`dimensions.theEnd` in `dynamicatmosphere-server.toml`). The End now ignores the shared fill-biased vertical movement by default (`honorPreferredAltitude = false`) and multiplies its own generic-grid dissipation chance (`globalDissipationMultiplier`, default `100`), making an activated End portal a practical, emergent pollution-disposal mechanism rather than a special-cased feature.

### Changed
- Smoke, Vapor, and sources already active in the Nether (lava, fire, soul fire, Netherrack, Nether portal blocks, etc.) were already dimension-generic; this phase adds Nether Gas as the Nether's own named material rather than introducing new per-dimension simulation plumbing.
