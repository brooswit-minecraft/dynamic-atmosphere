package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Disposable visual data only. No server simulation state is read from this directory. */
public final class AtmosphereDiskCache {
    private static final int MAGIC = 0x44414335;
    private static final int MAX_CHUNK_CELLS = 32768;
    private static final int MAX_LOADED_CELLS = 200000;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_FILES = 8192;
    private final Path root;

    public AtmosphereDiskCache(Path root) { this.root = root; }

    public String namespace(String server, UUID world, String dimension) {
        try {
            byte[] identity = (server + "\n" + world + "\n" + dimension + "\n" + AtmosphereGridLayout.CELL_SIZE)
                .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public synchronized List<AtmosphereClientCache.Update> load(String namespace) throws IOException {
        Path directory = directory(namespace);
        if (!Files.isDirectory(directory)) return List.of();
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".bin"))
                .sorted(Comparator.comparingLong(AtmosphereDiskCache::modified).reversed()).limit(MAX_FILES).toList();
        }
        var result = new ArrayList<AtmosphereClientCache.Update>();
        for (Path file : files) {
            try {
                if (Files.size(file) > 16L + MAX_CHUNK_CELLS * 20L) continue;
                try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    if (in.readInt() != MAGIC) continue;
                    int chunkX = in.readInt(), chunkZ = in.readInt(), count = in.readInt();
                    if (count < 0 || count > MAX_CHUNK_CELLS || result.size() + count > MAX_LOADED_CELLS) continue;
                    var chunk = new ArrayList<AtmosphereClientCache.Update>(count);
                    var coordinates = new HashSet<AtmosphereClientCache.Cell>();
                    for (int i = 0; i < count; i++) {
                        var cell = new AtmosphereClientCache.Cell(in.readInt(), in.readInt(), in.readInt());
                        int amount = in.readInt(), capacity = in.readInt();
                        if (AtmosphereGridLayout.chunkCoordinate(cell.x()) != chunkX
                            || AtmosphereGridLayout.chunkCoordinate(cell.z()) != chunkZ
                            || amount <= 0 || amount > 1000000 || capacity < 0 || capacity > 1000
                            || !coordinates.add(cell)) throw new IOException("Invalid visual cache cell");
                        chunk.add(new AtmosphereClientCache.Update(cell, amount, capacity));
                    }
                    if (in.read() != -1) throw new IOException("Trailing visual cache data");
                    result.addAll(chunk);
                    Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                }
            } catch (IOException invalid) {
                // A damaged/disappearing cache file must never prevent joining a world.
            }
        }
        return result;
    }

    /** Full retained client view, written incrementally per changed chunk. */
    public synchronized void save(String namespace, List<AtmosphereClientCache.Update> cells) throws IOException {
        Path directory = directory(namespace);
        Files.createDirectories(directory);
        record Chunk(int x, int z) { }
        Map<Chunk, List<AtmosphereClientCache.Update>> chunks = new LinkedHashMap<>();
        for (var update : cells) {
            if (update.amount() <= 0) continue;
            var key = new Chunk(AtmosphereGridLayout.chunkCoordinate(update.cell().x()),
                AtmosphereGridLayout.chunkCoordinate(update.cell().z()));
            chunks.computeIfAbsent(key, ignored -> new ArrayList<>()).add(update);
        }
        Set<String> retained = new HashSet<>();
        for (var entry : chunks.entrySet()) {
            String name = entry.getKey().x() + "_" + entry.getKey().z() + ".bin";
            retained.add(name);
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC); out.writeInt(entry.getKey().x()); out.writeInt(entry.getKey().z());
                out.writeInt(entry.getValue().size());
                entry.getValue().sort(Comparator.comparingInt((AtmosphereClientCache.Update u) -> u.cell().x())
                    .thenComparingInt(u -> u.cell().y()).thenComparingInt(u -> u.cell().z()));
                for (var update : entry.getValue()) {
                    out.writeInt(update.cell().x()); out.writeInt(update.cell().y()); out.writeInt(update.cell().z());
                    out.writeInt(update.amount()); out.writeInt(update.capacity());
                }
            }
            Path target = directory.resolve(name);
            byte[] data = bytes.toByteArray();
            if (Files.exists(target) && Files.size(target) == data.length
                && Arrays.equals(Files.readAllBytes(target), data)) continue;
            Path temporary = Files.createTempFile(directory, "chunk-", ".tmp");
            try {
                Files.write(temporary, data);
                try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".bin")).toList()) {
                if (!retained.contains(file.getFileName().toString())) Files.deleteIfExists(file);
            }
        }
        trim();
    }

    private Path directory(String namespace) {
        if (!namespace.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid cache namespace");
        return root.resolve(namespace);
    }

    private void trim() throws IOException {
        List<Path> files;
        try (var stream = Files.walk(root, 2)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".bin") && Files.isRegularFile(p))
                .sorted(Comparator.comparingLong(AtmosphereDiskCache::modified)).toList();
        }
        long bytes = 0;
        for (Path file : files) bytes += Files.size(file);
        int count = files.size();
        for (Path file : files) {
            if (bytes <= MAX_BYTES && count <= MAX_FILES) break;
            long size = Files.size(file);
            if (Files.deleteIfExists(file)) { bytes -= size; count--; }
        }
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException gone) { return 0; }
    }
}
