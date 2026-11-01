package com.posatasa.aoaagriculture.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

/**
 * A cake that eats like vanilla's but feeds whatever it is told to.
 *
 * <p>The block itself is vanilla's: this extends {@link CakeBlock}, so the states, the shape, the bites
 * and the way it is eaten are all inherited. The one thing that cannot be inherited is the amount,
 * because vanilla's {@code eat} is private and hardcodes two hunger and 0.1 saturation - so {@code use}
 * is reimplemented here with vanilla's own sequence and nothing changed but the numbers: refuse when the
 * player is full, count the slice, feed, then either take a bite off or remove the cake.
 */
public class ChocolateCakeBlock extends CakeBlock {
    private final int hunger;
    private final float saturation;

    public ChocolateCakeBlock(AbstractBlock.Properties properties, int hunger, float saturation) {
        super(properties);
        this.hunger = hunger;
        this.saturation = saturation;
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                Hand hand, BlockRayTraceResult hit) {
        if (world.isClientSide) {
            ItemStack held = player.getItemInHand(hand);
            return eat(world, pos, state, player).consumesAction() ? ActionResultType.SUCCESS
                    : (held.isEmpty() ? ActionResultType.CONSUME : ActionResultType.PASS);
        }
        return eat(world, pos, state, player);
    }

    /** Vanilla's private eat, with the numbers this cake wants. */
    private ActionResultType eat(IWorld world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!player.canEat(false)) {
            return ActionResultType.PASS;
        }
        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.getFoodData().eat(hunger, saturation);
        int bites = state.getValue(CakeBlock.BITES);
        if (bites < 6) {
            world.setBlock(pos, state.setValue(CakeBlock.BITES, bites + 1), 3);
        } else {
            world.removeBlock(pos, false);
        }
        return ActionResultType.SUCCESS;
    }
}
