package com.posatasa.aoaagriculture.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A bucket whose fluid never runs out: Shyre's water one, and the Dark lava one.
 *
 * <p>Everything else about it is a normal bucket. Pouring is vanilla's pour with the answer thrown
 * away, which is what keeps the fluid; the button is read as a hold, and a full two-second hold
 * empties the bucket instead. Nothing is poured when the button goes down, so a long press never
 * spills on the way to emptying.
 *
 * <p>Three details are easy to get wrong here, and this class exists to get them right in one place:
 *
 * <ul>
 *   <li>a completed hold goes through {@code finishUsingItem}, not {@code releaseUsing} - the latter
 *       only runs when the player lets go early;
 *   <li>returning CONSUME does not start a use action, so {@code startUsingItem} is called;
 *   <li>vanilla's pour plays its sound with the player passed as an exception, because vanilla pours
 *       on both sides. Ours only pours on the server, so copying that left the player who poured
 *       hearing nothing - see {@link #playEmptySound}.
 * </ul>
 */
public class InfiniteBucketItem extends AoABucketItem {
    /** How long the button has to be held: two seconds. */
    private static final int HOLD_TICKS = 40;
    /** Pitch for the sound a completed hold makes, a touch above the pour. */
    private static final float CLEAR_PITCH = 1.4F;

    public InfiniteBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return HOLD_TICKS;
    }

    /** The bow's pull-back pose, so the hold is visible rather than silent. */
    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.BOW;
    }

    /** The tooltip is looked up by this item's own name, so each bucket describes itself. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        ResourceLocation name = getRegistryName();
        if (name != null) {
            tooltip.add(new TranslationTextComponent("item.aoaagriculture." + name.getPath() + ".tooltip")
                    .withStyle(TextFormatting.GRAY));
        }
    }

    /** Starts the hold. Nothing is poured yet; that is what keeps a long press from spilling. */
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return ActionResult.consume(stack);
    }

    /** Released early: an ordinary pour, and the fluid is kept. */
    @Override
    public void releaseUsing(ItemStack stack, World world, LivingEntity user, int timeLeft) {
        if (world.isClientSide || timeLeft <= 0 || !(user instanceof PlayerEntity)) {
            return;
        }
        if (!(stack.getItem() instanceof InfiniteBucketItem)) {
            return;                                  // the hold already emptied it
        }
        PlayerEntity player = (PlayerEntity) user;
        super.use(world, player, player.getUsedItemHand());
    }

    /** Held for the whole two seconds: the bucket empties. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity user) {
        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return stack;
        }
        if (!world.isClientSide) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), emptySound(),
                    SoundCategory.PLAYERS, 1.0F, CLEAR_PITCH);
        }
        return new ItemStack(empty);
    }

    /**
     * Plays vanilla's pour sound for everyone, the player who poured included.
     *
     * <p>Vanilla's {@code playEmptySound} is called with the player as the sound's "except" argument,
     * which is right there because vanilla pours on both sides and that player's own client plays it
     * locally. Our pour only runs on the server, so copying that left the player who poured hearing
     * nothing at all - an utterly silent bucket. Passing no exception broadcasts it to everyone,
     * including them.
     */
    @Override
    protected void playEmptySound(@Nullable PlayerEntity player, IWorld world, BlockPos pos) {
        world.playSound(null, pos, emptySound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** The fluid's own empty sound, or vanilla's choice for water and lava. */
    private SoundEvent emptySound() {
        SoundEvent sound = getFluid().getAttributes().getEmptySound();
        if (sound != null) {
            return sound;
        }
        return getFluid().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }
}
