package com.posatasa.aoaagriculture.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ILiquidContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.IWorld;
import java.util.function.Supplier;

/**
 * A bucket whose fluid is registered after items: AoA's candied water, and this add-on's chilli water.
 *
 * <p>It cannot be an ordinary {@code BucketItem}, for a reason that is easy to miss: Forge registers
 * fluids <em>after</em> items, so when these buckets are created the fluid is not in the registry
 * yet. Vanilla's bucket keeps its fluid in a field that it fills at construction time, and both its
 * fill and its pour read that field - so a bucket built then would either take the wrong branch or
 * refuse to pour at all.
 *
 * <p>Hence the fluid is supplied lazily, and the steps that would read the field are overridden here:
 * the pour ({@link #emptyBucket}), its sound, and {@link #getFluid()} itself. Everything else -
 * including {@code use} - stays vanilla's, which is what the chilli water bucket does too, and it is
 * the path that is known to work.
 */
public class LazyFluidBucketItem extends AoABucketItem {

    public LazyFluidBucketItem(Supplier<? extends Fluid> content,
                               Item.Properties properties,
                               BucketFamily family) {
        super(content, properties, family);
    }

    /** Never null: an unresolvable fluid is treated as empty, which only disables the item. */
    @Override
    public Fluid getFluid() {
        Fluid fluid = super.getFluid();
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    /**
     * Vanilla's pour sound, taken from the fluid rather than from the field vanilla reads.
     *
     * <p>Without this the bucket throws a {@code NullPointerException} while playing the sound, after
     * it has already placed the fluid - which is exactly the kind of half-done action that is hard to
     * diagnose from the outside.
     */
    @Override
    protected void playEmptySound(@Nullable PlayerEntity player, IWorld world, BlockPos pos) {
        SoundEvent sound = getFluid().getAttributes().getEmptySound();
        if (sound == null) {
            sound = SoundEvents.BUCKET_EMPTY;
        }
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }


    /**
     * Vanilla's placement, with the fluid taken from {@link #getFluid()} instead of the field that
     * vanilla reads. Same order of decisions: pour into a container, otherwise into a replaceable
     * block, otherwise into the block next to the one that was clicked.
     */
    @Override
    public boolean emptyBucket(PlayerEntity player, World world, BlockPos pos,
                               @Nullable BlockRayTraceResult hit) {
        Fluid fluid = getFluid();
        if (!(fluid instanceof FlowingFluid)) {
            return false;
        }
        // Always place the source: a bucket holds still water, whatever the block it came from
        // reported as its fluid.
        FlowingFluid source = (FlowingFluid) ((FlowingFluid) fluid).getSource();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Material material = state.getMaterial();
        boolean replaceable = state.canBeReplaced(source);
        boolean canPlace = state.isAir() || replaceable
                || (block instanceof ILiquidContainer
                    && ((ILiquidContainer) block).canPlaceLiquid(world, pos, state, source));
        if (!canPlace) {
            return hit != null
                    && emptyBucket(player, world,
                            hit.getBlockPos().relative(hit.getDirection()), null);
        }
        if (world.dimensionType().ultraWarm() && source.is(FluidTags.WATER)) {
            world.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F,
                    2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);
            for (int i = 0; i < 8; i++) {
                world.addParticle(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + Math.random(), pos.getY() + Math.random(),
                        pos.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
            }
            return true;
        }
        if (block instanceof ILiquidContainer
                && ((ILiquidContainer) block).canPlaceLiquid(world, pos, state, source)) {
            ((ILiquidContainer) block).placeLiquid(world, pos, state, source.getSource(false));
            playEmptySound(player, world, pos);
            return true;
        }
        if (!world.isClientSide && replaceable && !material.isLiquid()) {
            world.destroyBlock(pos, true);
        }
        if (!world.setBlock(pos, source.defaultFluidState().createLegacyBlock(), 11)
                && !state.getFluidState().isSource()) {
            return false;
        }
        playEmptySound(player, world, pos);
        return true;
    }
}
