package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.block.ChocolateCakeBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The chocolate cakes, and the honey cakes made by pouring honey over a cake.
 *
 * <p>Every one of them is vanilla's cake block: the same properties, the same states, the same bites. A
 * chocolate cake feeds twice what vanilla's does a slice; a honey cake feeds exactly what the cake it was
 * made from fed, because honey is a topping rather than a second helping.
 */
public final class Cakes {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FarmlandBlocks.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** Vanilla cake's own properties, so nothing about these blocks differs from it. */
    private static final AbstractBlock.Properties PROPERTIES =
            AbstractBlock.Properties.of(Material.CAKE).strength(0.5F).sound(SoundType.WOOL);

    /** Vanilla cake's numbers, which are also what a honey cake feeds. */
    public static final int PLAIN_HUNGER = 2;
    public static final float PLAIN_SATURATION = 0.1F;
    /** A chocolate cake feeds twice a vanilla slice. */
    public static final int CHOCOLATE_HUNGER = 4;
    public static final float CHOCOLATE_SATURATION = 0.2F;

    public static final RegistryObject<Block> CHOCOLATE = cake("chocolate_cake", CHOCOLATE_HUNGER);
    public static final RegistryObject<Block> DARK = cake("dark_chocolate_cake", CHOCOLATE_HUNGER);
    public static final RegistryObject<Block> WHITE = cake("white_chocolate_cake", CHOCOLATE_HUNGER);
    public static final RegistryObject<Block> HONEY = cake("honey_cake", PLAIN_HUNGER);
    public static final RegistryObject<Block> HONEY_CHOCOLATE =
            cake("honey_chocolate_cake", CHOCOLATE_HUNGER);
    public static final RegistryObject<Block> HONEY_DARK =
            cake("honey_dark_chocolate_cake", CHOCOLATE_HUNGER);
    public static final RegistryObject<Block> HONEY_WHITE =
            cake("honey_white_chocolate_cake", CHOCOLATE_HUNGER);

    /** The slice of a vanilla cake is not a block of ours, so it is registered on its own. */
    private static void vanillaSlice() {
        Slices.register("cake_slice", PLAIN_HUNGER, PLAIN_SATURATION);
    }

    private static RegistryObject<Block> cake(String name, int hunger) {
        float saturation = hunger == PLAIN_HUNGER ? PLAIN_SATURATION : CHOCOLATE_SATURATION;
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new ChocolateCakeBlock(PROPERTIES, hunger, saturation));
        ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().tab(Tabs.FOOD)));
        Slices.register(name + "_slice", hunger, saturation);
        return block;
    }

    /** Every cake this mod adds, in the order the tab lists them. */
    static Item[] all() {
        return new Item[]{CHOCOLATE.get().asItem(), DARK.get().asItem(), WHITE.get().asItem(),
                HONEY.get().asItem(), HONEY_CHOCOLATE.get().asItem(), HONEY_DARK.get().asItem(),
                HONEY_WHITE.get().asItem()};
    }

    public static void register(IEventBus modBus) {
        // the cakes ask Slices for a slice each while the class loads, so vanilla's is asked for first
        vanillaSlice();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        Slices.register(modBus);
    }

    private Cakes() {}
}
