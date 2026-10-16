package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Phase 4 (design brief section 32): "A Transfer Sink consumes material locally but does
 * not destroy it: it re-emits it at the linked destination." Built entirely on the existing
 * generic material grid's public emit/consume boundary ({@link DynamicAtmosphereMod}), so no
 * scenario-specific Java logic is needed for either leg:
 *
 * <ul>
 *   <li><b>Nether portals</b> (section 33): Nether Gas crosses between the Nether and the
 *       Overworld using the vanilla 8:1 coordinate scale. Smoke is also named in section 33's
 *       example, but Smoke uses a separate legacy grid without a public cross-grid consume API;
 *       extending that grid is left to a follow-up rather than risked here.</li>
 *   <li><b>End portals</b> (section 34/35): every generic-grid material near an End portal
 *       drains into The End, where {@link DimensionAtmosphereConfigs}' global dissipation
 *       multiplier makes it vanish almost immediately. "This is not a special industrial
 *       feature. It is an emergent use of normal atmospheric rules" (section 48).</li>
 * </ul>
 *
 * <p><b>Performance (section 50's "ONE THING TO WATCH"):</b> a portal links two independently
 * simulated regions, and a naive implementation would force both to stay active. This sink
 * never forces a load: {@link DynamicAtmosphereMod#emitMaterial} already only succeeds against
 * an already-loaded, already-tracked destination chunk, so an unreachable destination simply
 * fails the emit for this pass. The source is only debited after a successful emit, so a
 * skipped transfer never destroys material -- it is retried on a later producer pass instead.</p>
 */
public final class PortalTransferSink {
    static final int NETHER_PORTAL_COORDINATE_SCALE = 8;

    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        int throughput = DynamicAtmosphereServerConfig.snapshot().portalTransferSink().throughputPerPortalBlock();
        if (throughput <= 0) return;
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            boolean hasNetherPortal = section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL));
            boolean hasEndPortal = section.maybeHas(state -> state.is(Blocks.END_PORTAL));
            if (!hasNetherPortal && !hasEndPortal) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                var state = section.getBlockState(x, y, z);
                var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                if (hasNetherPortal && state.is(Blocks.NETHER_PORTAL)) {
                    transferNetherPortal(server, level, pos, throughput);
                }
                if (hasEndPortal && state.is(Blocks.END_PORTAL)) {
                    transferEndPortal(server, level, pos, throughput);
                }
            }
        }
    }

    /** Pure, so the vanilla 8:1 Nether/Overworld coordinate scale is unit-testable without a live level. */
    static BlockPos netherToOverworld(BlockPos pos) {
        return new BlockPos(pos.getX() * NETHER_PORTAL_COORDINATE_SCALE, pos.getY(),
            pos.getZ() * NETHER_PORTAL_COORDINATE_SCALE);
    }

    static BlockPos overworldToNether(BlockPos pos) {
        return new BlockPos(pos.getX() / NETHER_PORTAL_COORDINATE_SCALE, pos.getY(),
            pos.getZ() / NETHER_PORTAL_COORDINATE_SCALE);
    }

    private void transferNetherPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput) {
        ResourceKey<Level> destinationDimension;
        BlockPos destinationPos;
        if (level.dimension().equals(Level.NETHER)) {
            destinationDimension = Level.OVERWORLD;
            destinationPos = netherToOverworld(pos);
        } else if (level.dimension().equals(Level.OVERWORLD)) {
            destinationDimension = Level.NETHER;
            destinationPos = overworldToNether(pos);
        } else {
            return;
        }
        transfer(server, level, pos, destinationDimension, destinationPos, AtmosphereMaterial.NETHER_GAS, throughput);
    }

    private void transferEndPortal(MinecraftServer server, ServerLevel level, BlockPos pos, int throughput) {
        if (level.dimension().equals(Level.END)) return;
        for (AtmosphereMaterial material : AtmosphereMaterial.values()) {
            transfer(server, level, pos, Level.END, pos, material, throughput);
        }
    }

    private void transfer(
        MinecraftServer server, ServerLevel sourceLevel, BlockPos sourcePos,
        ResourceKey<Level> destinationDimension, BlockPos destinationPos,
        AtmosphereMaterial material, int throughput
    ) {
        var sourceState = DynamicAtmosphereMod.materialState(sourceLevel, material, sourcePos);
        if (sourceState.isEmpty() || sourceState.get().amount() <= 0) return;
        int amount = Math.min(throughput, sourceState.get().amount());
        ServerLevel destinationLevel = server.getLevel(destinationDimension);
        if (destinationLevel == null) return;
        if (!DynamicAtmosphereMod.emitMaterial(destinationLevel, material, destinationPos, amount)) return;
        DynamicAtmosphereMod.consumeMaterial(sourceLevel, material, sourcePos, amount);
    }

    private PortalTransferSink() { }

    public static PortalTransferSink create() { return new PortalTransferSink(); }
}
