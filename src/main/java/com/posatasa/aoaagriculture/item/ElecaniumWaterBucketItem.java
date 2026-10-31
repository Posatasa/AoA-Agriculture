package com.posatasa.aoaagriculture.item;

import com.posatasa.aoaagriculture.compat.AoASpirit;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Elecanium water bucket: an ordinary water bucket that can spend its water on the player.
 *
 * <p>Used on nothing - right-clicked at the air, so there is nowhere to pour - the water is spent
 * instead: the bucket comes back empty, and the player's Spirit is refilled to its maximum. Nothing
 * else about it is unusual; over a block it pours water like any bucket.
 *
 * <p>The water is what pays for it, which is why this is a bucket and not a free source of Spirit:
 * filling it again means finding water.
 */
public class ElecaniumWaterBucketItem extends AoABucketItem {
    /** Pitch for spending the water, a touch above the pour so the two sound different. */
    private static final float SPEND_PITCH = 1.4F;

    public ElecaniumWaterBucketItem(Fluid content, Item.Properties properties,
                                    BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockRayTraceResult hit = Item.getPlayerPOVHitResult(world, player,
                RayTraceContext.FluidMode.NONE);
        if (hit.getType() == RayTraceResult.Type.BLOCK) {
            return super.use(world, player, hand);       // somewhere to pour: an ordinary bucket
        }

        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return ActionResult.fail(stack);
        }
        if (!world.isClientSide) {
            if (player instanceof ServerPlayerEntity) {
                AoASpirit.refill((ServerPlayerEntity) player);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, SPEND_PITCH);
        }
        return ActionResult.sidedSuccess(new ItemStack(empty), world.isClientSide);
    }

    /** The tooltip is looked up by this item's own name, like the other buckets that do more. */
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
