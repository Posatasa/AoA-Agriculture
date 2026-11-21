package com.posatasa.aoaagriculture.item;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameterSets;
import net.minecraft.loot.LootParameters;
import net.minecraft.loot.LootTable;
import net.minecraft.util.ActionResult;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import javax.annotation.Nullable;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

/**
 * The Lyon bucket: one time in five, the water comes with something else.
 *
 * <p>Filling it from water looks through AoA's crystal box loot table and drops whatever comes out at the
 * player's feet. Nothing is taken away - the bucket works normally either way - so this is a small bonus
 * on fetching water rather than a second function.
 */
public class LuckyBucketItem extends AoABucketItem {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("aoaagriculture");

    /** One in this many fills pays out. */
    private static final int ODDS = 5;
    private static final ResourceLocation CRYSTAL_BOX =
            new ResourceLocation("aoa3", "items/crystal_box");

    public LuckyBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack before = player.getItemInHand(hand).copy();
        ActionResult<ItemStack> result = super.use(world, player, hand);
        if (world.isClientSide || result.getResult().consumesAction() == false) {
            return result;
        }
        // Only a fill counts, and a fill is the bucket coming back holding water.
        Item water = family.item(BucketFamily.Kind.WATER);
        ItemStack after = result.getObject();
        if (water == null || after == null || after.getItem() != water || after == before) {
            return result;
        }
        if (world.random.nextInt(ODDS) == 0) {
            payout(world, player);
        }
        return result;
    }

    /** Rolls AoA's crystal box and drops what it gives. */
    private static void payout(World world, PlayerEntity player) {
        if (!(world instanceof ServerWorld) || world.getServer() == null) {
            return;
        }
        ServerWorld server = (ServerWorld) world;
        if (server.getServer() == null) {
            return;
        }
        LootTable table = server.getServer().getLootTables().get(CRYSTAL_BOX);
        List<ItemStack> items;
        try {
            // The empty parameter set needs nothing, so a loot table that merely has a pool of items
            // rolls fine; the two optional parameters are there for tables that flavour their drop.
            items = table.getRandomItems(new LootContext.Builder(server)
                    .withRandom(server.random)
                    .withOptionalParameter(LootParameters.THIS_ENTITY, player)
                    .withOptionalParameter(LootParameters.ORIGIN,
                            net.minecraft.util.math.vector.Vector3d.atCenterOf(player.blockPosition()))
                    .create(LootParameterSets.EMPTY));
        } catch (RuntimeException failure) {
            LOGGER.warn("AoA's crystal box loot table could not be rolled: {}", failure.toString());
            return;
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(server, player.getX(), player.getY() + 0.5D,
                        player.getZ(), stack);
                server.addFreshEntity(drop);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world,
                                List<ITextComponent> tooltip, ITooltipFlag flag) {
        addOwnTooltip(tooltip);
    }
}
