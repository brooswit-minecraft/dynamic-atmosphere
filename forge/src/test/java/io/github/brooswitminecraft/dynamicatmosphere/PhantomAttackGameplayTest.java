package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhantomAttackGameplayTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyThePhantomEntityTypeIsRecognizedAsAPhantomAttack() {
        assertTrue(PhantomAttackGameplay.isPhantomType(EntityType.PHANTOM));
        assertFalse(PhantomAttackGameplay.isPhantomType(EntityType.ZOMBIE));
        assertFalse(PhantomAttackGameplay.isPhantomType(EntityType.ENDERMAN));
    }

    @Test
    void anAttackWithNoResponsibleEntityIsNotTreatedAsAPhantomAttack() {
        // e.g. fall damage or fire damage sources carry no causing entity at all; such a
        // null-entity damage source must not be misread as a Phantom attack.
        assertFalse(PhantomAttackGameplay.isPhantomAttack(null));
    }

    @Test
    void phantomDamageIsZeroedButOtherMobsKeepTheirFullPostReductionDamage() {
        assertEquals(0f, PhantomAttackGameplay.zeroedDamage(EntityType.PHANTOM, 6.5f));
        assertEquals(0f, PhantomAttackGameplay.zeroedDamage(EntityType.PHANTOM, 0f));
        assertEquals(6.5f, PhantomAttackGameplay.zeroedDamage(EntityType.ZOMBIE, 6.5f));
    }
}
