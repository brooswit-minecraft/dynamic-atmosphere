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

import java.util.List;
import java.util.ArrayList;

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
    private record Volume(int x, int y, int z, float amount, boolean coarse) { }

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
        List<AtmosphereClientCache.VisibleCell> visible = cache.visibleDetailed(
            event.getPartialTick().getGameTimeDeltaPartialTick(false));
        visible.removeIf(cell -> {
            AABB bounds = bounds(cell.cell());
            return !level.getChunkSource().hasChunk(
                AtmosphereGridLayout.chunkCoordinate(cell.cell().x()),
                AtmosphereGridLayout.chunkCoordinate(cell.cell().z()))
                || !AtmosphereClientView.contains(cell.cell(), position.x, position.z, viewChunks)
                || !event.getFrustum().isVisible(bounds);
        });
        var volumes = new ArrayList<Volume>(visible.size());
        for (var cell : visible) {
            volumes.add(new Volume(cell.cell().x(), cell.cell().y(), cell.cell().z(), cell.amount(), false));
        }
        // Minecraft's existing projection far plane is 4x effective render distance.
        // Reuse it (and depth) rather than altering the projection for the cached layer.
        for (var cell : cache.coarseCells()) {
            if (!AtmosphereClientView.containsChunk(cell.x(), cell.z(), position.x, position.z, viewChunks * 4)) continue;
            if (AtmosphereClientView.coarseVisible(cell, position.x, position.z, viewChunks,
                AtmosphereClientView.containsChunk(cell.x(), cell.z(), position.x, position.z, viewChunks)
                    && level.getChunkSource().hasChunk(cell.x(), cell.z()))
                && event.getFrustum().isVisible(coarseBounds(cell))) {
                volumes.add(new Volume(cell.x(), cell.y(), cell.z(), cell.amount(), true));
            }
        }
        if (volumes.isEmpty()) {
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
            for (var cell : volumes) {
                var slices = cell.coarse()
                    ? AtmosphereVolumeGeometry.coarseSlices(cell.x(), cell.y(), cell.z(), cell.amount(), camera, forward)
                    : AtmosphereVolumeGeometry.slices(new AtmosphereClientCache.Cell(cell.x(), cell.y(), cell.z()),
                        cell.amount(), camera, forward);
                if (!slices.isEmpty()) {
                    if (cell.coarse()) renderedCoarseCount++;
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

    private static AABB bounds(AtmosphereClientCache.Cell cell) {
        double x = (double) cell.x() * AtmosphereVolumeGeometry.CELL_SIZE;
        double y = (double) cell.y() * AtmosphereVolumeGeometry.CELL_SIZE;
        double z = (double) cell.z() * AtmosphereVolumeGeometry.CELL_SIZE;
        return new AABB(x, y, z, x + AtmosphereGridLayout.CELL_SIZE,
            y + AtmosphereGridLayout.CELL_SIZE, z + AtmosphereGridLayout.CELL_SIZE);
    }

    private static AABB coarseBounds(AtmosphereClientView.CoarseCell cell) {
        double x = (double) cell.x() * 16;
        double y = (double) cell.y() * 16;
        double z = (double) cell.z() * 16;
        return new AABB(x, y, z, x + 16, y + 16, z + 16);
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
