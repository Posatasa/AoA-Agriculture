package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.item.AoABucketItem;
import com.posatasa.aoaagriculture.item.CandiedWaterBucketItem;
import com.posatasa.aoaagriculture.item.InfiniteBucketItem;
import com.posatasa.aoaagriculture.item.LavaOnlyBucketItem;
import com.posatasa.aoaagriculture.item.LavaTankBucketItem;
import com.posatasa.aoaagriculture.item.LunarWaterBucketItem;
import com.posatasa.aoaagriculture.item.BucketFamily;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * The bucket items: an empty, water, lava and candied water bucket for each of two AoA metals.
 *
 * <p>Each bucket is a plain {@link AoABucketItem}, i.e. vanilla's bucket with the family mapping
 * layered on. The fluid is resolved eagerly rather than through the supplier constructor: this
 * version's {@code BucketItem} keeps the fluid in a field that its emptying logic reads directly, so
 * a supplier-built bucket would silently refuse to pour. Forge registers fluids before items, so
 * AoA's candied water is already there when these suppliers run.
 */
public final class Buckets {
    private static final Logger LOGGER = LogManager.getLogger(FarmlandBlocks.MOD_ID);

    /** AoA's candied water - the fluid the sugar water buckets hold. */
    private static final String CANDIED_WATER = "aoa3:candied_water";

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    public static final BucketFamily LIMONITE = new BucketFamily("limonite");
    public static final BucketFamily ROSITE = new BucketFamily("rosite");
    /** Shyre's water bucket is a portable water source; the rest of the family is ordinary. */
    public static final BucketFamily SHYRE = new BucketFamily("shyre");
    /** Lunar's water bucket makes the player immune to fall damage while held. */
    public static final BucketFamily LUNAR = new BucketFamily("lunar");
    /** Emberstone's bucket is a lava tank: four deep, and water is refused outright. */
    public static final BucketFamily EMBERSTONE = new BucketFamily("emberstone");
    /** Blazium's bucket is the same tank, eight deep. */
    public static final BucketFamily BLAZIUM = new BucketFamily("blazium");
    /** Dark: an empty lava bucket, and an endless one its two-second hold falls back to. */
    public static final BucketFamily DARK = new BucketFamily("dark");

    /**
     * The order the creative tab shows the metals in: Limonite, Rosite, Emberstone, Blazium, Lunar,
     * Shyre, Dark.
     *
     * <p>Written out because the registry's order is whichever item register happened to run first,
     * which is no order anyone chose. Within a metal the buckets come in their own order - empty,
     * water, lava, candied water - which is {@code Kind}'s declaration order.
     */
    private static final java.util.List<BucketFamily> TAB_ORDER = java.util.Arrays.asList(
            LIMONITE, ROSITE, EMBERSTONE, BLAZIUM, LUNAR, SHYRE, DARK);

    private static Fluid candiedWater;

    public static void register(IEventBus modBus) {
        family(LIMONITE, AoABucketItem::new);
        family(ROSITE, AoABucketItem::new);
        family(SHYRE, InfiniteBucketItem::new);
        family(LUNAR, LunarWaterBucketItem::new);
        emberstone();
        blazium();
        dark();
        ITEMS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(Buckets::onFall);
    }

    /** How one bucket item is built; the Shyre water bucket is the only one that differs. */
    private interface Factory {
        Item create(Fluid content, Item.Properties properties, BucketFamily family);
    }

    private static void family(BucketFamily family, Factory waterFactory) {
        String metal = family.metal();
        Factory ordinary = AoABucketItem::new;
        // An empty bucket stacks to 16 and a filled one to 1, exactly as vanilla's do.
        bind(family, BucketFamily.Kind.EMPTY, metal + "_bucket", () -> Fluids.EMPTY, 16, ordinary);
        bind(family, BucketFamily.Kind.WATER, "water_" + metal + "_bucket", () -> Fluids.WATER, 1,
                waterFactory);
        bind(family, BucketFamily.Kind.LAVA, "lava_" + metal + "_bucket", () -> Fluids.LAVA, 1,
                ordinary);
        // Candied water is AoA's fluid, and fluids are registered after items, so this one is
        // built around a lazily resolved fluid rather than a value.
        RegistryObject<Item> candied = ITEMS.register("candied_water_" + metal + "_bucket",
                () -> new CandiedWaterBucketItem(Buckets::candiedWater,
                        new Item.Properties().tab(FarmlandBlocks.TAB).stacksTo(1), family));
        family.bind(BucketFamily.Kind.CANDIED_WATER, candied);
    }

    private static void bind(BucketFamily family, BucketFamily.Kind kind, String name,
                             Supplier<Fluid> fluid, int stackSize, Factory factory) {
        RegistryObject<Item> item = ITEMS.register(name, () -> factory.create(fluid.get(),
                new Item.Properties().tab(FarmlandBlocks.TAB).stacksTo(stackSize), family));
        family.bind(kind, item);
    }

    /**
     * The Moon Ingot water bucket's promise: no fall damage while it is in hand.
     *
     * <p>Setting the multiplier rather than cancelling keeps the landing itself - the sounds and
     * particles still play, there is simply no damage to take.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && LunarWaterBucketItem.isHeldBy((PlayerEntity) event.getEntityLiving())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /**
     * The Emberstone family has two buckets, not four: it holds lava and nothing else, so a water
     * or candied water bucket for it would be an item that cannot be filled.
     */
    private static void emberstone() {
        bind(EMBERSTONE, BucketFamily.Kind.EMPTY, "emberstone_bucket", () -> Fluids.EMPTY, 1,
                LavaOnlyBucketItem::new);
        bind(EMBERSTONE, BucketFamily.Kind.LAVA, "lava_emberstone_bucket", () -> Fluids.LAVA, 1,
                (fluid, properties, family) -> new LavaTankBucketItem(fluid, properties, family, 4));
    }

    /** The same tank as Emberstone's, but eight deep. */
    private static void blazium() {
        bind(BLAZIUM, BucketFamily.Kind.EMPTY, "blazium_bucket", () -> Fluids.EMPTY, 1,
                LavaOnlyBucketItem::new);
        bind(BLAZIUM, BucketFamily.Kind.LAVA, "lava_blazium_bucket", () -> Fluids.LAVA, 1,
                (fluid, properties, family) -> new LavaTankBucketItem(fluid, properties, family, 8));
    }

    /**
     * The Dark family, named like every other one: the plain name is the empty bucket - what the
     * upgrade kit makes and the only form the recipe accepts - and its lava one is endless.
     */
    private static void dark() {
        bind(DARK, BucketFamily.Kind.EMPTY, "dark_bucket", () -> Fluids.EMPTY, 1,
                LavaOnlyBucketItem::new);
        bind(DARK, BucketFamily.Kind.LAVA, "lava_dark_bucket", () -> Fluids.LAVA, 1,
                InfiniteBucketItem::new);
    }

    public static Fluid candiedWater() {
        if (candiedWater == null) {
            candiedWater = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(CANDIED_WATER));
            if (candiedWater == null) {
                // AoA is a hard dependency and the name is checked at build time, so this only
                // happens on a version mismatch. Say so, and leave the bucket inert rather than
                // crashing the registry.
                LOGGER.warn("AoA fluid {} is missing; the candied water buckets will do nothing",
                        CANDIED_WATER);
                candiedWater = Fluids.EMPTY;
            }
        }
        return candiedWater;
    }

    /** Adds this mod's buckets to the creative tab, in {@link #TAB_ORDER}. */
    static void addToTab(NonNullList<ItemStack> items) {
        for (BucketFamily family : TAB_ORDER) {
            for (BucketFamily.Kind kind : BucketFamily.Kind.values()) {
                Item item = family.item(kind);
                if (item != null) {
                    items.add(new ItemStack(item));
                }
            }
        }
    }

    private Buckets() {}
}
