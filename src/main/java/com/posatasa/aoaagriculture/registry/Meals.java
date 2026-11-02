package com.posatasa.aoaagriculture.registry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * The food tab: the cakes, the slices, and the two dishes made with chilli paste.
 *
 * <p>Separate from the ingredients tab, which holds what goes into a recipe - the bottles and the four
 * sauces. Everything here is something you eat by itself.
 */
public final class Meals {
    /** The icon for the food tab: a cake. */
    static ItemStack iconStack() {
        return new ItemStack(Cakes.CHOCOLATE.get().asItem());
    }

    static void fillTab(NonNullList<ItemStack> items) {
        for (Item cake : Cakes.all()) {
            items.add(new ItemStack(cake));
        }
        for (Item slice : Slices.all()) {
            items.add(new ItemStack(slice));
        }
        for (Item dish : ChilliFood.dishes()) {
            items.add(new ItemStack(dish));
        }
        Tabs.complete(Tabs.MEALS, items);
    }

    private Meals() {}
}
