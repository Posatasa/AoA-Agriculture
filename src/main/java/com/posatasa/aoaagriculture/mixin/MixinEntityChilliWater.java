package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.registry.ChilliWater;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.potion.Effects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chilli water's heat: it does not put fires out, and it sets entities alight.
 *
 * <p>Water extinguishes fire every tick an entity is in it. Chilli water must not, which is the one
 * place it cannot simply behave like water, so the extinguish call is skipped while the entity is in
 * it - and the same tick sets the entity on fire instead.
 *
 * <p>Fire Resistance and Frost Walker both protect, as asked: the effect on the entity, and the
 * enchantment on its boots. Nothing is applied to entities that already have either.
 */
@Mixin(Entity.class)
public abstract class MixinEntityChilliWater {
    /** A second of fire per tick in the water, which keeps it alight for as long as it stands there. */
    private static final int FIRE_SECONDS = 1;

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void aoaagriculture$chilliWaterBurns(CallbackInfo info) {
        Entity self = (Entity) (Object) this;
        if (!ChilliWater.contains(self) || !burnable(self)) {
            return;
        }
        if (self.getRemainingFireTicks() < FIRE_SECONDS * 20) {
            self.setSecondsOnFire(FIRE_SECONDS);
        }
    }

    /** Water puts fire out; chilli water does not. */
    @Inject(method = "clearFire", at = @At("HEAD"), cancellable = true)
    private void aoaagriculture$chilliWaterDoesNotExtinguish(CallbackInfo info) {
        Entity self = (Entity) (Object) this;
        if (ChilliWater.contains(self) && burnable(self)) {
            info.cancel();
        }
    }

    /** Whether this entity can be set alight at all: not fire-proof, and not frost-walkered. */
    private static boolean burnable(Entity entity) {
        if (entity.fireImmune()) {
            return false;
        }
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.hasEffect(Effects.FIRE_RESISTANCE)) {
                return false;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FROST_WALKER,
                    living.getItemBySlot(EquipmentSlotType.FEET)) > 0) {
                return false;
            }
        }
        return true;
    }
}
