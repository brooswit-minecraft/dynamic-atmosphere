package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

public final class AtmosphereVolumeRenderer extends RenderStateShard {
    private static final int SLICES_PER_BATCH = 4096;
    private static final RenderType VOLUME = RenderType.create(
        "dynamicatmosphere_volume", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES,
        1024 * 1024, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setOutputState(PARTICLES_TARGET)
            .createCompositeState(false));
    private static ByteBufferBuilder storage;
    private static MultiBufferSource.BufferSource buffers;
    private static int renderedCellCount;
    private static int renderedSliceCount;
    private static int renderedCoarseCount;
    private record DrawVolume(AtmosphereLodHierarchy.Volume cell, boolean fallback) { }
    private static AtmosphereLodHierarchy.Selection orderedSelection;
    private static AtmosphereClientCache.Cell orderCamera;
    private static List<DrawVolume> orderedVolumes = List.of();
    private static final AtmosphereLightCache LIGHT = new AtmosphereLightCache();

    static void tick(ClientLevel level) {
        LIGHT.advance(cell -> {
            var chunk = level.getChunkSource().getChunk(AtmosphereGridLayout.chunkCoordinate(cell.x()),
                AtmosphereGridLayout.chunkCoordinate(cell.z()), ChunkStatus.FULL, false);
            if (chunk == null) return Double.NaN;
            int size = AtmosphereVolumeGeometry.CELL_SIZE;
            var pos = new BlockPos.MutableBlockPos();
            return AtmosphereLightCache.meanAirLight(index -> {
                pos.set(cell.x() * size + index % size, cell.y() * size + index / (size * size),
                    cell.z() * size + (index / size) % size);
                return !level.isOutsideBuildHeight(pos) && chunk.getBlockState(pos).isAir()
                    ? level.getMaxLocalRawBrightness(pos) : -1;
            });
        });
    }

    static void render(RenderLevelStageEvent event, ClientLevel level, AtmosphereClientCache cache) {
        renderedCellCount = 0;
        renderedSliceCount = 0;
        renderedCoarseCount = 0;
        var position = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var camera = new AtmosphereVolumeGeometry.Point(position.x, position.y, position.z);
        var forward = new AtmosphereVolumeGeometry.Point(look.x(), look.y(), look.z());
        int viewChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        cache.setView(position.x, position.z, viewChunks);
        var selection = cache.lodSelection(position.x, position.y, position.z, viewChunks);
        double tick = cache.renderTick(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        double farDistance = viewChunks * 64.0;
        if (selection.volumes().isEmpty() && selection.unloadedFallbacks().isEmpty()) {
            orderedSelection = null;
            orderCamera = null;
            orderedVolumes = List.of();
            return;
        }
        if (storage == null) {
            storage = new ByteBufferBuilder(1024 * 1024);
            buffers = MultiBufferSource.immediate(storage);
        }
        float[] color = RenderSystem.getShaderColor().clone();
        // LevelRenderer installs FogRenderer's biome/weather/dimension color before this stage.
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        var shader = RenderSystem.getShader();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            VertexConsumer vertices = null;
            int batchSlices = 0;
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            // Mixed horizon/gray RGB requires painter order, including near unloaded fallbacks.
            // Cache BSP order of selected volumes; rotation never sorts a global slice list.
            // Each volume has uniform RGB, so its internal slices still commute.
            for (var draw : ordered(selection, camera)) {
                var cell = draw.cell();
                // Chunk sections own fallback membership, so load/unload never double-covers detail.
                boolean loaded = level.getChunkSource().hasChunk(
                    AtmosphereGridLayout.chunkCoordinate(cell.x), AtmosphereGridLayout.chunkCoordinate(cell.z));
                if (!AtmosphereLodHierarchy.visibleWhenLoaded(cell, draw.fallback(), loaded)) continue;
                if (AtmosphereLodHierarchy.distanceSquared(cell, position.x, position.y, position.z)
                    > farDistance * farDistance || !event.getFrustum().isVisible(bounds(cell))) continue;
                var slices = AtmosphereVolumeGeometry.lodSlices(cell, cell.amount(tick), camera, forward);
                float gray = cell.level == 0 ? LIGHT.value(new AtmosphereClientCache.Cell(cell.x, cell.y, cell.z)) : 0;
                float red = AtmosphereVolumeGeometry.colorChannel(cell.level, fogColor[0], gray);
                float green = AtmosphereVolumeGeometry.colorChannel(cell.level, fogColor[1], gray);
                float blue = AtmosphereVolumeGeometry.colorChannel(cell.level, fogColor[2], gray);
                if (!slices.isEmpty()) {
                    if (cell.level > 0) renderedCoarseCount++;
                    else renderedCellCount++;
                }
                for (var slice : slices) {
                    if (vertices == null) {
                        vertices = buffers.getBuffer(VOLUME);
                    }
                    var polygon = slice.vertices();
                    for (int i = 1; i < polygon.size() - 1; i++) {
                        vertex(vertices, polygon.getFirst(), slice.alpha(), red, green, blue);
                        vertex(vertices, polygon.get(i), slice.alpha(), red, green, blue);
                        vertex(vertices, polygon.get(i + 1), slice.alpha(), red, green, blue);
                    }
                    renderedSliceCount++;
                    if (++batchSlices == SLICES_PER_BATCH) {
                        buffers.endBatch(VOLUME);
                        vertices = null;
                        batchSlices = 0;
                    }
                }
            }
            if (vertices != null) {
                buffers.endBatch(VOLUME);
            }
        } finally {
            // RenderType.draw does not clear its state if the GPU upload throws.
            try {
                VOLUME.clearRenderState();
            } finally {
                RenderSystem.setShader(() -> shader);
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    private static List<DrawVolume> ordered(AtmosphereLodHierarchy.Selection selection, AtmosphereVolumeGeometry.Point camera) {
        var cell = AtmosphereVolumeGeometry.cameraCell(camera);
        int count = selection.volumes().size() + selection.unloadedFallbacks().size();
        if (orderedSelection == selection && cell.equals(orderCamera) && orderedVolumes.size() == count) return orderedVolumes;
        var result = new ArrayList<DrawVolume>(count);
        for (var volume : selection.volumes()) result.add(new DrawVolume(volume, false));
        for (var volume : selection.unloadedFallbacks()) result.add(new DrawVolume(volume, true));
        result.sort((a, b) -> AtmosphereVolumeGeometry.backToFront(a.cell(), b.cell(), cell));
        orderedSelection = selection;
        orderCamera = cell;
        orderedVolumes = result;
        return result;
    }

    private static AABB bounds(AtmosphereLodHierarchy.Volume cell) {
        double x = cell.blockX(), y = cell.blockY(), z = cell.blockZ();
        return new AABB(x, y, z, x + cell.size(), y + cell.size(), z + cell.size());
    }

    private static void vertex(VertexConsumer vertices, AtmosphereVolumeGeometry.Point point, float alpha,
                               float red, float green, float blue) {
        vertices.addVertex((float) point.x(), (float) point.y(), (float) point.z()).setColor(red, green, blue, alpha);
    }

    static void close() {
        LIGHT.clear();
        if (storage != null) {
            storage.close();
            storage = null;
            buffers = null;
        }
        renderedCellCount = 0;
        renderedSliceCount = 0;
        renderedCoarseCount = 0;
        orderedSelection = null;
        orderCamera = null;
        orderedVolumes = List.of();
    }

    public static int renderedCellCount() { return renderedCellCount; }
    public static int renderedSliceCount() { return renderedSliceCount; }
    public static int renderedCoarseCount() { return renderedCoarseCount; }

    private AtmosphereVolumeRenderer() {
        super("dynamicatmosphere_volume", () -> { }, () -> { });
    }
}
