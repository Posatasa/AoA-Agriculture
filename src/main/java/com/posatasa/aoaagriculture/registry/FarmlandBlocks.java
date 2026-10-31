package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.block.AoAFarmlandBlock;
import com.posatasa.aoaagriculture.loot.FertilisedFarmlandLootModifier;
import com.posatasa.aoaagriculture.block.AoaFertilisedFarmlandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.Blocks;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Set;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.event.entity.player.UseHoeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and lookup for the farmland blocks.
 *
 * <p>For every AoA dirt block that AoA itself puts in the {@code forge:dirt} tag (polluted soil
 * excluded), two blocks are added: a farmland, and the fertilised farmland AoA's farming system
 * expects. That tag is this mod's definition of "AoA's dirt blocks": it is the same set AoA treats
 * as dirt for every other purpose, and it excludes the stone-like block that merely happens to be
 * named dirt (archaic dirt is a pickaxe-mined stone block, not soil) as well as the handful of AoA
 * grass variants that sit on stone or rock rather than on soil.
 *
 * <p>Nothing here names an AoA class. Every association to AoA's blocks is made by registry name
 * after all mods have finished registering, so this add-on contains no AoA code and needs no
 * AoA classes on its compile classpath; the two places that have to cooperate with AoA's own
 * abilities and crops do so through mixins configured by name (see {@code aoaagriculture.mixins.json}).
 */
public final class FarmlandBlocks {
    public static final String MOD_ID = "aoaagriculture";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** The first block added, used as this mod's creative tab icon. */
    private static RegistryObject<Block> icon;

    /**
     * This mod's own creative tab. Forge patches {@link ItemGroup} so that a plain
     * {@code new ItemGroup(label)} is registered with a spare index, and vanilla builds the
     * translation key as {@code itemGroup.<label>} - which is what the lang files provide.
     */
    /** The icon for the farmland tab: the first block this mod adds. */
    static ItemStack iconStack() {
        return new ItemStack(icon.get());
    }

    /**
     * Fills this mod's tab: each soil's farmland and its fertilised counterpart, then the buckets in
     * the order the metals are listed, then the bottle - and afterwards anything else that names this
     * tab, so an item added later cannot go missing by being left off a list here.
     */
    static void fillTab(NonNullList<ItemStack> items) {
        for (Soil soil : SOILS) {
            items.add(new ItemStack(soil.block.get().asItem()));
            items.add(new ItemStack(soil.fertilised.get().asItem()));
        }
    }



    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<GlobalLootModifierSerializer<?>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.LOOT_MODIFIER_SERIALIZERS, MOD_ID);

    /** One entry per AoA soil: its farmland, its fertilised farmland, and the names behind them. */
    private static final List<Soil> SOILS = new ArrayList<>();

    /** Plain farmland -> its fertilised counterpart. */
    private static final Map<Block, Block> FERTILISED_BY_FARMLAND = new HashMap<>();

    /**
     * Source block (an AoA soil or the grass growing on it) -> the AoA farmland a hoe turns it
     * into. Filled by {@link #ensureBound()}.
     */
    private static final Map<Block, Block> BY_SOURCE = new HashMap<>();

    /** Our farmland or fertilised farmland -> the AoA soil it belongs to. */
    private static final Map<Block, Block> SOIL_OF = new HashMap<>();

    /**
     * AoA crop -> the AoA soils whose farmland speeds that crop up.
     *
     * <p>Each crop belongs to the dimension its soil comes from, so growing it on that soil's
     * farmland (plain or fertilised) doubles its growth speed. Crops from the list AoA grows
     * without any soil of their own are left out: tea, heart fruit, the thorny plant, the
     * trilliad, eye bulbs, magic marang and green manure have no matching dirt, so there is
     * nothing to reward.
     */
    private static final String[][] BONUS_CROPS = {
            {"aoa3:bubble_berry_crop", "aoa3:coral_soil"},
            {"aoa3:floracles_crop", "aoa3:coral_soil"},
            {"aoa3:chilli_crop", "aoa3:aromatic_dirt"},
            {"aoa3:goldicaps_crop", "aoa3:aromatic_dirt"},
            {"aoa3:rosidon_crop", "aoa3:aromatic_dirt"},
            // Both moons share Lunalyte and Lunasole soil between them.
            {"aoa3:luna_globe_crop", "aoa3:lunalyte_dirt"},
            {"aoa3:luna_globe_crop", "aoa3:lunasole_dirt"},
            {"aoa3:lunacrike_crop", "aoa3:lunalyte_dirt"},
            {"aoa3:lunacrike_crop", "aoa3:lunasole_dirt"},
            {"aoa3:lunalon_crop", "aoa3:lunalyte_dirt"},
            {"aoa3:lunalon_crop", "aoa3:lunasole_dirt"},
            {"aoa3:mystic_shroom_crop", "aoa3:fungal_dirt"},
    };

    /** AoA crop -> the soils that speed it up. Filled by {@link #ensureBound()}. */
    private static final Map<Block, Set<Block>> BONUS_SOILS = new HashMap<>();

    /** The two AoA soils Farmer's Delight's rich soil behaviour is extended to. */
    private static final String[] COLONY_SOILS = {"aoa3:blackened_soil", "aoa3:fungal_dirt"};

    /** Vanilla mushroom -> Farmer's Delight's colony block for it, if that mod is installed. */
    private static final Map<Block, Block> COLONIES = new HashMap<>();

    /** The AoA soils that behave like Farmer's Delight's rich soil for mushrooms. */
    private static final Set<Block> colonySoils = new HashSet<>();

    private static volatile boolean bound;

    /**
     * Registers the blocks, items and loot modifier, plus the two Forge event listeners.
     *
     * <p>The listeners are plain (non-generic) events, which matters: Forge's
     * {@code EventBus#addListener} throws "Cannot register a generic event listener with
     * addListener" for anything extending {@code GenericEvent}, and that exception is thrown
     * while the mod is being constructed, so it fails the whole game at startup rather than at
     * the point of use.
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(FarmlandBlocks::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(FarmlandBlocks::onUseHoe);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
    }

    /**
     * The fertilised counterpart of one of our farmlands, or null when the block is not ours.
     * Used by the bone meal ability hook.
     */
    public static Block fertilisedFor(Block farmland) {
        ensureBound();
        return FERTILISED_BY_FARMLAND.get(farmland);
    }

    /** The AoA soil a farmland or fertilised farmland belongs to, or null if it is not ours. */
    public static Block soilOf(Block farmland) {
        ensureBound();
        return SOIL_OF.get(farmland);
    }

    /** True when {@code crop} is an AoA crop that grows faster on the farmland {@code below}. */
    public static boolean speedsUpCrop(Block crop, Block below) {
        ensureBound();
        Block soil = SOIL_OF.get(below);
        if (soil == null) {
            return false;
        }
        Set<Block> soils = BONUS_SOILS.get(crop);
        return soils != null && soils.contains(soil);
    }

    /**
     * Farmer's Delight's colony block for this mushroom, when it sits on one of the two AoA soils
     * that are meant to behave like rich soil; null otherwise, including when Farmer's Delight is
     * not installed.
     */
    public static Block colonyFor(Block mushroom, Block below) {
        ensureBound();
        return colonySoils.contains(below) ? COLONIES.get(mushroom) : null;
    }

    /** True when this block is one of the soils meant to behave like Farmer's Delight's rich soil. */
    public static boolean isColonySoil(Block below) {
        ensureBound();
        return colonySoils.contains(below);
    }

    /** Adds one soil: its farmland and its fertilised farmland, plus both items. */
    private static void add(String name, String soil, String grass) {
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new AoAFarmlandBlock(properties(), null));
        ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().tab(Tabs.FARMLAND)));

        String fertilisedName = name.replace("_farmland", "_fertilised_farmland");
        RegistryObject<Block> fertilised = BLOCKS.register(fertilisedName,
                () -> new AoaFertilisedFarmlandBlock(properties(), null));
        ITEMS.register(fertilisedName, () -> new BlockItem(fertilised.get(),
                new Item.Properties().tab(Tabs.FARMLAND)));

        if (icon == null) {
            icon = block;
        }
        SOILS.add(new Soil(soil, grass, MOD_ID + ":" + name, block, fertilised));
    }

    private static Block.Properties properties() {
        // Vanilla farmland's own properties, in vanilla's own order: random ticks first (that is
        // what runs FarmlandBlock#randomTick, and therefore what makes the block soak up water and
        // dry out again - without it the moisture can never leave 0), then hardness and explosion
        // resistance through one call, the gravel sound, and the two collision predicates forced
        // true because the block is only 15/16 of a block tall. The shovel tool type matches the
        // AoA soils.
        return Block.Properties.of(Material.DIRT)
                .randomTicks()
                .strength(0.6F)
                .sound(SoundType.GRAVEL)
                .isSuffocating((state, world, pos) -> true)
                .isViewBlocking((state, world, pos) -> true)
                .harvestTool(ToolType.SHOVEL);
    }

    /**
     * Resolves every AoA soil (and its grass) to the blocks added for it.
     *
     * <p>Deliberately not done inside a registry event: those fire once per mod in an unspecified
     * order, so looking AoA's blocks up while registration is still running would find them
     * missing whenever this mod happens to come first.
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        ensureBound();
    }

    private static void ensureBound() {
        if (bound) {
            return;
        }
        synchronized (FarmlandBlocks.class) {
            if (bound) {
                return;
            }
            int boundSoils = 0;
            for (Soil soil : SOILS) {
                Block farmland = soil.block.get();
                Block soilBlock = block(soil.soil);
                if (soilBlock == null) {
                    // Not a crash: AoA's registry simply does not have this name, which points at
                    // a version mismatch (or AoA not installed). Say so instead of doing nothing.
                    LOGGER.warn("AoA block {} is missing; {} will not be tillable",
                            soil.soil, soil.name);
                    continue;
                }
                if (farmland instanceof AoAFarmlandBlock) {
                    ((AoAFarmlandBlock) farmland).setSoil(soilBlock);
                }
                SOIL_OF.put(farmland, soilBlock);
                Block fertilised = soil.fertilised.get();
                if (fertilised instanceof AoaFertilisedFarmlandBlock) {
                    AoaFertilisedFarmlandBlock fertile = (AoaFertilisedFarmlandBlock) fertilised;
                    fertile.setSoil(soilBlock);
                    fertile.setFarmland(farmland);
                    FERTILISED_BY_FARMLAND.put(farmland, fertilised);
                    SOIL_OF.put(fertilised, soilBlock);
                }
                for (String source : new String[] { soil.soil, soil.grass }) {
                    Block from = block(source);
                    if (from != null) {
                        BY_SOURCE.put(from, farmland);
                    }
                }
                boundSoils++;
            }

            for (String[] entry : BONUS_CROPS) {
                Block crop = block(entry[0]);
                Block soilBlock = block(entry[1]);
                if (crop == null || soilBlock == null) {
                    LOGGER.warn("AoA crop {} or soil {} is missing; no growth bonus for it",
                            entry[0], entry[1]);
                    continue;
                }
                BONUS_SOILS.computeIfAbsent(crop, key -> new HashSet<>()).add(soilBlock);
            }

            // Farmer's Delight is optional: without it these stay absent and the mushroom hook
            // does nothing, rather than turning mushrooms into air.
            Block brown = block("farmersdelight:brown_mushroom_colony");
            Block red = block("farmersdelight:red_mushroom_colony");
            if (brown != null) {
                COLONIES.put(Blocks.BROWN_MUSHROOM, brown);
            }
            if (red != null) {
                COLONIES.put(Blocks.RED_MUSHROOM, red);
            }
            for (String name : COLONY_SOILS) {
                if (block(name) != null) {
                    colonySoils.add(block(name));
                }
            }

            bound = true;
            LOGGER.info("AoA Agriculture active: {} AoA soils, {} farmlands, {} speed-up crops, "
                            + "{} colony soils",
                    boundSoils, boundSoils * 2, BONUS_SOILS.size(), colonySoils.size());
        }
    }

    private static Block block(String registryName) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(registryName));
    }

    /**
     * Turns one of AoA's soils into the matching farmland, the way vanilla's hoe tills dirt.
     *
     * <p>Setting {@link Event#getResult} to ALLOW tells Forge the tilling was handled: it then
     * spends one point of hoe durability itself.
     */
    @SubscribeEvent
    public static void onUseHoe(UseHoeEvent event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            return;                                    // another handler already dealt with it
        }
        ItemUseContext context = event.getContext();
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (context.getClickedFace() == Direction.DOWN) {
            return;
        }
        if (!world.isEmptyBlock(pos.above())) {
            return;                                    // vanilla refuses to till when covered
        }
        ensureBound();
        Block farmland = BY_SOURCE.get(world.getBlockState(pos).getBlock());
        if (farmland == null) {
            return;
        }
        world.playSound(context.getPlayer(), pos, SoundEvents.HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!world.isClientSide) {
            world.setBlock(pos, farmland.defaultBlockState(), 11);
        }
        event.setResult(Event.Result.ALLOW);
    }

    // ---- the soils -------------------------------------------------------------------------
    //                         farmland name             AoA soil                its grass
    static {
        add("aromatic_farmland",   "aoa3:aromatic_dirt",   "aoa3:aromatic_grass");
        add("blackened_farmland",  "aoa3:blackened_soil",  "aoa3:faded_grass");
        add("candied_farmland",    "aoa3:candied_dirt",    "aoa3:candied_grass");
        add("celevian_farmland",   "aoa3:celevian_dirt",   "aoa3:celevian_grass");
        add("coral_farmland",      "aoa3:coral_soil",      "aoa3:coral_grass");
        add("creep_farmland",      "aoa3:creep_dirt",      "aoa3:creep_grass");
        add("fungal_farmland",     "aoa3:fungal_dirt",     "aoa3:fungal_grass");
        add("greckon_farmland",    "aoa3:greckon_dirt",    "aoa3:greckon_grass");
        add("lunalyte_farmland",   "aoa3:lunalyte_dirt",   "aoa3:lunalyte_grass");
        add("lunasole_farmland",   "aoa3:lunasole_dirt",   "aoa3:lunasole_grass");
        add("weightless_farmland", "aoa3:weightless_dirt", "aoa3:weightless_grass");
        LOOT_MODIFIERS.register("fertilised_farmland",
                FertilisedFarmlandLootModifier.Serializer::new);
    }

    private FarmlandBlocks() {}

    /** The names and block references that make up one soil's two farmlands. */
    private static final class Soil {
        private final String soil;
        private final String grass;
        private final String name;
        private final RegistryObject<Block> block;
        private final RegistryObject<Block> fertilised;

        private Soil(String soil, String grass, String name, RegistryObject<Block> block,
                     RegistryObject<Block> fertilised) {
            this.soil = soil;
            this.grass = grass;
            this.name = name;
            this.block = block;
            this.fertilised = fertilised;
        }
    }
}
