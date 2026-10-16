package io.github.brooswitminecraft.dynamicatmosphere;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MINECRAFT-1 (B4): the mod's first registered block and item. ID {@code dynamicatmosphere:end_sand}
 * is FROZEN -- sickos pins it, so renaming it later is a breaking change.
 */
public final class DynamicAtmosphereRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DynamicAtmosphereMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DynamicAtmosphereMod.MODID);

    public static final DeferredBlock<EndSandBlock> END_SAND_BLOCK = BLOCKS.register("end_sand",
        key -> new EndSandBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .sound(SoundType.SAND)
            .strength(0.5F)));
    public static final DeferredItem<BlockItem> END_SAND_ITEM =
        ITEMS.registerSimpleBlockItem("end_sand", END_SAND_BLOCK, new Item.Properties());

    private DynamicAtmosphereRegistry() { }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
