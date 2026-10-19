bump: patch

### Changed
- Per-chunk material persistence (Dust, Obsidian Powder, and every other atmospheric material) now batches all of a chunk's dirty materials into one coherent snapshot write per flush, instead of one write per dirty material. Stored data, readability tracking and failure handling per material are unchanged; same-coordinate chunk-incarnation swaps (unload/reload) still never merge two incarnations' data.
