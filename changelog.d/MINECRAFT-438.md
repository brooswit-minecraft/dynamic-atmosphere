bump: patch

### Fixed
- The Vapor visual cache no longer reads its historical disk cache synchronously on the main packet handler when joining a world or changing dimension; the read now happens off that thread and is applied back on the client thread in bounded batches, guarded against a stale result crossing a subsequent teleport/reconnect/dimension change. Live authoritative data still always wins over any restored historical value.
- Checkpointing the Vapor visual cache to disk now tracks only the chunks that actually changed (including chunks fully emptied, which are now explicitly deleted) instead of re-exporting and re-serializing the entire retained cache every checkpoint.
