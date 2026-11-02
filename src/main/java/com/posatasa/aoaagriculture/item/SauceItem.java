package com.posatasa.aoaagriculture.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effects;
import net.minecraft.stats.Stats;
import net.minecraft.world.World;

/**
 * A bowl of sauce: eaten like mushroom stew, and it gives the bowl back.
 *
 * <p>Bowl foods in 1.16.5 do not hand their bowl back through an item property, so this does it in
 * {@link #finishUsingItem} the same way the candied water bottle returns its glass bottle - including
 * dropping the bowl when there is no room for it.
 */
public class SauceItem extends Item {
    /** Whether eating this sets the eater alight for a second, as chilli water does. */
    private final boolean setsFire;

    public SauceItem(Item.Properties properties) {
        this(properties, false);
    }

    public SauceItem(Item.Properties properties, boolean setsFire) {
        super(properties);
        this.setsFire = setsFire;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity user) {
        super.finishUsingItem(stack, world, user);
        if (setsFire && !user.fireImmune() && !user.hasEffect(Effects.FIRE_RESISTANCE)) {
            user.setSecondsOnFire(1);
        }
        if (user instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        if (stack.isEmpty()) {
            return new ItemStack(Items.BOWL);
        }
        if (user instanceof PlayerEntity && !((PlayerEntity) user).abilities.instabuild) {
            PlayerEntity player = (PlayerEntity) user;
            ItemStack bowl = new ItemStack(Items.BOWL);
            if (!player.inventory.add(bowl)) {
                player.drop(bowl, false);
            }
        }
        return stack;
    }
}
