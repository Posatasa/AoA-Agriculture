package com.posatasa.aoaagriculture;

import com.posatasa.aoaagriculture.registry.Bottles;
import com.posatasa.aoaagriculture.registry.Buckets;
import com.posatasa.aoaagriculture.registry.FarmlandBlocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/**
 * AoA Agriculture: farmland for every AoA soil, and the small things that go with it.
 *
 * <p>The blocks are vanilla farmland with AoA's own soil as their drop, and their textures are
 * vanilla's farmland pattern recoloured to AoA's palettes. Nothing here is AoA code or an AoA asset.
 */
@Mod(AoAAgriculture.MOD_ID)
public class AoAAgriculture {
    public static final String MOD_ID = FarmlandBlocks.MOD_ID;

    public AoAAgriculture() {
        IEventBus bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        FarmlandBlocks.register(bus);
        Buckets.register(bus);
        Bottles.register(bus);
    }
}
