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
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeMod;
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
 * <p>Two things make this less simple than it looks.
 *
 * <p>The first is that AoA puts its candied water in the {@code minecraft:water} fluid tag, so
 * vanilla's glass bottle treats it as water and hands back a <em>water bottle</em>. Anything that
 * wants to bottle it has to run before vanilla's bottle does.
 *
 * <p>The second is which event that is. Aiming at a fluid surface is an <em>item</em> use: the
 * client's own block hit does not include fluids, so the click never takes the block path, and
 * {@code RightClickBlock} - the obvious choice - does not fire at all. Both events are handled for
 * that reason: one is the path a fluid click takes, the other covers a click that lands on a block,
 * and they are mutually exclusive per click.
 */
public final class Bottles {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    /**
     * Six hunger and a little saturation, like a honey bottle - but drinkable at any time.
     *
     * <p>Honey is gated on being hungry; a bottle of drink is not, and gating it made the item look
     * broken whenever the player was full.
     */
    private static final Food CANDIED_WATER =
            new Food.Builder().nutrition(6).saturationMod(0.1F).alwaysEat().build();

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
        MinecraftForge.EVENT_BUS.addListener(Bottles::onUseItem);
        MinecraftForge.EVENT_BUS.addListener(Bottles::onUseBlock);
    }

    /** The path a click on a fluid takes. */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (fill(event.getPlayer(), event.getWorld(), event.getHand(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /** The path a click that lands on a block takes. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (fill(event.getPlayer(), event.getWorld(), event.getHand(), event.getItemStack())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    /**
     * Fills a glass bottle from AoA's candied water, the way vanilla fills one from water.
     *
     * <p>Returns whether it did anything. Everything is decided before the fluid is taken, so a
     * bottle pointed at anything else keeps vanilla's behaviour and no source is ever lost.
     */
    private static boolean fill(PlayerEntity player, World world, Hand hand, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.GLASS_BOTTLE) {
            return false;
        }
        BlockRayTraceResult hit = aimedAt(player, world);
        if (hit == null) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        FluidState fluidState = world.getFluidState(pos);
        // Deliberately not "is this a source block": AoA builds its fluid blocks from the FLOWING
        // fluid, so their state reports itself as flowing even for a full block. takeLiquid below is
        // the real test - it returns EMPTY unless the block is full.
        if (!isCandiedWater(fluidState.getType())) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IBucketPickupHandler)
                || !world.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return false;
        }
        Fluid taken = ((IBucketPickupHandler) state.getBlock()).takeLiquid(world, pos, state);
        if (taken == Fluids.EMPTY) {
            return false;
        }

        player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
        world.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        ItemStack filled = DrinkHelper.createFilledResult(stack, player,
                new ItemStack(CANDIED_WATER_BOTTLE.get()));
        player.setItemInHand(hand, filled);
        return true;
    }

    /** What the player is looking at, in the same way vanilla's bottle looks at it. */
    private static BlockRayTraceResult aimedAt(PlayerEntity player, World world) {
        double reach = player.getAttributeValue(ForgeMod.REACH_DISTANCE.get());
        Vector3d eye = player.getEyePosition(1.0F);
        Vector3d end = eye.add(player.getLookAngle().scale(reach));
        return world.clip(new RayTraceContext(eye, end, RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.SOURCE_ONLY, player));
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
        return candied != Fluids.EMPTY
                && BucketFamily.sourceOf(fluid) == BucketFamily.sourceOf(candied);
    }

    private Bottles() {}
}
