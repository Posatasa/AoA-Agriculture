package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.block.ChilliWaterCauldronBlock;
import com.posatasa.aoaagriculture.fluid.ChilliWaterFluid;
import com.posatasa.aoaagriculture.item.ChilliWaterBottleItem;
import com.posatasa.aoaagriculture.item.ChilliWaterBucketItem;
import net.minecraft.block.Block;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Chilli water: the fluid, its block, its cauldron, and the two plain items that hold it.
 *
 * <p>Modelled on AoA's candied water - a water-like fluid with its own bucket and bottle - with the
 * heat added: an entity in it keeps burning and is set alight, which is why it is not simply put in
 * the water tag and left alone. See {@code MixinEntityChilliWater}.
 *
 * <p>It is made, not found: thirty-two AoA chillies in a cauldron of water, and that cauldron becomes
 * one of these. One cauldron is a bucket, which is three bottles.
 */
public final class ChilliWater {
    /** A hot, saturated red with a little orange in it, so it reads as heat rather than as blood. */
    public static final int COLOR = 0xFFD42A18;
    /** AoA's chilli, by registry name. */
    public static final String CHILLI = "aoa3:chilli";
    /** How many chillies one cauldron of water takes. */
    public static final int CHILLIES_PER_CAULDRON = 32;

    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, FarmlandBlocks.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FarmlandBlocks.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    public static final RegistryObject<FlowingFluid> STILL = FLUIDS.register("chilli_water",
            () -> new ChilliWaterFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING = FLUIDS.register("chilli_water_flowing",
            () -> new ChilliWaterFluid.Flowing(properties()));

    /** The fluid block. A plain water-like block; the heat lives in the entity mixin, in one place. */
    public static final RegistryObject<FlowingFluidBlock> BLOCK = BLOCKS.register("chilli_water",
            () -> new FlowingFluidBlock(STILL,
                    Block.Properties.of(Material.WATER).noCollission().strength(100.0F).noDrops()));

    public static final RegistryObject<ChilliWaterCauldronBlock> CAULDRON =
            BLOCKS.register("chilli_water_cauldron", ChilliWaterCauldronBlock::new);

    /** A vanilla bucket of chilli water; emptying it gives the vanilla bucket back. */
    public static final RegistryObject<Item> BUCKET = ITEMS.register("chilli_water_bucket",
            () -> new ChilliWaterBucketItem(new Item.Properties()
                    .tab(FarmlandBlocks.TAB).stacksTo(1)));

    /** The bottle: no hunger at all, and a second of fire. */
    public static final RegistryObject<Item> BOTTLE = ITEMS.register("chilli_water_bottle",
            () -> new ChilliWaterBottleItem(new Item.Properties()
                    .tab(FarmlandBlocks.TAB)
                    .stacksTo(16)
                    .craftRemainder(Items.GLASS_BOTTLE)
                    .food(new Food.Builder().nutrition(0).saturationMod(0.0F).alwaysEat().build())));

    public static final RegistryObject<Item> CAULDRON_ITEM = ITEMS.register("chilli_water_cauldron",
            () -> new BlockItem(CAULDRON.get(), new Item.Properties().tab(FarmlandBlocks.TAB)));

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(STILL, FLOWING, FluidAttributes.builder(
                new ResourceLocation(FarmlandBlocks.MOD_ID, "block/chilli_water_still"),
                new ResourceLocation(FarmlandBlocks.MOD_ID, "block/chilli_water_flowing"))
                .color(COLOR).density(1000).viscosity(1000))
                .block(BLOCK)
                .bucket(BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5);
    }

    /** The still chilli water, resolved lazily because fluids register after items. */
    public static Fluid still() {
        return STILL.get();
    }

    /** Whether this entity is standing in chilli water. Used by the mixin. */
    public static boolean contains(Entity entity) {
        if (entity == null || entity.level == null) {
            return false;
        }
        if (isChilliWater(entity.level.getFluidState(entity.blockPosition()))) {
            return true;
        }
        return isChilliWater(entity.level.getFluidState(new BlockPos(entity.getEyePosition(1.0F))));
    }

    /** Whether this fluid state is chilli water, source or flowing. */
    public static boolean isChilliWater(FluidState state) {
        Fluid fluid = STILL.get();
        return fluid != null && state.getType() == fluid;
    }

    public static void register(IEventBus modBus) {
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ChilliWaterCauldronBlock.register();
    }

    private ChilliWater() {}
}
