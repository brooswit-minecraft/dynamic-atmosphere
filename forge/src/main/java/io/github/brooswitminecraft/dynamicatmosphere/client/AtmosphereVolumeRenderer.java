package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.ArrayList;
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
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
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
        // LevelRenderer installs FogRenderer's biome/weather/dimension color before this stage.
        // Captured once, up front, so every material this frame (including VAPOR's own
        // fog-darkness opacity boost, see visit()) reads the exact same fog snapshot used
        // for VAPOR's RGB -- never a later/different sample.
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        var celestial = celestialFrame(event, level, forward);
        var ordered = new AtmosphereSliceOrder();
        for (var entry : materials.entrySet()) {
            visit(event, level, entry.getValue().cache(), camera, forward, entry.getKey(), fogColor,
                slices -> ordered.add(slices, entry.getKey()));
        }
        boolean mixed = !ordered.isEmpty();
        if (storage == null) {
            storage = new ByteBufferBuilder(1024 * 1024);
            buffers = MultiBufferSource.immediate(storage);
        }
        float[] color = RenderSystem.getShaderColor().clone();
        var shader = RenderSystem.getShader();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            var batch = new Batch(fogColor, celestial);
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            // Keep the constant-RGB vapor path unsorted when no other material is visible.
            // Otherwise merge slices, not volume centers: materials can overlap.
            visit(event, level, cache, camera, forward, AtmosphereRenderMaterial.VAPOR, fogColor, slices -> {
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
                              AtmosphereRenderMaterial material, float[] fogColor,
                              Consumer<List<AtmosphereVolumeGeometry.Slice>> consume) {
        int viewChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        var tuning = material.tuning();
        var config = io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot();
        double maxDistanceMultiplier = config.maxDistanceMultiplier();
        double viewBlocks = Math.max(1, viewChunks) * 16.0;
        double maxDistance = viewBlocks * maxDistanceMultiplier;
        double fadeStart = maxDistance * config.fadeStartFraction();
        cache.setReachMultiplier(maxDistanceMultiplier);
        cache.setView(camera.x(), camera.z(), viewChunks);
        var selection = cache.lodSelection(camera.x(), camera.y(), camera.z(), viewChunks);
        double tick = cache.renderTick(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        for (int pass = 0; pass < 2; pass++) {
            boolean fallback = pass == 1;
            for (var cell : fallback ? selection.unloadedFallbacks() : selection.volumes()) {
                boolean loaded = level.getChunkSource().hasChunk(
                    Math.floorDiv(cell.x, 16 / cell.baseCellSize), Math.floorDiv(cell.z, 16 / cell.baseCellSize));
                if (!AtmosphereLodHierarchy.visibleWhenLoaded(cell, fallback, loaded)) continue;
                if (!AtmosphereLodHierarchy.withinReach(cell, camera.x(), camera.z(), viewChunks, maxDistanceMultiplier)
                    || !event.getFrustum().isVisible(bounds(cell))) continue;
                // Fade uses the same nearest-point horizontal metric as the cull above,
                // ignoring Y, so vertical visibility is never affected by distance.
                double horizontalDistance = Math.sqrt(
                    AtmosphereLodHierarchy.horizontalDistanceSquared(cell, camera.x(), camera.z()));
                float fade = AtmosphereVolumeGeometry.fadeMultiplier(horizontalDistance, fadeStart, maxDistance);
                if (fade <= 0) continue;
                var slices = AtmosphereVolumeGeometry.lodSlices(cell, cell.amount(tick), camera, forward,
                    tuning.opticalDensity());
                // Farthest-corner check, horizontal only: decides whether any part of this
                // cell's box can extend past the cylindrical reach and needs clipping.
                double dx = Math.max(Math.abs(cell.blockX() - camera.x()), Math.abs(cell.blockX() + cell.size() - camera.x()));
                double dz = Math.max(Math.abs(cell.blockZ() - camera.z()), Math.abs(cell.blockZ() + cell.size() - camera.z()));
                if (dx * dx + dz * dz > maxDistance * maxDistance) {
                    slices = AtmosphereVolumeGeometry.clipToReach(slices, maxDistance);
                }
                // MINECRAFT-387: Vapor's fog-darkness opacity boost (extra optical density) and
                // distance fade (a plain post-hoc alpha multiply) must combine in that exact
                // order -- see AtmosphereRenderMaterial#vaporSliceAlpha. Every other material's
                // fade behaviour is unchanged.
                if (material == AtmosphereRenderMaterial.VAPOR) {
                    var adjusted = new ArrayList<AtmosphereVolumeGeometry.Slice>(slices.size());
                    for (var slice : slices) {
                        adjusted.add(new AtmosphereVolumeGeometry.Slice(slice.depth(),
                            AtmosphereRenderMaterial.vaporSliceAlpha(slice.alpha(), fade, fogColor), slice.vertices()));
                    }
                    slices = adjusted;
                } else if (fade < 1) {
                    var faded = new ArrayList<AtmosphereVolumeGeometry.Slice>(slices.size());
                    for (var slice : slices) {
                        faded.add(new AtmosphereVolumeGeometry.Slice(slice.depth(), slice.alpha() * fade, slice.vertices()));
                    }
                    slices = faded;
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
        private final CelestialFrame celestial;
        private VertexConsumer vertices;
        private int slices;
        Batch(float[] fogColor, CelestialFrame celestial) { this.fogColor = fogColor; this.celestial = celestial; }

        void draw(AtmosphereVolumeGeometry.Slice slice, AtmosphereRenderMaterial material) {
            if (vertices == null) vertices = buffers.getBuffer(VOLUME);
            float[] rgb = {material.channel(0, fogColor), material.channel(1, fogColor), material.channel(2, fogColor)};
            // MINECRAFT-399: blend toward the sun/moon tint colour on top of the material's own
            // base colour, shared by every material (including Vapor's fog-derived base) -- see
            // CelestialTint for the weight curve and why this is a bounded convex combination,
            // never an override.
            rgb = celestial.apply(rgb);
            float r = rgb[0], g = rgb[1], b = rgb[2];
            // MINECRAFT-387: Vapor's fog-darkness opacity boost is already baked into
            // slice.alpha() by visit() (applied before distance fade -- see there); every
            // other material's alpha is likewise already final by the time it reaches here.
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

    /**
     * MINECRAFT-399: per-frame sun/moon tint inputs, computed once and shared by every
     * material/slice this frame rather than per-vertex (the blend depends only on the camera's
     * fixed look direction and the frame's sun angle/visibility, never on slice position).
     */
    private record CelestialFrame(CelestialTint.Vector look, CelestialTint.Vector sunDir, CelestialTint.Vector moonDir,
                                  double sunVisibility, double moonVisibility, double maxStrength,
                                  float[] sunColor, float[] moonColor) {
        float[] apply(float[] base) {
            double sunWeight = CelestialTint.weight(look, sunDir, sunVisibility, maxStrength);
            double moonWeight = CelestialTint.weight(look, moonDir, moonVisibility, maxStrength);
            return CelestialTint.blend(base, sunColor, sunWeight, moonColor, moonWeight);
        }
    }

    /** Soft horizon band half-width, in direction-Y units (see {@link CelestialTint#horizonWeight}). */
    private static final double HORIZON_SOFTEN = 0.06;
    /** Per-body occlusion ray step, in blocks. */
    private static final double OCCLUSION_RAY_STEP = 2.0;
    /** Per-body occlusion ray bound, in blocks: a terrain/building test needs to reach past
     *  ordinary structures, not the full render distance -- bounding it keeps the per-frame cost
     *  fixed regardless of view distance, which the {@code canSeeSky} column check this replaces
     *  did not need to worry about (a single height-map lookup is O(1) either way). */
    private static final double OCCLUSION_RAY_MAX_DISTANCE = 96.0;
    /** Jittered-ring radius/count for soft occlusion edges (see {@link CelestialTint#jitterRing}):
     *  5 total samples per body per frame, small enough that the tint changing under a roof edge
     *  or around a wall corner reads as a gradient over roughly a block of camera movement rather
     *  than an instant pop, without materially increasing the per-frame ray budget. */
    private static final double OCCLUSION_JITTER_RADIUS = 0.08;
    private static final int OCCLUSION_JITTER_COUNT = 4;

    /**
     * Visibility is gated on three independent things, each folding to 0 (no tint, not a guess)
     * rather than assuming time-of-day alone means a body is visible -- see the ticket's
     * acceptance criteria and CelestialTint's class javadoc:
     * <ul>
     * <li>Dimension: only {@link DimensionSpecialEffects.SkyType#NORMAL} dimensions render a sun
     * and moon at all (vanilla's {@code LevelRenderer#renderSky} only draws the sun/moon quads in
     * that branch); the End's own sky type draws no sun/moon, and the Nether has no sky to render
     * in the first place.</li>
     * <li>Horizon: a body below the local horizon (negative world Y component of its direction)
     * cannot be contributing real light from that direction; smoothed by
     * {@link CelestialTint#horizonWeight} over {@link #HORIZON_SOFTEN} rather than a bare
     * {@code > 0} step, so crossing the horizon fades rather than pops.</li>
     * <li>Weather: vanilla's own sky renderer fades the sun/moon quads to fully transparent as
     * rain level approaches 1 ({@code 1 - getRainLevel}); mirrored here exactly so this tint
     * disappears in step with the vanilla sun/moon disappearing into cloud, with no separate
     * thunder/clear-sky case to invent.</li>
     * </ul>
     * Terrain/building occlusion is evaluated PER BODY, toward that body's own direction from the
     * camera -- not a single shared "can the camera see the sky overhead" column check, which
     * conflates the two: a wall or hill directly between the camera and a low sun hides it even
     * with open sky overhead, and a gap in an overhang can reveal a body the camera's own column
     * does not have clear sky above. {@link #bodyOcclusionVisibility} casts a small jittered
     * bundle of rays toward the body (see {@link CelestialTint#jitterRing}) and returns the
     * fraction that reach {@link #OCCLUSION_RAY_MAX_DISTANCE} unblocked, so occlusion is a smooth
     * fraction in {@code [0, 1]}, not a single binary hit/miss -- the camera crossing a cover edge
     * moves the bundle's rays across that edge one at a time rather than all at once. Each ray
     * walks outward from the camera in fixed steps and stops, treating the remainder of that ray
     * as unblocked (the safe default, not a claim of visibility it cannot verify), the moment it
     * either leaves the loaded world ({@code Level#isLoaded}, never forcing a chunk to load) or
     * exits the world's build height -- a real body is effectively at infinity, so nothing beyond
     * the bounded probe distance can be checked either way.
     */
    private static CelestialFrame celestialFrame(RenderLevelStageEvent event, ClientLevel level,
                                                  AtmosphereVolumeGeometry.Point forward) {
        var config = io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot();
        double len = Math.sqrt(forward.dot(forward));
        var look = len > 0 && Double.isFinite(len)
            ? new CelestialTint.Vector(forward.x() / len, forward.y() / len, forward.z() / len)
            : new CelestialTint.Vector(0, 0, 0);
        boolean rendersSunMoon = level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        CelestialTint.Vector sunDir = new CelestialTint.Vector(0, 0, 0);
        CelestialTint.Vector moonDir = new CelestialTint.Vector(0, 0, 0);
        double sunVisibility = 0;
        double moonVisibility = 0;
        if (rendersSunMoon) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            double sunAngle = level.getSunAngle(partialTick);
            sunDir = CelestialTint.sunDirection(sunAngle);
            moonDir = CelestialTint.moonDirection(sunAngle);
            double rainFade = 1 - Math.clamp((double) level.getRainLevel(partialTick), 0, 1);
            var eye = event.getCamera().getPosition();
            double sunHorizon = CelestialTint.horizonWeight(sunDir.y(), HORIZON_SOFTEN);
            double moonHorizon = CelestialTint.horizonWeight(moonDir.y(), HORIZON_SOFTEN);
            sunVisibility = sunHorizon > 0 ? sunHorizon * rainFade * bodyOcclusionVisibility(level, eye, sunDir) : 0;
            moonVisibility = moonHorizon > 0 ? moonHorizon * rainFade * bodyOcclusionVisibility(level, eye, moonDir) : 0;
        }
        return new CelestialFrame(look, sunDir, moonDir, sunVisibility, moonVisibility,
            config.celestialTintMaxStrength(), unpack(config.sunTintColor()), unpack(config.moonTintColor()));
    }

    /** See {@link #celestialFrame}'s javadoc for the occlusion model this implements. */
    private static double bodyOcclusionVisibility(ClientLevel level, net.minecraft.world.phys.Vec3 eye,
                                                   CelestialTint.Vector direction) {
        var rays = CelestialTint.jitterRing(direction, OCCLUSION_JITTER_RADIUS, OCCLUSION_JITTER_COUNT);
        int clear = 0;
        for (var ray : rays) {
            if (!rayObstructed(level, eye, ray)) clear++;
        }
        return (double) clear / rays.length;
    }

    private static boolean rayObstructed(ClientLevel level, net.minecraft.world.phys.Vec3 eye, CelestialTint.Vector ray) {
        for (double distance = OCCLUSION_RAY_STEP; distance <= OCCLUSION_RAY_MAX_DISTANCE; distance += OCCLUSION_RAY_STEP) {
            BlockPos pos = BlockPos.containing(
                eye.x + ray.x() * distance, eye.y + ray.y() * distance, eye.z + ray.z() * distance);
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return false;
            if (level.getBlockState(pos).getLightBlock(level, pos) >= 15) return true;
        }
        return false;
    }

    private static float[] unpack(int rgb) {
        return new float[] {((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
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
