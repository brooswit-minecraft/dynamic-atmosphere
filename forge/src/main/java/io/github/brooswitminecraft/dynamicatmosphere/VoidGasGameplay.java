package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Loaded-only Void Gas producers and cell effects. The passive callbacks are
 * intended for the existing bounded producer schedule; this class never walks
 * the loaded world or requests a chunk.
 *
 * <p>Conservative MVP defaults: hostile deaths emit 40, sampled Netherrack
 * emits 2, and the independent bottom-of-world 1/8 check emits 8. Above 75%
 * fullness, one 1/32 roll may create one zombie for 25% of cell capacity, with
 * at most eight vanilla-checked positions. Between 10% and 25% fullness, one
 * existing eligible villager can receive three bread and become willing at a
 * cost of 5% of the cell's current amount. Neither path creates replacement
 * villagers or forces a breeding partner, bed, or successful birth.</p>
 */
public final class VoidGasGameplay {
    static final int HOSTILE_DEATH_AMOUNT = 40;
    static final int NETHERRACK_AMOUNT = 2;
    static final int BOTTOM_AMOUNT = 8;
    static final int BOTTOM_CHANCE_DENOMINATOR = 8;
    static final int HIGH_SPAWN_CHANCE_DENOMINATOR = 32;
    static final int SPAWN_ATTEMPTS = 8;
    static final int MAX_VILLAGER_CANDIDATES = 64;
    static final int BREEDING_BREAD = 3;

    record CellEffect(boolean villagerBand, boolean spawnZombie, int cost) {
        static final CellEffect NONE = new CellEffect(false, false, 0);
    }

    static boolean isHostileCategory(MobCategory category) {
        return category == MobCategory.MONSTER;
    }

    static boolean bottomEmission(int roll) {
        return roll == 0;
    }

    static CellEffect effectFor(int amount, int capacity, int spawnRoll) {
        if (amount <= 0 || capacity <= 0) return CellEffect.NONE;
        long scaled = (long) amount * 100;
        if (scaled >= (long) capacity * 10 && scaled <= (long) capacity * 25) {
            return new CellEffect(true, false, Math.max(1, (int) (((long) amount * 5 + 99) / 100)));
        }
        if ((long) amount * 4 >= (long) capacity * 3 && spawnRoll == 0) {
            return new CellEffect(false, true, Math.max(1, (capacity + 3) / 4));
        }
        return CellEffect.NONE;
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !isHostileCategory(event.getEntity().getType().getCategory())
            || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.VOID_GAS,
            event.getEntity().blockPosition(),
            DynamicAtmosphereServerConfig.snapshot().voidGas().hostileDeathEmission());
    }

    /** Called for a position already selected inside a loaded chunk. */
    public void onPassiveBlockSample(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.NETHERRACK)) {
            DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.VOID_GAS, pos,
                DynamicAtmosphereServerConfig.snapshot().voidGas().netherrackEmission());
        }
    }

    /** Called once per loaded chunk selected by the bounded producer schedule. */
    public void onLoadedChunkProducerCheck(ServerLevel level, LevelChunk chunk) {
        DynamicAtmosphereServerConfig.VoidGas config = DynamicAtmosphereServerConfig.snapshot().voidGas();
        if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk
            || !bottomEmission(level.random.nextInt(config.bottomChanceDenominator()))) {
            return;
        }
        int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
        DynamicAtmosphereMod.emitMaterial(level, AtmosphereMaterial.VOID_GAS,
            new BlockPos(x, level.getMinBuildHeight(), z), config.bottomEmission());
    }

    /** Called once for a processed Void Gas cell whose origin is already loaded. */
    public void onProcessedCell(ServerLevel level, BlockPos cellOrigin) {
        DynamicAtmosphereServerConfig.VoidGas config = DynamicAtmosphereServerConfig.snapshot().voidGas();
        DynamicAtmosphereMod.materialState(level, AtmosphereMaterial.VOID_GAS, cellOrigin).ifPresent(state -> {
            CellEffect effect = effectFor(state.amount(), state.capacity(),
                level.random.nextInt(config.spawnChanceDenominator()));
            if (effect.villagerBand()) {
                readyOneVillager(level, cellOrigin, effect.cost(), config.breedingBread());
            } else if (effect.spawnZombie()) {
                spawnOne(level, cellOrigin, effect.cost(), config.spawnAttempts());
            }
        });
    }

    private static void readyOneVillager(ServerLevel level, BlockPos cellOrigin, int cost, int breedingBread) {
        AABB bounds = cellBounds(cellOrigin, AtmosphereMaterial.VOID_GAS.cellSize());
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, bounds,
            candidate -> bounds.contains(candidate.position()) && eligibleVillager(candidate, breedingBread));
        int checked = 0;
        for (Villager villager : villagers) {
            if (checked++ >= MAX_VILLAGER_CANDIDATES) break;
            if (!DynamicAtmosphereMod.consumeMaterial(level, AtmosphereMaterial.VOID_GAS, cellOrigin, cost)) return;
            villager.getInventory().addItem(new ItemStack(Items.BREAD, breedingBread));
            return;
        }
    }

    static boolean eligibleVillager(Villager villager) {
        return eligibleVillager(villager, BREEDING_BREAD);
    }

    static boolean eligibleVillager(Villager villager, int breedingBread) {
        if (!villager.isAlive() || villager.getAge() != 0 || villager.isSleeping() || villager.canBreed()) return false;
        int room = 0;
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (stack.isEmpty()) return true;
            if (stack.is(Items.BREAD)) room += stack.getMaxStackSize() - stack.getCount();
            if (room >= breedingBread) return true;
        }
        return false;
    }

    private static void spawnOne(ServerLevel level, BlockPos cellOrigin, int cost, int attempts) {
        BlockPos pos = findSpawnPosition(
            level, cellOrigin, AtmosphereMaterial.VOID_GAS.cellSize(), EntityType.ZOMBIE, attempts);
        if (pos == null || !DynamicAtmosphereMod.consumeMaterial(
            level, AtmosphereMaterial.VOID_GAS, cellOrigin, cost)) return;
        EntityType.ZOMBIE.spawn(level, pos, MobSpawnType.NATURAL);
    }

    private static BlockPos findSpawnPosition(
        ServerLevel level, BlockPos origin, int size, EntityType<?> type, int attempts
    ) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos pos = origin.offset(level.random.nextInt(size), level.random.nextInt(size), level.random.nextInt(size));
            if (level.isOutsideBuildHeight(pos)
                || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !SpawnPlacements.isSpawnPositionOk(type, level, pos)
                || !checkSpawnRules(type, level, pos)) continue;
            return pos;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean checkSpawnRules(EntityType<?> type, ServerLevel level, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules((EntityType) type, level, MobSpawnType.NATURAL, pos, level.random);
    }

    private static AABB cellBounds(BlockPos origin, int size) {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + size, origin.getY() + size, origin.getZ() + size);
    }

    private VoidGasGameplay() { }

    public static VoidGasGameplay create() { return new VoidGasGameplay(); }
}
