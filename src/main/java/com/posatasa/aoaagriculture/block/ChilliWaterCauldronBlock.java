package com.posatasa.aoaagriculture.block;

import com.posatasa.aoaagriculture.registry.Buckets;
import com.posatasa.aoaagriculture.registry.ChilliWater;
import com.posatasa.aoaagriculture.util.LazyPour;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CauldronBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A cauldron of chilli water, in three levels - one per bottle, three to a bucket.
 *
 * <p>Vanilla's cauldron can only hold water, so this is a block of its own that looks the same and
 * carries a level. It is made by using AoA's chillies on a cauldron of water; emptying it out gives
 * the vanilla cauldron back, so nothing is left behind that only this mod understands.
 */
public class ChilliWaterCauldronBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 3);
    public static final int MAX_LEVEL = 3;

    /** The cauldron's own shape: floor plus walls, so it is not a full cube to walk into. */
    private static final VoxelShape SHAPE = VoxelShapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D),
            Block.box(0.0D, 3.0D, 0.0D, 2.0D, 16.0D, 16.0D),
            Block.box(14.0D, 3.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(2.0D, 3.0D, 0.0D, 14.0D, 16.0D, 2.0D),
            Block.box(2.0D, 3.0D, 14.0D, 14.0D, 16.0D, 16.0D));

    public ChilliWaterCauldronBlock() {
        super(Block.Properties.of(Material.METAL).strength(2.0F).sound(SoundType.METAL)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(LEVEL, MAX_LEVEL));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos,
                               ISelectionContext context) {
        return SHAPE;
    }

    /**
     * Picking this block gives a vanilla cauldron: it has no item of its own, and a plain cauldron is
     * what it was made from and what it turns back into when emptied.
     */
    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(Blocks.CAULDRON);
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                Hand hand, BlockRayTraceResult hit) {
        ItemStack held = player.getItemInHand(hand);
        int level = state.getValue(LEVEL);

        // A glass bottle takes one level - a third of the cauldron.
        if (held.getItem() == Items.GLASS_BOTTLE) {
            if (!world.isClientSide) {
                ItemStack filled = LazyPour.createFilledResult(player, held,
                        new ItemStack(ChilliWater.BOTTLE.get()));
                player.setItemInHand(hand, filled);
                drain(state, world, pos, level - 1);
                world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundCategory.BLOCKS,
                        1.0F, 1.0F);
            }
            return ActionResultType.SUCCESS;
        }

        // A bucket takes the lot: one cauldron is one bucket.
        ItemStack bucket = Buckets.chilliWaterBucketFor(held, player);
        if (bucket != null) {
            if (!world.isClientSide) {
                ItemStack result = player.abilities.instabuild
                        ? held
                        : LazyPour.createFilledResult(player, held, bucket);
                player.setItemInHand(hand, result);
                world.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                world.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundCategory.BLOCKS,
                        1.0F, 1.0F);
            }
            return ActionResultType.SUCCESS;
        }
        return ActionResultType.PASS;
    }

    /** One level less, or the plain cauldron when the last one goes. */
    private static void drain(BlockState state, World world, BlockPos pos, int level) {
        if (level <= 0) {
            world.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        } else {
            world.setBlockAndUpdate(pos, state.setValue(LEVEL, level));
        }
    }

    // ---- making it: chillies on a cauldron of water

    /** Registers the conversion from a vanilla water cauldron. Called from the fluid's registrar. */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ChilliWaterCauldronBlock::onUseCauldron);
    }

    /**
     * Thirty-two AoA chillies on a cauldron of water make one of these.
     *
     * <p>Creative uses nothing up and does not demand the count, the way the rest of the mod treats
     * creative; survival is told how many chillies it wants when it does not have them.
     */
    @SubscribeEvent
    public static void onUseCauldron(PlayerInteractEvent.RightClickBlock event) {
        PlayerEntity player = event.getPlayer();
        ItemStack held = event.getItemStack();
        if (!isChilli(held)) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = world.getBlockState(pos);
        if (!state.is(Blocks.CAULDRON) || state.getValue(CauldronBlock.LEVEL) <= 0) {
            return;
        }
        boolean creative = player.abilities.instabuild;
        if (!creative && countChillies(player) < ChilliWater.CHILLIES_PER_CAULDRON) {
            if (!world.isClientSide) {
                player.displayClientMessage(new TranslationTextComponent(
                        "message.aoaagriculture.chilli_water.not_enough",
                        ChilliWater.CHILLIES_PER_CAULDRON), false);
            }
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
            return;
        }
        if (!world.isClientSide) {
            if (!creative) {
                spendChillies(player);
            }
            world.setBlockAndUpdate(pos, ChilliWater.CAULDRON.get().defaultBlockState()
                    .setValue(LEVEL, MAX_LEVEL));
            world.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundCategory.BLOCKS,
                    1.0F, 1.0F);
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }

    private static boolean isChilli(ItemStack stack) {
        return !stack.isEmpty()
                && ChilliWater.CHILLI.equals(String.valueOf(stack.getItem().getRegistryName()));
    }

    private static int countChillies(PlayerEntity player) {
        int total = 0;
        for (ItemStack stack : carried(player)) {
            if (isChilli(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void spendChillies(PlayerEntity player) {
        int left = ChilliWater.CHILLIES_PER_CAULDRON;
        for (ItemStack stack : carried(player)) {
            if (left <= 0) {
                break;
            }
            if (isChilli(stack)) {
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                left -= used;
            }
        }
        player.inventory.setChanged();
    }

    /** Everything the player carries, main inventory and offhand. */
    private static List<ItemStack> carried(PlayerEntity player) {
        List<ItemStack> all = new ArrayList<>(player.inventory.items);
        all.addAll(player.inventory.offhand);
        return all;
    }
}
