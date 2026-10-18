bump: patch

### Changed
- Dust's default colour moved from `0x8B4513` (saddle brown) to `0xD2B48C` (tan), a sandier hue, by request. Optical density, LOD, producers, transformations, and every other material are unchanged. `dustColor` remains overridable; this changes only the SPEC default, so a config file already generated under the old default keeps rendering `0x8B4513` until that line is edited or the file is deleted/regenerated — fresh installs get the new default immediately.
