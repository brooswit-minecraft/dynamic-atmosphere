package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

    static void render(RenderLevelStageEvent event, ClientLevel level, AtmosphereClientCache cache,
                       Map<AtmosphereRenderMaterial, MaterialClientSession> materials) {
        renderedCellCount = 0;
        renderedSliceCount = 0;
        renderedCoarseCount = 0;
        if (!io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().enabled()) return;
        if (cache.size() == 0 && materials.values().stream().allMatch(session -> session.cache().size() == 0)) return;
        var position = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var camera = new AtmosphereVolumeGeometry.Point(position.x, position.y, position.z);
        var forward = new AtmosphereVolumeGeometry.Point(look.x(), look.y(), look.z());
        var ordered = new AtmosphereSliceOrder();
        for (var entry : materials.entrySet()) {
            visit(event, level, entry.getValue().cache(), camera, forward, entry.getKey(),
                slices -> ordered.add(slices, entry.getKey()));
        }
        boolean mixed = !ordered.isEmpty();
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
            var batch = new Batch(fogColor);
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            // Keep the constant-RGB vapor path unsorted when no other material is visible.
            // Otherwise merge slices, not volume centers: materials can overlap.
            visit(event, level, cache, camera, forward, AtmosphereRenderMaterial.VAPOR, slices -> {
                if (mixed) ordered.add(slices, AtmosphereRenderMaterial.VAPOR);
                else for (var slice : slices) batch.draw(slice, AtmosphereRenderMaterial.VAPOR);
            });
            while (!ordered.isEmpty()) {
                var slice = ordered.next();
                batch.draw(slice.slice(), slice.material());
            }
            batch.flush();
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

    private static void visit(RenderLevelStageEvent event, ClientLevel level, AtmosphereClientCache cache,
                              AtmosphereVolumeGeometry.Point camera, AtmosphereVolumeGeometry.Point forward,
                              AtmosphereRenderMaterial material,
                              Consumer<List<AtmosphereVolumeGeometry.Slice>> consume) {
        int viewChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        var tuning = material.tuning();
        cache.setReachMultiplier(tuning.reachMultiplier());
        cache.setView(camera.x(), camera.z(), viewChunks);
        var selection = cache.lodSelection(camera.x(), camera.y(), camera.z(), viewChunks);
        double tick = cache.renderTick(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        for (int pass = 0; pass < 2; pass++) {
            boolean fallback = pass == 1;
            for (var cell : fallback ? selection.unloadedFallbacks() : selection.volumes()) {
                boolean loaded = level.getChunkSource().hasChunk(
                    Math.floorDiv(cell.x, 16 / cell.baseCellSize), Math.floorDiv(cell.z, 16 / cell.baseCellSize));
                if (!AtmosphereLodHierarchy.visibleWhenLoaded(cell, fallback, loaded)) continue;
                if (!AtmosphereLodHierarchy.withinReach(cell, camera.x(), camera.y(), camera.z(), viewChunks, tuning.reachMultiplier())
                    || !event.getFrustum().isVisible(bounds(cell))) continue;
                var slices = AtmosphereVolumeGeometry.lodSlices(cell, cell.amount(tick), camera, forward,
                    tuning.opticalDensity());
                double dx = Math.max(Math.abs(cell.blockX() - camera.x()), Math.abs(cell.blockX() + cell.size() - camera.x()));
                double dy = Math.max(Math.abs(cell.blockY() - camera.y()), Math.abs(cell.blockY() + cell.size() - camera.y()));
                double dz = Math.max(Math.abs(cell.blockZ() - camera.z()), Math.abs(cell.blockZ() + cell.size() - camera.z()));
                double reach = Math.max(1, viewChunks) * 16.0 * tuning.reachMultiplier();
                if (dx * dx + dy * dy + dz * dz > reach * reach) {
                    slices = AtmosphereVolumeGeometry.clipToReach(slices, forward, reach);
                }
                if (!slices.isEmpty()) {
                    if (cell.level > 0) renderedCoarseCount++;
                    else renderedCellCount++;
                    consume.accept(slices);
                }
            }
        }
    }

    private static final class Batch {
        private final float[] fogColor;
        private VertexConsumer vertices;
        private int slices;
        Batch(float[] fogColor) { this.fogColor = fogColor; }

        void draw(AtmosphereVolumeGeometry.Slice slice, AtmosphereRenderMaterial material) {
            if (vertices == null) vertices = buffers.getBuffer(VOLUME);
            float r = material.channel(0, fogColor), g = material.channel(1, fogColor), b = material.channel(2, fogColor);
            var polygon = slice.vertices();
            for (int i = 1; i < polygon.size() - 1; i++) {
                vertex(vertices, polygon.getFirst(), slice.alpha(), r, g, b);
                vertex(vertices, polygon.get(i), slice.alpha(), r, g, b);
                vertex(vertices, polygon.get(i + 1), slice.alpha(), r, g, b);
            }
            renderedSliceCount++;
            if (++slices >= io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot().slicesPerBatch()) flush();
        }

        void flush() {
            if (vertices != null) buffers.endBatch(VOLUME);
            vertices = null;
            slices = 0;
        }
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
