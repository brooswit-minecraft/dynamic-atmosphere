package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Uses vanilla in-place profession mutation. Profession changes invalidate offers; trades are not preserved. */
public final class ForgeSmokeVillagerEffect {
    public static boolean eligible(Villager villager) {
        return villager.isAlive() && villager.level() instanceof ServerLevel
            && villager.getVillagerData().getProfession() != VillagerProfession.NITWIT;
    }

    public static boolean convert(Villager villager) {
        if (!eligible(villager)) return false;
        ServerLevel level = (ServerLevel) villager.level();
        if (!level.getServer().isSameThread()) return false;
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
        villager.refreshBrain(level);
        return villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    private ForgeSmokeVillagerEffect() { }
}
