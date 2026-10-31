package com.posatasa.aoaagriculture.item;

import com.posatasa.aoaagriculture.compat.AoAResourceRefill;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A filled bucket that can spend its fluid on the player: Elecanium's water buys Spirit, Varsium's
 * lava buys Energy.
 *
 * <p>Used on nothing - right-clicked at the air, so there is nowhere to pour - the fluid is spent
 * instead: the bucket comes back empty, with the pour sound, and the resource goes back to its
 * maximum. Over a block it pours like any bucket, so the fluid is what pays for it.
 *
 * <p>In creative nothing is spent: the bucket stays full, exactly as a vanilla bucket does when it
 * pours, and the resource is still refilled.
 */
public class ResourceRefillBucketItem extends AoABucketItem {
    /** Pitch for spending the fluid, a touch above the pour so the two sound different. */
    private static final float SPEND_PITCH = 1.4F;

    private final AoAResourceRefill.Kind resource;

    public ResourceRefillBucketItem(Fluid content, Item.Properties properties, BucketFamily family,
                                    AoAResourceRefill.Kind resource) {
        super(content, properties, family);
        this.resource = resource;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockRayTraceResult hit = Item.getPlayerPOVHitResult(world, player,
                RayTraceContext.FluidMode.NONE);
        if (hit.getType() == RayTraceResult.Type.BLOCK) {
            return super.use(world, player, hand);       // somewhere to pour: an ordinary bucket
        }

        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return ActionResult.fail(stack);
        }
        if (!world.isClientSide) {
            if (player instanceof ServerPlayerEntity) {
                AoAResourceRefill.refill((ServerPlayerEntity) player, resource);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, SPEND_PITCH);
        }
        // Creative keeps the bucket full, the way a vanilla bucket does when it pours.
        ItemStack result = player.abilities.instabuild ? stack : new ItemStack(empty);
        return ActionResult.sidedSuccess(result, world.isClientSide);
    }

    /** The tooltip is looked up by this item's own name, like the other buckets that do more. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        ResourceLocation name = getRegistryName();
        if (name != null) {
            tooltip.add(new TranslationTextComponent("item.aoaagriculture." + name.getPath() + ".tooltip")
                    .withStyle(TextFormatting.GRAY));
        }
    }
}
