package com.posatasa.aoaagriculture.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/**
 * The Ghoulish bucket: it fills from water it can see, not from water it can reach.
 *
 * <p>A vanilla bucket picks up only what is within arm's length. This one traces the player's line of
 * sight thirty blocks out and takes the water source it finds there, which leaves the bucket empty-handed
 * only if there is no water in sight at all. Anything else - pouring, lava, candied water, chilli water -
 * is left to the ordinary bucket code.
 */
public class LongReachBucketItem extends AoABucketItem {
    /** How far the bucket can see. Thirty blocks is well past any reach attribute. */
    private static final double REACH = 30.0D;

    public LongReachBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockRayTraceResult hit = sightLine(world, player);
        if (hit == null) {
            return super.use(world, player, hand);       // nothing in sight: whatever vanilla would do
        }
        BlockPos pos = hit.getBlockPos();
        if (!world.mayInteract(player, pos)) {
            return ActionResult.fail(stack);
        }

        Item water = family.item(BucketFamily.Kind.WATER);
        if (water == null) {
            return ActionResult.fail(stack);
        }
        if (!world.isClientSide) {
            world.setBlockAndUpdate(pos, net.minecraft.block.Blocks.AIR.defaultBlockState());
            world.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        ItemStack filled = player.abilities.instabuild ? stack : new ItemStack(water);
        return ActionResult.sidedSuccess(filled, world.isClientSide);
    }

    /** The water source the player is looking at, up to {@link #REACH} blocks away, or null. */
    private static BlockRayTraceResult sightLine(World world, PlayerEntity player) {
        Vector3d eye = player.getEyePosition(1.0F);
        Vector3d end = eye.add(player.getViewVector(1.0F).scale(REACH));
        BlockRayTraceResult hit = world.clip(new RayTraceContext(eye, end,
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.SOURCE_ONLY, player));
        if (hit.getType() != RayTraceResult.Type.BLOCK) {
            return null;
        }
        FluidState fluid = world.getFluidState(hit.getBlockPos());
        // Only water: the bucket is named for what it fetches, and lava at range is not what was asked.
        return fluid.isSource() && fluid.getType() == Fluids.WATER ? hit : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world,
                                List<ITextComponent> tooltip, ITooltipFlag flag) {
        addOwnTooltip(tooltip);
    }
}
