package io.github.brooswitminecraft.dynamicatmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Loaded-only Void Gas producers and cell effects. The passive callbacks are
 * intended for the existing bounded producer schedule; this class never walks
 * the loaded world or requests a chunk.
 *
 * <p>Amplified defaults (ATMO-24 C1): hostile deaths emit 320, sampled
 * Netherrack emits 16, and the independent bottom-of-world check — now
 * unconditional (denominator 1) — emits 64. Between 10% and 25% fullness,
 * one existing eligible villager can receive three bread and become willing
 * at a cost of 5% of the cell's current amount. This does not create
 * replacement villagers or force a breeding partner, bed, or successful
 * birth.</p>
 *
 * <p>MINECRAFT-17 removed the consumption-driven zombie spawn that used to
 * fire above 75% fullness, along with its now-dead
 * {@code voidGas.spawnChanceDenominator}/{@code voidGas.spawnAttempts} config
 * keys: Void Gas is a hazard, not a spawner, per ATMO-22 comment 23227. A
 * sufficiently full Void Gas cell instead makes the position eligible for
 * surface spawning via {@link VaporHostileSpawnGate}, the same way a
 * more-than-half-full Vapor cell does.</p>
 */
public final class VoidGasGameplay {
    static final int HOSTILE_DEATH_AMOUNT = 320;
    static final int NETHERRACK_AMOUNT = 16;
    static final int BOTTOM_AMOUNT = 64;
    static final int BOTTOM_CHANCE_DENOMINATOR = 1;
    /** Only the first eligible villager is ever rewarded, so the bounded query only ever needs
     * to ask for one successful match -- not a larger pool that would go unused. */
    static final int VILLAGER_SELECTION_LIMIT = 1;
    static final int BREEDING_BREAD = 3;

    record CellEffect(boolean villagerBand, int cost) {
        static final CellEffect NONE = new CellEffect(false, 0);
    }

    static boolean isHostileCategory(MobCategory category) {
        return category == MobCategory.MONSTER;
    }

    static boolean bottomEmission(int roll) {
        return roll == 0;
    }

    static CellEffect effectFor(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return CellEffect.NONE;
        long scaled = (long) amount * 100;
        if (scaled >= (long) capacity * 10 && scaled <= (long) capacity * 25) {
            return new CellEffect(true, Math.max(1, (int) (((long) amount * 5 + 99) / 100)));
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
            CellEffect effect = effectFor(state.amount(), state.capacity());
            if (effect.villagerBand()) {
                readyOneVillager(level, cellOrigin, effect.cost(), config.breedingBread());
            }
        });
    }

    /**
     * Seam {@code candidates} is expected to already be eligibility-filtered and bounded (see
     * {@link #readyOneVillagerVia}). Only the first candidate is ever tried, {@code consume} is
     * invoked at most once (with {@code cost}), and a refused consumption grants no reward and
     * tries no further candidate -- matching the original loop, whose per-iteration cap was
     * unreachable dead code since every body path already returned on iteration 1.
     */
    static <T> boolean readyFirstEligible(int cost, IntPredicate consume, Iterable<? extends T> candidates, Consumer<? super T> reward) {
        for (T candidate : candidates) {
            if (!consume.test(cost)) return false;
            reward.accept(candidate);
            return true;
        }
        return false;
    }

    /**
     * Shared orchestration {@link #readyOneVillager} delegates to -- the exact same method a
     * test exercises -- so a regression in the real selection limit or eligibility predicate is
     * caught here rather than only in a disconnected test mirror. {@code boundedQuery} is the one
     * injected part (production wires the real {@code Level#getEntities} bounded call; a test
     * wires a deterministic in-memory stand-in with the same bound-matches-not-candidates
     * semantics), but the limit passed to it is always {@link #VILLAGER_SELECTION_LIMIT} -- never
     * independently chosen by the caller -- so this method, not the caller, is the single source
     * of truth for how many successful results are ever requested.
     */
    static <T> boolean readyOneVillagerVia(
        int cost, IntPredicate consume, Predicate<? super T> eligible, Consumer<? super T> reward,
        BiFunction<Predicate<? super T>, Integer, List<T>> boundedQuery
    ) {
        List<T> matches = boundedQuery.apply(eligible, VILLAGER_SELECTION_LIMIT);
        return readyFirstEligible(cost, consume, matches, reward);
    }

    /**
     * Test-only mirror of {@code Level#getEntities(type, bounds, predicate, output, maxResults)}'s
     * documented semantics (verified against its decompiled source): {@code maxResults} bounds
     * the number of candidates that PASS {@code predicate}, not the number of candidates visited
     * -- a run of non-matching candidates never consumes a result slot. Production never calls
     * this; it calls the real {@code Level#getEntities} inside {@link #readyOneVillager}. Used as
     * a test's {@code boundedQuery} implementation for {@link #readyOneVillagerVia} so a test can
     * inject more than {@link #VILLAGER_SELECTION_LIMIT} worth of ineligible candidates ahead of
     * an eligible one and prove the eligible one is still found.
     */
    static <T> List<T> boundedMatchesForTesting(Iterable<? extends T> candidates, Predicate<? super T> predicate, int maxResults) {
        List<T> output = new ArrayList<>();
        for (T candidate : candidates) {
            if (predicate.test(candidate)) {
                output.add(candidate);
                if (output.size() >= maxResults) break;
            }
        }
        return output;
    }

    private static void readyOneVillager(ServerLevel level, BlockPos cellOrigin, int cost, int breedingBread) {
        AABB bounds = cellBounds(cellOrigin, AtmosphereMaterial.VOID_GAS.cellSize());
        VoidGasGameplay.<Villager>readyOneVillagerVia(cost,
            candidateCost -> DynamicAtmosphereMod.consumeMaterial(level, AtmosphereMaterial.VOID_GAS, cellOrigin, candidateCost),
            villager -> eligibleVillager(villager, breedingBread),
            villager -> villager.getInventory().addItem(new ItemStack(Items.BREAD, breedingBread)),
            (eligible, limit) -> {
                // eligibleVillager is baked into the native predicate (not applied afterward) so
                // the limit bounds the count of ELIGIBLE matches, matching Level#getEntities' own
                // maxResults semantics -- bounding arbitrary in-bounds candidates instead could
                // exhaust the limit on ineligible villagers and miss an eligible one.
                List<Villager> villagers = new ArrayList<>(limit);
                level.getEntities(EntityTypeTest.forClass(Villager.class), bounds,
                    candidate -> bounds.contains(candidate.position()) && eligible.test(candidate), villagers, limit);
                return villagers;
            });
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

    private static AABB cellBounds(BlockPos origin, int size) {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + size, origin.getY() + size, origin.getZ() + size);
    }

    private VoidGasGameplay() { }

    public static VoidGasGameplay create() { return new VoidGasGameplay(); }
}
