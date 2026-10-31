package com.posatasa.aoaagriculture.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.stats.Stats;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

/**
 * A bottle of chilli water: no hunger at all, and a second of fire.
 *
 * <p>It follows the candied water bottle - bottled from a cauldron, glass bottle back afterwards -
 * except that it feeds nothing and sets the drinker alight for one second. It is drinkable at any
 * time, which is what the zero-nutrition always-eat food on the item is for.
 */
public class ChilliWaterBottleItem extends Item {
    /** How long drinking takes, the same as any bottle. */
    private static final int DRINK_TICKS = 40;
    /** One second of fire, as the drink promises. */
    private static final int FIRE_TICKS = 20;

    public ChilliWaterBottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity user) {
        super.finishUsingItem(stack, world, user);
        if (user instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        if (!world.isClientSide) {
            user.setSecondsOnFire(1);
        }
        if (stack.isEmpty()) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        if (user instanceof PlayerEntity && !((PlayerEntity) user).abilities.instabuild) {
            PlayerEntity player = (PlayerEntity) user;
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!player.inventory.add(bottle)) {
                player.drop(bottle, false);
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_TICKS;
    }

    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }
}
