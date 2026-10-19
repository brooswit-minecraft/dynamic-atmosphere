bump: patch

### Changed
- Vapor, Smoke and every atmospheric material grid now share one terrain-facts cache per chunk (empty-block count and downward-barrier presence) instead of each independently rescanning identical terrain. Each grid still derives its own capacity from those shared facts with its own formula; authoritative per-material amounts and readability remain independent. No user-visible behavior change.
