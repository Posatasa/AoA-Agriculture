package com.posatasa.aoaagriculture.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The empty bucket of a lava-only family: Emberstone's and Blazium's.
 *
 * <p>It refuses everything except lava - water and candied water are simply not taken, and the
 * refusal happens before the fluid is lifted out of the world, so pointing it at a pool does not
 * drain it. Filling it gives the family's lava bucket: a tank for Emberstone and Blazium, and the
 * endless one for Dark.
 */
public class LavaOnlyBucketItem extends AoABucketItem {
    public LavaOnlyBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    protected boolean accepts(Fluid fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    /** The family's lava bucket, started at one lava if it is a tank. */
    @Override
    protected ItemStack filledFor(ItemStack stack, Fluid pickedUp) {
        Item filled = family.item(BucketFamily.Kind.LAVA);
        if (filled == null) {
            return new ItemStack(pickedUp.getBucket());
        }
        if (filled instanceof LavaTankBucketItem) {
            LavaTankBucketItem tank = (LavaTankBucketItem) filled;
            return tank.withLava(new ItemStack(filled), 1);
        }
        return new ItemStack(filled);
    }

    /** The tooltip is looked up by this item's own name, so each bucket describes itself. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        ResourceLocation name = getRegistryName();
        if (name != null) {
            tooltip.add(new TranslationTextComponent("item.aoaagriculture." + name.getPath() + ".tooltip")
                    .withStyle(TextFormatting.GRAY));
        }
    }
}
