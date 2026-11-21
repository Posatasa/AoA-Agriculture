package com.posatasa.aoaagriculture.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ActionResult;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

/**
 * The Mystite water bucket: it grants night vision before it pours.
 *
 * <p>Used while the player has no night vision, it gives five minutes of it and keeps the water - a lamp
 * as much as a bucket. Once the player already has night vision, it is an ordinary water bucket, so the
 * water is not wasted on an effect that is already running.
 */
public class NightVisionBucketItem extends AoABucketItem {
    /** Five minutes. */
    private static final int DURATION = 20 * 60 * 5;

    public NightVisionBucketItem(net.minecraft.fluid.Fluid content,
                                 net.minecraft.item.Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (player.hasEffect(Effects.NIGHT_VISION)) {
            return super.use(world, player, hand);
        }
        if (!world.isClientSide) {
            player.addEffect(new EffectInstance(Effects.NIGHT_VISION, DURATION, 0, false, true));
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMBIENT_CAVE, SoundCategory.PLAYERS, 0.6F, 1.2F);
        }
        return ActionResult.sidedSuccess(player.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world,
                                List<ITextComponent> tooltip, ITooltipFlag flag) {
        addOwnTooltip(tooltip);
    }
}
