package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.item.BucketFamily;
import com.posatasa.aoaagriculture.item.CandiedWaterBottleItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stats.Stats;
import net.minecraft.util.DrinkHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The candied water bottle: AoA's sweet fluid, in a vanilla glass bottle.
 *
 * <p>Vanilla's glass bottle only knows {@code #forge:water} - it fills from water and nothing else -
 * so a bottle of AoA's candied water has to be arranged separately. Doing it with Forge's
 * right-click event rather than by patching vanilla's bottle keeps the two apart: vanilla keeps
 * filling bottles from water exactly as before, and this only ever acts on AoA's fluid.
 */
public final class Bottles {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /** Six hunger and a little saturation: the same meal a honey bottle is. */
    private static final Food CANDIED_WATER =
            new Food.Builder().nutrition(6).saturationMod(0.1F).build();

    /**
     * Crafting it into sugar gives the bottle back. Vanilla does this with {@code craftRemainder},
     * which is how a honey bottle becomes three sugar and still leaves a glass bottle in the grid.
     */
    public static final RegistryObject<Item> CANDIED_WATER_BOTTLE = ITEMS.register(
            "candied_water_bottle", () -> new CandiedWaterBottleItem(new Item.Properties()
                    .tab(FarmlandBlocks.TAB)
                    .stacksTo(16)
                    .craftRemainder(Items.GLASS_BOTTLE)
                    .food(CANDIED_WATER)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(Bottles::onFill);
    }

    /**
     * Fills a glass bottle from AoA's candied water, the way vanilla fills one from water.
     *
     * <p>Everything is done only after the fluid has been identified as candied water and the player
     * is allowed to touch it, so a bottle pointed at anything else keeps vanilla's behaviour - and
     * the source block is never taken for a fluid that will not be bottled.
     */
    @SubscribeEvent
    public static void onFill(PlayerInteractEvent.RightClickBlock event) {
        PlayerEntity player = event.getPlayer();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != Items.GLASS_BOTTLE) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isSource() || !isCandiedWater(fluidState.getType())) {
            return;
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IBucketPickupHandler)
                || !world.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, event.getFace(), stack)) {
            return;
        }
        Fluid taken = ((IBucketPickupHandler) state.getBlock()).takeLiquid(world, pos, state);
        if (taken == Fluids.EMPTY) {
            return;
        }

        player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
        world.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        ItemStack filled = DrinkHelper.createFilledResult(stack, player,
                new ItemStack(CANDIED_WATER_BOTTLE.get()));
        player.setItemInHand(event.getHand() == null ? Hand.MAIN_HAND : event.getHand(), filled);

        // Stop here, so vanilla's bottle does not also get a turn at this click.
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }

    /**
     * Whether this fluid is AoA's candied water.
     *
     * <p>Compared as sources, because a fluid block hands back the fluid it was built from and AoA
     * builds its fluid blocks from the flowing one.
     */
    private static boolean isCandiedWater(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return false;
        }
        Fluid candied = Buckets.candiedWater();
        return candied != Fluids.EMPTY && BucketFamily.sourceOf(fluid) == BucketFamily.sourceOf(candied);
    }

    private Bottles() {}
}
