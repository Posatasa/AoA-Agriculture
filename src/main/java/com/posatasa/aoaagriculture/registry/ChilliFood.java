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
 * Chilli food: the paste made from chilli water, and the two things it goes on.
 *
 * <p>The paste is the only one that burns: it is chilli water thickened, so eating it feeds nothing and
 * sets the eater alight for a second, and the bowl comes back. The steak and the bread are ordinary food
 * - ten hunger and eight, both with 0.8 saturation - and do not burn, because the chilli is cooked into
 * them.
 */
public final class ChilliFood {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    public static final RegistryObject<Item> SAUCE = ITEMS.register("chilli_sauce",
            () -> new SauceItem(new Item.Properties()
                    .tab(Tabs.FOOD)
                    .stacksTo(16)
                    .craftRemainder(Items.BOWL)
                    .food(new Food.Builder().nutrition(0).saturationMod(0.0F).alwaysEat().build()),
                    true));

    public static final RegistryObject<Item> STEAK = ITEMS.register("spicy_steak",
            () -> new Item(new Item.Properties()
                    .tab(Tabs.MEALS)
                    .food(new Food.Builder().nutrition(10).saturationMod(0.8F).build())));

    public static final RegistryObject<Item> BREAD = ITEMS.register("chilli_sauce_bread",
            () -> new Item(new Item.Properties()
                    .tab(Tabs.MEALS)
                    .food(new Food.Builder().nutrition(8).saturationMod(0.8F).build())));

    /** The three, in the order the food tab lists them. */
    static Item[] all() {
        return new Item[]{SAUCE.get(), STEAK.get(), BREAD.get()};
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private ChilliFood() {}
}
