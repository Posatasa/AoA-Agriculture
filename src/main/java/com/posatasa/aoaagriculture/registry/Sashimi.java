package com.posatasa.aoaagriculture.registry;

import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sashimi: one raw fish, cut into four slices.
 *
 * <p>There is one slice item per fish AoA adds that can be eaten raw - twenty-five of them. Each is a
 * plain food of two hunger and 0.5 saturation, and none of them cooks into anything yet, so they are all
 * eaten as they come off the cutting board. A fish is cut into four of its slices, by hand or on Farmer's
 * Delight's cutting board.
 */
public final class Sashimi {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** The AoA fish, in the order the pack lists them and the food tab shows them. */
    private static final String[] FISH = {
            "rainbowfish", "sailback", "violet_skipper", "crimson_skipper", "candlefish",
            "charred_char", "crimson_stripefish", "dark_hatchetfish", "chocaw", "parapiranha",
            "jamfish", "sapphire_strider", "ironback", "pearl_stripefish", "razorfish",
            "reeftooth", "rocketfish", "skelecanth", "turquoise_stripefish", "blue_gemtrap",
            "green_gemtrap", "purple_gemtrap", "red_gemtrap", "white_gemtrap", "yellow_gemtrap",
    };

    private static final java.util.List<RegistryObject<Item>> SLICES =
            new java.util.ArrayList<>();

    private static final Food FOOD = new Food.Builder().nutrition(2).saturationMod(0.5F).build();

    static {
        for (String fish : FISH) {
            SLICES.add(ITEMS.register(fish + "_slice", () -> new Item(new Item.Properties()
                    .tab(Tabs.MEALS)
                    .food(FOOD))));
        }
    }

    /** Every sashimi, in the order the tab lists them. */
    static Item[] all() {
        Item[] items = new Item[SLICES.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = SLICES.get(i).get();
        }
        return items;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private Sashimi() {}
}
