bump: minor

### Added

- `configVersion` (both `dynamicatmosphere-server.toml` and `dynamicatmosphere-client.toml`, top level, default `1`): an internal config-format revision. Deliberately NOT a regular settable `ModConfigSpec` option — it's a raw key the mod manages itself, so NeoForge's own "fill in missing defaults" correction can never invent/overwrite it before the mod can compare it. A mismatch between the stored value and the mod's current revision for that file (a pre-MINECRAFT-21 file with no `configVersion` key at all counts as `0`) preserves the old file as `<name>.toml.bak-<oldVersion>` (collision-safe: never overwrites an existing backup) and writes a fresh file carrying just the current marker in its place, so the config loader regenerates every other key from current defaults around it. Checked against the global `config/` copy at mod load, and — for the server file — against a dedicated server's actual per-world `<world>/serverconfig/` override on every server start. **This resets that file's configuration; it does not merge old custom settings into the new defaults.**
- All seven materials' colours are now client config values (`client.materials.<material>Color`, packed `0xRRGGBB`), not hard-coded `AtmosphereRenderMaterial` enum literals — a colour used to require a full mod release to retune. See the table below for the key names and defaults.

### Changed

- Smoke: `smokeOpticalDensity` `2` → `4`; colour (new `smokeColor` key) `0x1F160F` → `0x0D0A08`. Deliberately NOT pure black — off-black specifically so Smoke stays visually distinct from Void Gas, the other material at `4x` optical density.
- Obsidian Powder: `obsidianPowderOpticalDensity` `40` → `0.3`; colour (new `obsidianPowderColor` key) `0x800080` → `0x1A1025`. The new colour is a PROPOSAL (ATMO-22 comment 23234), not a locked value — it is pending in-game visual confirmation against Smoke and Void Gas at representative densities before it can be called final.
- Vapor: `vaporOpticalDensity` `1` → `1.2`. Colour is unchanged; the new `vaporColor` config key exists for catalog/config parity only — the renderer always uses the live fog colour for Vapor and never reads this key.
- Void Gas, Dust, Exhaust, Slime: unchanged in both colour and optical density. `voidGasColor` (`0x000000`), `dustColor` (`0x8B4513`), `exhaustColor` (`0xFFFF00`), and `slimeColor` (`0x00FF00`) are new config keys exposing their existing catalog colours; no behavior change.

Added client config keys (`[materials]` section of `dynamicatmosphere-client.toml`), all `RestartType.NONE`, range `0x000000`-`0xFFFFFF`:

| Key | Default | Changed from |
| --- | --- | --- |
| `materials.vaporColor` | `0x000000` | n/a (unused by the renderer; see above) |
| `materials.smokeColor` | `0x0D0A08` | `0x1F160F` |
| `materials.dustColor` | `0x8B4513` | unchanged |
| `materials.voidGasColor` | `0x000000` | unchanged |
| `materials.exhaustColor` | `0xFFFF00` | unchanged |
| `materials.slimeColor` | `0x00FF00` | unchanged |
| `materials.obsidianPowderColor` | `0x1A1025` (proposed) | `0x800080` |

No keys were removed. This slice was transferred verbatim from MINECRAFT-20 (closed Won't Do without implementation) into this ticket's own scope; it is not a duplicate of any merged work.

### Acceptance note

Per MINECRAFT-21's acceptance criterion, Smoke/Void Gas/Obsidian Powder must be confirmed BY EYE in-game as visually distinct before this is called complete — that check is explicitly NOT performed by this PR (no live Minecraft client was available in this session); it remains outstanding and is called out separately in the PR body and the MINECRAFT-21 ticket.
