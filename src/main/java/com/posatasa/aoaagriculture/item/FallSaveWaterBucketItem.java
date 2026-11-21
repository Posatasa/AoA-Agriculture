package com.posatasa.aoaagriculture.item;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * The Ghastly water bucket: pour it at the ground below you when there is nothing in reach.
 *
 * <p>Over a block it is an ordinary bucket. Pointed at the air - which, falling, is what the player has
 * - it finds the first solid ground under them and puts the water on top of it, so the bucket itself is
 * the fall save. That is the same idea as the water bomb: a column of water under the player, made where
 * they can see it coming.
 */
public class FallSaveWaterBucketItem extends AoABucketItem {
    /** How far down to look for ground. The world is 256 tall, so this covers a fall from the ceiling. */
    private static final int SEARCH_DEPTH = 320;

    public FallSaveWaterBucketItem(Fluid content, Item.Properties properties,
                                   BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        BlockRayTraceResult hit = Item.getPlayerPOVHitResult(world, player,
                RayTraceContext.FluidMode.NONE);
        if (hit.getType() == RayTraceResult.Type.BLOCK) {
            return super.use(world, player, hand);       // something to pour on: an ordinary bucket
        }
        return fallSave(world, player, hand);
    }

    private ActionResult<ItemStack> fallSave(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos target = groundBelow(world, player);
        if (target == null) {
            return ActionResult.fail(stack);
        }
        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return ActionResult.fail(stack);
        }
        if (!world.isClientSide) {
            world.setBlockAndUpdate(target,
                    Fluids.WATER.defaultFluidState().createLegacyBlock());
            world.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        ItemStack result = player.abilities.instabuild ? stack : new ItemStack(empty);
        return ActionResult.sidedSuccess(result, world.isClientSide);
    }

    /** The first free block above solid ground under the player, or null when there is none. */
    private static BlockPos groundBelow(World world, PlayerEntity player) {
        BlockPos start = player.blockPosition();
        for (int depth = 1; depth <= SEARCH_DEPTH; depth++) {
            BlockPos floor = start.below(depth);
            if (floor.getY() < 0) {
                return null;
            }
            BlockState state = world.getBlockState(floor);
            if (!state.getMaterial().isReplaceable() && !state.getMaterial().isLiquid()) {
                return floor.above();
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world,
                                List<ITextComponent> tooltip, ITooltipFlag flag) {
        addOwnTooltip(tooltip);
    }
}
