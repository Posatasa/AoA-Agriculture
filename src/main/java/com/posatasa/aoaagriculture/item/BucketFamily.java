package com.posatasa.aoaagriculture.item;

import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The four buckets of one metal: empty, water, lava and candied water.
 *
 * <p>This exists because vanilla's bucket logic hardcodes its answers. {@code BucketItem} turns a
 * filled bucket back into {@code minecraft:bucket}, and filling a bucket produces
 * {@code fluid.getBucket()} - the vanilla bucket for water and lava. So a modded bucket that merely
 * inherits the behaviour turns into the iron bucket the first time it is used, which is the item
 * equivalent of that farmland-becomes-dirt bug.
 *
 * <p>Keeping the four items together in a family means every result can be routed back to the same
 * metal: whichever family member was used, and whichever bucket vanilla tried to hand back, the
 * answer is the family's own bucket for that fluid.
 */
public final class BucketFamily {
    /** The four buckets a metal has. */
    public enum Kind { EMPTY, WATER, LAVA, CANDIED_WATER, CHILLI_WATER }

    private final String metal;
    private final Map<Kind, RegistryObject<Item>> items = new EnumMap<>(Kind.class);

    public BucketFamily(String metal) {
        this.metal = metal;
    }

    /** The metal's registry name prefix, e.g. {@code limonite}. */
    public String metal() {
        return metal;
    }

    public void bind(Kind kind, RegistryObject<Item> item) {
        items.put(kind, item);
    }

    /** The family's item for this kind, or null before registration has finished. */
    public Item item(Kind kind) {
        RegistryObject<Item> item = items.get(kind);
        return item == null ? null : item.get();
    }

    /**
     * Which of this family's buckets holds {@code fluid}, or null when it is not one of them.
     *
     * <p>Matched by fluid identity against the family's own buckets rather than against a list of
     * fluid names, so nothing here has to know what water, lava or candied water are called.
     *
     * <p>Compared as sources, because the fluid that gets picked up is not always the one a bucket
     * holds: {@code FlowingFluidBlock#takeLiquid} hands back the fluid the block was built from, and
     * AoA builds its fluid blocks from the <em>flowing</em> fluid. Matching identities directly would
     * miss AoA's candied water and fall back to AoA's own bucket - which is exactly what happened.
     */
    public Kind kindFor(Fluid fluid) {
        if (fluid == null) {
            return null;
        }
        Fluid wanted = sourceOf(fluid);
        for (Kind kind : Kind.values()) {
            Item item = item(kind);
            if (item instanceof BucketItem && sourceOf(((BucketItem) item).getFluid()) == wanted) {
                return kind;
            }
        }
        return null;
    }

    /** The source fluid of a flowing one; anything else is already its own source. */
    public static Fluid sourceOf(Fluid fluid) {
        return fluid instanceof FlowingFluid ? ((FlowingFluid) fluid).getSource() : fluid;
    }

    /** Maps a bucket vanilla's fill logic produced onto this family's bucket of the same kind. */
    public ItemStack bucketFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BucketItem)) {
            return stack;
        }
        Kind kind = kindFor(((BucketItem) stack.getItem()).getFluid());
        Item mine = kind == null ? null : item(kind);
        return mine == null ? stack : new ItemStack(mine, stack.getCount());
    }
}
