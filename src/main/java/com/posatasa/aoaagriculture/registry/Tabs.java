package com.posatasa.aoaagriculture.registry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This mod's three creative tabs: farmland, buckets, and food.
 *
 * <p>They live here rather than one per registry class because an item names its tab while it is
 * being registered, and a tab names an item for its icon - which in one class would be a circular
 * static initialiser. With the tabs in their own class both references are ordinary and ordered.
 */
public final class Tabs {
    /** The 22 farmland blocks, in soil order, plain then fertilised. */
    public static final ItemGroup FARMLAND = group("farmland", FarmlandBlocks::iconStack,
            FarmlandBlocks::fillTab);
    /** Every empty bucket and every variant, then the chilli water cauldron. */
    public static final ItemGroup BUCKETS = group("buckets", Buckets::iconStack, Buckets::fillTab);
    /** The bottles: the only edible things this mod has. */
    public static final ItemGroup FOOD = group("food", Bottles::iconStack, Bottles::fillTab);

    private static ItemGroup group(String name, Supplier<ItemStack> icon,
                                   Consumer<NonNullList<ItemStack>> filler) {
        return new ItemGroup(FarmlandBlocks.MOD_ID + "." + name) {
            @Override
            public ItemStack makeIcon() {
                return icon.get();
            }

            @Override
            public void fillItemList(NonNullList<ItemStack> items) {
                filler.accept(items);
            }
        };
    }

    /**
     * Adds anything that names {@code group} but is not already in {@code items}, so an item added
     * later cannot go missing from its tab by being left off a list.
     */
    static void complete(ItemGroup group, NonNullList<ItemStack> items) {
        Set<Item> placed = new HashSet<>();
        for (ItemStack stack : items) {
            placed.add(stack.getItem());
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (item.getItemCategory() == group && placed.add(item)) {
                items.add(new ItemStack(item));
            }
        }
    }

    private Tabs() {}
}
