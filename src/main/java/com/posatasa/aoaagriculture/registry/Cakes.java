package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.block.ChocolateCakeBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Three chocolate cakes, one per sauce: a vanilla cake with a chocolate one poured over it.
 *
 * <p>The blocks are built with vanilla cake's own properties, so their states, hardness, sound and
 * shape are vanilla's; only the slice's food is doubled, in {@link ChocolateCakeBlock}.
 */
public final class Cakes {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FarmlandBlocks.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** Vanilla cake's own properties, so nothing about the block differs from it. */
    private static final AbstractBlock.Properties PROPERTIES =
            AbstractBlock.Properties.of(Material.CAKE).strength(0.5F).sound(SoundType.WOOL);

    public static final RegistryObject<Block> CHOCOLATE = cake("chocolate_cake");
    public static final RegistryObject<Block> DARK = cake("dark_chocolate_cake");
    public static final RegistryObject<Block> WHITE = cake("white_chocolate_cake");

    private static RegistryObject<Block> cake(String name) {
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new ChocolateCakeBlock(PROPERTIES));
        ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().tab(Tabs.FOOD)));
        return block;
    }

    /** The three cakes, in the order their sauces are listed. */
    static Item[] all() {
        return new Item[]{CHOCOLATE.get().asItem(), DARK.get().asItem(), WHITE.get().asItem()};
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private Cakes() {}
}
