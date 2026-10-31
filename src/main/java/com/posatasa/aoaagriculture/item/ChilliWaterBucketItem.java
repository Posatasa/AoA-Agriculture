package com.posatasa.aoaagriculture.item;

import com.posatasa.aoaagriculture.registry.ChilliWater;
import com.posatasa.aoaagriculture.util.LazyPour;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A bucket of chilli water - the one a vanilla bucket produces, since emptying it gives the vanilla
 * bucket back.
 *
 * <p>Chilli water is registered by this add-on, and fluids register after items, so this bucket
 * cannot hold it the way vanilla holds water: vanilla's bucket reads the fluid field it filled at
 * construction. The fluid is resolved on use instead, and the pour is done through {@link LazyPour},
 * which is vanilla's own placement written against the fluid rather than against the field.
 */
public class ChilliWaterBucketItem extends BucketItem {
    public ChilliWaterBucketItem(Item.Properties properties) {
        super(ChilliWater::still, properties);
    }

    @Override
    public boolean emptyBucket(PlayerEntity player, World world, BlockPos pos,
                               @Nullable BlockRayTraceResult hit) {
        return LazyPour.emptyBucket(getFluid(), player, world, pos, hit);
    }

    /** Creative keeps the bucket, as a vanilla bucket does; survival gets the empty one. */
    @Override
    protected ItemStack getEmptySuccessItem(ItemStack stack, PlayerEntity player) {
        return player.abilities.instabuild ? stack : new ItemStack(net.minecraft.item.Items.BUCKET);
    }
}
