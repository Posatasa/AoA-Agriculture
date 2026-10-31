package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.item.SauceItem;
import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Chocolate sauces: one per chocolate block Advent of Ascension adds, eaten from a bowl.
 *
 * <p>Four hunger and a saturation modifier of 0.8, in a stack of one, and the bowl comes back - the
 * same shape as mushroom stew, which is also where the recipes take their bowl from.
 */
public final class Sauces {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** Four hunger, saturation 0.8: a small, rich bowl. */
    private static final Food SAUCE =
            new Food.Builder().nutrition(4).saturationMod(0.8F).build();

    public static final RegistryObject<Item> CHOCOLATE = sauce("chocolate_sauce");
    public static final RegistryObject<Item> DARK = sauce("dark_chocolate_sauce");
    public static final RegistryObject<Item> WHITE = sauce("white_chocolate_sauce");

    private static RegistryObject<Item> sauce(String name) {
        return ITEMS.register(name, () -> new SauceItem(new Item.Properties()
                .tab(Tabs.FOOD)
                .stacksTo(1)
                .craftRemainder(Items.BOWL)
                .food(SAUCE)));
    }

    /** The three sauces, in the order their blocks are listed. */
    static Item[] all() {
        return new Item[]{CHOCOLATE.get(), DARK.get(), WHITE.get()};
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private Sauces() {}
}
