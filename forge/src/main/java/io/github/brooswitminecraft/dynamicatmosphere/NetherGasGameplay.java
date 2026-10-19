package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Loaded-only Nether Gas producer and cell effects (Phase 4, design brief section 48): mild
 * negative potion effects at lower concentration, increasingly dangerous effects at high
 * concentration. Movement and dissipation reuse the existing generic heavy-gas rules
 * ({@code ForgeAtmospherePrototype#dissipateHeavyGas}); this class only supplies the passive
 * Netherrack source and the entity effect curve.
 */
public final class NetherGasGameplay {
    static final int MILD_AMPLIFIER = 0;
    static final int DANGEROUS_AMPLIFIER = 0;
    static final int EFFECT_DURATION_TICKS = 100;

    record CellEffect(boolean mild, boolean dangerous) {
        static final CellEffect NONE = new CellEffect(false, false);

        boolean active() { return mild || dangerous; }
    }

    static CellEffect effectFor(int amount, int capacity, double mildFullness, double dangerousFullness) {
        if (amount <= 0 || capacity <= 0) return CellEffect.NONE;
        double fullness = (double) amount / capacity;
        boolean mild = fullness >= mildFullness;
        boolean dangerous = fullness >= dangerousFullness;
        return mild || dangerous ? new CellEffect(mild, dangerous) : CellEffect.NONE;
    }

    /** Called for a position already selected inside a loaded chunk. */
    public void onPassiveBlockSample(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.NETHERRACK)) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.NETHER_GAS, pos,
                DynamicAtmosphereServerConfig.snapshot().netherGas().netherrackEmission());
        }
    }

    /** Called once per loaded chunk selected by the bounded producer schedule. */
    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
        int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
        int y = level.getMinBuildHeight() + level.random.nextInt(level.getHeight());
        BlockPos pos = new BlockPos(x, y, z);
        onPassiveBlockSample(level, pos, chunk.getBlockState(pos));
    }

    /**
     * Called once for a processed Nether Gas cell. The entity supplier is only invoked when the
     * cell's amount/capacity make an effect eligible, so inactive cells never issue the
     * underlying living-entity spatial query.
     */
    public void processTurn(
        ServerLevel level, BlockPos cellOrigin, Supplier<Iterable<? extends LivingEntity>> entitiesSupplier
    ) {
        DynamicAtmosphereServerConfig.NetherGas config = DynamicAtmosphereServerConfig.snapshot().netherGas();
        DynamicAtmosphereMod.materialState(level, AtmosphereMaterial.NETHER_GAS, cellOrigin).ifPresent(state -> {
            CellEffect effect = effectFor(state.amount(), state.capacity(), config.mildFullness(), config.dangerousFullness());
            if (!effect.active()) return;
            for (LivingEntity entity : entitiesSupplier.get()) {
                if (!entity.isAlive()) continue;
                if (effect.mild()) {
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_DURATION_TICKS, MILD_AMPLIFIER));
                }
                if (effect.dangerous()) {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, EFFECT_DURATION_TICKS, DANGEROUS_AMPLIFIER));
                }
            }
        });
    }

    private NetherGasGameplay() { }

    public static NetherGasGameplay create() { return new NetherGasGameplay(); }
}
