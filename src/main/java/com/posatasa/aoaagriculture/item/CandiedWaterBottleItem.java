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
 * A bottle of AoA's candied water: something sweet to drink, and a glass bottle back afterwards.
 *
 * <p>Drinking follows vanilla's honey bottle - the closest vanilla thing to a sweet drink in a bottle
 * - down to the details: the same 40-tick drink, the same animation and sound, and the same empty
 * bottle afterwards, dropped if the inventory has no room for it. Crafting it into sugar also returns
 * the bottle, which is set on the item's properties as a crafting remainder.
 *
 * <p>What it does not copy from honey is the poison cure: this is sugar water, not medicine.
 */
public class CandiedWaterBottleItem extends Item {
    /** How long drinking takes. Vanilla's honey bottle and potions both use 40 ticks. */
    private static final int DRINK_TICKS = 40;

    public CandiedWaterBottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity user) {
        super.finishUsingItem(stack, world, user);        // the food itself, and one bottle used up
        if (user instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            player.awardStat(Stats.ITEM_USED.get(this));
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
