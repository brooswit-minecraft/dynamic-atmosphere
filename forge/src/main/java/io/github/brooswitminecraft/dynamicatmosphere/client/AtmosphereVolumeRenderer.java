package io.github.brooswitminecraft.dynamicatmosphere.client;

import java.util.EnumMap;
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
    // MINECRAFT-399: persistent across frames (not reset at the top of render()) so
    // CelestialTint#smoothTowards has a "last frame's value" to interpolate from; a per-body
    // ray-bundle occlusion sample can only take a handful of discrete values (see
    // celestialFrame's javadoc), and this is what turns that into an actually smooth-over-time
    // signal rather than a value that jumps straight to each new discrete sample. Reset in
    // close() (dimension/world change) so a teleport does not slowly fade in from a stale value.
    private static double sunVisibilityState;
    private static double moonVisibilityState;

    static void render(RenderLevelStageEvent event, ClientLevel level, AtmosphereClientCache cache,
                       Map<AtmosphereRenderMaterial, MaterialClientSession> materials) {
        renderedCellCount = 0;
        renderedSliceCount = 0;
        renderedCoarseCount = 0;
        // MINECRAFT-432: one config snapshot for the whole frame, threaded explicitly into every
        // material/cell below, instead of each of them independently calling snapshot() -- a
        // config reload mid-frame can no longer make one material/cell see different tuning than
        // another within the same frame (the acceptance criterion this preserves).
        var config = io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.snapshot();
        if (!config.enabled()) return;
        if (cache.size() == 0 && materials.values().stream().allMatch(session -> session.cache().size() == 0)) return;
        var position = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var camera = new AtmosphereVolumeGeometry.Point(position.x, position.y, position.z);
        var forward = new AtmosphereVolumeGeometry.Point(look.x(), look.y(), look.z());
        // MINECRAFT-432: one normalized camera-facing basis for the whole frame, instead of
        // every visible volume's geometry generation renormalizing the same look vector and
        // rebuilding the same right/up cross products.
        var basis = AtmosphereVolumeGeometry.Basis.of(forward);
        // LevelRenderer installs FogRenderer's biome/weather/dimension color before this stage.
        // Captured once, up front, so every material this frame (including VAPOR's own
        // fog-darkness opacity boost, see visit()) reads the exact same fog snapshot used
        // for VAPOR's RGB -- never a later/different sample.
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        var celestial = celestialFrame(event, level, forward, config);
        var ordered = new AtmosphereSliceOrder();
        for (var entry : materials.entrySet()) {
            visit(event, level, entry.getValue().cache(), camera, basis, entry.getKey(), fogColor, config,
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
            var batch = new Batch(fogColor, celestial, config);
            // AFTER_PARTICLES already has the event model-view rotation on RenderSystem's
            // stack. These vertices are camera-relative: do not apply that matrix twice.
            // Keep the constant-RGB vapor path unsorted when no other material is visible.
            // Otherwise merge slices, not volume centers: materials can overlap.
            visit(event, level, cache, camera, basis, AtmosphereRenderMaterial.VAPOR, fogColor, config, slices -> {
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
                              AtmosphereVolumeGeometry.Point camera, AtmosphereVolumeGeometry.Basis basis,
                              AtmosphereRenderMaterial material, float[] fogColor,
                              io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.Snapshot config,
                              Consumer<List<AtmosphereVolumeGeometry.Slice>> consume) {
        int viewChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        var tuning = material.tuning(config);
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
                // MINECRAFT-432: Vapor's fog-darkness opacity boost (extra optical density) and
                // distance fade (a plain post-hoc alpha multiply) must combine in that exact
                // order -- see AtmosphereRenderMaterial#vaporSliceAlpha -- every other material's
                // fade behaviour is unchanged. Folded into an AlphaAdjust hook that lodSlices
                // applies at slice-generation time, instead of copying the returned list a
                // second time afterward just to rewrite every slice's alpha.
                AtmosphereVolumeGeometry.AlphaAdjust alphaAdjust = material == AtmosphereRenderMaterial.VAPOR
                    ? rawAlpha -> AtmosphereRenderMaterial.vaporSliceAlpha(
                        rawAlpha, fade, fogColor, config.vaporDarknessOpacityBoost())
                    : fade < 1 ? rawAlpha -> rawAlpha * fade : rawAlpha -> rawAlpha;
                var slices = AtmosphereVolumeGeometry.lodSlices(cell, cell.amount(tick), camera, basis,
                    tuning.opticalDensity(), config.slicesPerBaseCell(), alphaAdjust);
                // Farthest-corner check, horizontal only: decides whether any part of this
                // cell's box can extend past the cylindrical reach and needs clipping.
                double dx = Math.max(Math.abs(cell.blockX() - camera.x()), Math.abs(cell.blockX() + cell.size() - camera.x()));
                double dz = Math.max(Math.abs(cell.blockZ() - camera.z()), Math.abs(cell.blockZ() + cell.size() - camera.z()));
                if (dx * dx + dz * dz > maxDistance * maxDistance) {
                    slices = AtmosphereVolumeGeometry.clipToReach(slices, maxDistance);
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
        private final io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.Snapshot config;
        // MINECRAFT-432: final RGB depends only on material and this frame's fog/celestial
        // state, never on slice position -- computed once per visible material (lazily, the
        // first time that material is drawn this frame) and reused for every slice of it,
        // instead of recomputing per slice.
        private final Map<AtmosphereRenderMaterial, float[]> rgbByMaterial = new EnumMap<>(AtmosphereRenderMaterial.class);
        private VertexConsumer vertices;
        private int slices;
        Batch(float[] fogColor, CelestialFrame celestial,
              io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.Snapshot config) {
            this.fogColor = fogColor;
            this.celestial = celestial;
            this.config = config;
        }

        void draw(AtmosphereVolumeGeometry.Slice slice, AtmosphereRenderMaterial material) {
            if (vertices == null) vertices = buffers.getBuffer(VOLUME);
            // MINECRAFT-399: blend toward the sun/moon tint colour on top of the material's own
            // base colour, shared by every material (including Vapor's fog-derived base) -- see
            // CelestialTint for the weight curve and why this is a bounded convex combination,
            // never an override.
            float[] rgb = rgbByMaterial.computeIfAbsent(material, m -> celestial.apply(new float[] {
                m.channel(0, fogColor, config), m.channel(1, fogColor, config), m.channel(2, fogColor, config)}));
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
            if (++slices >= config.slicesPerBatch()) flush();
        }

        void flush() {
            if (vertices != null) buffers.endBatch(VOLUME);
            vertices = null;
            slices = 0;
        }
    }

    /**
     * MINECRAFT-399/432: per-frame sun/moon tint inputs. The weights depend only on the camera's
     * fixed look direction and the frame's sun angle/visibility/strength, never on which material
     * or slice is being drawn -- so they are computed exactly ONCE per frame (in
     * {@link #celestialFrame}) rather than once per material (previously recomputed on every
     * {@code apply()} call, i.e. every slice). {@code apply()} itself is left doing only the
     * (cheap, material-specific) blend.
     */
    private record CelestialFrame(double sunWeight, double moonWeight, float[] sunColor, float[] moonColor) {
        float[] apply(float[] base) {
            // MINECRAFT-432: this is the only safe place to "skip tint calculations when
            // configured strength is zero" (celestialTintMaxStrength <= 0 forces both weights to
            // 0 via CelestialTint#weight's clampedStrength factor, so blend() would be a provable
            // no-op) -- NOT by short-circuiting celestialFrame's own sampling/smoothing above.
            // Skipping the sampling/state update there instead would freeze
            // sunVisibilityState/moonVisibilityState while disabled; a changed occlusion/weather
            // during that freeze is lost, so re-enabling replays stale visibility through
            // VISIBILITY_SMOOTHING_STEP instead of tracking what actually changed (caught in
            // review). Skipping only the blend here, after sampling/state always ran, has no such
            // risk: it changes nothing about what gets stored, only avoids recomputing a result
            // already known to equal base.
            if (sunWeight <= 0 && moonWeight <= 0) return base;
            return CelestialTint.blend(base, sunColor, sunWeight, moonColor, moonWeight);
        }
    }

    /** Soft horizon band half-width, in direction-Y units (see {@link CelestialTint#horizonWeight}). */
    private static final double HORIZON_SOFTEN = 0.06;
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
    /** Per-frame bound on how far {@link CelestialTint#smoothTowards} can move the SMOOTHED
     *  sun/moon visibility toward each frame's raw (discrete, ray-count-limited) sample: at this
     *  rate, fully crossing from 0 to 1 takes about a dozen frames (under a quarter second at 60
     *  FPS) -- enough to visibly smooth the few discrete values the ray bundle can produce into a
     *  continuous-looking transition, without reading as sluggish or lagging real occlusion changes. */
    private static final double VISIBILITY_SMOOTHING_STEP = 0.08;

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
     * bundle of rays toward the body (see {@link CelestialTint#jitterRing}); each ray is walked
     * with a 3D grid (voxel) traversal that visits every block cell it passes through, not fixed
     * world-space sample points -- a point-sampled ray can step clean over a wall only one block
     * thick if the step size happens to land on either side of it, which a cell-by-cell traversal
     * cannot do, since every boundary the ray crosses is itself a visited cell. The fraction of
     * rays that reach {@link #OCCLUSION_RAY_MAX_DISTANCE} unblocked is this frame's RAW occlusion
     * sample; because the ray bundle only has {@code OCCLUSION_JITTER_COUNT + 1} rays, that raw
     * sample can only take a handful of discrete values (fifths, with the current count) and is
     * NOT itself a continuous signal. {@link CelestialTint#smoothTowards} turns it into one:
     * {@link #sunVisibilityState}/{@link #moonVisibilityState} persist across frames and move
     * toward each new raw sample by at most {@link #VISIBILITY_SMOOTHING_STEP} per frame, so the
     * camera crossing a cover edge produces a multi-frame fade in the rendered tint rather than a
     * jump straight to the next discrete sample. A ray walking outward from the camera stops,
     * treating the remainder of that ray as unblocked (the safe default, not a claim of
     * visibility it cannot verify), the moment it either leaves the loaded world
     * ({@code Level#isLoaded}, never forcing a chunk to load) or exits the world's build height --
     * a real body is effectively at infinity, so nothing beyond the bounded probe distance can be
     * checked either way.
     */
    private static CelestialFrame celestialFrame(RenderLevelStageEvent event, ClientLevel level,
                                                  AtmosphereVolumeGeometry.Point forward,
                                                  io.github.brooswitminecraft.dynamicatmosphere.DynamicAtmosphereClientConfig.Snapshot config) {
        double maxStrength = config.celestialTintMaxStrength();
        float[] sunColor = unpack(config.sunTintColor());
        float[] moonColor = unpack(config.moonTintColor());
        // MINECRAFT-432: sampling/smoothing below runs EVERY frame, strength 0 included -- see
        // CelestialFrame#apply for where strength-0 tint math is actually (and safely) skipped,
        // and why skipping it HERE instead would corrupt resumed visibility state (caught in
        // review): an occlusion/weather change while strength is 0 would otherwise go unsampled
        // and be lost, so re-enabling the tint would replay stale brightness/darkness through
        // VISIBILITY_SMOOTHING_STEP instead of reflecting what actually changed while disabled.
        double len = Math.sqrt(forward.dot(forward));
        var look = len > 0 && Double.isFinite(len)
            ? new CelestialTint.Vector(forward.x() / len, forward.y() / len, forward.z() / len)
            : new CelestialTint.Vector(0, 0, 0);
        boolean rendersSunMoon = level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        CelestialTint.Vector sunDir = new CelestialTint.Vector(0, 0, 0);
        CelestialTint.Vector moonDir = new CelestialTint.Vector(0, 0, 0);
        double sunRaw = 0;
        double moonRaw = 0;
        if (rendersSunMoon) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            double sunAngle = level.getSunAngle(partialTick);
            sunDir = CelestialTint.sunDirection(sunAngle);
            moonDir = CelestialTint.moonDirection(sunAngle);
            double rainFade = 1 - Math.clamp((double) level.getRainLevel(partialTick), 0, 1);
            var eye = event.getCamera().getPosition();
            double sunHorizon = CelestialTint.horizonWeight(sunDir.y(), HORIZON_SOFTEN);
            double moonHorizon = CelestialTint.horizonWeight(moonDir.y(), HORIZON_SOFTEN);
            sunRaw = sunHorizon > 0 ? sunHorizon * rainFade * bodyOcclusionVisibility(level, eye, sunDir) : 0;
            moonRaw = moonHorizon > 0 ? moonHorizon * rainFade * bodyOcclusionVisibility(level, eye, moonDir) : 0;
        }
        sunVisibilityState = CelestialTint.smoothTowards(sunVisibilityState, sunRaw, VISIBILITY_SMOOTHING_STEP);
        moonVisibilityState = CelestialTint.smoothTowards(moonVisibilityState, moonRaw, VISIBILITY_SMOOTHING_STEP);
        // MINECRAFT-432: computed once here, per frame -- shared by every material's apply()
        // call below instead of each one recomputing the same weight.
        double sunWeight = CelestialTint.weight(look, sunDir, sunVisibilityState, maxStrength);
        double moonWeight = CelestialTint.weight(look, moonDir, moonVisibilityState, maxStrength);
        return new CelestialFrame(sunWeight, moonWeight, sunColor, moonColor);
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

    /**
     * Amanatides-Woo 3D grid traversal: visits every voxel the ray from {@code eye} along
     * {@code ray} passes through, in order, up to {@link #OCCLUSION_RAY_MAX_DISTANCE} -- unlike
     * fixed-distance point sampling, this cannot skip over a wall thinner than a sample step,
     * since every cell boundary the ray crosses is itself one of the visited voxels.
     */
    private static boolean rayObstructed(ClientLevel level, net.minecraft.world.phys.Vec3 eye, CelestialTint.Vector ray) {
        int x = (int) Math.floor(eye.x), y = (int) Math.floor(eye.y), z = (int) Math.floor(eye.z);
        int stepX = Double.compare(ray.x(), 0), stepY = Double.compare(ray.y(), 0), stepZ = Double.compare(ray.z(), 0);
        double tDeltaX = ray.x() != 0 ? Math.abs(1 / ray.x()) : Double.POSITIVE_INFINITY;
        double tDeltaY = ray.y() != 0 ? Math.abs(1 / ray.y()) : Double.POSITIVE_INFINITY;
        double tDeltaZ = ray.z() != 0 ? Math.abs(1 / ray.z()) : Double.POSITIVE_INFINITY;
        double tMaxX = ray.x() != 0 ? ((stepX > 0 ? x + 1 - eye.x : eye.x - x) / Math.abs(ray.x())) : Double.POSITIVE_INFINITY;
        double tMaxY = ray.y() != 0 ? ((stepY > 0 ? y + 1 - eye.y : eye.y - y) / Math.abs(ray.y())) : Double.POSITIVE_INFINITY;
        double tMaxZ = ray.z() != 0 ? ((stepZ > 0 ? z + 1 - eye.z : eye.z - z) / Math.abs(ray.z())) : Double.POSITIVE_INFINITY;
        var pos = new BlockPos.MutableBlockPos();
        for (double t = 0; t <= OCCLUSION_RAY_MAX_DISTANCE; ) {
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) return false;
            if (level.getBlockState(pos).getLightBlock(level, pos) >= 15) return true;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { x += stepX; t = tMaxX; tMaxX += tDeltaX; }
            else if (tMaxY < tMaxZ) { y += stepY; t = tMaxY; tMaxY += tDeltaY; }
            else { z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; }
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
        sunVisibilityState = 0;
        moonVisibilityState = 0;
    }

    public static int renderedCellCount() { return renderedCellCount; }
    public static int renderedSliceCount() { return renderedSliceCount; }
    public static int renderedCoarseCount() { return renderedCoarseCount; }

    private AtmosphereVolumeRenderer() {
        super("dynamicatmosphere_volume", () -> { }, () -> { });
    }
}
