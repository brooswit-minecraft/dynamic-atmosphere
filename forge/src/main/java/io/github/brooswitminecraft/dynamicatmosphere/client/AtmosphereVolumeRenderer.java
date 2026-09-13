package io.github.brooswitminecraft.dynamicatmosphere.client;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AtmosphereVolumeRenderer extends RenderStateShard {
    public static final int MAX_RENDER_CELLS = 512;
    private static final int RENDER_RADIUS = 128;
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

    static void render(RenderLevelStageEvent event, ClientLevel level, AtmosphereClientCache cache) {
        renderedCellCount = 0;
        renderedSliceCount = 0;
        var position = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var camera = new AtmosphereVolumeGeometry.Point(position.x, position.y, position.z);
        var forward = new AtmosphereVolumeGeometry.Point(look.x(), look.y(), look.z());
        List<AtmosphereClientCache.VisibleCell> visible = cache.visible(
            event.getPartialTick().getGameTimeDeltaPartialTick(false));
        visible.removeIf(cell -> {
            AABB bounds = bounds(cell.cell());
            return !level.getChunkSource().hasChunk(cell.cell().x(), cell.cell().z())
                || bounds.distanceToSqr(position) > (double) RENDER_RADIUS * RENDER_RADIUS
                || !event.getFrustum().isVisible(bounds);
        });
        visible.sort(Comparator.comparingDouble(cell -> bounds(cell.cell()).distanceToSqr(position)));
        List<AtmosphereVolumeGeometry.Slice> slices = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_RENDER_CELLS, visible.size()); i++) {
            var cell = visible.get(i);
            var cellSlices = AtmosphereVolumeGeometry.slices(cell.cell(), cell.amount(), camera, forward);
            if (!cellSlices.isEmpty()) {
                renderedCellCount++;
                slices.addAll(cellSlices);
            }
        }
        if (slices.isEmpty()) {
            return;
        }
        slices.sort(Comparator.comparingDouble(AtmosphereVolumeGeometry.Slice::depth).reversed());
        if (storage == null) {
            storage = new ByteBufferBuilder(1024 * 1024);
            buffers = MultiBufferSource.immediate(storage);
        }
        float[] color = RenderSystem.getShaderColor().clone();
        var shader = RenderSystem.getShader();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            VertexConsumer vertices = buffers.getBuffer(VOLUME);
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            for (var slice : slices) {
                var polygon = slice.vertices();
                for (int i = 1; i < polygon.size() - 1; i++) {
                    vertex(vertices, polygon.getFirst(), slice.alpha());
                    vertex(vertices, polygon.get(i), slice.alpha());
                    vertex(vertices, polygon.get(i + 1), slice.alpha());
                }
            }
            buffers.endBatch(VOLUME);
            renderedSliceCount = slices.size();
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
    }

    public static int renderedCellCount() { return renderedCellCount; }
    public static int renderedSliceCount() { return renderedSliceCount; }

    private AtmosphereVolumeRenderer() {
        super("dynamicatmosphere_volume", () -> { }, () -> { });
    }
}
