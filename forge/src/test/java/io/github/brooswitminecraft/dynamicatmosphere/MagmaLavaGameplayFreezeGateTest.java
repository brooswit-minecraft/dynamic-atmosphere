package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MINECRAFT-384: sibling of {@link ForgeAtmospherePrototypeFreezeGateTest} -- the deferred lava
 * commit in {@code MagmaLavaGameplay.onServerTick} had the same freeze-gating defect: it runs on
 * every {@code ServerTickEvent.Post}, which still fires during platform {@code /tick freeze}.
 * Gating defers a pending commit to the next non-frozen tick instead of writing it into a frozen
 * world.
 */
class MagmaLavaGameplayFreezeGateTest {

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, Set<BlockPos>> pendingLavaOf(MagmaLavaGameplay gameplay) throws Exception {
        Field field = MagmaLavaGameplay.class.getDeclaredField("pendingLava");
        field.setAccessible(true);
        return (Map<ResourceKey<Level>, Set<BlockPos>>) field.get(gameplay);
    }

    @Test
    void frozenTickLeavesAPendingCommitUntouched() throws Exception {
        var gameplay = MagmaLavaGameplay.create();
        var pending = pendingLavaOf(gameplay);
        var positions = new HashSet<BlockPos>();
        positions.add(new BlockPos(1, 64, 1));
        pending.put(Level.OVERWORLD, positions);

        assertDoesNotThrow(() -> gameplay.tick(null, false),
            "a frozen tick must short-circuit before touching the server");
        assertEquals(Map.of(Level.OVERWORLD, positions), pendingLavaOf(gameplay),
            "a pending commit must survive a frozen tick untouched, to be committed on a later non-frozen tick");
    }

    @Test
    void nonFrozenTickWithPendingWorkActuallyAttemptsTheCommit() throws Exception {
        var gameplay = MagmaLavaGameplay.create();
        var pending = pendingLavaOf(gameplay);
        pending.put(Level.OVERWORLD, new HashSet<>(Set.of(new BlockPos(1, 64, 1))));

        // The inverse of the frozen case: once not frozen, a pending commit must actually be
        // attempted (and therefore dereference the null server here) -- proving the gate is an
        // early-return on "frozen", not a no-op that also swallows real, due work.
        assertThrows(NullPointerException.class, () -> gameplay.tick(null, true),
            "a non-frozen tick with pending work must attempt the commit, which uses the server");
    }

    @Test
    void nonFrozenTickWithNoPendingWorkStillShortCircuitsBeforeTouchingTheServer() {
        // With no pending lava, the production early-return ("if (pendingLava.isEmpty()) return;")
        // means a non-frozen tick is also safe to drive with a null server here -- this isolates
        // proof that the new freeze gate sits ahead of, and does not interfere with, that
        // pre-existing empty-case short circuit.
        var gameplay = MagmaLavaGameplay.create();
        assertDoesNotThrow(() -> gameplay.tick(null, true),
            "with nothing pending, a non-frozen tick must still short-circuit without touching the server");
    }
}
