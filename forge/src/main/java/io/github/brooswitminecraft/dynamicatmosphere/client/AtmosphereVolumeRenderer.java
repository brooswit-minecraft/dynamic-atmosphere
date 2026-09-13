package io.github.brooswitminecraft.dynamicatmosphere.client;

import io.github.brooswitminecraft.dynamicatmosphere.AtmosphereGridLayout;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

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
            return;
        }
        if (storage == null) {
            storage = new ByteBufferBuilder(1024 * 1024);
            buffers = MultiBufferSource.immediate(storage);
        }
        float[] color = RenderSystem.getShaderColor().clone();
        var shader = RenderSystem.getShader();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            VertexConsumer vertices = null;
            int batchSlices = 0;
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            // All slices have identical white RGB and no depth writes, so alpha
            // composition is order-independent. Stream bounded GPU batches without
            // a global slice list or nearest-cell cutoff. Revisit if materials gain colors.
            for (int layer = 0; layer < 2; layer++) {
                var volumes = layer == 0 ? selection.volumes() : selection.unloadedFallbacks();
                for (var cell : volumes) {
                    // Chunk sections own fallback membership, so load/unload never double-covers detail.
                    boolean loaded = level.getChunkSource().hasChunk(
                        AtmosphereGridLayout.chunkCoordinate(cell.x), AtmosphereGridLayout.chunkCoordinate(cell.z));
                    if (!AtmosphereLodHierarchy.visibleWhenLoaded(cell, layer == 1, loaded)) continue;
                    if (AtmosphereLodHierarchy.distanceSquared(cell, position.x, position.y, position.z)
                        > farDistance * farDistance || !event.getFrustum().isVisible(bounds(cell))) continue;
                    var slices = AtmosphereVolumeGeometry.lodSlices(cell, cell.amount(tick), camera, forward);
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
                            vertex(vertices, polygon.getFirst(), slice.alpha());
                            vertex(vertices, polygon.get(i), slice.alpha());
                            vertex(vertices, polygon.get(i + 1), slice.alpha());
                        }
                        renderedSliceCount++;
                        if (++batchSlices == SLICES_PER_BATCH) {
                            buffers.endBatch(VOLUME);
                            vertices = null;
                            batchSlices = 0;
                        }
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

    private static AABB bounds(AtmosphereLodHierarchy.Volume cell) {
        double x = cell.blockX(), y = cell.blockY(), z = cell.blockZ();
        return new AABB(x, y, z, x + cell.size(), y + cell.size(), z + cell.size());
    }

    private static void vertex(VertexConsumer vertices, AtmosphereVolumeGeometry.Point point, float alpha) {
        vertices.addVertex((float) point.x(), (float) point.y(), (float) point.z()).setColor(1.0f, 1.0f, 1.0f, alpha);
    }

    static void close() {
        if (storage != null) {
            storage.close();
            storage = null;
            buffers = null;
        }
        renderedCellCount = 0;
        renderedSliceCount = 0;
        renderedCoarseCount = 0;
    }

    public static int renderedCellCount() { return renderedCellCount; }
    public static int renderedSliceCount() { return renderedSliceCount; }
    public static int renderedCoarseCount() { return renderedCoarseCount; }

    private AtmosphereVolumeRenderer() {
        super("dynamicatmosphere_volume", () -> { }, () -> { });
    }
}
