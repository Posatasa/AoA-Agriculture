package com.posatasa.aoaagriculture.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ILiquidContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.DrinkHelper;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Pouring and filling for buckets whose fluid is resolved lazily.
 *
 * <p>A fluid registered by this add-on is registered after items are, so a bucket built at
 * registration time cannot hold it eagerly - vanilla's {@code BucketItem} keeps its fluid in a field
 * written at construction, and its emptying logic reads that field. These two helpers do the two
 * steps that read it, against a fluid passed in instead, which is exactly vanilla's own logic.
 */
public final class LazyPour {
    /** Vanilla's empty-bucket placement, with the fluid given instead of read from a field. */
    public static boolean emptyBucket(Fluid content, PlayerEntity player, World world, BlockPos pos,
                                      @Nullable BlockRayTraceResult hit) {
        if (!(content instanceof FlowingFluid)) {
            return false;
        }
        FlowingFluid source = (FlowingFluid) ((FlowingFluid) content).getSource();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Material material = state.getMaterial();
        boolean replaceable = state.canBeReplaced(source);
        boolean canPlace = state.isAir() || replaceable
                || (block instanceof ILiquidContainer
                    && ((ILiquidContainer) block).canPlaceLiquid(world, pos, state, source));
        if (!canPlace) {
            return hit != null
                    && emptyBucket(content, player, world,
                            hit.getBlockPos().relative(hit.getDirection()), null);
        }
        if (block instanceof ILiquidContainer
                && ((ILiquidContainer) block).canPlaceLiquid(world, pos, state, source)) {
            ((ILiquidContainer) block).placeLiquid(world, pos, state, source.getSource(false));
            playEmptySound(content, player, world, pos);
            return true;
        }
        if (!world.isClientSide && replaceable && !material.isLiquid()) {
            world.destroyBlock(pos, true);
        }
        if (!world.setBlock(pos, source.defaultFluidState().createLegacyBlock(), 11)
                && !state.getFluidState().isSource()) {
            return false;
        }
        playEmptySound(content, player, world, pos);
        return true;
    }

    /** Vanilla's empty-bucket sound, broadcast so the player who poured hears it too. */
    public static void playEmptySound(Fluid content, PlayerEntity player, World world, BlockPos pos) {
        SoundEvent sound = content.getAttributes().getEmptySound();
        if (sound == null) {
            sound = content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA
                    : SoundEvents.BUCKET_EMPTY;
        }
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** Fills a bottle or bucket the way vanilla does, creative included. */
    public static ItemStack createFilledResult(PlayerEntity player, ItemStack empty,
                                               ItemStack filled) {
        return DrinkHelper.createFilledResult(empty, player, filled);
    }

    /** The fluid's own fill sound, or vanilla's choice. */
    public static SoundEvent fillSound(Fluid fluid) {
        SoundEvent sound = fluid.getAttributes().getFillSound();
        return sound == null ? SoundEvents.BUCKET_FILL : sound;
    }

    /** Shorthand for the still fluid of a flowing one; anything else is its own source. */
    public static Fluid sourceOf(Fluid fluid) {
        return fluid instanceof FlowingFluid ? ((FlowingFluid) fluid).getSource() : fluid;
    }

    /** Usable when there is simply no fluid to pour: a no-op guard for callers. */
    public static boolean pourNothing(Fluid content, World world, BlockPos pos) {
        return content == Fluids.EMPTY || world.getBlockState(pos).isAir();
    }

    private LazyPour() {}
}
