package com.posatasa.aoaagriculture.registry;

import net.minecraft.item.Food;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A slice of cake: the serving a cake gives, eaten from the hand.
 *
 * <p>Registering is done by {@link Cakes} as it creates each cake, so a slice exists for every cake this
 * mod adds and always feeds exactly what a serving of that cake feeds.
 */
public final class Slices {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** Seven per cake, which is what a cake has: the whole cake plus six bites. */
    public static final int PER_CAKE = 7;

    private static final java.util.List<RegistryObject<Item>> ALL = new java.util.ArrayList<>();

    static void register(String name, int hunger, float saturation) {
        Food food = new Food.Builder().nutrition(hunger).saturationMod(saturation).build();
        ALL.add(ITEMS.register(name, () -> new Item(new Item.Properties()
                .tab(Tabs.FOOD)
                .food(food))));
    }

    /** Every slice, in the order the cakes were registered. */
    static Item[] all() {
        Item[] slices = new Item[ALL.size()];
        for (int i = 0; i < slices.length; i++) {
            slices[i] = ALL.get(i).get();
        }
        return slices;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private Slices() {}
}
