# Upgrade key/file map (MINECRAFT-21)

Durable, cross-feature record of every config key/file change contributed by the
ATMO-22 epic's C1/C2/C3 tuning passes plus MINECRAFT-17, MINECRAFT-18 and this
ticket's own material-configuration slice (transferred from MINECRAFT-20). This
is the single reference dependent config-sync tooling should read for literal
key/path mappings — it does not replace the per-PR `changelog.d/` fragments,
which remain the source for prose explaining *why* each change happened; this
document is the flattened *what changed, where* index across all of them.

**Scope.** Only Jira-tracked, already-merged (verified against current `main`,
not inferred from ticket status) config/key changes are listed. "Merged
commit evidence" below cites the actual merge commit on `main` at the time
this document was written; re-verify against your own checkout before relying
on it. MINECRAFT-20 is excluded as a source: it closed **Won't Do without
implementation** — the material-configuration slice attributed to it below
was never implemented there and is first implemented by this ticket
(MINECRAFT-21), not inherited from a merged MINECRAFT-20 PR.

**Files.** Two config files throughout, no path changes in any of these
tickets:
- Server: `config/dynamicatmosphere-server.toml` (global default) /
  `<world>/serverconfig/dynamicatmosphere-server.toml` (per-world override).
- Client: `config/dynamicatmosphere-client.toml` (game directory).

## C2 — Ender Gas → Obsidian Powder rename (MINECRAFT-16 / ATMO-25)

Merged commit evidence: PR47, `27990bd14bb5ccdcc2c02cdcc78edc1aa848e19a`
(verified an ancestor of current `main`).

| Change | Old | New | File/section |
| --- | --- | --- | --- |
| Material id | `ender_gas` | `obsidian_powder` | server/engine/client catalogs |
| Config section | `[enderGas]` | `[obsidianPowder]` | `dynamicatmosphere-server.toml` |
| `enderGas.portalBlockEmission` | — | `obsidianPowder.portalBlockEmission` | server, default `100`, unchanged value |
| Added | — | `obsidianPowder.obsidianBlockEmission` (default `1`) | server, new passive source |
| Added | — | `obsidianPowder.cryingObsidianEmission` (default `1000`) | server, new passive source |
| `enderGasReachMultiplier` | removed (superseded by C3 below) | — | client |
| `enderGasOpticalDensity` | `enderGasOpticalDensity` | `obsidianPowderOpticalDensity` | client, default unchanged at this step (`40`; MINECRAFT-21 later changes the default itself, see below) |

No migration: renamed keys reset to their defaults on upgrade; there is no
value carry-over from the old key name to the new one.

## C1 — Void Gas + Slime amplification (MINECRAFT-19 / ATMO-24)

Merged, included in the MINECRAFT-19 changelog fragment (pending release
collation; verify current `main` rather than inferring from fragment presence
alone).

| Key | Old | New | File/section |
| --- | --- | --- | --- |
| `voidGas.bottomChanceDenominator` | `8` | `1` | server, `[voidGas]` |
| `voidGas.bottomEmission` | `8` | `64` | server, `[voidGas]` |
| `voidGas.hostileDeathEmission` | `40` | `320` | server, `[voidGas]` |
| `voidGas.netherrackEmission` | `2` | `16` | server, `[voidGas]` |
| `slime.undergroundChanceDenominator` | `8` | `1` | server, `[slime]` |
| `slime.undergroundEmission` | `8` | `64` | server, `[slime]` |
| `heavyGas.dissipationFactor` | `3` | `12` | server, `[heavyGas]` (shared by Void Gas, Obsidian Powder, and Slime) |

No keys added, changed location, or removed in this pass.

## C3 — Uniform LOD/render distance (MINECRAFT-19 / ATMO-28)

Same merged fragment as C1 above.

Removed (client, `[materials]` section), no replacement at the per-material level:

| Key | Old default | Old range |
| --- | --- | --- |
| `vaporReachMultiplier` | `2` | `0.25`-`4` |
| `smokeReachMultiplier` | `2` | `0.25`-`4` |
| `dustReachMultiplier` | `0.25` | `0.25`-`4` |
| `voidGasReachMultiplier` | `2` | `0.25`-`4` |
| `exhaustReachMultiplier` | `0.25` | `0.25`-`4` |
| `slimeReachMultiplier` | `2` | `0.25`-`4` |
| `enderGasReachMultiplier` (née `obsidianPowderReachMultiplier` after C2) | `0.25` | `0.25`-`4` |

Added (client, `[rendering]` section), replacing all seven above with one shared value:

| Key | Default | Range |
| --- | --- | --- |
| `rendering.maxDistanceMultiplier` | `2.0` | `0.25`-`4` |
| `rendering.fadeStartFraction` | `0.75` | `0`-`1` |

## MINECRAFT-17 — Void Gas spawn removal / surface-spawn eligibility

Merged (PR60 on current `main`; verify ancestry against your own checkout).

Removed (server, `[voidGas]` section), no replacement:

| Key | Old default | Old range |
| --- | --- | --- |
| `voidGas.spawnChanceDenominator` | `32` | `1`-`1,000,000` |
| `voidGas.spawnAttempts` | `8` | `1`-`1024` |

No new config keys. Behavior change only: a Void Gas cell more than half full
now satisfies `VaporHostileSpawnGate` (Overworld-only, no config flag) instead
of spawning anything itself.

## MINECRAFT-18 — Slime spawn-on-dissipation

Merged (PR61 on current `main`; verify ancestry against your own checkout).

Removed (server, `[slime]` section), no replacement:

| Key | Old default | Old range |
| --- | --- | --- |
| `slime.spawnChanceDenominator` | `32` | `1`-`1,000,000` |
| `slime.spawnAttempts` | `8` | `1`-`1024` |

Added (server, same `[slime]` section), `RestartType.NONE`:

| Key | Default | Range |
| --- | --- | --- |
| `slime.dissipationSpawnChanceDenominator` | `32` | `1`-`1,000,000` |
| `slime.dissipationSpawnAttempts` | `8` | `1`-`1024` |
| `slime.mediumDensityThreshold` | `0.25` | `0`-`1` |
| `slime.bigDensityThreshold` | `0.75` | `0`-`1` |

## MINECRAFT-21 — this ticket's own slice

### Config-version invalidation (new mechanism)

| Key | Default | File | Managed by |
| --- | --- | --- | --- |
| `configVersion` | `1` | `dynamicatmosphere-server.toml` (top level, independent revision counter) | `ConfigVersionGuard`, NOT `ModConfigSpec` |
| `configVersion` | `1` | `dynamicatmosphere-client.toml` (top level, independent revision counter) | `ConfigVersionGuard`, NOT `ModConfigSpec` |

`configVersion` is deliberately NOT a regular `ModConfigSpec`-registered
option. PR review on this ticket caught that registering it normally would be
actively wrong: NeoForge's own config loader fills in any *missing* spec-
defined key with its default and writes that back to disk before this mod
ever reads the file — which would silently stamp a genuinely stale,
pre-MINECRAFT-21 file as already-current on the very load that should have
reset it. Keeping it as a raw key the mod reads/writes itself sidesteps that
entirely. See `ConfigVersionGuard`'s class javadoc (forge module) for the
full mechanics.

On a mismatch (including a pre-MINECRAFT-21 file with no `configVersion` key,
treated as `0`), the mod renames the old file to `<name>.toml.bak-<oldVersion>`
(collision-safe: never overwrites an existing backup) and writes a fresh file
containing just the current marker in its place, so the config loader
regenerates every other key from current defaults around it. **This resets
that file's configuration; it does not merge old custom settings into the new
defaults.** Checked against the global `config/` copy at mod load, and — for
the server file specifically, since `runtime.simulationIntervalTicks` and the
rest of that file stay per-world — against a dedicated server's actual
per-world `<world>/serverconfig/` override, on every server start (see
`DynamicAtmosphereMod.onServerAboutToStart`).

### Material colours become client config (new keys, all `[materials]`, `RestartType.NONE`, range `0x000000`-`0xFFFFFF`)

| Key | Default | Changed from (enum literal) |
| --- | --- | --- |
| `materials.vaporColor` | `0x000000` | n/a — defined for parity only; the renderer never reads this key for Vapor, which always uses the live fog colour |
| `materials.smokeColor` | `0x0D0A08` | `0x1F160F` |
| `materials.dustColor` | `0x8B4513` | unchanged |
| `materials.voidGasColor` | `0x000000` | unchanged |
| `materials.exhaustColor` | `0xFFFF00` | unchanged |
| `materials.slimeColor` | `0x00FF00` | unchanged |
| `materials.obsidianPowderColor` | `0x1A1025` | `0x800080` — **PROPOSED** (ATMO-22 comment 23234), pending in-game visual confirmation against Smoke and Void Gas; not yet a locked value |

### Optical density defaults changed against their previously shipped values

| Key | Previously shipped default | New default | File/section |
| --- | --- | --- | --- |
| `vaporOpticalDensity` | `1` | `1.2` | client, `[materials]` |
| `smokeOpticalDensity` | `2` (set by 0.20.0-alpha.1, itself a change from `4`) | `4` | client, `[materials]` |
| `obsidianPowderOpticalDensity` | `40` (renamed from `enderGasOpticalDensity` by C2 above; value unchanged at that step) | `0.3` | client, `[materials]` |

`voidGasOpticalDensity` (`4`), `dustOpticalDensity` (`1`), `exhaustOpticalDensity`
(`1`), and `slimeOpticalDensity` (`4`) are unchanged by this ticket.

### Explicitly unchanged, called out per the ticket's own request

- `integrations.createFanIntervalTicks` (server, default `100`): unchanged by
  any ticket in this map.
- `runtime.simulationIntervalTicks` (server): stays per-world/SERVER-type at
  `<world>/serverconfig/dynamicatmosphere-server.toml`, as before.
- `maxProducerChunksPerTick`, `maxChunkImportsPerTick`,
  `maxSmokeProducerChunksPerTick`, and the client `allocation.cellBudget`:
  locations remain whatever they are in the current checkout; no further user
  decision moves them, and this ticket does not move them.

### Outstanding acceptance item (not closed by this document)

Per MINECRAFT-21's acceptance criterion, Smoke / Void Gas / Obsidian Powder
must be confirmed BY EYE in-game as visually distinct at representative
densities before this slice is complete. That check requires a live Minecraft
client and was **not performed** in the session that authored this document —
see the linked PR body for the explicit disclosure. Obsidian Powder's
`0x1A1025` colour remains a proposal, not a locked value, until that check
happens.
